package com.cinetrack.data.sync.simkl

import androidx.room.withTransaction
import com.cinetrack.data.local.AppDatabase
import com.cinetrack.data.local.EpisodeEntity
import com.cinetrack.data.local.MediaEntity
import com.cinetrack.data.local.PendingWriteEntity
import com.cinetrack.data.local.PlaybackEntity
import com.cinetrack.data.local.SyncStateEntity
import com.cinetrack.data.local.SyncOperationEntity
import com.cinetrack.data.local.UserMediaStateEntity
import com.cinetrack.data.local.WatchHistoryEntity
import com.cinetrack.data.remote.*
import com.cinetrack.data.repository.AppPreferences
import com.cinetrack.data.repository.ProgressRefreshRequest
import com.cinetrack.data.sync.*
import com.cinetrack.domain.*
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import com.cinetrack.data.remote.tmdbImageUrl

data class ResolvedSimklItem(
    val item: SimklLibraryItem,
    val type: MediaType,
    val tmdbId: Int,
)

interface SimklSyncHost : MainSyncHost {
    val services: ApiServices
    /** Tracker-to-TMDB episode numbering; null when not configured (numbers pass through unchanged). */
    val episodeNumbering: com.cinetrack.data.sync.numbering.EpisodeNumbering? get() = null
    /** Runs after a successful Simkl pass; null when not configured. */
    val animeNumberingRepair: com.cinetrack.data.sync.numbering.AnimeNumberingRepair? get() = null
    val tmdbApiKey: () -> String
    val syncReconciler: SyncReconciler

    suspend fun validatedPendingLocalStates(): List<UserMediaStateEntity>
    suspend fun localToday(): LocalDate
    fun stateOperationId(type: String, id: Int): String
    fun writeOperationId(id: Long): String
    fun baselineValue(baseline: TrackingSnapshot?, mediaType: String, mediaId: Int): String?
    fun advanceSyncBaseline(
        previous: TrackingSnapshot?,
        remote: TrackingSnapshot,
        acknowledgedStates: List<UserMediaStateEntity>,
        acknowledgedMovieHistoryOperations: List<SyncOperation>,
        acknowledgedEpisodeWrites: List<PendingWriteEntity>,
        acknowledgedHistoryRemovals: List<PendingWriteEntity>,
        conflicts: List<SyncConflict>,
    ): TrackingSnapshot
    fun mergePulledSnapshot(
        previous: TrackingSnapshot?,
        pulled: TrackingSnapshot,
        completeMovies: Boolean,
        completeShows: Boolean,
    ): TrackingSnapshot
    fun mergeSimklItems(items: List<SimklLibraryItem>): List<SimklLibraryItem>
    suspend fun resolveSimklItems(items: List<SimklLibraryItem>, type: MediaType): List<ResolvedSimklItem>
    suspend fun pushLibraryState(state: UserMediaStateEntity)
    /** Sends exactly one coordinator-selected operation; it never discovers queue work. */
    suspend fun pushOperation(operation: SyncOperation)
    suspend fun pushMediaHistoryRemoval(write: PendingWriteEntity)
    suspend fun pushEpisodeWrite(write: PendingWriteEntity)
    suspend fun clearObsoleteOperations(
        mediaType: String,
        mediaId: Int,
        operationNames: Set<String>,
        season: Int? = null,
        episode: Int? = null,
    )
    fun syncError(error: Throwable): String
}

