package com.cinetrack.ui.progress

import androidx.compose.runtime.Immutable
import com.cinetrack.domain.AppUiState
import com.cinetrack.domain.EpisodeCard
import com.cinetrack.domain.MediaCard
import com.cinetrack.domain.PlaybackCard
import com.cinetrack.domain.TimelineCard
import com.cinetrack.domain.allMediaOf

/** The part of AppUiState the Progress screen reads, so other changes don't recompose it. */
@Immutable
data class ProgressUiState(
    val rails: Map<String, List<MediaCard>>,
    val playbackTv: List<PlaybackCard>,
    val playbackMovies: List<PlaybackCard>,
    val history: List<TimelineCard>,
    val calendar: List<TimelineCard>,
    val episodes: List<EpisodeCard>,
    val simklConnected: Boolean,
    val metadataTimezone: String,
    val excludeSpecials: Boolean,
    val hiddenUpcoming: Set<String>,
) {
    /** Same as AppUiState.allMedia. */
    val allMedia: List<MediaCard> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        allMediaOf(rails, playbackTv, playbackMovies, history, calendar)
    }
}

fun AppUiState.progressUiState() = ProgressUiState(
    rails = rails,
    playbackTv = playbackTv,
    playbackMovies = playbackMovies,
    history = history,
    calendar = calendar,
    episodes = episodes,
    simklConnected = simklConnected,
    metadataTimezone = metadataTimezone,
    excludeSpecials = excludeSpecials,
    hiddenUpcoming = hiddenUpcoming,
)
