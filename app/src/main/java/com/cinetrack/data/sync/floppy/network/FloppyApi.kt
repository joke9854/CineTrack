package com.cinetrack.data.sync.floppy.network

import com.cinetrack.data.sync.floppy.FloppyEpisodeWatchRequest
import com.cinetrack.data.sync.floppy.FloppyMovieWatchRequest
import com.cinetrack.data.sync.floppy.FloppyEpisodeBulkRequest
import com.cinetrack.data.sync.floppy.FloppyEpisodeEnsureRequest
import com.cinetrack.data.sync.floppy.FloppyEpisodeEnsureResponse
import com.cinetrack.data.sync.floppy.FloppyBootstrapMoviesRequest
import com.cinetrack.data.sync.floppy.FloppyBootstrapShowsRequest
import com.cinetrack.data.sync.floppy.FloppyBootstrapEnsureResponse
import com.cinetrack.data.sync.floppy.FloppyBulkTaskResponse
import com.cinetrack.data.sync.floppy.FloppyTaskStatusResponse
import com.cinetrack.data.sync.floppy.FloppyConnectionProbeDto
import com.cinetrack.data.sync.floppy.FloppyConsumption
import com.cinetrack.data.sync.floppy.FloppyConsumptionPage
import com.cinetrack.data.sync.floppy.FloppyInfoDto
import com.cinetrack.data.sync.floppy.FloppyMediaDetail
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
    @POST("api/v1/cinetrack/bootstrap/movies/ensure/")
    suspend fun ensureBootstrapMovies(@Body request: FloppyBootstrapMoviesRequest): FloppyBootstrapEnsureResponse

    @POST("api/v1/cinetrack/bootstrap/shows/ensure/")
    suspend fun ensureBootstrapShows(@Body request: FloppyBootstrapShowsRequest): FloppyBootstrapEnsureResponse

    @GET("api/v1/info/")
    suspend fun info(): FloppyInfoDto

    @GET("api/v1/cinetrack/connection/")
    suspend fun connection(): FloppyConnectionProbeDto

    /**
     * Compatibility name for the pre-V2 connection call site. This no longer
     * reads Floppy user preferences: it hits the dedicated authenticated
     * CineTrack probe and returns only its safe connection JSON.
     */
    @GET("api/v1/cinetrack/connection/")
    suspend fun preferences(): JsonObject

    @GET("api/v1/media/{mediaType}/{source}/{mediaId}/")
    suspend fun mediaDetail(
        @Path("mediaType") mediaType: String,
        @Path("source") source: String,
        @Path("mediaId") mediaId: String,
    ): FloppyMediaDetail

    @GET("api/v1/media/{mediaType}/{source}/{mediaId}/history/")
    suspend fun mediaHistory(
        @Path("mediaType") mediaType: String,
        @Path("source") source: String,
        @Path("mediaId") mediaId: String,
        @Query("limit") limit: Int = 200,
        @Query("offset") offset: Int = 0,
    ): FloppyConsumptionPage

    @PATCH("api/v1/media/{mediaType}/{source}/{mediaId}/history/{consumptionId}/")
    suspend fun updateConsumption(
        @Path("mediaType") mediaType: String,
        @Path("source") source: String,
        @Path("mediaId") mediaId: String,
        @Path("consumptionId") consumptionId: Int,
        @Body request: FloppyTrackedMediaUpdateRequest,
    ): FloppyConsumption

    @DELETE("api/v1/media/{mediaType}/{source}/{mediaId}/history/{consumptionId}/")
    suspend fun deleteConsumption(
        @Path("mediaType") mediaType: String,
        @Path("source") source: String,
        @Path("mediaId") mediaId: String,
        @Path("consumptionId") consumptionId: Int,
    )

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

    @POST("api/v1/media/{mediaType}/{source}/{mediaId}/watch/")
    suspend fun watchMovie(
        @Path("mediaType") mediaType: String = "movie",
        @Path("source") source: String,
        @Path("mediaId") mediaId: String,
        @Body request: FloppyMovieWatchRequest,
    ): FloppyTrackedMedia

    @POST("api/v1/media/{mediaType}/{source}/{mediaId}/{season}/episodes/{episode}/watch/")
    suspend fun watchEpisode(
        @Path("mediaType") mediaType: String = "tv",
        @Path("source") source: String,
        @Path("mediaId") mediaId: String,
        @Path("season") season: Int,
        @Path("episode") episode: Int,
        @Body request: FloppyEpisodeWatchRequest = FloppyEpisodeWatchRequest(),
    ): FloppyTrackedMedia

    @POST("api/v1/media/{mediaType}/{source}/{mediaId}/episodes/bulk/")
    suspend fun bulkEpisodes(
        @Path("mediaType") mediaType: String = "tv",
        @Path("source") source: String,
        @Path("mediaId") mediaId: String,
        @Body request: FloppyEpisodeBulkRequest,
    ): FloppyBulkTaskResponse

    @POST("api/v1/media/{mediaType}/{source}/{mediaId}/episodes/ensure/")
    suspend fun ensureEpisodes(
        @Path("mediaType") mediaType: String = "tv",
        @Path("source") source: String,
        @Path("mediaId") mediaId: String,
        @Body request: FloppyEpisodeEnsureRequest,
    ): FloppyEpisodeEnsureResponse

    @GET("api/v1/tasks/{taskId}/")
    suspend fun taskStatus(@Path("taskId") taskId: String): FloppyTaskStatusResponse

    @POST("api/v1/media/{mediaType}/{source}/{mediaId}/{season}/episodes/{episode}/drop/")
    suspend fun dropEpisode(
        @Path("mediaType") mediaType: String = "tv",
        @Path("source") source: String,
        @Path("mediaId") mediaId: String,
        @Path("season") season: Int,
        @Path("episode") episode: Int,
        @Body request: FloppyEpisodeWatchRequest = FloppyEpisodeWatchRequest(),
    ): FloppyTrackedMedia
}
