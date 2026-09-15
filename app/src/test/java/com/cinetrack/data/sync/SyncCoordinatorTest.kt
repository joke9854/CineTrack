package com.cinetrack.data.sync

import com.cinetrack.domain.MediaType
import com.cinetrack.domain.SyncOperationCard
import com.cinetrack.domain.SyncProgress
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncCoordinatorTest {
    @Test
    fun `outgoing operations reach main and secondary before completion`() = runTest {
        val operation = operation()
        val queue = FakeOperationRepository(operation)
        val main = FakeProvider(TrackingProviderId.SIMKL)
        val secondary = FakeProvider(TrackingProviderId.FLOPPY)
        val coordinator = SyncCoordinator(
            MutableRegistry(main, secondary),
            queue,
        )

        assertTrue(coordinator.pushPending().isSuccess)

        assertEquals(listOf(operation.id), main.pushed.single().map(SyncOperation::id))
        assertEquals(listOf(operation.id), secondary.pushed.single().map(SyncOperation::id))
        assertEquals(listOf(operation.id), queue.completed.map(SyncOperation::id))
    }

    @Test
    fun `generic pending dispatch excludes managed Floppy bootstrap rows`() = runTest {
        val normal = operation()
        val bootstrap = normal.copy(id = "bootstrap:instance:movie:42")
        val queue = FakeOperationRepository(normal, bootstrap)
        val simkl = FakeProvider(TrackingProviderId.SIMKL)
        val floppy = FakeProvider(TrackingProviderId.FLOPPY)
        val coordinator = SyncCoordinator(MutableRegistry(simkl, floppy), queue)

        assertTrue(coordinator.pushPending().isSuccess)
        assertEquals(listOf(normal.id), simkl.pushed.single().map(SyncOperation::id))
        assertEquals(listOf(normal.id), floppy.pushed.single().map(SyncOperation::id))
    }

    @Test
    fun `full sync never steals a large Floppy bootstrap queue`() = runTest {
        val normal = (1..3).map { index -> operation().copy(id = "state:MOVIE:$index", mediaId = index) }
        val bootstrap = (1..900).map { index -> operation().copy(id = "bootstrap:instance:movie:$index", mediaId = index) }
        val queue = FakeOperationRepository(*(normal + bootstrap).toTypedArray())
        val simkl = FakeProvider(TrackingProviderId.SIMKL)
        val floppy = FakeProvider(TrackingProviderId.FLOPPY)
        val result = SyncCoordinator(MutableRegistry(simkl, floppy), queue).sync { }

        assertTrue(result.isSuccess)
        assertEquals(3, simkl.pushed.single().size)
        assertEquals(3, floppy.pushed.single().size)
        assertTrue(floppy.pushed.single().none { it.isManagedBootstrapOperation() })
    }

    @Test
    fun `provider lanes allow Simkl and Floppy network calls concurrently`() = runTest {
        val simklStarted = CompletableDeferred<Unit>()
        val floppyStarted = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val simkl = FakeProvider(TrackingProviderId.SIMKL).apply {
            beforePush = { simklStarted.complete(Unit); release.await() }
        }
        val floppy = FakeProvider(TrackingProviderId.FLOPPY).apply {
            beforePush = { floppyStarted.complete(Unit); release.await() }
        }
        val queue = FakeOperationRepository(operation(), operation().copy(id = "state:MOVIE:43", mediaId = 43))
        val coordinator = SyncCoordinator(MutableRegistry(simkl, floppy), queue)

        val main = async { coordinator.pushPendingForProvider(TrackingProviderId.SIMKL, setOf("state:MOVIE:42")) }
        simklStarted.await()
        val secondary = async { coordinator.pushPendingForProvider(TrackingProviderId.FLOPPY, setOf("state:MOVIE:43")) }
        floppyStarted.await()
        release.complete(Unit)
        assertTrue(main.await().isSuccess)
        assertTrue(secondary.await().isSuccess)
    }

    @Test
    fun `generic retry refuses to own Floppy bootstrap`() = runTest {
        val coordinator = SyncCoordinator(MutableRegistry(FakeProvider(TrackingProviderId.SIMKL)), FakeOperationRepository(operation()))
        val result = coordinator.retry("bootstrap:instance-a:movie:42")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("WorkManager"))
    }

    @Test
    fun `secondary is outbound only during a full sync`() = runTest {
        val operation = operation()
        val queue = FakeOperationRepository(operation)
        val main = FakeProvider(TrackingProviderId.SIMKL)
        val secondary = FakeProvider(TrackingProviderId.FLOPPY)
        val coordinator = SyncCoordinator(MutableRegistry(main, secondary), queue)

        assertTrue(coordinator.sync { }.isSuccess)

        assertEquals(1, main.bidirectionalSyncs)
        assertEquals(0, secondary.bidirectionalSyncs)
        assertEquals(listOf(operation.id), secondary.pushed.single().map(SyncOperation::id))
    }

    @Test
    fun `switching providers uses the same coordinator and reverses roles`() = runTest {
        val queue = FakeOperationRepository(operation())
        val simkl = FakeProvider(TrackingProviderId.SIMKL)
        val floppy = FakeProvider(TrackingProviderId.FLOPPY)
        val registry = MutableRegistry(simkl, floppy)
        val coordinator = SyncCoordinator(registry, queue)

        assertTrue(coordinator.sync { }.isSuccess)
        registry.current = TrackingConfiguration(
            mainProvider = TrackingProviderId.FLOPPY,
            secondaryProvider = TrackingProviderId.SIMKL,
        )
        assertTrue(coordinator.sync { }.isSuccess)

        assertEquals(1, simkl.bidirectionalSyncs)
        assertEquals(1, floppy.bidirectionalSyncs)
    }

    @Test
    fun `failed operations remain retryable`() = runTest {
        val operation = operation()
        val queue = FakeOperationRepository(operation)
        val main = FakeProvider(TrackingProviderId.SIMKL).apply { pushFailure = IllegalStateException("offline") }
        val coordinator = SyncCoordinator(MutableRegistry(main), queue)

        assertTrue(coordinator.retry(operation.id).isFailure)
        assertEquals(listOf(operation.id), queue.failed.map(SyncOperation::id))
        assertTrue(queue.completed.isEmpty())

        main.pushFailure = null
        assertTrue(coordinator.retry(operation.id).isSuccess)
        assertEquals(listOf(operation.id), queue.completed.map(SyncOperation::id))
    }

    @Test
    fun `failed secondary push keeps operation retryable`() = runTest {
        val operation = operation()
        val queue = FakeOperationRepository(operation)
        val main = FakeProvider(TrackingProviderId.SIMKL)
        val secondary = FakeProvider(TrackingProviderId.FLOPPY).apply { pushFailure = IllegalStateException("secondary offline") }
        val coordinator = SyncCoordinator(MutableRegistry(main, secondary), queue)

        assertTrue(coordinator.pushPending().isFailure)
        assertTrue(queue.completed.isEmpty())
        assertEquals(listOf(operation.id), queue.failed.map(SyncOperation::id))
    }

    @Test
    fun `no main provider is rejected without pulling secondary`() = runTest {
        val queue = FakeOperationRepository(operation())
        val secondary = FakeProvider(TrackingProviderId.FLOPPY)
        val registry = MutableRegistry(secondary).apply {
            current = TrackingConfiguration(mainProvider = null, secondaryProvider = null)
        }
        val result = SyncCoordinator(registry, queue).sync { }
        assertTrue(result.isFailure)
        assertEquals(0, secondary.bidirectionalSyncs)
    }

    @Test
    fun `durable operation remains queued until remote acknowledgement`() = runTest {
        val operation = operation()
        val queue = FakeOperationRepository(operation)
        val provider = FakeProvider(TrackingProviderId.SIMKL)
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        provider.beforePush = {
            started.complete(Unit)
            release.await()
        }
        val coordinator = SyncCoordinator(MutableRegistry(provider), queue)

        val push = async { coordinator.pushPending() }
        started.await()
        assertEquals(listOf(operation.id), queue.pending().map(SyncOperation::id))
        assertTrue(queue.completed.isEmpty())

        release.complete(Unit)
        assertTrue(push.await().isSuccess)
        assertTrue(queue.pending().isEmpty())
    }

    @Test
    fun `unsupported capability fails without dropping operation`() = runTest {
        val operation = operation(type = SyncOperationType.SET_RATING)
        val queue = FakeOperationRepository(operation)
        val provider = FakeProvider(
            TrackingProviderId.SIMKL,
            capabilities = TrackingCapabilities(supportsRatings = false),
        )

        val result = SyncCoordinator(MutableRegistry(provider), queue).pushPending()

        assertTrue(result.exceptionOrNull() is TrackingSyncError.UnsupportedOperation)
        assertTrue(queue.completed.isEmpty())
        assertEquals(listOf(operation.id), queue.failed.map(SyncOperation::id))
    }

    @Test
    fun `same provider cannot occupy both roles`() {
        val result = runCatching {
            TrackingConfiguration(TrackingProviderId.SIMKL, TrackingProviderId.SIMKL)
        }
        assertFalse(result.isSuccess)
    }

    @Test
    fun `secondary without main is rejected and legacy read is normalized`() {
        assertFalse(runCatching {
            TrackingConfiguration(mainProvider = null, secondaryProvider = TrackingProviderId.FLOPPY)
        }.isSuccess)
        assertEquals(
            TrackingConfiguration(mainProvider = null, secondaryProvider = null),
            TrackingConfiguration.normalized(null, TrackingProviderId.FLOPPY),
        )
    }

    @Test
    fun `main promotion requires configured secondary bootstrap`() {
        val previous = TrackingConfiguration(TrackingProviderId.SIMKL, TrackingProviderId.FLOPPY)
        val next = TrackingConfiguration(TrackingProviderId.FLOPPY, TrackingProviderId.SIMKL)

        assertTrue(runCatching {
            validateTrackingConfigurationTransition(previous, next, ProviderBootstrapState.NOT_STARTED)
        }.isFailure)
        assertTrue(runCatching {
            validateTrackingConfigurationTransition(previous, next, ProviderBootstrapState.READY)
        }.isFailure)
        assertTrue(runCatching {
            validateTrackingConfigurationTransition(
                TrackingConfiguration(TrackingProviderId.SIMKL, null),
                TrackingConfiguration(TrackingProviderId.FLOPPY, null),
                ProviderBootstrapState.READY,
            )
        }.isFailure)
    }
}

