package com.cinetrack.data.repository

import com.cinetrack.domain.EpisodeCard
import com.cinetrack.domain.LibraryStatus
import com.cinetrack.domain.MediaCard
import com.cinetrack.domain.MediaType
import com.cinetrack.domain.PlaybackCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class ProgressMembershipTest {
    private val now = Instant.parse("2026-09-16T15:00:00Z")
    private val zone = ZoneId.of("UTC")
    private val show = MediaCard(42, MediaType.TV, "Test show", status = LibraryStatus.WATCHING)

    @Test fun fullyWatchedEndedShowHasNoCard() {
        val eps = listOf(episode(1, "2026-01-01"), episode(2, "2026-01-02"))
        assertNull(selectProgressCard(show, eps, setOf(Triple(42, 1, 1), Triple(42, 1, 2)), null, null, now, zone, false))
    }

    @Test fun caughtUpFutureEpisodeHasNoCardBeforeRelease() {
        val eps = listOf(episode(1, "2026-09-16T21:00:00Z"))
        assertNull(selectProgressCard(show, eps, emptySet(), null, null, now, zone, false))
    }

    @Test fun releasedEpisodeBecomesConcreteCard() {
        val eps = listOf(episode(1, "2026-09-16T14:59:00Z"))
        assertEquals(1, selectProgressCard(show, eps, emptySet(), null, null, now, zone, false)?.episodeNumber)
    }

    @Test fun partialPlaybackRemainsWhenNoNextEpisode() {
        val session = PlaybackCard(show, season = 1, episodeNumber = 2, progress = .42f)
        assertEquals(.42f, selectProgressCard(show, emptyList(), emptySet(), session, null, now, zone, false)?.progress)
    }

    @Test fun partialPlaybackWinsOverNewerAiredEpisode() {
        val session = PlaybackCard(show, season = 1, episodeNumber = 1, progress = .42f)
        val eps = listOf(episode(1, "2026-01-01"), episode(2, "2026-09-15"))
        assertEquals(1, selectProgressCard(show, eps, emptySet(), session, null, now, zone, false)?.episodeNumber)
    }

    @Test fun distantFutureDoesNotCreateCard() {
        val eps = listOf(episode(1, "2026-11-01"))
        assertNull(selectProgressCard(show, eps, emptySet(), null, null, now, zone, false))
    }

    private fun episode(number: Int, airDate: String) = EpisodeCard(
        id = number,
        showId = show.id,
        season = 1,
        number = number,
        title = "E$number",
        overview = "",
        airDate = airDate,
    )
}
