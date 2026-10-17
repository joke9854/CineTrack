package com.cinetrack.data.schedule

import org.junit.Assert.assertEquals
import org.junit.Test

class AirDateResolutionTest {
    private val all = mapOf(
        AirDateSource.TMDB to "2026-10-15",
        AirDateSource.TRAKT to "2026-10-15T01:00:00.000Z",
        AirDateSource.SIMKL to "2026-10-15T03:00:00Z",
    )

    @Test
    fun `the chosen provider's date is shown`() {
        assertEquals("2026-10-15", resolveAirDate(all, AirDateSource.TMDB))
        assertEquals("2026-10-15T01:00:00.000Z", resolveAirDate(all, AirDateSource.TRAKT))
        assertEquals("2026-10-15T03:00:00Z", resolveAirDate(all, AirDateSource.SIMKL))
    }

    @Test
    fun `without the chosen provider's date the most precise other one fills in`() {
        val noTrakt = all - AirDateSource.TRAKT
        assertEquals("2026-10-15T03:00:00Z", resolveAirDate(noTrakt, AirDateSource.TRAKT))
        val onlyTmdb = mapOf(AirDateSource.TMDB to "2026-10-15")
        assertEquals("2026-10-15", resolveAirDate(onlyTmdb, AirDateSource.SIMKL))
        val noTmdb = all - AirDateSource.TMDB
        assertEquals("2026-10-15T01:00:00.000Z", resolveAirDate(noTmdb, AirDateSource.TMDB))
    }

    @Test
    fun `a date stored before the update outranks TMDB unless TMDB is chosen`() {
        val onlyTmdb = mapOf(AirDateSource.TMDB to "2026-10-15")
        val stored = "2026-10-15T02:00:00Z"
        assertEquals(stored, resolveAirDate(onlyTmdb, AirDateSource.TRAKT, legacy = stored))
        assertEquals("2026-10-15", resolveAirDate(onlyTmdb, AirDateSource.TMDB, legacy = stored))
        assertEquals(stored, resolveAirDate(emptyMap(), AirDateSource.TMDB, legacy = stored))
    }

    @Test
    fun `blank dates are ignored`() {
        assertEquals("2026-10-15", resolveAirDate(mapOf(AirDateSource.TRAKT to "", AirDateSource.TMDB to "2026-10-15"), AirDateSource.TRAKT))
    }
}
