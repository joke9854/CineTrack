package com.cinetrack.data.sync.simkl

import com.cinetrack.data.remote.SimklEpisode
import com.cinetrack.data.remote.SimklIds
import com.cinetrack.data.remote.SimklSeason
import com.cinetrack.data.remote.SimklSyncItem
import com.cinetrack.data.sync.numbering.SimklTarget

/** One episode play about to be sent to Simkl: its local (TMDB) coordinate, exact time and Simkl target. */
internal data class SimklEpisodePlay(
    val season: Int,
    val episode: Int,
    val watchedAt: String?,
    val target: SimklTarget = SimklTarget.Unchanged,
)

/**
 * Groups plays of one show into Simkl seasons after mapping. Native and unchanged plays share
 * one item; TVDB-numbered plays go in a second item flagged `use_tvdb_anime_seasons`.
 * watched_at is passed through untouched.
 */
internal fun simklShowItems(ids: SimklIds, plays: List<SimklEpisodePlay>): List<SimklSyncItem> {
    fun seasonsOf(items: List<Triple<Int, Int, String?>>): List<SimklSeason> =
        items.groupBy { it.first }.toSortedMap().map { (season, entries) ->
            SimklSeason(season, entries.sortedBy { it.second }.map { SimklEpisode(it.second, it.third) })
        }

    val plain = mutableListOf<Triple<Int, Int, String?>>()
    val tvdb = mutableListOf<Triple<Int, Int, String?>>()
    plays.forEach { play ->
        when (val target = play.target) {
            is SimklTarget.Native -> plain += Triple(target.season, target.episode, play.watchedAt)
            is SimklTarget.Tvdb -> tvdb += Triple(target.season, target.episode, play.watchedAt)
            SimklTarget.Unchanged -> plain += Triple(play.season, play.episode, play.watchedAt)
        }
    }
    return buildList {
        if (plain.isNotEmpty()) add(SimklSyncItem(ids = ids, seasons = seasonsOf(plain)))
        if (tvdb.isNotEmpty()) add(SimklSyncItem(ids = ids, seasons = seasonsOf(tvdb), useTvdbAnimeSeasons = true))
    }
}

/** Maps queued local (TMDB) episodes to the numbering Simkl expects; watched_at is untouched. */
internal suspend fun simklEpisodeItems(
    numbering: com.cinetrack.data.sync.numbering.EpisodeNumbering?,
    showId: Int,
    targets: List<SimklEpisodeTarget>,
): List<SimklSyncItem> {
    val mapped = numbering?.toSimklBatch(showId, targets.map { it.season to it.episode }).orEmpty()
    return simklShowItems(
        SimklIds(tmdb = showId.toString()),
        targets.map { target ->
            SimklEpisodePlay(target.season, target.episode, target.watchedAt, mapped[target.season to target.episode] ?: SimklTarget.Unchanged)
        },
    )
}
