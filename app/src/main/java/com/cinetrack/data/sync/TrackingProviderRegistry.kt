package com.cinetrack.data.sync

import com.cinetrack.data.repository.SettingsRepository

interface TrackingProviderRegistry {
    fun getProvider(id: TrackingProviderId): TrackingProvider?
    suspend fun configuration(): TrackingConfiguration
    suspend fun getMainProvider(): TrackingProvider? = configuration().mainProvider?.let(::getProvider)
    suspend fun getSecondaryProvider(): TrackingProvider? = configuration().secondaryProvider?.let(::getProvider)
}

class DefaultTrackingProviderRegistry(
    providers: Collection<TrackingProvider>,
    private val settingsRepository: SettingsRepository,
) : TrackingProviderRegistry {
    private val providersById = providers.associateBy(TrackingProvider::id)

    override fun getProvider(id: TrackingProviderId): TrackingProvider? = providersById[id]
    override suspend fun configuration(): TrackingConfiguration = settingsRepository.trackingConfigurationNow()
}


