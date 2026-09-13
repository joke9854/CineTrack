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
        val configuration = providerRegistry.configuration()
        return providers.mapNotNull { providerId ->
            if (providerId != configuration.secondaryProvider) return@mapNotNull null
            val supported = providerRegistry.getProvider(providerId)?.capabilities?.supports(operation) == true
            SyncOperationDelivery(
                operationId = operation.id,
                operationVersion = operation.sourceVersion,
                providerId = providerId,
                required = supported,
                roleAtEnqueue = TrackingRole.SECONDARY,
                status = if (supported) DeliveryStatus.PENDING else DeliveryStatus.SKIPPED_UNSUPPORTED,
                createdAt = operation.sourceVersion,
                updatedAt = operation.sourceVersion,
            )
        }
    }

    /** Reads the configuration once and returns the current SECONDARY target. */
    internal suspend fun snapshotSecondaryUnlocked(operation: SyncOperation): SyncOperationDelivery? {
        val providerId = providerRegistry.configuration().secondaryProvider ?: return null
        val supported = providerRegistry.getProvider(providerId)?.capabilities?.supports(operation) == true
        return SyncOperationDelivery(
            operationId = operation.id,
            operationVersion = operation.sourceVersion,
            providerId = providerId,
            required = supported,
            roleAtEnqueue = TrackingRole.SECONDARY,
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
                add(
                    SyncOperationDelivery(
                        operationId = operation.id,
                        operationVersion = operation.sourceVersion,
                        providerId = provider,
                        required = true,
                        roleAtEnqueue = TrackingRole.MAIN,
                        createdAt = operation.sourceVersion,
                        updatedAt = operation.sourceVersion,
                    ),
                )
            }
            configuration.secondaryProvider?.let { providerId ->
                val supported = providerRegistry.getProvider(providerId)?.capabilities?.supports(operation) == true
                add(
                    SyncOperationDelivery(
                        operationId = operation.id,
                        operationVersion = operation.sourceVersion,
                        providerId = providerId,
                        required = supported,
                        roleAtEnqueue = TrackingRole.SECONDARY,
                        status = if (supported) DeliveryStatus.PENDING else DeliveryStatus.SKIPPED_UNSUPPORTED,
                        createdAt = operation.sourceVersion,
                        updatedAt = operation.sourceVersion,
                    ),
                )
            }
        }
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
    ) {
        routingMutex.withLock {
            val targets = durableQueue.snapshotForProvidersUnlocked(operation, providers)
            if (targets.isEmpty()) return@withLock
            operationRepository.enqueue(
                operations = listOf(operation),
                targets = targets,
                supersedeOperationIds = if (supersedeLogicalKey) setOf(operation.id) else emptySet(),
            )
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
