package com.cinetrack.data.sync

import com.cinetrack.domain.SyncProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Owns provider selection and direction. Providers own only transport/mapping. */
class SyncCoordinator(
    private val registry: TrackingProviderRegistry,
    private val operations: SyncOperationRepository,
    private val reconciler: SyncReconciler = SyncReconciler(),
    private val routingMutex: TrackingRoutingMutex = TrackingRoutingMutex(),
    private val secondaryDeliveryObserver: SecondaryProviderDeliveryObserver? = null,
) {
    private val fullSyncMutex = Mutex()
    /** Each provider owns an independent network lane.  A slow SECONDARY
     * transport must never starve the authoritative MAIN provider. */
    private val providerIoLanes = TrackingProviderId.entries.associateWith { Mutex() }

    /** Serializes Floppy activation/disconnect with Floppy network passes. */
    suspend fun <T> withProviderIoQuiesced(block: suspend () -> T): T {
        return withProviderIo(TrackingProviderId.FLOPPY, block = block)
    }
    /** Exposes the pure policy for provider adapters and deterministic tests. */
    fun reconcile(
        local: LocalTrackingSnapshot,
        remote: TrackingSnapshot,
        provider: TrackingProviderId,
    ): ReconciliationResult = reconciler.reconcile(local, remote, provider)

    suspend fun sync(onProgress: (SyncProgress) -> Unit): Result<SyncCoordinatorOutcome> = fullSyncMutex.withLock {
        resultOf {
        repairCurrentFloppyInstance()
        val configuration = registry.configuration()
        val mainId = configuration.mainProvider
            ?: throw IllegalStateException("Select a MAIN tracking provider")
        val main = registry.getProvider(mainId)
            ?: throw IllegalStateException("The MAIN tracking provider is unavailable")
        requireAuthenticated(main)
        val requiredMainPull = setOf(
            TrackingCapability.PULL_LIBRARY,
            TrackingCapability.PULL_MOVIE_HISTORY,
            TrackingCapability.PULL_EPISODE_HISTORY,
        )
        if (!main.capabilities.supportsTwoWaySync || !requiredMainPull.all(main.capabilities::supports)) {
            throw TrackingSyncError.ProviderUnavailable(main.id, IllegalStateException("MAIN provider cannot reconcile snapshots yet"))
        }

        // Capture once. A provider must never expose its DTOs to the queue or UI.
        // Managed Floppy bootstrap rows are durable queue data, but their
        // network owner is exclusively FloppyBootstrapWorker.  A normal or
        // forced sync must never steal that work.
        val pending = operations.pending().filterNot(SyncOperation::isManagedBootstrapOperation)
        var attemptedMain = emptyList<SyncOperation>()
        var mainPassCompleted = false
        try {
            val secondary = configuration.secondaryProvider?.let(registry::getProvider)
            prepareDeliveryRows(pending, main, secondary, configuration)

            val mainPending = pendingFor(pending, main.id)
            val mainUnsupported = mainPending.firstOrNull { !main.capabilities.supports(it) }
            if (mainUnsupported != null) {
                val error = TrackingSyncError.UnsupportedOperation(main.id, mainUnsupported.type)
                val unsupported = mainPending.filter { it.id == mainUnsupported.id }
                operations.failDelivery(main.id, unsupported, error)
                if (!operations.requiresPersistedDeliveryRows) operations.fail(unsupported, error)
                throw error
            }

            // Run the MAIN pass before the optional mirror.  This keeps a
            // queued/blocked Floppy lane from delaying authority even when a
            // forced sync is requested while bootstrap is active.
            requireAuthenticated(main)
            attemptedMain = mainPending
            val outcome = withProviderIo(main.id) { main.syncBidirectionally(mainPending, onProgress) }
            // The provider only attempted the current MAIN delivery set. Older
            // generations that are already ACKNOWLEDGED (for example after a
            // partial SECONDARY failure) must not be required in this response.
            // Keep the provider identity captured at the start of this pass.
            // Configuration may change while the network request is running;
            // its result must never be attributed to the new MAIN provider.
            acknowledge(main.id, mainPending, outcome.acknowledgedOperationIds, outcome.deferredOperationIds)
            mainPassCompleted = true
            // A retry may have had only SECONDARY work left after MAIN was
            // acknowledged by an earlier attempt. Re-evaluate the complete
            // operation against every persisted delivery, not just this pass.
            // Direction is structural: SECONDARY has no pull/sync call
            // anywhere here. A secondary failure is isolated; MAIN has
            // already completed its authoritative pass above.
            if (secondary != null) {
                val secondaryPending = pendingFor(pending, secondary.id)
                val unsupported = secondaryPending.filterNot(secondary.capabilities::supports).mapTo(linkedSetOf(), SyncOperation::id)
                operations.skipUnsupported(secondary.id, secondaryPending.filter { it.id in unsupported })
                val deliverable = secondaryPending.filter { it.id !in unsupported }
                if (deliverable.isNotEmpty()) try {
                    withProviderIo(secondary.id) {
                        requireAuthenticated(secondary)
                        pushTo(secondary, deliverable)
                    }
                    operations.acknowledge(secondary.id, deliverable)
                    secondaryDeliveryObserver?.onSecondaryDeliveryPassCompleted(secondary.id)
                } catch (error: Throwable) {
                    if (error is CancellationException) throw error
                    operations.failDelivery(secondary.id, deliverable, error)
                    if (!operations.requiresPersistedDeliveryRows) operations.fail(deliverable, error)
                }
            }
            operations.completeReady(pending)
            SyncCoordinatorOutcome(outcome.itemsChanged, outcome.report)
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            // A SECONDARY transport failure is reported after MAIN has
            // already acknowledged its current-generation deliveries. Never
            // overwrite that successful MAIN result just because the mirror
            // failed; delivery rows are the provider-specific authority.
            if (!mainPassCompleted) {
                operations.failDelivery(main.id, attemptedMain, error)
                if (!operations.requiresPersistedDeliveryRows) operations.fail(attemptedMain, error)
            }
            throw error
        }
    }

    }

    suspend fun pushPending(operationIds: Set<String>? = null): Result<Unit> =
        pushPendingAcrossProviders(operationIds)

    /**
     * Delivers only one provider's current-generation rows while retaining the
     * same provider-I/O serialization and delivery validation as normal sync.
     * This narrow boundary is used by the durable Floppy bootstrap worker so a
     * bootstrap batch cannot accidentally dispatch to MAIN or retarget rows.
     */
    suspend fun pushPendingForProvider(
        providerId: TrackingProviderId,
        operationIds: Set<String>,
        expectedInstanceId: String? = null,
        transport: (suspend (List<SyncOperation>) -> ProviderPushResult)? = null,
    ): Result<Unit> = withProviderIo(providerId) {
        resultOf {
            if (operationIds.isEmpty()) return@resultOf Unit
            val managedBootstrap = operationIds.isNotEmpty() && operationIds.all { it.startsWith("bootstrap:") }
            if (!managedBootstrap) repairCurrentFloppyInstance()
            val provider = registry.getProvider(providerId)
                ?: throw TrackingSyncError.ProviderUnavailable(providerId)
            val configuration = registry.configuration()
            val actualInstanceId = provider.currentDeliveryInstanceId()
            if (!expectedInstanceId.isNullOrBlank() && actualInstanceId != expectedInstanceId) {
                throw TrackingSyncError.ProviderUnavailable(
                    providerId,
                    IllegalStateException("Provider instance changed while delivering a batch"),
                )
            }
            // This exact-ID boundary is intentionally the one exception to
            // generic bootstrap exclusion: FloppyBootstrapWorker owns these
            // rows and passes their persisted ids explicitly.
            val pending = if (managedBootstrap && !expectedInstanceId.isNullOrBlank()) {
                operations.bootstrapPendingByIds(expectedInstanceId, operationIds)
            } else {
                operations.pending(operationIds)
            }
            if (pending.isEmpty()) return@resultOf Unit
            if (!operations.requiresPersistedDeliveryRows) {
                operations.ensureDeliveries(
                    pending,
                    pending.map {
                        SyncOperationDelivery(
                            operationId = it.id,
                            operationVersion = it.sourceVersion,
                            providerId = providerId,
                            required = true,
                            roleAtEnqueue = if (providerId == configuration.mainProvider) TrackingRole.MAIN else TrackingRole.SECONDARY,
                            providerInstanceId = actualInstanceId,
                            createdAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis(),
                        )
                    },
                )
            }
            val deliverable = pendingFor(pending, providerId)
            val unsupported = deliverable.filterNot(provider.capabilities::supports)
            if (unsupported.isNotEmpty()) {
                if (providerId == configuration.mainProvider) {
                    throw TrackingSyncError.UnsupportedOperation(providerId, unsupported.first().type)
                }
                operations.skipUnsupported(providerId, unsupported)
            }
            val supported = deliverable.filter { it !in unsupported }
            if (supported.isEmpty()) {
                operations.completeReady(pending)
                return@resultOf Unit
            }
            try {
                requireAuthenticated(provider)
                pushTo(provider, supported, transport)
                operations.acknowledge(providerId, supported)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                operations.failDelivery(providerId, supported, error)
                if (!operations.requiresPersistedDeliveryRows) operations.fail(supported, error)
                throw error
            }
            if (providerId != configuration.mainProvider) {
                secondaryDeliveryObserver?.onSecondaryDeliveryPassCompleted(providerId)
            }
            operations.completeReady(pending)
        }
    }

    /** Durable queue projection used by bounded background delivery workers. */
    suspend fun pendingOperationIds(operationIds: Set<String>): Set<String> =
        operations.pending(operationIds).mapTo(linkedSetOf(), SyncOperation::id)

    /** Returns the current durable operations for an exact bootstrap unit. */
    suspend fun pendingOperations(operationIds: Set<String>): List<SyncOperation> = operations.pending(operationIds)

    suspend fun pendingOperationCount(operationIds: Set<String>): Int =
        pendingOperationIds(operationIds).size

    suspend fun pendingBootstrapOperations(connectionId: String, limit: Int): List<SyncOperation> =
        operations.bootstrapPending(connectionId, limit)

    suspend fun pendingBootstrapEpisodeOperations(connectionId: String, limit: Int): List<SyncOperation> =
        operations.bootstrapEpisodePending(connectionId, limit)

    suspend fun pendingBootstrapCount(connectionId: String): Int =
        operations.bootstrapPendingCount(connectionId)

    /** Bootstrap-only lane ownership: the worker may run a bounded internal
     * wave while normal Floppy sync and connection changes remain excluded. */
    suspend fun <T> withFloppyBootstrapLane(
        expectedInstanceId: String,
        block: suspend () -> T,
    ): T = withProviderIo(TrackingProviderId.FLOPPY) {
        val provider = registry.getProvider(TrackingProviderId.FLOPPY)
            ?: throw TrackingSyncError.ProviderUnavailable(TrackingProviderId.FLOPPY)
        check(provider.currentDeliveryInstanceId() == expectedInstanceId) {
            "Floppy connection changed while bootstrap wave was active"
        }
        block()
    }

    /** Applies one completed bootstrap unit after its remote outcome is known.
     * This is deliberately narrow: it cannot retarget, dispatch, or reopen a
     * delivery belonging to another provider instance. */
    suspend fun settleFloppyBootstrapUnit(
        expectedInstanceId: String,
        operationsForUnit: List<SyncOperation>,
        error: Throwable? = null,
    ) = withProviderIo(TrackingProviderId.FLOPPY) {
        val provider = registry.getProvider(TrackingProviderId.FLOPPY)
            ?: throw TrackingSyncError.ProviderUnavailable(TrackingProviderId.FLOPPY)
        check(provider.currentDeliveryInstanceId() == expectedInstanceId) {
            "Floppy connection changed before bootstrap acknowledgement"
        }
        val current = operations.bootstrapPendingByIds(
            expectedInstanceId,
            operationsForUnit.mapTo(linkedSetOf(), SyncOperation::id),
        )
        if (error == null) {
            operations.acknowledge(TrackingProviderId.FLOPPY, current)
            operations.completeReady(current)
        } else {
            operations.failDelivery(TrackingProviderId.FLOPPY, current, error)
        }
    }

    /**
     * Dispatches pending work while the caller already owns providerIoMutex.
     * Connection activation uses this boundary so it can keep activation,
     * bootstrap and the first queue pass atomic without recursively locking
     * the non-reentrant provider mutex.
     */
    internal suspend fun pushPendingWhileProviderIoQuiesced(operationIds: Set<String>? = null): Result<Unit> =
        pushPendingAcrossProviders(operationIds, heldProvider = TrackingProviderId.FLOPPY)

    private suspend fun pushPendingAcrossProviders(
        operationIds: Set<String>? = null,
        heldProvider: TrackingProviderId? = null,
    ): Result<Unit> = resultOf {
        repairCurrentFloppyInstance()
        val pending = operations.pending(operationIds).filterNot(SyncOperation::isManagedBootstrapOperation)
        if (pending.isEmpty()) return@resultOf Unit
        val configuration = registry.configuration()
        val main = configuration.mainProvider?.let(registry::getProvider)
        val secondary = configuration.secondaryProvider?.let(registry::getProvider)
        val targets = listOfNotNull(main, secondary).distinctBy(TrackingProvider::id)
        if (targets.isEmpty()) throw IllegalStateException("No tracking provider is configured")
        prepareDeliveryRows(pending, main, secondary, configuration)
        var secondaryFailure: Throwable? = null
        targets.forEach { provider ->
            withProviderIo(provider.id, heldProvider) {
            val providerPending = pendingFor(pending, provider.id)
            val unsupported = providerPending.filterNot(provider.capabilities::supports).mapTo(linkedSetOf(), SyncOperation::id)
            if (provider.id == configuration.mainProvider && unsupported.isNotEmpty()) {
                val error = TrackingSyncError.UnsupportedOperation(provider.id, providerPending.first { it.id in unsupported }.type)
                operations.failDelivery(provider.id, unsupported, error)
                if (!operations.requiresPersistedDeliveryRows) operations.fail(providerPending.filter { it.id in unsupported }, error)
                throw error
            }
            operations.skipUnsupported(provider.id, providerPending.filter { it.id in unsupported })
            val deliverable = providerPending.filter { it.id !in unsupported }
            if (deliverable.isNotEmpty()) {
                try {
                    requireAuthenticated(provider)
                    pushTo(provider, deliverable)
                    operations.acknowledge(provider.id, deliverable)
                    if (provider.id != configuration.mainProvider) {
                        secondaryDeliveryObserver?.onSecondaryDeliveryPassCompleted(provider.id)
                    }
                } catch (error: Throwable) {
                    if (error is CancellationException) throw error
                    operations.failDelivery(provider.id, deliverable, error)
                    if (!operations.requiresPersistedDeliveryRows) operations.fail(deliverable, error)
                    if (provider.id == configuration.mainProvider) throw error
                    secondaryFailure = error
                }
            }
            }
        }
        secondaryFailure?.let { throw it }
        operations.completeReady(pending)
    }

    suspend fun retry(operationId: String): Result<Unit> = if (operationId.startsWith("bootstrap:")) {
        Result.failure(IllegalArgumentException("Floppy bootstrap retries are owned by WorkManager"))
    } else pushPending(setOf(operationId))

    suspend fun isMainProviderConnected(): Boolean {
        val provider = registry.getMainProvider() ?: return false
        return provider.isAuthenticated()
    }

    private suspend fun pushTo(
        provider: TrackingProvider,
        pending: List<SyncOperation>,
        transport: (suspend (List<SyncOperation>) -> ProviderPushResult)? = null,
    ) {
        val unsupported = pending.firstOrNull { !provider.capabilities.supports(it) }
        if (unsupported != null) throw TrackingSyncError.UnsupportedOperation(provider.id, unsupported.type)
        val completed = (transport?.invoke(pending) ?: provider.push(pending)).completedOperationIds
        check(completed.containsAll(pending.map(SyncOperation::id))) {
            "${provider.id.name} did not acknowledge every synchronization operation"
        }
    }

    private suspend fun acknowledge(
        providerId: TrackingProviderId,
        pending: List<SyncOperation>,
        acknowledgedIds: Set<String>,
        deferredIds: Set<String>,
    ) {
        val pendingIds = pending.mapTo(linkedSetOf(), SyncOperation::id)
        check((acknowledgedIds + deferredIds).containsAll(pendingIds)) {
            "MAIN provider did not acknowledge every synchronization operation"
        }
        val acknowledged = pending.filter { it.id in acknowledgedIds }
        operations.acknowledge(providerId, acknowledged)
        operations.completeReady(acknowledged)
    }

    private suspend fun pendingFor(all: List<SyncOperation>, provider: TrackingProviderId): List<SyncOperation> {
        val ids = all.mapTo(linkedSetOf(), SyncOperation::id)
        val rows = operations.deliveries(ids)
        if (rows.isEmpty()) return if (operations.requiresPersistedDeliveryRows) emptyList() else all
        val currentInstance = registry.getProvider(provider)?.currentDeliveryInstanceId()
        val byId = rows.filter {
            it.providerId == provider &&
                if (provider == TrackingProviderId.FLOPPY) {
                    !currentInstance.isNullOrBlank() && it.providerInstanceId == currentInstance
                } else {
                    it.providerInstanceId == currentInstance
                }
        }.associateBy { "${it.operationId}:${it.operationVersion}" }
        return all.filter { byId["${it.id}:${it.sourceVersion}"]?.status in setOf(DeliveryStatus.PENDING, DeliveryStatus.FAILED) }
    }

    /** Modern operations must already have an immutable target snapshot. */
    private suspend fun requireCurrentDeliveryRows(operations: List<SyncOperation>) {
        if (operations.isEmpty() || !this@SyncCoordinator.operations.requiresPersistedDeliveryRows) return
        val rows = this@SyncCoordinator.operations.deliveries(operations.mapTo(linkedSetOf(), SyncOperation::id))
        val missing = operations.filter { operation ->
            rows.none { it.operationId == operation.id && it.operationVersion == operation.sourceVersion }
        }
        if (missing.isNotEmpty()) {
            throw IllegalStateException("Sync queue invariant violated: missing delivery target for ${missing.joinToString { it.id }}")
        }
    }

    private suspend fun prepareDeliveryRows(
        pending: List<SyncOperation>,
        main: TrackingProvider?,
        secondary: TrackingProvider?,
        configuration: TrackingConfiguration,
    ) {
        if (operations.requiresPersistedDeliveryRows) {
            requireCurrentDeliveryRows(pending)
        } else {
            // Compatibility for lightweight legacy/test repositories. The Room
            // implementation above never derives normal production targets.
            operations.ensureDeliveries(pending, buildTargets(pending, main, secondary))
        }
    }

    private suspend fun buildTargets(
        pending: List<SyncOperation>,
        main: TrackingProvider?,
        secondary: TrackingProvider?,
    ): List<SyncOperationDelivery> = buildList {
        val now = System.currentTimeMillis()
        pending.forEach { operation ->
            main?.let { provider ->
                add(SyncOperationDelivery(operation.id, operation.sourceVersion, provider.id, providerInstanceId = provider.currentDeliveryInstanceId(), createdAt = now, updatedAt = now))
            }
            secondary?.let { provider ->
                val supported = provider.capabilities.supports(operation)
                add(SyncOperationDelivery(operation.id, operation.sourceVersion, provider.id, required = supported, roleAtEnqueue = TrackingRole.SECONDARY, providerInstanceId = provider.currentDeliveryInstanceId(), status = if (supported) DeliveryStatus.PENDING else DeliveryStatus.SKIPPED_UNSUPPORTED, createdAt = now, updatedAt = now))
            }
        }
    }

    private suspend fun requireAuthenticated(provider: TrackingProvider) {
        if (!provider.isAuthenticated()) throw TrackingSyncError.AuthenticationRequired(provider.id)
    }

    private suspend fun repairCurrentFloppyInstance() {
        val instance = routingMutex.withLock {
            registry.getProvider(TrackingProviderId.FLOPPY)?.currentDeliveryInstanceId()
        } ?: return
        operations.repairProviderInstanceTargets(TrackingProviderId.FLOPPY, instance)
    }

    private suspend fun <T> withProviderIo(
        providerId: TrackingProviderId,
        heldProvider: TrackingProviderId? = null,
        block: suspend () -> T,
    ): T = if (heldProvider == providerId) {
        block()
    } else {
        val lane = providerIoLanes.getValue(providerId)
        lane.lock()
        try {
            block()
        } finally {
            lane.unlock()
        }
    }
}

private suspend inline fun <T> resultOf(crossinline block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Throwable) {
    Result.failure(error)
}

