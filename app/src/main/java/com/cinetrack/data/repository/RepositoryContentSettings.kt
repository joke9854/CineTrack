package com.cinetrack.data.repository

import com.cinetrack.data.watchprovider.WatchProviderRepository
import com.cinetrack.ui.settings.ContentSettingsStore

/** The content pages' writes: preferences directly, metadata through the repository's cache handling. */
class RepositoryContentSettings(
    private val repository: CineTrackRepository,
    private val watchProviders: WatchProviderRepository,
) : ContentSettingsStore {
    private val preferences get() = repository.preferences
    override val contentSettings get() = preferences.contentSettings
    override suspend fun setRatingSource(key: String, enabled: Boolean) = preferences.setRatingSource(key, enabled)
    override suspend fun setPreferredProviders(values: Set<String>) = preferences.setPreferredProviders(values)
    override suspend fun setProviderRegion(value: String) = preferences.setProviderRegion(value)
    override suspend fun setVisibleProviderTypes(values: Set<String>) = preferences.setVisibleProviderTypes(values)
    override suspend fun setHideAdultTitles(enabled: Boolean) = preferences.setHideAdultTitles(enabled)
    override suspend fun setContentRegions(regions: Set<String>) = preferences.setContentRegions(regions)
    override suspend fun setMetadataLanguage(value: String) = repository.setMetadataLanguage(value)
    override suspend fun setMetadataRegion(value: String) = repository.setMetadataRegion(value)
    override suspend fun setMetadataTimezone(value: String) = repository.setMetadataTimezone(value)
    override suspend fun setAirDateSource(value: String) = repository.setAirDateSource(value)
    override suspend fun availableProviders(region: String) = watchProviders.getAvailableProviders(region)
}
