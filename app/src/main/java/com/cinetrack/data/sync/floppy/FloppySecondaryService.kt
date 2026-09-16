package com.cinetrack.data.sync.floppy

import android.content.Context
import com.cinetrack.data.repository.AppPreferences
import com.cinetrack.data.sync.ConnectionResult
import com.cinetrack.data.sync.ProviderBootstrapState
import com.cinetrack.data.sync.SyncCoordinator
import com.cinetrack.data.sync.TrackingConfigurationService
import com.cinetrack.data.sync.TrackingSyncError
import com.cinetrack.data.sync.TrackingProviderId
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
    private val context: Context? = null,
    private val wifiOnly: suspend () -> Boolean = { false },
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
            val transition = coordinator.withProviderIoQuiesced {
            configuration.withRoutingLock {
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
                result to configuration.current()
            }
            }
            val activation = transition.first
            val routing = transition.second
            activation.previous?.connectionId
                ?.takeIf { activation.instanceChanged }
                ?.let { context?.let { appContext -> FloppyBootstrapWorkScheduler.cancel(appContext, it) } }
            if (routing.mainProvider == null) {
                onStage(FloppyConnectionStage.COMPLETE, candidate.settings.serverVersion)
                return ConnectionResult.Connected
            }
            onStage(FloppyConnectionStage.INITIAL_SYNC, candidate.settings.serverVersion)
            val bootstrapState = preferences.providerBootstrapStateNow(TrackingProviderId.FLOPPY)
            val instance = activation.committed.connectionId
            if (context == null) {
                // Source-compatible path for lightweight Room integration
                // tests that do not provide an Android WorkManager context.
                val plan = when (bootstrapState) {
                    ProviderBootstrapState.NOT_STARTED,
                    ProviderBootstrapState.RUNNING,
                    ProviderBootstrapState.FAILED,
                    -> {
                        bootstrap().start()
                        bootstrap().ensurePlan()
                    }
                    ProviderBootstrapState.READY -> emptyList()
                }
                // The compatibility path has no WorkManager owner, so it
                // dispatches the persisted bootstrap ids explicitly in the
                // same bounded batches as FloppyBootstrapWorker. Generic
                // pushPending() intentionally excludes these managed rows.
                plan.map { it.id }.chunked(15).forEach { batch ->
                    val delivery = coordinator.pushPendingForProvider(
                        TrackingProviderId.FLOPPY,
                        batch.toSet(),
                        instance,
                    )
                    if (delivery.isFailure) throw delivery.exceptionOrNull() ?: IllegalStateException("Floppy delivery failed")
                }
                if (!bootstrap().markReadyIfComplete()) throw TrackingSyncError.BootstrapFailure(IllegalStateException("Bootstrap verification failed"))
            } else if (bootstrapState != ProviderBootstrapState.READY) {
                // Plan creation is a bounded local/DataStore operation. Do
                // not materialize every delivery row on the interactive
                // settings coroutine; WorkManager owns that long operation.
                bootstrap().ensurePlan()
                context.let { appContext ->
                    FloppyBootstrapWorkScheduler.enqueue(appContext, instance, wifiOnly())
                }
            }
            onStage(FloppyConnectionStage.COMPLETE, candidate.settings.serverVersion)
            ConnectionResult.Connected
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

