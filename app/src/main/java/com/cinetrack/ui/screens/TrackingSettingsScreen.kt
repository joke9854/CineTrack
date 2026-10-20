package com.cinetrack.ui.screens

import androidx.compose.runtime.Composable
import com.cinetrack.domain.AppUiState
import com.cinetrack.ui.settings.SyncSettingsViewModel
import com.cinetrack.ui.settings.TrackingSyncControls

/** Dedicated tracking settings entry point; layout remains shared during migration. */
@Composable
fun TrackingSettingsScreen(
    state: AppUiState,
    viewModel: SyncSettingsViewModel,
    controls: TrackingSyncControls,
    onConnect: () -> Unit,
) {
    SyncSettingsHost(state, viewModel, controls, onConnect)
}
