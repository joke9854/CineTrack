package com.cinetrack.data.library

import androidx.room.withTransaction
import com.cinetrack.data.local.AppDatabase
import com.cinetrack.data.local.MediaRailEntity
import com.cinetrack.data.local.PendingWriteEntity
import com.cinetrack.data.local.SyncOperationEntity
import com.cinetrack.data.local.UpNextEntity
import com.cinetrack.data.local.UserMediaStateEntity
import com.cinetrack.data.local.WatchHistoryEntity
import com.cinetrack.data.local.toEntity
import com.cinetrack.data.repository.AppPreferences
import com.cinetrack.data.sync.SyncCoordinator
import com.cinetrack.data.sync.TrackingProviderRegistry
import com.cinetrack.data.sync.DurableTrackingQueue
import com.cinetrack.data.sync.TrackingRoutingMutex
import com.cinetrack.data.sync.SyncOperation
import com.cinetrack.data.sync.SyncOperationType
import com.cinetrack.data.sync.logicalField
import com.cinetrack.domain.EpisodeCard
import com.cinetrack.domain.LibraryStatus
import com.cinetrack.domain.MediaCard
import com.cinetrack.domain.MediaType
import com.cinetrack.domain.RailIds
import com.cinetrack.domain.SyncOperationStatus
import com.cinetrack.domain.releaseDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId

/** CineTrack's local-first user state boundary. */
interface LibraryRepository {
    fun observeLocalChanges(): Flow<Set<String>>
    suspend fun setLibraryStatus(media: MediaCard, status: LibraryStatus)
    suspend fun markWatched(media: MediaCard)
    suspend fun markEpisodeWatched(episode: EpisodeCard)
    suspend fun setEpisodeWatched(episode: EpisodeCard, watched: Boolean)
    suspend fun setEpisodesWatched(episodes: List<EpisodeCard>, watched: Boolean) {
        episodes.forEach { setEpisodeWatched(it, watched) }
    }
}

