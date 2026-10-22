package com.cinetrack.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cinetrack.domain.LibraryArtworkRefreshProgress
import com.cinetrack.domain.LibraryArtworkRefreshStage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.cinetrack.ui.viewModelFactoryOf

/** The background artwork refresh: WorkManager progress, the enqueue and the Wi-Fi-only preference. */
interface LibraryArtworkStore {
    val progress: Flow<LibraryArtworkRefreshProgress?>
    suspend fun wifiOnly(): Boolean
    fun enqueue(wifiOnly: Boolean)
}

data class LibraryArtworkState(
    val refreshing: Boolean = false,
    val progress: LibraryArtworkRefreshProgress? = null,
    val progressVisible: Boolean = false,
)

/**
 * The Library page's artwork refresh and its progress pill. Activity-scoped, because the
 * pill shows on every screen. The library rows themselves still come from AppUiState.
 */
class LibraryViewModel(
    private val store: LibraryArtworkStore,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    private val _artwork = MutableStateFlow(LibraryArtworkState())
    val artwork: StateFlow<LibraryArtworkState> = _artwork.asStateFlow()

    init {
        viewModelScope.launch {
            store.progress.collect { progress ->
                _artwork.update { it.copy(progress = progress, refreshing = false) }
            }
        }
    }

    /** Refresh all cached library metadata/artwork without touching tracking data. */
    fun refreshArtwork(tmdbConfigured: Boolean) {
        if (!tmdbConfigured) return
        _artwork.value = LibraryArtworkState(
            refreshing = true,
            progress = LibraryArtworkRefreshProgress(LibraryArtworkRefreshStage.PREPARING),
            progressVisible = true,
        )
        viewModelScope.launch {
            try {
                withContext(io) { store.enqueue(store.wifiOnly()) }
                _artwork.update { it.copy(refreshing = false) }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _artwork.update { it.copy(refreshing = false) }
                error.message?.let(_errors::tryEmit)
            }
        }
    }

    fun dismissProgress() {
        _artwork.update { it.copy(progressVisible = false) }
    }

    class Factory(store: LibraryArtworkStore) : ViewModelProvider.Factory by viewModelFactoryOf({
        LibraryViewModel(store)
    })
}
