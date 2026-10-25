package com.cinetrack.data.repository

import com.cinetrack.data.local.AppDatabase
import com.cinetrack.data.local.AppDatabaseSnapshot
import com.cinetrack.data.local.UpNextEntity
import com.cinetrack.data.local.toDomain
import com.cinetrack.data.sync.TrackingProviderId
import com.cinetrack.data.sync.TrackingProviderRegistry
import com.cinetrack.data.sync.simkl.SimklFirstSyncMode
import com.cinetrack.domain.AppUiState
import com.cinetrack.domain.EpisodeCard
import com.cinetrack.domain.FloppyUiState
import com.cinetrack.domain.LibraryStatus
import com.cinetrack.domain.MediaCard
import com.cinetrack.domain.MediaType
import com.cinetrack.domain.PlaybackCard
import com.cinetrack.domain.RailIds
import com.cinetrack.domain.SyncProgress
import com.cinetrack.domain.TimelineCard
import com.cinetrack.domain.TrackingProviderState
import com.cinetrack.domain.releaseDateTime
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Builds the AppUiState snapshot from Room and the provider states. */
class AppStateRepository(
    private val database: AppDatabase,
    private val preferences: AppPreferences,
    private val currentToken: () -> String?,
    private val trackingProviderRegistry: TrackingProviderRegistry?,
    private val tmdbApiKey: () -> String,
    private val mdbListApiKey: () -> String,
) {
    private fun scheduleTime(raw: String?): Long = raw?.takeIf(String::isNotBlank)?.let { value ->
        releaseDateTime(value, ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
    } ?: 0L

    private fun playbackTimestamp(raw: String?): Long = raw?.trim()?.takeIf(String::isNotBlank)?.let {
        runCatching { Instant.parse(it).toEpochMilli() }.getOrDefault(0L)
    } ?: 0L

    fun simklConnectedNow(): Boolean = !currentToken().isNullOrBlank()

    fun observeLocalChanges() = database.invalidationTracker.createFlow(
        "media",
        "media_rails",
        "user_media_state",
        "playback",
        "watch_history",
        "up_next",
        "sync_state",
        emitInitialState = false,
    )

    private val snapshotLock = Mutex()
    private var lastSnapshot: AppDatabaseSnapshot? = null

    /** Loads serially so a partial reload always starts from the latest snapshot. */
    private suspend fun databaseSnapshot(changedTables: Set<String>?): AppDatabaseSnapshot = snapshotLock.withLock {
        database.snapshotDao().appStateSnapshot(lastSnapshot, changedTables).also { lastSnapshot = it }
    }

    /**
     * [changedTables] are Room invalidation names seen since the last load; only those
     * tables are re-read. Null (every caller but the invalidation observer) reads all.
     */
    suspend fun loadCachedState(changedTables: Set<String>? = null): AppUiState = coroutineScope {
        // The database portion is read in one Room transaction. Preferences can
        // still be collected concurrently because they are independent DataStore
        // values and cannot produce a structurally inconsistent media snapshot.
        val snapshotDeferred = async { databaseSnapshot(changedTables) }
        val prefsDeferred = async { preferences.appStatePreferences.first() }
        val providerStatesDeferred = async { buildTrackingProviderStates() }

        val snapshot = snapshotDeferred.await()
        val states = snapshot.states.associateBy { "${it.mediaType}:${it.mediaId}" }
        val mediaByKey = snapshot.media.associateBy { "${it.mediaType}:${it.tmdbId}" }
        val railEntries = snapshot.rails.groupBy { it.railId }
        val cachedEpisodes = snapshot.episodes
        val episodeByNumber = cachedEpisodes.associateBy { Triple(it.showId, it.season, it.number) }
        val railIds = listOf(
            RailIds.TRENDING_TV,
            RailIds.TRENDING_MOVIES,
            RailIds.POPULAR_TV,
            RailIds.POPULAR_MOVIES,
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
                progressUpdatedAtMillis = playbackTimestamp(item.updatedAt),
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
                episodeTitle = item.episodeTitle?.takeIf(String::isNotBlank) ?: cachedTitle,
                episodeId = item.episodeId,
                season = item.season,
                episodeNumber = item.episodeNumber,
            )
        }
        val prefs = prefsDeferred.await()
        val configuredTimezone = prefs.content.metadataTimezone
        val releaseZone = if (configuredTimezone == "system") ZoneId.systemDefault()
        else runCatching { ZoneId.of(configuredTimezone) }.getOrDefault(ZoneId.systemDefault())
        val releaseNow = Instant.now()
        val today = LocalDate.now(releaseZone)
        // Calendar is strictly a tracked-content surface. Build it from the
        // Simkl-backed Library rail so TMDB content-region preferences can never
        // hide an imported or locally tracked release.
        val movieCalendar = activeLibrary.asSequence().filter { it.type == MediaType.MOVIE }.mapNotNull { media ->
            val date = media.releaseDate?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            val releaseDay = releaseDateTime(date, releaseZone)?.toLocalDate() ?: return@mapNotNull null
            // The Calendar is an upcoming schedule, not a release history: titles out
            // before today never reappear here. Today's stay, marked as out.
            if (releaseDay.isBefore(today)) return@mapNotNull null
            TimelineCard(media = media, label = date, timestamp = date)
        }.toList()
        val activeShowsById = activeLibrary.asSequence()
            .filter { it.type == MediaType.TV }
            .associateBy(MediaCard::id)
        // The episode table is the durable schedule snapshot. Rebuild Calendar
        // from it at process start, just as Showly reads its local episode cache,
        // instead of waiting for a new network enrichment pass.
        val excludeSpecials = prefs.excludeSpecials
        val hiddenUpcoming = prefs.hiddenUpcoming
        val episodeCalendar = cachedEpisodes.mapNotNull { episode ->
            val show = activeShowsById[episode.showId] ?: return@mapNotNull null
            if (excludeSpecials && episode.season == 0) return@mapNotNull null
            if ("${episode.showId}:${episode.season}:${episode.number}" in hiddenUpcoming) return@mapNotNull null
            val date = episode.airDate?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            // The day it airs here, not the day written in the provider's timestamp:
            // "2026-10-14T21:00-04:00" is already the 15th in Europe.
            val airDay = releaseDateTime(date, releaseZone)?.toLocalDate() ?: return@mapNotNull null
            if (airDay.isBefore(today)) return@mapNotNull null
            TimelineCard(
                media = show,
                label = date,
                timestamp = date,
                episodeLabel = "S${episode.season.toString().padStart(2, '0')} E${episode.number.toString().padStart(2, '0')} · ${episode.title}",
                episodeId = episode.tmdbId,
                season = episode.season,
                episodeNumber = episode.number,
                episodeTitle = episode.title,
            )
        }
        val calendar = (movieCalendar + episodeCalendar)
            .distinctBy { "${it.media.stableKey}:${it.season ?: -1}:${it.episodeNumber ?: -1}" }
            .sortedWith(
                compareBy<TimelineCard> {
                    releaseDateTime(it.timestamp, releaseZone)?.toInstant() ?: Instant.MAX
                }
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
        val playbackByShow = playback.filter { it.media.type == MediaType.TV }.groupBy { it.media.stableKey }
            .mapValues { (_, items) -> items.maxByOrNull(::playbackActivityRecency) }
        val durableUpNext = snapshot.upNext.associateBy(UpNextEntity::showId)
        val cachedEpisodeCards = cachedEpisodes.map { it.toDomain() }
        
        val cachedUpNext = rails[RailIds.LIBRARY].orEmpty()
            .filter { it.type == MediaType.TV && it.status in setOf(LibraryStatus.WATCHING, LibraryStatus.COMPLETED) }
            .mapNotNull { show ->
                val stored = durableUpNext[show.id]?.takeUnless {
                    Triple(show.id, it.season, it.episodeNumber) in watchedNumbers
                }?.let { row ->
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
                }
                // mapNotNull is intentional: main's Progress invariant is
                // that a tracked show without a concrete next episode is not
                // a Progress card. selectProgressCard retains beta's genuine
                // partial-playback resume behavior.
                selectProgressCard(
                    show = show,
                    episodes = cachedEpisodeCards,
                    watched = watchedNumbers,
                    playbackSession = playbackByShow[show.stableKey],
                    storedNext = stored,
                    now = releaseNow,
                    zone = releaseZone,
                    excludeSpecials = excludeSpecials,
                )
            }
            // Latest viewing first, so a show advanced locally stays on top
            // without waiting for a sync to rewrite the library rail order.
            .sortedWith { left, right ->
                compareProgressAttention(left, right, latestWatchedAtByShow, releaseNow.toEpochMilli(), releaseZone)
            }
        val moviePlayback = playback.filter { it.media.type == MediaType.MOVIE }.toMutableList()
        val movieKeys = moviePlayback.map { it.media.stableKey }.toSet()
        moviePlayback += rails[RailIds.LIBRARY].orEmpty()
            .filter { it.type == MediaType.MOVIE && it.status == LibraryStatus.WATCHING && it.stableKey !in movieKeys }
            .map { media -> PlaybackCard(media = media, progress = 0f, remainingMinutes = media.runtimeMinutes, durationMinutes = media.runtimeMinutes) }
        moviePlayback.sortWith(compareByDescending<PlaybackCard>(::playbackActivityRecency).thenBy { it.media.title.lowercase() })

        val floppyConnected = prefs.floppySettings != null && preferences.floppyApiKeyNow(prefs.floppyCredentialAlias) != null
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
                report = prefs.syncReport,
            ),
            simklConnected = simklConnectedNow(),
            floppyConnected = floppyConnected,
            floppyServerVersion = prefs.floppySettings?.serverVersion,
            floppyBaseUrl = prefs.floppySettings?.baseUrl,
            floppyConnectionId = prefs.floppySettings?.connectionId,
            floppyAllowInsecureLocalHttp = prefs.floppyAllowInsecureLocalHttp,
            floppyUiState = providerStatesDeferred.await().firstOrNull { it.providerId == TrackingProviderId.FLOPPY.name }
                .toFloppyUiState(floppyConnected),
            trackingProviders = providerStatesDeferred.await(),
            backgroundSync = prefs.backgroundSync,
            wifiOnly = prefs.wifiOnly,
            notificationEpisodes = prefs.notifications.episodes,
            notificationMovies = prefs.notifications.movies,
            notificationSync = prefs.notifications.sync,
            quietHoursEnabled = prefs.notifications.quietHoursEnabled,
            quietHoursStart = prefs.notifications.quietHoursStart,
            quietHoursEnd = prefs.notifications.quietHoursEnd,
            ratingSources = prefs.content.ratingSources,
            contentRegions = prefs.content.contentRegions,
            uiAccent = prefs.appearance.uiAccent,
            tmdbApiConfigured = tmdbApiKey().isNotBlank(),
            mdbListApiConfigured = mdbListApiKey().isNotBlank(),
            tvmazeEnabled = preferences.tvmazeEnabled.first(),
            fanartConfigured = preferences.fanartApiKeyNow().isNotBlank(),
            tvmazeKeyConfigured = preferences.tvmazeUserNow() != null,
            tvdbConfigured = preferences.tvdbApiKeyNow().isNotBlank(),
            metadataLanguage = prefs.content.metadataLanguage,
            metadataRegion = prefs.content.metadataRegion,
            providerRegion = prefs.content.providerRegion,
            metadataTimezone = configuredTimezone,
            airDateSource = prefs.content.airDateSource,
            simklUsername = prefs.simklUsername,
            excludeSpecials = excludeSpecials,
            preferredProviders = prefs.content.preferredProviders,
            visibleProviderTypes = prefs.content.visibleProviderTypes,
            heroLayout = prefs.appearance.heroLayout,
            discoverLayout = prefs.appearance.discoverLayout,
            seasonPicker = prefs.seasonPicker,
            posterAccent = prefs.appearance.posterAccent,
            posterFormat = prefs.appearance.posterFormat,
            posterSize = prefs.appearance.posterSize,
            cardDensity = prefs.appearance.cardDensity,
            hiddenUpcoming = hiddenUpcoming,
            hiddenDiscovery = prefs.hiddenDiscovery,
            introductionCompleted = prefs.introductionCompleted,
            simklReconnectReason = prefs.simklReconnectReason,
            simklFirstSyncPending = prefs.simklFirstSyncMode == SimklFirstSyncMode.ASK,
            floppyUsername = prefs.floppyUsername,
            hideAdultTitles = prefs.content.hideAdultTitles,
            loading = false,
        )
    }

    private suspend fun buildTrackingProviderStates(): List<TrackingProviderState> {
        val registry = trackingProviderRegistry ?: return emptyList()
        val configuration = registry.configuration()
        return TrackingProviderId.entries.map { id ->
            val provider = registry.getProvider(id)
            val authenticated = provider?.let { runCatching { it.isAuthenticated() }.getOrDefault(false) } == true
            val role = when (id) {
                configuration.mainProvider -> "MAIN"
                configuration.secondaryProvider -> "SECONDARY"
                else -> "NONE"
            }
            TrackingProviderState(
                providerId = id.name,
                role = role,
                configured = id == configuration.mainProvider || id == configuration.secondaryProvider,
                authenticated = authenticated,
                connectionStatus = when {
                    provider == null -> "UNAVAILABLE"
                    authenticated -> "CONNECTED"
                    else -> "DISCONNECTED"
                },
                lastSuccessfulSync = preferences.trackingLastCheckAt(id),
                twoWayCapable = provider?.let { runCatching { it.currentCapabilities().supportsTwoWaySync }.getOrDefault(false) } == true,
                bootstrapState = preferences.providerBootstrapStateNow(id).name,
            )
        }
    }

    private fun TrackingProviderState?.toFloppyUiState(connected: Boolean): FloppyUiState = when {
        !connected -> FloppyUiState.NOT_CONNECTED
        this?.role == "MAIN" -> FloppyUiState.READY
        this?.role != "SECONDARY" -> FloppyUiState.CONNECTED_INACTIVE
        this.bootstrapState == "READY" -> FloppyUiState.READY
        this.bootstrapState == "FAILED" -> FloppyUiState.NEEDS_ATTENTION
        else -> FloppyUiState.SETTING_UP
    }
}
