package com.cinetrack.data.sync.numbering

import androidx.room.withTransaction
import com.cinetrack.data.local.AppDatabase
import com.cinetrack.data.local.WatchHistoryEntity
import com.cinetrack.data.remote.SimklLibraryItem
import com.cinetrack.data.repository.AppPreferences
import com.cinetrack.data.repository.ProgressRefreshRequest
import com.cinetrack.data.sync.TrackingProviderId
import com.cinetrack.data.sync.TrackingSnapshot
import com.cinetrack.domain.MediaType
import java.time.Instant
import kotlinx.coroutines.CancellationException

/** What one show's renumbering changes: rows moved in place and rows that were the same play as another. */
internal data class RenumberPlan(
    /** (history row id, new season, new episode). id and watchedAt never change. */
    val moves: List<Triple<Long, Int, Int>>,
    /** Native-numbered rows deleted because the same play (exact instant) already exists at the target. */
    val mergedRowIds: List<Long>,
) {
    val isEmpty: Boolean get() = moves.isEmpty() && mergedRowIds.isEmpty()
}

private fun instantKey(raw: String): String =
    runCatching { Instant.parse(raw).toString() }.getOrDefault(raw)

/**
 * Renumbers a show's TV history from Simkl numbering to TMDB. Only rows whose coordinate has a
 * translated mapping are touched. A row whose target already holds the same play (exactly the
 * same instant) is deleted; any other row moves in place. A rewatch at a different instant is
 * kept. Rows are never matched by coordinate alone.
 */
internal fun planRenumber(
    rows: List<WatchHistoryEntity>,
    mapping: Map<Pair<Int, Int>, Pair<Int, Int>>,
): RenumberPlan {
    val tv = rows.filter { it.mediaType == MediaType.TV.name && it.season != null && it.episodeNumber != null }
    fun target(row: WatchHistoryEntity): Pair<Int, Int>? =
        mapping[row.season!! to row.episodeNumber!!]?.takeIf { it != (row.season to row.episodeNumber) }
    // Plays that stay where they are anchor their coordinate.
    val occupied = HashSet<Triple<Int, Int, String>>()
    tv.filter { target(it) == null }.forEach { occupied += Triple(it.season!!, it.episodeNumber!!, instantKey(it.watchedAt)) }
    val moves = mutableListOf<Triple<Long, Int, Int>>()
    val merged = mutableListOf<Long>()
    tv.filter { target(it) != null }.sortedBy { it.id }.forEach { row ->
        val (season, episode) = target(row)!!
        if (occupied.add(Triple(season, episode, instantKey(row.watchedAt)))) {
            moves += Triple(row.id, season, episode)
        } else {
            merged += row.id
        }
    }
    return RenumberPlan(moves, merged)
}

/**
 * The Simkl baseline of one show in TMDB numbering: one line per resulting coordinate, the one
 * flagged watched winning a collision.
 */
internal fun renumberBaseline(
    baseline: TrackingSnapshot,
    showId: Int,
    mapping: Map<Pair<Int, Int>, Pair<Int, Int>>,
): TrackingSnapshot {
    val out = ArrayList<com.cinetrack.data.sync.TrackedEpisodeState>(baseline.episodes.size)
    val positions = HashMap<Triple<Long, Int, Int>, Int>()
    baseline.episodes.forEach { episode ->
        val tmdb = episode.showIds.tmdb
        val moved = if (tmdb == showId.toLong()) {
            mapping[episode.season to episode.episode]?.let { episode.copy(season = it.first, episode = it.second) } ?: episode
        } else episode
        if (tmdb == null || tmdb != showId.toLong()) {
            out += moved
            return@forEach
        }
        val key = Triple(tmdb, moved.season, moved.episode)
        val at = positions[key]
        if (at == null) {
            positions[key] = out.size
            out += moved
        } else if (moved.watched && !out[at].watched) {
            out[at] = moved
        }
    }
    return baseline.copy(episodes = out)
}

/** Everything the repair touches outside pure logic, so it can be exercised without Room. */
internal interface AnimeRepairStore {
    suspend fun isDone(): Boolean
    suspend fun markDone()
    suspend fun doneShows(): Set<Int>
    suspend fun markShowDone(showId: Int)
    /** The full Simkl anime list (no date filter). */
    suspend fun fetchSimklAnime(): List<SimklLibraryItem>
    /** Active sync operations or pending writes touching this show. */
    suspend fun hasActiveWork(showId: Int): Boolean
    suspend fun episodeHistory(showId: Int): List<WatchHistoryEntity>
    /** Simkl coordinate to TMDB coordinate, translated rows only. */
    suspend fun translatedMapping(showId: Int): Map<Pair<Int, Int>, Pair<Int, Int>>
    /** True when every coordinate already has an answer (translated, same or not_found). */
    suspend fun answered(showId: Int, coords: Collection<Pair<Int, Int>>): Boolean
    /** Applies the plan in one transaction; must not enqueue anything. */
    suspend fun apply(showId: Int, plan: RenumberPlan)
    suspend fun rewriteBaseline(showId: Int, mapping: Map<Pair<Int, Int>, Pair<Int, Int>>)
    /** Rebuilds derived progress (up next) from the renumbered history. */
    suspend fun refreshProgress()
    fun invalidate(showId: Int)
    fun log(message: String)
}

