package com.cinetrack.ui.detail

import com.cinetrack.domain.CharacterImage
import com.cinetrack.domain.EpisodeCard
import com.cinetrack.domain.MediaCard
import com.cinetrack.domain.MediaType
import com.cinetrack.domain.PersonCard
import com.cinetrack.domain.RatingScore
import com.cinetrack.domain.SeasonCard
import com.cinetrack.domain.SeasonDetails
import com.cinetrack.ui.settings.ContentChange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DetailViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private val show = MediaCard(id = 7, type = MediaType.TV, title = "Show")

    private fun episode(season: Int, number: Int, watched: Boolean = false) =
        EpisodeCard(id = season * 100 + number, showId = show.id, season = season, number = number, title = "E$number", overview = "", airDate = null, watched = watched)

    private class FakeStore : DetailStore {
        override val renumberedShows = MutableSharedFlow<Int>(extraBufferCapacity = 4)
        val calls = mutableMapOf<String, Int>()
        var seasons = listOf(SeasonCard(number = 1, title = "S1", episodeCount = 2), SeasonCard(number = 2, title = "S2", episodeCount = 2))
        var allEpisodes: List<EpisodeCard> = emptyList()
        var pictures: List<CharacterImage> = emptyList()
        private fun count(name: String) { calls[name] = (calls[name] ?: 0) + 1 }
        val seasonLoads = mutableListOf<Int>()
        override suspend fun loadTagline(media: MediaCard): String? = null
        override suspend fun loadSeasonDetails(show: MediaCard, number: Int) = Result.failure<SeasonDetails>(UnsupportedOperationException())
        override suspend fun loadDetails(media: MediaCard): MediaCard { count("details"); return media.copy(seasons = seasons) }
        override suspend fun loadMedia(type: MediaType, id: Int): MediaCard? = null
        override suspend fun loadPerson(person: PersonCard) = person
        override suspend fun loadRatings(media: MediaCard): List<RatingScore> { count("ratings"); return emptyList() }
        override suspend fun loadCast(media: MediaCard): List<PersonCard> { count("cast"); return listOf(PersonCard(id = 1, name = "Actor", role = "Hero", isCastMember = true, character = "Hero")) }
        override suspend fun loadCharacterPictures(media: MediaCard) = pictures
        override suspend fun loadEpisodes(show: MediaCard, season: Int): List<EpisodeCard> { seasonLoads += season; return emptyList() }
        override suspend fun loadAllEpisodes(show: MediaCard): List<EpisodeCard> { count("all"); return allEpisodes }
        override suspend fun loadEpisode(show: MediaCard, season: Int, number: Int): EpisodeCard? = null
        override suspend fun loadEpisodeCast(show: MediaCard, season: Int, number: Int): List<PersonCard> = emptyList()
        override suspend fun loadCollection(media: MediaCard): List<MediaCard> = emptyList()
        override suspend fun loadRecommendations(media: MediaCard): List<MediaCard> = emptyList()
        override suspend fun loadTrailerKey(media: MediaCard): String? = null
    }

    @Test
    fun detailsAndCastAreCachedUntilTheTitleIsRefreshed() = runTest(dispatcher) {
        val store = FakeStore()
        val viewModel = DetailViewModel(store, DetailEpisodeCache(), dispatcher)

        viewModel.loadDetails(show)
        viewModel.loadDetails(show)
        viewModel.loadCast(show)
        viewModel.loadCast(show)
        assertEquals(1, store.calls["details"])
        assertEquals(1, store.calls["cast"])
        assertEquals(show.id, viewModel.cachedDetails(show)?.id)

        val fresh = viewModel.refreshTitle(show)
        assertEquals(2, store.calls["details"])
        // A show's refresh fetches every season, then its whole episode list.
        assertEquals(listOf(1, 2), store.seasonLoads)
        assertEquals(1, store.calls["all"])
        assertEquals(listOf(1, 2), fresh.seasons.map { it.number })
        assertTrue(viewModel.cachedCast(show).isEmpty())
    }

    @Test
    fun emptyEpisodeListsAreNotCachedAndWatchedPatchesShowThroughTheSharedCache() = runTest(dispatcher) {
        val store = FakeStore()
        val cache = DetailEpisodeCache()
        val viewModel = DetailViewModel(store, cache, dispatcher)

        viewModel.loadAllEpisodes(show)
        store.allEpisodes = listOf(episode(1, 1), episode(1, 2))
        viewModel.loadAllEpisodes(show)
        viewModel.loadAllEpisodes(show)
        assertEquals(2, store.calls["all"])

        // CineTrackViewModel patches the same instance when a watched write starts.
        cache.markWatched(show.id, setOf(1 to 2), watched = true)
        assertEquals(listOf(false, true), viewModel.cachedEpisodes(show.id).map { it.watched })

        viewModel.forgetWatchedEpisodes(show.id)
        assertFalse(viewModel.cachedEpisodes(show.id).any { it.watched })
    }

    @Test
    fun renumberingAndALanguageChangeDropTheEpisodeList() = runTest(dispatcher) {
        val store = FakeStore()
        val cache = DetailEpisodeCache().apply { this[show.id] = listOf(episode(1, 1)) }
        val viewModel = DetailViewModel(store, cache, dispatcher)

        store.renumberedShows.emit(show.id)
        assertTrue(viewModel.cachedEpisodes(show.id).isEmpty())

        cache[show.id] = listOf(episode(1, 1))
        viewModel.loadDetails(show)
        viewModel.onContentChanged(ContentChange.Ratings)
        assertEquals(1, viewModel.cachedEpisodes(show.id).size)
        viewModel.onContentChanged(ContentChange.Language)
        assertTrue(viewModel.cachedEpisodes(show.id).isEmpty())
        assertEquals(null, viewModel.cachedDetails(show))
    }

    @Test
    fun characterPicturesAreRememberedOnlyForTheTitleCast() = runTest(dispatcher) {
        val store = FakeStore().apply { pictures = listOf(CharacterImage(character = "Hero", actor = "Actor", imageUrl = "https://img/hero.jpg")) }
        val viewModel = DetailViewModel(store, DetailEpisodeCache(), dispatcher)
        val cast = viewModel.loadCast(show)

        viewModel.withCharacterPictures(show, cast, remember = false)
        assertEquals(null, viewModel.cachedCast(show).single().characterImageUrl)

        viewModel.withCharacterPictures(show, cast)
        assertEquals("https://img/hero.jpg", viewModel.cachedCast(show).single().characterImageUrl)
    }
}
