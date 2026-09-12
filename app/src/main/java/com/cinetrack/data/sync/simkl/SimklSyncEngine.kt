package com.cinetrack.data.sync.simkl

import com.cinetrack.data.sync.ProviderSyncOutcome
import com.cinetrack.data.sync.SyncOperation
import com.cinetrack.domain.SyncProgress

/** Dedicated boundary for the proven Simkl full-sync algorithm during migration. */
class SimklSyncEngine(
    private val implementation: suspend (List<SyncOperation>, (SyncProgress) -> Unit) -> ProviderSyncOutcome,
) {
    suspend fun sync(operations: List<SyncOperation>, onProgress: (SyncProgress) -> Unit): ProviderSyncOutcome =
        implementation(operations, onProgress)
}