/**
 * One-time local renumbering of anime plays that older versions stored in Simkl's numbering.
 * It is a renumber, not a user change: it never enqueues sync work and never touches a timestamp.
 */
class AnimeNumberingRepair internal constructor(
    private val numbering: EpisodeNumbering,
    private val store: AnimeRepairStore,
) {
    /** Whether a show's local data is already in TMDB numbering. */
    suspend fun isSettled(showId: Int): Boolean = store.isDone() || showId in store.doneShows()

    suspend fun runIfNeeded() {
        try {
            if (store.isDone() || !numbering.isAvailable()) return
            val coordsByShow = LinkedHashMap<Int, MutableSet<Pair<Int, Int>>>()
            store.fetchSimklAnime().forEach { item ->
                val id = item.show?.ids?.tmdb?.toIntOrNull()?.takeIf { it > 0 } ?: return@forEach
                val coords = coordsByShow.getOrPut(id) { linkedSetOf() }
                item.seasons.forEach { season -> season.episodes.forEach { coords += season.number to it.number } }
            }
            val done = store.doneShows().toMutableSet()
            var everyShowDone = true
            for ((showId, coords) in coordsByShow) {
                if (showId in done) continue
                numbering.translateSimkl(showId, coords)
                if (!store.answered(showId, coords)) {
                    everyShowDone = false // retried on the next run
                    continue
                }
                if (store.hasActiveWork(showId)) {
                    everyShowDone = false
                    store.log("Anime numbering: show=$showId waits for its sync work")
                    continue
                }
                val mapping = store.translatedMapping(showId)
                val plan = planRenumber(store.episodeHistory(showId), mapping)
                if (!plan.isEmpty) {
                    store.apply(showId, plan)
                    store.refreshProgress()
                }
                store.rewriteBaseline(showId, mapping)
                store.markShowDone(showId)
                done += showId
                store.invalidate(showId)
                store.log("Anime numbering: show=$showId renumbered=${plan.moves.size} merged=${plan.mergedRowIds.size}")
            }
            if (everyShowDone) store.markDone()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            store.log("Anime numbering repair postponed: error=${error::class.simpleName}")
        }
    }
}

/** Room, DataStore and Simkl backed store. */
internal class RoomAnimeRepairStore(
    private val database: AppDatabase,
    private val preferences: AppPreferences,
    private val fetchAnime: suspend () -> List<SimklLibraryItem>,
    private val refreshProgress: suspend (ProgressRefreshRequest) -> Unit,
    private val onInvalidate: (Int) -> Unit,
    private val logLine: (String) -> Unit,
) : AnimeRepairStore {
    override suspend fun isDone() = preferences.animeNumberingRepairDoneNow()
    override suspend fun markDone() = preferences.setAnimeNumberingRepairDone()
    override suspend fun doneShows() = preferences.animeNumberingRepairShowsNow().mapNotNullTo(hashSetOf()) { it.toIntOrNull() }
    override suspend fun markShowDone(showId: Int) = preferences.addAnimeNumberingRepairShow(showId)
    override suspend fun fetchSimklAnime() = fetchAnime()

    override suspend fun hasActiveWork(showId: Int) =
        database.syncDao().operationCountForShow(showId) > 0 || database.syncDao().pendingWriteCountForShow(showId) > 0

    override suspend fun episodeHistory(showId: Int) =
        database.timelineDao().episodeHistoryForShow(MediaType.TV.name, showId)

    override suspend fun translatedMapping(showId: Int): Map<Pair<Int, Int>, Pair<Int, Int>> =
        database.episodeNumberingDao().forShow(showId, NUMBERING_SCHEME_SIMKL)
            .filter { it.status == NUMBERING_TRANSLATED && it.tmdbSeason != null && it.tmdbEpisode != null }
            .associate { (it.schemeSeason to it.schemeEpisode) to (it.tmdbSeason!! to it.tmdbEpisode!!) }

    override suspend fun answered(showId: Int, coords: Collection<Pair<Int, Int>>): Boolean {
        val known = database.episodeNumberingDao().forShow(showId, NUMBERING_SCHEME_SIMKL)
            .mapTo(hashSetOf()) { it.schemeSeason to it.schemeEpisode }
        return coords.all { it in known }
    }

    override suspend fun apply(showId: Int, plan: RenumberPlan) {
        database.withTransaction {
            val timeline = database.timelineDao()
            plan.moves.forEach { (id, season, episode) -> timeline.moveEpisodeHistoryRow(id, season, episode) }
            plan.mergedRowIds.chunked(500).forEach { timeline.deleteHistoryRows(it) }
        }
    }

    override suspend fun rewriteBaseline(showId: Int, mapping: Map<Pair<Int, Int>, Pair<Int, Int>>) {
        val baseline = preferences.syncBaselineNow(TrackingProviderId.SIMKL) ?: return
        val rewritten = renumberBaseline(baseline, showId, mapping)
        if (rewritten != baseline) preferences.saveSyncBaseline(rewritten, TrackingProviderId.SIMKL)
    }

    override suspend fun refreshProgress() = refreshProgress(ProgressRefreshRequest(episodeHistoryChanged = true))
    override fun invalidate(showId: Int) = onInvalidate(showId)
    override fun log(message: String) = logLine(message)
}
