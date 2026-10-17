package com.cinetrack.data.sync.simkl

import com.cinetrack.BuildConfig
import com.cinetrack.data.remote.SimklAuthService
import com.cinetrack.domain.SimklAuthRules
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import java.util.concurrent.atomic.AtomicReference

/** What [SimklTokenManager] needs from storage; AppPreferences in the app, a fake in tests. */
interface SimklTokenStore {
    suspend fun accessToken(): String?
    fun refreshToken(): String?
    suspend fun expiresAt(): Long?
    suspend fun storeRefreshed(accessToken: String, refreshToken: String?, expiresAt: Long?)
    /** The grant is gone (revoked or expired refresh token): drop it and ask for a new sign-in. */
    suspend fun dropGrant()
}

/**
 * The single owner of Simkl token refreshes. Refreshing a V2 grant kills the previous access
 * token immediately, so the UI and background sync must never refresh independently: every
 * refresh goes through [mutex], and a caller whose token was already replaced just takes the
 * new one instead of refreshing again.
 */
class SimklTokenManager(
    private val store: SimklTokenStore,
    private val auth: () -> SimklAuthService,
    private val current: AtomicReference<String?>,
    private val clock: () -> Long = System::currentTimeMillis,
    private val clientId: String = BuildConfig.SIMKL_CLIENT_ID,
) {
    private val mutex = Mutex()
    @Volatile private var expiresAt: Long? = null

    /** Loads the stored grant at startup. */
    suspend fun load(): String? {
        val token = store.accessToken()
        expiresAt = token?.let { store.expiresAt() }
        current.set(token)
        return token
    }

    /** Called after a new sign-in or a disconnect. */
    suspend fun onSignedIn() { load() }

    fun onSignedOut() {
        expiresAt = null
        current.set(null)
    }

    /** The token to send, refreshed first when it is within a day of expiring. Blocking; OkHttp threads only. */
    fun tokenForRequest(): String? {
        val token = current.get() ?: return null
        if (!SimklAuthRules.needsRefresh(expiresAt, clock())) return token
        return runBlocking { refresh(rejected = token) } ?: current.get()
    }

    /** OkHttp authenticator entry point after a 401 carrying [rejected]. */
    fun refreshAfterRejection(rejected: String): String? = runBlocking { refresh(rejected) }

    /**
     * Refreshes the grant unless [rejected] was already replaced. Returns the token to use, or
     * null when there is none. A network or server failure keeps the grant for the next try;
     * only Simkl rejecting the refresh token itself drops it.
     */
    suspend fun refresh(rejected: String?): String? = mutex.withLock {
        val latest = current.get() ?: return@withLock null
        if (rejected != null && latest != rejected) return@withLock latest
        val refreshToken = store.refreshToken() ?: run {
            dropGrant()
            return@withLock null
        }
        val response = try {
            auth().refresh(refreshToken = refreshToken, clientId = clientId)
        } catch (error: HttpException) {
            if (error.code() == 400 || error.code() == 401) dropGrant()
            return@withLock null
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (_: Exception) {
            return@withLock null
        }
        val newExpiry = SimklAuthRules.expiresAt(response.expiresIn, clock())
        store.storeRefreshed(response.accessToken, response.refreshToken, newExpiry)
        expiresAt = newExpiry
        current.set(response.accessToken)
        response.accessToken
    }

    private suspend fun dropGrant() {
        store.dropGrant()
        onSignedOut()
    }
}
