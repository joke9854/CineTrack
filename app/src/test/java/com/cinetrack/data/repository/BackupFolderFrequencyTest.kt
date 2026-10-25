package com.cinetrack.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class BackupFolderFrequencyTest {
    private val zone = ZoneId.of("Europe/Rome")
    private fun at(text: String) = LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

    @Test fun `daily copies once the next calendar day starts`() {
        val last = at("2026-10-25T23:30")
        assertFalse(BackupFolderFrequency.DAILY.isDue(last, at("2026-10-25T23:59"), zone))
        assertTrue(BackupFolderFrequency.DAILY.isDue(last, at("2026-10-26T00:05"), zone))
    }

    @Test fun `weekly waits seven days`() {
        val last = at("2026-10-25T10:00")
        assertFalse(BackupFolderFrequency.WEEKLY.isDue(last, at("2026-10-31T22:00"), zone))
        assertTrue(BackupFolderFrequency.WEEKLY.isDue(last, at("2026-11-01T08:00"), zone))
    }

    @Test fun `monthly follows the calendar month`() {
        val last = at("2026-01-31T10:00")
        assertFalse(BackupFolderFrequency.MONTHLY.isDue(last, at("2026-02-27T10:00"), zone))
        assertTrue(BackupFolderFrequency.MONTHLY.isDue(last, at("2026-02-28T10:00"), zone))
    }
}
