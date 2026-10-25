package com.cinetrack.data.repository

import com.cinetrack.data.media.MediaRepository
import com.cinetrack.data.people.PeopleRepository
import com.cinetrack.domain.MediaCard
import com.cinetrack.domain.MediaType
import com.cinetrack.domain.PersonCard
import com.cinetrack.ui.detail.DetailStore
import kotlinx.coroutines.flow.Flow

class RepositoryDetailStore(
    private val media: MediaRepository,
    private val people: PeopleRepository,
    override val renumberedShows: Flow<Int>,
    private val fanart: com.cinetrack.data.media.FanartRepository? = null,
) : DetailStore {
    override suspend fun loadLogo(media: MediaCard) = fanart?.logo(media)
    override fun cachedLogo(media: MediaCard) = fanart?.cached(media)
    override suspend fun loadTagline(media: MediaCard) = this.media.loadTagline(media)
    override suspend fun loadSeasonDetails(show: MediaCard, number: Int) = media.loadSeasonDetails(show, number)
    override suspend fun loadDetails(media: MediaCard) = this.media.loadDetails(media)
    override suspend fun loadMedia(type: MediaType, id: Int) = media.loadMedia(type, id)
    override suspend fun loadPerson(person: PersonCard) = people.getPerson(person)
    override suspend fun loadRatings(media: MediaCard) = this.media.loadRatings(media)
    override suspend fun loadCast(media: MediaCard) = this.media.loadCast(media)
    override suspend fun loadCharacterPictures(media: MediaCard) = this.media.loadCharacterPictures(media)
    override suspend fun loadEpisodes(show: MediaCard, season: Int) = media.loadEpisodes(show, season)
    override suspend fun loadAllEpisodes(show: MediaCard) = media.loadAllEpisodes(show)
    override suspend fun loadEpisode(show: MediaCard, season: Int, number: Int) = media.loadEpisode(show, season, number)
    override suspend fun loadEpisodeCast(show: MediaCard, season: Int, number: Int) = media.loadEpisodeCast(show, season, number)
    override suspend fun loadCollection(media: MediaCard) = this.media.loadCollection(media)
    override suspend fun loadRecommendations(media: MediaCard) = this.media.loadRecommendations(media)
    override suspend fun loadTrailerKey(media: MediaCard) = this.media.loadTrailerKey(media)
}
