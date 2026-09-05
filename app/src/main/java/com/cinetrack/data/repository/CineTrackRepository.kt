package com.cinetrack.data.repository

import androidx.room.withTransaction
import com.cinetrack.BuildConfig
import com.cinetrack.data.local.AppDatabase
import com.cinetrack.data.local.EpisodeEntity
import com.cinetrack.data.local.MediaEntity
import com.cinetrack.data.local.PendingWriteEntity
import com.cinetrack.data.local.PlaybackEntity
import com.cinetrack.data.local.SyncStateEntity
import com.cinetrack.data.local.UpNextEntity
import com.cinetrack.data.local.UserMediaStateEntity
import com.cinetrack.data.local.WatchHistoryEntity
import com.cinetrack.data.local.toDomain
import com.cinetrack.data.local.toEntity
import com.cinetrack.data.remote.ApiServices
import com.cinetrack.data.remote.NetworkFactory
import com.cinetrack.data.remote.MdbListRatingRequest
import com.cinetrack.data.remote.SimklIds
import com.cinetrack.data.remote.SimklLibraryItem
import com.cinetrack.data.remote.SimklLibraryResponse
import com.cinetrack.data.remote.SimklPlaybackItem
import com.cinetrack.data.remote.SimklSyncItem
import com.cinetrack.data.remote.SimklSyncRequest
import com.cinetrack.data.remote.TmdbMediaDto
import com.cinetrack.domain.AppUiState
import com.cinetrack.domain.DiscoverMovieFilters
import com.cinetrack.domain.EpisodeCard
import com.cinetrack.domain.LibraryStatus
import com.cinetrack.domain.MediaCard
import com.cinetrack.domain.MediaType
import com.cinetrack.domain.PersonCard
import com.cinetrack.domain.PlaybackCard
import com.cinetrack.domain.RailIds
import com.cinetrack.domain.RatingScore
import com.cinetrack.domain.SeasonCard
import com.cinetrack.domain.SyncProgress
import com.cinetrack.domain.SyncReport
import com.cinetrack.domain.SyncStage
import com.cinetrack.domain.StreamingProvider
import com.cinetrack.domain.TimelineCard
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.security.MessageDigest
import java.util.Locale

data class SimklSyncOutcome(val itemsChanged: Boolean, val report: SyncReport = SyncReport())

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

