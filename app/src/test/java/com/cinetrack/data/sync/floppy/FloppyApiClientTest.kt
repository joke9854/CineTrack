package com.cinetrack.data.sync.floppy

import com.cinetrack.data.sync.floppy.network.FloppyApiClientFactory
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlinx.coroutines.runBlocking

class FloppyApiClientTest {
    private lateinit var server: MockWebServer

    @Before fun setUp() { server = MockWebServer().also { it.start() } }
    @After fun tearDown() { server.shutdown() }

    @Test fun infoIsPublicAndAuthenticatedRequestsCarryOnlyApiKey() = runBlocking {
        server.enqueue(MockResponse().addHeader("Content-Type", "application/json").setBody("{\"version\":\"26.1\",\"debug\":false,\"frontend_url\":\"\",\"language\":\"en\",\"timezone\":\"UTC\",\"admin_enabled\":false,\"track_time\":true}"))
        server.enqueue(MockResponse().addHeader("Content-Type", "application/json").setBody("{\"username\":\"alice\"}"))
        val api = FloppyApiClientFactory().get(server.url("/").toString(), "secret-token")
        api.info()
        assertEquals(null, server.takeRequest().getHeader("X-API-Key"))
        api.preferences()
        assertEquals("secret-token", server.takeRequest().getHeader("X-API-Key"))
    }
}