/** Simkl full-sync orchestration, isolated from the repository façade. */
class SimklSyncEngine(
    private val hostProvider: () -> SimklSyncHost,
) {
    private suspend fun <T> cancellableResult(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        Result.failure(error)
    }

    suspend fun sync(
        operations: List<SyncOperation>,
        onProgress: (SyncProgress) -> Unit,
    ): ProviderSyncOutcome = runSync(operations, onProgress).getOrThrow()

    private suspend fun runSync(
        operations: List<SyncOperation>,
        onProgress: (SyncProgress) -> Unit,
    ): Result<ProviderSyncOutcome> {
        val host = hostProvider()
        val database = host.database
        val services = host.services
        val preferences = host.preferences
        val tmdbApiKey = host.tmdbApiKey
        val syncOperationRepository = host.syncOperationRepository
        val syncReconciler = host.syncReconciler
        suspend fun repairSyncQueue() = host.repairSyncQueue()
        suspend fun refreshProgressCache(
            request: ProgressRefreshRequest,
            onProgress: ((Float) -> Unit)? = null,
        ) = host.refreshProgressCache(request, onProgress)
        suspend fun localToday() = host.localToday()
        fun stateOperationId(type: String, id: Int) = host.stateOperationId(type, id)
        fun writeOperationId(id: Long) = host.writeOperationId(id)
        fun baselineValue(baseline: TrackingSnapshot?, mediaType: String, mediaId: Int) =
            host.baselineValue(baseline, mediaType, mediaId)
        fun advanceSyncBaseline(
            previous: TrackingSnapshot?,
            remote: TrackingSnapshot,
            acknowledgedStates: List<UserMediaStateEntity>,
            acknowledgedMovieHistoryOperations: List<SyncOperation>,
            acknowledgedEpisodeWrites: List<PendingWriteEntity>,
            acknowledgedHistoryRemovals: List<PendingWriteEntity>,
            conflicts: List<SyncConflict>,
        ) = host.advanceSyncBaseline(previous, remote, acknowledgedStates, acknowledgedMovieHistoryOperations, acknowledgedEpisodeWrites, acknowledgedHistoryRemovals, conflicts)
        fun mergePulledSnapshot(
            previous: TrackingSnapshot?,
            pulled: TrackingSnapshot,
            completeMovies: Boolean,
            completeShows: Boolean,
        ) = host.mergePulledSnapshot(previous, pulled, completeMovies, completeShows)
        fun mergeSimklItems(items: List<SimklLibraryItem>) = host.mergeSimklItems(items)
        suspend fun resolveSimklItems(items: List<SimklLibraryItem>, type: MediaType) = host.resolveSimklItems(items, type)
        suspend fun pushOperation(operation: SyncOperation) = host.pushOperation(operation)
        suspend fun enqueueSecondaryMirror(operation: SyncOperation) = host.enqueueSecondaryMirror(operation, TrackingProviderId.SIMKL)
        suspend fun isMainProviderCurrent() = host.isMainProviderCurrent(TrackingProviderId.SIMKL)
        suspend fun <T> withMainAuthority(block: suspend () -> T) = host.withMainAuthority(TrackingProviderId.SIMKL, block)
        suspend fun nextCausalGeneration() = host.nextCausalGeneration()
        suspend fun rebuildLibraryRailInTransaction() = host.rebuildLibraryRailInTransaction()
        fun syncError(error: Throwable) = host.syncError(error)

        val previousSyncState = database.syncDao().get("all")
        val previousSuccessfulSync = previousSyncState?.lastSuccessfulSync
        val previousReport = preferences.syncReportNow()
        return cancellableResult {
        check(!preferences.tokenNow().isNullOrBlank()) { "Connect Simkl first" }
        // The first sync after connecting waits for the user's choice; MERGE never removes local data.
        val firstSyncMode = preferences.simklFirstSyncModeNow()
        if (firstSyncMode == SimklFirstSyncMode.ASK) {
            // Not a failure: wait quietly for the choice; every queued operation stays pending.
            onProgress(SyncProgress(false, 0f, SyncStage.IDLE, null, previousSuccessfulSync, previousReport))
            return@cancellableResult ProviderSyncOutcome(itemsChanged = false, report = previousReport)
        }
        val mergeMode = firstSyncMode == SimklFirstSyncMode.MERGE
        // Repair stale queue rows before reading the local push set. This is
        // idempotent and is deliberately repeated defensively before sync.
        repairSyncQueue()
        val previousBaseline = preferences.syncBaselineNow(TrackingProviderId.SIMKL)
        fun progress(running: Boolean, value: Float, stage: SyncStage, message: String? = null) =
            onProgress(SyncProgress(running, value, stage, message, previousSuccessfulSync))
        progress(true, .08f, SyncStage.AUTH)
        // Correctness snapshot: capture every local generation and exact
        // queued intent before any provider network I/O.  A user edit made
        // while activities/all-items/playback are downloading must therefore
        // be recognized as newer during the final commit.
        val historyBeforeSync = database.timelineDao().historySnapshot()
        val localStates = database.stateDao().stateSnapshot()
        val localStatesByKey = localStates.associateBy { "${it.mediaType}:${it.mediaId}" }
        val exactWritesByOperationId = linkedMapOf<String, PendingWriteEntity>()
        for (operation in operations) {
            val writeId = operation.id.removePrefix("write:").toLongOrNull() ?: continue
            val write = database.syncDao().pendingWrite(writeId) ?: continue
            if (write.createdAt == operation.sourceVersion && write.operation == operation.type.name) {
                exactWritesByOperationId[operation.id] = write
            }
        }
        val pendingLocalStates = operations.filter { it.type == SyncOperationType.LIBRARY_STATUS }
            .mapNotNull { operation ->
                localStatesByKey["${operation.mediaType.name}:${operation.mediaId}"]?.takeIf {
                    it.dirty && it.updatedAt == operation.sourceVersion && it.status == operation.value
                }
            }
            .distinctBy { "${it.mediaType}:${it.mediaId}" }
        val pendingEpisodeWrites = operations
            .filter { it.type == SyncOperationType.EPISODE_WATCHED || it.type == SyncOperationType.EPISODE_UNWATCHED }
            .mapNotNull { exactWritesByOperationId[it.id] }
        val pendingMediaHistoryRemovals = operations
            .filter { it.type == SyncOperationType.MEDIA_HISTORY_REMOVE }
            .mapNotNull { exactWritesByOperationId[it.id] }
        val syncStartedAt = System.currentTimeMillis()
        val syncStartOperationsByField = operations.mapNotNull { operation ->
            operation.logicalField()?.let { it to operation }
        }.groupBy({ it.first }, { it.second })
        val activity = services.simklSync.activities()
        progress(true, .20f, SyncStage.ACTIVITY)

        // Versioned marker: force one complete episode-level baseline after upgrading.
        // This repairs accounts where an earlier delta sync imported a show status but
        // did not receive every watched episode in that show.
        val episodeBaselineComplete = database.syncDao().get("episode_baseline_037") != null
        // library_times_045: one full snapshot rewrites every title's library time from Simkl,
        // repairing times that earlier syncs stamped with the sync moment (Progress order ties).
        val librarySnapshotComplete = database.syncDao().get("library_snapshot_051") != null &&
            database.syncDao().get("library_times_045") != null
        val previousShows = if (episodeBaselineComplete) database.syncDao().get("shows")?.remoteTimestamp else null
        val previousAnime = if (episodeBaselineComplete) database.syncDao().get("anime")?.remoteTimestamp else null
        val previousMovies = database.syncDao().get("movies")?.remoteTimestamp
        val previousShowsRemoved = database.syncDao().get("shows_removed")?.remoteTimestamp
        val previousAnimeRemoved = database.syncDao().get("anime_removed")?.remoteTimestamp
        val previousMoviesRemoved = database.syncDao().get("movies_removed")?.remoteTimestamp
        // Anime and ordinary shows share CineTrack's TV media type. If either
        // removal generation changes, fetch both complete lists before comparing
        // membership so one category can never erase the other.
        // A merge compares against the complete account, so it always downloads full lists.
        val tvRemovalChanged = mergeMode || !librarySnapshotComplete ||
            previousShowsRemoved != activity.tvShows.removedFromList ||
            previousAnimeRemoved != activity.anime.removedFromList
        val movieRemovalChanged = mergeMode || !librarySnapshotComplete ||
            previousMoviesRemoved != activity.movies.removedFromList
        val remoteChanged = previousSyncState?.remoteTimestamp != activity.all ||
            previousShows != activity.tvShows.all ||
            previousAnime != activity.anime.all ||
            previousMovies != activity.movies.all ||
            tvRemovalChanged ||
            movieRemovalChanged
        // SyncCoordinator has already selected the exact MAIN delivery set.
        // These operations are the only local intents this provider may upload
        // or use as reconciliation protection during this pass.
        val pendingCount = operations.size

        // Simkl activity is the gate for every remote/item operation. When the
        // generation is unchanged and there is nothing local to push, stop here:
        // no playback request, no Room write and no Progress/UI reconstruction.
        if (
            !remoteChanged && operations.isEmpty()
        ) {
            val progressChanged = refreshProgressCache(
                request = ProgressRefreshRequest(),
            ) { value ->
                progress(true, value, SyncStage.PROCESSING)
            }
            val checkedAt = System.currentTimeMillis()
            if (firstSyncMode != null) preferences.setSimklFirstSyncMode(null)
            val report = SyncReport(
                unchanged = database.stateDao().stateSnapshot().count { it.status != LibraryStatus.NONE.name },
                lastFullSync = previousReport.lastFullSync,
                lastIncrementalSync = checkedAt,
                databaseUntouched = !progressChanged,
            )
            preferences.markSimklChecked(checkedAt)
            preferences.saveSyncReport(report)
            onProgress(
                SyncProgress(
                    running = false,
                    progress = 1f,
                    stage = SyncStage.COMPLETE,
                    message = if (progressChanged) "Progress data refreshed." else "No remote changes detected—database untouched.",
                    lastSuccessfulSync = previousSuccessfulSync,
                    report = report,
                ),
            )
            return@cancellableResult ProviderSyncOutcome(
                itemsChanged = progressChanged,
                report = report,
                acknowledgedOperationIds = emptySet(),
            )
        }

        val remote = coroutineScope {
            val shows = async {
                if (tvRemovalChanged) services.simklSync.allItems("shows")
                else if (previousShows == null || previousShows != activity.tvShows.all) services.simklSync.allItems("shows", previousShows)
                else SimklLibraryResponse()
            }
            val anime = async {
                if (tvRemovalChanged) services.simklSync.allItems("anime")
                else if (previousAnime == null || previousAnime != activity.anime.all) services.simklSync.allItems("anime", previousAnime)
                else SimklLibraryResponse()
            }
            val movies = async {
                if (movieRemovalChanged) services.simklSync.allItems("movies")
                else if (previousMovies == null || previousMovies != activity.movies.all) services.simklSync.allItems("movies", previousMovies)
                else SimklLibraryResponse()
            }
            Triple(shows.await(), anime.await(), movies.await())
        }
        val playbackResult = if (remoteChanged) runCatching {
            coroutineScope {
                val episodes = async { services.simklSync.playback("episodes") }
                val movies = async { services.simklSync.playback("movies") }
                episodes.await() + movies.await()
            }
        } else Result.success(emptyList())
        val importedPlayback = playbackResult.getOrNull()?.mapNotNull { it.toPlaybackEntity() }
        val previousPlayback = if (importedPlayback != null) database.timelineDao().playbackSnapshot() else emptyList()
        val tvPlaybackChanged = importedPlayback?.let { incoming ->
            incoming.filter { it.mediaType == MediaType.TV.name }.toSet() !=
                previousPlayback.filter { it.mediaType == MediaType.TV.name }.toSet()
        } ?: false
        progress(true, .42f, SyncStage.PLAYBACK)

        val remoteShows = mergeSimklItems(remote.first.shows + remote.first.anime + remote.second.shows + remote.second.anime)
        val remoteMovies = mergeSimklItems(remote.third.movies)
        val resolvedShowsRaw = resolveSimklItems(remoteShows, MediaType.TV)
        // Simkl anime come in Simkl's own numbering; the app stores TMDB numbering. Done after the
        // merge (which keys episodes by number) so entries that land on one coordinate all survive.
        val resolvedShows = host.episodeNumbering?.let { numbering ->
            val animeMedia = (remote.first.anime + remote.second.anime).mapNotNull { it.show?.ids }
            val animeSimkl = animeMedia.mapNotNullTo(hashSetOf()) { it.simkl }
            val animeTmdb = animeMedia.mapNotNullTo(hashSetOf()) { it.tmdb }
            if (animeMedia.isEmpty()) resolvedShowsRaw
            else resolvedShowsRaw.map { resolved ->
                val ids = resolved.item.show?.ids
                val isAnime = ids != null && ((ids.simkl != null && ids.simkl in animeSimkl) || (ids.tmdb != null && ids.tmdb in animeTmdb))
                if (isAnime) resolved.copy(item = numbering.localizeItem(resolved.tmdbId, resolved.item)) else resolved
            }
        } ?: resolvedShowsRaw
        val resolvedMovies = resolveSimklItems(remoteMovies, MediaType.MOVIE)
        val resolvedItems = resolvedShows + resolvedMovies
        // Simkl's episode arrays are the watched-set, but an older baseline could
        // be incomplete even when the show itself was already `completed`.
        // On this versioned repair, expand completed shows against TMDB's aired
        // regular episodes before replacing local episode history.
        val completedEpisodeRepairs: Map<Int, List<EpisodeCard>> = if (!episodeBaselineComplete && tmdbApiKey().isNotBlank()) {
            val today = localToday()
            coroutineScope {
                resolvedItems
                    .filter { it.type == MediaType.TV && it.item.status.equals("completed", ignoreCase = true) }
                    .chunked(3)
                    .flatMap { batch ->
                        batch.map { resolved ->
                            async {
                                resolved.tmdbId to runCatching {
                                    val importedBySeason = resolved.item.seasons.associate { season ->
                                        season.number to season.episodes.map { it.number }.distinct().size
                                    }
                                    services.tmdb.show(resolved.tmdbId).seasons
                                        .filter { it.number > 0 && importedBySeason.getOrDefault(it.number, 0) < it.episodeCount }
                                        .map { it.number }
                                        .distinct()
                                        .sorted()
                                        .flatMap { seasonNumber ->
                                            services.tmdb.season(resolved.tmdbId, seasonNumber).episodes
                                                .filter { episode ->
                                                    episode.airDate?.let { raw ->
                                                        runCatching { !LocalDate.parse(raw.take(10)).isAfter(today) }.getOrDefault(true)
                                                    } ?: true
                                                }
                                                .map { episode ->
                                                    EpisodeCard(
                                                        id = episode.id,
                                                        showId = resolved.tmdbId,
                                                        season = episode.season,
                                                        number = episode.number,
                                                        title = episode.name,
                                                        overview = episode.overview,
                                                        airDate = episode.airDate,
                                                        stillUrl = tmdbImageUrl(episode.stillPath, "w780"),
                                                        runtimeMinutes = episode.runtime,
                                                        watched = true,
                                                    )
                                                }
                                        }
                                }.getOrDefault(emptyList())
                            }
                        }.map { it.await() }
                    }
                    .toMap()
            }
        } else emptyMap()
        // A title Simkl gives no time for keeps this device's time instead of the sync moment.
        val remoteStates = resolvedItems.mapNotNull {
            it.item.toState(it.type, it.tmdbId, localStatesByKey["${it.type.name}:${it.tmdbId}"]?.updatedAt)
        }.let { states ->
            if (!mergeMode) states
            else states.map { it.mergedWithLocal(localStatesByKey["${it.mediaType}:${it.mediaId}"]) }
        }
        val remoteTvKeys = resolvedShows.map { "${MediaType.TV.name}:${it.tmdbId}" }.toSet()
        val remoteMovieKeys = resolvedMovies.map { "${MediaType.MOVIE.name}:${it.tmdbId}" }.toSet()
        val localTvCount = localStates.count { it.mediaType == MediaType.TV.name && it.status != LibraryStatus.NONE.name }
        val localMovieCount = localStates.count { it.mediaType == MediaType.MOVIE.name && it.status != LibraryStatus.NONE.name }
        // Reconcile absence only when every item in the full response resolved to
        // a stable TMDB id. A partial ID-resolution failure preserves the previous
        // local snapshot instead of deleting valid entries. Also reject a sudden
        // empty response for a non-empty local library; that is much more likely
        // to be a partial server response than a deliberate account-wide removal.
        val completeTvSnapshot = tvRemovalChanged && resolvedShows.size == remoteShows.size &&
            (remoteShows.isNotEmpty() || localTvCount == 0)
        val completeMovieSnapshot = movieRemovalChanged && resolvedMovies.size == remoteMovies.size &&
            (remoteMovies.isNotEmpty() || localMovieCount == 0)
        // A full download where some items did not resolve to TMDB still lists
        // every Simkl id the account has: an item whose Simkl id is known and
        // missing was removed on Simkl. Items without a known Simkl id keep
        // the conservative rule above.
        val fullTvDownload = tvRemovalChanged && (remoteShows.isNotEmpty() || localTvCount == 0)
        val fullMovieDownload = movieRemovalChanged && (remoteMovies.isNotEmpty() || localMovieCount == 0)
        val remoteShowSimklIds = if (fullTvDownload && !completeTvSnapshot) remoteShows.mapNotNullTo(hashSetOf()) { it.show?.ids?.simkl } else null
        val remoteMovieSimklIds = if (fullMovieDownload && !completeMovieSnapshot) remoteMovies.mapNotNullTo(hashSetOf()) { it.movie?.ids?.simkl } else null
        val remoteRemovedStates = if (mergeMode) emptyList() else localStates.mapNotNull { state ->
            if (state.dirty || state.status == LibraryStatus.NONE.name) return@mapNotNull null
            val key = "${state.mediaType}:${state.mediaId}"
            val removed = when (state.mediaType) {
                MediaType.TV.name -> key !in remoteTvKeys && removedFromSimklList(completeTvSnapshot, remoteShowSimklIds, state.simklId)
                MediaType.MOVIE.name -> key !in remoteMovieKeys && removedFromSimklList(completeMovieSnapshot, remoteMovieSimklIds, state.simklId)
                else -> false
            }
            if (removed) {
                state.copy(
                    status = LibraryStatus.NONE.name,
                    watched = false,
                    updatedAt = System.currentTimeMillis(),
                    dirty = false,
                )
            } else null
        }
        val libraryChanged = remoteStates.isNotEmpty() || remoteRemovedStates.isNotEmpty()
        val mediaCandidates = (resolvedItems.mapNotNull { it.item.toMediaEntity(it.type, it.tmdbId) } +
            playbackResult.getOrDefault(emptyList()).mapNotNull { it.toMediaEntity() })
            .distinctBy { "${it.mediaType}:${it.tmdbId}" }
        val localMediaByKey = database.mediaDao().mediaSnapshot()
            .associateBy { "${it.mediaType}:${it.tmdbId}" }
        val remoteStatesByKey = remoteStates.associateBy { "${it.mediaType}:${it.mediaId}" }
        // Normalize the provider response before applying the legacy Room import.
        // The reconciler is pure and provider-neutral; Simkl DTOs terminate at this
        // mapping boundary and never enter generic synchronization code.
        val pulledTrackingSnapshot = TrackingSnapshot(
            movies = resolvedMovies.mapNotNull { resolved ->
                resolved.item.movie?.let { media ->
                    TrackedMovieState(
                        ids = media.ids.toTrackingIds(),
                        libraryState = resolved.item.status.fromSimklStatus(),
                        watched = resolved.item.lastWatchedAt != null,
                        watchedAt = resolved.item.lastWatchedAt.toInstantOrNull(),
                        updatedAt = (resolved.item.lastWatchedAt ?: resolved.item.addedAt).toInstantOrNull(),
                    )
                }
            },
            shows = resolvedShows.mapNotNull { resolved ->
                resolved.item.show?.let { media ->
                    TrackedShowState(
                        ids = media.ids.toTrackingIds(),
                        libraryState = resolved.item.status.fromSimklStatus(),
                        updatedAt = (resolved.item.lastWatchedAt ?: resolved.item.addedAt).toInstantOrNull(),
                    )
                }
            },
            episodes = resolvedShows.flatMap { resolved ->
                val ids = resolved.item.show?.ids?.toTrackingIds() ?: return@flatMap emptyList()
                resolved.item.seasons.flatMap { season -> season.episodes.map { episode ->
                    TrackedEpisodeState(
                        showIds = ids,
                        season = season.number,
                        episode = episode.number,
                        watched = true,
                        watchedAt = episode.watchedAt.toInstantOrNull(),
                        updatedAt = (episode.watchedAt ?: resolved.item.lastWatchedAt).toInstantOrNull(),
                    )
                } }
            },
            generatedAt = Instant.now(),
        )
        val remoteTrackingSnapshot = mergePulledSnapshot(
            previous = previousBaseline?.withoutAbsentSimklItems(remoteShowSimklIds, remoteMovieSimklIds),
            pulled = pulledTrackingSnapshot,
            completeMovies = completeMovieSnapshot,
            completeShows = completeTvSnapshot,
        )
        // Where a remote library status came from: this pull (and which list
        // download) or only the stored baseline. No ids beyond the TMDB key.
        fun simklOrigin(type: String, id: Int): String {
            val pulled = resolvedItems.firstOrNull { it.type.name == type && it.tmdbId == id }
                ?: return "source=baseline fetch=${if (type == MediaType.MOVIE.name) movieRemovalChanged else tvRemovalChanged}"
            val inAnime = remote.second.shows.any { it.show?.ids?.tmdb == id.toString() } || remote.second.anime.any { it.show?.ids?.tmdb == id.toString() }
            return "source=pulled list=${if (inAnime) "anime" else if (type == MediaType.MOVIE.name) "movies" else "shows"} " +
                "full=${if (type == MediaType.MOVIE.name) movieRemovalChanged else tvRemovalChanged} status=${pulled.item.status} " +
                "last_watched=${pulled.item.lastWatchedAt ?: "none"} added=${pulled.item.addedAt ?: "none"} seasons=${pulled.item.seasons.size}"
        }
        val localEpisodesByKey = linkedMapOf<String, TrackedEpisodeState>()
        historyBeforeSync.forEach { history ->
            val season = history.season ?: return@forEach
            val episode = history.episodeNumber ?: return@forEach
            localEpisodesByKey["${history.mediaId}:$season:$episode"] = TrackedEpisodeState(
                showIds = MediaIds(tmdb = history.mediaId.toLong()),
                season = season,
                episode = episode,
                watched = true,
                watchedAt = history.watchedAt.toInstantOrNull(),
                updatedAt = history.watchedAt.toInstantOrNull(),
            )
        }
        operations.filter { it.type == SyncOperationType.EPISODE_WATCHED || it.type == SyncOperationType.EPISODE_UNWATCHED }
            .forEach { operation ->
                val parts = operation.payload.orEmpty().split(':', limit = 3)
                val season = parts.getOrNull(0)?.toIntOrNull() ?: return@forEach
                val episode = parts.getOrNull(1)?.toIntOrNull() ?: return@forEach
                val watched = operation.type == SyncOperationType.EPISODE_WATCHED
                val watchedAt = parts.getOrNull(2).toInstantOrNull()
                localEpisodesByKey["${operation.mediaId}:$season:$episode"] = TrackedEpisodeState(
                    showIds = MediaIds(tmdb = operation.mediaId.toLong()),
                    season = season,
                    episode = episode,
                    watched = watched,
                    watchedAt = watchedAt,
                    updatedAt = Instant.ofEpochMilli(operation.sourceVersion),
                )
            }
        val exactDirtyMediaKeys = buildSet {
            operations.forEach { operation ->
                add("${operation.mediaType.name}:${operation.mediaId}")
                add("tmdb:${operation.mediaId}")
            }
        }
        val localTrackingSnapshot = LocalTrackingSnapshot(
            state = TrackingSnapshot(
                movies = localStates.filter { it.mediaType == MediaType.MOVIE.name }.map { state ->
                    TrackedMovieState(
                        ids = MediaIds(tmdb = state.mediaId.toLong(), simkl = state.simklId),
                        libraryState = state.status.fromCineTrackStatus(),
                        watched = state.watched,
                        updatedAt = Instant.ofEpochMilli(state.updatedAt),
                    )
                },
                shows = localStates.filter { it.mediaType == MediaType.TV.name }.map { state ->
                    TrackedShowState(
                        ids = MediaIds(tmdb = state.mediaId.toLong(), simkl = state.simklId),
                        libraryState = state.status.fromCineTrackStatus(),
                        updatedAt = Instant.ofEpochMilli(state.updatedAt),
                    )
                },
                episodes = localEpisodesByKey.values.toList(),
                generatedAt = Instant.now(),
            ),
            baseline = previousBaseline,
            dirtyMediaKeys = exactDirtyMediaKeys,
            pendingOperations = operations,
        )
        val reconciliation = syncReconciler.reconcile(
            local = localTrackingSnapshot,
            remote = remoteTrackingSnapshot,
            provider = com.cinetrack.data.sync.TrackingProviderId.SIMKL,
        )
        // Reconciliation is direction-only. Existing state:/write: rows are
        // already the durable local intent and must not be duplicated as
        // reconcile:* operations.
        val conflictOperations = reconciliation.conflicts.map { conflict ->
            val mediaId = conflict.ids.tmdb?.toInt() ?: 0
            val mediaTitle = localMediaByKey["${conflict.mediaType.name}:$mediaId"]?.title
                ?: when {
                    conflict.season != null && conflict.episode != null -> "${conflict.mediaType.name} #$mediaId S${conflict.season.toString().padStart(2, '0')}E${conflict.episode.toString().padStart(2, '0')}"
                    else -> "${conflict.mediaType.name} #$mediaId"
                }
            SyncOperationEntity(
                operationId = "conflict:${conflict.conflictId}",
                operation = "${conflict.field.name}_CONFLICT",
                mediaType = conflict.mediaType.name,
                mediaId = mediaId,
                title = mediaTitle,
                status = SyncOperationStatus.CONFLICT.name,
                message = null,
                localValue = conflict.localValue,
                remoteValue = conflict.remoteValue,
                // Existing Room columns carry the conflict generations. The
                // resolver uses these values to reject stale USE_REMOTE work.
                createdAt = conflict.localUpdatedAt?.toEpochMilli() ?: System.currentTimeMillis(),
                updatedAt = conflict.remoteUpdatedAt?.toEpochMilli() ?: System.currentTimeMillis(),
                providerId = conflict.providerId.name,
                season = conflict.season,
                episode = conflict.episode,
            )
        }.distinctBy(SyncOperationEntity::operationId)
        val conflictLibraryKeys = reconciliation.conflicts
            .filter { it.field == com.cinetrack.data.sync.ConflictField.LIBRARY_STATUS }
            .map { "${it.mediaType.name}:${it.ids.tmdb?.toInt()}" }
            .toSet()
        val localStatesToPush = pendingLocalStates.filterNot { state ->
            "${state.mediaType}:${state.mediaId}" in conflictLibraryKeys
        }
        val deferredOperationIds = operations.filter { operation ->
            reconciliation.conflicts.any { conflict ->
                when (conflict.field) {
                    com.cinetrack.data.sync.ConflictField.LIBRARY_STATUS ->
                        operation.type == SyncOperationType.LIBRARY_STATUS && operation.mediaType == conflict.mediaType && operation.mediaId == conflict.ids.tmdb?.toInt()
                    com.cinetrack.data.sync.ConflictField.WATCHED ->
                        operation.mediaType == MediaType.MOVIE && operation.mediaId == conflict.ids.tmdb?.toInt() &&
                            operation.type in setOf(SyncOperationType.MOVIE_WATCHED, SyncOperationType.MOVIE_UNWATCHED, SyncOperationType.MEDIA_HISTORY_REMOVE)
                    com.cinetrack.data.sync.ConflictField.EPISODE_WATCHED ->
                        operation.type in setOf(SyncOperationType.EPISODE_WATCHED, SyncOperationType.EPISODE_UNWATCHED) &&
                            operation.mediaId == conflict.ids.tmdb?.toInt() && operation.payload.orEmpty().startsWith("${conflict.season}:${conflict.episode}")
                }
            }
        }.mapTo(linkedSetOf(), SyncOperation::id)
        val operationsToPush = operations.filterNot { it.id in deferredOperationIds }
        val localStatesToPushExact = localStatesToPush.filter { stateOperationId(it.mediaType, it.mediaId) in operationsToPush.mapTo(linkedSetOf(), SyncOperation::id) }
        val episodeWritesToPush = pendingEpisodeWrites.filter { writeOperationId(it.id) in operationsToPush.mapTo(linkedSetOf(), SyncOperation::id) }
        val historyRemovalsToPush = pendingMediaHistoryRemovals.filter { writeOperationId(it.id) in operationsToPush.mapTo(linkedSetOf(), SyncOperation::id) }
        /*
         * The coordinator-selected operations are the complete Simkl upload
         * set. Room's global dirty/pending tables are only backing data for
         * those exact operation ids; they never expand this list.
         */
        val transportedOperationIds = linkedSetOf<String>()
        operationsToPush.forEach { operation ->
            pushOperation(operation)
            transportedOperationIds += operation.id
        }
        // Merge: send Simkl what only this device has, in a few batched requests, before
        // committing. A failure here leaves the local database untouched and MERGE pending,
        // and the retry recomputes the difference from fresh full lists.
        val mergePlan = if (mergeMode) {
            planSimklMerge(
                localStates = localStates,
                localHistory = historyBeforeSync,
                remote = SimklRemoteLibrary(
                    keys = remoteTvKeys + remoteMovieKeys,
                    simklIds = (remoteShows + remoteMovies).mapNotNullTo(linkedSetOf()) { (it.show ?: it.movie)?.ids?.simkl },
                    watchedMovies = resolvedMovies.filter { it.item.lastWatchedAt != null }.mapTo(linkedSetOf()) { it.tmdbId },
                    watchedEpisodes = resolvedShows.flatMapTo(linkedSetOf()) { resolved ->
                        resolved.item.seasons.flatMap { season -> season.episodes.map { Triple(resolved.tmdbId, season.number, it.number) } }
                    },
                ),
                simklTargets = mergeSimklTargets(host.episodeNumbering, historyBeforeSync),
            )
        } else null
        mergePlan?.listRequests?.forEach { services.simklSync.addToList(it) }
        mergePlan?.historyRequests?.forEach { services.simklSync.addHistory(it) }
        val episodeWritesToPushExact = episodeWritesToPush.filter { writeOperationId(it.id) in transportedOperationIds }
        val historyRemovalsToPushExact = historyRemovalsToPush.filter { writeOperationId(it.id) in transportedOperationIds }
        // Commit Simkl's identifiers and list state immediately. TMDB artwork
        // enrichment used to run inside synchronization in serial batches and
        // made the apparent "local save" last minutes. Missing artwork is now
        // repaired asynchronously after the correctness-critical cache commit.
        val newMedia = mediaCandidates.map { incoming ->
            val existing = localMediaByKey["${incoming.mediaType}:${incoming.tmdbId}"]
                ?: return@map incoming
            incoming.copy(
                title = incoming.title.ifBlank { existing.title },
                overview = incoming.overview.ifBlank { existing.overview },
                posterPath = incoming.posterPath ?: existing.posterPath,
                backdropPath = incoming.backdropPath ?: existing.backdropPath,
                releaseDate = incoming.releaseDate ?: existing.releaseDate,
                score = incoming.score ?: existing.score,
                runtimeMinutes = incoming.runtimeMinutes ?: existing.runtimeMinutes,
                genres = incoming.genres.ifBlank { existing.genres },
                providers = incoming.providers.ifBlank { existing.providers },
                collectionId = incoming.collectionId ?: existing.collectionId,
            )
        }

        progress(true, .62f, SyncStage.HISTORY)

        val committedAt = System.currentTimeMillis()
        // Build every history mutation before opening Room's write transaction.
        // The previous implementation performed mapping plus one DELETE per
        // episode while SQLite held the transaction, which scaled badly on
        // accounts with long watch histories.
        val importedHistory = mutableListOf<WatchHistoryEntity>()
        val baselineShowIds = mutableSetOf<Int>()
        val deltaEpisodeKeys = mutableSetOf<Triple<Int, Int, Int>>()
        resolvedItems.forEach { resolved ->
            val item = resolved.item
            val type = resolved.type
            val tmdb = resolved.tmdbId
            item.lastWatchedAt?.takeIf { type == MediaType.MOVIE }?.let {
                importedHistory += WatchHistoryEntity(mediaType = type.name, mediaId = tmdb, watchedAt = it)
            }
            val repairedEpisodes = completedEpisodeRepairs[tmdb].orEmpty()
            if (type == MediaType.TV && (item.seasons.isNotEmpty() || repairedEpisodes.isNotEmpty())) {
                if (!episodeBaselineComplete) baselineShowIds += tmdb
                // A list, not a map: anime entries that map onto one TMDB coordinate are all kept.
                val watchedEpisodes = mutableListOf<Pair<Pair<Int, Int>, Pair<String?, String?>>>()
                item.seasons.forEach { season ->
                    season.episodes.forEach { episode ->
                        watchedEpisodes += (season.number to episode.number) to
                            (episode.title.takeIf(String::isNotBlank) to episode.watchedAt)
                    }
                }
                val importedKeys = watchedEpisodes.mapTo(hashSetOf()) { it.first }
                repairedEpisodes.forEach { episode ->
                    if (importedKeys.add(episode.season to episode.number)) {
                        watchedEpisodes += (episode.season to episode.number) to
                            (episode.title.takeIf(String::isNotBlank) to null)
                    }
                }
                watchedEpisodes.forEach { (key, episode) ->
                    val (seasonNumber, episodeNumber) = key
                    if (episodeBaselineComplete) deltaEpisodeKeys += Triple(tmdb, seasonNumber, episodeNumber)
                    importedHistory += WatchHistoryEntity(
                        mediaType = MediaType.TV.name,
                        mediaId = tmdb,
                        season = seasonNumber,
                        episodeNumber = episodeNumber,
                        episodeTitle = episode.first,
                        watchedAt = episode.second ?: item.lastWatchedAt ?: item.addedAt ?: Instant.now().toString(),
                    )
                }
            }
        }
        val historyRowsToReplace = if (deltaEpisodeKeys.isNotEmpty()) {
            database.timelineDao().historySnapshot().mapNotNull { history ->
                val season = history.season ?: return@mapNotNull null
                val episode = history.episodeNumber ?: return@mapNotNull null
                history.id.takeIf { Triple(history.mediaId, season, episode) in deltaEpisodeKeys }
            }
        } else emptyList()
        val appliedRemoteMutations = mutableListOf<LocalMutation>()
        // Items removed here that Simkl still lists: their history is not re-imported.
        val keptRemovals = mutableSetOf<String>()
        val syncStates = buildList {
            add(SyncStateEntity("all", activity.all, committedAt))
            add(SyncStateEntity("shows", activity.tvShows.all, committedAt))
            add(SyncStateEntity("anime", activity.anime.all, committedAt))
            add(SyncStateEntity("movies", activity.movies.all, committedAt))
            if (completeTvSnapshot) {
                add(SyncStateEntity("shows_removed", activity.tvShows.removedFromList, committedAt))
                add(SyncStateEntity("anime_removed", activity.anime.removedFromList, committedAt))
            }
            if (completeMovieSnapshot) {
                add(SyncStateEntity("movies_removed", activity.movies.removedFromList, committedAt))
            }
            add(SyncStateEntity("episode_baseline_037", activity.all, committedAt))
            if (librarySnapshotComplete || (completeTvSnapshot && completeMovieSnapshot)) {
                add(SyncStateEntity("library_snapshot_051", activity.all, committedAt))
                add(SyncStateEntity("library_times_045", activity.all, committedAt))
            }
        }
        val committedAsMain = withMainAuthority {
        database.withTransaction {
            fun WatchHistoryEntity.episodeKey(): Triple<Int, Int, Int>? =
                if (mediaType == MediaType.TV.name && season != null && episodeNumber != null)
                    Triple(mediaId, season, episodeNumber) else null
            val beforeKeys = historyBeforeSync.mapNotNull { it.episodeKey() }.toSet()
            val currentHistory = database.timelineDao().historySnapshot()
            val currentKeys = currentHistory.mapNotNull { it.episodeKey() }.toSet()
            val currentPendingWrites = database.syncDao().pendingWrites()
            val exactWriteIds = exactWritesByOperationId.values.mapTo(linkedSetOf(), PendingWriteEntity::id)
            val activeMainWriteIds = mutableSetOf<Long>()
            activeMainWriteIds += exactWriteIds
            for (write in currentPendingWrites) {
                if (syncOperationRepository.currentIntentTargetsProvider("write:${write.id}", write.createdAt, TrackingProviderId.SIMKL)) {
                    activeMainWriteIds += write.id
                }
            }
            val protectedKeys = currentPendingWrites
                .filter { it.id in exactWriteIds || it.id in activeMainWriteIds }
                .filter { it.operation == "EPISODE_WATCHED" || it.operation == "EPISODE_UNWATCHED" }
                .mapNotNull { write ->
                    val parts = write.payload.split(':', limit = 3)
                    val season = parts.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
                    val number = parts.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
                    Triple(write.mediaId, season, number)
                }.toSet()
            val locallyRemoved = (beforeKeys - currentKeys) + (protectedKeys - currentKeys)
            val locallyAdded = currentHistory.filter {
                it.episodeKey()?.let { key -> key !in beforeKeys || key in protectedKeys } == true
            }
            val protectedMovieIds = operations
                .filter { it.mediaType == MediaType.MOVIE && it.type in setOf(
                    SyncOperationType.MOVIE_WATCHED,
                    SyncOperationType.MOVIE_UNWATCHED,
                    SyncOperationType.MEDIA_HISTORY_REMOVE,
                ) }
                .mapTo(linkedSetOf(), SyncOperation::mediaId)
            val currentIntentEntities = database.syncDao().syncOperations()
            val activeMainIntentKeys = mutableSetOf<String>()
            // The coordinator supplied this exact MAIN delivery set. Keep it
            // active even when a lightweight repository implementation cannot
            // expose delivery rows; persisted Room rows are still checked
            // below for mutations created during the network pass.
            operations.forEach { operation ->
                activeMainIntentKeys += "${operation.id}:${operation.sourceVersion}"
            }
            for (entity in currentIntentEntities) {
                if (entity.status !in setOf(SyncOperationStatus.PENDING.name, SyncOperationStatus.FAILED.name)) continue
                if (syncOperationRepository.currentIntentTargetsProvider(entity.operationId, entity.createdAt, TrackingProviderId.SIMKL)) {
                    activeMainIntentKeys += "${entity.operationId}:${entity.createdAt}"
                    if (entity.mediaType == MediaType.MOVIE.name && entity.logicalField() == SyncLogicalField.movieWatched(entity.mediaId)) {
                        protectedMovieIds += entity.mediaId
                    }
                }
            }
            fun mutationField(mutation: LocalMutation): SyncLogicalField = when (mutation) {
                is LocalMutation.SetLibraryStatus -> SyncLogicalField.library(mutation.mediaType, mutation.mediaId.toInt())
                is LocalMutation.SetWatched -> if (mutation.season != null && mutation.episode != null) {
                    SyncLogicalField.episodeWatched(mutation.mediaId.toInt(), mutation.season, mutation.episode)
                } else SyncLogicalField.movieWatched(mutation.mediaId.toInt())
            }
            fun hasNewerLocalIntent(field: SyncLogicalField): Boolean {
                val start = syncStartOperationsByField[field].orEmpty()
                return currentIntentEntities.any { entity ->
                    entity.logicalField() == field &&
                        "${entity.operationId}:${entity.createdAt}" in activeMainIntentKeys &&
                        start.none { original -> original.id == entity.operationId && original.sourceVersion == entity.createdAt }
                }
            }
            if (newMedia.isNotEmpty()) database.mediaDao().upsertMedia(newMedia)
            suspend fun supersededState(state: UserMediaStateEntity): Boolean {
                val current = database.stateDao().get(state.mediaType, state.mediaId) ?: return false
                val before = localStatesByKey["${state.mediaType}:${state.mediaId}"]
                val libraryChanged = hasNewerLocalIntent(SyncLogicalField.library(MediaType.valueOf(state.mediaType), state.mediaId))
                val watchedChanged = state.mediaType == MediaType.MOVIE.name &&
                    hasNewerLocalIntent(SyncLogicalField.movieWatched(state.mediaId))
                return libraryChanged || watchedChanged || (current.dirty && (before == null || current.updatedAt > before.updatedAt))
            }
            val remoteStatesToApply = mutableListOf<UserMediaStateEntity>()
            for (state in remoteStates + remoteRemovedStates) {
                if (!supersededState(state)) remoteStatesToApply += state
            }
            val currentStatesByKey = database.stateDao().stateSnapshot()
                .associateBy { "${it.mediaType}:${it.mediaId}" }
            val currentPendingStates = pendingLocalStates.filter { state ->
                val current = currentStatesByKey["${state.mediaType}:${state.mediaId}"]
                current?.dirty == true && current.updatedAt == state.updatedAt && current.status == state.status
            }
            if (remoteStatesToApply.isNotEmpty() || currentPendingStates.isNotEmpty()) {
                database.stateDao().upsertAll(remoteStatesToApply)
                // Only validated, current user intent may override MAIN.
                database.stateDao().upsertAll(currentPendingStates)
            }
            for (mutation in reconciliation.localMutations) {
                // A merge only adds: never unwatch or drop something this device has.
                if (mergeMode && (
                        (mutation is LocalMutation.SetWatched && !mutation.watched) ||
                            (mutation is LocalMutation.SetLibraryStatus && mutation.status == LibraryStatus.NONE)
                        )
                ) continue
                val type = mutation.mediaType.name
                val id = mutation.mediaId.toInt()
                val before = localStatesByKey["$type:$id"]
                val current = database.stateDao().get(type, id)
                val stateSuperseded = hasNewerLocalIntent(mutationField(mutation)) ||
                    (current?.dirty == true && (before == null || current.updatedAt > before.updatedAt))
                val watchedMutation = mutation as? LocalMutation.SetWatched
                val mutationSeason = watchedMutation?.season
                val mutationEpisode = watchedMutation?.episode
                var episodeSuperseded = false
                if (mutationSeason != null && mutationEpisode != null) {
                    for (write in currentPendingWrites) {
                        if (write.mediaType != type || write.mediaId != id ||
                            write.operation !in setOf(SyncOperationType.EPISODE_WATCHED.name, SyncOperationType.EPISODE_UNWATCHED.name)
                        ) continue
                        val parts = write.payload.split(':', limit = 3)
                        if (parts.getOrNull(0)?.toIntOrNull() != mutationSeason ||
                            parts.getOrNull(1)?.toIntOrNull() != mutationEpisode
                        ) continue
                        val targetsMain = write.id in exactWriteIds ||
                            syncOperationRepository.currentIntentTargetsProvider(
                                "write:${write.id}",
                                write.createdAt,
                                TrackingProviderId.SIMKL,
                            )
                        if (targetsMain) {
                            episodeSuperseded = true
                            break
                        }
                    }
                }
                if (stateSuperseded || episodeSuperseded) continue
                // Simkl can still answer with the state from before a change
                // this device already delivered: its lists catch up a little
                // later. When Simkl still reports the last synced value and
                // this device changed it in the last half hour, after Simkl's
                // own record of the item, the change is kept instead of undone
                // (and so never mirrored back to the second tracker).
                val lastSynced = baselineValue(previousBaseline, type, id)
                if (mutation is LocalMutation.SetLibraryStatus && before != null && before.status != mutation.status.name &&
                    lastSynced != null && lastSynced != before.status && lastSynced == mutation.status.name &&
                    before.updatedAt > committedAt - RECENT_LOCAL_CHANGE_MS &&
                    before.updatedAt > (remoteStatesByKey["$type:$id"]?.updatedAt ?: 0L)
                ) {
                    if (before.status == LibraryStatus.NONE.name) keptRemovals += "$type:$id"
                    preferences.appendErrorLog("${Instant.now()}  Simkl sync: kept this device's change $type:$id ${before.status} (Simkl still had ${mutation.status.name}; ${simklOrigin(type, id)})")
                    continue
                }
                if (mutation is LocalMutation.SetLibraryStatus && before?.status == LibraryStatus.NONE.name && mutation.status != LibraryStatus.NONE) {
                    preferences.appendErrorLog("${Instant.now()}  Simkl sync: re-added $type:$id as ${mutation.status.name} (baseline=${lastSynced ?: "none"}; ${simklOrigin(type, id)})")
                }
                appliedRemoteMutations += mutation
                val previous = database.stateDao().get(type, id)
                when (mutation) {
                    is LocalMutation.SetLibraryStatus -> {
                        database.stateDao().upsert(
                            UserMediaStateEntity(type, id, mutation.status.name, previous?.watched ?: false, previous?.simklId, remoteActivityAt(remoteStatesByKey["$type:$id"]?.updatedAt, null, previous?.updatedAt, committedAt), dirty = false),
                        )
                    }
                    is LocalMutation.SetWatched -> {
                        database.stateDao().upsert(UserMediaStateEntity(type, id, previous?.status ?: LibraryStatus.NONE.name, mutation.watched, previous?.simklId, remoteActivityAt(remoteStatesByKey["$type:$id"]?.updatedAt, mutation.watchedAt?.toEpochMilli(), previous?.updatedAt, committedAt), dirty = false))
                        if (mutation.season != null && mutation.episode != null) {
                            if (mutation.watched) database.timelineDao().insertHistory(WatchHistoryEntity(mediaType = type, mediaId = id, season = mutation.season, episodeNumber = mutation.episode, watchedAt = mutation.watchedAt?.toString() ?: Instant.now().toString()))
                            else database.timelineDao().deleteEpisodeHistory(type, id, mutation.season, mutation.episode)
                        } else if (!mutation.watched) database.timelineDao().deleteMediaHistory(type, id)
                    }
                }
            }
            val remoteRemovedStatesToApply = remoteRemovedStates.filter { state -> state in remoteStatesToApply }
            remoteRemovedStatesToApply.groupBy(UserMediaStateEntity::mediaType).forEach { (mediaType, states) ->
                states.map(UserMediaStateEntity::mediaId).distinct().chunked(500).forEach { ids ->
                    database.timelineDao().deleteMediaHistories(mediaType, ids)
                }
            }
            // These replace local episode history with Simkl's; a merge keeps every local row.
            if (!mergeMode) {
                baselineShowIds.chunked(500).forEach { ids ->
                    database.timelineDao().deleteMediaHistories(MediaType.TV.name, ids)
                }
                historyRowsToReplace.chunked(500).forEach { ids ->
                    database.timelineDao().deleteHistoryRows(ids)
                }
            }
            // Room can bind the complete import in one prepared batch instead
            // of executing one INSERT statement for every watched episode.
            if (importedHistory.isNotEmpty()) {
                // Retain the local row once, even if the download already includes
                // that same episode. Otherwise watch-time statistics double-count it.
                val localKeys = locallyAdded.mapNotNull { it.episodeKey() }.toSet()
                // A merge keeps local rows, so only episodes and movies this device lacks are added.
                val mergeKeptKeys = if (mergeMode) currentKeys else emptySet()
                val mergeKeptMovies = if (mergeMode) {
                    currentHistory.filter { it.mediaType == MediaType.MOVIE.name && it.season == null }.mapTo(linkedSetOf()) { it.mediaId }
                } else emptySet()
                database.timelineDao().insertHistoryItems(
                    importedHistory.filterNot {
                        "${it.mediaType}:${it.mediaId}" in keptRemovals ||
                        it.episodeKey() in locallyRemoved || it.episodeKey() in localKeys ||
                            it.episodeKey() in mergeKeptKeys ||
                            (it.mediaType == MediaType.MOVIE.name && (it.mediaId in protectedMovieIds || it.mediaId in mergeKeptMovies))
                    },
                )
            }
            if (locallyAdded.isNotEmpty()) {
                database.timelineDao().insertHistoryItems(locallyAdded)
            }
            importedPlayback?.let { playbacks ->
                // An unchanged generation plus an empty response is treated as a
                // transient/partial playback response. Preserve the previous cache;
                // a real removal is committed when Simkl advances its activity id.
                if (playbacks.isNotEmpty() || remoteChanged) {
                    database.timelineDao().clearPlayback()
                    database.timelineDao().upsertPlayback(playbacks)
                }
            }
            if (conflictOperations.isNotEmpty()) database.syncDao().upsertOperations(conflictOperations)
            database.syncDao().upsertAll(syncStates)
            // Library membership, history, playback and sync generation become
            // visible in the same commit. No observer can see the halfway state.
            if (libraryChanged) rebuildLibraryRailInTransaction()
        }
        }
        if (committedAsMain == null) {
            // Transport bookkeeping can still be acknowledged by the
            // coordinator, but a role-swapped provider may not commit its
            // downloaded snapshot or create mirrors as the current MAIN.
            return@cancellableResult ProviderSyncOutcome(
                itemsChanged = false,
                report = previousReport,
                acknowledgedOperationIds = transportedOperationIds,
                deferredOperationIds = deferredOperationIds,
            )
        }
        // MAIN reconciliation is authoritative locally, but a remote-only
        // change still needs a SECONDARY-only durable mirror. Stable ids make
        // a newer remote generation supersede an older queued mirror.
        suspend fun mirrorStillCurrent(mutation: LocalMutation): Boolean {
            val key = "${mutation.mediaType.name}:${mutation.mediaId.toInt()}"
            val before = localStatesByKey[key]
            val current = database.stateDao().get(mutation.mediaType.name, mutation.mediaId.toInt())
            if (current?.dirty == true && (before == null || current.updatedAt > before.updatedAt)) return false
            val field = when (mutation) {
                is LocalMutation.SetLibraryStatus -> SyncLogicalField.library(mutation.mediaType, mutation.mediaId.toInt())
                is LocalMutation.SetWatched -> if (mutation.season != null && mutation.episode != null) {
                    SyncLogicalField.episodeWatched(mutation.mediaId.toInt(), mutation.season, mutation.episode)
                } else SyncLogicalField.movieWatched(mutation.mediaId.toInt())
            }
            val currentIntentEntities = database.syncDao().syncOperations()
            for (operation in currentIntentEntities) {
                if (operation.status !in setOf(SyncOperationStatus.PENDING.name, SyncOperationStatus.FAILED.name)) continue
                if (operation.createdAt <= syncStartedAt || operation.logicalField() != field) continue
                if (syncOperationRepository.currentIntentTargetsProvider(operation.operationId, operation.createdAt, TrackingProviderId.SIMKL)) return false
            }
            return true
        }
        for (mutation in appliedRemoteMutations) {
            if (!mirrorStillCurrent(mutation)) continue
            val mirrorGeneration = nextCausalGeneration()
            val operation = when (mutation) {
                is LocalMutation.SetLibraryStatus -> SyncOperation(
                    id = "mirror:library:${mutation.mediaType.name}:${mutation.mediaId}",
                    type = SyncOperationType.LIBRARY_STATUS,
                    mediaType = mutation.mediaType,
                    mediaId = mutation.mediaId.toInt(),
                    title = localMediaByKey["${mutation.mediaType.name}:${mutation.mediaId.toInt()}"]?.title
                        ?: "${mutation.mediaType.name} #${mutation.mediaId}",
                    value = mutation.status.name,
                    sourceVersion = mirrorGeneration,
                )
                is LocalMutation.SetWatched -> {
                    val movie = mutation.season == null
                    val watched = mutation.watched
                    SyncOperation(
                        id = if (movie) "mirror:movie-watched:${mutation.mediaId}" else "mirror:episode-watched:${mutation.mediaId}:${mutation.season}:${mutation.episode}",
                        type = when {
                            movie && watched -> SyncOperationType.MOVIE_WATCHED
                            movie -> SyncOperationType.MOVIE_UNWATCHED
                            watched -> SyncOperationType.EPISODE_WATCHED
                            else -> SyncOperationType.EPISODE_UNWATCHED
                        },
                        mediaType = mutation.mediaType,
                        mediaId = mutation.mediaId.toInt(),
                        title = localMediaByKey["${mutation.mediaType.name}:${mutation.mediaId.toInt()}"]?.title
                            ?: "${mutation.mediaType.name} #${mutation.mediaId}",
                        value = watched.toString(),
                        payload = if (!movie && watched) {
                            "${mutation.season}:${mutation.episode}:${mutation.watchedAt}"
                        } else if (!movie) {
                            "${mutation.season}:${mutation.episode}"
                        } else if (watched) {
                            mutation.watchedAt?.toString()
                        } else {
                            database.stateDao().get(MediaType.MOVIE.name, mutation.mediaId.toInt())?.status
                        },
                        sourceVersion = mirrorGeneration,
                    )
                }
            }
            if (isMainProviderCurrent()) enqueueSecondaryMirror(operation)
        }
        preferences.markSimklChecked(committedAt)
        val advancedBaseline = advanceSyncBaseline(
            previous = previousBaseline,
            remote = remoteTrackingSnapshot,
            acknowledgedStates = localStatesToPushExact.filter { stateOperationId(it.mediaType, it.mediaId) in transportedOperationIds },
            acknowledgedMovieHistoryOperations = operationsToPush.filter {
                it.id in transportedOperationIds &&
                    it.type in setOf(SyncOperationType.MOVIE_WATCHED, SyncOperationType.MOVIE_UNWATCHED)
            },
            acknowledgedEpisodeWrites = episodeWritesToPushExact,
            acknowledgedHistoryRemovals = historyRemovalsToPushExact,
            conflicts = reconciliation.conflicts,
        )
        val storedBaseline = preferences.syncBaselineNow(TrackingProviderId.SIMKL)
        val savedBaseline = keepDeliveriesDuringSync(previousBaseline, storedBaseline, advancedBaseline)
        // Diagnostic: a show the device removed that the saved copy of the
        // Simkl library still lists comes back on a later sync.
        savedBaseline.shows.filter { it.libraryState != null && it.libraryState != LibraryStatus.NONE }.forEach { show ->
            val id = show.ids.tmdb?.toInt() ?: return@forEach
            if (database.stateDao().get(MediaType.TV.name, id)?.status != LibraryStatus.NONE.name) return@forEach
            fun at(snapshot: TrackingSnapshot?) = snapshot?.shows?.firstOrNull { it.ids.tmdb == show.ids.tmdb }?.libraryState?.name ?: "absent"
            preferences.appendErrorLog("${Instant.now()}  Simkl sync: saved copy keeps removed TV:$id as ${show.libraryState} (start=${at(previousBaseline)} stored=${at(storedBaseline)} remote=${at(remoteTrackingSnapshot)} pulled=${at(pulledTrackingSnapshot)})")
        }
        preferences.saveSyncBaseline(savedBaseline, TrackingProviderId.SIMKL)
        progress(true, .78f, SyncStage.COMMIT)
        val remainingPendingChanges = database.stateDao().pendingStates().size + database.syncDao().pendingWrites().size
        val addedCount = remoteStates.count { state ->
            localStatesByKey["${state.mediaType}:${state.mediaId}"]?.status in setOf(null, LibraryStatus.NONE.name)
        }
        val conflictCount = conflictOperations.size
        val fullSync = !episodeBaselineComplete || !librarySnapshotComplete || completeTvSnapshot || completeMovieSnapshot
        val report = SyncReport(
            downloaded = resolvedItems.size + importedHistory.size,
            uploaded = pendingCount + (mergePlan?.let { it.titles + it.movies + it.episodes } ?: 0),
            added = addedCount,
            removed = remoteRemovedStates.size,
            unchanged = (localStates.size - addedCount - remoteRemovedStates.size).coerceAtLeast(0),
            pendingLocalChanges = remainingPendingChanges,
            conflicts = conflictCount,
            lastFullSync = if (fullSync) committedAt else previousReport.lastFullSync,
            lastIncrementalSync = committedAt,
            databaseUntouched = false,
        )
        preferences.saveSyncReport(report)
        if (firstSyncMode != null) preferences.setSimklFirstSyncMode(null)
        val tvLibraryChanged = (remoteStates + remoteRemovedStates + localStatesToPushExact)
            .any { it.mediaType == MediaType.TV.name }
        val episodeHistoryChanged = importedHistory.any { it.mediaType == MediaType.TV.name } ||
            baselineShowIds.isNotEmpty() || historyRowsToReplace.isNotEmpty() || pendingEpisodeWrites.isNotEmpty() ||
            pendingMediaHistoryRemovals.any { it.mediaType == MediaType.TV.name }
        val progressChanged = refreshProgressCache(
            request = ProgressRefreshRequest(
                tvLibraryChanged = tvLibraryChanged,
                episodeHistoryChanged = episodeHistoryChanged,
                tvPlaybackChanged = tvPlaybackChanged,
            ),
        ) { value ->
            progress(true, value, SyncStage.PROCESSING)
        }
        onProgress(SyncProgress(false, 1f, SyncStage.COMPLETE, lastSuccessfulSync = committedAt, report = report))
        val acknowledgedOperationIds = transportedOperationIds.intersect(operations.mapTo(linkedSetOf(), SyncOperation::id))
        ProviderSyncOutcome(
            itemsChanged = remoteChanged || pendingCount > 0 || progressChanged,
            report = report,
            acknowledgedOperationIds = acknowledgedOperationIds,
            deferredOperationIds = deferredOperationIds,
        )
        }.onSuccess {
            // One-time renumbering of anime plays stored in Simkl's numbering; never throws into sync.
            host.animeNumberingRepair?.runIfNeeded()
        }.onFailure { error ->
            val committedSync = database.syncDao().get("all")?.lastSuccessfulSync
            val databaseWasUpdated = committedSync != null && committedSync != previousSuccessfulSync
            val report = previousReport.copy(
                pendingLocalChanges = database.stateDao().pendingStates().size + database.syncDao().pendingWrites().size,
                failedOperations = 1,
                lastIncrementalSync = committedSync ?: previousReport.lastIncrementalSync,
                databaseUntouched = !databaseWasUpdated,
            )
            preferences.saveSyncReport(report)
            onProgress(SyncProgress(false, 0f, SyncStage.ERROR, error.message, previousSuccessfulSync, report))
        }
    }

}

