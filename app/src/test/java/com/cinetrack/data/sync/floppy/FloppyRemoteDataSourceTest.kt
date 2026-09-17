package com.cinetrack.data.sync.floppy

import com.cinetrack.data.sync.SyncOperation
import com.cinetrack.data.sync.SyncOperationType
import com.cinetrack.data.sync.MovieHistoryMutationContext
import com.cinetrack.data.sync.floppy.network.FloppyApiClientFactory
import com.cinetrack.data.sync.floppy.network.FloppyRemoteDataSource
import com.cinetrack.data.sync.floppy.network.FloppyBootstrapTransportContext
import com.cinetrack.data.sync.floppy.network.episodeClientEventId
import com.cinetrack.domain.MediaType
import com.cinetrack.domain.LibraryStatus
import com.cinetrack.domain.FloppyConnectionStage
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import kotlinx.coroutines.runBlocking

class FloppyRemoteDataSourceTest {
    private lateinit var server: MockWebServer
    private lateinit var remote: FloppyRemoteDataSource
    private lateinit var session: FloppySession

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        remote = FloppyRemoteDataSource(FloppyApiClientFactory())
        session = FloppySession(
            instanceId = "test-instance",
            baseUrl = server.url("/proxy/").toString(),
            accountIdentity = "alice",
            credentialAlias = "alias",
            apiKey = "secret",
            capabilities = FloppyCapabilities(
                canReadLibrary = true,
                canWriteLibrary = true,
                canReadHistory = true,
                canWriteMovieHistory = true,
                canWriteEpisodeHistory = true,
                canRemoveHistory = true,
            ),
            serverVersion = "test",
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun libraryUpdateTargetsTheActiveConsumption() = runBlocking {
        server.enqueue(json("{\"consumptions\":[{\"consumption_id\":7,\"status\":1}]}"))
        server.enqueue(json("{\"consumption_id\":7,\"status\":2}"))

        val operation = operation(SyncOperationType.LIBRARY_STATUS, value = "PAUSED")
        remote.push(session, listOf(operation))

        val getRequest = server.takeRequest()
        assertEquals("GET", getRequest.method)
        assertEquals("/proxy/api/v1/media/movie/tmdb/42/", getRequest.path)
        val request = server.takeRequest()
        assertEquals("PATCH", request.method)
        assertEquals("/proxy/api/v1/media/movie/tmdb/42/history/7/", request.path)
        assertEquals("{\"status\":2}", request.body.readUtf8())
    }

    @Test
    fun libraryRemovalDeletesOnlyTheActiveConsumption() = runBlocking {
        server.enqueue(json("{\"consumptions\":[{\"consumption_id\":7,\"status\":1}]}"))
        server.enqueue(MockResponse().setResponseCode(204))

        remote.push(session, listOf(operation(SyncOperationType.LIBRARY_STATUS, value = "NONE")))

        assertEquals("/proxy/api/v1/media/movie/tmdb/42/", server.takeRequest().path)
        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/proxy/api/v1/media/movie/tmdb/42/history/7/", request.path)
    }

    @Test
    fun movieWatchUsesDedicatedIdempotentWatchRoute() = runBlocking {
        server.enqueue(json("{}"))

        val watchedAt = Instant.parse("2026-01-01T00:00:00Z")
        remote.push(session, listOf(operation(SyncOperationType.MOVIE_WATCHED, payload = watchedAt.toString())))

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/proxy/api/v1/media/movie/tmdb/42/watch/", request.path)
        assertEquals(
            "{\"end_date\":\"2026-01-01T00:00:00Z\",\"external_id\":\"cinetrack:test-instance:MOVIE_WATCHED:1\"}",
            request.body.readUtf8(),
        )
    }

    @Test
    fun bootstrapMovieStateUsesTypedMediaAndAvoidsDuplicateWatch() = runBlocking {
        server.enqueue(typedPage(trackedMovie(endDate = "2026-01-01T00:00:00Z")))
        val context = FloppyBootstrapTransportContext(session.instanceId)
        val watchedAt = Instant.parse("2026-01-01T00:00:00Z")
        val first = operation(SyncOperationType.MOVIE_WATCHED, payload = watchedAt.toString())
        val duplicate = first.copy(id = "duplicate")

        remote.prepareBootstrap(session, listOf(first, duplicate), context)
        remote.push(session, listOf(first), context)
        remote.push(session, listOf(duplicate), context)

        assertEquals(1, server.requestCount)
        assertEquals(
            "/proxy/api/v1/media/movie/?limit=200&offset=0",
            server.takeRequest().path,
        )
    }

    @Test
    fun preparedMovieStateAvoidsPerMoviePreflightForCompletedPair() = runBlocking {
        server.enqueue(typedPage(trackedMovie(endDate = "2026-01-01T00:00:00Z")))
        val context = FloppyBootstrapTransportContext(session.instanceId)
        val watched = operation(
            SyncOperationType.MOVIE_WATCHED,
            payload = "2026-01-01T00:00:00Z",
        ).copy(id = "watched")
        val completed = operation(
            SyncOperationType.LIBRARY_STATUS,
            value = "COMPLETED",
        ).copy(id = "completed")

        remote.prepareBootstrap(session, listOf(watched, completed), context)
        val result = remote.push(session, listOf(watched, completed), context)

        assertEquals(setOf("watched", "completed"), result.completedOperationIds)
        assertEquals(1, server.requestCount)
        assertEquals(
            "/proxy/api/v1/media/movie/?limit=200&offset=0",
            server.takeRequest().path,
        )
    }

    @Test
    fun typedMoviePreparationPaginatesAndRetainsActiveConsumption() = runBlocking {
        server.enqueue(typedPage(trackedMovie(id = 42, endDate = "2026-01-01T00:00:00Z"), next = "/next"))
        server.enqueue(typedPage(trackedMovie(id = 43, consumptionId = 9, status = 1), offset = 1, total = 2))
        val context = FloppyBootstrapTransportContext(session.instanceId)

        remote.prepareBootstrap(session, listOf(operation(SyncOperationType.MOVIE_WATCHED)), context)

        assertEquals(2, server.requestCount)
        assertEquals("/proxy/api/v1/media/movie/?limit=200&offset=0", server.takeRequest().path)
        assertEquals("/proxy/api/v1/media/movie/?limit=200&offset=1", server.takeRequest().path)
        assertTrue(Instant.parse("2026-01-01T00:00:00Z") in context.watchedMovieIndex.getValue(42))
        assertEquals(9, context.activeMovieConsumptions.getValue(43).consumptionId)
    }

    @Test
    fun retryableMoviePreparationFallsBackToIdempotentWatch() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(json("{}"))
        val context = FloppyBootstrapTransportContext(session.instanceId)
        val watchedAt = Instant.parse("2026-01-01T00:00:00Z")
        val operation = operation(SyncOperationType.MOVIE_WATCHED, payload = watchedAt.toString())

        remote.prepareBootstrap(session, listOf(operation), context)
        remote.push(session, listOf(operation), context)

        assertTrue(context.moviePreparationUnavailable)
        assertEquals("/proxy/api/v1/media/movie/?limit=200&offset=0", server.takeRequest().path)
        assertEquals("/proxy/api/v1/media/movie/tmdb/42/watch/", server.takeRequest().path)
    }

