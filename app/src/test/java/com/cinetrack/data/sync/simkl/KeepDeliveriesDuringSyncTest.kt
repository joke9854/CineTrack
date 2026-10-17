package com.cinetrack.data.sync.simkl

import com.cinetrack.data.sync.MediaIds
import com.cinetrack.data.sync.TrackedEpisodeState
import com.cinetrack.data.sync.TrackedShowState
import com.cinetrack.data.sync.TrackingSnapshot
import com.cinetrack.domain.LibraryStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class KeepDeliveriesDuringSyncTest {
    private fun show(id: Long, status: LibraryStatus) = TrackedShowState(MediaIds(tmdb = id), status)
    private fun episode(id: Long, number: Int) = TrackedEpisodeState(MediaIds(tmdb = id), 1, number, watched = true)

    private val start = TrackingSnapshot(
        shows = listOf(show(1, LibraryStatus.WATCHING), show(2, LibraryStatus.WATCHING)),
        episodes = listOf(episode(1, 1), episode(2, 1)),
    )

    @Test
    fun `a removal Simkl accepted during the sync is not written back`() {
        // The sync's result still has show 1 from the baseline it read at the start.
        val advanced = start.copy(shows = listOf(show(1, LibraryStatus.WATCHING), show(2, LibraryStatus.COMPLETED)))
        // Meanwhile the removal of show 1 was delivered and recorded.
        val stored = start.copy(shows = listOf(show(2, LibraryStatus.WATCHING)), episodes = listOf(episode(2, 1)))

        val saved = keepDeliveriesDuringSync(start, stored, advanced)

        assertEquals(listOf(show(2, LibraryStatus.COMPLETED)), saved.shows)
        assertEquals(listOf(episode(2, 1)), saved.episodes)
    }

    @Test
    fun `a status delivered during the sync replaces the sync's value`() {
        val stored = start.copy(shows = listOf(show(1, LibraryStatus.PAUSED), show(2, LibraryStatus.WATCHING)))

        val saved = keepDeliveriesDuringSync(start, stored, start)

        assertEquals(setOf(show(1, LibraryStatus.PAUSED), show(2, LibraryStatus.WATCHING)), saved.shows.toSet())
    }

    @Test
    fun `without deliveries the sync's result is saved as is`() {
        val advanced = start.copy(shows = listOf(show(2, LibraryStatus.DROPPED)))
        assertSame(advanced, keepDeliveriesDuringSync(start, start, advanced))
        assertSame(advanced, keepDeliveriesDuringSync(start, null, advanced))
    }
}
