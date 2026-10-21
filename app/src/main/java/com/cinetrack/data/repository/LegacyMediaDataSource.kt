package com.cinetrack.data.repository

import com.cinetrack.data.media.MediaDataSource
import com.cinetrack.domain.DiscoverMovieFilters
import com.cinetrack.domain.MediaCard
import com.cinetrack.domain.MediaType
import com.cinetrack.domain.PersonCard
import com.cinetrack.domain.TimelineCard

/**
 * Bridges [MediaDataSource] to [CatalogRepository] and [MediaMetadataRepository],
 * keeping the focused media layer independent of the legacy façade type.
 */
class LegacyMediaDataSource(
    private val catalog: CatalogRepository,
    private val metadata: MediaMetadataRepository,
) : MediaDataSource {
    override suspend fun refreshDiscover() = catalog.refreshDiscover()
    override suspend fun loadDiscoverPage(railId: String, page: Int) = catalog.loadDiscoverPage(railId, page)
    override suspend fun discoverMovies(filters: DiscoverMovieFilters) = catalog.discoverMovies(filters)
    override suspend fun discoverBatch(filters: DiscoverMovieFilters, batch: Int) = catalog.discoverBatch(filters, batch)
    override suspend fun search(query: String) = catalog.search(query)
    override suspend fun searchPeople(query: String) = catalog.searchPeople(query)
    override suspend fun loadStreamingProviders(mediaType: MediaType, selectedRegion: String?, failOnError: Boolean) = catalog.loadStreamingProviders(mediaType, selectedRegion, failOnError)
    override suspend fun loadSettingsStreamingProviders(region: String) = catalog.loadSettingsStreamingProviders(region)
    override suspend fun loadTagline(media: MediaCard) = metadata.loadTagline(media)
    override suspend fun loadSeasonDetails(show: MediaCard, number: Int) = metadata.loadSeasonDetails(show, number)
    override suspend fun loadDetails(media: MediaCard) = metadata.loadDetails(media)
    override suspend fun loadMedia(type: MediaType, id: Int) = metadata.loadMedia(type, id)
    override suspend fun loadPerson(person: PersonCard) = metadata.loadPerson(person)
    override suspend fun loadRatings(media: MediaCard) = metadata.loadRatings(media)
    override suspend fun loadCast(media: MediaCard) = metadata.loadCast(media)
    override suspend fun loadCharacterPictures(media: MediaCard) = metadata.loadCharacterPictures(media)
    override suspend fun loadEpisodes(show: MediaCard, season: Int) = metadata.loadEpisodes(show, season)
    override suspend fun loadAllEpisodes(show: MediaCard) = metadata.loadAllEpisodes(show)
    override suspend fun loadEpisode(show: MediaCard, season: Int, number: Int) = metadata.loadEpisode(show, season, number)
    override suspend fun loadEpisodeCast(show: MediaCard, season: Int, number: Int) = metadata.loadEpisodeCast(show, season, number)
    override suspend fun loadCollection(media: MediaCard) = metadata.loadCollection(media)
    override suspend fun loadRecommendations(media: MediaCard) = metadata.loadRecommendations(media)
    override suspend fun loadTrailerKey(media: MediaCard) = metadata.loadTrailerKey(media)
    override suspend fun loadViewingPeople(history: List<TimelineCard>) = metadata.loadViewingPeople(history)
}

