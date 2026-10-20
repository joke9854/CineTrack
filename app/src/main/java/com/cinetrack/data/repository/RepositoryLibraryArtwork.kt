package com.cinetrack.data.repository

import com.cinetrack.data.work.LibraryArtworkRefreshManager
import com.cinetrack.ui.library.LibraryArtworkStore
import kotlinx.coroutines.flow.first

class RepositoryLibraryArtwork(
    private val manager: LibraryArtworkRefreshManager,
    private val preferences: AppPreferences,
) : LibraryArtworkStore {
    override val progress = manager.progress
    override suspend fun wifiOnly() = preferences.wifiOnly.first()
    override fun enqueue(wifiOnly: Boolean) {
        manager.enqueue(wifiOnly)
    }
}
