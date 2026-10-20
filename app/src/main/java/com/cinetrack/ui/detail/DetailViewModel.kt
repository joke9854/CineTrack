package com.cinetrack.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cinetrack.data.repository.BoundedLruCache
import com.cinetrack.domain.CharacterImage
import com.cinetrack.domain.EpisodeCard
import com.cinetrack.domain.MediaCard
import com.cinetrack.domain.MediaType
import com.cinetrack.domain.PersonCard
import com.cinetrack.domain.RatingScore
import com.cinetrack.domain.SeasonCard
import com.cinetrack.domain.SeasonDetails
import com.cinetrack.domain.withCharacterImages
import com.cinetrack.ui.settings.ContentChange
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The metadata the Detail, Episode and person pages read. No tracking writes. */
interface DetailStore {
    /** Shows whose episode numbers the one-time anime renumbering rewrote. */
    val renumberedShows: Flow<Int>
    suspend fun loadTagline(media: MediaCard): String?
    suspend fun loadSeasonDetails(show: MediaCard, number: Int): Result<SeasonDetails>
    suspend fun loadDetails(media: MediaCard): MediaCard
    suspend fun loadMedia(type: MediaType, id: Int): MediaCard?
    suspend fun loadPerson(person: PersonCard): PersonCard
    suspend fun loadRatings(media: MediaCard): List<RatingScore>
    suspend fun loadCast(media: MediaCard): List<PersonCard>
    suspend fun loadCharacterPictures(media: MediaCard): List<CharacterImage>
    suspend fun loadEpisodes(show: MediaCard, season: Int): List<EpisodeCard>
    suspend fun loadAllEpisodes(show: MediaCard): List<EpisodeCard>
    suspend fun loadEpisode(show: MediaCard, season: Int, number: Int): EpisodeCard?
    suspend fun loadEpisodeCast(show: MediaCard, season: Int, number: Int): List<PersonCard>
    suspend fun loadCollection(media: MediaCard): List<MediaCard>
    suspend fun loadRecommendations(media: MediaCard): List<MediaCard>
    suspend fun loadTrailerKey(media: MediaCard): String?
}

/**
 * Title metadata for the Detail and Episode pages, with the caches that let going back to a
 * page draw it at once. Activity-scoped. Watched writes stay on CineTrackViewModel, which
 * patches the shared [DetailEpisodeCache].
 */
