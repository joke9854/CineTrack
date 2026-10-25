package com.cinetrack.data.media

import com.cinetrack.data.remote.FanartImage
import com.cinetrack.data.remote.FanartMovieDto
import com.cinetrack.data.remote.FanartService
import com.cinetrack.data.remote.FanartShowDto
import com.cinetrack.data.remote.TmdbService
import com.cinetrack.domain.MediaCard
import com.cinetrack.domain.MediaType
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.lang.reflect.Proxy

class FanartRepositoryTest {
    private val tmdb = Proxy.newProxyInstance(TmdbService::class.java.classLoader, arrayOf(TmdbService::class.java)) { _, _, _ -> error("unused") } as TmdbService

    private class Fanart(val answers: MutableMap<Int, () -> FanartMovieDto>) : FanartService {
        val calls = mutableListOf<Int>()
        override suspend fun movie(tmdbId: Int, apiKey: String): FanartMovieDto { calls += tmdbId; return answers.getValue(tmdbId)() }
        override suspend fun show(tvdbId: Int, apiKey: String): FanartShowDto = error("unused")
    }

    private fun movie(id: Int) = MediaCard(id, MediaType.MOVIE, "Movie $id")
    private fun notFound(): Nothing = throw HttpException(Response.error<Any>(404, "".toResponseBody(null)))

    @Test
    fun aFailedRequestIsRetriedButATitleWithoutArtIsNot() = runBlocking {
        var offline = true
        val fanart = Fanart(
            mutableMapOf(
                1 to { if (offline) throw IOException("offline") else FanartMovieDto(hdLogos = listOf(FanartImage("http://assets.fanart.tv/logo.png", "en"))) },
                2 to { notFound() },
            ),
        )
        val repository = FanartRepository({ fanart }, { tmdb }, { "key" }, { "en" })

        assertNull(repository.logo(movie(1)))
        offline = false
        assertEquals("https://assets.fanart.tv/logo.png", repository.logo(movie(1)))
        assertNull(repository.logo(movie(2)))
        assertNull(repository.logo(movie(2)))
        // Movie 1 was asked twice (the failure isn't remembered), movie 2 once (no art is).
        assertEquals(listOf(1, 1, 2), fanart.calls)
    }

    @Test
    fun prefetchSkipsKnownTitlesAndHandsOverNewPictures() = runBlocking {
        val fanart = Fanart(mutableMapOf(3 to { FanartMovieDto(logos = listOf(FanartImage("https://a/3.png"))) }, 4 to { notFound() }))
        val repository = FanartRepository({ fanart }, { tmdb }, { "key" }, { "en" })
        val fetched = mutableListOf<String>()

        repository.prefetch(listOf(movie(3), movie(4))) { fetched += it }
        repository.prefetch(listOf(movie(3), movie(4))) { fetched += it }

        assertEquals(listOf("https://a/3.png"), fetched)
        assertEquals("https://a/3.png", repository.cached(movie(3)))
        assertEquals(listOf(3, 4), fanart.calls)
    }
}
