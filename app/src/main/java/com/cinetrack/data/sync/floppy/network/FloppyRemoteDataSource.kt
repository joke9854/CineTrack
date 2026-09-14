package com.cinetrack.data.sync.floppy.network

import com.cinetrack.data.sync.ConnectionResult
import com.cinetrack.data.sync.MediaIds
import com.cinetrack.data.sync.ProviderPushResult
import com.cinetrack.data.sync.SyncOperation
import com.cinetrack.data.sync.SyncOperationType
import com.cinetrack.data.sync.TrackedEpisodeState
import com.cinetrack.data.sync.TrackedMovieState
import com.cinetrack.data.sync.TrackedShowState
import com.cinetrack.data.sync.TrackingCapability
import com.cinetrack.data.sync.TrackingProviderId
import com.cinetrack.data.sync.TrackingSnapshot
import com.cinetrack.data.sync.TrackingSyncError
import com.cinetrack.data.sync.floppy.FloppyCapabilities
import com.cinetrack.data.sync.floppy.FloppyConnectionSettings
import com.cinetrack.data.sync.floppy.FloppyHistoryEntry
import com.cinetrack.data.sync.floppy.FloppyInfoDto
import com.cinetrack.data.sync.floppy.FloppyTrackMediaRequest
import com.cinetrack.data.sync.floppy.FloppyTrackedMedia
import com.cinetrack.data.sync.floppy.FloppyTrackedMediaUpdateRequest
import com.cinetrack.data.sync.floppy.FloppyEpisodeWatchRequest
import com.cinetrack.domain.LibraryStatus
import com.cinetrack.domain.MediaType
import java.time.Instant
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/** The only class that knows Floppy endpoint paths and response shapes. */
class FloppyRemoteDataSource(private val factory: FloppyApiClientFactory) {
    suspend fun inspect(baseUrl: String): FloppyInfoDto = try {
        factory.get(baseUrl, null).info()
    } catch (error: Throwable) {
        throw FloppyApiErrorMapper.map(error)
    }

    suspend fun connect(baseUrl: String, apiKey: String): FloppyConnectionSettings = try {
        val identity = FloppyUrlNormalizer.normalize(baseUrl)
        val api = factory.get(identity.baseUrl, apiKey)
        val info = api.info()
        // This harmless authenticated GET is the documented connection check.
        val preferences = api.preferences()
        val account = preferences["username"]?.jsonPrimitive?.contentOrNull
            ?: preferences["user"]?.jsonPrimitive?.contentOrNull
        val capabilities = FloppyCapabilities(
            canReadLibrary = true,
            canWriteLibrary = true,
            canReadHistory = true,
            canWriteMovieHistory = true,
            canWriteEpisodeHistory = true,
            canRemoveHistory = true,
            // The reviewed contract provides complete, offset-paginated media
            // and flat history reads. It has no cursor/change-feed contract.
            canReadCompleteSnapshot = true,
        )
        FloppyConnectionSettings(
            baseUrl = identity.baseUrl,
            serverVersion = info.version,
            serverIdentity = identity.baseUrl + "|" + info.version,
            accountIdentity = account,
            capabilities = capabilities,
            connectedAt = System.currentTimeMillis(),
        )
    } catch (error: Throwable) {
        throw FloppyApiErrorMapper.map(error)
    }

