package com.cinetrack.data.sync.floppy

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cinetrack.data.library.RoomLibraryRepository
import com.cinetrack.data.local.AppDatabase
import com.cinetrack.data.repository.AppPreferences
import com.cinetrack.data.sync.ConnectionResult
import com.cinetrack.data.sync.DurableSyncOperationWriter
import com.cinetrack.data.sync.DurableTrackingQueue
import com.cinetrack.data.sync.ProviderPushResult
import com.cinetrack.data.sync.ProviderSyncOutcome
import com.cinetrack.data.sync.ProviderBootstrapState
import com.cinetrack.data.sync.DeliveryStatus
import com.cinetrack.data.sync.RoomSyncOperationRepository
import com.cinetrack.data.sync.SyncCoordinator
import com.cinetrack.data.sync.SyncOperation
import com.cinetrack.data.sync.SyncOperationType
import com.cinetrack.data.sync.SyncReconciler
import com.cinetrack.data.sync.TrackingCapabilities
import com.cinetrack.data.sync.TrackingCapability
import com.cinetrack.data.sync.TrackingConfiguration
import com.cinetrack.data.sync.TrackingConfigurationService
import com.cinetrack.data.sync.TrackingProvider
import com.cinetrack.data.sync.TrackingProviderId
import com.cinetrack.data.sync.TrackingProviderRegistry
import com.cinetrack.data.sync.TrackingRoutingMutex
import com.cinetrack.data.sync.TrackingSnapshot
import com.cinetrack.data.sync.MediaIds
import com.cinetrack.data.sync.TrackedMovieState
import com.cinetrack.domain.LibraryStatus
import com.cinetrack.domain.MediaCard
import com.cinetrack.domain.MediaType
import com.cinetrack.domain.SyncProgress
import com.cinetrack.data.sync.TrackingSyncError
import java.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import com.cinetrack.data.sync.floppy.network.FloppyApiClientFactory
import com.cinetrack.data.sync.floppy.network.FloppyRemoteDataSource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Real Room producer -> durable queue -> Floppy consumer coverage. */
@RunWith(AndroidJUnit4::class)
class FloppySecondaryRoomIntegrationTest {
    private lateinit var database: AppDatabase
    private lateinit var preferences: AppPreferences
    private lateinit var server: MockWebServer
    private lateinit var repository: RoomSyncOperationRepository
    private lateinit var floppy: FloppyTrackingProvider
    private lateinit var coordinator: SyncCoordinator
    private lateinit var main: IntegrationMainProvider
    private lateinit var movie: MediaCard

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        preferences = AppPreferences(context)
        server = MockWebServer().also { it.start() }
        repository = RoomSyncOperationRepository(database, preferences)
        main = IntegrationMainProvider()
        floppy = FloppyTrackingProvider(
            preferences = preferences,
            remote = FloppyRemoteDataSource(FloppyApiClientFactory()),
        )
        preferences.setTrackingProviders(TrackingProviderId.SIMKL, TrackingProviderId.FLOPPY)
        preferences.setFloppyConnection(
            FloppyConnectionSettings(
                baseUrl = server.url("/").toString(),
                serverIdentity = server.url("/").toString(),
                accountIdentity = "integration-user",
                connectionId = "integration-instance-a",
                capabilities = FloppyCapabilities(
                    canReadLibrary = true,
                    canWriteLibrary = true,
                    canReadHistory = true,
                    canWriteMovieHistory = true,
                    canWriteEpisodeHistory = true,
                    canRemoveHistory = true,
                ),
            ),
            "integration-secret",
        )
        val registry = IntegrationRegistry(preferences, main, floppy)
        coordinator = SyncCoordinator(registry, repository, SyncReconciler())
        movie = MediaCard(
            id = 42,
            type = MediaType.MOVIE,
            title = "Integration movie",
            overview = "",
            posterUrl = null,
            backdropUrl = null,
            releaseDate = null,
            score = null,
            status = LibraryStatus.PLAN_TO_WATCH,
            watched = false,
            runtimeMinutes = null,
            genres = emptyList(),
            providers = emptyList(),
            collectionId = null,
        )
    }

    @After
    fun tearDown() = runBlocking {
        preferences.setTrackingProviders(null, null)
        database.close()
        server.shutdown()
    }

    @Test
    fun roomMoviePlanToWatchProducesStructuredUnwatchAndFloppySucceeds() = runBlocking {
        val library = RoomLibraryRepository(
            database,
            preferences,
            coordinator,
            onLocalStateChanged = {},
            providerRegistry = IntegrationRegistry(preferences, main, floppy),
            routingMutex = TrackingRoutingMutex(),
        )
        main.authenticated = false
        library.setLibraryStatus(movie, LibraryStatus.PLAN_TO_WATCH)

        val pending = repository.pending()
        val unwatch = pending.single { it.type == SyncOperationType.MOVIE_UNWATCHED }
        val context = com.cinetrack.data.sync.MovieHistoryMutationContext.parse(unwatch.payload)
        assertEquals(LibraryStatus.PLAN_TO_WATCH, context?.desiredLibraryStatus)
        assertEquals(false, context?.previousWatched)
        assertNotNull(pending.single { it.type == SyncOperationType.LIBRARY_STATUS })

        server.enqueue(json("{\"consumptions\":[]}"))
        server.enqueue(json("{}"))
        val result = floppy.push(pending)
        assertEquals(pending.mapTo(linkedSetOf(), SyncOperation::id), result.completedOperationIds)
        assertEquals("GET", server.takeRequest().method)
        assertEquals("POST", server.takeRequest().method)
    }

    @Test
    fun roomWatchedToWatchingCarriesExactTimestampAndDeletesOnlyMatchingConsumption() = runBlocking {
        val library = RoomLibraryRepository(
            database,
            preferences,
            coordinator,
            onLocalStateChanged = {},
            providerRegistry = IntegrationRegistry(preferences, main, floppy),
            routingMutex = TrackingRoutingMutex(),
        )
        main.authenticated = false
        library.setLibraryStatus(movie, LibraryStatus.COMPLETED)
        val exactTimestamp = database.timelineDao().movieHistory(MediaType.MOVIE.name, movie.id).single().watchedAt
        library.setLibraryStatus(movie, LibraryStatus.WATCHING)
        val unwatch = repository.pending().single { it.type == SyncOperationType.MOVIE_UNWATCHED }
        val context = com.cinetrack.data.sync.MovieHistoryMutationContext.parse(unwatch.payload)
        assertEquals(true, context?.previousWatched)
        assertEquals(Instant.parse(exactTimestamp), context?.previousWatchedAt)
        assertEquals(LibraryStatus.WATCHING, context?.desiredLibraryStatus)

        server.enqueue(json("{\"pagination\":{\"total\":2,\"limit\":200,\"offset\":0,\"next\":null,\"previous\":null},\"results\":[{\"consumption_id\":7,\"status\":3,\"end_date\":\"$exactTimestamp\"},{\"consumption_id\":8,\"status\":3,\"end_date\":\"2025-01-01T00:00:00Z\"}]}"))
        server.enqueue(MockResponse().setResponseCode(204))
        floppy.push(listOf(unwatch))

        server.takeRequest()
        assertTrue(server.takeRequest().path?.endsWith("/history/7/") == true)
    }

    @Test
    fun connectedFloppyActivationPersistsSecondaryWithoutRestart() = runBlocking {
        preferences.setTrackingProviders(TrackingProviderId.SIMKL, null)
        server.enqueue(json("{\"version\":\"26.1\"}"))
        server.enqueue(json("{\"username\":\"integration-user\"}"))
        val routingMutex = TrackingRoutingMutex()
        val writer = DurableSyncOperationWriter(
            repository,
            DurableTrackingQueue(IntegrationRegistry(preferences, main, floppy), routingMutex),
            routingMutex,
        )
        val bootstrap = FloppyBootstrapCoordinator(
            preferences = preferences,
            operationRepository = repository,
            operationWriter = writer,
            canonicalSnapshot = { TrackingSnapshot(emptyList(), emptyList(), emptyList(), Instant.now()) },
            verifyRemote = { true },
        )
        val service = FloppySecondaryService(
            provider = floppy,
            preferences = preferences,
            configuration = TrackingConfigurationService(preferences, repository, routingMutex),
            coordinator = coordinator,
            bootstrap = { bootstrap },
        )

        assertEquals(com.cinetrack.data.sync.ConnectionResult.Connected, service.connect(server.url("/").toString(), "integration-secret", allowInsecureLocalHttp = true))
        assertEquals(TrackingProviderId.FLOPPY, preferences.secondaryTrackingProvider.first())
        assertEquals(ProviderBootstrapState.READY, preferences.providerBootstrapStateNow(TrackingProviderId.FLOPPY))
    }

    @Test
    fun roomCompletedMovieBootstrapPersistsOneSharedGenerationAndFallbackTimestamp() = runBlocking {
        val routingMutex = TrackingRoutingMutex()
        val writer = DurableSyncOperationWriter(
            repository,
            DurableTrackingQueue(IntegrationRegistry(preferences, main, floppy), routingMutex),
            routingMutex,
        )
        val canonical = TrackingSnapshot(
            movies = listOf(TrackedMovieState(MediaIds(tmdb = 42), LibraryStatus.COMPLETED, watched = true)),
        )
        val bootstrap = FloppyBootstrapCoordinator(
            preferences = preferences,
            operationRepository = repository,
            operationWriter = writer,
            canonicalSnapshot = { canonical },
            verifyRemote = { false },
        )

        bootstrap.start()

        val raw = requireNotNull(preferences.floppyBootstrapPlanRawNow())
        val operations = Json.parseToJsonElement(raw).jsonObject.getValue("operations").jsonArray
        val library = operations.first { it.jsonObject.getValue("type").jsonPrimitive.content == "LIBRARY_STATUS" }
        val watched = operations.first { it.jsonObject.getValue("type").jsonPrimitive.content == "MOVIE_WATCHED" }
        val generation = library.jsonObject.getValue("sourceVersion").jsonPrimitive.long
        assertEquals(generation, watched.jsonObject.getValue("sourceVersion").jsonPrimitive.long)
        assertEquals(Instant.ofEpochMilli(generation), Instant.parse(watched.jsonObject.getValue("payload").jsonPrimitive.content))
    }

    @Test
    fun failedRoomBootstrapRestartReusesThePersistedPlanExactly() = runBlocking {
        val routingMutex = TrackingRoutingMutex()
        val writer = DurableSyncOperationWriter(
            repository,
            DurableTrackingQueue(IntegrationRegistry(preferences, main, floppy), routingMutex),
            routingMutex,
        )
        val canonical = TrackingSnapshot(
            movies = listOf(TrackedMovieState(MediaIds(tmdb = 42), LibraryStatus.COMPLETED, watched = true)),
        )
        val first = FloppyBootstrapCoordinator(
            preferences = preferences,
            operationRepository = repository,
            operationWriter = writer,
            canonicalSnapshot = { canonical },
            verifyRemote = { false },
        )
        first.start()
        val before = requireNotNull(preferences.floppyBootstrapPlanRawNow())
        preferences.setProviderBootstrapState(TrackingProviderId.FLOPPY, ProviderBootstrapState.FAILED)

        val recreated = FloppyBootstrapCoordinator(
            preferences = preferences,
            operationRepository = repository,
            operationWriter = writer,
            canonicalSnapshot = { canonical },
            verifyRemote = { false },
        )
        recreated.start()

        assertEquals(before, preferences.floppyBootstrapPlanRawNow())
    }

    @Test
    fun sameTargetReconnectPreservesDeliveryInstanceAndRetriesFailedOperation() = runBlocking {
        // Start with no saved Floppy connection so this exercises the real
        // validation -> activation -> routing path, not a hand-built session.
        preferences.setFloppyConnection(null, null)
        preferences.setTrackingProviders(TrackingProviderId.SIMKL, null)
        main.authenticated = true
        server.enqueue(json("{\"version\":\"26.1\"}"))
        server.enqueue(json("{\"username\":\"integration-user\"}"))

        val routingMutex = TrackingRoutingMutex()
        val writer = DurableSyncOperationWriter(
            repository,
            DurableTrackingQueue(IntegrationRegistry(preferences, main, floppy), routingMutex),
            routingMutex,
        )
        val bootstrap = FloppyBootstrapCoordinator(
            preferences = preferences,
            operationRepository = repository,
            operationWriter = writer,
            canonicalSnapshot = { TrackingSnapshot(emptyList(), emptyList(), emptyList(), Instant.now()) },
            verifyRemote = { true },
        )
        val service = FloppySecondaryService(
            provider = floppy,
            preferences = preferences,
            configuration = TrackingConfigurationService(preferences, repository, routingMutex),
            coordinator = coordinator,
            bootstrap = { bootstrap },
        )
        val baseUrl = server.url("/").toString()
        assertEquals(ConnectionResult.Connected, service.connect(baseUrl, "integration-secret", allowInsecureLocalHttp = true))
        val beforeSettings = requireNotNull(preferences.floppySettingsNow())
        val operation = SyncOperation(
            id = "same-target-operation",
            type = SyncOperationType.LIBRARY_STATUS,
            mediaType = MediaType.MOVIE,
            mediaId = 42,
            title = "Integration movie",
            value = LibraryStatus.PLAN_TO_WATCH.name,
            sourceVersion = 99L,
        )
        writer.enqueueForProviders(operation, setOf(TrackingProviderId.FLOPPY))
        val beforeDelivery = repository.deliveries(setOf(operation.id)).single()
        assertEquals(beforeSettings.connectionId, beforeDelivery.providerInstanceId)
        assertEquals(DeliveryStatus.PENDING, beforeDelivery.status)

        // Same-target reconnect keeps the instance, but the first delivery
        // attempt fails after activation so the row remains retryable.
        server.enqueue(json("{\"version\":\"26.1\"}"))
        server.enqueue(json("{\"username\":\"integration-user\"}"))
        server.enqueue(MockResponse().setResponseCode(500))
        val failed = service.connect(baseUrl, "integration-secret", allowInsecureLocalHttp = true)
        assertTrue(failed is ConnectionResult.Failed)
        val afterFailedReconnect = requireNotNull(preferences.floppySettingsNow())
        val failedDelivery = repository.deliveries(setOf(operation.id)).single()
        assertEquals(beforeSettings.connectionId, afterFailedReconnect.connectionId)
        assertEquals(beforeDelivery.operationVersion, failedDelivery.operationVersion)
        assertEquals(beforeDelivery.providerInstanceId, failedDelivery.providerInstanceId)
        assertEquals(DeliveryStatus.FAILED, failedDelivery.status)

        // A later normal retry succeeds against the same instance and only
        // then acknowledges the exact durable operation.
        server.enqueue(json("{\"consumptions\":[]}"))
        server.enqueue(json("{}"))
        assertTrue(coordinator.pushPending().isSuccess)
        assertEquals(DeliveryStatus.ACKNOWLEDGED, repository.deliveries(setOf(operation.id)).single().status)
    }

    @Test
    fun sameAccountApiKeyRotationKeepsInstanceButChangesTransportGeneration() = runBlocking {
        preferences.setProviderBootstrapState(TrackingProviderId.FLOPPY, ProviderBootstrapState.READY)
        val previous = requireNotNull(preferences.floppySettingsNow())
        val generationBefore = floppy.transportSessionGeneration()
        val activation = floppy.commitValidatedConnectionLocked(
            previous.copy(connectionId = "candidate"),
            "rotated-secret",
        )

        assertFalse(activation.instanceChanged)
        assertTrue(activation.sessionChanged)
        assertEquals(previous.connectionId, activation.committed.connectionId)
        assertTrue(floppy.transportSessionGeneration() > generationBefore)
        assertEquals(previous.connectionId, preferences.floppySettingsNow()?.connectionId)
        assertEquals(ProviderBootstrapState.READY, preferences.providerBootstrapStateNow(TrackingProviderId.FLOPPY))
    }

    private fun json(body: String) = MockResponse()
        .addHeader("Content-Type", "application/json")
        .setBody(body)
}

