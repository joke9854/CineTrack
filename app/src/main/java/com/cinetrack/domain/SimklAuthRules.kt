package com.cinetrack.domain

/** Simkl AUTH V2 rules that do not depend on Android, kept here so they can be unit tested. */
object SimklAuthRules {
    const val ISSUER = "https://simkl.com"
    const val AUTHORIZE_URL = "https://simkl.com/oauth2/authorize"
    /** CineTrack writes history and lists, so it needs both scopes; omitting scope grants read-only. */
    const val REQUESTED_SCOPE = "media:read media:write"
    /** Refresh this long before the 7-day access token expires. */
    const val REFRESH_MARGIN_MILLIS = 24L * 60 * 60 * 1000

    /** V2 access tokens carry this prefix; anything else is a retired AUTH V1 token. */
    fun isV2AccessToken(token: String?): Boolean = token?.startsWith("simkl_at_") == true

    /** True when the granted scope lets CineTrack write (an unknown or missing scope means read-only). */
    fun grantsWrite(scope: String?): Boolean = scope.orEmpty().split(' ').any { it == "media:write" }

    fun needsRefresh(expiresAtMillis: Long?, nowMillis: Long): Boolean =
        expiresAtMillis != null && expiresAtMillis - nowMillis <= REFRESH_MARGIN_MILLIS

    fun expiresAt(expiresInSeconds: Long?, nowMillis: Long): Long? =
        expiresInSeconds?.takeIf { it > 0 }?.let { nowMillis + it * 1000 }

    /**
     * Checks the browser callback before any code exchange: the state must match the one
     * stored at sign-in and the issuer must be Simkl (RFC 9207 mix-up protection).
     * Returns the problem to show, or null when the callback can be exchanged.
     */
    fun callbackProblem(expectedState: String?, returnedState: String?, issuer: String?): String? = when {
        expectedState == null -> "The Simkl login session has expired. Please connect again"
        returnedState.isNullOrBlank() || returnedState != expectedState -> "Simkl returned an invalid login state. Please connect again"
        issuer != ISSUER -> "The sign-in response did not come from Simkl. Please connect again"
        else -> null
    }

    /** User-facing text for an `error` callback; Simkl sends `access_denied` when the user declines. */
    fun callbackErrorMessage(error: String): String =
        if (error == "access_denied") "Simkl sign-in was cancelled" else "Simkl sign-in failed: $error"
}
