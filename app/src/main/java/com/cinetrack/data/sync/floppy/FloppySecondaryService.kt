package com.cinetrack.data.sync.floppy

import com.cinetrack.data.repository.AppPreferences
import com.cinetrack.data.sync.ConnectionResult
import com.cinetrack.data.sync.ProviderBootstrapState
import com.cinetrack.data.sync.SyncCoordinator
import com.cinetrack.data.sync.TrackingConfigurationService

/**
 * The single orchestration boundary for a Floppy SECONDARY connection.
 * Transport validation happens before the provider-I/O lock; activation,
 * role selection, bootstrap and queue dispatch are one serialized transition.
 */
class FloppySecondaryService(
    private val provider: FloppyTrackingProvider,
    private val preferences: AppPreferences,
    private val configuration: TrackingConfigurationService,
    private val coordinator: SyncCoordinator,
    private val bootstrap: () -> FloppyBootstrapCoordinator,
) {
    suspend fun connect(
        baseUrl: String,
        apiKey: String,
        allowInsecureLocalHttp: Boolean? = null,
    ): ConnectionResult {
        val allowHttp = allowInsecureLocalHttp ?: preferences.floppyAllowInsecureLocalHttpNow()
        val candidate = provider.validateConnection(baseUrl, apiKey, allowHttp)
            .getOrElse { error ->
                return if (error is com.cinetrack.data.sync.TrackingSyncError.AuthenticationRequired) {
                    ConnectionResult.AuthenticationRequired
                } else ConnectionResult.Failed(error as? com.cinetrack.data.sync.TrackingSyncError
                    ?: com.cinetrack.data.sync.TrackingSyncError.Unknown(error))
            }
        return coordinator.withProviderIoQuiesced {
            provider.activateConnection(candidate.settings, candidate.apiKey)
            val current = configuration.current()
            if (current.mainProvider == null) {
                // A connected provider is not falsely presented as an active
                // SECONDARY when there is no MAIN authority.
                return@withProviderIoQuiesced ConnectionResult.Connected
            }
            if (current.secondaryProvider != com.cinetrack.data.sync.TrackingProviderId.FLOPPY) {
                configuration.setProviders(current.mainProvider, com.cinetrack.data.sync.TrackingProviderId.FLOPPY)
            }
            when (preferences.providerBootstrapStateNow(com.cinetrack.data.sync.TrackingProviderId.FLOPPY)) {
                ProviderBootstrapState.NOT_STARTED,
                ProviderBootstrapState.RUNNING,
                -> bootstrap().start()
                ProviderBootstrapState.FAILED -> Unit
                ProviderBootstrapState.READY -> Unit
            }
            coordinator.pushPending()
            bootstrap().markReadyIfComplete()
            ConnectionResult.Connected
        }
    }

    suspend fun disconnect() = coordinator.withProviderIoQuiesced {
        provider.disconnect()
    }
}

