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
import com.cinetrack.data.sync.TrackingRole
import com.cinetrack.data.sync.DeliveryStatus
import com.cinetrack.data.sync.SyncOperation
import com.cinetrack.data.sync.SyncOperationType
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
    private val providerRegistry: TrackingProviderRegistry,
    private val onLocalStateChanged: () -> Unit,
) : LibraryRepository {
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
        database.withTransaction {
            val previous = database.stateDao().get(media.type.name, media.id)
            database.mediaDao().upsertMedia(listOf(media.toEntity()))
            database.stateDao().upsert(
                UserMediaStateEntity(
                    mediaType = media.type.name,
                    mediaId = media.id,
                    status = status.name,
                    watched = status == LibraryStatus.COMPLETED,
                    simklId = previous?.simklId,
                    dirty = true,
                ),
            )
            queueStateOperation(media, status)
            if (status == LibraryStatus.COMPLETED && previous?.watched != true) {
                database.timelineDao().insertHistory(
                    WatchHistoryEntity(mediaType = media.type.name, mediaId = media.id, watchedAt = Instant.now().toString()),
                )
            }
            if (status == LibraryStatus.NONE) {
                database.timelineDao().deleteMediaHistory(media.type.name, media.id)
            } else if (previous?.watched == true && status != LibraryStatus.COMPLETED) {
                database.timelineDao().deleteMediaHistory(media.type.name, media.id)
                database.syncDao().queue(
                    PendingWriteEntity(
                        operation = "MEDIA_HISTORY_REMOVE",
                        mediaType = media.type.name,
                        mediaId = media.id,
                        payload = previous.simklId?.toString().orEmpty(),
                    ),
                )
            }
            rebuildLibraryRail()
        }
        onLocalStateChanged()
    }

    override suspend fun markWatched(media: MediaCard) {
        database.withTransaction {
            val previous = database.stateDao().get(media.type.name, media.id)
            database.mediaDao().upsertMedia(listOf(media.toEntity()))
            database.stateDao().upsert(
                UserMediaStateEntity(
                    mediaType = media.type.name,
                    mediaId = media.id,
                    status = LibraryStatus.COMPLETED.name,
                    watched = true,
                    simklId = previous?.simklId,
                    dirty = true,
                ),
            )
            queueStateOperation(media, LibraryStatus.COMPLETED)
            database.timelineDao().insertHistory(
                WatchHistoryEntity(mediaType = media.type.name, mediaId = media.id, watchedAt = Instant.now().toString()),
            )
            rebuildLibraryRail()
        }
        onLocalStateChanged()
    }

    override suspend fun markEpisodeWatched(episode: EpisodeCard) {
        val watchedAt = Instant.now().toString()
        val operationId = database.withTransaction {
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
            database.stateDao().touch(MediaType.TV.name, episode.showId, System.currentTimeMillis())
            refreshLocalUpNext(episode.showId)
            val writeId = database.syncDao().queue(
                PendingWriteEntity(
                    operation = "EPISODE_WATCHED",
                    mediaType = MediaType.TV.name,
                    mediaId = episode.showId,
                    payload = "${episode.season}:${episode.number}:$watchedAt",
                ),
            )
            queueWriteOperation(writeId, "EPISODE_WATCHED", episode, episode.label)
            "write:$writeId"
        }
        onLocalStateChanged()
        if (syncCoordinator.isMainProviderConnected()) syncCoordinator.pushPending(setOf(operationId))
    }

    override suspend fun setEpisodeWatched(episode: EpisodeCard, watched: Boolean) {
        if (watched) return markEpisodeWatched(episode)
        val operationId = database.withTransaction {
            database.timelineDao().deleteEpisodeHistory(MediaType.TV.name, episode.showId, episode.season, episode.number)
            refreshLocalUpNext(episode.showId)
            val writeId = database.syncDao().queue(
                PendingWriteEntity(
                    operation = "EPISODE_UNWATCHED",
                    mediaType = MediaType.TV.name,
                    mediaId = episode.showId,
                    payload = "${episode.season}:${episode.number}",
                ),
            )
            queueWriteOperation(writeId, "EPISODE_UNWATCHED", episode, episode.label)
            "write:$writeId"
        }
        onLocalStateChanged()
        if (syncCoordinator.isMainProviderConnected()) syncCoordinator.pushPending(setOf(operationId))
    }

    override suspend fun setEpisodesWatched(episodes: List<EpisodeCard>, watched: Boolean) {
        val changed = episodes.distinctBy { Triple(it.showId, it.season, it.number) }
        if (changed.isEmpty()) return
        val operationIds = database.withTransaction {
            val ids = mutableListOf<String>()
            changed.forEach { episode ->
                val watchedAt = Instant.now().toString()
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
                    ),
                )
                queueWriteOperation(writeId, if (watched) "EPISODE_WATCHED" else "EPISODE_UNWATCHED", episode, episode.label)
                ids += "write:$writeId"
            }
            changed.map(EpisodeCard::showId).toSet().forEach { refreshLocalUpNext(it) }
            ids
        }
        onLocalStateChanged()
        if (syncCoordinator.isMainProviderConnected()) syncCoordinator.pushPending(operationIds.toSet())
    }

    private suspend fun queueStateOperation(media: MediaCard, status: LibraryStatus) {
        val updatedAt = database.stateDao().get(media.type.name, media.id)?.updatedAt ?: System.currentTimeMillis()
        // State operation keys are logical/media keys; delivery rows are generation-specific.
        // Remove any prior generation before creating the new intent in this transaction.
        database.syncDao().deleteDeliveries(listOf("state:${media.type.name}:${media.id}"))
        database.syncDao().upsertOperation(
            SyncOperationEntity(
                operationId = "state:${media.type.name}:${media.id}",
                operation = "LIBRARY_STATUS",
                mediaType = media.type.name,
                mediaId = media.id,
                title = media.title,
                status = SyncOperationStatus.PENDING.name,
                localValue = status.name,
                createdAt = updatedAt,
                updatedAt = updatedAt,
            ),
        )
        snapshotDeliveries(
            operationId = "state:${media.type.name}:${media.id}",
            operationVersion = updatedAt,
            type = SyncOperationType.LIBRARY_STATUS,
            mediaType = media.type,
            mediaId = media.id,
            value = status.name,
        )
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
        val config = providerRegistry.configuration()
        val rows = buildList {
            config.mainProvider?.let { provider ->
                add(com.cinetrack.data.local.SyncOperationDeliveryEntity(operationId = operationId, operationVersion = operationVersion, providerId = provider.name, status = DeliveryStatus.PENDING.name, required = true, roleAtEnqueue = TrackingRole.MAIN.name, createdAt = operationVersion, updatedAt = operationVersion))
            }
            config.secondaryProvider?.let { providerId ->
                val provider = providerRegistry.getProvider(providerId)
                val supported = provider?.capabilities?.supports(operation) == true
                add(com.cinetrack.data.local.SyncOperationDeliveryEntity(operationId = operationId, operationVersion = operationVersion, providerId = providerId.name, status = if (supported) DeliveryStatus.PENDING.name else DeliveryStatus.SKIPPED_UNSUPPORTED.name, required = supported, roleAtEnqueue = TrackingRole.SECONDARY.name, createdAt = operationVersion, updatedAt = operationVersion))
            }
        }
        if (rows.isNotEmpty()) database.syncDao().upsertDeliveries(rows)
    }

    private suspend fun queueWriteOperation(writeId: Long, operation: String, episode: EpisodeCard, value: String) {
        val title = database.mediaDao().get(MediaType.TV.name, episode.showId)?.title ?: "TV #${episode.showId}"
        database.syncDao().upsertOperation(
            SyncOperationEntity(
                operationId = "write:$writeId",
                operation = operation,
                mediaType = MediaType.TV.name,
                mediaId = episode.showId,
                title = title,
                status = SyncOperationStatus.PENDING.name,
                localValue = value,
            ),
        )
        val sourceVersion = database.syncDao().pendingWrite(writeId)?.createdAt ?: System.currentTimeMillis()
        snapshotDeliveries("write:$writeId", sourceVersion, SyncOperationType.valueOf(operation), MediaType.TV, episode.showId, value, "${episode.season}:${episode.number}")
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

