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
) {
    private val fullSyncMutex = Mutex()
    /** Serializes all provider network traffic, including immediate pushes. */
    private val providerIoMutex = Mutex()

    /** Serializes connection activation with every provider network pass. */
    suspend fun <T> withProviderIoQuiesced(block: suspend () -> T): T {
        providerIoMutex.lock()
        return try {
            block()
        } finally {
            providerIoMutex.unlock()
        }
    }
    /** Exposes the pure policy for provider adapters and deterministic tests. */
    fun reconcile(
        local: LocalTrackingSnapshot,
        remote: TrackingSnapshot,
        provider: TrackingProviderId,
    ): ReconciliationResult = reconciler.reconcile(local, remote, provider)

    suspend fun sync(onProgress: (SyncProgress) -> Unit): Result<SyncCoordinatorOutcome> = fullSyncMutex.withLock {
        providerIoMutex.withLock {
        resultOf {
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
        val pending = operations.pending()
        var attemptedMain = emptyList<SyncOperation>()
        try {
            val secondary = configuration.secondaryProvider?.let(registry::getProvider)
            prepareDeliveryRows(pending, main, secondary, configuration)

            val mainPending = pendingFor(pending, main.id)
            val mainUnsupported = mainPending.firstOrNull { !main.capabilities.supports(it) }
            if (mainUnsupported != null) {
                val error = TrackingSyncError.UnsupportedOperation(main.id, mainUnsupported.type)
                val unsupported = mainPending.filter { it.id == mainUnsupported.id }
                operations.failDelivery(main.id, unsupported, error)
                operations.fail(unsupported, error)
                throw error
            }

            // Direction is structural: SECONDARY has no pull/sync call anywhere here.
            // A secondary failure is isolated; MAIN still performs its authoritative pass.
            if (secondary != null) {
                val secondaryPending = pendingFor(pending, secondary.id)
                val unsupported = secondaryPending.filterNot(secondary.capabilities::supports).mapTo(linkedSetOf(), SyncOperation::id)
                operations.skipUnsupported(secondary.id, secondaryPending.filter { it.id in unsupported })
                val deliverable = secondaryPending.filter { it.id !in unsupported }
                if (deliverable.isNotEmpty()) runCatching {
                    requireAuthenticated(secondary)
                    pushTo(secondary, deliverable)
                }.onSuccess { operations.acknowledge(secondary.id, deliverable) }
                    .onFailure { error ->
                        operations.failDelivery(secondary.id, deliverable, error)
                        operations.fail(deliverable, error)
                    }
            }

            requireAuthenticated(main)
            attemptedMain = mainPending
            val outcome = main.syncBidirectionally(mainPending, onProgress)
            // The provider only attempted the current MAIN delivery set. Older
            // generations that are already ACKNOWLEDGED (for example after a
            // partial SECONDARY failure) must not be required in this response.
            // Keep the provider identity captured at the start of this pass.
            // Configuration may change while the network request is running;
            // its result must never be attributed to the new MAIN provider.
            acknowledge(main.id, mainPending, outcome.acknowledgedOperationIds, outcome.deferredOperationIds)
            // A retry may have had only SECONDARY work left after MAIN was
            // acknowledged by an earlier attempt. Re-evaluate the complete
            // operation against every persisted delivery, not just this pass.
            operations.completeReady(pending)
            SyncCoordinatorOutcome(outcome.itemsChanged, outcome.report)
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            operations.failDelivery(main.id, attemptedMain, error)
            operations.fail(attemptedMain, error)
            throw error
        }
        }
        }
    }

    suspend fun pushPending(operationIds: Set<String>? = null): Result<Unit> = providerIoMutex.withLock {
        pushPendingWhileProviderIoQuiesced(operationIds)
    }

    /**
     * Dispatches pending work while the caller already owns providerIoMutex.
     * Connection activation uses this boundary so it can keep activation,
     * bootstrap and the first queue pass atomic without recursively locking
     * the non-reentrant provider mutex.
     */
    internal suspend fun pushPendingWhileProviderIoQuiesced(operationIds: Set<String>? = null): Result<Unit> = resultOf {
        val pending = operations.pending(operationIds)
        if (pending.isEmpty()) return@resultOf Unit
        val configuration = registry.configuration()
        val main = configuration.mainProvider?.let(registry::getProvider)
        val secondary = configuration.secondaryProvider?.let(registry::getProvider)
        val targets = listOfNotNull(main, secondary).distinctBy(TrackingProvider::id)
        if (targets.isEmpty()) throw IllegalStateException("No tracking provider is configured")
        prepareDeliveryRows(pending, main, secondary, configuration)
        var secondaryFailure: Throwable? = null
        targets.forEach { provider ->
            val providerPending = pendingFor(pending, provider.id)
            val unsupported = providerPending.filterNot(provider.capabilities::supports).mapTo(linkedSetOf(), SyncOperation::id)
            if (provider.id == configuration.mainProvider && unsupported.isNotEmpty()) {
                val error = TrackingSyncError.UnsupportedOperation(provider.id, providerPending.first { it.id in unsupported }.type)
                operations.failDelivery(provider.id, unsupported, error)
                operations.fail(providerPending.filter { it.id in unsupported }, error)
                throw error
            }
            operations.skipUnsupported(provider.id, providerPending.filter { it.id in unsupported })
            val deliverable = providerPending.filter { it.id !in unsupported }
            if (deliverable.isEmpty()) return@forEach
            try {
                requireAuthenticated(provider)
                pushTo(provider, deliverable)
                operations.acknowledge(provider.id, deliverable)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                operations.failDelivery(provider.id, deliverable, error)
                operations.fail(deliverable, error)
                if (provider.id == configuration.mainProvider) throw error
                secondaryFailure = error
            }
        }
        secondaryFailure?.let { throw it }
        operations.completeReady(pending)
    }

    suspend fun retry(operationId: String): Result<Unit> = pushPending(setOf(operationId))

    suspend fun isMainProviderConnected(): Boolean {
        val provider = registry.getMainProvider() ?: return false
        return provider.isAuthenticated()
    }

    private suspend fun pushTo(provider: TrackingProvider, pending: List<SyncOperation>) {
        val unsupported = pending.firstOrNull { !provider.capabilities.supports(it) }
        if (unsupported != null) throw TrackingSyncError.UnsupportedOperation(provider.id, unsupported.type)
        val completed = provider.push(pending).completedOperationIds
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
}

private suspend inline fun <T> resultOf(crossinline block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Throwable) {
    Result.failure(error)
}
