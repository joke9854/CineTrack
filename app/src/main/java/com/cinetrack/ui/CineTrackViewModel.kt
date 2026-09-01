package com.cinetrack.ui

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cinetrack.data.repository.CineTrackRepository
import com.cinetrack.data.repository.ProgressRefreshRequest
import com.cinetrack.data.repository.SimklSyncOutcome
import com.cinetrack.data.update.AppUpdateState
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
import com.cinetrack.domain.ViewingPeopleInsights
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
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
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.util.zip.ZipInputStream
import android.net.Uri

class CineTrackViewModel(private val repository: CineTrackRepository) : ViewModel() {
    private val syncMutex = Mutex()
    private val refreshMutex = Mutex()
    private var progressRefreshJob: Job? = null
    private var progressRefreshRequested = false
    private var pendingProgressRefresh = ProgressRefreshRequest()
    private var startupStateBuilding = true
    private val detailMediaCache = mutableMapOf<String, MediaCard>()
    private val detailRatingsCache = mutableMapOf<String, List<RatingScore>>()
    private val detailEpisodeCache = mutableMapOf<Int, List<EpisodeCard>>()
    private val _state = MutableStateFlow(
        AppUiState(loading = true, simklConnected = repository.simklConnectedNow()),
    )
    val state: StateFlow<AppUiState> = _state.asStateFlow()
    private val _errorLogs = MutableStateFlow<List<String>>(emptyList())
    val errorLogs: StateFlow<List<String>> = _errorLogs.asStateFlow()
    private val _viewingInsights = MutableStateFlow(ViewingPeopleInsights())
    val viewingInsights: StateFlow<ViewingPeopleInsights> = _viewingInsights.asStateFlow()

    private val _searchResults = MutableStateFlow<List<MediaCard>>(emptyList())
    val searchResults: StateFlow<List<MediaCard>> = _searchResults.asStateFlow()
    private val _discoverFilterResults = MutableStateFlow<List<MediaCard>>(emptyList())
    val discoverFilterResults: StateFlow<List<MediaCard>> = _discoverFilterResults.asStateFlow()
    private val _discoverFiltersLoading = MutableStateFlow(false)
    val discoverFiltersLoading: StateFlow<Boolean> = _discoverFiltersLoading.asStateFlow()
    private val _streamingProviders = MutableStateFlow<List<StreamingProvider>>(emptyList())
    val streamingProviders: StateFlow<List<StreamingProvider>> = _streamingProviders.asStateFlow()
    private val _appUpdateState = MutableStateFlow<AppUpdateState>(AppUpdateState.Idle)
    val appUpdateState: StateFlow<AppUpdateState> = _appUpdateState.asStateFlow()
    private var downloadedUpdateFile: File? = null

    init {
        viewModelScope.launch {
            _errorLogs.value = withContext(Dispatchers.IO) { repository.preferences.readErrorLogs() }
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
            repository.observeLocalChanges().collectLatest {
                // Room can emit several invalidations for one synchronization.
                // Wait for the burst to settle and never publish a raw snapshot
                // while the coordinated sync is still rebuilding derived rows.
                delay(280)
                if (syncMutex.isLocked || refreshMutex.isLocked || repository.progressCacheRefreshing || startupStateBuilding) return@collectLatest
                val current = _state.value
                val cached = withContext(Dispatchers.IO) { repository.loadCachedState() }
                _state.value = cached.copy(
                    refreshing = current.refreshing,
                    error = current.error,
                    people = current.people,
                    sync = if (current.sync.running) current.sync else cached.sync,
                )
            }
        }
        viewModelScope.launch {
            // Room and DataStore are the source of truth at launch. Publish them
            // before any network work so process recreation never looks like a
            // disconnected, empty account while enrichment is running.
            val cached = withContext(Dispatchers.IO) { repository.loadCachedState() }
            _state.value = cached
            val coldSync = if (cached.simklConnected) {
                // Keep the cached UI stable while a cold-start delta check runs.
                // Publishing its intermediate snapshots caused the visible reorder
                // and scroll stalls captured in the supplied recording.
                performSimklSync(force = true, publishResult = false, exposeProgress = false)
            } else Result.success(SimklSyncOutcome(itemsChanged = false))
            var databaseChanged = coldSync.getOrNull()?.itemsChanged == true
            var discoverError: Throwable? = null
            if (cached.tmdbApiConfigured && cached.rails.values.all { it.isEmpty() }) {
                withContext(Dispatchers.IO) {
                    runCatching { repository.refreshDiscover() }
                        .onSuccess { databaseChanged = true }
                        .onFailure { discoverError = it }
                }
            }
            val error = coldSync.exceptionOrNull()?.message ?: discoverError?.message
            if (databaseChanged) {
                _state.value = withContext(Dispatchers.IO) { repository.loadCachedState() }.copy(error = error)
            } else if (error != null) {
                _state.value = _state.value.copy(error = error)
            }
            startupStateBuilding = false
            // A successful Simkl sync already rebuilt the correctness-critical
            // Progress cache and queued cosmetic enrichment. Only disconnected or
            // failed startup paths still need an independent cache refresh.
            if (!cached.simklConnected || coldSync.isFailure) scheduleProgressCacheRefresh()
        }
        viewModelScope.launch {
            val interval = TimeUnit.MINUTES.toMillis(510)
            delay(interval)
            while (isActive) {
                performSimklSync(force = false)
                delay(interval)
            }
        }
    }