    @Test
    fun movieUnwatchedDeletesTheMatchingConsumptionOnly() = runBlocking {
        server.enqueue(json("{\"pagination\":{\"total\":1,\"limit\":200,\"offset\":0,\"next\":null,\"previous\":null},\"results\":[{\"consumption_id\":7,\"status\":3,\"end_date\":\"2026-01-01T00:00:00Z\"}]}"))
        server.enqueue(MockResponse().setResponseCode(204))

        remote.push(session, listOf(operation(
            SyncOperationType.MOVIE_UNWATCHED,
            payload = "2026-01-01T00:00:00Z",
        )))

        server.takeRequest()
        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/proxy/api/v1/media/movie/tmdb/42/history/7/", request.path)
    }

    @Test
    fun episodeWatchChecksTypedMediaBeforeCallingWatchRoute() = runBlocking {
        server.enqueue(typedPage())
        server.enqueue(json("{}"))

        remote.push(session, listOf(operation(
            SyncOperationType.EPISODE_WATCHED,
            mediaType = MediaType.TV,
            payload = "2:3:2026-01-01T00:00:00Z",
        )))

        assertEquals("/proxy/api/v1/media/episode/?limit=200&offset=0", server.takeRequest().path)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/proxy/api/v1/media/tv/tmdb/42/2/episodes/3/watch/", request.path)
        assertEquals("{\"watched_at\":\"2026-01-01T00:00:00Z\"}", request.body.readUtf8())
    }

