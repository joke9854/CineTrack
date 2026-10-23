package com.cinetrack.ui.discover

import androidx.compose.runtime.Immutable
import com.cinetrack.domain.AppUiState
import com.cinetrack.domain.MediaCard
import com.cinetrack.domain.PlaybackCard
import com.cinetrack.domain.TimelineCard
import com.cinetrack.domain.allMediaOf
import com.cinetrack.domain.discoverBrowseKey

/** The part of AppUiState Discover and its "see all" pages read, so other changes don't recompose them. */
@Immutable
data class DiscoverUiState(
    val loading: Boolean,
    val refreshing: Boolean,
    val error: String?,
    val rails: Map<String, List<MediaCard>>,
    val playbackTv: List<PlaybackCard>,
    val playbackMovies: List<PlaybackCard>,
    val history: List<TimelineCard>,
    val calendar: List<TimelineCard>,
    val tmdbApiConfigured: Boolean,
    val metadataLanguage: String,
    val metadataRegion: String,
    val contentRegions: Set<String>,
    val discoverLayout: String,
    val cardDensity: String,
    val hiddenDiscovery: Set<String>,
) {
    /** Same as AppUiState.allMedia. */
    val allMedia: List<MediaCard> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        allMediaOf(rails, playbackTv, playbackMovies, history, calendar)
    }
}

fun AppUiState.discoverUiState() = DiscoverUiState(
    loading = loading,
    refreshing = refreshing,
    error = error,
    rails = rails,
    playbackTv = playbackTv,
    playbackMovies = playbackMovies,
    history = history,
    calendar = calendar,
    tmdbApiConfigured = tmdbApiConfigured,
    metadataLanguage = metadataLanguage,
    metadataRegion = metadataRegion,
    contentRegions = contentRegions,
    discoverLayout = discoverLayout,
    cardDensity = cardDensity,
    hiddenDiscovery = hiddenDiscovery,
)

fun discoverBrowseKey(railId: String, state: DiscoverUiState): String =
    discoverBrowseKey(railId, state.metadataLanguage, state.metadataRegion, state.contentRegions)
