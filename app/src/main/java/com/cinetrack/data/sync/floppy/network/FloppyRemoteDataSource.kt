package com.cinetrack.data.sync.floppy.network

import com.cinetrack.data.sync.ConnectionResult
import com.cinetrack.data.sync.MediaIds
import com.cinetrack.data.sync.ProviderPushResult
import com.cinetrack.data.sync.SyncOperation
import com.cinetrack.data.sync.SyncOperationType
import com.cinetrack.data.sync.TrackedEpisodeState
import com.cinetrack.data.sync.TrackedMovieState
import com.cinetrack.data.sync.TrackedShowState
import com.cinetrack.data.sync.TrackingSnapshot
import com.cinetrack.data.sync.TrackingSyncError
import com.cinetrack.data.sync.TrackingProviderId
import com.cinetrack.data.sync.MovieHistoryMutationContext
import com.cinetrack.data.sync.floppy.FloppyCapabilities
import com.cinetrack.data.sync.floppy.FloppyConnectionSettings
import com.cinetrack.data.sync.floppy.FloppyConsumption
import com.cinetrack.data.sync.floppy.FloppyConsumptionResolver
import com.cinetrack.data.sync.floppy.FloppyEpisodeWatchRequest
import com.cinetrack.data.sync.floppy.FloppyMovieWatchRequest
import com.cinetrack.data.sync.floppy.FloppyBootstrapMovie
import com.cinetrack.data.sync.floppy.FloppyBootstrapMovieWatch
import com.cinetrack.data.sync.floppy.FloppyBootstrapMoviesRequest
import com.cinetrack.data.sync.floppy.FloppyBootstrapShow
import com.cinetrack.data.sync.floppy.FloppyBootstrapShowsRequest
import com.cinetrack.data.sync.floppy.FloppyEpisodeBulkRequest
import com.cinetrack.data.sync.floppy.FloppyEpisodeEnsureEvent
import com.cinetrack.data.sync.floppy.FloppyEpisodeEnsureRequest
import com.cinetrack.data.sync.floppy.FloppyInfoDto
import com.cinetrack.data.sync.floppy.FloppyMediaDetail
import com.cinetrack.data.sync.floppy.FloppySession
import com.cinetrack.data.sync.floppy.FloppyTrackMediaRequest
import com.cinetrack.data.sync.floppy.FloppyTrackedMedia
import com.cinetrack.data.sync.floppy.FloppyTrackedMediaUpdateRequest
import com.cinetrack.data.sync.floppy.FloppyVerificationProjection
import com.cinetrack.domain.LibraryStatus
import com.cinetrack.domain.MediaType
import com.cinetrack.domain.FloppyConnectionStage
import java.time.Instant
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.Collections
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException
import kotlinx.coroutines.delay

private const val BULK_EPISODE_MAX = 50
private const val BULK_TASK_POLL_ATTEMPTS = 120
private const val BULK_TASK_POLL_DELAY_MS = 500L

/** A small, run-scoped cache used only by the managed bootstrap worker.  It is
 * deliberately not persisted: the durable bootstrap plan remains the source
 * of truth and a retry reconstructs this cache from Floppy. */
class FloppyBootstrapTransportContext internal constructor(
    val providerInstanceId: String,
    val canEnsureEpisodeEvents: Boolean = false,
    val canBootstrapV2: Boolean = false,
) {
    internal val watchedEpisodeIndex = mutableSetOf<EpisodeKey>()
    internal var episodeHistoryLoaded: Boolean = false
    // Typed movie media is prepared once per worker attempt.  Unlike Floppy's
    // flat HistoryView, this endpoint is paginated before the server builds a
    // response and includes the consumption id needed for active-state cleanup.
    internal val watchedMovieIndex = ConcurrentHashMap<Long, MutableSet<Instant>>()
    internal val activeMovieConsumptions = ConcurrentHashMap<Long, FloppyConsumption>()
    internal var movieHistoryLoaded: Boolean = false
    internal var moviePreparationUnavailable: Boolean = false
    internal val mediaDetailCache = Collections.synchronizedMap(mutableMapOf<String, FloppyMediaDetail?>())
    internal val historyCache = ConcurrentHashMap<String, List<FloppyConsumption>>()
}

/** Transport boundary for one Floppy bootstrap worker attempt. */
class FloppyBootstrapTransportSession internal constructor(
    private val remote: FloppyRemoteDataSource,
    private val session: FloppySession,
    val context: FloppyBootstrapTransportContext,
    private val ensureCurrent: suspend () -> Boolean = { true },
) {
    val canEnsureEpisodeEvents: Boolean get() = context.canEnsureEpisodeEvents
    suspend fun prepare(
        operations: List<SyncOperation>,
        onRemoteProgress: suspend (itemsLoaded: Int, totalItems: Int) -> Unit = { _, _ -> },
    ): Int {
        check(ensureCurrent()) { "Floppy connection changed before bootstrap preparation" }
        val result = remote.prepareBootstrap(session, operations, context, onRemoteProgress)
        check(ensureCurrent()) { "Floppy connection changed during bootstrap preparation" }
        return result
    }
    suspend fun push(operations: List<SyncOperation>): ProviderPushResult {
        check(ensureCurrent()) { "Floppy connection changed before bootstrap delivery" }
        val result = remote.push(session, operations, context)
        check(ensureCurrent()) { "Floppy connection changed during bootstrap delivery" }
        return result
    }
}

