package com.cinetrack.ui.screens

import androidx.compose.runtime.Composable
import com.cinetrack.domain.AppUiState
import com.cinetrack.ui.CineTrackViewModel

/** Dedicated tracking settings entry point; layout remains shared during migration. */
@Composable
fun TrackingSettingsScreen(
    state: AppUiState,
    viewModel: CineTrackViewModel,
    onConnect: () -> Unit,
) {
    SyncSettingsHost(state, viewModel, onConnect)
}

