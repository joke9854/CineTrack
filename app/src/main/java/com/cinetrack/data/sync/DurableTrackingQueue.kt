package com.cinetrack.data.sync

import kotlinx.coroutines.sync.Mutex

/** Serializes configuration changes with local mutation target snapshots. */
class TrackingRoutingMutex {
    private val mutex = Mutex()

    suspend fun <T> withLock(block: suspend () -> T): T {
        mutex.lock()
        return try {
            block()
        } finally {
            mutex.unlock()
        }
    }
}

/**
 * Required target-snapshot dependency for local mutations. A mutation captures
 * this result once; later role changes never rewrite the provider set.
 */
class DurableTrackingQueue(
    private val providerRegistry: TrackingProviderRegistry,
    private val routingMutex: TrackingRoutingMutex = TrackingRoutingMutex(),
) {
    suspend fun snapshot(operation: SyncOperation): List<SyncOperationDelivery> =
        routingMutex.withLock { snapshotUnlocked(operation) }

    /**
     * Captures an explicit provider target set. This is used for remote-only
     * MAIN reconciliation mirrors: they must be delivered to SECONDARY only
     * and must never become a new MAIN intent.
     */
    suspend fun snapshotForProviders(
        operation: SyncOperation,
        providers: Set<TrackingProviderId>,
    ): List<SyncOperationDelivery> = routingMutex.withLock {
        snapshotForProvidersUnlocked(operation, providers)
    }

    internal suspend fun snapshotForProvidersUnlocked(
        operation: SyncOperation,
        providers: Set<TrackingProviderId>,
    ): List<SyncOperationDelivery> {
        return snapshotForProvidersUnlocked(listOf(operation), providers)
    }

    /** Captures a provider target snapshot for a bulk plan with one config and
     * one immutable provider-instance read per provider. */
    internal suspend fun snapshotForProvidersUnlocked(
        operations: List<SyncOperation>,
        providers: Set<TrackingProviderId>,
    ): List<SyncOperationDelivery> {
        if (operations.isEmpty() || providers.isEmpty()) return emptyList()
        val configuration = providerRegistry.configuration()
        return providers.flatMap { providerId ->
            if (providerId != configuration.secondaryProvider) return@flatMap emptyList()
            val provider = providerRegistry.getProvider(providerId)
            val instanceId = providerInstanceId(providerId)
            operations.map { operation ->
                val supported = provider?.capabilities?.supports(operation) == true
                SyncOperationDelivery(
                    operationId = operation.id,
                    operationVersion = operation.sourceVersion,
                    providerId = providerId,
                    required = supported,
                    roleAtEnqueue = TrackingRole.SECONDARY,
                    providerInstanceId = instanceId,
                    status = if (supported) DeliveryStatus.PENDING else DeliveryStatus.SKIPPED_UNSUPPORTED,
                    createdAt = operation.sourceVersion,
                    updatedAt = operation.sourceVersion,
                )
            }
        }
    }

    /** Reads the configuration once and returns the current SECONDARY target. */
    internal suspend fun snapshotSecondaryUnlocked(operation: SyncOperation): SyncOperationDelivery? {
        val providerId = providerRegistry.configuration().secondaryProvider ?: return null
        val supported = providerRegistry.getProvider(providerId)?.capabilities?.supports(operation) == true
        val instanceId = providerInstanceId(providerId)
        return SyncOperationDelivery(
            operationId = operation.id,
            operationVersion = operation.sourceVersion,
            providerId = providerId,
            required = supported,
            roleAtEnqueue = TrackingRole.SECONDARY,
            providerInstanceId = instanceId,
            status = if (supported) DeliveryStatus.PENDING else DeliveryStatus.SKIPPED_UNSUPPORTED,
            createdAt = operation.sourceVersion,
            updatedAt = operation.sourceVersion,
        )
    }

    /** Caller already holding the shared routing mutex (normally a Room transaction). */
    internal suspend fun snapshotUnlocked(operation: SyncOperation): List<SyncOperationDelivery> {
        val configuration = providerRegistry.configuration()
        return buildList {
            configuration.mainProvider?.let { provider ->
                val instanceId = providerInstanceId(provider)
                add(
                    SyncOperationDelivery(
                        operationId = operation.id,
                        operationVersion = operation.sourceVersion,
                        providerId = provider,
                        required = true,
                        roleAtEnqueue = TrackingRole.MAIN,
                        providerInstanceId = instanceId,
                        createdAt = operation.sourceVersion,
                        updatedAt = operation.sourceVersion,
                    ),
                )
            }
            configuration.secondaryProvider?.let { providerId ->
                val supported = providerRegistry.getProvider(providerId)?.capabilities?.supports(operation) == true
                val instanceId = providerInstanceId(providerId)
                add(
                    SyncOperationDelivery(
                        operationId = operation.id,
                        operationVersion = operation.sourceVersion,
                        providerId = providerId,
                        required = supported,
                        roleAtEnqueue = TrackingRole.SECONDARY,
                        providerInstanceId = instanceId,
                        status = if (supported) DeliveryStatus.PENDING else DeliveryStatus.SKIPPED_UNSUPPORTED,
                        createdAt = operation.sourceVersion,
                        updatedAt = operation.sourceVersion,
                    ),
                )
            }
        }
    }

    private suspend fun providerInstanceId(providerId: TrackingProviderId): String? {
        val instance = providerRegistry.getProvider(providerId)?.currentDeliveryInstanceId()
        if (providerId == TrackingProviderId.FLOPPY && instance.isNullOrBlank()) {
            throw IllegalStateException("Floppy is not connected as a valid delivery target")
        }
        return instance
    }
}

