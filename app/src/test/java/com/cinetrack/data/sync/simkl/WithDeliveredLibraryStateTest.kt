package com.cinetrack.data.sync.simkl

import com.cinetrack.data.sync.MediaIds
import com.cinetrack.data.sync.TrackedEpisodeState
import com.cinetrack.data.sync.TrackedMovieState
import com.cinetrack.data.sync.TrackedShowState
import com.cinetrack.data.sync.TrackingSnapshot
import com.cinetrack.domain.LibraryStatus
import com.cinetrack.domain.MediaType
import org.junit.Assert.assertEquals
import org.junit.Test

class WithDeliveredLibraryStateTest {
    private fun show(id: Long, status: LibraryStatus) = TrackedShowState(MediaIds(tmdb = id), status)
    private fun episode(id: Long, number: Int) = TrackedEpisodeState(MediaIds(tmdb = id), 1, number, watched = true)

    private val baseline = TrackingSnapshot(
        movies = listOf(TrackedMovieState(MediaIds(tmdb = 7), LibraryStatus.COMPLETED, watched = true)),
        shows = listOf(show(1, LibraryStatus.WATCHING), show(2, LibraryStatus.WATCHING)),
        episodes = listOf(episode(1, 1), episode(2, 1)),
    )

    @Test
    fun `a delivered show removal leaves the baseline with its episodes`() {
        val updated = withDeliveredLibraryState(baseline, MediaType.TV, 1, LibraryStatus.NONE, watched = false)

        assertEquals(listOf(show(2, LibraryStatus.WATCHING)), updated.shows)
        assertEquals(listOf(episode(2, 1)), updated.episodes)
        assertEquals(baseline.movies, updated.movies)
    }

    @Test
    fun `a removed show is not brought back by a later incomplete merge`() {
        val updated = withDeliveredLibraryState(baseline, MediaType.TV, 1, LibraryStatus.NONE, watched = false)
        // An incomplete pull keeps every baseline entry it does not mention;
        // Simkl's lists no longer mention the removed show at all.
        val remote = (updated.shows.associateBy { it.ids.tmdb } + mapOf(2L to show(2, LibraryStatus.COMPLETED))).values

        assertEquals(setOf(show(2, LibraryStatus.COMPLETED)), remote.toSet())
    }

    @Test
    fun `a delivered status replaces the old one`() {
        val updated = withDeliveredLibraryState(baseline, MediaType.TV, 2, LibraryStatus.PAUSED, watched = false)

        assertEquals(setOf(show(1, LibraryStatus.WATCHING), show(2, LibraryStatus.PAUSED)), updated.shows.toSet())
        assertEquals(baseline.episodes, updated.episodes)
    }

    @Test
    fun `a delivered movie removal drops only that movie`() {
        val updated = withDeliveredLibraryState(baseline, MediaType.MOVIE, 7, LibraryStatus.NONE, watched = false)

        assertEquals(emptyList<TrackedMovieState>(), updated.movies)
        assertEquals(baseline.shows, updated.shows)
    }
}
