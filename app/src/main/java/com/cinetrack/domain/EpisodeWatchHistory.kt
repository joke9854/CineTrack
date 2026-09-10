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
