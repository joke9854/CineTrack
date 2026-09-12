package com.cinetrack.data.sync.simkl

import com.cinetrack.data.sync.ProviderSyncOutcome
import com.cinetrack.data.sync.SyncOperation
import com.cinetrack.domain.SyncProgress

/** Focused orchestration contract; provider transport never depends on the UI façade. */
fun interface SimklSyncOrchestrator {
    suspend fun syncSimklProvider(
        operations: List<SyncOperation>,
        onProgress: (SyncProgress) -> Unit,
    ): Result<ProviderSyncOutcome>
}

/** Dedicated boundary for the proven Simkl full-sync algorithm during migration. */
class SimklSyncEngine(
    private var orchestrator: SimklSyncOrchestrator? = null,
) {
    fun bind(orchestrator: SimklSyncOrchestrator) {
        check(this.orchestrator == null) { "Simkl sync engine is already bound" }
        this.orchestrator = orchestrator
    }

    suspend fun sync(operations: List<SyncOperation>, onProgress: (SyncProgress) -> Unit): ProviderSyncOutcome =
        (orchestrator ?: error("Simkl sync engine is not initialized"))
            .syncSimklProvider(operations, onProgress)
            .getOrThrow()
}