    suspend fun test(baseUrl: String, apiKey: String?): ConnectionResult = try {
        val identity = FloppyUrlNormalizer.normalize(baseUrl)
        val api = factory.get(identity.baseUrl, apiKey)
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

    suspend fun push(baseUrl: String, apiKey: String, operations: List<SyncOperation>): ProviderPushResult {
        if (operations.isEmpty()) return ProviderPushResult(emptySet())
        val api = factory.get(FloppyUrlNormalizer.normalize(baseUrl).baseUrl, apiKey)
        val completed = linkedSetOf<String>()
        try {
            operations.forEach { operation ->
                pushOne(api, operation)
                completed += operation.id
            }
        } catch (error: Throwable) {
            // The coordinator keeps uncompleted operations durable. Do not
            // claim ACK for requests after an ambiguous/non-idempotent failure.
            throw FloppyApiErrorMapper.map(error)
        }
        return ProviderPushResult(completed)
    }

    suspend fun snapshot(baseUrl: String, apiKey: String): TrackingSnapshot {
        val api = factory.get(FloppyUrlNormalizer.normalize(baseUrl).baseUrl, apiKey)
        try {
            val movies = paginate(api, "movie").mapNotNull { it.toMovie() }
            val shows = paginate(api, "tv").mapNotNull { it.toShow() }
            val episodes = paginate(api, "episode").mapNotNull { it.toEpisode() }
            // The history endpoint is authoritative for timestamps and explicit
            // tombstones. Media rows remain the source for library membership.
            val history = paginateHistory(api)
            val historyByMovie = history.filter { it.mediaType == "movie" }.associateBy { "${it.source}:${it.mediaId}" }
            val mergedMovies = movies.map { movie ->
                val h = historyByMovie["tmdb:${movie.ids.tmdb}"] ?: historyByMovie.values.firstOrNull { it.mediaId == movie.ids.tmdb?.toString() }
                if (h == null) movie else movie.copy(watched = h.watched ?: movie.watched, watchedAt = h.watchedAt.toInstantOrNull() ?: movie.watchedAt)
            }
            val mergedEpisodes = episodes.map { episode ->
                val h = history.firstOrNull { it.mediaType == "episode" && it.mediaId == episode.showIds.tmdb?.toString() && it.season == episode.season && it.episode == episode.episode }
                if (h == null) episode else episode.copy(watched = h.watched ?: episode.watched, watchedAt = h.watchedAt.toInstantOrNull() ?: episode.watchedAt)
            }
            return TrackingSnapshot(mergedMovies, shows, mergedEpisodes, Instant.now(), completeHistory = true)
        } catch (error: Throwable) {
            throw FloppyApiErrorMapper.map(error)
        }
    }

    private suspend fun pushOne(api: FloppyApi, operation: SyncOperation) {
        val source = "tmdb"
        val mediaId = operation.mediaId.toString()
        when (operation.type) {
            SyncOperationType.LIBRARY_STATUS -> {
                val status = operation.value?.let { runCatching { LibraryStatus.valueOf(it) }.getOrNull() }
                    ?: throw TrackingSyncError.InvalidRemoteData("Missing Floppy library status")
                if (status == LibraryStatus.NONE) api.delete(operation.mediaType.floppyType(), source, mediaId)
                else api.track(operation.mediaType.floppyType(), FloppyTrackMediaRequest(source, mediaId, operation.title, status = status.toFloppyStatus()))
            }
            SyncOperationType.MOVIE_WATCHED -> {
                require(operation.mediaType == MediaType.MOVIE) { "Movie watched operation must target a movie" }
                val watchedAt = operation.payload.toInstantOrNull() ?: Instant.now()
                api.track("movie", FloppyTrackMediaRequest(source, mediaId, operation.title, status = 3, endDate = watchedAt.toString()))
            }
            SyncOperationType.MOVIE_UNWATCHED -> {
                require(operation.mediaType == MediaType.MOVIE) { "Movie unwatched operation must target a movie" }
                api.delete("movie", source, mediaId)
                // Delete removes the consumption and library row in Floppy. If
                // CineTrack persisted a desired library status, recreate only
                // that library state without coupling watched=true.
                operation.payload?.let { runCatching { LibraryStatus.valueOf(it) }.getOrNull() }
                    ?.takeIf { it != LibraryStatus.NONE }
                    ?.let { api.track("movie", FloppyTrackMediaRequest(source, mediaId, operation.title, status = it.toFloppyStatus())) }
            }
            SyncOperationType.EPISODE_WATCHED -> {
                val (season, episode, watchedAt) = operation.episodeParts()
                api.watchEpisode("tv", source, mediaId, season, episode, FloppyEpisodeWatchRequest(watchedAt = watchedAt?.toString()))
            }
            SyncOperationType.EPISODE_UNWATCHED -> {
                val (season, episode, _) = operation.episodeParts()
                api.deleteEpisode("tv", source, mediaId, season, episode)
            }
            SyncOperationType.MEDIA_HISTORY_REMOVE -> api.delete(operation.mediaType.floppyType(), source, mediaId)
            SyncOperationType.SET_RATING -> throw TrackingSyncError.UnsupportedOperation(TrackingProviderId.FLOPPY, operation.type)
        }
    }

    private suspend fun paginate(api: FloppyApi, type: String): List<FloppyTrackedMedia> {
        val all = mutableListOf<FloppyTrackedMedia>()
        var offset = 0
        while (true) {
            val page = api.media(type, limit = 200, offset = offset)
            all += page.results
            if (page.results.isEmpty() || page.pagination.next == null || page.results.size < 200) break
            offset += page.results.size
        }
        return all
    }

    private suspend fun paginateHistory(api: FloppyApi): List<FloppyHistoryEntry> {
        val all = mutableListOf<FloppyHistoryEntry>()
        var offset = 0
        while (true) {
            val page = api.history(limit = 200, offset = offset)
            all += page.results
            if (page.results.isEmpty() || page.pagination.next == null || page.results.size < 200) break
            offset += page.results.size
        }
        return all
    }
}

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

private fun FloppyTrackedMedia.toMovie(): TrackedMovieState? {
    val id = (item?.get("media_id")?.jsonPrimitive?.contentOrNull ?: itemId)?.toLongOrNull() ?: return null
    return TrackedMovieState(MediaIds(tmdb = id), libraryState = status.toLibraryStatus(), watched = endDate != null, watchedAt = endDate.toInstantOrNull(), updatedAt = createdAt.toInstantOrNull())
}

private fun FloppyTrackedMedia.toShow(): TrackedShowState? {
    val id = (item?.get("media_id")?.jsonPrimitive?.contentOrNull ?: itemId)?.toLongOrNull() ?: return null
    return TrackedShowState(MediaIds(tmdb = id), status.toLibraryStatus(), createdAt.toInstantOrNull())
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
