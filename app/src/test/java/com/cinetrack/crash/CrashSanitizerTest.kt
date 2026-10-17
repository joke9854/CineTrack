package com.cinetrack.crash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashSanitizerTest {
    @Test
    fun redactsBearerAndAuthorization() {
        val out = CrashSanitizer.sanitize("failed with Bearer abc.def-ghi and\nAuthorization: Basic Zm9vOmJhcg==\nnext")
        assertFalse(out.contains("abc.def-ghi"))
        assertFalse(out.contains("Zm9vOmJhcg"))
        assertTrue(out.contains("Bearer <redacted>"))
        assertTrue(out.contains("Authorization: <redacted>"))
        assertTrue(out.contains("\nnext"))
    }

    @Test
    fun redactsSecretKeyValuesInBothForms() {
        val out = CrashSanitizer.sanitize(
            "api_key=hunter2 x-api-key: hunter3 API-KEY=hunter4 access_token=a1 refresh_token: b2 client_secret=c3 password=\"p w\" token: t5",
        )
        listOf("hunter2", "hunter3", "hunter4", "a1", "b2", "c3", "p w", "t5").forEach { assertFalse(it, out.contains(it)) }
        assertTrue(out.contains("api_key=<redacted>"))
        assertTrue(out.contains("x-api-key: <redacted>"))
    }

    @Test
    fun dropsUrlQueryButKeepsSchemeHostAndPath() {
        val out = CrashSanitizer.sanitize("GET https://floppy.example.com:8443/api/v1/info/?foo=1&session=zzz failed")
        assertEquals("GET https://floppy.example.com:8443/api/v1/info/?<redacted> failed", out)
        assertEquals("see http://a.b/c", CrashSanitizer.sanitize("see http://a.b/c"))
    }

    @Test
    fun redactsLongHexAndBase64UrlRuns() {
        val hex = "0123456789abcdef0123456789abcdef"
        val b64 = "AbCdEfGhIjKlMnOpQrStUvWxYz012345-_"
        val out = CrashSanitizer.sanitize("digest $hex and $b64 end")
        assertEquals("digest <redacted> and <redacted> end", out)
        // 31 characters stays.
        assertEquals("a".repeat(31), CrashSanitizer.sanitize("a".repeat(31)))
    }

    @Test
    fun keepsOrdinaryStackFrames() {
        val frame = "\tat com.cinetrack.ui.CineTrackViewModel.sync(CineTrackViewModel.kt:123)"
        assertEquals(frame, CrashSanitizer.sanitize(frame))
    }

    @Test
    fun truncatesStackTraceToTheContractLimit() {
        val out = CrashSanitizer.sanitizeStackTrace("x. ".repeat(40000))
        assertEquals(CrashSanitizer.MAX_STACK_TRACE, out.length)
    }
}