class CineTrackRepository(
    private val database: AppDatabase,
    private val services: ApiServices,
    val preferences: AppPreferences,
    private val onTokenChanged: (String?) -> Unit = {},
    private val currentToken: () -> String? = { null },
    private val onTmdbApiKeyChanged: (String) -> Unit = {},
    private val tmdbApiKey: () -> String = { BuildConfig.TMDB_API_TOKEN },
    private val onMdbListApiKeyChanged: (String) -> Unit = {},
    private val mdbListApiKey: () -> String = { BuildConfig.MDBLIST_API_KEY },
    private val onMetadataLanguageChanged: (String) -> Unit = {},
    private val onMetadataRegionChanged: (String) -> Unit = {},
    private val onMetadataTimezoneChanged: (String) -> Unit = {},
) {
    private suspend fun localToday(): LocalDate {
        val configured = preferences.metadataTimezone.first()
        val zone = if (configured == "system") ZoneId.systemDefault()
        else runCatching { ZoneId.of(configured) }.getOrDefault(ZoneId.systemDefault())
        return LocalDate.now(zone)
    }

    /** TMDB accepts one watch region. Make Discover/provider availability follow
     * the content-region filter, then fall back to metadata and device regions. */
    private suspend fun effectiveProviderRegion(): String {
        preferences.contentRegions.first().sorted().firstOrNull()?.let { return it.uppercase() }
        preferences.metadataRegion.first().takeUnless { it == "system" || it.isBlank() }?.let { return it.uppercase() }
        return Locale.getDefault().country.takeIf(String::isNotBlank)?.uppercase() ?: "US"
    }

    private fun scheduleTime(raw: String?): Long = raw?.takeIf(String::isNotBlank)?.let { value ->
        runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
            ?: runCatching {
                LocalDate.parse(value.take(10)).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }.getOrNull()
    } ?: 0L

    private fun playbackRecency(item: PlaybackCard): Long = maxOf(
        item.media.libraryUpdatedAt ?: 0L,
        scheduleTime(item.episodeAirDate),
    )

    private val episodeTitleCache = BoundedLruCache<String, String>(750)
    private val progressCacheMutex = Mutex()
    private val progressEnrichmentScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var progressEnrichmentJob: Job? = null
    @Volatile private var progressEnrichmentRequested = false
    private val libraryPushMutex = Mutex()
    private val progressCacheRepository by lazy {
        ProgressCacheRepository(database, preferences, tmdbApiKey, ::loadEpisodes)
    }
    val progressCacheRefreshing: Boolean get() = progressCacheMutex.isLocked
    suspend fun loadInitial(): AppUiState {
        val cached = loadCachedState()
        if (tmdbApiKey().isBlank()) {
            return cached.copy(loading = false, error = "TMDB_API_TOKEN is missing")
        }
        return runCatching {
            refreshDiscover()
            loadCachedState().copy(loading = false)
        }.getOrElse { cached.copy(loading = false, error = it.message) }
    }

    suspend fun refreshDiscover() = coroutineScope {
        check(tmdbApiKey().isNotBlank()) { "TMDB API credential is missing" }
        val allowedRegions = preferences.contentRegions.first()
        val hiddenDiscovery = preferences.hiddenDiscovery.first()
        val regionQuery = allowedRegions.takeIf { it.isNotEmpty() }?.sorted()?.joinToString("|")
        fun List<com.cinetrack.data.remote.TmdbMediaDto>.inAllowedRegions() = filter { dto ->
            allowedRegions.isEmpty() || dto.originCountries.isEmpty() || dto.originCountries.any(allowedRegions::contains)
        }
        // Cache several pages so View all is a real catalogue view rather than
        // the same small home rail expanded into a grid.
        val tv = async {
            (1..3).flatMap { page ->
                if (regionQuery == null) services.tmdb.trendingTv(page).results
                else services.tmdb.discoverTv(regionQuery, page = page).results
            }.inAllowedRegions().map { it.toEntity(MediaType.TV) }
                .filterNot { "${it.mediaType}:${it.tmdbId}" in hiddenDiscovery }
        }
        val movies = async {
            (1..3).flatMap { page ->
                if (regionQuery == null) services.tmdb.trendingMovies(page).results
                else services.tmdb.discoverMovies(regionQuery, page = page).results
            }.inAllowedRegions().map { it.toEntity(MediaType.MOVIE) }
                .filterNot { "${it.mediaType}:${it.tmdbId}" in hiddenDiscovery }
        }
        val today = localToday()
        val upcomingMovies = async {
            (1..3).flatMap { page ->
                if (regionQuery == null) services.tmdb.upcomingMovies(page).results
                else services.tmdb.discoverMovies(regionQuery, sortBy = "primary_release_date.asc", dateFrom = today.plusDays(1).toString(), page = page).results
            }.inAllowedRegions().map { it.toEntity(MediaType.MOVIE) }
                .filterNot { "${it.mediaType}:${it.tmdbId}" in hiddenDiscovery }
        }
        val upcomingTv = async {
            runCatching {
                (1..3).flatMap { page ->
                    services.tmdb.upcomingTv(today.plusDays(1).toString(), originCountries = regionQuery, page = page).results
                }.inAllowedRegions().map { it.toEntity(MediaType.TV) }
                    .filterNot { "${it.mediaType}:${it.tmdbId}" in hiddenDiscovery }
            }
                .getOrDefault(emptyList())
        }
        database.withTransaction {
            database.mediaDao().replaceRail(RailIds.TRENDING_TV, tv.await())
            database.mediaDao().replaceRail(RailIds.TRENDING_MOVIES, movies.await())
            val upcoming = (upcomingMovies.await() + upcomingTv.await())
                .filter { entity ->
                    entity.releaseDate?.let { raw -> runCatching { LocalDate.parse(raw.take(10)).isAfter(today) }.getOrDefault(false) } == true
                }
                .distinctBy { "${it.mediaType}:${it.tmdbId}" }
                .sortedBy(MediaEntity::releaseDate)
            database.mediaDao().replaceRail(RailIds.UPCOMING, upcoming)
        }
    }

    suspend fun discoverMovies(filters: DiscoverMovieFilters): List<MediaCard> = coroutineScope {
        check(tmdbApiKey().isNotBlank()) { "TMDB API credential is missing" }
        val allowedRegions = preferences.contentRegions.first()
        val hiddenDiscovery = preferences.hiddenDiscovery.first()
        val regionQuery = allowedRegions.takeIf { it.isNotEmpty() }?.sorted()?.joinToString("|")
        val states = database.stateDao().observeAll().first().associateBy { "${it.mediaType}:${it.mediaId}" }
        val genreQuery = filters.genreIds.takeIf { it.isNotEmpty() }?.sorted()?.joinToString(",")
        val excludedGenreQuery = filters.excludedGenreIds.takeIf { it.isNotEmpty() }?.sorted()?.joinToString(",")
        val providerQuery = filters.providerIds.takeIf { it.isNotEmpty() }?.sorted()?.joinToString("|")
        val watchRegion = effectiveProviderRegion()
        val decadeFrom = filters.decadeStart?.let { "$it-01-01" }
        val decadeTo = filters.decadeStart?.let { "${it + 9}-12-31" }
        val entities = (1..3).flatMap { page ->
            if (filters.mediaType == MediaType.TV) {
                services.tmdb.discoverTv(
                    originCountries = regionQuery,
                    sortBy = filters.sortBy.replace("primary_release_date", "first_air_date"),
                    releaseYear = filters.releaseYear,
                    genreIds = genreQuery,
                    excludedGenreIds = excludedGenreQuery,
                    minimumRating = filters.minimumRating,
                    minimumVotes = filters.minimumRating?.let { 50 },
                    providerIds = providerQuery,
                    watchRegion = watchRegion,
                    maximumRuntime = filters.maximumRuntime,
                    originalLanguage = filters.originalLanguage,
                    dateFrom = decadeFrom,
                    dateTo = decadeTo,
                    page = page,
                ).results
            } else {
                services.tmdb.discoverMovies(
                    originCountries = regionQuery,
                    sortBy = filters.sortBy,
                    releaseYear = filters.releaseYear,
                    genreIds = genreQuery,
                    excludedGenreIds = excludedGenreQuery,
                    minimumRating = filters.minimumRating,
                    minimumVotes = filters.minimumRating?.let { 50 },
                    providerIds = providerQuery,
                    watchRegion = watchRegion,
                    maximumRuntime = filters.maximumRuntime,
                    originalLanguage = filters.originalLanguage,
                    dateFrom = decadeFrom,
                    dateTo = decadeTo,
                    page = page,
                ).results
            }
        }.filter { dto ->
            val regionAllowed = allowedRegions.isEmpty() || dto.originCountries.isEmpty() || dto.originCountries.any(allowedRegions::contains)
            val isAnime = dto.originalLanguage.equals("ja", ignoreCase = true) && 16 in dto.genreIds
            regionAllowed && when (filters.animeMode) {
                "only" -> isAnime
                "exclude" -> !isAnime
                else -> true
            }
        }.distinctBy(TmdbMediaDto::id)
            .map { it.toEntity(filters.mediaType) }
        database.mediaDao().upsertMedia(entities)
        entities.map { entity -> entity.toDomain(states["${entity.mediaType}:${entity.tmdbId}"]) }
            .filterNot { it.stableKey in hiddenDiscovery }
            .filterNot { filters.hideWatched && (it.watched || it.status == LibraryStatus.COMPLETED) }
            .filterNot { filters.hideDropped && it.status == LibraryStatus.DROPPED }
    }

    suspend fun loadStreamingProviders(mediaType: MediaType): List<StreamingProvider> {
        if (tmdbApiKey().isBlank()) return emptyList()
        val region = effectiveProviderRegion()
        return runCatching {
            val providers = if (mediaType == MediaType.TV) {
                services.tmdb.tvProviders(region).results
            } else {
                services.tmdb.movieProviders(region).results
            }
            providers.distinctBy { it.id }
                .sortedBy { it.name.lowercase(Locale.getDefault()) }
                .map { provider ->
                    StreamingProvider(
                        id = provider.id,
                        name = provider.name,
                        logoUrl = provider.logoPath?.let { "https://image.tmdb.org/t/p/w92$it" },
                    )
                }
        }.getOrDefault(emptyList())
    }

    suspend fun loadSettingsStreamingProviders(): List<StreamingProvider> = coroutineScope {
        val movies = async { loadStreamingProviders(MediaType.MOVIE) }
        val shows = async { loadStreamingProviders(MediaType.TV) }
        (movies.await() + shows.await())
            .distinctBy(StreamingProvider::id)
            .sortedBy { it.name.lowercase(Locale.getDefault()) }
    }

    fun simklConnectedNow(): Boolean = !currentToken().isNullOrBlank()

    fun observeLocalChanges() = database.invalidationTracker.createFlow(
        "media",
        "media_rails",
        "user_media_state",
        "episodes",
        "playback",
        "watch_history",
        "up_next",
        "sync_state",
        emitInitialState = false,
    )

    suspend fun isSimklSyncDue(maxAgeMillis: Long): Boolean {
        val lastCheck = preferences.simklLastCheckAt()
            ?: database.syncDao().get("all")?.lastSuccessfulSync
            ?: return true
        return System.currentTimeMillis() - lastCheck >= maxAgeMillis
    }

    suspend fun loadCachedState(): AppUiState = coroutineScope {
        // The database portion is read in one Room transaction. Preferences can
        // still be collected concurrently because they are independent DataStore
        // values and cannot produce a structurally inconsistent media snapshot.
        val snapshotDeferred = async { database.snapshotDao().snapshot() }
        val backgroundSyncDeferred = async { preferences.backgroundSync.first() }
        val wifiOnlyDeferred = async { preferences.wifiOnly.first() }
        val ratingSourcesDeferred = async { preferences.ratingSources.first() }
        val contentRegionsDeferred = async { preferences.contentRegions.first() }
        val uiAccentDeferred = async { preferences.uiAccent.first() }
        val metadataLanguageDeferred = async { preferences.metadataLanguage.first() }
        val metadataRegionDeferred = async { preferences.metadataRegion.first() }
        val metadataTimezoneDeferred = async { preferences.metadataTimezone.first() }
        val syncReportDeferred = async { preferences.syncReportNow() }
        val excludeSpecialsDeferred = async { preferences.excludeSpecials.first() }
        val preferredProvidersDeferred = async { preferences.preferredProviders.first() }
        val notificationEpisodesDeferred = async { preferences.notificationEpisodes.first() }
        val notificationMoviesDeferred = async { preferences.notificationMovies.first() }
        val notificationSyncDeferred = async { preferences.notificationSync.first() }
        val cardDensityDeferred = async { preferences.cardDensity.first() }
        val hiddenUpcomingDeferred = async { preferences.hiddenUpcoming.first() }
        val hiddenDiscoveryDeferred = async { preferences.hiddenDiscovery.first() }
        val introductionCompletedDeferred = async { preferences.introductionCompleted.first() }

        val snapshot = snapshotDeferred.await()
        val states = snapshot.states.associateBy { "${it.mediaType}:${it.mediaId}" }
        val mediaByKey = snapshot.media.associateBy { "${it.mediaType}:${it.tmdbId}" }
        val railEntries = snapshot.rails.groupBy { it.railId }
        val cachedEpisodes = snapshot.episodes
        val episodeByNumber = cachedEpisodes.associateBy { Triple(it.showId, it.season, it.number) }
        val railIds = listOf(
            RailIds.TRENDING_TV,
            RailIds.TRENDING_MOVIES,
            RailIds.UPCOMING,
            RailIds.RECOMMENDED,
            RailIds.LIBRARY,
        )
        val rails = railIds.associateWith { rail ->
            railEntries[rail].orEmpty().mapNotNull { entry ->
                mediaByKey["${entry.mediaType}:${entry.mediaId}"]?.let { entity ->
                    entity.toDomain(states["${entity.mediaType}:${entity.tmdbId}"])
                }
            }
        }
        val activeLibrary = rails[RailIds.LIBRARY].orEmpty().filter {
            it.status != LibraryStatus.NONE && it.status != LibraryStatus.DROPPED
        }
        val playback = snapshot.playback.mapNotNull { item ->
            val media = mediaByKey["${item.mediaType}:${item.mediaId}"]
                ?.toDomain(states["${item.mediaType}:${item.mediaId}"]) ?: return@mapNotNull null
            val cachedEpisode = if (item.mediaType == MediaType.TV.name && item.season != null && item.episodeNumber != null) {
                episodeByNumber[Triple(item.mediaId, item.season, item.episodeNumber)]
            } else null
            PlaybackCard(
                media = media,
                episodeId = item.episodeId.takeIf { it != 0 } ?: cachedEpisode?.tmdbId,
                episodeLabel = if ((item.season ?: cachedEpisode?.season) != null && (item.episodeNumber ?: cachedEpisode?.number) != null) {
                    "S${(item.season ?: cachedEpisode?.season).toString().padStart(2, '0')} E${(item.episodeNumber ?: cachedEpisode?.number).toString().padStart(2, '0')}"
                } else null,
                episodeTitle = item.episodeTitle?.takeIf(String::isNotBlank) ?: cachedEpisode?.title,
                season = item.season ?: cachedEpisode?.season,
                episodeNumber = item.episodeNumber ?: cachedEpisode?.number,
                progress = item.progress.coerceIn(0f, 1f),
                remainingMinutes = if (item.durationSeconds > item.positionSeconds) {
                    ((item.durationSeconds - item.positionSeconds) / 60).toInt()
                } else media.runtimeMinutes?.let { runtime -> (runtime * (1f - item.progress.coerceIn(0f, 1f))).toInt().coerceAtLeast(0) },
                durationMinutes = item.durationSeconds.takeIf { it > 0 }?.let { (it / 60).toInt().coerceAtLeast(1) }
                    ?: media.runtimeMinutes,
                episodeAirDate = cachedEpisode?.airDate,
            )
        }
        val history = snapshot.history
            // Simkl commonly assigns the same watched_at value to every episode
            // in a bulk import.  episodeId is also null for the all-items payload,
            // so the old key collapsed an entire batch into a single row.  Season
            // and episode number are the stable Simkl episode identity.
            .distinctBy {
                "${it.mediaType}:${it.mediaId}:${it.season ?: -1}:${it.episodeNumber ?: -1}:${it.episodeId ?: -1}:${it.watchedAt}"
            }
            .mapNotNull { item ->
            val media = mediaByKey["${item.mediaType}:${item.mediaId}"]
                ?.toDomain(states["${item.mediaType}:${item.mediaId}"]) ?: return@mapNotNull null
            val cachedTitle = if (item.mediaType == MediaType.TV.name && item.season != null && item.episodeNumber != null) {
                episodeByNumber[Triple(item.mediaId, item.season, item.episodeNumber)]?.title
            } else null
            val episodeLabel = if (item.mediaType == MediaType.TV.name && item.season != null && item.episodeNumber != null) {
                "S${item.season.toString().padStart(2, '0')} E${item.episodeNumber.toString().padStart(2, '0')}" +
                    (item.episodeTitle?.takeIf(String::isNotBlank) ?: cachedTitle?.takeIf(String::isNotBlank))?.let { " · $it" }.orEmpty()
            } else null
            TimelineCard(
                media = media,
                label = item.watchedAt.take(10),
                timestamp = item.watchedAt,
                episodeLabel = episodeLabel,
                episodeId = item.episodeId,
                season = item.season,
                episodeNumber = item.episodeNumber,
            )
        }
        val today = localToday()
        // Calendar is strictly a tracked-content surface. Build it from the
        // Simkl-backed Library rail so TMDB content-region preferences can never
        // hide an imported or locally tracked release.
        val movieCalendar = activeLibrary.asSequence().filter { it.type == MediaType.MOVIE }.mapNotNull { media ->
            val date = media.releaseDate?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            val releaseDay = runCatching { LocalDate.parse(date.take(10)) }.getOrNull() ?: return@mapNotNull null
            // The Calendar is an upcoming schedule, not a release history. Date-only
            // TMDB entries are kept only after today so already-aired/released titles
            // never reappear here.
            if (!releaseDay.isAfter(today)) return@mapNotNull null
            TimelineCard(media = media, label = date, timestamp = date)
        }.toList()
        val activeShowsById = activeLibrary.asSequence()
            .filter { it.type == MediaType.TV }
            .associateBy(MediaCard::id)
        // The episode table is the durable schedule snapshot. Rebuild Calendar
        // from it at process start, just as Showly reads its local episode cache,
        // instead of waiting for a new network enrichment pass.
        val excludeSpecials = excludeSpecialsDeferred.await()
        val hiddenUpcoming = hiddenUpcomingDeferred.await()
        val episodeCalendar = cachedEpisodes.mapNotNull { episode ->
            val show = activeShowsById[episode.showId] ?: return@mapNotNull null
            if (excludeSpecials && episode.season == 0) return@mapNotNull null
            if ("${episode.showId}:${episode.season}:${episode.number}" in hiddenUpcoming) return@mapNotNull null
            val date = episode.airDate?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            val airDay = runCatching { LocalDate.parse(date.take(10)) }.getOrNull() ?: return@mapNotNull null
            if (airDay.isBefore(today)) return@mapNotNull null
            TimelineCard(
                media = show,
                label = date,
                timestamp = date,
                episodeLabel = "S${episode.season.toString().padStart(2, '0')} E${episode.number.toString().padStart(2, '0')} · ${episode.title}",
                episodeId = episode.tmdbId,
                season = episode.season,
                episodeNumber = episode.number,
            )
        }
        val calendar = (movieCalendar + episodeCalendar)
            .distinctBy { "${it.media.stableKey}:${it.season ?: -1}:${it.episodeNumber ?: -1}:${it.timestamp.take(10)}" }
            .sortedWith(
                compareBy<TimelineCard> { it.timestamp.take(10) }
                    .thenBy { it.media.title.lowercase() }
                    .thenBy { it.season ?: -1 }
                    .thenBy { it.episodeNumber ?: -1 },
            )
        val watchedNumbers = history.asSequence().mapNotNull { event ->
            if (event.media.type == MediaType.TV && event.season != null && event.episodeNumber != null) {
                Triple(event.media.id, event.season, event.episodeNumber)
            } else null
        }.toSet()
        val latestWatchedAtByShow = history.asSequence()
            .filter { it.media.type == MediaType.TV }
            .groupBy { it.media.id }
            .mapValues { (_, events) -> events.maxOfOrNull { scheduleTime(it.timestamp) } ?: 0L }
        val playbackByShow = playback.filter { it.media.type == MediaType.TV }.associateBy { it.media.stableKey }
        val durableUpNext = snapshot.upNext.associateBy(UpNextEntity::showId)
        val durableUpNextReady = snapshot.syncStates.any { it.area == "up_next_cache_v1" }
        val cachedUpNext = rails[RailIds.LIBRARY].orEmpty()
            .filter { it.type == MediaType.TV && it.status in setOf(LibraryStatus.WATCHING, LibraryStatus.COMPLETED) }
            .mapNotNull { show ->
                val session = playbackByShow[show.stableKey]
                val stored = durableUpNext[show.id]
                val next = stored?.let { row ->
                    EpisodeCard(
                        id = row.episodeId ?: 0,
                        showId = row.showId,
                        season = row.season,
                        number = row.episodeNumber,
                        title = row.episodeTitle,
                        overview = "",
                        airDate = row.episodeAirDate,
                        runtimeMinutes = row.durationMinutes,
                    )
                } ?: if (!durableUpNextReady) {
                    // One-time compatibility path while migration/startup builds
                    // the durable cache. Once marked ready an empty row correctly
                    // means that the show has no aired unwatched episode.
                    val candidates = cachedEpisodes.asSequence()
                        .filter { it.showId == show.id && (!excludeSpecials || it.season > 0) }
                        .filter { episode ->
                            episode.airDate?.take(10)?.let { raw ->
                                runCatching { !LocalDate.parse(raw).isAfter(today) }.getOrDefault(false)
                            } == true
                        }
                        .filterNot { Triple(show.id, it.season, it.number) in watchedNumbers }
                        .sortedWith(compareBy(EpisodeEntity::season, EpisodeEntity::number))
                        .toList()
                    val lastWatched = watchedNumbers.asSequence().filter { it.first == show.id && it.second > 0 }
                        .maxWithOrNull(compareBy<Triple<Int, Int, Int>>({ it.second }, { it.third }))
                    val legacyNext = lastWatched?.let { last ->
                        candidates.firstOrNull { it.season > last.second || (it.season == last.second && it.number > last.third) }
                    } ?: candidates.firstOrNull()
                    legacyNext?.toDomain()
                } else null
                if (next == null) return@mapNotNull session
                val sameEpisode = session?.season == next.season && session.episodeNumber == next.number
                PlaybackCard(
                    media = show,
                    episodeId = next.id.takeIf { it > 0 },
                    episodeLabel = "S${next.season.toString().padStart(2, '0')} E${next.number.toString().padStart(2, '0')}",
                    episodeTitle = next.title,
                    season = next.season,
                    episodeNumber = next.number,
                    progress = if (sameEpisode) session?.progress ?: 0f else 0f,
                    remainingMinutes = if (sameEpisode && (session?.progress ?: 0f) > 0f) session?.remainingMinutes else null,
                    durationMinutes = next.runtimeMinutes ?: session?.durationMinutes ?: show.runtimeMinutes,
                    episodeAirDate = next.airDate,
                )
            }
            .sortedWith(
                compareByDescending<PlaybackCard> { item ->
                    maxOf(latestWatchedAtByShow[item.media.id] ?: 0L, playbackRecency(item))
                }
                    .thenBy { it.media.title.lowercase() },
            )
        val moviePlayback = playback.filter { it.media.type == MediaType.MOVIE }.toMutableList()
        val movieKeys = moviePlayback.map { it.media.stableKey }.toSet()
        moviePlayback += rails[RailIds.LIBRARY].orEmpty()
            .filter { it.type == MediaType.MOVIE && it.status == LibraryStatus.WATCHING && it.stableKey !in movieKeys }
            .map { media -> PlaybackCard(media = media, progress = 0f, remainingMinutes = media.runtimeMinutes, durationMinutes = media.runtimeMinutes) }

        AppUiState(
            rails = rails,
            playbackTv = cachedUpNext,
            playbackMovies = moviePlayback,
            history = history,
            calendar = calendar,
            episodes = cachedEpisodes.map { episode ->
                episode.toDomain().copy(
                    watched = Triple(episode.showId, episode.season, episode.number) in watchedNumbers,
                )
            },
            sync = SyncProgress(
                lastSuccessfulSync = snapshot.syncStates.firstOrNull { it.area == "all" }?.lastSuccessfulSync,
                report = syncReportDeferred.await(),
            ),
            simklConnected = simklConnectedNow(),
            backgroundSync = backgroundSyncDeferred.await(),
            wifiOnly = wifiOnlyDeferred.await(),
            notificationEpisodes = notificationEpisodesDeferred.await(),
            notificationMovies = notificationMoviesDeferred.await(),
            notificationSync = notificationSyncDeferred.await(),
            ratingSources = ratingSourcesDeferred.await(),
            contentRegions = contentRegionsDeferred.await(),
            uiAccent = uiAccentDeferred.await(),
            tmdbApiConfigured = tmdbApiKey().isNotBlank(),
            mdbListApiConfigured = mdbListApiKey().isNotBlank(),
            metadataLanguage = metadataLanguageDeferred.await(),
            metadataRegion = metadataRegionDeferred.await(),
            metadataTimezone = metadataTimezoneDeferred.await(),
            excludeSpecials = excludeSpecials,
            preferredProviders = preferredProvidersDeferred.await(),
            cardDensity = cardDensityDeferred.await(),
            hiddenUpcoming = hiddenUpcoming,
            hiddenDiscovery = hiddenDiscoveryDeferred.await(),
            introductionCompleted = introductionCompletedDeferred.await(),
            loading = false,
        )
    }

    suspend fun search(query: String): List<MediaCard> {
        if (query.isBlank()) return emptyList()
        val allowedRegions = preferences.contentRegions.first().map { it.uppercase() }.toSet()
        val hiddenDiscovery = preferences.hiddenDiscovery.first()
        val states = database.stateDao().observeAll().first().associateBy { "${it.mediaType}:${it.mediaId}" }
        val remote = if (tmdbApiKey().isNotBlank()) runCatching {
            val candidates = services.tmdb.search(query).results
                .filter { it.mediaType == "movie" || it.mediaType == "tv" }
            val regionFiltered = if (allowedRegions.isEmpty()) candidates else coroutineScope {
                val requests = Semaphore(6)
                candidates.map { candidate ->
                    async {
                        requests.withPermit {
                            val embeddedCountries = candidate.originCountries +
                                candidate.productionCountries.map { it.code }
                            val countries = embeddedCountries.ifEmpty {
                                runCatching {
                                    val details = if (candidate.mediaType == "tv") {
                                        services.tmdb.show(candidate.id, append = "")
                                    } else {
                                        services.tmdb.movie(candidate.id, append = "")
                                    }
                                    details.originCountries + details.productionCountries.map { it.code }
                                }.getOrElse { error ->
                                    if (error is kotlinx.coroutines.CancellationException) throw error
                                    emptyList()
                                }
                            }
                            candidate.takeIf {
                                countries.any { country -> country.uppercase() in allowedRegions }
                            }
                        }
                    }
                }.mapNotNull { it.await() }
            }
            val items = regionFiltered
                .map { it.toEntity(if (it.mediaType == "tv") MediaType.TV else MediaType.MOVIE) }
            database.mediaDao().upsertMedia(items)
            items
        }.getOrElse { error ->
            if (error is kotlinx.coroutines.CancellationException) throw error
            emptyList()
        } else emptyList()
        // Discover search follows the TMDB content-region allowlist. When an
        // allowlist is active, do not merge unclassified local/imported rows back
        // into these results; Library and Progress searches remain unfiltered.
        val local = if (allowedRegions.isEmpty()) database.mediaDao().searchLocal(query).first() else emptyList()
        return (remote + local).distinctBy { "${it.mediaType}:${it.tmdbId}" }
            .filterNot { "${it.mediaType}:${it.tmdbId}" in hiddenDiscovery }
            .map { it.toDomain(states["${it.mediaType}:${it.tmdbId}"]) }
    }

    suspend fun setLibraryStatus(media: MediaCard, status: LibraryStatus) {
        database.withTransaction {
            val previous = database.stateDao().get(media.type.name, media.id)
            database.mediaDao().upsertMedia(listOf(media.toEntity()))
            database.stateDao().upsert(UserMediaStateEntity(
                mediaType = media.type.name,
                mediaId = media.id,
                status = status.name,
                watched = status == LibraryStatus.COMPLETED,
                simklId = previous?.simklId,
                dirty = true,
            ))
            if (status == LibraryStatus.COMPLETED && previous?.watched != true) {
                database.timelineDao().insertHistory(
                    WatchHistoryEntity(
                        mediaType = media.type.name,
                        mediaId = media.id,
                        watchedAt = Instant.now().toString(),
                    ),
                )
            }
            if (status == LibraryStatus.NONE) {
                // Simkl's full-library removal also clears the item's watch
                // history. Mirror that atomically so no stale history card or
                // watched episode can survive the local removal.
                database.timelineDao().deleteMediaHistory(media.type.name, media.id)
            } else if (previous?.watched == true && status != LibraryStatus.COMPLETED) {
                database.timelineDao().deleteMediaHistory(media.type.name, media.id)
                // Moving a completed item back to another list must also clear
                // Simkl history. Keep this as an ordered durable write so the
                // removal happens before the new list status is sent.
                database.syncDao().queue(
                    PendingWriteEntity(
                        operation = "MEDIA_HISTORY_REMOVE",
                        mediaType = media.type.name,
                        mediaId = media.id,
                        payload = previous.simklId?.toString().orEmpty(),
                    ),
                )
            }
            rebuildLibraryRailInTransaction()
        }
    }

    /**
     * Pushes library mutations immediately. A failed request leaves the exact
     * state dirty, so the normal synchronization pass retries it later.
     */
    suspend fun pushPendingLibraryChanges(): Result<Unit> = libraryPushMutex.withLock {
        runCatching {
            check(!preferences.tokenNow().isNullOrBlank()) { "Connect Simkl first" }
            val historyRemovals = database.syncDao().pendingWrites().filter { it.operation == "MEDIA_HISTORY_REMOVE" }
            historyRemovals.forEach { pushMediaHistoryRemoval(it) }
            if (historyRemovals.isNotEmpty()) {
                database.syncDao().deleteWrites(historyRemovals.map(PendingWriteEntity::id))
            }
            database.stateDao().pendingStates().forEach { state ->
                pushLibraryState(state)
                // Do not clear a newer selection that was made while this request
                // was in flight.
                database.stateDao().markCleanIfUnchanged(
                    state.mediaType,
                    state.mediaId,
                    state.updatedAt,
                )
            }
        }
    }

    /** Sends the item the user just changed before retrying unrelated writes. */
    suspend fun pushLibraryChange(type: MediaType, id: Int): Result<Unit> = libraryPushMutex.withLock {
        runCatching {
            check(!preferences.tokenNow().isNullOrBlank()) { "Connect Simkl first" }
            val state = database.stateDao().get(type.name, id) ?: return@runCatching
            val historyRemovals = database.syncDao().pendingWrites().filter {
                it.operation == "MEDIA_HISTORY_REMOVE" && it.mediaType == type.name && it.mediaId == id
            }
            historyRemovals.forEach { pushMediaHistoryRemoval(it) }
            pushLibraryState(state)
            database.stateDao().markCleanIfUnchanged(state.mediaType, state.mediaId, state.updatedAt)
            if (historyRemovals.isNotEmpty()) {
                database.syncDao().deleteWrites(historyRemovals.map(PendingWriteEntity::id))
            }
        }
    }

    private suspend fun pushLibraryState(state: UserMediaStateEntity) {
        val targetStatus = state.status.toSimklStatus()
        val ids = SimklIds(simkl = state.simklId, tmdb = state.mediaId.toString())
        if (state.status == LibraryStatus.NONE.name) {
            // With no season/episode granularity, history/remove deletes the
            // item from the Simkl library as well as its watched history.
            val request = state.syncRequest(SimklSyncItem(ids = ids))
            val response = services.simklSync.removeHistory(request)
            val unmatched = if (state.mediaType == MediaType.MOVIE.name) {
                response.notFound.movies
            } else {
                response.notFound.shows
            }
            check(unmatched.isEmpty()) { "Simkl could not match the item being removed" }
        } else if (state.watched) {
            services.simklSync.addHistory(
                state.syncRequest(
                    SimklSyncItem(
                        ids = ids,
                        watchedAt = Instant.now().toString(),
                        status = targetStatus,
                    ),
                ),
            )
        } else {
            // Simkl requires `to` on every item, not at the request root.
            services.simklSync.addToList(state.syncRequest(SimklSyncItem(ids = ids, to = targetStatus)))
        }
    }

    private fun UserMediaStateEntity.syncRequest(item: SimklSyncItem): SimklSyncRequest =
        if (mediaType == MediaType.MOVIE.name) SimklSyncRequest(movies = listOf(item))
        else SimklSyncRequest(shows = listOf(item))

    private suspend fun pushMediaHistoryRemoval(write: PendingWriteEntity) {
        val simklId = write.payload.toLongOrNull()
        val ids = SimklIds(simkl = simklId, tmdb = write.mediaId.toString())
        val request = if (write.mediaType == MediaType.MOVIE.name) {
            SimklSyncRequest(movies = listOf(SimklSyncItem(ids = ids)))
        } else {
            SimklSyncRequest(shows = listOf(SimklSyncItem(ids = ids)))
        }
        services.simklSync.removeHistory(request)
    }

    suspend fun markWatched(media: MediaCard) {
        database.withTransaction {
            val previous = database.stateDao().get(media.type.name, media.id)
            database.mediaDao().upsertMedia(listOf(media.toEntity()))
            database.stateDao().upsert(
                UserMediaStateEntity(
                    mediaType = media.type.name,
                    mediaId = media.id,
                    status = LibraryStatus.COMPLETED.name,
                    watched = true,
                    simklId = previous?.simklId,
                    dirty = true,
                ),
            )
            database.timelineDao().insertHistory(
                WatchHistoryEntity(mediaType = media.type.name, mediaId = media.id, watchedAt = Instant.now().toString()),
            )
            rebuildLibraryRailInTransaction()
        }
    }

    suspend fun markEpisodeWatched(episode: EpisodeCard) {
        val watchedAt = Instant.now().toString()
        database.withTransaction {
            database.timelineDao().insertHistory(
                WatchHistoryEntity(
                    mediaType = MediaType.TV.name,
                    mediaId = episode.showId,
                    episodeId = episode.id,
                    season = episode.season,
                    episodeNumber = episode.number,
                    episodeTitle = episode.title,
                    watchedAt = watchedAt,
                ),
            )
            // Persist the user's explicit action as the primary Progress recency
            // signal. A newly aired episode may lead the list until another show
            // is watched, but it must not reclaim the top after this transaction.
            database.stateDao().touch(MediaType.TV.name, episode.showId, System.currentTimeMillis())
        }
        refreshLocalUpNext(episode.showId)
        val request = SimklSyncRequest(
            shows = listOf(
                SimklSyncItem(
                    ids = SimklIds(tmdb = episode.showId.toString()),
                    seasons = listOf(
                        com.cinetrack.data.remote.SimklSeason(
                            episode.season,
                            listOf(com.cinetrack.data.remote.SimklEpisode(episode.number, watchedAt)),
                        ),
                    ),
                ),
            ),
        )
        if (preferences.tokenNow().isNullOrBlank() || runCatching { services.simklSync.addHistory(request) }.isFailure) {
            database.syncDao().queue(
                PendingWriteEntity(
                    operation = "EPISODE_WATCHED",
                    mediaType = MediaType.TV.name,
                    mediaId = episode.showId,
                    payload = "${episode.season}:${episode.number}:$watchedAt",
                ),
            )
        }
    }

    suspend fun setEpisodeWatched(episode: EpisodeCard, watched: Boolean) {
        if (watched) {
            markEpisodeWatched(episode)
            return
        }
        database.timelineDao().deleteEpisodeHistory(MediaType.TV.name, episode.showId, episode.season, episode.number)
        refreshLocalUpNext(episode.showId)
        val request = SimklSyncRequest(
            shows = listOf(
                SimklSyncItem(
                    ids = SimklIds(tmdb = episode.showId.toString()),
                    seasons = listOf(
                        com.cinetrack.data.remote.SimklSeason(
                            episode.season,
                            listOf(com.cinetrack.data.remote.SimklEpisode(episode.number)),
                        ),
                    ),
                ),
            ),
        )
        if (preferences.tokenNow().isNullOrBlank() || runCatching { services.simklSync.removeHistory(request) }.isFailure) {
            database.syncDao().queue(
                PendingWriteEntity(
                    operation = "EPISODE_UNWATCHED",
                    mediaType = MediaType.TV.name,
                    mediaId = episode.showId,
                    payload = "${episode.season}:${episode.number}",
                ),
            )
        }
    }

    /**
     * Advances one show's durable next-episode row using Room only. This runs
     * immediately after a watched-state change, so neither Compose nor the
     * database invalidation observer has to wait for Simkl or a full cache pass.
     */
    private suspend fun refreshLocalUpNext(showId: Int) {
        val today = localToday()
        val excludeSpecials = preferences.excludeSpecials.first()
        val watched = watchedEpisodeNumbers(showId)
        val lastWatched = watched.asSequence()
            .filter { it.first > 0 }
            .maxWithOrNull(compareBy<Pair<Int, Int>>({ it.first }, { it.second }))
        val candidates = database.mediaDao().episodesForShow(showId).asSequence()
            .map { it.toDomain() }
            .filter { !excludeSpecials || it.season > 0 }
            .filter { episode ->
                episode.airDate?.take(10)?.let { raw ->
                    runCatching { !LocalDate.parse(raw).isAfter(today) }.getOrDefault(false)
                } == true
            }
            .filterNot { (it.season to it.number) in watched }
            .sortedWith(compareBy(EpisodeCard::season, EpisodeCard::number))
            .toList()
        val next = lastWatched?.let { last ->
            candidates.firstOrNull {
                it.season > last.first || (it.season == last.first && it.number > last.second)
            }
        } ?: candidates.firstOrNull()
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

    suspend fun loadDetails(media: MediaCard): MediaCard {
        if (tmdbApiKey().isBlank()) return media
        return runCatching {
            val localized = if (media.type == MediaType.MOVIE) services.tmdb.movie(media.id) else services.tmdb.show(media.id)
            val english = if (localized.overview.isBlank() || (localized.title ?: localized.name).isNullOrBlank()) {
                runCatching {
                    if (media.type == MediaType.MOVIE) services.tmdb.movie(media.id, language = "en-US")
                    else services.tmdb.show(media.id, language = "en-US")
                }.getOrNull()
            } else null
            val dto = localized.copy(
                title = localized.title?.takeIf(String::isNotBlank) ?: english?.title,
                name = localized.name?.takeIf(String::isNotBlank) ?: english?.name,
                overview = localized.overview.ifBlank { english?.overview.orEmpty() },
                seasons = localized.seasons.ifEmpty { english?.seasons.orEmpty() },
            )
            val providerRegion = effectiveProviderRegion()
            val providerCountry = dto.watchProviders?.results?.get(providerRegion)
            val preferredProviders = preferences.preferredProviders.first()
            fun List<com.cinetrack.data.remote.TmdbProviderDto>.visibleProviders() =
                distinctBy { it.id }
                    .let { providers ->
                        if (preferredProviders.isEmpty()) providers
                        else providers.filter { it.name in preferredProviders }
                    }
                    .sortedWith(
                    compareByDescending<com.cinetrack.data.remote.TmdbProviderDto> { it.name in preferredProviders }
                        .thenBy { it.name.lowercase() },
                )
            val providers = providerCountry
                ?.let { it.flatrate + it.rent + it.buy }
                .orEmpty()
                .visibleProviders()
            dto.toEntity(media.type).toDomain().copy(
                status = media.status,
                watched = media.watched,
                libraryUpdatedAt = media.libraryUpdatedAt,
                tmdbStatus = dto.status,
                networks = dto.networks.map { it.name }.filter(String::isNotBlank),
                budget = dto.budget?.takeIf { it > 0L },
                boxOffice = dto.revenue?.takeIf { it > 0L },
                productionCompanies = dto.productionCompanies.map { it.name }.filter(String::isNotBlank),
                productionCountries = dto.productionCountries.map { country -> country.name.ifBlank { country.code } },
                originalLanguage = dto.originalLanguage?.takeIf(String::isNotBlank)?.uppercase(),
                providers = providers.map { it.name },
                providerLogos = providers.mapNotNull { provider ->
                    provider.logoPath?.let { provider.name to "https://image.tmdb.org/t/p/w92$it" }
                }.toMap(),
                subscriptionProviders = providerCountry?.flatrate.orEmpty().visibleProviders().map { it.name },
                rentProviders = providerCountry?.rent.orEmpty().visibleProviders().map { it.name },
                buyProviders = providerCountry?.buy.orEmpty().visibleProviders().map { it.name },
                providerLink = providerCountry?.link,
                seasons = dto.seasons.filter { it.number > 0 }.map { season ->
                    SeasonCard(
                        number = season.number,
                        title = season.name,
                        episodeCount = season.episodeCount,
                        posterUrl = season.posterPath?.let { "https://image.tmdb.org/t/p/w342$it" },
                    )
                },
            )
        }.getOrDefault(media)
    }

    suspend fun loadMedia(type: MediaType, id: Int): MediaCard? {
        val state = database.stateDao().stateSnapshot().firstOrNull {
            it.mediaType == type.name && it.mediaId == id
        }
        database.mediaDao().get(type.name, id)?.let { return it.toDomain(state) }
        if (tmdbApiKey().isBlank()) return null
        return runCatching {
            val dto = if (type == MediaType.MOVIE) services.tmdb.movie(id) else services.tmdb.show(id)
            val entity = dto.toEntity(type)
            database.mediaDao().upsertMedia(listOf(entity))
            entity.toDomain(state)
        }.getOrNull()
    }

    suspend fun loadAllEpisodes(show: MediaCard): List<EpisodeCard> {
        val detailedShow = if (show.seasons.isEmpty()) loadDetails(show) else show
        val seasons = detailedShow.seasons.map(SeasonCard::number).ifEmpty { listOf(1) }
        val cached = loadCachedEpisodes(show.id)
        val expectedCount = detailedShow.seasons.sumOf(SeasonCard::episodeCount)
        val tmdbBackedCount = cached.count { it.id > 0 }
        if (tmdbBackedCount == 0 || expectedCount <= 0 || tmdbBackedCount < expectedCount) {
            // TMDB remains the independent source for the complete seasons and
            // episodes catalogue. Simkl is applied afterwards only as a date
            // overlay, so disconnecting a Simkl account never hides this section.
            seasons.distinct().sorted().forEach { season -> loadEpisodes(detailedShow, season) }
        }
        refreshSimklSchedule(listOf(detailedShow))
        return loadCachedEpisodes(show.id)
            .filter { it.id > 0 }
            .sortedWith(compareBy(EpisodeCard::season, EpisodeCard::number))
    }

    suspend fun loadUpNextEpisodes(
        shows: List<MediaCard>,
        watched: Set<Triple<Int, Int, Int>>,
        cachedEpisodes: List<EpisodeCard>? = null,
        onItemProcessed: ((processed: Int, total: Int) -> Unit)? = null,
    ): Map<String, EpisodeCard> = progressCacheRepository.loadUpNextEpisodes(
        shows = shows,
        watched = watched,
        cachedEpisodes = cachedEpisodes,
        onItemProcessed = onItemProcessed,
    )

    suspend fun enrichHistoryLabels(items: List<TimelineCard>): List<TimelineCard> {
        val missing = items.filter {
            it.media.type == MediaType.TV && it.season != null && it.episodeNumber != null &&
                (it.episodeLabel.isNullOrBlank() || !it.episodeLabel.contains(" · "))
        }.distinctBy { "${it.media.id}:${it.season}:${it.episodeNumber}" }
        for (batch in missing.chunked(6)) {
            coroutineScope {
                batch.map { item ->
                    async {
                        val key = "${item.media.id}:${item.season}:${item.episodeNumber}"
                        if (episodeTitleCache[key].isNullOrBlank()) {
                            database.mediaDao().episode(item.media.id, item.season!!, item.episodeNumber!!)
                                ?.title?.takeIf(String::isNotBlank)?.let { episodeTitleCache[key] = it }
                        }
                        if (episodeTitleCache[key].isNullOrBlank() && tmdbApiKey().isNotBlank()) {
                            runCatching { services.tmdb.episode(item.media.id, item.season!!, item.episodeNumber!!).name }
                                .getOrNull()?.takeIf(String::isNotBlank)?.let { title ->
                                    episodeTitleCache[key] = title
                                    database.timelineDao().updateEpisodeTitle(MediaType.TV.name, item.media.id, item.season!!, item.episodeNumber!!, title)
                                }
                        }
                    }
                }.forEach { it.await() }
            }
        }
        return items.map { item ->
            if (item.media.type != MediaType.TV || item.season == null || item.episodeNumber == null) item
            else {
                val existingTitle = item.episodeLabel?.substringAfter(" · ", "")?.takeIf(String::isNotBlank)
                val title = episodeTitleCache["${item.media.id}:${item.season}:${item.episodeNumber}"] ?: existingTitle
                val number = "S${item.season.toString().padStart(2, '0')} E${item.episodeNumber.toString().padStart(2, '0')}"
                item.copy(episodeLabel = listOfNotNull(number, title?.takeIf(String::isNotBlank)).joinToString(" · "))
            }
        }
    }

    suspend fun loadPerson(person: PersonCard): PersonCard {
        if (tmdbApiKey().isBlank()) return person
        return runCatching {
            val localized = services.tmdb.person(person.id)
            val english = if (localized.biography.isBlank()) {
                runCatching { services.tmdb.person(person.id, language = "en-US") }.getOrNull()
            } else null
            val details = localized.copy(
                name = localized.name.ifBlank { english?.name.orEmpty() },
                biography = localized.biography.ifBlank { english?.biography.orEmpty() },
                placeOfBirth = localized.placeOfBirth?.takeIf(String::isNotBlank) ?: english?.placeOfBirth,
                profilePath = localized.profilePath ?: english?.profilePath,
            )
            val credits = services.tmdb.combinedCredits(person.id).cast
                .mapNotNull { credit ->
                    val type = if (credit.mediaType == "tv") MediaType.TV else MediaType.MOVIE
                    val title = if (type == MediaType.TV) credit.name else credit.title
                    val date = if (type == MediaType.TV) credit.firstAirDate else credit.releaseDate
                    if (title.isNullOrBlank()) return@mapNotNull null
                    MediaCard(
                        id = credit.id,
                        type = type,
                        title = title,
                        releaseDate = date,
                        posterUrl = credit.posterPath?.let { path -> "https://image.tmdb.org/t/p/w500$path" },
                        score = credit.voteAverage,
                    )
                }
                .distinctBy(MediaCard::stableKey)
                .sortedByDescending { credit ->
                    credit.releaseDate?.let { raw -> runCatching { LocalDate.parse(raw) }.getOrNull() }
                }
            PersonCard(
                id = details.id,
                name = details.name,
                role = person.role,
                profileUrl = details.profilePath?.let { "https://image.tmdb.org/t/p/w500$it" },
                biography = details.biography,
                birthday = details.birthday,
                placeOfBirth = details.placeOfBirth,
                movieCredits = credits,
            )
        }.getOrDefault(person)
    }

    suspend fun loadCast(media: MediaCard): List<PersonCard> {
        if (tmdbApiKey().isBlank()) return emptyList()
        return runCatching {
            val dto = if (media.type == MediaType.MOVIE) services.tmdb.movie(media.id) else services.tmdb.show(media.id)
            val cast = dto.credits?.cast.orEmpty().take(14).map {
                PersonCard(
                    id = it.id,
                    name = it.name,
                    role = it.character.orEmpty(),
                    profileUrl = it.profilePath?.let { path -> "https://image.tmdb.org/t/p/w500$path" },
                )
            }
            val crew = dto.credits?.crew.orEmpty().filter { it.job in setOf("Director", "Creator", "Executive Producer") }.take(4).map {
                PersonCard(
                    id = it.id,
                    name = it.name,
                    role = it.job.orEmpty(),
                    profileUrl = it.profilePath?.let { path -> "https://image.tmdb.org/t/p/w500$path" },
                )
            }
            (cast + crew).distinctBy(PersonCard::id)
        }.getOrDefault(emptyList())
    }

    suspend fun loadEpisodes(show: MediaCard, season: Int = 1): List<EpisodeCard> {
        val watchedNumbers = watchedEpisodeNumbers(show.id)
        val cachedEntities = database.mediaDao().episodesForShow(show.id)
            .filter { it.season == season }
        val cachedByNumber = cachedEntities.associateBy(EpisodeEntity::number)
        val cached = cachedEntities
            .map { entity ->
                entity.toDomain().copy(watched = (entity.season to entity.number) in watchedNumbers)
            }
        if (tmdbApiKey().isBlank()) return cached
        return runCatching {
            val localized = services.tmdb.season(show.id, season).episodes
            val englishByNumber = if (localized.any { it.name.isBlank() || it.overview.isBlank() }) {
                runCatching { services.tmdb.season(show.id, season, language = "en-US").episodes }
                    .getOrDefault(emptyList())
                    .associateBy { it.number }
            } else emptyMap()
            localized.map { source ->
                val english = englishByNumber[source.number]
                val episode = source.copy(
                    name = source.name.ifBlank { english?.name.orEmpty() },
                    overview = source.overview.ifBlank { english?.overview.orEmpty() },
                )
                EpisodeCard(
                    id = episode.id,
                    showId = show.id,
                    season = episode.season,
                    number = episode.number,
                    title = episode.name,
                    overview = episode.overview,
                    // A Simkl schedule row may already have corrected this date.
                    // Preserve it while TMDB continues to provide every other field.
                    airDate = cachedByNumber[episode.number]?.airDate ?: episode.airDate,
                    stillUrl = episode.stillPath?.let { path -> "https://image.tmdb.org/t/p/w780$path" },
                    runtimeMinutes = episode.runtime,
                    watched = (episode.season to episode.number) in watchedNumbers,
                )
            }.also { episodes ->
                if (episodes.isNotEmpty()) database.mediaDao().upsertEpisodes(episodes.map { it.toEntity() })
            }
        }.getOrDefault(cached)
    }

    suspend fun loadCachedEpisodes(showId: Int): List<EpisodeCard> {
        val watchedNumbers = watchedEpisodeNumbers(showId)
        return database.mediaDao().episodesForShow(showId).map { entity ->
            entity.toDomain().copy(watched = (entity.season to entity.number) in watchedNumbers)
        }
    }

    private suspend fun watchedEpisodeNumbers(showId: Int): Set<Pair<Int, Int>> =
        database.timelineDao().episodeHistoryForShow(MediaType.TV.name, showId)
            .mapNotNull { history ->
                val season = history.season ?: return@mapNotNull null
                val episode = history.episodeNumber ?: return@mapNotNull null
                season to episode
            }
            .toSet()

    /**
     * Refreshes only the durable inputs used by Progress. Remote responses are
     * written to Room first; callers then publish a fresh database snapshot.
     * Nothing returned by the network is exposed directly to Compose.
     */
    suspend fun refreshProgressCache(
        request: ProgressRefreshRequest = ProgressRefreshRequest(force = true),
        onProgress: ((Float) -> Unit)? = null,
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
            database.upNextDao().clear()
            val rows = upNext.values.map { episode ->
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
    private suspend fun refreshSimklSchedule(shows: List<MediaCard>, force: Boolean = false): Boolean {
        val now = System.currentTimeMillis()
        val freshness = 5L * 60L * 60L * 1_000L
        val previous = database.syncDao().get("simkl_calendar")
        val requestedShowsCovered = shows.all { show ->
            database.syncDao().get("simkl_calendar:${show.id}")?.let { now - it.lastSuccessfulSync < freshness } == true
        }
        if (!force && previous != null && now - previous.lastSuccessfulSync < freshness && requestedShowsCovered) return true

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
                val date = item.date.takeIf { it.length >= 10 } ?: item.releaseDate.takeIf { it.length >= 10 }
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

    suspend fun loadUpcomingEpisodes(
        shows: List<MediaCard>,
        forceSchedule: Boolean = false,
    ): List<EpisodeCard> {
        val today = localToday()
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
                                    stillUrl = episode.stillPath?.let { path -> "https://image.tmdb.org/t/p/w780$path" },
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
                                        stillUrl = episode.stillPath?.let { path -> "https://image.tmdb.org/t/p/w780$path" },
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
        val remote = remoteResults.flatMap { (_, result) -> result.getOrDefault(emptyList()) }
            .map { episode ->
                // TMDB fills the full episode model; a date already overlaid by
                // Simkl remains canonical for this one field only.
                episode.copy(
                    airDate = cachedDates[Triple(episode.showId, episode.season, episode.number)]
                        ?: episode.airDate,
                )
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

    suspend fun loadEpisode(show: MediaCard, season: Int, number: Int): EpisodeCard? {
        val watched = (season to number) in watchedEpisodeNumbers(show.id)
        val cached = database.mediaDao().episode(show.id, season, number)?.toDomain()?.copy(watched = watched)
        if (tmdbApiKey().isBlank()) return cached
        return runCatching {
            val localized = services.tmdb.episode(show.id, season, number)
            val english = if (localized.name.isBlank() || localized.overview.isBlank()) {
                runCatching { services.tmdb.episode(show.id, season, number, language = "en-US") }.getOrNull()
            } else null
            localized.copy(
                name = localized.name.ifBlank { english?.name.orEmpty() },
                overview = localized.overview.ifBlank { english?.overview.orEmpty() },
            ).let {
                EpisodeCard(
                    id = it.id,
                    showId = show.id,
                    season = it.season,
                    number = it.number,
                    title = it.name,
                    overview = it.overview,
                    airDate = it.airDate,
                    stillUrl = it.stillPath?.let { path -> "https://image.tmdb.org/t/p/w1280$path" },
                    runtimeMinutes = it.runtime,
                    watched = watched,
                ).also { episode -> database.mediaDao().upsertEpisodes(listOf(episode.toEntity())) }
            }
        }.getOrNull() ?: cached
    }

    suspend fun loadTrailerKey(media: MediaCard): String? {
        if (tmdbApiKey().isBlank()) return null
        return runCatching {
            val videos = if (media.type == MediaType.TV) {
                services.tmdb.show(media.id).videos?.results.orEmpty()
            } else {
                services.tmdb.movie(media.id).videos?.results.orEmpty()
            }
            videos.asSequence()
                .filter { it.site.equals("YouTube", ignoreCase = true) && it.key.isNotBlank() }
                .sortedWith(
                    compareByDescending<com.cinetrack.data.remote.TmdbVideoDto> { it.official }
                        .thenByDescending { it.type.equals("Trailer", ignoreCase = true) }
                        .thenByDescending { it.publishedAt.orEmpty() },
                )
                .firstOrNull()
                ?.key
        }.getOrNull()
    }

    suspend fun loadEpisodeCast(show: MediaCard, season: Int, number: Int): List<PersonCard> {
        if (tmdbApiKey().isBlank()) return emptyList()
        return runCatching {
            val episode = services.tmdb.episode(show.id, season, number)
            (episode.guestStars + episode.crew).distinctBy { it.id }.take(16).map {
                PersonCard(
                    id = it.id,
                    name = it.name,
                    role = it.character ?: it.job.orEmpty(),
                    profileUrl = it.profilePath?.let { path -> "https://image.tmdb.org/t/p/w500$path" },
                )
            }
        }.getOrDefault(emptyList())
    }

    suspend fun loadCollection(media: MediaCard): List<MediaCard> {
        val collectionId = media.collectionId ?: return emptyList()
        if (tmdbApiKey().isBlank()) return emptyList()
        return runCatching {
            services.tmdb.collection(collectionId).parts
                .sortedBy { it.releaseDate.orEmpty() }
                .map { it.toEntity(MediaType.MOVIE).toDomain() }
        }.getOrDefault(emptyList())
    }

    suspend fun loadRecommendations(media: MediaCard): List<MediaCard> {
        if (tmdbApiKey().isBlank()) return emptyList()
        return runCatching {
            val dto = if (media.type == MediaType.MOVIE) services.tmdb.movie(media.id) else services.tmdb.show(media.id)
            val allowedRegions = preferences.contentRegions.first()
            val hiddenDiscovery = preferences.hiddenDiscovery.first()
            val candidates = dto.recommendations?.results.orEmpty().take(18)
            val filteredCandidates = if (allowedRegions.isEmpty()) candidates else coroutineScope {
                val requests = Semaphore(4)
                candidates.map { candidate -> async {
                    requests.withPermit {
                        val countries = candidate.originCountries.ifEmpty {
                            runCatching {
                                val details = if (media.type == MediaType.MOVIE) services.tmdb.movie(candidate.id) else services.tmdb.show(candidate.id)
                                details.originCountries + details.productionCountries.map { it.code }
                            }.getOrDefault(emptyList())
                        }
                        candidate.takeIf { countries.any { it.uppercase() in allowedRegions } }
                    }
                } }.mapNotNull { it.await() }
            }
            filteredCandidates
                .map { it.toEntity(media.type).toDomain() }
                .filterNot { it.id == media.id }
                .filterNot { it.stableKey in hiddenDiscovery }
                .distinctBy(MediaCard::stableKey)
                .take(18)
        }.getOrDefault(emptyList())
    }

    /** Loads cast/crew only when the Statistics tab requests it, avoiding startup and scrolling work. */
    suspend fun loadViewingPeople(history: List<TimelineCard>): Pair<List<PersonCard>, List<PersonCard>> = coroutineScope {
        val weights = history.groupingBy { it.media.stableKey }.eachCount()
        val media = history.map(TimelineCard::media).distinctBy(MediaCard::stableKey).take(12)
        val requests = Semaphore(4)
        val credits = media.map { item -> async {
            item to requests.withPermit { loadCast(item) }
        } }.map { it.await() }
        val actorCounts = mutableMapOf<Int, Pair<PersonCard, Int>>()
        val directorCounts = mutableMapOf<Int, Pair<PersonCard, Int>>()
        credits.forEach { (item, people) ->
            val weight = weights[item.stableKey] ?: 1
            people.distinctBy(PersonCard::id).forEach { person ->
                val isDirector = person.role.contains("director", true) ||
                    person.role.contains("creator", true) || person.role.contains("executive producer", true)
                val target = if (isDirector) directorCounts else actorCounts
                val previous = target[person.id]
                target[person.id] = person to ((previous?.second ?: 0) + weight)
            }
        }
        fun Map<Int, Pair<PersonCard, Int>>.top() = values
            .sortedWith(compareByDescending<Pair<PersonCard, Int>> { it.second }.thenBy { it.first.name })
            .take(5)
            .map { it.first }
        actorCounts.top() to directorCounts.top()
    }

    suspend fun loadRatings(media: MediaCard): List<RatingScore> {
        val enabledSources = preferences.ratingSources.first()
        val displayNames = linkedMapOf(
            "tmdb" to "TMDB",
            "imdb" to "IMDb",
            "metacritic" to "Metacritic",
            "tomatoes" to "R.Tomatoes",
            "letterboxd" to "Letterboxd",
        )
        val enabledDisplayNames = displayNames.filterKeys(enabledSources::contains)
        val base = enabledDisplayNames.map { (source, label) ->
            RatingScore(label, if (source == "tmdb" && media.score != null) "%.1f".format(media.score) else "—")
        }
        if (mdbListApiKey().isBlank()) return base
        val type = if (media.type == MediaType.MOVIE) "movie" else "show"
        fun formatted(source: String, value: Double): String = when (source) {
            "metacritic" -> value.toInt().toString()
            "tomatoes" -> "${value.toInt()}%"
            else -> "%.1f".format(value)
        }
        val remote = linkedMapOf<String, String>()
        // MDBList's single-title response carries every available rating in one
        // request. Prefer it so free-tier rate limiting cannot turn four parallel
        // requests into four dashes.
        val mediaInfoLoaded = runCatching {
            services.mdbList.mediaInfo(type, media.id, mdbListApiKey())["ratings"]?.jsonArray.orEmpty().forEach { element ->
                    val rating = element.jsonObject
                    val rawSource = listOf("source", "provider", "name").firstNotNullOfOrNull { key ->
                        rating[key]?.jsonPrimitive?.contentOrNull
                    }?.lowercase()?.replace(" ", "")
                    val source = when (rawSource) {
                        "rottentomatoes", "rtomatoes" -> "tomatoes"
                        else -> rawSource
                    }
                    val value = listOf("value", "rating", "score").firstNotNullOfOrNull { key ->
                        rating[key]?.jsonPrimitive?.doubleOrNull
                    }
                    if (source != null && value != null && source in displayNames) remote[source] = formatted(source, value)
                }
        }.isSuccess && remote.isNotEmpty()
        if (!mediaInfoLoaded) coroutineScope {
            val fallback = displayNames.keys.map { source -> async {
                runCatching {
                    services.mdbList.rating(
                        mediaType = type,
                        ratingSource = source,
                        apiKey = mdbListApiKey(),
                        request = MdbListRatingRequest(listOf(media.id.toString()), "tmdb"),
                    ).ratings.firstOrNull()?.rating?.let { source to formatted(source, it) }
                }.getOrNull()
            } }.mapNotNull { it.await() }
            remote.putAll(fallback)
        }
        return enabledDisplayNames.map { (source, label) ->
            RatingScore(label, remote[source] ?: if (source == "tmdb" && media.score != null) "%.1f".format(media.score) else "—")
        }
    }

    suspend fun syncSimkl(onProgress: (SyncProgress) -> Unit): Result<SimklSyncOutcome> {
        val previousSyncState = database.syncDao().get("all")
        val previousSuccessfulSync = previousSyncState?.lastSuccessfulSync
        val previousReport = preferences.syncReportNow()
        return runCatching {
        check(!preferences.tokenNow().isNullOrBlank()) { "Connect Simkl first" }
        fun progress(running: Boolean, value: Float, stage: SyncStage, message: String? = null) =
            onProgress(SyncProgress(running, value, stage, message, previousSuccessfulSync))
        progress(true, .08f, SyncStage.AUTH)
        val activity = services.simklSync.activities()
        progress(true, .20f, SyncStage.ACTIVITY)

        // Versioned marker: force one complete episode-level baseline after upgrading.
        // This repairs accounts where an earlier delta sync imported a show status but
        // did not receive every watched episode in that show.
        val episodeBaselineComplete = database.syncDao().get("episode_baseline_037") != null
        val librarySnapshotComplete = database.syncDao().get("library_snapshot_051") != null
        val previousShows = if (episodeBaselineComplete) database.syncDao().get("shows")?.remoteTimestamp else null
        val previousAnime = if (episodeBaselineComplete) database.syncDao().get("anime")?.remoteTimestamp else null
        val previousMovies = database.syncDao().get("movies")?.remoteTimestamp
        val previousShowsRemoved = database.syncDao().get("shows_removed")?.remoteTimestamp
        val previousAnimeRemoved = database.syncDao().get("anime_removed")?.remoteTimestamp
        val previousMoviesRemoved = database.syncDao().get("movies_removed")?.remoteTimestamp
        // Anime and ordinary shows share CineTrack's TV media type. If either
        // removal generation changes, fetch both complete lists before comparing
        // membership so one category can never erase the other.
        val tvRemovalChanged = !librarySnapshotComplete ||
            previousShowsRemoved != activity.tvShows.removedFromList ||
            previousAnimeRemoved != activity.anime.removedFromList
        val movieRemovalChanged = !librarySnapshotComplete ||
            previousMoviesRemoved != activity.movies.removedFromList
        val remoteChanged = previousSyncState?.remoteTimestamp != activity.all ||
            previousShows != activity.tvShows.all ||
            previousAnime != activity.anime.all ||
            previousMovies != activity.movies.all ||
            tvRemovalChanged ||
            movieRemovalChanged
        val pendingLocalStates = database.stateDao().pendingStates()
        val pendingWrites = database.syncDao().pendingWrites()
        val pendingEpisodeWrites = pendingWrites.filter {
            it.operation == "EPISODE_WATCHED" || it.operation == "EPISODE_UNWATCHED"
        }
        val pendingMediaHistoryRemovals = pendingWrites.filter { it.operation == "MEDIA_HISTORY_REMOVE" }
        val pendingCount = pendingLocalStates.size + pendingEpisodeWrites.size + pendingMediaHistoryRemovals.size

        // Simkl activity is the gate for every remote/item operation. When the
        // generation is unchanged and there is nothing local to push, stop here:
        // no playback request, no Room write and no Progress/UI reconstruction.
        if (
            !remoteChanged && pendingLocalStates.isEmpty() && pendingEpisodeWrites.isEmpty() &&
            pendingMediaHistoryRemovals.isEmpty()
        ) {
            val progressChanged = refreshProgressCache(
                request = ProgressRefreshRequest(),
            ) { value ->
                progress(true, value, SyncStage.PROCESSING)
            }
            val checkedAt = System.currentTimeMillis()
            val report = SyncReport(
                unchanged = database.stateDao().stateSnapshot().count { it.status != LibraryStatus.NONE.name },
                lastFullSync = previousReport.lastFullSync,
                lastIncrementalSync = checkedAt,
                databaseUntouched = !progressChanged,
            )
            preferences.markSimklChecked(checkedAt)
            preferences.saveSyncReport(report)
            onProgress(
                SyncProgress(
                    running = false,
                    progress = 1f,
                    stage = SyncStage.COMPLETE,
                    message = if (progressChanged) "Progress data refreshed." else "No remote changes detected—database untouched.",
                    lastSuccessfulSync = previousSuccessfulSync,
                    report = report,
                ),
            )
            return@runCatching SimklSyncOutcome(itemsChanged = progressChanged, report = report)
        }

        val remote = coroutineScope {
            val shows = async {
                if (tvRemovalChanged) services.simklSync.allItems("shows")
                else if (previousShows == null || previousShows != activity.tvShows.all) services.simklSync.allItems("shows", previousShows)
                else SimklLibraryResponse()
            }
            val anime = async {
                if (tvRemovalChanged) services.simklSync.allItems("anime")
                else if (previousAnime == null || previousAnime != activity.anime.all) services.simklSync.allItems("anime", previousAnime)
                else SimklLibraryResponse()
            }
            val movies = async {
                if (movieRemovalChanged) services.simklSync.allItems("movies")
                else if (previousMovies == null || previousMovies != activity.movies.all) services.simklSync.allItems("movies", previousMovies)
                else SimklLibraryResponse()
            }
            Triple(shows.await(), anime.await(), movies.await())
        }
        val playbackResult = if (remoteChanged) runCatching {
            coroutineScope {
                val episodes = async { services.simklSync.playback("episodes") }
                val movies = async { services.simklSync.playback("movies") }
                episodes.await() + movies.await()
            }
        } else Result.success(emptyList())
        val importedPlayback = playbackResult.getOrNull()?.mapNotNull { it.toPlaybackEntity() }
        val previousPlayback = if (importedPlayback != null) database.timelineDao().playbackSnapshot() else emptyList()
        val tvPlaybackChanged = importedPlayback?.let { incoming ->
            incoming.filter { it.mediaType == MediaType.TV.name }.toSet() !=
                previousPlayback.filter { it.mediaType == MediaType.TV.name }.toSet()
        } ?: false
        progress(true, .42f, SyncStage.PLAYBACK)

        val remoteShows = mergeSimklItems(remote.first.shows + remote.first.anime + remote.second.shows + remote.second.anime)
        val remoteMovies = mergeSimklItems(remote.third.movies)
        val resolvedShows = resolveSimklItems(remoteShows, MediaType.TV)
        val resolvedMovies = resolveSimklItems(remoteMovies, MediaType.MOVIE)
        val resolvedItems = resolvedShows + resolvedMovies
        // Simkl's episode arrays are the watched-set, but an older baseline could
        // be incomplete even when the show itself was already `completed`.
        // On this versioned repair, expand completed shows against TMDB's aired
        // regular episodes before replacing local episode history.
        val completedEpisodeRepairs: Map<Int, List<EpisodeCard>> = if (!episodeBaselineComplete && tmdbApiKey().isNotBlank()) {
            val today = localToday()
            coroutineScope {
                resolvedItems
                    .filter { it.type == MediaType.TV && it.item.status.equals("completed", ignoreCase = true) }
                    .chunked(3)
                    .flatMap { batch ->
                        batch.map { resolved ->
                            async {
                                resolved.tmdbId to runCatching {
                                    val importedBySeason = resolved.item.seasons.associate { season ->
                                        season.number to season.episodes.map { it.number }.distinct().size
                                    }
                                    services.tmdb.show(resolved.tmdbId).seasons
                                        .filter { it.number > 0 && importedBySeason.getOrDefault(it.number, 0) < it.episodeCount }
                                        .map { it.number }
                                        .distinct()
                                        .sorted()
                                        .flatMap { seasonNumber ->
                                            services.tmdb.season(resolved.tmdbId, seasonNumber).episodes
                                                .filter { episode ->
                                                    episode.airDate?.let { raw ->
                                                        runCatching { !LocalDate.parse(raw.take(10)).isAfter(today) }.getOrDefault(true)
                                                    } ?: true
                                                }
                                                .map { episode ->
                                                    EpisodeCard(
                                                        id = episode.id,
                                                        showId = resolved.tmdbId,
                                                        season = episode.season,
                                                        number = episode.number,
                                                        title = episode.name,
                                                        overview = episode.overview,
                                                        airDate = episode.airDate,
                                                        stillUrl = episode.stillPath?.let { path -> "https://image.tmdb.org/t/p/w780$path" },
                                                        runtimeMinutes = episode.runtime,
                                                        watched = true,
                                                    )
                                                }
                                        }
                                }.getOrDefault(emptyList())
                            }
                        }.map { it.await() }
                    }
                    .toMap()
            }
        } else emptyMap()
        val remoteStates = resolvedItems.mapNotNull { it.item.toState(it.type, it.tmdbId) }
        val remoteTvKeys = resolvedShows.map { "${MediaType.TV.name}:${it.tmdbId}" }.toSet()
        val remoteMovieKeys = resolvedMovies.map { "${MediaType.MOVIE.name}:${it.tmdbId}" }.toSet()
        val localStates = database.stateDao().stateSnapshot()
        val localStatesByKey = localStates.associateBy { "${it.mediaType}:${it.mediaId}" }
        val localTvCount = localStates.count { it.mediaType == MediaType.TV.name && it.status != LibraryStatus.NONE.name }
        val localMovieCount = localStates.count { it.mediaType == MediaType.MOVIE.name && it.status != LibraryStatus.NONE.name }
        // Reconcile absence only when every item in the full response resolved to
        // a stable TMDB id. A partial ID-resolution failure preserves the previous
        // local snapshot instead of deleting valid entries. Also reject a sudden
        // empty response for a non-empty local library; that is much more likely
        // to be a partial server response than a deliberate account-wide removal.
        val completeTvSnapshot = tvRemovalChanged && resolvedShows.size == remoteShows.size &&
            (remoteShows.isNotEmpty() || localTvCount == 0)
        val completeMovieSnapshot = movieRemovalChanged && resolvedMovies.size == remoteMovies.size &&
            (remoteMovies.isNotEmpty() || localMovieCount == 0)
        val remoteRemovedStates = localStates.mapNotNull { state ->
            if (state.dirty || state.status == LibraryStatus.NONE.name) return@mapNotNull null
            val key = "${state.mediaType}:${state.mediaId}"
            val removed = when (state.mediaType) {
                MediaType.TV.name -> completeTvSnapshot && key !in remoteTvKeys
                MediaType.MOVIE.name -> completeMovieSnapshot && key !in remoteMovieKeys
                else -> false
            }
            if (removed) {
                state.copy(
                    status = LibraryStatus.NONE.name,
                    watched = false,
                    updatedAt = System.currentTimeMillis(),
                    dirty = false,
                )
            } else null
        }
        val libraryChanged = remoteStates.isNotEmpty() || remoteRemovedStates.isNotEmpty()
        val mediaCandidates = (resolvedItems.mapNotNull { it.item.toMediaEntity(it.type, it.tmdbId) } +
            playbackResult.getOrDefault(emptyList()).mapNotNull { it.toMediaEntity() })
            .distinctBy { "${it.mediaType}:${it.tmdbId}" }
        val localMediaByKey = database.mediaDao().mediaSnapshot()
            .associateBy { "${it.mediaType}:${it.tmdbId}" }
        // Commit Simkl's identifiers and list state immediately. TMDB artwork
        // enrichment used to run inside synchronization in serial batches and
        // made the apparent "local save" last minutes. Missing artwork is now
        // repaired asynchronously after the correctness-critical cache commit.
        val newMedia = mediaCandidates.map { incoming ->
            val existing = localMediaByKey["${incoming.mediaType}:${incoming.tmdbId}"]
                ?: return@map incoming
            incoming.copy(
                title = incoming.title.ifBlank { existing.title },
                overview = incoming.overview.ifBlank { existing.overview },
                posterPath = incoming.posterPath ?: existing.posterPath,
                backdropPath = incoming.backdropPath ?: existing.backdropPath,
                releaseDate = incoming.releaseDate ?: existing.releaseDate,
                score = incoming.score ?: existing.score,
                runtimeMinutes = incoming.runtimeMinutes ?: existing.runtimeMinutes,
                genres = incoming.genres.ifBlank { existing.genres },
                providers = incoming.providers.ifBlank { existing.providers },
                collectionId = incoming.collectionId ?: existing.collectionId,
            )
        }

        // Push pending local mutations before publishing the downloaded snapshot.
        // If a request fails, the transaction below never runs: the existing local
        // snapshot and dirty queue remain intact and can safely retry later.
        val pushedWriteIds = mutableListOf<Long>()
        pendingMediaHistoryRemovals.forEach { write ->
            pushMediaHistoryRemoval(write)
            pushedWriteIds += write.id
        }
        pendingLocalStates.forEach { state -> pushLibraryState(state) }
        pendingEpisodeWrites.forEach { write ->
            val parts = write.payload.split(':', limit = 3)
            val season = parts.getOrNull(0)?.toIntOrNull() ?: return@forEach
            val episode = parts.getOrNull(1)?.toIntOrNull() ?: return@forEach
            val watchedAt = parts.getOrNull(2) ?: Instant.now().toString()
            val request = SimklSyncRequest(
                shows = listOf(
                    SimklSyncItem(
                        ids = SimklIds(tmdb = write.mediaId.toString()),
                        seasons = listOf(
                            com.cinetrack.data.remote.SimklSeason(
                                season,
                                listOf(com.cinetrack.data.remote.SimklEpisode(episode, if (write.operation == "EPISODE_WATCHED") watchedAt else null)),
                            ),
                        ),
                    ),
                ),
            )
            if (write.operation == "EPISODE_WATCHED") services.simklSync.addHistory(request)
            else services.simklSync.removeHistory(request)
            pushedWriteIds += write.id
        }
        progress(true, .62f, SyncStage.HISTORY)

        val committedAt = System.currentTimeMillis()
        // Build every history mutation before opening Room's write transaction.
        // The previous implementation performed mapping plus one DELETE per
        // episode while SQLite held the transaction, which scaled badly on
        // accounts with long watch histories.
        val importedHistory = mutableListOf<WatchHistoryEntity>()
        val baselineShowIds = mutableSetOf<Int>()
        val deltaEpisodeKeys = mutableSetOf<Triple<Int, Int, Int>>()
        resolvedItems.forEach { resolved ->
            val item = resolved.item
            val type = resolved.type
            val tmdb = resolved.tmdbId
            item.lastWatchedAt?.takeIf { type == MediaType.MOVIE }?.let {
                importedHistory += WatchHistoryEntity(mediaType = type.name, mediaId = tmdb, watchedAt = it)
            }
            val repairedEpisodes = completedEpisodeRepairs[tmdb].orEmpty()
            if (type == MediaType.TV && (item.seasons.isNotEmpty() || repairedEpisodes.isNotEmpty())) {
                if (!episodeBaselineComplete) baselineShowIds += tmdb
                val watchedEpisodes = mutableMapOf<Pair<Int, Int>, Pair<String?, String?>>()
                item.seasons.forEach { season ->
                    season.episodes.forEach { episode ->
                        watchedEpisodes[season.number to episode.number] =
                            episode.title.takeIf(String::isNotBlank) to episode.watchedAt
                    }
                }
                repairedEpisodes.forEach { episode ->
                    watchedEpisodes.putIfAbsent(
                        episode.season to episode.number,
                        episode.title.takeIf(String::isNotBlank) to null,
                    )
                }
                watchedEpisodes.forEach { (key, episode) ->
                    val (seasonNumber, episodeNumber) = key
                    if (episodeBaselineComplete) deltaEpisodeKeys += Triple(tmdb, seasonNumber, episodeNumber)
                    importedHistory += WatchHistoryEntity(
                        mediaType = MediaType.TV.name,
                        mediaId = tmdb,
                        season = seasonNumber,
                        episodeNumber = episodeNumber,
                        episodeTitle = episode.first,
                        watchedAt = episode.second ?: item.lastWatchedAt ?: item.addedAt ?: Instant.now().toString(),
                    )
                }
            }
        }
        val historyRowsToReplace = if (deltaEpisodeKeys.isNotEmpty()) {
            database.timelineDao().historySnapshot().mapNotNull { history ->
                val season = history.season ?: return@mapNotNull null
                val episode = history.episodeNumber ?: return@mapNotNull null
                history.id.takeIf { Triple(history.mediaId, season, episode) in deltaEpisodeKeys }
            }
        } else emptyList()
        val syncStates = buildList {
            add(SyncStateEntity("all", activity.all, committedAt))
            add(SyncStateEntity("shows", activity.tvShows.all, committedAt))
            add(SyncStateEntity("anime", activity.anime.all, committedAt))
            add(SyncStateEntity("movies", activity.movies.all, committedAt))
            if (completeTvSnapshot) {
                add(SyncStateEntity("shows_removed", activity.tvShows.removedFromList, committedAt))
                add(SyncStateEntity("anime_removed", activity.anime.removedFromList, committedAt))
            }
            if (completeMovieSnapshot) {
                add(SyncStateEntity("movies_removed", activity.movies.removedFromList, committedAt))
            }
            add(SyncStateEntity("episode_baseline_037", activity.all, committedAt))
            if (librarySnapshotComplete || (completeTvSnapshot && completeMovieSnapshot)) {
                add(SyncStateEntity("library_snapshot_051", activity.all, committedAt))
            }
        }
        database.withTransaction {
            if (newMedia.isNotEmpty()) database.mediaDao().upsertMedia(newMedia)
            if (remoteStates.isNotEmpty() || remoteRemovedStates.isNotEmpty()) {
                database.stateDao().upsertAll(remoteStates + remoteRemovedStates)
                // Locally dirty states win over a simultaneous remote snapshot.
                database.stateDao().upsertAll(pendingLocalStates)
            }
            remoteRemovedStates.groupBy(UserMediaStateEntity::mediaType).forEach { (mediaType, states) ->
                states.map(UserMediaStateEntity::mediaId).distinct().chunked(500).forEach { ids ->
                    database.timelineDao().deleteMediaHistories(mediaType, ids)
                }
            }
            baselineShowIds.chunked(500).forEach { ids ->
                database.timelineDao().deleteMediaHistories(MediaType.TV.name, ids)
            }
            historyRowsToReplace.chunked(500).forEach { ids ->
                database.timelineDao().deleteHistoryRows(ids)
            }
            // Room can bind the complete import in one prepared batch instead
            // of executing one INSERT statement for every watched episode.
            if (importedHistory.isNotEmpty()) {
                database.timelineDao().insertHistoryItems(importedHistory)
            }
            importedPlayback?.let { playbacks ->
                // An unchanged generation plus an empty response is treated as a
                // transient/partial playback response. Preserve the previous cache;
                // a real removal is committed when Simkl advances its activity id.
                if (playbacks.isNotEmpty() || remoteChanged) {
                    database.timelineDao().clearPlayback()
                    database.timelineDao().upsertPlayback(playbacks)
                }
            }
            pendingLocalStates.forEach { state ->
                database.stateDao().markCleanIfUnchanged(state.mediaType, state.mediaId, state.updatedAt)
            }
            pushedWriteIds.chunked(500).forEach { ids -> database.syncDao().deleteWrites(ids) }
            database.syncDao().upsertAll(syncStates)
            // Library membership, history, playback and sync generation become
            // visible in the same commit. No observer can see the halfway state.
            if (libraryChanged) rebuildLibraryRailInTransaction()
        }
        preferences.markSimklChecked(committedAt)
        progress(true, .78f, SyncStage.COMMIT)
        val remoteStateKeys = remoteStates.map { "${it.mediaType}:${it.mediaId}" }.toSet()
        val addedCount = remoteStates.count { state ->
            localStatesByKey["${state.mediaType}:${state.mediaId}"]?.status in setOf(null, LibraryStatus.NONE.name)
        }
        val conflictCount = pendingLocalStates.count { "${it.mediaType}:${it.mediaId}" in remoteStateKeys }
        val fullSync = !episodeBaselineComplete || !librarySnapshotComplete || completeTvSnapshot || completeMovieSnapshot
        val report = SyncReport(
            downloaded = resolvedItems.size + importedHistory.size,
            uploaded = pendingCount,
            added = addedCount,
            removed = remoteRemovedStates.size,
            unchanged = (localStates.size - addedCount - remoteRemovedStates.size).coerceAtLeast(0),
            pendingLocalChanges = 0,
            conflicts = conflictCount,
            lastFullSync = if (fullSync) committedAt else previousReport.lastFullSync,
            lastIncrementalSync = committedAt,
            databaseUntouched = false,
        )
        preferences.saveSyncReport(report)
        val tvLibraryChanged = (remoteStates + remoteRemovedStates + pendingLocalStates)
            .any { it.mediaType == MediaType.TV.name }
        val episodeHistoryChanged = importedHistory.any { it.mediaType == MediaType.TV.name } ||
            baselineShowIds.isNotEmpty() || historyRowsToReplace.isNotEmpty() || pendingEpisodeWrites.isNotEmpty() ||
            pendingMediaHistoryRemovals.any { it.mediaType == MediaType.TV.name }
        val progressChanged = refreshProgressCache(
            request = ProgressRefreshRequest(
                tvLibraryChanged = tvLibraryChanged,
                episodeHistoryChanged = episodeHistoryChanged,
                tvPlaybackChanged = tvPlaybackChanged,
            ),
        ) { value ->
            progress(true, value, SyncStage.PROCESSING)
        }
        onProgress(SyncProgress(false, 1f, SyncStage.COMPLETE, lastSuccessfulSync = committedAt, report = report))
        SimklSyncOutcome(itemsChanged = remoteChanged || pendingCount > 0 || progressChanged, report = report)
        }.onFailure {
            val committedSync = database.syncDao().get("all")?.lastSuccessfulSync
            val databaseWasUpdated = committedSync != null && committedSync != previousSuccessfulSync
            val report = previousReport.copy(
                pendingLocalChanges = database.stateDao().pendingStates().size + database.syncDao().pendingWrites().size,
                failedOperations = 1,
                lastIncrementalSync = committedSync ?: previousReport.lastIncrementalSync,
                databaseUntouched = !databaseWasUpdated,
            )
            preferences.saveSyncReport(report)
            onProgress(SyncProgress(false, 0f, SyncStage.ERROR, it.message, previousSuccessfulSync, report))
        }
    }

    suspend fun completeLogin(code: String, state: String?): Result<Unit> =
        preferences.completeSimklLogin(code, state, services.simklAuth).onSuccess {
            onTokenChanged(preferences.tokenNow())
        }

    suspend fun disconnectSimkl() {
        preferences.setToken(null)
        onTokenChanged(null)
    }

    suspend fun setUiAccent(value: String) = preferences.setUiAccent(value)

    suspend fun setIntroductionCompleted(value: Boolean) = preferences.setIntroductionCompleted(value)

    suspend fun verifyAndSetTmdbApiKey(value: String): Result<Unit> = runCatching {
        val candidate = value.trim()
        require(candidate.isNotBlank()) { "TMDB API credential cannot be empty" }
        NetworkFactory.create(
            token = { null },
            tmdbApiKey = { candidate },
            metadataLanguage = { "en-US" },
            metadataRegion = { "US" },
            metadataTimezone = { "UTC" },
        ).tmdb.trendingMovies()
        preferences.setTmdbApiKey(candidate)
        onTmdbApiKeyChanged(candidate)
    }

    suspend fun verifyAndSetMdbListApiKey(value: String): Result<Unit> = runCatching {
        val candidate = value.trim()
        require(candidate.isNotBlank()) { "MDBList API credential cannot be empty" }
        services.mdbList.mediaInfo(mediaType = "movie", id = 550, apiKey = candidate)
        preferences.setMdbListApiKey(candidate)
        onMdbListApiKeyChanged(candidate)
    }

    suspend fun setTmdbApiKey(value: String?) {
        preferences.setTmdbApiKey(value)
        onTmdbApiKeyChanged(preferences.tmdbApiKeyNow())
    }

    suspend fun setMdbListApiKey(value: String?) {
        preferences.setMdbListApiKey(value)
        onMdbListApiKeyChanged(preferences.mdbListApiKeyNow())
    }

    suspend fun setMetadataLanguage(value: String) {
        preferences.setMetadataLanguage(value)
        onMetadataLanguageChanged(value)
    }

    suspend fun setMetadataRegion(value: String) {
        preferences.setMetadataRegion(value)
        onMetadataRegionChanged(value)
    }

    suspend fun setMetadataTimezone(value: String) {
        preferences.setMetadataTimezone(value)
        onMetadataTimezoneChanged(value)
    }

    suspend fun exportJson(): String {
        val state = loadCachedState()
        return buildJsonObject {
            put("format", "cinetrack-0.63")
            put("exportedAt", Instant.now().toString())
            putJsonArray("library") {
                state.rails[RailIds.LIBRARY].orEmpty().forEach { media ->
                    add(buildJsonObject {
                        put("tmdbId", media.id)
                        put("type", media.type.name.lowercase())
                        put("title", media.title)
                        put("status", media.status.name.lowercase())
                        put("watched", media.watched)
                    })
                }
            }
            putJsonArray("history") {
                state.history.forEach { item ->
                    add(buildJsonObject {
                        put("tmdbId", item.media.id)
                        put("type", item.media.type.name.lowercase())
                        put("watchedAt", item.timestamp)
                        item.episodeLabel?.let { put("episode", it) }
                    })
                }
            }
        }.toString()
    }

    /**
     * Creates the logical files for a portable CineTrack backup. JSONL is the
     * authoritative representation; CSV mirrors it for spreadsheet inspection.
     * API credentials and the Simkl token are deliberately never exported.
     */
    suspend fun exportBackupFiles(sections: Set<String>): Map<String, String> {
        val selected = sections.ifEmpty { setOf("library", "progress", "history", "settings") }
        val state = loadCachedState()
        val payload = linkedMapOf<String, String>()
        val counts = linkedMapOf<String, Int>()

        if ("library" in selected) {
            val items = state.rails[RailIds.LIBRARY].orEmpty()
            payload["data/library.jsonl"] = items.joinToString("\n") { media ->
                buildJsonObject {
                    put("tmdbId", media.id)
                    put("type", media.type.name.lowercase())
                    put("title", media.title)
                    put("status", media.status.name.lowercase())
                    put("watched", media.watched)
                    media.releaseDate?.let { put("releaseDate", it) }
                }.toString()
            }.withTrailingLine()
            payload["readable/library.csv"] = buildString {
                appendLine(csvRow("tmdb_id", "type", "title", "status", "watched", "release_date"))
                items.forEach { media ->
                    appendLine(csvRow(media.id, media.type.name.lowercase(), media.title, media.status.name.lowercase(), media.watched, media.releaseDate.orEmpty()))
                }
            }
            counts["library"] = items.size
        }

        if ("progress" in selected) {
            val items = state.playbackTv + state.playbackMovies
            payload["data/progress.jsonl"] = items.joinToString("\n") { item ->
                buildJsonObject {
                    put("tmdbId", item.media.id)
                    put("type", item.media.type.name.lowercase())
                    put("title", item.media.title)
                    item.season?.let { put("season", it) }
                    item.episodeNumber?.let { put("episode", it) }
                    item.episodeTitle?.let { put("episodeTitle", it) }
                    put("progress", item.progress.toDouble())
                }.toString()
            }.withTrailingLine()
            payload["readable/progress.csv"] = buildString {
                appendLine(csvRow("tmdb_id", "type", "title", "season", "episode", "episode_title", "progress"))
                items.forEach { item ->
                    appendLine(csvRow(item.media.id, item.media.type.name.lowercase(), item.media.title, item.season ?: "", item.episodeNumber ?: "", item.episodeTitle.orEmpty(), item.progress))
                }
            }
            counts["progress"] = items.size
        }

        if ("history" in selected) {
            val items = state.history
            payload["data/history.jsonl"] = items.joinToString("\n") { item ->
                buildJsonObject {
                    put("tmdbId", item.media.id)
                    put("type", item.media.type.name.lowercase())
                    put("title", item.media.title)
                    put("watchedAt", item.timestamp)
                    item.season?.let { put("season", it) }
                    item.episodeNumber?.let { put("episode", it) }
                    item.episodeLabel?.let { put("episodeLabel", it) }
                }.toString()
            }.withTrailingLine()
            payload["readable/history.csv"] = buildString {
                appendLine(csvRow("tmdb_id", "type", "title", "watched_at", "season", "episode", "episode_label"))
                items.forEach { item ->
                    appendLine(csvRow(item.media.id, item.media.type.name.lowercase(), item.media.title, item.timestamp, item.season ?: "", item.episodeNumber ?: "", item.episodeLabel.orEmpty()))
                }
            }
            counts["history"] = items.size
        }

        if ("settings" in selected) {
            val settings = buildJsonObject {
                put("uiAccent", state.uiAccent)
                put("metadataLanguage", state.metadataLanguage)
                put("metadataRegion", state.metadataRegion)
                put("metadataTimezone", state.metadataTimezone)
                put("backgroundSync", state.backgroundSync)
                put("wifiOnly", state.wifiOnly)
                put("contentRegions", state.contentRegions.sorted().joinToString(","))
                put("ratingSources", state.ratingSources.sorted().joinToString(","))
                put("excludeSpecials", state.excludeSpecials)
                put("preferredProviders", state.preferredProviders.sorted().joinToString("|"))
                put("cardDensity", state.cardDensity)
                put("hiddenDiscovery", state.hiddenDiscovery.sorted().joinToString("|"))
            }.toString()
            payload["data/settings.jsonl"] = "$settings\n"
            payload["readable/settings.csv"] = buildString {
                appendLine(csvRow("ui_accent", "metadata_language", "metadata_region", "metadata_timezone", "background_sync", "wifi_only", "content_regions", "rating_sources"))
                appendLine(csvRow(state.uiAccent, state.metadataLanguage, state.metadataRegion, state.metadataTimezone, state.backgroundSync, state.wifiOnly, state.contentRegions.sorted().joinToString("|"), state.ratingSources.sorted().joinToString("|")))
            }
            counts["settings"] = 1
        }

        val generatedAt = Instant.now().toString()
        val manifest = buildJsonObject {
            put("formatVersion", "1.0")
            put("generatedAt", generatedAt)
            put("generator", "CineTrack ${BuildConfig.VERSION_NAME}")
            putJsonObject("sections") {
                counts.forEach { (section, count) ->
                    putJsonObject(section) {
                        put("records", count)
                        put("dataFile", "data/$section.jsonl")
                        put("readableFile", "readable/$section.csv")
                        put("sha256", payload["data/$section.jsonl"].orEmpty().sha256())
                    }
                }
            }
        }.toString()
        return linkedMapOf(
            "manifest.json" to manifest,
            "README.md" to "# CineTrack backup\n\nGenerated $generatedAt. Files in `data/` are authoritative JSON Lines; `readable/` contains matching CSV exports. Authentication tokens and API credentials are excluded.\n",
        ).apply { putAll(payload) }
    }

    suspend fun createAutomaticBackup() {
        preferences.saveAutomaticBackup(exportBackupFiles(setOf("library", "progress", "history", "settings")))
    }

    suspend fun restoreAutomaticBackup(): Int = restoreBackupFiles(preferences.readAutomaticBackup())

    /** Validates the complete archive before a single Room row is changed. */
    suspend fun restoreBackupFiles(files: Map<String, String>): Int {
        val manifestText = files["manifest.json"] ?: error("Backup manifest is missing")
        val manifest = Json.parseToJsonElement(manifestText).jsonObject
        check(manifest["formatVersion"]?.jsonPrimitive?.contentOrNull?.startsWith("1.") == true) {
            "Unsupported backup format"
        }
        val sections = manifest["sections"]?.jsonObject ?: error("Backup sections are missing")
        sections.forEach { (_, value) ->
            val section = value.jsonObject
            val path = section["dataFile"]?.jsonPrimitive?.contentOrNull ?: error("Backup data path is missing")
            val expected = section["sha256"]?.jsonPrimitive?.contentOrNull ?: error("Backup checksum is missing")
            val contents = files[path] ?: error("Backup file $path is missing")
            check(contents.sha256() == expected) { "Backup checksum failed for $path" }
        }

        val media = mutableListOf<MediaEntity>()
        val states = mutableListOf<UserMediaStateEntity>()
        files["data/library.jsonl"].orEmpty().lineSequence().filter(String::isNotBlank).forEach { line ->
            val item = Json.parseToJsonElement(line).jsonObject
            val id = item["tmdbId"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: error("Invalid library id")
            val type = item["type"]?.jsonPrimitive?.contentOrNull?.uppercase()?.let(MediaType::valueOf) ?: error("Invalid media type")
            val title = item["title"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank) ?: error("Invalid title")
            val status = item["status"]?.jsonPrimitive?.contentOrNull?.uppercase()?.let(LibraryStatus::valueOf) ?: LibraryStatus.NONE
            val watched = item["watched"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false
            media += MediaEntity(type.name, id, title, "", null, null, item["releaseDate"]?.jsonPrimitive?.contentOrNull, null, null, "", "", null)
            states += UserMediaStateEntity(type.name, id, status.name, watched, dirty = false)
        }
        val playback = mutableListOf<PlaybackEntity>()
        files["data/progress.jsonl"].orEmpty().lineSequence().filter(String::isNotBlank).forEach { line ->
            val item = Json.parseToJsonElement(line).jsonObject
            val id = item["tmdbId"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: error("Invalid progress id")
            val type = item["type"]?.jsonPrimitive?.contentOrNull?.uppercase()?.let(MediaType::valueOf) ?: error("Invalid progress type")
            val title = item["title"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank) ?: error("Invalid progress title")
            media += MediaEntity(type.name, id, title, "", null, null, null, null, null, "", "", null)
            playback += PlaybackEntity(
                mediaType = type.name,
                mediaId = id,
                progress = item["progress"]?.jsonPrimitive?.contentOrNull?.toFloatOrNull()?.coerceIn(0f, 1f) ?: 0f,
                positionSeconds = 0L,
                durationSeconds = 0L,
                updatedAt = Instant.now().toString(),
                season = item["season"]?.jsonPrimitive?.contentOrNull?.toIntOrNull(),
                episodeNumber = item["episode"]?.jsonPrimitive?.contentOrNull?.toIntOrNull(),
                episodeTitle = item["episodeTitle"]?.jsonPrimitive?.contentOrNull,
            )
        }
        val history = mutableListOf<WatchHistoryEntity>()
        files["data/history.jsonl"].orEmpty().lineSequence().filter(String::isNotBlank).forEach { line ->
            val item = Json.parseToJsonElement(line).jsonObject
            val id = item["tmdbId"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: error("Invalid history id")
            val type = item["type"]?.jsonPrimitive?.contentOrNull?.uppercase()?.let(MediaType::valueOf) ?: error("Invalid media type")
            val watchedAt = item["watchedAt"]?.jsonPrimitive?.contentOrNull ?: error("Invalid watch timestamp")
            history += WatchHistoryEntity(
                mediaType = type.name,
                mediaId = id,
                season = item["season"]?.jsonPrimitive?.contentOrNull?.toIntOrNull(),
                episodeNumber = item["episode"]?.jsonPrimitive?.contentOrNull?.toIntOrNull(),
                episodeTitle = item["episodeLabel"]?.jsonPrimitive?.contentOrNull?.substringAfter(" · ", "")?.takeIf(String::isNotBlank),
                watchedAt = watchedAt,
            )
        }
        val settings = files["data/settings.jsonl"]?.lineSequence()?.firstOrNull(String::isNotBlank)
            ?.let { Json.parseToJsonElement(it).jsonObject }
        check(media.isNotEmpty() || history.isNotEmpty() || playback.isNotEmpty() || settings != null) {
            "The backup has no restorable records"
        }
        database.withTransaction {
            if (media.isNotEmpty()) database.mediaDao().upsertMedia(media.distinctBy { "${it.mediaType}:${it.tmdbId}" })
            if (states.isNotEmpty()) database.stateDao().upsertAll(states.distinctBy { "${it.mediaType}:${it.mediaId}" })
            if (history.isNotEmpty()) database.timelineDao().insertHistoryItems(history)
            if (files.containsKey("data/progress.jsonl")) {
                database.timelineDao().clearPlayback()
                if (playback.isNotEmpty()) database.timelineDao().upsertPlayback(playback)
            }
            database.upNextDao().clear()
            database.syncDao().delete("up_next_cache_v1")
            rebuildLibraryRailInTransaction()
        }
        settings?.let { saved ->
            saved["uiAccent"]?.jsonPrimitive?.contentOrNull?.let { setUiAccent(it) }
            saved["metadataLanguage"]?.jsonPrimitive?.contentOrNull?.let { setMetadataLanguage(it) }
            saved["metadataRegion"]?.jsonPrimitive?.contentOrNull?.let { setMetadataRegion(it) }
            saved["metadataTimezone"]?.jsonPrimitive?.contentOrNull?.let { setMetadataTimezone(it) }
            saved["backgroundSync"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()?.let { preferences.setBackgroundSync(it) }
            saved["wifiOnly"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()?.let { preferences.setWifiOnly(it) }
            saved["contentRegions"]?.jsonPrimitive?.contentOrNull?.split(',')?.filter(String::isNotBlank)?.toSet()
                ?.let { preferences.setContentRegions(it) }
            saved["excludeSpecials"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()?.let { preferences.setExcludeSpecials(it) }
            saved["preferredProviders"]?.jsonPrimitive?.contentOrNull?.split('|')?.filter(String::isNotBlank)?.toSet()
                ?.let { preferences.setPreferredProviders(it) }
            saved["cardDensity"]?.jsonPrimitive?.contentOrNull?.let { preferences.setCardDensity(it) }
            saved["hiddenDiscovery"]?.jsonPrimitive?.contentOrNull?.split('|')?.filter(String::isNotBlank)?.toSet()
                ?.let { preferences.setHiddenDiscovery(it) }
            val sources = saved["ratingSources"]?.jsonPrimitive?.contentOrNull?.split(',')?.filter(String::isNotBlank)?.toSet()
            if (sources != null) listOf("imdb", "tmdb", "metacritic", "tomatoes", "letterboxd").forEach { source ->
                preferences.setRatingSource(source, source in sources)
            }
        }
        return states.size + history.size
    }

    private fun String.withTrailingLine(): String = if (isBlank()) "" else trimEnd() + "\n"

    private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(toByteArray())
        .joinToString("") { byte -> "%02x".format(byte) }

    private fun csvRow(vararg values: Any): String = values.joinToString(",") { value ->
        val escaped = value.toString().replace("\"", "\"\"")
        "\"$escaped\""
    }

    private suspend fun rebuildLibraryRailInTransaction() {
        val states = database.stateDao().stateSnapshot()
            .filter { it.status != LibraryStatus.NONE.name }
            .sortedByDescending(UserMediaStateEntity::updatedAt)
        val mediaByKey = database.mediaDao().mediaSnapshot().associateBy { "${it.mediaType}:${it.tmdbId}" }
        val media = states.mapNotNull { mediaByKey["${it.mediaType}:${it.mediaId}"] }
        database.mediaDao().clearRail(RailIds.LIBRARY)
        database.mediaDao().upsertRails(
            media.mapIndexed { index, item ->
                com.cinetrack.data.local.MediaRailEntity(RailIds.LIBRARY, item.mediaType, item.tmdbId, index)
            },
        )
    }

    private fun TmdbMediaDto.toEntity(type: MediaType) = MediaEntity(
        mediaType = type.name,
        tmdbId = id,
        title = title ?: name ?: "Untitled",
        overview = overview,
        posterPath = posterPath,
        backdropPath = backdropPath,
        releaseDate = releaseDate ?: firstAirDate,
        score = voteAverage,
        runtimeMinutes = runtime ?: episodeRunTime.firstOrNull(),
        genres = if (genres.isNotEmpty()) genres.joinToString("|") { it.name }
        else genreIds.mapNotNull(::tmdbGenreName).joinToString("|"),
        providers = watchProviders?.results?.get("IT")?.let { (it.flatrate + it.rent + it.buy).distinctBy { p -> p.id }.joinToString("|") { p -> p.name } }.orEmpty(),
        collectionId = collection?.id,
    )

    private fun tmdbGenreName(id: Int): String? = when (id) {
        12 -> "Adventure"
        14 -> "Fantasy"
        16 -> "Animation"
        18 -> "Drama"
        27 -> "Horror"
        28 -> "Action"
        35 -> "Comedy"
        36 -> "History"
        37 -> "Western"
        53 -> "Thriller"
        80 -> "Crime"
        99 -> "Documentary"
        878 -> "Science Fiction"
        9648 -> "Mystery"
        10402 -> "Music"
        10749 -> "Romance"
        10751 -> "Family"
        10752 -> "War"
        10759 -> "Action & Adventure"
        10762 -> "Kids"
        10763 -> "News"
        10764 -> "Reality"
        10765 -> "Sci-Fi & Fantasy"
        10766 -> "Soap"
        10767 -> "Talk"
        10768 -> "War & Politics"
        else -> null
    }

    private data class ResolvedSimklItem(
        val item: SimklLibraryItem,
        val type: MediaType,
        val tmdbId: Int,
    )

    private fun mergeSimklItems(items: List<SimklLibraryItem>): List<SimklLibraryItem> =
        items.groupBy { item ->
            val media = item.show ?: item.movie
            media?.ids?.tmdb ?: media?.ids?.imdb ?: media?.ids?.tvdb ?: media?.ids?.simkl?.toString()
                ?: "${media?.title.orEmpty().lowercase()}:${media?.year?.toString().orEmpty()}"
        }.values.mapNotNull { duplicates ->
            val preferred = duplicates.maxByOrNull { item -> item.seasons.sumOf { it.episodes.size } } ?: return@mapNotNull null
            val seasons = duplicates.flatMap(SimklLibraryItem::seasons)
                .groupBy { it.number }
                .map { (number, versions) ->
                    val episodes = versions.flatMap { it.episodes }
                        .groupBy { it.number }
                        .mapNotNull { (_, candidates) ->
                            candidates.maxByOrNull { episode ->
                                (if (episode.watchedAt != null) 2 else 0) + (if (episode.title.isNotBlank()) 1 else 0)
                            }
                        }
                        .sortedBy { it.number }
                    com.cinetrack.data.remote.SimklSeason(number, episodes)
                }
                .sortedBy { it.number }
            preferred.copy(seasons = seasons)
        }

    private suspend fun resolveSimklItems(items: List<SimklLibraryItem>, type: MediaType): List<ResolvedSimklItem> {
        val resolved = mutableListOf<ResolvedSimklItem>()
        for (batch in items.chunked(6)) {
            resolved += coroutineScope {
                batch.map { item -> async {
                    resolveSimklTmdbId(item, type)?.let { ResolvedSimklItem(item, type, it) }
                } }.mapNotNull { it.await() }
            }
        }
        return resolved
    }

    private suspend fun resolveSimklTmdbId(item: SimklLibraryItem, type: MediaType): Int? {
        val media = item.show ?: item.movie ?: return null
        media.ids.tmdb?.toIntOrNull()?.takeIf { it > 0 }?.let { return it }
        if (tmdbApiKey().isBlank()) return null

        media.ids.imdb?.takeIf(String::isNotBlank)?.let { imdb ->
            val found = runCatching { services.tmdb.find(imdb, "imdb_id") }.getOrNull()
            (if (type == MediaType.TV) found?.tvResults else found?.movieResults)
                ?.firstOrNull()?.id?.let { return it }
        }
        if (type == MediaType.TV) media.ids.tvdb?.takeIf(String::isNotBlank)?.let { tvdb ->
            runCatching { services.tmdb.find(tvdb, "tvdb_id") }.getOrNull()
                ?.tvResults?.firstOrNull()?.id?.let { return it }
        }

        if (media.title.isBlank()) return null
        val expectedType = if (type == MediaType.TV) "tv" else "movie"
        val normalized = normalizeMediaTitle(media.title)
        val candidates = runCatching { services.tmdb.search(media.title).results }
            .getOrDefault(emptyList())
            .filter { it.mediaType == expectedType }
        val exact = candidates.firstOrNull { candidate ->
            val title = if (type == MediaType.TV) candidate.name else candidate.title
            val year = (if (type == MediaType.TV) candidate.firstAirDate else candidate.releaseDate)?.take(4)?.toIntOrNull()
            normalizeMediaTitle(title.orEmpty()) == normalized && (media.year == null || media.year == year)
        } ?: candidates.firstOrNull { candidate ->
            normalizeMediaTitle((if (type == MediaType.TV) candidate.name else candidate.title).orEmpty()) == normalized
        }
        return exact?.id
    }

    private fun normalizeMediaTitle(value: String): String = value.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), "")

    private fun SimklLibraryItem.toState(type: MediaType, resolvedTmdbId: Int? = null): UserMediaStateEntity? {
        val item = show ?: movie ?: return null
        val tmdbId = resolvedTmdbId ?: item.ids.tmdb?.toIntOrNull() ?: return null
        return UserMediaStateEntity(
            mediaType = type.name,
            mediaId = tmdbId,
            status = status.fromSimklStatus().name,
            watched = status == "completed",
            simklId = item.ids.simkl,
            updatedAt = listOfNotNull(lastWatchedAt, addedAt).firstNotNullOfOrNull { raw ->
                runCatching { Instant.parse(raw).toEpochMilli() }.getOrNull()
            } ?: System.currentTimeMillis(),
            dirty = false,
        )
    }

    private fun SimklLibraryItem.toMediaEntity(type: MediaType, resolvedTmdbId: Int? = null): MediaEntity? {
        val item = show ?: movie ?: return null
        val tmdbId = resolvedTmdbId ?: item.ids.tmdb?.toIntOrNull() ?: return null
        return MediaEntity(
            mediaType = type.name,
            tmdbId = tmdbId,
            title = item.title.ifBlank { "Untitled" },
            overview = "",
            posterPath = null,
            backdropPath = null,
            releaseDate = item.year?.toString(),
            score = null,
            runtimeMinutes = item.runtime,
            genres = "",
            providers = "",
            collectionId = null,
        )
    }

    private fun SimklPlaybackItem.toMediaEntity(): MediaEntity? {
        val item = show ?: movie ?: return null
        val tmdbId = item.ids.tmdb?.toIntOrNull() ?: return null
        return MediaEntity(
            mediaType = if (movie != null) MediaType.MOVIE.name else MediaType.TV.name,
            tmdbId = tmdbId,
            title = item.title.ifBlank { "Untitled" },
            overview = "",
            posterPath = null,
            backdropPath = null,
            releaseDate = item.year?.toString(),
            score = null,
            runtimeMinutes = item.runtime,
            genres = "",
            providers = "",
            collectionId = null,
        )
    }

    private fun SimklPlaybackItem.toPlaybackEntity(): PlaybackEntity? {
        val item = show ?: movie ?: return null
        val tmdbId = item.ids.tmdb?.toIntOrNull() ?: return null
        val episodeNumber = episode?.number ?: episode?.episode
        return PlaybackEntity(
            mediaType = if (movie != null) MediaType.MOVIE.name else MediaType.TV.name,
            mediaId = tmdbId,
            episodeId = id,
            progress = (progress / 100.0).toFloat().coerceIn(0f, 1f),
            positionSeconds = 0,
            durationSeconds = 0,
            updatedAt = pausedAt,
            season = episode?.season,
            episodeNumber = episodeNumber,
            episodeTitle = episode?.title,
        )
    }

    private fun EpisodeCard.toEntity() = EpisodeEntity(
        showId = showId,
        season = season,
        number = number,
        tmdbId = id.takeIf { it > 0 },
        title = title,
        overview = overview,
        airDate = airDate,
        stillPath = stillUrl?.substringAfter("/w780", stillUrl)?.substringAfter("/w1280", stillUrl),
        runtimeMinutes = runtimeMinutes,
    )

    private fun EpisodeEntity.toDomain() = EpisodeCard(
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

    private fun String.fromSimklStatus() = when (this) {
        "watching" -> LibraryStatus.WATCHING
        "hold" -> LibraryStatus.PAUSED
        "plantowatch" -> LibraryStatus.PLAN_TO_WATCH
        "completed" -> LibraryStatus.COMPLETED
        "dropped" -> LibraryStatus.DROPPED
        else -> LibraryStatus.NONE
    }

    private fun String.toSimklStatus() = when (this) {
        LibraryStatus.WATCHING.name -> "watching"
        LibraryStatus.PLAN_TO_WATCH.name -> "plantowatch"
        LibraryStatus.PAUSED.name -> "hold"
        LibraryStatus.COMPLETED.name -> "completed"
        LibraryStatus.DROPPED.name -> "dropped"
        else -> null
    }
}
