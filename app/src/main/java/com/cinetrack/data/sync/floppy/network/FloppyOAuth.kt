package com.cinetrack.data.sync.floppy.network

import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import com.cinetrack.data.remote.AppJson

/**
 * Floppy app sign-in (OAuth 2.0 authorization code + PKCE, like Simkl).
 *
 * A signed-in connection stores the credential `oauth:<grant id>` where an API
 * key would be. It never changes while the tokens behind it renew, so the
 * connection identity, its role and its sync history are untouched by a
 * renewal; only the HTTP layer swaps in the current access token.
 */
object FloppyOAuthRules {
    const val CLIENT_ID = "cinetrack"
    const val REDIRECT_URI = "cinetrack://floppy"
    const val CREDENTIAL_PREFIX = "oauth:"

    /** Renew this long before the access token expires. */
    val REFRESH_MARGIN_MS: Long = TimeUnit.MINUTES.toMillis(5)

    fun isOAuthCredential(credential: String?): Boolean = credential?.startsWith(CREDENTIAL_PREFIX) == true

    fun grantId(credential: String): String = credential.removePrefix(CREDENTIAL_PREFIX)

    fun credential(grantId: String): String = CREDENTIAL_PREFIX + grantId

    fun authorizeUrl(baseUrl: String, codeChallenge: String, state: String): String {
        // No scope: Floppy grants its app preset (tracking + openid) by default.
        val query = listOf(
            "response_type" to "code",
            "client_id" to CLIENT_ID,
            "redirect_uri" to REDIRECT_URI,
            "code_challenge" to codeChallenge,
            "code_challenge_method" to "S256",
            "state" to state,
        ).joinToString("&") { (name, value) -> "$name=${URLEncoder.encode(value, "UTF-8")}" }
        return "${baseUrl}o/authorize/?$query"
    }

    fun tokenUrl(baseUrl: String): String = "${baseUrl}o/token/"

    fun revokeUrl(baseUrl: String): String = "${baseUrl}o/revoke_token/"

    /**
     * Why a sign-in callback must be refused, or null when it may be exchanged.
     * The state proves this device started the sign-in; the issuer (RFC 9207),
     * when Floppy sends it, must be the server the sign-in was started for.
     */
    fun callbackProblem(
        expectedState: String?,
        returnedState: String?,
        error: String?,
        issuer: String?,
        baseUrl: String,
    ): String? = when {
        error == "access_denied" -> "Sign-in was cancelled."
        error != null -> "Floppy refused the sign-in ($error)."
        expectedState == null -> "This sign-in has expired. Please start it again."
        expectedState != returnedState -> "This sign-in didn't come from CineTrack. Please start it again."
        issuer != null && !sameOrigin(issuer, baseUrl) -> "This sign-in came from a different server. Please start it again."
        else -> null
    }

    private fun sameOrigin(left: String, right: String): Boolean = runCatching {
        val a = URI(left)
        val b = URI(right)
        fun port(uri: URI) = if (uri.port >= 0) uri.port else if (uri.scheme.equals("https", true)) 443 else 80
        a.scheme.equals(b.scheme, true) && a.host.equals(b.host, true) && port(a) == port(b)
    }.getOrDefault(false)
}

/** The tokens of one sign-in. [expiresAt] is epoch milliseconds. */
@Serializable
data class FloppyOAuthTokens(
    @SerialName("access") val accessToken: String,
    @SerialName("refresh") val refreshToken: String,
    @SerialName("expires_at") val expiresAt: Long,
)

/** Durable, encrypted token storage; [save] must reach disk before returning. */
interface FloppyOAuthTokenStore {
    fun load(grantId: String): FloppyOAuthTokens?
    fun save(grantId: String, tokens: FloppyOAuthTokens?)
}

@Serializable
private data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresIn: Long? = null,
)

/**
 * Exchanges, renews and revokes Floppy sign-in tokens.
 *
 * Renewal is serialized: Floppy rotates the refresh token on every use and
 * revokes the whole sign-in when a used one is presented again, so two
 * concurrent renewals would sign the user out.
 */
