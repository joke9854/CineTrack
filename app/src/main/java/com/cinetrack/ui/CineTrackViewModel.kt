package com.cinetrack.ui

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cinetrack.data.library.LibraryRepository
import com.cinetrack.data.discovery.DiscoveryRepository
import com.cinetrack.data.media.MediaRepository
import com.cinetrack.data.people.PeopleRepository
import com.cinetrack.data.repository.CineTrackRepository
import com.cinetrack.data.repository.BoundedLruCache
import com.cinetrack.data.repository.ProgressRefreshRequest
import com.cinetrack.data.sync.SyncCoordinator
import com.cinetrack.data.sync.SyncCoordinatorOutcome
import com.cinetrack.data.watchprovider.WatchProviderRepository
import com.cinetrack.data.sync.floppy.FloppyBootstrapWorkManager
import com.cinetrack.domain.withCharacterImages
import com.cinetrack.domain.AppUiState
import com.cinetrack.domain.MainTrackerSwapUiState
import com.cinetrack.domain.EpisodeCard
import com.cinetrack.domain.LibraryStatus
import com.cinetrack.domain.MediaCard
import com.cinetrack.domain.PersonCard
import com.cinetrack.domain.PlaybackCard
import com.cinetrack.domain.RatingScore
import com.cinetrack.domain.MediaType
import com.cinetrack.domain.StreamingProvider
import com.cinetrack.domain.FloppyBootstrapStage
import com.cinetrack.domain.SyncProgress
import com.cinetrack.domain.ViewingPeopleInsights
import com.cinetrack.domain.hasExplicitReleaseTime
import com.cinetrack.domain.releaseDateTime
import com.cinetrack.domain.appendPage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.ZoneId
import java.util.concurrent.TimeUnit