    @Test
    fun episodeWatchPaginatesTypedMediaBeyondFirstPageBeforePosting() = runBlocking {
        val firstPage = (1..200).joinToString(",") { episode ->
            trackedEpisode(99, 1, episode, "2026-01-01T00:00:00Z")
        }
        server.enqueue(json("{\"pagination\":{\"total\":201,\"limit\":200,\"offset\":0,\"next\":\"/next\",\"previous\":null},\"results\":[$firstPage]}"))
        server.enqueue(typedPage(trackedEpisode(42, 2, 3, "2026-01-01T00:00:00Z"), offset = 200, total = 201))

        remote.push(session, listOf(operation(
            SyncOperationType.EPISODE_WATCHED,
            mediaType = MediaType.TV,
            payload = "2:3:2026-01-01T00:00:00Z",
        )))

        assertEquals(2, server.requestCount)
        assertEquals("/proxy/api/v1/media/episode/?limit=200&offset=0", server.takeRequest().path)
        assertEquals("/proxy/api/v1/media/episode/?limit=200&offset=200", server.takeRequest().path)
    }

    @Test
    fun bootstrapTransportIndexesTypedEpisodeMediaOnceAndUpdatesCache() = runBlocking {
        server.enqueue(typedPage())
        server.enqueue(json("{\"task_id\":\"task-1\"}"))
        server.enqueue(json("{\"status\":\"SUCCESS\"}"))
        val context = FloppyBootstrapTransportContext(session.instanceId)
        val first = operation(SyncOperationType.EPISODE_WATCHED, mediaType = MediaType.TV, payload = "2:3:2026-01-01T00:00:00Z")
        val duplicate = first.copy(id = "duplicate")

        remote.prepareBootstrap(session, listOf(first, duplicate), context)
        remote.push(session, listOf(first), context)
        remote.push(session, listOf(duplicate), context)

        // One typed-media index request and one async bulk range task; the
        // duplicate is acknowledged from the run-scoped EpisodeKey cache.
        assertEquals(3, server.requestCount)
        assertEquals("/proxy/api/v1/media/episode/?limit=200&offset=0", server.takeRequest().path)
        assertEquals("/proxy/api/v1/media/tv/tmdb/42/episodes/bulk/", server.takeRequest().path)
        assertEquals("/proxy/api/v1/tasks/task-1/", server.takeRequest().path)
    }