private class IntegrationRegistry(
    private val preferences: AppPreferences,
    private val main: TrackingProvider,
    private val floppy: TrackingProvider,
) : TrackingProviderRegistry {
    override fun getProvider(id: TrackingProviderId): TrackingProvider? = when (id) {
        TrackingProviderId.SIMKL -> main
        TrackingProviderId.FLOPPY -> floppy
    }

    override suspend fun configuration() = TrackingConfiguration.normalized(
        preferences.mainTrackingProvider.first(),
        preferences.secondaryTrackingProvider.first(),
    )
}

private class IntegrationMainProvider : TrackingProvider {
    override val id = TrackingProviderId.SIMKL
    override val capabilities = TrackingCapabilities(
        supportsMovies = true,
        supportsShows = true,
        supportsWatchHistory = true,
        supportsLibrary = true,
        supported = TrackingCapability.entries.toSet(),
    )
    var authenticated = false

    override suspend fun isAuthenticated() = authenticated
    override suspend fun push(operations: List<SyncOperation>) = ProviderPushResult(operations.mapTo(linkedSetOf(), SyncOperation::id))
    override suspend fun syncBidirectionally(operations: List<SyncOperation>, onProgress: (SyncProgress) -> Unit) =
        ProviderSyncOutcome(
            itemsChanged = false,
            acknowledgedOperationIds = operations.mapTo(linkedSetOf(), SyncOperation::id),
        )
    override suspend fun testConnection() = ConnectionResult.Connected
}

