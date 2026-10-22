package com.cinetrack.ui

/** How long ago something happened, in the coarse steps the sync labels show. */
internal sealed interface Elapsed {
    data object JustNow : Elapsed
    data class Minutes(val count: Long) : Elapsed
    data class Hours(val count: Long) : Elapsed
    data class Days(val count: Long) : Elapsed
}

/** Time since [millis]; a future time counts as just now. */
internal fun elapsedSince(millis: Long, now: Long = System.currentTimeMillis()): Elapsed {
    val seconds = (now - millis).coerceAtLeast(0L) / 1_000L
    return when {
        seconds < 60 -> Elapsed.JustNow
        seconds < 3_600 -> Elapsed.Minutes(seconds / 60)
        seconds < 86_400 -> Elapsed.Hours(seconds / 3_600)
        else -> Elapsed.Days(seconds / 86_400)
    }
}
