package com.cinetrack.data.sync.simkl

import com.cinetrack.data.sync.SyncOperation
import com.cinetrack.data.sync.SyncOperationType
import com.cinetrack.data.sync.TrackingSyncError
import java.time.Instant

/** One Simkl request: either a single queued change, or several episode changes of one kind. */
internal sealed interface SimklPushStep {
    data class Single(val operation: SyncOperation) : SimklPushStep
    data class EpisodeBatch(val watched: Boolean, val operations: List<SyncOperation>) : SimklPushStep
}

/** The episode a queued EPISODE_WATCHED/UNWATCHED change targets, with the exact time to send. */
internal data class SimklEpisodeTarget(val season: Int, val episode: Int, val watchedAt: String?)

/**
 * Reads an episode change. Room's legacy SyncOperationEntity has no payload column for
 * provider-only mirror rows, so their stable episode key is rebuilt from the id and the
 * operation generation is the watched time.
 */
internal fun SyncOperation.simklEpisodeTarget(): SimklEpisodeTarget {
    val parts = payload?.split(':', limit = 3)
        ?: id.split(':').let { idParts ->
            if (idParts.size >= 5 && idParts[0] == "mirror" && idParts[1] == "episode-watched") {
                listOf(idParts[3], idParts[4])
            } else emptyList()
        }
    val season = parts.getOrNull(0)?.toIntOrNull()
        ?: throw TrackingSyncError.InvalidRemoteData("Invalid queued season")
    val episode = parts.getOrNull(1)?.toIntOrNull()
        ?: throw TrackingSyncError.InvalidRemoteData("Invalid queued episode")
    val watchedAt = if (type == SyncOperationType.EPISODE_WATCHED) {
        parts.getOrNull(2)?.let { runCatching { Instant.parse(it) }.getOrNull()?.toString() }
            ?: Instant.ofEpochMilli(sourceVersion).toString()
    } else null
    return SimklEpisodeTarget(season, episode, watchedAt)
}

/**
 * Groups a push into as few Simkl requests as possible without changing what is sent or in
 * which order. Only consecutive episode changes of the same kind share a request; anything
 * else (list status, movies, removals) is sent on its own at its original position and closes
 * the current batch. A batch also closes when the kind flips or the same episode comes again
 * (a rewatch stays its own request), and at [maxEpisodes]. A lone change is still one request,
 * exactly as before.
 */
internal fun planSimklPush(operations: List<SyncOperation>, maxEpisodes: Int = 500): List<SimklPushStep> {
    val steps = mutableListOf<SimklPushStep>()
    var batch = mutableListOf<SyncOperation>()
    var batchWatched: Boolean? = null
    val batchKeys = mutableSetOf<String>()
    fun flush() {
        when (batch.size) {
            0 -> Unit
            1 -> steps += SimklPushStep.Single(batch.single())
            else -> steps += SimklPushStep.EpisodeBatch(batchWatched!!, batch.toList())
        }
        batch = mutableListOf()
        batchWatched = null
        batchKeys.clear()
    }
    for (operation in operations) {
        val watched = when (operation.type) {
            SyncOperationType.EPISODE_WATCHED -> true
            SyncOperationType.EPISODE_UNWATCHED -> false
            else -> null
        }
        val target = if (watched != null) runCatching { operation.simklEpisodeTarget() }.getOrNull() else null
        if (watched == null || target == null) {
            // Not batchable (or unreadable: the single path reports the error as before).
            flush()
            steps += SimklPushStep.Single(operation)
            continue
        }
        val key = "${operation.mediaId}:${target.season}:${target.episode}"
        if (batchWatched != null && (batchWatched != watched || key in batchKeys || batch.size >= maxEpisodes)) flush()
        batch += operation
        batchWatched = watched
        batchKeys += key
    }
    flush()
    return steps
}
