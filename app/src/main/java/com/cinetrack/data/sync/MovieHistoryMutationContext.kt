package com.cinetrack.data.sync

import com.cinetrack.domain.LibraryStatus
import java.time.Instant

/**
 * Versioned provider-neutral context for a movie history mutation.
 *
 * The old queue stored either a LibraryStatus or an ISO timestamp in the same
 * string field.  Providers must not guess which meaning an arbitrary string
 * has, so new rows use the explicit `v2|` encoding while the parser keeps the
 * two legacy forms readable.
 */
data class MovieHistoryMutationContext(
    val desiredLibraryStatus: LibraryStatus?,
    val previousWatched: Boolean?,
    val previousWatchedAt: Instant?,
) {
    fun encode(): String = buildString {
        append("v2|")
        append("status=").append(desiredLibraryStatus?.name.orEmpty()).append('|')
        append("previousWatched=")
            .append(previousWatched?.let { if (it) "1" else "0" }.orEmpty())
            .append('|')
        append("previousWatchedAt=").append(previousWatchedAt?.toString().orEmpty())
    }

    companion object {
        fun parse(raw: String?): MovieHistoryMutationContext? {
            val value = raw?.takeIf(String::isNotBlank) ?: return null
            if (value.startsWith("v2|")) {
                val fields = value.removePrefix("v2|").split('|')
                    .mapNotNull { part -> part.split('=', limit = 2).takeIf { it.size == 2 } }
                    .associate { it[0] to it[1] }
                val status = fields["status"]?.takeIf(String::isNotBlank)
                    ?.let { runCatching { LibraryStatus.valueOf(it) }.getOrNull() }
                val previousWatched = when (fields["previousWatched"]) {
                    "1" -> true
                    "0" -> false
                    else -> null
                }
                return MovieHistoryMutationContext(
                    desiredLibraryStatus = status,
                    previousWatched = previousWatched,
                    previousWatchedAt = fields["previousWatchedAt"]?.takeIf(String::isNotBlank)
                        ?.let { runCatching { Instant.parse(it) }.getOrNull() },
                )
            }
            // Legacy rows used a plain desired LibraryStatus for unwatch.
            runCatching { LibraryStatus.valueOf(value) }.getOrNull()?.let {
                return MovieHistoryMutationContext(it, null, null)
            }
            // Legacy rows from intermediate builds used a plain watchedAt.
            runCatching { Instant.parse(value) }.getOrNull()?.let {
                return MovieHistoryMutationContext(null, true, it)
            }
            return null
        }
    }
}

