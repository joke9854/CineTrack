package com.cinetrack.data.sync.floppy

import androidx.room.withTransaction
import com.cinetrack.data.local.MediaEntity
import com.cinetrack.data.local.SyncStateEntity
import com.cinetrack.data.local.UserMediaStateEntity
import com.cinetrack.data.local.WatchHistoryEntity
import com.cinetrack.data.repository.ProgressRefreshRequest
import com.cinetrack.data.sync.MainSyncHost
import com.cinetrack.data.sync.ProviderPushResult
import com.cinetrack.data.sync.ProviderSyncOutcome
import com.cinetrack.data.sync.SyncLogicalField
import com.cinetrack.data.sync.SyncOperation
import com.cinetrack.data.sync.SyncOperationType
import com.cinetrack.data.sync.TrackingProviderId
import com.cinetrack.data.sync.TrackingSyncError
import com.cinetrack.data.sync.logicalField
import com.cinetrack.data.sync.logicalFieldKey
import com.cinetrack.domain.LibraryStatus
import com.cinetrack.domain.MediaType
import com.cinetrack.domain.SyncOperationStatus
import com.cinetrack.domain.SyncProgress
import com.cinetrack.domain.SyncReport
import com.cinetrack.domain.SyncStage
import java.time.Instant
import java.time.OffsetDateTime
import kotlinx.coroutines.CancellationException

/**
 * Floppy's MAIN pass. Floppy keeps every play at its exact instant, so this
 * pass mirrors that state play for play instead of reconciling one watched
 * flag per item:
 *
 * 1. the coordinator-selected operations go out through the normal Floppy
 *    transport (idempotent client event ids, exact timestamps);
 * 2. the first pass reads Floppy's whole library (snapshot); later passes
 *    follow the change feed from a cursor. Plays are only ever added, except
 *    when Floppy reports an item removed (`in_library: false`): Floppy can
 *    hold fewer plays than this device (its initial sync sent one watch per
 *    movie), so a missing play is never read as a deletion;
 * 3. everything is applied in one Room transaction together with the cursor,
 *    skipping items that still carry an unsent or newer local intent. The
 *    cursor never moves past a skipped item, so its remote state is read
 *    again once the local intent is delivered.
 */
