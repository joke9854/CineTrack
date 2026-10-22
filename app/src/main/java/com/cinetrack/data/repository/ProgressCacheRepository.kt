package com.cinetrack.data.repository

import androidx.room.withTransaction
import com.cinetrack.data.local.MediaEntity
import com.cinetrack.data.local.SyncStateEntity
import com.cinetrack.data.local.UpNextEntity
import com.cinetrack.data.local.toDomain
import com.cinetrack.data.local.toEntity
import com.cinetrack.data.remote.ApiServices
import com.cinetrack.data.schedule.ReleaseScheduleRepository
import com.cinetrack.domain.AppUiState
import com.cinetrack.domain.LibraryArtworkRefreshProgress
import com.cinetrack.domain.LibraryArtworkRefreshStage
import com.cinetrack.domain.LibraryStatus
import com.cinetrack.domain.MediaType
import com.cinetrack.domain.RailIds
import com.cinetrack.domain.TimelineCard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
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
import com.cinetrack.data.remote.tmdbImageUrl

/** Future schedule events only get attention priority when they are imminent. */
internal const val UPCOMING_PROGRESS_ATTENTION_DAYS: Long = 7L
private const val PLAYBACK_COMPLETION_THRESHOLD = 1f

/**
 * The one "next episode" rule for Progress: the first released, unwatched
 * episode after the furthest regular episode watched. An earlier skipped
 * episode is not offered; without any regular history the earliest candidate
 * is. [candidates] are released and unwatched, sorted by season and number.
 */
internal fun nextAfterLastWatched(candidates: List<EpisodeCard>, watched: Collection<Pair<Int, Int>>): EpisodeCard? {
    val last = watched.asSequence()
        .filter { it.first > 0 }
        .maxWithOrNull(compareBy<Pair<Int, Int>>({ it.first }, { it.second }))
        ?: return candidates.firstOrNull()
    return candidates.firstOrNull { it.season > last.first || (it.season == last.first && it.number > last.second) }
}

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
    val showWatched = watched.asSequence().filter { it.first == showId }.map { it.second to it.third }.toList()
    nextAfterLastWatched(aired, showWatched)?.let { return it }
    // Unknown release metadata is not proof of availability. The cache loader
    // refreshes the relevant season instead of rendering a speculative card.
    return null
}


/** Seasons to try first: the last watched one and the next, or without any
 * history the first regular season the show is known to have. */
internal fun progressStartSeasons(lastWatchedSeason: Int?, knownSeasons: Collection<Int>): List<Int> =
    lastWatchedSeason?.let { listOf(it, it + 1) }
        ?: listOf(knownSeasons.filter { it > 0 }.minOrNull() ?: 1)

/** Whether a season's cached episodes cannot be trusted to contain the next
 * episode. With a known episode count every number must be cached; without
 * one, a gap (for example only a schedule row for E8) means earlier episodes
 * were never fetched. */
