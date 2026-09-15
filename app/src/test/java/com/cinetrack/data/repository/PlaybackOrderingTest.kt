package com.cinetrack.data.repository

import com.cinetrack.domain.MediaCard
import com.cinetrack.domain.MediaType
import com.cinetrack.domain.PlaybackCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
}

