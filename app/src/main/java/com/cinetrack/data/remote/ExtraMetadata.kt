package com.cinetrack.data.remote

import com.cinetrack.BuildConfig
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * TVmaze's public API: exact air instants per episode, no key. Limit: 20 calls per 10 s.
 * [validate] is the premium user API (HTTP basic: TVmaze username and API key); the public
 * calls never send it.
 */
interface TvmazeService {
    @GET("lookup/shows") suspend fun lookup(@Query("thetvdb") tvdb: Int? = null, @Query("imdb") imdb: String? = null): TvmazeShowDto
    @GET("shows/{id}/episodes") suspend fun episodes(@Path("id") id: Int): List<TvmazeEpisodeDto>
    @GET("v1/auth/validate") suspend fun validate(@Header("Authorization") basicAuth: String): TvmazePremiumDto
}

/** 1 Bronze, 2 Silver, 3 Gold, 0 none. */
@Serializable data class TvmazePremiumDto(val premium: Int = 0)

@Serializable data class TvmazeShowDto(val id: Int)
@Serializable data class TvmazeEpisodeDto(val season: Int = 0, val number: Int? = null, val airdate: String? = null, val airstamp: String? = null)

/** Fanart.tv: title logos. The personal API key goes in a header, never in the URL. */
interface FanartService {
    @GET("v3/movies/{id}") suspend fun movie(@Path("id") tmdbId: Int, @Header("api-key") apiKey: String): FanartMovieDto
    @GET("v3/tv/{id}") suspend fun show(@Path("id") tvdbId: Int, @Header("api-key") apiKey: String): FanartShowDto
}

@Serializable data class FanartImage(val url: String, val lang: String? = null, val likes: String? = null)
@Serializable data class FanartMovieDto(
    @SerialName("hdmovielogo") val hdLogos: List<FanartImage> = emptyList(),
    @SerialName("movielogo") val logos: List<FanartImage> = emptyList(),
)
@Serializable data class FanartShowDto(
    @SerialName("hdtvlogo") val hdLogos: List<FanartImage> = emptyList(),
    @SerialName("clearlogo") val logos: List<FanartImage> = emptyList(),
)

/**
 * TVDB v4 with the user's own key (and subscriber PIN for user-supported keys): [login] trades
 * them for a bearer token, sent in a header only, never in the URL. Used for character pictures
 * when Floppy is not connected.
 */
interface TvdbService {
    @retrofit2.http.POST("v4/login") suspend fun login(@retrofit2.http.Body body: TvdbLoginBody): TvdbEnvelope<TvdbTokenDto>
    @GET("v4/series/{id}/extended") suspend fun series(@Path("id") id: Int, @Header("Authorization") bearer: String): TvdbEnvelope<TvdbCharactersDto>
    @GET("v4/movies/{id}/extended") suspend fun movie(@Path("id") id: Int, @Header("Authorization") bearer: String): TvdbEnvelope<TvdbCharactersDto>
    @GET("v4/search/remoteid/{id}") suspend fun remoteId(@Path("id") remoteId: String, @Header("Authorization") bearer: String): TvdbEnvelope<List<TvdbRemoteResultDto>>
}

@Serializable data class TvdbLoginBody(val apikey: String, val pin: String? = null)
@Serializable data class TvdbEnvelope<T>(val data: T? = null)
@Serializable data class TvdbTokenDto(val token: String? = null)
@Serializable data class TvdbCharactersDto(val characters: List<TvdbCharacterDto>? = null)
@Serializable data class TvdbCharacterDto(
    val name: String? = null,
    val personName: String? = null,
    val image: String? = null,
    val type: Int? = null,
    val peopleType: String? = null,
    val sort: Int? = null,
)
@Serializable data class TvdbRemoteResultDto(val movie: TvdbIdDto? = null, val series: TvdbIdDto? = null)
@Serializable data class TvdbIdDto(val id: Int? = null)

@Serializable data class TmdbExternalIdsDto(
    @SerialName("tvdb_id") val tvdbId: Int? = null,
    @SerialName("imdb_id") val imdbId: String? = null,
)

/** Clients for the keyless or user-keyed metadata add-ons, built on the shared HTTP client. */
object ExtraMetadataClients {
    private fun retrofit(baseUrl: String) = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(
            SharedHttp.webClient.newBuilder().addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("User-Agent", "CineTrack/${BuildConfig.VERSION_NAME} (Android)").build())
            }.build(),
        )
        .addConverterFactory(AppJson.network.asConverterFactory("application/json".toMediaType()))
        .build()

    val tvmaze: TvmazeService by lazy { retrofit("https://api.tvmaze.com/").create(TvmazeService::class.java) }
    val fanart: FanartService by lazy { retrofit("https://webservice.fanart.tv/").create(FanartService::class.java) }
    val tvdb: TvdbService by lazy { retrofit("https://api4.thetvdb.com/").create(TvdbService::class.java) }
}

/** HTTP basic credentials for TVmaze's user API (username, API key as password). */
internal fun tvmazeBasicAuth(username: String, apiKey: String): String = okhttp3.Credentials.basic(username.trim(), apiKey.trim())

/** TVDB characters on screen (actor, guest star, voice) with their picture, as absolute URLs. */
internal fun tvdbCharacterImages(characters: List<TvdbCharacterDto>): List<com.cinetrack.domain.CharacterImage> =
    characters.filter { character ->
        val type = character.type
        if (type != null) type in setOf(3, 4) else character.peopleType?.trim()?.lowercase() in setOf("actor", "guest star", "voice")
    }.sortedWith(compareBy(nullsLast()) { it.sort }).mapNotNull { character ->
        val actor = character.personName?.trim().orEmpty()
        val name = character.name?.trim().orEmpty()
        if (actor.isBlank() && name.isBlank()) return@mapNotNull null
        val image = character.image?.trim()?.takeIf(String::isNotBlank)?.let {
            if (it.startsWith("http")) it.replaceFirst("http://", "https://") else "https://artworks.thetvdb.com" + (if (it.startsWith("/")) it else "/$it")
        }
        com.cinetrack.domain.CharacterImage(name, actor, image)
    }

/** The logo to show: the app language's, else English, else one without text language, else the most liked. */
internal fun pickLogo(images: List<FanartImage>, language: String): String? {
    if (images.isEmpty()) return null
    val ranked = images.sortedByDescending { it.likes?.toIntOrNull() ?: 0 }
    return (ranked.firstOrNull { it.lang == language } ?: ranked.firstOrNull { it.lang == "en" }
        ?: ranked.firstOrNull { it.lang.isNullOrBlank() || it.lang == "00" } ?: ranked.first()).url.replaceFirst("http://", "https://")
}
