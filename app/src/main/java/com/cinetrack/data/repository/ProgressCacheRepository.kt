package com.cinetrack.data.repository

import com.cinetrack.data.local.AppDatabase
import com.cinetrack.data.local.EpisodeEntity
import com.cinetrack.domain.EpisodeCard
import com.cinetrack.domain.MediaCard
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicInteger

/**
 * Owns the expensive, cache-backed derivation of the next episode for each show.
 * Network writes and the surrounding Room transaction remain coordinated by the
 * CineTrackRepository facade, preserving the existing mutex boundaries.
 */
internal class ProgressCacheRepository(
    private val database: AppDatabase,
    private val preferences: AppPreferences,
    private val tmdbApiKey: () -> String,
    private val loadEpisodes: suspend (MediaCard, Int) -> List<EpisodeCard>,
) {
    suspend fun loadUpNextEpisodes(
        shows: List<MediaCard>,
        watched: Set<Triple<Int, Int, Int>>,
        cachedEpisodes: List<EpisodeCard>? = null,
        onItemProcessed: ((processed: Int, total: Int) -> Unit)? = null,
    ): Map<String, EpisodeCard> {
        val today = localToday()
        val excludeSpecials = preferences.excludeSpecials.first()
        val cachedByShow = (cachedEpisodes ?: database.mediaDao().episodeSnapshot().map { it.toEpisodeCard() })
            .groupBy(EpisodeCard::showId)
        val watchedByShow = watched.groupBy { it.first }
        val distinctShows = shows.distinctBy(MediaCard::stableKey)
        if (distinctShows.isEmpty()) return emptyMap()

        val requestSlots = Semaphore(permits = 4)
        val completed = AtomicInteger(0)
        val progressLock = Any()
        val loaded = coroutineScope {
            distinctShows.map { show ->
                async {
                    try {
                        requestSlots.withPermit {
                            val lastWatched = watchedByShow[show.id].orEmpty().asSequence()
                                .filter { it.second > 0 }
                                .maxWithOrNull(compareBy<Triple<Int, Int, Int>>({ it.second }, { it.third }))

                            fun nextFrom(source: List<EpisodeCard>): EpisodeCard? {
                                val candidates = source.asSequence()
                                    .filter { !excludeSpecials || it.season > 0 }
                                    .filter { episode ->
                                        episode.airDate?.take(10)?.let { raw ->
                                            runCatching { !LocalDate.parse(raw).isAfter(today) }.getOrDefault(false)
                                        } == true
                                    }
                                    .filterNot { Triple(show.id, it.season, it.number) in watched }
                                    .sortedWith(compareBy(EpisodeCard::season, EpisodeCard::number))
                                    .toList()
                                return lastWatched?.let { last ->
                                    candidates.firstOrNull {
                                        it.season > last.second || (it.season == last.second && it.number > last.third)
                                    }
                                } ?: candidates.firstOrNull()
                            }

                            var candidates = cachedByShow[show.id].orEmpty()
                            var episode = nextFrom(candidates)
                            if (episode == null && tmdbApiKey().isNotBlank()) {
                                val seasons = if (lastWatched != null) {
                                    listOf(lastWatched.second, lastWatched.second + 1)
                                } else {
                                    listOf(show.seasons.firstOrNull { it.number > 0 }?.number ?: 1)
                                }
                                for (season in seasons.distinct().filter { it > 0 }) {
                                    val fetched = runCatching { loadEpisodes(show, season) }.getOrDefault(emptyList())
                                    candidates = (candidates + fetched).distinctBy { it.season to it.number }
                                    episode = nextFrom(candidates)
                                    if (episode != null) break
                                }
                            }
                            episode?.let { show.stableKey to it }
                        }
                    } finally {
                        synchronized(progressLock) {
                            onItemProcessed?.invoke(completed.incrementAndGet(), distinctShows.size)
                        }
                    }
                }
            }.awaitAll().filterNotNull()
        }
        return loaded.toMap(linkedMapOf())
    }

    private suspend fun localToday(): LocalDate {
        val configured = preferences.metadataTimezone.first()
        val zone = if (configured == "system") ZoneId.systemDefault()
        else runCatching { ZoneId.of(configured) }.getOrDefault(ZoneId.systemDefault())
        return LocalDate.now(zone)
    }
}

private fun EpisodeEntity.toEpisodeCard() = EpisodeCard(
    id = tmdbId ?: 0,
    showId = showId,
    season = season,
    number = number,
    title = title,
    overview = overview,
    airDate = airDate,
    stillUrl = stillPath?.let { "https://image.tmdb.org/t/p/w780$it" },
    runtimeMinutes = runtimeMinutes,
)
