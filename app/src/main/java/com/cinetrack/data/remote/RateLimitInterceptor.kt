package com.cinetrack.data.remote

import java.io.InterruptedIOException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import okhttp3.Interceptor
import okhttp3.Response

/** One retry only, on OkHttp's worker thread, never on the main thread. */
internal class RateLimitInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (response.code != 429 || request.body?.isOneShot() == true || request.body?.isDuplex() == true) return response
        val delayMs = retryDelayMillis(response.header("Retry-After"), System.currentTimeMillis())
            ?: return response // Do not retry earlier than a long server-requested delay.
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
        return chain.proceed(request)
    }

    companion object {
        private const val MAX_WAIT_MS = 30_000L
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
