package com.cinetrack.data.sync.floppy

import com.cinetrack.data.sync.ConnectionResult
import com.cinetrack.data.sync.ProviderPushResult
import com.cinetrack.data.sync.ProviderSyncOutcome
import com.cinetrack.data.sync.SyncOperation
import com.cinetrack.data.sync.TrackingCapabilities
import com.cinetrack.data.sync.TrackingProvider
import com.cinetrack.data.sync.TrackingProviderId
import com.cinetrack.data.sync.TrackingSyncError
import com.cinetrack.data.sync.TrackingCapability
import com.cinetrack.domain.SyncProgress

/** Registration-ready boundary. Network/authentication are intentionally not implemented yet. */
class FloppyTrackingProvider : TrackingProvider {
    override val id = TrackingProviderId.FLOPPY
    override val capabilities = TrackingCapabilities(
        supportsMovies = false,
        supportsShows = false,
        supportsWatchHistory = false,
        supportsLibrary = false,
        supportsTwoWaySync = false,
        supported = emptySet<TrackingCapability>(),
    )

    override suspend fun isAuthenticated(): Boolean = false
    override suspend fun push(operations: List<SyncOperation>): ProviderPushResult =
        throw TrackingSyncError.AuthenticationRequired(id)

    override suspend fun syncBidirectionally(
        operations: List<SyncOperation>,
        onProgress: (SyncProgress) -> Unit,
    ): ProviderSyncOutcome = throw TrackingSyncError.AuthenticationRequired(id)

    override suspend fun testConnection(): ConnectionResult = ConnectionResult.AuthenticationRequired
}

