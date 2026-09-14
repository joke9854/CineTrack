package com.cinetrack.data.sync.floppy.network

import com.cinetrack.BuildConfig
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import java.net.URI
import java.util.concurrent.TimeUnit

/** Normalized identity used for both Retrofit and credential scoping. */
data class FloppyServerIdentity(val baseUrl: String, val origin: String)

object FloppyUrlNormalizer {
    fun normalize(raw: String): FloppyServerIdentity {
        val value = raw.trim()
        require(value.isNotBlank()) { "Floppy server URL is required" }
        val uri = runCatching { URI(value) }.getOrElse { throw IllegalArgumentException("Invalid Floppy server URL") }
        require(uri.scheme == "http" || uri.scheme == "https") { "Floppy URL must use http:// or https://" }
        require(uri.userInfo == null && uri.query == null && uri.fragment == null) { "Floppy URL must not include credentials or query parameters" }
        require(!uri.host.isNullOrBlank()) { "Floppy URL host is missing" }
        require(uri.port in -1..65535) { "Floppy URL port is invalid" }
        val host = uri.host
        val origin = buildString {
            append(uri.scheme.lowercase()).append("://").append(host.lowercase())
            if (uri.port >= 0) append(':').append(uri.port)
        }
        val path = (uri.rawPath ?: "").trimEnd('/')
        val normalized = origin + if (path.isBlank() || path == "/") "/" else "$path/"
        // Retrofit requires a trailing slash and preserves reverse-proxy prefixes.
        normalized.toHttpUrl()
        return FloppyServerIdentity(normalized, origin)
    }
}

/** Creates a client per normalized server identity; API keys never cross origins. */
class FloppyApiClientFactory {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }
    private val clients = mutableMapOf<String, FloppyApi>()

    @Synchronized
    fun get(baseUrl: String, apiKey: String?): FloppyApi {
        val identity = FloppyUrlNormalizer.normalize(baseUrl)
        val originUrl = identity.origin.toHttpUrl()
        val key = identity.baseUrl + "|" + apiKey.orEmpty().hashCode()
        return clients.getOrPut(key) {
            val logger = HttpLoggingInterceptor().apply {
                redactHeader("X-API-Key")
                redactHeader("Authorization")
                level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
            }
            val client = OkHttpClient.Builder()
                .followRedirects(false)
                .followSslRedirects(false)
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val request = chain.request()
                    val sameOrigin = request.url.scheme == originUrl.scheme &&
                        request.url.host.equals(originUrl.host, ignoreCase = true) &&
                        request.url.port == originUrl.port
                    val isPublicInfo = request.url.encodedPath.endsWith("/api/v1/info/") ||
                        request.url.encodedPath.endsWith("/api/v1/info")
                    chain.proceed(
                        request.newBuilder().apply {
                            if (sameOrigin && !isPublicInfo && !apiKey.isNullOrBlank()) header("X-API-Key", apiKey)
                            else removeHeader("X-API-Key")
                            header("Accept", "application/json")
                            header("User-Agent", "CineTrack/${BuildConfig.VERSION_NAME} (Android)")
                        }.build(),
                    )
                }
                .addInterceptor(logger)
                .build()
            Retrofit.Builder()
                .baseUrl(identity.baseUrl)
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(FloppyApi::class.java)
        }
    }
}
