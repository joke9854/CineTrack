package com.cinetrack.data.remote

import com.cinetrack.BuildConfig
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

@Serializable
data class TmdbPage(val page: Int = 1, val results: List<TmdbMediaDto> = emptyList())

@Serializable
data class TmdbFindResponse(
    @SerialName("movie_results") val movieResults: List<TmdbMediaDto> = emptyList(),
    @SerialName("tv_results") val tvResults: List<TmdbMediaDto> = emptyList(),
)

@Serializable
data class TmdbMediaDto(
    val id: Int,
    val title: String? = null,
    val name: String? = null,
    val overview: String = "",
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
    @SerialName("genre_ids") val genreIds: List<Int> = emptyList(),
    @SerialName("origin_country") val originCountries: List<String> = emptyList(),
    @SerialName("original_language") val originalLanguage: String? = null,
    @SerialName("media_type") val mediaType: String? = null,
    val runtime: Int? = null,
    val status: String? = null,
    val budget: Long? = null,
    val revenue: Long? = null,
    val networks: List<TmdbCompanyDto> = emptyList(),
    @SerialName("production_companies") val productionCompanies: List<TmdbCompanyDto> = emptyList(),
    @SerialName("production_countries") val productionCountries: List<TmdbCountryDto> = emptyList(),
    @SerialName("episode_run_time") val episodeRunTime: List<Int> = emptyList(),
    val genres: List<TmdbGenreDto> = emptyList(),
    @SerialName("belongs_to_collection") val collection: TmdbCollectionRefDto? = null,
    val credits: TmdbCreditsDto? = null,
    val recommendations: TmdbPage? = null,
    val videos: TmdbVideoResultsDto? = null,
    @SerialName("next_episode_to_air") val nextEpisodeToAir: TmdbEpisodeDto? = null,
    @SerialName("watch/providers") val watchProviders: TmdbProviderResultDto? = null,
    val seasons: List<TmdbSeasonSummaryDto> = emptyList(),
)

@Serializable data class TmdbVideoResultsDto(val results: List<TmdbVideoDto> = emptyList())
@Serializable data class TmdbVideoDto(
    val key: String = "",
    val site: String = "",
    val type: String = "",
    val official: Boolean = false,
    @SerialName("published_at") val publishedAt: String? = null,
)

@Serializable data class TmdbGenreDto(val id: Int, val name: String)
@Serializable data class TmdbCompanyDto(val id: Int = 0, val name: String = "", @SerialName("logo_path") val logoPath: String? = null)
@Serializable data class TmdbCountryDto(@SerialName("iso_3166_1") val code: String, val name: String = "")
@Serializable data class TmdbCollectionRefDto(val id: Int, val name: String, @SerialName("poster_path") val posterPath: String? = null)
@Serializable data class TmdbCreditsDto(val cast: List<TmdbPersonCreditDto> = emptyList(), val crew: List<TmdbPersonCreditDto> = emptyList())
@Serializable data class TmdbPersonCreditDto(
    val id: Int,
    val name: String,
    val character: String? = null,
    val job: String? = null,
    @SerialName("profile_path") val profilePath: String? = null,
)
@Serializable data class TmdbProviderResultDto(val results: Map<String, TmdbProviderCountryDto> = emptyMap())
@Serializable data class TmdbProviderListDto(val results: List<TmdbProviderDto> = emptyList())
@Serializable data class TmdbProviderCountryDto(val link: String? = null, val flatrate: List<TmdbProviderDto> = emptyList(), val rent: List<TmdbProviderDto> = emptyList(), val buy: List<TmdbProviderDto> = emptyList())
@Serializable data class TmdbProviderDto(@SerialName("provider_id") val id: Int, @SerialName("provider_name") val name: String, @SerialName("logo_path") val logoPath: String? = null)
@Serializable data class TmdbSeasonSummaryDto(
    val id: Int,
    @SerialName("season_number") val number: Int,
    val name: String,
    @SerialName("episode_count") val episodeCount: Int = 0,
    @SerialName("poster_path") val posterPath: String? = null,
)

