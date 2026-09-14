package com.cinetrack.data.sync.floppy

import com.cinetrack.data.repository.AppPreferences
import com.cinetrack.data.sync.ConnectionResult
import com.cinetrack.data.sync.ProviderPushResult
import com.cinetrack.data.sync.ProviderSyncOutcome
import com.cinetrack.data.sync.ProviderBootstrapState
import com.cinetrack.data.sync.SyncOperation
import com.cinetrack.data.sync.TrackingCapabilities
import com.cinetrack.data.sync.TrackingCapability
import com.cinetrack.data.sync.TrackingProvider
import com.cinetrack.data.sync.TrackingProviderId
import com.cinetrack.data.sync.TrackingSnapshot
import com.cinetrack.data.sync.TrackingSyncError
import com.cinetrack.data.sync.floppy.network.FloppyApiClientFactory
import com.cinetrack.data.sync.floppy.network.FloppyRemoteDataSource
import com.cinetrack.domain.SyncProgress

/** Runtime-configured Floppy transport and provider-neutral mapping adapter. */
class FloppyTrackingProvider(
    private val preferences: AppPreferences? = null,
    private val remote: FloppyRemoteDataSource = FloppyRemoteDataSource(FloppyApiClientFactory()),
    private val syncPass: (suspend (List<SyncOperation>, (SyncProgress) -> Unit) -> ProviderSyncOutcome)? = null,
    private val onDisconnect: (suspend () -> Unit)? = null,
    private val onIdentityChanged: (suspend () -> Unit)? = null,
) : TrackingProvider {
    override val id = TrackingProviderId.FLOPPY

    @Volatile private var discovered = FloppyCapabilities()
    @Volatile private var bootstrapReady = false
    override val capabilities: TrackingCapabilities
        get() = discovered.toTrackingCapabilities(bootstrapReady)

    override suspend fun isAuthenticated(): Boolean {
        val settings = preferences?.floppySettingsNow() ?: return false
        val key = preferences.floppyApiKeyNow()
        if (key.isNullOrBlank()) return false
        discovered = settings.capabilities
        bootstrapReady = preferences.providerBootstrapStateNow(TrackingProviderId.FLOPPY) == ProviderBootstrapState.READY
        return true
    }

    /** Performs the staged public-info then authenticated-preferences flow. */
    suspend fun connect(baseUrl: String, apiKey: String): ConnectionResult {
        if (apiKey.isBlank()) return ConnectionResult.AuthenticationRequired
        return runCatching { remote.connect(baseUrl.trim(), apiKey) }.fold(
            onSuccess = { settings ->
                val previous = preferences?.floppySettingsNow()
                if (previous != null && (previous.serverIdentity != settings.serverIdentity || previous.accountIdentity != settings.accountIdentity)) {
                    onIdentityChanged?.invoke()
                }
                preferences?.setFloppyConnection(settings, apiKey)
                discovered = settings.capabilities
                bootstrapReady = preferences?.providerBootstrapStateNow(TrackingProviderId.FLOPPY) == ProviderBootstrapState.READY
                ConnectionResult.Connected
            },
            onFailure = { error ->
                val mapped = error as? TrackingSyncError ?: TrackingSyncError.Unknown(error)
                if (mapped is TrackingSyncError.AuthenticationRequired) ConnectionResult.AuthenticationRequired
                else ConnectionResult.Failed(mapped)
            },
        )
    }

    suspend fun disconnect() {
        onDisconnect?.invoke()
        preferences?.setFloppyConnection(null, null)
        discovered = FloppyCapabilities()
        bootstrapReady = false
    }

    override suspend fun push(operations: List<SyncOperation>): ProviderPushResult {
        val settings = requireSettings()
        val key = preferences?.floppyApiKeyNow() ?: throw TrackingSyncError.AuthenticationRequired(id)
        // Never query Room here: the coordinator's immutable delivery set is
        // the complete authority for this provider pass.
        return remote.push(settings.baseUrl, key, operations)
    }

    override suspend fun pullSnapshot(): TrackingSnapshot {
        val settings = requireSettings()
        val key = preferences?.floppyApiKeyNow() ?: throw TrackingSyncError.AuthenticationRequired(id)
        return remote.snapshot(settings.baseUrl, key)
    }

    override suspend fun syncBidirectionally(
        operations: List<SyncOperation>,
        onProgress: (SyncProgress) -> Unit,
    ): ProviderSyncOutcome {
        requireSettings()
        syncPass?.let { return it(operations, onProgress) }
        val pushed = push(operations)
        onProgress(SyncProgress(running = true, stage = com.cinetrack.domain.SyncStage.PROCESSING, message = "Fetching Floppy changes"))
        pullSnapshot()
        return ProviderSyncOutcome(itemsChanged = false, acknowledgedOperationIds = pushed.completedOperationIds)
    }

    override suspend fun testConnection(): ConnectionResult {
        val settings = preferences?.floppySettingsNow() ?: return ConnectionResult.AuthenticationRequired
        return remote.test(settings.baseUrl, preferences.floppyApiKeyNow()).also {
            if (it is ConnectionResult.Connected) {
                discovered = settings.capabilities
                bootstrapReady = preferences.providerBootstrapStateNow(TrackingProviderId.FLOPPY) == ProviderBootstrapState.READY
            }
        }
    }

    private suspend fun requireSettings(): FloppyConnectionSettings {
        val settings = preferences?.floppySettingsNow() ?: throw TrackingSyncError.AuthenticationRequired(id)
        if (preferences.floppyApiKeyNow().isNullOrBlank()) throw TrackingSyncError.AuthenticationRequired(id)
        discovered = settings.capabilities
        bootstrapReady = preferences.providerBootstrapStateNow(TrackingProviderId.FLOPPY) == ProviderBootstrapState.READY
        return settings
    }
}

private fun FloppyCapabilities.toTrackingCapabilities(bootstrapReady: Boolean) = TrackingCapabilities(
    supportsMovies = canReadLibrary || canWriteLibrary || canReadHistory,
    supportsShows = canReadLibrary || canWriteLibrary || canReadHistory,
    supportsWatchHistory = canReadHistory || canWriteMovieHistory || canWriteEpisodeHistory,
    supportsRatings = false,
    supportsLibrary = canReadLibrary || canWriteLibrary,
    supportsTwoWaySync = bootstrapReady && canReadCompleteSnapshot && canReadLibrary && canReadHistory,
    supported = buildSet {
        if (canReadLibrary) add(TrackingCapability.PULL_LIBRARY)
        if (canWriteLibrary) add(TrackingCapability.PUSH_LIBRARY)
        if (canReadHistory) {
            add(TrackingCapability.PULL_MOVIE_HISTORY)
            add(TrackingCapability.PULL_EPISODE_HISTORY)
        }
        if (canWriteMovieHistory) add(TrackingCapability.PUSH_MOVIE_HISTORY)
        if (canWriteEpisodeHistory) add(TrackingCapability.PUSH_EPISODE_HISTORY)
        if (canRemoveHistory) add(TrackingCapability.REMOVE_REMOTE_STATE)
        if (canReadCompleteSnapshot) {
            add(TrackingCapability.TIMESTAMPS)
            add(TrackingCapability.FULL_HISTORY)
        }
    },
)
