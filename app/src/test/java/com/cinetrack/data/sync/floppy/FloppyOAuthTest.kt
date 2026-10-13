package com.cinetrack.data.sync.floppy

import com.cinetrack.data.sync.floppy.network.FloppyApiClientFactory
import com.cinetrack.data.sync.floppy.network.FloppyOAuthClient
import com.cinetrack.data.sync.floppy.network.FloppyOAuthRules
import com.cinetrack.data.sync.floppy.network.FloppyOAuthTokenStore
import com.cinetrack.data.sync.floppy.network.FloppyOAuthTokens
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FloppyOAuthTest {
    private lateinit var server: MockWebServer
    private lateinit var baseUrl: String
    private val store = MemoryStore()
    private var now = 1_000_000L
    private val credential = FloppyOAuthRules.credential("grant-1")

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        baseUrl = server.url("/proxy/").toString()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun client() = FloppyOAuthClient(store, clock = { now })

    private fun tokens(access: String, refresh: String, expiresIn: Long = 3_600_000) =
        FloppyOAuthTokens(access, refresh, now + expiresIn)

    private fun tokenResponse(access: String, refresh: String) = MockResponse()
        .addHeader("Content-Type", "application/json")
        .setBody("""{"access_token":"$access","refresh_token":"$refresh","expires_in":604800,"token_type":"Bearer"}""")

    @Test
    fun `a fresh access token is used without any request`() {
        store.save("grant-1", tokens("access-1", "refresh-1"))

        assertEquals("access-1", client().accessToken(baseUrl, credential))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `an expiring token is renewed and the rotated refresh token is kept`() {
        store.save("grant-1", tokens("access-1", "refresh-1", expiresIn = 60_000))
        server.enqueue(tokenResponse("access-2", "refresh-2"))

        assertEquals("access-2", client().accessToken(baseUrl, credential))

        val request = server.takeRequest()
        assertEquals("/proxy/o/token/", request.path)
        val body = request.body.readUtf8()
        assertTrue(body, "grant_type=refresh_token" in body && "refresh_token=refresh-1" in body && "client_id=cinetrack" in body)
        assertEquals("refresh-2", store.load("grant-1")?.refreshToken)
    }

    @Test
    fun `a refused renewal ends the sign-in instead of retrying`() {
        store.save("grant-1", tokens("access-1", "refresh-1", expiresIn = 0))
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":"invalid_grant"}"""))

        assertNull(client().accessToken(baseUrl, credential))
        assertNull(store.load("grant-1"))
    }

    @Test
    fun `concurrent rejections renew only once`() {
        // Floppy revokes the whole sign-in when a used refresh token comes back.
        store.save("grant-1", tokens("access-1", "refresh-1"))
        server.enqueue(tokenResponse("access-2", "refresh-2"))
        val oauth = client()
        val start = CountDownLatch(1)
        val results = mutableListOf<String?>()
        val workers = List(4) {
            thread {
                start.await()
                val token = oauth.accessToken(baseUrl, credential, rejected = "access-1")
                synchronized(results) { results += token }
            }
        }
        start.countDown()
        workers.forEach { it.join(TimeUnit.SECONDS.toMillis(10)) }

        assertEquals(1, server.requestCount)
        assertEquals(List(4) { "access-2" }, results)
    }

    @Test
    fun `requests carry the bearer token and retry once after a 401`() = runBlocking {
        store.save("grant-1", tokens("access-1", "refresh-1"))
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"detail":"Invalid token"}"""))
        server.enqueue(tokenResponse("access-2", "refresh-2"))
        server.enqueue(MockResponse().addHeader("Content-Type", "application/json").setBody("""{"authenticated":true,"account_id":"7"}"""))
        val api = FloppyApiClientFactory(client()).get(baseUrl, credential)

        val probe = api.connection()

        assertTrue(probe.authenticated)
        val first = server.takeRequest()
        assertEquals("Bearer access-1", first.getHeader("Authorization"))
        assertNull(first.getHeader("X-API-Key"))
        assertEquals("/proxy/o/token/", server.takeRequest().path)
        assertEquals("Bearer access-2", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `api keys are still sent as before`() = runBlocking {
        server.enqueue(MockResponse().addHeader("Content-Type", "application/json").setBody("""{"authenticated":true,"account_id":"7"}"""))
        val api = FloppyApiClientFactory(client()).get(baseUrl, "plain-api-key")

        api.connection()

        val request = server.takeRequest()
        assertEquals("plain-api-key", request.getHeader("X-API-Key"))
        assertNull(request.getHeader("Authorization"))
    }

    @Test
    fun `sign-in callbacks are checked before the code is used`() {
        val url = "https://floppy.example/"
        assertNull(FloppyOAuthRules.callbackProblem("s1", "s1", null, "https://floppy.example", url))
        assertNull(FloppyOAuthRules.callbackProblem("s1", "s1", null, null, url))
        assertTrue(FloppyOAuthRules.callbackProblem("s1", "s2", null, null, url) != null)
        assertTrue(FloppyOAuthRules.callbackProblem(null, "s1", null, null, url) != null)
        assertTrue(FloppyOAuthRules.callbackProblem("s1", "s1", null, "https://evil.example", url) != null)
        assertEquals("Sign-in was cancelled.", FloppyOAuthRules.callbackProblem("s1", "s1", "access_denied", null, url))
    }

    @Test
    fun `the authorize link targets the cinetrack app with PKCE`() {
        val link = FloppyOAuthRules.authorizeUrl("https://floppy.example/base/", "challenge", "state-1")

        assertTrue(link, link.startsWith("https://floppy.example/base/o/authorize/?"))
        assertTrue(link, "client_id=cinetrack" in link)
        assertTrue(link, "redirect_uri=cinetrack%3A%2F%2Ffloppy" in link)
        assertTrue(link, "code_challenge_method=S256" in link)
        assertTrue(link, "scope=" !in link)
    }

    private class MemoryStore : FloppyOAuthTokenStore {
        private val values = mutableMapOf<String, FloppyOAuthTokens>()

        @Synchronized
        override fun load(grantId: String): FloppyOAuthTokens? = values[grantId]

        @Synchronized
        override fun save(grantId: String, tokens: FloppyOAuthTokens?) {
            if (tokens == null) values.remove(grantId) else values[grantId] = tokens
        }
    }
}
