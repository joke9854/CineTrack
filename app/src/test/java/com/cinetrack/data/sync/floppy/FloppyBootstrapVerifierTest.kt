package com.cinetrack.data.sync.floppy

import com.cinetrack.data.sync.MediaIds
import com.cinetrack.data.sync.TrackedEpisodeState
import com.cinetrack.data.sync.TrackedMovieState
import com.cinetrack.data.sync.TrackedShowState
import com.cinetrack.data.sync.TrackingSnapshot
import com.cinetrack.domain.LibraryStatus
import java.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloppyBootstrapVerifierTest {
    private val verifier = FloppyBootstrapVerifier()
    private val watchedAt = Instant.parse("2026-01-02T03:04:05Z")

    @Test
    fun completedMovieUsesExactCompletionAndIgnoresActiveAggregate() {
        val expected = TrackingSnapshot(
            movies = listOf(TrackedMovieState(MediaIds(tmdb = 42), LibraryStatus.COMPLETED, watched = true, watchedAt = watchedAt)),
        )
        val actual = FloppyVerificationProjection(
            movies = mapOf(42L to FloppyVerificationProjection.Movie(null, setOf(watchedAt), completedConsumptions = 1)),
            shows = emptyMap(),
            episodes = emptySet(),
        )

        assertTrue(verifier.verify(expected, actual))
    }

    @Test
    fun noneAcceptsUnrelatedHistoricalMovieCompletion() {
        val expected = TrackingSnapshot(
            movies = listOf(TrackedMovieState(MediaIds(tmdb = 42), LibraryStatus.NONE, watched = false)),
        )
        val actual = FloppyVerificationProjection(
            movies = mapOf(42L to FloppyVerificationProjection.Movie(null, setOf(watchedAt), completedConsumptions = 1)),
            shows = emptyMap(),
            episodes = emptySet(),
        )

        assertTrue(verifier.verify(expected, actual))
    }

    @Test
    fun mismatchedActiveStatusDoesNotBecomeReady() {
        val expected = TrackingSnapshot(
            movies = listOf(TrackedMovieState(MediaIds(tmdb = 42), LibraryStatus.WATCHING)),
        )
        val actual = FloppyVerificationProjection(
            movies = mapOf(42L to FloppyVerificationProjection.Movie(LibraryStatus.PAUSED)),
            shows = emptyMap(),
            episodes = emptySet(),
        )

        assertFalse(verifier.verify(expected, actual))
    }

    @Test
    fun completedShowAndExactEpisodeAreVerifiedSeparately() {
        val expected = TrackingSnapshot(
            shows = listOf(TrackedShowState(MediaIds(tmdb = 7), LibraryStatus.COMPLETED)),
            episodes = listOf(TrackedEpisodeState(MediaIds(tmdb = 7), season = 2, episode = 3, watched = true, watchedAt = watchedAt)),
        )
        val actual = FloppyVerificationProjection(
            movies = emptyMap(),
            shows = mapOf(7L to FloppyVerificationProjection.Show(null, completedConsumptions = 1)),
            episodes = setOf(FloppyVerificationProjection.Episode(7, 2, 3, watched = true, watchedAt = watchedAt)),
        )

        assertTrue(verifier.verify(expected, actual))
    }
}

