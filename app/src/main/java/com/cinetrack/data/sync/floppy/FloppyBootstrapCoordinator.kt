package com.cinetrack.data.sync.floppy

import com.cinetrack.data.sync.DurableSyncOperationWriter
import com.cinetrack.data.sync.ProviderBootstrapState
import com.cinetrack.data.sync.SyncOperation
import com.cinetrack.data.sync.SyncOperationRepository
import com.cinetrack.data.sync.SyncOperationType
import com.cinetrack.data.sync.MutationGeneration
import com.cinetrack.data.sync.TrackingProviderId
import com.cinetrack.data.sync.TrackingSnapshot
import com.cinetrack.domain.LibraryStatus
import com.cinetrack.domain.MediaType
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Durable, restart-safe canonical seed targeted only to a configured Floppy SECONDARY. */
class FloppyBootstrapCoordinator(
    private val preferences: com.cinetrack.data.repository.AppPreferences,
    private val operationRepository: SyncOperationRepository,
    private val operationWriter: DurableSyncOperationWriter,
    private val canonicalSnapshot: suspend () -> TrackingSnapshot,
) {
    private val mutex = Mutex()

    suspend fun start(): Int = mutex.withLock {
        preferences.setProviderBootstrapState(TrackingProviderId.FLOPPY, ProviderBootstrapState.RUNNING)
        val snapshot = canonicalSnapshot()
        val operations = buildList {
            snapshot.movies.forEach { movie ->
                movie.libraryState?.let { status -> movie.ids.tmdb?.let { add(movieLibrary(it, status)) } }
                if (movie.watched) add(movieWatched(movie.ids.tmdb ?: return@forEach, movie.watchedAt))
            }
            snapshot.shows.forEach { show ->
                show.libraryState?.let { add(showLibrary(show.ids.tmdb ?: return@let, it)) }
            }
            snapshot.episodes.filter { it.watched }.forEach { episode ->
                episode.showIds.tmdb?.let { add(episodeWatched(it, episode.season, episode.episode, episode.watchedAt)) }
            }
        }
        operations.chunked(CHUNK_SIZE).forEach { chunk ->
            chunk.forEach { operation ->
                operationWriter.enqueueForProviders(operation, setOf(TrackingProviderId.FLOPPY))
            }
        }
        if (operations.isEmpty()) preferences.setProviderBootstrapState(TrackingProviderId.FLOPPY, ProviderBootstrapState.READY)
        operations.size
    }

    suspend fun markReadyIfComplete() = mutex.withLock {
        val state = preferences.providerBootstrapStateNow(TrackingProviderId.FLOPPY)
        if (state != ProviderBootstrapState.RUNNING) return@withLock
        val pending = operationRepository.pending().filter { operation ->
            operationRepository.deliveries(setOf(operation.id)).any { it.providerId == TrackingProviderId.FLOPPY }
        }
        if (pending.isEmpty()) preferences.setProviderBootstrapState(TrackingProviderId.FLOPPY, ProviderBootstrapState.READY)
    }

    private fun movieLibrary(id: Long, status: LibraryStatus) = SyncOperation(
        id = "bootstrap:movie:$id:library:${UUID.randomUUID()}", type = SyncOperationType.LIBRARY_STATUS,
        mediaType = MediaType.MOVIE, mediaId = id.toInt(), title = "", value = status.name,
        sourceVersion = MutationGeneration.next(),
    )
    private fun showLibrary(id: Long, status: LibraryStatus) = SyncOperation(
        id = "bootstrap:show:$id:library:${UUID.randomUUID()}", type = SyncOperationType.LIBRARY_STATUS,
        mediaType = MediaType.TV, mediaId = id.toInt(), title = "", value = status.name,
        sourceVersion = MutationGeneration.next(),
    )
    private fun movieWatched(id: Long, at: Instant?) = SyncOperation(
        id = "bootstrap:movie:$id:watched:${UUID.randomUUID()}", type = SyncOperationType.MOVIE_WATCHED,
        mediaType = MediaType.MOVIE, mediaId = id.toInt(), title = "", payload = at?.toString(),
        sourceVersion = MutationGeneration.next(),
    )
    private fun episodeWatched(id: Long, season: Int, episode: Int, at: Instant?) = SyncOperation(
        id = "bootstrap:episode:$id:$season:$episode:${UUID.randomUUID()}", type = SyncOperationType.EPISODE_WATCHED,
        mediaType = MediaType.TV, mediaId = id.toInt(), title = "", payload = "$season:$episode:${at ?: Instant.now()}",
        sourceVersion = MutationGeneration.next(),
    )

    private companion object { const val CHUNK_SIZE = 100 }
}
