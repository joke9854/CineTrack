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
) {
    suspend fun current(): TrackingConfiguration = TrackingConfiguration(
        preferences.mainTrackingProvider.first(),
        preferences.secondaryTrackingProvider.first(),
    )

    suspend fun setProviders(main: TrackingProviderId?, secondary: TrackingProviderId?): TrackingConfiguration {
        val next = TrackingConfiguration(main, secondary)
        val previous = current()
        val removed = (setOfNotNull(previous.mainProvider, previous.secondaryProvider) -
            setOfNotNull(next.mainProvider, next.secondaryProvider))
        // Cancel first. If the process dies before DataStore is committed, the
        // retry sees the same old configuration and repeats this harmlessly.
        removed.forEach { operations.cancelProviderDeliveries(it) }
        preferences.setTrackingProviders(next.mainProvider, next.secondaryProvider)
        // A retry after the DataStore commit also repairs any remaining rows for
        // providers no longer configured.
        repair(next)
        return next
    }

    suspend fun repair(configuration: TrackingConfiguration = current()) {
        val configured = setOfNotNull(configuration.mainProvider, configuration.secondaryProvider)
        TrackingProviderId.entries.filterNot(configured::contains).forEach {
            operations.cancelProviderDeliveries(it)
        }
    }
}

