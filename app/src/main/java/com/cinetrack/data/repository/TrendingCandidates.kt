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
    val matching = trending.filter { title -> title.inContentRegions(allowedOrigins) }
    if (matching.size >= 10) matching
    else (matching + loadFallback().filter(include)).distinctBy { it.id }
}

/**
 * Whether a TMDB title belongs to the content regions. TMDB list entries for
 * movies carry no origin country, so a title without one is judged by its
 * original language: kept only when that language is spoken in a chosen region.
 */
internal fun TmdbMediaDto.inContentRegions(allowed: Set<String>): Boolean {
    if (allowed.isEmpty()) return true
    val regions = allowed.mapTo(HashSet()) { it.uppercase() }
    if (originCountries.isNotEmpty()) return originCountries.any { it.uppercase() in regions }
    val language = originalLanguage?.lowercase() ?: return false
    return language in regionLanguages(regions)
}

private val regionLanguageCache = java.util.concurrent.ConcurrentHashMap<Set<String>, Set<String>>()

internal fun regionLanguages(allowed: Set<String>): Set<String> = regionLanguageCache.getOrPut(allowed) {
    java.util.Locale.getAvailableLocales()
        .filter { it.country.uppercase() in allowed && it.language.isNotBlank() }
        .map { it.language.lowercase() }
        .toSet()
}
