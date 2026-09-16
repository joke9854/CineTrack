package com.cinetrack.data.repository

import com.cinetrack.data.local.AppDatabase
import com.cinetrack.data.local.EpisodeEntity
import com.cinetrack.domain.EpisodeCard
import com.cinetrack.domain.MediaCard
import com.cinetrack.domain.PlaybackCard
import com.cinetrack.domain.releaseDateTime
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicInteger

/** Future schedule events only get attention priority when they are imminent. */
internal const val UPCOMING_PROGRESS_ATTENTION_DAYS: Long = 7L
private const val PLAYBACK_COMPLETION_THRESHOLD = 1f

internal fun selectNextProgressEpisode(
    showId: Int,
    episodes: List<EpisodeCard>,
    watched: Set<Triple<Int, Int, Int>>,
    now: java.time.Instant,
    zone: ZoneId,
    excludeSpecials: Boolean,
    playbackSession: PlaybackCard? = null,
): EpisodeCard? {
    val ordered = episodes.asSequence()
        .filter { it.showId == showId && (!excludeSpecials || it.season > 0) }
        .sortedWith(compareBy(EpisodeCard::season, EpisodeCard::number))
        .toList()
    val active = playbackSession?.takeIf { session ->
        session.media.id == showId &&
            session.season != null &&
            session.episodeNumber != null &&
            session.progress > 0f &&
            session.progress < PLAYBACK_COMPLETION_THRESHOLD &&
            Triple(showId, session.season, session.episodeNumber) !in watched
    }
    if (active != null) {
        ordered.firstOrNull { it.season == active.season && it.number == active.episodeNumber }
            ?.let { return it }
    }
    val candidates = ordered.filterNot { Triple(showId, it.season, it.number) in watched }
    val aired = candidates.filter { episode ->
        releaseDateTime(episode.airDate, zone)?.toInstant()?.let { !it.isAfter(now) } == true
    }
    aired.firstOrNull()?.let { return it }
    // Missing release metadata is not proof that an episode is in the future.
    // Keep the sequential watch-history-first invariant and let the caller
    // refresh the missing season metadata instead of skipping this episode.
    candidates.firstOrNull { releaseDateTime(it.airDate, zone) == null }?.let { return it }
    val attentionWindow = UPCOMING_PROGRESS_ATTENTION_DAYS * 86_400_000L
    return candidates.firstOrNull { episode ->
        val air = releaseDateTime(episode.airDate, zone)?.toInstant() ?: return@firstOrNull false
        val distance = air.toEpochMilli() - now.toEpochMilli()
        distance > 0L && distance <= attentionWindow
    }
}


/**
 * Builds one TV Progress card from durable watch state. Membership is
 * deliberately episode-based: a tracked show without a real next episode is
 * absent, while an unfinished playback session remains resumable.
 */
internal fun selectProgressCard(
    show: MediaCard,
    episodes: List<EpisodeCard>,
    watched: Set<Triple<Int, Int, Int>>,
    playbackSession: PlaybackCard?,
    storedNext: EpisodeCard?,
    now: java.time.Instant,
    zone: ZoneId,
    excludeSpecials: Boolean,
): PlaybackCard? {
    val session = playbackSession?.takeIf {
        it.media.id == show.id &&
            it.season != null &&
            it.episodeNumber != null &&
            it.progress > 0f &&
            it.progress < PLAYBACK_COMPLETION_THRESHOLD &&
            Triple(show.id, it.season, it.episodeNumber) !in watched
    }
    // The resume session is an input to selection, not a post-selection
    // decoration. It must survive even if its episode metadata is temporarily
    // absent from Room.
    if (session != null) return session.copy(media = show)

    val computed = selectNextProgressEpisode(
        show.id,
        episodes,
        watched,
        now,
        zone,
        excludeSpecials,
    )
    fun storedIsEligible(candidate: EpisodeCard): Boolean {
        val air = releaseDateTime(candidate.airDate, zone)?.toInstant()
        return air == null || !air.isAfter(now) ||
            air.toEpochMilli() - now.toEpochMilli() <= UPCOMING_PROGRESS_ATTENTION_DAYS * 86_400_000L
    }
    val next = when {
        computed == null -> storedNext?.takeIf(::storedIsEligible)
        storedNext == null || !storedIsEligible(storedNext) -> computed
        else -> if (
            compareBy<EpisodeCard> { it.season }.thenBy { it.number }
                .compare(computed, storedNext) <= 0
        ) computed else storedNext
    } ?: return null

    return PlaybackCard(
        media = show,
        episodeId = next.id.takeIf { it > 0 },
        episodeLabel = "S${next.season.toString().padStart(2, '0')} E${next.number.toString().padStart(2, '0')}",
        episodeTitle = next.title,
        season = next.season,
        episodeNumber = next.number,
        progress = 0f,
        remainingMinutes = null,
        durationMinutes = next.runtimeMinutes ?: show.runtimeMinutes,
        episodeAirDate = next.airDate,
        progressUpdatedAtMillis = 0L,
    )
}

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
        val releaseZone = localZone()
        val releaseNow = java.time.Instant.now()
        val excludeSpecials = preferences.excludeSpecials.first()
        val cachedByShow = (cachedEpisodes ?: database.mediaDao().episodeSnapshot().map { it.toEpisodeCard() })
            .groupBy(EpisodeCard::showId)
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
                            fun nextFrom(source: List<EpisodeCard>): EpisodeCard? =
                                selectNextProgressEpisode(show.id, source, watched, releaseNow, releaseZone, excludeSpecials)

                            var candidates = cachedByShow[show.id].orEmpty()
                            var episode = nextFrom(candidates)
                            if (tmdbApiKey().isNotBlank()) {
                                val seasonCounts = show.seasons
                                    .filter { it.number > 0 }
                                    .associate { it.number to it.episodeCount }
                                val seasons = (seasonCounts.keys + candidates.map { it.season })
                                    .filter { it > 0 }
                                    .distinct()
                                    .sorted()
                                for (season in seasons) {
                                    val expected = seasonCounts[season] ?: 0
                                    val cachedNumbers = candidates.asSequence()
                                        .filter { it.season == season }
                                        .map(EpisodeCard::number)
                                        .toSet()
                                    val incomplete = expected > 0 &&
                                        (1..expected).any { it !in cachedNumbers }
                                    // A sparse late episode is not evidence that
                                    // earlier episodes do not exist. Fetch the
                                    // minimal incomplete season before accepting
                                    // a candidate from it.
                                    if (episode != null && episode.season < season) break
                                    if (episode != null && episode.season == season && !incomplete) break
                                    if (!incomplete && episode == null) continue
                                    val fetched = runCatching { loadEpisodes(show, season) }.getOrDefault(emptyList())
                                    candidates = (candidates + fetched).distinctBy { it.season to it.number }
                                    episode = nextFrom(candidates)
                                    if (episode != null && episode.season <= season) break
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

    private suspend fun localZone(): ZoneId {
        val configured = preferences.metadataTimezone.first()
        return if (configured == "system") ZoneId.systemDefault()
        else runCatching { ZoneId.of(configured) }.getOrDefault(ZoneId.systemDefault())
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

