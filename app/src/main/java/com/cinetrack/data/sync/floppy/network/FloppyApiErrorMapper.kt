package com.cinetrack.data.sync.floppy.network

import com.cinetrack.data.sync.TrackingProviderId
import com.cinetrack.data.sync.TrackingSyncError
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.URI
import javax.net.ssl.SSLException

object FloppyApiErrorMapper {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun map(error: Throwable): TrackingSyncError = when (error) {
        is TrackingSyncError -> error
        // SerializationException inherits IllegalArgumentException; keep it
        // ahead of URL validation so malformed provider JSON is actionable as
        // an invalid response rather than being reported as a bad URL.
        is SerializationException -> TrackingSyncError.InvalidRemoteData("Floppy returned malformed JSON")
        is IllegalArgumentException -> TrackingSyncError.InvalidUrl(error.message ?: "Invalid Floppy URL", error)
        is SocketTimeoutException -> TrackingSyncError.Timeout(error)
        is UnknownHostException -> TrackingSyncError.DnsFailure(error)
        is SSLException -> TrackingSyncError.TlsFailure(error)
        is ConnectException, is IOException -> TrackingSyncError.NetworkUnavailable(error)
        is HttpException -> when (error.code()) {
            301, 302, 303, 307, 308 -> TrackingSyncError.ApiRedirect(safeRedirectLocation(error))
            401 -> TrackingSyncError.AuthenticationRequired(TrackingProviderId.FLOPPY)
            403 -> TrackingSyncError.TokenScope(TrackingProviderId.FLOPPY)
            404 -> TrackingSyncError.WrongApi(TrackingProviderId.FLOPPY)
            409 -> TrackingSyncError.Conflict("Floppy returned a synchronization conflict")
            422 -> TrackingSyncError.Validation("Floppy rejected the synchronization operation")
            429 -> TrackingSyncError.RateLimited(error.response()?.headers()?.get("Retry-After")?.toLongOrNull())
            // Preserve the safe HTTP cause for bootstrap retry classification.
            in 500..599 -> TrackingSyncError.ProviderUnavailable(TrackingProviderId.FLOPPY, error)
            else -> TrackingSyncError.InvalidRemoteData("Floppy returned HTTP ${error.code()}")
        }
        else -> TrackingSyncError.Unknown(error)
    }

    /** Extract only a structured error code; never include arbitrary response text. */
    fun code(error: HttpException): String? = runCatching {
        error.response()?.errorBody()?.string()?.let { body ->
            json.parseToJsonElement(body).jsonObject["code"]?.jsonPrimitive?.content
        }
    }.getOrNull()

    /** Location is diagnostic-only. Strip query/fragment so tunnel tokens or other
     * credentials can never be retained in logs/UI state. */
    private fun safeRedirectLocation(error: HttpException): String? = runCatching {
        error.response()?.headers()?.get("Location")?.let { raw ->
            val uri = URI(raw)
            URI(uri.scheme, uri.authority, uri.path, null, null).toString()
        }
    }.getOrNull()
}
