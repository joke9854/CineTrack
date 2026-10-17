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

    @Test fun `remote only library change is applied without conflict`() {
        val baseline = movie(LibraryStatus.WATCHING, false, null)
        val localState = baseline
        val remoteState = baseline.copy(libraryState = LibraryStatus.DROPPED)
        val result = reconciler.reconcile(localWithBaseline(TrackingSnapshot(movies = listOf(localState)), TrackingSnapshot(movies = listOf(baseline))), TrackingSnapshot(movies = listOf(remoteState)), provider)
        assertEquals(LibraryStatus.DROPPED, (result.localMutations.single() as LocalMutation.SetLibraryStatus).status)
        assertTrue(result.remoteOperations.isEmpty())
        assertTrue(result.conflicts.isEmpty())
    }

    @Test fun `local only library change is uploaded without conflict`() {
        val baseline = movie(LibraryStatus.WATCHING, false, null)
        val localState = baseline.copy(libraryState = LibraryStatus.COMPLETED)
        val result = reconciler.reconcile(localWithBaseline(TrackingSnapshot(movies = listOf(localState)), TrackingSnapshot(movies = listOf(baseline))), TrackingSnapshot(movies = listOf(baseline)), provider)
        assertEquals(LibraryStatus.WATCHING, (result.localMutations.single() as LocalMutation.SetLibraryStatus).status)
        assertTrue(result.remoteOperations.isEmpty())
        assertTrue(result.conflicts.isEmpty())
    }

    @Test fun `concurrent library edits create one conflict`() {
        val baseline = movie(LibraryStatus.WATCHING, false, null)
        val at = Instant.parse("2025-01-02T00:00:00Z")
        val localState = baseline.copy(libraryState = LibraryStatus.COMPLETED, updatedAt = at)
        val remoteState = baseline.copy(libraryState = LibraryStatus.DROPPED)
        val pending = SyncOperation("state:MOVIE:42", SyncOperationType.LIBRARY_STATUS, MediaType.MOVIE, 42, "", value = "COMPLETED", sourceVersion = at.toEpochMilli())
        val local = LocalTrackingSnapshot(
            state = TrackingSnapshot(movies = listOf(localState)),
            baseline = TrackingSnapshot(movies = listOf(baseline)),
            dirtyMediaKeys = setOf("MOVIE:42"),
            pendingOperations = listOf(pending),
        )
        val result = reconciler.reconcile(local, TrackingSnapshot(movies = listOf(remoteState)), provider)
        assertEquals(1, result.conflicts.count { it.field == ConflictField.LIBRARY_STATUS })
    }

    @Test fun `bootstrap accepts main when no local pending write exists`() {
        val localState = movie(LibraryStatus.WATCHING, false, null)
        val remoteState = localState.copy(libraryState = LibraryStatus.DROPPED)
        val result = reconciler.reconcile(local(localStateSnapshot(localState)), TrackingSnapshot(movies = listOf(remoteState)), provider)
        assertTrue(result.remoteOperations.isEmpty())
        assertTrue(result.conflicts.isEmpty())
        assertEquals(LibraryStatus.DROPPED, (result.localMutations.single() as LocalMutation.SetLibraryStatus).status)
    }

    @Test fun `local newer state becomes outbound operation`() {
        val localState = movie(LibraryStatus.COMPLETED, true, Instant.parse("2025-01-02T00:00:00Z"))
        val remoteState = movie(LibraryStatus.WATCHING, false, Instant.parse("2025-01-01T00:00:00Z"))
        val baseline = movie(LibraryStatus.WATCHING, false, Instant.parse("2025-01-01T00:00:00Z"))
        val at = localState.updatedAt!!.toEpochMilli()
        val local = LocalTrackingSnapshot(
            state = TrackingSnapshot(movies = listOf(localState)),
            baseline = TrackingSnapshot(movies = listOf(baseline)),
            dirtyMediaKeys = setOf("MOVIE:42"),
            pendingOperations = listOf(SyncOperation("state:MOVIE:42", SyncOperationType.LIBRARY_STATUS, MediaType.MOVIE, 42, "", value = "COMPLETED", sourceVersion = at)),
        )
        val result = reconciler.reconcile(local, TrackingSnapshot(movies = listOf(remoteState)), provider)
        assertTrue(result.remoteOperations.isEmpty())
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
        val localState = movie(LibraryStatus.COMPLETED, false, Instant.parse("2025-01-02T00:00:00Z"))
        val remoteState = movie(LibraryStatus.WATCHING, false, Instant.parse("2025-01-03T00:00:00Z"))
        val at = localState.updatedAt!!.toEpochMilli()
        val pending = SyncOperation("state:MOVIE:42", SyncOperationType.LIBRARY_STATUS, MediaType.MOVIE, 42, "", value = "COMPLETED", sourceVersion = at)
        val protectedLocal = LocalTrackingSnapshot(
            state = TrackingSnapshot(movies = listOf(localState)),
            dirtyMediaKeys = setOf("MOVIE:42"),
            pendingOperations = listOf(pending),
        )
        val result = reconciler.reconcile(protectedLocal, TrackingSnapshot(movies = listOf(remoteState)), provider)
        assertTrue(result.remoteOperations.isEmpty())
        assertTrue(result.localMutations.isEmpty())
    }

    @Test fun `episode progress compares every episode`() {
        val at = Instant.parse("2025-01-01T00:00:00Z")
        val localEpisode = TrackedEpisodeState(MediaIds(tmdb = 7), 1, 2, true, at, at)
        val remoteEpisode = localEpisode.copy(watched = false, updatedAt = at.plusSeconds(60))
        val result = reconciler.reconcile(local(TrackingSnapshot(episodes = listOf(localEpisode))), TrackingSnapshot(episodes = listOf(remoteEpisode)), provider)
        assertTrue(result.localMutations.single() is LocalMutation.SetWatched)
    }

    @Test fun `remote only episode watched state becomes local mutation`() {
        val at = Instant.parse("2025-01-01T00:00:00Z")
        val baseline = TrackedEpisodeState(MediaIds(tmdb = 7), 1, 2, false, null, at)
        val remote = baseline.copy(watched = true, watchedAt = at.plusSeconds(60))
        val result = reconciler.reconcile(localWithBaseline(TrackingSnapshot(episodes = listOf(baseline)), TrackingSnapshot(episodes = listOf(baseline))), TrackingSnapshot(episodes = listOf(remote)), provider)
        assertEquals(true, (result.localMutations.single() as LocalMutation.SetWatched).watched)
        assertTrue(result.conflicts.isEmpty())
    }

    @Test fun `remote only movie watched state becomes local mutation`() {
        val at = Instant.parse("2025-01-01T00:00:00Z")
        val baseline = movie(LibraryStatus.WATCHING, false, at)
        val remote = baseline.copy(watched = true, watchedAt = at.plusSeconds(60))
        val result = reconciler.reconcile(localWithBaseline(TrackingSnapshot(movies = listOf(baseline)), TrackingSnapshot(movies = listOf(baseline))), TrackingSnapshot(movies = listOf(remote)), provider)
        assertEquals(true, (result.localMutations.single() as LocalMutation.SetWatched).watched)
        assertTrue(result.conflicts.isEmpty())
    }

    @Test fun `stale pending row cannot override remote library change`() {
        val baseline = movie(LibraryStatus.PLAN_TO_WATCH, false, null)
        val stale = SyncOperation(
            "reconcile:library:tmdb:42:PLAN_TO_WATCH",
            SyncOperationType.LIBRARY_STATUS,
            MediaType.MOVIE,
            42,
            "",
            value = LibraryStatus.PLAN_TO_WATCH.name,
            sourceVersion = 1L,
        )
        val result = reconciler.reconcile(
            LocalTrackingSnapshot(
                state = TrackingSnapshot(movies = listOf(baseline)),
                baseline = TrackingSnapshot(movies = listOf(baseline)),
                pendingOperations = listOf(stale),
            ),
            TrackingSnapshot(movies = listOf(baseline.copy(libraryState = LibraryStatus.DROPPED))),
            provider,
        )
        assertEquals(LibraryStatus.DROPPED, (result.localMutations.single() as LocalMutation.SetLibraryStatus).status)
        assertTrue(result.remoteOperations.isEmpty())
        assertTrue(result.conflicts.isEmpty())
    }

    @Test fun `library intent does not protect independent movie history`() {
        val at = Instant.parse("2025-01-02T00:00:00Z")
        val localState = movie(LibraryStatus.COMPLETED, false, at)
        val baseline = movie(LibraryStatus.WATCHING, false, at)
        val result = reconciler.reconcile(
            LocalTrackingSnapshot(
                state = TrackingSnapshot(movies = listOf(localState)),
                baseline = TrackingSnapshot(movies = listOf(baseline)),
                dirtyMediaKeys = setOf("MOVIE:42"),
                pendingOperations = listOf(SyncOperation("state:MOVIE:42", SyncOperationType.LIBRARY_STATUS, MediaType.MOVIE, 42, "", value = "COMPLETED", sourceVersion = at.toEpochMilli())),
            ),
            TrackingSnapshot(movies = listOf(baseline.copy(watched = true, watchedAt = at.plusSeconds(60)))),
            provider,
        )
        assertTrue(result.localMutations.any { it is LocalMutation.SetWatched && it.watched })
    }

    @Test fun `completed library intent protects its coupled watched change`() {
        val at = Instant.parse("2025-01-02T00:00:00Z")
        val baseline = movie(LibraryStatus.WATCHING, false, at)
        val localState = movie(LibraryStatus.COMPLETED, true, at)
        val result = reconciler.reconcile(
            LocalTrackingSnapshot(
                state = TrackingSnapshot(movies = listOf(localState)),
                baseline = TrackingSnapshot(movies = listOf(baseline)),
                dirtyMediaKeys = setOf("MOVIE:42"),
                pendingOperations = listOf(SyncOperation("state:MOVIE:42", SyncOperationType.LIBRARY_STATUS, MediaType.MOVIE, 42, "", value = "COMPLETED", sourceVersion = at.toEpochMilli())),
            ),
            TrackingSnapshot(movies = listOf(baseline)),
            provider,
        )
        assertTrue(result.localMutations.isEmpty())
        assertTrue(result.remoteOperations.isEmpty())
    }

    @Test fun `missing timestamps are deterministic conflicts`() {
        val localState = movie(LibraryStatus.WATCHING, true, null)
        val remoteState = movie(LibraryStatus.COMPLETED, false, null)
        val result = reconciler.reconcile(local(TrackingSnapshot(movies = listOf(localState))), TrackingSnapshot(movies = listOf(remoteState)), provider)
        assertTrue(result.conflicts.isEmpty())
    }

    @Test fun `identity uses stable external ids rather than title`() {
        val localState = TrackedMovieState(MediaIds(tmdb = 99, imdb = "tt123"), LibraryStatus.WATCHING, false, updatedAt = Instant.parse("2025-01-01T00:00:00Z"))
        val remoteState = TrackedMovieState(MediaIds(tmdb = 99, imdb = "tt123"), LibraryStatus.COMPLETED, false, updatedAt = Instant.parse("2025-01-02T00:00:00Z"))
        val result = reconciler.reconcile(local(TrackingSnapshot(movies = listOf(localState))), TrackingSnapshot(movies = listOf(remoteState)), provider)
        assertEquals(1, result.localMutations.size)
    }

    private fun movie(status: LibraryStatus, watched: Boolean, at: Instant?) =
        TrackedMovieState(MediaIds(tmdb = 42), status, watched, at, at)

    private fun localStateSnapshot(state: TrackedMovieState) = TrackingSnapshot(movies = listOf(state))

    private fun local(state: TrackingSnapshot, vararg pending: SyncOperation) =
        LocalTrackingSnapshot(state = state, pendingOperations = pending.toList())

    private fun localWithBaseline(state: TrackingSnapshot, baseline: TrackingSnapshot, vararg pending: SyncOperation) =
        LocalTrackingSnapshot(state = state, baseline = baseline, pendingOperations = pending.toList())
}