/** The only class that knows Floppy endpoint paths, DTOs, and retry policy. */
class FloppyRemoteDataSource(
    private val factory: FloppyApiClientFactory,
    private val resolver: FloppyConsumptionResolver = FloppyConsumptionResolver(),
) {
    fun invalidateClient(baseUrl: String, apiKey: String? = null) {
        factory.invalidate(baseUrl, apiKey)
    }

    fun clearClients() {
        factory.clear()
    }

    suspend fun inspect(baseUrl: String): FloppyInfoDto = try {
        factory.get(baseUrl, null).info()
    } catch (error: Throwable) {
        throw FloppyApiErrorMapper.map(error)
    }

    suspend fun connect(
        baseUrl: String,
        apiKey: String,
        allowInsecureLocalHttp: Boolean = false,
        onStage: (FloppyConnectionStage, String?) -> Unit = { _, _ -> },
    ): FloppyConnectionSettings = try {
        val identity = FloppyUrlNormalizer.normalize(baseUrl, allowInsecureLocalHttp)
        val api = factory.get(identity.baseUrl, apiKey, allowInsecureLocalHttp)
        val info = api.info()
        onStage(FloppyConnectionStage.AUTHENTICATING, info.version)
        val preferences = api.preferences()
        val account = preferences["username"]?.jsonPrimitive?.contentOrNull
            ?: preferences["user_name"]?.jsonPrimitive?.contentOrNull
            ?: preferences["user"]?.jsonPrimitive?.contentOrNull
        // Do not infer MAIN/read-complete capabilities from one harmless GET.
        val capabilities = FloppyCapabilities(
            canReadLibrary = true,
            canWriteLibrary = true,
            canReadHistory = true,
            canWriteMovieHistory = true,
            canWriteEpisodeHistory = true,
            canRemoveHistory = true,
            canReadCompleteSnapshot = false,
            canEnsureEpisodeEvents = info.apiExtensions?.cinetrackEpisodeEventsV1 == true,
            canBootstrapV2 = info.apiExtensions?.cinetrackBootstrapV2 == true,
        )
        FloppyConnectionSettings(
            baseUrl = identity.baseUrl,
            serverVersion = info.version,
            serverIdentity = identity.baseUrl,
            accountIdentity = account,
            capabilities = capabilities,
            connectedAt = System.currentTimeMillis(),
            allowInsecureLocalHttp = allowInsecureLocalHttp,
        )
    } catch (error: Throwable) {
        throw FloppyApiErrorMapper.map(error)
    }

    suspend fun test(baseUrl: String, apiKey: String?, allowInsecureLocalHttp: Boolean = false): ConnectionResult = try {
        val identity = FloppyUrlNormalizer.normalize(baseUrl, allowInsecureLocalHttp)
        val api = factory.get(identity.baseUrl, apiKey, allowInsecureLocalHttp)
        api.info()
        if (apiKey.isNullOrBlank()) ConnectionResult.AuthenticationRequired
        else {
            api.preferences()
            ConnectionResult.Connected
        }
    } catch (error: Throwable) {
        val mapped = FloppyApiErrorMapper.map(error)
        if (mapped is TrackingSyncError.AuthenticationRequired) ConnectionResult.AuthenticationRequired
        else ConnectionResult.Failed(mapped)
    }

    /** Compatibility overload; production calls use one immutable session. */
    suspend fun push(
        baseUrl: String,
        apiKey: String,
        operations: List<SyncOperation>,
        allowInsecureLocalHttp: Boolean = false,
    ): ProviderPushResult = push(session(baseUrl, apiKey, allowInsecureLocalHttp), operations)

    suspend fun push(session: FloppySession, operations: List<SyncOperation>): ProviderPushResult {
        return push(session, operations, null)
    }

    /** Managed bootstrap overload. The context is shared by every bounded
     * batch in one worker run, preventing typed media pagination from being
     * repeated for each batch. */
    internal suspend fun push(
        session: FloppySession,
        operations: List<SyncOperation>,
        bootstrapContext: FloppyBootstrapTransportContext?,
    ): ProviderPushResult {
        if (operations.isEmpty()) return ProviderPushResult(emptySet())
        val api = factory.get(session.baseUrl, session.apiKey, session.allowInsecureLocalHttp)
        val completed = linkedSetOf<String>()
        try {
            if (bootstrapContext?.canBootstrapV2 == true) {
                return pushBootstrapV2(api, operations, bootstrapContext, session.instanceId)
            }
            val episodeIndex = bootstrapContext?.watchedEpisodeIndex
                ?: if (operations.any { it.type == SyncOperationType.EPISODE_WATCHED }) {
                    loadEpisodeIndex(api)
                } else null

            // Bootstrap episode writes use the Floppy bulk endpoint when
            // available. Runs are contiguous and show-scoped so a range cannot
            // accidentally include an unrelated show or an unplanned episode.
            val episodeOperations = operations.filter { it.type == SyncOperationType.EPISODE_WATCHED }
            // Bulk transport is deliberately restricted to managed bootstrap.
            // Realtime/delta pushes keep their existing singular semantics.
            val bulkCompleted = if (bootstrapContext != null) {
                pushEpisodeBatches(api, episodeOperations, episodeIndex, bootstrapContext.canEnsureEpisodeEvents, session.instanceId)
            } else {
                emptySet()
            }
            completed += bulkCompleted

            val byMovieGeneration = operations
                .filter { it.mediaType == MediaType.MOVIE }
                .groupBy { it.mediaId to it.sourceVersion }
            val consumed = bulkCompleted.toMutableSet()
            operations.forEach { operation ->
                if (operation.id in consumed) return@forEach
                val pair = byMovieGeneration[operation.mediaId to operation.sourceVersion].orEmpty()
                val library = pair.firstOrNull {
                    it.type == SyncOperationType.LIBRARY_STATUS && it.value == LibraryStatus.COMPLETED.name
                }
                val watched = pair.firstOrNull { it.type == SyncOperationType.MOVIE_WATCHED }
                if (library != null && watched != null) {
                    pushMovieCompleted(api, library, watched, "tmdb", watched.mediaId.toString(), bootstrapContext, session.instanceId)
                    completed += library.id
                    completed += watched.id
                    consumed += library.id
                    consumed += watched.id
                } else {
                    pushOne(api, operation, episodeIndex, bootstrapContext, session.instanceId)
                    completed += operation.id
                    consumed += operation.id
                }
            }
        } catch (error: Throwable) {
            throw FloppyApiErrorMapper.map(error)
        }
        return ProviderPushResult(completed)
    }

    /** Fork V2 owns idempotency, so its import requests never need the
     * run-scoped full movie/episode index.  The returned ids stay exact: only
     * operations included in confirmed server entries are acknowledged. */
    private suspend fun pushBootstrapV2(
        api: FloppyApi,
        operations: List<SyncOperation>,
        context: FloppyBootstrapTransportContext,
        providerInstanceId: String,
    ): ProviderPushResult {
        val completed = linkedSetOf<String>()
        val movies = operations.filter { it.mediaType == MediaType.MOVIE }
            .groupBy { it.mediaId to it.sourceVersion }
        if (movies.isNotEmpty()) {
            val entries = movies.values.mapNotNull { group ->
                val watched = group.firstOrNull { it.type == SyncOperationType.MOVIE_WATCHED }
                val library = group.firstOrNull { it.type == SyncOperationType.LIBRARY_STATUS }
                val anchor = watched ?: library ?: return@mapNotNull null
                val status = library?.value?.let { runCatching { LibraryStatus.valueOf(it) }.getOrNull()?.toFloppyStatus() }
                val watch = watched?.payload?.toInstantOrNull()?.let { instant ->
                    FloppyBootstrapMovieWatch(
                        watchedAt = instant.toString(),
                        clientEventId = "cinetrack:${providerInstanceId}:${watched.id}:${watched.sourceVersion}",
                    )
                }
                FloppyBootstrapMovie("tmdb", anchor.mediaId.toString(), anchor.title.takeIf(String::isNotBlank), status = status, watch = watch)
            }
            if (entries.isNotEmpty()) {
                val response = api.ensureBootstrapMovies(FloppyBootstrapMoviesRequest(entries))
                require(response.results.size == entries.size && response.results.all { it.status in setOf("created", "already_satisfied") }) {
                    "Floppy V2 movie response did not confirm every submitted movie"
                }
                completed += movies.values.flatten().map(SyncOperation::id)
            }
        }
        val shows = operations.filter { it.mediaType == MediaType.TV && it.type == SyncOperationType.LIBRARY_STATUS }
        if (shows.isNotEmpty()) {
            val entries = shows.map { operation ->
                FloppyBootstrapShow("tmdb", operation.mediaId.toString(), operation.title.takeIf(String::isNotBlank), status = operation.value?.let { runCatching { LibraryStatus.valueOf(it) }.getOrNull()?.toFloppyStatus() })
            }
            val response = api.ensureBootstrapShows(FloppyBootstrapShowsRequest(entries))
            require(response.results.size == entries.size && response.results.all { it.status in setOf("created", "already_satisfied") }) {
                "Floppy V2 show response did not confirm every submitted show"
            }
            completed += shows.map(SyncOperation::id)
        }
        val episodes = operations.filter { it.type == SyncOperationType.EPISODE_WATCHED }
        if (episodes.isNotEmpty()) completed += pushEpisodeBatches(api, episodes, null, true, providerInstanceId)
        val unsupported = operations.map(SyncOperation::id).toSet() - completed
        if (unsupported.isNotEmpty()) throw TrackingSyncError.UnsupportedOperation(TrackingProviderId.FLOPPY, operations.first { it.id in unsupported }.type)
        return ProviderPushResult(completed)
    }

    /**
     * Sends contiguous, same-show watched episodes through Floppy's
     * asynchronous bulk range endpoint. The server contract is range-level
     * (202 + task id), so a confirmed task acknowledges every child in that
     * range; an ambiguous/failed task acknowledges none. Older Floppy builds
     * that do not expose the route safely fall back to the existing
     * idempotent singular endpoint.
     */
    private suspend fun pushEpisodeBatches(
        api: FloppyApi,
        operations: List<SyncOperation>,
        episodeIndex: MutableSet<EpisodeKey>?,
        canEnsureEpisodeEvents: Boolean,
        providerInstanceId: String,
    ): Set<String> {
        if (operations.isEmpty()) return emptySet()
        val completed = linkedSetOf<String>()
        if (canEnsureEpisodeEvents) {
            operations.groupBy { it.mediaId }.toSortedMap().forEach { (showId, showOperations) ->
                showOperations.chunked(BULK_EPISODE_MAX).forEach { chunk ->
                    val events = chunk.map { operation ->
                        val (season, episode, watchedAt) = operation.episodeParts()
                        FloppyEpisodeEnsureEvent(
                            seasonNumber = season,
                            episodeNumber = episode,
                            watchedAt = requireNotNull(watchedAt) { "Episode bootstrap requires watchedAt" }.toString(),
                            clientEventId = episodeClientEventId(providerInstanceId, operation),
                        )
                    }
                    val response = api.ensureEpisodes(source = "tmdb", mediaId = showId.toString(), request = FloppyEpisodeEnsureRequest(events))
                    val accepted = response.results.filter { it.status == "created" || it.status == "already_satisfied" }
                    val expectedIds = events.mapTo(linkedSetOf()) { it.clientEventId }
                    require(accepted.mapTo(linkedSetOf()) { it.clientEventId } == expectedIds) {
                        "Floppy ensure response did not confirm every submitted event"
                    }
                    completed += chunk.map(SyncOperation::id)
                    chunk.forEach { operation ->
                        val (season, episode, _) = operation.episodeParts()
                        episodeIndex?.add(EpisodeKey(operation.mediaId.toLong(), season, episode))
                    }
                }
            }
            return completed
        }
        operations
            .groupBy { it.mediaId }
            .toSortedMap()
            .values
            .flatMap { showOperations -> contiguousEpisodeRuns(showOperations).flatMap { it.chunked(BULK_EPISODE_MAX) } }
            .forEach { run ->
                val pending = run.filter { operation ->
                    val (season, episode, _) = operation.episodeParts()
                    episodeIndex?.contains(EpisodeKey(operation.mediaId.toLong(), season, episode)) != true
                }
                if (pending.isEmpty()) {
                    completed += run.map(SyncOperation::id)
                    return@forEach
                }
                // Only a route-level 404/405 means the deployed server lacks
                // bulk support. Once a task has been accepted, status failures
                // must propagate so the range is retried atomically; falling
                // back after acceptance could duplicate the remote write.
                val first = pending.first().episodeParts()
                val last = pending.last().episodeParts()
                val dates = pending.mapNotNull { it.episodeParts().third?.toString() }
                val task = try {
                    api.bulkEpisodes(
                        source = "tmdb",
                        mediaId = pending.first().mediaId.toString(),
                        request = FloppyEpisodeBulkRequest(
                            firstSeasonNumber = first.first,
                            firstEpisodeNumber = first.second,
                            lastSeasonNumber = last.first,
                            lastEpisodeNumber = last.second,
                            startDate = dates.minOrNull() ?: Instant.EPOCH.toString(),
                            endDate = dates.maxOrNull() ?: Instant.now().toString(),
                        ),
                    )
                } catch (error: HttpException) {
                    if (error.code() == 404 || error.code() == 405) {
                        null
                    } else if (error.code() == 409) {
                        // Submission was rejected before a task id existed:
                        // nothing in this range is remotely confirmed. Keep all
                        // children durable and retryable, while surfacing only
                        // safe endpoint/range diagnostics.
                        val code = FloppyApiErrorMapper.code(error) ?: "unspecified"
                        throw TrackingSyncError.Conflict(
                            "Floppy bulk episode conflict: show=${pending.first().mediaId}, " +
                                "season=${first.first}, episodes=${first.second}-${last.second}, code=$code",
                        )
                    } else throw error
                }
                val usedBulk = if (task == null) {
                    false
                } else {
                    val taskId = task.taskId
                        ?: throw TrackingSyncError.InvalidRemoteData("Floppy bulk response did not include a task id")
                    awaitBulkTask(
                        api,
                        taskId,
                        "show=${pending.first().mediaId}, season=${first.first}, episodes=${first.second}-${last.second}",
                    )
                    true
                }
                if (usedBulk) {
                    completed += pending.map(SyncOperation::id)
                    pending.forEach {
                        val (season, episode, _) = it.episodeParts()
                        episodeIndex?.add(EpisodeKey(it.mediaId.toLong(), season, episode))
                    }
                } else {
                    // Compatible legacy server: preserve exact watched_at
                    // values and idempotency, at the cost of singular calls.
                    pending.forEach { operation ->
                        pushEpisodeWatched(api, operation, "tmdb", operation.mediaId.toString(), episodeIndex)
                        completed += operation.id
                    }
                }
            }
        return completed
    }

    internal fun contiguousEpisodeRuns(operations: List<SyncOperation>): List<List<SyncOperation>> {
        val sorted = operations.sortedWith(compareBy<SyncOperation> { it.episodeParts().first }.thenBy { it.episodeParts().second })
        val runs = mutableListOf<MutableList<SyncOperation>>()
        sorted.forEach { operation ->
            val current = runs.lastOrNull()
            val previous = current?.lastOrNull()
            val parts = operation.episodeParts()
            val previousParts = previous?.episodeParts()
            // Floppy bulk payloads are inclusive ranges. Crossing a season
            // boundary can therefore synthesize every unplanned episode left
            // in the old season. Keep each range single-season; fifty
            // episodes per request retains the transport benefit safely.
            val contiguous = previous != null &&
                operation.mediaId == previous.mediaId &&
                previousParts != null &&
                parts.first == previousParts.first &&
                parts.second == previousParts.second + 1
            if (current == null || !contiguous) runs += mutableListOf(operation) else current += operation
        }
        return runs
    }

    private suspend fun awaitBulkTask(api: FloppyApi, taskId: String, safeRange: String) {
        repeat(BULK_TASK_POLL_ATTEMPTS) {
            val status = try {
                api.taskStatus(taskId).status?.uppercase()
            } catch (error: HttpException) {
                if (error.code() == 409) {
                    val code = FloppyApiErrorMapper.code(error) ?: "unspecified"
                    throw TrackingSyncError.Conflict(
                        "Floppy bulk task conflict: task=$taskId, $safeRange, code=$code",
                    )
                }
                throw error
            }
            when (status) {
                "SUCCESS", "SUCCEEDED", "COMPLETED" -> return
                "FAILURE", "FAILED", "REVOKED" -> throw TrackingSyncError.InvalidRemoteData(
                    "Floppy bulk episode task failed: task=$taskId, $safeRange, state=$status",
                )
                else -> delay(BULK_TASK_POLL_DELAY_MS)
            }
        }
        throw TrackingSyncError.Timeout(IllegalStateException("Floppy bulk episode task timed out: task=$taskId, $safeRange"))
    }

    /**
     * Lazily prepares only the media types present in the current bootstrap
     * unit.  Flat HistoryView is deliberately not used here: v26.9.10 builds
     * the complete filtered history before applying its flat pagination.
     */
    internal suspend fun prepareBootstrap(
        session: FloppySession,
        operations: List<SyncOperation>,
        context: FloppyBootstrapTransportContext,
        onRemoteProgress: suspend (itemsLoaded: Int, totalItems: Int) -> Unit = { _, _ -> },
    ): Int {
        if (context.providerInstanceId != session.instanceId) {
            throw TrackingSyncError.ProviderUnavailable(TrackingProviderId.FLOPPY, IllegalStateException("Floppy instance changed"))
        }
        val api = factory.get(session.baseUrl, session.apiKey, session.allowInsecureLocalHttp)
        return try {
            if (!context.canEnsureEpisodeEvents && !context.episodeHistoryLoaded && operations.any { it.type == SyncOperationType.EPISODE_WATCHED }) {
                context.watchedEpisodeIndex += loadEpisodeIndex(api, onRemoteProgress)
                context.episodeHistoryLoaded = true
            }
            if (!context.movieHistoryLoaded && !context.moviePreparationUnavailable &&
                operations.any { it.mediaType == MediaType.MOVIE }
            ) {
                try {
                    loadMovieIndex(api, context, onRemoteProgress)
                    context.movieHistoryLoaded = true
                } catch (error: Throwable) {
                    val mapped = FloppyApiErrorMapper.map(error)
                    if (mapped.isTransientMoviePreparationFailure()) {
                        // Movie /watch/ calls are idempotent through external_id.
                        // Completed pairs safely fall back to targeted history.
                        context.moviePreparationUnavailable = true
                    } else {
                        throw mapped
                    }
                }
            }
            context.watchedEpisodeIndex.size + context.watchedMovieIndex.values.sumOf { it.size }
        } catch (error: Throwable) {
            throw FloppyApiErrorMapper.map(error)
        }
    }

    private suspend fun loadEpisodeIndex(
        api: FloppyApi,
        onRemoteProgress: suspend (itemsLoaded: Int, totalItems: Int) -> Unit = { _, _ -> },
    ): MutableSet<EpisodeKey> =
        paginate(api, "episode", onRemoteProgress).mapNotNull { row ->
            row.toEpisode()
                ?.takeIf(TrackedEpisodeState::watched)
                ?.let { EpisodeKey(it.showIds.tmdb ?: return@mapNotNull null, it.season, it.episode) }
        }.toMutableSet()

    private suspend fun loadMovieIndex(
        api: FloppyApi,
        context: FloppyBootstrapTransportContext,
        onRemoteProgress: suspend (itemsLoaded: Int, totalItems: Int) -> Unit = { _, _ -> },
    ) {
        var offset = 0
        var loaded = 0
        while (true) {
            val page = api.media("movie", limit = 200, offset = offset)
            loaded += page.results.size
            page.results.forEach { row ->
                val movieId = (row.item?.get("media_id")?.jsonPrimitive?.contentOrNull ?: row.itemId)
                    ?.toLongOrNull() ?: return@forEach
                val consumption = row.toConsumption() ?: return@forEach
                if (resolver.isCompleted(consumption)) {
                    consumption.endDate.toInstantOrNull()?.let { watchedAt ->
                        context.watchedMovieIndex.computeIfAbsent(movieId) { ConcurrentHashMap.newKeySet() }.add(watchedAt)
                    }
                } else if (resolver.isActive(consumption)) {
                    context.activeMovieConsumptions[movieId] = consumption
                }
            }
            onRemoteProgress(loaded, page.pagination.total)
            if (page.results.isEmpty() || page.pagination.next == null) break
            val nextOffset = offset + page.results.size
            check(nextOffset > offset) { "Floppy movie pagination did not advance" }
            offset = nextOffset
        }
    }


    suspend fun snapshot(
        baseUrl: String,
        apiKey: String,
        allowInsecureLocalHttp: Boolean = false,
    ): TrackingSnapshot = snapshot(session(baseUrl, apiKey, allowInsecureLocalHttp))

    suspend fun snapshot(session: FloppySession): TrackingSnapshot {
        val api = factory.get(session.baseUrl, session.apiKey, session.allowInsecureLocalHttp)
        try {
            val movies = aggregateMovies(paginate(api, "movie"))
            val shows = aggregateShows(paginate(api, "tv"))
            val episodes = aggregateEpisodes(paginate(api, "episode"))
            // Collection rows do not prove complete absence/history semantics.
            return TrackingSnapshot(movies, shows, episodes, Instant.now(), completeHistory = false)
        } catch (error: Throwable) {
            throw FloppyApiErrorMapper.map(error)
        }
    }

    /** Fetches Floppy's active consumptions and completed history without
     * collapsing the two semantic dimensions into TrackingSnapshot. */
    suspend fun verificationProjection(session: FloppySession): FloppyVerificationProjection {
        val api = factory.get(session.baseUrl, session.apiKey, session.allowInsecureLocalHttp)
        try {
            val movies = paginate(api, "movie").groupBy { (it.item?.get("media_id")?.jsonPrimitive?.contentOrNull ?: it.itemId) ?: "" }
                .mapNotNull { (id, rows) -> id.toLongOrNull()?.let { tmdb ->
                    val consumptions = rows.mapNotNull { it.toConsumption() }
                    val resolved = resolver.resolve(consumptions)
                    tmdb to FloppyVerificationProjection.Movie(
                        resolved.active?.status.toLibraryStatus(),
                        resolved.completed.mapNotNull { it.endDate.toInstantOrNull() }.toSet(),
                        resolved.completed.size,
                    )
                } }.toMap()
            val shows = paginate(api, "tv").groupBy { (it.item?.get("media_id")?.jsonPrimitive?.contentOrNull ?: it.itemId) ?: "" }
                .mapNotNull { (id, rows) -> id.toLongOrNull()?.let { tmdb ->
                    val consumptions = rows.mapNotNull { it.toConsumption() }
                    val resolved = resolver.resolve(consumptions)
                    tmdb to FloppyVerificationProjection.Show(
                        resolved.active?.status.toLibraryStatus(), resolved.completed.size,
                    )
                } }.toMap()
            val episodes = paginate(api, "episode").mapNotNull { row ->
                row.toEpisode()?.let { state ->
                    FloppyVerificationProjection.Episode(
                        state.showIds.tmdb ?: return@let null,
                        state.season,
                        state.episode,
                        state.watched,
                        state.watchedAt,
                    )
                }
            }.toSet()
            return FloppyVerificationProjection(movies, shows, episodes)
        } catch (error: Throwable) {
            throw FloppyApiErrorMapper.map(error)
        }
    }

    private suspend fun pushOne(
        api: FloppyApi,
        operation: SyncOperation,
        episodeIndex: MutableSet<EpisodeKey>? = null,
        bootstrapContext: FloppyBootstrapTransportContext? = null,
        providerInstanceId: String,
    ) {
        val source = "tmdb"
        val mediaId = operation.mediaId.toString()
        when (operation.type) {
            SyncOperationType.LIBRARY_STATUS -> pushLibrary(api, operation, source, mediaId, bootstrapContext)
            SyncOperationType.MOVIE_WATCHED -> pushMovieWatched(api, operation, source, mediaId, bootstrapContext, providerInstanceId)
            SyncOperationType.MOVIE_UNWATCHED -> removeExactMovieHistory(api, operation, source, mediaId, bootstrapContext)
            SyncOperationType.EPISODE_WATCHED -> pushEpisodeWatched(api, operation, source, mediaId, episodeIndex)
            SyncOperationType.EPISODE_UNWATCHED -> pushEpisodeDrop(api, operation, source, mediaId)
            SyncOperationType.MEDIA_HISTORY_REMOVE -> removeExactMovieHistory(api, operation, source, mediaId, bootstrapContext)
            SyncOperationType.SET_RATING -> throw TrackingSyncError.UnsupportedOperation(TrackingProviderId.FLOPPY, operation.type)
        }
    }

    private suspend fun pushLibrary(api: FloppyApi, operation: SyncOperation, source: String, mediaId: String, context: FloppyBootstrapTransportContext? = null) {
        val desired = operation.value?.let { runCatching { LibraryStatus.valueOf(it) }.getOrNull() }
            ?: throw TrackingSyncError.InvalidRemoteData("Missing Floppy library status")
        val detail = mediaDetailOrNull(api, operation.mediaType.floppyType(), source, mediaId, context)
        val active = resolver.resolve(detail?.consumptions.orEmpty()).active
        val latestCompleted = resolver.resolve(detail?.consumptions.orEmpty()).latestCompleted
        if (desired == LibraryStatus.NONE) {
            active?.let { deleteConsumptionSafely(api, operation.mediaType.floppyType(), source, mediaId, it.consumptionId) }
            invalidateCaches(context, operation.mediaType.floppyType(), source, mediaId)
            return
        }
        if (desired == LibraryStatus.COMPLETED) {
            // A standalone completed movie mutation has no trustworthy local
            // timestamp. Do not invent one or create a history row merely to
            // satisfy a legacy library-only operation; the paired
            // MOVIE_WATCHED operation owns movie completion. TV has no paired
            // movie timestamp and can use one idempotent status-3 entry.
            if (operation.mediaType == MediaType.MOVIE) return
            pushCompletedShow(api, operation, source, mediaId, detail, latestCompleted != null, active, context)
            invalidateCaches(context, operation.mediaType.floppyType(), source, mediaId)
            return
        }
        val targetStatus = desired.toFloppyStatus()
        if (active?.status == targetStatus) return
        if (active != null) {
            api.updateConsumption(
                operation.mediaType.floppyType(), source, mediaId, active.consumptionId,
                FloppyTrackedMediaUpdateRequest(status = targetStatus),
            )
        } else {
            api.track(operation.mediaType.floppyType(), FloppyTrackMediaRequest(source, mediaId, operation.title, status = targetStatus))
        }
        invalidateCaches(context, operation.mediaType.floppyType(), source, mediaId)
    }

    /** Projects a completed TV show without leaving a contradictory active
     * consumption behind. Each read/write step is idempotent so a timeout
     * after a committed POST or DELETE is safe to retry. Historical completed
     * rows are never deleted. */
    private suspend fun pushCompletedShow(
        api: FloppyApi,
        operation: SyncOperation,
        source: String,
        mediaId: String,
        initialDetail: com.cinetrack.data.sync.floppy.FloppyMediaDetail?,
        alreadyCompleted: Boolean,
        initialActive: FloppyConsumption?,
        context: FloppyBootstrapTransportContext? = null,
    ) {
        var detail = initialDetail
        var resolution = resolver.resolve(detail?.consumptions.orEmpty())
        if (!alreadyCompleted && resolution.completed.isEmpty()) {
            api.track("tv", FloppyTrackMediaRequest(source, mediaId, operation.title, status = 3))
            invalidateCaches(context, "tv", source, mediaId)
            // A show that had an active consumption needs a reload to locate
            // that row after the new completion is committed. If it had no
            // active row, the POST itself satisfies the desired projection and
            // no extra request is needed.
            if (initialActive != null) {
                detail = mediaDetailOrNull(api, "tv", source, mediaId, context)
                resolution = resolver.resolve(detail?.consumptions.orEmpty())
            }
        }
        // Use the freshly reloaded active row when available. The initial
        // value is only a defensive fallback for servers that return 404 on a
        // follow-up detail request after a successful completion POST.
        val active = resolution.active ?: initialActive
        active?.let { deleteConsumptionSafely(api, "tv", source, mediaId, it.consumptionId) }
        if (active != null) {
            invalidateCaches(context, "tv", source, mediaId)
            val remaining = mediaDetailOrNull(api, "tv", source, mediaId, context)
            check(resolver.resolve(remaining?.consumptions.orEmpty()).active == null) {
                "Floppy retained an active TV consumption after completion"
            }
        }
    }

    private fun invalidateCaches(context: FloppyBootstrapTransportContext?, mediaType: String, source: String, mediaId: String) {
        val key = "$mediaType:$source:$mediaId"
        context?.mediaDetailCache?.remove(key)
        context?.historyCache?.remove(key)
    }

    private suspend fun pushMovieWatched(
        api: FloppyApi,
        operation: SyncOperation,
        source: String,
        mediaId: String,
        context: FloppyBootstrapTransportContext? = null,
        providerInstanceId: String,
    ) {
        require(operation.mediaType == MediaType.MOVIE) { "Movie watched operation must target a movie" }
        val watchedAt = operation.payload.toInstantOrNull()
            ?: throw TrackingSyncError.InvalidRemoteData("Movie watched operation has no timestamp payload")
        val movieId = mediaId.toLongOrNull()
        if (movieId != null && context?.watchedMovieIndex?.get(movieId)?.contains(watchedAt) == true) return
        // v26.9.10's dedicated endpoint returns 201 on creation and 200 for
        // the same external id. The identity is stable across retries.
        api.watchMovie(
            source = source,
            mediaId = mediaId,
            request = FloppyMovieWatchRequest(
                endDate = watchedAt.toString(),
                externalId = "cinetrack:${context?.providerInstanceId ?: providerInstanceId}:${operation.id}:${operation.sourceVersion}",
            ),
        )
        if (movieId != null) context?.watchedMovieIndex?.computeIfAbsent(movieId) { ConcurrentHashMap.newKeySet() }?.add(watchedAt)
        invalidateCaches(context, "movie", source, mediaId)
    }

    /** Coupled completion effect: establish the exact play, then remove only
     * the currently active consumption.  Both steps are independently
     * idempotent so an ambiguous timeout is safe to retry. */
    private suspend fun pushMovieCompleted(
        api: FloppyApi,
        library: SyncOperation,
        watched: SyncOperation,
        source: String,
        mediaId: String,
        context: FloppyBootstrapTransportContext? = null,
        providerInstanceId: String,
    ) {
        val watchedAt = watched.payload.toInstantOrNull()
            ?: throw TrackingSyncError.InvalidRemoteData("Movie watched operation has no timestamp payload")
        val movieId = mediaId.toLongOrNull()
        val preparedActive = movieId?.let { context?.activeMovieConsumptions?.get(it) }
        // Typed bootstrap state includes the active consumption id.  The
        // fallback is deliberately targeted history, never flat HistoryView.
        var history = if (context?.movieHistoryLoaded == true) {
            listOfNotNull(preparedActive)
        } else {
            loadHistory(api, "movie", source, mediaId, context)
        }
        val hadActiveBefore = resolver.resolve(history).active != null
        val exactHistoryAlreadyPresent = resolver.findExactWatch(history, watchedAt) != null
        if (!exactHistoryAlreadyPresent &&
            (movieId == null || context?.watchedMovieIndex?.get(movieId)?.contains(watchedAt) != true)
        ) {
            api.watchMovie(
                source = source,
                mediaId = mediaId,
                request = FloppyMovieWatchRequest(
                    endDate = watchedAt.toString(),
                    externalId = "cinetrack:${context?.providerInstanceId ?: providerInstanceId}:${watched.id}:${watched.sourceVersion}",
                ),
            )
            if (movieId != null) context?.watchedMovieIndex?.computeIfAbsent(movieId) { ConcurrentHashMap.newKeySet() }?.add(watchedAt)
            invalidateCaches(context, "movie", source, mediaId)
        }
        preparedActive?.let {
            deleteConsumptionSafely(api, "movie", source, mediaId, it.consumptionId)
            movieId?.let { context?.activeMovieConsumptions?.remove(it) }
            invalidateCaches(context, "movie", source, mediaId)
            return
        }
        // When there was no active consumption, the exact completion check is
        // sufficient and avoids an unnecessary round trip on retries.
        if (!hadActiveBefore) return
        history = loadHistory(api, "movie", source, mediaId, context)
        val active = resolver.resolve(history).active
        active?.let { deleteConsumptionSafely(api, "movie", source, mediaId, it.consumptionId) }
        movieId?.let { context?.activeMovieConsumptions?.remove(it) }
        invalidateCaches(context, "movie", source, mediaId)
        val remaining = loadHistory(api, "movie", source, mediaId, context)
        check(resolver.resolve(remaining).active == null) {
            "Floppy retained an active movie consumption after completion"
        }
        invalidateCaches(context, "movie", source, mediaId)
    }

    private suspend fun removeExactMovieHistory(api: FloppyApi, operation: SyncOperation, source: String, mediaId: String, context: FloppyBootstrapTransportContext? = null) {
        require(operation.mediaType == MediaType.MOVIE) { "Movie history removal must target a movie" }
        val mutationContext = MovieHistoryMutationContext.parse(operation.payload)
        if (mutationContext?.previousWatched == false) return
        val watchedAt = mutationContext?.previousWatchedAt
            ?: operation.payload.toInstantOrNull()
            ?: return // Legacy LibraryStatus-only rows are handled conservatively.
        val history = loadHistory(api, "movie", source, mediaId, context)
        val intended = resolver.findExactWatch(history, watchedAt) ?: return
        deleteConsumptionSafely(api, "movie", source, mediaId, intended.consumptionId)
        invalidateCaches(context, "movie", source, mediaId)
    }

    private suspend fun pushEpisodeWatched(
        api: FloppyApi,
        operation: SyncOperation,
        source: String,
        mediaId: String,
        episodeIndex: MutableSet<EpisodeKey>? = null,
    ) {
        val (season, episode, watchedAt) = operation.episodeParts()
        val key = EpisodeKey(mediaId.toLongOrNull() ?: -1L, season, episode)
        if (episodeIndex?.contains(key) == true) return
        if (episodeIndex == null && loadEpisodeIndex(api).contains(key)) return
        api.watchEpisode("tv", source, mediaId, season, episode, FloppyEpisodeWatchRequest(watchedAt = watchedAt?.toString()))
        episodeIndex?.add(key)
    }

    private suspend fun pushEpisodeDrop(api: FloppyApi, operation: SyncOperation, source: String, mediaId: String) {
        val (season, episode, _) = operation.episodeParts()
        api.dropEpisode("tv", source, mediaId, season, episode)
    }

    private suspend fun loadHistory(api: FloppyApi, mediaType: String, source: String, mediaId: String, context: FloppyBootstrapTransportContext? = null): List<FloppyConsumption> {
        val key = "$mediaType:$source:$mediaId"
        context?.historyCache?.get(key)?.let { return it }
        val value = runCatching { paginateHistory(api, mediaType, source, mediaId) }.getOrElse { error ->
            if (error is HttpException && error.code() == 404) api.mediaDetail(mediaType, source, mediaId).consumptions
            else throw error
        }
        context?.historyCache?.set(key, value)
        return value
    }

    private suspend fun mediaDetailOrNull(
        api: FloppyApi,
        mediaType: String,
        source: String,
        mediaId: String,
        context: FloppyBootstrapTransportContext? = null,
    ): com.cinetrack.data.sync.floppy.FloppyMediaDetail? {
        val key = "$mediaType:$source:$mediaId"
        if (context?.mediaDetailCache?.containsKey(key) == true) return context.mediaDetailCache[key]
        return try {
            val value = api.mediaDetail(mediaType, source, mediaId)
            context?.mediaDetailCache?.set(key, value)
            value
        } catch (error: HttpException) {
            if (error.code() == 404) {
                context?.mediaDetailCache?.set(key, null)
                null
            } else throw error
        }
    }

    private suspend fun deleteConsumptionSafely(api: FloppyApi, mediaType: String, source: String, mediaId: String, consumptionId: Int) {
        try {
            api.deleteConsumption(mediaType, source, mediaId, consumptionId)
        } catch (error: HttpException) {
            if (error.code() != 404) throw error
        }
    }

    private suspend fun paginate(
        api: FloppyApi,
        type: String,
        onRemoteProgress: suspend (itemsLoaded: Int, totalItems: Int) -> Unit = { _, _ -> },
    ): List<FloppyTrackedMedia> {
        val all = mutableListOf<FloppyTrackedMedia>()
        var offset = 0
        while (true) {
            val page = api.media(type, limit = 200, offset = offset)
            all += page.results
            onRemoteProgress(all.size, page.pagination.total)
            if (page.results.isEmpty() || page.pagination.next == null) break
            val nextOffset = offset + page.results.size
            check(nextOffset > offset) { "Floppy $type pagination did not advance" }
            offset = nextOffset
        }
        return all
    }

    private suspend fun paginateHistory(api: FloppyApi, mediaType: String, source: String, mediaId: String): List<FloppyConsumption> {
        val all = mutableListOf<FloppyConsumption>()
        var offset = 0
        while (true) {
            val page = api.mediaHistory(mediaType, source, mediaId, limit = 200, offset = offset)
            all += page.results
            if (page.results.isEmpty() || page.pagination.next == null) break
            offset += page.results.size
        }
        return all
    }

    private fun aggregateMovies(rows: List<FloppyTrackedMedia>): List<TrackedMovieState> = rows
        .groupBy { (it.item?.get("media_id")?.jsonPrimitive?.contentOrNull ?: it.itemId) ?: "" }
        .mapNotNull { (id, grouped) ->
            val tmdb = id.toLongOrNull() ?: return@mapNotNull null
            val resolution = resolver.resolve(grouped.mapNotNull { it.toConsumption() })
            val active = resolution.active
            val completed = resolution.latestCompleted
            TrackedMovieState(
                MediaIds(tmdb = tmdb), active?.status.toLibraryStatus(), completed != null,
                completed?.endDate.toInstantOrNull(), active?.progressedAt.toInstantOrNull() ?: completed?.created.toInstantOrNull(),
            )
        }
        .sortedBy { it.ids.tmdb }

    private fun aggregateShows(rows: List<FloppyTrackedMedia>): List<TrackedShowState> = rows
        .groupBy { (it.item?.get("media_id")?.jsonPrimitive?.contentOrNull ?: it.itemId) ?: "" }
        .mapNotNull { (id, grouped) ->
            val tmdb = id.toLongOrNull() ?: return@mapNotNull null
            val active = resolver.resolve(grouped.mapNotNull { it.toConsumption() }).active
            TrackedShowState(MediaIds(tmdb = tmdb), active?.status.toLibraryStatus(), active?.progressedAt.toInstantOrNull())
        }
        .sortedBy { it.ids.tmdb }

    private fun aggregateEpisodes(rows: List<FloppyTrackedMedia>): List<TrackedEpisodeState> = rows.mapNotNull { it.toEpisode() }
        .sortedWith(compareBy({ it.showIds.tmdb }, { it.season }, { it.episode }))

    private fun FloppyTrackedMedia.toConsumption() = consumptionId?.let {
        FloppyConsumption(it, created = createdAt, score = score, progress = progress, progressedAt = progressedAt, status = status, startDate = startDate, endDate = endDate)
    }

    private fun session(baseUrl: String, apiKey: String, allowInsecureLocalHttp: Boolean = false): FloppySession {
        val identity = FloppyUrlNormalizer.normalize(baseUrl, allowInsecureLocalHttp)
        return FloppySession(
            instanceId = identity.baseUrl,
            baseUrl = identity.baseUrl,
            accountIdentity = null,
            credentialAlias = "floppy_api_key",
            apiKey = apiKey,
            capabilities = FloppyCapabilities(),
            serverVersion = null,
            allowInsecureLocalHttp = allowInsecureLocalHttp,
        )
    }
}

