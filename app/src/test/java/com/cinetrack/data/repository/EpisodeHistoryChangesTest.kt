package com.cinetrack.data.repository

import org.junit.Assert.*
import org.junit.Test

class EpisodeHistoryChangesTest {
    @Test fun rebuildStartedAtE7RebasesAfterE7IsWatched() {
        val before = (1..6).map { Triple(42, 1, it) }.toSet()
        val after = before + Triple(42, 1, 7)
        val networkResult = mapOf(42 to 7, 99 to 3)
        val committed = networkResult.toMutableMap()
        changedEpisodeShows(before, after).forEach { show ->
            committed[show] = (1..8).first { Triple(show, 1, it) !in after }
        }
        assertEquals(8, committed[42])
        assertEquals(3, committed[99])
    }

    @Test fun undoAndRapidWatchesInvalidateOnlyAffectedShows() {
        val before = setOf(Triple(42, 1, 6), Triple(9, 1, 1), Triple(99, 2, 3))
        val after = setOf(Triple(42, 1, 6), Triple(42, 1, 7), Triple(42, 1, 8), Triple(99, 2, 3))
        assertEquals(setOf(42, 9), changedEpisodeShows(before, after))
        assertTrue(changedEpisodeShows(after, after).isEmpty())
    }
}