internal fun cachedSeasonIncomplete(cachedNumbers: Set<Int>, expected: Int): Boolean = when {
    cachedNumbers.isEmpty() -> true
    expected > 0 -> (1..expected).any { it !in cachedNumbers }
    else -> (1..cachedNumbers.max()).any { it !in cachedNumbers }
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
        // Unknown/stale release metadata must not preserve a speculative
        // durable row after current metadata says the episode is unavailable.
        return air != null && !air.isAfter(now)
    }
    // Both follow the same rule; the stored row only fills in when the next
    // episode's metadata is not cached here.
    val next = computed ?: storedNext?.takeIf { stored ->
        storedIsEligible(stored) &&
            Triple(show.id, stored.season, stored.number) !in watched &&
            nextAfterLastWatched(
                listOf(stored),
                watched.asSequence().filter { it.first == show.id }.map { it.second to it.third }.toList(),
            ) != null
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
 * TrackingSyncHost facade, preserving the existing mutex boundaries.
 */
class ProgressCacheRepository(
    private val database: AppDatabase,
    private val preferences: AppPreferences,
    private val tmdbApiKey: () -> String,
    private val loadEpisodes: suspend (MediaCard, Int) -> List<EpisodeCard>,
    private val services: ApiServices,
    private val releaseScheduleRepository: ReleaseScheduleRepository,
    private val mediaMetadata: MediaMetadataRepository,
    private val loadCachedState: suspend () -> AppUiState,
) {
    private val episodeTitleCache = BoundedLruCache<String, String>(750)
    /** Episodes TMDB answered without a title (or not at all), not asked again this process. */
    private val episodeTitleMisses = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val progressCacheMutex = Mutex()
    private val progressEnrichmentScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var progressEnrichmentJob: Job? = null
    @Volatile private var progressEnrichmentRequested = false
    val progressCacheRefreshing: Boolean get() = progressCacheMutex.isLocked

    fun clearEpisodeTitleCache() = episodeTitleCache.clear()

    /**
     * Advances one show's durable next-episode row using Room only. This runs
     * immediately after a watched-state change, so neither Compose nor the
     * database invalidation observer has to wait for Simkl or a full cache pass.
     */
    private suspend fun refreshLocalUpNext(showId: Int) {
        val releaseZone = localZone()
        val releaseNow = Instant.now()
        val excludeSpecials = preferences.excludeSpecials.first()
        val watched = mediaMetadata.watchedEpisodeNumbers(showId)
        val candidates = database.mediaDao().episodesForShow(showId).asSequence()
            .map { it.toDomain() }
            .filter { !excludeSpecials || it.season > 0 }
            .filter { episode ->
                releaseDateTime(episode.airDate, releaseZone)?.toInstant()?.let { !it.isAfter(releaseNow) } == true
            }
            .filterNot { (it.season to it.number) in watched }
            .sortedWith(compareBy(EpisodeCard::season, EpisodeCard::number))
            .toList()
        val next = nextAfterLastWatched(candidates, watched)
        database.withTransaction {
            database.upNextDao().delete(showId)
            if (next != null) {
                database.upNextDao().upsertAll(
                    listOf(
                        UpNextEntity(
                            showId = showId,
                            episodeId = next.id.takeIf { it > 0 },
                            season = next.season,
                            episodeNumber = next.number,
                            episodeTitle = next.title,
                            episodeAirDate = next.airDate,
                            durationMinutes = next.runtimeMinutes,
                            refreshedAt = System.currentTimeMillis(),
                        ),
                    ),
                )
            }
        }
    }

    suspend fun enrichHistoryLabels(items: List<TimelineCard>): List<TimelineCard> {
        // Keep this call's results until its output is built. The process-wide
        // LRU may evict early titles when an import contains more than 750 rows.
        val resolvedTitles = mutableMapOf<String, String>()
        val missing = items.filter {
            it.media.type == MediaType.TV && it.season != null && it.episodeNumber != null &&
                (it.episodeLabel.isNullOrBlank() || !it.episodeLabel.contains(" · "))
        }.distinctBy { "${it.media.id}:${it.season}:${it.episodeNumber}" }
            .filterNot { "${it.media.id}:${it.season}:${it.episodeNumber}" in episodeTitleMisses }
        // Plays whose season was read as a whole: a number that season does not
        // list (an absolutely numbered anime play) has no single page either.
        val seasonRead = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
        missing.forEach { item ->
            val key = "${item.media.id}:${item.season}:${item.episodeNumber}"
            if (episodeTitleCache[key].isNullOrBlank()) {
                database.mediaDao().episode(item.media.id, item.season!!, item.episodeNumber!!)
                    ?.title?.takeIf(String::isNotBlank)?.let { episodeTitleCache[key] = it }
            }
        }
        // A season with several untitled plays (a fresh import of a long show)
        // is read in one request instead of one request per episode.
        if (tmdbApiKey().isNotBlank()) {
            val untitledSeasons = missing
                .filter { episodeTitleCache["${it.media.id}:${it.season}:${it.episodeNumber}"].isNullOrBlank() }
                .groupBy { it.media.id to it.season!! }
                .filterValues { it.size >= SEASON_TITLE_FETCH_MIN }
            for (batch in untitledSeasons.entries.chunked(4)) {
                coroutineScope {
                    batch.map { (showSeason, plays) ->
                        async {
                            val (showId, season) = showSeason
                            val fetched = runCatching { services.tmdb.season(showId, season).episodes }
                            val titles = fetched.getOrDefault(emptyList())
                                .filter { it.name.isNotBlank() }
                                .associate { it.number to it.name }
                            plays.forEach { play ->
                                val key = "$showId:$season:${play.episodeNumber}"
                                seasonRead += key
                                val title = titles[play.episodeNumber!!]
                                if (title == null) {
                                    if (fetched.isSuccess || fetched.isTmdbNotFound()) episodeTitleMisses += key
                                    return@forEach
                                }
                                episodeTitleCache["$showId:$season:${play.episodeNumber}"] = title
                                database.timelineDao().updateEpisodeTitle(MediaType.TV.name, showId, season, play.episodeNumber, title)
                            }
                        }
                    }.awaitAll()
                }
            }
        }
        for (batch in missing.chunked(6)) {
            coroutineScope {
                batch.map { item ->
                    async {
                        val key = "${item.media.id}:${item.season}:${item.episodeNumber}"
                        if (episodeTitleCache[key].isNullOrBlank()) {
                            database.mediaDao().episode(item.media.id, item.season!!, item.episodeNumber!!)
                                ?.title?.takeIf(String::isNotBlank)?.let { episodeTitleCache[key] = it }
                        }
                        if (episodeTitleCache[key].isNullOrBlank() && key !in seasonRead && tmdbApiKey().isNotBlank()) {
                            val fetched = runCatching { services.tmdb.episode(item.media.id, item.season!!, item.episodeNumber!!).name }
                            if (fetched.getOrNull().isNullOrBlank() && (fetched.isSuccess || fetched.isTmdbNotFound())) episodeTitleMisses += key
                            fetched.getOrNull()?.takeIf(String::isNotBlank)?.let { title ->
                                    episodeTitleCache[key] = title
                                    database.timelineDao().updateEpisodeTitle(MediaType.TV.name, item.media.id, item.season!!, item.episodeNumber!!, title)
                                }
                        }
                        key to episodeTitleCache[key]
                    }
                }.forEach { pending ->
                    val (key, title) = pending.await()
                    if (!title.isNullOrBlank()) resolvedTitles[key] = title
                }
            }
        }
        return items.map { item ->
            if (item.media.type != MediaType.TV || item.season == null || item.episodeNumber == null) item
            else {
                val existingTitle = item.episodeLabel?.substringAfter(" · ", "")?.takeIf(String::isNotBlank)
                val key = "${item.media.id}:${item.season}:${item.episodeNumber}"
                val title = resolvedTitles[key] ?: episodeTitleCache[key] ?: existingTitle
                val number = "S${item.season.toString().padStart(2, '0')} E${item.episodeNumber.toString().padStart(2, '0')}"
                item.copy(episodeLabel = listOfNotNull(number, title?.takeIf(String::isNotBlank)).joinToString(" · "))
            }
        }
    }

    /**
     * Refreshes only the durable inputs used by Progress. Remote responses are
     * written to Room first; callers then publish a fresh database snapshot.
     * Nothing returned by the network is exposed directly to Compose.
     */
    suspend fun refreshProgressCache(
        request: ProgressRefreshRequest,
        onProgress: ((Float) -> Unit)?,
    ): Boolean = progressCacheMutex.withLock {
        onProgress?.invoke(.82f)
        val snapshot = database.progressSnapshotDao().snapshot()
        val states = snapshot.states.associateBy { it.mediaId }
        val libraryShows = snapshot.media.map { media -> media.toDomain(states[media.tmdbId]) }
            .distinctBy(MediaCard::stableKey)
        val progressShows = libraryShows.filter {
            it.status in setOf(LibraryStatus.WATCHING, LibraryStatus.COMPLETED)
        }
        val watched = snapshot.history.mapNotNull { item ->
            val season = item.season ?: return@mapNotNull null
            val episode = item.episodeNumber ?: return@mapNotNull null
            Triple(item.mediaId, season, episode)
        }.toSet()
        val now = System.currentTimeMillis()
        val scheduleFreshness = 5L * 60L * 60L * 1_000L
        val scheduleState = database.syncDao().get("simkl_calendar")
        val scheduleDue = libraryShows.isNotEmpty() &&
            (scheduleState == null || now - scheduleState.lastSuccessfulSync >= scheduleFreshness)
        val cacheReady = database.syncDao().get("up_next_cache_v1") != null
        val needsUpNext = request.requiresUpNext(scheduleDue, cacheMissing = !cacheReady)
        if (!needsUpNext) {
            onProgress?.invoke(.99f)
            scheduleProgressEnrichment()
            return@withLock false
        }

        val needsSchedule = request.force || request.tvLibraryChanged || scheduleDue
        if (needsSchedule) {
            // Upcoming metadata populates the episode cache first; up-next then
            // reuses it instead of issuing duplicate recent-season requests.
            loadUpcomingEpisodes(
                libraryShows,
                forceSchedule = request.force || request.tvLibraryChanged,
            )
        }
        onProgress?.invoke(.88f)
        val refreshedEpisodes = database.progressSnapshotDao().activeTvEpisodes().map { it.toDomain() }
        val upNext = loadUpNextEpisodes(
            shows = progressShows,
            watched = watched,
            cachedEpisodes = refreshedEpisodes,
        ) { processed, total ->
            val fraction = if (total == 0) 1f else processed.toFloat() / total.toFloat()
            onProgress?.invoke(.88f + (.09f * fraction))
        }

        val refreshedAt = System.currentTimeMillis()
        database.withTransaction {
            // Network work used an earlier history snapshot. Rebase only the
            // shows changed during that work, without delaying a local tap.
            val latest = database.progressSnapshotDao().snapshot()
            val latestWatched = latest.history.mapNotNull { row ->
                val season = row.season ?: return@mapNotNull null
                val number = row.episodeNumber ?: return@mapNotNull null
                Triple(row.mediaId, season, number)
            }.toSet()
            val eligible = latest.states.filter {
                it.status in setOf(LibraryStatus.WATCHING.name, LibraryStatus.COMPLETED.name)
            }.map { it.mediaId }.toSet()
            val changedShows = changedEpisodeShows(watched, latestWatched) +
                (eligible - progressShows.map { it.id }.toSet())
            database.upNextDao().clear()
            val rows = upNext.values.filter { it.showId in eligible }.map { episode ->
                UpNextEntity(
                    showId = episode.showId,
                    episodeId = episode.id.takeIf { it > 0 },
                    season = episode.season,
                    episodeNumber = episode.number,
                    episodeTitle = episode.title,
                    episodeAirDate = episode.airDate,
                    durationMinutes = episode.runtimeMinutes,
                    refreshedAt = refreshedAt,
                )
            }
            if (rows.isNotEmpty()) database.upNextDao().upsertAll(rows)
            changedShows.filter { it in eligible }.forEach { refreshLocalUpNext(it) }
            database.syncDao().upsertAll(
                listOf(
                    SyncStateEntity("progress_cache", Instant.ofEpochMilli(refreshedAt).toString(), refreshedAt),
                    SyncStateEntity("up_next_cache_v1", Instant.ofEpochMilli(refreshedAt).toString(), refreshedAt),
                ),
            )
        }
        onProgress?.invoke(.99f)
        scheduleProgressEnrichment()
        true
    }

    /**
     * Artwork and missing history labels improve presentation but are not needed
     * to make imported library/history/playback data correct. Run them after the
     * visible sync has completed and coalesce requests that arrive while a repair
     * pass is already active.
     */
    fun scheduleProgressEnrichment() {
        progressEnrichmentRequested = true
        if (progressEnrichmentJob?.isActive == true) return
        progressEnrichmentJob = progressEnrichmentScope.launch {
            do {
                progressEnrichmentRequested = false
                val state = loadCachedState()
                coroutineScope {
                    val activeLibrary = state.rails[RailIds.LIBRARY].orEmpty().filter {
                        it.status != LibraryStatus.NONE && it.status != LibraryStatus.DROPPED
                    }
                    val artworkTargets = activeLibrary + state.playbackTv.map(PlaybackCard::media) +
                        state.playbackMovies.map(PlaybackCard::media) + state.calendar.map(TimelineCard::media) +
                        state.history.map(TimelineCard::media)
                    val artworkRepair = async { refreshMissingArtwork(artworkTargets) }
                    val historyRepair = async { enrichHistoryLabels(state.history) }
                    artworkRepair.await()
                    historyRepair.await()
                }
            } while (progressEnrichmentRequested)
        }
    }

    /**
     * Simkl's public calendar is regenerated every few hours and is more precise
     * for currently airing shows. It supplies the canonical date while existing
     * TMDB rows continue to provide localized titles, stills and runtimes.
     */
    private suspend fun refreshSimklSchedule(shows: List<MediaCard>, force: Boolean = false): Boolean =
        releaseScheduleRepository.refresh(shows, force)

    private suspend fun refreshMissingArtwork(library: List<MediaCard>) {
        if (tmdbApiKey().isBlank()) return
        val missing = library.distinctBy(MediaCard::stableKey).filter { media ->
            media.posterUrl.isNullOrBlank() || media.backdropUrl.isNullOrBlank()
        }
        if (missing.isEmpty()) return
        val requests = Semaphore(permits = 10)
        val enriched = coroutineScope {
            missing.map { media ->
                async {
                    requests.withPermit {
                        runCatching {
                            val current = database.mediaDao().get(media.type.name, media.id)
                                ?: media.toEntity()
                            val details = if (media.type == MediaType.TV) {
                                services.tmdb.show(media.id, append = "")
                            } else {
                                services.tmdb.movie(media.id, append = "")
                            }.toEntity(media.type)
                            details.copy(
                                title = details.title.ifBlank { current.title },
                                overview = details.overview.ifBlank { current.overview },
                                posterPath = details.posterPath ?: current.posterPath,
                                backdropPath = details.backdropPath ?: current.backdropPath,
                                releaseDate = details.releaseDate ?: current.releaseDate,
                                score = details.score ?: current.score,
                                runtimeMinutes = details.runtimeMinutes ?: current.runtimeMinutes,
                                genres = details.genres.ifBlank { current.genres },
                                providers = details.providers.ifBlank { current.providers },
                                collectionId = details.collectionId ?: current.collectionId,
                            )
                        }.getOrNull()
                    }
                }
            }.mapNotNull { it.await() }
        }
        if (enriched.isNotEmpty()) database.mediaDao().upsertMedia(enriched)
    }

    /**
     * Explicit user-requested metadata refresh for every active library item.
     * This deliberately touches only MediaEntity rows: tracking state,
     * playback, history, sync generations and provider deliveries remain
     * completely independent of artwork enrichment.
     */
    suspend fun refreshLibraryArtwork(
        onProgress: suspend (LibraryArtworkRefreshProgress) -> Unit = {},
    ): LibraryArtworkRefreshProgress {
        check(tmdbApiKey().isNotBlank()) { "TMDB API credential is missing" }
        val snapshot = database.snapshotDao().snapshot()
        val states = snapshot.states.associateBy { it.mediaType to it.mediaId }
        val libraryKeys = snapshot.rails.filter { it.railId == RailIds.LIBRARY }
            .map { it.mediaType to it.mediaId }
            .toSet()
        val targets = snapshot.media
            .filter { (it.mediaType to it.tmdbId) in libraryKeys }
            .filter { states[it.mediaType to it.tmdbId]?.status !in setOf(LibraryStatus.NONE.name, LibraryStatus.DROPPED.name) }
            .distinctBy { "${it.mediaType}:${it.tmdbId}" }
        if (targets.isEmpty()) {
            val empty = LibraryArtworkRefreshProgress(LibraryArtworkRefreshStage.COMPLETE)
            onProgress(empty)
            return empty
        }
        var processed = 0
        var changed = 0
        var unchanged = 0
        var failed = 0
        val requestSlots = Semaphore(permits = 5)
        preferences.appendErrorLog("Artwork refresh started: ${targets.size} targets")
        onProgress(LibraryArtworkRefreshProgress(LibraryArtworkRefreshStage.PREPARING, total = targets.size))
        targets.chunked(20).forEachIndexed { batchIndex, batch ->
            val outcomes = coroutineScope {
                batch.map { current ->
                    async(Dispatchers.IO) {
                        requestSlots.withPermit {
                            try {
                                kotlinx.coroutines.withTimeout(45_000L) {
                                val type = MediaType.valueOf(current.mediaType)
                                val details = if (type == MediaType.TV) services.tmdb.show(current.tmdbId, append = "")
                                else services.tmdb.movie(current.tmdbId, append = "")
                                val merged = details.toEntity(type).copy(
                                    title = details.title?.ifBlank { current.title } ?: details.name?.ifBlank { current.title } ?: current.title,
                                    overview = details.overview.ifBlank { current.overview },
                                    posterPath = details.posterPath ?: current.posterPath,
                                    backdropPath = details.backdropPath ?: current.backdropPath,
                                    releaseDate = details.releaseDate ?: details.firstAirDate ?: current.releaseDate,
                                    score = details.voteAverage ?: current.score,
                                    runtimeMinutes = details.runtime ?: details.episodeRunTime.firstOrNull() ?: current.runtimeMinutes,
                                    genres = details.genres.let { genres -> if (genres.isEmpty()) current.genres else genres.joinToString("|") { it.name } },
                                    providers = details.watchProviders?.results?.get("IT")?.let { (it.flatrate + it.rent + it.buy).distinctBy { p -> p.id }.joinToString("|") { p -> p.name } }?.takeIf(String::isNotBlank) ?: current.providers,
                                    collectionId = details.collection?.id ?: current.collectionId,
                                )
                                    ArtworkRefreshOutcome(current, merged, merged.presentationFingerprint() != current.presentationFingerprint(), null)
                                }
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (error: retrofit2.HttpException) {
                                if (error.code() == 401 || error.code() == 403) {
                                    throw IllegalStateException("TMDB credential was rejected")
                                }
                                ArtworkRefreshOutcome(current, null, false, errorCategory(error))
                            } catch (error: Throwable) {
                                ArtworkRefreshOutcome(current, null, false, errorCategory(error))
                            }
                        }
                    }
                }.awaitAll()
            }
            if (outcomes.isNotEmpty() && outcomes.all { it.entity == null && it.errorType == "NETWORK" }) {
                preferences.appendErrorLog("Artwork refresh paused: TMDB network unavailable at ${processed}/${targets.size}")
                throw IOException("TMDB network unavailable")
            }
            val updates = outcomes.mapNotNull { it.entity.takeIf { _ -> it.changed } }
            if (updates.isNotEmpty()) {
                onProgress(LibraryArtworkRefreshProgress(LibraryArtworkRefreshStage.APPLYING, processed, targets.size, changed, unchanged, failed))
                database.mediaDao().upsertMedia(updates)
            }
            outcomes.forEach { outcome ->
                processed++
                if (outcome.entity == null) {
                    failed++
                    preferences.appendErrorLog("Artwork refresh: ${outcome.current.mediaType}:${outcome.current.tmdbId} failed ${outcome.errorType ?: "UNKNOWN"}")
                }
                else if (outcome.changed) changed++ else unchanged++
                onProgress(
                    LibraryArtworkRefreshProgress(
                        stage = LibraryArtworkRefreshStage.REFRESHING,
                        processed = processed,
                        total = targets.size,
                        changed = changed,
                        unchanged = unchanged,
                        failed = failed,
                        currentTitle = outcome.current.title,
                    ),
                )
            }
            if ((batchIndex + 1) % 4 == 0 || processed == targets.size) {
                preferences.appendErrorLog("Artwork refresh: batch ${batchIndex + 1}, processed $processed/${targets.size}")
            }
        }
        val finalStage = if (failed == 0) LibraryArtworkRefreshStage.COMPLETE else LibraryArtworkRefreshStage.COMPLETE_WITH_ERRORS
        return LibraryArtworkRefreshProgress(finalStage, processed, targets.size, changed, unchanged, failed).also {
            preferences.appendErrorLog("Artwork refresh complete: changed=$changed unchanged=$unchanged failed=$failed")
            onProgress(it)
        }
    }

    private data class ArtworkRefreshOutcome(
        val current: MediaEntity,
        val entity: MediaEntity?,
        val changed: Boolean,
        val errorType: String?,
    )

    private fun MediaEntity.presentationFingerprint(): String = listOf(
        title, overview, posterPath, backdropPath, releaseDate, score, runtimeMinutes, genres, providers, collectionId,
    ).joinToString("|")

    private fun errorCategory(error: Throwable): String = when (error) {
        is kotlinx.coroutines.TimeoutCancellationException -> "TIMEOUT"
        is retrofit2.HttpException -> "HTTP_${error.code()}"
        is java.io.IOException -> "NETWORK"
        else -> error::class.java.simpleName.take(48)
    }

    /** Resolve an episode title from the local Room cache without a network request. */
    suspend fun cachedEpisodeTitle(showId: Int, season: Int, episode: Int): String? =
        database.mediaDao().episode(showId, season, episode)?.title?.takeIf(String::isNotBlank)

    suspend fun loadUpcomingEpisodes(
        shows: List<MediaCard>,
        forceSchedule: Boolean = false,
    ): List<EpisodeCard> {
        val today = LocalDate.now(localZone())
        val excludeSpecials = preferences.excludeSpecials.first()
        val distinctShows = shows.distinctBy(MediaCard::stableKey)
        val trackedShowIds = distinctShows.map(MediaCard::id).toSet()
        if (trackedShowIds.isEmpty()) return emptyList()
        refreshSimklSchedule(distinctShows, force = forceSchedule)
        val cached = database.mediaDao().episodeSnapshot().map { it.toDomain() }.filter { episode ->
            episode.showId in trackedShowIds && (!excludeSpecials || episode.season > 0) && episode.airDate?.take(10)?.let { raw ->
                runCatching { !LocalDate.parse(raw).isBefore(today) }.getOrDefault(false)
            } == true
        }
        if (tmdbApiKey().isBlank()) return cached.sortedBy(EpisodeCard::airDate)
        val now = System.currentTimeMillis()
        val scheduleFreshnessMillis = 8L * 60L * 60L * 1_000L
        val scheduleChecks = database.snapshotDao().syncStates().associateBy(SyncStateEntity::area)
        val showsDueForSchedule = distinctShows.filter { show ->
            val lastCheck = scheduleChecks["schedule:${show.id}"]?.lastSuccessfulSync
            lastCheck == null || now - lastCheck >= scheduleFreshnessMillis
        }
        if (showsDueForSchedule.isEmpty()) return cached.sortedBy(EpisodeCard::airDate)
        val requestSlots = Semaphore(permits = 5)
        val remoteResults = coroutineScope {
            showsDueForSchedule.map { show ->
                async {
                    val result = requestSlots.withPermit {
                        runCatching<List<EpisodeCard>> {
                            // The schedule only needs the base show payload. Avoid
                            // downloading credits, recommendations and videos for
                            // every tracked show during a Progress refresh.
                            val showDetails = services.tmdb.show(show.id, append = "")
                            val seasonNumbers = showDetails.seasons
                                .asSequence()
                                .map { it.number }
                                .filter { !excludeSpecials || it > 0 }
                                .sortedDescending()
                                .take(2)
                                .toList()
                            val announcedNext = listOfNotNull(showDetails.nextEpisodeToAir).mapNotNull { episode ->
                                val airDay = episode.airDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                                    ?: return@mapNotNull null
                                if (airDay.isBefore(today)) return@mapNotNull null
                                EpisodeCard(
                                    id = episode.id,
                                    showId = show.id,
                                    season = episode.season,
                                    number = episode.number,
                                    title = episode.name,
                                    overview = episode.overview,
                                    airDate = episode.airDate,
                                    stillUrl = tmdbImageUrl(episode.stillPath, "w780"),
                                    runtimeMinutes = episode.runtime,
                                )
                            }
                            (announcedNext + seasonNumbers.flatMap { season ->
                                services.tmdb.season(show.id, season).episodes.mapNotNull { episode ->
                                    val airDay = episode.airDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                                        ?: return@mapNotNull null
                                    if (airDay.isBefore(today)) return@mapNotNull null
                                    EpisodeCard(
                                        id = episode.id,
                                        showId = show.id,
                                        season = episode.season,
                                        number = episode.number,
                                        title = episode.name,
                                        overview = episode.overview,
                                        airDate = episode.airDate,
                                        stillUrl = tmdbImageUrl(episode.stillPath, "w780"),
                                        runtimeMinutes = episode.runtime,
                                    )
                                }
                            }).distinctBy { episode -> episode.season to episode.number }.sortedBy { it.airDate }
                        }
                    }
                    show.id to result
                }
            }.map { it.await() }
        }
        val cachedDates = cached.associate { episode ->
            Triple(episode.showId, episode.season, episode.number) to episode.airDate
        }
        val fetched = remoteResults.flatMap { (_, result) -> result.getOrDefault(emptyList()) }
        // TMDB fills the full episode model; the date shown is the chosen provider's.
        val shownDates = com.cinetrack.data.schedule.recordAirDates(
            database, com.cinetrack.data.schedule.AirDateSource.TMDB,
            fetched.associate { Triple(it.showId, it.season, it.number) to it.airDate },
            preferences.airDateSource.first(),
            legacy = cachedDates,
        )
        val remote = fetched.map { episode ->
            episode.copy(airDate = shownDates[Triple(episode.showId, episode.season, episode.number)] ?: episode.airDate)
        }
        val completedChecks = remoteResults.mapNotNull { (showId, result) ->
            if (result.isSuccess) {
                SyncStateEntity("schedule:$showId", remoteTimestamp = null, lastSuccessfulSync = now)
            } else null
        }
        if (remote.isNotEmpty() || completedChecks.isNotEmpty()) {
            database.withTransaction {
                if (remote.isNotEmpty()) database.mediaDao().upsertEpisodes(remote.map { it.toEntity() })
                if (completedChecks.isNotEmpty()) database.syncDao().upsertAll(completedChecks)
            }
        }
        // A failed/partial season request must not erase the last valid schedule.
        // Key by show/season/number because TMDB episode IDs can be absent in old
        // imported cache rows.
        return (cached + remote)
            .distinctBy { "${it.showId}:${it.season}:${it.number}" }
            .sortedBy(EpisodeCard::airDate)
    }

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
                            fun nextFrom(source: List<EpisodeCard>): EpisodeCard? =
                                selectNextProgressEpisode(show.id, source, watched, releaseNow, releaseZone, excludeSpecials)

                            var candidates = cachedByShow[show.id].orEmpty()
                            var episode = nextFrom(candidates)
                            if (tmdbApiKey().isNotBlank()) {
                                val seasonCounts = show.seasons
                                    .filter { it.number > 0 }
                                    .associate { it.number to it.episodeCount }
                                // Room's compact MediaEntity does not carry
                                // SeasonCard metadata. Derive the first
                                // required season from durable watch history
                                // so a cold start never depends on DetailScreen.
                                val lastWatchedSeason = watchedByShow[show.id].orEmpty()
                                    .asSequence()
                                    .filter { it.second > 0 }
                                    .maxWithOrNull(compareBy<Triple<Int, Int, Int>>({ it.second }, { it.third }))
                                    ?.second
                                val historySeasons = progressStartSeasons(
                                    lastWatchedSeason,
                                    seasonCounts.keys + candidates.map { it.season },
                                )
                                val seasons = (historySeasons + candidates.map { it.season } + seasonCounts.keys)
                                    .filter { it > 0 }
                                    .distinct()
                                    .sorted()
                                for (season in seasons) {
                                    val expected = seasonCounts[season] ?: 0
                                    val cachedNumbers = candidates.asSequence()
                                        .filter { it.season == season }
                                        .map(EpisodeCard::number)
                                        .toSet()
                                    val incomplete = cachedSeasonIncomplete(cachedNumbers, expected)
                                    // A sparse late episode is not evidence
                                    // that earlier episodes do not exist.
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
    stillUrl = tmdbImageUrl(stillPath, "w780"),
    runtimeMinutes = runtimeMinutes,
)

/** Untitled plays in one season above which the whole season is fetched once. */
private const val SEASON_TITLE_FETCH_MIN = 3

private fun Result<*>.isTmdbNotFound(): Boolean = (exceptionOrNull() as? retrofit2.HttpException)?.code() == 404

data class ProgressRefreshRequest(
    val tvLibraryChanged: Boolean = false,
    val episodeHistoryChanged: Boolean = false,
    val tvPlaybackChanged: Boolean = false,
    val force: Boolean = false,
) {
    fun requiresUpNext(scheduleDue: Boolean, cacheMissing: Boolean): Boolean =
        force || scheduleDue || cacheMissing || tvLibraryChanged || episodeHistoryChanged || tvPlaybackChanged

    fun mergedWith(other: ProgressRefreshRequest) = ProgressRefreshRequest(
        tvLibraryChanged = tvLibraryChanged || other.tvLibraryChanged,
        episodeHistoryChanged = episodeHistoryChanged || other.episodeHistoryChanged,
        tvPlaybackChanged = tvPlaybackChanged || other.tvPlaybackChanged,
        force = force || other.force,
    )
}

/** Canonical activity ordering for a progress card. Air dates are schedule
 * metadata and intentionally never contribute to viewing recency. */
internal fun playbackActivityRecency(item: PlaybackCard): Long = maxOf(
    item.media.libraryUpdatedAt ?: 0L,
    item.progressUpdatedAtMillis,
)

/**
 * The digital release (TMDB release type 4) for [region], as (yyyy-MM-dd, country). Falls back to
 * the US date, which TMDB covers best, when the region has none. The earliest digital date wins.
 */
internal fun digitalReleaseFor(releaseDates: com.cinetrack.data.remote.TmdbReleaseDatesDto?, region: String): Pair<String, String>? {
    fun earliest(country: String) = releaseDates?.results
        ?.firstOrNull { it.country.equals(country, ignoreCase = true) }
        ?.dates?.filter { it.type == 4 && it.date.length >= 10 }
        ?.minOfOrNull { it.date.take(10) }
    return earliest(region)?.let { it to region.uppercase() }
        ?: earliest("US")?.let { it to "US" }
}

internal fun compareProgressAttention(
    left: PlaybackCard,
    right: PlaybackCard,
    latestWatchedAtByShow: Map<Int, Long>,
    nowMillis: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): Int {
    fun key(item: PlaybackCard): Triple<Long, Boolean, Long> {
        val activity = maxOf(latestWatchedAtByShow[item.media.id] ?: 0L, playbackActivityRecency(item))
        val air = if (item.media.type == MediaType.TV) {
            releaseDateTime(item.episodeAirDate, zone)?.toInstant()?.toEpochMilli()
        } else null
        val attentionWindow = UPCOMING_PROGRESS_ATTENTION_DAYS * 86_400_000L
        val upcoming = air?.takeIf { it > nowMillis && it - nowMillis <= attentionWindow }
        // A newly aired selected episode is still meaningful attention for a
        // short window. Once it is older than that window, ordinary viewing
        // activity regains priority. This lets an episode airing now surface
        // without allowing schedule metadata to permanently dominate.
        val recentAir = air?.takeIf { it <= nowMillis && nowMillis - it <= attentionWindow }
        // Viewing newer than the air date wins: a show the user just advanced
        // by hand must not drop below one whose episode merely aired recently.
        val event = upcoming ?: maxOf(recentAir ?: 0L, activity)
        return Triple(kotlin.math.abs(event - nowMillis), upcoming != null, event)
    }
    val a = key(left)
    val b = key(right)
    val distanceDelta = a.first - b.first
    // A near tie between a past viewing event and an imminent future episode
    // favors the upcoming attention signal, without allowing distant air dates
    // to dominate real viewing activity.
    if (kotlin.math.abs(distanceDelta) <= 5 * 60_000L && a.second != b.second) {
        return if (a.second) -1 else 1
    }
    if (distanceDelta != 0L) return distanceDelta.compareTo(0L)
    if (a.second != b.second) return if (a.second) -1 else 1
    val eventDelta = b.third - a.third
    if (eventDelta != 0L) return eventDelta.compareTo(0L)
    return left.media.title.lowercase().compareTo(right.media.title.lowercase())
}
