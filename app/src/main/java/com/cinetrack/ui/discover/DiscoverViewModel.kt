package com.cinetrack.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cinetrack.data.discovery.DiscoveryRepository
import com.cinetrack.data.watchprovider.WatchProviderRepository
import com.cinetrack.domain.DiscoverMovieFilters
import com.cinetrack.domain.MediaCard
import com.cinetrack.domain.MediaType
import com.cinetrack.domain.StreamingProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The Discover filter page, the Mosaic layout's chip results and the streaming
 * providers the filter page offers. The rails and their "see all" pages stay in
 * CineTrackViewModel, because they are built from the library state. Activity-scoped,
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
        private val discoveryRepository: DiscoveryRepository,
        private val watchProviderRepository: WatchProviderRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            DiscoverViewModel(discoveryRepository, watchProviderRepository) as T
    }
}
