package com.cinetrack.data.remote

import java.lang.reflect.Proxy
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test

class RateLimitRetryTest {
    private fun exercise(vararg codes: Int): Pair<Int, Int> {
        val request = Request.Builder().url("https://example.invalid/test").build()
        val call = OkHttpClient().newCall(request)
        var attempts = 0
        val chain = Proxy.newProxyInstance(
            Interceptor.Chain::class.java.classLoader,
            arrayOf(Interceptor.Chain::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "request" -> request
                "call" -> call
                "proceed" -> Response.Builder()
                    .request(request).protocol(Protocol.HTTP_1_1)
                    .code(codes[attempts++]).message("test")
                    .header("Retry-After", "0").body("test".toResponseBody()).build()
                else -> error("Unexpected chain call: ${method.name}")
            }
        } as Interceptor.Chain
        val code = RateLimitInterceptor().intercept(chain).use { it.code }
        return attempts to code
    }

    @Test fun retries429Once() { assertEquals(2 to 200, exercise(429, 200)) }
    @Test fun persistentLimitDoesNotLoop() { assertEquals(2 to 429, exercise(429, 429)) }
    @Test fun otherFailuresAreNotRetried() { assertEquals(1 to 503, exercise(503)) }
    @Test fun successIsNotRetried() { assertEquals(1 to 200, exercise(200)) }
}
