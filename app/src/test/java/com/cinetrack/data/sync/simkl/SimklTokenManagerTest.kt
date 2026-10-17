package com.cinetrack.data.sync.simkl

import com.cinetrack.data.remote.SimklAuthService
import com.cinetrack.data.remote.SimklOAuthResponse
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference

class SimklTokenManagerTest {
    private class FakeStore(
        var access: String? = "simkl_at_old",
        var refresh: String? = "simkl_rt_grant",
        var expiry: Long? = null,
    ) : SimklTokenStore {
        var dropped = false
        override suspend fun accessToken() = access
        override fun refreshToken() = refresh
        override suspend fun expiresAt() = expiry
        override suspend fun storeRefreshed(accessToken: String, refreshToken: String?, expiresAt: Long?) {
            access = accessToken
            refreshToken?.let { refresh = it }
            expiry = expiresAt
        }
        override suspend fun dropGrant() {
            dropped = true
            access = null
            refresh = null
        }
    }

    private class FakeAuth(private val onRefresh: suspend (String) -> SimklOAuthResponse) : SimklAuthService {
        var refreshCalls = 0
        override suspend fun exchangeCode(code: String, clientId: String, codeVerifier: String, redirectUri: String, grantType: String) = error("unused")
        override suspend fun refresh(refreshToken: String, clientId: String, grantType: String): SimklOAuthResponse {
            refreshCalls++
            return onRefresh(refreshToken)
        }
        override suspend fun revoke(token: String, clientId: String): Response<ResponseBody> = error("unused")
    }

    private fun tokens(n: Int) = SimklOAuthResponse(accessToken = "simkl_at_new$n", expiresIn = 604_800, refreshToken = "simkl_rt_grant")
    private fun http(code: Int) = HttpException(Response.error<Any>(code, "{}".toResponseBody()))
    private val now = 1_000_000L

    private fun manager(store: FakeStore, auth: FakeAuth) =
        SimklTokenManager(store, { auth }, AtomicReference(null), clock = { now }, clientId = "client")

    @Test
    fun `a 401 refreshes once and stores the new token`() = runBlocking {
        val store = FakeStore()
        val auth = FakeAuth { tokens(1) }
        val manager = manager(store, auth)
        manager.load()
        assertEquals("simkl_at_new1", manager.refresh("simkl_at_old"))
        assertEquals("simkl_at_new1", store.access)
        assertEquals(now + 604_800_000L, store.expiry)
        assertEquals(1, auth.refreshCalls)
    }

    @Test
    fun `concurrent rejections of the same token refresh only once`() = runBlocking {
        val store = FakeStore()
        val auth = FakeAuth { delay(50); tokens(1) }
        val manager = manager(store, auth)
        manager.load()
        val results = (1..5).map { async { manager.refresh("simkl_at_old") } }.awaitAll()
        assertEquals(1, auth.refreshCalls)
        assertTrue(results.all { it == "simkl_at_new1" })
    }


    @Test
    fun `a rejected refresh token drops the grant`() = runBlocking {
        val store = FakeStore()
        val manager = manager(store, FakeAuth { throw http(400) })
        manager.load()
        assertNull(manager.refresh("simkl_at_old"))
        assertTrue(store.dropped)
        assertNull(manager.tokenForRequest())
    }

    @Test
    fun `a network failure keeps the grant`() = runBlocking {
        val store = FakeStore()
        val manager = manager(store, FakeAuth { throw IOException("offline") })
        manager.load()
        assertNull(manager.refresh("simkl_at_old"))
        assertEquals(false, store.dropped)
        assertEquals("simkl_at_old", manager.tokenForRequest())
    }

    @Test
    fun `a server error keeps the grant`() = runBlocking {
        val store = FakeStore()
        val manager = manager(store, FakeAuth { throw http(503) })
        manager.load()
        assertNull(manager.refresh("simkl_at_old"))
        assertEquals(false, store.dropped)
    }

    @Test
    fun `a token close to expiry is refreshed before the request`() {
        val store = FakeStore(expiry = now + 60_000)
        val auth = FakeAuth { tokens(1) }
        val manager = manager(store, auth)
        runBlocking { manager.load() }
        assertEquals("simkl_at_new1", manager.tokenForRequest())
        assertEquals("simkl_at_new1", manager.tokenForRequest())
        assertEquals(1, auth.refreshCalls)
    }

    @Test
    fun `a fresh token is sent without refreshing`() {
        val store = FakeStore(expiry = now + 5L * 24 * 60 * 60 * 1000)
        val auth = FakeAuth { tokens(1) }
        val manager = manager(store, auth)
        runBlocking { manager.load() }
        assertEquals("simkl_at_old", manager.tokenForRequest())
        assertEquals(0, auth.refreshCalls)
    }

    @Test
    fun `no refresh token means a new sign-in is needed`() = runBlocking {
        val store = FakeStore(refresh = null)
        val auth = FakeAuth { tokens(1) }
        val manager = manager(store, auth)
        manager.load()
        assertNull(manager.refresh("simkl_at_old"))
        assertTrue(store.dropped)
        assertEquals(0, auth.refreshCalls)
    }
}
