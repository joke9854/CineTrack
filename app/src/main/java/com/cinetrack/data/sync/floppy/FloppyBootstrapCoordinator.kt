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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Durable, restart-safe canonical seed targeted only to a configured Floppy SECONDARY. */
class FloppyBootstrapCoordinator(
    private val preferences: com.cinetrack.data.repository.AppPreferences,
    private val operationRepository: SyncOperationRepository,
    private val operationWriter: DurableSyncOperationWriter,
    private val canonicalSnapshot: suspend () -> TrackingSnapshot,
    private val instanceId: suspend () -> String = {
        preferences.floppySettingsNow()?.connectionId ?: "unknown"
    },
    private val verifyRemote: suspend (TrackingSnapshot) -> Boolean = { true },
) {
    private val mutex = Mutex()

    suspend fun start(): Int = mutex.withLock {
        try {
            val instance = instanceId()
            val existing = decodePlan(preferences.floppyBootstrapPlanRawNow())
                ?.takeIf { it.instanceId == instance && preferences.providerBootstrapStateNow(TrackingProviderId.FLOPPY) == ProviderBootstrapState.RUNNING }
            val snapshot = canonicalSnapshot()
            val operations = existing?.operations?.map(::toOperation) ?: buildOperations(instance, snapshot)
            preferences.setProviderBootstrapState(TrackingProviderId.FLOPPY, ProviderBootstrapState.RUNNING)
            // Keep the plan after Room retires acknowledged rows. The marker
            // is written before enqueueing so a restart can reconstruct the
            // same deterministic operation ids.
            if (existing == null) {
                preferences.setFloppyBootstrapPlanRaw(encodePlan(PersistedPlan(instance, operations.map(::toPersisted))))
            }
            operations.chunked(CHUNK_SIZE).forEach { chunk ->
                chunk.forEach { operation ->
                    val existing = operationRepository.deliveries(setOf(operation.id))
                    if (existing.isEmpty()) operationWriter.enqueueForProviders(operation, setOf(TrackingProviderId.FLOPPY))
                }
            }
            if (operations.isEmpty() && verifyRemote(snapshot)) {
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
        val plan = decodePlan(preferences.floppyBootstrapPlanRawNow())
        if (plan?.instanceId != instance) return@withLock
        val prefix = "bootstrap:$instance:"
        val planned = operationRepository.pending().filter { it.id.startsWith(prefix) }
        val incomplete = planned.any { operation ->
            operationRepository.deliveries(setOf(operation.id)).any { delivery ->
                delivery.providerId == TrackingProviderId.FLOPPY &&
                    delivery.status in setOf(DeliveryStatus.PENDING, DeliveryStatus.FAILED)
            }
        }
        if (!incomplete && verifyRemote(canonicalSnapshot())) {
            preferences.setProviderBootstrapState(TrackingProviderId.FLOPPY, ProviderBootstrapState.READY)
            preferences.clearFloppyBootstrapPlan()
        }
    }

    private fun movieLibrary(instance: String, id: Long, status: LibraryStatus, sourceVersion: Long = MutationGeneration.next()) = SyncOperation(
        id = "bootstrap:$instance:movie:$id:library", type = SyncOperationType.LIBRARY_STATUS,
        mediaType = MediaType.MOVIE, mediaId = id.toInt(), title = "", value = status.name,
        sourceVersion = sourceVersion,
    )
    private fun showLibrary(instance: String, id: Long, status: LibraryStatus?, sourceVersion: Long = MutationGeneration.next()) = SyncOperation(
        id = "bootstrap:$instance:show:$id:library", type = SyncOperationType.LIBRARY_STATUS,
        mediaType = MediaType.TV, mediaId = id.toInt(), title = "", value = status?.name ?: LibraryStatus.NONE.name,
        sourceVersion = sourceVersion,
    )
    private fun movieWatched(instance: String, id: Long, at: Instant?, sourceVersion: Long = MutationGeneration.next()) = SyncOperation(
        id = "bootstrap:$instance:movie:$id:watched", type = SyncOperationType.MOVIE_WATCHED,
        mediaType = MediaType.MOVIE, mediaId = id.toInt(), title = "", payload = at?.toString(),
        sourceVersion = sourceVersion,
    )
    private fun episodeWatched(instance: String, id: Long, season: Int, episode: Int, at: Instant?, sourceVersion: Long = MutationGeneration.next()) = SyncOperation(
        id = "bootstrap:$instance:episode:$id:$season:$episode", type = SyncOperationType.EPISODE_WATCHED,
        mediaType = MediaType.TV, mediaId = id.toInt(), title = "", payload = "$season:$episode${at?.let { ":$it" }.orEmpty()}",
        sourceVersion = sourceVersion,
    )

    private fun buildOperations(instance: String, snapshot: TrackingSnapshot): List<SyncOperation> = buildList {
        snapshot.movies.forEach { movie ->
            movie.ids.tmdb?.let { id ->
                movie.libraryState?.let { add(movieLibrary(instance, id, it)) }
                if (movie.watched && movie.watchedAt != null) add(movieWatched(instance, id, movie.watchedAt))
            }
        }
        snapshot.shows.forEach { show ->
            show.ids.tmdb?.let { id -> show.libraryState?.let { add(showLibrary(instance, id, it)) } }
        }
        snapshot.episodes.filter { it.watched }.forEach { episode ->
            episode.showIds.tmdb?.let { id -> add(episodeWatched(instance, id, episode.season, episode.episode, episode.watchedAt)) }
        }
    }

    @Serializable
    private data class PersistedPlan(val instanceId: String, val operations: List<PersistedOperation>)

    @Serializable
    private data class PersistedOperation(
        val id: String,
        val type: String,
        val mediaType: String,
        val mediaId: Int,
        val title: String,
        val value: String? = null,
        val payload: String? = null,
        val sourceVersion: Long,
    )

    private fun toPersisted(operation: SyncOperation) = PersistedOperation(operation.id, operation.type.name, operation.mediaType.name, operation.mediaId, operation.title, operation.value, operation.payload, operation.sourceVersion)
    private fun toOperation(operation: PersistedOperation) = SyncOperation(operation.id, SyncOperationType.valueOf(operation.type), MediaType.valueOf(operation.mediaType), operation.mediaId, operation.title, operation.value, operation.payload, operation.sourceVersion)
    private fun encodePlan(plan: PersistedPlan) = Json.encodeToString(PersistedPlan.serializer(), plan)
    private fun decodePlan(raw: String?): PersistedPlan? = raw?.let { runCatching { Json.decodeFromString(PersistedPlan.serializer(), it) }.getOrNull() }

    private companion object { const val CHUNK_SIZE = 100 }
}

