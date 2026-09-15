package com.cinetrack.data.repository

import com.cinetrack.domain.MediaCard
import com.cinetrack.domain.MediaType
import com.cinetrack.domain.PlaybackCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class PlaybackOrderingTest {
    private val media = MediaCard(id = 1, type = MediaType.TV, title = "Show", libraryUpdatedAt = 1_000L)

    @Test
    fun newerProgressActivityWinsOverLibraryAndAirDate() {
        val card = PlaybackCard(
            media = media,
            progress = .4f,
            episodeAirDate = "2099-01-01",
            progressUpdatedAtMillis = 5_000L,
        )
        assertEquals(5_000L, playbackActivityRecency(card))
    }

    @Test
    fun identicalRemoteTimestampProducesStableOrderingKey() {
        val first = PlaybackCard(media = media, progress = .4f, progressUpdatedAtMillis = 5_000L)
        val second = first.copy(progress = .4f)
        assertEquals(playbackActivityRecency(first), playbackActivityRecency(second))
        assertTrue(playbackActivityRecency(first) > (media.libraryUpdatedAt ?: 0L))
    }

    @Test
    fun imminentUpcomingEpisodeWinsAnEqualDistanceTie() {
        val now = Instant.parse("2026-09-16T12:00:00Z").toEpochMilli()
        val abbott = PlaybackCard(media = media.copy(id = 1, title = "Abbott"), progress = .3f, progressUpdatedAtMillis = now - 86_400_000L)
        val ted = PlaybackCard(media = media.copy(id = 2, title = "Ted"), progress = 0f, episodeAirDate = "2026-09-17T12:00:00Z")
        val ordered = listOf(abbott, ted).sortedWith { a, b -> compareProgressAttention(a, b, emptyMap(), now) }
        assertEquals("Ted", ordered.first().media.title)
    }

    @Test
    fun recentPlaybackBeatsUpcomingEpisodeSixDaysAway() {
        val now = Instant.parse("2026-09-16T12:00:00Z").toEpochMilli()
        val abbott = PlaybackCard(media = media.copy(id = 1, title = "Abbott"), progress = .3f, progressUpdatedAtMillis = now - 5 * 60_000L)
        val ted = PlaybackCard(media = media.copy(id = 2, title = "Ted"), progress = 0f, episodeAirDate = "2026-09-22T12:00:00Z")
        val ordered = listOf(ted, abbott).sortedWith { a, b -> compareProgressAttention(a, b, emptyMap(), now) }
        assertEquals("Abbott", ordered.first().media.title)
    }

    @Test
    fun airedUnwatchedEpisodeIsPreferredOverFutureEpisode() {
        val now = Instant.parse("2026-09-16T12:00:00Z")
        val episodes = listOf(
            com.cinetrack.domain.EpisodeCard(id = 1, showId = 42, season = 1, number = 1, title = "Aired", overview = "", airDate = "2026-09-10"),
            com.cinetrack.domain.EpisodeCard(id = 2, showId = 42, season = 1, number = 2, title = "Tomorrow", overview = "", airDate = "2026-09-17"),
        )
        assertEquals(1, selectNextProgressEpisode(42, episodes, emptySet(), now, java.time.ZoneId.of("UTC"), false)?.number)
    }

    @Test
    fun futureEpisodeOutsideAttentionWindowDoesNotBecomePriority() {
        val now = Instant.parse("2026-09-16T12:00:00Z").toEpochMilli()
        val active = PlaybackCard(media = media.copy(id = 1, title = "Active"), progress = .2f, progressUpdatedAtMillis = now - 2 * 86_400_000L)
        val distant = PlaybackCard(media = media.copy(id = 2, title = "Distant"), progress = 0f, episodeAirDate = "2026-10-16T12:00:00Z", progressUpdatedAtMillis = 0L)
        val ordered = listOf(distant, active).sortedWith { a, b -> compareProgressAttention(a, b, emptyMap(), now) }
        assertEquals("Active", ordered.first().media.title)
    }

    @Test
    fun recentlyAiredEpisodeSurfacesUntilNewerViewingActivityArrives() {
        val now = Instant.parse("2026-09-16T12:00:00Z").toEpochMilli()
        val aired = PlaybackCard(media = media.copy(id = 1, title = "Ted"), progress = 0f, episodeAirDate = "2026-09-16T11:55:00Z")
        val stale = PlaybackCard(media = media.copy(id = 2, title = "Older"), progress = 0f, progressUpdatedAtMillis = now - 2 * 86_400_000L)
        val first = listOf(stale, aired).sortedWith { a, b -> compareProgressAttention(a, b, emptyMap(), now) }
        assertEquals("Ted", first.first().media.title)

        val activelyWatched = aired.copy(media = aired.media.copy(title = "Active"), episodeAirDate = null, progress = .3f, progressUpdatedAtMillis = now - 60_000L)
        val second = listOf(aired, activelyWatched).sortedWith { a, b -> compareProgressAttention(a, b, emptyMap(), now) }
        assertEquals("Active", second.first().media.title)
    }
}

