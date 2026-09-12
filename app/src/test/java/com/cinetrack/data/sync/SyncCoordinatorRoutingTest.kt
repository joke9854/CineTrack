package com.cinetrack.data.sync

import com.cinetrack.domain.MediaType
import com.cinetrack.domain.SyncOperationCard
import com.cinetrack.domain.SyncProgress
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Production-mode routing regressions: persisted rows, not current roles, select targets. */
class SyncCoordinatorRoutingTest {
    @Test
    fun `missing exact delivery is not inferred for a newly selected main`() = runTest {
        val operation = operation()
        val queue = PersistedQueue(operation).apply {
            rows += delivery(TrackingProviderId.SIMKL, TrackingRole.MAIN)
        }
        val simkl = RecordingProvider(TrackingProviderId.SIMKL)
        val floppy = RecordingProvider(TrackingProviderId.FLOPPY)
        val registry = MutableRoutingRegistry(simkl, floppy).apply {
            current = TrackingConfiguration(TrackingProviderId.FLOPPY, TrackingProviderId.SIMKL)
        }

        assertTrue(SyncCoordinator(registry, queue).pushPending().isSuccess)

        assertEquals(0, floppy.pushes)
        assertEquals(1, simkl.pushes)
        assertEquals(listOf(TrackingProviderId.SIMKL), queue.acknowledgedProviders)
    }

    @Test
    fun `main acknowledgement stays with provider that started the pass after role switch`() = runTest {
        val operation = operation()
        val queue = PersistedQueue(operation).apply {
            rows += delivery(TrackingProviderId.SIMKL, TrackingRole.MAIN)
        }
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val simkl = RecordingProvider(TrackingProviderId.SIMKL).apply {
            beforeSync = {
                started.complete(Unit)
                release.await()
            }
        }
        val floppy = RecordingProvider(TrackingProviderId.FLOPPY)
        val registry = MutableRoutingRegistry(simkl, floppy)
        val coordinator = SyncCoordinator(registry, queue)

        val running = async { coordinator.sync {} }
        started.await()
        registry.current = TrackingConfiguration(TrackingProviderId.FLOPPY, TrackingProviderId.SIMKL)
        release.complete(Unit)

        assertTrue(running.await().isSuccess)
        assertEquals(listOf(TrackingProviderId.SIMKL), queue.acknowledgedProviders)
        assertEquals(0, floppy.pushes)
    }

    @Test
    fun `role swap preserves persisted provider targets`() = runTest {
        val operation = operation()
        val queue = PersistedQueue(operation).apply {
            rows += delivery(TrackingProviderId.SIMKL, TrackingRole.MAIN)
            rows += delivery(TrackingProviderId.FLOPPY, TrackingRole.SECONDARY)
        }
        val simkl = RecordingProvider(TrackingProviderId.SIMKL)
        val floppy = RecordingProvider(TrackingProviderId.FLOPPY)
        val registry = MutableRoutingRegistry(simkl, floppy).apply {
            current = TrackingConfiguration(TrackingProviderId.FLOPPY, TrackingProviderId.SIMKL)
        }

        assertTrue(SyncCoordinator(registry, queue).pushPending().isSuccess)

        assertEquals(setOf(TrackingProviderId.SIMKL, TrackingProviderId.FLOPPY), queue.rows.map { it.providerId }.toSet())
        assertEquals(setOf(TrackingRole.MAIN, TrackingRole.SECONDARY), queue.rows.map { it.roleAtEnqueue }.toSet())
    }

    @Test
    fun `removed provider is cancelled and re-adding it does not revive the operation`() = runTest {
        val operation = operation()
        val queue = PersistedQueue(operation).apply {
            rows += delivery(TrackingProviderId.SIMKL, TrackingRole.MAIN).copy(status = DeliveryStatus.ACKNOWLEDGED)
            rows += delivery(TrackingProviderId.FLOPPY, TrackingRole.SECONDARY).copy(status = DeliveryStatus.FAILED)
        }

        queue.cancelProviderDeliveries(TrackingProviderId.FLOPPY, "Provider removed")
        queue.completeReady(queue.pending())

        assertEquals(DeliveryStatus.CANCELLED_PROVIDER_REMOVED, queue.rows.single { it.providerId == TrackingProviderId.FLOPPY }.status)
        assertTrue(queue.pending().isEmpty())
    }

