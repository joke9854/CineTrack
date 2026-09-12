package com.cinetrack.data.sync.simkl

import com.cinetrack.data.sync.ProviderSyncOutcome
import com.cinetrack.data.sync.SyncOperation
import com.cinetrack.domain.SyncProgress

/**
 * Dedicated entry point for the Simkl full-sync orchestration. The callable is
 * supplied at construction time, so the provider has no mutable bind step and
 * cannot observe a partially initialized application façade.
 */
class SimklSyncEngine(
    private val syncBlock: suspend (
        operations: List<SyncOperation>,
        onProgress: (SyncProgress) -> Unit,
    ) -> Result<ProviderSyncOutcome>,
) {
    suspend fun sync(operations: List<SyncOperation>, onProgress: (SyncProgress) -> Unit): ProviderSyncOutcome =
        syncBlock(operations, onProgress).getOrThrow()
}