    @Test
    fun capableBootstrapUsesExactEnsureEventsWithoutEpisodePrefetch() = runBlocking {
        val capable = session.copy(capabilities = session.capabilities.copy(canEnsureEpisodeEvents = true))
        val context = FloppyBootstrapTransportContext(capable.instanceId, canEnsureEpisodeEvents = true)
        val operation = operation(
            SyncOperationType.EPISODE_WATCHED,
            mediaType = MediaType.TV,
            payload = "2:3:2024-01-03T21:13:00Z",
        )
        server.enqueue(json("""{"results":[{"client_event_id":"${episodeClientEventId(capable.instanceId, operation)}","season_number":2,"episode_number":3,"status":"created"}]}"""))

        remote.push(capable, listOf(operation), context)

        assertEquals(1, server.requestCount)
        val request = server.takeRequest()
        assertEquals("/proxy/api/v1/media/tv/tmdb/42/episodes/ensure/", request.path)
        val body = request.body.readUtf8()
        assertTrue(body.contains("2024-01-03T21:13:00Z"))
        assertTrue(body.contains("\"season_number\":2"))
        assertTrue(body.contains("\"episode_number\":3"))
    }

    @Test
    fun episodeUnwatchedUsesTheDedicatedDropRoute() = runBlocking {
        server.enqueue(json("{}"))

        remote.push(session, listOf(operation(
            SyncOperationType.EPISODE_UNWATCHED,
            mediaType = MediaType.TV,
            payload = "2:3",
        )))

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/proxy/api/v1/media/tv/tmdb/42/2/episodes/3/drop/", request.path)
    }

