package com.cinetrack.data.sync

import com.cinetrack.data.repository.AppPreferences
import kotlinx.coroutines.flow.first

/**
 * Coordinates provider-role changes across DataStore and the durable Room
 * queue. DataStore and Room cannot share a transaction, so every transition is
 * deliberately idempotent and safe to repeat after process death.
 */
class TrackingConfigurationService(
    private val preferences: AppPreferences,
    private val operations: SyncOperationRepository,
    private val routingMutex: TrackingRoutingMutex = TrackingRoutingMutex(),
) {
    /** Executes a routing-critical block without reacquiring the mutex. */
    internal suspend fun <T> withRoutingLock(block: suspend () -> T): T = routingMutex.withLock(block)

    /** Caller must hold the routing mutex when used during activation. */
    internal suspend fun repairProviderInstanceTargets(instanceId: String) =
        operations.repairProviderInstanceTargets(TrackingProviderId.FLOPPY, instanceId)

    suspend fun current(): TrackingConfiguration = TrackingConfiguration.normalized(
        preferences.mainTrackingProvider.first(),
        preferences.secondaryTrackingProvider.first(),
    )

    suspend fun setProviders(main: TrackingProviderId?, secondary: TrackingProviderId?): TrackingConfiguration {
        return routingMutex.withLock { setProvidersLocked(main, secondary) }
    }

    /**
     * Applies a role transition while the caller already owns the shared
     * TrackingRoutingMutex.  This is intentionally non-locking so provider
     * instance activation can persist identity, repair deliveries, and update
     * roles in one uninterrupted routing critical section.
     */
    internal suspend fun setProvidersLocked(main: TrackingProviderId?, secondary: TrackingProviderId?): TrackingConfiguration {
        val next = TrackingConfiguration(main, secondary)
            val previous = current()
            if (next.mainProvider == TrackingProviderId.FLOPPY && previous.mainProvider != TrackingProviderId.FLOPPY) {
                // Discovered from /info at connection time, never from the version.
                check(preferences.floppySettingsNow()?.capabilities?.canSyncItems == true) {
                    "This Floppy server can't be the main tracker yet. Update Floppy, then reconnect it."
                }
            }
            val promotedBootstrapState = if (
                previous.mainProvider != null &&
                next.mainProvider != null &&
                previous.mainProvider != next.mainProvider
            ) {
                preferences.providerBootstrapStateNow(next.mainProvider)
            } else {
                null
            }
            validateTrackingConfigurationTransition(
                previous = previous,
                next = next,
                promotedBootstrapState = promotedBootstrapState,
            )
            val removed = (setOfNotNull(previous.mainProvider, previous.secondaryProvider) -
                setOfNotNull(next.mainProvider, next.secondaryProvider))
            val newlyAddedSecondary = next.secondaryProvider?.takeIf {
                it !in setOfNotNull(previous.mainProvider, previous.secondaryProvider)
            }
            val demotedFormerMain = previous.mainProvider?.takeIf {
                it != next.mainProvider && it == next.secondaryProvider
            }
            // Reset stale readiness before committing a re-add. If the process
            // dies before the DataStore write, the old configuration remains and
            // the provider is conservatively still not promotable.
            newlyAddedSecondary?.let {
                preferences.setProviderBootstrapState(it, ProviderBootstrapState.NOT_STARTED)
            }
            demotedFormerMain?.let {
                // A provider that has successfully served as MAIN is already
                // bootstrapped and remains eligible for a later promotion. Do
                // this before the role write so an interrupted swap leaves the
                // old configuration valid and READY.
                preferences.setProviderBootstrapState(it, ProviderBootstrapState.READY)
            }
            // Persist after readiness. If the process dies here, startup repair
            // sees either the old valid configuration or the new one and
            // completes the remaining idempotent queue work.
            preferences.setTrackingProviders(next.mainProvider, next.secondaryProvider)
            removed.forEach { operations.cancelProviderDeliveries(it) }
            next.mainProvider?.let { operations.bindUnboundCurrentIntents(it) }
            val pending = operations.pending()
            operations.completeReady(pending)
        return next
    }

    suspend fun bootstrapState(provider: TrackingProviderId): ProviderBootstrapState =
        preferences.providerBootstrapStateNow(provider)

    /**
     * Removes a provider from roles before its credentials are discarded. When
     * MAIN goes, a bootstrapped SECONDARY takes over as MAIN; otherwise sync
     * stops until the user picks a provider again.
     */
    suspend fun removeProvider(provider: TrackingProviderId) = routingMutex.withLock {
        val current = current()
        if (provider != current.mainProvider && provider != current.secondaryProvider) return@withLock current
        val next = when (provider) {
            current.secondaryProvider -> TrackingConfiguration(current.mainProvider, null)
            else -> current.secondaryProvider
                ?.takeIf { preferences.providerBootstrapStateNow(it) == ProviderBootstrapState.READY }
                ?.let { TrackingConfiguration(it, null) }
                ?: TrackingConfiguration(null, null)
        }
        setProvidersLocked(next.mainProvider, next.secondaryProvider)
    }

    suspend fun repair(configuration: TrackingConfiguration? = null) = routingMutex.withLock {
        // Floppy may be MAIN only while it is connected to a server with the
        // exact two-way read side. Otherwise hand MAIN to the SECONDARY (or
        // stop) before startup repair, so it can never be authoritative blind.
        if (preferences.mainTrackingProvider.first() == TrackingProviderId.FLOPPY &&
            preferences.floppySettingsNow()?.capabilities?.canSyncItems != true
        ) {
            val secondary = preferences.secondaryTrackingProvider.first()
                ?.takeIf { it != TrackingProviderId.FLOPPY && preferences.providerBootstrapStateNow(it) == ProviderBootstrapState.READY }
            preferences.setTrackingProviders(secondary, null)
            operations.cancelProviderDeliveries(TrackingProviderId.FLOPPY, "Floppy can no longer be the main tracker")
        }
        val requested = configuration ?: current()
        val resolved = TrackingConfiguration.normalized(requested.mainProvider, requested.secondaryProvider)
        val configured = setOfNotNull(resolved.mainProvider, resolved.secondaryProvider)
        // Bind first so a process dying after the MAIN DataStore write cannot
        // strand current local intents without an immutable target row.
        resolved.mainProvider?.let { operations.bindUnboundCurrentIntents(it) }
        if (resolved.secondaryProvider == TrackingProviderId.FLOPPY) {
            preferences.floppySettingsNow()?.connectionId?.takeIf(String::isNotBlank)?.let { instance ->
                operations.repairProviderInstanceTargets(TrackingProviderId.FLOPPY, instance)
            }
        }
        TrackingProviderId.entries.filterNot(configured::contains).forEach {
            operations.cancelProviderDeliveries(it)
        }
        operations.completeReady(operations.pending())
    }
}

