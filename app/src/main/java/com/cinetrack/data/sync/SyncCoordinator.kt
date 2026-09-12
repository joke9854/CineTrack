package com.cinetrack.data.sync

import com.cinetrack.domain.SyncProgress
import kotlinx.coroutines.CancellationException

/** Owns provider selection and direction. Providers own only transport/mapping. */
class SyncCoordinator(
    private val registry: TrackingProviderRegistry,
    private val operations: SyncOperationRepository,
    private val reconciler: SyncReconciler = SyncReconciler(),
) {
    /** Exposes the pure policy for provider adapters and deterministic tests. */
    fun reconcile(
        local: LocalTrackingSnapshot,
        remote: TrackingSnapshot,
        provider: TrackingProviderId,
    ): ReconciliationResult = reconciler.reconcile(local, remote, provider)

    suspend fun sync(onProgress: (SyncProgress) -> Unit): Result<SyncCoordinatorOutcome> = resultOf {
        val configuration = registry.configuration()
        val mainId = configuration.mainProvider
            ?: throw IllegalStateException("Select a MAIN tracking provider")
        val main = registry.getProvider(mainId)
            ?: throw IllegalStateException("The MAIN tracking provider is unavailable")
        requireAuthenticated(main)
        if (!main.capabilities.supportsTwoWaySync) {
            throw TrackingSyncError.ProviderUnavailable(main.id, IllegalStateException("MAIN provider cannot reconcile snapshots yet"))
        }

        // Capture once. A provider must never expose its DTOs to the queue or UI.
        val pending = operations.pending()
        try {
            val mainUnsupported = pending.firstOrNull { !main.capabilities.supports(it) }
            if (mainUnsupported != null) throw TrackingSyncError.UnsupportedOperation(main.id, mainUnsupported.type)

            // Direction is structural: SECONDARY has no pull/sync call anywhere here.
            // Deliver first because the legacy Simkl MAIN pass also clears its
            // acknowledged queue rows; a secondary failure must leave them retryable.
            val secondary = configuration.secondaryProvider?.let(registry::getProvider)
            if (secondary != null && pending.isNotEmpty()) {
                requireAuthenticated(secondary)
                pushTo(secondary, pending)
            }

            val outcome = main.syncBidirectionally(pending, onProgress)
            operations.complete(pending)
            SyncCoordinatorOutcome(outcome.itemsChanged, outcome.report)
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            operations.fail(pending, error)
            throw error
        }
    }

    suspend fun pushPending(operationIds: Set<String>? = null): Result<Unit> = resultOf {
        val pending = operations.pending(operationIds)
        if (pending.isEmpty()) return@resultOf Unit
        val configuration = registry.configuration()
        val targets = listOfNotNull(
            configuration.mainProvider?.let(registry::getProvider),
            configuration.secondaryProvider?.let(registry::getProvider),
        ).distinctBy(TrackingProvider::id)
        if (targets.isEmpty()) throw IllegalStateException("No tracking provider is configured")
        try {
            targets.forEach { provider ->
                requireAuthenticated(provider)
                pushTo(provider, pending)
            }
            operations.complete(pending)
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            operations.fail(pending, error)
            throw error
        }
    }

    suspend fun retry(operationId: String): Result<Unit> = pushPending(setOf(operationId))

    suspend fun isMainProviderConnected(): Boolean {
        val provider = registry.getMainProvider() ?: return false
        return provider.isAuthenticated()
    }

    private suspend fun pushTo(provider: TrackingProvider, pending: List<SyncOperation>) {
        val unsupported = pending.firstOrNull { !provider.capabilities.supports(it) }
        if (unsupported != null) throw TrackingSyncError.UnsupportedOperation(provider.id, unsupported.type)
        val completed = provider.push(pending).completedOperationIds
        check(completed.containsAll(pending.map(SyncOperation::id))) {
            "${provider.id.name} did not acknowledge every synchronization operation"
        }
    }

    private suspend fun requireAuthenticated(provider: TrackingProvider) {
        if (!provider.isAuthenticated()) throw TrackingSyncError.AuthenticationRequired(provider.id)
    }
}

private suspend inline fun <T> resultOf(crossinline block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Throwable) {
    Result.failure(error)
}

