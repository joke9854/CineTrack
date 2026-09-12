package com.cinetrack.data.schedule

import androidx.room.withTransaction
import com.cinetrack.data.local.AppDatabase
import com.cinetrack.data.local.EpisodeEntity
import com.cinetrack.data.local.SyncStateEntity
import com.cinetrack.data.remote.ApiServices
import com.cinetrack.domain.MediaCard
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

interface ReleaseScheduleRepository {
    suspend fun refresh(shows: List<MediaCard>, force: Boolean = false): Boolean
}

/**
 * Schedule sources are intentionally selected independently from tracking roles.
 * Simkl can remain an air-time source when another service becomes MAIN.
 */
class DefaultReleaseScheduleRepository(
    private val database: AppDatabase,
    private val services: ApiServices,
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
        val existing = database.mediaDao().episodeSnapshot()
            .associateBy { Triple(it.showId, it.season, it.number) }
        val calendarRows = (tvResult.getOrDefault(emptyList()) + animeResult.getOrDefault(emptyList()))
            .mapNotNull { item ->
                val showId = item.ids.tmdb?.toIntOrNull()?.takeIf(tracked::contains) ?: return@mapNotNull null
                val season = item.episode.season
                val number = item.episode.number
                if (season < 0 || number <= 0) return@mapNotNull null
                val date = item.date.takeIf { it.length >= 10 }
                    ?: item.releaseDate.takeIf { it.length >= 10 }
                    ?: return@mapNotNull null
                val cached = existing[Triple(showId, season, number)]
                EpisodeEntity(
                    showId = showId,
                    season = season,
                    number = number,
                    tmdbId = cached?.tmdbId,
                    title = cached?.title?.takeIf(String::isNotBlank) ?: "Episode $number",
                    overview = cached?.overview.orEmpty(),
                    airDate = date,
                    stillPath = cached?.stillPath,
                    runtimeMinutes = cached?.runtimeMinutes,
                )
            }
            .distinctBy { Triple(it.showId, it.season, it.number) }
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
}