class FloppySyncEngine(
    private val hostProvider: () -> MainSyncHost,
) {
    suspend fun sync(
        provider: FloppyTrackingProvider,
        operations: List<SyncOperation>,
        onProgress: (SyncProgress) -> Unit,
    ): ProviderSyncOutcome {
        val host = hostProvider()
        val database = host.database
        val preferences = host.preferences
        val previousReport = preferences.syncReportNow()
        val previousSuccessfulSync = database.syncDao().get(AREA_ALL)?.lastSuccessfulSync
        fun progress(value: Float, stage: SyncStage) =
            onProgress(SyncProgress(true, value, stage, null, previousSuccessfulSync))
        return try {
            run(host, provider, operations, previousReport, ::progress, onProgress)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            val report = previousReport.copy(
                pendingLocalChanges = database.stateDao().pendingStates().size + database.syncDao().pendingWrites().size,
                failedOperations = 1,
                databaseUntouched = true,
            )
            preferences.saveSyncReport(report)
            onProgress(SyncProgress(false, 0f, SyncStage.ERROR, error.message, previousSuccessfulSync, report))
            throw error
        }
    }

    private suspend fun run(
        host: MainSyncHost,
        provider: FloppyTrackingProvider,
        operations: List<SyncOperation>,
        previousReport: SyncReport,
        progress: (Float, SyncStage) -> Unit,
        onProgress: (SyncProgress) -> Unit,
    ): ProviderSyncOutcome {
        val database = host.database
        val instanceId = provider.currentDeliveryInstanceId()
            ?: throw TrackingSyncError.AuthenticationRequired(TrackingProviderId.FLOPPY)
        val startedAt = System.nanoTime()
        fun since(mark: Long) = (System.nanoTime() - mark) / 1_000_000
        host.repairSyncQueue()
        progress(.08f, SyncStage.AUTH)
        val statesBefore = database.stateDao().stateSnapshot().associateBy { "${it.mediaType}:${it.mediaId}" }

        // 1. Deliver the exact MAIN intents first, so the pull below already
        // contains them and cannot undo them.
        val operationIds = operations.mapTo(linkedSetOf(), SyncOperation::id)
        val push = if (operations.isEmpty()) ProviderPushResult(emptySet()) else provider.push(operations)
        val completed = push.completedOperationIds.intersect(operationIds)
        val unmatched = push.unmatchedOperationIds.filterKeys { it in operationIds && it !in completed }
        val rejected = push.rejectedOperationIds.filterKeys { it in operationIds && it !in completed && it !in unmatched }
        val deferred = operationIds - completed - unmatched.keys - rejected.keys
        val pushMs = since(startedAt)
        val pullStartedAt = System.nanoTime()
        progress(.25f, SyncStage.ACTIVITY)

        // 2. Read Floppy's state: whole library once, then only what changed.
        provider.registerAppConnection(instanceId)
        val area = "$AREA_PREFIX$instanceId"
        val storedCursor = database.syncDao().get(area)?.remoteTimestamp?.toLongOrNull()
        val snapshotMode = storedCursor == null
        val remoteItems = linkedMapOf<String, FloppySyncItemDto>()
        var nextCursor: Long
        if (storedCursor == null) {
            var after: Long? = null
            var firstCursor: Long? = null
            do {
                val page = provider.syncItemsSnapshot(instanceId, after, PAGE_SIZE)
                if (firstCursor == null) firstCursor = page.cursor
                page.results.forEach { remoteItems[it.syncKey()] = it }
                after = page.nextAfter
            } while (after != null)
            nextCursor = firstCursor ?: 0L
        } else {
            nextCursor = storedCursor
            var pages = 0
            do {
                val page = provider.syncItemsChanges(instanceId, nextCursor, PAGE_SIZE)
                page.results.forEach { item ->
                    val key = item.syncKey()
                    remoteItems.remove(key)
                    remoteItems[key] = item
                }
                nextCursor = page.cursor
                pages++
            } while (page.hasMore && pages < MAX_CHANGE_PAGES)
        }
        val pullMs = since(pullStartedAt)
        val applyStartedAt = System.nanoTime()
        progress(.45f, SyncStage.HISTORY)

        // Fields whose local intent was not delivered in this pass keep their
        // local value; Floppy's version is read again after delivery.
        val protectedAtStart = operations.filter { it.id !in completed }.mapNotNullTo(hashSetOf()) { it.logicalFieldKey() }
        val committedAt = System.currentTimeMillis()
        val applied = AppliedChanges()

        val committed = host.withMainAuthority(TrackingProviderId.FLOPPY) {
            database.withTransaction {
                val protectedFields = HashSet(protectedAtStart)
                for (entity in database.syncDao().syncOperations()) {
                    if (entity.operationId in completed) continue
                    if (entity.status !in ACTIVE_STATUSES) continue
                    if (host.syncOperationRepository.currentIntentTargetsProvider(entity.operationId, entity.createdAt, TrackingProviderId.FLOPPY)) {
                        entity.logicalField()?.key?.let(protectedFields::add)
                    }
                }
                for (write in database.syncDao().pendingWrites()) {
                    val operationId = "write:${write.id}"
                    if (operationId in completed) continue
                    if (host.syncOperationRepository.currentIntentTargetsProvider(operationId, write.createdAt, TrackingProviderId.FLOPPY)) {
                        write.logicalField()?.key?.let(protectedFields::add)
                    }
                }
                val history = database.timelineDao().historySnapshot()
                val movieHistory = history.filter { it.mediaType == MediaType.MOVIE.name && it.season == null }.groupBy { it.mediaId }
                val episodeHistory = history.filter { it.mediaType == MediaType.TV.name && it.season != null && it.episodeNumber != null }
                    .groupBy { Triple(it.mediaId, it.season!!, it.episodeNumber!!) }
                // A play Floppy filed under another coordinate (absolute
                // numbering) is the same play, recognised by its exact instant.
                val showPlayInstants = episodeHistory.entries
                    .groupBy({ it.key.first }, { entry -> entry.value.mapNotNull { it.watchedAt.toInstantOrNull() } })
                    .mapValues { (_, lists) -> lists.flatten().toHashSet() }
                val knownMedia = database.mediaDao().mediaSnapshot().mapTo(hashSetOf()) { "${it.mediaType}:${it.tmdbId}" }
                val newMedia = mutableListOf<MediaEntity>()
                val inserts = mutableListOf<WatchHistoryEntity>()
                val deletes = mutableListOf<Long>()
                val states = mutableListOf<UserMediaStateEntity>()
                var skippedSequence: Long? = null

                fun skip(item: FloppySyncItemDto) {
                    applied.skipped++
                    val sequence = item.sequence ?: return
                    skippedSequence = minOf(skippedSequence ?: sequence, sequence)
                }

                suspend fun locallyChanged(type: MediaType, id: Int): Boolean {
                    val current = database.stateDao().get(type.name, id) ?: return false
                    val before = statesBefore["${type.name}:$id"]
                    return current.dirty && (before == null || current.updatedAt > before.updatedAt)
                }

                for (item in remoteItems.values) {
                    val type = item.mediaType()
                    val tmdb = item.localTmdbId()
                    if (type == null || tmdb == null) {
                        applied.unsupported++
                        continue
                    }
                    if (item.type == TYPE_EPISODE) {
                        val season = item.seasonNumber
                        val number = item.episodeNumber
                        if (season == null || number == null) {
                            applied.unsupported++
                            continue
                        }
                        if (SyncLogicalField.episodeWatched(tmdb, season, number).key in protectedFields) {
                            skip(item)
                            continue
                        }
                        val plays = applyPlays(
                            item = item,
                            local = episodeHistory[Triple(tmdb, season, number)].orEmpty(),
                            replace = removedOnFloppy(item, snapshotMode),
                            inserts = inserts,
                            deletes = deletes,
                            elsewhere = showPlayInstants[tmdb].orEmpty(),
                        ) { watchedAt ->
                            WatchHistoryEntity(
                                mediaType = MediaType.TV.name,
                                mediaId = tmdb,
                                season = season,
                                episodeNumber = number,
                                episodeTitle = item.title.takeIf(String::isNotBlank),
                                watchedAt = watchedAt.toString(),
                            )
                        }
                        if (plays.changed) {
                            applied.episodes++
                            applied.mirrors += when {
                                plays.latestAdded != null -> Mirror.EpisodeWatched(tmdb, season, number, plays.latestAdded)
                                plays.remaining == 0 -> Mirror.EpisodeUnwatched(tmdb, season, number)
                                else -> null
                            } ?: continue
                        }
                        continue
                    }

                    // Movies and shows: plays (movies only), then library status.
                    var watched: Boolean? = null
                    var latestPlay: Instant? = null
                    if (type == MediaType.MOVIE) {
                        if (SyncLogicalField.movieWatched(tmdb).key in protectedFields) {
                            skip(item)
                        } else {
                            val plays = applyPlays(
                                item = item,
                                local = movieHistory[tmdb].orEmpty(),
                                replace = removedOnFloppy(item, snapshotMode),
                                inserts = inserts,
                                deletes = deletes,
                            ) { watchedAt ->
                                WatchHistoryEntity(mediaType = MediaType.MOVIE.name, mediaId = tmdb, watchedAt = watchedAt.toString())
                            }
                            watched = plays.remaining > 0
                            latestPlay = plays.latest
                            if (plays.changed) {
                                applied.movies++
                                when {
                                    plays.latestAdded != null -> applied.mirrors += Mirror.MovieWatched(tmdb, plays.latestAdded)
                                    plays.remaining == 0 -> applied.mirrors += Mirror.MovieUnwatched(tmdb)
                                }
                            }
                        }
                    }
                    val libraryField = SyncLogicalField.library(type, tmdb).key
                    if (libraryField in protectedFields || locallyChanged(type, tmdb)) {
                        skip(item)
                        continue
                    }
                    val previous = database.stateDao().get(type.name, tmdb)
                    val status = item.libraryStatus()
                        ?: if (!item.inLibrary && !snapshotMode) LibraryStatus.NONE else null
                    val nextStatus = status?.name ?: previous?.status ?: continue
                    val nextWatched = when {
                        watched != null -> watched
                        status != null && type == MediaType.TV -> status == LibraryStatus.COMPLETED
                        else -> previous?.watched ?: false
                    }
                    if (previous == null && nextStatus == LibraryStatus.NONE.name) continue
                    if (previous != null && previous.status == nextStatus && previous.watched == nextWatched && !previous.dirty) continue
                    states += UserMediaStateEntity(
                        mediaType = type.name,
                        mediaId = tmdb,
                        status = nextStatus,
                        watched = nextWatched,
                        simklId = previous?.simklId,
                        updatedAt = latestPlay?.toEpochMilli() ?: previous?.updatedAt ?: committedAt,
                        dirty = false,
                    )
                    if (previous?.status != nextStatus) {
                        applied.mirrors += Mirror.Library(type, tmdb, LibraryStatus.valueOf(nextStatus))
                        if (previous == null || previous.status == LibraryStatus.NONE.name) applied.added++
                        if (nextStatus == LibraryStatus.NONE.name) applied.removed++
                    }
                    if (nextStatus != LibraryStatus.NONE.name && "${type.name}:$tmdb" !in knownMedia && item.title.isNotBlank()) {
                        newMedia += item.toMediaEntity(type, tmdb)
                        knownMedia += "${type.name}:$tmdb"
                    }
                }

                if (newMedia.isNotEmpty()) database.mediaDao().upsertMedia(newMedia)
                deletes.distinct().chunked(500).forEach { database.timelineDao().deleteHistoryRows(it) }
                if (inserts.isNotEmpty()) database.timelineDao().insertHistoryItems(inserts)
                if (states.isNotEmpty()) database.stateDao().upsertAll(states)
                applied.libraryChanged = states.isNotEmpty()
                applied.tvLibraryChanged = states.any { it.mediaType == MediaType.TV.name }
                applied.episodeHistoryChanged = (inserts + history.filter { it.id in deletes })
                    .any { it.mediaType == MediaType.TV.name }
                applied.historyChanged = inserts.isNotEmpty() || deletes.isNotEmpty()
                if (applied.libraryChanged) host.rebuildLibraryRailInTransaction()
                // The feed resumes before the first skipped item, so nothing
                // Floppy changed is lost while a local intent is outstanding.
                val cursorToStore = floppyCursorToStore(storedCursor, nextCursor, skippedSequence)
                database.syncDao().upsertAll(
                    listOf(
                        SyncStateEntity(area, cursorToStore.toString(), committedAt),
                        SyncStateEntity(AREA_ALL, database.syncDao().get(AREA_ALL)?.remoteTimestamp, committedAt),
                    ),
                )
                true
            }
        }
        if (committed == null) {
            // MAIN changed while the pass ran: the delivered intents are still
            // known outcomes, but the downloaded state must not be applied.
            return ProviderSyncOutcome(
                itemsChanged = false,
                report = previousReport,
                acknowledgedOperationIds = completed,
                deferredOperationIds = deferred,
                unmatchedOperationIds = unmatched,
                rejectedOperationIds = rejected,
            )
        }

        val applyMs = since(applyStartedAt)
        val mirrorStartedAt = System.nanoTime()
        host.preferences.markTrackingChecked(TrackingProviderId.FLOPPY, committedAt)
        // Remote-only changes still reach the SECONDARY provider.
        for (mirror in applied.mirrors) {
            if (!host.isMainProviderCurrent(TrackingProviderId.FLOPPY)) break
            host.enqueueSecondaryMirror(mirror.toOperation(host.nextCausalGeneration(), database), TrackingProviderId.FLOPPY)
        }
        val mirrorMs = since(mirrorStartedAt)
        progress(.78f, SyncStage.COMMIT)

        val progressStartedAt = System.nanoTime()
        val progressChanged = host.refreshProgressCache(
            ProgressRefreshRequest(
                tvLibraryChanged = applied.tvLibraryChanged,
                episodeHistoryChanged = applied.episodeHistoryChanged || operations.any { it.mediaType == MediaType.TV },
            ),
        ) { value -> progress(value, SyncStage.PROCESSING) }
        val report = SyncReport(
            downloaded = remoteItems.size,
            uploaded = completed.size,
            added = applied.added,
            removed = applied.removed,
            unchanged = (remoteItems.size - applied.movies - applied.episodes - applied.added - applied.removed).coerceAtLeast(0),
            pendingLocalChanges = database.stateDao().pendingStates().size + database.syncDao().pendingWrites().size,
            failedOperations = rejected.size,
            lastFullSync = if (snapshotMode) committedAt else previousReport.lastFullSync,
            lastIncrementalSync = committedAt,
            databaseUntouched = !applied.libraryChanged && !applied.historyChanged && !progressChanged,
        )
        host.preferences.saveSyncReport(report)
        host.preferences.appendErrorLog(
            "${java.time.Instant.now()}  Floppy main sync: ${if (snapshotMode) "full" else "changes"} items=${remoteItems.size} " +
                "pushed=${completed.size}/${operations.size} added=${applied.added} removed=${applied.removed} mirrored=${applied.mirrors.size} " +
                "push=${pushMs}ms pull=${pullMs}ms apply=${applyMs}ms mirror=${mirrorMs}ms progress=${since(progressStartedAt)}ms total=${since(startedAt)}ms",
        )
        onProgress(SyncProgress(false, 1f, SyncStage.COMPLETE, lastSuccessfulSync = committedAt, report = report))
        return ProviderSyncOutcome(
            itemsChanged = applied.libraryChanged || applied.historyChanged || completed.isNotEmpty() || progressChanged,
            report = report,
            acknowledgedOperationIds = completed,
            deferredOperationIds = deferred,
            unmatchedOperationIds = unmatched,
            rejectedOperationIds = rejected,
        )
    }

    private fun applyPlays(
        item: FloppySyncItemDto,
        local: List<WatchHistoryEntity>,
        replace: Boolean,
        inserts: MutableList<WatchHistoryEntity>,
        deletes: MutableList<Long>,
        elsewhere: Set<Instant> = emptySet(),
        build: (Instant) -> WatchHistoryEntity,
    ): FloppyPlayPlan {
        val plan = planFloppyPlays(item.plays, local, replace, elsewhere)
        plan.added.forEach { inserts += build(it) }
        plan.removed.forEach { deletes += it.id }
        return plan
    }

    private class AppliedChanges {
        var movies = 0
        var episodes = 0
        var added = 0
        var removed = 0
        var skipped = 0
        var unsupported = 0
        var libraryChanged = false
        var tvLibraryChanged = false
        var episodeHistoryChanged = false
        var historyChanged = false
        val mirrors = mutableListOf<Mirror>()
    }

    /** A remote-only change, queued for the SECONDARY provider only. */
    private sealed interface Mirror {
        data class Library(val type: MediaType, val id: Int, val status: LibraryStatus) : Mirror
        data class MovieWatched(val id: Int, val watchedAt: Instant) : Mirror
        data class MovieUnwatched(val id: Int) : Mirror
        data class EpisodeWatched(val showId: Int, val season: Int, val episode: Int, val watchedAt: Instant) : Mirror
        data class EpisodeUnwatched(val showId: Int, val season: Int, val episode: Int) : Mirror
    }

    private suspend fun Mirror.toOperation(generation: Long, database: com.cinetrack.data.local.AppDatabase): SyncOperation {
        suspend fun title(type: MediaType, id: Int) = database.mediaDao().get(type.name, id)?.title ?: "${type.name} #$id"
        return when (this) {
            is Mirror.Library -> SyncOperation(
                id = "mirror:library:${type.name}:$id",
                type = SyncOperationType.LIBRARY_STATUS,
                mediaType = type,
                mediaId = id,
                title = title(type, id),
                value = status.name,
                sourceVersion = generation,
            )
            is Mirror.MovieWatched -> SyncOperation(
                id = "mirror:movie-watched:$id",
                type = SyncOperationType.MOVIE_WATCHED,
                mediaType = MediaType.MOVIE,
                mediaId = id,
                title = title(MediaType.MOVIE, id),
                value = "true",
                payload = watchedAt.toString(),
                sourceVersion = generation,
            )
            is Mirror.MovieUnwatched -> SyncOperation(
                id = "mirror:movie-watched:$id",
                type = SyncOperationType.MOVIE_UNWATCHED,
                mediaType = MediaType.MOVIE,
                mediaId = id,
                title = title(MediaType.MOVIE, id),
                value = "false",
                payload = database.stateDao().get(MediaType.MOVIE.name, id)?.status,
                sourceVersion = generation,
            )
            is Mirror.EpisodeWatched -> SyncOperation(
                id = "mirror:episode-watched:$showId:$season:$episode",
                type = SyncOperationType.EPISODE_WATCHED,
                mediaType = MediaType.TV,
                mediaId = showId,
                title = title(MediaType.TV, showId),
                value = "true",
                payload = "$season:$episode:$watchedAt",
                sourceVersion = generation,
            )
            is Mirror.EpisodeUnwatched -> SyncOperation(
                id = "mirror:episode-watched:$showId:$season:$episode",
                type = SyncOperationType.EPISODE_UNWATCHED,
                mediaType = MediaType.TV,
                mediaId = showId,
                title = title(MediaType.TV, showId),
                value = "false",
                payload = "$season:$episode",
                sourceVersion = generation,
            )
        }
    }

    companion object {
        /** One cursor per Floppy connection: a different account starts over. */
        const val AREA_PREFIX = "floppy_items:"
        private const val AREA_ALL = "all"
        private const val PAGE_SIZE = 500
        private const val MAX_CHANGE_PAGES = 40
        private const val TYPE_EPISODE = "episode"
        private val ACTIVE_STATUSES = setOf(SyncOperationStatus.PENDING.name, SyncOperationStatus.FAILED.name)
    }
}

