package com.cinetrack.data.sync.floppy

import com.cinetrack.data.sync.TrackedEpisodeState
import com.cinetrack.data.sync.TrackedMovieState
import com.cinetrack.data.sync.TrackedShowState
import com.cinetrack.data.sync.TrackingSnapshot
import com.cinetrack.domain.LibraryStatus
import java.time.Instant
import java.time.OffsetDateTime

/** One item Floppy lacks: its stable [key] ("MOVIE:1", "TV:2", "TV:2:1:3"),
 * a readable [label] and, when Floppy has it at another time, both times. */
data class FloppyCheckFinding(val key: String, val label: String, val detail: String? = null)

/** What Floppy lacks compared with this device. */
data class FloppyCheckResult(
    /** Absent from Floppy: re-sending places them. */
    val missing: List<FloppyCheckFinding>,
    /** Floppy has the episode, at another time: re-sending cannot change
     * that (Floppy answers it already has it), so it is only reported. */
    val differing: List<FloppyCheckFinding>,
    val missingSnapshot: TrackingSnapshot,
) {
    val isEmpty: Boolean get() = missing.isEmpty() && differing.isEmpty()

    /** The re-send for [keys] only. */
    fun snapshotFor(keys: Set<String>): TrackingSnapshot = TrackingSnapshot(
        movies = missingSnapshot.movies.filter { movieKey(it) in keys },
        shows = missingSnapshot.shows.filter { showKey(it) in keys },
        episodes = missingSnapshot.episodes.filter { episodeKey(it) in keys },
    )
}

internal fun movieKey(movie: TrackedMovieState) = "MOVIE:${movie.ids.tmdb}"
internal fun showKey(show: TrackedShowState) = "TV:${show.ids.tmdb}"
internal fun episodeKey(episode: TrackedEpisodeState) = "TV:${episode.showIds.tmdb}:${episode.season}:${episode.episode}"

/**
 * Compares what Floppy should hold ([expected], with episodes already moved
 * to where Floppy stored them and unplaceable ones left out) with Floppy's
 * own per-item state. An episode counts as present when Floppy has a play of
 * that show at one of this device's times for it (so numbering differences
 * and rewatches still match); this device can hold several plays of one
 * episode while the initial sync sent one, so one match is enough. Nothing
 * here deletes or merges: it only lists what to send.
 */
internal fun floppyMissing(expected: TrackingSnapshot, remote: List<FloppySyncItemDto>): FloppyCheckResult {
    val remoteMovies = remote.filter { it.type == "movie" }.mapNotNull { item -> item.localTmdbId()?.let { it.toLong() to item } }.toMap()
    val remoteShows = remote.filter { it.type == "show" }.mapNotNull { item -> item.localTmdbId()?.let { it.toLong() to item } }.toMap()
    // show -> instant -> plays at that instant (two episodes can share one).
    val remotePlays = HashMap<Long, MutableMap<Instant, Int>>()
    // (show, season, episode) -> Floppy's times for it.
    val remoteTimes = HashMap<Triple<Long, Int, Int>, MutableList<Instant>>()
    remote.filter { it.type == "episode" && it.source == "tmdb" }.forEach { item ->
        val show = item.mediaId.toLongOrNull() ?: return@forEach
        item.plays.forEach { play ->
            val at = play.watchedAt.toInstantOrNull() ?: return@forEach
            remotePlays.getOrPut(show) { HashMap() }.merge(at, 1, Int::plus)
            if (item.seasonNumber != null && item.episodeNumber != null) {
                remoteTimes.getOrPut(Triple(show, item.seasonNumber, item.episodeNumber)) { mutableListOf() } += at
            }
        }
    }

    val missing = mutableListOf<FloppyCheckFinding>()
    val differing = mutableListOf<FloppyCheckFinding>()
    val movies = expected.movies.filter { movie ->
        val id = movie.ids.tmdb ?: return@filter false
        val inLibrary = movie.libraryState != null && movie.libraryState != LibraryStatus.NONE
        if (!inLibrary && !movie.watched) return@filter false
        val item = remoteMovies[id]
        val plays = item?.plays.orEmpty().mapNotNull { it.watchedAt.toInstantOrNull() }
        val label = "MOVIE:$id"
        when {
            item == null || !item.inLibrary -> { missing += FloppyCheckFinding(movieKey(movie), label); true }
            movie.watched && plays.isEmpty() -> { missing += FloppyCheckFinding(movieKey(movie), label); true }
            movie.watched && movie.watchedAt != null && movie.watchedAt !in plays -> {
                differing += FloppyCheckFinding(movieKey(movie), label, "phone ${movie.watchedAt}, Floppy ${plays.sorted().joinToString()}")
                false
            }
            // A completed movie is its play; other statuses must match.
            !movie.watched && movie.libraryState != LibraryStatus.COMPLETED && item.libraryStatus() != movie.libraryState -> {
                missing += FloppyCheckFinding(movieKey(movie), "$label status"); true
            }
            else -> false
        }
    }
    val shows = expected.shows.filter { show ->
        val id = show.ids.tmdb ?: return@filter false
        val status = show.libraryState?.takeIf { it != LibraryStatus.NONE } ?: return@filter false
        val item = remoteShows[id]
        val absent = item == null || !item.inLibrary || item.libraryStatus() != status
        if (absent) missing += FloppyCheckFinding(showKey(show), "TV:$id ${status.name.lowercase()}", item?.status?.let { "Floppy $it" })
        absent
    }
    val episodes = expected.episodes.filter { it.watched }
        .groupBy { Triple(it.showIds.tmdb, it.season, it.episode) }
        .mapNotNull { (coordinate, plays) ->
            val show = coordinate.first ?: return@mapNotNull null
            val available = remotePlays.getOrPut(show) { HashMap() }
            val times = plays.mapNotNull(TrackedEpisodeState::watchedAt)
            val matched = times.firstOrNull { (available[it] ?: 0) > 0 }
            if (matched != null) {
                available[matched] = available.getValue(matched) - 1
                return@mapNotNull null
            }
            val first = plays.first()
            val label = "TV:$show S%02dE%02d".format(coordinate.second, coordinate.third)
            val floppyTimes = remoteTimes[Triple(show, coordinate.second, coordinate.third)].orEmpty()
            if (times.isEmpty() && floppyTimes.isNotEmpty()) return@mapNotNull null
            if (floppyTimes.isNotEmpty()) {
                differing += FloppyCheckFinding(episodeKey(first), label, "phone ${times.sorted().joinToString()}, Floppy ${floppyTimes.sorted().joinToString()}")
                return@mapNotNull null
            }
            missing += FloppyCheckFinding(episodeKey(first), label)
            // The initial sync sends one play per episode: the latest.
            plays.maxByOrNull { it.watchedAt ?: Instant.MIN }
        }
    return FloppyCheckResult(missing, differing, TrackingSnapshot(movies = movies, shows = shows, episodes = episodes))
}

