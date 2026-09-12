package com.cinetrack.data.sync

import com.cinetrack.data.repository.AppPreferences
import kotlinx.coroutines.flow.first

/**
 * Coordinates provider-role changes across DataStore and the durable Room
 * queue. DataStore and Room cannot share a transaction, so every transition is
 * deliberately idempotent and safe to repeat after process death.
 */
class TrackingConfigurationService(
    private val preferences: AppPreferences,
    private val operations: SyncOperationRepository,
    private val routingMutex: TrackingRoutingMutex = TrackingRoutingMutex(),
) {
    suspend fun current(): TrackingConfiguration = TrackingConfiguration(
        preferences.mainTrackingProvider.first(),
        preferences.secondaryTrackingProvider.first(),
    )

    suspend fun setProviders(main: TrackingProviderId?, secondary: TrackingProviderId?): TrackingConfiguration {
        val next = TrackingConfiguration(main, secondary)
        routingMutex.withLock {
            val previous = current()
            val removed = (setOfNotNull(previous.mainProvider, previous.secondaryProvider) -
                setOfNotNull(next.mainProvider, next.secondaryProvider))
            // Persist first. If the process dies before cancellation, startup
            // repair sees the committed configuration and finishes the transition.
            preferences.setTrackingProviders(next.mainProvider, next.secondaryProvider)
            removed.forEach { operations.cancelProviderDeliveries(it) }
            val pending = operations.pending()
            operations.completeReady(pending)
        }
        return next
    }

    suspend fun repair(configuration: TrackingConfiguration? = null) = routingMutex.withLock {
        val resolved = configuration ?: current()
        val configured = setOfNotNull(resolved.mainProvider, resolved.secondaryProvider)
        TrackingProviderId.entries.filterNot(configured::contains).forEach {
            operations.cancelProviderDeliveries(it)
        }
        operations.completeReady(operations.pending())
    }
}