class FloppyOAuthClient(
    private val store: FloppyOAuthTokenStore,
    private val clock: () -> Long = System::currentTimeMillis,
    /** Built on first use, not when the app starts. */
    httpClient: Lazy<OkHttpClient> = lazy {
        com.cinetrack.data.remote.SharedHttp.client.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    },
) {
    private val http by httpClient
    private val json = AppJson.tolerant

    /**
     * The access token to send, renewed first when it is about to expire or
     * Floppy just rejected it ([rejected]). Null once the sign-in is gone
     * (revoked, expired or never stored): the request then goes out without
     * credentials and Floppy's 401 asks the user to reconnect. Network
     * failures while renewing are thrown as [IOException].
     */
    @Synchronized
    fun accessToken(baseUrl: String, credential: String, rejected: String? = null): String? {
        val grantId = FloppyOAuthRules.grantId(credential)
        val tokens = store.load(grantId) ?: return null
        // Another request may already have renewed past the rejected token.
        if (rejected != null && tokens.accessToken != rejected) return tokens.accessToken
        val expiring = tokens.expiresAt - clock() < FloppyOAuthRules.REFRESH_MARGIN_MS
        if (rejected == null && !expiring) return tokens.accessToken
        return refresh(baseUrl, grantId, tokens)?.accessToken
    }

    /** Swaps an authorization code for tokens. Throws on any failure. */
    fun exchangeCode(baseUrl: String, code: String, codeVerifier: String): FloppyOAuthTokens {
        val body = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("redirect_uri", FloppyOAuthRules.REDIRECT_URI)
            .add("client_id", FloppyOAuthRules.CLIENT_ID)
            .add("code_verifier", codeVerifier)
            .build()
        http.newCall(Request.Builder().url(FloppyOAuthRules.tokenUrl(baseUrl)).post(body).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Floppy sign-in failed (HTTP ${response.code})")
            return parse(response.body?.string().orEmpty(), previousRefresh = null)
        }
    }

    fun save(grantId: String, tokens: FloppyOAuthTokens) = store.save(grantId, tokens)

    /** Ends the sign-in on Floppy (best effort) and forgets its tokens. */
    @Synchronized
    fun revoke(baseUrl: String, credential: String) {
        val grantId = FloppyOAuthRules.grantId(credential)
        val tokens = store.load(grantId)
        store.save(grantId, null)
        if (tokens == null) return
        runCatching {
            val body = FormBody.Builder()
                .add("token", tokens.refreshToken)
                .add("token_type_hint", "refresh_token")
                .add("client_id", FloppyOAuthRules.CLIENT_ID)
                .build()
            http.newCall(Request.Builder().url(FloppyOAuthRules.revokeUrl(baseUrl)).post(body).build()).execute().close()
        }
    }

    private fun refresh(baseUrl: String, grantId: String, tokens: FloppyOAuthTokens): FloppyOAuthTokens? {
        val body = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", tokens.refreshToken)
            .add("client_id", FloppyOAuthRules.CLIENT_ID)
            .build()
        http.newCall(Request.Builder().url(FloppyOAuthRules.tokenUrl(baseUrl)).post(body).build()).execute().use { response ->
            if (response.code == 400 || response.code == 401) {
                // invalid_grant: revoked, expired or already used. Only a new
                // sign-in can recover, so drop the dead tokens.
                store.save(grantId, null)
                return null
            }
            if (!response.isSuccessful) throw IOException("Floppy sign-in renewal failed (HTTP ${response.code})")
            val renewed = parse(response.body?.string().orEmpty(), previousRefresh = tokens.refreshToken)
            store.save(grantId, renewed)
            return renewed
        }
    }

    private fun parse(body: String, previousRefresh: String?): FloppyOAuthTokens {
        val response = runCatching { json.decodeFromString<TokenResponse>(body) }
            .getOrElse { throw IOException("Floppy returned an unreadable sign-in response") }
        val refresh = response.refreshToken ?: previousRefresh
            ?: throw IOException("Floppy did not return a refresh token")
        val lifetime = (response.expiresIn ?: 3600L).coerceAtLeast(60L)
        return FloppyOAuthTokens(response.accessToken, refresh, clock() + lifetime * 1000)
    }
}
