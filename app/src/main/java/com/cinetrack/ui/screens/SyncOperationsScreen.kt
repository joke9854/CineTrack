package com.cinetrack.ui.screens

import androidx.compose.runtime.Composable
import com.cinetrack.ui.CineTrackViewModel

/** Dedicated synchronization operations entry point for future provider actions. */
@Composable
fun SyncOperationsScreen(viewModel: CineTrackViewModel) {
    SyncOperationsSettings(viewModel)
}

