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
import javax.net.ssl.SSLException

object FloppyApiErrorMapper {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun map(error: Throwable): TrackingSyncError = when (error) {
        is TrackingSyncError -> error
        is SocketTimeoutException -> TrackingSyncError.Timeout(error)
        is UnknownHostException -> TrackingSyncError.DnsFailure(error)
        is SSLException -> TrackingSyncError.TlsFailure(error)
        is SerializationException -> TrackingSyncError.InvalidRemoteData("Floppy returned malformed JSON")
        is ConnectException, is IOException -> TrackingSyncError.NetworkUnavailable(error)
        is HttpException -> when (error.code()) {
            401, 403 -> TrackingSyncError.AuthenticationRequired(TrackingProviderId.FLOPPY)
            404 -> TrackingSyncError.WrongApi(TrackingProviderId.FLOPPY)
            409 -> TrackingSyncError.Conflict("Floppy returned a synchronization conflict")
            422 -> TrackingSyncError.Validation("Floppy rejected the synchronization operation")
            429 -> TrackingSyncError.RateLimited(error.response()?.headers()?.get("Retry-After")?.toLongOrNull())
            in 500..599 -> TrackingSyncError.ProviderUnavailable(TrackingProviderId.FLOPPY)
            else -> TrackingSyncError.ProviderUnavailable(TrackingProviderId.FLOPPY)
        }
        else -> TrackingSyncError.Unknown(error)
    }

    /** Extract only a structured error code; never include arbitrary response text. */
    fun code(error: HttpException): String? = runCatching {
        error.response()?.errorBody()?.string()?.let { body ->
            json.parseToJsonElement(body).jsonObject["code"]?.jsonPrimitive?.content
        }
    }.getOrNull()
}
