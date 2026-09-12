package com.cinetrack.data.sync

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serializes configuration changes with local mutation target snapshots. */
class TrackingRoutingMutex {
    private val mutex = Mutex()

    suspend fun <T> withLock(block: suspend () -> T): T = mutex.withLock(block)
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