@Serializable
data class TmdbSeasonDto(val id: Int, val name: String, val episodes: List<TmdbEpisodeDto> = emptyList())

@Serializable
data class TmdbEpisodeDto(
    val id: Int,
    val name: String,
    val overview: String = "",
    @SerialName("air_date") val airDate: String? = null,
    @SerialName("still_path") val stillPath: String? = null,
    val runtime: Int? = null,
    @SerialName("season_number") val season: Int,
    @SerialName("episode_number") val number: Int,
    @SerialName("guest_stars") val guestStars: List<TmdbPersonCreditDto> = emptyList(),
    val crew: List<TmdbPersonCreditDto> = emptyList(),
)

@Serializable
data class TmdbPersonDto(
    val id: Int,
    val name: String,
    val biography: String = "",
    val birthday: String? = null,
    @SerialName("place_of_birth") val placeOfBirth: String? = null,
    @SerialName("profile_path") val profilePath: String? = null,
)

@Serializable
data class TmdbMovieCreditsDto(val cast: List<TmdbMovieCreditDto> = emptyList())

@Serializable
data class TmdbMovieCreditDto(
    val id: Int,
    val title: String,
    val character: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
)

@Serializable
data class TmdbCombinedCreditsDto(val cast: List<TmdbCombinedCreditDto> = emptyList())

@Serializable
data class TmdbCombinedCreditDto(
    val id: Int,
    val title: String? = null,
    val name: String? = null,
    @SerialName("media_type") val mediaType: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
)

@Serializable data class TmdbCollectionDto(val id: Int, val name: String, val parts: List<TmdbMediaDto> = emptyList())

interface TmdbService {
    @GET("3/trending/tv/day") suspend fun trendingTv(@Query("page") page: Int = 1): TmdbPage
    @GET("3/trending/movie/day") suspend fun trendingMovies(@Query("page") page: Int = 1): TmdbPage
    @GET("3/movie/upcoming") suspend fun upcomingMovies(@Query("page") page: Int = 1): TmdbPage
    @GET("3/watch/providers/movie")
    suspend fun movieProviders(@Query("watch_region") region: String? = null): TmdbProviderListDto
    @GET("3/watch/providers/tv")
    suspend fun tvProviders(@Query("watch_region") region: String? = null): TmdbProviderListDto
    @GET("3/discover/movie")
    suspend fun discoverMovies(
        @Query("with_origin_country") originCountries: String? = null,
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("primary_release_date.gte") dateFrom: String? = null,
        @Query("primary_release_year") releaseYear: Int? = null,
        @Query("with_genres") genreIds: String? = null,
        @Query("without_genres") excludedGenreIds: String? = null,
        @Query("vote_average.gte") minimumRating: Double? = null,
        @Query("vote_count.gte") minimumVotes: Int? = null,
        @Query("with_watch_providers") providerIds: String? = null,
        @Query("watch_region") watchRegion: String? = null,
        @Query("with_runtime.lte") maximumRuntime: Int? = null,
        @Query("with_original_language") originalLanguage: String? = null,
        @Query("primary_release_date.lte") dateTo: String? = null,
        @Query("page") page: Int = 1,
    ): TmdbPage
    @GET("3/discover/tv")
    suspend fun discoverTv(
        @Query("with_origin_country") originCountries: String? = null,
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("first_air_date_year") releaseYear: Int? = null,
        @Query("with_genres") genreIds: String? = null,
        @Query("without_genres") excludedGenreIds: String? = null,
        @Query("vote_average.gte") minimumRating: Double? = null,
        @Query("vote_count.gte") minimumVotes: Int? = null,
        @Query("with_watch_providers") providerIds: String? = null,
        @Query("watch_region") watchRegion: String? = null,
        @Query("with_runtime.lte") maximumRuntime: Int? = null,
        @Query("with_original_language") originalLanguage: String? = null,
        @Query("first_air_date.gte") dateFrom: String? = null,
        @Query("first_air_date.lte") dateTo: String? = null,
        @Query("page") page: Int = 1,
    ): TmdbPage
    @GET("3/discover/tv")
    suspend fun upcomingTv(
        @Query("first_air_date.gte") dateFrom: String,
        @Query("sort_by") sortBy: String = "first_air_date.asc",
        @Query("include_null_first_air_dates") includeUndated: Boolean = false,
        @Query("with_origin_country") originCountries: String? = null,
        @Query("page") page: Int = 1,
    ): TmdbPage
    @GET("3/search/multi") suspend fun search(@Query("query") query: String, @Query("page") page: Int = 1): TmdbPage
    @GET("3/find/{externalId}") suspend fun find(
        @Path("externalId") externalId: String,
        @Query("external_source") externalSource: String,
    ): TmdbFindResponse
    @GET("3/movie/{id}") suspend fun movie(@Path("id") id: Int, @Query("append_to_response") append: String = "credits,recommendations,watch/providers,videos", @Query("language") language: String? = null): TmdbMediaDto
    @GET("3/tv/{id}") suspend fun show(@Path("id") id: Int, @Query("append_to_response") append: String = "credits,recommendations,watch/providers,videos", @Query("language") language: String? = null): TmdbMediaDto
    @GET("3/tv/{id}/season/{season}") suspend fun season(@Path("id") id: Int, @Path("season") season: Int, @Query("language") language: String? = null): TmdbSeasonDto
    @GET("3/tv/{id}/season/{season}/episode/{episode}") suspend fun episode(@Path("id") id: Int, @Path("season") season: Int, @Path("episode") episode: Int, @Query("append_to_response") append: String = "credits", @Query("language") language: String? = null): TmdbEpisodeDto
    @GET("3/person/{id}") suspend fun person(@Path("id") id: Int, @Query("language") language: String? = null): TmdbPersonDto
    @GET("3/person/{id}/movie_credits") suspend fun movieCredits(@Path("id") id: Int): TmdbMovieCreditsDto
    @GET("3/person/{id}/combined_credits") suspend fun combinedCredits(@Path("id") id: Int): TmdbCombinedCreditsDto
    @GET("3/collection/{id}") suspend fun collection(@Path("id") id: Int): TmdbCollectionDto
}

