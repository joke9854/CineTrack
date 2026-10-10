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
 * Air times come from Trakt when a Trakt client ID is configured (exact first
 * air instants, including streaming release times), with Simkl's public
 * calendar as the fallback.
 */
class DefaultReleaseScheduleRepository(
    private val database: AppDatabase,
    private val services: ApiServices,
    private val traktClientId: suspend () -> String = { "" },
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

        val traktKey = traktClientId().trim()
        val (simkl, trakt) = coroutineScope {
            val tv = async { runCatching { services.simklCalendar.tv() } }
            val anime = async { runCatching { services.simklCalendar.anime() } }
            val trakt = async {
                val service = services.traktCalendar
                if (traktKey.isBlank() || service == null) null
                else runCatching {
                    service.shows(traktKey, LocalDate.now(ZoneOffset.UTC).minusDays(1).toString(), TRAKT_CALENDAR_DAYS)
                }
            }
            (tv.await() to anime.await()) to trakt.await()
        }
        val (tvResult, animeResult) = simkl
        val complete = tvResult.isSuccess && animeResult.isSuccess
        val tracked = shows.map(MediaCard::id).toSet()
        val snapshot = database.mediaDao().episodeSnapshot()
        val existing = snapshot.associateBy { Triple(it.showId, it.season, it.number) }
        val existingByEpisodeId = snapshot.filter { it.tmdbId != null }.associateBy { it.showId to it.tmdbId }

        val airTimes = mergeAirTimes(
            simkl = tvResult.getOrDefault(emptyList()) + animeResult.getOrDefault(emptyList()),
            trakt = trakt?.getOrNull().orEmpty(),
            tracked = tracked,
            localByEpisodeId = existingByEpisodeId.mapValues { (_, row) -> Triple(row.showId, row.season, row.number) },
        )
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

    private companion object {
        /** Yesterday plus the coming week: where an exact time decides
         * whether an episode is out yet. Simkl covers the longer horizon. */
        const val TRAKT_CALENDAR_DAYS = 9
    }
}

/**
 * Air time per (TMDB show, season, episode): Trakt's exact first-air instant
 * wherever Trakt knows the episode, otherwise Simkl's calendar time. A Trakt
 * episode's TMDB id finds the local row even when Trakt numbers it differently.
 */
internal fun mergeAirTimes(
    simkl: List<com.cinetrack.data.remote.SimklCalendarItem>,
    trakt: List<com.cinetrack.data.remote.TraktCalendarItem>,
    tracked: Set<Int>,
    localByEpisodeId: Map<Pair<Int, Int?>, Triple<Int, Int, Int>> = emptyMap(),
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
    trakt.forEach { item ->
        val showId = item.show.ids.tmdb?.takeIf(tracked::contains) ?: return@forEach
        val aired = item.firstAired?.takeIf { it.length > 10 } ?: return@forEach
        val key = item.episode.ids.tmdb?.let { localByEpisodeId[showId to it] }
            ?: Triple(showId, item.episode.season, item.episode.number).takeIf { it.second >= 0 && it.third > 0 }
            ?: return@forEach
        airTimes[key] = aired
    }
    return airTimes
}