/**
 * The library time to store when a Simkl change is applied locally. It feeds Progress's recency
 * order, so it is the change's own time on Simkl (the watch, or the title's last activity), or
 * else the time this device already had, and the commit time only for a title never seen.
 * Stamping every applied change with the commit time tied all of them at the sync moment, which
 * pushed a show watched just before the sync below the rest and sorted the others by title.
 */
private const val RECENT_LOCAL_CHANGE_MS = 30 * 60 * 1000L

/**
 * The baseline this sync saves was built from the one read when it started. A status
 * Simkl accepted meanwhile (recordDeliveredLibraryState writes the stored baseline as
 * soon as Simkl confirms it) must survive that save: otherwise a removal delivered
 * during a sync was written back as the old status, Simkl's change-only downloads never
 * mention the removal, and half an hour later the show came back In progress (and was
 * mirrored to Floppy). Titles the stored baseline changed since [start] keep the stored
 * entry, episodes included; everything else is this sync's result.
 */
internal fun keepDeliveriesDuringSync(start: TrackingSnapshot?, stored: TrackingSnapshot?, advanced: TrackingSnapshot): TrackingSnapshot {
    if (stored == null || stored == start) return advanced
    val startShows = start?.shows.orEmpty().associateBy { it.ids.tmdb }
    val storedShows = stored.shows.associateBy { it.ids.tmdb }
    val changedShows = (startShows.keys + storedShows.keys).filterNotNull().filter { startShows[it] != storedShows[it] }.toSet()
    val startMovies = start?.movies.orEmpty().associateBy { it.ids.tmdb }
    val storedMovies = stored.movies.associateBy { it.ids.tmdb }
    val changedMovies = (startMovies.keys + storedMovies.keys).filterNotNull().filter { startMovies[it] != storedMovies[it] }.toSet()
    if (changedShows.isEmpty() && changedMovies.isEmpty()) return advanced
    return advanced.copy(
        shows = advanced.shows.filterNot { it.ids.tmdb in changedShows } + changedShows.mapNotNull(storedShows::get),
        episodes = advanced.episodes.filterNot { it.showIds.tmdb in changedShows } + stored.episodes.filter { it.showIds.tmdb in changedShows },
        movies = advanced.movies.filterNot { it.ids.tmdb in changedMovies } + changedMovies.mapNotNull(storedMovies::get),
    )
}