class DetailViewModel(
    private val store: DetailStore,
    private val episodeCache: DetailEpisodeCache,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val castCache = BoundedLruCache<String, List<PersonCard>>(32)
    private val mediaCache = BoundedLruCache<String, MediaCard>(64)
    // Rows of a detail page, kept so going back to it shows the same page at once.
    private val collectionCache = BoundedLruCache<String, List<MediaCard>>(32)
    private val recommendationsCache = BoundedLruCache<String, List<MediaCard>>(32)
    // Titles opened from rows that are not in the library, so their page can draw at once.
    private val openedMediaCache = BoundedLruCache<String, MediaCard>(32)
    private val ratingsCache = BoundedLruCache<String, List<RatingScore>>(64)

    init {
        viewModelScope.launch {
            store.renumberedShows.collect { showId -> episodeCache.remove(showId) }
        }
    }

    fun cachedDetails(media: MediaCard): MediaCard? = mediaCache[media.stableKey]

    /**
     * Drops what this session cached for [media] and fetches it again (for a TV show, every
     * season). Returns the fresh title; for a show the caller refreshes Progress afterwards.
     */
    suspend fun refreshTitle(media: MediaCard): MediaCard {
        mediaCache.remove(media.stableKey)
        ratingsCache.remove(media.stableKey)
        castCache.remove(media.stableKey)
        collectionCache.remove(media.stableKey)
        recommendationsCache.remove(media.stableKey)
        if (media.type == MediaType.TV) episodeCache.remove(media.id)
        val fresh = loadDetails(media)
        if (media.type == MediaType.TV) {
            withContext(io) {
                fresh.seasons.map(SeasonCard::number).distinct().forEach { season -> store.loadEpisodes(fresh, season) }
            }
            loadAllEpisodes(fresh)
        }
        return fresh
    }
    fun cachedRatings(media: MediaCard): List<RatingScore> = ratingsCache[media.stableKey].orEmpty()
    fun cachedEpisodes(showId: Int): List<EpisodeCard> = episodeCache[showId].orEmpty()

    suspend fun loadTagline(media: MediaCard): String? = withContext(io) { store.loadTagline(media) }
    suspend fun loadSeasonDetails(show: MediaCard, number: Int): Result<SeasonDetails> =
        withContext(io) { store.loadSeasonDetails(show, number) }
    suspend fun loadDetails(media: MediaCard): MediaCard = mediaCache[media.stableKey]
        ?: withContext(io) { store.loadDetails(media) }.also { mediaCache[media.stableKey] = it }
    suspend fun loadMedia(type: MediaType, id: Int): MediaCard? = withContext(io) { store.loadMedia(type, id) }
    suspend fun loadPerson(person: PersonCard): PersonCard = withContext(io) { store.loadPerson(person) }
    suspend fun loadRatings(media: MediaCard): List<RatingScore> = ratingsCache[media.stableKey]
        ?.takeIf { it.isNotEmpty() }
        ?: withContext(io) { store.loadRatings(media) }.also { if (it.isNotEmpty()) ratingsCache[media.stableKey] = it }
    fun cachedCast(media: MediaCard): List<PersonCard> = castCache[media.stableKey].orEmpty()
    suspend fun loadCast(media: MediaCard): List<PersonCard> = castCache[media.stableKey]
        ?: withContext(io) { store.loadCast(media) }.also {
            if (it.isNotEmpty()) castCache[media.stableKey] = it
        }
    /**
     * Adds TVDB character pictures to an already shown cast; the cast is returned unchanged
     * without them. [remember] keeps the result as the title's cast (episode casts don't).
     */
    suspend fun withCharacterPictures(media: MediaCard, cast: List<PersonCard>, remember: Boolean = true): List<PersonCard> {
        if (cast.isEmpty() || cast.any { it.characterImageUrl != null }) return cast
        val pictures = withContext(io) { store.loadCharacterPictures(media) }
        return cast.withCharacterImages(pictures).also { if (remember && it !== cast) castCache[media.stableKey] = it }
    }
    suspend fun loadEpisodes(show: MediaCard, season: Int = 1): List<EpisodeCard> = withContext(io) { store.loadEpisodes(show, season) }
    suspend fun loadAllEpisodes(show: MediaCard): List<EpisodeCard> = episodeCache[show.id]
        ?.takeIf { it.isNotEmpty() }
        ?: withContext(io) { store.loadAllEpisodes(show) }.also { if (it.isNotEmpty()) episodeCache[show.id] = it }
    suspend fun loadEpisode(show: MediaCard, season: Int, number: Int): EpisodeCard? = withContext(io) { store.loadEpisode(show, season, number) }
    suspend fun loadEpisodeCast(show: MediaCard, season: Int, number: Int): List<PersonCard> = withContext(io) { store.loadEpisodeCast(show, season, number) }
    fun cachedCollection(media: MediaCard): List<MediaCard> = collectionCache[media.stableKey].orEmpty()
    fun cachedRecommendations(media: MediaCard): List<MediaCard> = recommendationsCache[media.stableKey].orEmpty()
    suspend fun loadCollection(media: MediaCard): List<MediaCard> = withContext(io) { store.loadCollection(media) }
        .also { if (it.isNotEmpty()) collectionCache[media.stableKey] = it }
    suspend fun loadRecommendations(media: MediaCard): List<MediaCard> = withContext(io) { store.loadRecommendations(media) }
        .also { if (it.isNotEmpty()) recommendationsCache[media.stableKey] = it }

    /** Remembers a title being opened, so its page can draw before it is loaded. */
    fun rememberOpened(media: MediaCard) {
        openedMediaCache[media.stableKey] = media
    }

    fun openedMedia(type: MediaType, id: Int): MediaCard? =
        openedMediaCache["${type.name}:$id"] ?: mediaCache["${type.name}:$id"]
    suspend fun loadTrailerKey(media: MediaCard): String? = withContext(io) { store.loadTrailerKey(media) }

    /** Clears the watched marks of a show's cached episodes (its history was removed). */
    fun forgetWatchedEpisodes(showId: Int) = episodeCache.forgetWatched(showId)

    /** Called once a content-setting write has finished; see ContentSettingsViewModel.changes. */
    fun onContentChanged(change: ContentChange) {
        when (change) {
            ContentChange.Providers, ContentChange.Region -> mediaCache.clear()
            ContentChange.Ratings, ContentChange.MdbListKey -> ratingsCache.clear()
            ContentChange.Language -> {
                mediaCache.clear()
                castCache.clear()
                episodeCache.clear()
            }
            else -> Unit
        }
    }

    class Factory(private val store: DetailStore, private val episodeCache: DetailEpisodeCache) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = DetailViewModel(store, episodeCache) as T
    }
}
