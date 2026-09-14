package com.cinetrack.data.sync.floppy

import com.cinetrack.data.sync.DurableSyncOperationWriter
import com.cinetrack.data.sync.ProviderBootstrapState
import com.cinetrack.data.sync.DeliveryStatus
import com.cinetrack.data.sync.SyncOperation
import com.cinetrack.data.sync.SyncOperationRepository
import com.cinetrack.data.sync.SyncOperationType
import com.cinetrack.data.sync.MutationGeneration
import com.cinetrack.data.sync.TrackingProviderId
import com.cinetrack.data.sync.TrackingSnapshot
import com.cinetrack.domain.LibraryStatus
import com.cinetrack.domain.MediaType
import java.time.Instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Durable, restart-safe canonical seed targeted only to a configured Floppy SECONDARY. */
class FloppyBootstrapCoordinator(
    private val preferences: com.cinetrack.data.repository.AppPreferences,
    private val operationRepository: SyncOperationRepository,
    private val operationWriter: DurableSyncOperationWriter,
    private val canonicalSnapshot: suspend () -> TrackingSnapshot,
    private val instanceId: suspend () -> String = {
        preferences.floppySettingsNow()?.connectionId ?: "unknown"
    },
    private val verifyRemote: suspend () -> Boolean = { true },
) {
    private val mutex = Mutex()

    suspend fun start(): Int = mutex.withLock {
        try {
            preferences.setProviderBootstrapState(TrackingProviderId.FLOPPY, ProviderBootstrapState.RUNNING)
            val instance = instanceId()
            val snapshot = canonicalSnapshot()
            val operations = buildList {
                snapshot.movies.forEach { movie ->
                    movie.ids.tmdb?.let { id ->
                        movie.libraryState?.let { add(movieLibrary(instance, id, it)) }
                        if (movie.watched) add(movieWatched(instance, id, movie.watchedAt))
                    }
                }
                snapshot.shows.forEach { show ->
                    show.ids.tmdb?.let { id -> show.libraryState?.let { add(showLibrary(instance, id, it)) } }
                }
                snapshot.episodes.filter { it.watched }.forEach { episode ->
                    episode.showIds.tmdb?.let { id -> add(episodeWatched(instance, id, episode.season, episode.episode, episode.watchedAt)) }
                }
            }
            // Keep the plan after Room retires acknowledged rows. The marker
            // is written before enqueueing so a restart can reconstruct the
            // same deterministic operation ids.
            preferences.setFloppyBootstrapPlan(instance, operations.size)
            operations.chunked(CHUNK_SIZE).forEach { chunk ->
                chunk.forEach { operation ->
                    val existing = operationRepository.deliveries(setOf(operation.id))
                    if (existing.isEmpty()) operationWriter.enqueueForProviders(operation, setOf(TrackingProviderId.FLOPPY))
                }
            }
            if (operations.isEmpty() && verifyRemote()) {
                preferences.setProviderBootstrapState(TrackingProviderId.FLOPPY, ProviderBootstrapState.READY)
                preferences.clearFloppyBootstrapPlan()
            }
            operations.size
        } catch (error: Throwable) {
            preferences.setProviderBootstrapState(TrackingProviderId.FLOPPY, ProviderBootstrapState.FAILED)
            throw error
        }
    }

    suspend fun markReadyIfComplete() = mutex.withLock {
        val state = preferences.providerBootstrapStateNow(TrackingProviderId.FLOPPY)
        if (state != ProviderBootstrapState.RUNNING) return@withLock
        val instance = instanceId()
        val plan = preferences.floppyBootstrapPlanNow()
        if (plan?.first != instance) return@withLock
        val prefix = "bootstrap:$instance:"
        val planned = operationRepository.pending().filter { it.id.startsWith(prefix) }
        val incomplete = planned.any { operation ->
            operationRepository.deliveries(setOf(operation.id)).any { delivery ->
                delivery.providerId == TrackingProviderId.FLOPPY &&
                    delivery.status in setOf(DeliveryStatus.PENDING, DeliveryStatus.FAILED)
            }
        }
        if (!incomplete && verifyRemote()) {
            preferences.setProviderBootstrapState(TrackingProviderId.FLOPPY, ProviderBootstrapState.READY)
            preferences.clearFloppyBootstrapPlan()
        }
    }

    private fun movieLibrary(instance: String, id: Long, status: LibraryStatus) = SyncOperation(
        id = "bootstrap:$instance:movie:$id:library", type = SyncOperationType.LIBRARY_STATUS,
        mediaType = MediaType.MOVIE, mediaId = id.toInt(), title = "", value = status.name,
        sourceVersion = MutationGeneration.next(),
    )
    private fun showLibrary(instance: String, id: Long, status: LibraryStatus?) = SyncOperation(
        id = "bootstrap:$instance:show:$id:library", type = SyncOperationType.LIBRARY_STATUS,
        mediaType = MediaType.TV, mediaId = id.toInt(), title = "", value = status?.name ?: LibraryStatus.NONE.name,
        sourceVersion = MutationGeneration.next(),
    )
    private fun movieWatched(instance: String, id: Long, at: Instant?) = SyncOperation(
        id = "bootstrap:$instance:movie:$id:watched", type = SyncOperationType.MOVIE_WATCHED,
        mediaType = MediaType.MOVIE, mediaId = id.toInt(), title = "", payload = at?.toString(),
        sourceVersion = MutationGeneration.next(),
    )
    private fun episodeWatched(instance: String, id: Long, season: Int, episode: Int, at: Instant?) = SyncOperation(
        id = "bootstrap:$instance:episode:$id:$season:$episode", type = SyncOperationType.EPISODE_WATCHED,
        mediaType = MediaType.TV, mediaId = id.toInt(), title = "", payload = "$season:$episode:${at ?: Instant.now()}",
        sourceVersion = MutationGeneration.next(),
    )

    private companion object { const val CHUNK_SIZE = 100 }
}
