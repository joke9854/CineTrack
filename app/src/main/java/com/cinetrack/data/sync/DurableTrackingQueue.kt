package com.cinetrack.data.sync

/**
 * Required target-snapshot dependency for local mutations. A mutation captures
 * this result once; later role changes never rewrite the provider set.
 */
class DurableTrackingQueue(
    private val providerRegistry: TrackingProviderRegistry,
) {
    suspend fun snapshot(operation: SyncOperation): List<SyncOperationDelivery> {
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

