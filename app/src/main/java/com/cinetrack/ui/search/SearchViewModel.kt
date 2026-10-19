package com.cinetrack.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cinetrack.data.discovery.DiscoveryRepository
import com.cinetrack.data.people.PeopleRepository
import com.cinetrack.domain.MediaCard
import com.cinetrack.domain.PersonCard
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The recent searches the Search screen shows and edits. */
interface SearchHistoryStore {
    val searchHistory: Flow<List<String>>
    suspend fun addSearchHistory(query: String)
    suspend fun clearSearchHistory()
    suspend fun removeSearchHistory(query: String)
}

/**
 * Title and people search plus the recent-search list. Activity-scoped, because the
 * details route looks up a title opened from the results in [searchResults].
 */
class SearchViewModel(
    private val discoveryRepository: DiscoveryRepository,
    private val peopleRepository: PeopleRepository,
    private val history: SearchHistoryStore,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
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
    val searchHistory: StateFlow<List<String>> = history.searchHistory
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        observeSearch()
    }

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
                        val results = withContext(io) { discoveryRepository.search(query) }
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
                        val results = withContext(io) { peopleRepository.search(query) }
                        if (personSearchQuery.value == query) {
                            _personSearchResults.value = results
                            _personSearchLoading.value = false
                        }
                    }
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
        if (normalized.isNotBlank()) viewModelScope.launch { history.addSearchHistory(normalized) }
    }

    fun clearSearchHistory() {
        viewModelScope.launch { history.clearSearchHistory() }
    }

    fun removeSearchHistory(query: String) {
        viewModelScope.launch { history.removeSearchHistory(query) }
    }

    /** Drops a title the user marked "Not interested" from the current results. */
    fun hideResult(media: MediaCard) {
        _searchResults.value = _searchResults.value.filterNot { it.stableKey == media.stableKey }
    }

    fun clearSearch() {
        searchQuery.value = ""
        _searchResults.value = emptyList()
        _searchLoading.value = false
        personSearchQuery.value = ""
        _personSearchResults.value = emptyList()
        _personSearchLoading.value = false
    }

    class Factory(
        private val discoveryRepository: DiscoveryRepository,
        private val peopleRepository: PeopleRepository,
        private val history: SearchHistoryStore,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SearchViewModel(discoveryRepository, peopleRepository, history) as T
    }
}