private fun operation(type: SyncOperationType = SyncOperationType.LIBRARY_STATUS) = SyncOperation(
    id = "state:MOVIE:42",
    type = type,
    mediaType = MediaType.MOVIE,
    mediaId = 42,
    title = "Example",
    value = "WATCHING",
    sourceVersion = 1L,
)

private class MutableRegistry(vararg providers: TrackingProvider) : TrackingProviderRegistry {
    private val values = providers.associateBy(TrackingProvider::id)
    var current = TrackingConfiguration(
        mainProvider = providers.firstOrNull()?.id,
        secondaryProvider = providers.getOrNull(1)?.id,
    )

    override fun getProvider(id: TrackingProviderId): TrackingProvider? = values[id]
    override suspend fun configuration(): TrackingConfiguration = current
}

private class FakeProvider(
    override val id: TrackingProviderId,
    override val capabilities: TrackingCapabilities = TrackingCapabilities(),
) : TrackingProvider {
    val pushed = mutableListOf<List<SyncOperation>>()
    var bidirectionalSyncs = 0
    var pushFailure: Throwable? = null
    var beforePush: suspend () -> Unit = {}

    override suspend fun isAuthenticated(): Boolean = true

    override suspend fun push(operations: List<SyncOperation>): ProviderPushResult {
        beforePush()
        pushFailure?.let { throw it }
        pushed += operations
        return ProviderPushResult(operations.mapTo(linkedSetOf(), SyncOperation::id))
    }

    override suspend fun syncBidirectionally(
        operations: List<SyncOperation>,
        onProgress: (SyncProgress) -> Unit,
    ): ProviderSyncOutcome {
        bidirectionalSyncs++
        if (operations.isNotEmpty()) pushed += operations
        return ProviderSyncOutcome(
            itemsChanged = false,
            acknowledgedOperationIds = operations.mapTo(linkedSetOf(), SyncOperation::id),
        )
    }

    override suspend fun testConnection(): ConnectionResult = ConnectionResult.Connected
}

private class FakeOperationRepository(vararg initial: SyncOperation) : SyncOperationRepository {
    private val values = initial.toMutableList()
    val completed = mutableListOf<SyncOperation>()
    val failed = mutableListOf<SyncOperation>()

    override suspend fun pending(operationIds: Set<String>?): List<SyncOperation> =
        values.filter { operationIds == null || it.id in operationIds }

    override suspend fun cards(): List<SyncOperationCard> = emptyList()

    override suspend fun complete(operations: List<SyncOperation>) {
        completed += operations
        values.removeAll(operations.toSet())
    }

    override suspend fun fail(operations: List<SyncOperation>, error: Throwable) {
        failed.clear()
        failed += operations
    }
}

