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
    val traktConfigured: Boolean,
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
    traktConfigured = traktConfigured,
)

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
