package com.cinetrack.data.schedule

import androidx.room.withTransaction
import com.cinetrack.data.local.AppDatabase
import com.cinetrack.data.local.EpisodeEntity
import com.cinetrack.data.local.SyncStateEntity
import com.cinetrack.data.remote.ApiServices
import com.cinetrack.domain.MediaCard
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.time.LocalDate
import java.time.ZoneOffset

interface ReleaseScheduleRepository {
    suspend fun refresh(shows: List<MediaCard>, force: Boolean = false): Boolean
}

/**
 * Schedule sources are intentionally selected independently from tracking roles.
 *
 * Air times come from TVmaze when it is on (exact air instants), with Simkl's
 * public calendar as the fallback.
 */
class DefaultReleaseScheduleRepository(
    private val database: AppDatabase,
    private val services: ApiServices,
    private val airDateSource: suspend () -> String = { AirDateSource.DEFAULT },
    private val tvmazeEnabled: suspend () -> Boolean = { false },
    private val tvmaze: () -> com.cinetrack.data.remote.TvmazeService? = { null },
) : ReleaseScheduleRepository {
    override suspend fun refresh(shows: List<MediaCard>, force: Boolean): Boolean {
        val now = System.currentTimeMillis()
        val freshness = 5L * 60L * 60L * 1_000L
        val previous = database.syncDao().get("simkl_calendar")
        val requestedShowsCovered = shows.all { show ->
            database.syncDao().get("simkl_calendar:${show.id}")
                ?.let { now - it.lastSuccessfulSync < freshness } == true
        }
        if (!force && previous != null && now - previous.lastSuccessfulSync < freshness && requestedShowsCovered) {
            return true
        }

        val (tvResult, animeResult) = coroutineScope {
            val tv = async { runCatching { services.simklCalendar.tv() } }
            val anime = async { runCatching { services.simklCalendar.anime() } }
            tv.await() to anime.await()
        }
        val complete = tvResult.isSuccess && animeResult.isSuccess
        val tracked = shows.map(MediaCard::id).toSet()
        val snapshot = database.mediaDao().episodeSnapshot()
        val existing = snapshot.associateBy { Triple(it.showId, it.season, it.number) }

        val simklTimes = simklAirTimes(tvResult.getOrDefault(emptyList()) + animeResult.getOrDefault(emptyList()), tracked)
        val tvmazeTimes = if (tvmazeEnabled()) runCatching { tvmazeAirTimes(tracked, snapshot, now) }.getOrDefault(emptyMap()) else emptyMap()
        // Each provider's dates are kept; the episode shows the chosen provider's.
        val preferred = AirDateSource.normalize(airDateSource())
        val legacy = (simklTimes.keys + tvmazeTimes.keys).associateWith { existing[it]?.airDate }
        recordAirDates(database, AirDateSource.SIMKL, simklTimes, preferred, legacy)
        // The last call resolves every key, so it covers the Simkl-only episodes as well.
        val airTimes = recordAirDates(database, AirDateSource.TVMAZE, tvmazeTimes + simklTimes.keys.filter { it !in tvmazeTimes }.associateWith { null }, preferred, legacy)
            .mapNotNull { (key, date) -> date?.let { key to it } }.toMap()
        val calendarRows = airTimes.map { (key, airDate) ->
            val (showId, season, number) = key
            val cached = existing[key]
            EpisodeEntity(
                showId = showId,
                season = season,
                number = number,
                tmdbId = cached?.tmdbId,
                title = cached?.title?.takeIf(String::isNotBlank) ?: "Episode $number",
                overview = cached?.overview.orEmpty(),
                airDate = airDate,
                stillPath = cached?.stillPath,
                runtimeMinutes = cached?.runtimeMinutes,
            )
        }
        if (calendarRows.isNotEmpty() || complete) {
            database.withTransaction {
                if (calendarRows.isNotEmpty()) database.mediaDao().upsertEpisodes(calendarRows)
                if (complete) {
                    database.syncDao().upsertAll(
                        listOf(SyncStateEntity("simkl_calendar", null, now)) +
                            shows.map { show -> SyncStateEntity("simkl_calendar:${show.id}", null, now) },
                    )
                }
            }
        }
        return complete
    }

    /**
     * TVmaze's exact air instants for tracked shows with an episode from yesterday to the
     * coming week. Show ids are looked up once (TVDB, then IMDb, through TMDB's external
     * ids) and kept in sync_state; a show TVmaze doesn't know is retried after a week.
     */
    private suspend fun tvmazeAirTimes(tracked: Set<Int>, snapshot: List<EpisodeEntity>, now: Long): Map<Triple<Int, Int, Int>, String> {
        val service = tvmaze() ?: return emptyMap()
        val today = LocalDate.now(ZoneOffset.UTC)
        val window = today.minusDays(1)..today.plusDays(TVMAZE_WINDOW_DAYS.toLong())
        val byShow = snapshot.filter { it.showId in tracked }.groupBy(EpisodeEntity::showId)
        val due = byShow.filterValues { episodes ->
            episodes.any { episode -> episode.airDate?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.let { it in window } == true }
        }.keys.take(TVMAZE_MAX_SHOWS)
        val times = linkedMapOf<Triple<Int, Int, Int>, String>()
        for (showId in due) {
            val mazeId = tvmazeId(service, showId, now) ?: continue
            val local = byShow[showId].orEmpty().associateBy { it.season to it.number }
            runCatching { service.episodes(mazeId) }.getOrNull().orEmpty().forEach { episode ->
                val number = episode.number ?: return@forEach
                val stamp = episode.airstamp?.takeIf(String::isNotBlank) ?: return@forEach
                val row = local[episode.season to number] ?: return@forEach
                // Numbering can differ (anime, specials): only trust TVmaze when its day matches TMDB's within one.
                if (tvmazeMatchesTmdb(row.airDate, episode.airdate)) times[Triple(showId, episode.season, number)] = stamp
            }
        }
        return times
    }

    private suspend fun tvmazeId(service: com.cinetrack.data.remote.TvmazeService, showId: Int, now: Long): Int? {
        val cacheKey = "tvmaze:$showId"
        database.syncDao().get(cacheKey)?.let { cached ->
            val id = cached.remoteTimestamp?.toIntOrNull()
            if (id != null && id > 0) return id
            if (now - cached.lastSuccessfulSync < 7L * 24 * 60 * 60 * 1000) return null
        }
        val ids = runCatching { services.tmdb.tvExternalIds(showId) }.getOrNull() ?: return null
        val found = ids.tvdbId?.let { runCatching { service.lookup(tvdb = it) }.getOrNull() }
            ?: ids.imdbId?.takeIf(String::isNotBlank)?.let { runCatching { service.lookup(imdb = it) }.getOrNull() }
        database.syncDao().upsertAll(listOf(SyncStateEntity(cacheKey, (found?.id ?: 0).toString(), now)))
        return found?.id
    }

    private companion object {
        /** Yesterday plus the coming week: where an exact time decides
         * whether an episode is out yet. Simkl covers the longer horizon. */
        const val TVMAZE_WINDOW_DAYS = 9
        /** TVmaze allows 20 calls per 10 s; a refresh stays well under it. */
        const val TVMAZE_MAX_SHOWS = 12
    }
}


