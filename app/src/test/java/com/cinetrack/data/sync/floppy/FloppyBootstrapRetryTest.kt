package com.cinetrack.data.sync.floppy

import com.cinetrack.data.sync.TrackingSyncError
import com.cinetrack.data.sync.SyncOperation
import com.cinetrack.data.sync.SyncOperationType
import com.cinetrack.domain.LibraryStatus
import com.cinetrack.domain.MediaType
import com.cinetrack.domain.FloppyBootstrapStage
import com.cinetrack.domain.allowsRetry
import com.cinetrack.domain.isManagedActive
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloppyBootstrapRetryTest {
    @Test
    fun dnsFailureIsRetryable() {
        assertTrue(isFloppyBootstrapRetryable(TrackingSyncError.DnsFailure(IllegalStateException("dns"))))
    }

    @Test
    fun authenticationFailureIsTerminal() {
        assertFalse(isFloppyBootstrapRetryable(TrackingSyncError.AuthenticationRequired(com.cinetrack.data.sync.TrackingProviderId.FLOPPY)))
    }

    @Test
    fun completedMoviePairIsOneLogicalUnitButCountsBothOperations() {
        val generation = 42L
        val library = SyncOperation("library", SyncOperationType.LIBRARY_STATUS, MediaType.MOVIE, 7, "Movie", LibraryStatus.COMPLETED.name, sourceVersion = generation)
        val watched = SyncOperation("watched", SyncOperationType.MOVIE_WATCHED, MediaType.MOVIE, 7, "Movie", payload = "2026-01-01T00:00:00Z", sourceVersion = generation)
        val other = SyncOperation("other", SyncOperationType.LIBRARY_STATUS, MediaType.MOVIE, 8, "Other", LibraryStatus.PLAN_TO_WATCH.name, sourceVersion = generation + 1)
        assertTrue(listOf(library, watched, other).bootstrapLogicalUnit().map { it.id }.toSet() == setOf("library", "watched"))
    }

    @Test
    fun managedCardRetryIsOnlyShownForRecoverableStages() {
        assertFalse(FloppyBootstrapStage.BUILDING_PLAN.allowsRetry())
        assertFalse(FloppyBootstrapStage.MATERIALIZING_QUEUE.allowsRetry())
        assertFalse(FloppyBootstrapStage.SYNCING.allowsRetry())
        assertFalse(FloppyBootstrapStage.COMPLETE.allowsRetry())
        assertTrue(FloppyBootstrapStage.STALLED.allowsRetry())
        assertTrue(FloppyBootstrapStage.NEEDS_ATTENTION.allowsRetry())
        assertTrue(FloppyBootstrapStage.SYNCING.isManagedActive())
        assertFalse(FloppyBootstrapStage.COMPLETE.isManagedActive())
    }
}

