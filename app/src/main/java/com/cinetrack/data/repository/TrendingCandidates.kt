package com.cinetrack.data.repository

import com.cinetrack.data.remote.TmdbMediaDto
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** Keep the global trending rank; country-filtered popularity only fills a short list. */
internal suspend fun loadTrendingCandidates(
    allowedOrigins: Set<String>,
    loadPage: suspend (Int) -> List<TmdbMediaDto>,
    loadFallback: suspend () -> List<TmdbMediaDto>,
    include: (TmdbMediaDto) -> Boolean = { true },
): List<TmdbMediaDto> = coroutineScope {
    val pages = if (allowedOrigins.isEmpty()) 1..3 else 1..2
    val trending = pages.map { page -> async { loadPage(page) } }.awaitAll().flatten()
        .filter(include).distinctBy { it.id }
    if (allowedOrigins.isEmpty()) return@coroutineScope trending
    val matching = trending.filter { title -> title.originCountries.any { it in allowedOrigins } }
    if (matching.size >= 10) matching
    else (matching + loadFallback().filter(include)).distinctBy { it.id }
}
