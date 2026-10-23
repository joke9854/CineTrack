package com.cinetrack.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cinetrack.data.discovery.DiscoveryRepository
import com.cinetrack.data.watchprovider.WatchProviderRepository
import com.cinetrack.domain.DiscoverBrowseState
import com.cinetrack.domain.DiscoverMovieFilters
import com.cinetrack.domain.LibraryStatus
import com.cinetrack.domain.MediaCard
import com.cinetrack.domain.MediaType
import com.cinetrack.domain.RailIds
import com.cinetrack.domain.StreamingProvider
import com.cinetrack.domain.appendPage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import com.cinetrack.ui.viewModelFactoryOf

/**
 * The Discover filter page, the Mosaic layout's chip results and the streaming
 * providers the filter page offers, and the rails' "see all" pages. The rails themselves stay
 * in AppUiState (built from the library); a page load takes that state. Activity-scoped,
 * because the details route looks up a title opened from these results.
 */
class DiscoverViewModel(
    private val discoveryRepository: DiscoveryRepository,
    private val watchProviderRepository: WatchProviderRepository,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 4)
    /** Failures for the app's error banner; CineTrackApp forwards them to CineTrackViewModel. */
    val errors: SharedFlow<String> = _errors.asSharedFlow()
    private val _discoverFilterResults = MutableStateFlow<List<MediaCard>>(emptyList())
    val discoverFilterResults: StateFlow<List<MediaCard>> = _discoverFilterResults.asStateFlow()
    private val _discoverFiltersLoading = MutableStateFlow(false)
    val discoverFiltersLoading: StateFlow<Boolean> = _discoverFiltersLoading.asStateFlow()
    /** Results for the Mosaic layout's type / window / genre chips; kept apart from the filter page. */
    private val _mosaicResults = MutableStateFlow<Pair<DiscoverMovieFilters, List<MediaCard>>?>(null)
    val mosaicResults: StateFlow<Pair<DiscoverMovieFilters, List<MediaCard>>?> = _mosaicResults.asStateFlow()
    private val mosaicCache = mutableMapOf<DiscoverMovieFilters, List<MediaCard>>()
    // Next batch to load for each mosaic view; absent once TMDB has no more.
    private val mosaicNextBatch = mutableMapOf<DiscoverMovieFilters, Int>()
    private var mosaicMoreJob: Job? = null
    private val _mosaicCanLoadMore = MutableStateFlow(false)
    val mosaicCanLoadMore: StateFlow<Boolean> = _mosaicCanLoadMore.asStateFlow()
    private var mosaicJob: Job? = null
    private val _streamingProviders = MutableStateFlow<List<StreamingProvider>>(emptyList())
    val streamingProviders: StateFlow<List<StreamingProvider>> = _streamingProviders.asStateFlow()

    private val _discoverBrowse = MutableStateFlow<Map<String, DiscoverBrowseState>>(emptyMap())
    /** The "see all" pages of the Discover rails, keyed by discoverBrowseKey (rail, region, language). */
    val discoverBrowse: StateFlow<Map<String, DiscoverBrowseState>> = _discoverBrowse.asStateFlow()
    private var browseGeneration = 0

    /** Loads the next pages of [railId]; [state] gives its key and first items (the rail itself). */
    fun loadDiscoverMore(railId: String, state: DiscoverUiState) {
        val key = discoverBrowseKey(railId, state)
        val existing = _discoverBrowse.value[key] ?: DiscoverBrowseState(items = state.rails[railId].orEmpty())
        if (existing.loading || !existing.hasMore) return
        val generation = browseGeneration
        fun publish(value: DiscoverBrowseState) {
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
                withTimeout(30_000) {
                    var scanned = 0
                    val targetCount = existing.items.size + 20
                    // Skip cached duplicates and sparse filtered pages, but bound each user action.
                    while (current.hasMore && current.items.size < targetCount && scanned < 6) {
                        val page = withContext(io) { discoveryRepository.page(railId, current.nextPage) }
                        current = current.appendPage(page, railId == RailIds.UPCOMING)
                        publish(current)
                        scanned++
                    }
                }
            } catch (timeout: TimeoutCancellationException) {
                current = current.copy(failed = true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                current = current.copy(failed = true)
            } finally {
                publish(current.copy(loading = false))
            }
        }
    }

    /** A Discover refresh started: drop the paged rails, and ignore pages still arriving for them. */
    fun resetBrowse() {
        browseGeneration++
        _discoverBrowse.value = emptyMap()
    }

    /** A status write committed (CineTrackViewModel.libraryStatusChanges). */
    fun applyStatus(media: MediaCard, status: LibraryStatus) {
        _discoverBrowse.value = _discoverBrowse.value.mapValues { (_, browse) ->
            browse.copy(items = browse.items.map { if (it.stableKey == media.stableKey) it.copy(status = status, watched = status == LibraryStatus.COMPLETED) else it })
        }
    }

    private fun report(failure: Throwable) {
        failure.message?.let(_errors::tryEmit)
    }

    fun applyDiscoverFilters(filters: DiscoverMovieFilters) {
        viewModelScope.launch {
            _discoverFiltersLoading.value = true
            runCatching { withContext(io) { discoveryRepository.discover(filters) } }
                .onSuccess { _discoverFilterResults.value = it }
                .onFailure(::report)
            _discoverFiltersLoading.value = false
        }
    }

    fun loadMosaic(filters: DiscoverMovieFilters) {
        mosaicCache[filters]?.let {
            _mosaicResults.value = filters to it
            _mosaicCanLoadMore.value = filters in mosaicNextBatch
            return
        }
        mosaicJob?.cancel()
        mosaicMoreJob?.cancel()
        _mosaicCanLoadMore.value = false
        mosaicJob = viewModelScope.launch {
            runCatching { withContext(io) { discoveryRepository.discoverBatch(filters, 1) } }
                .onSuccess { page ->
                    if (mosaicCache.size > 24) {
                        mosaicCache.clear()
                        mosaicNextBatch.clear()
                    }
                    mosaicCache[filters] = page.items
                    if (page.hasMore) mosaicNextBatch[filters] = 2 else mosaicNextBatch.remove(filters)
                    _mosaicResults.value = filters to page.items
                    _mosaicCanLoadMore.value = page.hasMore
                }
                .onFailure { if (it !is CancellationException) report(it) }
        }
    }

    /** Appends the next batch to the mosaic on screen; called as its end scrolls into view. */
    fun loadMoreMosaic() {
        val filters = _mosaicResults.value?.first ?: return
        val batch = mosaicNextBatch[filters] ?: return
        if (mosaicMoreJob?.isActive == true) return
        mosaicMoreJob = viewModelScope.launch {
            runCatching { withContext(io) { discoveryRepository.discoverBatch(filters, batch) } }
                .onSuccess { page ->
                    val merged = (mosaicCache[filters].orEmpty() + page.items).distinctBy(MediaCard::stableKey)
                    mosaicCache[filters] = merged
                    // A batch that added nothing new (all duplicates or hidden) still moves on.
                    if (page.hasMore) mosaicNextBatch[filters] = batch + 1 else mosaicNextBatch.remove(filters)
                    if (_mosaicResults.value?.first == filters) {
                        _mosaicResults.value = filters to merged
                        _mosaicCanLoadMore.value = page.hasMore
                    }
                }
        }
    }

    /** Loads the filter page's providers; [preferred] (the user's services) narrows them when set. */
    fun loadStreamingProviders(mediaType: MediaType, preferred: Set<String>) {
        viewModelScope.launch {
            _streamingProviders.value = withContext(io) {
                watchProviderRepository.getWatchProviders(mediaType).let { providers ->
                    if (preferred.isEmpty()) providers else providers.filter { it.name in preferred }
                }
            }
        }
    }

    /** Drops a title the user marked "Not interested" from the filter page and every mosaic view. */
    fun hideResult(media: MediaCard) {
        _discoverFilterResults.value = _discoverFilterResults.value.filterNot { it.stableKey == media.stableKey }
        _mosaicResults.value = _mosaicResults.value?.let { (key, items) -> key to items.filterNot { it.stableKey == media.stableKey } }
        mosaicCache.replaceAll { _, items -> items.filterNot { it.stableKey == media.stableKey } }
    }

    class Factory(
        discoveryRepository: DiscoveryRepository,
        watchProviderRepository: WatchProviderRepository,
    ) : ViewModelProvider.Factory by viewModelFactoryOf({
        DiscoverViewModel(discoveryRepository, watchProviderRepository)
    })
}
