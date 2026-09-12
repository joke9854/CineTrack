package com.cinetrack.data.repository

import androidx.room.withTransaction
import com.cinetrack.BuildConfig
import com.cinetrack.data.library.LibraryRepository
import com.cinetrack.data.local.AppDatabase
import com.cinetrack.data.local.EpisodeEntity
import com.cinetrack.data.local.MediaEntity
import com.cinetrack.data.local.PendingWriteEntity
import com.cinetrack.data.local.PlaybackEntity
import com.cinetrack.data.local.SyncStateEntity
import com.cinetrack.data.local.SyncOperationEntity
import com.cinetrack.data.local.UpNextEntity
import com.cinetrack.data.local.UserMediaStateEntity
import com.cinetrack.data.local.WatchHistoryEntity
import com.cinetrack.data.local.toDomain
import com.cinetrack.data.local.toEntity
import com.cinetrack.data.remote.ApiServices
import com.cinetrack.data.remote.NetworkFactory
import com.cinetrack.data.remote.MdbListRatingRequest
import com.cinetrack.data.remote.SimklIds
import com.cinetrack.data.remote.SimklLibraryItem
import com.cinetrack.data.remote.SimklLibraryResponse
import com.cinetrack.data.remote.SimklPlaybackItem
import com.cinetrack.data.remote.SimklSyncItem
import com.cinetrack.data.remote.SimklSyncRequest
import com.cinetrack.data.remote.TmdbMediaDto
import com.cinetrack.data.schedule.ReleaseScheduleRepository
import com.cinetrack.data.sync.ProviderSyncOutcome
import com.cinetrack.data.sync.SyncCoordinator
import com.cinetrack.data.sync.SyncOperationRepository
import com.cinetrack.data.sync.SyncOperation
import com.cinetrack.data.sync.SyncOperationType
import com.cinetrack.data.sync.MediaIds
import com.cinetrack.data.sync.LocalTrackingSnapshot
import com.cinetrack.data.sync.SyncReconciler
import com.cinetrack.data.sync.TrackingSnapshot
import com.cinetrack.data.sync.TrackedMovieState
import com.cinetrack.data.sync.TrackedShowState
import com.cinetrack.data.sync.TrackedEpisodeState
import com.cinetrack.data.sync.LocalMutation
import com.cinetrack.data.sync.TrackingProviderId
import com.cinetrack.data.sync.SyncConflict
import com.cinetrack.data.sync.ConflictField
import com.cinetrack.domain.AppUiState
import com.cinetrack.domain.DiscoverMovieFilters
import com.cinetrack.domain.EpisodeCard
import com.cinetrack.domain.LibraryStatus
import com.cinetrack.domain.MediaCard
import com.cinetrack.domain.MediaType
import com.cinetrack.domain.PersonCard
import com.cinetrack.domain.PlaybackCard
import com.cinetrack.domain.RailIds
import com.cinetrack.domain.RatingScore
import com.cinetrack.domain.SeasonDetails
import com.cinetrack.domain.SeasonCard
import com.cinetrack.domain.SyncProgress
import com.cinetrack.domain.SyncConflictChoice
import com.cinetrack.domain.SyncOperationCard
import com.cinetrack.domain.SyncOperationStatus
import com.cinetrack.domain.SyncReport
import com.cinetrack.domain.SyncStage
import com.cinetrack.domain.StreamingProvider
import com.cinetrack.domain.TimelineCard
import com.cinetrack.domain.releaseDateTime
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.security.MessageDigest
import java.util.Locale

data class ProgressRefreshRequest(
    val tvLibraryChanged: Boolean = false,
    val episodeHistoryChanged: Boolean = false,
    val tvPlaybackChanged: Boolean = false,
    val force: Boolean = false,
) {
    fun requiresUpNext(scheduleDue: Boolean, cacheMissing: Boolean): Boolean =
        force || scheduleDue || cacheMissing || tvLibraryChanged || episodeHistoryChanged || tvPlaybackChanged

    fun mergedWith(other: ProgressRefreshRequest) = ProgressRefreshRequest(
        tvLibraryChanged = tvLibraryChanged || other.tvLibraryChanged,
        episodeHistoryChanged = episodeHistoryChanged || other.episodeHistoryChanged,
        tvPlaybackChanged = tvPlaybackChanged || other.tvPlaybackChanged,
        force = force || other.force,
    )
}

