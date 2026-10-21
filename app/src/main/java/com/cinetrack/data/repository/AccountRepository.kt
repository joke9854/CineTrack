package com.cinetrack.data.repository

import com.cinetrack.data.local.AppDatabase
import com.cinetrack.data.remote.ApiServices
import com.cinetrack.data.sync.TrackingProviderId
import com.cinetrack.data.sync.TrackingProviderRegistry
import com.cinetrack.data.sync.floppy.FloppySecondaryService
import com.cinetrack.data.sync.floppy.FloppyTrackingProvider
import com.cinetrack.data.sync.floppy.network.FloppyOAuthRules
import com.cinetrack.domain.FloppyConnectionStage
import com.cinetrack.domain.MediaType

/** Floppy and Simkl sign-in, connection and account names. */
class AccountRepository(
    private val database: AppDatabase,
    private val services: ApiServices,
    private val preferences: AppPreferences,
    private val onTokenChanged: (String?) -> Unit = {},
    private val currentToken: () -> String? = { null },
    private val trackingProviderRegistry: TrackingProviderRegistry? = null,
    private val floppySecondaryService: FloppySecondaryService? = null,
    private val floppyOAuth: com.cinetrack.data.sync.floppy.network.FloppyOAuthClient? = null,
    private val floppyRetryBootstrap: (suspend () -> Unit)? = null,
    private val floppyUnplacedEpisodes: (suspend () -> List<com.cinetrack.data.sync.floppy.FloppyUnplacedEpisode>)? = null,
) {
    suspend fun connectFloppy(
        baseUrl: String,
        apiKey: String,
        allowInsecureLocalHttp: Boolean? = null,
        onStage: (FloppyConnectionStage, String?) -> Unit = { _, _ -> },
        sameServerAtNewAddress: Boolean? = null,
    ): com.cinetrack.data.sync.ConnectionResult {
        val previousUrl = preferences.floppySettingsNow()?.baseUrl
        val previousCredential = preferences.floppyApiKeyNow()
        // The account may change; Settings fetches the name again.
        preferences.setFloppyUsername(null)
        val result = floppySecondaryService?.connect(baseUrl.trim(), apiKey.trim(), allowInsecureLocalHttp, onStage, sameServerAtNewAddress)
            ?: (trackingProviderRegistry?.getProvider(TrackingProviderId.FLOPPY) as? FloppyTrackingProvider)
                ?.connect(baseUrl.trim(), apiKey.trim(), allowInsecureLocalHttp, onStage)
            ?: com.cinetrack.data.sync.ConnectionResult.AuthenticationRequired
        // Waiting for the user's answer: the fresh sign-in is still needed.
        if (result == com.cinetrack.data.sync.ConnectionResult.AddressMoveNeedsConfirmation) return result
        val active = preferences.floppyApiKeyNow()
        // End sign-ins nothing uses any more: a fresh one that failed to
        // connect, or the one a new connection just replaced.
        if (FloppyOAuthRules.isOAuthCredential(apiKey) && apiKey.trim() != active) {
            floppyOAuth?.revoke(baseUrl.trim(), apiKey.trim())
        }
        if (FloppyOAuthRules.isOAuthCredential(previousCredential) && previousCredential != active && previousUrl != null) {
            floppyOAuth?.revoke(previousUrl, previousCredential!!)
        }
        return result
    }

    /** Ends a sign-in the user abandoned at the "same Floppy at a new address?" question. */
    suspend fun discardFloppyCredential(baseUrl: String, credential: String) {
        if (FloppyOAuthRules.isOAuthCredential(credential) && credential.trim() != preferences.floppyApiKeyNow()) {
            floppyOAuth?.revoke(baseUrl.trim(), credential.trim())
        }
    }

    /** The normalized server URL when it offers sign-in. */
    suspend fun floppySignInServer(baseUrl: String, allowInsecureLocalHttp: Boolean): Result<String> =
        (trackingProviderRegistry?.getProvider(TrackingProviderId.FLOPPY) as? FloppyTrackingProvider)
            ?.signInServer(baseUrl, allowInsecureLocalHttp)
            ?: Result.failure(IllegalStateException("Floppy is unavailable"))

    data class FloppySignIn(val baseUrl: String, val credential: String, val allowInsecureLocalHttp: Boolean)

    /** Swaps the sign-in callback's code for tokens; the caller then connects with [FloppySignIn.credential]. */
    suspend fun completeFloppySignIn(code: String?, state: String?, error: String?, issuer: String?): Result<FloppySignIn> = runCatching {
        val oauth = checkNotNull(floppyOAuth) { "Floppy sign-in is unavailable" }
        val pending = preferences.takeFloppyPendingLogin(state, error, issuer).getOrThrow()
        val tokens = oauth.exchangeCode(pending.baseUrl, checkNotNull(code) { "Floppy did not return a sign-in code" }, pending.codeVerifier)
        val grantId = java.util.UUID.randomUUID().toString()
        oauth.save(grantId, tokens)
        FloppySignIn(pending.baseUrl, FloppyOAuthRules.credential(grantId), pending.allowInsecureLocalHttp)
    }

    suspend fun disconnectFloppy() {
        val previousUrl = preferences.floppySettingsNow()?.baseUrl
        val previousCredential = preferences.floppyApiKeyNow()
        preferences.setFloppyUsername(null)
        floppySecondaryService?.disconnect()
            ?: (trackingProviderRegistry?.getProvider(TrackingProviderId.FLOPPY) as? FloppyTrackingProvider)?.disconnect()
        if (FloppyOAuthRules.isOAuthCredential(previousCredential) && previousUrl != null) {
            floppyOAuth?.revoke(previousUrl, previousCredential!!)
        }
    }
    suspend fun retryFloppyBootstrap() { floppyRetryBootstrap?.invoke() }

    /** Episodes Floppy could not place, with their show titles. */
    suspend fun floppyUnplacedItems(): List<com.cinetrack.domain.FloppyUnplacedItem> =
        floppyUnplacedEpisodes?.invoke().orEmpty().map { episode ->
            com.cinetrack.domain.FloppyUnplacedItem(
                showId = episode.showId,
                showTitle = database.mediaDao().get(MediaType.TV.name, episode.showId)?.title ?: "TMDB ${episode.showId}",
                season = episode.season,
                episode = episode.episode,
                episodeTitle = episode.title,
            )
        }

    suspend fun completeLogin(code: String, state: String?, issuer: String?): Result<Unit> =
        preferences.completeSimklLogin(code, state, issuer, services.simklAuth).onSuccess {
            database.syncDao().delete("all")
            onTokenChanged(preferences.tokenNow())
        }

    /** Reads the Simkl account name for the Settings page; null when it cannot be read. */
    suspend fun refreshFloppyUsername(): String? =
        runCatching { (trackingProviderRegistry?.getProvider(TrackingProviderId.FLOPPY) as? FloppyTrackingProvider)?.displayName() }.getOrNull()
            ?.also { preferences.setFloppyUsername(it) }

    suspend fun refreshSimklUsername(): String? =
        runCatching { services.simklSync.userSettings().user?.name?.trim() }.getOrNull()
            ?.takeIf(String::isNotBlank)
            ?.also { preferences.setSimklUsername(it) }

    suspend fun disconnectSimkl() {
        // Revoking either half ends the grant; a 200 confirms nothing, so the local copy goes regardless.
        (preferences.simklRefreshTokenNow() ?: currentToken())?.let { grant ->
            runCatching { services.simklAuth.revoke(grant, com.cinetrack.BuildConfig.SIMKL_CLIENT_ID) }
        }
        preferences.setSimklUsername(null)
        preferences.setSimklFirstSyncMode(null)
        preferences.setToken(null)
        database.syncDao().delete("all")
        onTokenChanged(null)
    }
}
