package com.cinetrack.data.repository

import com.cinetrack.data.remote.TmdbMediaDto
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class TrendingCandidatesTest {
    private fun title(id: Int, vararg countries: String) = TmdbMediaDto(id = id, originCountries = countries.toList())

    @Test fun regionalTrendingKeepsRankThenAppendsDeduplicatedFallback() = runBlocking {
        val pages = mutableListOf<Int>()
        var fallbackCalls = 0
        val result = loadTrendingCandidates(setOf("IT", "US"), { page ->
            pages += page
            if (page == 1) listOf(title(9, "IT"), title(2, "FR"), title(7, "US", "GB"))
            else listOf(title(6, "IT"), title(7, "US"), title(1))
        }, {
            fallbackCalls++
            listOf(title(7, "US"), title(3, "IT"), title(9, "IT"), title(4, "US"))
        })
        assertEquals(listOf(9, 7, 6, 3, 4), result.map { it.id })
        assertEquals(listOf(1, 2), pages.sorted())
        assertEquals(1, fallbackCalls)
    }

    @Test fun tenMatchingTrendingItemsNeedNoFallback() = runBlocking {
        val result = loadTrendingCandidates(setOf("IT"), { page ->
            ((page - 1) * 5 + 1..page * 5).map { title(it, "IT") }
        }, { error("Fallback must not run") })
        assertEquals((1..10).toList(), result.map { it.id })
    }

    @Test fun noCountryFilterKeepsThreeGlobalPagesAndNeverUsesFallback() = runBlocking {
        val pages = mutableListOf<Int>()
        val result = loadTrendingCandidates(emptySet(), { page ->
            pages += page
            listOf(title(page))
        }, { error("Global Trending must not use Popular") })
        assertEquals(listOf(1, 2, 3), pages.sorted())
        assertEquals(listOf(1, 2, 3), result.map { it.id })
    }

    @Test fun hiddenTitlesStayHiddenInBothSources() = runBlocking {
        val result = loadTrendingCandidates(setOf("IT"), { listOf(title(1, "IT")) },
            { listOf(title(1, "IT"), title(2, "IT")) }, include = { it.id != 1 })
        assertEquals(listOf(2), result.map { it.id })
    }

    @Test fun regionalPagesOverlapWithoutChangingTheirRankOrder() = runBlocking {
        val bothStarted = CompletableDeferred<Unit>()
        var started = 0
        val result = withTimeout(2_000) {
            loadTrendingCandidates(setOf("IT"), { page ->
                started++
                if (started == 2) bothStarted.complete(Unit)
                // Serial loading would never reach the second request and time out.
                bothStarted.await()
                listOf(title(page, "IT"))
            }, { emptyList() })
        }
        assertEquals(listOf(1, 2), result.map { it.id })
    }
}
