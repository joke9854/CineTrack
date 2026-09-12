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
import com.cinetrack.data.update.AppUpdateState
import com.cinetrack.data.update.AppChangelogState
import com.cinetrack.data.update.GitHubAppUpdater
import com.cinetrack.domain.AppUiState
import com.cinetrack.domain.DiscoverMovieFilters
import com.cinetrack.domain.EpisodeCard
import com.cinetrack.domain.LibraryStatus
import com.cinetrack.domain.MediaCard
import com.cinetrack.domain.PersonCard
import com.cinetrack.domain.PlaybackCard
import com.cinetrack.domain.RatingScore
import com.cinetrack.domain.MediaType
import com.cinetrack.domain.StreamingProvider
import com.cinetrack.domain.SyncProgress
import com.cinetrack.domain.SyncConflictChoice
import com.cinetrack.domain.SyncOperationCard
import com.cinetrack.domain.ViewingPeopleInsights
import com.cinetrack.domain.hasExplicitReleaseTime
import com.cinetrack.domain.releaseDateTime
import com.cinetrack.domain.appendPage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.util.zip.ZipInputStream
import android.net.Uri

class CineTrackViewModel(
    private val repository: CineTrackRepository,
    private val libraryRepository: LibraryRepository,
    private val mediaRepository: MediaRepository,
    private val discoveryRepository: DiscoveryRepository,
    private val peopleRepository: PeopleRepository,
    private val watchProviderRepository: WatchProviderRepository,
    private val syncCoordinator: SyncCoordinator,
) : ViewModel() {
    private val syncMutex = Mutex()
    private val refreshMutex = Mutex()
    private var progressRefreshJob: Job? = null
    private var progressRefreshRequested = false
    private var pendingProgressRefresh = ProgressRefreshRequest()
    private var startupStateBuilding = true
    private val foregroundObserver = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_RESUME && !startupStateBuilding && !syncMutex.isLocked) {
            viewModelScope.launch { performTrackingSync(force = false) }
        }
    }
    private val pendingEpisodeEdits = PendingEpisodeEdits()
    private val detailCastCache = BoundedLruCache<String, List<PersonCard>>(32)
    private val detailMediaCache = BoundedLruCache<String, MediaCard>(64)
    private val detailRatingsCache = BoundedLruCache<String, List<RatingScore>>(64)
    private val detailEpisodeCache = BoundedLruCache<Int, List<EpisodeCard>>(32)
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
    private val _errorLogs = MutableStateFlow<List<String>>(emptyList())
    val errorLogs: StateFlow<List<String>> = _errorLogs.asStateFlow()
    private val _viewingInsights = MutableStateFlow(ViewingPeopleInsights())
    val viewingInsights: StateFlow<ViewingPeopleInsights> = _viewingInsights.asStateFlow()
    private val _syncOperations = MutableStateFlow<List<SyncOperationCard>>(emptyList())
    val syncOperations: StateFlow<List<SyncOperationCard>> = _syncOperations.asStateFlow()

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

    private val _searchResults = MutableStateFlow<List<MediaCard>>(emptyList())
    private val searchQuery = MutableStateFlow("")
    val searchResults: StateFlow<List<MediaCard>> = _searchResults.asStateFlow()
    private val _searchLoading = MutableStateFlow(false)
    val searchLoading: StateFlow<Boolean> = _searchLoading.asStateFlow()
    private val _personSearchResults = MutableStateFlow<List<PersonCard>>(emptyList())
    private val personSearchQuery = MutableStateFlow("")
    val personSearchResults: StateFlow<List<PersonCard>> = _personSearchResults.asStateFlow()
    private val _personSearchLoading = MutableStateFlow(false)
    val personSearchLoading: StateFlow<Boolean> = _personSearchLoading.asStateFlow()
    val searchHistory: StateFlow<List<String>> = repository.preferences.searchHistory
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val _discoverFilterResults = MutableStateFlow<List<MediaCard>>(emptyList())
    val discoverFilterResults: StateFlow<List<MediaCard>> = _discoverFilterResults.asStateFlow()
    private val _discoverFiltersLoading = MutableStateFlow(false)
    val discoverFiltersLoading: StateFlow<Boolean> = _discoverFiltersLoading.asStateFlow()
    private val _streamingProviders = MutableStateFlow<List<StreamingProvider>>(emptyList())
    val streamingProviders: StateFlow<List<StreamingProvider>> = _streamingProviders.asStateFlow()
    private val _settingsProviders = MutableStateFlow<List<StreamingProvider>>(emptyList())
    val settingsProviders = _settingsProviders.asStateFlow()
    private val _settingsProvidersLoading = MutableStateFlow(false)
    val settingsProvidersLoading = _settingsProvidersLoading.asStateFlow()
    private val _settingsProvidersError = MutableStateFlow(false)
    val settingsProvidersError = _settingsProvidersError.asStateFlow()
    private val _appUpdateState = MutableStateFlow<AppUpdateState>(AppUpdateState.Idle)
    val appUpdateState: StateFlow<AppUpdateState> = _appUpdateState.asStateFlow()
    private val _appChangelogState = MutableStateFlow<AppChangelogState>(AppChangelogState.Idle)
    val appChangelogState: StateFlow<AppChangelogState> = _appChangelogState.asStateFlow()
    private var downloadedUpdateFile: File? = null

    @OptIn(FlowPreview::class)
    private fun observeSearch() {
        viewModelScope.launch {
            searchQuery.debounce { if (it.isBlank()) 0L else 300L }
                .distinctUntilChanged()
                .collectLatest { query ->
                    if (query.isBlank()) {
                        _searchResults.value = emptyList()
                        _searchLoading.value = false
                    } else {
                        val results = withContext(Dispatchers.IO) { discoveryRepository.search(query) }
                        // A newer keystroke may still be inside its debounce window.
                        if (searchQuery.value == query) {
                            _searchResults.value = results
                            _searchLoading.value = false
                        }
                    }
                }
        }
        viewModelScope.launch {
            personSearchQuery.debounce { if (it.isBlank()) 0L else 300L }
                .distinctUntilChanged()
                .collectLatest { query ->
                    if (query.isBlank()) {
                        _personSearchResults.value = emptyList()
                        _personSearchLoading.value = false
                    } else {
                        val results = withContext(Dispatchers.IO) { peopleRepository.search(query) }
                        if (personSearchQuery.value == query) {
                            _personSearchResults.value = results
                            _personSearchLoading.value = false
                        }
                    }
                }
        }
    }

    init {
        observeSearch()
        ProcessLifecycleOwner.get().lifecycle.addObserver(foregroundObserver)
        viewModelScope.launch {
            _errorLogs.value = withContext(Dispatchers.IO) { repository.preferences.readErrorLogs() }
            _syncOperations.value = withContext(Dispatchers.IO) { repository.loadSyncOperations() }
            state
                .map { uiState: AppUiState -> uiState.error }
                .filterNotNull()
                .distinctUntilChanged()
                .collect { message: String ->
                    val line = "${java.time.Instant.now()}  $message"
                    _errorLogs.value = (_errorLogs.value + line).takeLast(200)
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

    fun search(query: String) {
        val normalized = query.trim()
        if (searchQuery.value != normalized) {
            _searchResults.value = emptyList()
            _searchLoading.value = normalized.isNotBlank()
            searchQuery.value = normalized
        } else if (normalized.isBlank()) _searchLoading.value = false
    }

    fun searchPeople(query: String) {
        val normalized = query.trim()
        if (personSearchQuery.value != normalized) {
            _personSearchResults.value = emptyList()
            _personSearchLoading.value = normalized.isNotBlank()
            personSearchQuery.value = normalized
        } else if (normalized.isBlank()) _personSearchLoading.value = false
    }

    fun rememberSearchQuery(query: String) {
        val normalized = query.trim()
        if (normalized.isNotBlank()) viewModelScope.launch { repository.preferences.addSearchHistory(normalized) }
    }

    fun removeSearchHistory(query: String) {
        viewModelScope.launch { repository.preferences.removeSearchHistory(query) }
    }

    fun clearSearch() {
        searchQuery.value = ""
        _searchResults.value = emptyList()
        _searchLoading.value = false
        personSearchQuery.value = ""
        _personSearchResults.value = emptyList()
        _personSearchLoading.value = false
    }

    fun applyDiscoverFilters(filters: DiscoverMovieFilters) {
        viewModelScope.launch {
            _discoverFiltersLoading.value = true
            runCatching { withContext(Dispatchers.IO) { discoveryRepository.discover(filters) } }
                .onSuccess { _discoverFilterResults.value = it }
                .onFailure { _state.value = _state.value.copy(error = it.message) }
            _discoverFiltersLoading.value = false
        }
    }

    fun loadStreamingProviders(mediaType: MediaType) {
        viewModelScope.launch {
            _streamingProviders.value = withContext(Dispatchers.IO) {
                watchProviderRepository.getWatchProviders(mediaType).let { providers ->
                    val preferred = _state.value.preferredProviders
                    if (preferred.isEmpty()) providers else providers.filter { it.name in preferred }
                }
            }
        }
    }

    private var settingsProvidersJob: Job? = null
    private val providerPreferenceMutex = kotlinx.coroutines.sync.Mutex()

    fun loadSettingsStreamingProviders(region: String) {
        settingsProvidersJob?.cancel()
        _settingsProvidersLoading.value = true
        _settingsProvidersError.value = false
        _settingsProviders.value = emptyList()
        settingsProvidersJob = viewModelScope.launch {
            try {
                _settingsProviders.value = withContext(Dispatchers.IO) {
                    kotlinx.coroutines.withTimeout(30_000) { watchProviderRepository.getAvailableProviders(region) }
                }
            } catch (timeout: kotlinx.coroutines.TimeoutCancellationException) {
                _settingsProvidersError.value = true
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _settingsProvidersError.value = true
            } finally {
                if (kotlinx.coroutines.currentCoroutineContext().isActive) _settingsProvidersLoading.value = false
            }
        }
    }

    fun setPreferredProviders(values: Set<String>) {
        _state.value = _state.value.copy(preferredProviders = values)
        detailMediaCache.clear()
        viewModelScope.launch {
            providerPreferenceMutex.withLock { repository.preferences.setPreferredProviders(values) }
        }
    }

    fun togglePreferredProvider(name: String) {
        val selected = _state.value.preferredProviders
        setPreferredProviders(if (name in selected) selected - name else selected + name)
    }

    fun setProviderRegion(value: String) {
        _state.value = _state.value.copy(providerRegion = value)
        detailMediaCache.clear()
        viewModelScope.launch {
            providerPreferenceMutex.withLock { repository.preferences.setProviderRegion(value) }
        }
    }

    fun setVisibleProviderTypes(values: Set<String>) {
        _state.value = _state.value.copy(visibleProviderTypes = values)
        detailMediaCache.clear()
        viewModelScope.launch {
            providerPreferenceMutex.withLock { repository.preferences.setVisibleProviderTypes(values) }
        }
    }

    fun setHeroLayout(value: String) {
        val normalized = com.cinetrack.domain.CardAppearance.normalizeHero(value)
        _state.value = _state.value.copy(heroLayout = normalized)
        viewModelScope.launch { repository.preferences.setHeroLayout(normalized) }
    }

    fun setPosterFormat(value: String) {
        val normalized = com.cinetrack.domain.CardAppearance.normalizeFormat(value)
        _state.value = _state.value.copy(posterFormat = normalized)
        viewModelScope.launch { repository.preferences.setPosterFormat(normalized) }
    }

    fun setPosterSize(value: String) {
        val normalized = com.cinetrack.domain.CardAppearance.normalizeSize(value)
        _state.value = _state.value.copy(posterSize = normalized)
        viewModelScope.launch { repository.preferences.setPosterSize(normalized) }
    }

    fun setCardDensity(value: String) {
        _state.value = _state.value.copy(cardDensity = value)
        viewModelScope.launch { repository.preferences.setCardDensity(value) }
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
        _searchResults.value = _searchResults.value.filterNot { it.stableKey == media.stableKey }
        _discoverFilterResults.value = _discoverFilterResults.value.filterNot { it.stableKey == media.stableKey }
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

    fun loadViewingInsights() {
        if (_viewingInsights.value.loading || _viewingInsights.value.actors.isNotEmpty() || _viewingInsights.value.directors.isNotEmpty()) return
        viewModelScope.launch {
            _viewingInsights.value = ViewingPeopleInsights(loading = true)
            val result = withContext(Dispatchers.IO) { runCatching { peopleRepository.viewingPeople(_state.value.history) } }
            _viewingInsights.value = result.fold(
                onSuccess = { (actors, directors) -> ViewingPeopleInsights(actors, directors) },
                onFailure = { ViewingPeopleInsights() },
            )
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
            val pushError = if (syncCoordinator.isMainProviderConnected()) withContext(Dispatchers.IO) {
                repository.pushLibraryChange(media.type, media.id).exceptionOrNull()
            } else null
            // The Simkl request starts immediately after the local commit. TMDB
            // metadata enrichment is independent and must never delay that push.
            scheduleProgressCacheRefresh(
                ProgressRefreshRequest(tvLibraryChanged = media.type == MediaType.TV),
            )
            if (pushError != null) {
                _state.value = _state.value.copy(error = pushError.message)
            }
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
            if (syncCoordinator.isMainProviderConnected()) {
                val pushError = withContext(Dispatchers.IO) {
                    repository.pushLibraryChange(media.type, media.id).exceptionOrNull()
                }
                if (pushError != null) _state.value = _state.value.copy(error = pushError.message)
            }
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
                if (syncCoordinator.isMainProviderConnected()) {
                    val pushError = withContext(Dispatchers.IO) {
                        repository.pushLibraryChange(playback.media.type, playback.media.id).exceptionOrNull()
                    }
                    if (pushError != null) _state.value = _state.value.copy(error = pushError.message)
                }
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
        episodes.firstOrNull()?.showId?.let { showId ->
            detailEpisodeCache[showId] = detailEpisodeCache[showId].orEmpty().map { cached ->
                if ((cached.season to cached.number) in numbers) cached.copy(watched = watched) else cached
            }
        }
        viewModelScope.launch {
            episodes.forEach { libraryRepository.setEpisodeWatched(it, watched) }
            refreshCachedState(refreshProgress = true, promoteShowId = episodes.firstOrNull()?.showId.takeIf { watched })
        }
    }

    fun setEpisodesWatched(episodes: List<EpisodeCard>, watched: Boolean) {
        val changed = episodes.distinctBy { Triple(it.showId, it.season, it.number) }
        changed.groupBy(EpisodeCard::showId).forEach { (showId, showEpisodes) ->
            val numbers = showEpisodes.map { it.season to it.number }.toSet()
            detailEpisodeCache[showId] = detailEpisodeCache[showId].orEmpty().map { cached ->
                if ((cached.season to cached.number) in numbers) cached.copy(watched = watched) else cached
            }
        }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                changed.forEach { libraryRepository.setEpisodeWatched(it, watched) }
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
            cached = withContext(Dispatchers.IO) { repository.loadCachedState() }
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
        detailEpisodeCache[episode.showId] = detailEpisodeCache[episode.showId].orEmpty().map { cached ->
            if (cached.season == episode.season && cached.number == episode.number) cached.copy(watched = watched) else cached
        }
    }

    private suspend fun refreshCachedState(refreshProgress: Boolean = false, promoteShowId: Int? = null) {
        val current = _state.value
        if (refreshProgress) {
            withContext(Dispatchers.IO) {
                repository.refreshProgressCache(ProgressRefreshRequest(episodeHistoryChanged = true))
            }
        }
        val cached = readCachedState()
        val watchedNumbers = cached.history.mapNotNull { event ->
            if (event.media.type == MediaType.TV && event.season != null && event.episodeNumber != null) {
                Triple(event.media.id, event.season, event.episodeNumber)
            } else null
        }.toSet()
        _state.value = cached.copy(
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
        viewModelScope.launch { performTrackingSync(force = true) }
    }

    private suspend fun performTrackingSync(
        force: Boolean,
        publishResult: Boolean = true,
        exposeProgress: Boolean = true,
    ): Result<SyncCoordinatorOutcome> = syncMutex.withLock {
        if (!syncCoordinator.isMainProviderConnected()) {
            return@withLock Result.success(SyncCoordinatorOutcome(itemsChanged = false))
        }
        if (!force && !repository.isSimklSyncDue(TimeUnit.HOURS.toMillis(8))) {
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
        _syncProgress.value = completedSync
        _syncOperations.value = withContext(Dispatchers.IO) { repository.loadSyncOperations() }
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

    fun refreshSyncOperations() {
        viewModelScope.launch {
            _syncOperations.value = withContext(Dispatchers.IO) { repository.loadSyncOperations() }
        }
    }

    fun retrySyncOperation(operationId: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { repository.retrySyncOperation(operationId) }
            _syncOperations.value = withContext(Dispatchers.IO) { repository.loadSyncOperations() }
            result.exceptionOrNull()?.let { error -> _state.value = _state.value.copy(error = error.message) }
        }
    }

    fun resolveSyncConflict(operationId: String, choice: SyncConflictChoice) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { repository.resolveSyncConflict(operationId, choice) }
            _syncOperations.value = withContext(Dispatchers.IO) { repository.loadSyncOperations() }
            if (result.isSuccess) {
                _state.value = readCachedState().copy(sync = _syncProgress.value)
            } else {
                _state.value = _state.value.copy(error = result.exceptionOrNull()?.message)
            }
        }
    }

    /**
     * Refreshes derived Progress metadata without extending the visible Simkl
     * synchronization or queueing duplicate refreshes after rapid status taps.
     */
    private fun scheduleProgressCacheRefresh(
        request: ProgressRefreshRequest = ProgressRefreshRequest(force = true),
    ) {
        progressRefreshRequested = true
        pendingProgressRefresh = pendingProgressRefresh.mergedWith(request)
        if (progressRefreshJob?.isActive == true) return
        progressRefreshJob = viewModelScope.launch {
            do {
                progressRefreshRequested = false
                val refreshRequest = pendingProgressRefresh
                pendingProgressRefresh = ProgressRefreshRequest()
                val refreshResult = withContext(Dispatchers.IO) {
                    runCatching { repository.refreshProgressCache(refreshRequest) }
                }
                if (refreshResult.getOrDefault(false)) {
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

    fun beginSimklLogin(context: Context) {
        viewModelScope.launch {
            _state.value = _state.value.copy(error = null)
            repository.preferences.beginSimklLogin(context).onFailure {
                _state.value = _state.value.copy(error = it.message)
            }
        }
    }

    fun completeSimklLogin(code: String?, state: String?, callbackError: String?) {
        viewModelScope.launch {
            if (!callbackError.isNullOrBlank()) {
                _state.value = _state.value.copy(error = callbackError)
                return@launch
            }
            if (code.isNullOrBlank()) {
                _state.value = _state.value.copy(error = "Simkl did not return an authorization code. Please connect again")
                return@launch
            }
            repository.completeLogin(code, state).onSuccess {
                _state.value = _state.value.copy(simklConnected = true, error = null)
                sync()
            }.onFailure { _state.value = _state.value.copy(error = it.message) }
        }
    }

    fun disconnectSimkl() {
        viewModelScope.launch {
            repository.disconnectSimkl()
            _state.value = _state.value.copy(simklConnected = false)
        }
    }

    fun cachedDetails(media: MediaCard): MediaCard? = detailMediaCache[media.stableKey]
    fun cachedRatings(media: MediaCard): List<RatingScore> = detailRatingsCache[media.stableKey].orEmpty()
    fun cachedEpisodes(showId: Int): List<EpisodeCard> = detailEpisodeCache[showId].orEmpty()

    suspend fun loadTagline(media: MediaCard): String? = withContext(Dispatchers.IO) { mediaRepository.loadTagline(media) }
    suspend fun loadSeasonDetails(show: MediaCard, number: Int): Result<com.cinetrack.domain.SeasonDetails> =
        withContext(Dispatchers.IO) { mediaRepository.loadSeasonDetails(show, number) }
    suspend fun loadDetails(media: MediaCard): MediaCard = detailMediaCache[media.stableKey]
        ?: withContext(Dispatchers.IO) { mediaRepository.loadDetails(media) }.also { detailMediaCache[media.stableKey] = it }
    suspend fun loadMedia(type: MediaType, id: Int): MediaCard? = withContext(Dispatchers.IO) { mediaRepository.loadMedia(type, id) }
    suspend fun loadPerson(person: PersonCard): PersonCard = withContext(Dispatchers.IO) { peopleRepository.getPerson(person) }
    suspend fun loadRatings(media: MediaCard): List<RatingScore> = detailRatingsCache[media.stableKey]
        ?.takeIf { it.isNotEmpty() }
        ?: withContext(Dispatchers.IO) { mediaRepository.loadRatings(media) }.also { if (it.isNotEmpty()) detailRatingsCache[media.stableKey] = it }
    fun cachedCast(media: MediaCard): List<PersonCard> = detailCastCache[media.stableKey].orEmpty()
    suspend fun loadCast(media: MediaCard): List<PersonCard> = detailCastCache[media.stableKey]
        ?: withContext(Dispatchers.IO) { mediaRepository.loadCast(media) }.also {
            if (it.isNotEmpty()) detailCastCache[media.stableKey] = it
        }
    suspend fun loadEpisodes(show: MediaCard, season: Int = 1): List<EpisodeCard> = withContext(Dispatchers.IO) { mediaRepository.loadEpisodes(show, season) }
    suspend fun loadAllEpisodes(show: MediaCard): List<EpisodeCard> = detailEpisodeCache[show.id]
        ?.takeIf { it.isNotEmpty() }
        ?: withContext(Dispatchers.IO) { mediaRepository.loadAllEpisodes(show) }.also { if (it.isNotEmpty()) detailEpisodeCache[show.id] = it }
    suspend fun loadEpisode(show: MediaCard, season: Int, number: Int): EpisodeCard? = withContext(Dispatchers.IO) { mediaRepository.loadEpisode(show, season, number) }
    suspend fun loadEpisodeCast(show: MediaCard, season: Int, number: Int): List<PersonCard> = withContext(Dispatchers.IO) { mediaRepository.loadEpisodeCast(show, season, number) }
    suspend fun loadCollection(media: MediaCard): List<MediaCard> = withContext(Dispatchers.IO) { mediaRepository.loadCollection(media) }
    suspend fun loadRecommendations(media: MediaCard): List<MediaCard> = withContext(Dispatchers.IO) { mediaRepository.loadRecommendations(media) }
    suspend fun loadTrailerKey(media: MediaCard): String? = withContext(Dispatchers.IO) { mediaRepository.loadTrailerKey(media) }

    fun setBackgroundSync(enabled: Boolean) {
        _state.value = _state.value.copy(backgroundSync = enabled)
        viewModelScope.launch { repository.preferences.setBackgroundSync(enabled) }
    }

    fun setWifiOnly(enabled: Boolean) {
        _state.value = _state.value.copy(wifiOnly = enabled)
        viewModelScope.launch { repository.preferences.setWifiOnly(enabled) }
    }
    fun setLanguage(value: String) = viewModelScope.launch { repository.preferences.setLanguage(value) }
    fun setNotification(kind: String, enabled: Boolean) {
        _state.value = when (kind) {
            "episodes" -> _state.value.copy(notificationEpisodes = enabled)
            "movies" -> _state.value.copy(notificationMovies = enabled)
            else -> _state.value.copy(notificationSync = enabled)
        }
        viewModelScope.launch { repository.preferences.setNotification(kind, enabled) }
    }

    fun setQuietHours(enabled: Boolean, startHour: Int? = null, endHour: Int? = null) {
        _state.value = _state.value.copy(
            quietHoursEnabled = enabled,
            quietHoursStart = startHour ?: _state.value.quietHoursStart,
            quietHoursEnd = endHour ?: _state.value.quietHoursEnd,
        )
        viewModelScope.launch { repository.preferences.setQuietHours(enabled, startHour, endHour) }
    }

    fun setExcludeSpecials(enabled: Boolean) {
        _state.value = _state.value.copy(excludeSpecials = enabled)
        viewModelScope.launch {
            repository.preferences.setExcludeSpecials(enabled)
            scheduleProgressCacheRefresh()
        }
    }
    fun setRatingSource(source: String, enabled: Boolean) {
        val key = when (source.lowercase()) {
            "rotten tomatoes", "r.tomatoes", "tomatoes" -> "tomatoes"
            else -> source.lowercase()
        }
        _state.value = _state.value.copy(
            ratingSources = if (enabled) _state.value.ratingSources + key else _state.value.ratingSources - key,
        )
        detailRatingsCache.clear()
        viewModelScope.launch { repository.preferences.setRatingSource(source, enabled) }
    }

    fun setContentRegions(regions: Set<String>) {
        _state.value = _state.value.copy(contentRegions = regions)
        viewModelScope.launch {
            repository.preferences.setContentRegions(regions)
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

    fun setUiAccent(value: String) {
        _state.value = _state.value.copy(uiAccent = value)
        viewModelScope.launch { repository.setUiAccent(value) }
    }

    fun completeIntroduction() {
        _state.value = _state.value.copy(introductionCompleted = true)
        viewModelScope.launch { repository.setIntroductionCompleted(true) }
    }

    fun verifyAndSetTmdbApiKey(value: String, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { repository.verifyAndSetTmdbApiKey(value) }
            if (result.isSuccess) {
                _state.value = _state.value.copy(tmdbApiConfigured = true, error = null)
                onResult(Result.success(Unit))
                val refreshError = withContext(Dispatchers.IO) {
                runCatching { discoveryRepository.refresh() }.exceptionOrNull()
                }
                if (refreshError == null) {
                    val current = _state.value
                    _state.value = readCachedState().copy(
                        sync = _syncProgress.value,
                        people = current.people,
                    )
                } else {
                    _state.value = _state.value.copy(error = refreshError.message)
                }
            } else onResult(result)
        }
    }

    fun verifyAndSetMdbListApiKey(value: String, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { repository.verifyAndSetMdbListApiKey(value) }
            if (result.isSuccess) {
                detailRatingsCache.clear()
                _state.value = _state.value.copy(mdbListApiConfigured = true, error = null)
            }
            onResult(result)
        }
    }

    fun setTmdbApiKey(value: String?) {
        viewModelScope.launch {
            repository.setTmdbApiKey(value)
            _state.value = _state.value.copy(tmdbApiConfigured = repository.preferences.tmdbApiKeyNow().isNotBlank())
        }
    }

    fun setMdbListApiKey(value: String?) {
        viewModelScope.launch {
            repository.setMdbListApiKey(value)
            detailRatingsCache.clear()
            _state.value = _state.value.copy(mdbListApiConfigured = repository.preferences.mdbListApiKeyNow().isNotBlank())
        }
    }

    fun setMetadataLanguage(value: String) {
        _state.value = _state.value.copy(metadataLanguage = value)
        detailMediaCache.clear()
        detailCastCache.clear()
        detailEpisodeCache.clear()
        viewModelScope.launch { repository.setMetadataLanguage(value) }
    }

    fun setMetadataRegion(value: String) {
        _state.value = _state.value.copy(metadataRegion = value)
        detailMediaCache.clear()
        viewModelScope.launch { repository.setMetadataRegion(value) }
    }

    fun setMetadataTimezone(value: String) {
        _state.value = _state.value.copy(metadataTimezone = value)
        viewModelScope.launch { repository.setMetadataTimezone(value) }
    }

    fun dismissError() {
        _state.value = _state.value.copy(error = null)
    }

    fun exportData(context: Context, sections: Set<String> = emptySet()) {
        viewModelScope.launch {
            runCatching {
                val file = withContext(Dispatchers.IO) {
                    val files = repository.exportBackupFiles(sections)
                    val directory = File(context.cacheDir, "exports").apply { mkdirs() }
                    val target = File(directory, "cinetrack-backup-${LocalDate.now()}.zip")
                    ZipOutputStream(FileOutputStream(target)).use { archive ->
                        files.forEach { (path, contents) ->
                            archive.putNextEntry(ZipEntry(path))
                            archive.write(contents.toByteArray())
                            archive.closeEntry()
                        }
                    }
                    target
                }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(share, context.getString(com.cinetrack.R.string.export_data)))
            }.onFailure { _state.value = _state.value.copy(error = it.message) }
        }
    }

    fun exportLogs(context: Context) {
        viewModelScope.launch {
            runCatching {
                val file = withContext(Dispatchers.IO) {
                    val directory = File(context.cacheDir, "exports").apply { mkdirs() }
                    File(directory, "cinetrack-logs.txt").apply {
                        writeText(_errorLogs.value.joinToString(separator = "\n", postfix = "\n"))
                    }
                }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(share, context.getString(com.cinetrack.R.string.export_logs)))
            }.onFailure { _state.value = _state.value.copy(error = it.message) }
        }
    }

    fun checkForAppUpdate() {
        if (_appUpdateState.value is AppUpdateState.Checking || _appUpdateState.value is AppUpdateState.Downloading) return
        viewModelScope.launch {
            _appUpdateState.value = AppUpdateState.Checking
            GitHubAppUpdater.check()
                .onSuccess { update ->
                    _appUpdateState.value = update?.let(AppUpdateState::Available) ?: AppUpdateState.UpToDate
                }
                .onFailure { failure ->
                    _appUpdateState.value = AppUpdateState.Error(failure.message ?: "Update check failed")
                }
        }
    }

    fun loadAppChangelog() {
        if (_appChangelogState.value is AppChangelogState.Loading) return
        viewModelScope.launch {
            _appChangelogState.value = AppChangelogState.Loading
            GitHubAppUpdater.latestRelease()
                .onSuccess { release ->
                    _appChangelogState.value = release?.let(AppChangelogState::Available)
                        ?: AppChangelogState.Empty
                }
                .onFailure { failure ->
                    _appChangelogState.value = AppChangelogState.Error(
                        failure.message ?: "Could not load the changelog",
                    )
                }
        }
    }

    fun openAppUpdate(context: Context) {
        val update = (_appUpdateState.value as? AppUpdateState.Available)?.update ?: return
        viewModelScope.launch {
            val cached = downloadedUpdateFile?.takeIf(File::exists)
            if (cached != null) {
                runCatching { GitHubAppUpdater.launchInstaller(context, cached) }
                    .onFailure { _appUpdateState.value = AppUpdateState.Error(it.message ?: "Could not open Android installer") }
                return@launch
            }
            _appUpdateState.value = AppUpdateState.Downloading(update, 0f)
            GitHubAppUpdater.download(context, update) { progress ->
                _appUpdateState.value = AppUpdateState.Downloading(update, progress)
            }.onSuccess { apk ->
                downloadedUpdateFile = apk
                _appUpdateState.value = AppUpdateState.Available(update)
                runCatching { GitHubAppUpdater.launchInstaller(context, apk) }
                    .onFailure { _appUpdateState.value = AppUpdateState.Error(it.message ?: "Could not open Android installer") }
            }.onFailure { failure ->
                _appUpdateState.value = AppUpdateState.Error(failure.message ?: "Update download failed")
            }
        }
    }

    fun restoreData(context: Context, uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val restored = withContext(Dispatchers.IO) {
                    val entries = linkedMapOf<String, String>()
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        ZipInputStream(input).use { archive ->
                            var totalBytes = 0L
                            var entry = archive.nextEntry
                            while (entry != null) {
                                if (!entry.isDirectory) {
                                    check(entries.size < 32) { "Backup contains too many entries" }
                                    check(entry.size <= 32L * 1024 * 1024 || entry.size < 0L) { "Backup entry is too large" }
                                    val output = ByteArrayOutputStream()
                                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                    var entryBytes = 0L
                                    while (true) {
                                        val read = archive.read(buffer)
                                        if (read <= 0) break
                                        entryBytes += read
                                        totalBytes += read
                                        check(entryBytes <= 32L * 1024 * 1024) { "Backup entry is too large" }
                                        check(totalBytes <= 64L * 1024 * 1024) { "Backup is too large" }
                                        output.write(buffer, 0, read)
                                    }
                                    entries[entry.name] = output.toByteArray().decodeToString()
                                }
                                archive.closeEntry()
                                entry = archive.nextEntry
                            }
                        }
                    } ?: error("Could not open backup")
                    repository.restoreBackupFiles(entries)
                    repository.refreshProgressCache()
                }
                val cached = readCachedState()
                _state.value = cached.copy(sync = _syncProgress.value, error = null)
                restored
            }.onFailure { _state.value = _state.value.copy(error = it.message) }
        }
    }

    fun restoreAutomaticBackup() {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    repository.restoreAutomaticBackup()
                    repository.refreshProgressCache()
                }
                val cached = readCachedState()
                _state.value = cached.copy(sync = _syncProgress.value, error = null)
            }.onFailure { _state.value = _state.value.copy(error = it.message) }
        }
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
                        writeText(buildString {
                            appendLine("BEGIN:VCALENDAR")
                            appendLine("VERSION:2.0")
                            appendLine("PRODID:-//CineTrack//Upcoming//EN")
                            _state.value.calendar.forEach { item ->
                                val day = item.timestamp.take(10).replace("-", "")
                                val uid = "${item.media.stableKey}-${item.season ?: 0}-${item.episodeNumber ?: 0}-$day@cinetrack"
                                val summary = (item.media.title + item.episodeLabel?.let { " · $it" }.orEmpty())
                                    .replace("\\", "\\\\").replace(",", "\\,").replace(";", "\\;")
                                appendLine("BEGIN:VEVENT")
                                appendLine("UID:$uid")
                                val releaseAt = releaseDateTime(item.timestamp, releaseZone)
                                if (releaseAt != null && hasExplicitReleaseTime(item.timestamp)) {
                                    val utcTimestamp = java.time.format.DateTimeFormatter
                                        .ofPattern("yyyyMMdd'T'HHmmss'Z'")
                                        .withZone(java.time.ZoneOffset.UTC)
                                        .format(releaseAt.toInstant())
                                    appendLine("DTSTART:$utcTimestamp")
                                } else {
                                    appendLine("DTSTART;VALUE=DATE:$day")
                                }
                                appendLine("SUMMARY:$summary")
                                appendLine("END:VEVENT")
                            }
                            appendLine("END:VCALENDAR")
                        })
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
            ) as T
    }
}