/**
 * The Simkl baseline after Simkl accepted a library status: a removal drops
 * the item (and a show's episodes), any other status replaces it. Without it
 * a later sync whose lists count as incomplete reads the old value from the
 * baseline as if Simkl still had it and puts a removed item back.
 */
internal fun withDeliveredLibraryState(
    baseline: TrackingSnapshot,
    mediaType: MediaType,
    mediaId: Long,
    status: LibraryStatus,
    watched: Boolean,
): TrackingSnapshot = if (mediaType == MediaType.MOVIE) {
    val others = baseline.movies.filterNot { it.ids.tmdb == mediaId }
    val previous = baseline.movies.firstOrNull { it.ids.tmdb == mediaId }
    baseline.copy(
        movies = if (status == LibraryStatus.NONE) others
        else others + (previous?.copy(libraryState = status) ?: TrackedMovieState(MediaIds(tmdb = mediaId), status, watched = watched)),
    )
} else {
    baseline.copy(
        shows = baseline.shows.filterNot { it.ids.tmdb == mediaId } +
            if (status == LibraryStatus.NONE) emptyList() else listOf(TrackedShowState(MediaIds(tmdb = mediaId), status)),
        episodes = if (status == LibraryStatus.NONE) baseline.episodes.filterNot { it.showIds.tmdb == mediaId } else baseline.episodes,
    )
}

