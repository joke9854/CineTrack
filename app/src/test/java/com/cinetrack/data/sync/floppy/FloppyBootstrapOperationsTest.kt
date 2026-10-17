package com.cinetrack.data.sync.floppy

import com.cinetrack.data.sync.MediaIds
import com.cinetrack.data.sync.TrackingSnapshot
import com.cinetrack.data.sync.TrackedMovieState
import com.cinetrack.domain.LibraryStatus
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class FloppyBootstrapOperationsTest {
    @Test
    fun completedMovieUsesOneGenerationForLibraryAndWatch() {
        val watchedAt = Instant.parse("2026-01-01T00:00:00Z")
        val operations = buildFloppyBootstrapOperations(
            "instance-a",
            TrackingSnapshot(movies = listOf(
                TrackedMovieState(MediaIds(tmdb = 42), LibraryStatus.COMPLETED, watched = true, watchedAt = watchedAt),
            )),
        )

        assertEquals(2, operations.size)
        assertEquals(operations[0].sourceVersion, operations[1].sourceVersion)
        assertEquals(watchedAt.toString(), operations[1].payload)
    }

    @Test
    fun completedMovieWithoutWatchedAtUsesGenerationAnchoredFallback() {
        val operations = buildFloppyBootstrapOperations(
            "instance-a",
            TrackingSnapshot(movies = listOf(
                TrackedMovieState(MediaIds(tmdb = 42), LibraryStatus.COMPLETED, watched = true, watchedAt = null, updatedAt = null),
            )),
        )

        assertEquals(2, operations.size)
        assertEquals(operations[0].sourceVersion, operations[1].sourceVersion)
        val fallback = Instant.parse(requireNotNull(operations[1].payload))
        assertEquals(Instant.ofEpochMilli(operations[0].sourceVersion), fallback)
    }

    @Test
    fun completedMoviePrefersUpdatedAtWhenHistoryTimestampIsMissing() {
        val updatedAt = Instant.parse("2026-02-03T04:05:06Z")
        val operations = buildFloppyBootstrapOperations(
            "instance-a",
            TrackingSnapshot(movies = listOf(
                TrackedMovieState(MediaIds(tmdb = 42), LibraryStatus.COMPLETED, watched = true, updatedAt = updatedAt),
            )),
        )

        assertEquals(updatedAt.toString(), operations.single { it.type == com.cinetrack.data.sync.SyncOperationType.MOVIE_WATCHED }.payload)
    }
}

