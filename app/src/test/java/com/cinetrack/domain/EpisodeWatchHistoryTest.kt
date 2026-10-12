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

    @Test
    fun rewatchesAreCountedWithTheLatestDate() {
        val film = MediaCard(id = 7, type = MediaType.MOVIE, title = "Film")
        val history = listOf(
            TimelineCard(film, "", "2026-01-02T20:00:00Z"),
            TimelineCard(film, "", "2026-10-12T21:00:00Z"),
            TimelineCard(show, "", "2026-09-08T20:00:00Z", season = 1, episodeNumber = 2),
        )

        assertEquals(WatchSummary(2, "2026-10-12T21:00:00Z"), watchSummary(history, film.stableKey))
        assertEquals(WatchSummary(1, "2026-09-08T20:00:00Z"), watchSummary(history, show.stableKey, 1, 2))
        assertEquals(null, watchSummary(history, show.stableKey, 1, 3))
    }

    @Test
    fun episodeCountsStayWithinTheShow() {
        val other = MediaCard(id = 2, type = MediaType.TV, title = "Other")
        val counts = episodeWatchCounts(
            listOf(
                TimelineCard(show, "", "2026-09-08T20:00:00Z", season = 1, episodeNumber = 2),
                TimelineCard(show, "", "2026-09-09T20:00:00Z", season = 1, episodeNumber = 2),
                TimelineCard(other, "", "2026-09-09T20:00:00Z", season = 1, episodeNumber = 2),
            ),
            show.id,
        )

        assertEquals(2, counts[1 to 2])
        assertEquals(1, counts.size)
    }
}