/** One item's local play changes, matched to Floppy's plays by exact instant. */
internal class FloppyPlayPlan(
    val added: List<Instant>,
    val removed: List<WatchHistoryEntity>,
    val remaining: Int,
    val latest: Instant?,
) {
    val changed: Boolean = added.isNotEmpty() || removed.isNotEmpty()
    val latestAdded: Instant? = added.maxOrNull()
}

/**
 * Brings one item's local plays in line with Floppy's, play for play.
 * Snapshot mode only adds; the change feed ([replace]) also removes plays
 * Floppy no longer has. Plays are matched by exact instant, so rewatches stay
 * distinct and a play is never moved to another time. A play Floppy keeps
 * without a time is never invented locally, and its presence makes the item
 * add-only so a local row is never dropped for it. [elsewhere] holds instants
 * this device already has for the same show under another coordinate.
 */
internal fun planFloppyPlays(
    remotePlays: List<FloppySyncPlayDto>,
    local: List<WatchHistoryEntity>,
    replace: Boolean,
    elsewhere: Set<Instant> = emptySet(),
): FloppyPlayPlan {
    val untimed = remotePlays.any { it.watchedAt.isNullOrBlank() }
    val remote = remotePlays.mapNotNullTo(linkedSetOf()) { it.watchedAt.toInstantOrNull() }
    val localInstants = local.mapNotNullTo(hashSetOf()) { it.watchedAt.toInstantOrNull() }
    val added = remote.filter { it !in localInstants && it !in elsewhere }
    val removed = if (replace && !untimed) {
        local.filter { row -> row.watchedAt.toInstantOrNull()?.let { it !in remote } == true }
    } else emptyList()
    val removedInstants = removed.mapNotNullTo(hashSetOf()) { it.watchedAt.toInstantOrNull() }
    val latest = ((localInstants - removedInstants) + added).maxOrNull()
    return FloppyPlayPlan(added, removed, local.size - removed.size + added.size, latest)
}

