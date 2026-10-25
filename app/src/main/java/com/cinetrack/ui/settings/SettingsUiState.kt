package com.cinetrack.ui.settings

import android.content.Context
import androidx.compose.runtime.Immutable
import com.cinetrack.domain.AppUiState
import com.cinetrack.domain.EpisodeCard
import com.cinetrack.domain.FloppyUiState
import com.cinetrack.domain.SyncReport

/** The part of AppUiState the Settings list reads, so other changes don't recompose it. */
@Immutable
data class SettingsUiState(
    val uiAccent: String,
    val discoverLayout: String,
    val syncReport: SyncReport,
    val simklConnected: Boolean,
    val simklUsername: String?,
    val simklReconnectReason: String?,
    val floppyConnected: Boolean,
    val floppyUiState: FloppyUiState,
    val floppyUsername: String?,
    val tmdbApiConfigured: Boolean,
    val mdbListApiConfigured: Boolean,
    val libraryTitles: Int = 0,
    val episodesWatched: Int = 0,
    val moviesWatched: Int = 0,
    val tvmazeEnabled: Boolean = true,
    val fanartConfigured: Boolean = false,
    val tvdbConfigured: Boolean = false,
    /** Newest first: the posters behind the Profile card's banner. */
    val recentPosters: List<String> = emptyList(),
    val history: List<com.cinetrack.domain.TimelineCard> = emptyList(),
    val library: List<com.cinetrack.domain.MediaCard> = emptyList(),
    val metadataTimezone: String = "system",
)

fun AppUiState.settingsUiState() = SettingsUiState(
    uiAccent = uiAccent,
    discoverLayout = discoverLayout,
    syncReport = sync.report,
    simklConnected = simklConnected,
    simklUsername = simklUsername,
    simklReconnectReason = simklReconnectReason,
    floppyConnected = floppyConnected,
    floppyUiState = floppyUiState,
    floppyUsername = floppyUsername,
    tmdbApiConfigured = tmdbApiConfigured,
    mdbListApiConfigured = mdbListApiConfigured,
    libraryTitles = rails[com.cinetrack.domain.RailIds.LIBRARY].orEmpty().size,
    episodesWatched = history.count { it.season != null },
    moviesWatched = history.asSequence().filter { it.media.type == com.cinetrack.domain.MediaType.MOVIE }.map { it.media.id }.distinct().count(),
    tvmazeEnabled = tvmazeEnabled,
    fanartConfigured = fanartConfigured,
    tvdbConfigured = tvdbConfigured,
    recentPosters = recentPosters(),
    history = history,
    library = rails[com.cinetrack.domain.RailIds.LIBRARY].orEmpty(),
    metadataTimezone = metadataTimezone,
)

/** The five most recent distinct posters (history is newest first), else the library's. */
fun AppUiState.recentPosters(count: Int = 5): List<String> =
    (history.asSequence().map { it.media } + rails[com.cinetrack.domain.RailIds.LIBRARY].orEmpty().asSequence())
        .mapNotNull { it.posterUrl?.takeIf(String::isNotBlank) }.distinct().take(count).toList()

/**
 * What the Notifications and Content pages still take from CineTrackViewModel: the specials
 * switch, restoring hidden titles and the calendar export update its AppUiState and Progress.
 */
class HiddenContentControls(
    val onExcludeSpecials: (Boolean) -> Unit,
    val onRestoreHiddenUpcomingEpisode: (EpisodeCard) -> Unit,
    val onRestoreHiddenUpcoming: () -> Unit,
    val onRestoreHiddenDiscovery: () -> Unit,
    val onExportCalendar: (Context) -> Unit,
)