/** Simkl's calendar time per tracked (TMDB show, season, episode); the first listing wins. */
internal fun simklAirTimes(
    simkl: List<com.cinetrack.data.remote.SimklCalendarItem>,
    tracked: Set<Int>,
): Map<Triple<Int, Int, Int>, String> {
    val airTimes = linkedMapOf<Triple<Int, Int, Int>, String>()
    simkl.forEach { item ->
        val showId = item.ids.tmdb?.toIntOrNull()?.takeIf(tracked::contains) ?: return@forEach
        val season = item.episode.season
        val number = item.episode.number
        if (season < 0 || number <= 0) return@forEach
        val date = item.date.takeIf { it.length >= 10 }
            ?: item.releaseDate.takeIf { it.length >= 10 }
            ?: return@forEach
        airTimes.putIfAbsent(Triple(showId, season, number), date)
    }
    return airTimes
}

/** True when TVmaze's air day is within one day of TMDB's (either may be in another zone). */
internal fun tvmazeMatchesTmdb(tmdbAirDate: String?, tvmazeAirDate: String?): Boolean {
    val tmdb = tmdbAirDate?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return false
    val maze = tvmazeAirDate?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return false
    return kotlin.math.abs(java.time.temporal.ChronoUnit.DAYS.between(tmdb, maze)) <= 1
}