@Serializable data class MdbListRatingRequest(val ids: List<String>, val provider: String)
@Serializable data class MdbListRatingResponse(val ratings: List<MdbListRatingDto> = emptyList())
@Serializable data class MdbListRatingDto(val id: String? = null, val rating: Double? = null)

interface MdbListService {
    @GET("tmdb/{mediaType}/{id}/")
    suspend fun mediaInfo(
        @Path("mediaType") mediaType: String,
        @Path("id") id: Int,
        @Query("apikey") apiKey: String,
    ): JsonObject

    @POST("rating/{mediaType}/{ratingSource}")
    suspend fun rating(
        @Path("mediaType") mediaType: String,
        @Path("ratingSource") ratingSource: String,
        @Query("apikey") apiKey: String,
        @Body request: MdbListRatingRequest,
    ): MdbListRatingResponse
}

@Serializable
data class SimklOAuthResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String? = null,
    val scope: String? = null,
    @SerialName("expires_in") val expiresIn: Long? = null,
)

@Serializable data class SimklActivity(val all: String = "", @SerialName("tv_shows") val tvShows: SimklActivityGroup = SimklActivityGroup(), val movies: SimklActivityGroup = SimklActivityGroup(), val anime: SimklActivityGroup = SimklActivityGroup())
@Serializable data class SimklActivityGroup(
    val all: String = "",
    @SerialName("rated_at") val ratedAt: String? = null,
    @SerialName("removed_from_list") val removedFromList: String? = null,
    val plantowatch: String? = null,
    val watching: String? = null,
    val completed: String? = null,
    val hold: String? = null,
    val dropped: String? = null,
)
@Serializable data class SimklLibraryResponse(val shows: List<SimklLibraryItem> = emptyList(), val movies: List<SimklLibraryItem> = emptyList(), val anime: List<SimklLibraryItem> = emptyList())
@Serializable data class SimklLibraryItem(@SerialName("added_to_watchlist_at") val addedAt: String? = null, @SerialName("last_watched_at") val lastWatchedAt: String? = null, val status: String = "", val show: SimklMedia? = null, val movie: SimklMedia? = null, val seasons: List<SimklSeason> = emptyList())
@Serializable data class SimklMedia(val title: String = "", val poster: String? = null, val year: Int? = null, val runtime: Int? = null, val ids: SimklIds = SimklIds())
@Serializable data class SimklIds(val simkl: Long? = null, val tmdb: String? = null, val tvdb: String? = null, val imdb: String? = null, val slug: String? = null)
@Serializable data class SimklSeason(val number: Int, val episodes: List<SimklEpisode> = emptyList())
@Serializable data class SimklEpisode(val number: Int, @SerialName("watched_at") val watchedAt: String? = null, val title: String = "")
@Serializable data class SimklPlaybackEpisode(val season: Int? = null, val number: Int? = null, val episode: Int? = null, val title: String = "")
@Serializable data class SimklPlaybackItem(
    val id: Int,
    val progress: Double = 0.0,
    @SerialName("paused_at") val pausedAt: String = "",
    val type: String = "",
    val episode: SimklPlaybackEpisode? = null,
    val show: SimklMedia? = null,
    val movie: SimklMedia? = null,
)
@Serializable data class SimklCalendarItem(
    val title: String = "",
    val date: String = "",
    @SerialName("release_date") val releaseDate: String = "",
    val ids: SimklCalendarIds = SimklCalendarIds(),
    val episode: SimklCalendarEpisode = SimklCalendarEpisode(),
)
@Serializable data class SimklCalendarIds(
    @SerialName("simkl_id") val simklId: Long? = null,
    val tmdb: String? = null,
)
@Serializable data class SimklCalendarEpisode(
    val season: Int = 0,
    @SerialName("episode") val number: Int = 0,
)
@Serializable data class SimklSyncRequest(val movies: List<SimklSyncItem> = emptyList(), val shows: List<SimklSyncItem> = emptyList())
@Serializable data class SimklSyncItem(
    val ids: SimklIds,
    /** Required per item by Simkl's current /sync/add-to-list contract. */
    val to: String? = null,
    @SerialName("watched_at") val watchedAt: String? = null,
    val status: String? = null,
    val seasons: List<SimklSeason> = emptyList(),
)
@Serializable data class SimklNotFoundItems(
    val movies: List<JsonObject> = emptyList(),
    val shows: List<JsonObject> = emptyList(),
)
@Serializable data class SimklRemoveResponse(
    @SerialName("not_found") val notFound: SimklNotFoundItems = SimklNotFoundItems(),
)

