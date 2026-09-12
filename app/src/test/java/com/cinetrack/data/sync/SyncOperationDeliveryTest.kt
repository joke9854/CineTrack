package com.cinetrack.data.sync

import com.cinetrack.domain.MediaType
import com.cinetrack.domain.SyncOperationCard
import com.cinetrack.domain.SyncProgress
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Exercises the durable per-provider state machine rather than no-op test hooks. */
class SyncOperationDeliveryTest {
    @Test
    fun `secondary failure is retried without resending acknowledged main`() = runTest {
        val op = operation(1L)
        val queue = DeliveryAwareRepository(op)
        val main = DeliveryProvider(TrackingProviderId.SIMKL)
        val secondary = DeliveryProvider(TrackingProviderId.FLOPPY).apply { fail = true }
        val registry = DeliveryRegistry(main, secondary)
        val coordinator = SyncCoordinator(registry, queue)

        assertTrue(coordinator.pushPending().isFailure)
        assertEquals(1, main.calls)
        assertEquals(1, secondary.calls)
        assertEquals(DeliveryStatus.ACKNOWLEDGED, queue.rows.single { it.providerId == TrackingProviderId.SIMKL }.status)

        secondary.fail = false
        assertTrue(coordinator.pushPending().isSuccess)
        assertEquals(1, main.calls)
        assertEquals(2, secondary.calls)
        assertTrue(queue.values.isEmpty())
    }

    @Test
    fun `fresh generation receives a fresh pending delivery`() = runTest {
        val first = operation(10L)
        val queue = DeliveryAwareRepository(first)
        val main = DeliveryProvider(TrackingProviderId.SIMKL)
        val coordinator = SyncCoordinator(DeliveryRegistry(main), queue)
        assertTrue(coordinator.pushPending().isSuccess)

        val second = first.copy(value = "DROPPED", sourceVersion = 11L)
        queue.values += second
        queue.rows.removeAll { it.operationId == first.id }
        assertTrue(coordinator.pushPending().isSuccess)
        assertEquals(listOf(first.id, second.id), main.sentIds)
    }

    private fun operation(version: Long) = SyncOperation("state:MOVIE:42", SyncOperationType.LIBRARY_STATUS, MediaType.MOVIE, 42, "Example", "WATCHING", sourceVersion = version)
}

private class DeliveryAwareRepository(vararg initial: SyncOperation) : SyncOperationRepository {
    val values = initial.toMutableList()
    val rows = mutableListOf<SyncOperationDelivery>()

    override suspend fun pending(operationIds: Set<String>?): List<SyncOperation> = values.filter { operationIds == null || it.id in operationIds }
    override suspend fun cards(): List<SyncOperationCard> = emptyList()
    override suspend fun complete(operations: List<SyncOperation>) { values.removeAll(operations.toSet()); val ids = operations.map(SyncOperation::id).toSet(); rows.removeAll { it.operationId in ids } }
    override suspend fun fail(operations: List<SyncOperation>, error: Throwable) = Unit
    override suspend fun ensureDeliveries(operations: List<SyncOperation>, targets: List<SyncOperationDelivery>) { rows += targets.filter { target -> rows.none { it.operationId == target.operationId && it.operationVersion == target.operationVersion && it.providerId == target.providerId } } }
    override suspend fun deliveries(operationIds: Set<String>): List<SyncOperationDelivery> = rows.filter { it.operationId in operationIds }
    override suspend fun acknowledge(provider: TrackingProviderId, operations: List<SyncOperation>) { operations.forEach { op -> rows.replaceFor(provider, op, DeliveryStatus.ACKNOWLEDGED) } }
    override suspend fun failDelivery(provider: TrackingProviderId, operations: List<SyncOperation>, error: Throwable) { operations.forEach { op -> rows.replaceFor(provider, op, DeliveryStatus.FAILED) } }
    override suspend fun skipUnsupported(provider: TrackingProviderId, operations: List<SyncOperation>) { operations.forEach { op -> rows.replaceFor(provider, op, DeliveryStatus.SKIPPED_UNSUPPORTED) } }
    override suspend fun completeReady(operations: List<SyncOperation>) { complete(operations.filter { op -> rows.filter { it.operationId == op.id && it.operationVersion == op.sourceVersion && it.required }.all { it.status == DeliveryStatus.ACKNOWLEDGED || it.status == DeliveryStatus.SKIPPED_UNSUPPORTED } }) }
}

private fun MutableList<SyncOperationDelivery>.replaceFor(provider: TrackingProviderId, operation: SyncOperation, status: DeliveryStatus) {
    val i = indexOfFirst { it.operationId == operation.id && it.operationVersion == operation.sourceVersion && it.providerId == provider }
    if (i >= 0 && this[i].status in setOf(DeliveryStatus.PENDING, DeliveryStatus.FAILED)) this[i] = this[i].copy(status = status)
}

private class DeliveryProvider(override val id: TrackingProviderId) : TrackingProvider {
    override val capabilities = TrackingCapabilities()
    var fail = false
    var calls = 0
    val sentIds = mutableListOf<String>()
    override suspend fun isAuthenticated() = true
    override suspend fun push(operations: List<SyncOperation>): ProviderPushResult { calls++; if (fail) error("offline"); sentIds += operations.map(SyncOperation::id); return ProviderPushResult(operations.mapTo(linkedSetOf(), SyncOperation::id)) }
    override suspend fun syncBidirectionally(operations: List<SyncOperation>, onProgress: (SyncProgress) -> Unit) = ProviderSyncOutcome(false, acknowledgedOperationIds = operations.mapTo(linkedSetOf(), SyncOperation::id))
    override suspend fun testConnection() = ConnectionResult.Connected
}

private class DeliveryRegistry(vararg providers: TrackingProvider) : TrackingProviderRegistry {
    private val providerIds = providers.map(TrackingProvider::id)
    private val values = providers.associateBy(TrackingProvider::id)
    override fun getProvider(id: TrackingProviderId) = values[id]
    override suspend fun configuration() = TrackingConfiguration(providerIds.firstOrNull(), providerIds.getOrNull(1))
}

