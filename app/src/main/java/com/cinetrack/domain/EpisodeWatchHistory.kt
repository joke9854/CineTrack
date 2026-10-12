package com.cinetrack.domain

import java.time.ZoneOffset

/** Latest known watch timestamp for each season/episode pair. */
fun latestEpisodeWatchTimes(history: List<TimelineCard>): Map<Pair<Int, Int>, String> =
    history.asSequence()
        .mapNotNull { event ->
            val season = event.season ?: return@mapNotNull null
            val episode = event.episodeNumber ?: return@mapNotNull null
            (season to episode) to event.timestamp
        }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, timestamps) ->
            timestamps.maxByOrNull { timestamp ->
                releaseInstant(timestamp, ZoneOffset.UTC)?.toEpochMilli() ?: Long.MIN_VALUE
            }.orEmpty()
        }

/** How many times something was watched and when last. Every watch event counts, so rewatches add up. */
data class WatchSummary(val count: Int, val last: String)

fun watchSummary(history: List<TimelineCard>, mediaKey: String, season: Int? = null, episode: Int? = null): WatchSummary? {
    val events = history.filter { it.media.stableKey == mediaKey && it.season == season && it.episodeNumber == episode }
    if (events.isEmpty()) return null
    val last = events.maxByOrNull { releaseInstant(it.timestamp, ZoneOffset.UTC)?.toEpochMilli() ?: Long.MIN_VALUE }!!.timestamp
    return WatchSummary(events.size, last)
}

/** Watch events per episode of one show, so rewatched episodes can say how often. */
fun episodeWatchCounts(history: List<TimelineCard>, showId: Int): Map<Pair<Int, Int>, Int> =
    history.asSequence()
        .filter { it.media.id == showId && it.media.type == MediaType.TV }
        .mapNotNull { event -> event.season?.let { season -> event.episodeNumber?.let { season to it } } }
        .groupingBy { it }
        .eachCount()
