package com.cinetrack.data.repository

import androidx.room.withTransaction
import com.cinetrack.data.local.AppDatabase
import com.cinetrack.data.local.PendingWriteEntity
import com.cinetrack.data.local.SyncOperationEntity
import com.cinetrack.data.local.UserMediaStateEntity
import com.cinetrack.data.local.WatchHistoryEntity
import com.cinetrack.data.sync.DurableSyncOperationWriter
import com.cinetrack.data.sync.MutationGeneration
import com.cinetrack.data.sync.SyncCoordinator
import com.cinetrack.data.sync.SyncOperation
import com.cinetrack.data.sync.SyncOperationType
import com.cinetrack.data.sync.TrackingRoutingMutex
import com.cinetrack.domain.LibraryStatus
import com.cinetrack.domain.MediaType
import com.cinetrack.domain.SyncConflictChoice
import com.cinetrack.domain.SyncOperationStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.withLock
import java.time.Instant

private sealed interface RemoteConflictPlan {
    data class Library(val status: LibraryStatus) : RemoteConflictPlan
    data class MovieWatched(val watched: Boolean) : RemoteConflictPlan
    data class EpisodeWatched(
        val watched: Boolean,
        val season: Int,
        val episode: Int,
    ) : RemoteConflictPlan
}

/** Largest persisted causal generation across state, operations, deliveries and pending writes. */
internal suspend fun persistedGenerationFloor(database: AppDatabase): Long? {
    val values = buildList {
        addAll(database.stateDao().stateSnapshot().map(UserMediaStateEntity::updatedAt))
        addAll(database.syncDao().syncOperations().map(SyncOperationEntity::createdAt))
        addAll(database.syncDao().allDeliveries().map { it.operationVersion })
        addAll(database.syncDao().pendingWrites().map { it.createdAt })
    }
    return values.maxOrNull()
}