    fun refresh() {
        if (!_state.value.tmdbApiConfigured) return
        viewModelScope.launch {
            refreshMutex.withLock {
                _state.value = _state.value.copy(refreshing = true, error = null)
                val refreshed = withContext(Dispatchers.IO) {
                    runCatching {
                        repository.refreshDiscover()
                        repository.refreshProgressCache()
                        repository.loadCachedState().copy(refreshing = false)
                    }
                }
                _state.value = refreshed.getOrElse { _state.value.copy(refreshing = false, error = it.message) }
            }
        }
    }

    fun search(query: String) {
        viewModelScope.launch { _searchResults.value = repository.search(query) }
    }

    fun applyDiscoverFilters(filters: DiscoverMovieFilters) {
        viewModelScope.launch {
            _discoverFiltersLoading.value = true
            runCatching { withContext(Dispatchers.IO) { repository.discoverMovies(filters) } }
                .onSuccess { _discoverFilterResults.value = it }
                .onFailure { _state.value = _state.value.copy(error = it.message) }
            _discoverFiltersLoading.value = false
        }
    }

    fun loadStreamingProviders(mediaType: MediaType) {
        viewModelScope.launch {
            _streamingProviders.value = withContext(Dispatchers.IO) {
                repository.loadStreamingProviders(mediaType).let { providers ->
                    val preferred = _state.value.preferredProviders
                    if (preferred.isEmpty()) providers else providers.filter { it.name in preferred }
                }
            }
        }
    }

    fun loadSettingsStreamingProviders() {
        viewModelScope.launch {
            val providers = withContext(Dispatchers.IO) {
                repository.loadSettingsStreamingProviders()
            }
            _streamingProviders.value = providers
            if (providers.isNotEmpty()) {
                val availableNames = providers.map(StreamingProvider::name).toSet()
                val retained = _state.value.preferredProviders.intersect(availableNames)
                if (retained != _state.value.preferredProviders) setPreferredProviders(retained)
            }
        }
    }

