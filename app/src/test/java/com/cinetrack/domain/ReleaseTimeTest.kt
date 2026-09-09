package com.cinetrack.domain

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseTimeTest {
    private val rome = ZoneId.of("Europe/Rome")

    @Test
    fun offsetTimestampKeepsItsExactInstant() {
        assertEquals(
            Instant.parse("2026-09-09T07:30:00Z"),
            releaseInstant("2026-09-09T09:30:00+02:00", rome),
        )
        assertTrue(hasExplicitReleaseTime("2026-09-09T09:30:00+02:00"))
    }

    @Test
    fun dateOnlyFallsBackToStartOfSelectedDay() {
        assertEquals(
            Instant.parse("2026-09-08T22:00:00Z"),
            releaseInstant("2026-09-09", rome),
        )
        assertFalse(hasExplicitReleaseTime("2026-09-09"))
    }

    @Test
    fun invalidValueIsRejected() {
        assertNull(releaseInstant("not-a-date", rome))
    }
}
