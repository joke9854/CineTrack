package com.cinetrack.data.remote

import com.cinetrack.data.sync.floppy.network.FloppyUrlNormalizer
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient

/**
 * One connection pool and dispatcher for the whole app. Every client is derived from
 * [client] with `newBuilder()`, which keeps the pool and adds its own settings on top.
 */
object SharedHttp {
    val client: OkHttpClient by lazy { OkHttpClient() }

    /**
     * The manifest allows cleartext so a LAN Floppy can work over HTTP. Everything else
     * (TMDB, Simkl, Trakt, MDBList and images from any server) never goes out in the
     * clear: plain HTTP to a non-private host is upgraded to HTTPS.
     */
    val upgradeInsecure = Interceptor { chain ->
        val request = chain.request()
        val url = secureUrl(request.url)
        chain.proceed(if (url == request.url) request else request.newBuilder().url(url).build())
    }

    fun secureUrl(url: HttpUrl): HttpUrl =
        if (url.scheme != "http" || FloppyUrlNormalizer.isPrivateHost(url.host)) url
        else url.newBuilder().scheme("https").build()

    /** [client] for public web traffic: TMDB/Simkl/Trakt/MDBList and Coil. */
    val webClient: OkHttpClient by lazy { client.newBuilder().addInterceptor(upgradeInsecure).build() }
}