class CineTrackViewModel(
    private val repository: CineTrackRepository,
    private val libraryRepository: LibraryRepository,
    private val mediaRepository: MediaRepository,
    private val discoveryRepository: DiscoveryRepository,
    private val peopleRepository: PeopleRepository,
    private val watchProviderRepository: WatchProviderRepository,
    private val syncCoordinator: SyncCoordinator,
    private val floppyBootstrapWorkManager: FloppyBootstrapWorkManager,
    private val detailEpisodeCache: com.cinetrack.ui.detail.DetailEpisodeCache,
) : ViewModel() {
    private val syncMutex = Mutex()
    private val refreshMutex = Mutex()
    private var progressRefreshJob: Job? = null
    private var progressRefreshRequested = false
    private var pendingProgressRefresh = ProgressRefreshRequest()
    private var pendingProgressRepublish = false
    private var startupStateBuilding = true
    private val foregroundObserver = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_RESUME && !startupStateBuilding && !syncMutex.isLocked) {
            viewModelScope.launch {
                performTrackingSync(force = false)
                // A skipped sync (not due, or no provider) must still re-select
                // Progress: episodes may have been released in the background.
                scheduleProgressCacheRefresh(ProgressRefreshRequest(), republish = true)
            }
        }
    }
    private val pendingEpisodeEdits = PendingEpisodeEdits()
    private val _state = MutableStateFlow(
        AppUiState(loading = true, simklConnected = repository.simklConnectedNow()),
    )
    val state: StateFlow<AppUiState> = _state.asStateFlow()
    private val _syncProgress = MutableStateFlow(_state.value.sync)
    val syncProgress: StateFlow<SyncProgress> = _syncProgress.asStateFlow()
    val syncRunning: StateFlow<Boolean> = syncProgress
        .map { progress: SyncProgress -> progress.running }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    /** WorkManager-owned progress is kept outside Room-derived AppUiState so
     * delivery invalidations cannot erase an active Floppy bootstrap. */
    private val _floppyBootstrapProgress = MutableStateFlow<com.cinetrack.domain.FloppyBootstrapProgress?>(null)
    val floppyBootstrapProgress: StateFlow<com.cinetrack.domain.FloppyBootstrapProgress?> = _floppyBootstrapProgress.asStateFlow()
    private val _discoverBrowse = MutableStateFlow<Map<String, com.cinetrack.domain.DiscoverBrowseState>>(emptyMap())
    val discoverBrowse = _discoverBrowse.asStateFlow()
    private var browseGeneration = 0

    fun loadDiscoverMore(railId: String) {
        val key = com.cinetrack.domain.discoverBrowseKey(railId, _state.value)
        val existing = _discoverBrowse.value[key] ?: com.cinetrack.domain.DiscoverBrowseState(items = _state.value.rails[railId].orEmpty())
        if (existing.loading || !existing.hasMore) return
        val generation = browseGeneration
        fun publish(value: com.cinetrack.domain.DiscoverBrowseState) {
            if (generation != browseGeneration) return
            // Retain navigation state, with a small bound across countries/languages.
            val latest = _discoverBrowse.value[key]?.items.orEmpty().associateBy(MediaCard::stableKey)
            val reconciled = value.copy(items = value.items.map { item ->
                latest[item.stableKey]?.let { item.copy(status = it.status, watched = it.watched) } ?: item
            })
            _discoverBrowse.value = (_discoverBrowse.value - key + (key to reconciled)).entries.toList().takeLast(8).associate { it.toPair() }
        }
        publish(existing.copy(loading = true, failed = false))
        viewModelScope.launch {
            var current = existing.copy(loading = true, failed = false)
            try {
                kotlinx.coroutines.withTimeout(30_000) {
                    var scanned = 0
                    val targetCount = existing.items.size + 20
                    // Skip cached duplicates and sparse filtered pages, but bound each user action.
                    while (current.hasMore && current.items.size < targetCount && scanned < 6) {
                        val page = withContext(Dispatchers.IO) { discoveryRepository.page(railId, current.nextPage) }
                        current = current.appendPage(page, railId == com.cinetrack.domain.RailIds.UPCOMING)
                        publish(current)
                        scanned++
                    }
                }
            } catch (timeout: kotlinx.coroutines.TimeoutCancellationException) {
                current = current.copy(failed = true)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                current = current.copy(failed = true)
            } finally {
                publish(current.copy(loading = false))
            }
        }
    }

    /** Backup creation or restore in progress, shown as a pill over the app; null when idle. */

    init {
        viewModelScope.launch {
            combine(repository.preferences.simklConnected, repository.preferences.simklReconnectReason, ::Pair)
                .distinctUntilChanged()
                .collect { (connected, reconnect) -> _state.update { it.copy(simklConnected = connected, simklReconnectReason = reconnect) } }
        }
        viewModelScope.launch {
            repository.preferences.simklFirstSyncMode
                .map { it == com.cinetrack.data.sync.simkl.SimklFirstSyncMode.ASK }
                .distinctUntilChanged()
                .collect { pending -> _state.update { it.copy(simklFirstSyncPending = pending) } }
        }
        viewModelScope.launch {
            // SettingsViewModel writes these; reminders and the backup still read them here.
            repository.preferences.notificationSettings.collect { settings ->
                _state.update {
                    it.copy(
                        notificationEpisodes = settings.episodes,
                        notificationMovies = settings.movies,
                        notificationSync = settings.sync,
                        quietHoursEnabled = settings.quietHoursEnabled,
                        quietHoursStart = settings.quietHoursStart,
                        quietHoursEnd = settings.quietHoursEnd,
                    )
                }
            }
        }
        viewModelScope.launch {
            // ContentSettingsViewModel writes these; detail pages, Discover and progress read them here.
            repository.preferences.contentSettings.collect { settings ->
                _state.update {
                    it.copy(
                        metadataLanguage = settings.metadataLanguage,
                        metadataRegion = settings.metadataRegion,
                        metadataTimezone = settings.metadataTimezone,
                        airDateSource = settings.airDateSource,
                        hideAdultTitles = settings.hideAdultTitles,
                        ratingSources = settings.ratingSources,
                        contentRegions = settings.contentRegions,
                        providerRegion = settings.providerRegion,
                        preferredProviders = settings.preferredProviders,
                        visibleProviderTypes = settings.visibleProviderTypes,
                    )
                }
            }
        }
        viewModelScope.launch {
            // SettingsViewModel writes these on the Appearance page; the other screens read them here.
            repository.preferences.appearanceSettings.collect { settings ->
                _state.update {
                    it.copy(
                        uiAccent = settings.uiAccent,
                        posterAccent = settings.posterAccent,
                        discoverLayout = settings.discoverLayout,
                        heroLayout = settings.heroLayout,
                        posterFormat = settings.posterFormat,
                        posterSize = settings.posterSize,
                        cardDensity = settings.cardDensity,
                    )
                }
            }
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(foregroundObserver)
        viewModelScope.launch {
            _state.map { it.floppyConnectionId }.distinctUntilChanged().collectLatest { connectionId ->
                floppyBootstrapWorkManager.progressFor(connectionId).collectLatest { progress ->
                    _floppyBootstrapProgress.value = progress
                    _state.update { current ->
                        val ui = when (progress?.stage) {
                            FloppyBootstrapStage.COMPLETE -> com.cinetrack.domain.FloppyUiState.READY
                            FloppyBootstrapStage.NEEDS_ATTENTION -> com.cinetrack.domain.FloppyUiState.NEEDS_ATTENTION
                            FloppyBootstrapStage.PREPARING,
                            FloppyBootstrapStage.BUILDING_PLAN,
                            FloppyBootstrapStage.MATERIALIZING_QUEUE,
                            FloppyBootstrapStage.QUEUED,
                            FloppyBootstrapStage.CHECKING_REMOTE_STATE,
                            FloppyBootstrapStage.WAITING_FOR_SERVER,
                            FloppyBootstrapStage.SYNCING,
                            FloppyBootstrapStage.VERIFYING,
                            FloppyBootstrapStage.STALLED,
                            -> if (current.floppyConnected) com.cinetrack.domain.FloppyUiState.SETTING_UP else current.floppyUiState
                            null -> current.floppyUiState
                        }
                        current.copy(floppyBootstrapProgress = progress, floppyUiState = ui)
                    }
                }
            }
        }
        viewModelScope.launch {
            state
                .map { uiState: AppUiState -> uiState.error }
                .filterNotNull()
                .distinctUntilChanged()
                .collect { message: String ->
                    val line = "${java.time.Instant.now()}  $message"
                    withContext(Dispatchers.IO) { repository.preferences.appendErrorLog(line) }
                }
        }
        viewModelScope.launch {
            // Room is the source of truth for imports performed by either this
            // ViewModel or WorkManager. Refresh the active pages whenever an
            // import transaction commits instead of waiting for process restart.
            libraryRepository.observeLocalChanges().collectLatest {
                // Room can emit several invalidations for one synchronization.
                // Wait for the burst to settle and never publish a raw snapshot
                // while the coordinated sync is still rebuilding derived rows.
                delay(280)
                if (syncMutex.isLocked || refreshMutex.isLocked || repository.progressCacheRefreshing || startupStateBuilding) return@collectLatest
                val current = _state.value
                val cached = readCachedState()
                val latestSync = if (_syncProgress.value.running) _syncProgress.value else cached.sync
                _syncProgress.value = latestSync
                _state.value = cached.copy(
                    refreshing = current.refreshing,
                    error = current.error,
                    people = current.people,
                    sync = latestSync,
                )
            }
        }
        viewModelScope.launch {
            // Room and DataStore are the source of truth at launch. Publish them
            // before any network work so process recreation never looks like a
            // disconnected, empty account while enrichment is running.
            withContext(Dispatchers.IO) { repository.awaitStartup() }
            val cached = readCachedState()
            _syncProgress.value = cached.sync
            _state.value = cached
            // WorkManager may have emitted before Room/DataStore startup
            // finished. Re-read the bootstrap job after the cached projection
            // is published so process recreation cannot lose visible progress.
            val restoredBootstrap = withContext(Dispatchers.IO) { floppyBootstrapWorkManager.progressFor(cached.floppyConnectionId).first() }
            _state.update { current ->
                val bootstrapMatches = restoredBootstrap?.providerInstanceId.isNullOrBlank() ||
                    current.floppyConnectionId.isNullOrBlank() ||
                    restoredBootstrap?.providerInstanceId == current.floppyConnectionId
                current.copy(
                    floppyBootstrapProgress = if (bootstrapMatches) restoredBootstrap ?: current.floppyBootstrapProgress else current.floppyBootstrapProgress,
                )
            }
            _floppyBootstrapProgress.value = restoredBootstrap
            val mainProviderConnected = withContext(Dispatchers.IO) { syncCoordinator.isMainProviderConnected() }
            val coldSync = if (mainProviderConnected) {
                // Keep the cached UI stable while a cold-start delta check runs.
                // Publishing its intermediate snapshots caused the visible reorder
                // and scroll stalls captured in the supplied recording.
                performTrackingSync(force = true, publishResult = false, exposeProgress = false)
            } else Result.success(SyncCoordinatorOutcome(itemsChanged = false))
            var databaseChanged = coldSync.getOrNull()?.itemsChanged == true
            var discoverError: Throwable? = null
            val discoverRails = listOf(
                com.cinetrack.domain.RailIds.UPCOMING,
                com.cinetrack.domain.RailIds.POPULAR_TV,
                com.cinetrack.domain.RailIds.POPULAR_MOVIES,
            )
            if (cached.tmdbApiConfigured && discoverRails.any { cached.rails[it].isNullOrEmpty() }) {
                withContext(Dispatchers.IO) {
                    runCatching { discoveryRepository.refresh() }
                        .onSuccess { databaseChanged = true }
                        .onFailure { discoverError = it }
                }
            }
            val error = coldSync.exceptionOrNull()?.message ?: discoverError?.message
            if (databaseChanged) {
                _state.value = readCachedState().copy(error = error)
            } else if (error != null) {
                _state.value = _state.value.copy(error = error)
            }
            startupStateBuilding = false
            // A successful Simkl sync already rebuilt the correctness-critical
            // Progress cache and queued cosmetic enrichment. Only disconnected or
            // failed startup paths still need an independent cache refresh.
            if (!mainProviderConnected || coldSync.isFailure) scheduleProgressCacheRefresh()
        }
        viewModelScope.launch {
            val interval = TimeUnit.MINUTES.toMillis(510)
            delay(interval)
            while (isActive) {
                performTrackingSync(force = false)
                delay(interval)
            }
        }
    }

    fun refresh() {
        if (!_state.value.tmdbApiConfigured || !refreshMutex.tryLock()) return
        browseGeneration++
        _discoverBrowse.value = emptyMap()
        // Acquire before launching: repeated pulls never queue another refresh.
        _state.value = _state.value.copy(refreshing = true, error = null)
        viewModelScope.launch {
            try {
                val refreshed = withContext(Dispatchers.IO) {
                    runDiscoverRefresh { discoveryRepository.refresh() }
                    readCachedState()
                }
                _state.value = refreshed.copy(refreshing = false, sync = _syncProgress.value)
            } catch (timeout: kotlinx.coroutines.TimeoutCancellationException) {
                _state.value = _state.value.copy(error = repository.preferences.discoverTimeoutMessage())
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.value = _state.value.copy(error = error.message)
            } finally {
                _state.value = _state.value.copy(refreshing = false)
                refreshMutex.unlock()
            }
        }
    }

    fun setSeasonPicker(value: String) {
        val normalized = if (value == "posters") "posters" else "text"
        _state.value = _state.value.copy(seasonPicker = normalized)
        viewModelScope.launch { repository.preferences.setSeasonPicker(normalized) }
    }

    fun hideUpcomingEpisode(episode: EpisodeCard) {
        val hidden = _state.value.hiddenUpcoming + episode.scheduleKey
        _state.value = _state.value.copy(
            hiddenUpcoming = hidden,
            calendar = _state.value.calendar.filterNot {
                it.media.id == episode.showId && it.season == episode.season && it.episodeNumber == episode.number
            },
        )
        viewModelScope.launch { repository.preferences.setHiddenUpcoming(hidden) }
    }

    fun hideDiscoveryItem(media: MediaCard) {
        val hidden = _state.value.hiddenDiscovery + media.stableKey
        _state.value = _state.value.copy(
            hiddenDiscovery = hidden,
            rails = _state.value.rails.mapValues { (_, items) -> items.filterNot { it.stableKey == media.stableKey } },
        )
        viewModelScope.launch { repository.preferences.setHiddenDiscovery(hidden) }
    }

    fun restoreHiddenDiscovery() {
        _state.value = _state.value.copy(hiddenDiscovery = emptySet())
        viewModelScope.launch {
            repository.preferences.setHiddenDiscovery(emptySet())
            val error = withContext(Dispatchers.IO) { runCatching { discoveryRepository.refresh() }.exceptionOrNull() }
            if (error == null) {
                val cached = readCachedState()
                _state.value = cached.copy(sync = _syncProgress.value)
            } else _state.value = _state.value.copy(error = error.message)
        }
    }

    fun restoreHiddenUpcoming() {
        _state.value = _state.value.copy(hiddenUpcoming = emptySet())
        viewModelScope.launch {
            repository.preferences.setHiddenUpcoming(emptySet())
            val cached = readCachedState()
            _state.value = cached.copy(sync = _syncProgress.value)
        }
    }

    fun restoreHiddenUpcomingEpisode(episode: EpisodeCard) {
        val hidden = _state.value.hiddenUpcoming - episode.scheduleKey
        _state.value = _state.value.copy(hiddenUpcoming = hidden)
        viewModelScope.launch {
            repository.preferences.setHiddenUpcoming(hidden)
            val cached = readCachedState()
            _state.value = cached.copy(sync = _syncProgress.value)
        }
    }

    fun setStatus(media: MediaCard, status: LibraryStatus) {
        viewModelScope.launch {
            val syncState = _syncProgress.value
            withContext(Dispatchers.IO) { libraryRepository.setLibraryStatus(media, status) }
            val refreshed = readCachedState().copy(sync = syncState)
            _state.value = refreshed
            _discoverBrowse.value = _discoverBrowse.value.mapValues { (_, browse) ->
                browse.copy(items = browse.items.map { if (it.stableKey == media.stableKey) it.copy(status = status, watched = status == LibraryStatus.COMPLETED) else it })
            }
            // The Simkl request starts immediately after the local commit. TMDB
            // metadata enrichment is independent and must never delay that push.
            scheduleProgressCacheRefresh(
                ProgressRefreshRequest(tvLibraryChanged = media.type == MediaType.TV),
            )
        }
    }

    fun markWatched(media: MediaCard) {
        viewModelScope.launch {
            libraryRepository.markWatched(media)
            val current = _state.value
            fun update(item: MediaCard) = if (item.stableKey == media.stableKey) item.copy(watched = true, status = LibraryStatus.COMPLETED) else item
            _state.value = current.copy(
                rails = current.rails.mapValues { (_, items) -> items.map(::update) },
                playbackTv = current.playbackTv.filterNot { it.media.stableKey == media.stableKey },
                playbackMovies = current.playbackMovies.filterNot { it.media.stableKey == media.stableKey },
            )
            scheduleProgressCacheRefresh(
                ProgressRefreshRequest(tvLibraryChanged = media.type == MediaType.TV),
            )
        }
    }

    fun markPlaybackWatched(playback: PlaybackCard) {
        val parsed = Regex("S\\s*(\\d+)\\D+?(\\d+)", RegexOption.IGNORE_CASE)
            .find(playback.episodeLabel.orEmpty())
        val season = playback.season ?: parsed?.groupValues?.getOrNull(1)?.toIntOrNull()
        val episodeNumber = playback.episodeNumber ?: parsed?.groupValues?.getOrNull(2)?.toIntOrNull()
        val episode = if (playback.media.type == MediaType.TV && season != null && episodeNumber != null) {
            EpisodeCard(
                id = playback.episodeId ?: 0,
                showId = playback.media.id,
                season = season,
                number = episodeNumber,
                title = playback.episodeTitle.orEmpty(),
                overview = "",
                airDate = playback.episodeAirDate,
            )
        } else null
        if (episode != null) {
            advancePlaybackImmediately(playback, episode)
        }
        viewModelScope.launch {
            if (episode != null) {
                withContext(Dispatchers.IO) { libraryRepository.markEpisodeWatched(episode) }
                scheduleProgressCacheRefresh(
                    ProgressRefreshRequest(episodeHistoryChanged = true),
                )
            } else if (playback.media.type == MediaType.MOVIE) {
                libraryRepository.markWatched(playback.media)
                _state.value = _state.value.copy(
                    playbackMovies = _state.value.playbackMovies.filterNot { it.media.stableKey == playback.media.stableKey },
                )
            }
        }
    }

    /**
     * The next cached episode is known before the Simkl write starts. Publish it
     * synchronously so the checked card changes in the same frame as the tap.
     */
    private fun advancePlaybackImmediately(playback: PlaybackCard, watchedEpisode: EpisodeCard) {
        updateCachedEpisode(watchedEpisode, watched = true)
        val current = _state.value
        val zone = if (current.metadataTimezone == "system") ZoneId.systemDefault()
        else runCatching { ZoneId.of(current.metadataTimezone) }.getOrDefault(ZoneId.systemDefault())
        val releaseNow = java.time.Instant.now()
        val updatedEpisodes = current.episodes.map { episode ->
            if (
                episode.showId == watchedEpisode.showId &&
                episode.season == watchedEpisode.season &&
                episode.number == watchedEpisode.number
            ) episode.copy(watched = true) else episode
        }
        val next = updatedEpisodes.asSequence()
            .filter { it.showId == watchedEpisode.showId }
            .filter { !current.excludeSpecials || it.season > 0 }
            .filterNot(EpisodeCard::watched)
            .filter {
                it.season > watchedEpisode.season ||
                    (it.season == watchedEpisode.season && it.number > watchedEpisode.number)
            }
            .filter { candidate ->
                releaseDateTime(candidate.airDate, zone)?.toInstant()?.let { !it.isAfter(releaseNow) } == true
            }
            .minWithOrNull(compareBy(EpisodeCard::season, EpisodeCard::number))
        val advancedCard = next?.let { candidate ->
            PlaybackCard(
                media = playback.media,
                episodeId = candidate.id.takeIf { it > 0 },
                episodeLabel = "S${candidate.season.toString().padStart(2, '0')} E${candidate.number.toString().padStart(2, '0')}",
                episodeTitle = candidate.title,
                season = candidate.season,
                episodeNumber = candidate.number,
                progress = 0f,
                remainingMinutes = candidate.runtimeMinutes ?: playback.durationMinutes ?: playback.media.runtimeMinutes,
                durationMinutes = candidate.runtimeMinutes ?: playback.durationMinutes ?: playback.media.runtimeMinutes,
                episodeAirDate = candidate.airDate,
            )
        }
        _state.value = current.copy(
            episodes = updatedEpisodes,
            playbackTv = listOfNotNull(advancedCard) + current.playbackTv.filterNot {
                it.media.id == playback.media.id
            },
        )
    }

    fun markEpisodeWatched(episode: EpisodeCard) {
        updateCachedEpisode(episode, watched = true)
        viewModelScope.launch {
            libraryRepository.markEpisodeWatched(episode)
            refreshCachedState(refreshProgress = true, promoteShowId = episode.showId)
        }
    }

    fun setEpisodeWatched(episode: EpisodeCard, watched: Boolean) {
        updateCachedEpisode(episode, watched)
        viewModelScope.launch {
            libraryRepository.setEpisodeWatched(episode, watched)
            refreshCachedState(refreshProgress = true, promoteShowId = episode.showId.takeIf { watched })
        }
    }

    fun setSeasonWatched(episodes: List<EpisodeCard>, watched: Boolean) {
        val numbers = episodes.map { it.season to it.number }.toSet()
        episodes.firstOrNull()?.showId?.let { showId -> detailEpisodeCache.markWatched(showId, numbers, watched) }
        viewModelScope.launch {
            libraryRepository.setEpisodesWatched(episodes, watched)
            refreshCachedState(refreshProgress = true, promoteShowId = episodes.firstOrNull()?.showId.takeIf { watched })
        }
    }

    fun setEpisodesWatched(episodes: List<EpisodeCard>, watched: Boolean) {
        val changed = episodes.distinctBy { Triple(it.showId, it.season, it.number) }
        changed.groupBy(EpisodeCard::showId).forEach { (showId, showEpisodes) ->
            detailEpisodeCache.markWatched(showId, showEpisodes.map { it.season to it.number }.toSet(), watched)
        }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                libraryRepository.setEpisodesWatched(changed, watched)
            }
            refreshCachedState(refreshProgress = true, promoteShowId = changed.firstOrNull()?.showId.takeIf { watched })
        }
    }

    /** Read on IO, but reconcile optimistic edits on the main thread. A read
     * started before a tap is retried, and uncommitted shows keep their UI state. */
    private suspend fun readCachedState(): AppUiState = withContext(Dispatchers.Main.immediate) {
        var version: Long
        var cached: AppUiState
        do {
            version = pendingEpisodeEdits.version
            cached = withContext(Dispatchers.IO) {
                // Simkl's connect flags live outside loadCachedState, which is at the JVM method-size limit.
                repository.loadCachedState().copy(
                    simklReconnectReason = repository.preferences.simklReconnectReason.first(),
                    simklFirstSyncPending = repository.preferences.simklFirstSyncModeNow() == com.cinetrack.data.sync.simkl.SimklFirstSyncMode.ASK,
                    floppyUsername = repository.preferences.floppyUsername.first(),
                    hideAdultTitles = repository.preferences.hideAdultTitles.first(),
                )
            }
        } while (version != pendingEpisodeEdits.version)
        val watched = cached.history.mapNotNull { event ->
            val season = event.season ?: return@mapNotNull null
            val number = event.episodeNumber ?: return@mapNotNull null
            if (event.media.type == MediaType.TV) Triple(event.media.id, season, number) else null
        }.toSet()
        val pendingShows = pendingEpisodeEdits.unconfirmedShows(watched)
        if (pendingShows.isEmpty()) cached else cached.copy(
            playbackTv = _state.value.playbackTv.filter { it.media.id in pendingShows } +
                cached.playbackTv.filterNot { it.media.id in pendingShows },
            episodes = cached.episodes.map { episode ->
                pendingEpisodeEdits[Triple(episode.showId, episode.season, episode.number)]?.let {
                    episode.copy(watched = it)
                } ?: episode
            },
        )
    }

    private fun updateCachedEpisode(episode: EpisodeCard, watched: Boolean) {
        pendingEpisodeEdits.record(Triple(episode.showId, episode.season, episode.number), watched)
        detailEpisodeCache.markWatched(episode.showId, setOf(episode.season to episode.number), watched)
    }

    private suspend fun refreshCachedState(refreshProgress: Boolean = false, promoteShowId: Int? = null) {
        if (refreshProgress) {
            // The local transaction already advanced the show's next episode;
            // show that at once instead of the watched one until the network
            // refresh (which may fetch an uncached next season) completes.
            publishCachedState(promoteShowId)
            withContext(Dispatchers.IO) {
                repository.refreshProgressCache(ProgressRefreshRequest(episodeHistoryChanged = true))
            }
        }
        publishCachedState(promoteShowId)
    }

    private suspend fun publishCachedState(promoteShowId: Int?) {
        val current = _state.value
        val cached = readCachedState()
        val watchedNumbers = cached.history.mapNotNull { event ->
            if (event.media.type == MediaType.TV && event.season != null && event.episodeNumber != null) {
                Triple(event.media.id, event.season, event.episodeNumber)
            } else null
        }.toSet()
        _state.value = cached.copy(
            floppyBootstrapProgress = current.floppyBootstrapProgress,
            sync = _syncProgress.value,
            people = current.people,
            playbackTv = cached.playbackTv.let { items ->
                val promoted = promoteShowId?.let { id -> items.firstOrNull { it.media.id == id } }
                if (promoted == null) items else listOf(promoted) + items.filterNot { it.media.id == promoteShowId }
            },
            episodes = current.episodes.map { episode ->
                episode.copy(watched = pendingEpisodeEdits[Triple(episode.showId, episode.season, episode.number)]
                    ?: (Triple(episode.showId, episode.season, episode.number) in watchedNumbers))
            },
        )
    }

    fun sync() {
        if (_syncProgress.value.running) return
        val queued = syncMutex.isLocked
        val starting = _syncProgress.value.copy(
            running = true,
            stage = com.cinetrack.domain.SyncStage.PROCESSING,
            message = if (queued) "Waiting for current Simkl operation…" else "Starting synchronization…",
        )
        _syncProgress.value = starting
        _state.update { it.copy(sync = starting) }
        viewModelScope.launch { performTrackingSync(force = true) }
    }

    private suspend fun performTrackingSync(
        force: Boolean,
        publishResult: Boolean = true,
        exposeProgress: Boolean = true,
    ): Result<SyncCoordinatorOutcome> = syncMutex.withLock {
        if (exposeProgress) {
            val started = _syncProgress.value.copy(
                running = true,
                stage = com.cinetrack.domain.SyncStage.AUTH,
                message = null,
            )
            _syncProgress.value = started
            _state.update { it.copy(sync = started) }
        }
        if (!syncCoordinator.isMainProviderConnected()) {
            if (exposeProgress) {
                val idle = SyncProgress(running = false, stage = com.cinetrack.domain.SyncStage.IDLE)
                _syncProgress.value = idle
                _state.update { it.copy(sync = idle) }
            }
            return@withLock Result.success(SyncCoordinatorOutcome(itemsChanged = false))
        }
        if (!force && !repository.isMainTrackingSyncDue(TimeUnit.HOURS.toMillis(8))) {
            if (exposeProgress) {
                val idle = _syncProgress.value.copy(running = false, stage = com.cinetrack.domain.SyncStage.IDLE, message = null)
                _syncProgress.value = idle
                _state.update { it.copy(sync = idle) }
            }
            return@withLock Result.success(SyncCoordinatorOutcome(itemsChanged = false))
        }
        var completedSync = _syncProgress.value
        val result = withContext(Dispatchers.IO) {
            syncCoordinator.sync { progress ->
                completedSync = progress
                if (exposeProgress) {
                    _syncProgress.value = progress
                }
            }
        }
        completedSync = when {
            result.isFailure -> completedSync.copy(
                running = false,
                stage = com.cinetrack.domain.SyncStage.ERROR,
                message = result.exceptionOrNull()?.message ?: completedSync.message,
            )
            completedSync.running -> completedSync.copy(
                running = false,
                stage = com.cinetrack.domain.SyncStage.COMPLETE,
                message = null,
            )
            else -> completedSync
        }
        _syncProgress.value = completedSync
        val outcome = result.getOrNull()
        if (outcome?.itemsChanged == true && publishResult) {
            // The repository does not finish the visible sync until both the
            // remote transaction and correctness-critical Progress data are complete.
            _state.value = readCachedState()
                .copy(sync = completedSync)
            viewModelScope.launch(Dispatchers.IO) { repository.createAutomaticBackup() }
        } else if (outcome != null && exposeProgress) {
            // Complete the synchronization indicator without replacing any page
            // collections when Simkl reported an unchanged activity generation.
            _state.value = _state.value.copy(sync = completedSync)
        } else if (result.isFailure) {
            val cached = readCachedState()
            _state.value = cached.copy(
                sync = completedSync,
                error = result.exceptionOrNull()?.message ?: completedSync.message,
            )
        }
        result
    }


    /**
     * Refreshes derived Progress metadata without extending the visible Simkl
     * synchronization or queueing duplicate refreshes after rapid status taps.
     */
    /** [republish] re-reads Progress even when the cache needed no refresh,
     * because selection depends on the current time and timezone. */
    private fun scheduleProgressCacheRefresh(
        request: ProgressRefreshRequest = ProgressRefreshRequest(force = true),
        republish: Boolean = false,
    ) {
        progressRefreshRequested = true
        pendingProgressRefresh = pendingProgressRefresh.mergedWith(request)
        pendingProgressRepublish = pendingProgressRepublish || republish
        if (progressRefreshJob?.isActive == true) return
        progressRefreshJob = viewModelScope.launch {
            do {
                progressRefreshRequested = false
                val refreshRequest = pendingProgressRefresh
                pendingProgressRefresh = ProgressRefreshRequest()
                val republish = pendingProgressRepublish
                pendingProgressRepublish = false
                val refreshResult = withContext(Dispatchers.IO) {
                    runCatching { repository.refreshProgressCache(refreshRequest) }
                }
                if (refreshResult.getOrDefault(false) || republish) {
                    val cached = readCachedState()
                    val current = _state.value
                    _state.value = cached.copy(
                        refreshing = current.refreshing,
                        people = current.people,
                        sync = _syncProgress.value,
                        error = current.error,
                    )
                }
            } while (progressRefreshRequested)
        }
    }

    fun completeSimklLogin(code: String?, state: String?, callbackError: String?, issuer: String?) {
        viewModelScope.launch {
            if (!callbackError.isNullOrBlank()) {
                _state.value = _state.value.copy(error = com.cinetrack.domain.SimklAuthRules.callbackErrorMessage(callbackError))
                return@launch
            }
            if (code.isNullOrBlank()) {
                _state.value = _state.value.copy(error = "Simkl did not return an authorization code. Please connect again")
                return@launch
            }
            repository.completeLogin(code, state, issuer).onSuccess {
                // The first sync waits for chooseSimklFirstSync.
                _state.value = _state.value.copy(simklConnected = true, simklFirstSyncPending = true, error = null)
                loadSimklUsername()
            }.onFailure { _state.value = _state.value.copy(error = it.message) }
        }
    }

    /** Merge keeps everything on this device and uploads what Simkl lacks; otherwise the account replaces it. */
    fun chooseSimklFirstSync(merge: Boolean) {
        viewModelScope.launch {
            repository.preferences.setSimklFirstSyncMode(
                if (merge) com.cinetrack.data.sync.simkl.SimklFirstSyncMode.MERGE else com.cinetrack.data.sync.simkl.SimklFirstSyncMode.REPLACE,
            )
            _state.update { it.copy(simklFirstSyncPending = false) }
            sync()
        }
    }

    /** Fills in the Simkl account name right after sign-in; Settings uses SyncSettingsViewModel. */
    private fun loadSimklUsername() {
        if (!_state.value.simklConnected) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.refreshSimklUsername() }?.let { name ->
                _state.value = _state.value.copy(simklUsername = name)
            }
        }
    }

    fun setFloppyAllowInsecureLocalHttp(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.preferences.setFloppyAllowInsecureLocalHttp(enabled)
            withContext(Dispatchers.Main) {
                _state.value = _state.value.copy(floppyAllowInsecureLocalHttp = enabled)
            }
        }
    }

    private val _mainTrackerSwap = MutableStateFlow(MainTrackerSwapUiState())
    val mainTrackerSwap: StateFlow<MainTrackerSwapUiState> = _mainTrackerSwap.asStateFlow()

    /**
     * Swaps MAIN and SECONDARY: one last full sync with the current MAIN, the
     * role change (refused unless the new MAIN finished its initial sync),
     * then the new MAIN's first two-way pass.
     */
    fun swapMainTracker() {
        if (_mainTrackerSwap.value.running) return
        _mainTrackerSwap.value = MainTrackerSwapUiState(running = true)
        viewModelScope.launch {
            val error = try {
                performTrackingSync(force = true).getOrThrow()
                withContext(Dispatchers.IO) { repository.swapMainTracker() }
                null
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                failure
            }
            if (error == null) performTrackingSync(force = true)
            _state.value = readCachedState().copy(sync = _syncProgress.value, error = null)
            _mainTrackerSwap.value = MainTrackerSwapUiState(running = false, error = error?.message)
        }
    }

    /** A show's metadata was fetched again on its Detail page (DetailViewModel.refreshTitle): rebuild Progress. */
    suspend fun onTitleRefreshed() = refreshCachedState(refreshProgress = true)

    fun setExcludeSpecials(enabled: Boolean) {
        _state.value = _state.value.copy(excludeSpecials = enabled)
        viewModelScope.launch {
            repository.preferences.setExcludeSpecials(enabled)
            scheduleProgressCacheRefresh()
        }
    }
    fun completeIntroduction() {
        _state.value = _state.value.copy(introductionCompleted = true)
        viewModelScope.launch { repository.preferences.setIntroductionCompleted(true) }
    }

    fun setTmdbApiKey(value: String?) {
        viewModelScope.launch {
            repository.setTmdbApiKey(value)
            _state.value = _state.value.copy(tmdbApiConfigured = repository.preferences.tmdbApiKeyNow().isNotBlank())
        }
    }

    /** Shows a failure from another screen's ViewModel in the app's error banner. */
    /** A backup restore finished: the library, history and settings all come from the database again. */
    fun onBackupRestored() {
        viewModelScope.launch {
            val cached = readCachedState()
            _state.value = cached.copy(sync = _syncProgress.value, error = null)
        }
    }

    /** Called once a write on the Sync, Simkl or Floppy pages has finished; see SyncSettingsViewModel.changes. */
    fun onSyncSettingsChanged(change: com.cinetrack.ui.settings.SyncSettingsChange) {
        when (change) {
            com.cinetrack.ui.settings.SyncSettingsChange.ProviderChanged -> viewModelScope.launch {
                _state.value = readCachedState().copy(error = null)
            }
            com.cinetrack.ui.settings.SyncSettingsChange.ConflictResolved -> viewModelScope.launch {
                _state.value = readCachedState().copy(sync = _syncProgress.value)
            }
            is com.cinetrack.ui.settings.SyncSettingsChange.Error -> _state.update { it.copy(error = change.message) }
            com.cinetrack.ui.settings.SyncSettingsChange.SimklDisconnected ->
                _state.update { it.copy(simklConnected = false, simklUsername = null) }
            is com.cinetrack.ui.settings.SyncSettingsChange.SimklUsername -> _state.update { it.copy(simklUsername = change.name) }
            is com.cinetrack.ui.settings.SyncSettingsChange.FloppyUsername -> _state.update { it.copy(floppyUsername = change.name) }
            is com.cinetrack.ui.settings.SyncSettingsChange.BackgroundSync -> _state.update { it.copy(backgroundSync = change.enabled) }
            is com.cinetrack.ui.settings.SyncSettingsChange.WifiOnly -> _state.update { it.copy(wifiOnly = change.enabled) }
        }
    }

    /** Called once a content-setting write has finished; see ContentSettingsViewModel.changes. */
    fun onContentChanged(change: com.cinetrack.ui.settings.ContentChange) {
        when (change) {
            // DetailViewModel.onContentChanged drops its own caches.
            com.cinetrack.ui.settings.ContentChange.Providers,
            com.cinetrack.ui.settings.ContentChange.Ratings,
            com.cinetrack.ui.settings.ContentChange.Region -> Unit
            // Cached episode titles are in the previous language.
            com.cinetrack.ui.settings.ContentChange.Language -> scheduleProgressCacheRefresh()
            // Release days and what counts as out follow the new dates and timezone.
            com.cinetrack.ui.settings.ContentChange.AirDates,
            com.cinetrack.ui.settings.ContentChange.Timezone -> scheduleProgressCacheRefresh(ProgressRefreshRequest(), republish = true)
            com.cinetrack.ui.settings.ContentChange.MdbListKey -> {
                _state.value = _state.value.copy(mdbListApiConfigured = true, error = null)
            }
            com.cinetrack.ui.settings.ContentChange.TraktClientId -> {
                _state.value = _state.value.copy(traktConfigured = true, error = null)
                // Pull exact air times now rather than at the next schedule refresh.
                scheduleProgressCacheRefresh(ProgressRefreshRequest(force = true))
            }
            com.cinetrack.ui.settings.ContentChange.TmdbKey -> viewModelScope.launch {
                _state.value = _state.value.copy(tmdbApiConfigured = true, error = null)
                val refreshError = withContext(Dispatchers.IO) {
                    runCatching { discoveryRepository.refresh() }.exceptionOrNull()
                }
                if (refreshError == null) {
                    val current = _state.value
                    _state.value = readCachedState().copy(sync = _syncProgress.value, people = current.people)
                } else {
                    _state.value = _state.value.copy(error = refreshError.message)
                }
            }
            com.cinetrack.ui.settings.ContentChange.ContentRegions -> viewModelScope.launch {
                val refreshError = withContext(Dispatchers.IO) {
                    runCatching { discoveryRepository.refresh() }.exceptionOrNull()
                }
                if (refreshError == null) {
                    val cached = readCachedState()
                    val current = _state.value
                    _state.value = cached.copy(sync = _syncProgress.value, people = current.people, error = current.error)
                } else {
                    _state.value = _state.value.copy(error = refreshError.message)
                }
            }
        }
    }

    fun showError(message: String) {
        _state.value = _state.value.copy(error = message)
    }

    fun dismissError() {
        _state.value = _state.value.copy(error = null)
    }

    fun exportCalendar(context: Context) {
        viewModelScope.launch {
            runCatching {
                val timezone = _state.value.metadataTimezone
                val releaseZone = if (timezone == "system") ZoneId.systemDefault()
                else runCatching { ZoneId.of(timezone) }.getOrDefault(ZoneId.systemDefault())
                val file = withContext(Dispatchers.IO) {
                    val directory = File(context.cacheDir, "exports").apply { mkdirs() }
                    File(directory, "cinetrack-calendar.ics").apply {
                        writeText(com.cinetrack.domain.upcomingCalendarIcs(_state.value.calendar, releaseZone))
                    }
                }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/calendar"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }, context.getString(com.cinetrack.R.string.export_calendar)))
            }.onFailure { _state.value = _state.value.copy(error = it.message) }
        }
    }

    override fun onCleared() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(foregroundObserver)
        super.onCleared()
    }

    class Factory(
        private val repository: CineTrackRepository,
        private val libraryRepository: LibraryRepository,
        private val mediaRepository: MediaRepository,
        private val discoveryRepository: DiscoveryRepository,
        private val peopleRepository: PeopleRepository,
        private val watchProviderRepository: WatchProviderRepository,
        private val syncCoordinator: SyncCoordinator,
            private val floppyBootstrapWorkManager: FloppyBootstrapWorkManager,
        private val detailEpisodeCache: com.cinetrack.ui.detail.DetailEpisodeCache,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            CineTrackViewModel(
                repository,
                libraryRepository,
                mediaRepository,
                discoveryRepository,
                peopleRepository,
                watchProviderRepository,
                syncCoordinator,
                floppyBootstrapWorkManager,
                detailEpisodeCache,
            ) as T
    }
}