    private fun operation() = SyncOperation(
        id = "state:MOVIE:42",
        type = SyncOperationType.LIBRARY_STATUS,
        mediaType = MediaType.MOVIE,
        mediaId = 42,
        title = "Example",
        value = "WATCHING",
        sourceVersion = 1L,
    )

    private fun delivery(provider: TrackingProviderId, role: TrackingRole) = SyncOperationDelivery(
        operationId = "state:MOVIE:42",
        operationVersion = 1L,
        providerId = provider,
        roleAtEnqueue = role,
    )
}

private class MutableRoutingRegistry(
    private val simkl: TrackingProvider,
    private val floppy: TrackingProvider,
) : TrackingProviderRegistry {
    var current = TrackingConfiguration(TrackingProviderId.SIMKL, TrackingProviderId.FLOPPY)

    override fun getProvider(id: TrackingProviderId): TrackingProvider = when (id) {
        TrackingProviderId.SIMKL -> simkl
        TrackingProviderId.FLOPPY -> floppy
    }

    override suspend fun configuration(): TrackingConfiguration = current
}

private class RecordingProvider(override val id: TrackingProviderId) : TrackingProvider {
    override val capabilities = TrackingCapabilities()
    var pushes = 0
    var beforeSync: suspend () -> Unit = {}

    override suspend fun isAuthenticated(): Boolean = true

    override suspend fun push(operations: List<SyncOperation>): ProviderPushResult {
        pushes++
        return ProviderPushResult(operations.mapTo(linkedSetOf(), SyncOperation::id))
    }

    override suspend fun syncBidirectionally(
        operations: List<SyncOperation>,
        onProgress: (SyncProgress) -> Unit,
    ): ProviderSyncOutcome {
        beforeSync()
        return ProviderSyncOutcome(
            itemsChanged = false,
            acknowledgedOperationIds = operations.mapTo(linkedSetOf(), SyncOperation::id),
        )
    }

    override suspend fun testConnection(): ConnectionResult = ConnectionResult.Connected
}

private class PersistedQueue(vararg initial: SyncOperation) : SyncOperationRepository {
    override val requiresPersistedDeliveryRows = true
    private val values = initial.toMutableList()
    val rows = mutableListOf<SyncOperationDelivery>()
    val acknowledgedProviders = mutableListOf<TrackingProviderId>()

    override suspend fun pending(operationIds: Set<String>?): List<SyncOperation> =
        values.filter { operationIds == null || it.id in operationIds }

    override suspend fun complete(operations: List<SyncOperation>) {
        values.removeAll(operations.toSet())
    }

    override suspend fun fail(operations: List<SyncOperation>, error: Throwable) = Unit

    override suspend fun cards(): List<SyncOperationCard> = emptyList()

    override suspend fun deliveries(operationIds: Set<String>): List<SyncOperationDelivery> =
        rows.filter { it.operationId in operationIds }

    override suspend fun acknowledge(provider: TrackingProviderId, operations: List<SyncOperation>) {
        acknowledgedProviders += provider
        operations.forEach { operation ->
            rows.replaceStatus(provider, operation, DeliveryStatus.ACKNOWLEDGED)
        }
    }

    override suspend fun failDelivery(provider: TrackingProviderId, operations: List<SyncOperation>, error: Throwable) {
        operations.forEach { operation -> rows.replaceStatus(provider, operation, DeliveryStatus.FAILED) }
    }

    override suspend fun completeReady(operations: List<SyncOperation>) {
        val ready = operations.filter { operation ->
            rows.filter { it.operationId == operation.id && it.operationVersion == operation.sourceVersion && it.required }
                .all { it.status == DeliveryStatus.ACKNOWLEDGED }
        }
        values.removeAll(ready.toSet())
    }

    override suspend fun cancelProviderDeliveries(provider: TrackingProviderId, reason: String) {
        rows.indices.forEach { index ->
            val row = rows[index]
            if (row.providerId == provider && row.status in setOf(DeliveryStatus.PENDING, DeliveryStatus.FAILED)) {
                rows[index] = row.copy(status = DeliveryStatus.CANCELLED_PROVIDER_REMOVED, lastError = reason)
            }
        }
    }
}

private fun MutableList<SyncOperationDelivery>.replaceStatus(
    provider: TrackingProviderId,
    operation: SyncOperation,
    status: DeliveryStatus,
) {
    val index = indexOfFirst {
        it.providerId == provider && it.operationId == operation.id && it.operationVersion == operation.sourceVersion
    }
    if (index >= 0) this[index] = this[index].copy(status = status)
}

