package com.cinetrack.data.work

import com.cinetrack.domain.LibraryArtworkRefreshProgress
import com.cinetrack.domain.LibraryArtworkRefreshStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryArtworkRefreshProgressTest {
    @Test
    fun processedCountIsTerminalOutcomeCount() {
        var progress = LibraryArtworkRefreshProgress(stage = LibraryArtworkRefreshStage.REFRESHING, total = 100)
        progress = progress.copy(processed = 25, changed = 4, unchanged = 19, failed = 2)
        assertEquals(25, progress.changed + progress.unchanged + progress.failed)
        assertEquals(100, progress.total)
        assertTrue(progress.processed < progress.total)
    }

    @Test
    fun partialFailuresHaveExplicitCompletionStage() {
        val progress = LibraryArtworkRefreshProgress(
            stage = LibraryArtworkRefreshStage.COMPLETE_WITH_ERRORS,
            processed = 100,
            total = 100,
            changed = 42,
            unchanged = 51,
            failed = 7,
        )
        assertEquals(progress.total, progress.processed)
        assertEquals(7, progress.failed)
        assertEquals(LibraryArtworkRefreshStage.COMPLETE_WITH_ERRORS, progress.stage)
    }
}

