package com.cinetrack.data.sync.floppy

import com.cinetrack.data.sync.ConnectionResult
import com.cinetrack.data.sync.TrackingSyncError
import com.cinetrack.data.sync.floppy.network.FloppyApiClientFactory
import com.cinetrack.data.sync.floppy.network.FloppyRemoteDataSource
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FloppyConnectionPreflightTest {
    private lateinit var server: MockWebServer
    private lateinit var remote: FloppyRemoteDataSource

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        remote = FloppyRemoteDataSource(FloppyApiClientFactory())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun connectUsesInfoThenAuthenticatedCineTrackProbeAndNeverPreferences() = runBlocking {
        server.enqueue(info(canBootstrapV2 = true, canEnsureEpisodes = true))
        server.enqueue(probe())

        val settings = remote.connect(server.url("/proxy/").toString(), "tracking-token", allowInsecureLocalHttp = true)

        assertEquals("opaque-account", settings.accountIdentity)
        assertEquals("26.9.17", settings.serverVersion)
        assertTrue(settings.capabilities.canBootstrapV2)
        assertTrue(settings.capabilities.canEnsureEpisodeEvents)

        val infoRequest = server.takeRequest()
        val probeRequest = server.takeRequest()
        assertEquals("/proxy/api/v1/info/", infoRequest.path)
        assertNull(infoRequest.getHeader("X-API-Key"))
        assertEquals("/proxy/api/v1/cinetrack/connection/", probeRequest.path)
        assertEquals("tracking-token", probeRequest.getHeader("X-API-Key"))
        assertFalse(probeRequest.path.orEmpty().contains("user/preferences"))
    }

    @Test
    fun testConnectionUsesFreshNetworkProbe() = runBlocking {
        server.enqueue(info(canBootstrapV2 = false, canEnsureEpisodes = false))
        server.enqueue(probe())

        val result = remote.test(server.url("/").toString(), "tracking-token", allowInsecureLocalHttp = true)

        assertEquals(ConnectionResult.Connected, result)
        assertEquals("/api/v1/info/", server.takeRequest().path)
        assertEquals("/api/v1/cinetrack/connection/", server.takeRequest().path)
    }

    @Test
    fun redirectIsNotClassifiedAsProviderUnavailable() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(302)
                .setHeader("Location", "https://final.example/api/?token=must-not-survive#fragment"),
        )

        val result = remote.test(server.url("/").toString(), "tracking-token", allowInsecureLocalHttp = true)

        val error = (result as ConnectionResult.Failed).error
        assertTrue(error is TrackingSyncError.ApiRedirect)
        assertEquals("https://final.example/api/", (error as TrackingSyncError.ApiRedirect).location)
    }

    @Test
    fun forbiddenProbeIsTokenScopeFailure() = runBlocking {
        server.enqueue(info(canBootstrapV2 = true, canEnsureEpisodes = true))
        server.enqueue(MockResponse().setResponseCode(403).setBody("{\"detail\":\"insufficient_scope\"}"))

        val result = remote.test(server.url("/").toString(), "tracking-token", allowInsecureLocalHttp = true)

        assertTrue((result as ConnectionResult.Failed).error is TrackingSyncError.TokenScope)
    }

    @Test
    fun malformedProbeIsInvalidRemoteData() = runBlocking {
        server.enqueue(info(canBootstrapV2 = true, canEnsureEpisodes = true))
        server.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody("<html>not-json</html>"))

        val result = remote.test(server.url("/").toString(), "tracking-token", allowInsecureLocalHttp = true)

        assertTrue((result as ConnectionResult.Failed).error is TrackingSyncError.InvalidRemoteData)
    }

    private fun info(canBootstrapV2: Boolean, canEnsureEpisodes: Boolean) = json(
        """{"version":"26.9.17","api_extensions":{"cinetrack_bootstrap_v2":$canBootstrapV2,"cinetrack_episode_events_v1":$canEnsureEpisodes,"episode_sql_pagination":true}}""",
    )

    private fun probe() = json(
        """{"authenticated":true,"account_id":"opaque-account","user":"opaque-account","server_version":"26.9.17","api_extensions":{"cinetrack_bootstrap_v2":true,"cinetrack_episode_events_v1":true,"episode_sql_pagination":true}}""",
    )

    private fun json(body: String) = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(body)
}
