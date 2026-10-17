package com.cinetrack.data.sync.numbering

import com.cinetrack.data.local.EpisodeNumberingDao
import com.cinetrack.data.local.EpisodeNumberingEntity
import com.cinetrack.data.remote.SimklEpisode
import com.cinetrack.data.remote.SimklLibraryItem
import com.cinetrack.data.remote.SimklSeason
import com.cinetrack.data.sync.floppy.FloppyEpisodeTranslateResult
import kotlinx.coroutines.CancellationException

/** Where a local (TMDB) episode goes when it is sent to Simkl. */
sealed interface SimklTarget {
    /** Simkl's own numbering. */
    data class Native(val season: Int, val episode: Int) : SimklTarget
    /** TVDB anime numbering, sent with `use_tvdb_anime_seasons`. */
    data class Tvdb(val season: Int, val episode: Int) : SimklTarget
    data object Unchanged : SimklTarget
}

/** Floppy's translate endpoint. */
interface EpisodeTranslateTransport {
    /** True when a Floppy connection with the translate capability exists. */
    suspend fun isAvailable(): Boolean

    /** Returns null when unavailable; throws (classified) on request failures. [from] is "tracker" or "tmdb". */
    suspend fun translate(tmdbId: Int, from: String, coords: List<Pair<Int, Int>>): List<FloppyEpisodeTranslateResult>?
}

const val NUMBERING_SCHEME_SIMKL = "simkl"
const val NUMBERING_SAME = "same"
const val NUMBERING_TRANSLATED = "translated"
const val NUMBERING_NOT_FOUND = "not_found"
private const val RETRY_NOT_FOUND_AFTER_MS = 7L * 24 * 60 * 60 * 1000
private const val TRANSLATE_CHUNK = 400

/**
 * The app stores TMDB numbering. Simkl's own numbers (anime) exist only at the Simkl
 * boundary and are converted through a persisted per-episode table filled from Floppy.
 */
