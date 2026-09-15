package com.cinetrack.data.sync.floppy

import com.cinetrack.data.repository.AppPreferences
import com.cinetrack.data.sync.ConnectionResult
import com.cinetrack.data.sync.ProviderBootstrapState
import com.cinetrack.data.sync.SyncCoordinator
import com.cinetrack.data.sync.TrackingConfigurationService
import com.cinetrack.data.sync.TrackingSyncError
import com.cinetrack.domain.FloppyConnectionStage
import kotlinx.coroutines.CancellationException

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
        onStage: (FloppyConnectionStage, String?) -> Unit = { _, _ -> },
    ): ConnectionResult {
        val allowHttp = allowInsecureLocalHttp ?: preferences.floppyAllowInsecureLocalHttpNow()
        onStage(FloppyConnectionStage.CHECKING_SERVER, null)
        val candidate = provider.validateConnection(baseUrl.trim(), apiKey.trim(), allowHttp, onStage)
            .getOrElse { error ->
                return if (error is com.cinetrack.data.sync.TrackingSyncError.AuthenticationRequired) {
                    ConnectionResult.AuthenticationRequired
                } else ConnectionResult.Failed(error as? com.cinetrack.data.sync.TrackingSyncError
                    ?: com.cinetrack.data.sync.TrackingSyncError.Unknown(error))
        }
        var activated = false
        return try {
            coordinator.withProviderIoQuiesced {
            val activation = configuration.withRoutingLock {
                onStage(FloppyConnectionStage.ACTIVATING, candidate.settings.serverVersion)
                val result = provider.commitValidatedConnectionLocked(candidate.settings, candidate.apiKey)
                activated = true
                // This repair is intentionally ordered after persisting B. It
                // is idempotent and also covers a crash between those steps.
                preferences.floppySettingsNow()?.connectionId?.let { instance ->
                    configurationRepair(instance)
                }
                if (result.instanceChanged) {
                    bootstrap().resetForInstanceChange(result.committed.connectionId)
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
                onStage(FloppyConnectionStage.COMPLETE, candidate.settings.serverVersion)
                return@withProviderIoQuiesced ConnectionResult.Connected
            }
            onStage(FloppyConnectionStage.INITIAL_SYNC, candidate.settings.serverVersion)
            when (preferences.providerBootstrapStateNow(com.cinetrack.data.sync.TrackingProviderId.FLOPPY)) {
                ProviderBootstrapState.NOT_STARTED,
                ProviderBootstrapState.RUNNING,
                ProviderBootstrapState.FAILED,
                -> bootstrap().start()
                ProviderBootstrapState.READY -> Unit
            }
            val deliveryResult = coordinator.pushPendingWhileProviderIoQuiesced()
            if (deliveryResult.isFailure) {
                // Keep the validated connection and durable plan. A failed
                // first delivery is actionable, but it is still retryable
                // without reconnecting or regenerating bootstrap operations.
                preferences.setProviderBootstrapState(
                    com.cinetrack.data.sync.TrackingProviderId.FLOPPY,
                    ProviderBootstrapState.FAILED,
                )
                val error = deliveryResult.exceptionOrNull()
                val mapped = error as? com.cinetrack.data.sync.TrackingSyncError
                    ?: com.cinetrack.data.sync.TrackingSyncError.Unknown(error ?: IllegalStateException("Floppy delivery failed"))
                return@withProviderIoQuiesced ConnectionResult.Failed(mapped)
            }
            bootstrap().markReadyIfComplete()
            onStage(FloppyConnectionStage.COMPLETE, candidate.settings.serverVersion)
            ConnectionResult.Connected
            }
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            // Bootstrap construction/queue failures happen after the
            // connection metadata is committed. Preserve that connection and
            // surface a retryable NEEDS_ATTENTION state instead of letting the
            // UI coroutine fail silently.
            preferences.setProviderBootstrapState(
                com.cinetrack.data.sync.TrackingProviderId.FLOPPY,
                ProviderBootstrapState.FAILED,
            )
            val mapped = error as? TrackingSyncError ?: TrackingSyncError.Unknown(error)
            ConnectionResult.Failed(if (activated && mapped !is TrackingSyncError.BootstrapFailure) TrackingSyncError.BootstrapFailure(mapped) else mapped)
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

