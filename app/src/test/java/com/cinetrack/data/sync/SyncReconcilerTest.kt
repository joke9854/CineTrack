package com.cinetrack.data.sync

import com.cinetrack.domain.LibraryStatus
import com.cinetrack.domain.MediaType
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncReconcilerTest {
    private val reconciler = SyncReconciler()
    private val provider = TrackingProviderId.SIMKL

    @Test fun `identical snapshots produce no work`() {
        val at = Instant.parse("2025-01-01T00:00:00Z")
        val state = movie(LibraryStatus.WATCHING, true, at)
        val result = reconciler.reconcile(local(TrackingSnapshot(movies = listOf(state))), TrackingSnapshot(movies = listOf(state)), provider)
        assertTrue(result.localMutations.isEmpty())
        assertTrue(result.remoteOperations.isEmpty())
        assertTrue(result.conflicts.isEmpty())
    }

    @Test fun `local newer state becomes outbound operation`() {
        val localState = movie(LibraryStatus.COMPLETED, true, Instant.parse("2025-01-02T00:00:00Z"))
        val remoteState = movie(LibraryStatus.WATCHING, false, Instant.parse("2025-01-01T00:00:00Z"))
        val result = reconciler.reconcile(local(TrackingSnapshot(movies = listOf(localState))), TrackingSnapshot(movies = listOf(remoteState)), provider)
        assertEquals(
            setOf(SyncOperationType.LIBRARY_STATUS, SyncOperationType.MOVIE_WATCHED),
            result.remoteOperations.map { it.type }.toSet(),
        )
        assertTrue(result.localMutations.isEmpty())
    }

    @Test fun `remote newer state becomes remote-origin local mutation`() {
        val localState = movie(LibraryStatus.WATCHING, false, Instant.parse("2025-01-01T00:00:00Z"))
        val remoteState = movie(LibraryStatus.COMPLETED, true, Instant.parse("2025-01-02T00:00:00Z"))
        val result = reconciler.reconcile(local(TrackingSnapshot(movies = listOf(localState))), TrackingSnapshot(movies = listOf(remoteState)), provider)
        val statusMutation = result.localMutations.filterIsInstance<LocalMutation.SetLibraryStatus>().single()
        assertEquals(LibraryStatus.COMPLETED, statusMutation.status)
        assertEquals(MutationOrigin.REMOTE_SYNC, statusMutation.origin)
    }

    @Test fun `pending local operation protects state from stale remote`() {
        val localState = movie(LibraryStatus.COMPLETED, true, Instant.parse("2025-01-02T00:00:00Z"))
        val remoteState = movie(LibraryStatus.WATCHING, false, Instant.parse("2025-01-03T00:00:00Z"))
        val pending = SyncOperation("pending", SyncOperationType.LIBRARY_STATUS, MediaType.MOVIE, 42, "", value = "COMPLETED", sourceVersion = 1)
        val result = reconciler.reconcile(local(TrackingSnapshot(movies = listOf(localState)), pending), TrackingSnapshot(movies = listOf(remoteState)), provider)
        assertTrue(result.localMutations.isEmpty())
        assertTrue(result.remoteOperations.isEmpty())
    }

    @Test fun `episode progress compares every episode`() {
        val at = Instant.parse("2025-01-01T00:00:00Z")
        val localEpisode = TrackedEpisodeState(MediaIds(tmdb = 7), 1, 2, true, at, at)
        val remoteEpisode = localEpisode.copy(watched = false, updatedAt = at.plusSeconds(60))
        val result = reconciler.reconcile(local(TrackingSnapshot(episodes = listOf(localEpisode))), TrackingSnapshot(episodes = listOf(remoteEpisode)), provider)
        assertTrue(result.localMutations.single() is LocalMutation.SetWatched)
    }

    @Test fun `missing timestamps are deterministic conflicts`() {
        val localState = movie(LibraryStatus.WATCHING, true, null)
        val remoteState = movie(LibraryStatus.COMPLETED, false, null)
        val result = reconciler.reconcile(local(TrackingSnapshot(movies = listOf(localState))), TrackingSnapshot(movies = listOf(remoteState)), provider)
        assertEquals(2, result.conflicts.size)
    }

    @Test fun `identity uses stable external ids rather than title`() {
        val localState = TrackedMovieState(MediaIds(tmdb = 99, imdb = "tt123"), LibraryStatus.WATCHING, false, updatedAt = Instant.parse("2025-01-01T00:00:00Z"))
        val remoteState = TrackedMovieState(MediaIds(tmdb = 99, imdb = "tt123"), LibraryStatus.COMPLETED, false, updatedAt = Instant.parse("2025-01-02T00:00:00Z"))
        val result = reconciler.reconcile(local(TrackingSnapshot(movies = listOf(localState))), TrackingSnapshot(movies = listOf(remoteState)), provider)
        assertEquals(1, result.localMutations.size)
    }

    private fun movie(status: LibraryStatus, watched: Boolean, at: Instant?) =
        TrackedMovieState(MediaIds(tmdb = 42), status, watched, at, at)

    private fun local(state: TrackingSnapshot, vararg pending: SyncOperation) =
        LocalTrackingSnapshot(state = state, pendingOperations = pending.toList())
}