class EpisodeNumbering(
    private val dao: EpisodeNumberingDao,
    private val transport: EpisodeTranslateTransport?,
    private val log: (String) -> Unit = {},
    private val clock: () -> Long = System::currentTimeMillis,
    /**
     * Whether a show's stored data is already in TMDB numbering. Until the one-time local
     * renumbering has settled a show, pulls and pushes keep Simkl's numbers for it so local
     * data, baseline and remote never disagree.
     */
    private val settled: suspend (showId: Int) -> Boolean = { true },
) {
    suspend fun isAvailable(): Boolean = transport?.isAvailable() == true

    /** Null on unavailability or any failure (one warn line, no body or key). */
    private suspend fun safeTranslate(showId: Int, from: String, coords: List<Pair<Int, Int>>): List<FloppyEpisodeTranslateResult>? {
        val transport = transport ?: return null
        val results = ArrayList<FloppyEpisodeTranslateResult>()
        for (chunk in coords.chunked(TRANSLATE_CHUNK)) {
            try {
                results += transport.translate(showId, from, chunk) ?: return null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                log("Episode translation unavailable: show=$showId count=${chunk.size} error=${error::class.simpleName}")
                return null
            }
        }
        return results
    }

    /** Fills the table for the given Simkl-numbered coordinates. Never throws into sync. */
    suspend fun translateSimkl(showId: Int, coords: Collection<Pair<Int, Int>>) {
        val wanted = coords.toCollection(linkedSetOf())
        if (wanted.isEmpty() || transport == null) return
        val now = clock()
        val existing = try {
            dao.forShow(showId, NUMBERING_SCHEME_SIMKL).associateBy { it.schemeSeason to it.schemeEpisode }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            log("Episode numbering unreadable: show=$showId count=${wanted.size} error=${error::class.simpleName}")
            return
        }
        val missing = wanted.filter { coord ->
            val row = existing[coord]
            row == null || (row.status == NUMBERING_NOT_FOUND && now - row.updatedAt > RETRY_NOT_FOUND_AFTER_MS)
        }
        if (missing.isEmpty()) return
        val results = safeTranslate(showId, "tracker", missing) ?: return
        val rows = results.mapNotNull { result ->
            if ((result.season to result.episode) !in wanted) return@mapNotNull null
            when (result.status) {
                NUMBERING_SAME -> row(showId, result, NUMBERING_SAME, result.season, result.episode, now)
                NUMBERING_TRANSLATED -> result.tmdb?.let { row(showId, result, NUMBERING_TRANSLATED, it.season, it.episode, now) }
                NUMBERING_NOT_FOUND -> row(showId, result, NUMBERING_NOT_FOUND, null, null, now)
                else -> null // metadata_unavailable and anything unknown: nothing is stored
            }
        }
        if (rows.isNotEmpty()) {
            try {
                dao.upsert(rows)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                log("Episode numbering not stored: show=$showId count=${rows.size} error=${error::class.simpleName}")
            }
        }
    }

    private fun row(showId: Int, r: FloppyEpisodeTranslateResult, status: String, tmdbS: Int?, tmdbE: Int?, now: Long) =
        EpisodeNumberingEntity(
            showId = showId,
            scheme = NUMBERING_SCHEME_SIMKL,
            schemeSeason = r.season,
            schemeEpisode = r.episode,
            status = status,
            tmdbSeason = tmdbS,
            tmdbEpisode = tmdbE,
            absolute = r.absolute,
            tvdbSeason = r.tvdb?.season,
            tvdbEpisode = r.tvdb?.episode,
            updatedAt = now,
        )

    /** Records that Floppy stored a locally submitted coordinate elsewhere (submitted -> stored). */
    suspend fun recordStored(showId: Int, season: Int, episode: Int, storedSeason: Int, storedEpisode: Int) {
        if (season == storedSeason && episode == storedEpisode) return
        dao.upsert(
            listOf(
                EpisodeNumberingEntity(
                    showId = showId, scheme = NUMBERING_SCHEME_SIMKL,
                    schemeSeason = season, schemeEpisode = episode,
                    status = NUMBERING_TRANSLATED, tmdbSeason = storedSeason, tmdbEpisode = storedEpisode,
                    updatedAt = clock(),
                ),
            ),
        )
    }

    private fun EpisodeNumberingEntity?.localCoordinate(): Pair<Int, Int>? {
        val row = this ?: return null
        if (row.status != NUMBERING_TRANSLATED && row.status != NUMBERING_SAME) return null
        val s = row.tmdbSeason ?: return null
        val e = row.tmdbEpisode ?: return null
        return s to e
    }

    /** Simkl-numbered coordinate to its TMDB coordinate; unchanged when there is no usable mapping. */
    suspend fun toLocal(showId: Int, season: Int, episode: Int): Pair<Int, Int> =
        dao.row(showId, NUMBERING_SCHEME_SIMKL, season, episode).localCoordinate() ?: (season to episode)

    suspend fun toSimkl(showId: Int, tmdbSeason: Int, tmdbEpisode: Int): SimklTarget =
        toSimklBatch(showId, listOf(tmdbSeason to tmdbEpisode)).getValue(tmdbSeason to tmdbEpisode)

    /**
     * Local (TMDB) coordinates to what Simkl must be sent. Order: (a) a stored Simkl row for
     * the coordinate, (b) continuous numbering through Floppy's absolute number, (c) TVDB
     * numbering, (d) unchanged. Floppy is asked only for shows that already have a translated
     * row, so ordinary shows never cost a request.
     */
    suspend fun toSimklBatch(showId: Int, coords: Collection<Pair<Int, Int>>): Map<Pair<Int, Int>, SimklTarget> {
        val wanted = coords.toCollection(linkedSetOf())
        val result = LinkedHashMap<Pair<Int, Int>, SimklTarget>()
        if (wanted.isEmpty()) return result
        if (!settled(showId)) {
            wanted.forEach { result[it] = SimklTarget.Unchanged }
            return result
        }
        val rows = try {
            dao.forShow(showId, NUMBERING_SCHEME_SIMKL)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            log("Episode numbering unreadable: show=$showId count=${wanted.size} error=${error::class.simpleName}")
            emptyList()
        }
        val byTmdb = rows.filter { it.localCoordinate() != null }
            .groupBy { it.localCoordinate()!! }
            .mapValues { (_, list) ->
                list.firstOrNull { it.status == NUMBERING_SAME }
                    ?: list.minWith(compareBy({ it.schemeSeason }, { it.schemeEpisode }))
            }
        val unresolved = mutableListOf<Pair<Int, Int>>()
        for (coord in wanted) {
            val row = byTmdb[coord]
            if (row != null) result[coord] = SimklTarget.Native(row.schemeSeason, row.schemeEpisode) else unresolved += coord
        }
        if (unresolved.isNotEmpty() && rows.any { it.status == NUMBERING_TRANSLATED }) {
            val continuous = rows.all { it.schemeSeason == 1 }
            val answers = safeTranslate(showId, "tmdb", unresolved)?.associateBy { it.season to it.episode }.orEmpty()
            for (coord in unresolved) {
                val answer = answers[coord]
                val absolute = answer?.absolute
                val tvdb = answer?.tvdb
                result[coord] = when {
                    continuous && absolute != null -> SimklTarget.Native(1, absolute)
                    tvdb != null -> SimklTarget.Tvdb(tvdb.season, tvdb.episode)
                    else -> SimklTarget.Unchanged
                }
            }
        } else {
            unresolved.forEach { result[it] = SimklTarget.Unchanged }
        }
        return result
    }

    /**
     * Rewrites a pulled Simkl anime item from Simkl numbering to TMDB numbering. Every
     * episode entry keeps its own watched_at; entries that land on the same coordinate are
     * all kept.
     */
    suspend fun localizeItem(showId: Int, item: SimklLibraryItem): SimklLibraryItem {
        val coords = item.seasons.flatMap { season -> season.episodes.map { season.number to it.number } }
        if (coords.isEmpty() || !settled(showId)) return item
        translateSimkl(showId, coords)
        val rows = try {
            dao.forShow(showId, NUMBERING_SCHEME_SIMKL).associateBy { it.schemeSeason to it.schemeEpisode }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            return item
        }
        val mapped = item.seasons.flatMap { season ->
            season.episodes.map { episode ->
                val local = rows[season.number to episode.number].localCoordinate() ?: (season.number to episode.number)
                local.first to SimklEpisode(local.second, episode.watchedAt, episode.title)
            }
        }
        val seasons = mapped.groupBy({ it.first }, { it.second })
            .toSortedMap()
            .map { (number, episodes) -> SimklSeason(number, episodes) }
        return item.copy(seasons = seasons)
    }

    /** Anime items of a Simkl pull, rewritten to TMDB numbering; [tmdbIdOf] resolves the show. */
    suspend fun localizeItems(
        items: List<SimklLibraryItem>,
        tmdbIdOf: (SimklLibraryItem) -> Int?,
    ): List<SimklLibraryItem> = items.map { item ->
        val id = tmdbIdOf(item)
        if (id == null) item else localizeItem(id, item)
    }
}
