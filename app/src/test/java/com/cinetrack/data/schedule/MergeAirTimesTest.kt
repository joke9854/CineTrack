package com.cinetrack.data.schedule

import com.cinetrack.data.remote.SimklCalendarEpisode
import com.cinetrack.data.remote.SimklCalendarIds
import com.cinetrack.data.remote.SimklCalendarItem
import org.junit.Assert.assertEquals
import org.junit.Test

class MergeAirTimesTest {
    private fun simkl(show: Int, season: Int, episode: Int, date: String) =
        SimklCalendarItem(date = date, ids = SimklCalendarIds(tmdb = show.toString()), episode = SimklCalendarEpisode(season, episode))

    @Test
    fun simklTimesAreKeptPerEpisodeAndTheFirstListingWins() {
        val times = simklAirTimes(
            listOf(
                simkl(1, 2, 7, "2026-10-10T00:00:00-04:00"),
                simkl(1, 2, 8, "2026-10-17T21:00:00-04:00"),
                simkl(1, 2, 7, "2026-10-11T00:00:00-04:00"),
            ),
            tracked = setOf(1),
        )

        assertEquals("2026-10-10T00:00:00-04:00", times[Triple(1, 2, 7)])
        assertEquals("2026-10-17T21:00:00-04:00", times[Triple(1, 2, 8)])
    }

    @Test
    fun untrackedShowsAndBadNumbersAreIgnored() {
        assertEquals(
            emptyMap<Triple<Int, Int, Int>, String>(),
            simklAirTimes(listOf(simkl(2, 1, 1, "2026-10-10"), simkl(1, 1, 0, "2026-10-10"), simkl(1, 1, 2, "short")), setOf(1)),
        )
    }
}
