package com.cinetrack.data.sync.floppy.network

import com.cinetrack.data.sync.floppy.FloppyEpisodeWatchRequest
import com.cinetrack.data.sync.floppy.FloppyHistoryEnvelope
import com.cinetrack.data.sync.floppy.FloppyInfoDto
import com.cinetrack.data.sync.floppy.FloppyTrackMediaRequest
import com.cinetrack.data.sync.floppy.FloppyTrackedMedia
import com.cinetrack.data.sync.floppy.FloppyTrackedMediaEnvelope
import com.cinetrack.data.sync.floppy.FloppyTrackedMediaUpdateRequest
import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** Retrofit surface is limited to endpoints present in Floppy's public OpenAPI. */
interface FloppyApi {
    @GET("api/v1/info/")
    suspend fun info(): FloppyInfoDto

    @GET("api/v1/user/preferences/")
    suspend fun preferences(): JsonObject

    @GET("api/v1/media/{mediaType}/")
    suspend fun media(
        @Path("mediaType") mediaType: String,
        @Query("limit") limit: Int = 200,
        @Query("offset") offset: Int = 0,
    ): FloppyTrackedMediaEnvelope

    @POST("api/v1/media/{mediaType}/")
    suspend fun track(
        @Path("mediaType") mediaType: String,
        @Body request: FloppyTrackMediaRequest,
    ): FloppyTrackedMedia

    @PATCH("api/v1/media/{mediaType}/{source}/{mediaId}/")
    suspend fun update(
        @Path("mediaType") mediaType: String,
        @Path("source") source: String,
        @Path("mediaId") mediaId: String,
        @Body request: FloppyTrackedMediaUpdateRequest,
    ): FloppyTrackedMedia

    @DELETE("api/v1/media/{mediaType}/{source}/{mediaId}/")
    suspend fun delete(
        @Path("mediaType") mediaType: String,
        @Path("source") source: String,
        @Path("mediaId") mediaId: String,
    )

    @POST("api/v1/media/{mediaType}/{source}/{mediaId}/{season}/episodes/{episode}/watch/")
    suspend fun watchEpisode(
        @Path("mediaType") mediaType: String = "tv",
        @Path("source") source: String,
        @Path("mediaId") mediaId: String,
        @Path("season") season: Int,
        @Path("episode") episode: Int,
        @Body request: FloppyEpisodeWatchRequest = FloppyEpisodeWatchRequest(),
    ): FloppyTrackedMedia

    @DELETE("api/v1/media/{mediaType}/{source}/{mediaId}/{season}/{episode}/")
    suspend fun deleteEpisode(
        @Path("mediaType") mediaType: String = "tv",
        @Path("source") source: String,
        @Path("mediaId") mediaId: String,
        @Path("season") season: Int,
        @Path("episode") episode: Int,
    )

    @GET("api/v1/history/")
    suspend fun history(
        @Query("flat") flat: String = "1",
        @Query("limit") limit: Int = 200,
        @Query("offset") offset: Int = 0,
        @Query("types") types: String? = null,
    ): FloppyHistoryEnvelope
}