/**
 * Whether a library item missing from Simkl's downloaded lists was removed
 * there. A complete download decides on its own; a full download with
 * unresolved items decides only for an item whose Simkl id is known
 * ([remoteSimklIds] is then every Simkl id Simkl listed, else null).
 */
internal fun removedFromSimklList(complete: Boolean, remoteSimklIds: Set<Long>?, simklId: Long?): Boolean = when {
    complete -> true
    remoteSimklIds == null || simklId == null -> false
    else -> simklId !in remoteSimklIds
}

/** The baseline without items Simkl's full lists no longer have (by known Simkl id). */
internal fun TrackingSnapshot.withoutAbsentSimklItems(remoteShowSimklIds: Set<Long>?, remoteMovieSimklIds: Set<Long>?): TrackingSnapshot {
    val goneShows = remoteShowSimklIds?.let { listed ->
        shows.filter { it.ids.simkl != null && it.ids.simkl !in listed }.mapNotNullTo(hashSetOf()) { it.ids.tmdb }
    }.orEmpty()
    val goneMovies = remoteMovieSimklIds?.let { listed ->
        movies.filter { it.ids.simkl != null && it.ids.simkl !in listed }.mapNotNullTo(hashSetOf()) { it.ids.tmdb }
    }.orEmpty()
    if (goneShows.isEmpty() && goneMovies.isEmpty()) return this
    return copy(
        shows = shows.filterNot { it.ids.tmdb in goneShows },
        episodes = episodes.filterNot { it.showIds.tmdb in goneShows },
        movies = movies.filterNot { it.ids.tmdb in goneMovies },
    )
}

