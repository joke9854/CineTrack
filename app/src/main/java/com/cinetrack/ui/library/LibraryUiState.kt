package com.cinetrack.ui.library

import androidx.compose.runtime.Immutable
import com.cinetrack.domain.AppUiState
import com.cinetrack.domain.MediaCard
import com.cinetrack.domain.PlaybackCard
import com.cinetrack.domain.RailIds
import com.cinetrack.domain.TimelineCard

/** The part of AppUiState the Library screen reads, so other changes don't recompose it. */
@Immutable
data class LibraryUiState(
    val library: List<MediaCard>,
    val playbackTv: List<PlaybackCard>,
    val playbackMovies: List<PlaybackCard>,
    val history: List<TimelineCard>,
    val cardDensity: String,
    val tmdbApiConfigured: Boolean,
)

fun AppUiState.libraryUiState() = LibraryUiState(
    library = rails[RailIds.LIBRARY].orEmpty(),
    playbackTv = playbackTv,
    playbackMovies = playbackMovies,
    history = history,
    cardDensity = cardDensity,
    tmdbApiConfigured = tmdbApiConfigured,
)
