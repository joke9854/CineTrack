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
import java.net.Inet6Address
import java.net.InetAddress
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** Normalized identity used for both Retrofit and credential scoping. */
data class FloppyServerIdentity(val baseUrl: String, val origin: String)

object FloppyUrlNormalizer {
    fun normalize(raw: String, allowInsecureLocalHttp: Boolean = false): FloppyServerIdentity {
        val value = raw.trim()
        require(value.isNotBlank()) { "Floppy server URL is required" }
        val uri = runCatching { URI(value) }.getOrElse { throw IllegalArgumentException("Invalid Floppy server URL") }
        require(uri.scheme.equals("http", ignoreCase = true) || uri.scheme.equals("https", ignoreCase = true)) {
            "Floppy URL must use http:// or https://"
        }
        require(uri.userInfo == null && uri.query == null && uri.fragment == null) { "Floppy URL must not include credentials or query parameters" }
        require(!uri.host.isNullOrBlank()) { "Floppy URL host is missing" }
        require(uri.port in -1..65535) { "Floppy URL port is invalid" }
        val host = uri.host
        if (uri.scheme.equals("http", ignoreCase = true)) {
            require(allowInsecureLocalHttp && isPrivateHost(host)) {
                "Plain HTTP is disabled. Use HTTPS or explicitly allow a private LAN Floppy address."
            }
        }
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

    private fun isPrivateHost(rawHost: String): Boolean {
        val host = rawHost.trim('[', ']').lowercase()
        if (host == "localhost" || host.endsWith(".local")) return true
        if (host.contains(':')) {
            val address = runCatching { InetAddress.getByName(host) }.getOrNull()
            if (address is Inet6Address) {
                val bytes = address.address
                val first = bytes.firstOrNull()?.toInt()?.and(0xff) ?: return false
                val second = bytes.getOrNull(1)?.toInt()?.and(0xff) ?: return false
                val ula = first and 0xfe == 0xfc
                val linkLocal = first == 0xfe && second and 0xc0 == 0x80
                return address.isLoopbackAddress || ula || linkLocal
            }
            return false
        }
        val octets = host.split('.')
        if (octets.size != 4) return false
        val values = octets.mapNotNull(String::toIntOrNull)
        if (values.size != 4 || values.any { it !in 0..255 }) return false
        return values[0] == 10 ||
            (values[0] == 172 && values[1] in 16..31) ||
            (values[0] == 192 && values[1] == 168) ||
            (values[0] == 169 && values[1] == 254) ||
            (values[0] == 127)
    }
}

/** Creates a client per normalized server identity; API keys never cross origins. */
class FloppyApiClientFactory {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }
    private val clients = mutableMapOf<String, FloppyApi>()

    @Synchronized
    fun get(baseUrl: String, apiKey: String?, allowInsecureLocalHttp: Boolean = false): FloppyApi {
        // Loopback is used by MockWebServer and is not reachable beyond this
        // process. Private-LAN addresses still require the explicit opt-in.
        val loopback = runCatching { URI(baseUrl.trim()).host?.lowercase() }
            .getOrNull() in setOf("localhost", "127.0.0.1", "::1")
        val identity = FloppyUrlNormalizer.normalize(baseUrl, allowInsecureLocalHttp || loopback)
        val originUrl = identity.origin.toHttpUrl()
        val key = identity.baseUrl + "|" + credentialFingerprint(apiKey)
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

    /** Drop clients that hold credentials which are no longer active. */
    @Synchronized
    fun invalidate(baseUrl: String, apiKey: String? = null) {
        val identity = runCatching { FloppyUrlNormalizer.normalize(baseUrl, allowInsecureLocalHttp = true) }.getOrNull() ?: return
        if (apiKey == null) clients.keys.removeAll { it.startsWith("${identity.baseUrl}|") }
        else clients.remove(identity.baseUrl + "|" + credentialFingerprint(apiKey))
    }

    @Synchronized
    fun clear() { clients.clear() }

    private fun credentialFingerprint(apiKey: String?): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(apiKey.orEmpty().toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

