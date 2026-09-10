package com.cinetrack.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class EpisodeWatchHistoryTest {
    private val show = MediaCard(id = 1, type = MediaType.TV, title = "Show")

    @Test
    fun keepsLatestWatchForEachEpisode() {
        val times = latestEpisodeWatchTimes(
            listOf(
                TimelineCard(show, "", "2026-09-08T20:00:00Z", season = 1, episodeNumber = 2),
                TimelineCard(show, "", "2026-09-09T18:30:00Z", season = 1, episodeNumber = 2),
                TimelineCard(show, "", "2026-09-07T12:00:00Z", season = 1, episodeNumber = 3),
                TimelineCard(show, "", "2026-09-10T12:00:00Z"),
            ),
        )

        assertEquals("2026-09-09T18:30:00Z", times[1 to 2])
        assertEquals("2026-09-07T12:00:00Z", times[1 to 3])
        assertFalse(times.containsKey(0 to 0))
    }
}