    fun setPreferredProviders(values: Set<String>) {
        _state.value = _state.value.copy(preferredProviders = values)
        viewModelScope.launch { repository.preferences.setPreferredProviders(values) }
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
            val error = withContext(Dispatchers.IO) { runCatching { repository.refreshDiscover() }.exceptionOrNull() }
            if (error == null) {
                val cached = withContext(Dispatchers.IO) { repository.loadCachedState() }
                _state.value = cached.copy(sync = _state.value.sync)
            } else _state.value = _state.value.copy(error = error.message)
        }
    }

    fun loadViewingInsights() {
        if (_viewingInsights.value.loading || _viewingInsights.value.actors.isNotEmpty() || _viewingInsights.value.directors.isNotEmpty()) return
        viewModelScope.launch {
            _viewingInsights.value = ViewingPeopleInsights(loading = true)
            val result = withContext(Dispatchers.IO) { runCatching { repository.loadViewingPeople(_state.value.history) } }
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
            val cached = withContext(Dispatchers.IO) { repository.loadCachedState() }
            _state.value = cached.copy(sync = _state.value.sync)
        }
    }

    fun setStatus(media: MediaCard, status: LibraryStatus) {
        viewModelScope.launch {
            val syncState = _state.value.sync
            val refreshed = withContext(Dispatchers.IO) {
                repository.setLibraryStatus(media, status)
                repository.loadCachedState().copy(sync = syncState)
            }
            _state.value = refreshed
            val pushError = if (repository.simklConnectedNow()) withContext(Dispatchers.IO) {
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
            repository.markWatched(media)
            val current = _state.value
            fun update(item: MediaCard) = if (item.stableKey == media.stableKey) item.copy(watched = true, status = LibraryStatus.COMPLETED) else item
            _state.value = current.copy(
                rails = current.rails.mapValues { (_, items) -> items.map(::update) },
                playbackTv = current.playbackTv.filterNot { it.media.stableKey == media.stableKey },
                playbackMovies = current.playbackMovies.filterNot { it.media.stableKey == media.stableKey },
            )
            if (repository.simklConnectedNow()) {
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
        if (playback.media.type == MediaType.TV) {
            val current = _state.value
            _state.value = current.copy(
                playbackTv = listOf(playback) + current.playbackTv.filterNot { it.media.id == playback.media.id },
            )
        }
        viewModelScope.launch {
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
                    airDate = null,
                )
            } else null
            if (episode != null) {
                val syncState = _state.value.sync
                val refreshed = withContext(Dispatchers.IO) {
                    repository.markEpisodeWatched(episode)
                    repository.refreshProgressCache(
                        ProgressRefreshRequest(episodeHistoryChanged = true),
                    )
                    repository.loadCachedState().copy(sync = syncState)
                }
                val promoted = refreshed.playbackTv.firstOrNull { it.media.id == playback.media.id }
                _state.value = refreshed.copy(
                    playbackTv = if (promoted == null) refreshed.playbackTv
                    else listOf(promoted) + refreshed.playbackTv.filterNot { it.media.id == playback.media.id },
                )
            } else if (playback.media.type == MediaType.MOVIE) {
                repository.markWatched(playback.media)
                _state.value = _state.value.copy(
                    playbackMovies = _state.value.playbackMovies.filterNot { it.media.stableKey == playback.media.stableKey },
                )
                if (repository.simklConnectedNow()) {
                    val pushError = withContext(Dispatchers.IO) {
                        repository.pushLibraryChange(playback.media.type, playback.media.id).exceptionOrNull()
                    }
                    if (pushError != null) _state.value = _state.value.copy(error = pushError.message)
                }
            }
        }
    }

    fun markEpisodeWatched(episode: EpisodeCard) {
        updateCachedEpisode(episode, watched = true)
        viewModelScope.launch {
            repository.markEpisodeWatched(episode)
            refreshCachedState(refreshProgress = true, promoteShowId = episode.showId)
        }
    }

    fun setEpisodeWatched(episode: EpisodeCard, watched: Boolean) {
        updateCachedEpisode(episode, watched)
        viewModelScope.launch {
            repository.setEpisodeWatched(episode, watched)
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
            episodes.forEach { repository.setEpisodeWatched(it, watched) }
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
                changed.forEach { repository.setEpisodeWatched(it, watched) }
            }
            refreshCachedState(refreshProgress = true, promoteShowId = changed.firstOrNull()?.showId.takeIf { watched })
        }
    }

    private fun updateCachedEpisode(episode: EpisodeCard, watched: Boolean) {
        detailEpisodeCache[episode.showId] = detailEpisodeCache[episode.showId].orEmpty().map { cached ->
            if (cached.season == episode.season && cached.number == episode.number) cached.copy(watched = watched) else cached
        }
    }

    private suspend fun refreshCachedState(refreshProgress: Boolean = false, promoteShowId: Int? = null) {
        val current = _state.value
        val cached = withContext(Dispatchers.IO) {
            if (refreshProgress) {
                repository.refreshProgressCache(
                    ProgressRefreshRequest(episodeHistoryChanged = true),
                )
            }
            repository.loadCachedState()
        }
        val watchedNumbers = cached.history.mapNotNull { event ->
            if (event.media.type == MediaType.TV && event.season != null && event.episodeNumber != null) {
                Triple(event.media.id, event.season, event.episodeNumber)
            } else null
        }.toSet()
        _state.value = cached.copy(
            sync = current.sync,
            people = current.people,
            playbackTv = cached.playbackTv.let { items ->
                val promoted = promoteShowId?.let { id -> items.firstOrNull { it.media.id == id } }
                if (promoted == null) items else listOf(promoted) + items.filterNot { it.media.id == promoteShowId }
            },
            episodes = current.episodes.map { episode ->
                episode.copy(watched = Triple(episode.showId, episode.season, episode.number) in watchedNumbers)
            },
        )
    }

    fun sync() {
        if (_state.value.sync.running) return
        viewModelScope.launch { performSimklSync(force = true) }
    }

    private suspend fun performSimklSync(
        force: Boolean,
        publishResult: Boolean = true,
        exposeProgress: Boolean = true,
    ): Result<SimklSyncOutcome> = syncMutex.withLock {
        if (!repository.simklConnectedNow()) {
            return@withLock Result.success(SimklSyncOutcome(itemsChanged = false))
        }
        if (!force && !repository.isSimklSyncDue(TimeUnit.HOURS.toMillis(8))) {
            return@withLock Result.success(SimklSyncOutcome(itemsChanged = false))
        }
        var completedSync = _state.value.sync
        val result = withContext(Dispatchers.IO) {
            repository.syncSimkl { progress ->
                completedSync = progress
                if (exposeProgress) {
                    _state.value = _state.value.copy(sync = progress)
                }
            }
        }
        val outcome = result.getOrNull()
        if (outcome?.itemsChanged == true && publishResult) {
            // The repository does not finish the visible sync until both the
            // remote transaction and correctness-critical Progress data are complete.
            _state.value = withContext(Dispatchers.IO) { repository.loadCachedState() }
                .copy(sync = completedSync)
            viewModelScope.launch(Dispatchers.IO) { repository.createAutomaticBackup() }
        } else if (outcome != null && exposeProgress) {
            // Complete the synchronization indicator without replacing any page
            // collections when Simkl reported an unchanged activity generation.
            _state.value = _state.value.copy(sync = completedSync)
        } else if (result.isFailure) {
            val cached = withContext(Dispatchers.IO) { repository.loadCachedState() }
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
                    val cached = withContext(Dispatchers.IO) { repository.loadCachedState() }
                    val current = _state.value
                    _state.value = cached.copy(
                        refreshing = current.refreshing,
                        people = current.people,
                        sync = current.sync,
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

    suspend fun loadDetails(media: MediaCard): MediaCard = detailMediaCache[media.stableKey]
        ?: repository.loadDetails(media).also { detailMediaCache[media.stableKey] = it }
    suspend fun loadMedia(type: MediaType, id: Int): MediaCard? = repository.loadMedia(type, id)
    suspend fun loadPerson(person: PersonCard): PersonCard = repository.loadPerson(person)
    suspend fun loadRatings(media: MediaCard): List<RatingScore> = detailRatingsCache[media.stableKey]
        ?.takeIf { it.isNotEmpty() }
        ?: repository.loadRatings(media).also { if (it.isNotEmpty()) detailRatingsCache[media.stableKey] = it }
    suspend fun loadCast(media: MediaCard): List<PersonCard> = repository.loadCast(media)
    suspend fun loadEpisodes(show: MediaCard, season: Int = 1): List<EpisodeCard> = repository.loadEpisodes(show, season)
    suspend fun loadAllEpisodes(show: MediaCard): List<EpisodeCard> = detailEpisodeCache[show.id]
        ?.takeIf { it.isNotEmpty() }
        ?: repository.loadAllEpisodes(show).also { if (it.isNotEmpty()) detailEpisodeCache[show.id] = it }
    suspend fun loadEpisode(show: MediaCard, season: Int, number: Int): EpisodeCard? = repository.loadEpisode(show, season, number)
    suspend fun loadEpisodeCast(show: MediaCard, season: Int, number: Int): List<PersonCard> = repository.loadEpisodeCast(show, season, number)
    suspend fun loadCollection(media: MediaCard): List<MediaCard> = repository.loadCollection(media)
    suspend fun loadRecommendations(media: MediaCard): List<MediaCard> = repository.loadRecommendations(media)
    suspend fun loadTrailerKey(media: MediaCard): String? = repository.loadTrailerKey(media)

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
                runCatching { repository.refreshDiscover() }.exceptionOrNull()
            }
            if (refreshError == null) {
                val cached = withContext(Dispatchers.IO) { repository.loadCachedState() }
                val current = _state.value
                _state.value = cached.copy(sync = current.sync, people = current.people, error = current.error)
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
                    runCatching { repository.refreshDiscover() }.exceptionOrNull()
                }
                if (refreshError == null) {
                    val current = _state.value
                    _state.value = withContext(Dispatchers.IO) { repository.loadCachedState() }.copy(
                        sync = current.sync,
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
                            var entry = archive.nextEntry
                            while (entry != null) {
                                if (!entry.isDirectory) {
                                    check(entry.size <= 10_000_000L || entry.size < 0L) { "Backup entry is too large" }
                                    entries[entry.name] = archive.readBytes().decodeToString()
                                }
                                archive.closeEntry()
                                entry = archive.nextEntry
                            }
                        }
                    } ?: error("Could not open backup")
                    repository.restoreBackupFiles(entries)
                    repository.refreshProgressCache()
                }
                val cached = withContext(Dispatchers.IO) { repository.loadCachedState() }
                _state.value = cached.copy(sync = _state.value.sync, error = null)
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
                val cached = withContext(Dispatchers.IO) { repository.loadCachedState() }
                _state.value = cached.copy(sync = _state.value.sync, error = null)
            }.onFailure { _state.value = _state.value.copy(error = it.message) }
        }
    }

    fun exportCalendar(context: Context) {
        viewModelScope.launch {
            runCatching {
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
                                appendLine("DTSTART;VALUE=DATE:$day")
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

    class Factory(private val repository: CineTrackRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = CineTrackViewModel(repository) as T
    }
}
