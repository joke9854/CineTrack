package com.cinetrack.data.sync.floppy

import com.cinetrack.data.sync.SyncOperation
import com.cinetrack.data.sync.SyncOperationType
import com.cinetrack.data.sync.MovieHistoryMutationContext
import com.cinetrack.data.sync.floppy.network.FloppyApiClientFactory
import com.cinetrack.data.sync.floppy.network.FloppyRemoteDataSource
import com.cinetrack.domain.MediaType
import com.cinetrack.domain.LibraryStatus
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
    fun movieWatchUsesExactHistoryBeforeCreatingAConsumption() = runBlocking {
        server.enqueue(json("{\"pagination\":{\"total\":0,\"limit\":200,\"offset\":0,\"next\":null,\"previous\":null},\"results\":[]}"))
        server.enqueue(json("{}"))

        val watchedAt = Instant.parse("2026-01-01T00:00:00Z")
        remote.push(session, listOf(operation(SyncOperationType.MOVIE_WATCHED, payload = watchedAt.toString())))

        assertEquals("/proxy/api/v1/media/movie/tmdb/42/history/?limit=200&offset=0", server.takeRequest().path)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/proxy/api/v1/media/movie/", request.path)
        assertEquals("{\"source\":\"tmdb\",\"media_id\":\"42\",\"title\":\"Movie\",\"status\":3,\"end_date\":\"2026-01-01T00:00:00Z\"}", request.body.readUtf8())
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
    fun episodeWatchChecksHistoryBeforeCallingWatchRoute() = runBlocking {
        server.enqueue(json("{\"pagination\":{\"total\":0,\"limit\":200,\"offset\":0,\"next\":null,\"previous\":null},\"results\":[]}"))
        server.enqueue(json("{}"))

        remote.push(session, listOf(operation(
            SyncOperationType.EPISODE_WATCHED,
            mediaType = MediaType.TV,
            payload = "2:3:2026-01-01T00:00:00Z",
        )))

        assertEquals("/proxy/api/v1/history/?flat=1&limit=200&offset=0&types=episode", server.takeRequest().path)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/proxy/api/v1/media/tv/tmdb/42/2/episodes/3/watch/", request.path)
        assertEquals("{\"watched_at\":\"2026-01-01T00:00:00Z\"}", request.body.readUtf8())
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
        assertEquals("/proxy/api/v1/media/movie/", post.path)
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

    private fun json(body: String) = MockResponse()
        .addHeader("Content-Type", "application/json")
        .setBody(body)
}