internal fun remoteActivityAt(remoteUpdatedAt: Long?, watchedAt: Long?, previousUpdatedAt: Long?, committedAt: Long): Long =
    watchedAt ?: remoteUpdatedAt ?: previousUpdatedAt ?: committedAt

private fun String.fromCineTrackStatus(): LibraryStatus =
    runCatching { LibraryStatus.valueOf(this) }.getOrDefault(LibraryStatus.NONE)
private fun SimklLibraryItem.toState(type: MediaType, resolvedTmdbId: Int? = null, localUpdatedAt: Long? = null): UserMediaStateEntity? {
    val item = show ?: movie ?: return null
    val tmdbId = resolvedTmdbId ?: item.ids.tmdb?.toIntOrNull() ?: return null
    return UserMediaStateEntity(
        mediaType = type.name,
        mediaId = tmdbId,
        status = status.fromSimklStatus().name,
        watched = status == "completed",
        simklId = item.ids.simkl,
        updatedAt = listOfNotNull(lastWatchedAt, addedAt).firstNotNullOfOrNull { raw ->
            runCatching { Instant.parse(raw).toEpochMilli() }.getOrNull()
        } ?: localUpdatedAt ?: System.currentTimeMillis(),
        dirty = false,
    )
}

private fun SimklLibraryItem.toMediaEntity(type: MediaType, resolvedTmdbId: Int? = null): MediaEntity? {
    val item = show ?: movie ?: return null
    val tmdbId = resolvedTmdbId ?: item.ids.tmdb?.toIntOrNull() ?: return null
    return MediaEntity(
        mediaType = type.name,
        tmdbId = tmdbId,
        title = item.title.ifBlank { "Untitled" },
        overview = "",
        posterPath = null,
        backdropPath = null,
        releaseDate = item.year?.toString(),
        score = null,
        runtimeMinutes = item.runtime,
        genres = "",
        providers = "",
        collectionId = null,
    )
}

