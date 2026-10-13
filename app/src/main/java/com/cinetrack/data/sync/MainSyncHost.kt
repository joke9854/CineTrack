package com.cinetrack.data.sync

import com.cinetrack.data.local.AppDatabase
import com.cinetrack.data.repository.AppPreferences
import com.cinetrack.data.repository.ProgressRefreshRequest

/** Local-database services every MAIN provider's two-way pass needs. */
interface MainSyncHost {
    val database: AppDatabase
    val preferences: AppPreferences
    val syncOperationRepository: SyncOperationRepository

    suspend fun repairSyncQueue()
    suspend fun refreshProgressCache(
        request: ProgressRefreshRequest = ProgressRefreshRequest(force = true),
        onProgress: ((Float) -> Unit)? = null,
    ): Boolean
    /** Persists a remote-only MAIN mutation as a SECONDARY-only mirror. */
    suspend fun enqueueSecondaryMirror(
        operation: SyncOperation,
        mainProvider: TrackingProviderId = TrackingProviderId.SIMKL,
    ) {}
    /** Validates that the provider which started this pass is still MAIN. */
    suspend fun isMainProviderCurrent(provider: TrackingProviderId): Boolean = true
    /** Executes a commit only while the provider remains the authoritative MAIN. */
    suspend fun <T> withMainAuthority(
        provider: TrackingProviderId,
        block: suspend () -> T,
    ): T? = if (isMainProviderCurrent(provider)) block() else null
    /** Allocates a fresh CineTrack causal generation from persisted state. */
    suspend fun nextCausalGeneration(): Long = MutationGeneration.next()
    suspend fun rebuildLibraryRailInTransaction()
}
