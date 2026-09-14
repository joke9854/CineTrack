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
import com.cinetrack.data.sync.floppy.FloppyInfoDto
import com.cinetrack.data.sync.floppy.FloppySession
import com.cinetrack.data.sync.floppy.FloppyTrackMediaRequest
import com.cinetrack.data.sync.floppy.FloppyTrackedMedia
import com.cinetrack.data.sync.floppy.FloppyTrackedMediaUpdateRequest
import com.cinetrack.data.sync.floppy.FloppyVerificationProjection
import com.cinetrack.domain.LibraryStatus
import com.cinetrack.domain.MediaType
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException

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

    suspend fun connect(baseUrl: String, apiKey: String, allowInsecureLocalHttp: Boolean = false): FloppyConnectionSettings = try {
        val identity = FloppyUrlNormalizer.normalize(baseUrl, allowInsecureLocalHttp)
        val api = factory.get(identity.baseUrl, apiKey, allowInsecureLocalHttp)
        val info = api.info()
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
        )
        FloppyConnectionSettings(
            baseUrl = identity.baseUrl,
            serverVersion = info.version,
            serverIdentity = identity.baseUrl,
            accountIdentity = account,
            capabilities = capabilities,
            connectedAt = System.currentTimeMillis(),
            connectionId = UUID.randomUUID().toString(),
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
        if (operations.isEmpty()) return ProviderPushResult(emptySet())
        val api = factory.get(session.baseUrl, session.apiKey, session.allowInsecureLocalHttp)
        val completed = linkedSetOf<String>()
        try {
            val episodeIndex = if (operations.any { it.type == SyncOperationType.EPISODE_WATCHED }) {
                paginateEpisodeHistory(api).mapNotNull { entry ->
                    val show = entry.mediaId?.toLongOrNull() ?: return@mapNotNull null
                    val season = entry.season ?: return@mapNotNull null
                    val episode = entry.episode ?: return@mapNotNull null
                    if (entry.watched == true || entry.endDate != null) EpisodeKey(show, season, episode) else null
                }.toMutableSet()
            } else null
            val byMovieGeneration = operations
                .filter { it.mediaType == MediaType.MOVIE }
                .groupBy { it.mediaId to it.sourceVersion }
            val consumed = mutableSetOf<String>()
            operations.forEach { operation ->
                if (operation.id in consumed) return@forEach
                val pair = byMovieGeneration[operation.mediaId to operation.sourceVersion].orEmpty()
                val library = pair.firstOrNull {
                    it.type == SyncOperationType.LIBRARY_STATUS && it.value == LibraryStatus.COMPLETED.name
                }
                val watched = pair.firstOrNull { it.type == SyncOperationType.MOVIE_WATCHED }
                if (library != null && watched != null) {
                    pushMovieCompleted(api, library, watched, "tmdb", watched.mediaId.toString())
                    completed += library.id
                    completed += watched.id
                    consumed += library.id
                    consumed += watched.id
                } else {
                    pushOne(api, operation, episodeIndex)
                    completed += operation.id
                    consumed += operation.id
                }
            }
        } catch (error: Throwable) {
            throw FloppyApiErrorMapper.map(error)
        }
        return ProviderPushResult(completed)
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

    private suspend fun pushOne(api: FloppyApi, operation: SyncOperation, episodeIndex: MutableSet<EpisodeKey>? = null) {
        val source = "tmdb"
        val mediaId = operation.mediaId.toString()
        when (operation.type) {
            SyncOperationType.LIBRARY_STATUS -> pushLibrary(api, operation, source, mediaId)
            SyncOperationType.MOVIE_WATCHED -> pushMovieWatched(api, operation, source, mediaId)
            SyncOperationType.MOVIE_UNWATCHED -> removeExactMovieHistory(api, operation, source, mediaId)
            SyncOperationType.EPISODE_WATCHED -> pushEpisodeWatched(api, operation, source, mediaId, episodeIndex)
            SyncOperationType.EPISODE_UNWATCHED -> pushEpisodeDrop(api, operation, source, mediaId)
            SyncOperationType.MEDIA_HISTORY_REMOVE -> removeExactMovieHistory(api, operation, source, mediaId)
            SyncOperationType.SET_RATING -> throw TrackingSyncError.UnsupportedOperation(TrackingProviderId.FLOPPY, operation.type)
        }
    }

    private suspend fun pushLibrary(api: FloppyApi, operation: SyncOperation, source: String, mediaId: String) {
        val desired = operation.value?.let { runCatching { LibraryStatus.valueOf(it) }.getOrNull() }
            ?: throw TrackingSyncError.InvalidRemoteData("Missing Floppy library status")
        val detail = mediaDetailOrNull(api, operation.mediaType.floppyType(), source, mediaId)
        val active = resolver.resolve(detail?.consumptions.orEmpty()).active
        val latestCompleted = resolver.resolve(detail?.consumptions.orEmpty()).latestCompleted
        if (desired == LibraryStatus.NONE) {
            active?.let { deleteConsumptionSafely(api, operation.mediaType.floppyType(), source, mediaId, it.consumptionId) }
            return
        }
        if (desired == LibraryStatus.COMPLETED) {
            if (latestCompleted != null) return
            // A standalone completed movie mutation has no trustworthy local
            // timestamp. Do not invent one or create a history row merely to
            // satisfy a legacy library-only operation; the paired
            // MOVIE_WATCHED operation owns movie completion. TV has no paired
            // movie timestamp and can use one idempotent status-3 entry.
            if (operation.mediaType == MediaType.MOVIE) return
            api.track(
                operation.mediaType.floppyType(),
                FloppyTrackMediaRequest(source, mediaId, operation.title, status = 3),
            )
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
    }

    private suspend fun pushMovieWatched(api: FloppyApi, operation: SyncOperation, source: String, mediaId: String) {
        require(operation.mediaType == MediaType.MOVIE) { "Movie watched operation must target a movie" }
        val watchedAt = operation.payload.toInstantOrNull()
            ?: throw TrackingSyncError.InvalidRemoteData("Movie watched operation has no timestamp payload")
        val history = loadHistory(api, "movie", source, mediaId)
        if (resolver.findExactWatch(history, watchedAt) != null) return
        api.track("movie", FloppyTrackMediaRequest(source, mediaId, operation.title, status = 3, endDate = watchedAt.toString()))
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
    ) {
        val watchedAt = watched.payload.toInstantOrNull()
            ?: throw TrackingSyncError.InvalidRemoteData("Movie watched operation has no timestamp payload")
        var history = loadHistory(api, "movie", source, mediaId)
        val hadActiveBefore = resolver.resolve(history).active != null
        if (resolver.findExactWatch(history, watchedAt) == null) {
            api.track("movie", FloppyTrackMediaRequest(source, mediaId, watched.title, status = 3, endDate = watchedAt.toString()))
        }
        // When there was no active consumption, the exact completion check is
        // sufficient and avoids an unnecessary round trip on retries.
        if (!hadActiveBefore) return
        history = loadHistory(api, "movie", source, mediaId)
        val active = resolver.resolve(history).active
        active?.let { deleteConsumptionSafely(api, "movie", source, mediaId, it.consumptionId) }
        val remaining = loadHistory(api, "movie", source, mediaId)
        check(resolver.resolve(remaining).active == null) {
            "Floppy retained an active movie consumption after completion"
        }
    }

    private suspend fun removeExactMovieHistory(api: FloppyApi, operation: SyncOperation, source: String, mediaId: String) {
        require(operation.mediaType == MediaType.MOVIE) { "Movie history removal must target a movie" }
        val context = MovieHistoryMutationContext.parse(operation.payload)
        if (context?.previousWatched == false) return
        val watchedAt = context?.previousWatchedAt
            ?: operation.payload.toInstantOrNull()
            ?: return // Legacy LibraryStatus-only rows are handled conservatively.
        val history = loadHistory(api, "movie", source, mediaId)
        val intended = resolver.findExactWatch(history, watchedAt) ?: return
        deleteConsumptionSafely(api, "movie", source, mediaId, intended.consumptionId)
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
        if (episodeIndex == null && paginateEpisodeHistory(api).any {
                it.mediaType == "episode" && it.mediaId == mediaId && it.season == season && it.episode == episode && (it.watched == true || it.endDate != null)
            }) return
        api.watchEpisode("tv", source, mediaId, season, episode, FloppyEpisodeWatchRequest(watchedAt = watchedAt?.toString()))
        episodeIndex?.add(key)
    }

    private suspend fun pushEpisodeDrop(api: FloppyApi, operation: SyncOperation, source: String, mediaId: String) {
        val (season, episode, _) = operation.episodeParts()
        api.dropEpisode("tv", source, mediaId, season, episode)
    }

    private suspend fun loadHistory(api: FloppyApi, mediaType: String, source: String, mediaId: String): List<FloppyConsumption> =
        runCatching { paginateHistory(api, mediaType, source, mediaId) }.getOrElse { error ->
            if (error is HttpException && error.code() == 404) api.mediaDetail(mediaType, source, mediaId).consumptions
            else throw error
        }

    private suspend fun mediaDetailOrNull(
        api: FloppyApi,
        mediaType: String,
        source: String,
        mediaId: String,
    ): com.cinetrack.data.sync.floppy.FloppyMediaDetail? = try {
        api.mediaDetail(mediaType, source, mediaId)
    } catch (error: HttpException) {
        if (error.code() == 404) null else throw error
    }

    private suspend fun paginateEpisodeHistory(api: FloppyApi): List<com.cinetrack.data.sync.floppy.FloppyHistoryEntry> {
        val all = mutableListOf<com.cinetrack.data.sync.floppy.FloppyHistoryEntry>()
        var offset = 0
        while (true) {
            val page = try {
                api.history(flat = "1", limit = 200, offset = offset, types = "episode")
            } catch (error: HttpException) {
                if (error.code() == 404) return all else throw error
            }
            all += page.results
            if (page.results.isEmpty() || page.pagination.next == null) break
            offset += page.results.size
        }
        return all
    }

    private suspend fun deleteConsumptionSafely(api: FloppyApi, mediaType: String, source: String, mediaId: String, consumptionId: Int) {
        try {
            api.deleteConsumption(mediaType, source, mediaId, consumptionId)
        } catch (error: HttpException) {
            if (error.code() != 404) throw error
        }
    }

    private suspend fun paginate(api: FloppyApi, type: String): List<FloppyTrackedMedia> {
        val all = mutableListOf<FloppyTrackedMedia>()
        var offset = 0
        while (true) {
            val page = api.media(type, limit = 200, offset = offset)
            all += page.results
            if (page.results.isEmpty() || page.pagination.next == null) break
            offset += page.results.size
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

private data class EpisodeKey(val showTmdbId: Long, val season: Int, val episode: Int)

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

private fun String?.toInstantOrNull(): Instant? = this?.let { runCatching { Instant.parse(it) }.getOrNull() }

