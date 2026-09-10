package com.cinetrack.data.repository

/** Both watching and undoing a watch invalidate an in-flight next-episode result. */
internal fun changedEpisodeShows(
    before: Set<Triple<Int, Int, Int>>,
    after: Set<Triple<Int, Int, Int>>,
): Set<Int> = ((before - after) + (after - before)).map { it.first }.toSet()
