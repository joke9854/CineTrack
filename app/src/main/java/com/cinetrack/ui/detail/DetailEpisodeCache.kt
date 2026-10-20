package com.cinetrack.ui.detail

import com.cinetrack.data.repository.BoundedLruCache
import com.cinetrack.domain.EpisodeCard

/**
 * A show's full episode list as the Detail page last loaded it. One instance lives in the app
 * container: DetailViewModel fills and reads it, and CineTrackViewModel patches the watched
 * marks synchronously when an episode write starts, so a page drawn right after the tap is
 * already up to date.
 */
class DetailEpisodeCache {
    private val cache = BoundedLruCache<Int, List<EpisodeCard>>(32)

    operator fun get(showId: Int): List<EpisodeCard>? = cache[showId]
    operator fun set(showId: Int, episodes: List<EpisodeCard>) {
        cache[showId] = episodes
    }
    fun remove(showId: Int) {
        cache.remove(showId)
    }
    fun clear() = cache.clear()

    /** Sets the watched mark of the given (season, number) episodes of one show. */
    fun markWatched(showId: Int, numbers: Set<Pair<Int, Int>>, watched: Boolean) {
        cache[showId] = cache[showId].orEmpty().map { cached ->
            if ((cached.season to cached.number) in numbers) cached.copy(watched = watched) else cached
        }
    }

    /** Clears the watched marks of a show's cached episodes (its history was removed). */
    fun forgetWatched(showId: Int) {
        cache[showId]?.let { cached -> cache[showId] = cached.map { it.copy(watched = false) } }
    }
}
