package com.cinetrack.domain

/** A page is exhausted by the server's page count, never by local filtering. */
data class DiscoverPage(val items: List<MediaCard>, val hasMore: Boolean)

data class DiscoverBrowseState(
    val items: List<MediaCard> = emptyList(),
    val nextPage: Int = 1,
    val hasMore: Boolean = true,
    val loading: Boolean = false,
    val failed: Boolean = false,
)

fun DiscoverBrowseState.appendPage(page: DiscoverPage, upcoming: Boolean = false): DiscoverBrowseState {
    val combined = (items + page.items).distinctBy(MediaCard::stableKey)
    return copy(items = if (upcoming) combined.sortedBy { it.releaseDate ?: "9999" } else combined,
        nextPage = nextPage + 1, hasMore = page.hasMore, failed = false)
}

fun discoverBrowseKey(railId: String, state: AppUiState): String =
    listOf(railId, state.metadataLanguage, state.metadataRegion, state.contentRegions.sorted().joinToString(",")).joinToString(":")
