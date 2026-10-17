package com.cinetrack.data.sync.simkl

import com.cinetrack.data.sync.MediaIds
import com.cinetrack.data.sync.TrackedEpisodeState
import com.cinetrack.data.sync.TrackedMovieState
import com.cinetrack.data.sync.TrackedShowState
import com.cinetrack.data.sync.TrackingSnapshot
import com.cinetrack.domain.LibraryStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SimklRemovalByIdTest {
    @Test
    fun `a complete download decides on its own`() {
        assertTrue(removedFromSimklList(complete = true, remoteSimklIds = null, simklId = null))
    }

    @Test
    fun `with unresolved items a known missing Simkl id is a removal`() {
        assertTrue(removedFromSimklList(complete = false, remoteSimklIds = setOf(10, 11), simklId = 12))
    }

    @Test
    fun `an item Simkl still lists, even unresolved to TMDB, is kept`() {
        assertFalse(removedFromSimklList(complete = false, remoteSimklIds = setOf(10, 11), simklId = 11))
    }

    @Test
    fun `an item without a known Simkl id is kept`() {
        assertFalse(removedFromSimklList(complete = false, remoteSimklIds = setOf(10), simklId = null))
    }

    @Test
    fun `an incremental download never removes`() {
        assertFalse(removedFromSimklList(complete = false, remoteSimklIds = null, simklId = 12))
    }

    private val baseline = TrackingSnapshot(
        movies = listOf(
            TrackedMovieState(MediaIds(tmdb = 7, simkl = 70), LibraryStatus.PLAN_TO_WATCH, watched = false),
            TrackedMovieState(MediaIds(tmdb = 8), LibraryStatus.PLAN_TO_WATCH, watched = false),
        ),
        shows = listOf(
            TrackedShowState(MediaIds(tmdb = 1, simkl = 100), LibraryStatus.WATCHING),
            TrackedShowState(MediaIds(tmdb = 2, simkl = 200), LibraryStatus.WATCHING),
            TrackedShowState(MediaIds(tmdb = 3), LibraryStatus.WATCHING),
        ),
        episodes = listOf(
            TrackedEpisodeState(MediaIds(tmdb = 1), 1, 1, watched = true),
            TrackedEpisodeState(MediaIds(tmdb = 2), 1, 1, watched = true),
        ),
    )

    @Test
    fun `the baseline drops shows and movies Simkl no longer lists by id`() {
        val pruned = baseline.withoutAbsentSimklItems(remoteShowSimklIds = setOf(200), remoteMovieSimklIds = setOf())

        assertEquals(listOf(2L, 3L), pruned.shows.map { it.ids.tmdb })
        assertEquals(listOf(2L), pruned.episodes.map { it.showIds.tmdb })
        assertEquals(listOf(8L), pruned.movies.map { it.ids.tmdb })
    }

    @Test
    fun `without a full download the baseline is unchanged`() {
        assertSame(baseline, baseline.withoutAbsentSimklItems(null, null))
    }
}
