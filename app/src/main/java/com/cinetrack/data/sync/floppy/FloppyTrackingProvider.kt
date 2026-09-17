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
import com.cinetrack.data.sync.floppy.network.FloppyBootstrapTransportContext
import com.cinetrack.data.sync.floppy.network.FloppyBootstrapTransportSession
import com.cinetrack.domain.SyncProgress
import com.cinetrack.domain.FloppyConnectionStage
import java.util.concurrent.atomic.AtomicLong
import java.util.UUID

/** Runtime-configured Floppy transport and provider-neutral mapping adapter. */
class FloppyTrackingProvider(
    private val preferences: AppPreferences? = null,
    private val remote: FloppyRemoteDataSource = FloppyRemoteDataSource(FloppyApiClientFactory()),
    private val syncPass: (suspend (List<SyncOperation>, (SyncProgress) -> Unit) -> ProviderSyncOutcome)? = null,
    private val onDisconnect: (suspend () -> Unit)? = null,
) : TrackingProvider {
    data class ConnectionCandidate(
        val settings: FloppyConnectionSettings,
        val apiKey: String,
    )
    data class ActivationResult(
        val previous: FloppyConnectionSettings?,
        val committed: FloppyConnectionSettings,
        val instanceChanged: Boolean,
        val sessionChanged: Boolean,
    )
    override val id = TrackingProviderId.FLOPPY

    @Volatile private var discovered = FloppyCapabilities()
    @Volatile private var bootstrapReady = false
    private val connectionGeneration = AtomicLong(0)
    override val capabilities: TrackingCapabilities
        get() = discovered.toTrackingCapabilities(bootstrapReady)

    override suspend fun currentDeliveryInstanceId(): String? =
        preferences?.floppySettingsNow()?.connectionId?.takeIf(String::isNotBlank)

    /** Exposed to same-module regression tests; this is transport-session
     * state, deliberately distinct from the persisted provider instance ID. */
    internal fun transportSessionGeneration(): Long = connectionGeneration.get()

    override suspend fun isAuthenticated(): Boolean {
        val settings = preferences?.floppySettingsNow() ?: return false
        val key = preferences.floppyApiKeyNow()
        if (key.isNullOrBlank()) return false
        discovered = settings.capabilities
        bootstrapReady = preferences.providerBootstrapStateNow(TrackingProviderId.FLOPPY) == ProviderBootstrapState.READY
        return true
    }

    /** Performs the staged public-info then authenticated CineTrack probe flow. */
    suspend fun validateConnection(
        baseUrl: String,
        apiKey: String,
        allowInsecureLocalHttp: Boolean = false,
        onStage: (FloppyConnectionStage, String?) -> Unit = { _, _ -> },
    ): Result<ConnectionCandidate> {
        val normalizedUrl = baseUrl.trim()
        val normalizedApiKey = apiKey.trim()
        val previousKey = preferences?.floppyApiKeyNow()
        val resolvedKey = normalizedApiKey.takeIf(String::isNotBlank) ?: previousKey?.trim()
        if (resolvedKey.isNullOrBlank()) return Result.failure(TrackingSyncError.AuthenticationRequired(id))
        return runCatching {
            ConnectionCandidate(remote.connect(normalizedUrl, resolvedKey, allowInsecureLocalHttp, onStage), resolvedKey)
        }.map { it }
    }

    suspend fun activateConnection(settings: FloppyConnectionSettings, apiKey: String) {
        commitValidatedConnectionLocked(settings, apiKey)
    }

    /**
     * Commits a previously validated connection. The caller serializes provider
     * I/O before invoking this method so an immutable bootstrap session cannot
     * observe a partially replaced target.
     */
    internal suspend fun commitValidatedConnectionLocked(
        settings: FloppyConnectionSettings,
        apiKey: String,
    ): ActivationResult {
        val previous = preferences?.floppySettingsNow()
        val previousKey = preferences?.floppyApiKeyNow()
        val sameTarget = previous?.let {
            sameFloppyRemoteTarget(it, settings, previousKey, apiKey)
        } == true
        val resolvedConnectionId = when {
            previous == null -> UUID.randomUUID().toString()
            sameTarget -> previous.connectionId
            else -> UUID.randomUUID().toString()
        }
        val resolvedSettings = settings.copy(connectionId = resolvedConnectionId)
        val sessionChanged = previous != null && (
            previous.baseUrl != resolvedSettings.baseUrl ||
                previous.serverIdentity != resolvedSettings.serverIdentity ||
                previous.accountIdentity != resolvedSettings.accountIdentity ||
                previousKey != apiKey ||
                previous.allowInsecureLocalHttp != resolvedSettings.allowInsecureLocalHttp ||
                previous.connectionId != resolvedSettings.connectionId
            )
        if (sessionChanged) {
            connectionGeneration.incrementAndGet()
        }
        preferences?.setFloppyConnection(resolvedSettings, apiKey)
        if (sessionChanged) previous?.let { remote.invalidateClient(it.baseUrl, previousKey) }
        val committed = preferences?.floppySettingsNow() ?: resolvedSettings
        discovered = committed.capabilities
        bootstrapReady = preferences?.providerBootstrapStateNow(TrackingProviderId.FLOPPY) == ProviderBootstrapState.READY
        return ActivationResult(
            previous = previous,
            committed = committed,
            instanceChanged = previous != null && previous.connectionId != committed.connectionId,
            sessionChanged = sessionChanged,
        )
    }

    suspend fun connect(
        baseUrl: String,
        apiKey: String,
        allowInsecureLocalHttp: Boolean? = null,
        onStage: (FloppyConnectionStage, String?) -> Unit = { _, _ -> },
    ): ConnectionResult =
        validateConnection(baseUrl, apiKey, allowInsecureLocalHttp ?: preferences?.floppyAllowInsecureLocalHttpNow() ?: false, onStage).fold(
            onSuccess = { candidate ->
                activateConnection(candidate.settings, candidate.apiKey)
                ConnectionResult.Connected
            },
            onFailure = { error ->
                val mapped = error as? TrackingSyncError ?: TrackingSyncError.Unknown(error)
                if (mapped is TrackingSyncError.AuthenticationRequired) ConnectionResult.AuthenticationRequired
                else ConnectionResult.Failed(mapped)
            },
        )

    suspend fun disconnect() {
        val previous = preferences?.floppySettingsNow()
        val previousKey = preferences?.floppyApiKeyNow()
        connectionGeneration.incrementAndGet()
        onDisconnect?.invoke()
        previous?.let { remote.invalidateClient(it.baseUrl, previousKey) }
        preferences?.setFloppyConnection(null, null)
        discovered = FloppyCapabilities()
        bootstrapReady = false
    }

    override suspend fun push(operations: List<SyncOperation>): ProviderPushResult {
        val generation = connectionGeneration.get()
        val session = captureSession()
        checkGeneration(generation)
        val result = remote.push(session, operations)
        checkGeneration(generation)
        checkSessionStillCurrent(session)
        return result
    }

    /** Opens the run-scoped transport used exclusively by Floppy bootstrap.
     * The immutable session and expected instance protect retries from ever
     * sending an old plan to a newly activated account. */
    suspend fun openBootstrapSession(expectedInstanceId: String): FloppyBootstrapTransportSession {
        val session = captureSession()
        if (session.instanceId != expectedInstanceId) {
            throw TrackingSyncError.ProviderUnavailable(
                id,
                IllegalStateException("Floppy connection changed before bootstrap started"),
            )
        }
        return FloppyBootstrapTransportSession(
            remote = remote,
            session = session,
            context = FloppyBootstrapTransportContext(
                expectedInstanceId,
                canEnsureEpisodeEvents = session.capabilities.canEnsureEpisodeEvents,
                canBootstrapV2 = session.capabilities.canBootstrapV2,
            ),
            ensureCurrent = { currentDeliveryInstanceId() == expectedInstanceId },
        )
    }

    override suspend fun pullSnapshot(): TrackingSnapshot {
        val generation = connectionGeneration.get()
        val session = captureSession()
        checkGeneration(generation)
        val snapshot = remote.snapshot(session)
        checkGeneration(generation)
        checkSessionStillCurrent(session)
        return snapshot
    }

    suspend fun verificationProjection(): FloppyVerificationProjection {
        val generation = connectionGeneration.get()
        val session = captureSession()
        checkGeneration(generation)
        val projection = remote.verificationProjection(session)
        checkGeneration(generation)
        checkSessionStillCurrent(session)
        return projection
    }

    override suspend fun syncBidirectionally(
        operations: List<SyncOperation>,
        onProgress: (SyncProgress) -> Unit,
    ): ProviderSyncOutcome {
        throw TrackingSyncError.UnsupportedOperation(id, com.cinetrack.data.sync.SyncOperationType.LIBRARY_STATUS)
    }

    override suspend fun testConnection(): ConnectionResult {
        val prefs = preferences ?: return ConnectionResult.AuthenticationRequired
        val settings = prefs.floppySettingsNow() ?: return ConnectionResult.AuthenticationRequired
        val apiKey = prefs.floppyApiKeyNow()?.takeIf(String::isNotBlank)
            ?: return ConnectionResult.AuthenticationRequired
        return runCatching {
            val refreshed = remote.connect(
                settings.baseUrl,
                apiKey,
                settings.allowInsecureLocalHttp,
            )
            val activation = commitValidatedConnectionLocked(refreshed, apiKey)
            discovered = activation.committed.capabilities
            bootstrapReady = prefs.providerBootstrapStateNow(TrackingProviderId.FLOPPY) == ProviderBootstrapState.READY
            ConnectionResult.Connected
        }.getOrElse { error ->
            val mapped = error as? TrackingSyncError ?: TrackingSyncError.Unknown(error)
            if (mapped is TrackingSyncError.AuthenticationRequired) ConnectionResult.AuthenticationRequired
            else ConnectionResult.Failed(mapped)
        }
    }

    private suspend fun captureSession(): FloppySession {
        val prefs = preferences ?: throw TrackingSyncError.AuthenticationRequired(id)
        val settings = prefs.floppySettingsNow() ?: throw TrackingSyncError.AuthenticationRequired(id)
        val apiKey = prefs.floppyApiKeyNow(settings.credentialAlias)?.takeIf(String::isNotBlank)
            ?: throw TrackingSyncError.AuthenticationRequired(id)
        discovered = settings.capabilities
        bootstrapReady = prefs.providerBootstrapStateNow(TrackingProviderId.FLOPPY) == ProviderBootstrapState.READY
        return FloppySession(
            instanceId = settings.connectionId,
            baseUrl = settings.baseUrl,
            accountIdentity = settings.accountIdentity,
            credentialAlias = settings.credentialAlias,
            apiKey = apiKey,
            capabilities = settings.capabilities,
            serverVersion = settings.serverVersion,
            allowInsecureLocalHttp = settings.allowInsecureLocalHttp,
        )
    }

    private suspend fun checkSessionStillCurrent(session: FloppySession) {
        val current = preferences?.floppySettingsNow()
        if (current?.connectionId != session.instanceId) {
            throw TrackingSyncError.ProviderUnavailable(
                id,
                IllegalStateException("Floppy connection changed while the request was in flight"),
            )
        }
    }

    private fun checkGeneration(expected: Long) {
        check(connectionGeneration.get() == expected) {
            "Floppy connection changed before the request could be attributed safely"
        }
    }
}

private fun FloppyCapabilities.toTrackingCapabilities(bootstrapReady: Boolean) = TrackingCapabilities(
    supportsMovies = canReadLibrary || canWriteLibrary || canReadHistory,
    supportsShows = canReadLibrary || canWriteLibrary || canReadHistory,
    supportsWatchHistory = canReadHistory || canWriteMovieHistory || canWriteEpisodeHistory,
    supportsRatings = false,
    supportsLibrary = canReadLibrary || canWriteLibrary,
    supportsTwoWaySync = false,
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