/** Local plays are removed only for an explicit removal in the change feed. */
internal fun removedOnFloppy(item: FloppySyncItemDto, snapshotMode: Boolean): Boolean =
    !snapshotMode && !item.inLibrary

/** The feed position to persist: never past an item skipped for a pending
 * local intent, so Floppy's version of it is read again later. */
internal fun floppyCursorToStore(storedCursor: Long?, nextCursor: Long, skippedSequence: Long?): Long =
    skippedSequence?.let { (it - 1).coerceAtLeast(storedCursor ?: 0L) } ?: nextCursor

private fun FloppySyncItemDto.syncKey(): String = "$type:$source:$mediaId:${seasonNumber ?: ""}:${episodeNumber ?: ""}"

internal fun FloppySyncItemDto.mediaType(): MediaType? = when (type) {
    "movie" -> MediaType.MOVIE
    "show", "episode" -> MediaType.TV
    else -> null
}

/** CineTrack keys everything by TMDB id. An episode's ids are the episode's
 * own, so only a TMDB-sourced show places its episodes. */
internal fun FloppySyncItemDto.localTmdbId(): Int? = when {
    source == "tmdb" -> mediaId.toIntOrNull()
    type == "episode" -> null
    else -> ids["tmdb"]?.toIntOrNull()
}

internal fun FloppySyncItemDto.libraryStatus(): LibraryStatus? = when (status) {
    "planning" -> LibraryStatus.PLAN_TO_WATCH
    "in_progress" -> LibraryStatus.WATCHING
    "paused" -> LibraryStatus.PAUSED
    "dropped" -> LibraryStatus.DROPPED
    "completed" -> LibraryStatus.COMPLETED
    else -> null
}

private fun FloppySyncItemDto.toMediaEntity(type: MediaType, tmdb: Int) = MediaEntity(
    mediaType = type.name,
    tmdbId = tmdb,
    title = title,
    overview = "",
    posterPath = null,
    backdropPath = null,
    releaseDate = null,
    score = null,
    runtimeMinutes = null,
    genres = "",
    providers = "",
    collectionId = null,
)

private fun String?.toInstantOrNull(): Instant? {
    if (isNullOrBlank()) return null
    return runCatching { Instant.parse(this) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(this).toInstant() }.getOrNull()
}
