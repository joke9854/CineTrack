package com.cinetrack.data.repository

import com.cinetrack.data.remote.TmdbReleaseCountryDto
import com.cinetrack.data.remote.TmdbReleaseDateDto
import com.cinetrack.data.remote.TmdbReleaseDatesDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DigitalReleaseTest {
    private fun country(code: String, vararg dates: Pair<String, Int>) =
        TmdbReleaseCountryDto(code, dates.map { (date, type) -> TmdbReleaseDateDto("${date}T00:00:00.000Z", type) })

    @Test
    fun `the region's own digital date wins`() {
        val dates = TmdbReleaseDatesDto(listOf(
            country("US", "2026-01-10" to 4),
            country("IT", "2025-12-01" to 3, "2026-02-14" to 4),
        ))
        assertEquals("2026-02-14" to "IT", digitalReleaseFor(dates, "IT"))
    }

    @Test
    fun `falls back to the US digital date`() {
        val dates = TmdbReleaseDatesDto(listOf(
            country("US", "2026-01-10" to 4),
            country("IT", "2025-12-01" to 3),
        ))
        assertEquals("2026-01-10" to "US", digitalReleaseFor(dates, "IT"))
    }

    @Test
    fun `the earliest digital date is used and other types are ignored`() {
        val dates = TmdbReleaseDatesDto(listOf(country("US", "2026-03-01" to 4, "2026-01-20" to 4, "2025-12-01" to 5)))
        assertEquals("2026-01-20" to "US", digitalReleaseFor(dates, "us"))
    }

    @Test
    fun `no digital date means nothing to show`() {
        assertNull(digitalReleaseFor(TmdbReleaseDatesDto(listOf(country("US", "2025-12-01" to 3))), "IT"))
        assertNull(digitalReleaseFor(null, "IT"))
    }
}