internal data class EpisodeKey(val showTmdbId: Long, val season: Int, val episode: Int)

private fun MediaType.floppyType() = when (this) {
    MediaType.MOVIE -> "movie"
    MediaType.TV -> "tv"
}

private fun LibraryStatus.toFloppyStatus() = when (this) {
    LibraryStatus.PLAN_TO_WATCH -> 0
    LibraryStatus.WATCHING -> 1
    LibraryStatus.PAUSED -> 2
    LibraryStatus.COMPLETED -> 3
    LibraryStatus.DROPPED -> 4
    LibraryStatus.NONE -> null
}

private fun FloppyTrackedMedia.toEpisode(): TrackedEpisodeState? {
    val id = (item?.get("media_id")?.jsonPrimitive?.contentOrNull ?: parentId ?: itemId)?.toLongOrNull() ?: return null
    val season = item?.get("season_number")?.jsonPrimitive?.intOrNull ?: return null
    val episode = item?.get("episode_number")?.jsonPrimitive?.intOrNull ?: return null
    return TrackedEpisodeState(MediaIds(tmdb = id), season, episode, endDate != null, endDate.toInstantOrNull(), createdAt.toInstantOrNull())
}

private fun Int?.toLibraryStatus(): LibraryStatus? = when (this) {
    0 -> LibraryStatus.PLAN_TO_WATCH
    1 -> LibraryStatus.WATCHING
    2 -> LibraryStatus.PAUSED
    3 -> LibraryStatus.COMPLETED
    4 -> LibraryStatus.DROPPED
    null -> null
    else -> throw TrackingSyncError.InvalidRemoteData("Unknown Floppy library status")
}

