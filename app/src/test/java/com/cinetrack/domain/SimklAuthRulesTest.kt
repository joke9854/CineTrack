package com.cinetrack.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SimklAuthRulesTest {
    @Test
    fun `only V2 prefixed tokens are accepted`() {
        assertTrue(SimklAuthRules.isV2AccessToken("simkl_at_abcdefghijklmnopqrstuvwxyz0123456789"))
        assertFalse(SimklAuthRules.isV2AccessToken("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"))
        assertFalse(SimklAuthRules.isV2AccessToken("simkl_rt_refresh"))
        assertFalse(SimklAuthRules.isV2AccessToken(null))
    }

    @Test
    fun `write access needs the exact media write scope`() {
        assertTrue(SimklAuthRules.grantsWrite("media:read media:write"))
        assertTrue(SimklAuthRules.grantsWrite("media:write"))
        assertFalse(SimklAuthRules.grantsWrite("media:read"))
        assertFalse(SimklAuthRules.grantsWrite("media:wrote"))
        assertFalse(SimklAuthRules.grantsWrite(null))
    }

    @Test
    fun `refresh is due within a day of expiry`() {
        val now = 1_000_000_000L
        assertFalse(SimklAuthRules.needsRefresh(null, now))
        assertFalse(SimklAuthRules.needsRefresh(now + 2 * SimklAuthRules.REFRESH_MARGIN_MILLIS, now))
        assertTrue(SimklAuthRules.needsRefresh(now + SimklAuthRules.REFRESH_MARGIN_MILLIS, now))
        assertTrue(SimklAuthRules.needsRefresh(now - 1, now))
    }

    @Test
    fun `expiry is computed from expires_in`() {
        assertEquals(1_000L + 604_800_000L, SimklAuthRules.expiresAt(604_800, 1_000L))
        assertNull(SimklAuthRules.expiresAt(null, 1_000L))
        assertNull(SimklAuthRules.expiresAt(0, 1_000L))
    }

    @Test
    fun `callback needs matching state and the Simkl issuer`() {
        assertNull(SimklAuthRules.callbackProblem("s1", "s1", "https://simkl.com"))
        assertTrue(SimklAuthRules.callbackProblem(null, "s1", "https://simkl.com")!!.contains("expired"))
        assertTrue(SimklAuthRules.callbackProblem("s1", "s2", "https://simkl.com")!!.contains("state"))
        assertTrue(SimklAuthRules.callbackProblem("s1", null, "https://simkl.com")!!.contains("state"))
        assertTrue(SimklAuthRules.callbackProblem("s1", "s1", null)!!.contains("did not come from Simkl"))
        assertTrue(SimklAuthRules.callbackProblem("s1", "s1", "https://simkl.com.evil.example")!!.contains("did not come from Simkl"))
        assertTrue(SimklAuthRules.callbackProblem("s1", "s1", "https://trakt.tv")!!.contains("did not come from Simkl"))
    }

    @Test
    fun `declined consent reads as cancelled`() {
        assertEquals("Simkl sign-in was cancelled", SimklAuthRules.callbackErrorMessage("access_denied"))
        assertEquals("Simkl sign-in failed: server_error", SimklAuthRules.callbackErrorMessage("server_error"))
        // Any page can fire the callback: free text is never echoed.
        assertEquals("Simkl sign-in failed", SimklAuthRules.callbackErrorMessage("Your account is locked, call +1 555"))
    }
}
