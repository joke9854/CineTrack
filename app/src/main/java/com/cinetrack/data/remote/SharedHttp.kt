package com.cinetrack.data.remote

import okhttp3.OkHttpClient

/**
 * One connection pool and dispatcher for the whole app. Every client is derived from
 * [client] with `newBuilder()`, which keeps the pool and adds its own settings on top.
 */
object SharedHttp {
    val client: OkHttpClient by lazy { OkHttpClient() }
}
