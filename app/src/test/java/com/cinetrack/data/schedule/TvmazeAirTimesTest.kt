package com.cinetrack.data.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvmazeAirTimesTest {
    @Test
    fun tvmazeIsTrustedOnlyWhenItsDayMatchesTmdb() {
        assertTrue(tvmazeMatchesTmdb("2026-10-25", "2026-10-25"))
        // US evening release is the next day in Europe.
        assertTrue(tvmazeMatchesTmdb("2026-10-26", "2026-10-25"))
        assertFalse(tvmazeMatchesTmdb("2026-10-25", "2026-11-01"))
        assertFalse(tvmazeMatchesTmdb(null, "2026-10-25"))
    }

    @Test
    fun tvmazeRanksBeforeSimklAndTmdb() {
        val dates = mapOf(
            AirDateSource.TVMAZE to "2026-10-25T01:00:00+00:00",
            AirDateSource.SIMKL to "2026-10-25T02:00:00Z",
            AirDateSource.TMDB to "2026-10-24",
        )
        assertEquals("2026-10-25T01:00:00+00:00", resolveAirDate(dates, AirDateSource.TVMAZE))
        assertEquals("2026-10-25T02:00:00Z", resolveAirDate(dates, AirDateSource.SIMKL))
        assertEquals("2026-10-25T01:00:00+00:00", resolveAirDate(dates - AirDateSource.SIMKL, AirDateSource.SIMKL))
        assertEquals("2026-10-24", resolveAirDate(dates, AirDateSource.TMDB))
    }
}