private fun SimklPlaybackItem.toMediaEntity(): MediaEntity? {
    val item = show ?: movie ?: return null
    val tmdbId = item.ids.tmdb?.toIntOrNull() ?: return null
    return MediaEntity(
        mediaType = if (movie != null) MediaType.MOVIE.name else MediaType.TV.name,
        tmdbId = tmdbId,
        title = item.title.ifBlank { "Untitled" },
        overview = "",
        posterPath = null,
        backdropPath = null,
        releaseDate = item.year?.toString(),
        score = null,
        runtimeMinutes = item.runtime,
        genres = "",
        providers = "",
        collectionId = null,
    )
}

private fun SimklPlaybackItem.toPlaybackEntity(): PlaybackEntity? {
    val item = show ?: movie ?: return null
    val tmdbId = item.ids.tmdb?.toIntOrNull() ?: return null
    val episodeNumber = episode?.number ?: episode?.episode
    return PlaybackEntity(
        mediaType = if (movie != null) MediaType.MOVIE.name else MediaType.TV.name,
        mediaId = tmdbId,
        episodeId = id,
        progress = (progress / 100.0).toFloat().coerceIn(0f, 1f),
        positionSeconds = 0,
        durationSeconds = 0,
        updatedAt = pausedAt,
        season = episode?.season,
        episodeNumber = episodeNumber,
        episodeTitle = episode?.title,
    )
}