    @Test
    fun coupledMovieCompletionInEitherOrderCreatesExactlyOneConsumption() = runBlocking {
        val watchedAt = Instant.parse("2026-01-01T00:00:00Z")
        server.enqueue(json("{\"pagination\":{\"total\":0,\"limit\":200,\"offset\":0,\"next\":null,\"previous\":null},\"results\":[]}"))
        server.enqueue(json("{}"))
        val watched = operation(SyncOperationType.MOVIE_WATCHED, payload = watchedAt.toString()).copy(id = "watched")
        val completed = operation(SyncOperationType.LIBRARY_STATUS, value = "COMPLETED").copy(id = "completed")

        val result = remote.push(session, listOf(watched, completed))

        assertEquals(setOf("watched", "completed"), result.completedOperationIds)
        assertEquals("/proxy/api/v1/media/movie/tmdb/42/history/?limit=200&offset=0", server.takeRequest().path)
        val post = server.takeRequest()
        assertEquals("POST", post.method)
        assertEquals("/proxy/api/v1/media/movie/tmdb/42/watch/", post.path)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun coupledMovieCompletionRetryAfterRemoteCommitDoesNotAppendAgain() = runBlocking {
        val watchedAt = Instant.parse("2026-01-01T00:00:00Z")
        val watched = operation(SyncOperationType.MOVIE_WATCHED, payload = watchedAt.toString()).copy(id = "watched")
        val completed = operation(SyncOperationType.LIBRARY_STATUS, value = "COMPLETED").copy(id = "completed")
        server.enqueue(json("{\"pagination\":{\"total\":0,\"limit\":200,\"offset\":0,\"next\":null,\"previous\":null},\"results\":[]}"))
        server.enqueue(json("{}"))
        remote.push(session, listOf(completed, watched))
        server.enqueue(json("{\"pagination\":{\"total\":1,\"limit\":200,\"offset\":0,\"next\":null,\"previous\":null},\"results\":[{\"consumption_id\":7,\"status\":3,\"end_date\":\"2026-01-01T00:00:00Z\"}]}"))

        remote.push(session, listOf(watched, completed))

        assertEquals(3, server.requestCount)
    }

    @Test
    fun coupledMovieCompletionRemovesContradictoryActiveConsumptionAfterSeedingPlay() = runBlocking {
        val watchedAt = Instant.parse("2026-01-01T00:00:00Z")
        val watched = operation(SyncOperationType.MOVIE_WATCHED, payload = watchedAt.toString()).copy(id = "watched")
        val completed = operation(SyncOperationType.LIBRARY_STATUS, value = "COMPLETED").copy(id = "completed")
        server.enqueue(json("{\"pagination\":{\"total\":1,\"limit\":200,\"offset\":0,\"next\":null,\"previous\":null},\"results\":[{\"consumption_id\":7,\"status\":1}]}"))
        server.enqueue(json("{}"))
        server.enqueue(json("{\"pagination\":{\"total\":2,\"limit\":200,\"offset\":0,\"next\":null,\"previous\":null},\"results\":[{\"consumption_id\":7,\"status\":1},{\"consumption_id\":8,\"status\":3,\"end_date\":\"2026-01-01T00:00:00Z\"}]}"))
        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(json("{\"pagination\":{\"total\":1,\"limit\":200,\"offset\":0,\"next\":null,\"previous\":null},\"results\":[{\"consumption_id\":8,\"status\":3,\"end_date\":\"2026-01-01T00:00:00Z\"}]}"))

        val result = remote.push(session, listOf(completed, watched))

        assertEquals(setOf("completed", "watched"), result.completedOperationIds)
        assertEquals(5, server.requestCount)
        repeat(3) { server.takeRequest() }
        assertTrue(server.takeRequest().path?.endsWith("/history/7/") == true)
        server.takeRequest()
        Unit
        Unit
    }

    @Test
    fun standaloneCompletedTvIsIdempotent() = runBlocking {
        server.enqueue(json("{\"consumptions\":[]}"))
        server.enqueue(json("{}"))
        val operation = operation(SyncOperationType.LIBRARY_STATUS, mediaType = MediaType.TV, value = "COMPLETED")
        remote.push(session, listOf(operation))
        server.enqueue(json("{\"consumptions\":[{\"consumption_id\":7,\"status\":3}]}"))

        remote.push(session, listOf(operation))

        assertEquals(3, server.requestCount)
    }

    @Test
    fun completedTvRemovesActiveConsumptionAfterCreatingCompletion() = runBlocking {
        server.enqueue(json("{\"consumptions\":[{\"consumption_id\":7,\"status\":1}]}"))
        server.enqueue(json("{}"))
        server.enqueue(json("{\"consumptions\":[{\"consumption_id\":7,\"status\":1},{\"consumption_id\":8,\"status\":3}]}"))
        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(json("{\"consumptions\":[{\"consumption_id\":8,\"status\":3}]}"))

        remote.push(session, listOf(operation(SyncOperationType.LIBRARY_STATUS, mediaType = MediaType.TV, value = "COMPLETED")))

        assertEquals(5, server.requestCount)
        repeat(3) { server.takeRequest() }
        assertTrue(server.takeRequest().path?.endsWith("/history/7/") == true)
        server.takeRequest()
        Unit
    }

    @Test
    fun completedTvWithHistoricalCompletionOnlyCleansActiveAndPreservesHistory() = runBlocking {
        server.enqueue(json("{\"consumptions\":[{\"consumption_id\":7,\"status\":1},{\"consumption_id\":8,\"status\":3}]}"))
        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(json("{\"consumptions\":[{\"consumption_id\":8,\"status\":3}]}"))

        remote.push(session, listOf(operation(SyncOperationType.LIBRARY_STATUS, mediaType = MediaType.TV, value = "COMPLETED")))

        assertEquals(3, server.requestCount)
        server.takeRequest()
        assertTrue(server.takeRequest().path?.endsWith("/history/7/") == true)
        server.takeRequest()
        Unit
    }

    @Test
    fun knownNeverWatchedUnwatchIsSuccessfulNoOp() = runBlocking {
        val operation = operation(
            SyncOperationType.MOVIE_UNWATCHED,
            payload = MovieHistoryMutationContext(LibraryStatus.PLAN_TO_WATCH, false, null).encode(),
        )

        val result = remote.push(session, listOf(operation))

        assertEquals(setOf(operation.id), result.completedOperationIds)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun connectUsesPublicInfoThenAuthenticatedPreferencesAndReportsServerVersion() = runBlocking {
        server.enqueue(json("{\"version\":\"v26.9.10\",\"frontend_url\":\"https://frontend.example\"}"))
        server.enqueue(json("{\"preferences\":{},\"choices\":{}}"))
        val stages = mutableListOf<Pair<FloppyConnectionStage, String?>>()

        val settings = remote.connect(
            baseUrl = server.url("/proxy/").toString(),
            apiKey = "secret-token",
            allowInsecureLocalHttp = true,
            onStage = { stage, version -> stages += stage to version },
        )

        assertEquals("v26.9.10", settings.serverVersion)
        assertEquals(listOf(FloppyConnectionStage.AUTHENTICATING to "v26.9.10"), stages)
        val infoRequest = server.takeRequest()
        assertEquals("/proxy/api/v1/info/", infoRequest.path)
        assertTrue(infoRequest.getHeader("X-API-Key").isNullOrBlank())
        val preferencesRequest = server.takeRequest()
        assertEquals("/proxy/api/v1/user/preferences/", preferencesRequest.path)
        assertEquals("secret-token", preferencesRequest.getHeader("X-API-Key"))
    }

    @Test
    fun structuredUnwatchDeletesOnlyTheExactPreviousTimestamp() = runBlocking {
        val watchedAt = Instant.parse("2026-01-01T00:00:00Z")
        server.enqueue(json("{\"pagination\":{\"total\":2,\"limit\":200,\"offset\":0,\"next\":null,\"previous\":null},\"results\":[{\"consumption_id\":7,\"status\":3,\"end_date\":\"2026-01-01T00:00:00Z\"},{\"consumption_id\":8,\"status\":3,\"end_date\":\"2025-01-01T00:00:00Z\"}]}"))
        server.enqueue(MockResponse().setResponseCode(204))
        val operation = operation(
            SyncOperationType.MOVIE_UNWATCHED,
            payload = MovieHistoryMutationContext(LibraryStatus.WATCHING, true, watchedAt).encode(),
        )

        remote.push(session, listOf(operation))

        server.takeRequest()
        val delete = server.takeRequest()
        assertTrue(delete.path?.endsWith("/history/7/") == true)
    }

    private fun operation(
        type: SyncOperationType,
        mediaType: MediaType = MediaType.MOVIE,
        value: String? = null,
        payload: String? = null,
    ) = SyncOperation(
        id = type.name,
        type = type,
        mediaType = mediaType,
        mediaId = 42,
        title = "Movie",
        value = value,
        payload = payload,
        sourceVersion = 1L,
    )

    private fun typedPage(
        vararg rows: String,
        offset: Int = 0,
        total: Int = rows.size,
        next: String? = null,
    ) = json(
        "{\"pagination\":{\"total\":$total,\"limit\":200,\"offset\":$offset," +
            "\"next\":${next?.let { "\"$it\"" } ?: "null"},\"previous\":null}," +
            "\"results\":[${rows.joinToString(",")}]}"
    )

    private fun trackedMovie(
        id: Int = 42,
        consumptionId: Int = 7,
        status: Int = 3,
        endDate: String? = null,
    ) = "{\"id\":$consumptionId,\"consumption_id\":$consumptionId,\"item\":{\"media_id\":\"$id\",\"source\":\"tmdb\",\"media_type\":\"movie\"},\"status\":$status," +
        "\"end_date\":${endDate?.let { "\"$it\"" } ?: "null"}}"

    private fun trackedEpisode(show: Int, season: Int, episode: Int, endDate: String?) =
        "{\"id\":$episode,\"consumption_id\":$episode,\"item\":{\"media_id\":\"$show\",\"source\":\"tmdb\",\"season_number\":$season,\"episode_number\":$episode},\"status\":3," +
            "\"end_date\":${endDate?.let { "\"$it\"" } ?: "null"}}"

    private fun json(body: String) = MockResponse()
        .addHeader("Content-Type", "application/json")
        .setBody(body)
}

