package com.cinetrack.data.repository

import com.cinetrack.data.watchprovider.WatchProviderRepository
import com.cinetrack.ui.settings.ContentSettingsStore

/** The content pages' writes: preferences directly, metadata settings and keys through MetadataSettingsRepository. */
class RepositoryContentSettings(
    private val preferences: AppPreferences,
    private val metadata: MetadataSettingsRepository,
    private val watchProviders: WatchProviderRepository,
) : ContentSettingsStore {
    override val contentSettings get() = preferences.contentSettings
    override suspend fun setRatingSource(key: String, enabled: Boolean) = preferences.setRatingSource(key, enabled)
    override suspend fun setPreferredProviders(values: Set<String>) = preferences.setPreferredProviders(values)
    override suspend fun setProviderRegion(value: String) = preferences.setProviderRegion(value)
    override suspend fun setVisibleProviderTypes(values: Set<String>) = preferences.setVisibleProviderTypes(values)
    override suspend fun setHideAdultTitles(enabled: Boolean) = preferences.setHideAdultTitles(enabled)
    override suspend fun setContentRegions(regions: Set<String>) = preferences.setContentRegions(regions)
    override suspend fun setMetadataLanguage(value: String) = metadata.setMetadataLanguage(value)
    override suspend fun setMetadataRegion(value: String) = metadata.setMetadataRegion(value)
    override suspend fun setMetadataTimezone(value: String) = metadata.setMetadataTimezone(value)
    override suspend fun setAirDateSource(value: String) = metadata.setAirDateSource(value)
    override suspend fun availableProviders(region: String) = watchProviders.getAvailableProviders(region)
    override suspend fun verifyAndSetTmdbApiKey(value: String) = metadata.verifyAndSetTmdbApiKey(value)
    override suspend fun verifyAndSetMdbListApiKey(value: String) = metadata.verifyAndSetMdbListApiKey(value)
    override suspend fun verifyAndSetTraktClientId(value: String) = metadata.verifyAndSetTraktClientId(value)
}