class CineTrackRepository(
    private val database: AppDatabase,
    private val services: ApiServices,
    val preferences: AppPreferences,
    private val awaitStartupReady: suspend () -> Unit = {},
    private val onTokenChanged: (String?) -> Unit = {},
    private val currentToken: () -> String? = { null },
    private val onTmdbApiKeyChanged: (String) -> Unit = {},
    private val tmdbApiKey: () -> String = { BuildConfig.TMDB_API_TOKEN },
    private val onMdbListApiKeyChanged: (String) -> Unit = {},
    private val mdbListApiKey: () -> String = { BuildConfig.MDBLIST_API_KEY },
    private val onMetadataLanguageChanged: (String) -> Unit = {},
    private val onMetadataRegionChanged: (String) -> Unit = {},
    private val onMetadataTimezoneChanged: (String) -> Unit = {},
    private val syncOperationRepository: SyncOperationRepository,
    private val syncCoordinator: SyncCoordinator,
    private val releaseScheduleRepository: ReleaseScheduleRepository,
    private val libraryRepository: LibraryRepository,
    private val syncReconciler: SyncReconciler = SyncReconciler(),
) {
    suspend fun awaitStartup() {
        awaitStartupReady()
        // Queue repair is idempotent and also runs defensively before every
        // sync, so upgraded installations cannot expose stale writes to UI.
        repairSyncQueue()
    }

    suspend fun loadWidgetUpNext(): PlaybackCard? {
        awaitStartup()
        val next = database.upNextDao().firstForWidget() ?: return null
        val media = database.mediaDao().get(MediaType.TV.name, next.showId) ?: return null
        val state = database.stateDao().get(MediaType.TV.name, next.showId)
        return PlaybackCard(
            media = media.toDomain(state),
            episodeId = next.episodeId,
            episodeLabel = "S${next.season.toString().padStart(2, '0')} E${next.episodeNumber.toString().padStart(2, '0')}",
            episodeTitle = next.episodeTitle,
            season = next.season,
            episodeNumber = next.episodeNumber,
            progress = 0f,
            remainingMinutes = next.durationMinutes,
            durationMinutes = next.durationMinutes,
            episodeAirDate = next.episodeAirDate,
        )
    }
    private fun stateOperationId(type: String, id: Int) = "state:$type:$id"
    private fun writeOperationId(id: Long) = "write:$id"

    private suspend fun queueStateOperation(media: MediaCard, status: LibraryStatus) {
        val updatedAt = database.stateDao().get(media.type.name, media.id)?.updatedAt ?: System.currentTimeMillis()
        database.syncDao().upsertOperation(
            SyncOperationEntity(
                operationId = stateOperationId(media.type.name, media.id),
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
    }

    /**
     * Repairs queue rows written by older reconciliation versions. A stale
     * reconcile:* row is never evidence of current local intent. State rows are
     * retained only when their value still matches the current dirty Room row;
     * dirty rows that already equal MAIN are cleaned, while genuinely changed
     * rows are materialized as one canonical state:* operation.
     */
    private suspend fun repairSyncQueue() {
        val baseline = preferences.syncBaselineNow(TrackingProviderId.SIMKL)
        database.withTransaction {
            val states = database.stateDao().stateSnapshot()
            val operations = database.syncDao().syncOperations()
            val media = database.mediaDao().mediaSnapshot().associateBy { "${it.mediaType}:${it.tmdbId}" }
            val deletes = operations.filter { it.operationId.startsWith("reconcile:") }.map(SyncOperationEntity::operationId).toMutableList()
            states.forEach { state ->
                val operationId = stateOperationId(state.mediaType, state.mediaId)
                val operation = operations.firstOrNull { it.operationId == operationId }
                val baselineValue = baselineValue(baseline, state.mediaType, state.mediaId)
                if (!state.dirty) {
                    if (operation != null) deletes += operationId
                    return@forEach
                }
                if (baselineValue != null && baselineValue == state.status) {
                    database.stateDao().upsert(state.copy(dirty = false))
                    if (operation != null) deletes += operationId
                    return@forEach
                }
                val current = operation != null &&
                    operation.status in setOf(SyncOperationStatus.PENDING.name, SyncOperationStatus.FAILED.name) &&
                    operation.localValue == state.status && operation.createdAt == state.updatedAt
                if (!current) {
                    database.syncDao().upsertOperation(
                        SyncOperationEntity(
                            operationId = operationId,
                            operation = SyncOperationType.LIBRARY_STATUS.name,
                            mediaType = state.mediaType,
                            mediaId = state.mediaId,
                            title = media["${state.mediaType}:${state.mediaId}"]?.title ?: "${state.mediaType} #${state.mediaId}",
                            status = SyncOperationStatus.PENDING.name,
                            localValue = state.status,
                            createdAt = state.updatedAt,
                            updatedAt = state.updatedAt,
                        ),
                    )
                }
            }
            if (deletes.isNotEmpty()) database.syncDao().deleteOperations(deletes.distinct())
            database.syncDao().upsert(SyncStateEntity("sync_queue_repair_087", null, System.currentTimeMillis()))
        }
    }

    private fun baselineValue(baseline: TrackingSnapshot?, mediaType: String, mediaId: Int): String? = when (mediaType) {
        MediaType.MOVIE.name -> baseline?.movies?.firstOrNull { it.ids.tmdb?.toInt() == mediaId }?.libraryState?.name
        MediaType.TV.name -> baseline?.shows?.firstOrNull { it.ids.tmdb?.toInt() == mediaId }?.libraryState?.name
        else -> null
    }

    private fun advanceSyncBaseline(
        previous: TrackingSnapshot?,
        remote: TrackingSnapshot,
        acknowledgedStates: List<UserMediaStateEntity>,
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

    private fun mergePulledSnapshot(
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

    private suspend fun validatedPendingLocalStates(): List<UserMediaStateEntity> {
        val operations = database.syncDao().syncOperations().associateBy(SyncOperationEntity::operationId)
        return database.stateDao().pendingStates().filter { state ->
            val operation = operations[stateOperationId(state.mediaType, state.mediaId)]
            operation != null &&
                operation.status in setOf(SyncOperationStatus.PENDING.name, SyncOperationStatus.FAILED.name) &&
                operation.localValue == state.status && operation.createdAt == state.updatedAt
        }
    }

    private suspend fun clearObsoleteOperations(
        mediaType: String,
        mediaId: Int,
        operationNames: Set<String>,
        season: Int? = null,
        episode: Int? = null,
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

    private suspend fun <T> cancellableResult(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        Result.failure(error)
    }

    private fun syncError(error: Throwable): String =
        error.message?.takeIf(String::isNotBlank) ?: error::class.java.simpleName
    private suspend fun localZone(): ZoneId {
        val configured = preferences.metadataTimezone.first()
        return if (configured == "system") ZoneId.systemDefault()
        else runCatching { ZoneId.of(configured) }.getOrDefault(ZoneId.systemDefault())
    }

    private suspend fun localToday(): LocalDate = LocalDate.now(localZone())

    /** TMDB accepts one watch region. Make Discover/provider availability follow
     * the content-region filter, then fall back to metadata and device regions. */
    private suspend fun effectiveProviderRegion(): String = com.cinetrack.domain.resolveProviderRegion(
        preferences.providerRegion.first(), preferences.contentRegions.first(),
        preferences.metadataRegion.first(), Locale.getDefault().country,
    )

    private fun scheduleTime(raw: String?): Long = raw?.takeIf(String::isNotBlank)?.let { value ->
        releaseDateTime(value, ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
    } ?: 0L

    private fun playbackRecency(item: PlaybackCard): Long = maxOf(
        item.media.libraryUpdatedAt ?: 0L,
        scheduleTime(item.episodeAirDate),
    )

    private val castCache = BoundedLruCache<String, List<PersonCard>>(32)
    private val recommendationCandidatesCache = BoundedLruCache<String, List<TmdbMediaDto>>(32)
    private val episodeTitleCache = BoundedLruCache<String, String>(750)
    private val progressCacheMutex = Mutex()
    private val progressEnrichmentScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var progressEnrichmentJob: Job? = null
    private var automaticBackupJob: Job? = null
    @Volatile private var progressEnrichmentRequested = false
    private val progressCacheRepository by lazy {
        ProgressCacheRepository(database, preferences, tmdbApiKey, ::loadEpisodes)
    }
    val progressCacheRefreshing: Boolean get() = progressCacheMutex.isLocked

    @Synchronized
    internal fun scheduleAutomaticBackup() {
        automaticBackupJob?.cancel()
        automaticBackupJob = progressEnrichmentScope.launch {
            delay(1_500)
            createAutomaticBackup()
        }
    }
    suspend fun loadInitial(): AppUiState {
        val cached = loadCachedState()
        if (tmdbApiKey().isBlank()) {
            return cached.copy(loading = false, error = "TMDB_API_TOKEN is missing")
        }
        return cancellableResult {
            refreshDiscover()
            loadCachedState().copy(loading = false)
        }.getOrElse { cached.copy(loading = false, error = it.message) }
    }

    suspend fun refreshDiscover() = coroutineScope {
        check(tmdbApiKey().isNotBlank()) { "TMDB API credential is missing" }
        val allowedRegions = preferences.contentRegions.first()
        val hiddenDiscovery = preferences.hiddenDiscovery.first()
        val regionQuery = allowedRegions.takeIf { it.isNotEmpty() }?.sorted()?.joinToString("|")
        fun List<com.cinetrack.data.remote.TmdbMediaDto>.inAllowedRegions() = filter { dto ->
            allowedRegions.isEmpty() || dto.originCountries.isEmpty() || dto.originCountries.any(allowedRegions::contains)
        }
        // Bound all Discover requests, and reuse Popular's first page when a
        // country-filtered Trending list needs filling. No extra fallback call.
        val requests = Semaphore(6)
        suspend fun pages(load: suspend (Int) -> List<com.cinetrack.data.remote.TmdbMediaDto>) = coroutineScope {
            (1..3).map { page -> async { requests.withPermit { load(page) } } }
                .map { it.await() }.flatten()
        }
        val popularTv = async {
            pages { services.tmdb.discoverTv(regionQuery, sortBy = "popularity.desc", page = it).results }
                .inAllowedRegions().distinctBy { it.id }
        }
        val popularMovies = async {
            pages { services.tmdb.discoverMovies(regionQuery, sortBy = "popularity.desc", page = it).results }
                .inAllowedRegions().distinctBy { it.id }
        }
        val tv = async {
            loadTrendingCandidates(
                allowedOrigins = allowedRegions,
                loadPage = { page -> requests.withPermit { services.tmdb.trendingTv(page).results } },
                loadFallback = { popularTv.await().take(20) },
                include = { "${MediaType.TV.name}:${it.id}" !in hiddenDiscovery },
            ).map { it.toEntity(MediaType.TV) }
        }
        val movies = async {
            loadTrendingCandidates(
                allowedOrigins = allowedRegions,
                loadPage = { page -> requests.withPermit { services.tmdb.trendingMovies(page).results } },
                loadFallback = { popularMovies.await().take(20) },
                include = { "${MediaType.MOVIE.name}:${it.id}" !in hiddenDiscovery },
            ).map { it.toEntity(MediaType.MOVIE) }
        }
        val today = localToday()
        val upcomingMovies = async {
            pages { page ->
                services.tmdb.discoverMovies(
                    originCountries = regionQuery,
                    sortBy = "popularity.desc",
                    dateFrom = today.plusDays(1).toString(),
                    page = page,
                ).results
            }.inAllowedRegions().map { it.toEntity(MediaType.MOVIE) }
                .filterNot { "${it.mediaType}:${it.tmdbId}" in hiddenDiscovery }
        }
        val upcomingTv = async {
            cancellableResult {
                pages { page ->
                    services.tmdb.upcomingTv(
                        dateFrom = today.plusDays(1).toString(),
                        sortBy = "popularity.desc",
                        originCountries = regionQuery,
                        page = page,
                    ).results
                }.inAllowedRegions().map { it.toEntity(MediaType.TV) }
                    .filterNot { "${it.mediaType}:${it.tmdbId}" in hiddenDiscovery }
            }
        }
        fun List<com.cinetrack.data.remote.TmdbMediaDto>.asRail(type: MediaType) =
            filterNot { "${type.name}:${it.id}" in hiddenDiscovery }.map { it.toEntity(type) }
        // Await every network result before taking Room's write lock.
        val upcomingTvResult = upcomingTv.await()
        val upcomingTvItems = upcomingTvResult.getOrElse {
            // A partial Discover refresh must not erase a previously valid rail.
            // Keep cached TV releases while fresh movie data can still update.
            database.mediaDao().railMedia(RailIds.UPCOMING).filter { it.mediaType == MediaType.TV.name }
        }
        val rails = linkedMapOf(
            RailIds.TRENDING_TV to tv.await(),
            RailIds.TRENDING_MOVIES to movies.await(),
            RailIds.POPULAR_TV to popularTv.await().asRail(MediaType.TV),
            RailIds.POPULAR_MOVIES to popularMovies.await().asRail(MediaType.MOVIE),
            RailIds.UPCOMING to (upcomingMovies.await() + upcomingTvItems)
                .filter { entity ->
                    entity.releaseDate?.let { raw -> runCatching { LocalDate.parse(raw.take(10)).isAfter(today) }.getOrDefault(false) } == true
                }
                .distinctBy { "${it.mediaType}:${it.tmdbId}" }
                .sortedBy(MediaEntity::releaseDate),
        )
        database.withTransaction {
            rails.forEach { (id, items) -> database.mediaDao().replaceRail(id, items) }
        }
    }

    /** Browse on demand without enlarging refresh rails or overwriting detailed cached metadata. */
    suspend fun loadDiscoverPage(railId: String, page: Int): com.cinetrack.domain.DiscoverPage = coroutineScope {
        check(tmdbApiKey().isNotBlank()) { "TMDB API credential is missing" }
        require(page in 1..500)
        val regions = preferences.contentRegions.first()
        val origins = regions.takeIf { it.isNotEmpty() }?.sorted()?.joinToString("|")
        val hidden = preferences.hiddenDiscovery.first()
        val today = localToday()
        val responses: List<Pair<MediaType, com.cinetrack.data.remote.TmdbPage>> = when (railId) {
            RailIds.TRENDING_TV -> listOf(MediaType.TV to services.tmdb.trendingTv(page))
            RailIds.TRENDING_MOVIES -> listOf(MediaType.MOVIE to services.tmdb.trendingMovies(page))
            RailIds.POPULAR_TV -> listOf(MediaType.TV to services.tmdb.discoverTv(origins, page = page))
            RailIds.POPULAR_MOVIES -> listOf(MediaType.MOVIE to services.tmdb.discoverMovies(origins, page = page))
            RailIds.UPCOMING -> {
                val movies = async { services.tmdb.discoverMovies(origins, dateFrom = today.plusDays(1).toString(), page = page) }
                val tv = async { services.tmdb.upcomingTv(today.plusDays(1).toString(), originCountries = origins, page = page) }
                listOf(MediaType.MOVIE to movies.await(), MediaType.TV to tv.await())
            }
            else -> error("Unknown Discover list")
        }
        val states = database.stateDao().observeAll().first().associateBy { "${it.mediaType}:${it.mediaId}" }
        val cards = responses.flatMap { (type, response) ->
            response.results.filter { dto ->
                regions.isEmpty() || dto.originCountries.any(regions::contains) ||
                    (railId != RailIds.TRENDING_TV && railId != RailIds.TRENDING_MOVIES && dto.originCountries.isEmpty())
            }.map { it.toEntity(type) }.map { it.toDomain(states["${it.mediaType}:${it.tmdbId}"]) }
        }.distinctBy(MediaCard::stableKey).filterNot { it.stableKey in hidden }
            .filter { railId != RailIds.UPCOMING || it.releaseDate?.let { raw ->
                runCatching { LocalDate.parse(raw.take(10)).isAfter(today) }.getOrDefault(false)
            } == true }
        com.cinetrack.domain.DiscoverPage(cards, page < 500 && responses.any { page < it.second.totalPages })
    }

    suspend fun discoverMovies(filters: DiscoverMovieFilters): List<MediaCard> = coroutineScope {
        check(tmdbApiKey().isNotBlank()) { "TMDB API credential is missing" }
        val allowedRegions = preferences.contentRegions.first()
        val hiddenDiscovery = preferences.hiddenDiscovery.first()
        val regionQuery = allowedRegions.takeIf { it.isNotEmpty() }?.sorted()?.joinToString("|")
        val states = database.stateDao().observeAll().first().associateBy { "${it.mediaType}:${it.mediaId}" }
        val genreQuery = filters.genreIds.takeIf { it.isNotEmpty() }?.sorted()?.joinToString(",")
        val excludedGenreQuery = filters.excludedGenreIds.takeIf { it.isNotEmpty() }?.sorted()?.joinToString(",")
        val providerQuery = filters.providerIds.takeIf { it.isNotEmpty() }?.sorted()?.joinToString("|")
        val watchRegion = effectiveProviderRegion()
        val decadeFrom = filters.decadeStart?.let { "$it-01-01" }
        val decadeTo = filters.decadeStart?.let { "${it + 9}-12-31" }
        val entities = (1..3).flatMap { page ->
            if (filters.mediaType == MediaType.TV) {
                services.tmdb.discoverTv(
                    originCountries = regionQuery,
                    sortBy = filters.sortBy.replace("primary_release_date", "first_air_date"),
                    releaseYear = filters.releaseYear,
                    genreIds = genreQuery,
                    excludedGenreIds = excludedGenreQuery,
                    minimumRating = filters.minimumRating,
                    minimumVotes = filters.minimumRating?.let { 50 },
                    providerIds = providerQuery,
                    watchRegion = watchRegion,
                    maximumRuntime = filters.maximumRuntime,
                    originalLanguage = filters.originalLanguage,
                    dateFrom = decadeFrom,
                    dateTo = decadeTo,
                    page = page,
                ).results
            } else {
                services.tmdb.discoverMovies(
                    originCountries = regionQuery,
                    sortBy = filters.sortBy,
                    releaseYear = filters.releaseYear,
                    genreIds = genreQuery,
                    excludedGenreIds = excludedGenreQuery,
                    minimumRating = filters.minimumRating,
                    minimumVotes = filters.minimumRating?.let { 50 },
                    providerIds = providerQuery,
                    watchRegion = watchRegion,
                    maximumRuntime = filters.maximumRuntime,
                    originalLanguage = filters.originalLanguage,
                    dateFrom = decadeFrom,
                    dateTo = decadeTo,
                    page = page,
                ).results
            }
        }.filter { dto ->
            val regionAllowed = allowedRegions.isEmpty() || dto.originCountries.isEmpty() || dto.originCountries.any(allowedRegions::contains)
            val isAnime = dto.originalLanguage.equals("ja", ignoreCase = true) && 16 in dto.genreIds
            regionAllowed && when (filters.animeMode) {
                "only" -> isAnime
                "exclude" -> !isAnime
                else -> true
            }
        }.distinctBy(TmdbMediaDto::id)
            .map { it.toEntity(filters.mediaType) }
        database.mediaDao().upsertMedia(entities)
        entities.map { entity -> entity.toDomain(states["${entity.mediaType}:${entity.tmdbId}"]) }
            .filterNot { it.stableKey in hiddenDiscovery }
            .filterNot { filters.hideWatched && (it.watched || it.status == LibraryStatus.COMPLETED) }
            .filterNot { filters.hideDropped && it.status == LibraryStatus.DROPPED }
    }

    suspend fun loadStreamingProviders(mediaType: MediaType, selectedRegion: String? = null, failOnError: Boolean = false): List<StreamingProvider> {
        if (tmdbApiKey().isBlank()) return emptyList()
        val region = selectedRegion ?: effectiveProviderRegion()
        val result = cancellableResult {
            val providers = if (mediaType == MediaType.TV) {
                services.tmdb.tvProviders(region).results
            } else {
                services.tmdb.movieProviders(region).results
            }
            providers.distinctBy { it.id }
                .sortedBy { it.name.lowercase(Locale.getDefault()) }
                .map { provider ->
                    StreamingProvider(
                        id = provider.id,
                        name = provider.name,
                        logoUrl = provider.logoPath?.let { "https://image.tmdb.org/t/p/w92$it" },
                    )
                }
        }
        return if (failOnError) result.getOrThrow() else result.getOrDefault(emptyList())
    }

    suspend fun loadSettingsStreamingProviders(region: String): List<StreamingProvider> = coroutineScope {
        val movies = async { loadStreamingProviders(MediaType.MOVIE, region, failOnError = true) }
        val shows = async { loadStreamingProviders(MediaType.TV, region, failOnError = true) }
        (movies.await() + shows.await())
            .distinctBy(StreamingProvider::id)
            .sortedBy { it.name.lowercase(Locale.getDefault()) }
    }

    fun simklConnectedNow(): Boolean = !currentToken().isNullOrBlank()

    fun observeLocalChanges() = database.invalidationTracker.createFlow(
        "media",
        "media_rails",
        "user_media_state",
        "playback",
        "watch_history",
        "up_next",
        "sync_state",
        emitInitialState = false,
    )

    suspend fun isSimklSyncDue(maxAgeMillis: Long): Boolean {
        val lastCheck = preferences.simklLastCheckAt()
            ?: database.syncDao().get("all")?.lastSuccessfulSync
            ?: return true
        return System.currentTimeMillis() - lastCheck >= maxAgeMillis
    }

    suspend fun loadCachedState(): AppUiState = coroutineScope {
        // The database portion is read in one Room transaction. Preferences can
        // still be collected concurrently because they are independent DataStore
        // values and cannot produce a structurally inconsistent media snapshot.
        val snapshotDeferred = async { database.snapshotDao().snapshot() }
        val backgroundSyncDeferred = async { preferences.backgroundSync.first() }
        val wifiOnlyDeferred = async { preferences.wifiOnly.first() }
        val ratingSourcesDeferred = async { preferences.ratingSources.first() }
        val contentRegionsDeferred = async { preferences.contentRegions.first() }
        val uiAccentDeferred = async { preferences.uiAccent.first() }
        val metadataLanguageDeferred = async { preferences.metadataLanguage.first() }
        val metadataRegionDeferred = async { preferences.metadataRegion.first() }
        val metadataTimezoneDeferred = async { preferences.metadataTimezone.first() }
        val syncReportDeferred = async { preferences.syncReportNow() }
        val excludeSpecialsDeferred = async { preferences.excludeSpecials.first() }
        val providerRegionDeferred = async { preferences.providerRegion.first() }
        val preferredProvidersDeferred = async { preferences.preferredProviders.first() }
        val visibleProviderTypesDeferred = async { preferences.visibleProviderTypes.first() }
        val notificationEpisodesDeferred = async { preferences.notificationEpisodes.first() }
        val notificationMoviesDeferred = async { preferences.notificationMovies.first() }
        val notificationSyncDeferred = async { preferences.notificationSync.first() }
        val quietHoursEnabledDeferred = async { preferences.quietHoursEnabled.first() }
        val quietHoursStartDeferred = async { preferences.quietHoursStart.first() }
        val quietHoursEndDeferred = async { preferences.quietHoursEnd.first() }
        val heroLayoutDeferred = async { preferences.heroLayout.first() }
        val posterFormatDeferred = async { preferences.posterFormat.first() }
        val posterSizeDeferred = async { preferences.posterSize.first() }
        val cardDensityDeferred = async { preferences.cardDensity.first() }
        val hiddenUpcomingDeferred = async { preferences.hiddenUpcoming.first() }
        val hiddenDiscoveryDeferred = async { preferences.hiddenDiscovery.first() }
        val introductionCompletedDeferred = async { preferences.introductionCompleted.first() }

        val snapshot = snapshotDeferred.await()
        val states = snapshot.states.associateBy { "${it.mediaType}:${it.mediaId}" }
        val mediaByKey = snapshot.media.associateBy { "${it.mediaType}:${it.tmdbId}" }
        val railEntries = snapshot.rails.groupBy { it.railId }
        val cachedEpisodes = snapshot.episodes
        val episodeByNumber = cachedEpisodes.associateBy { Triple(it.showId, it.season, it.number) }
        val railIds = listOf(
            RailIds.TRENDING_TV,
            RailIds.TRENDING_MOVIES,
            RailIds.POPULAR_TV,
            RailIds.POPULAR_MOVIES,
            RailIds.UPCOMING,
            RailIds.RECOMMENDED,
            RailIds.LIBRARY,
        )
        val rails = railIds.associateWith { rail ->
            railEntries[rail].orEmpty().mapNotNull { entry ->
                mediaByKey["${entry.mediaType}:${entry.mediaId}"]?.let { entity ->
                    entity.toDomain(states["${entity.mediaType}:${entity.tmdbId}"])
                }
            }
        }
        val activeLibrary = rails[RailIds.LIBRARY].orEmpty().filter {
            it.status != LibraryStatus.NONE && it.status != LibraryStatus.DROPPED
        }
        val playback = snapshot.playback.mapNotNull { item ->
            val media = mediaByKey["${item.mediaType}:${item.mediaId}"]
                ?.toDomain(states["${item.mediaType}:${item.mediaId}"]) ?: return@mapNotNull null
            val cachedEpisode = if (item.mediaType == MediaType.TV.name && item.season != null && item.episodeNumber != null) {
                episodeByNumber[Triple(item.mediaId, item.season, item.episodeNumber)]
            } else null
            PlaybackCard(
                media = media,
                episodeId = item.episodeId.takeIf { it != 0 } ?: cachedEpisode?.tmdbId,
                episodeLabel = if ((item.season ?: cachedEpisode?.season) != null && (item.episodeNumber ?: cachedEpisode?.number) != null) {
                    "S${(item.season ?: cachedEpisode?.season).toString().padStart(2, '0')} E${(item.episodeNumber ?: cachedEpisode?.number).toString().padStart(2, '0')}"
                } else null,
                episodeTitle = item.episodeTitle?.takeIf(String::isNotBlank) ?: cachedEpisode?.title,
                season = item.season ?: cachedEpisode?.season,
                episodeNumber = item.episodeNumber ?: cachedEpisode?.number,
                progress = item.progress.coerceIn(0f, 1f),
                remainingMinutes = if (item.durationSeconds > item.positionSeconds) {
                    ((item.durationSeconds - item.positionSeconds) / 60).toInt()
                } else media.runtimeMinutes?.let { runtime -> (runtime * (1f - item.progress.coerceIn(0f, 1f))).toInt().coerceAtLeast(0) },
                durationMinutes = item.durationSeconds.takeIf { it > 0 }?.let { (it / 60).toInt().coerceAtLeast(1) }
                    ?: media.runtimeMinutes,
                episodeAirDate = cachedEpisode?.airDate,
            )
        }
        val history = snapshot.history
            // Simkl commonly assigns the same watched_at value to every episode
            // in a bulk import.  episodeId is also null for the all-items payload,
            // so the old key collapsed an entire batch into a single row.  Season
            // and episode number are the stable Simkl episode identity.
            .distinctBy {
                "${it.mediaType}:${it.mediaId}:${it.season ?: -1}:${it.episodeNumber ?: -1}:${it.episodeId ?: -1}:${it.watchedAt}"
            }
            .mapNotNull { item ->
            val media = mediaByKey["${item.mediaType}:${item.mediaId}"]
                ?.toDomain(states["${item.mediaType}:${item.mediaId}"]) ?: return@mapNotNull null
            val cachedTitle = if (item.mediaType == MediaType.TV.name && item.season != null && item.episodeNumber != null) {
                episodeByNumber[Triple(item.mediaId, item.season, item.episodeNumber)]?.title
            } else null
            val episodeLabel = if (item.mediaType == MediaType.TV.name && item.season != null && item.episodeNumber != null) {
                "S${item.season.toString().padStart(2, '0')} E${item.episodeNumber.toString().padStart(2, '0')}" +
                    (item.episodeTitle?.takeIf(String::isNotBlank) ?: cachedTitle?.takeIf(String::isNotBlank))?.let { " · $it" }.orEmpty()
            } else null
            TimelineCard(
                media = media,
                label = item.watchedAt.take(10),
                timestamp = item.watchedAt,
                episodeLabel = episodeLabel,
                episodeId = item.episodeId,
                season = item.season,
                episodeNumber = item.episodeNumber,
            )
        }
        val configuredTimezone = metadataTimezoneDeferred.await()
        val releaseZone = if (configuredTimezone == "system") ZoneId.systemDefault()
        else runCatching { ZoneId.of(configuredTimezone) }.getOrDefault(ZoneId.systemDefault())
        val releaseNow = Instant.now()
        val today = LocalDate.now(releaseZone)
        // Calendar is strictly a tracked-content surface. Build it from the
        // Simkl-backed Library rail so TMDB content-region preferences can never
        // hide an imported or locally tracked release.
        val movieCalendar = activeLibrary.asSequence().filter { it.type == MediaType.MOVIE }.mapNotNull { media ->
            val date = media.releaseDate?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            val releaseDay = runCatching { LocalDate.parse(date.take(10)) }.getOrNull() ?: return@mapNotNull null
            // The Calendar is an upcoming schedule, not a release history. Date-only
            // TMDB entries are kept only after today so already-aired/released titles
            // never reappear here.
            if (!releaseDay.isAfter(today)) return@mapNotNull null
            TimelineCard(media = media, label = date, timestamp = date)
        }.toList()
        val activeShowsById = activeLibrary.asSequence()
            .filter { it.type == MediaType.TV }
            .associateBy(MediaCard::id)
        // The episode table is the durable schedule snapshot. Rebuild Calendar
        // from it at process start, just as Showly reads its local episode cache,
        // instead of waiting for a new network enrichment pass.
        val excludeSpecials = excludeSpecialsDeferred.await()
        val hiddenUpcoming = hiddenUpcomingDeferred.await()
        val episodeCalendar = cachedEpisodes.mapNotNull { episode ->
            val show = activeShowsById[episode.showId] ?: return@mapNotNull null
            if (excludeSpecials && episode.season == 0) return@mapNotNull null
            if ("${episode.showId}:${episode.season}:${episode.number}" in hiddenUpcoming) return@mapNotNull null
            val date = episode.airDate?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            val airDay = runCatching { LocalDate.parse(date.take(10)) }.getOrNull() ?: return@mapNotNull null
            if (airDay.isBefore(today)) return@mapNotNull null
            TimelineCard(
                media = show,
                label = date,
                timestamp = date,
                episodeLabel = "S${episode.season.toString().padStart(2, '0')} E${episode.number.toString().padStart(2, '0')} · ${episode.title}",
                episodeId = episode.tmdbId,
                season = episode.season,
                episodeNumber = episode.number,
            )
        }
        val calendar = (movieCalendar + episodeCalendar)
            .distinctBy { "${it.media.stableKey}:${it.season ?: -1}:${it.episodeNumber ?: -1}" }
            .sortedWith(
                compareBy<TimelineCard> {
                    releaseDateTime(it.timestamp, releaseZone)?.toInstant() ?: Instant.MAX
                }
                    .thenBy { it.media.title.lowercase() }
                    .thenBy { it.season ?: -1 }
                    .thenBy { it.episodeNumber ?: -1 },
            )
        val watchedNumbers = history.asSequence().mapNotNull { event ->
            if (event.media.type == MediaType.TV && event.season != null && event.episodeNumber != null) {
                Triple(event.media.id, event.season, event.episodeNumber)
            } else null
        }.toSet()
        val latestWatchedAtByShow = history.asSequence()
            .filter { it.media.type == MediaType.TV }
            .groupBy { it.media.id }
            .mapValues { (_, events) -> events.maxOfOrNull { scheduleTime(it.timestamp) } ?: 0L }
        val playbackByShow = playback.filter { it.media.type == MediaType.TV }.associateBy { it.media.stableKey }
        val durableUpNext = snapshot.upNext.associateBy(UpNextEntity::showId)
        val durableUpNextReady = snapshot.syncStates.any { it.area == "up_next_cache_v1" }
        val cachedUpNext = rails[RailIds.LIBRARY].orEmpty()
            .filter { it.type == MediaType.TV && it.status in setOf(LibraryStatus.WATCHING, LibraryStatus.COMPLETED) }
            .mapNotNull { show ->
                val session = playbackByShow[show.stableKey]?.takeUnless {
                    Triple(show.id, it.season, it.episodeNumber) in watchedNumbers
                }
                val cachedRow = durableUpNext[show.id]
                val stored = cachedRow?.takeUnless {
                    Triple(show.id, it.season, it.episodeNumber) in watchedNumbers
                }
                val next = stored?.let { row ->
                    EpisodeCard(
                        id = row.episodeId ?: 0,
                        showId = row.showId,
                        season = row.season,
                        number = row.episodeNumber,
                        title = row.episodeTitle,
                        overview = "",
                        airDate = row.episodeAirDate,
                        runtimeMinutes = row.durationMinutes,
                    )
                } ?: if (!durableUpNextReady || (cachedRow != null && stored == null)) {
                    // One-time compatibility path while migration/startup builds
                    // the durable cache. Once marked ready an empty row correctly
                    // means that the show has no aired unwatched episode.
                    val candidates = cachedEpisodes.asSequence()
                        .filter { it.showId == show.id && (!excludeSpecials || it.season > 0) }
                        .filter { episode ->
                            releaseDateTime(episode.airDate, releaseZone)?.toInstant()?.let { !it.isAfter(releaseNow) } == true
                        }
                        .filterNot { Triple(show.id, it.season, it.number) in watchedNumbers }
                        .sortedWith(compareBy(EpisodeEntity::season, EpisodeEntity::number))
                        .toList()
                    val lastWatched = watchedNumbers.asSequence().filter { it.first == show.id && it.second > 0 }
                        .maxWithOrNull(compareBy<Triple<Int, Int, Int>>({ it.second }, { it.third }))
                    val legacyNext = lastWatched?.let { last ->
                        candidates.firstOrNull { it.season > last.second || (it.season == last.second && it.number > last.third) }
                    } ?: candidates.firstOrNull()
                    legacyNext?.toDomain()
                } else null
                if (next == null) return@mapNotNull session
                val sameEpisode = session?.season == next.season && session.episodeNumber == next.number
                PlaybackCard(
                    media = show,
                    episodeId = next.id.takeIf { it > 0 },
                    episodeLabel = "S${next.season.toString().padStart(2, '0')} E${next.number.toString().padStart(2, '0')}",
                    episodeTitle = next.title,
                    season = next.season,
                    episodeNumber = next.number,
                    progress = if (sameEpisode) session?.progress ?: 0f else 0f,
                    remainingMinutes = if (sameEpisode && (session?.progress ?: 0f) > 0f) session?.remainingMinutes else null,
                    durationMinutes = next.runtimeMinutes ?: session?.durationMinutes ?: show.runtimeMinutes,
                    episodeAirDate = next.airDate,
                )
            }
            .sortedWith(
                compareByDescending<PlaybackCard> { item ->
                    maxOf(latestWatchedAtByShow[item.media.id] ?: 0L, playbackRecency(item))
                }
                    .thenBy { it.media.title.lowercase() },
            )
        val moviePlayback = playback.filter { it.media.type == MediaType.MOVIE }.toMutableList()
        val movieKeys = moviePlayback.map { it.media.stableKey }.toSet()
        moviePlayback += rails[RailIds.LIBRARY].orEmpty()
            .filter { it.type == MediaType.MOVIE && it.status == LibraryStatus.WATCHING && it.stableKey !in movieKeys }
            .map { media -> PlaybackCard(media = media, progress = 0f, remainingMinutes = media.runtimeMinutes, durationMinutes = media.runtimeMinutes) }

        AppUiState(
            rails = rails,
            playbackTv = cachedUpNext,
            playbackMovies = moviePlayback,
            history = history,
            calendar = calendar,
            episodes = cachedEpisodes.map { episode ->
                episode.toDomain().copy(
                    watched = Triple(episode.showId, episode.season, episode.number) in watchedNumbers,
                )
            },
            sync = SyncProgress(
                lastSuccessfulSync = snapshot.syncStates.firstOrNull { it.area == "all" }?.lastSuccessfulSync,
                report = syncReportDeferred.await(),
            ),
            simklConnected = simklConnectedNow(),
            backgroundSync = backgroundSyncDeferred.await(),
            wifiOnly = wifiOnlyDeferred.await(),
            notificationEpisodes = notificationEpisodesDeferred.await(),
            notificationMovies = notificationMoviesDeferred.await(),
            notificationSync = notificationSyncDeferred.await(),
            quietHoursEnabled = quietHoursEnabledDeferred.await(),
            quietHoursStart = quietHoursStartDeferred.await(),
            quietHoursEnd = quietHoursEndDeferred.await(),
            ratingSources = ratingSourcesDeferred.await(),
            contentRegions = contentRegionsDeferred.await(),
            uiAccent = uiAccentDeferred.await(),
            tmdbApiConfigured = tmdbApiKey().isNotBlank(),
            mdbListApiConfigured = mdbListApiKey().isNotBlank(),
            metadataLanguage = metadataLanguageDeferred.await(),
            metadataRegion = metadataRegionDeferred.await(),
            providerRegion = providerRegionDeferred.await(),
            metadataTimezone = metadataTimezoneDeferred.await(),
            excludeSpecials = excludeSpecials,
            preferredProviders = preferredProvidersDeferred.await(),
            visibleProviderTypes = visibleProviderTypesDeferred.await(),
            heroLayout = heroLayoutDeferred.await(),
            posterFormat = posterFormatDeferred.await(),
            posterSize = posterSizeDeferred.await(),
            cardDensity = cardDensityDeferred.await(),
            hiddenUpcoming = hiddenUpcoming,
            hiddenDiscovery = hiddenDiscoveryDeferred.await(),
            introductionCompleted = introductionCompletedDeferred.await(),
            loading = false,
        )
    }

    suspend fun search(query: String): List<MediaCard> {
        if (query.isBlank()) return emptyList()
        val allowedRegions = preferences.contentRegions.first().map { it.uppercase() }.toSet()
        val hiddenDiscovery = preferences.hiddenDiscovery.first()
        val states = database.stateDao().observeAll().first().associateBy { "${it.mediaType}:${it.mediaId}" }
        val remote = if (tmdbApiKey().isNotBlank()) runCatching {
            val candidates = services.tmdb.search(query).results
                .filter { it.mediaType == "movie" || it.mediaType == "tv" }
            val regionFiltered = if (allowedRegions.isEmpty()) candidates else coroutineScope {
                val requests = Semaphore(6)
                candidates.map { candidate ->
                    async {
                        requests.withPermit {
                            val embeddedCountries = candidate.originCountries +
                                candidate.productionCountries.map { it.code }
                            val countries = embeddedCountries.ifEmpty {
                                runCatching {
                                    val details = if (candidate.mediaType == "tv") {
                                        services.tmdb.show(candidate.id, append = "")
                                    } else {
                                        services.tmdb.movie(candidate.id, append = "")
                                    }
                                    details.originCountries + details.productionCountries.map { it.code }
                                }.getOrElse { error ->
                                    if (error is kotlinx.coroutines.CancellationException) throw error
                                    emptyList()
                                }
                            }
                            candidate.takeIf {
                                countries.any { country -> country.uppercase() in allowedRegions }
                            }
                        }
                    }
                }.mapNotNull { it.await() }
            }
            val items = regionFiltered
                .map { it.toEntity(if (it.mediaType == "tv") MediaType.TV else MediaType.MOVIE) }
            database.mediaDao().upsertMedia(items)
            items
        }.getOrElse { error ->
            if (error is kotlinx.coroutines.CancellationException) throw error
            emptyList()
        } else emptyList()
        // Discover search follows the TMDB content-region allowlist. When an
        // allowlist is active, do not merge unclassified local/imported rows back
        // into these results; Library and Progress searches remain unfiltered.
        val local = if (allowedRegions.isEmpty()) database.mediaDao().searchLocal(query).first() else emptyList()
        return (remote + local).distinctBy { "${it.mediaType}:${it.tmdbId}" }
            .filterNot { "${it.mediaType}:${it.tmdbId}" in hiddenDiscovery }
            .map { it.toDomain(states["${it.mediaType}:${it.tmdbId}"]) }
    }

    suspend fun searchPeople(query: String): List<PersonCard> {
        if (query.isBlank() || tmdbApiKey().isBlank()) return emptyList()
        return runCatching {
            services.tmdb.search(query).results.asSequence()
                .filter { it.mediaType == "person" && !it.name.isNullOrBlank() }
                .map { person ->
                    PersonCard(
                        id = person.id,
                        name = person.name.orEmpty(),
                        role = person.knownForDepartment.orEmpty(),
                        profileUrl = person.profilePath?.let { "https://image.tmdb.org/t/p/w500$it" },
                    )
                }
                .distinctBy(PersonCard::id)
                .toList()
        }.getOrElse { error ->
            if (error is kotlinx.coroutines.CancellationException) throw error
            emptyList()
        }
    }

    suspend fun setLibraryStatus(media: MediaCard, status: LibraryStatus) {
        libraryRepository.setLibraryStatus(media, status)
    }

    /**
     * Pushes library mutations immediately. A failed request leaves the exact
     * state dirty, so the normal synchronization pass retries it later.
     */
    suspend fun pushPendingLibraryChanges(): Result<Unit> = syncCoordinator.pushPending()

    /** Sends the item the user just changed before retrying unrelated writes. */
    suspend fun pushLibraryChange(type: MediaType, id: Int): Result<Unit> {
        val ids = buildSet {
            add(stateOperationId(type.name, id))
            database.syncDao().pendingWrites()
                .filter { it.operation == "MEDIA_HISTORY_REMOVE" && it.mediaType == type.name && it.mediaId == id }
                .forEach { add(writeOperationId(it.id)) }
        }
        return syncCoordinator.pushPending(ids)
    }

    private suspend fun pushLibraryState(state: UserMediaStateEntity) {
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
        } else if (state.watched) {
            services.simklSync.addHistory(
                state.syncRequest(
                    SimklSyncItem(
                        ids = ids,
                        watchedAt = Instant.now().toString(),
                        status = targetStatus,
                    ),
                ),
            )
        } else {
            // Simkl requires `to` on every item, not at the request root.
            services.simklSync.addToList(state.syncRequest(SimklSyncItem(ids = ids, to = targetStatus)))
        }
    }

    private fun UserMediaStateEntity.syncRequest(item: SimklSyncItem): SimklSyncRequest =
        if (mediaType == MediaType.MOVIE.name) SimklSyncRequest(movies = listOf(item))
        else SimklSyncRequest(shows = listOf(item))

    private suspend fun pushMediaHistoryRemoval(write: PendingWriteEntity) {
        val simklId = write.payload.toLongOrNull()
        val ids = SimklIds(simkl = simklId, tmdb = write.mediaId.toString())
        val request = if (write.mediaType == MediaType.MOVIE.name) {
            SimklSyncRequest(movies = listOf(SimklSyncItem(ids = ids)))
        } else {
            SimklSyncRequest(shows = listOf(SimklSyncItem(ids = ids)))
        }
        services.simklSync.removeHistory(request)
    }

    private suspend fun pushEpisodeWrite(write: PendingWriteEntity) {
        val parts = write.payload.split(':', limit = 3)
        val season = parts.getOrNull(0)?.toIntOrNull() ?: error("Invalid queued season")
        val episode = parts.getOrNull(1)?.toIntOrNull() ?: error("Invalid queued episode")
        val watchedAt = parts.getOrNull(2) ?: Instant.now().toString()
        val request = SimklSyncRequest(
            shows = listOf(
                SimklSyncItem(
                    ids = SimklIds(tmdb = write.mediaId.toString()),
                    seasons = listOf(
                        com.cinetrack.data.remote.SimklSeason(
                            season,
                            listOf(
                                com.cinetrack.data.remote.SimklEpisode(
                                    episode,
                                    watchedAt.takeIf { write.operation == "EPISODE_WATCHED" },
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
        if (write.operation == "EPISODE_WATCHED") services.simklSync.addHistory(request)
        else services.simklSync.removeHistory(request)
    }

    /** Returns every durable local write, including rows created by older app versions. */
    suspend fun loadSyncOperations(): List<SyncOperationCard> = syncOperationRepository.cards()

    suspend fun retrySyncOperation(operationId: String): Result<Unit> = syncCoordinator.retry(operationId)

    suspend fun resolveSyncConflict(operationId: String, choice: SyncConflictChoice): Result<Unit> {
        val conflict = database.syncDao().syncOperation(operationId)
            ?: return Result.failure(IllegalStateException("Conflict no longer exists"))
        if (conflict.status != SyncOperationStatus.CONFLICT.name) {
            return Result.failure(IllegalStateException("Operation is not a conflict"))
        }
        if (choice == SyncConflictChoice.KEEP_LOCAL) {
            val type = runCatching { MediaType.valueOf(conflict.mediaType) }.getOrNull()
                ?: return Result.failure(IllegalStateException("Conflict media type is invalid"))
            val current = database.stateDao().get(conflict.mediaType, conflict.mediaId)
                ?: return Result.failure(IllegalStateException("Local state is no longer available"))
            val operationType = when (conflict.operation) {
                "LIBRARY_STATUS_CONFLICT", "LIBRARY_CONFLICT" -> com.cinetrack.data.sync.SyncOperationType.LIBRARY_STATUS
                "WATCHED_CONFLICT" -> if (current.watched) com.cinetrack.data.sync.SyncOperationType.MOVIE_WATCHED else com.cinetrack.data.sync.SyncOperationType.MOVIE_UNWATCHED
                "EPISODE_WATCHED_CONFLICT" -> if (current.watched) com.cinetrack.data.sync.SyncOperationType.EPISODE_WATCHED else com.cinetrack.data.sync.SyncOperationType.EPISODE_UNWATCHED
                else -> return Result.failure(IllegalStateException("Unsupported conflict field"))
            }
            val operationIdForResolution = if (operationType == com.cinetrack.data.sync.SyncOperationType.LIBRARY_STATUS) {
                stateOperationId(type.name, current.mediaId)
            } else {
                "resolution:$operationId"
            }
            val operation = com.cinetrack.data.sync.SyncOperation(
                id = operationIdForResolution,
                type = operationType,
                mediaType = type,
                mediaId = current.mediaId,
                title = conflict.title,
                value = if (operationType == com.cinetrack.data.sync.SyncOperationType.LIBRARY_STATUS) current.status else current.watched.toString(),
                sourceVersion = current.updatedAt,
            )
            syncOperationRepository.enqueue(listOf(operation))
            database.syncDao().deleteOperation(operationId)
            return syncCoordinator.pushPending(setOf(operationIdForResolution))
        }
        val media = database.mediaDao().get(conflict.mediaType, conflict.mediaId)
            ?: return Result.failure(IllegalStateException("Media is no longer available"))
        database.withTransaction {
            val previous = database.stateDao().get(conflict.mediaType, conflict.mediaId)
            when {
                conflict.operation == "LIBRARY_STATUS_CONFLICT" || conflict.operation == "LIBRARY_CONFLICT" -> {
                    val remoteStatus = conflict.remoteValue?.let { runCatching { LibraryStatus.valueOf(it) }.getOrNull() }
                        ?: return@withTransaction
                    database.stateDao().upsert(UserMediaStateEntity(conflict.mediaType, conflict.mediaId, remoteStatus.name, previous?.watched ?: false, previous?.simklId, System.currentTimeMillis(), dirty = false))
                    if (remoteStatus == LibraryStatus.NONE) database.timelineDao().deleteMediaHistory(conflict.mediaType, conflict.mediaId)
                }
                conflict.operation == "WATCHED_CONFLICT" -> {
                    val watched = conflict.remoteValue?.toBooleanStrictOrNull() ?: return@withTransaction
                    database.stateDao().upsert(UserMediaStateEntity(conflict.mediaType, conflict.mediaId, previous?.status ?: LibraryStatus.NONE.name, watched, previous?.simklId, System.currentTimeMillis(), dirty = false))
                }
                conflict.operation == "EPISODE_WATCHED_CONFLICT" -> {
                    val watched = conflict.remoteValue?.toBooleanStrictOrNull() ?: return@withTransaction
                    val season = conflict.season ?: return@withTransaction
                    val episode = conflict.episode ?: return@withTransaction
                    if (watched) database.timelineDao().insertHistory(WatchHistoryEntity(mediaType = conflict.mediaType, mediaId = conflict.mediaId, season = season, episodeNumber = episode, watchedAt = Instant.now().toString()))
                    else database.timelineDao().deleteEpisodeHistory(conflict.mediaType, conflict.mediaId, season, episode)
                }
            }
            val staleOperations = database.syncDao().syncOperations()
                .filter { it.operationId != operationId && it.mediaType == conflict.mediaType && it.mediaId == conflict.mediaId && it.status != SyncOperationStatus.CONFLICT.name }
                .map(SyncOperationEntity::operationId)
            if (staleOperations.isNotEmpty()) database.syncDao().deleteOperations(staleOperations)
            rebuildLibraryRailInTransaction()
        }
        database.syncDao().deleteOperation(operationId)
        return Result.success(Unit)
    }

    suspend fun markWatched(media: MediaCard) {
        libraryRepository.markWatched(media)
    }

    suspend fun markEpisodeWatched(episode: EpisodeCard) {
        libraryRepository.markEpisodeWatched(episode)
    }

    suspend fun setEpisodeWatched(episode: EpisodeCard, watched: Boolean) {
        libraryRepository.setEpisodeWatched(episode, watched)
    }

    /**
     * Advances one show's durable next-episode row using Room only. This runs
     * immediately after a watched-state change, so neither Compose nor the
     * database invalidation observer has to wait for Simkl or a full cache pass.
     */
    private suspend fun refreshLocalUpNext(showId: Int) {
        val releaseZone = localZone()
        val releaseNow = Instant.now()
        val excludeSpecials = preferences.excludeSpecials.first()
        val watched = watchedEpisodeNumbers(showId)
        val lastWatched = watched.asSequence()
            .filter { it.first > 0 }
            .maxWithOrNull(compareBy<Pair<Int, Int>>({ it.first }, { it.second }))
        val candidates = database.mediaDao().episodesForShow(showId).asSequence()
            .map { it.toDomain() }
            .filter { !excludeSpecials || it.season > 0 }
            .filter { episode ->
                releaseDateTime(episode.airDate, releaseZone)?.toInstant()?.let { !it.isAfter(releaseNow) } == true
            }
            .filterNot { (it.season to it.number) in watched }
            .sortedWith(compareBy(EpisodeCard::season, EpisodeCard::number))
            .toList()
        val next = lastWatched?.let { last ->
            candidates.firstOrNull {
                it.season > last.first || (it.season == last.first && it.number > last.second)
            }
        } ?: candidates.firstOrNull()
        database.withTransaction {
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
    }

    private val taglineCache = BoundedLruCache<String, String>(64)
    private val seasonDetailsCache = BoundedLruCache<String, SeasonDetails>(32)

    /** Hero enrichment is independent of the catalogue refresh and fetches no appended resources. */
    suspend fun loadTagline(media: MediaCard): String? {
        if (tmdbApiKey().isBlank()) return null
        val language = preferences.metadataLanguage.first()
        val key = "$language:${media.stableKey}"
        taglineCache[key]?.let { return it.takeIf(String::isNotBlank) }
        return kotlinx.coroutines.withTimeoutOrNull(8_000) {
            cancellableResult {
                val dto = if (media.type == MediaType.MOVIE) services.tmdb.movie(media.id, append = "")
                    else services.tmdb.show(media.id, append = "")
                dto.tagline.orEmpty().also { taglineCache[key] = it }.takeIf(String::isNotBlank)
            }.getOrNull()
        }
    }

    suspend fun loadSeasonDetails(show: MediaCard, number: Int): Result<SeasonDetails> = cancellableResult {
        check(tmdbApiKey().isNotBlank()) { "TMDB credential required" }
        val key = "${preferences.metadataLanguage.first()}:${show.id}:$number"
        seasonDetailsCache[key] ?: (kotlinx.coroutines.withTimeoutOrNull(15_000) {
            val dto = services.tmdb.season(show.id, number, append = "aggregate_credits")
            SeasonDetails(
                number = number,
                title = dto.name,
                overview = dto.overview,
                posterUrl = dto.posterPath?.let { "https://image.tmdb.org/t/p/w342$it" },
                airDate = dto.airDate,
                episodeCount = dto.episodes.size,
                runtimeMinutes = dto.episodes.mapNotNull { it.runtime?.takeIf { minutes -> minutes > 0 } }
                    .takeIf { it.isNotEmpty() }?.average()?.toInt(),
                score = dto.voteAverage?.takeIf { it > 0 },
                cast = castCards(dto.aggregateCredits).filter { it.isCastMember },
            ).also { seasonDetailsCache[key] = it }
        } ?: error("Season request timed out"))
    }

    suspend fun loadDetails(media: MediaCard): MediaCard {
        if (tmdbApiKey().isBlank()) return media
        return cancellableResult {
            val localized = if (media.type == MediaType.MOVIE) services.tmdb.movie(media.id) else services.tmdb.show(media.id, append = "aggregate_credits,recommendations,watch/providers,videos")
            val english = if (localized.overview.isBlank() || (localized.title ?: localized.name).isNullOrBlank()) {
                runCatching {
                    if (media.type == MediaType.MOVIE) services.tmdb.movie(media.id, language = "en-US")
                    else services.tmdb.show(media.id, language = "en-US")
                }.getOrNull()
            } else null
            val dto = localized.copy(
                title = localized.title?.takeIf(String::isNotBlank) ?: english?.title,
                name = localized.name?.takeIf(String::isNotBlank) ?: english?.name,
                overview = localized.overview.ifBlank { english?.overview.orEmpty() },
                seasons = localized.seasons.ifEmpty { english?.seasons.orEmpty() },
            )
            recommendationCandidatesCache[media.stableKey] = dto.recommendations?.results.orEmpty()
            castCards(dto.aggregateCredits ?: dto.credits).takeIf { it.isNotEmpty() }?.let {
                castCache["${preferences.metadataLanguage.first()}:${media.stableKey}"] = it
            }
            val providerRegion = effectiveProviderRegion()
            val providerCountry = dto.watchProviders?.results?.get(providerRegion)
            val preferredProviders = preferences.preferredProviders.first()
            val visibleTypes = preferences.visibleProviderTypes.first()
            fun List<com.cinetrack.data.remote.TmdbProviderDto>.visibleProviders() =
                distinctBy { it.id }
                    .let { providers ->
                        if (preferredProviders.isEmpty()) providers
                        else providers.filter { it.name in preferredProviders }
                    }
                    .sortedWith(
                    compareByDescending<com.cinetrack.data.remote.TmdbProviderDto> { it.name in preferredProviders }
                        .thenBy { it.name.lowercase() },
                )
            val providers = providerCountry
                ?.let { it.flatrate + it.rent + it.buy + it.free + it.ads }
                .orEmpty()
                .visibleProviders()
            dto.toEntity(media.type).toDomain().copy(
                status = media.status,
                watched = media.watched,
                libraryUpdatedAt = media.libraryUpdatedAt,
                tmdbStatus = dto.status,
                networks = dto.networks.map { it.name }.filter(String::isNotBlank),
                budget = dto.budget?.takeIf { it > 0L },
                boxOffice = dto.revenue?.takeIf { it > 0L },
                productionCompanies = dto.productionCompanies.map { it.name }.filter(String::isNotBlank),
                productionCountries = dto.productionCountries.map { country -> country.name.ifBlank { country.code } },
                originalLanguage = dto.originalLanguage?.takeIf(String::isNotBlank)?.uppercase(),
                providers = providers.map { it.name },
                providerLogos = providers.mapNotNull { provider ->
                    provider.logoPath?.let { provider.name to "https://image.tmdb.org/t/p/w92$it" }
                }.toMap(),
                subscriptionProviders = providerCountry?.flatrate.orEmpty().visibleProviders().map { it.name },
                rentProviders = providerCountry?.rent.orEmpty().visibleProviders().map { it.name },
                buyProviders = providerCountry?.buy.orEmpty().visibleProviders().map { it.name },
                freeProviders = providerCountry?.free.orEmpty().visibleProviders().map { it.name },
                adsProviders = providerCountry?.ads.orEmpty().visibleProviders().map { it.name },
                providerLink = providerCountry?.link,
                visibleProviderTypes = visibleTypes,
                providerAvailabilityExists = providerCountry?.let { (it.flatrate + it.rent + it.buy + it.free + it.ads).isNotEmpty() } == true,
                seasons = dto.seasons.filter { it.number > 0 }.map { season ->
                    SeasonCard(
                        number = season.number,
                        title = season.name,
                        episodeCount = season.episodeCount,
                        posterUrl = season.posterPath?.let { "https://image.tmdb.org/t/p/w342$it" },
                    )
                },
            )
        }.getOrDefault(media)
    }

    suspend fun loadMedia(type: MediaType, id: Int): MediaCard? {
        val state = database.stateDao().stateSnapshot().firstOrNull {
            it.mediaType == type.name && it.mediaId == id
        }
        database.mediaDao().get(type.name, id)?.let { return it.toDomain(state) }
        if (tmdbApiKey().isBlank()) return null
        return cancellableResult {
            val dto = if (type == MediaType.MOVIE) services.tmdb.movie(id) else services.tmdb.show(id)
            val entity = dto.toEntity(type)
            database.mediaDao().upsertMedia(listOf(entity))
            entity.toDomain(state)
        }.getOrNull()
    }

    suspend fun loadAllEpisodes(show: MediaCard): List<EpisodeCard> {
        val detailedShow = if (show.seasons.isEmpty()) loadDetails(show) else show
        val seasons = detailedShow.seasons.map(SeasonCard::number).ifEmpty { listOf(1) }
        val cached = loadCachedEpisodes(show.id)
        val expectedCount = detailedShow.seasons.sumOf(SeasonCard::episodeCount)
        val tmdbBackedCount = cached.count { it.id > 0 }
        if (tmdbBackedCount == 0 || expectedCount <= 0 || tmdbBackedCount < expectedCount) {
            // TMDB remains the independent source for the complete seasons and
            // episodes catalogue. Simkl is applied afterwards only as a date
            // overlay, so disconnecting a Simkl account never hides this section.
            val requests = Semaphore(4)
            coroutineScope {
                seasons.distinct().sorted().map { season ->
                    async { requests.withPermit { loadEpisodes(detailedShow, season) } }
                }.awaitAll()
            }
        }
        refreshSimklSchedule(listOf(detailedShow))
        return loadCachedEpisodes(show.id)
            .filter { it.id > 0 }
            .sortedWith(compareBy(EpisodeCard::season, EpisodeCard::number))
    }

    suspend fun loadUpNextEpisodes(
        shows: List<MediaCard>,
        watched: Set<Triple<Int, Int, Int>>,
        cachedEpisodes: List<EpisodeCard>? = null,
        onItemProcessed: ((processed: Int, total: Int) -> Unit)? = null,
    ): Map<String, EpisodeCard> = progressCacheRepository.loadUpNextEpisodes(
        shows = shows,
        watched = watched,
        cachedEpisodes = cachedEpisodes,
        onItemProcessed = onItemProcessed,
    )

    suspend fun enrichHistoryLabels(items: List<TimelineCard>): List<TimelineCard> {
        // Keep this call's results until its output is built. The process-wide
        // LRU may evict early titles when an import contains more than 750 rows.
        val resolvedTitles = mutableMapOf<String, String>()
        val missing = items.filter {
            it.media.type == MediaType.TV && it.season != null && it.episodeNumber != null &&
                (it.episodeLabel.isNullOrBlank() || !it.episodeLabel.contains(" · "))
        }.distinctBy { "${it.media.id}:${it.season}:${it.episodeNumber}" }
        for (batch in missing.chunked(6)) {
            coroutineScope {
                batch.map { item ->
                    async {
                        val key = "${item.media.id}:${item.season}:${item.episodeNumber}"
                        if (episodeTitleCache[key].isNullOrBlank()) {
                            database.mediaDao().episode(item.media.id, item.season!!, item.episodeNumber!!)
                                ?.title?.takeIf(String::isNotBlank)?.let { episodeTitleCache[key] = it }
                        }
                        if (episodeTitleCache[key].isNullOrBlank() && tmdbApiKey().isNotBlank()) {
                            runCatching { services.tmdb.episode(item.media.id, item.season!!, item.episodeNumber!!).name }
                                .getOrNull()?.takeIf(String::isNotBlank)?.let { title ->
                                    episodeTitleCache[key] = title
                                    database.timelineDao().updateEpisodeTitle(MediaType.TV.name, item.media.id, item.season!!, item.episodeNumber!!, title)
                                }
                        }
                        key to episodeTitleCache[key]
                    }
                }.forEach { pending ->
                    val (key, title) = pending.await()
                    if (!title.isNullOrBlank()) resolvedTitles[key] = title
                }
            }
        }
        return items.map { item ->
            if (item.media.type != MediaType.TV || item.season == null || item.episodeNumber == null) item
            else {
                val existingTitle = item.episodeLabel?.substringAfter(" · ", "")?.takeIf(String::isNotBlank)
                val key = "${item.media.id}:${item.season}:${item.episodeNumber}"
                val title = resolvedTitles[key] ?: episodeTitleCache[key] ?: existingTitle
                val number = "S${item.season.toString().padStart(2, '0')} E${item.episodeNumber.toString().padStart(2, '0')}"
                item.copy(episodeLabel = listOfNotNull(number, title?.takeIf(String::isNotBlank)).joinToString(" · "))
            }
        }
    }

    suspend fun loadPerson(person: PersonCard): PersonCard {
        if (tmdbApiKey().isBlank()) return person
        return cancellableResult {
            val localized = services.tmdb.person(person.id)
            val english = if (localized.biography.isBlank()) {
                runCatching { services.tmdb.person(person.id, language = "en-US") }.getOrNull()
            } else null
            val details = localized.copy(
                name = localized.name.ifBlank { english?.name.orEmpty() },
                biography = localized.biography.ifBlank { english?.biography.orEmpty() },
                placeOfBirth = localized.placeOfBirth?.takeIf(String::isNotBlank) ?: english?.placeOfBirth,
                profilePath = localized.profilePath ?: english?.profilePath,
            )
            val credits = services.tmdb.combinedCredits(person.id).cast
                .mapNotNull { credit ->
                    val type = if (credit.mediaType == "tv") MediaType.TV else MediaType.MOVIE
                    val title = if (type == MediaType.TV) credit.name else credit.title
                    val date = if (type == MediaType.TV) credit.firstAirDate else credit.releaseDate
                    if (title.isNullOrBlank()) return@mapNotNull null
                    MediaCard(
                        id = credit.id,
                        type = type,
                        title = title,
                        releaseDate = date,
                        posterUrl = credit.posterPath?.let { path -> "https://image.tmdb.org/t/p/w500$path" },
                        score = credit.voteAverage,
                    )
                }
                .distinctBy(MediaCard::stableKey)
                .sortedByDescending { credit ->
                    credit.releaseDate?.let { raw -> runCatching { LocalDate.parse(raw) }.getOrNull() }
                }
            PersonCard(
                id = details.id,
                name = details.name,
                role = person.role,
                profileUrl = details.profilePath?.let { "https://image.tmdb.org/t/p/w500$it" },
                biography = details.biography,
                birthday = details.birthday,
                placeOfBirth = details.placeOfBirth,
                movieCredits = credits,
            )
        }.getOrDefault(person)
    }

    private fun castCards(credits: com.cinetrack.data.remote.TmdbCreditsDto?): List<PersonCard> {
        val castIds = credits?.cast.orEmpty().map { it.id }.toSet()
        val directingJobs = setOf("Director", "Creator", "Executive Producer")
        return (credits?.cast.orEmpty() + credits?.crew.orEmpty()).groupBy { it.id }.map { (_, roles) ->
            val person = roles.first()
            PersonCard(
                id = person.id,
                isCastMember = person.id in castIds,
                isDirector = roles.any { credit ->
                    credit.job in directingJobs || credit.jobs.any { it.job in directingJobs }
                },
                name = person.name,
                role = roles.flatMap { credit ->
                    listOfNotNull(credit.character, credit.job) +
                        (credit.roles + credit.jobs).mapNotNull { it.character ?: it.job }
                }.filter { it.isNotBlank() }.distinct().joinToString(" · "),
                profileUrl = person.profilePath?.let { "https://image.tmdb.org/t/p/w500$it" },
            )
        }
    }

    suspend fun loadCast(media: MediaCard): List<PersonCard> {
        if (tmdbApiKey().isBlank()) return emptyList()
        val key = "${preferences.metadataLanguage.first()}:${media.stableKey}"
        castCache[key]?.let { return it }
        return cancellableResult {
            val dto = if (media.type == MediaType.MOVIE) services.tmdb.movie(media.id) else services.tmdb.show(media.id, append = "aggregate_credits")
            castCards(dto.aggregateCredits ?: dto.credits).also { if (it.isNotEmpty()) castCache[key] = it }
        }.getOrDefault(emptyList())
    }

    suspend fun loadEpisodes(show: MediaCard, season: Int = 1): List<EpisodeCard> {
        val watchedNumbers = watchedEpisodeNumbers(show.id)
        val cachedEntities = database.mediaDao().episodesForShow(show.id)
            .filter { it.season == season }
        val cachedByNumber = cachedEntities.associateBy(EpisodeEntity::number)
        val cached = cachedEntities
            .map { entity ->
                entity.toDomain().copy(watched = (entity.season to entity.number) in watchedNumbers)
            }
        if (tmdbApiKey().isBlank()) return cached
        return cancellableResult {
            val localized = services.tmdb.season(show.id, season).episodes
            val englishByNumber = if (localized.any { it.name.isBlank() || it.overview.isBlank() }) {
                runCatching { services.tmdb.season(show.id, season, language = "en-US").episodes }
                    .getOrDefault(emptyList())
                    .associateBy { it.number }
            } else emptyMap()
            localized.map { source ->
                val english = englishByNumber[source.number]
                val episode = source.copy(
                    name = source.name.ifBlank { english?.name.orEmpty() },
                    overview = source.overview.ifBlank { english?.overview.orEmpty() },
                )
                EpisodeCard(
                    id = episode.id,
                    showId = show.id,
                    season = episode.season,
                    number = episode.number,
                    title = episode.name,
                    overview = episode.overview,
                    // A Simkl schedule row may already have corrected this date.
                    // Preserve it while TMDB continues to provide every other field.
                    airDate = cachedByNumber[episode.number]?.airDate ?: episode.airDate,
                    stillUrl = episode.stillPath?.let { path -> "https://image.tmdb.org/t/p/w780$path" },
                    runtimeMinutes = episode.runtime,
                    watched = (episode.season to episode.number) in watchedNumbers,
                )
            }.also { episodes ->
                if (episodes.isNotEmpty()) database.mediaDao().upsertEpisodes(episodes.map { it.toEntity() })
            }
        }.getOrDefault(cached)
    }

    suspend fun loadCachedEpisodes(showId: Int): List<EpisodeCard> {
        val watchedNumbers = watchedEpisodeNumbers(showId)
        return database.mediaDao().episodesForShow(showId).map { entity ->
            entity.toDomain().copy(watched = (entity.season to entity.number) in watchedNumbers)
        }
    }

    private suspend fun watchedEpisodeNumbers(showId: Int): Set<Pair<Int, Int>> =
        database.timelineDao().episodeHistoryForShow(MediaType.TV.name, showId)
            .mapNotNull { history ->
                val season = history.season ?: return@mapNotNull null
                val episode = history.episodeNumber ?: return@mapNotNull null
                season to episode
            }
            .toSet()

    /**
     * Refreshes only the durable inputs used by Progress. Remote responses are
     * written to Room first; callers then publish a fresh database snapshot.
     * Nothing returned by the network is exposed directly to Compose.
     */
    suspend fun refreshProgressCache(
        request: ProgressRefreshRequest = ProgressRefreshRequest(force = true),
        onProgress: ((Float) -> Unit)? = null,
    ): Boolean = progressCacheMutex.withLock {
        onProgress?.invoke(.82f)
        val snapshot = database.progressSnapshotDao().snapshot()
        val states = snapshot.states.associateBy { it.mediaId }
        val libraryShows = snapshot.media.map { media -> media.toDomain(states[media.tmdbId]) }
            .distinctBy(MediaCard::stableKey)
        val progressShows = libraryShows.filter {
            it.status in setOf(LibraryStatus.WATCHING, LibraryStatus.COMPLETED)
        }
        val watched = snapshot.history.mapNotNull { item ->
            val season = item.season ?: return@mapNotNull null
            val episode = item.episodeNumber ?: return@mapNotNull null
            Triple(item.mediaId, season, episode)
        }.toSet()
        val now = System.currentTimeMillis()
        val scheduleFreshness = 5L * 60L * 60L * 1_000L
        val scheduleState = database.syncDao().get("simkl_calendar")
        val scheduleDue = libraryShows.isNotEmpty() &&
            (scheduleState == null || now - scheduleState.lastSuccessfulSync >= scheduleFreshness)
        val cacheReady = database.syncDao().get("up_next_cache_v1") != null
        val needsUpNext = request.requiresUpNext(scheduleDue, cacheMissing = !cacheReady)
        if (!needsUpNext) {
            onProgress?.invoke(.99f)
            scheduleProgressEnrichment()
            return@withLock false
        }

        val needsSchedule = request.force || request.tvLibraryChanged || scheduleDue
        if (needsSchedule) {
            // Upcoming metadata populates the episode cache first; up-next then
            // reuses it instead of issuing duplicate recent-season requests.
            loadUpcomingEpisodes(
                libraryShows,
                forceSchedule = request.force || request.tvLibraryChanged,
            )
        }
        onProgress?.invoke(.88f)
        val refreshedEpisodes = database.progressSnapshotDao().activeTvEpisodes().map { it.toDomain() }
        val upNext = loadUpNextEpisodes(
            shows = progressShows,
            watched = watched,
            cachedEpisodes = refreshedEpisodes,
        ) { processed, total ->
            val fraction = if (total == 0) 1f else processed.toFloat() / total.toFloat()
            onProgress?.invoke(.88f + (.09f * fraction))
        }

        val refreshedAt = System.currentTimeMillis()
        database.withTransaction {
            // Network work used an earlier history snapshot. Rebase only the
            // shows changed during that work, without delaying a local tap.
            val latest = database.progressSnapshotDao().snapshot()
            val latestWatched = latest.history.mapNotNull { row ->
                val season = row.season ?: return@mapNotNull null
                val number = row.episodeNumber ?: return@mapNotNull null
                Triple(row.mediaId, season, number)
            }.toSet()
            val eligible = latest.states.filter {
                it.status in setOf(LibraryStatus.WATCHING.name, LibraryStatus.COMPLETED.name)
            }.map { it.mediaId }.toSet()
            val changedShows = changedEpisodeShows(watched, latestWatched) +
                (eligible - progressShows.map { it.id }.toSet())
            database.upNextDao().clear()
            val rows = upNext.values.filter { it.showId in eligible }.map { episode ->
                UpNextEntity(
                    showId = episode.showId,
                    episodeId = episode.id.takeIf { it > 0 },
                    season = episode.season,
                    episodeNumber = episode.number,
                    episodeTitle = episode.title,
                    episodeAirDate = episode.airDate,
                    durationMinutes = episode.runtimeMinutes,
                    refreshedAt = refreshedAt,
                )
            }
            if (rows.isNotEmpty()) database.upNextDao().upsertAll(rows)
            changedShows.filter { it in eligible }.forEach { refreshLocalUpNext(it) }
            database.syncDao().upsertAll(
                listOf(
                    SyncStateEntity("progress_cache", Instant.ofEpochMilli(refreshedAt).toString(), refreshedAt),
                    SyncStateEntity("up_next_cache_v1", Instant.ofEpochMilli(refreshedAt).toString(), refreshedAt),
                ),
            )
        }
        onProgress?.invoke(.99f)
        scheduleProgressEnrichment()
        true
    }

    /**
     * Artwork and missing history labels improve presentation but are not needed
     * to make imported library/history/playback data correct. Run them after the
     * visible sync has completed and coalesce requests that arrive while a repair
     * pass is already active.
     */
    fun scheduleProgressEnrichment() {
        progressEnrichmentRequested = true
        if (progressEnrichmentJob?.isActive == true) return
        progressEnrichmentJob = progressEnrichmentScope.launch {
            do {
                progressEnrichmentRequested = false
                val state = loadCachedState()
                coroutineScope {
                    val activeLibrary = state.rails[RailIds.LIBRARY].orEmpty().filter {
                        it.status != LibraryStatus.NONE && it.status != LibraryStatus.DROPPED
                    }
                    val artworkTargets = activeLibrary + state.playbackTv.map(PlaybackCard::media) +
                        state.playbackMovies.map(PlaybackCard::media) + state.calendar.map(TimelineCard::media) +
                        state.history.map(TimelineCard::media)
                    val artworkRepair = async { refreshMissingArtwork(artworkTargets) }
                    val historyRepair = async { enrichHistoryLabels(state.history) }
                    artworkRepair.await()
                    historyRepair.await()
                }
            } while (progressEnrichmentRequested)
        }
    }

    /**
     * Simkl's public calendar is regenerated every few hours and is more precise
     * for currently airing shows. It supplies the canonical date while existing
     * TMDB rows continue to provide localized titles, stills and runtimes.
     */
    private suspend fun refreshSimklSchedule(shows: List<MediaCard>, force: Boolean = false): Boolean =
        releaseScheduleRepository.refresh(shows, force)

    private suspend fun refreshMissingArtwork(library: List<MediaCard>) {
        if (tmdbApiKey().isBlank()) return
        val missing = library.distinctBy(MediaCard::stableKey).filter { media ->
            media.posterUrl.isNullOrBlank() || media.backdropUrl.isNullOrBlank()
        }
        if (missing.isEmpty()) return
        val requests = Semaphore(permits = 10)
        val enriched = coroutineScope {
            missing.map { media ->
                async {
                    requests.withPermit {
                        runCatching {
                            val current = database.mediaDao().get(media.type.name, media.id)
                                ?: media.toEntity()
                            val details = if (media.type == MediaType.TV) {
                                services.tmdb.show(media.id, append = "")
                            } else {
                                services.tmdb.movie(media.id, append = "")
                            }.toEntity(media.type)
                            details.copy(
                                title = details.title.ifBlank { current.title },
                                overview = details.overview.ifBlank { current.overview },
                                posterPath = details.posterPath ?: current.posterPath,
                                backdropPath = details.backdropPath ?: current.backdropPath,
                                releaseDate = details.releaseDate ?: current.releaseDate,
                                score = details.score ?: current.score,
                                runtimeMinutes = details.runtimeMinutes ?: current.runtimeMinutes,
                                genres = details.genres.ifBlank { current.genres },
                                providers = details.providers.ifBlank { current.providers },
                                collectionId = details.collectionId ?: current.collectionId,
                            )
                        }.getOrNull()
                    }
                }
            }.mapNotNull { it.await() }
        }
        if (enriched.isNotEmpty()) database.mediaDao().upsertMedia(enriched)
    }

    suspend fun loadUpcomingEpisodes(
        shows: List<MediaCard>,
        forceSchedule: Boolean = false,
    ): List<EpisodeCard> {
        val today = localToday()
        val excludeSpecials = preferences.excludeSpecials.first()
        val distinctShows = shows.distinctBy(MediaCard::stableKey)
        val trackedShowIds = distinctShows.map(MediaCard::id).toSet()
        if (trackedShowIds.isEmpty()) return emptyList()
        refreshSimklSchedule(distinctShows, force = forceSchedule)
        val cached = database.mediaDao().episodeSnapshot().map { it.toDomain() }.filter { episode ->
            episode.showId in trackedShowIds && (!excludeSpecials || episode.season > 0) && episode.airDate?.take(10)?.let { raw ->
                runCatching { !LocalDate.parse(raw).isBefore(today) }.getOrDefault(false)
            } == true
        }
        if (tmdbApiKey().isBlank()) return cached.sortedBy(EpisodeCard::airDate)
        val now = System.currentTimeMillis()
        val scheduleFreshnessMillis = 8L * 60L * 60L * 1_000L
        val scheduleChecks = database.snapshotDao().syncStates().associateBy(SyncStateEntity::area)
        val showsDueForSchedule = distinctShows.filter { show ->
            val lastCheck = scheduleChecks["schedule:${show.id}"]?.lastSuccessfulSync
            lastCheck == null || now - lastCheck >= scheduleFreshnessMillis
        }
        if (showsDueForSchedule.isEmpty()) return cached.sortedBy(EpisodeCard::airDate)
        val requestSlots = Semaphore(permits = 5)
        val remoteResults = coroutineScope {
            showsDueForSchedule.map { show ->
                async {
                    val result = requestSlots.withPermit {
                        runCatching<List<EpisodeCard>> {
                            // The schedule only needs the base show payload. Avoid
                            // downloading credits, recommendations and videos for
                            // every tracked show during a Progress refresh.
                            val showDetails = services.tmdb.show(show.id, append = "")
                            val seasonNumbers = showDetails.seasons
                                .asSequence()
                                .map { it.number }
                                .filter { !excludeSpecials || it > 0 }
                                .sortedDescending()
                                .take(2)
                                .toList()
                            val announcedNext = listOfNotNull(showDetails.nextEpisodeToAir).mapNotNull { episode ->
                                val airDay = episode.airDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                                    ?: return@mapNotNull null
                                if (airDay.isBefore(today)) return@mapNotNull null
                                EpisodeCard(
                                    id = episode.id,
                                    showId = show.id,
                                    season = episode.season,
                                    number = episode.number,
                                    title = episode.name,
                                    overview = episode.overview,
                                    airDate = episode.airDate,
                                    stillUrl = episode.stillPath?.let { path -> "https://image.tmdb.org/t/p/w780$path" },
                                    runtimeMinutes = episode.runtime,
                                )
                            }
                            (announcedNext + seasonNumbers.flatMap { season ->
                                services.tmdb.season(show.id, season).episodes.mapNotNull { episode ->
                                    val airDay = episode.airDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                                        ?: return@mapNotNull null
                                    if (airDay.isBefore(today)) return@mapNotNull null
                                    EpisodeCard(
                                        id = episode.id,
                                        showId = show.id,
                                        season = episode.season,
                                        number = episode.number,
                                        title = episode.name,
                                        overview = episode.overview,
                                        airDate = episode.airDate,
                                        stillUrl = episode.stillPath?.let { path -> "https://image.tmdb.org/t/p/w780$path" },
                                        runtimeMinutes = episode.runtime,
                                    )
                                }
                            }).distinctBy { episode -> episode.season to episode.number }.sortedBy { it.airDate }
                        }
                    }
                    show.id to result
                }
            }.map { it.await() }
        }
        val cachedDates = cached.associate { episode ->
            Triple(episode.showId, episode.season, episode.number) to episode.airDate
        }
        val remote = remoteResults.flatMap { (_, result) -> result.getOrDefault(emptyList()) }
            .map { episode ->
                // TMDB fills the full episode model; a date already overlaid by
                // Simkl remains canonical for this one field only.
                episode.copy(
                    airDate = cachedDates[Triple(episode.showId, episode.season, episode.number)]
                        ?: episode.airDate,
                )
            }
        val completedChecks = remoteResults.mapNotNull { (showId, result) ->
            if (result.isSuccess) {
                SyncStateEntity("schedule:$showId", remoteTimestamp = null, lastSuccessfulSync = now)
            } else null
        }
        if (remote.isNotEmpty() || completedChecks.isNotEmpty()) {
            database.withTransaction {
                if (remote.isNotEmpty()) database.mediaDao().upsertEpisodes(remote.map { it.toEntity() })
                if (completedChecks.isNotEmpty()) database.syncDao().upsertAll(completedChecks)
            }
        }
        // A failed/partial season request must not erase the last valid schedule.
        // Key by show/season/number because TMDB episode IDs can be absent in old
        // imported cache rows.
        return (cached + remote)
            .distinctBy { "${it.showId}:${it.season}:${it.number}" }
            .sortedBy(EpisodeCard::airDate)
    }

    suspend fun loadEpisode(show: MediaCard, season: Int, number: Int): EpisodeCard? {
        val watched = (season to number) in watchedEpisodeNumbers(show.id)
        val cached = database.mediaDao().episode(show.id, season, number)?.toDomain()?.copy(watched = watched)
        if (tmdbApiKey().isBlank()) return cached
        return cancellableResult {
            val localized = services.tmdb.episode(show.id, season, number)
            val english = if (localized.name.isBlank() || localized.overview.isBlank()) {
                runCatching { services.tmdb.episode(show.id, season, number, language = "en-US") }.getOrNull()
            } else null
            localized.copy(
                name = localized.name.ifBlank { english?.name.orEmpty() },
                overview = localized.overview.ifBlank { english?.overview.orEmpty() },
            ).let {
                EpisodeCard(
                    id = it.id,
                    showId = show.id,
                    season = it.season,
                    number = it.number,
                    title = it.name,
                    overview = it.overview,
                    // Simkl's calendar can supply a precise timestamp while TMDB
                    // exposes only a date. Opening an episode must not downgrade it.
                    airDate = cached?.airDate ?: it.airDate,
                    stillUrl = it.stillPath?.let { path -> "https://image.tmdb.org/t/p/w1280$path" },
                    runtimeMinutes = it.runtime,
                    watched = watched,
                ).also { episode -> database.mediaDao().upsertEpisodes(listOf(episode.toEntity())) }
            }
        }.getOrNull() ?: cached
    }

    suspend fun loadTrailerKey(media: MediaCard): String? {
        if (tmdbApiKey().isBlank()) return null
        return cancellableResult {
            val videos = if (media.type == MediaType.TV) {
                services.tmdb.show(media.id).videos?.results.orEmpty()
            } else {
                services.tmdb.movie(media.id).videos?.results.orEmpty()
            }
            videos.asSequence()
                .filter { it.site.equals("YouTube", ignoreCase = true) && it.key.isNotBlank() }
                .sortedWith(
                    compareByDescending<com.cinetrack.data.remote.TmdbVideoDto> { it.official }
                        .thenByDescending { it.type.equals("Trailer", ignoreCase = true) }
                        .thenByDescending { it.publishedAt.orEmpty() },
                )
                .firstOrNull()
                ?.key
        }.getOrNull()
    }

    suspend fun loadEpisodeCast(show: MediaCard, season: Int, number: Int): List<PersonCard> {
        if (tmdbApiKey().isBlank()) return emptyList()
        return cancellableResult {
            val episode = services.tmdb.episode(show.id, season, number)
            (episode.credits?.cast.orEmpty() + episode.guestStars + episode.credits?.crew.orEmpty() + episode.crew).distinctBy { it.id }.map {
                PersonCard(
                    id = it.id,
                    name = it.name,
                    role = it.character ?: it.job.orEmpty(),
                    profileUrl = it.profilePath?.let { path -> "https://image.tmdb.org/t/p/w500$path" },
                )
            }
        }.getOrDefault(emptyList())
    }

    suspend fun loadCollection(media: MediaCard): List<MediaCard> {
        val collectionId = media.collectionId ?: return emptyList()
        if (tmdbApiKey().isBlank()) return emptyList()
        return cancellableResult {
            services.tmdb.collection(collectionId).parts
                .sortedBy { it.releaseDate.orEmpty() }
                .map { it.toEntity(MediaType.MOVIE).toDomain() }
        }.getOrDefault(emptyList())
    }

    suspend fun loadRecommendations(media: MediaCard): List<MediaCard> {
        if (tmdbApiKey().isBlank()) return emptyList()
        return cancellableResult {
            val allowedRegions = preferences.contentRegions.first()
            val hiddenDiscovery = preferences.hiddenDiscovery.first()
            val candidates = (recommendationCandidatesCache[media.stableKey] ?: run {
                val dto = if (media.type == MediaType.MOVIE) services.tmdb.movie(media.id) else services.tmdb.show(media.id)
                dto.recommendations?.results.orEmpty().also { recommendationCandidatesCache[media.stableKey] = it }
            }).take(18)
            val filteredCandidates = if (allowedRegions.isEmpty()) candidates else coroutineScope {
                val requests = Semaphore(4)
                candidates.map { candidate -> async {
                    requests.withPermit {
                        val countries = candidate.originCountries.ifEmpty {
                            runCatching {
                                val details = if (media.type == MediaType.MOVIE) services.tmdb.movie(candidate.id) else services.tmdb.show(candidate.id)
                                details.originCountries + details.productionCountries.map { it.code }
                            }.getOrDefault(emptyList())
                        }
                        candidate.takeIf { countries.any { it.uppercase() in allowedRegions } }
                    }
                } }.mapNotNull { it.await() }
            }
            filteredCandidates
                .map { it.toEntity(media.type).toDomain() }
                .filterNot { it.id == media.id }
                .filterNot { it.stableKey in hiddenDiscovery }
                .distinctBy(MediaCard::stableKey)
                .take(18)
        }.getOrDefault(emptyList())
    }

    /** Loads cast/crew only when the Statistics tab requests it, avoiding startup and scrolling work. */
    suspend fun loadViewingPeople(history: List<TimelineCard>): Pair<List<PersonCard>, List<PersonCard>> = coroutineScope {
        val weights = history.groupingBy { it.media.stableKey }.eachCount()
        val media = history.map(TimelineCard::media).distinctBy(MediaCard::stableKey).take(12)
        val requests = Semaphore(4)
        val credits = media.map { item -> async {
            item to requests.withPermit { loadCast(item) }
        } }.map { it.await() }
        val actorCounts = mutableMapOf<Int, Pair<PersonCard, Int>>()
        val directorCounts = mutableMapOf<Int, Pair<PersonCard, Int>>()
        credits.forEach { (item, people) ->
            val weight = weights[item.stableKey] ?: 1
            people.distinctBy(PersonCard::id).forEach { person ->
                // Full credits include writers, camera, sound and other crew.
                // Only actual cast members belong in the actor statistics.
                if (person.isCastMember) {
                    actorCounts[person.id] = person to ((actorCounts[person.id]?.second ?: 0) + weight)
                }
                if (person.isDirector) {
                    directorCounts[person.id] = person to ((directorCounts[person.id]?.second ?: 0) + weight)
                }
            }
        }
        fun Map<Int, Pair<PersonCard, Int>>.top() = values
            .sortedWith(compareByDescending<Pair<PersonCard, Int>> { it.second }.thenBy { it.first.name })
            .take(5)
            .map { it.first }
        actorCounts.top() to directorCounts.top()
    }

    suspend fun loadRatings(media: MediaCard): List<RatingScore> {
        val enabledSources = preferences.ratingSources.first()
        val displayNames = linkedMapOf(
            "tmdb" to "TMDB",
            "imdb" to "IMDb",
            "metacritic" to "Metacritic",
            "tomatoes" to "R.Tomatoes",
        )
        val enabledDisplayNames = displayNames.filterKeys(enabledSources::contains)
        val base = enabledDisplayNames.map { (source, label) ->
            RatingScore(label, if (source == "tmdb" && media.score != null) "%.1f".format(media.score) else "—")
        }
        if (mdbListApiKey().isBlank()) return base
        val type = if (media.type == MediaType.MOVIE) "movie" else "show"
        fun formatted(source: String, value: Double): String = when (source) {
            "metacritic" -> value.toInt().toString()
            "tomatoes" -> "${value.toInt()}%"
            else -> "%.1f".format(value)
        }
        val remote = linkedMapOf<String, String>()
        // MDBList's single-title response carries every available rating in one
        // request. Prefer it so free-tier rate limiting cannot turn four parallel
        // requests into four dashes.
        val mediaInfoLoaded = runCatching {
            services.mdbList.mediaInfo(type, media.id, mdbListApiKey())["ratings"]?.jsonArray.orEmpty().forEach { element ->
                    val rating = element.jsonObject
                    val rawSource = listOf("source", "provider", "name").firstNotNullOfOrNull { key ->
                        rating[key]?.jsonPrimitive?.contentOrNull
                    }?.lowercase()?.replace(" ", "")
                    val source = when (rawSource) {
                        "rottentomatoes", "rtomatoes" -> "tomatoes"
                        else -> rawSource
                    }
                    val value = listOf("value", "rating", "score").firstNotNullOfOrNull { key ->
                        rating[key]?.jsonPrimitive?.doubleOrNull
                    }
                    if (source != null && value != null && source in displayNames) remote[source] = formatted(source, value)
                }
        }.isSuccess && remote.isNotEmpty()
        if (!mediaInfoLoaded) coroutineScope {
            val fallback = displayNames.keys.map { source -> async {
                runCatching {
                    services.mdbList.rating(
                        mediaType = type,
                        ratingSource = source,
                        apiKey = mdbListApiKey(),
                        request = MdbListRatingRequest(listOf(media.id.toString()), "tmdb"),
                    ).ratings.firstOrNull()?.rating?.let { source to formatted(source, it) }
                }.getOrNull()
            } }.mapNotNull { it.await() }
            remote.putAll(fallback)
        }
        return enabledDisplayNames.map { (source, label) ->
            RatingScore(label, remote[source] ?: if (source == "tmdb" && media.score != null) "%.1f".format(media.score) else "—")
        }
    }

    internal suspend fun syncSimklProvider(
        operations: List<SyncOperation> = emptyList(),
        onProgress: (SyncProgress) -> Unit,
    ): Result<ProviderSyncOutcome> {
        val previousSyncState = database.syncDao().get("all")
        val previousSuccessfulSync = previousSyncState?.lastSuccessfulSync
        val previousReport = preferences.syncReportNow()
        return cancellableResult {
        check(!preferences.tokenNow().isNullOrBlank()) { "Connect Simkl first" }
        // Repair stale queue rows before reading the local push set. This is
        // idempotent and is deliberately repeated defensively before sync.
        repairSyncQueue()
        val previousBaseline = preferences.syncBaselineNow(TrackingProviderId.SIMKL)
        fun progress(running: Boolean, value: Float, stage: SyncStage, message: String? = null) =
            onProgress(SyncProgress(running, value, stage, message, previousSuccessfulSync))
        progress(true, .08f, SyncStage.AUTH)
        val historyBeforeSync = database.timelineDao().historySnapshot()
        val activity = services.simklSync.activities()
        progress(true, .20f, SyncStage.ACTIVITY)

        // Versioned marker: force one complete episode-level baseline after upgrading.
        // This repairs accounts where an earlier delta sync imported a show status but
        // did not receive every watched episode in that show.
        val episodeBaselineComplete = database.syncDao().get("episode_baseline_037") != null
        val librarySnapshotComplete = database.syncDao().get("library_snapshot_051") != null
        val previousShows = if (episodeBaselineComplete) database.syncDao().get("shows")?.remoteTimestamp else null
        val previousAnime = if (episodeBaselineComplete) database.syncDao().get("anime")?.remoteTimestamp else null
        val previousMovies = database.syncDao().get("movies")?.remoteTimestamp
        val previousShowsRemoved = database.syncDao().get("shows_removed")?.remoteTimestamp
        val previousAnimeRemoved = database.syncDao().get("anime_removed")?.remoteTimestamp
        val previousMoviesRemoved = database.syncDao().get("movies_removed")?.remoteTimestamp
        // Anime and ordinary shows share CineTrack's TV media type. If either
        // removal generation changes, fetch both complete lists before comparing
        // membership so one category can never erase the other.
        val tvRemovalChanged = !librarySnapshotComplete ||
            previousShowsRemoved != activity.tvShows.removedFromList ||
            previousAnimeRemoved != activity.anime.removedFromList
        val movieRemovalChanged = !librarySnapshotComplete ||
            previousMoviesRemoved != activity.movies.removedFromList
        val remoteChanged = previousSyncState?.remoteTimestamp != activity.all ||
            previousShows != activity.tvShows.all ||
            previousAnime != activity.anime.all ||
            previousMovies != activity.movies.all ||
            tvRemovalChanged ||
            movieRemovalChanged
        val pendingLocalStates = validatedPendingLocalStates()
        val pendingWrites = database.syncDao().pendingWrites()
        val pendingEpisodeWrites = pendingWrites.filter {
            it.operation == "EPISODE_WATCHED" || it.operation == "EPISODE_UNWATCHED"
        }
        val pendingMediaHistoryRemovals = pendingWrites.filter { it.operation == "MEDIA_HISTORY_REMOVE" }
        val pendingCount = pendingLocalStates.size + pendingEpisodeWrites.size + pendingMediaHistoryRemovals.size

        // Simkl activity is the gate for every remote/item operation. When the
        // generation is unchanged and there is nothing local to push, stop here:
        // no playback request, no Room write and no Progress/UI reconstruction.
        if (
            !remoteChanged && pendingLocalStates.isEmpty() && pendingEpisodeWrites.isEmpty() &&
            pendingMediaHistoryRemovals.isEmpty()
        ) {
            val progressChanged = refreshProgressCache(
                request = ProgressRefreshRequest(),
            ) { value ->
                progress(true, value, SyncStage.PROCESSING)
            }
            val checkedAt = System.currentTimeMillis()
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
                acknowledgedOperationIds = operations.mapTo(linkedSetOf(), SyncOperation::id),
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
        val resolvedShows = resolveSimklItems(remoteShows, MediaType.TV)
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
                                                        stillUrl = episode.stillPath?.let { path -> "https://image.tmdb.org/t/p/w780$path" },
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
        val remoteStates = resolvedItems.mapNotNull { it.item.toState(it.type, it.tmdbId) }
        val remoteTvKeys = resolvedShows.map { "${MediaType.TV.name}:${it.tmdbId}" }.toSet()
        val remoteMovieKeys = resolvedMovies.map { "${MediaType.MOVIE.name}:${it.tmdbId}" }.toSet()
        val localStates = database.stateDao().stateSnapshot()
        val localStatesByKey = localStates.associateBy { "${it.mediaType}:${it.mediaId}" }
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
        val remoteRemovedStates = localStates.mapNotNull { state ->
            if (state.dirty || state.status == LibraryStatus.NONE.name) return@mapNotNull null
            val key = "${state.mediaType}:${state.mediaId}"
            val removed = when (state.mediaType) {
                MediaType.TV.name -> completeTvSnapshot && key !in remoteTvKeys
                MediaType.MOVIE.name -> completeMovieSnapshot && key !in remoteMovieKeys
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
            previous = previousBaseline,
            pulled = pulledTrackingSnapshot,
            completeMovies = completeMovieSnapshot,
            completeShows = completeTvSnapshot,
        )
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
                episodes = historyBeforeSync.mapNotNull { history ->
                    val season = history.season ?: return@mapNotNull null
                    val episode = history.episodeNumber ?: return@mapNotNull null
                    TrackedEpisodeState(
                        showIds = MediaIds(tmdb = history.mediaId.toLong()),
                        season = season,
                        episode = episode,
                        watched = true,
                        watchedAt = history.watchedAt.toInstantOrNull(),
                        updatedAt = history.watchedAt.toInstantOrNull(),
                    )
                }.distinctBy { "${it.showIds.tmdb}:$it.season:$it.episode" },
                generatedAt = Instant.now(),
            ),
            baseline = previousBaseline,
            dirtyMediaKeys = pendingLocalStates.flatMap {
                listOf("${it.mediaType}:${it.mediaId}", "tmdb:${it.mediaId}")
            }.toSet(),
            pendingOperations = syncOperationRepository.pending(),
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
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                providerId = conflict.providerId.name,
                season = conflict.season,
                episode = conflict.episode,
            )
        }.distinctBy(SyncOperationEntity::operationId)
        val conflictLibraryKeys = reconciliation.conflicts
            .filter { it.field == com.cinetrack.data.sync.ConflictField.LIBRARY_STATUS }
            .map { "${it.mediaType.name}:${it.ids.tmdb?.toInt()}" }
            .toSet()
        val conflictEpisodeKeys = reconciliation.conflicts
            .filter { it.field == com.cinetrack.data.sync.ConflictField.EPISODE_WATCHED }
            .map { "${it.ids.tmdb?.toInt()}:${it.season}:${it.episode}" }
            .toSet()
        val localStatesToPush = pendingLocalStates.filterNot { state ->
            "${state.mediaType}:${state.mediaId}" in conflictLibraryKeys
        }
        val episodeWritesToPush = pendingEpisodeWrites.filterNot { write ->
            val parts = write.payload.split(':', limit = 3)
            "${write.mediaId}:${parts.getOrNull(0)}:${parts.getOrNull(1)}" in conflictEpisodeKeys
        }
        val historyRemovalsToPush = pendingMediaHistoryRemovals.filterNot { write ->
            reconciliation.conflicts.any {
                it.field == com.cinetrack.data.sync.ConflictField.WATCHED &&
                    it.mediaType.name == write.mediaType && it.ids.tmdb?.toInt() == write.mediaId
            }
        }
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

        // Push pending local mutations before publishing the downloaded snapshot.
        // If a request fails, the transaction below never runs: the existing local
        // snapshot and dirty queue remain intact and can safely retry later.
        val pushedWriteIds = mutableListOf<Long>()
        historyRemovalsToPush.forEach { write ->
            pushMediaHistoryRemoval(write)
            pushedWriteIds += write.id
        }
        localStatesToPush.forEach { state -> pushLibraryState(state) }
        episodeWritesToPush.forEach { write ->
            pushEpisodeWrite(write)
            pushedWriteIds += write.id
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
                val watchedEpisodes = mutableMapOf<Pair<Int, Int>, Pair<String?, String?>>()
                item.seasons.forEach { season ->
                    season.episodes.forEach { episode ->
                        watchedEpisodes[season.number to episode.number] =
                            episode.title.takeIf(String::isNotBlank) to episode.watchedAt
                    }
                }
                repairedEpisodes.forEach { episode ->
                    watchedEpisodes.putIfAbsent(
                        episode.season to episode.number,
                        episode.title.takeIf(String::isNotBlank) to null,
                    )
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
            }
        }
        database.withTransaction {
            fun WatchHistoryEntity.episodeKey(): Triple<Int, Int, Int>? =
                if (mediaType == MediaType.TV.name && season != null && episodeNumber != null)
                    Triple(mediaId, season, episodeNumber) else null
            val beforeKeys = historyBeforeSync.mapNotNull { it.episodeKey() }.toSet()
            val currentHistory = database.timelineDao().historySnapshot()
            val currentKeys = currentHistory.mapNotNull { it.episodeKey() }.toSet()
            val protectedKeys = (pendingEpisodeWrites + database.syncDao().pendingWrites())
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
            if (newMedia.isNotEmpty()) database.mediaDao().upsertMedia(newMedia)
            if (remoteStates.isNotEmpty() || remoteRemovedStates.isNotEmpty()) {
                database.stateDao().upsertAll(remoteStates + remoteRemovedStates)
                // Only validated, current user intent may override MAIN.
                database.stateDao().upsertAll(pendingLocalStates)
            }
            reconciliation.localMutations.forEach { mutation ->
                val type = mutation.mediaType.name
                val id = mutation.mediaId.toInt()
                val previous = database.stateDao().get(type, id)
                when (mutation) {
                    is LocalMutation.SetLibraryStatus -> {
                        database.stateDao().upsert(
                            UserMediaStateEntity(type, id, mutation.status.name, previous?.watched ?: false, previous?.simklId, System.currentTimeMillis(), dirty = false),
                        )
                        clearObsoleteOperations(type, id, setOf(SyncOperationType.LIBRARY_STATUS.name))
                    }
                    is LocalMutation.SetWatched -> {
                        database.stateDao().upsert(UserMediaStateEntity(type, id, previous?.status ?: LibraryStatus.NONE.name, mutation.watched, previous?.simklId, System.currentTimeMillis(), dirty = false))
                        clearObsoleteOperations(
                            type,
                            id,
                            if (mutation.season != null) setOf(SyncOperationType.EPISODE_WATCHED.name, SyncOperationType.EPISODE_UNWATCHED.name)
                            else setOf(SyncOperationType.MOVIE_WATCHED.name, SyncOperationType.MOVIE_UNWATCHED.name),
                            mutation.season,
                            mutation.episode,
                        )
                        if (mutation.season != null && mutation.episode != null) {
                            if (mutation.watched) database.timelineDao().insertHistory(WatchHistoryEntity(mediaType = type, mediaId = id, season = mutation.season, episodeNumber = mutation.episode, watchedAt = mutation.watchedAt?.toString() ?: Instant.now().toString()))
                            else database.timelineDao().deleteEpisodeHistory(type, id, mutation.season, mutation.episode)
                        } else if (!mutation.watched) database.timelineDao().deleteMediaHistory(type, id)
                    }
                }
            }
            remoteRemovedStates.groupBy(UserMediaStateEntity::mediaType).forEach { (mediaType, states) ->
                states.map(UserMediaStateEntity::mediaId).distinct().chunked(500).forEach { ids ->
                    database.timelineDao().deleteMediaHistories(mediaType, ids)
                }
            }
            baselineShowIds.chunked(500).forEach { ids ->
                database.timelineDao().deleteMediaHistories(MediaType.TV.name, ids)
            }
            historyRowsToReplace.chunked(500).forEach { ids ->
                database.timelineDao().deleteHistoryRows(ids)
            }
            // Room can bind the complete import in one prepared batch instead
            // of executing one INSERT statement for every watched episode.
            if (importedHistory.isNotEmpty()) {
                // Retain the local row once, even if the download already includes
                // that same episode. Otherwise watch-time statistics double-count it.
                val localKeys = locallyAdded.mapNotNull { it.episodeKey() }.toSet()
                database.timelineDao().insertHistoryItems(
                    importedHistory.filterNot { it.episodeKey() in locallyRemoved || it.episodeKey() in localKeys },
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
            localStatesToPush.forEach { state ->
                database.stateDao().markCleanIfUnchanged(state.mediaType, state.mediaId, state.updatedAt)
            }
            pushedWriteIds.chunked(500).forEach { ids -> database.syncDao().deleteWrites(ids) }
            val completedOperationIds = localStatesToPush.map { stateOperationId(it.mediaType, it.mediaId) } +
                pushedWriteIds.map(::writeOperationId)
            completedOperationIds.chunked(500).forEach { ids -> database.syncDao().deleteOperations(ids) }
            if (conflictOperations.isNotEmpty()) database.syncDao().upsertOperations(conflictOperations)
            database.syncDao().upsertAll(syncStates)
            // Library membership, history, playback and sync generation become
            // visible in the same commit. No observer can see the halfway state.
            if (libraryChanged) rebuildLibraryRailInTransaction()
        }
        preferences.markSimklChecked(committedAt)
        preferences.saveSyncBaseline(
            advanceSyncBaseline(
                previous = previousBaseline,
                remote = remoteTrackingSnapshot,
                acknowledgedStates = localStatesToPush,
                acknowledgedEpisodeWrites = episodeWritesToPush,
                acknowledgedHistoryRemovals = historyRemovalsToPush,
                conflicts = reconciliation.conflicts,
            ),
            TrackingProviderId.SIMKL,
        )
        progress(true, .78f, SyncStage.COMMIT)
        val remainingPendingChanges = database.stateDao().pendingStates().size + database.syncDao().pendingWrites().size
        val addedCount = remoteStates.count { state ->
            localStatesByKey["${state.mediaType}:${state.mediaId}"]?.status in setOf(null, LibraryStatus.NONE.name)
        }
        val conflictCount = conflictOperations.size
        val fullSync = !episodeBaselineComplete || !librarySnapshotComplete || completeTvSnapshot || completeMovieSnapshot
        val report = SyncReport(
            downloaded = resolvedItems.size + importedHistory.size,
            uploaded = pendingCount,
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
        val tvLibraryChanged = (remoteStates + remoteRemovedStates + pendingLocalStates)
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
        val acknowledgedOperationIds = (localStatesToPush.map { stateOperationId(it.mediaType, it.mediaId) } +
            pushedWriteIds.map(::writeOperationId)).toSet().intersect(operations.mapTo(linkedSetOf(), SyncOperation::id))
        val deferredOperationIds = operations.filter { operation ->
            reconciliation.conflicts.any { conflict ->
                when (conflict.field) {
                    com.cinetrack.data.sync.ConflictField.LIBRARY_STATUS ->
                        operation.type == SyncOperationType.LIBRARY_STATUS && operation.mediaType == conflict.mediaType && operation.mediaId == conflict.ids.tmdb?.toInt()
                    com.cinetrack.data.sync.ConflictField.WATCHED ->
                        operation.type in setOf(SyncOperationType.MOVIE_WATCHED, SyncOperationType.MOVIE_UNWATCHED) && operation.mediaId == conflict.ids.tmdb?.toInt()
                    com.cinetrack.data.sync.ConflictField.EPISODE_WATCHED ->
                        operation.type in setOf(SyncOperationType.EPISODE_WATCHED, SyncOperationType.EPISODE_UNWATCHED) && operation.mediaId == conflict.ids.tmdb?.toInt() && operation.payload.orEmpty().startsWith("${conflict.season}:${conflict.episode}")
                }
            }
        }.mapTo(linkedSetOf(), SyncOperation::id)
        ProviderSyncOutcome(
            itemsChanged = remoteChanged || pendingCount > 0 || progressChanged,
            report = report,
            acknowledgedOperationIds = acknowledgedOperationIds,
            deferredOperationIds = deferredOperationIds,
        )
        }.onFailure { error ->
            database.syncDao().syncOperations()
                .filter { operation -> operation.status == SyncOperationStatus.PENDING.name }
                .forEach { operation -> database.syncDao().markOperationFailed(operation.operationId, syncError(error)) }
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

    suspend fun completeLogin(code: String, state: String?): Result<Unit> =
        preferences.completeSimklLogin(code, state, services.simklAuth).onSuccess {
            onTokenChanged(preferences.tokenNow())
        }

    suspend fun disconnectSimkl() {
        preferences.setToken(null)
        onTokenChanged(null)
    }

    suspend fun setUiAccent(value: String) = preferences.setUiAccent(value)

    suspend fun setIntroductionCompleted(value: Boolean) = preferences.setIntroductionCompleted(value)

    suspend fun verifyAndSetTmdbApiKey(value: String): Result<Unit> = cancellableResult {
        val candidate = value.trim()
        require(candidate.isNotBlank()) { "TMDB API credential cannot be empty" }
        NetworkFactory.create(
            token = { null },
            tmdbApiKey = { candidate },
            metadataLanguage = { "en-US" },
            metadataRegion = { "US" },
            metadataTimezone = { "UTC" },
        ).tmdb.trendingMovies()
        preferences.setTmdbApiKey(candidate)
        onTmdbApiKeyChanged(candidate)
    }

    suspend fun verifyAndSetMdbListApiKey(value: String): Result<Unit> = cancellableResult {
        val candidate = value.trim()
        require(candidate.isNotBlank()) { "MDBList API credential cannot be empty" }
        services.mdbList.mediaInfo(mediaType = "movie", id = 550, apiKey = candidate)
        preferences.setMdbListApiKey(candidate)
        onMdbListApiKeyChanged(candidate)
    }

    suspend fun setTmdbApiKey(value: String?) {
        preferences.setTmdbApiKey(value)
        onTmdbApiKeyChanged(preferences.tmdbApiKeyNow())
    }

    suspend fun setMdbListApiKey(value: String?) {
        preferences.setMdbListApiKey(value)
        onMdbListApiKeyChanged(preferences.mdbListApiKeyNow())
    }

    suspend fun setMetadataLanguage(value: String) {
        preferences.setMetadataLanguage(value)
        onMetadataLanguageChanged(value)
        castCache.clear()
        episodeTitleCache.clear()
        recommendationCandidatesCache.clear()
    }

    suspend fun setMetadataRegion(value: String) {
        preferences.setMetadataRegion(value)
        onMetadataRegionChanged(value)
    }

    suspend fun setMetadataTimezone(value: String) {
        preferences.setMetadataTimezone(value)
        onMetadataTimezoneChanged(value)
    }

    suspend fun exportJson(): String {
        val state = loadCachedState()
        return buildJsonObject {
            put("format", "cinetrack-0.63")
            put("exportedAt", Instant.now().toString())
            putJsonArray("library") {
                state.rails[RailIds.LIBRARY].orEmpty().forEach { media ->
                    add(buildJsonObject {
                        put("tmdbId", media.id)
                        put("type", media.type.name.lowercase())
                        put("title", media.title)
                        put("status", media.status.name.lowercase())
                        put("watched", media.watched)
                    })
                }
            }
            putJsonArray("history") {
                state.history.forEach { item ->
                    add(buildJsonObject {
                        put("tmdbId", item.media.id)
                        put("type", item.media.type.name.lowercase())
                        put("watchedAt", item.timestamp)
                        item.episodeLabel?.let { put("episode", it) }
                    })
                }
            }
        }.toString()
    }

    /**
     * Creates the logical files for a portable CineTrack backup. JSONL is the
     * authoritative representation; CSV mirrors it for spreadsheet inspection.
     * API credentials and the Simkl token are deliberately never exported.
     */
    suspend fun exportBackupFiles(sections: Set<String>): Map<String, String> {
        val selected = sections.ifEmpty { setOf("library", "progress", "history", "settings") }
        val state = loadCachedState()
        val payload = linkedMapOf<String, String>()
        val counts = linkedMapOf<String, Int>()

        if ("library" in selected) {
            val items = state.rails[RailIds.LIBRARY].orEmpty()
            payload["data/library.jsonl"] = items.joinToString("\n") { media ->
                buildJsonObject {
                    put("tmdbId", media.id)
                    put("type", media.type.name.lowercase())
                    put("title", media.title)
                    put("status", media.status.name.lowercase())
                    put("watched", media.watched)
                    media.releaseDate?.let { put("releaseDate", it) }
                }.toString()
            }.withTrailingLine()
            payload["readable/library.csv"] = buildString {
                appendLine(csvRow("tmdb_id", "type", "title", "status", "watched", "release_date"))
                items.forEach { media ->
                    appendLine(csvRow(media.id, media.type.name.lowercase(), media.title, media.status.name.lowercase(), media.watched, media.releaseDate.orEmpty()))
                }
            }
            counts["library"] = items.size
        }

        if ("progress" in selected) {
            val items = state.playbackTv + state.playbackMovies
            payload["data/progress.jsonl"] = items.joinToString("\n") { item ->
                buildJsonObject {
                    put("tmdbId", item.media.id)
                    put("type", item.media.type.name.lowercase())
                    put("title", item.media.title)
                    item.season?.let { put("season", it) }
                    item.episodeNumber?.let { put("episode", it) }
                    item.episodeTitle?.let { put("episodeTitle", it) }
                    put("progress", item.progress.toDouble())
                }.toString()
            }.withTrailingLine()
            payload["readable/progress.csv"] = buildString {
                appendLine(csvRow("tmdb_id", "type", "title", "season", "episode", "episode_title", "progress"))
                items.forEach { item ->
                    appendLine(csvRow(item.media.id, item.media.type.name.lowercase(), item.media.title, item.season ?: "", item.episodeNumber ?: "", item.episodeTitle.orEmpty(), item.progress))
                }
            }
            counts["progress"] = items.size
        }

        if ("history" in selected) {
            val items = state.history
            payload["data/history.jsonl"] = items.joinToString("\n") { item ->
                buildJsonObject {
                    put("tmdbId", item.media.id)
                    put("type", item.media.type.name.lowercase())
                    put("title", item.media.title)
                    put("watchedAt", item.timestamp)
                    item.season?.let { put("season", it) }
                    item.episodeNumber?.let { put("episode", it) }
                    item.episodeLabel?.let { put("episodeLabel", it) }
                }.toString()
            }.withTrailingLine()
            payload["readable/history.csv"] = buildString {
                appendLine(csvRow("tmdb_id", "type", "title", "watched_at", "season", "episode", "episode_label"))
                items.forEach { item ->
                    appendLine(csvRow(item.media.id, item.media.type.name.lowercase(), item.media.title, item.timestamp, item.season ?: "", item.episodeNumber ?: "", item.episodeLabel.orEmpty()))
                }
            }
            counts["history"] = items.size
        }

        if ("settings" in selected) {
            val settings = buildJsonObject {
                put("uiAccent", state.uiAccent)
                put("metadataLanguage", state.metadataLanguage)
                put("metadataRegion", state.metadataRegion)
                put("metadataTimezone", state.metadataTimezone)
                put("backgroundSync", state.backgroundSync)
                put("wifiOnly", state.wifiOnly)
                put("notificationEpisodes", state.notificationEpisodes)
                put("notificationMovies", state.notificationMovies)
                put("notificationSync", state.notificationSync)
                put("quietHoursEnabled", state.quietHoursEnabled)
                put("quietHoursStart", state.quietHoursStart)
                put("quietHoursEnd", state.quietHoursEnd)
                put("contentRegions", state.contentRegions.sorted().joinToString(","))
                put("ratingSources", state.ratingSources.sorted().joinToString(","))
                put("excludeSpecials", state.excludeSpecials)
                put("preferredProviders", state.preferredProviders.sorted().joinToString("|"))
                put("providerRegion", state.providerRegion)
                put("visibleProviderTypes", state.visibleProviderTypes.sorted().joinToString("|"))
                put("heroLayout", state.heroLayout)
                put("posterFormat", state.posterFormat)
                put("posterSize", state.posterSize)
                put("cardDensity", state.cardDensity)
                put("hiddenDiscovery", state.hiddenDiscovery.sorted().joinToString("|"))
            }.toString()
            payload["data/settings.jsonl"] = "$settings\n"
            payload["readable/settings.csv"] = buildString {
                appendLine(csvRow("ui_accent", "metadata_language", "metadata_region", "metadata_timezone", "background_sync", "wifi_only", "content_regions", "rating_sources"))
                appendLine(csvRow(state.uiAccent, state.metadataLanguage, state.metadataRegion, state.metadataTimezone, state.backgroundSync, state.wifiOnly, state.contentRegions.sorted().joinToString("|"), state.ratingSources.sorted().joinToString("|")))
            }
            counts["settings"] = 1
        }

        val generatedAt = Instant.now().toString()
        val manifest = buildJsonObject {
            put("formatVersion", "1.0")
            put("generatedAt", generatedAt)
            put("generator", "CineTrack ${BuildConfig.VERSION_NAME}")
            putJsonObject("sections") {
                counts.forEach { (section, count) ->
                    putJsonObject(section) {
                        put("records", count)
                        put("dataFile", "data/$section.jsonl")
                        put("readableFile", "readable/$section.csv")
                        put("sha256", payload["data/$section.jsonl"].orEmpty().sha256())
                    }
                }
            }
        }.toString()
        return linkedMapOf(
            "manifest.json" to manifest,
            "README.md" to "# CineTrack backup\n\nGenerated $generatedAt. Files in `data/` are authoritative JSON Lines; `readable/` contains matching CSV exports. Authentication tokens and API credentials are excluded.\n",
        ).apply { putAll(payload) }
    }

    suspend fun createAutomaticBackup() {
        preferences.saveAutomaticBackup(exportBackupFiles(setOf("library", "progress", "history", "settings")))
    }

    suspend fun restoreAutomaticBackup(): Int = restoreBackupFiles(preferences.readAutomaticBackup())

    /** Validates the complete archive before a single Room row is changed. */
    suspend fun restoreBackupFiles(files: Map<String, String>): Int {
        val manifestText = files["manifest.json"] ?: error("Backup manifest is missing")
        val manifest = Json.parseToJsonElement(manifestText).jsonObject
        check(manifest["formatVersion"]?.jsonPrimitive?.contentOrNull?.startsWith("1.") == true) {
            "Unsupported backup format"
        }
        val sections = manifest["sections"]?.jsonObject ?: error("Backup sections are missing")
        sections.forEach { (_, value) ->
            val section = value.jsonObject
            val path = section["dataFile"]?.jsonPrimitive?.contentOrNull ?: error("Backup data path is missing")
            val expected = section["sha256"]?.jsonPrimitive?.contentOrNull ?: error("Backup checksum is missing")
            val contents = files[path] ?: error("Backup file $path is missing")
            check(contents.sha256() == expected) { "Backup checksum failed for $path" }
        }

        val media = mutableListOf<MediaEntity>()
        val states = mutableListOf<UserMediaStateEntity>()
        files["data/library.jsonl"].orEmpty().lineSequence().filter(String::isNotBlank).forEach { line ->
            val item = Json.parseToJsonElement(line).jsonObject
            val id = item["tmdbId"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: error("Invalid library id")
            val type = item["type"]?.jsonPrimitive?.contentOrNull?.uppercase()?.let(MediaType::valueOf) ?: error("Invalid media type")
            val title = item["title"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank) ?: error("Invalid title")
            val status = item["status"]?.jsonPrimitive?.contentOrNull?.uppercase()?.let(LibraryStatus::valueOf) ?: LibraryStatus.NONE
            val watched = item["watched"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false
            media += MediaEntity(type.name, id, title, "", null, null, item["releaseDate"]?.jsonPrimitive?.contentOrNull, null, null, "", "", null)
            states += UserMediaStateEntity(type.name, id, status.name, watched, dirty = false)
        }
        val playback = mutableListOf<PlaybackEntity>()
        files["data/progress.jsonl"].orEmpty().lineSequence().filter(String::isNotBlank).forEach { line ->
            val item = Json.parseToJsonElement(line).jsonObject
            val id = item["tmdbId"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: error("Invalid progress id")
            val type = item["type"]?.jsonPrimitive?.contentOrNull?.uppercase()?.let(MediaType::valueOf) ?: error("Invalid progress type")
            val title = item["title"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank) ?: error("Invalid progress title")
            media += MediaEntity(type.name, id, title, "", null, null, null, null, null, "", "", null)
            playback += PlaybackEntity(
                mediaType = type.name,
                mediaId = id,
                progress = item["progress"]?.jsonPrimitive?.contentOrNull?.toFloatOrNull()?.coerceIn(0f, 1f) ?: 0f,
                positionSeconds = 0L,
                durationSeconds = 0L,
                updatedAt = Instant.now().toString(),
                season = item["season"]?.jsonPrimitive?.contentOrNull?.toIntOrNull(),
                episodeNumber = item["episode"]?.jsonPrimitive?.contentOrNull?.toIntOrNull(),
                episodeTitle = item["episodeTitle"]?.jsonPrimitive?.contentOrNull,
            )
        }
        val history = mutableListOf<WatchHistoryEntity>()
        files["data/history.jsonl"].orEmpty().lineSequence().filter(String::isNotBlank).forEach { line ->
            val item = Json.parseToJsonElement(line).jsonObject
            val id = item["tmdbId"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: error("Invalid history id")
            val type = item["type"]?.jsonPrimitive?.contentOrNull?.uppercase()?.let(MediaType::valueOf) ?: error("Invalid media type")
            val watchedAt = item["watchedAt"]?.jsonPrimitive?.contentOrNull ?: error("Invalid watch timestamp")
            history += WatchHistoryEntity(
                mediaType = type.name,
                mediaId = id,
                season = item["season"]?.jsonPrimitive?.contentOrNull?.toIntOrNull(),
                episodeNumber = item["episode"]?.jsonPrimitive?.contentOrNull?.toIntOrNull(),
                episodeTitle = item["episodeLabel"]?.jsonPrimitive?.contentOrNull?.substringAfter(" · ", "")?.takeIf(String::isNotBlank),
                watchedAt = watchedAt,
            )
        }
        val settings = files["data/settings.jsonl"]?.lineSequence()?.firstOrNull(String::isNotBlank)
            ?.let { Json.parseToJsonElement(it).jsonObject }
        check(media.isNotEmpty() || history.isNotEmpty() || playback.isNotEmpty() || settings != null) {
            "The backup has no restorable records"
        }
        fun WatchHistoryEntity.restoreKey() = listOf(
            mediaType,
            mediaId.toString(),
            season?.toString().orEmpty(),
            episodeNumber?.toString().orEmpty(),
            watchedAt,
        ).joinToString(":")
        val existingHistoryKeys = database.timelineDao().historySnapshot().mapTo(mutableSetOf()) { it.restoreKey() }
        val historyToInsert = history.distinctBy { it.restoreKey() }.filter { existingHistoryKeys.add(it.restoreKey()) }
        database.withTransaction {
            if (media.isNotEmpty()) database.mediaDao().upsertMedia(media.distinctBy { "${it.mediaType}:${it.tmdbId}" })
            if (states.isNotEmpty()) database.stateDao().upsertAll(states.distinctBy { "${it.mediaType}:${it.mediaId}" })
            if (historyToInsert.isNotEmpty()) database.timelineDao().insertHistoryItems(historyToInsert)
            if (files.containsKey("data/progress.jsonl")) {
                database.timelineDao().clearPlayback()
                if (playback.isNotEmpty()) database.timelineDao().upsertPlayback(playback)
            }
            database.upNextDao().clear()
            database.syncDao().delete("up_next_cache_v1")
            rebuildLibraryRailInTransaction()
        }
        settings?.let { saved ->
            saved["uiAccent"]?.jsonPrimitive?.contentOrNull?.let { setUiAccent(it) }
            saved["metadataLanguage"]?.jsonPrimitive?.contentOrNull?.let { setMetadataLanguage(it) }
            saved["metadataRegion"]?.jsonPrimitive?.contentOrNull?.let { setMetadataRegion(it) }
            saved["metadataTimezone"]?.jsonPrimitive?.contentOrNull?.let { setMetadataTimezone(it) }
            saved["backgroundSync"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()?.let { preferences.setBackgroundSync(it) }
            saved["wifiOnly"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()?.let { preferences.setWifiOnly(it) }
            saved["notificationEpisodes"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()?.let { preferences.setNotification("episodes", it) }
            saved["notificationMovies"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()?.let { preferences.setNotification("movies", it) }
            saved["notificationSync"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()?.let { preferences.setNotification("sync", it) }
            val quietEnabled = saved["quietHoursEnabled"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()
            if (quietEnabled != null) {
                preferences.setQuietHours(
                    quietEnabled,
                    saved["quietHoursStart"]?.jsonPrimitive?.contentOrNull?.toIntOrNull(),
                    saved["quietHoursEnd"]?.jsonPrimitive?.contentOrNull?.toIntOrNull(),
                )
            }
            saved["contentRegions"]?.jsonPrimitive?.contentOrNull?.split(',')?.filter(String::isNotBlank)?.toSet()
                ?.let { preferences.setContentRegions(it) }
            saved["excludeSpecials"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()?.let { preferences.setExcludeSpecials(it) }
            saved["preferredProviders"]?.jsonPrimitive?.contentOrNull?.split('|')?.filter(String::isNotBlank)?.toSet()
                ?.let { preferences.setPreferredProviders(it) }
            saved["providerRegion"]?.jsonPrimitive?.contentOrNull?.let { preferences.setProviderRegion(it) }
            saved["visibleProviderTypes"]?.jsonPrimitive?.contentOrNull?.split('|')?.filter(String::isNotBlank)?.toSet()
                ?.let { preferences.setVisibleProviderTypes(it) }
            saved["heroLayout"]?.jsonPrimitive?.contentOrNull?.let { preferences.setHeroLayout(it) }
            saved["posterFormat"]?.jsonPrimitive?.contentOrNull?.let { preferences.setPosterFormat(it) }
            saved["posterSize"]?.jsonPrimitive?.contentOrNull?.let { preferences.setPosterSize(it) }
            saved["cardDensity"]?.jsonPrimitive?.contentOrNull?.let { preferences.setCardDensity(it) }
            saved["hiddenDiscovery"]?.jsonPrimitive?.contentOrNull?.split('|')?.filter(String::isNotBlank)?.toSet()
                ?.let { preferences.setHiddenDiscovery(it) }
            val sources = saved["ratingSources"]?.jsonPrimitive?.contentOrNull?.split(',')?.filter(String::isNotBlank)?.toSet()
            if (sources != null) listOf("imdb", "tmdb", "metacritic", "tomatoes").forEach { source ->
                preferences.setRatingSource(source, source in sources)
            }
        }
        scheduleAutomaticBackup()
        return states.size + historyToInsert.size
    }

    private fun String.withTrailingLine(): String = if (isBlank()) "" else trimEnd() + "\n"

    private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(toByteArray())
        .joinToString("") { byte -> "%02x".format(byte) }

    private fun csvRow(vararg values: Any): String = values.joinToString(",") { value ->
        val escaped = value.toString().replace("\"", "\"\"")
        "\"$escaped\""
    }

    private suspend fun rebuildLibraryRailInTransaction() {
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

    private fun TmdbMediaDto.toEntity(type: MediaType) = MediaEntity(
        mediaType = type.name,
        tmdbId = id,
        title = title ?: name ?: "Untitled",
        overview = overview,
        posterPath = posterPath,
        backdropPath = backdropPath,
        releaseDate = releaseDate ?: firstAirDate,
        score = voteAverage,
        runtimeMinutes = runtime ?: episodeRunTime.firstOrNull(),
        genres = if (genres.isNotEmpty()) genres.joinToString("|") { it.name }
        else genreIds.mapNotNull(::tmdbGenreName).joinToString("|"),
        providers = watchProviders?.results?.get("IT")?.let { (it.flatrate + it.rent + it.buy).distinctBy { p -> p.id }.joinToString("|") { p -> p.name } }.orEmpty(),
        collectionId = collection?.id,
    )

    private fun tmdbGenreName(id: Int): String? = when (id) {
        12 -> "Adventure"
        14 -> "Fantasy"
        16 -> "Animation"
        18 -> "Drama"
        27 -> "Horror"
        28 -> "Action"
        35 -> "Comedy"
        36 -> "History"
        37 -> "Western"
        53 -> "Thriller"
        80 -> "Crime"
        99 -> "Documentary"
        878 -> "Science Fiction"
        9648 -> "Mystery"
        10402 -> "Music"
        10749 -> "Romance"
        10751 -> "Family"
        10752 -> "War"
        10759 -> "Action & Adventure"
        10762 -> "Kids"
        10763 -> "News"
        10764 -> "Reality"
        10765 -> "Sci-Fi & Fantasy"
        10766 -> "Soap"
        10767 -> "Talk"
        10768 -> "War & Politics"
        else -> null
    }

    private data class ResolvedSimklItem(
        val item: SimklLibraryItem,
        val type: MediaType,
        val tmdbId: Int,
    )

    private fun mergeSimklItems(items: List<SimklLibraryItem>): List<SimklLibraryItem> =
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

    private suspend fun resolveSimklItems(items: List<SimklLibraryItem>, type: MediaType): List<ResolvedSimklItem> {
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

    private fun SimklLibraryItem.toState(type: MediaType, resolvedTmdbId: Int? = null): UserMediaStateEntity? {
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
            } ?: System.currentTimeMillis(),
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
        stillUrl = stillPath?.let { "https://image.tmdb.org/t/p/w780$it" },
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

    private fun String.fromCineTrackStatus(): LibraryStatus =
        runCatching { LibraryStatus.valueOf(this) }.getOrDefault(LibraryStatus.NONE)
}

