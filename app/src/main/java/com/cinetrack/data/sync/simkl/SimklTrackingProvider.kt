package com.cinetrack.data.sync.simkl

import com.cinetrack.data.remote.ApiServices
import com.cinetrack.data.remote.SimklEpisode
import com.cinetrack.data.remote.SimklIds
import com.cinetrack.data.remote.SimklSeason
import com.cinetrack.data.remote.SimklSyncItem
import com.cinetrack.data.remote.SimklSyncRequest
import com.cinetrack.data.repository.AppPreferences
import com.cinetrack.data.sync.ConnectionResult
import com.cinetrack.data.sync.ProviderPushResult
import com.cinetrack.data.sync.ProviderSyncOutcome
import com.cinetrack.data.sync.SyncOperation
import com.cinetrack.data.sync.SyncOperationType
import com.cinetrack.data.sync.TrackingCapabilities
import com.cinetrack.data.sync.TrackingProvider
import com.cinetrack.data.sync.TrackingProviderId
import com.cinetrack.data.sync.TrackingSyncError
import com.cinetrack.data.sync.TrackingSnapshot
import com.cinetrack.data.sync.TrackedMovieState
import com.cinetrack.data.sync.TrackedShowState
import com.cinetrack.data.sync.TrackedEpisodeState
import com.cinetrack.data.sync.MediaIds
import com.cinetrack.domain.LibraryStatus
import com.cinetrack.domain.MediaType
import com.cinetrack.domain.SyncProgress
import java.time.Instant
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** Simkl transport and mapping. Overall MAIN/SECONDARY policy lives in SyncCoordinator. */
class SimklTrackingProvider(
    private val services: ApiServices,
    private val preferences: AppPreferences,
    private val syncEngine: () -> SimklSyncEngine,
    private val normalizedSnapshot: (suspend () -> TrackingSnapshot)? = null,
    /** Simkl answered 429 (daily quota): schedule a retry after this many seconds, when known. */
    private val onRateLimited: (retryAfterSeconds: Long?) -> Unit = {},
) : TrackingProvider {
    override val id = TrackingProviderId.SIMKL
    override val capabilities = TrackingCapabilities(
        supportsMovies = true,
        supportsShows = true,
        supportsWatchHistory = true,
        supportsRatings = false,
        supportsLibrary = true,
        supportsTwoWaySync = true,
    )

    override suspend fun isAuthenticated(): Boolean = !preferences.tokenNow().isNullOrBlank()

    override suspend fun push(operations: List<SyncOperation>): ProviderPushResult {
        if (!isAuthenticated()) throw TrackingSyncError.AuthenticationRequired(id)
        // Bulk episode changes (a whole series, "previous episodes too") share requests so
        // they fit Simkl's per-user daily quota; a single change is still one request.
        rateLimitAware {
            planSimklPush(operations).forEach { step ->
                when (step) {
                    is SimklPushStep.Single -> pushSingle(step.operation)
                    is SimklPushStep.EpisodeBatch -> pushEpisodeBatch(step)
                }
            }
        }
        return ProviderPushResult(operations.mapTo(linkedSetOf(), SyncOperation::id))
    }

    private suspend fun pushSingle(operation: SyncOperation) {
        when (operation.type) {
            SyncOperationType.LIBRARY_STATUS -> pushLibraryStatus(operation)
            SyncOperationType.MOVIE_WATCHED -> pushMovieHistory(operation, watched = true)
            SyncOperationType.MOVIE_UNWATCHED -> pushMovieHistory(operation, watched = false)
            SyncOperationType.EPISODE_WATCHED,
            SyncOperationType.EPISODE_UNWATCHED -> pushEpisodeHistory(operation)
            SyncOperationType.MEDIA_HISTORY_REMOVE -> removeMediaHistory(operation)
            SyncOperationType.SET_RATING -> throw TrackingSyncError.UnsupportedOperation(id, operation.type)
        }
    }

    /** One request for many episodes: each keeps its own exact watched time. */
    private suspend fun pushEpisodeBatch(step: SimklPushStep.EpisodeBatch) {
        val shows = step.operations.groupBy(SyncOperation::mediaId).map { (showId, operations) ->
            val seasons = operations.map(SyncOperation::simklEpisodeTarget)
                .groupBy(SimklEpisodeTarget::season)
                .toSortedMap()
                .map { (season, targets) ->
                    SimklSeason(season, targets.sortedBy(SimklEpisodeTarget::episode).map { SimklEpisode(it.episode, it.watchedAt) })
                }
            SimklSyncItem(ids = SimklIds(tmdb = showId.toString()), seasons = seasons)
        }
        val request = SimklSyncRequest(shows = shows)
        if (step.watched) services.simklSync.addHistory(request) else services.simklSync.removeHistory(request)
    }

    /**
     * Simkl's 429 means the user's daily request allowance is spent. Nothing is lost: the
     * queued changes stay pending, a retry is scheduled, and the error says so.
     */
    private suspend fun <T> rateLimitAware(block: suspend () -> T): T = try {
        block()
    } catch (error: retrofit2.HttpException) {
        if (error.code() != 429) throw error
        val retryAfter = error.response()?.headers()?.get("Retry-After")?.trim()?.toLongOrNull()
        onRateLimited(retryAfter)
        throw TrackingSyncError.RateLimited(
            retryAfter,
            "Simkl's daily request limit was reached. Your changes are saved and will be sent automatically.",
        )
    }

    override suspend fun pullSnapshot(): TrackingSnapshot {
        normalizedSnapshot?.invoke()?.let { return it }
        if (!isAuthenticated()) throw TrackingSyncError.AuthenticationRequired(id)
        val responses = coroutineScope {
            val shows = async { services.simklSync.allItems("shows") }
            val anime = async { services.simklSync.allItems("anime") }
            val movies = async { services.simklSync.allItems("movies") }
            Triple(shows.await(), anime.await(), movies.await())
        }
        val showItems = responses.first.shows + responses.first.anime + responses.second.shows + responses.second.anime
        fun ids(item: com.cinetrack.data.remote.SimklLibraryItem): MediaIds =
            (item.show ?: item.movie)?.ids?.let { MediaIds(it.tmdb?.toLongOrNull(), it.tvdb?.toLongOrNull(), it.imdb, it.simkl) }
                ?: MediaIds()
        val shows = showItems.map { item ->
            TrackedShowState(ids(item), item.status.toLibraryStatus(), null)
        }
        val movies = responses.third.movies.map { item ->
            TrackedMovieState(ids(item), item.status.toLibraryStatus(), item.lastWatchedAt != null, item.lastWatchedAt.toInstantOrNull(), null)
        }
        val episodes = showItems.flatMap { item ->
            val showIds = ids(item)
            item.seasons.flatMap { season -> season.episodes.map { episode ->
                TrackedEpisodeState(showIds, season.number, episode.number, true, episode.watchedAt.toInstantOrNull(), episode.watchedAt.toInstantOrNull() ?: item.lastWatchedAt.toInstantOrNull())
            } }
        }
        return TrackingSnapshot(movies = movies, shows = shows, episodes = episodes, generatedAt = Instant.now(), completeHistory = true)
    }

    override suspend fun syncBidirectionally(
        operations: List<SyncOperation>,
        onProgress: (SyncProgress) -> Unit,
    ): ProviderSyncOutcome {
        if (!isAuthenticated()) throw TrackingSyncError.AuthenticationRequired(id)
        return rateLimitAware { syncEngine().sync(operations, onProgress) }
    }

    override suspend fun testConnection(): ConnectionResult {
        if (!isAuthenticated()) return ConnectionResult.AuthenticationRequired
        return runCatching { services.simklSync.activities() }.fold(
            onSuccess = { ConnectionResult.Connected },
            onFailure = { ConnectionResult.Failed(TrackingSyncError.ProviderUnavailable(id, it)) },
        )
    }

    private suspend fun pushLibraryStatus(operation: SyncOperation) {
        val status = operation.value?.let { runCatching { LibraryStatus.valueOf(it) }.getOrNull() }
            ?: throw TrackingSyncError.InvalidRemoteData("Missing CineTrack library status")
        val ids = SimklIds(tmdb = operation.mediaId.toString())
        val item = SimklSyncItem(ids = ids)
        val request = operation.request(item)
        when (status) {
            LibraryStatus.NONE -> {
                val response = services.simklSync.removeHistory(request)
                val unmatched = if (operation.mediaType == MediaType.MOVIE) response.notFound.movies else response.notFound.shows
                check(unmatched.isEmpty()) { "Simkl could not match the item being removed" }
            }
            else -> services.simklSync.addToList(operation.request(item.copy(to = status.toSimklStatus())))
        }
        // Simkl holds this status now, so it is also the last state both sides
        // agree on; the baseline must not keep the old one.
        preferences.syncBaselineNow(TrackingProviderId.SIMKL)?.let { baseline ->
            preferences.saveSyncBaseline(
                withDeliveredLibraryState(baseline, operation.mediaType, operation.mediaId.toLong(), status, watched = status == LibraryStatus.COMPLETED),
                TrackingProviderId.SIMKL,
            )
        }
    }

    private suspend fun pushEpisodeHistory(operation: SyncOperation) {
        val target = operation.simklEpisodeTarget()
        val request = operation.request(
            SimklSyncItem(
                ids = SimklIds(tmdb = operation.mediaId.toString()),
                seasons = listOf(SimklSeason(target.season, listOf(SimklEpisode(target.episode, target.watchedAt)))),
            ),
        )
        if (operation.type == SyncOperationType.EPISODE_WATCHED) services.simklSync.addHistory(request)
        else services.simklSync.removeHistory(request)
    }

    private suspend fun pushMovieHistory(operation: SyncOperation, watched: Boolean) {
        val watchedAt = if (watched) {
            operation.payload
                ?.let { runCatching { Instant.parse(it) }.getOrNull() }
                ?.toString()
                ?: Instant.ofEpochMilli(operation.sourceVersion).toString()
        } else {
            null
        }
        val request = operation.request(SimklSyncItem(
            ids = SimklIds(tmdb = operation.mediaId.toString()),
            watchedAt = watchedAt,
        ))
        if (watched) {
            services.simklSync.addHistory(request)
        } else {
            services.simklSync.removeHistory(request)
            // MOVIE_UNWATCHED may be retried independently of its library
            // operation. If context was persisted, restore that exact list
            // status after Simkl removes history.
            val desiredStatus = operation.payload
                ?.let { runCatching { LibraryStatus.valueOf(it) }.getOrNull() }
            if (desiredStatus != null && desiredStatus != LibraryStatus.NONE) {
                services.simklSync.addToList(
                    operation.request(
                        SimklSyncItem(
                            ids = SimklIds(tmdb = operation.mediaId.toString()),
                            to = desiredStatus.toSimklStatus(),
                        ),
                    ),
                )
            }
        }
    }

    private suspend fun removeMediaHistory(operation: SyncOperation) {
        val ids = SimklIds(simkl = operation.payload?.toLongOrNull(), tmdb = operation.mediaId.toString())
        services.simklSync.removeHistory(operation.request(SimklSyncItem(ids = ids)))
    }
}

private fun SyncOperation.request(item: SimklSyncItem): SimklSyncRequest =
    if (mediaType == MediaType.MOVIE) SimklSyncRequest(movies = listOf(item))
    else SimklSyncRequest(shows = listOf(item))

private fun LibraryStatus.toSimklStatus(): String = when (this) {
    LibraryStatus.WATCHING -> "watching"
    LibraryStatus.PLAN_TO_WATCH -> "plantowatch"
    LibraryStatus.PAUSED -> "hold"
    LibraryStatus.COMPLETED -> "completed"
    LibraryStatus.DROPPED -> "dropped"
    LibraryStatus.NONE -> error("NONE is represented by a remove operation")
}

private fun String.toLibraryStatus(): LibraryStatus = when (lowercase()) {
    "watching" -> LibraryStatus.WATCHING
    "plantowatch" -> LibraryStatus.PLAN_TO_WATCH
    "hold" -> LibraryStatus.PAUSED
    "completed" -> LibraryStatus.COMPLETED
    "dropped" -> LibraryStatus.DROPPED
    else -> LibraryStatus.NONE
}

private fun String?.toInstantOrNull(): Instant? = this?.let { runCatching { Instant.parse(it) }.getOrNull() }