private fun String?.toInstantOrNull(): Instant? {
    if (isNullOrBlank()) return null
    return runCatching { OffsetDateTime.parse(this).toInstant() }.getOrNull()
        ?: runCatching { Instant.parse(this) }.getOrNull()
}

/**
 * After a sync, checks that Floppy (as SECONDARY) holds everything this
 * device has, like the Simkl reconciliation does for MAIN, and re-sends only
 * what is missing through the bootstrap worker. Items a re-send already
 * covered are not sent again: Floppy answered that it has them, so another
 * re-send would loop; they are reported instead. With Floppy as MAIN it does
 * nothing: Floppy is then the source of truth, and re-sending a play removed
 * there would bring it back.
 */
class FloppyLibraryCheck(
    private val isSecondary: suspend () -> Boolean,
    private val provider: FloppyTrackingProvider,
    private val coordinator: FloppyBootstrapCoordinator,
    private val lastCheckedAt: suspend () -> Long,
    private val setLastCheckedAt: suspend (Long) -> Unit,
    private val lastResent: suspend () -> Set<String>,
    private val setLastResent: suspend (Set<String>) -> Unit,
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
        val already = lastResent()
        val (repeated, toSend) = result.missing.partition { it.key in already }
        if (result.isEmpty) {
            log("Floppy check: every item is on Floppy (${remote.size} checked)")
        }
        if (result.differing.isNotEmpty()) {
            log("Floppy check: ${result.differing.size} item(s) on Floppy at another time (not re-sent), e.g. ${describe(result.differing)}")
        }
        if (repeated.isNotEmpty()) {
            log("Floppy check: ${repeated.size} item(s) still missing after a re-send (not re-sent again), e.g. ${describe(repeated)}")
        }
        val planned = if (toSend.isEmpty()) 0 else coordinator.startRepair(instance, result.snapshotFor(toSend.mapTo(hashSetOf()) { it.key }))
        if (toSend.isNotEmpty()) {
            log("Floppy check: ${toSend.size} item(s) missing on Floppy, e.g. ${describe(toSend)}; re-sending $planned operation(s)")
        }
        // Remember what this pass sent, plus what was already sent and is
        // still missing, so neither is sent again while it stays missing.
        setLastResent(toSend.mapTo(hashSetOf()) { it.key } + repeated.map { it.key })
        if (planned > 0) startWorker(instance)
    }

    private fun describe(findings: List<FloppyCheckFinding>) =
        findings.take(5).joinToString { finding -> finding.detail?.let { "${finding.label} ($it)" } ?: finding.label }

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
