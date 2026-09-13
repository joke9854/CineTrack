package com.cinetrack.data.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cinetrack.data.local.AppDatabase
import com.cinetrack.data.local.UserMediaStateEntity
import com.cinetrack.data.repository.AppPreferences
import com.cinetrack.data.library.RoomLibraryRepository
import com.cinetrack.domain.LibraryStatus
import com.cinetrack.domain.MediaCard
import com.cinetrack.domain.MediaType
import com.cinetrack.domain.SyncProgress
import com.cinetrack.domain.SyncOperationStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

    @Test
    fun staleSameIdCompletionAndFailureCannotTouchNewGeneration() = runBlocking {
        val first = libraryOperation("state:MOVIE:42", 100L, LibraryStatus.WATCHING.name)
        val second = libraryOperation("state:MOVIE:42", 200L, LibraryStatus.DROPPED.name)
        repository.enqueue(listOf(first), targets(first).take(1))
        repository.enqueue(listOf(second), targets(second).take(1))

        repository.complete(listOf(first))
        repository.fail(listOf(first), error("stale failure"))

        val current = database.syncDao().syncOperation(second.id)
        assertNotNull(current)
        assertEquals(200L, current?.createdAt)
        assertEquals(SyncOperationStatus.PENDING.name, current?.status)
        assertTrue(repository.deliveries(setOf(second.id)).any { it.operationVersion == 200L && it.status == DeliveryStatus.PENDING })
        assertTrue(repository.deliveries(setOf(first.id)).none { it.operationVersion == 100L })

        val movieFirst = SyncOperation(
            id = "movie-watched:MOVIE:42",
            type = SyncOperationType.MOVIE_WATCHED,
            mediaType = MediaType.MOVIE,
            mediaId = 42,
            title = "Example movie",
            value = "true",
            payload = Instant.ofEpochMilli(300L).toString(),
            sourceVersion = 300L,
        )
        val movieSecond = movieFirst.copy(
            type = SyncOperationType.MOVIE_UNWATCHED,
            value = "false",
            payload = LibraryStatus.DROPPED.name,
            sourceVersion = 400L,
        )
        repository.enqueue(listOf(movieFirst), targets(movieFirst).take(1))
        repository.enqueue(listOf(movieSecond), targets(movieSecond).take(1))
        repository.complete(listOf(movieFirst))
        repository.fail(listOf(movieFirst), error("stale movie failure"))
        assertEquals(400L, database.syncDao().syncOperation(movieSecond.id)?.createdAt)
        assertEquals(SyncOperationStatus.PENDING.name, database.syncDao().syncOperation(movieSecond.id)?.status)
    }

    @Test
    fun watchedCompletionDoesNotClearIndependentMovieLibraryDirtyState() = runBlocking {
        database.stateDao().upsert(
            UserMediaStateEntity(
                mediaType = MediaType.MOVIE.name,
                mediaId = 42,
                status = LibraryStatus.DROPPED.name,
                watched = false,
                updatedAt = 300L,
                dirty = true,
            ),
        )
        val library = libraryOperation("state:MOVIE:42", 300L, LibraryStatus.DROPPED.name)
        val unwatched = SyncOperation(
            id = "movie-watched:MOVIE:42",
            type = SyncOperationType.MOVIE_UNWATCHED,
            mediaType = MediaType.MOVIE,
            mediaId = 42,
            title = "Example movie",
            value = "false",
            payload = LibraryStatus.DROPPED.name,
            sourceVersion = 300L,
        )
        repository.enqueue(listOf(library, unwatched), targets(library).take(1) + targets(unwatched).take(1))
        repository.fail(listOf(library), error("library unavailable"))
        repository.acknowledge(TrackingProviderId.SIMKL, listOf(unwatched))
        repository.complete(listOf(unwatched))

        assertTrue(database.stateDao().get(MediaType.MOVIE.name, 42)?.dirty == true)
        assertTrue(repository.pending().any { it.id == library.id })
        repository.complete(listOf(library))
        assertFalse(database.stateDao().get(MediaType.MOVIE.name, 42)?.dirty == true)
    }

    @Test
    fun movieStatusTransitionsKeepOneCanonicalHistoryRow() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val registry = object : TrackingProviderRegistry {
            override fun getProvider(id: TrackingProviderId): TrackingProvider? = null
            override suspend fun configuration() = TrackingConfiguration(null, null)
        }
        val coordinator = SyncCoordinator(registry, repository)
        val library = RoomLibraryRepository(
            database = database,
            preferences = AppPreferences(context),
            syncCoordinator = coordinator,
            onLocalStateChanged = {},
            providerRegistry = registry,
            routingMutex = TrackingRoutingMutex(),
        )
        val movie = MediaCard(
            id = 42,
            type = MediaType.MOVIE,
            title = "Example movie",
            overview = "",
            posterUrl = null,
            backdropUrl = null,
            releaseDate = null,
            score = null,
            status = LibraryStatus.COMPLETED,
            watched = true,
            runtimeMinutes = null,
            genres = emptyList(),
            providers = emptyList(),
            collectionId = null,
        )
        library.setLibraryStatus(movie, LibraryStatus.COMPLETED)
        library.setLibraryStatus(movie, LibraryStatus.DROPPED)
        assertEquals(0, database.timelineDao().historySnapshot().count { it.mediaType == MediaType.MOVIE.name && it.mediaId == 42 })
        assertTrue(database.stateDao().get(MediaType.MOVIE.name, 42)?.watched == false)

        library.setLibraryStatus(movie.copy(status = LibraryStatus.DROPPED, watched = false), LibraryStatus.COMPLETED)
        assertEquals(1, database.timelineDao().historySnapshot().count { it.mediaType == MediaType.MOVIE.name && it.mediaId == 42 })
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

    private fun libraryOperation(id: String, sourceVersion: Long, status: String) = SyncOperation(
        id = id,
        type = SyncOperationType.LIBRARY_STATUS,
        mediaType = MediaType.MOVIE,
        mediaId = 42,
        title = "Example movie",
        value = status,
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
