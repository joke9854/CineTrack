package com.cinetrack.data.watchprovider

import com.cinetrack.data.media.MediaRepository
import com.cinetrack.domain.MediaType
import com.cinetrack.domain.StreamingProvider

interface WatchProviderRepository {
    suspend fun getWatchProviders(mediaType: MediaType, region: String? = null, failOnError: Boolean = false): List<StreamingProvider>
    suspend fun getAvailableProviders(region: String): List<StreamingProvider>
}

class DefaultWatchProviderRepository(private val media: MediaRepository) : WatchProviderRepository {
    override suspend fun getWatchProviders(mediaType: MediaType, region: String?, failOnError: Boolean) =
        media.loadStreamingProviders(mediaType, region, failOnError)
    override suspend fun getAvailableProviders(region: String) = media.loadSettingsStreamingProviders(region)
}

