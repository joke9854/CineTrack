package com.cinetrack.data.sync.simkl

import com.cinetrack.data.local.UserMediaStateEntity
import com.cinetrack.data.local.WatchHistoryEntity
import com.cinetrack.data.remote.SimklEpisode
import com.cinetrack.data.remote.SimklIds
import com.cinetrack.data.remote.SimklSeason
import com.cinetrack.data.remote.SimklSyncItem
import com.cinetrack.data.remote.SimklSyncRequest
import com.cinetrack.data.sync.numbering.SimklTarget
import com.cinetrack.domain.LibraryStatus
import com.cinetrack.domain.MediaType
import java.time.Instant

/** How the first sync after connecting a Simkl account combines it with the library on this device. */
enum class SimklFirstSyncMode {
    /** Not chosen yet: no sync runs until the user picks one. */
    ASK,
    /** Upload what only this device has, then add Simkl's data without removing anything locally. */
    MERGE,
    /** The account is the source of truth: local titles missing from it are removed. */
    REPLACE,
}

/** What Simkl's account already has, by TMDB id, after its full lists were downloaded. */
internal data class SimklRemoteLibrary(
    /** "TV:<tmdb>" / "MOVIE:<tmdb>". */
    val keys: Set<String>,
    val simklIds: Set<Long>,
    val watchedMovies: Set<Int>,
    /** (show tmdb, season, episode). */
    val watchedEpisodes: Set<Triple<Int, Int, Int>>,
)

/** Batched uploads for a non-destructive merge; nothing in it removes data on either side. */
internal data class SimklMergePlan(
    val listRequests: List<SimklSyncRequest>,
    val historyRequests: List<SimklSyncRequest>,
    val titles: Int,
    val movies: Int,
    val episodes: Int,
) {
    val isEmpty: Boolean get() = listRequests.isEmpty() && historyRequests.isEmpty()
    val requests: List<SimklSyncRequest> get() = listRequests + historyRequests
}

/**
 * Plans the upload of everything only this device has: library titles Simkl does not list,
 * movies Simkl has not marked watched, and watched episodes Simkl does not have. Watch times
 * are sent exactly as stored; an episode watched several times locally is sent once, with its
 * latest time, because Simkl keeps one watched time per episode.
 */
internal fun planSimklMerge(
    localStates: List<UserMediaStateEntity>,
    localHistory: List<WatchHistoryEntity>,
    remote: SimklRemoteLibrary,
    itemsPerRequest: Int = 100,
    episodesPerRequest: Int = 500,
    /** (show, local season, local episode) to what Simkl must be sent; missing entries go unchanged. */
    simklTargets: Map<Triple<Int, Int, Int>, SimklTarget> = emptyMap(),
): SimklMergePlan {
    fun known(type: String, id: Int, simklId: Long?) =
        "$type:$id" in remote.keys || (simklId != null && simklId in remote.simklIds)

    val simklIdByKey = localStates.associate { "${it.mediaType}:${it.mediaId}" to it.simklId }
    val listItems = localStates
        .filter { it.status != LibraryStatus.NONE.name && !known(it.mediaType, it.mediaId, it.simklId) }
        .mapNotNull { state ->
            val to = state.status.toSimklListStatus() ?: return@mapNotNull null
            state.mediaType to SimklSyncItem(ids = SimklIds(simkl = state.simklId, tmdb = state.mediaId.toString()), to = to)
        }
    val listRequests = listItems.chunked(itemsPerRequest).map { chunk ->
        SimklSyncRequest(
            movies = chunk.filter { it.first == MediaType.MOVIE.name }.map { it.second },
            shows = chunk.filter { it.first == MediaType.TV.name }.map { it.second },
        )
    }

    val movieItems = localHistory
        .filter { it.mediaType == MediaType.MOVIE.name && it.season == null && it.mediaId !in remote.watchedMovies }
        .groupBy { it.mediaId }
        .map { (movieId, rows) ->
            SimklSyncItem(
                ids = SimklIds(simkl = simklIdByKey["${MediaType.MOVIE.name}:$movieId"], tmdb = movieId.toString()),
                watchedAt = rows.latestWatchedAt(),
            )
        }
    val episodeRows = localHistory.filter { row ->
        row.mediaType == MediaType.TV.name && row.season != null && row.episodeNumber != null &&
            Triple(row.mediaId, row.season, row.episodeNumber) !in remote.watchedEpisodes
    }
    val showItems = episodeRows.groupBy { it.mediaId }.flatMap { (showId, rows) ->
        // One play per local episode (its latest), then mapped to the numbering Simkl uses.
        val plays = rows.groupBy { it.season!! to it.episodeNumber!! }.toSortedMap(compareBy({ it.first }, { it.second }))
            .map { (coord, episodePlays) ->
                SimklEpisodePlay(coord.first, coord.second, episodePlays.latestWatchedAt(), simklTargets[Triple(showId, coord.first, coord.second)] ?: SimklTarget.Unchanged)
            }
        simklShowItems(SimklIds(simkl = simklIdByKey["${MediaType.TV.name}:$showId"], tmdb = showId.toString()), plays)
    }
    // Long shows can carry hundreds of episodes, so batches are bounded by episode count.
    val historyRequests = buildList {
        movieItems.chunked(itemsPerRequest).forEach { add(SimklSyncRequest(movies = it)) }
        var batch = mutableListOf<SimklSyncItem>()
        var batchEpisodes = 0
        for (show in showItems) {
            val count = show.seasons.sumOf { it.episodes.size }
            if (batch.isNotEmpty() && batchEpisodes + count > episodesPerRequest) {
                add(SimklSyncRequest(shows = batch))
                batch = mutableListOf()
                batchEpisodes = 0
            }
            batch += show
            batchEpisodes += count
        }
        if (batch.isNotEmpty()) add(SimklSyncRequest(shows = batch))
    }
    return SimklMergePlan(
        listRequests = listRequests,
        historyRequests = historyRequests,
        titles = listItems.size,
        movies = movieItems.size,
        episodes = showItems.sumOf { show -> show.seasons.sumOf { it.episodes.size } },
    )
}

private fun List<WatchHistoryEntity>.latestWatchedAt(): String =
    maxByOrNull { row -> runCatching { Instant.parse(row.watchedAt).toEpochMilli() }.getOrDefault(Long.MIN_VALUE) }!!.watchedAt

private fun String.toSimklListStatus() = when (this) {
    LibraryStatus.WATCHING.name -> "watching"
    LibraryStatus.PLAN_TO_WATCH.name -> "plantowatch"
    LibraryStatus.PAUSED.name -> "hold"
    LibraryStatus.COMPLETED.name -> "completed"
    LibraryStatus.DROPPED.name -> "dropped"
    else -> null
}

/**
 * During a merge a title on both sides keeps Simkl's list status, but a movie watched on this
 * device stays watched and a status Simkl does not have never clears the local one.
 */
internal fun UserMediaStateEntity.mergedWithLocal(local: UserMediaStateEntity?): UserMediaStateEntity {
    if (local == null || local.status == LibraryStatus.NONE.name) return this
    return copy(
        status = if (status == LibraryStatus.NONE.name) local.status else status,
        watched = watched || local.watched,
    )
}
