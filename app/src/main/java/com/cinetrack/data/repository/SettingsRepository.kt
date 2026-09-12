package com.cinetrack.data.repository

import com.cinetrack.data.sync.TrackingConfiguration
import com.cinetrack.data.sync.TrackingProviderId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

/** Provider and synchronization configuration, independent from provider credentials. */
class SettingsRepository(private val preferences: AppPreferences) {
    val trackingConfiguration: Flow<TrackingConfiguration> = combine(
        preferences.mainTrackingProvider,
        preferences.secondaryTrackingProvider,
    ) { main, secondary -> validConfiguration(main, secondary) }

    suspend fun trackingConfigurationNow(): TrackingConfiguration = trackingConfiguration.first()

    suspend fun setTrackingProviders(main: TrackingProviderId?, secondary: TrackingProviderId?) {
        val configuration = TrackingConfiguration(main, secondary)
        preferences.setTrackingProviders(configuration.mainProvider, configuration.secondaryProvider)
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


