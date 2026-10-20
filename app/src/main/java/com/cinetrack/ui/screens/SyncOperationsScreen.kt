package com.cinetrack.ui.screens

import androidx.compose.runtime.Composable
import com.cinetrack.ui.settings.SyncSettingsViewModel
import com.cinetrack.ui.settings.TrackingSyncControls

/** Dedicated synchronization operations entry point for future provider actions. */
@Composable
fun SyncOperationsScreen(viewModel: SyncSettingsViewModel, controls: TrackingSyncControls) {
    SyncOperationsSettings(viewModel, controls)
}
