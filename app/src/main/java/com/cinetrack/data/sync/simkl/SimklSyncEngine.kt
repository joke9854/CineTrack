package com.cinetrack.data.sync.simkl

import com.cinetrack.data.sync.ProviderSyncOutcome
import com.cinetrack.data.sync.SyncOperation
import com.cinetrack.domain.SyncProgress

/** Dedicated boundary for the proven Simkl full-sync algorithm during migration. */
class SimklSyncEngine(
    private var implementation: (suspend (List<SyncOperation>, (SyncProgress) -> Unit) -> ProviderSyncOutcome)? = null,
) {
    fun bind(implementation: suspend (List<SyncOperation>, (SyncProgress) -> Unit) -> ProviderSyncOutcome) {
        check(this.implementation == null) { "Simkl sync engine is already bound" }
        this.implementation = implementation
    }

    suspend fun sync(operations: List<SyncOperation>, onProgress: (SyncProgress) -> Unit): ProviderSyncOutcome =
        (implementation ?: error("Simkl sync engine is not initialized"))(operations, onProgress)
}

