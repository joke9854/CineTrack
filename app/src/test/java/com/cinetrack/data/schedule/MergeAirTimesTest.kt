package com.cinetrack.data.schedule

import com.cinetrack.data.remote.SimklCalendarEpisode
import com.cinetrack.data.remote.SimklCalendarIds
import com.cinetrack.data.remote.SimklCalendarItem
import com.cinetrack.data.remote.TraktCalendarEpisode
import com.cinetrack.data.remote.TraktCalendarItem
import com.cinetrack.data.remote.TraktCalendarShow
import com.cinetrack.data.remote.TraktIds
import org.junit.Assert.assertEquals
import org.junit.Test

class MergeAirTimesTest {
    private fun simkl(show: Int, season: Int, episode: Int, date: String) =
        SimklCalendarItem(date = date, ids = SimklCalendarIds(tmdb = show.toString()), episode = SimklCalendarEpisode(season, episode))

    private fun trakt(show: Int, season: Int, episode: Int, aired: String, episodeTmdb: Int? = null) =
        TraktCalendarItem(aired, TraktCalendarEpisode(season, episode, TraktIds(episodeTmdb)), TraktCalendarShow(TraktIds(show)))

    @Test
    fun traktTimeWinsAndSimklFillsTheRest() {
        val merged = mergeAirTimes(
            simkl = listOf(simkl(1, 2, 7, "2026-10-10T00:00:00-04:00"), simkl(1, 2, 8, "2026-10-17T21:00:00-04:00")),
            trakt = listOf(trakt(1, 2, 7, "2026-10-10T07:00:00.000Z")),
            tracked = setOf(1),
        )

        assertEquals("2026-10-10T07:00:00.000Z", merged[Triple(1, 2, 7)])
        assertEquals("2026-10-17T21:00:00-04:00", merged[Triple(1, 2, 8)])
    }

    @Test
    fun traktEpisodeIdFindsTheLocalRowWhenNumberingDiffers() {
        val merged = mergeAirTimes(
            simkl = emptyList(),
            trakt = listOf(trakt(37854, 1, 1100, "2026-10-11T14:15:00.000Z", episodeTmdb = 999)),
            tracked = setOf(37854),
            localByEpisodeId = mapOf((37854 to 999) to Triple(37854, 22, 1100)),
        )

        assertEquals(mapOf(Triple(37854, 22, 1100) to "2026-10-11T14:15:00.000Z"), merged)
    }

    @Test
    fun untrackedShowsAreIgnored() {
        assertEquals(
            emptyMap<Triple<Int, Int, Int>, String>(),
            mergeAirTimes(listOf(simkl(2, 1, 1, "2026-10-10")), listOf(trakt(2, 1, 1, "2026-10-10T01:00:00.000Z")), setOf(1)),
        )
    }
}
