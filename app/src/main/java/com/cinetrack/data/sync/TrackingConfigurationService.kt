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
            // Reset stale readiness before committing a re-add. If the process
            // dies before the DataStore write, the old configuration remains and
            // the provider is conservatively still not promotable.
            newlyAddedSecondary?.let {
                preferences.setProviderBootstrapState(it, ProviderBootstrapState.NOT_STARTED)
            }
            // Persist first. If the process dies before cancellation, startup
            // repair sees the committed configuration and finishes the transition.
            preferences.setTrackingProviders(next.mainProvider, next.secondaryProvider)
            removed.forEach { operations.cancelProviderDeliveries(it) }
            val pending = operations.pending()
            operations.completeReady(pending)
        }
        return next
    }

    suspend fun bootstrapState(provider: TrackingProviderId): ProviderBootstrapState =
        preferences.providerBootstrapStateNow(provider)

    suspend fun setBootstrapState(provider: TrackingProviderId, state: ProviderBootstrapState) =
        routingMutex.withLock { preferences.setProviderBootstrapState(provider, state) }

    suspend fun repair(configuration: TrackingConfiguration? = null) = routingMutex.withLock {
        val resolved = configuration ?: current()
        val configured = setOfNotNull(resolved.mainProvider, resolved.secondaryProvider)
        TrackingProviderId.entries.filterNot(configured::contains).forEach {
            operations.cancelProviderDeliveries(it)
        }
        operations.completeReady(operations.pending())
    }
}

