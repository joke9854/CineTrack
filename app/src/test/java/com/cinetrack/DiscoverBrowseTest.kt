package com.cinetrack

import com.cinetrack.data.remote.TmdbPage
import com.cinetrack.domain.*
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class DiscoverBrowseTest {
    private fun movie(id: Int) = MediaCard(id, MediaType.MOVIE, "Title $id")

    @Test fun browseAddsTitlesBeyondTheCachedRowWithoutDuplicates() {
        var browse = DiscoverBrowseState(items = (1..60).map(::movie))
        for (page in 1..4) browse = browse.appendPage(DiscoverPage(((page - 1) * 20 + 1..page * 20).map(::movie), true))
        assertEquals(80, browse.items.size)
        assertEquals((1..80).toList(), browse.items.map { it.id })
        assertEquals(5, browse.nextPage)
    }

    @Test fun filteredEmptyPageStillAdvancesUntilServerExhaustion() {
        val empty = DiscoverBrowseState().appendPage(DiscoverPage(emptyList(), true))
        assertTrue(empty.hasMore)
        assertEquals(2, empty.nextPage)
        val last = empty.appendPage(DiscoverPage(listOf(movie(2)), false))
        assertFalse(last.hasMore)
        assertEquals(1, last.items.size)
    }

    @Test fun upcomingKeepsMoviesAndShowsWithSameIdAndOrdersDates() {
        val movie = movie(1).copy(releaseDate = "2026-12-10")
        val show = MediaCard(1, MediaType.TV, "Show", releaseDate = "2026-10-01")
        val merged = DiscoverBrowseState(items = listOf(movie)).appendPage(DiscoverPage(listOf(movie, show), false), upcoming = true)
        assertEquals(listOf(show, movie), merged.items)
    }

    @Test fun metadataAndRegionChangesDoNotReuseOtherCountriesPages() {
        val original = AppUiState()
        val key = discoverBrowseKey(RailIds.POPULAR_MOVIES, original)
        assertNotEquals(key, discoverBrowseKey(RailIds.POPULAR_MOVIES, original.copy(contentRegions = setOf("IT"))))
        assertNotEquals(key, discoverBrowseKey(RailIds.POPULAR_MOVIES, original.copy(metadataLanguage = "it")))
        assertNotEquals(key, discoverBrowseKey(RailIds.POPULAR_TV, original))
    }

    @Test fun readsTmdbTotalPagesForReliableEndOfList() {
        val page = Json.decodeFromString<TmdbPage>("""{"page":4,"total_pages":12,"results":[]}""")
        assertEquals(12, page.totalPages)
    }
}