private fun EpisodeCard.toEntity() = EpisodeEntity(
    showId = showId,
    season = season,
    number = number,
    tmdbId = id.takeIf { it > 0 },
    title = title,
    overview = overview,
    airDate = airDate,
    stillPath = stillUrl?.substringAfter("/w780", stillUrl)?.substringAfter("/w1280", stillUrl),
    runtimeMinutes = runtimeMinutes,
)

private fun EpisodeEntity.toDomain() = EpisodeCard(
    id = tmdbId ?: 0,
    showId = showId,
    season = season,
    number = number,
    title = title,
    overview = overview,
    airDate = airDate,
    stillUrl = tmdbImageUrl(stillPath, "w780"),
    runtimeMinutes = runtimeMinutes,
)

private fun String.fromSimklStatus() = when (this) {
    "watching" -> LibraryStatus.WATCHING
    "hold" -> LibraryStatus.PAUSED
    "plantowatch" -> LibraryStatus.PLAN_TO_WATCH
    "completed" -> LibraryStatus.COMPLETED
    "dropped" -> LibraryStatus.DROPPED
    else -> LibraryStatus.NONE
}

private fun String.toSimklStatus() = when (this) {
    LibraryStatus.WATCHING.name -> "watching"
    LibraryStatus.PLAN_TO_WATCH.name -> "plantowatch"
    LibraryStatus.PAUSED.name -> "hold"
    LibraryStatus.COMPLETED.name -> "completed"
    LibraryStatus.DROPPED.name -> "dropped"
    else -> null
}

private fun SimklIds.toTrackingIds() = MediaIds(
    tmdb = tmdb?.toLongOrNull(),
    tvdb = tvdb?.toLongOrNull(),
    imdb = imdb,
    simkl = simkl,
)

private fun String?.toInstantOrNull(): Instant? = this?.let { raw ->
    runCatching { Instant.parse(raw) }.getOrNull()
}

/** Simkl targets for the local TV history of a first-sync merge; entries missing from the map go unchanged. */
private suspend fun mergeSimklTargets(
    numbering: com.cinetrack.data.sync.numbering.EpisodeNumbering?,
    history: List<WatchHistoryEntity>,
): Map<Triple<Int, Int, Int>, com.cinetrack.data.sync.numbering.SimklTarget> {
    if (numbering == null) return emptyMap()
    val targets = mutableMapOf<Triple<Int, Int, Int>, com.cinetrack.data.sync.numbering.SimklTarget>()
    history.filter { it.mediaType == MediaType.TV.name && it.season != null && it.episodeNumber != null }
        .groupBy { it.mediaId }
        .forEach { (showId, rows) ->
            numbering.toSimklBatch(showId, rows.map { it.season!! to it.episodeNumber!! }).forEach { (coord, target) ->
                if (target != com.cinetrack.data.sync.numbering.SimklTarget.Unchanged) targets[Triple(showId, coord.first, coord.second)] = target
            }
        }
    return targets
}
