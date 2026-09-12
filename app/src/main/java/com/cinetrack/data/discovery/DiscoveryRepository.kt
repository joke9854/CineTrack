package com.cinetrack.data.discovery

import com.cinetrack.data.media.MediaRepository
import com.cinetrack.domain.DiscoverMovieFilters
import com.cinetrack.domain.DiscoverPage
import com.cinetrack.domain.MediaCard

/** Owns discover rails, pagination, filtering and search. */
interface DiscoveryRepository {
    suspend fun refresh()
    suspend fun page(railId: String, page: Int): DiscoverPage
    suspend fun discover(filters: DiscoverMovieFilters): List<MediaCard>
    suspend fun search(query: String): List<MediaCard>
}

class DefaultDiscoveryRepository(private val media: MediaRepository) : DiscoveryRepository {
    override suspend fun refresh() = media.refreshDiscover()
    override suspend fun page(railId: String, page: Int) = media.loadDiscoverPage(railId, page)
    override suspend fun discover(filters: DiscoverMovieFilters) = media.discoverMovies(filters)
    override suspend fun search(query: String) = media.search(query)
}

