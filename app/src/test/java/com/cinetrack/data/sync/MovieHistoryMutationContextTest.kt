package com.cinetrack.data.sync

import com.cinetrack.domain.LibraryStatus
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MovieHistoryMutationContextTest {
    @Test
    fun v2RoundTripPreservesDesiredStatusAndExactPreviousTimestamp() {
        val watchedAt = Instant.parse("2026-01-01T00:00:00Z")
        val encoded = MovieHistoryMutationContext(
            desiredLibraryStatus = LibraryStatus.PAUSED,
            previousWatched = true,
            previousWatchedAt = watchedAt,
        ).encode()

        assertTrue(encoded.startsWith("v2|"))
        assertEquals(
            MovieHistoryMutationContext(LibraryStatus.PAUSED, true, watchedAt),
            MovieHistoryMutationContext.parse(encoded),
        )
    }

    @Test
    fun legacyLibraryStatusAndTimestampRemainReadable() {
        assertEquals(
            MovieHistoryMutationContext(LibraryStatus.WATCHING, null, null),
            MovieHistoryMutationContext.parse("WATCHING"),
        )
        assertEquals(
            MovieHistoryMutationContext(null, true, Instant.parse("2026-01-01T00:00:00Z")),
            MovieHistoryMutationContext.parse("2026-01-01T00:00:00Z"),
        )
    }

    @Test
    fun knownNeverWatchedMutationDoesNotRequireTimestamp() {
        val parsed = MovieHistoryMutationContext.parse(
            MovieHistoryMutationContext(LibraryStatus.PLAN_TO_WATCH, false, null).encode(),
        )
        assertEquals(LibraryStatus.PLAN_TO_WATCH, parsed?.desiredLibraryStatus)
        assertFalse(parsed?.previousWatched ?: true)
        assertNull(parsed?.previousWatchedAt)
    }
}