class RoomLibraryRepository(
    private val database: AppDatabase,
    private val preferences: AppPreferences,
    private val syncCoordinator: SyncCoordinator,
    private val onLocalStateChanged: () -> Unit,
    private val providerRegistry: TrackingProviderRegistry,
    private val routingMutex: TrackingRoutingMutex,
) : LibraryRepository {
    private val durableQueue = DurableTrackingQueue(providerRegistry, routingMutex)
    override fun observeLocalChanges(): Flow<Set<String>> = database.invalidationTracker.createFlow(
        "media",
        "media_rails",
        "user_media_state",
        "playback",
        "watch_history",
        "up_next",
        "sync_state",
        emitInitialState = false,
    )

    override suspend fun setLibraryStatus(media: MediaCard, status: LibraryStatus) {
        val mutationVersion = System.currentTimeMillis()
        val operationIds = routingMutex.withLock { database.withTransaction {
            val ids = mutableSetOf("state:${media.type.name}:${media.id}")
            val previous = database.stateDao().get(media.type.name, media.id)
            database.mediaDao().upsertMedia(listOf(media.toEntity()))
            database.stateDao().upsert(
                UserMediaStateEntity(
                    mediaType = media.type.name,
                    mediaId = media.id,
                    status = status.name,
                    watched = status == LibraryStatus.COMPLETED,
                    simklId = previous?.simklId,
                    updatedAt = mutationVersion,
                    dirty = true,
                ),
            )
            queueStateOperation(media, status)
            if (media.type == MediaType.MOVIE) {
                queueMovieWatchedOperation(media, status == LibraryStatus.COMPLETED, mutationVersion)
            }
            if (status == LibraryStatus.COMPLETED && previous?.watched != true) {
                database.timelineDao().insertHistory(
                    WatchHistoryEntity(mediaType = media.type.name, mediaId = media.id, watchedAt = Instant.ofEpochMilli(mutationVersion).toString()),
                )
            }
            if (status == LibraryStatus.NONE) {
                database.timelineDao().deleteMediaHistory(media.type.name, media.id)
            } else if (previous?.watched == true && status != LibraryStatus.COMPLETED && media.type != MediaType.MOVIE) {
                database.timelineDao().deleteMediaHistory(media.type.name, media.id)
                val writeId = database.syncDao().queue(
                    PendingWriteEntity(
                        operation = "MEDIA_HISTORY_REMOVE",
                        mediaType = media.type.name,
                        mediaId = media.id,
                        payload = previous.simklId?.toString().orEmpty(),
                        createdAt = mutationVersion,
                    ),
                )
                queueWriteOperation(
                    writeId = writeId,
                    operation = "MEDIA_HISTORY_REMOVE",
                    mediaType = media.type,
                    mediaId = media.id,
                    title = media.title,
                    value = "MEDIA_HISTORY_REMOVE",
                    payload = previous.simklId?.toString().orEmpty(),
                )
                ids += "write:$writeId"
            }
            if (media.type == MediaType.MOVIE) ids += "movie-watched:${media.type.name}:${media.id}"
            rebuildLibraryRail()
            ids
        } }
        onLocalStateChanged()
        if (syncCoordinator.isMainProviderConnected()) {
            syncCoordinator.pushPending(operationIds)
        }
    }

    override suspend fun markWatched(media: MediaCard) {
        val mutationVersion = System.currentTimeMillis()
        val watchedAt = Instant.ofEpochMilli(mutationVersion).toString()
        val operationIds = routingMutex.withLock { database.withTransaction {
            val previous = database.stateDao().get(media.type.name, media.id)
            database.mediaDao().upsertMedia(listOf(media.toEntity()))
            database.stateDao().upsert(
                UserMediaStateEntity(
                    mediaType = media.type.name,
                    mediaId = media.id,
                    status = LibraryStatus.COMPLETED.name,
                    watched = true,
                    simklId = previous?.simklId,
                    updatedAt = mutationVersion,
                    dirty = true,
                ),
            )
            queueStateOperation(media, LibraryStatus.COMPLETED)
            if (media.type == MediaType.MOVIE) {
                queueMovieWatchedOperation(media, watched = true, mutationVersion)
            }
            database.timelineDao().insertHistory(
                WatchHistoryEntity(mediaType = media.type.name, mediaId = media.id, watchedAt = watchedAt),
            )
            rebuildLibraryRail()
            buildSet {
                add("state:${media.type.name}:${media.id}")
                if (media.type == MediaType.MOVIE) add("movie-watched:${media.type.name}:${media.id}")
            }
        } }
        onLocalStateChanged()
        if (syncCoordinator.isMainProviderConnected()) {
            syncCoordinator.pushPending(operationIds)
        }
    }

    override suspend fun markEpisodeWatched(episode: EpisodeCard) {
        val mutationVersion = System.currentTimeMillis()
        val watchedAt = Instant.ofEpochMilli(mutationVersion).toString()
        val operationId = routingMutex.withLock { database.withTransaction {
            database.timelineDao().insertHistory(
                WatchHistoryEntity(
                    mediaType = MediaType.TV.name,
                    mediaId = episode.showId,
                    episodeId = episode.id,
                    season = episode.season,
                    episodeNumber = episode.number,
                    episodeTitle = episode.title,
                    watchedAt = watchedAt,
                ),
            )
            database.stateDao().touch(MediaType.TV.name, episode.showId, mutationVersion)
            refreshLocalUpNext(episode.showId)
            val writeId = database.syncDao().queue(
                PendingWriteEntity(
                    operation = "EPISODE_WATCHED",
                    mediaType = MediaType.TV.name,
                    mediaId = episode.showId,
                    payload = "${episode.season}:${episode.number}:$watchedAt",
                    createdAt = mutationVersion,
                ),
            )
            queueWriteOperation(writeId, "EPISODE_WATCHED", episode, episode.label)
            "write:$writeId"
        } }
        onLocalStateChanged()
        if (syncCoordinator.isMainProviderConnected()) syncCoordinator.pushPending(setOf(operationId))
    }

    override suspend fun setEpisodeWatched(episode: EpisodeCard, watched: Boolean) {
        if (watched) return markEpisodeWatched(episode)
        val mutationVersion = System.currentTimeMillis()
        val operationId = routingMutex.withLock { database.withTransaction {
            database.timelineDao().deleteEpisodeHistory(MediaType.TV.name, episode.showId, episode.season, episode.number)
            refreshLocalUpNext(episode.showId)
            val writeId = database.syncDao().queue(
                PendingWriteEntity(
                    operation = "EPISODE_UNWATCHED",
                    mediaType = MediaType.TV.name,
                    mediaId = episode.showId,
                    payload = "${episode.season}:${episode.number}",
                    createdAt = mutationVersion,
                ),
            )
            queueWriteOperation(writeId, "EPISODE_UNWATCHED", episode, episode.label)
            "write:$writeId"
        } }
        onLocalStateChanged()
        if (syncCoordinator.isMainProviderConnected()) syncCoordinator.pushPending(setOf(operationId))
    }

    override suspend fun setEpisodesWatched(episodes: List<EpisodeCard>, watched: Boolean) {
        val changed = episodes.distinctBy { Triple(it.showId, it.season, it.number) }
        if (changed.isEmpty()) return
        val mutationVersion = System.currentTimeMillis()
        val watchedAt = Instant.ofEpochMilli(mutationVersion).toString()
        val operationIds = routingMutex.withLock { database.withTransaction {
            val ids = mutableListOf<String>()
            changed.forEach { episode ->
                if (watched) {
                    database.timelineDao().insertHistory(
                        WatchHistoryEntity(mediaType = MediaType.TV.name, mediaId = episode.showId, episodeId = episode.id, season = episode.season, episodeNumber = episode.number, episodeTitle = episode.title, watchedAt = watchedAt),
                    )
                } else {
                    database.timelineDao().deleteEpisodeHistory(MediaType.TV.name, episode.showId, episode.season, episode.number)
                }
                val writeId = database.syncDao().queue(
                        PendingWriteEntity(
                            operation = if (watched) "EPISODE_WATCHED" else "EPISODE_UNWATCHED",
                            mediaType = MediaType.TV.name,
                            mediaId = episode.showId,
                            payload = if (watched) "${episode.season}:${episode.number}:$watchedAt" else "${episode.season}:${episode.number}",
                            createdAt = mutationVersion,
                        ),
                )
                queueWriteOperation(writeId, if (watched) "EPISODE_WATCHED" else "EPISODE_UNWATCHED", episode, episode.label)
                ids += "write:$writeId"
            }
            changed.map(EpisodeCard::showId).toSet().forEach { refreshLocalUpNext(it) }
            ids
        } }
        onLocalStateChanged()
        if (syncCoordinator.isMainProviderConnected()) syncCoordinator.pushPending(operationIds.toSet())
    }

    private suspend fun queueStateOperation(media: MediaCard, status: LibraryStatus) {
        val updatedAt = database.stateDao().get(media.type.name, media.id)?.updatedAt ?: System.currentTimeMillis()
        persistOperation(
            SyncOperation(
                id = "state:${media.type.name}:${media.id}",
                type = SyncOperationType.LIBRARY_STATUS,
                mediaType = media.type,
                mediaId = media.id,
                title = media.title,
                value = status.name,
                sourceVersion = updatedAt,
            ),
        )
    }

    private suspend fun queueMovieWatchedOperation(media: MediaCard, watched: Boolean, mutationVersion: Long) {
        persistOperation(
            SyncOperation(
                id = "movie-watched:${media.type.name}:${media.id}",
                type = if (watched) SyncOperationType.MOVIE_WATCHED else SyncOperationType.MOVIE_UNWATCHED,
                mediaType = MediaType.MOVIE,
                mediaId = media.id,
                title = media.title,
                value = watched.toString(),
                payload = if (watched) Instant.ofEpochMilli(mutationVersion).toString() else null,
                sourceVersion = mutationVersion,
            ),
        )
    }

    /** Persists one operation and supersedes older generations atomically. */
    private suspend fun persistOperation(operation: SyncOperation) {
        val field = operation.logicalField()
        val existing = database.syncDao().syncOperations()
        val staleIds = if (field == null) emptySet() else existing.filter {
            it.operationId != operation.id && it.logicalField() == field
        }.mapTo(linkedSetOf(), SyncOperationEntity::operationId)
        if (staleIds.isNotEmpty()) database.syncDao().supersedeAllDeliveries(staleIds.toList())
        val oldGeneration = database.syncDao().deliveries(listOf(operation.id))
            .any { it.operationVersion != operation.sourceVersion && it.status in setOf("PENDING", "FAILED") }
        if (oldGeneration) database.syncDao().supersedeAllDeliveries(listOf(operation.id))
        database.syncDao().upsertOperation(
            SyncOperationEntity(
                operationId = operation.id,
                operation = operation.type.name,
                mediaType = operation.mediaType.name,
                mediaId = operation.mediaId,
                title = operation.title,
                status = SyncOperationStatus.PENDING.name,
                localValue = operation.value,
                createdAt = operation.sourceVersion,
                updatedAt = operation.sourceVersion,
                season = operation.payload?.split(':', limit = 3)?.getOrNull(0)?.toIntOrNull(),
                episode = operation.payload?.split(':', limit = 3)?.getOrNull(1)?.toIntOrNull(),
            ),
        )
        snapshotDeliveries(
            operationId = operation.id,
            operationVersion = operation.sourceVersion,
            type = operation.type,
            mediaType = operation.mediaType,
            mediaId = operation.mediaId,
            value = operation.value,
            payload = operation.payload,
        )
        retireTerminalOperations(staleIds)
    }

    private suspend fun retireTerminalOperations(operationIds: Set<String>) {
        operationIds.forEach { id ->
            val rows = database.syncDao().deliveries(listOf(id))
            val required = rows.filter { it.required }
            if (required.isEmpty() || required.any { it.status !in setOf("ACKNOWLEDGED", "SKIPPED_UNSUPPORTED", "CANCELLED_PROVIDER_REMOVED", "SUPERSEDED") }) return@forEach
            id.removePrefix("write:").toLongOrNull()?.let { database.syncDao().deleteWrite(it) }
            database.syncDao().deleteOperation(id)
            database.syncDao().deleteDeliveries(listOf(id))
        }
    }

    private suspend fun snapshotDeliveries(
        operationId: String,
        operationVersion: Long,
        type: SyncOperationType,
        mediaType: MediaType,
        mediaId: Int,
        value: String?,
        payload: String? = null,
    ) {
        val operation = SyncOperation(operationId, type, mediaType, mediaId, "", value, payload, operationVersion)
        val rows = durableQueue.snapshotUnlocked(operation).map { delivery ->
            com.cinetrack.data.local.SyncOperationDeliveryEntity(
                operationId = delivery.operationId,
                operationVersion = delivery.operationVersion,
                providerId = delivery.providerId.name,
                status = delivery.status.name,
                required = delivery.required,
                roleAtEnqueue = delivery.roleAtEnqueue.name,
                attemptCount = delivery.attemptCount,
                lastError = delivery.lastError,
                createdAt = delivery.createdAt,
                updatedAt = delivery.updatedAt,
            )
        }
        if (rows.isNotEmpty()) database.syncDao().upsertDeliveries(rows)
    }

    private suspend fun queueWriteOperation(writeId: Long, operation: String, episode: EpisodeCard, value: String) {
        val title = database.mediaDao().get(MediaType.TV.name, episode.showId)?.title ?: "TV #${episode.showId}"
        queueWriteOperation(writeId, operation, MediaType.TV, episode.showId, title, value, "${episode.season}:${episode.number}")
    }

    private suspend fun queueWriteOperation(
        writeId: Long,
        operation: String,
        mediaType: MediaType,
        mediaId: Int,
        title: String,
        value: String,
        payload: String,
    ) {
        val sourceVersion = database.syncDao().pendingWrite(writeId)?.createdAt ?: System.currentTimeMillis()
        persistOperation(
            SyncOperation(
                id = "write:$writeId",
                type = SyncOperationType.valueOf(operation),
                mediaType = mediaType,
                mediaId = mediaId,
                title = title,
                value = value,
                payload = database.syncDao().pendingWrite(writeId)?.payload ?: payload,
                sourceVersion = sourceVersion,
            ),
        )
    }

    private suspend fun refreshLocalUpNext(showId: Int) {
        val configuredZone = preferences.metadataTimezone.first()
        val releaseZone = if (configuredZone == "system") ZoneId.systemDefault()
        else runCatching { ZoneId.of(configuredZone) }.getOrDefault(ZoneId.systemDefault())
        val releaseNow = Instant.now()
        val excludeSpecials = preferences.excludeSpecials.first()
        val watched = watchedEpisodeNumbers(showId)
        val lastWatched = watched.asSequence().filter { it.first > 0 }
            .maxWithOrNull(compareBy<Pair<Int, Int>>({ it.first }, { it.second }))
        val candidates = database.mediaDao().episodesForShow(showId).asSequence()
            .map { entity ->
                EpisodeCard(
                    id = entity.tmdbId ?: 0,
                    showId = entity.showId,
                    season = entity.season,
                    number = entity.number,
                    title = entity.title,
                    overview = entity.overview,
                    airDate = entity.airDate,
                    stillUrl = entity.stillPath?.let { "https://image.tmdb.org/t/p/w780$it" },
                    runtimeMinutes = entity.runtimeMinutes,
                )
            }
            .filter { !excludeSpecials || it.season > 0 }
            .filter { releaseDateTime(it.airDate, releaseZone)?.toInstant()?.let { time -> !time.isAfter(releaseNow) } == true }
            .filterNot { (it.season to it.number) in watched }
            .sortedWith(compareBy(EpisodeCard::season, EpisodeCard::number))
            .toList()
        val next = lastWatched?.let { last ->
            candidates.firstOrNull { it.season > last.first || (it.season == last.first && it.number > last.second) }
        } ?: candidates.firstOrNull()
        database.upNextDao().delete(showId)
        if (next != null) {
            database.upNextDao().upsertAll(
                listOf(
                    UpNextEntity(
                        showId = showId,
                        episodeId = next.id.takeIf { it > 0 },
                        season = next.season,
                        episodeNumber = next.number,
                        episodeTitle = next.title,
                        episodeAirDate = next.airDate,
                        durationMinutes = next.runtimeMinutes,
                        refreshedAt = System.currentTimeMillis(),
                    ),
                ),
            )
        }
    }

    private suspend fun watchedEpisodeNumbers(showId: Int): Set<Pair<Int, Int>> =
        database.timelineDao().episodeHistoryForShow(MediaType.TV.name, showId).mapNotNull { history ->
            val season = history.season ?: return@mapNotNull null
            val episode = history.episodeNumber ?: return@mapNotNull null
            season to episode
        }.toSet()

    private suspend fun rebuildLibraryRail() {
        val states = database.stateDao().stateSnapshot()
            .filter { it.status != LibraryStatus.NONE.name }
            .sortedByDescending(UserMediaStateEntity::updatedAt)
        val mediaByKey = database.mediaDao().mediaSnapshot().associateBy { "${it.mediaType}:${it.tmdbId}" }
        database.mediaDao().clearRail(RailIds.LIBRARY)
        database.mediaDao().upsertRails(
            states.mapNotNull { mediaByKey["${it.mediaType}:${it.mediaId}"] }
                .mapIndexed { index, item -> MediaRailEntity(RailIds.LIBRARY, item.mediaType, item.tmdbId, index) },
        )
    }
}
