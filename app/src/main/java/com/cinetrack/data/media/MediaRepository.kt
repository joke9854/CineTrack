package com.cinetrack.data.media

import com.cinetrack.domain.DiscoverMovieFilters
import com.cinetrack.domain.DiscoverPage
import com.cinetrack.domain.EpisodeCard
import com.cinetrack.domain.MediaCard
import com.cinetrack.domain.MediaType
import com.cinetrack.domain.PersonCard
import com.cinetrack.domain.RatingScore
import com.cinetrack.domain.SeasonDetails
import com.cinetrack.domain.StreamingProvider
import com.cinetrack.domain.TimelineCard

/** Provider-agnostic catalogue/metadata API consumed by UI and ViewModels. */
interface MediaRepository {
    suspend fun refreshDiscover()
    suspend fun loadDiscoverPage(railId: String, page: Int): DiscoverPage
    suspend fun discoverMovies(filters: DiscoverMovieFilters): List<MediaCard>
    suspend fun search(query: String): List<MediaCard>
    suspend fun searchPeople(query: String): List<PersonCard>
    suspend fun loadStreamingProviders(mediaType: MediaType, selectedRegion: String? = null, failOnError: Boolean = false): List<StreamingProvider>
    suspend fun loadSettingsStreamingProviders(region: String): List<StreamingProvider>
    suspend fun loadTagline(media: MediaCard): String?
    suspend fun loadSeasonDetails(show: MediaCard, number: Int): Result<SeasonDetails>
    suspend fun loadDetails(media: MediaCard): MediaCard
    suspend fun loadMedia(type: MediaType, id: Int): MediaCard?
    suspend fun loadPerson(person: PersonCard): PersonCard
    suspend fun loadRatings(media: MediaCard): List<RatingScore>
    suspend fun loadCast(media: MediaCard): List<PersonCard>
    suspend fun loadEpisodes(show: MediaCard, season: Int = 1): List<EpisodeCard>
    suspend fun loadAllEpisodes(show: MediaCard): List<EpisodeCard>
    suspend fun loadEpisode(show: MediaCard, season: Int, number: Int): EpisodeCard?
    suspend fun loadEpisodeCast(show: MediaCard, season: Int, number: Int): List<PersonCard>
    suspend fun loadCollection(media: MediaCard): List<MediaCard>
    suspend fun loadRecommendations(media: MediaCard): List<MediaCard>
    suspend fun loadTrailerKey(media: MediaCard): String?
    suspend fun loadViewingPeople(history: List<TimelineCard>): Pair<List<PersonCard>, List<PersonCard>>
}

/** Internal transport/cache source used during the incremental migration. */
interface MediaDataSource : MediaRepository

/** Repository implementation that exposes only CineTrack domain models. */
class DefaultMediaRepository(private val source: MediaDataSource) : MediaRepository {
    override suspend fun refreshDiscover() { source.refreshDiscover() }
    override suspend fun loadDiscoverPage(railId: String, page: Int) = source.loadDiscoverPage(railId, page)
    override suspend fun discoverMovies(filters: DiscoverMovieFilters) = source.discoverMovies(filters)
    override suspend fun search(query: String) = source.search(query)
    override suspend fun searchPeople(query: String) = source.searchPeople(query)
    override suspend fun loadStreamingProviders(mediaType: MediaType, selectedRegion: String?, failOnError: Boolean) =
        source.loadStreamingProviders(mediaType, selectedRegion, failOnError)
    override suspend fun loadSettingsStreamingProviders(region: String) = source.loadSettingsStreamingProviders(region)
    override suspend fun loadTagline(media: MediaCard) = source.loadTagline(media)
    override suspend fun loadSeasonDetails(show: MediaCard, number: Int) = source.loadSeasonDetails(show, number)
    override suspend fun loadDetails(media: MediaCard) = source.loadDetails(media)
    override suspend fun loadMedia(type: MediaType, id: Int) = source.loadMedia(type, id)
    override suspend fun loadPerson(person: PersonCard) = source.loadPerson(person)
    override suspend fun loadRatings(media: MediaCard) = source.loadRatings(media)
    override suspend fun loadCast(media: MediaCard) = source.loadCast(media)
    override suspend fun loadEpisodes(show: MediaCard, season: Int) = source.loadEpisodes(show, season)
    override suspend fun loadAllEpisodes(show: MediaCard) = source.loadAllEpisodes(show)
    override suspend fun loadEpisode(show: MediaCard, season: Int, number: Int) = source.loadEpisode(show, season, number)
    override suspend fun loadEpisodeCast(show: MediaCard, season: Int, number: Int) = source.loadEpisodeCast(show, season, number)
    override suspend fun loadCollection(media: MediaCard) = source.loadCollection(media)
    override suspend fun loadRecommendations(media: MediaCard) = source.loadRecommendations(media)
    override suspend fun loadTrailerKey(media: MediaCard) = source.loadTrailerKey(media)
    override suspend fun loadViewingPeople(history: List<TimelineCard>) = source.loadViewingPeople(history)
}

