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
import com.cinetrack.domain.FloppyBootstrapProgress
import com.cinetrack.domain.FloppyBootstrapStage
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

    /** Clears a persisted plan when the configured Floppy identity changes. */
    suspend fun resetForInstanceChange(newInstanceId: String) = mutex.withLock {
        val old = decodePlan(preferences.floppyBootstrapPlanRawNow())
        if (old?.instanceId != newInstanceId ||
            preferences.providerBootstrapStateNow(TrackingProviderId.FLOPPY) != ProviderBootstrapState.READY
        ) {
            preferences.clearFloppyBootstrapPlan()
            preferences.setProviderBootstrapState(TrackingProviderId.FLOPPY, ProviderBootstrapState.NOT_STARTED)
        }
    }

    suspend fun start(): Int = mutex.withLock {
        try {
            val instance = instanceId()
            // A stale queued WorkManager record can outlive a successful
            // empty-plan bootstrap. Never rebuild a new plan for an instance
            // that is already semantically READY.
            if (preferences.providerBootstrapStateNow(TrackingProviderId.FLOPPY) == ProviderBootstrapState.READY &&
                decodePlan(preferences.floppyBootstrapPlanRawNow()) == null
            ) return@withLock 0
            val existing = decodePlan(preferences.floppyBootstrapPlanRawNow())
                ?.takeIf {
                    it.instanceId == instance &&
                        preferences.providerBootstrapStateNow(TrackingProviderId.FLOPPY) in
                            setOf(ProviderBootstrapState.RUNNING, ProviderBootstrapState.FAILED)
                }
            val snapshot = canonicalSnapshot()
            val operations = existing?.operations?.map(::toOperation) ?: buildFloppyBootstrapOperations(instance, snapshot)
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

    /** Returns the immutable operation plan, creating it once when necessary. */
    suspend fun ensurePlan(): List<SyncOperation> = mutex.withLock {
        val instance = instanceId()
        val existing = decodePlan(preferences.floppyBootstrapPlanRawNow())
            ?.takeIf { it.instanceId == instance }
        if (existing != null) return@withLock existing.operations.map(::toOperation)
        val operations = buildFloppyBootstrapOperations(instance, canonicalSnapshot())
        preferences.setFloppyBootstrapPlanRaw(encodePlan(PersistedPlan(instance, operations.map(::toPersisted))))
        preferences.setProviderBootstrapState(TrackingProviderId.FLOPPY, ProviderBootstrapState.RUNNING)
        operations
    }

    /** Snapshot used by WorkManager for durable item-count progress. */
    suspend fun progress(instance: String): FloppyBootstrapProgress = mutex.withLock {
        val plan = decodePlan(preferences.floppyBootstrapPlanRawNow())
            ?.takeIf { it.instanceId == instance }
        val total = plan?.operations?.size ?: 0
        val pending = if (plan == null) emptyList() else operationRepository.pending(plan.operations.mapTo(linkedSetOf()) { it.id })
        val pendingIds = pending.mapTo(linkedSetOf(), SyncOperation::id)
        val processed = (total - pendingIds.size).coerceIn(0, total)
        val state = preferences.providerBootstrapStateNow(TrackingProviderId.FLOPPY)
        val stage = when (state) {
            ProviderBootstrapState.READY -> FloppyBootstrapStage.COMPLETE
            ProviderBootstrapState.FAILED -> FloppyBootstrapStage.NEEDS_ATTENTION
            else -> if (processed > 0) FloppyBootstrapStage.SYNCING else FloppyBootstrapStage.PREPARING
        }
        FloppyBootstrapProgress(
            stage = stage,
            processed = processed,
            total = total,
            succeeded = processed,
            failed = 0,
            providerInstanceId = instance,
        )
    }

    suspend fun markReadyIfComplete(): Boolean = mutex.withLock {
        val state = preferences.providerBootstrapStateNow(TrackingProviderId.FLOPPY)
        if (state !in setOf(ProviderBootstrapState.RUNNING, ProviderBootstrapState.FAILED)) return@withLock state == ProviderBootstrapState.READY
        val instance = instanceId()
        val plan = decodePlan(preferences.floppyBootstrapPlanRawNow())
        if (plan?.instanceId != instance) return@withLock false
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
            return@withLock true
        }
        false
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

/** Pure operation construction kept separate so generation/timestamp
 * invariants can be tested without Android DataStore dependencies. */
internal fun buildFloppyBootstrapOperations(instance: String, snapshot: TrackingSnapshot): List<SyncOperation> = buildList {
    snapshot.movies.forEach { movie ->
        movie.ids.tmdb?.let { id ->
            val libraryState = movie.libraryState
            if (libraryState == LibraryStatus.COMPLETED && movie.watched) {
                // Completion and its exact play are one causal mutation. The
                // generation also anchors the deterministic fallback timestamp
                // for legacy rows with no usable watchedAt.
                val generation = MutationGeneration.next()
                val watchedAt = movie.watchedAt
                    ?: movie.updatedAt
                    ?: Instant.ofEpochMilli(generation)
                add(SyncOperation(
                    id = "bootstrap:$instance:movie:$id:library",
                    type = SyncOperationType.LIBRARY_STATUS,
                    mediaType = MediaType.MOVIE,
                    mediaId = id.toInt(),
                    title = "",
                    value = LibraryStatus.COMPLETED.name,
                    sourceVersion = generation,
                ))
                add(SyncOperation(
                    id = "bootstrap:$instance:movie:$id:watched",
                    type = SyncOperationType.MOVIE_WATCHED,
                    mediaType = MediaType.MOVIE,
                    mediaId = id.toInt(),
                    title = "",
                    payload = watchedAt.toString(),
                    sourceVersion = generation,
                ))
            } else {
                libraryState?.let { status ->
                    add(SyncOperation(
                        id = "bootstrap:$instance:movie:$id:library",
                        type = SyncOperationType.LIBRARY_STATUS,
                        mediaType = MediaType.MOVIE,
                        mediaId = id.toInt(),
                        title = "",
                        value = status.name,
                        sourceVersion = MutationGeneration.next(),
                    ))
                }
                if (movie.watched && movie.watchedAt != null) {
                    add(SyncOperation(
                        id = "bootstrap:$instance:movie:$id:watched",
                        type = SyncOperationType.MOVIE_WATCHED,
                        mediaType = MediaType.MOVIE,
                        mediaId = id.toInt(),
                        title = "",
                        payload = movie.watchedAt.toString(),
                        sourceVersion = MutationGeneration.next(),
                    ))
                }
            }
        }
    }
    snapshot.shows.forEach { show ->
        show.ids.tmdb?.let { id ->
            show.libraryState?.let { status ->
                add(SyncOperation(
                    id = "bootstrap:$instance:show:$id:library",
                    type = SyncOperationType.LIBRARY_STATUS,
                    mediaType = MediaType.TV,
                    mediaId = id.toInt(),
                    title = "",
                    value = status.name,
                    sourceVersion = MutationGeneration.next(),
                ))
            }
        }
    }
    snapshot.episodes.filter { it.watched }.forEach { episode ->
        episode.showIds.tmdb?.let { id ->
            add(SyncOperation(
                id = "bootstrap:$instance:episode:$id:${episode.season}:${episode.episode}",
                type = SyncOperationType.EPISODE_WATCHED,
                mediaType = MediaType.TV,
                mediaId = id.toInt(),
                title = "",
                payload = "${episode.season}:${episode.episode}${episode.watchedAt?.let { ":$it" }.orEmpty()}",
                sourceVersion = MutationGeneration.next(),
            ))
        }
    }
}

