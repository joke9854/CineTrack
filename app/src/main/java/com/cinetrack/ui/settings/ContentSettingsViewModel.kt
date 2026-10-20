package com.cinetrack.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cinetrack.data.remote.TmdbContentFilter
import com.cinetrack.domain.StreamingProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** What the TMDB, MDBList and Streaming pages show, as AppPreferences stores it. */
data class ContentSettings(
    val metadataLanguage: String = "system",
    val metadataRegion: String = "system",
    val metadataTimezone: String = "system",
    val airDateSource: String = com.cinetrack.data.schedule.AirDateSource.DEFAULT,
    val hideAdultTitles: Boolean = true,
    val ratingSources: Set<String> = setOf("imdb", "tmdb", "metacritic", "tomatoes"),
    val contentRegions: Set<String> = emptySet(),
    val providerRegion: String = "system",
    val preferredProviders: Set<String> = emptySet(),
    val visibleProviderTypes: Set<String> = setOf("flatrate", "rent", "buy", "free", "ads"),
)

/**
 * A finished content-setting write that CineTrackViewModel still has to act on: its detail
 * caches, the progress cache and Discover are built from these settings.
 */
enum class ContentChange { Providers, Ratings, Language, Region, AirDates, Timezone, ContentRegions, TmdbKey, MdbListKey, TraktClientId }

/** The writes behind the content pages. The metadata ones do more than save a preference. */
interface ContentSettingsStore {
    val contentSettings: Flow<ContentSettings>
    suspend fun setRatingSource(key: String, enabled: Boolean)
    suspend fun setPreferredProviders(values: Set<String>)
    suspend fun setProviderRegion(value: String)
    suspend fun setVisibleProviderTypes(values: Set<String>)
    suspend fun setHideAdultTitles(enabled: Boolean)
    suspend fun setContentRegions(regions: Set<String>)
    suspend fun setMetadataLanguage(value: String)
    suspend fun setMetadataRegion(value: String)
    suspend fun setMetadataTimezone(value: String)
    suspend fun setAirDateSource(value: String)
    suspend fun availableProviders(region: String): List<StreamingProvider>
    /** Each checks the credential against its service and saves it only when it works. */
    suspend fun verifyAndSetTmdbApiKey(value: String): Result<Unit>
    suspend fun verifyAndSetMdbListApiKey(value: String): Result<Unit>
    suspend fun verifyAndSetTraktClientId(value: String): Result<Unit>
}

/** TMDB metadata, content regions, rating sources, streaming providers and the TMDB, MDBList and Trakt credentials. Activity-scoped. */
class ContentSettingsViewModel(
    private val store: ContentSettingsStore,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    val content: StateFlow<ContentSettings> = store.contentSettings
        .stateIn(viewModelScope, SharingStarted.Eagerly, ContentSettings())

    private val _changes = MutableSharedFlow<ContentChange>(extraBufferCapacity = 8)
    /** Emitted after the write has finished, so a refresh reads the new settings. */
    val changes: SharedFlow<ContentChange> = _changes.asSharedFlow()

    private val _providers = MutableStateFlow<List<StreamingProvider>>(emptyList())
    val providers: StateFlow<List<StreamingProvider>> = _providers.asStateFlow()
    private val _providersLoading = MutableStateFlow(false)
    val providersLoading: StateFlow<Boolean> = _providersLoading.asStateFlow()
    private val _providersError = MutableStateFlow(false)
    val providersError: StateFlow<Boolean> = _providersError.asStateFlow()
    private var providersJob: Job? = null

    // One provider write at a time, so a toggle never works from a stale selection.
    private val providerMutex = Mutex()

    fun loadStreamingProviders(region: String) {
        providersJob?.cancel()
        _providersLoading.value = true
        _providersError.value = false
        _providers.value = emptyList()
        providersJob = viewModelScope.launch {
            try {
                _providers.value = withContext(io) { withTimeout(30_000) { store.availableProviders(region) } }
            } catch (timeout: kotlinx.coroutines.TimeoutCancellationException) {
                _providersError.value = true
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _providersError.value = true
            } finally {
                if (kotlinx.coroutines.currentCoroutineContext().isActive) _providersLoading.value = false
            }
        }
    }

    fun setPreferredProviders(values: Set<String>) = providerWrite { store.setPreferredProviders(values) }

    fun togglePreferredProvider(name: String) = providerWrite {
        val selected = store.contentSettings.first().preferredProviders
        store.setPreferredProviders(if (name in selected) selected - name else selected + name)
    }

    fun setProviderRegion(value: String) = providerWrite { store.setProviderRegion(value) }

    fun setVisibleProviderTypes(values: Set<String>) = providerWrite { store.setVisibleProviderTypes(values) }

    fun toggleVisibleProviderType(key: String) = providerWrite {
        val visible = store.contentSettings.first().visibleProviderTypes
        store.setVisibleProviderTypes(if (key in visible) visible - key else visible + key)
    }

    fun setRatingSource(source: String, enabled: Boolean) {
        val key = when (source.lowercase()) {
            "rotten tomatoes", "r.tomatoes", "tomatoes" -> "tomatoes"
            else -> source.lowercase()
        }
        write(ContentChange.Ratings) { store.setRatingSource(key, enabled) }
    }

    fun setHideAdultTitles(enabled: Boolean) {
        TmdbContentFilter.hideAdult = enabled
        write(null) { store.setHideAdultTitles(enabled) }
    }

    fun setContentRegions(regions: Set<String>) = write(ContentChange.ContentRegions) { store.setContentRegions(regions) }
    fun setMetadataLanguage(value: String) = write(ContentChange.Language) { store.setMetadataLanguage(value) }
    fun setMetadataRegion(value: String) = write(ContentChange.Region) { store.setMetadataRegion(value) }
    fun setMetadataTimezone(value: String) = write(ContentChange.Timezone) { store.setMetadataTimezone(value) }
    fun setAirDateSource(value: String) = write(ContentChange.AirDates) { withContext(io) { store.setAirDateSource(value) } }

    fun verifyAndSetTmdbApiKey(value: String, onResult: (Result<Unit>) -> Unit) =
        verify(ContentChange.TmdbKey, onResult) { store.verifyAndSetTmdbApiKey(value) }
    fun verifyAndSetMdbListApiKey(value: String, onResult: (Result<Unit>) -> Unit) =
        verify(ContentChange.MdbListKey, onResult) { store.verifyAndSetMdbListApiKey(value) }
    fun verifyAndSetTraktClientId(value: String, onResult: (Result<Unit>) -> Unit) =
        verify(ContentChange.TraktClientId, onResult) { store.verifyAndSetTraktClientId(value) }

    private fun verify(change: ContentChange, onResult: (Result<Unit>) -> Unit, check: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            val result = withContext(io) { check() }
            if (result.isSuccess) _changes.emit(change)
            onResult(result)
        }
    }

    private fun providerWrite(block: suspend () -> Unit) = write(ContentChange.Providers) { providerMutex.withLock { block() } }

    private fun write(change: ContentChange?, block: suspend () -> Unit) {
        viewModelScope.launch {
            block()
            change?.let { _changes.emit(it) }
        }
    }

    class Factory(private val store: ContentSettingsStore) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ContentSettingsViewModel(store) as T
    }
}
