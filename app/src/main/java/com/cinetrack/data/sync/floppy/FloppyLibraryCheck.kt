package com.cinetrack.data.sync.floppy

import com.cinetrack.data.sync.TrackingSnapshot
import com.cinetrack.domain.LibraryStatus
import java.time.Instant
import java.time.OffsetDateTime

/** What Floppy lacks compared with this device, as the snapshot to re-send. */
data class FloppyCheckResult(
    val missing: TrackingSnapshot,
    val movies: Int,
    val shows: Int,
    val episodes: Int,
    /** A few readable examples ("TV:123 S01E02") for the log. */
    val examples: List<String>,
) {
    val isEmpty: Boolean get() = movies == 0 && shows == 0 && episodes == 0
}

/**
 * Compares what Floppy should hold ([expected], with episodes already moved
 * to where Floppy stored them and unplaceable ones left out) with Floppy's
 * own per-item state. Plays are matched by their exact instant within the
 * same movie or show, so an episode Floppy stored under other numbering
 * still counts. Nothing here deletes or merges: it only lists what to send.
 */
internal fun floppyMissing(expected: TrackingSnapshot, remote: List<FloppySyncItemDto>): FloppyCheckResult {
    val remoteMovies = remote.filter { it.type == "movie" }.mapNotNull { item -> item.localTmdbId()?.let { it.toLong() to item } }.toMap()
    val remoteShows = remote.filter { it.type == "show" }.mapNotNull { item -> item.localTmdbId()?.let { it.toLong() to item } }.toMap()
    val episodeItems = remote.filter { it.type == "episode" && it.source == "tmdb" }
    // show -> instant -> plays at that instant (two episodes can share one).
    val remotePlays = HashMap<Long, MutableMap<Instant, Int>>()
    val remoteCoordinates = HashSet<Triple<Long, Int, Int>>()
    episodeItems.forEach { item ->
        val show = item.mediaId.toLongOrNull() ?: return@forEach
        if (item.plays.isNotEmpty() && item.seasonNumber != null && item.episodeNumber != null) {
            remoteCoordinates += Triple(show, item.seasonNumber, item.episodeNumber)
        }
        item.plays.forEach { play ->
            val at = play.watchedAt.toInstantOrNull() ?: return@forEach
            remotePlays.getOrPut(show) { HashMap() }.merge(at, 1, Int::plus)
        }
    }

    val examples = mutableListOf<String>()
    fun note(text: String) { if (examples.size < 5) examples += text }

    val movies = expected.movies.filter { movie ->
        val id = movie.ids.tmdb ?: return@filter false
        val inLibrary = movie.libraryState != null && movie.libraryState != LibraryStatus.NONE
        if (!inLibrary && !movie.watched) return@filter false
        val item = remoteMovies[id]
        val plays = item?.plays.orEmpty().mapNotNull { it.watchedAt.toInstantOrNull() }
        val missing = when {
            item == null || !item.inLibrary -> true
            movie.watched && movie.watchedAt != null -> movie.watchedAt !in plays
            movie.watched -> plays.isEmpty()
            // A completed movie is its play; other statuses must match.
            else -> movie.libraryState != LibraryStatus.COMPLETED && item.libraryStatus() != movie.libraryState
        }
        if (missing) note("MOVIE:$id")
        missing
    }
    val shows = expected.shows.filter { show ->
        val id = show.ids.tmdb ?: return@filter false
        val status = show.libraryState?.takeIf { it != LibraryStatus.NONE } ?: return@filter false
        val item = remoteShows[id]
        val missing = item == null || !item.inLibrary || item.libraryStatus() != status
        if (missing) note("TV:$id")
        missing
    }
    val episodes = expected.episodes.filter { it.watched }
        .groupBy { it.showIds.tmdb }
        .flatMap { (show, watched) ->
            if (show == null) return@flatMap emptyList()
            val available = remotePlays[show].orEmpty().toMutableMap()
            watched.sortedWith(compareBy({ it.season }, { it.episode })).filter { episode ->
                val at = episode.watchedAt
                val missing = if (at != null) {
                    val left = available[at] ?: 0
                    if (left > 0) available[at] = left - 1
                    left == 0
                } else {
                    Triple(show, episode.season, episode.episode) !in remoteCoordinates
                }
                if (missing) note("TV:$show S%02dE%02d".format(episode.season, episode.episode))
                missing
            }
        }
    return FloppyCheckResult(
        missing = TrackingSnapshot(movies = movies, shows = shows, episodes = episodes),
        movies = movies.size,
        shows = shows.size,
        episodes = episodes.size,
        examples = examples,
    )
}

private fun String?.toInstantOrNull(): Instant? {
    if (isNullOrBlank()) return null
    return runCatching { OffsetDateTime.parse(this).toInstant() }.getOrNull()
        ?: runCatching { Instant.parse(this) }.getOrNull()
}

/**
 * After a sync, checks that Floppy (as SECONDARY) holds everything this
 * device has, like the Simkl reconciliation does for MAIN, and re-sends only
 * what is missing through the bootstrap worker. With Floppy as MAIN it does
 * nothing: Floppy is then the source of truth, and re-sending a play removed
 * there would bring it back.
 */
class FloppyLibraryCheck(
    private val isSecondary: suspend () -> Boolean,
    private val provider: FloppyTrackingProvider,
    private val coordinator: FloppyBootstrapCoordinator,
    private val lastCheckedAt: suspend () -> Long,
    private val setLastCheckedAt: suspend (Long) -> Unit,
    private val quiesced: suspend (suspend () -> List<FloppySyncItemDto>) -> List<FloppySyncItemDto>,
    private val startWorker: suspend (String) -> Unit,
    private val log: (String) -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun runIfDue() {
        if (!isSecondary() || !provider.currentCapabilities().supportsTwoWaySync) return
        val now = clock()
        if (now - lastCheckedAt() < MIN_INTERVAL_MS) return
        val instance = provider.currentDeliveryInstanceId() ?: return
        // Only once the bootstrap is READY; a running plan is still sending.
        val expected = coordinator.expectedOnFloppy(instance) ?: return
        val remote = runCatching { quiesced { readAll(instance) } }.getOrElse { error ->
            log("Floppy check could not read Floppy: ${error.message?.take(160) ?: error::class.simpleName}")
            return
        }
        setLastCheckedAt(now)
        val result = floppyMissing(expected, remote)
        if (result.isEmpty) {
            log("Floppy check: every item is on Floppy (${remote.size} checked)")
            return
        }
        val planned = coordinator.startRepair(instance, result.missing)
        log(
            "Floppy check: missing on Floppy movies=${result.movies} shows=${result.shows} episodes=${result.episodes}, " +
                "e.g. ${result.examples.joinToString()}; re-sending $planned operation(s)",
        )
        if (planned > 0) startWorker(instance)
    }

    private suspend fun readAll(instance: String): List<FloppySyncItemDto> {
        val items = mutableListOf<FloppySyncItemDto>()
        var after = 0L
        while (true) {
            val page = provider.syncItemsSnapshot(instance, after, PAGE_SIZE)
            items += page.results
            after = page.nextAfter ?: break
        }
        return items
    }

    private companion object {
        const val PAGE_SIZE = 500
        const val MIN_INTERVAL_MS = 60 * 60 * 1000L
    }
}
