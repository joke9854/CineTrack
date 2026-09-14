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
            val activation = configuration.withRoutingLock {
                val result = provider.commitValidatedConnectionLocked(candidate.settings, candidate.apiKey)
                // This repair is intentionally ordered after persisting B. It
                // is idempotent and also covers a crash between those steps.
                preferences.floppySettingsNow()?.connectionId?.let { instance ->
                    configurationRepair(instance)
                }
                if (result.identityChanged) {
                    bootstrap().resetForInstanceChange(candidate.settings.connectionId)
                }
                val current = configuration.current()
                if (current.mainProvider != null && current.secondaryProvider != com.cinetrack.data.sync.TrackingProviderId.FLOPPY) {
                    configuration.setProvidersLocked(current.mainProvider, com.cinetrack.data.sync.TrackingProviderId.FLOPPY)
                }
                current
            }
            if (activation.mainProvider == null) {
                // A connected provider is not falsely presented as an active
                // SECONDARY when there is no MAIN authority.
                return@withProviderIoQuiesced ConnectionResult.Connected
            }
            when (preferences.providerBootstrapStateNow(com.cinetrack.data.sync.TrackingProviderId.FLOPPY)) {
                ProviderBootstrapState.NOT_STARTED,
                ProviderBootstrapState.RUNNING,
                -> bootstrap().start()
                ProviderBootstrapState.FAILED -> Unit
                ProviderBootstrapState.READY -> Unit
            }
            coordinator.pushPendingWhileProviderIoQuiesced()
            bootstrap().markReadyIfComplete()
            ConnectionResult.Connected
        }
    }

    private suspend fun configurationRepair(instanceId: String) {
        // The service owns the provider-I/O quiescence and routing lock while
        // invoking this helper; repository repair never retargets rows.
        configuration.repairProviderInstanceTargets(instanceId)
    }

    suspend fun disconnect() = coordinator.withProviderIoQuiesced {
        provider.disconnect()
    }
}