interface SimklAuthService {
    @FormUrlEncoded
    @POST("oauth/token")
    suspend fun exchangeCode(
        @Field("code") code: String,
        @Field("client_id") clientId: String,
        @Field("code_verifier") codeVerifier: String,
        @Field("redirect_uri") redirectUri: String,
        @Field("grant_type") grantType: String = "authorization_code",
    ): SimklOAuthResponse
}

interface SimklSyncService {
    @GET("sync/activities") suspend fun activities(): SimklActivity
    @GET("sync/playback/{type}") suspend fun playback(@Path("type") type: String): List<SimklPlaybackItem>
    @GET("sync/all-items/{type}") suspend fun allItems(
        @Path("type") type: String,
        @Query("date_from") dateFrom: String? = null,
        @Query("extended") extended: String = "full",
        @Query("episode_watched_at") episodeWatchedAt: String = "yes",
        @Query("include_all_episodes") includeAllEpisodes: String = "yes",
    ): SimklLibraryResponse
    @POST("sync/history") suspend fun addHistory(@Body request: SimklSyncRequest)
    @POST("sync/history/remove") suspend fun removeHistory(@Body request: SimklSyncRequest): SimklRemoveResponse
    @POST("sync/add-to-list") suspend fun addToList(@Body request: SimklSyncRequest)
}

