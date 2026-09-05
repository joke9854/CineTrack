package com.cinetrack.data.remote

import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class RateLimitInterceptorTest {
    @Test fun supportsSecondsAndCapsWait() {
        assertEquals(2_000L, RateLimitInterceptor.retryDelayMillis("2", 0))
        assertEquals(30_000L, RateLimitInterceptor.retryDelayMillis("30", 0))
        assertNull(RateLimitInterceptor.retryDelayMillis("31", 0))
        assertNull(RateLimitInterceptor.retryDelayMillis(Long.MAX_VALUE.toString(), 0))
    }
    @Test fun supportsHttpDatesAndPastDates() {
        val now = Instant.parse("2026-09-05T12:00:00Z").toEpochMilli()
        assertEquals(10_000L, RateLimitInterceptor.retryDelayMillis("Sat, 5 Sep 2026 12:00:10 GMT", now))
        assertEquals(0L, RateLimitInterceptor.retryDelayMillis("Sat, 5 Sep 2026 11:59:00 GMT", now))
        assertNull(RateLimitInterceptor.retryDelayMillis("Sat, 5 Sep 2026 12:02:00 GMT", now))
    }
    @Test fun defaultsForAbsentOrMalformedHeaders() {
        assertEquals(1_000L, RateLimitInterceptor.retryDelayMillis(null, 0))
        assertEquals(1_000L, RateLimitInterceptor.retryDelayMillis("invalid", 0))
        assertEquals(1_000L, RateLimitInterceptor.retryDelayMillis("-1", 0))
    }
}
