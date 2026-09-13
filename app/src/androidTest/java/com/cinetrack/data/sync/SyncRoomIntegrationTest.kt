package com.cinetrack.data.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cinetrack.data.local.AppDatabase
import com.cinetrack.data.repository.AppPreferences
import com.cinetrack.domain.MediaType
import com.cinetrack.domain.SyncProgress
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * Exercises the production Room queue at the provider boundary. The unit
 * tests intentionally use small in-memory repositories; these tests verify
 * that persisted delivery generations behave the same way after a reload.
 */
@RunWith(AndroidJUnit4::class)
class SyncRoomIntegrationTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: RoomSyncOperationRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomSyncOperationRepository(database, AppPreferences(context))
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun sameEpisodeFieldSupersedesEveryOlderProviderDelivery() = runBlocking {
        val first = episodeOperation(
            id = "episode:watched:100",
            type = SyncOperationType.EPISODE_WATCHED,
            sourceVersion = 100L,
            payload = "1:5:${Instant.ofEpochMilli(100L)}",
        )
        val second = episodeOperation(
            id = "episode:unwatched:200",
            type = SyncOperationType.EPISODE_UNWATCHED,
            sourceVersion = 200L,
            payload = "1:5",
        )
        repository.enqueue(listOf(first), targets(first))
        repository.enqueue(listOf(second), targets(second))

        // The superseded generation is retired immediately because every
        // required delivery is terminal; only the canonical generation remains.
        assertTrue(repository.deliveries(setOf(first.id)).isEmpty())
        assertEquals(setOf(second.id), repository.pending().mapTo(linkedSetOf(), SyncOperation::id))

        val main = RecordingProvider(TrackingProviderId.SIMKL)
        val secondary = RecordingProvider(TrackingProviderId.FLOPPY)
        val coordinator = SyncCoordinator(
            TestRegistry(main, secondary),
            repository,
        )
        assertTrue(coordinator.pushPending().isSuccess)
        assertEquals(listOf(second.id), main.sentIds)
        assertEquals(listOf(second.id), secondary.sentIds)
        assertTrue(repository.pending().isEmpty())
    }

    @Test
    fun nonWriteEpisodeOperationsReconstructPayloadAfterRoomReload() = runBlocking {
        val watched = episodeOperation(
            id = "episode:watched:300",
            type = SyncOperationType.EPISODE_WATCHED,
            sourceVersion = 300L,
            payload = "1:5:${Instant.ofEpochMilli(300L)}",
        )
        val unwatched = episodeOperation(
            id = "episode:unwatched:400",
            type = SyncOperationType.EPISODE_UNWATCHED,
            sourceVersion = 400L,
            payload = "1:6",
        )
        repository.enqueue(listOf(watched, unwatched), targets(watched) + targets(unwatched))

        val loaded = repository.pending().associateBy(SyncOperation::id)
        assertEquals("1:5:${Instant.ofEpochMilli(300L)}", loaded[watched.id]?.payload)
        assertEquals("1:6", loaded[unwatched.id]?.payload)
        assertEquals(300L, loaded[watched.id]?.sourceVersion)
        assertEquals(400L, loaded[unwatched.id]?.sourceVersion)
    }

    @Test
    fun secondaryFailureIsRetriedWithoutResendingAcknowledgedMain() = runBlocking {
        val operation = episodeOperation(
            id = "episode:watched:500",
            type = SyncOperationType.EPISODE_WATCHED,
            sourceVersion = 500L,
            payload = "1:5:${Instant.ofEpochMilli(500L)}",
        )
        repository.enqueue(listOf(operation), targets(operation))
        val main = RecordingProvider(TrackingProviderId.SIMKL)
        val secondary = RecordingProvider(TrackingProviderId.FLOPPY).apply { fail = true }
        val coordinator = SyncCoordinator(TestRegistry(main, secondary), repository)

        assertTrue(coordinator.pushPending().isFailure)
        assertEquals(1, main.calls)
        assertEquals(1, secondary.calls)
        val failedRows = repository.deliveries(setOf(operation.id)).associateBy(SyncOperationDelivery::providerId)
        assertEquals(DeliveryStatus.ACKNOWLEDGED, failedRows[TrackingProviderId.SIMKL]?.status)
        assertEquals(DeliveryStatus.FAILED, failedRows[TrackingProviderId.FLOPPY]?.status)

        secondary.fail = false
        assertTrue(coordinator.pushPending().isSuccess)
        assertEquals(1, main.calls)
        assertEquals(2, secondary.calls)
        assertTrue(repository.pending().isEmpty())
    }

    private fun episodeOperation(
        id: String,
        type: SyncOperationType,
        sourceVersion: Long,
        payload: String,
    ) = SyncOperation(
        id = id,
        type = type,
        mediaType = MediaType.TV,
        mediaId = 42,
        title = "Example show",
        value = type.name,
        payload = payload,
        sourceVersion = sourceVersion,
    )

    private fun targets(operation: SyncOperation) = listOf(
        SyncOperationDelivery(
            operationId = operation.id,
            operationVersion = operation.sourceVersion,
            providerId = TrackingProviderId.SIMKL,
            required = true,
            roleAtEnqueue = TrackingRole.MAIN,
        ),
        SyncOperationDelivery(
            operationId = operation.id,
            operationVersion = operation.sourceVersion,
            providerId = TrackingProviderId.FLOPPY,
            required = true,
            roleAtEnqueue = TrackingRole.SECONDARY,
        ),
    )
}

private class TestRegistry(
    private val main: TrackingProvider,
    private val secondary: TrackingProvider,
) : TrackingProviderRegistry {
    override fun getProvider(id: TrackingProviderId): TrackingProvider? = when (id) {
        main.id -> main
        secondary.id -> secondary
        else -> null
    }

    override suspend fun configuration() = TrackingConfiguration(
        mainProvider = main.id,
        secondaryProvider = secondary.id,
    )
}

private class RecordingProvider(override val id: TrackingProviderId) : TrackingProvider {
    override val capabilities = TrackingCapabilities()
    var fail = false
    var calls = 0
    val sentIds = mutableListOf<String>()

    override suspend fun isAuthenticated(): Boolean = true

    override suspend fun push(operations: List<SyncOperation>): ProviderPushResult {
        calls++
        if (fail) error("offline")
        sentIds += operations.map(SyncOperation::id)
        return ProviderPushResult(operations.mapTo(linkedSetOf(), SyncOperation::id))
    }

    override suspend fun syncBidirectionally(
        operations: List<SyncOperation>,
        onProgress: (SyncProgress) -> Unit,
    ): ProviderSyncOutcome = ProviderSyncOutcome(
        itemsChanged = false,
        acknowledgedOperationIds = operations.mapTo(linkedSetOf(), SyncOperation::id),
    )

    override suspend fun testConnection(): ConnectionResult = ConnectionResult.Connected
}
