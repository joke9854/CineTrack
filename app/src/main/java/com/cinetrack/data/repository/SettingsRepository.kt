package com.cinetrack.data.repository

import com.cinetrack.data.sync.TrackingConfiguration
import com.cinetrack.data.sync.TrackingConfigurationService
import com.cinetrack.data.sync.TrackingProviderId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

/** Provider and synchronization configuration, independent from provider credentials. */
class SettingsRepository(
    private val preferences: AppPreferences,
    private val trackingConfigurationService: TrackingConfigurationService,
) {
    val trackingConfiguration: Flow<TrackingConfiguration> = combine(
        preferences.mainTrackingProvider,
        preferences.secondaryTrackingProvider,
    ) { main, secondary -> validConfiguration(main, secondary) }

    suspend fun trackingConfigurationNow(): TrackingConfiguration = trackingConfiguration.first()

    suspend fun setTrackingProviders(main: TrackingProviderId?, secondary: TrackingProviderId?) {
        trackingConfigurationService.setProviders(main, secondary)
    }

    private fun validConfiguration(
        main: TrackingProviderId?,
        secondary: TrackingProviderId?,
    ): TrackingConfiguration = if (main != null && main == secondary) {
        TrackingConfiguration(mainProvider = main, secondaryProvider = null)
    } else {
        TrackingConfiguration(mainProvider = main, secondaryProvider = secondary)
    }
}

