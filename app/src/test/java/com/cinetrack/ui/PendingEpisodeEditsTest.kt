package com.cinetrack.ui

import org.junit.Assert.*
import org.junit.Test

class PendingEpisodeEditsTest {
    private val e6 = Triple(42, 1, 6)
    private val e7 = Triple(42, 1, 7)

    @Test fun snapshotBeforeTapCannotBePublishedAsCurrent() {
        val edits = PendingEpisodeEdits()
        val readVersion = edits.version
        edits.record(e6, true)
        assertNotEquals(readVersion, edits.version)
        assertEquals(setOf(42), edits.unconfirmedShows(emptySet()))
        assertTrue(edits.unconfirmedShows(setOf(e6)).isEmpty())
    }

    @Test fun rapidSecondTapSurvivesSnapshotContainingOnlyFirstTap() {
        val edits = PendingEpisodeEdits()
        edits.record(e6, true)
        edits.record(e7, true)
        // Room has committed E06, but E07's transaction is still waiting.
        assertEquals(setOf(42), edits.unconfirmedShows(setOf(e6)))
        assertEquals(true, edits[e7])
        assertTrue(edits.unconfirmedShows(setOf(e6, e7)).isEmpty())
    }

    @Test fun undoWaitsForHistoryRemovalAndDoesNotBlockOtherShows() {
        val edits = PendingEpisodeEdits()
        edits.record(e7, false)
        edits.record(Triple(9, 2, 1), true)
        assertEquals(setOf(42), edits.unconfirmedShows(setOf(e7, Triple(9, 2, 1))))
        assertTrue(edits.unconfirmedShows(setOf(Triple(9, 2, 1))).isEmpty())
    }
}
