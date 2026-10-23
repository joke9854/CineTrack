package com.cinetrack.data.sync

import androidx.room.withTransaction
import com.cinetrack.BuildConfig
import com.cinetrack.data.local.AppDatabase
import com.cinetrack.data.local.PendingWriteEntity
import com.cinetrack.data.local.SyncStateEntity
import com.cinetrack.data.local.SyncOperationEntity
import com.cinetrack.data.local.UserMediaStateEntity
import com.cinetrack.data.remote.ApiServices
import com.cinetrack.data.remote.SimklIds
import com.cinetrack.data.remote.SimklLibraryItem
import com.cinetrack.data.remote.SimklSyncItem
import com.cinetrack.data.remote.SimklSyncRequest
import com.cinetrack.data.repository.AppPreferences
import com.cinetrack.data.repository.ProgressCacheRepository
import com.cinetrack.data.repository.ProgressRefreshRequest
import com.cinetrack.data.repository.persistedGenerationFloor
import com.cinetrack.data.sync.simkl.ResolvedSimklItem
import com.cinetrack.data.sync.simkl.fromCineTrackStatus
import com.cinetrack.data.sync.simkl.toSimklStatus
import com.cinetrack.data.sync.simkl.SimklSyncHost
import com.cinetrack.data.sync.simkl.withDeliveredLibraryState
import com.cinetrack.domain.LibraryStatus
import com.cinetrack.domain.MediaType
import com.cinetrack.domain.RailIds
import com.cinetrack.domain.SyncOperationStatus
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The SimklSyncHost/MainSyncHost the sync engines call back into (push, baseline, queue repair, main-tracker swap). */
class TrackingSyncHost(
    override val database: AppDatabase,
    override val services: ApiServices,
    override val preferences: AppPreferences,
    private val awaitStartupReady: suspend () -> Unit = {},
    override val tmdbApiKey: () -> String = { BuildConfig.TMDB_API_TOKEN },
    override val syncOperationRepository: SyncOperationRepository,
    private val durableOperationWriter: DurableSyncOperationWriter,
    override val syncReconciler: SyncReconciler = SyncReconciler(),
    private val trackingProviderRegistry: TrackingProviderRegistry? = null,
    private val trackingRoutingMutex: TrackingRoutingMutex = TrackingRoutingMutex(),
    private val trackingConfigurationService: com.cinetrack.data.sync.TrackingConfigurationService? = null,
    override val episodeNumbering: com.cinetrack.data.sync.numbering.EpisodeNumbering? = null,
    override val animeNumberingRepair: com.cinetrack.data.sync.numbering.AnimeNumberingRepair? = null,
    private val progressCache: ProgressCacheRepository,
) : SimklSyncHost {
    /** Shows whose local episode numbers were rewritten, so screens can drop cached episode lists. */
    val renumberedShows = kotlinx.coroutines.flow.MutableSharedFlow<Int>(extraBufferCapacity = 64)

    suspend fun awaitStartup() {
        awaitStartupReady()
        // Queue repair is idempotent and also runs defensively before every
        // sync, so upgraded installations cannot expose stale writes to UI.
        repairSyncQueue()
    }

    override fun stateOperationId(type: String, id: Int) = "state:$type:$id"
    override fun writeOperationId(id: Long) = "write:$id"

    /** Provider-neutral canonical state used by a SECONDARY bootstrap plan. */
    suspend fun canonicalTrackingSnapshot(): TrackingSnapshot {
        val snapshot = database.snapshotDao().snapshot()
        val states = snapshot.states.associateBy { it.mediaType to it.mediaId }
        val movieHistory = snapshot.history.filter { it.mediaType == MediaType.MOVIE.name }
            .groupBy { it.mediaId }
        val episodeHistory = snapshot.history.filter { it.mediaType == MediaType.TV.name && it.season != null && it.episodeNumber != null }
        val movies = snapshot.media.filter { it.mediaType == MediaType.MOVIE.name }.map { media ->
            val state = states[MediaType.MOVIE.name to media.tmdbId]
            val latest = movieHistory[media.tmdbId].orEmpty().maxByOrNull { it.watchedAt }
            TrackedMovieState(
                ids = MediaIds(tmdb = media.tmdbId.toLong()),
                libraryState = state?.status?.let { runCatching { LibraryStatus.valueOf(it) }.getOrNull() },
                watched = state?.watched == true || latest != null,
                watchedAt = latest?.watchedAt?.let { runCatching { Instant.parse(it) }.getOrNull() },
                updatedAt = state?.updatedAt?.let { Instant.ofEpochMilli(it) },
            )
        }
        val shows = snapshot.media.filter { it.mediaType == MediaType.TV.name }.map { media ->
            val state = states[MediaType.TV.name to media.tmdbId]
            TrackedShowState(
                ids = MediaIds(tmdb = media.tmdbId.toLong()),
                libraryState = state?.status?.let { runCatching { LibraryStatus.valueOf(it) }.getOrNull() },
                updatedAt = state?.updatedAt?.let { Instant.ofEpochMilli(it) },
            )
        }
        val episodes = episodeHistory.map { history ->
            TrackedEpisodeState(
                showIds = MediaIds(tmdb = history.mediaId.toLong()),
                season = history.season!!,
                episode = history.episodeNumber!!,
                watched = true,
                watchedAt = runCatching { Instant.parse(history.watchedAt) }.getOrNull(),
            )
        }.distinctBy { Triple(it.showIds.tmdb, it.season, it.episode) }
        return TrackingSnapshot(movies, shows, episodes, Instant.now(), completeHistory = true)
    }

    private suspend fun nextCausalGenerationLocked(): Long =
        MutationGeneration.next(previous = persistedGenerationFloor(database))

    override suspend fun nextCausalGeneration(): Long = trackingRoutingMutex.withLock {
        nextCausalGenerationLocked()
    }

    override suspend fun isMainProviderCurrent(provider: TrackingProviderId): Boolean =
        (trackingProviderRegistry?.configuration()?.mainProvider
            ?: preferences.mainTrackingProvider.first()) == provider

    override suspend fun <T> withMainAuthority(
        provider: TrackingProviderId,
        block: suspend () -> T,
    ): T? = trackingRoutingMutex.withLock {
        if (isMainProviderCurrent(provider)) block() else null
    }

    /**
     * Repairs queue rows written by older reconciliation versions. A stale
     * reconcile:* row is never evidence of current local intent. State rows are
     * retained only when their value still matches the current dirty Room row;
     * dirty rows that already equal MAIN are cleaned, while genuinely changed
     * rows are materialized as one canonical state:* operation.
     */
    override suspend fun repairSyncQueue() {
        trackingRoutingMutex.withLock {
            // Materialize legacy rows and capture provider configuration under
            // the same routing lock used by local mutations and role changes.
            syncOperationRepository.backfillLegacyDeliveries()
            val mainProvider = preferences.mainTrackingProvider.first()
            val baseline = mainProvider?.let { preferences.syncBaselineNow(it) }
            val repairs = mutableListOf<SyncOperation>()
            database.withTransaction {
                val states = database.stateDao().stateSnapshot()
                val operations = database.syncDao().syncOperations()
                val media = database.mediaDao().mediaSnapshot().associateBy { "${it.mediaType}:${it.tmdbId}" }
                operations.filter { it.operationId.startsWith("reconcile:") }.forEach { stale ->
                    database.syncDao().deleteDeliveriesForGeneration(stale.operationId, stale.createdAt)
                    database.syncDao().deleteOperationIfGeneration(stale.operationId, stale.createdAt)
                }
                states.forEach { state ->
                    val operationId = stateOperationId(state.mediaType, state.mediaId)
                    val operation = operations.firstOrNull { it.operationId == operationId }
                    val baselineValue = baselineValue(baseline, state.mediaType, state.mediaId)
                    if (!state.dirty) {
                        operation?.let {
                            database.syncDao().deleteDeliveriesForGeneration(it.operationId, it.createdAt)
                            database.syncDao().deleteOperationIfGeneration(it.operationId, it.createdAt)
                        }
                        return@forEach
                    }
                    if (baselineValue != null && baselineValue == state.status) {
                        database.stateDao().upsert(state.copy(dirty = false))
                        operation?.let {
                            database.syncDao().deleteDeliveriesForGeneration(it.operationId, it.createdAt)
                            database.syncDao().deleteOperationIfGeneration(it.operationId, it.createdAt)
                        }
                        return@forEach
                    }
                    val current = operation != null &&
                        operation.status in setOf(SyncOperationStatus.PENDING.name, SyncOperationStatus.FAILED.name) &&
                        operation.localValue == state.status && operation.createdAt == state.updatedAt
                    if (!current) repairs += SyncOperation(
                        id = operationId,
                        type = SyncOperationType.LIBRARY_STATUS,
                        mediaType = runCatching { MediaType.valueOf(state.mediaType) }.getOrElse { return@forEach },
                        mediaId = state.mediaId,
                        title = media["${state.mediaType}:${state.mediaId}"]?.title ?: "${state.mediaType} #${state.mediaId}",
                        value = state.status,
                        sourceVersion = state.updatedAt,
                    )
                }
                database.syncDao().upsert(SyncStateEntity("sync_queue_repair_087", mainProvider?.name, System.currentTimeMillis()))
            }
            // Persist each repaired operation and its immutable target snapshot
            // atomically while the routing lock is still held.
            repairs.forEach { durableOperationWriter.repairCurrentOperationUnlocked(it) }
        }
    }

    override fun baselineValue(baseline: TrackingSnapshot?, mediaType: String, mediaId: Int): String? = when (mediaType) {
        MediaType.MOVIE.name -> baseline?.movies?.firstOrNull { it.ids.tmdb?.toInt() == mediaId }?.libraryState?.name
        MediaType.TV.name -> baseline?.shows?.firstOrNull { it.ids.tmdb?.toInt() == mediaId }?.libraryState?.name
        else -> null
    }

    override fun advanceSyncBaseline(
        previous: TrackingSnapshot?,
        remote: TrackingSnapshot,
        acknowledgedStates: List<UserMediaStateEntity>,
        acknowledgedMovieHistoryOperations: List<SyncOperation>,
        acknowledgedEpisodeWrites: List<PendingWriteEntity>,
        acknowledgedHistoryRemovals: List<PendingWriteEntity>,
        conflicts: List<SyncConflict>,
    ): TrackingSnapshot {
        var movies = remote.movies
        var shows = remote.shows
        var episodes = remote.episodes
        acknowledgedStates.forEach { state ->
            val status = state.status.fromCineTrackStatus()
            if (state.mediaType == MediaType.MOVIE.name) {
            val index = movies.indexOfFirst { it.ids.tmdb?.toInt() == state.mediaId }
                if (index >= 0) movies = movies.toMutableList().also { it[index] = it[index].copy(libraryState = status) }
            } else if (state.mediaType == MediaType.TV.name) {
                val index = shows.indexOfFirst { it.ids.tmdb?.toInt() == state.mediaId }
                if (index >= 0) shows = shows.toMutableList().also { it[index] = it[index].copy(libraryState = status) }
            }
        }
        acknowledgedMovieHistoryOperations.forEach { operation ->
            if (operation.mediaType != MediaType.MOVIE) return@forEach
            val index = movies.indexOfFirst { it.ids.tmdb?.toInt() == operation.mediaId }
            if (index < 0) return@forEach
            val current = movies[index]
            val watched = operation.type == SyncOperationType.MOVIE_WATCHED
            val watchedAt = if (watched) {
                operation.payload?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: current.watchedAt
            } else null
            movies = movies.toMutableList().also {
                it[index] = current.copy(watched = watched, watchedAt = watchedAt)
            }
        }
        acknowledgedEpisodeWrites.forEach { write ->
            val parts = write.payload.split(':', limit = 3)
            val season = parts.getOrNull(0)?.toIntOrNull() ?: return@forEach
            val episode = parts.getOrNull(1)?.toIntOrNull() ?: return@forEach
            val watched = write.operation == "EPISODE_WATCHED"
            val watchedAt = parts.getOrNull(2)?.let { runCatching { Instant.parse(it) }.getOrNull() }
            val index = episodes.indexOfFirst { it.showIds.tmdb?.toInt() == write.mediaId && it.season == season && it.episode == episode }
            val value = TrackedEpisodeState(MediaIds(tmdb = write.mediaId.toLong()), season, episode, watched, watchedAt, watchedAt)
            episodes = if (index >= 0) episodes.toMutableList().also { it[index] = it[index].copy(watched = watched, watchedAt = watchedAt, updatedAt = watchedAt) }
            else if (watched) episodes + value else episodes
        }
        acknowledgedHistoryRemovals.forEach { write ->
            val index = movies.indexOfFirst { it.ids.tmdb?.toInt() == write.mediaId }
            if (index >= 0) movies = movies.toMutableList().also { it[index] = it[index].copy(watched = false, watchedAt = null) }
        }
        conflicts.forEach { conflict ->
            val old = previous ?: return@forEach
            when (conflict.field) {
                ConflictField.LIBRARY_STATUS -> {
                    val value = conflict.baselineValue?.let { runCatching { LibraryStatus.valueOf(it) }.getOrNull() } ?: return@forEach
                    if (conflict.mediaType == MediaType.MOVIE) {
                        val index = movies.indexOfFirst { it.ids.tmdb?.toInt() == conflict.ids.tmdb?.toInt() }
                        if (index >= 0) movies = movies.toMutableList().also { it[index] = it[index].copy(libraryState = value) }
                    } else {
                        val index = shows.indexOfFirst { it.ids.tmdb?.toInt() == conflict.ids.tmdb?.toInt() }
                        if (index >= 0) shows = shows.toMutableList().also { it[index] = it[index].copy(libraryState = value) }
                    }
                }
                ConflictField.WATCHED -> {
                    val index = movies.indexOfFirst { it.ids.tmdb?.toInt() == conflict.ids.tmdb?.toInt() }
                    if (index >= 0) movies = movies.toMutableList().also { it[index] = it[index].copy(watched = old.movies.firstOrNull { movie -> movie.ids.tmdb?.toInt() == conflict.ids.tmdb?.toInt() }?.watched ?: it[index].watched) }
                }
                ConflictField.EPISODE_WATCHED -> {
                    val index = episodes.indexOfFirst { it.showIds.tmdb?.toInt() == conflict.ids.tmdb?.toInt() && it.season == conflict.season && it.episode == conflict.episode }
                    if (index >= 0) episodes = episodes.toMutableList().also { it[index] = it[index].copy(watched = old.episodes.firstOrNull { e -> e.showIds.tmdb?.toInt() == conflict.ids.tmdb?.toInt() && e.season == conflict.season && e.episode == conflict.episode }?.watched ?: it[index].watched) }
                }
            }
        }
        return remote.copy(movies = movies, shows = shows, episodes = episodes)
    }

    override fun mergePulledSnapshot(
        previous: TrackingSnapshot?,
        pulled: TrackingSnapshot,
        completeMovies: Boolean,
        completeShows: Boolean,
    ): TrackingSnapshot {
        if (previous == null) return pulled
        fun <T> merge(
            old: List<T>,
            incoming: List<T>,
            key: (T) -> String,
            complete: Boolean,
        ): List<T> {
            if (complete) return incoming
            val values = old.associateBy(key).toMutableMap()
            incoming.forEach { values[key(it)] = it }
            return values.values.toList()
        }
        val movies = merge(previous.movies, pulled.movies, { it.ids.stableKeys().joinToString("|") }, completeMovies)
        val shows = merge(previous.shows, pulled.shows, { it.ids.stableKeys().joinToString("|") }, completeShows)
        val changedShowIds = pulled.episodes.map { it.showIds.stableKeys().joinToString("|") }.toSet()
        val oldEpisodes = if (completeShows) emptyList() else previous.episodes.filterNot {
            it.showIds.stableKeys().joinToString("|") in changedShowIds
        }
        return pulled.copy(
            movies = movies,
            shows = shows,
            episodes = oldEpisodes + pulled.episodes,
            generatedAt = pulled.generatedAt ?: Instant.now(),
            completeHistory = previous.completeHistory || pulled.completeHistory,
        )
    }

    override suspend fun validatedPendingLocalStates(): List<UserMediaStateEntity> {
        val operations = database.syncDao().syncOperations().associateBy(SyncOperationEntity::operationId)
        return database.stateDao().pendingStates().filter { state ->
            val operation = operations[stateOperationId(state.mediaType, state.mediaId)]
            operation != null &&
                operation.status in setOf(SyncOperationStatus.PENDING.name, SyncOperationStatus.FAILED.name) &&
                operation.localValue == state.status && operation.createdAt == state.updatedAt
        }
    }

    override suspend fun clearObsoleteOperations(
        mediaType: String,
        mediaId: Int,
        operationNames: Set<String>,
        season: Int?,
        episode: Int?,
    ) {
        val operations = database.syncDao().syncOperations().filter { operation ->
            operation.mediaType == mediaType && operation.mediaId == mediaId &&
                operation.operation in operationNames && operation.status != SyncOperationStatus.CONFLICT.name &&
                (season == null || operation.season == season) && (episode == null || operation.episode == episode)
        }
        if (operations.isNotEmpty()) database.syncDao().deleteOperations(operations.map(SyncOperationEntity::operationId))
        val writes = database.syncDao().pendingWrites().filter { write ->
            write.mediaType == mediaType && write.mediaId == mediaId && write.operation in operationNames &&
                (season == null || write.payload.split(':', limit = 3).getOrNull(0)?.toIntOrNull() == season) &&
                (episode == null || write.payload.split(':', limit = 3).getOrNull(1)?.toIntOrNull() == episode)
        }
        if (writes.isNotEmpty()) {
            database.syncDao().deleteWrites(writes.map(PendingWriteEntity::id))
            database.syncDao().deleteOperations(writes.map { "write:${it.id}" })
        }
    }

    override fun syncError(error: Throwable): String =
        if (error is retrofit2.HttpException && error.code() == 429) {
            "Simkl's daily request limit was reached. Your changes are saved and will be sent automatically."
        } else error.message?.takeIf(String::isNotBlank) ?: error::class.java.simpleName
    private suspend fun localZone(): ZoneId {
        val configured = preferences.metadataTimezone.first()
        return if (configured == "system") ZoneId.systemDefault()
        else runCatching { ZoneId.of(configured) }.getOrDefault(ZoneId.systemDefault())
    }

    override suspend fun localToday(): LocalDate = LocalDate.now(localZone())

    /**
     * Swaps MAIN and SECONDARY. The caller has already run a final sync with
     * the current MAIN. When Floppy takes over, its first pass reads the whole
     * library and only adds, so nothing on this device is removed by the swap.
     */
    suspend fun swapMainTracker(): com.cinetrack.data.sync.TrackingConfiguration {
        val service = checkNotNull(trackingConfigurationService) { "Tracker roles can't be changed here" }
        val current = service.current()
        val oldMain = checkNotNull(current.mainProvider) { "Choose a main tracker first" }
        val newMain = checkNotNull(current.secondaryProvider) { "Connect a second tracker first" }
        if (newMain == TrackingProviderId.FLOPPY) {
            preferences.floppySettingsNow()?.connectionId?.takeIf(String::isNotBlank)?.let { connection ->
                database.syncDao().upsert(
                    SyncStateEntity("${com.cinetrack.data.sync.floppy.FloppySyncEngine.AREA_PREFIX}$connection", null, 0L),
                )
            }
        }
        return service.setProviders(newMain, oldMain)
    }

    suspend fun isMainTrackingSyncDue(maxAgeMillis: Long): Boolean {
        val provider = preferences.mainTrackingProvider.first() ?: return false
        val lastCheck = preferences.trackingLastCheckAt(provider)
            ?: (if (provider == TrackingProviderId.SIMKL) database.syncDao().get("all")?.lastSuccessfulSync else null)
        if (lastCheck == null) return true
        return System.currentTimeMillis() - lastCheck >= maxAgeMillis
    }


    override suspend fun pushLibraryState(state: UserMediaStateEntity) {
        val targetStatus = state.status.toSimklStatus()
        val ids = SimklIds(simkl = state.simklId, tmdb = state.mediaId.toString())
        if (state.status == LibraryStatus.NONE.name) {
            // With no season/episode granularity, history/remove deletes the
            // item from the Simkl library as well as its watched history.
            val request = state.syncRequest(SimklSyncItem(ids = ids))
            val response = services.simklSync.removeHistory(request)
            val unmatched = if (state.mediaType == MediaType.MOVIE.name) {
                response.notFound.movies
            } else {
                response.notFound.shows
            }
            check(unmatched.isEmpty()) { "Simkl could not match the item being removed" }
        } else {
            // Library status is its own logical field. Movie/episode history
            // operations are dispatched independently by pushOperation; this
            // adapter call changes only Simkl's library membership/status.
            services.simklSync.addToList(state.syncRequest(SimklSyncItem(ids = ids, to = targetStatus)))
        }
        recordDeliveredLibraryState(state)
    }

    /**
     * Simkl now holds this status, so it is also the last state both sides
     * agree on. Without this, a later sync that downloads only changes (a
     * history removal is not a list change for Simkl) compared against the
     * old value and put a removed or changed show back.
     */
    private suspend fun recordDeliveredLibraryState(state: UserMediaStateEntity) {
        val baseline = preferences.syncBaselineNow(TrackingProviderId.SIMKL) ?: return
        val status = runCatching { LibraryStatus.valueOf(state.status) }.getOrDefault(LibraryStatus.NONE)
        val type = if (state.mediaType == MediaType.MOVIE.name) MediaType.MOVIE else MediaType.TV
        preferences.saveSyncBaseline(
            withDeliveredLibraryState(baseline, type, state.mediaId.toLong(), status, state.watched),
            TrackingProviderId.SIMKL,
        )
    }

    override suspend fun pushOperation(operation: SyncOperation) {
        when (operation.type) {
            SyncOperationType.LIBRARY_STATUS -> {
                val state = database.stateDao().get(operation.mediaType.name, operation.mediaId)
                    ?: error("Local state is no longer available")
                require(state.updatedAt == operation.sourceVersion && state.status == operation.value) {
                    "Local library generation changed before upload"
                }
                pushLibraryState(state)
            }
            SyncOperationType.MOVIE_WATCHED,
            SyncOperationType.MOVIE_UNWATCHED -> {
                require(operation.mediaType == MediaType.MOVIE) { "Movie history operation must target a movie" }
                val watched = operation.type == SyncOperationType.MOVIE_WATCHED
                val state = database.stateDao().get(MediaType.MOVIE.name, operation.mediaId)
                    ?: error("Local movie state is no longer available")
                require(state.updatedAt == operation.sourceVersion && state.watched == watched) {
                    "Local movie history generation changed before upload"
                }
                val watchedAt = if (watched) {
                    operation.payload?.let { runCatching { Instant.parse(it) }.getOrNull() }?.toString()
                        ?: Instant.ofEpochMilli(operation.sourceVersion).toString()
                } else null
                val request = SimklSyncRequest(
                    movies = listOf(
                        SimklSyncItem(
                            ids = SimklIds(tmdb = operation.mediaId.toString()),
                            watchedAt = watchedAt,
                        ),
                    ),
                )
                if (watched) services.simklSync.addHistory(request)
                else {
                    services.simklSync.removeHistory(request)
                    // Simkl may couple history removal with watchlist
                    // membership. Re-apply the canonical library field so a
                    // MOVIE_UNWATCHED retry remains semantically complete on
                    // its own.
                    if (state.status != LibraryStatus.NONE.name) {
                        services.simklSync.addToList(
                            state.syncRequest(
                                SimklSyncItem(
                                    ids = SimklIds(simkl = state.simklId, tmdb = operation.mediaId.toString()),
                                    to = state.status.toSimklStatus(),
                                ),
                            ),
                        )
                    }
                }
            }
            SyncOperationType.EPISODE_WATCHED,
            SyncOperationType.EPISODE_UNWATCHED -> {
                require(operation.mediaType == MediaType.TV) { "Episode history operation must target a show" }
                val write = operation.id.removePrefix("write:").toLongOrNull()
                    ?.let { database.syncDao().pendingWrite(it) }
                if (write != null) {
                    require(write.createdAt == operation.sourceVersion && write.operation == operation.type.name) {
                        "Episode write generation changed before upload"
                    }
                    pushEpisodeWrite(write)
                } else {
                    val parts = operation.payload.orEmpty().split(':', limit = 3)
                    val season = parts.getOrNull(0)?.toIntOrNull() ?: error("Invalid queued season")
                    val episode = parts.getOrNull(1)?.toIntOrNull() ?: error("Invalid queued episode")
                    val watchedAt = parts.getOrNull(2)?.let { runCatching { Instant.parse(it) }.getOrNull() }?.toString()
                    val request = SimklSyncRequest(
                        shows = listOf(
                            SimklSyncItem(
                                ids = SimklIds(tmdb = operation.mediaId.toString()),
                                seasons = listOf(
                                    com.cinetrack.data.remote.SimklSeason(
                                        season,
                                        listOf(com.cinetrack.data.remote.SimklEpisode(episode, watchedAt.takeIf { operation.type == SyncOperationType.EPISODE_WATCHED })),
                                    ),
                                ),
                            ),
                        ),
                    )
                    if (operation.type == SyncOperationType.EPISODE_WATCHED) services.simklSync.addHistory(request)
                    else services.simklSync.removeHistory(request)
                }
            }
            SyncOperationType.MEDIA_HISTORY_REMOVE -> {
                val write = operation.id.removePrefix("write:").toLongOrNull()
                    ?.let { database.syncDao().pendingWrite(it) }
                if (write != null) {
                    require(write.createdAt == operation.sourceVersion && write.operation == operation.type.name) {
                        "History-removal generation changed before upload"
                    }
                    pushMediaHistoryRemoval(write)
                } else {
                    val ids = SimklIds(simkl = operation.payload?.toLongOrNull(), tmdb = operation.mediaId.toString())
                    val item = SimklSyncItem(ids = ids)
                    val request = if (operation.mediaType == MediaType.MOVIE) {
                        SimklSyncRequest(movies = listOf(item))
                    } else {
                        SimklSyncRequest(shows = listOf(item))
                    }
                    services.simklSync.removeHistory(request)
                }
            }
            SyncOperationType.SET_RATING -> throw com.cinetrack.data.sync.TrackingSyncError.UnsupportedOperation(TrackingProviderId.SIMKL, operation.type)
        }
    }

    override suspend fun enqueueSecondaryMirror(operation: SyncOperation, mainProvider: TrackingProviderId) {
        // The writer captures SECONDARY once and performs supersession plus
        // replacement in one Room transaction. Re-read the canonical field
        // while its routing lock is held so an intervening local edit owns the
        // normal MAIN+SECONDARY path and cannot be superseded by this mirror.
        durableOperationWriter.replaceSecondaryMirror(operation) {
            if (!isMainProviderCurrent(mainProvider)) return@replaceSecondaryMirror false
            val field = operation.logicalField()
            if (field == null) {
                false
            } else {
                var newerLocalIntent = false
                for (entity in database.syncDao().syncOperations()) {
                    if (entity.logicalField() != field || entity.operationId == operation.id ||
                        entity.status !in setOf(SyncOperationStatus.PENDING.name, SyncOperationStatus.FAILED.name) ||
                        entity.createdAt <= operation.sourceVersion
                    ) continue
                    if (syncOperationRepository.currentIntentTargetsProvider(
                            entity.operationId,
                            entity.createdAt,
                            mainProvider,
                        )) {
                        newerLocalIntent = true
                        break
                    }
                }
                if (newerLocalIntent) {
                    false
                } else {
                    when (operation.type) {
                        SyncOperationType.LIBRARY_STATUS ->
                            database.stateDao().get(operation.mediaType.name, operation.mediaId)?.status == operation.value
                        SyncOperationType.MOVIE_WATCHED ->
                            database.stateDao().get(MediaType.MOVIE.name, operation.mediaId)?.watched == true
                        SyncOperationType.MOVIE_UNWATCHED,
                        SyncOperationType.MEDIA_HISTORY_REMOVE ->
                            database.stateDao().get(MediaType.MOVIE.name, operation.mediaId)?.watched == false
                        SyncOperationType.EPISODE_WATCHED,
                        SyncOperationType.EPISODE_UNWATCHED -> {
                            val parts = operation.payload.orEmpty().split(':', limit = 3)
                            val season = parts.getOrNull(0)?.toIntOrNull()
                            val episode = parts.getOrNull(1)?.toIntOrNull()
                            season != null && episode != null &&
                                (database.timelineDao().episodeHistoryForShow(MediaType.TV.name, operation.mediaId)
                                    .any { it.season == season && it.episodeNumber == episode } == (operation.type == SyncOperationType.EPISODE_WATCHED))
                        }
                        SyncOperationType.SET_RATING -> true
                    }
                }
            }
        }
    }

    private fun UserMediaStateEntity.syncRequest(item: SimklSyncItem): SimklSyncRequest =
        if (mediaType == MediaType.MOVIE.name) SimklSyncRequest(movies = listOf(item))
        else SimklSyncRequest(shows = listOf(item))

    override suspend fun pushMediaHistoryRemoval(write: PendingWriteEntity) {
        val simklId = write.payload.toLongOrNull()
        val ids = SimklIds(simkl = simklId, tmdb = write.mediaId.toString())
        val request = if (write.mediaType == MediaType.MOVIE.name) {
            SimklSyncRequest(movies = listOf(SimklSyncItem(ids = ids)))
        } else {
            SimklSyncRequest(shows = listOf(SimklSyncItem(ids = ids)))
        }
        services.simklSync.removeHistory(request)
    }

    override suspend fun pushEpisodeWrite(write: PendingWriteEntity) {
        val parts = write.payload.split(':', limit = 3)
        val season = parts.getOrNull(0)?.toIntOrNull() ?: error("Invalid queued season")
        val episode = parts.getOrNull(1)?.toIntOrNull() ?: error("Invalid queued episode")
        val watchedAt = parts.getOrNull(2) ?: Instant.now().toString()
        // The queued coordinate is local (TMDB) numbering; Simkl may count anime differently.
        val target = episodeNumbering?.toSimkl(write.mediaId, season, episode)
            ?: com.cinetrack.data.sync.numbering.SimklTarget.Unchanged
        val request = SimklSyncRequest(
            shows = com.cinetrack.data.sync.simkl.simklShowItems(
                SimklIds(tmdb = write.mediaId.toString()),
                listOf(
                    com.cinetrack.data.sync.simkl.SimklEpisodePlay(
                        season, episode, watchedAt.takeIf { write.operation == "EPISODE_WATCHED" }, target,
                    ),
                ),
            ),
        )
        if (write.operation == "EPISODE_WATCHED") services.simklSync.addHistory(request)
        else services.simklSync.removeHistory(request)
    }

    override suspend fun refreshProgressCache(
        request: ProgressRefreshRequest,
        onProgress: ((Float) -> Unit)?,
    ): Boolean = progressCache.refreshProgressCache(request, onProgress)

    override suspend fun rebuildLibraryRailInTransaction() {
        val states = database.stateDao().stateSnapshot()
            .filter { it.status != LibraryStatus.NONE.name }
            .sortedByDescending(UserMediaStateEntity::updatedAt)
        val mediaByKey = database.mediaDao().mediaSnapshot().associateBy { "${it.mediaType}:${it.tmdbId}" }
        val media = states.mapNotNull { mediaByKey["${it.mediaType}:${it.mediaId}"] }
        database.mediaDao().clearRail(RailIds.LIBRARY)
        database.mediaDao().upsertRails(
            media.mapIndexed { index, item ->
                com.cinetrack.data.local.MediaRailEntity(RailIds.LIBRARY, item.mediaType, item.tmdbId, index)
            },
        )
    }

    override fun mergeSimklItems(items: List<SimklLibraryItem>): List<SimklLibraryItem> =
        items.groupBy { item ->
            val media = item.show ?: item.movie
            media?.ids?.tmdb ?: media?.ids?.imdb ?: media?.ids?.tvdb ?: media?.ids?.simkl?.toString()
                ?: "${media?.title.orEmpty().lowercase()}:${media?.year?.toString().orEmpty()}"
        }.values.mapNotNull { duplicates ->
            val preferred = duplicates.maxByOrNull { item -> item.seasons.sumOf { it.episodes.size } } ?: return@mapNotNull null
            val seasons = duplicates.flatMap(SimklLibraryItem::seasons)
                .groupBy { it.number }
                .map { (number, versions) ->
                    val episodes = versions.flatMap { it.episodes }
                        .groupBy { it.number }
                        .mapNotNull { (_, candidates) ->
                            candidates.maxByOrNull { episode ->
                                (if (episode.watchedAt != null) 2 else 0) + (if (episode.title.isNotBlank()) 1 else 0)
                            }
                        }
                        .sortedBy { it.number }
                    com.cinetrack.data.remote.SimklSeason(number, episodes)
                }
                .sortedBy { it.number }
            preferred.copy(seasons = seasons)
        }

    override suspend fun resolveSimklItems(items: List<SimklLibraryItem>, type: MediaType): List<ResolvedSimklItem> {
        val resolved = mutableListOf<ResolvedSimklItem>()
        for (batch in items.chunked(6)) {
            resolved += coroutineScope {
                batch.map { item -> async {
                    resolveSimklTmdbId(item, type)?.let { ResolvedSimklItem(item, type, it) }
                } }.mapNotNull { it.await() }
            }
        }
        return resolved
    }

    private suspend fun resolveSimklTmdbId(item: SimklLibraryItem, type: MediaType): Int? {
        val media = item.show ?: item.movie ?: return null
        media.ids.tmdb?.toIntOrNull()?.takeIf { it > 0 }?.let { return it }
        if (tmdbApiKey().isBlank()) return null

        media.ids.imdb?.takeIf(String::isNotBlank)?.let { imdb ->
            val found = runCatching { services.tmdb.find(imdb, "imdb_id") }.getOrNull()
            (if (type == MediaType.TV) found?.tvResults else found?.movieResults)
                ?.firstOrNull()?.id?.let { return it }
        }
        if (type == MediaType.TV) media.ids.tvdb?.takeIf(String::isNotBlank)?.let { tvdb ->
            runCatching { services.tmdb.find(tvdb, "tvdb_id") }.getOrNull()
                ?.tvResults?.firstOrNull()?.id?.let { return it }
        }

        if (media.title.isBlank()) return null
        val expectedType = if (type == MediaType.TV) "tv" else "movie"
        val normalized = normalizeMediaTitle(media.title)
        val candidates = runCatching { services.tmdb.search(media.title).results }
            .getOrDefault(emptyList())
            .filter { it.mediaType == expectedType }
        val exact = candidates.firstOrNull { candidate ->
            val title = if (type == MediaType.TV) candidate.name else candidate.title
            val year = (if (type == MediaType.TV) candidate.firstAirDate else candidate.releaseDate)?.take(4)?.toIntOrNull()
            normalizeMediaTitle(title.orEmpty()) == normalized && (media.year == null || media.year == year)
        } ?: candidates.firstOrNull { candidate ->
            normalizeMediaTitle((if (type == MediaType.TV) candidate.name else candidate.title).orEmpty()) == normalized
        }
        return exact?.id
    }

    private fun normalizeMediaTitle(value: String): String = value.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), "")
}
