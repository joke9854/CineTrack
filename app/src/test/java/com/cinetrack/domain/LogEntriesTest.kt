package com.cinetrack.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class LogEntriesTest {
    @Test
    fun errorLineIsSplitIntoTimeHeadlineAndDetail() {
        val entry = parseLogLine(
            "2026-10-10T11:23:00.138Z  Floppy bootstrap stopped: run=fc9ebe98-b09a-4737-97c5-4426c9fe9b4a processed=9072 total=9073 error=Request timed out",
        )

        assertEquals(Instant.parse("2026-10-10T11:23:00.138Z"), entry.at)
        assertEquals(LogSeverity.ERROR, entry.severity)
        assertEquals("Floppy bootstrap stopped", entry.summary)
        assertEquals("Floppy bootstrap stopped: processed=9072 total=9073 error=Request timed out", entry.detail)
    }

    @Test
    fun unplacedItemsAreWarningsAndProgressIsInfo() {
        assertEquals(
            LogSeverity.WARNING,
            parseLogLine("2026-10-10T14:09:00.071Z  Floppy bootstrap item closed: TV:8592 S00E12 has no Floppy counterpart (no_unique_tmdb_match)").severity,
        )
        assertEquals(
            LogSeverity.INFO,
            parseLogLine("2026-10-10T14:09:00.079Z  Floppy bootstrap READY: 2/2").severity,
        )
        assertEquals(
            LogSeverity.INFO,
            parseLogLine("2026-10-10T15:00:00Z  FLOPPY delivered 1: EPISODE_WATCHED TV:136311 S02E07").severity,
        )
    }

    @Test
    fun lineWithoutTimestampIsKeptWhole() {
        val entry = parseLogLine("Artwork refresh started: 12 targets")

        assertNull(entry.at)
        assertEquals("Artwork refresh started", entry.summary)
    }
}
