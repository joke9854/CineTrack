package com.cinetrack.data.remote

import java.io.InterruptedIOException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Retries on OkHttp's worker thread, never on the main thread: a 429 up to
 * [MAX_RATE_LIMIT_RETRIES] times after the server's Retry-After, and a brief
 * 502/503/504 on a read (GET) up to [MAX_UNAVAILABLE_RETRIES] times after 1 s,
 * then 2 s. A failed write is not repeated here (it may have been applied);
 * its durable queue retries it. Each wait gets up to [jitterMs] extra so
 * retries do not arrive in step.
 */
internal class RateLimitInterceptor(
    private val jitterMs: () -> Long = { kotlin.random.Random.nextLong(0, MAX_JITTER_MS + 1) },
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var response = chain.proceed(request)
        var attempt = 0
        while (true) {
            val code = response.code
            if (request.body?.isOneShot() == true || request.body?.isDuplex() == true) return response
            val delayMs = when {
                code == 429 && attempt < MAX_RATE_LIMIT_RETRIES ->
                    // Do not retry earlier than a long server-requested delay.
                    retryDelayMillis(response.header("Retry-After"), System.currentTimeMillis()) ?: return response
                code in UNAVAILABLE_CODES && request.method == "GET" && attempt < MAX_UNAVAILABLE_RETRIES ->
                    retryDelayMillis(response.header("Retry-After") ?: ((attempt + 1)).toString(), System.currentTimeMillis()) ?: return response
                else -> return response
            } + jitterMs()
            attempt++
            response.close() // Release the connection before waiting and proceeding again.
            var remaining = delayMs
            while (remaining > 0) {
                if (chain.call().isCanceled()) throw InterruptedIOException("Rate-limit retry cancelled")
                val slice = minOf(remaining, 100L)
                try {
                    Thread.sleep(slice)
                } catch (interrupted: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw InterruptedIOException("Rate-limit retry interrupted")
                }
                remaining -= slice
            }
            if (chain.call().isCanceled()) throw InterruptedIOException("Rate-limit retry cancelled")
            response = chain.proceed(request)
        }
    }

    companion object {
        private const val MAX_WAIT_MS = 30_000L
        internal const val MAX_RATE_LIMIT_RETRIES = 2
        internal const val MAX_UNAVAILABLE_RETRIES = 2
        private const val MAX_JITTER_MS = 500L
        private val UNAVAILABLE_CODES = setOf(502, 503, 504)
        internal fun retryDelayMillis(header: String?, nowMillis: Long): Long? {
            val value = header?.trim()
            val seconds = value?.toLongOrNull()
            val delay = if (seconds != null) {
                if (seconds < 0) 1_000L else if (seconds > MAX_WAIT_MS / 1_000) return null else seconds * 1_000
            } else {
                val retryAt = value?.let {
                    runCatching { ZonedDateTime.parse(it, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }.getOrNull()
                }
                retryAt?.let { (it - nowMillis).coerceAtLeast(0) } ?: 1_000L
            }
            return delay.takeIf { it <= MAX_WAIT_MS }
        }
    }
}
