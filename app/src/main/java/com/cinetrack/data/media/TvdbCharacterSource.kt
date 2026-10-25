package com.cinetrack.data.media

import com.cinetrack.data.remote.TmdbService
import com.cinetrack.data.remote.TvdbLoginBody
import com.cinetrack.data.remote.TvdbService
import com.cinetrack.data.remote.tvdbCharacterImages
import com.cinetrack.domain.CharacterImage
import com.cinetrack.domain.MediaType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * TVDB character pictures with the user's own TVDB key, for when Floppy is not connected.
 * The key (and PIN) are traded for a bearer token once; the token stays in memory only and is
 * sent in the Authorization header (OkHttp drops it on a redirect to another host). Shows are
 * found by TMDB's TVDB id; movies through their IMDb id and TVDB's remote-id search.
 */
class TvdbCharacterSource(
    private val tvdb: () -> TvdbService,
    private val tmdb: () -> TmdbService,
    private val credentials: suspend () -> Pair<String, String>,
) {
    private val tokenLock = Mutex()
    @Volatile private var token: String? = null
    /** TVDB movie id per TMDB movie id; 0 when TVDB has none. */
    private val movieIds = ConcurrentHashMap<Int, Int>()

    /** True when a key is saved. */
    suspend fun configured(): Boolean = credentials().first.isNotBlank()

    /** Logs in with [apiKey] and [pin]; throws when TVDB refuses them. */
    suspend fun verify(apiKey: String, pin: String) {
        login(apiKey, pin)
    }

    /** The title's on-screen characters; null when no key is saved. */
    suspend fun characters(type: MediaType, tmdbId: Int): List<CharacterImage>? {
        if (!configured()) return null
        return try {
            fetch(type, tmdbId, bearer())
        } catch (error: retrofit2.HttpException) {
            if (error.code() != 401) throw error
            // The month-long token ran out: log in again once.
            token = null
            fetch(type, tmdbId, bearer())
        }
    }

    fun clear() {
        token = null
        movieIds.clear()
    }

    private suspend fun fetch(type: MediaType, tmdbId: Int, bearer: String): List<CharacterImage> {
        val characters = if (type == MediaType.MOVIE) {
            val id = movieId(tmdbId, bearer) ?: return emptyList()
            tvdb().movie(id, bearer).data?.characters
        } else {
            val id = tmdb().tvExternalIds(tmdbId).tvdbId ?: return emptyList()
            tvdb().series(id, bearer).data?.characters
        }
        return tvdbCharacterImages(characters.orEmpty())
    }

    private suspend fun movieId(tmdbId: Int, bearer: String): Int? {
        movieIds[tmdbId]?.let { return it.takeIf { id -> id > 0 } }
        val imdb = tmdb().movieExternalIds(tmdbId).imdbId?.takeIf(String::isNotBlank) ?: return null
        val id = tvdb().remoteId(imdb, bearer).data.orEmpty().firstNotNullOfOrNull { it.movie?.id } ?: 0
        movieIds[tmdbId] = id
        return id.takeIf { it > 0 }
    }

    private suspend fun bearer(): String = tokenLock.withLock {
        val current = token ?: credentials().let { (key, pin) -> login(key, pin) }.also { token = it }
        "Bearer $current"
    }

    private suspend fun login(apiKey: String, pin: String): String {
        val response = try {
            tvdb().login(TvdbLoginBody(apiKey.trim(), pin.trim().takeIf(String::isNotBlank)))
        } catch (error: retrofit2.HttpException) {
            throw IllegalArgumentException(
                if (error.code() == 401) "TVDB did not accept this key (a user-supported key also needs your subscriber PIN)"
                else "TVDB answered ${error.code()}",
            )
        }
        return response.data?.token?.takeIf(String::isNotBlank) ?: error("TVDB login returned no token")
    }
}
