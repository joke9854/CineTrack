package com.cinetrack.data.repository

import com.cinetrack.data.media.MediaDataSource
import com.cinetrack.domain.DiscoverMovieFilters
import com.cinetrack.domain.MediaCard
import com.cinetrack.domain.MediaType
import com.cinetrack.domain.PersonCard
import com.cinetrack.domain.TimelineCard

/**
 * Temporary bridge while the proven TMDB/Room implementation is moved out of
 * CineTrackRepository. Keeping this bridge in the repository package prevents
 * the focused media layer from depending on the legacy façade type.
 */
class LegacyMediaDataSource(private val facade: CineTrackRepository) : MediaDataSource {
    override suspend fun refreshDiscover() = facade.refreshDiscover()
    override suspend fun loadDiscoverPage(railId: String, page: Int) = facade.loadDiscoverPage(railId, page)
    override suspend fun discoverMovies(filters: DiscoverMovieFilters) = facade.discoverMovies(filters)
    override suspend fun search(query: String) = facade.search(query)
    override suspend fun searchPeople(query: String) = facade.searchPeople(query)
    override suspend fun loadStreamingProviders(mediaType: MediaType, selectedRegion: String?, failOnError: Boolean) = facade.loadStreamingProviders(mediaType, selectedRegion, failOnError)
    override suspend fun loadSettingsStreamingProviders(region: String) = facade.loadSettingsStreamingProviders(region)
    override suspend fun loadTagline(media: MediaCard) = facade.loadTagline(media)
    override suspend fun loadSeasonDetails(show: MediaCard, number: Int) = facade.loadSeasonDetails(show, number)
    override suspend fun loadDetails(media: MediaCard) = facade.loadDetails(media)
    override suspend fun loadMedia(type: MediaType, id: Int) = facade.loadMedia(type, id)
    override suspend fun loadPerson(person: PersonCard) = facade.loadPerson(person)
    override suspend fun loadRatings(media: MediaCard) = facade.loadRatings(media)
    override suspend fun loadCast(media: MediaCard) = facade.loadCast(media)
    override suspend fun loadEpisodes(show: MediaCard, season: Int) = facade.loadEpisodes(show, season)
    override suspend fun loadAllEpisodes(show: MediaCard) = facade.loadAllEpisodes(show)
    override suspend fun loadEpisode(show: MediaCard, season: Int, number: Int) = facade.loadEpisode(show, season, number)
    override suspend fun loadEpisodeCast(show: MediaCard, season: Int, number: Int) = facade.loadEpisodeCast(show, season, number)
    override suspend fun loadCollection(media: MediaCard) = facade.loadCollection(media)
    override suspend fun loadRecommendations(media: MediaCard) = facade.loadRecommendations(media)
    override suspend fun loadTrailerKey(media: MediaCard) = facade.loadTrailerKey(media)
    override suspend fun loadViewingPeople(history: List<TimelineCard>) = facade.loadViewingPeople(history)
}

