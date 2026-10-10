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
import com.cinetrack.data.sync.TrackingSyncError
import java.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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
    fun liveTimedEpisodeWatchUsesExplicitEventWithoutHistoryScan() = runBlocking {
        val capable = session.copy(capabilities = session.capabilities.copy(canEnsureEpisodeEvents = true))
        val watch = v2Operation("write:1", SyncOperationType.EPISODE_WATCHED, MediaType.TV, 42, payload = "2:3:2026-10-09T20:41:07.500Z")
        val rewatch = watch.copy(sourceVersion = 8L)
        val ids = listOf(watch, watch, rewatch).map { episodeClientEventId(capable.instanceId, it) }
        ids.forEach { id ->
            server.enqueue(json("""{"results":[{"client_event_id":"$id","season_number":2,"episode_number":3,"status":"created"}]}"""))
        }

        listOf(watch, watch, rewatch).forEach { assertEquals(setOf(it.id), remote.push(capable, listOf(it)).completedOperationIds) }

        assertEquals(3, server.requestCount)
        val sent = (1..3).map {
            val request = server.takeRequest()
            assertEquals("/proxy/api/v1/media/tv/tmdb/42/episodes/ensure/", request.path)
            Json.parseToJsonElement(request.body.readUtf8()).jsonObject["events"]!!.jsonArray.single().jsonObject
        }
        assertEquals(Instant.parse("2026-10-09T20:41:07.500Z"), Instant.parse(sent[0]["watched_at"]!!.jsonPrimitive.content))
        // A retry of the same generation repeats the id; a rewatch gets a new one.
        assertEquals(sent[0]["client_event_id"], sent[1]["client_event_id"])
        assertTrue(sent[0]["client_event_id"] != sent[2]["client_event_id"])
    }

    @Test
    fun liveEpisodeWatchWithoutKnownTimeKeepsLegacyPath() = runBlocking {
        val capable = session.copy(capabilities = session.capabilities.copy(canEnsureEpisodeEvents = true))
        server.enqueue(typedPage())
        server.enqueue(json("{}"))

        remote.push(capable, listOf(v2Operation("write:2", SyncOperationType.EPISODE_WATCHED, MediaType.TV, 42, payload = "2:3")))

        assertEquals(2, server.requestCount)
        assertTrue(server.takeRequest().path!!.startsWith("/proxy/api/v1/media/episode/"))
        assertTrue(server.takeRequest().path!!.contains("/episodes/") )
    }

    @Test
    fun v2MovieWaveIsOneBatchRequestWithExactTimestampsAndEventIds() = runBlocking {
        val context = FloppyBootstrapTransportContext(session.instanceId, canEnsureEpisodeEvents = true, canBootstrapV2 = true)
        val watchedAt = Instant.parse("2024-05-06T07:08:09.123Z")
        val operations = listOf(
            v2Operation("m42-status", SyncOperationType.LIBRARY_STATUS, MediaType.MOVIE, 42, value = "COMPLETED"),
            v2Operation("m42-watch", SyncOperationType.MOVIE_WATCHED, MediaType.MOVIE, 42, payload = watchedAt.toString()),
            v2Operation("m43-status", SyncOperationType.LIBRARY_STATUS, MediaType.MOVIE, 43, value = "PLAN_TO_WATCH"),
            v2Operation("m44-watch", SyncOperationType.MOVIE_WATCHED, MediaType.MOVIE, 44, payload = "2023-01-02T03:04:05Z"),
        )
        server.enqueue(json(results("42" to "created", "43" to "already_satisfied", "44" to "created")))

        val result = remote.push(session, operations, context)

        assertEquals(1, server.requestCount)
        val request = server.takeRequest()
        assertEquals("/proxy/api/v1/cinetrack/bootstrap/movies/ensure/", request.path)
        val movies = Json.parseToJsonElement(request.body.readUtf8()).jsonObject["movies"]!!.jsonArray
        assertEquals(listOf("42", "43", "44"), movies.map { it.jsonObject["media_id"]!!.jsonPrimitive.content })
        val watch = movies[0].jsonObject["watch"]!!.jsonObject
        assertEquals(watchedAt, Instant.parse(watch["watched_at"]!!.jsonPrimitive.content))
        assertEquals("cinetrack:test-instance:m42-watch:7", watch["client_event_id"]!!.jsonPrimitive.content)
        assertTrue(movies[1].jsonObject["watch"] == null || movies[1].jsonObject["watch"] is JsonNull)
        assertEquals(operations.map(SyncOperation::id).toSet(), result.completedOperationIds)
    }

    @Test
    fun v2ShowBatchIsOneRequestAndNeverScansHistory() = runBlocking {
        val context = FloppyBootstrapTransportContext(session.instanceId, canEnsureEpisodeEvents = true, canBootstrapV2 = true)
        val operations = (1..3).map { v2Operation("show-$it", SyncOperationType.LIBRARY_STATUS, MediaType.TV, 100 + it, value = "WATCHING") }
        server.enqueue(json(results("101" to "created", "102" to "created", "103" to "already_satisfied")))

        val result = remote.push(session, operations, context)

        assertEquals(1, server.requestCount)
        val request = server.takeRequest()
        assertEquals("/proxy/api/v1/cinetrack/bootstrap/shows/ensure/", request.path)
        assertEquals(3, Json.parseToJsonElement(request.body.readUtf8()).jsonObject["shows"]!!.jsonArray.size)
        assertEquals(operations.map(SyncOperation::id).toSet(), result.completedOperationIds)
    }

    @Test
    fun v2EpisodeUnitSpanningGapsAndSeasonsIsOneExplicitEventRequest() = runBlocking {
        val context = FloppyBootstrapTransportContext(session.instanceId, canEnsureEpisodeEvents = true, canBootstrapV2 = true)
        val operations = listOf(
            v2Operation("e1", SyncOperationType.EPISODE_WATCHED, MediaType.TV, 42, payload = "1:1:2024-01-01T20:00:00.250Z"),
            v2Operation("e3", SyncOperationType.EPISODE_WATCHED, MediaType.TV, 42, payload = "1:3:2024-01-03T21:13:00Z"),
            v2Operation("e21", SyncOperationType.EPISODE_WATCHED, MediaType.TV, 42, payload = "2:1:2024-02-01T22:00:00Z"),
        )
        val ids = operations.map { episodeClientEventId(session.instanceId, it) }
        val coordinates = listOf(1 to 1, 1 to 3, 2 to 1)
        server.enqueue(json("""{"results":[${ids.zip(coordinates).joinToString(",") { (id, c) -> """{"client_event_id":"$id","season_number":${c.first},"episode_number":${c.second},"status":"created"}""" }}]}"""))

        val result = remote.push(session, operations, context)

        assertEquals(1, server.requestCount)
        val request = server.takeRequest()
        assertEquals("/proxy/api/v1/media/tv/tmdb/42/episodes/ensure/", request.path)
        val events = Json.parseToJsonElement(request.body.readUtf8()).jsonObject["events"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf(1 to 1, 1 to 3, 2 to 1), events.map { it["season_number"]!!.jsonPrimitive.content.toInt() to it["episode_number"]!!.jsonPrimitive.content.toInt() })
        assertEquals(
            listOf("2024-01-01T20:00:00.250Z", "2024-01-03T21:13:00Z", "2024-02-01T22:00:00Z").map(Instant::parse),
            events.map { Instant.parse(it["watched_at"]!!.jsonPrimitive.content) },
        )
        assertEquals(ids, events.map { it["client_event_id"]!!.jsonPrimitive.content })
        assertEquals(operations.map(SyncOperation::id).toSet(), result.completedOperationIds)
    }

    @Test
    fun refusedEpisodesAreReportedWhileTheRestOfTheRequestIsApplied() = runBlocking {
        val context = FloppyBootstrapTransportContext(session.instanceId, canEnsureEpisodeEvents = true, canBootstrapV2 = true)
        val valid = v2Operation("e1", SyncOperationType.EPISODE_WATCHED, MediaType.TV, 2316, payload = "6:1:2024-01-01T20:00:00Z")
        val unknown = v2Operation("e26", SyncOperationType.EPISODE_WATCHED, MediaType.TV, 2316, payload = "6:26:2024-01-02T20:00:00Z")
        val validId = episodeClientEventId(session.instanceId, valid)
        val unknownId = episodeClientEventId(session.instanceId, unknown)
        server.enqueue(json("""{"results":[{"client_event_id":"$validId","season_number":6,"episode_number":1,"status":"created"},{"client_event_id":"$unknownId","season_number":6,"episode_number":26,"status":"metadata_unavailable","reason":"tvdb_unavailable"}]}"""))

        val result = remote.push(session, listOf(valid, unknown), context)

        assertEquals(setOf("e1"), result.completedOperationIds)
        assertEquals(mapOf("e26" to "TV:2316 S06E26 metadata_unavailable (tvdb_unavailable)"), result.rejectedOperationIds)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun repairResendAsksFloppyToMoveMisplacedPlays() = runBlocking {
        val context = FloppyBootstrapTransportContext(session.instanceId, canEnsureEpisodeEvents = true, canBootstrapV2 = true, repairCoordinates = true)
        val episode = v2Operation("e15", SyncOperationType.EPISODE_WATCHED, MediaType.TV, 2316, payload = "4:15:2024-01-01T20:00:00Z")
        val eventId = episodeClientEventId(session.instanceId, episode)
        server.enqueue(json("""{"results":[{"client_event_id":"$eventId","season_number":4,"episode_number":15,"status":"already_satisfied","stored_season_number":4,"stored_episode_number":14}]}"""))

        val result = remote.push(session, listOf(episode), context)

        assertEquals(setOf("e15"), result.completedOperationIds)
        val sent = server.takeRequest().body.readUtf8()
        assertTrue(sent, sent.contains("\"repair_coordinates\":true"))
    }

    @Test
    fun bootstrapNotFoundIsFinalAndTranslatedCoordinatesAreReported() = runBlocking {
        val context = FloppyBootstrapTransportContext(session.instanceId, canEnsureEpisodeEvents = true, canBootstrapV2 = true)
        val translated = v2Operation("e62", SyncOperationType.EPISODE_WATCHED, MediaType.TV, 37854, payload = "1:62:2024-01-01T20:00:00Z")
        val double = v2Operation("e26", SyncOperationType.EPISODE_WATCHED, MediaType.TV, 37854, payload = "6:26:2024-01-02T20:00:00Z")
        val translatedId = episodeClientEventId(session.instanceId, translated)
        val doubleId = episodeClientEventId(session.instanceId, double)
        server.enqueue(json("""{"results":[{"client_event_id":"$translatedId","season_number":1,"episode_number":62,"status":"created","stored_season_number":2,"stored_episode_number":1},{"client_event_id":"$doubleId","season_number":6,"episode_number":26,"status":"not_found","reason":"not_in_tvdb","title":"A Parks and Recreation Special"}]}"""))

        val result = remote.push(session, listOf(translated, double), context)

        assertEquals(setOf("e62"), result.completedOperationIds)
        assertEquals(mapOf("e62" to "2:1"), result.storedCoordinates)
        assertEquals(mapOf("e26" to "TV:37854 S06E26 has no Floppy counterpart (not_in_tvdb)"), result.unmatchedOperationIds)
        assertEquals(mapOf("e26" to "A Parks and Recreation Special"), result.unmatchedTitles)
        assertTrue(result.rejectedOperationIds.isEmpty())
    }

    @Test
    fun liveNotFoundStaysRetryable() = runBlocking {
        val capable = session.copy(capabilities = session.capabilities.copy(canEnsureEpisodeEvents = true))
        val watch = v2Operation("write:9", SyncOperationType.EPISODE_WATCHED, MediaType.TV, 1399, payload = "8:7:2026-10-09T20:00:00Z")
        server.enqueue(json("""{"results":[{"client_event_id":"${episodeClientEventId(capable.instanceId, watch)}","season_number":8,"episode_number":7,"status":"not_found"}]}"""))

        val result = remote.push(capable, listOf(watch))

        assertTrue(result.unmatchedOperationIds.isEmpty())
        assertEquals(setOf("write:9"), result.rejectedOperationIds.keys)
    }

    @Test
    fun episodeResponseWithAnUnknownStatusAcknowledgesNothing() = runBlocking {
        val context = FloppyBootstrapTransportContext(session.instanceId, canEnsureEpisodeEvents = true, canBootstrapV2 = true)
        val op = v2Operation("e1", SyncOperationType.EPISODE_WATCHED, MediaType.TV, 2316, payload = "6:1:2024-01-01T20:00:00Z")
        server.enqueue(json("""{"results":[{"client_event_id":"${episodeClientEventId(session.instanceId, op)}","season_number":6,"episode_number":1,"status":"maybe"}]}"""))

        val error = runCatching { remote.push(session, listOf(op), context) }.exceptionOrNull()

        assertTrue(error is TrackingSyncError.InvalidRemoteData)
    }

    @Test
    fun jsonNotFoundIsARefusalNotAMissingRoute() = runBlocking {
        val context = FloppyBootstrapTransportContext(session.instanceId, canEnsureEpisodeEvents = true, canBootstrapV2 = true)
        server.enqueue(MockResponse().setResponseCode(404).setHeader("Content-Type", "application/json").setBody("""{"detail":"Could not resolve episode events."}"""))

        val error = runCatching {
            remote.push(session, listOf(v2Operation("e1", SyncOperationType.EPISODE_WATCHED, MediaType.TV, 2316, payload = "6:1:2024-01-01T20:00:00Z")), context)
        }.exceptionOrNull()

        assertTrue(error is TrackingSyncError.InvalidRemoteData)
        assertEquals("Floppy returned HTTP 404: Could not resolve episode events.", error!!.message)
    }

    @Test
    fun v2BatchServerFailureAcknowledgesNothing() = runBlocking {
        val context = FloppyBootstrapTransportContext(session.instanceId, canBootstrapV2 = true)
        server.enqueue(MockResponse().setResponseCode(503))

        val error = runCatching {
            remote.push(session, listOf(v2Operation("m42", SyncOperationType.LIBRARY_STATUS, MediaType.MOVIE, 42, value = "COMPLETED")), context)
        }.exceptionOrNull()

        assertTrue(error is TrackingSyncError.ProviderUnavailable)
    }

    @Test
    fun v2ResponseMissingAnItemAcknowledgesNothing() = runBlocking {
        val context = FloppyBootstrapTransportContext(session.instanceId, canBootstrapV2 = true)
        server.enqueue(json(results("42" to "created")))

        val error = runCatching {
            remote.push(
                session,
                listOf(
                    v2Operation("m42", SyncOperationType.LIBRARY_STATUS, MediaType.MOVIE, 42, value = "COMPLETED"),
                    v2Operation("m43", SyncOperationType.LIBRARY_STATUS, MediaType.MOVIE, 43, value = "COMPLETED"),
                ),
                context,
            )
        }.exceptionOrNull()

        assertTrue(error is TrackingSyncError.InvalidRemoteData)
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
    fun connectUsesPublicInfoThenAuthenticatedConnectionProbeAndReportsServerVersion() = runBlocking {
        server.enqueue(json("{\"version\":\"v26.9.10\",\"frontend_url\":\"https://frontend.example\",\"api_extensions\":{\"cinetrack_episode_events_v1\":true,\"cinetrack_bootstrap_v2\":true,\"episode_sql_pagination\":true}}"))
        server.enqueue(json("{\"authenticated\":true,\"account_id\":\"opaque-account\",\"user\":\"opaque-account\",\"server_version\":\"v26.9.10\",\"api_extensions\":{\"cinetrack_episode_events_v1\":true,\"cinetrack_bootstrap_v2\":true,\"episode_sql_pagination\":true}}"))
        val stages = mutableListOf<Pair<FloppyConnectionStage, String?>>()

        val settings = remote.connect(
            baseUrl = server.url("/proxy/").toString(),
            apiKey = "secret-token",
            allowInsecureLocalHttp = true,
            onStage = { stage, version -> stages += stage to version },
        )

        assertEquals("v26.9.10", settings.serverVersion)
        assertEquals(FLOPPY_PROBE_ACCOUNT_PREFIX + "opaque-account", settings.accountIdentity)
        assertTrue(settings.capabilities.canEnsureEpisodeEvents)
        assertTrue(settings.capabilities.canBootstrapV2)
        assertEquals(listOf(FloppyConnectionStage.AUTHENTICATING to "v26.9.10"), stages)
        val infoRequest = server.takeRequest()
        assertEquals("/proxy/api/v1/info/", infoRequest.path)
        assertTrue(infoRequest.getHeader("X-API-Key").isNullOrBlank())
        val connectionRequest = server.takeRequest()
        assertEquals("/proxy/api/v1/cinetrack/connection/", connectionRequest.path)
        assertEquals("secret-token", connectionRequest.getHeader("X-API-Key"))
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

    private fun v2Operation(
        id: String,
        type: SyncOperationType,
        mediaType: MediaType,
        mediaId: Int,
        value: String? = null,
        payload: String? = null,
    ) = SyncOperation(
        id = id,
        type = type,
        mediaType = mediaType,
        mediaId = mediaId,
        title = "Title $mediaId",
        value = value,
        payload = payload,
        sourceVersion = 7L,
    )

    private fun results(vararg outcomes: Pair<String, String>) =
        """{"results":[${outcomes.joinToString(",") { (id, status) -> """{"source":"tmdb","media_id":"$id","status":"$status"}""" }}]}"""

    private fun json(body: String) = MockResponse()
        .addHeader("Content-Type", "application/json")
        .setBody(body)
}
