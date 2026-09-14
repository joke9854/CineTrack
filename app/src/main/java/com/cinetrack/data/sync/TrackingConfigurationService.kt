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
    suspend fun current(): TrackingConfiguration = TrackingConfiguration.normalized(
        preferences.mainTrackingProvider.first(),
        preferences.secondaryTrackingProvider.first(),
    )

    suspend fun setProviders(main: TrackingProviderId?, secondary: TrackingProviderId?): TrackingConfiguration {
        val next = TrackingConfiguration(main, secondary)
        routingMutex.withLock {
            val previous = current()
            if (next.mainProvider == TrackingProviderId.FLOPPY) {
                require(preferences.providerBootstrapStateNow(TrackingProviderId.FLOPPY) == ProviderBootstrapState.READY) {
                    "Floppy must complete bootstrap before it can become MAIN"
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
        }
        return next
    }

    suspend fun bootstrapState(provider: TrackingProviderId): ProviderBootstrapState =
        preferences.providerBootstrapStateNow(provider)

    suspend fun setBootstrapState(provider: TrackingProviderId, state: ProviderBootstrapState) =
        routingMutex.withLock { preferences.setProviderBootstrapState(provider, state) }

    /** Removes a provider from roles before its credentials are discarded. */
    suspend fun removeProvider(provider: TrackingProviderId) {
        val current = current()
        val nextMain = current.mainProvider.takeUnless { it == provider }
        val nextSecondary = current.secondaryProvider.takeUnless { it == provider }
        if (nextMain != current.mainProvider || nextSecondary != current.secondaryProvider) {
            setProviders(nextMain, nextSecondary)
        }
    }

    suspend fun repair(configuration: TrackingConfiguration? = null) = routingMutex.withLock {
        val resolved = configuration ?: current()
        val configured = setOfNotNull(resolved.mainProvider, resolved.secondaryProvider)
        // Bind first so a process dying after the MAIN DataStore write cannot
        // strand current local intents without an immutable target row.
        resolved.mainProvider?.let { operations.bindUnboundCurrentIntents(it) }
        TrackingProviderId.entries.filterNot(configured::contains).forEach {
            operations.cancelProviderDeliveries(it)
        }
        operations.completeReady(operations.pending())
    }
}