/** Resolves Sync-operation conflicts (keep local / use remote) under the tracking routing lock. */
class SyncConflictResolver(
    private val database: AppDatabase,
    private val durableOperationWriter: DurableSyncOperationWriter,
    private val syncCoordinator: SyncCoordinator,
    private val trackingRoutingMutex: TrackingRoutingMutex,
    private val stateOperationId: (type: String, id: Int) -> String,
    private val rebuildLibraryRailInTransaction: suspend () -> Unit,
    private val enqueueSecondaryMirror: suspend (SyncOperation) -> Unit,
) {
    // Callers hold trackingRoutingMutex.
    private suspend fun nextCausalGenerationLocked(): Long =
        MutationGeneration.next(previous = persistedGenerationFloor(database))

    suspend fun resolveSyncConflict(operationId: String, choice: SyncConflictChoice): Result<Unit> =
        if (choice == SyncConflictChoice.KEEP_LOCAL) {
            resolveKeepLocalConflict(operationId)
        } else {
            resolveUseRemoteConflict(operationId)
        }

    private suspend fun resolveKeepLocalConflict(operationId: String): Result<Unit> = try {
        val operation = durableOperationWriter.enqueueFromCurrentState(
            removeConflictId = operationId,
        ) {
            val conflict = database.syncDao().syncOperation(operationId)
                ?: error("Conflict no longer exists")
            require(conflict.status == SyncOperationStatus.CONFLICT.name) {
                "Operation is not a conflict"
            }
            buildKeepLocalOperation(conflict)
        }
        // Network work starts only after the routing lock has been released.
        syncCoordinator.pushPending(setOf(operation.id)).getOrThrow()
        Result.success(Unit)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        Result.failure(error)
    }

    private suspend fun buildKeepLocalOperation(conflict: SyncOperationEntity): SyncOperation {
        val mediaType = runCatching { MediaType.valueOf(conflict.mediaType) }
            .getOrElse { error("Conflict media type is invalid") }
        return when (conflict.operation) {
            "LIBRARY_STATUS_CONFLICT", "LIBRARY_CONFLICT" -> {
                val state = database.stateDao().get(conflict.mediaType, conflict.mediaId)
                    ?: error("Local state is no longer available")
                SyncOperation(
                    id = stateOperationId(mediaType.name, state.mediaId),
                    type = SyncOperationType.LIBRARY_STATUS,
                    mediaType = mediaType,
                    mediaId = state.mediaId,
                    title = conflict.title,
                    value = state.status,
                    sourceVersion = state.updatedAt,
                )
            }
            "WATCHED_CONFLICT" -> {
                val state = database.stateDao().get(conflict.mediaType, conflict.mediaId)
                    ?: error("Local state is no longer available")
                val existing = database.syncDao().syncOperations()
                    .asSequence()
                    .filter { it.status in setOf(SyncOperationStatus.PENDING.name, SyncOperationStatus.FAILED.name) }
                    .filter { it.mediaType == mediaType.name && it.mediaId == state.mediaId }
                    .filter { it.operation in setOf(SyncOperationType.MOVIE_WATCHED.name, SyncOperationType.MOVIE_UNWATCHED.name) }
                    .filter { (it.operation == SyncOperationType.MOVIE_WATCHED.name) == state.watched }
                    .maxByOrNull(SyncOperationEntity::createdAt)
                    ?.let { it.toDomainOperation() }
                existing ?: SyncOperation(
                    id = "resolution:${conflict.operationId}",
                    type = if (state.watched) SyncOperationType.MOVIE_WATCHED else SyncOperationType.MOVIE_UNWATCHED,
                    mediaType = mediaType,
                    mediaId = state.mediaId,
                    title = conflict.title,
                    value = state.watched.toString(),
                    payload = if (state.watched) {
                        database.timelineDao().historySnapshot()
                            .asSequence()
                            .filter { it.mediaType == mediaType.name && it.mediaId == state.mediaId && it.season == null && it.episodeNumber == null }
                            .mapNotNull { it.watchedAt.toInstantOrNull() }
                            .maxOrNull()
                            ?.toString()
                    } else null,
                    sourceVersion = state.updatedAt,
                )
            }
            "EPISODE_WATCHED_CONFLICT" -> buildKeepLocalEpisodeOperation(conflict, mediaType)
            else -> error("Unsupported conflict field")
        }
    }

    private suspend fun buildKeepLocalEpisodeOperation(
        conflict: SyncOperationEntity,
        mediaType: MediaType,
    ): SyncOperation {
        require(mediaType == MediaType.TV) { "Episode conflicts must target TV media" }
        val season = conflict.season ?: error("Episode conflict season is missing")
        val episode = conflict.episode ?: error("Episode conflict number is missing")
        val title = episodeDisplayTitle(conflict.mediaId, season, episode, conflict.title)
        val exactWrite = database.syncDao().pendingWrites()
            .asSequence()
            .filter { it.mediaType == MediaType.TV.name && it.mediaId == conflict.mediaId }
            .filter { it.operation in setOf(SyncOperationType.EPISODE_WATCHED.name, SyncOperationType.EPISODE_UNWATCHED.name) }
            .filter { it.episodeParts() == (season to episode) }
            .maxByOrNull(PendingWriteEntity::createdAt)
        if (exactWrite != null) return exactWrite.toEpisodeOperation(title)
            ?: error("Episode write operation is invalid")

        val exactOperation = database.syncDao().syncOperations()
            .asSequence()
            .filter { it.status in setOf(SyncOperationStatus.PENDING.name, SyncOperationStatus.FAILED.name) }
            .filter { it.mediaType == MediaType.TV.name && it.mediaId == conflict.mediaId }
            .filter { it.season == season && it.episode == episode }
            .filter { it.operation in setOf(SyncOperationType.EPISODE_WATCHED.name, SyncOperationType.EPISODE_UNWATCHED.name) }
            .maxByOrNull(SyncOperationEntity::createdAt)
        if (exactOperation != null) return exactOperation.toDomainOperation()
            ?: error("Episode operation is invalid")

        val history = database.timelineDao().episodeHistoryForShow(MediaType.TV.name, conflict.mediaId)
        val matchingHistory = history.firstOrNull { it.season == season && it.episodeNumber == episode }
        val watched = matchingHistory != null ||
            (matchingHistory == null && conflict.localValue?.toBooleanStrictOrNull() == true)
        val watchedAt = matchingHistory?.watchedAt
            ?: Instant.now().toString()
        // History timestamps are provider event times, not local queue
        // generations. Allocate a fresh causal generation while the caller
        // still holds TrackingRoutingMutex.
        val sourceVersion = nextCausalGenerationLocked()
        val operationType = if (watched) SyncOperationType.EPISODE_WATCHED else SyncOperationType.EPISODE_UNWATCHED
        return SyncOperation(
            id = "resolution:${conflict.operationId}",
            type = operationType,
            mediaType = MediaType.TV,
            mediaId = conflict.mediaId,
            title = title,
            value = watched.toString(),
            payload = if (watched) "$season:$episode:$watchedAt" else "$season:$episode",
            sourceVersion = sourceVersion,
        )
    }

    private suspend fun resolveUseRemoteConflict(operationId: String): Result<Unit> {
        var mirrorOperation: SyncOperation? = null
        val result = trackingRoutingMutex.withLock {
            try {
        val initial = database.syncDao().syncOperation(operationId)
            ?: error("Conflict no longer exists")
        require(initial.status == SyncOperationStatus.CONFLICT.name) { "Operation is not a conflict" }
        val plan = validateRemoteConflict(initial)
        database.withTransaction {
            val conflict = database.syncDao().syncOperation(operationId)
                ?: error("Conflict no longer exists")
            require(conflict.status == SyncOperationStatus.CONFLICT.name) { "Operation is not a conflict" }
            require(
                conflict.operation == initial.operation &&
                    conflict.mediaType == initial.mediaType &&
                    conflict.mediaId == initial.mediaId &&
                    conflict.localValue == initial.localValue &&
                    conflict.remoteValue == initial.remoteValue &&
                    conflict.season == initial.season &&
                    conflict.episode == initial.episode,
            ) { "Conflict changed while resolving" }
            validateConflictGeneration(conflict, plan)
            val mirrorGeneration = nextCausalGenerationLocked()
            val previous = database.stateDao().get(conflict.mediaType, conflict.mediaId)
            mirrorOperation = when (plan) {
                is RemoteConflictPlan.Library -> SyncOperation(
                    id = "mirror:library:${conflict.mediaType}:${conflict.mediaId}",
                    type = SyncOperationType.LIBRARY_STATUS,
                    mediaType = MediaType.valueOf(conflict.mediaType),
                    mediaId = conflict.mediaId,
                    title = conflict.title.orEmpty(),
                    value = plan.status.name,
                    payload = if (plan.status == LibraryStatus.COMPLETED) Instant.ofEpochMilli(conflict.updatedAt).toString() else null,
                    sourceVersion = mirrorGeneration,
                )
                is RemoteConflictPlan.MovieWatched -> SyncOperation(
                    id = "mirror:movie-watched:${conflict.mediaId}",
                    type = if (plan.watched) SyncOperationType.MOVIE_WATCHED else SyncOperationType.MOVIE_UNWATCHED,
                    mediaType = MediaType.MOVIE,
                    mediaId = conflict.mediaId,
                    title = conflict.title.orEmpty(),
                    value = plan.watched.toString(),
                    payload = if (plan.watched) {
                        Instant.ofEpochMilli(conflict.updatedAt).toString()
                    } else {
                        previous?.status
                    },
                    sourceVersion = mirrorGeneration,
                )
                is RemoteConflictPlan.EpisodeWatched -> SyncOperation(
                    id = "mirror:episode-watched:${conflict.mediaId}:${plan.season}:${plan.episode}",
                    type = if (plan.watched) SyncOperationType.EPISODE_WATCHED else SyncOperationType.EPISODE_UNWATCHED,
                    mediaType = MediaType.TV,
                    mediaId = conflict.mediaId,
                    title = conflict.title.orEmpty(),
                    value = plan.watched.toString(),
                    payload = if (plan.watched) "${plan.season}:${plan.episode}:${Instant.ofEpochMilli(conflict.updatedAt)}" else "${plan.season}:${plan.episode}",
                    sourceVersion = mirrorGeneration,
                )
            }
            when (plan) {
                is RemoteConflictPlan.Library -> {
                    database.stateDao().upsert(
                        UserMediaStateEntity(
                            conflict.mediaType,
                            conflict.mediaId,
                            plan.status.name,
                            previous?.watched ?: false,
                            previous?.simklId,
                            System.currentTimeMillis(),
                            dirty = false,
                        ),
                    )
                    if (plan.status == LibraryStatus.NONE) {
                        database.timelineDao().deleteMediaHistory(conflict.mediaType, conflict.mediaId)
                    }
                    removeExactConflictIntent(conflict, plan)
                }
                is RemoteConflictPlan.MovieWatched -> {
                    if (plan.watched) {
                        database.timelineDao().deleteMediaHistory(conflict.mediaType, conflict.mediaId)
                        database.timelineDao().insertHistory(
                            WatchHistoryEntity(
                                mediaType = conflict.mediaType,
                                mediaId = conflict.mediaId,
                                watchedAt = Instant.ofEpochMilli(conflict.updatedAt).toString(),
                            ),
                        )
                    } else {
                        database.timelineDao().deleteMediaHistory(conflict.mediaType, conflict.mediaId)
                    }
                    database.stateDao().upsert(
                        UserMediaStateEntity(
                            conflict.mediaType,
                            conflict.mediaId,
                            previous?.status ?: LibraryStatus.NONE.name,
                            plan.watched,
                            previous?.simklId,
                            System.currentTimeMillis(),
                            dirty = false,
                        ),
                    )
                    removeExactConflictIntent(conflict, plan)
                }
                is RemoteConflictPlan.EpisodeWatched -> {
                    if (plan.watched) {
                        database.timelineDao().deleteEpisodeHistory(MediaType.TV.name, conflict.mediaId, plan.season, plan.episode)
                        database.timelineDao().insertHistory(
                            WatchHistoryEntity(
                                mediaType = MediaType.TV.name,
                                mediaId = conflict.mediaId,
                                season = plan.season,
                                episodeNumber = plan.episode,
                                watchedAt = Instant.ofEpochMilli(conflict.updatedAt).toString(),
                            ),
                        )
                    } else {
                        database.timelineDao().deleteEpisodeHistory(MediaType.TV.name, conflict.mediaId, plan.season, plan.episode)
                    }
                    removeExactConflictIntent(conflict, plan)
                }
            }
            rebuildLibraryRailInTransaction()
            database.syncDao().deleteOperation(operationId)
        }
        Result.success(Unit)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        Result.failure(error)
        }
    }
        if (result.isSuccess) mirrorOperation?.let { enqueueSecondaryMirror(it) }
        return result
    }

    private suspend fun validateRemoteConflict(conflict: SyncOperationEntity): RemoteConflictPlan {
        val mediaType = runCatching { MediaType.valueOf(conflict.mediaType) }
            .getOrElse { error("Conflict media type is invalid") }
        require(database.mediaDao().get(conflict.mediaType, conflict.mediaId) != null) {
            "Media is no longer available"
        }
        return when (conflict.operation) {
            "LIBRARY_STATUS_CONFLICT", "LIBRARY_CONFLICT" -> RemoteConflictPlan.Library(
                conflict.remoteValue?.let { runCatching { LibraryStatus.valueOf(it) }.getOrNull() }
                    ?: error("Remote library status is invalid"),
            )
            "WATCHED_CONFLICT" -> {
                require(mediaType == MediaType.MOVIE) { "Movie watched conflicts must target movies" }
                require(conflict.localValue?.toBooleanStrictOrNull() != null) { "Local watched value is invalid" }
                RemoteConflictPlan.MovieWatched(conflict.remoteValue?.toBooleanStrictOrNull()
                    ?: error("Remote watched value is invalid"))
            }
            "EPISODE_WATCHED_CONFLICT" -> {
                require(mediaType == MediaType.TV) { "Episode conflicts must target TV media" }
                require(conflict.localValue?.toBooleanStrictOrNull() != null) { "Local watched value is invalid" }
                RemoteConflictPlan.EpisodeWatched(
                    watched = conflict.remoteValue?.toBooleanStrictOrNull()
                        ?: error("Remote watched value is invalid"),
                    season = conflict.season ?: error("Episode conflict season is missing"),
                    episode = conflict.episode ?: error("Episode conflict number is missing"),
                )
            }
            else -> error("Unsupported conflict field")
        }
    }

    private suspend fun validateConflictGeneration(
        conflict: SyncOperationEntity,
        plan: RemoteConflictPlan,
    ) {
        val local = conflict.localValue?.toBooleanStrictOrNull()
        when (plan) {
            is RemoteConflictPlan.Library -> {
                val state = database.stateDao().get(conflict.mediaType, conflict.mediaId)
                require(state != null && state.updatedAt == conflict.createdAt && state.status == conflict.localValue) {
                    "Conflict changed while resolving"
                }
            }
            is RemoteConflictPlan.MovieWatched -> {
                val state = database.stateDao().get(conflict.mediaType, conflict.mediaId)
                require(state != null && state.updatedAt == conflict.createdAt && state.watched == local) {
                    "Conflict changed while resolving"
                }
                val matchingOperation = database.syncDao().syncOperations().any { operation ->
                    operation.status != SyncOperationStatus.CONFLICT.name &&
                        operation.mediaType == MediaType.MOVIE.name &&
                        operation.mediaId == conflict.mediaId &&
                        operation.createdAt == conflict.createdAt &&
                        operation.operation in setOf(SyncOperationType.MOVIE_WATCHED.name, SyncOperationType.MOVIE_UNWATCHED.name)
                }
                // A movie watched value may be coupled to state:COMPLETED;
                // absence of a standalone row is valid in that legacy shape.
                require(matchingOperation || state?.status == LibraryStatus.COMPLETED.name || state?.updatedAt == conflict.createdAt) {
                    "Conflict changed while resolving"
                }
            }
            is RemoteConflictPlan.EpisodeWatched -> {
                val matchingWrite = database.syncDao().pendingWrites().any { write ->
                    write.mediaType == MediaType.TV.name &&
                        write.mediaId == conflict.mediaId &&
                        write.createdAt == conflict.createdAt &&
                        write.operation in setOf(SyncOperationType.EPISODE_WATCHED.name, SyncOperationType.EPISODE_UNWATCHED.name) &&
                        write.payload.split(':', limit = 3).let { parts ->
                            parts.getOrNull(0)?.toIntOrNull() == plan.season &&
                                parts.getOrNull(1)?.toIntOrNull() == plan.episode &&
                                (write.operation == SyncOperationType.EPISODE_WATCHED.name) == local
                        }
                }
                val history = database.timelineDao().episodeHistoryForShow(MediaType.TV.name, conflict.mediaId)
                    .firstOrNull { it.season == plan.season && it.episodeNumber == plan.episode }
                val historyGeneration = history?.watchedAt?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
                require(matchingWrite || historyGeneration == conflict.createdAt) {
                    "Conflict changed while resolving"
                }
            }
        }
    }

    private suspend fun removeExactConflictIntent(conflict: SyncOperationEntity, plan: RemoteConflictPlan) {
        val ids = linkedSetOf<String>()
        val writes = database.syncDao().pendingWrites()
        when (plan) {
            is RemoteConflictPlan.Library -> {
                ids += stateOperationId(conflict.mediaType, conflict.mediaId)
            }
            is RemoteConflictPlan.MovieWatched -> {
                database.syncDao().syncOperations()
                    .filter { it.status != SyncOperationStatus.CONFLICT.name }
                    .filter { it.mediaType == MediaType.MOVIE.name && it.mediaId == conflict.mediaId }
                    .filter { it.operation in setOf(
                        SyncOperationType.MOVIE_WATCHED.name,
                        SyncOperationType.MOVIE_UNWATCHED.name,
                        SyncOperationType.MEDIA_HISTORY_REMOVE.name,
                    ) || (it.operation == SyncOperationType.LIBRARY_STATUS.name && conflict.localValue == "true") }
                    .filter {
                        it.operation == SyncOperationType.MEDIA_HISTORY_REMOVE.name ||
                            it.operation == SyncOperationType.LIBRARY_STATUS.name ||
                            (it.operation == SyncOperationType.MOVIE_WATCHED.name) == conflict.localValue?.toBooleanStrictOrNull()
                    }
                    .mapTo(ids, SyncOperationEntity::operationId)
                if (conflict.localValue?.toBooleanStrictOrNull() == true) {
                    ids += stateOperationId(MediaType.MOVIE.name, conflict.mediaId)
                }
            }
            is RemoteConflictPlan.EpisodeWatched -> {
                writes.filter { write ->
                    write.mediaType == MediaType.TV.name &&
                        write.mediaId == conflict.mediaId &&
                        write.operation in setOf(SyncOperationType.EPISODE_WATCHED.name, SyncOperationType.EPISODE_UNWATCHED.name) &&
                        write.episodeParts() == (plan.season to plan.episode) &&
                        (write.operation == SyncOperationType.EPISODE_WATCHED.name) == conflict.localValue?.toBooleanStrictOrNull()
                }.forEach { write ->
                    database.syncDao().deleteWrite(write.id)
                    ids += "write:${write.id}"
                }
                database.syncDao().syncOperations()
                    .filter { it.status != SyncOperationStatus.CONFLICT.name }
                    .filter { it.mediaType == MediaType.TV.name && it.mediaId == conflict.mediaId && it.season == plan.season && it.episode == plan.episode }
                    .filter { it.operation in setOf(SyncOperationType.EPISODE_WATCHED.name, SyncOperationType.EPISODE_UNWATCHED.name) }
                    .filter { (it.operation == SyncOperationType.EPISODE_WATCHED.name) == conflict.localValue?.toBooleanStrictOrNull() }
                    .mapTo(ids, SyncOperationEntity::operationId)
            }
        }
        ids.filter { it.startsWith("write:") }
            .mapNotNull { it.removePrefix("write:").toLongOrNull() }
            .forEach { writeId -> database.syncDao().deleteWrite(writeId) }
        if (ids.isNotEmpty()) {
            database.syncDao().deleteDeliveries(ids.toList())
            database.syncDao().deleteOperations(ids.toList())
        }
    }

    private fun SyncOperationEntity.toDomainOperation(): SyncOperation? {
        val type = runCatching { SyncOperationType.valueOf(operation) }.getOrNull() ?: return null
        val media = runCatching { MediaType.valueOf(mediaType) }.getOrNull() ?: return null
        val payload = entityPayloadOrEpisodeFallback()
        return SyncOperation(
            id = operationId,
            type = type,
            mediaType = media,
            mediaId = mediaId,
            title = title,
            value = localValue,
            payload = payload,
            sourceVersion = createdAt,
        )
    }

    private fun SyncOperationEntity.entityPayloadOrEpisodeFallback(): String? =
        payload ?: if (season != null && episode != null) "$season:$episode" else null

    private fun PendingWriteEntity.episodeParts(): Pair<Int?, Int?> {
        val parts = payload.split(':', limit = 3)
        return parts.getOrNull(0)?.toIntOrNull() to parts.getOrNull(1)?.toIntOrNull()
    }

    private fun PendingWriteEntity.toEpisodeOperation(title: String): SyncOperation? {
        val type = runCatching { SyncOperationType.valueOf(operation) }.getOrNull() ?: return null
        return SyncOperation(
            id = "write:$id",
            type = type,
            mediaType = MediaType.TV,
            mediaId = mediaId,
            title = title,
            value = (type == SyncOperationType.EPISODE_WATCHED).toString(),
            payload = payload,
            sourceVersion = createdAt,
        )
    }

    private suspend fun episodeDisplayTitle(
        showId: Int,
        season: Int,
        episode: Int,
        fallback: String,
    ): String {
        val showTitle = database.mediaDao().get(MediaType.TV.name, showId)?.title
            ?.takeIf(String::isNotBlank)
            ?: fallback.takeIf(String::isNotBlank)
            ?: "TV #$showId"
        val episodeTitle = database.mediaDao().episode(showId, season, episode)?.title
            ?.takeIf(String::isNotBlank)
        val label = "S${season.toString().padStart(2, '0')}E${episode.toString().padStart(2, '0')}"
        return listOf(showTitle, label, episodeTitle).filterNotNull().joinToString(" · ")
    }

    private fun String?.toInstantOrNull(): Instant? = this?.let { raw ->
        runCatching { Instant.parse(raw) }.getOrNull()
    }
}