private fun SyncOperation.episodeParts(): Triple<Int, Int, Instant?> {
    val parts = payload?.split(':', limit = 3).orEmpty()
    val season = parts.getOrNull(0)?.toIntOrNull() ?: throw TrackingSyncError.InvalidRemoteData("Invalid Floppy season")
    val episode = parts.getOrNull(1)?.toIntOrNull() ?: throw TrackingSyncError.InvalidRemoteData("Invalid Floppy episode")
    return Triple(season, episode, parts.getOrNull(2).toInstantOrNull())
}

/** UUID v3 gives Floppy's durable UUID receipt a stable value per exact delivery. */
internal fun episodeClientEventId(providerInstanceId: String, operation: SyncOperation): String =
    UUID.nameUUIDFromBytes(
        "cinetrack:$providerInstanceId:${operation.id}:${operation.sourceVersion}".toByteArray(StandardCharsets.UTF_8),
    ).toString()

private fun String?.toInstantOrNull(): Instant? = this?.let { runCatching { Instant.parse(it) }.getOrNull() }

private fun TrackingSyncError.isTransientMoviePreparationFailure(): Boolean = when (this) {
    is TrackingSyncError.NetworkUnavailable,
    is TrackingSyncError.DnsFailure,
    is TrackingSyncError.Timeout,
    is TrackingSyncError.RateLimited,
    -> true
    is TrackingSyncError.ProviderUnavailable ->
        (cause as? HttpException)?.code() in 500..599
    else -> false
}