interface SimklCalendarService {
    @GET("calendar/tv.json") suspend fun tv(): List<SimklCalendarItem>
    @GET("calendar/anime.json") suspend fun anime(): List<SimklCalendarItem>
}

data class ApiServices(
    val tmdb: TmdbService,
    val mdbList: MdbListService,
    val simklAuth: SimklAuthService,
    val simklSync: SimklSyncService,
    val simklCalendar: SimklCalendarService,
)

object NetworkFactory {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }
    private val TMDB_API_KEY_PATTERN = Regex("[A-Fa-f0-9]{32}")

    fun create(
        token: () -> String?,
        tmdbApiKey: () -> String,
        metadataLanguage: () -> String,
        metadataRegion: () -> String,
        metadataTimezone: () -> String,
    ): ApiServices {
        val logger = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
        }
        val common = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(logger)
            .build()

        val tmdbClient = common.newBuilder().addInterceptor { chain ->
            val original = chain.request()
            val language = metadataLanguage().takeUnless { it == "system" }.orEmpty()
                .ifBlank { Locale.getDefault().toLanguageTag() }
            val region = metadataRegion().takeUnless { it == "system" }.orEmpty()
                .ifBlank { Locale.getDefault().country }
            val timezone = metadataTimezone().takeUnless { it == "system" }.orEmpty()
                .ifBlank { TimeZone.getDefault().id }
            val key = tmdbApiKey().trim()
            val url = original.url.newBuilder().apply {
                if (original.url.queryParameter("language") == null && language.isNotBlank()) addQueryParameter("language", language)
                if (original.url.queryParameter("region") == null && region.isNotBlank()) addQueryParameter("region", region)
                if (original.url.queryParameter("timezone") == null && timezone.isNotBlank()) addQueryParameter("timezone", timezone)
                if (key.matches(TMDB_API_KEY_PATTERN)) addQueryParameter("api_key", key)
            }.build()
            val request = original.newBuilder().url(url)
                .header("accept", "application/json")
                .apply { if (key.isNotBlank() && !key.matches(TMDB_API_KEY_PATTERN)) header("Authorization", "Bearer $key") }
                .build()
            chain.proceed(request)
        }.build()

        val simklClient = common.newBuilder().addInterceptor { chain ->
            val original = chain.request()
            val url = original.url.newBuilder().apply {
                if (BuildConfig.SIMKL_CLIENT_ID.isNotBlank()) addQueryParameter("client_id", BuildConfig.SIMKL_CLIENT_ID)
                addQueryParameter("app-name", "CineTrack")
                addQueryParameter("app-version", BuildConfig.VERSION_NAME)
            }.build()
            val request = original.newBuilder().url(url).apply {
                token()?.takeIf(String::isNotBlank)?.let { header("Authorization", "Bearer $it") }
                header("simkl-api-key", BuildConfig.SIMKL_CLIENT_ID)
                header("Content-Type", "application/json")
                header("Accept", "application/json")
                header("User-Agent", "CineTrack/${BuildConfig.VERSION_NAME} (Android)")
            }.build()
            chain.proceed(request)
        }.build()
        val simklAuthClient = common.newBuilder().addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("Accept", "application/json")
                    .header("User-Agent", "CineTrack/${BuildConfig.VERSION_NAME} (Android)")
                    .build(),
            )
        }.build()

        fun retrofit(baseUrl: String, client: OkHttpClient) = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        return ApiServices(
            tmdb = retrofit("https://api.themoviedb.org/", tmdbClient).create(TmdbService::class.java),
            mdbList = retrofit("https://api.mdblist.com/", common.newBuilder().build()).create(MdbListService::class.java),
            simklAuth = retrofit("https://api.simkl.com/", simklAuthClient).create(SimklAuthService::class.java),
            simklSync = retrofit("https://api.simkl.com/", simklClient).create(SimklSyncService::class.java),
            simklCalendar = retrofit("https://data.simkl.in/", common.newBuilder().build()).create(SimklCalendarService::class.java),
        )
    }
}