/**
 * Captures provider targets and persists the operation as one routing-critical
 * section. No network work is performed while the shared mutex is held.
 */
class DurableSyncOperationWriter(
    private val operationRepository: SyncOperationRepository,
    private val durableQueue: DurableTrackingQueue,
    private val routingMutex: TrackingRoutingMutex,
) {
    /** Repairs one current operation using an atomic provider-target snapshot. */
    suspend fun repairCurrentOperation(operation: SyncOperation) = routingMutex.withLock {
        repairCurrentOperationUnlocked(operation)
    }

    /** Caller already holds TrackingRoutingMutex. */
    internal suspend fun repairCurrentOperationUnlocked(operation: SyncOperation) {
        val targets = durableQueue.snapshotUnlocked(operation)
        operationRepository.enqueue(
            operations = listOf(operation),
            targets = targets,
        )
    }

    /**
     * Replaces one SECONDARY mirror atomically with its supersession.  The
     * routing snapshot is captured exactly once while the mutex is held; the
     * optional validation callback is also evaluated under that same lock.
     */
    suspend fun replaceSecondaryMirror(
        operation: SyncOperation,
        isCurrent: suspend () -> Boolean = { true },
    ): Boolean = routingMutex.withLock {
        val target = durableQueue.snapshotSecondaryUnlocked(operation) ?: return@withLock false
        if (!isCurrent()) return@withLock false
        operationRepository.replaceSecondaryMirror(operation, target)
        true
    }

    suspend fun enqueueForProviders(
        operation: SyncOperation,
        providers: Set<TrackingProviderId>,
        supersedeLogicalKey: Boolean = false,
    ) = enqueueForProviders(listOf(operation), providers, supersedeLogicalKey)

    /** Bulk bootstrap boundary. Routing/configuration is captured once, then
     * the repository persists the bounded chunk in one Room transaction. */
    suspend fun enqueueForProviders(
        operations: List<SyncOperation>,
        providers: Set<TrackingProviderId>,
        supersedeLogicalKey: Boolean = false,
    ) {
        if (operations.isEmpty() || providers.isEmpty()) return
        routingMutex.withLock {
            val targets = durableQueue.snapshotForProvidersUnlocked(operations, providers)
            if (targets.isEmpty()) return@withLock
            if (!supersedeLogicalKey && operations.all(SyncOperation::isManagedBootstrapOperation)) {
                operationRepository.enqueueBootstrapForProviders(operations, targets)
            } else {
                operationRepository.enqueue(
                    operations = operations,
                    targets = targets,
                    supersedeOperationIds = if (supersedeLogicalKey) operations.mapTo(linkedSetOf(), SyncOperation::id) else emptySet(),
                )
            }
        }
    }

    suspend fun enqueue(
        operation: SyncOperation,
        supersedeLogicalKey: Boolean = false,
        removeConflictId: String? = null,
    ) = enqueue(listOf(operation), supersedeLogicalKey, removeConflictId)

    suspend fun enqueue(
        operations: List<SyncOperation>,
        supersedeLogicalKey: Boolean = false,
        removeConflictId: String? = null,
    ) {
        if (operations.isEmpty()) return
        routingMutex.withLock {
            val targets = operations.flatMap { durableQueue.snapshotUnlocked(it) }
            operationRepository.enqueue(
                operations = operations,
                targets = targets,
                removeOperationId = removeConflictId,
                supersedeOperationIds = if (supersedeLogicalKey) {
                    operations.mapTo(linkedSetOf(), SyncOperation::id)
                } else {
                    emptySet()
                },
            )
        }
    }

    /**
     * Builds a replacement from current Room state while the routing mutex is
     * held. The repository revalidates and removes the conflict in its same
     * persistence transaction, so a failed replacement leaves the conflict
     * visible.
     */
    suspend fun enqueueFromCurrentState(
        removeConflictId: String,
        supersedeLogicalKey: Boolean = false,
        buildOperation: suspend () -> SyncOperation,
    ): SyncOperation = routingMutex.withLock {
        val operation = buildOperation()
        val targets = durableQueue.snapshotUnlocked(operation)
        operationRepository.enqueue(
            operations = listOf(operation),
            targets = targets,
            removeOperationId = removeConflictId,
            supersedeOperationIds = if (supersedeLogicalKey) {
                setOf(operation.id)
            } else {
                emptySet()
            },
        )
        operation
    }
}

