package com.cinetrack.data.schedule

import org.junit.Assert.assertEquals
import org.junit.Test

class AirDateResolutionTest {
    private val all = mapOf(
        AirDateSource.TMDB to "2026-10-15",
        AirDateSource.TVMAZE to "2026-10-15T01:00:00+00:00",
        AirDateSource.SIMKL to "2026-10-15T03:00:00Z",
    )

    @Test
    fun `the chosen provider's date is shown`() {
        assertEquals("2026-10-15", resolveAirDate(all, AirDateSource.TMDB))
        assertEquals("2026-10-15T01:00:00+00:00", resolveAirDate(all, AirDateSource.TVMAZE))
        assertEquals("2026-10-15T03:00:00Z", resolveAirDate(all, AirDateSource.SIMKL))
    }

    @Test
    fun `without the chosen provider's date the most precise other one fills in`() {
        val noTvmaze = all - AirDateSource.TVMAZE
        assertEquals("2026-10-15T03:00:00Z", resolveAirDate(noTvmaze, AirDateSource.TVMAZE))
        val onlyTmdb = mapOf(AirDateSource.TMDB to "2026-10-15")
        assertEquals("2026-10-15", resolveAirDate(onlyTmdb, AirDateSource.SIMKL))
        val noTmdb = all - AirDateSource.TMDB
        assertEquals("2026-10-15T01:00:00+00:00", resolveAirDate(noTmdb, AirDateSource.TMDB))
    }

    @Test
    fun `a date stored before the update outranks TMDB unless TMDB is chosen`() {
        val onlyTmdb = mapOf(AirDateSource.TMDB to "2026-10-15")
        val stored = "2026-10-15T02:00:00Z"
        assertEquals(stored, resolveAirDate(onlyTmdb, AirDateSource.TVMAZE, legacy = stored))
        assertEquals("2026-10-15", resolveAirDate(onlyTmdb, AirDateSource.TMDB, legacy = stored))
        assertEquals(stored, resolveAirDate(emptyMap(), AirDateSource.TMDB, legacy = stored))
    }

    @Test
    fun `blank dates are ignored`() {
        assertEquals("2026-10-15", resolveAirDate(mapOf(AirDateSource.TVMAZE to "", AirDateSource.TMDB to "2026-10-15"), AirDateSource.TVMAZE))
    }

    @Test
    fun `stored Trakt rows and a saved Trakt choice fall back to TVmaze`() {
        val withTrakt = all + (AirDateSource.RETIRED_TRAKT to "2026-10-15T00:30:00Z")
        assertEquals("2026-10-15T01:00:00+00:00", resolveAirDate(withTrakt, AirDateSource.RETIRED_TRAKT))
        assertEquals("2026-10-15T03:00:00Z", resolveAirDate(mapOf(AirDateSource.RETIRED_TRAKT to "2026-10-15T00:30:00Z", AirDateSource.SIMKL to "2026-10-15T03:00:00Z"), AirDateSource.TVMAZE))
        assertEquals(AirDateSource.TVMAZE, AirDateSource.normalize(AirDateSource.RETIRED_TRAKT))
        assertEquals(AirDateSource.TVMAZE, AirDateSource.normalize(null))
        assertEquals(AirDateSource.TVMAZE, AirDateSource.DEFAULT)
        assertEquals(AirDateSource.SIMKL, AirDateSource.normalize(AirDateSource.SIMKL))
    }
}
