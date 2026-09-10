package com.cinetrack.domain

import kotlin.math.roundToInt

/** Average known regular episodes; missing values and specials must not skew the result. */
fun averageEpisodeRuntime(showId: Int, episodes: List<EpisodeCard>, fallback: Int?): Int? {
    val runtimes = episodes.asSequence()
        .filter { it.showId == showId && it.season > 0 && it.number > 0 && (it.runtimeMinutes ?: 0) > 0 }
        .distinctBy { it.scheduleKey }
        .map { it.runtimeMinutes!! }.toList()
    return if (runtimes.isEmpty()) fallback?.takeIf { it > 0 } else runtimes.average().roundToInt()
}
