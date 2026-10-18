package com.cinetrack.data.sync.floppy

import com.cinetrack.data.sync.TrackingSyncError
import com.cinetrack.data.sync.floppy.network.FloppyApiClientFactory
import com.cinetrack.data.sync.floppy.network.FloppyApiErrorMapper
import kotlinx.serialization.SerializationException
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlinx.coroutines.runBlocking

class FloppyApiClientTest {
    private lateinit var server: MockWebServer

    @Before fun setUp() { server = MockWebServer().also { it.start() } }
    @After fun tearDown() { server.shutdown() }

    @Test fun infoIsPublicAndAuthenticatedProbeCarriesOnlyApiKey() = runBlocking {
        server.enqueue(MockResponse().addHeader("Content-Type", "application/json").setBody("{\"version\":\"26.1\",\"debug\":false,\"frontend_url\":\"\",\"language\":\"en\",\"timezone\":\"UTC\",\"admin_enabled\":false,\"track_time\":true}"))
        server.enqueue(MockResponse().addHeader("Content-Type", "application/json").setBody("{\"authenticated\":true,\"account_id\":\"opaque\",\"user\":\"opaque\"}"))
        val api = FloppyApiClientFactory().get(server.url("/").toString(), "secret-token")
        api.info()
        val info = server.takeRequest()
        assertEquals("/api/v1/info/", info.path)
        assertEquals(null, info.getHeader("X-API-Key"))

        api.connection()
        val probe = server.takeRequest()
        assertEquals("/api/v1/cinetrack/connection/", probe.path)
        assertFalse(probe.path.orEmpty().contains("user/preferences"))
        assertEquals("secret-token", probe.getHeader("X-API-Key"))
    }

    @Test fun charactersParseBothPicturesAndToleratePartialRows() = runBlocking {
        server.enqueue(MockResponse().addHeader("Content-Type", "application/json").setBody(
            "{\"tvdb_id\":\"81189\",\"characters\":[" +
                "{\"name\":\"Walter White\",\"person_name\":\"Bryan Cranston\",\"person_id\":\"101\",\"character_image\":\"https://artworks.thetvdb.com/c.jpg\",\"person_image\":\"https://artworks.thetvdb.com/p.jpg\",\"sort\":1}," +
                "{\"name\":\"Jesse Pinkman\",\"person_name\":\"Aaron Paul\",\"character_image\":null}]}",
        ))
        val api = FloppyApiClientFactory().get(server.url("/").toString(), "secret-token")
        val response = api.characters("tv", "1396")

        assertEquals("/api/v1/media/tv/tmdb/1396/characters/", server.takeRequest().path)
        assertEquals("81189", response.tvdbId)
        assertEquals("https://artworks.thetvdb.com/c.jpg", response.characters[0].characterImage)
        assertEquals("Bryan Cranston", response.characters[0].personName)
        assertEquals(null, response.characters[1].characterImage)
    }

    @Test fun charactersWithoutTvdbReturnAnEmptyListAndReason() = runBlocking {
        server.enqueue(MockResponse().addHeader("Content-Type", "application/json").setBody(
            "{\"tvdb_id\":null,\"reason\":\"tvdb_not_configured\",\"characters\":[]}",
        ))
        val response = FloppyApiClientFactory().get(server.url("/").toString(), "secret-token").characters("movie", "603")

        assertEquals("tvdb_not_configured", response.reason)
        assertEquals(emptyList<FloppyCharacterDto>(), response.characters)
    }

    @Test fun malformedJsonMapsToInvalidRemoteDataInsteadOfInvalidUrl() {
        val mapped = FloppyApiErrorMapper.map(SerializationException("malformed"))
        assertEquals(true, mapped is TrackingSyncError.InvalidRemoteData)
    }
}
