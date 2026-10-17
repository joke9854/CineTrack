package com.cinetrack.data.sync.simkl

import com.cinetrack.data.local.UserMediaStateEntity
import com.cinetrack.data.local.WatchHistoryEntity
import com.cinetrack.domain.LibraryStatus
import com.cinetrack.domain.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SimklMergePlanTest {
    private fun state(type: MediaType, id: Int, status: LibraryStatus, watched: Boolean = false, simklId: Long? = null) =
        UserMediaStateEntity(type.name, id, status.name, watched, simklId, updatedAt = 1L)

    private fun episode(show: Int, season: Int, number: Int, at: String) =
        WatchHistoryEntity(mediaType = MediaType.TV.name, mediaId = show, season = season, episodeNumber = number, watchedAt = at)

    private fun movie(id: Int, at: String) = WatchHistoryEntity(mediaType = MediaType.MOVIE.name, mediaId = id, watchedAt = at)

    private val emptyRemote = SimklRemoteLibrary(emptySet(), emptySet(), emptySet(), emptySet())

    @Test
    fun `only titles Simkl lacks are added to its lists`() {
        val plan = planSimklMerge(
            localStates = listOf(
                state(MediaType.TV, 1, LibraryStatus.WATCHING),
                state(MediaType.TV, 2, LibraryStatus.COMPLETED),
                state(MediaType.MOVIE, 3, LibraryStatus.PLAN_TO_WATCH),
                state(MediaType.MOVIE, 4, LibraryStatus.NONE),
                state(MediaType.TV, 5, LibraryStatus.DROPPED, simklId = 99),
            ),
            localHistory = emptyList(),
            remote = emptyRemote.copy(keys = setOf("TV:2"), simklIds = setOf(99L)),
        )
        assertEquals(2, plan.titles)
        val request = plan.listRequests.single()
        assertEquals(listOf("1" to "watching"), request.shows.map { it.ids.tmdb to it.to })
        assertEquals(listOf("3" to "plantowatch"), request.movies.map { it.ids.tmdb to it.to })
    }

    @Test
    fun `episodes Simkl lacks are sent with their exact local time`() {
        val plan = planSimklMerge(
            localStates = emptyList(),
            localHistory = listOf(
                episode(10, 1, 1, "2024-01-01T20:00:00Z"),
                episode(10, 1, 2, "2024-01-02T20:00:00.123Z"),
                episode(10, 2, 1, "2024-03-01T21:15:00Z"),
            ),
            remote = emptyRemote.copy(watchedEpisodes = setOf(Triple(10, 1, 1))),
        )
        assertEquals(2, plan.episodes)
        val show = plan.historyRequests.single().shows.single()
        assertEquals("10", show.ids.tmdb)
        assertEquals(
            listOf(Triple(1, 2, "2024-01-02T20:00:00.123Z"), Triple(2, 1, "2024-03-01T21:15:00Z")),
            show.seasons.flatMap { season -> season.episodes.map { Triple(season.number, it.number, it.watchedAt) } },
        )
    }

    @Test
    fun `a rewatched episode is sent once with its latest time`() {
        val plan = planSimklMerge(
            localStates = emptyList(),
            localHistory = listOf(
                episode(10, 1, 1, "2025-05-01T10:00:00Z"),
                episode(10, 1, 1, "2023-01-01T10:00:00Z"),
            ),
            remote = emptyRemote,
        )
        assertEquals("2025-05-01T10:00:00Z", plan.historyRequests.single().shows.single().seasons.single().episodes.single().watchedAt)
    }

    @Test
    fun `movies Simkl has not marked watched are sent with their time`() {
        val plan = planSimklMerge(
            localStates = emptyList(),
            localHistory = listOf(movie(7, "2022-02-02T02:02:02Z"), movie(8, "2022-03-03T03:03:03Z")),
            remote = emptyRemote.copy(watchedMovies = setOf(8)),
        )
        assertEquals(1, plan.movies)
        val sent = plan.historyRequests.single().movies.single()
        assertEquals("7" to "2022-02-02T02:02:02Z", sent.ids.tmdb to sent.watchedAt)
    }

    @Test
    fun `history batches are bounded by episode count`() {
        val history = (1..3).flatMap { show -> (1..4).map { episode(show, 1, it, "2024-01-0${it}T00:00:00Z") } }
        val plan = planSimklMerge(emptyList(), history, emptyRemote, episodesPerRequest = 5)
        assertEquals(3, plan.historyRequests.size)
        assertEquals(12, plan.episodes)
    }

    @Test
    fun `an account that already has everything needs no upload`() {
        val plan = planSimklMerge(
            localStates = listOf(state(MediaType.TV, 1, LibraryStatus.WATCHING), state(MediaType.MOVIE, 2, LibraryStatus.COMPLETED, watched = true)),
            localHistory = listOf(episode(1, 1, 1, "2024-01-01T00:00:00Z"), movie(2, "2024-01-01T00:00:00Z")),
            remote = SimklRemoteLibrary(setOf("TV:1", "MOVIE:2"), emptySet(), setOf(2), setOf(Triple(1, 1, 1))),
        )
        assertTrue(plan.isEmpty)
    }

    @Test
    fun `a merged state keeps Simkl's status but never loses local data`() {
        val remote = state(MediaType.MOVIE, 1, LibraryStatus.WATCHING, watched = false)
        val local = state(MediaType.MOVIE, 1, LibraryStatus.COMPLETED, watched = true)
        val merged = remote.mergedWithLocal(local)
        assertEquals(LibraryStatus.WATCHING.name, merged.status)
        assertTrue(merged.watched)
        assertEquals(LibraryStatus.COMPLETED.name, state(MediaType.MOVIE, 1, LibraryStatus.NONE).mergedWithLocal(local).status)
        assertEquals(remote, remote.mergedWithLocal(null))
    }
}
