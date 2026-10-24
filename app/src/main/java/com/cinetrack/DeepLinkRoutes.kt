package com.cinetrack

import com.cinetrack.domain.MediaType

/**
 * Maps a `cinetrack://app|detail|episode` link to a navigation route. Links are BROWSABLE, so
 * any web page can fire one: ids must be positive ints and the type a known [MediaType], or the
 * link is dropped instead of crashing NavController on an argument it can't parse.
 */
internal object DeepLinkRoutes {
    fun route(host: String?, path: String?, segments: List<String>): String? = when (host) {
        "app" -> when (path) {
            "/search" -> "search/discover"
            "/up-next", "/progress" -> "progress"
            "/sync" -> "sync"
            "/sync-operations" -> "settings-detail/sync-operations"
            "/library" -> "library"
            else -> null
        }
        "episode" -> {
            val show = segments.getOrNull(0)?.positiveInt()
            val season = segments.getOrNull(1)?.toIntOrNull()?.takeIf { it >= 0 }
            val number = segments.getOrNull(2)?.positiveInt()
            if (show != null && season != null && number != null) "episode/$show/$season/$number" else null
        }
        "detail" -> {
            val type = segments.getOrNull(0)?.uppercase()?.takeIf { name -> MediaType.entries.any { it.name == name } }
            val id = segments.getOrNull(1)?.positiveInt()
            if (type != null && id != null) "detail/$type/$id" else null
        }
        else -> null
    }

    private fun String.positiveInt(): Int? = toIntOrNull()?.takeIf { it > 0 }
}
