package com.cinetrack.data.schedule

import com.cinetrack.data.local.AppDatabase
import com.cinetrack.data.local.EpisodeAirDateEntity

/**
 * Air dates are kept per provider (TMDB, TVmaze, Simkl) so the one the user
 * picks in Settings can be shown, and switching providers later needs no
 * new download: each episode's shown date is recomputed from what is kept.
 */
object AirDateSource {
    const val TMDB = "tmdb"
    const val SIMKL = "simkl"
    const val TVMAZE = "tvmaze"
    const val DEFAULT = TVMAZE
    /** Removed in 0.99.78 (TVmaze gives the same exact times): a saved choice reads as TVmaze, stored rows are ignored. */
    const val RETIRED_TRAKT = "trakt"

    /** Most precise first: TVmaze has exact air instants, Simkl calendar times, TMDB dates. */
    val all = listOf(TVMAZE, SIMKL, TMDB)

    /** A saved choice that is no longer offered (Trakt, or anything unknown) becomes the default. */
    fun normalize(value: String?): String = value?.takeIf { it in all } ?: DEFAULT
}

/**
 * The date to show: the chosen provider's, else the next provider that has
 * one. [legacy] is the date already stored before per-provider dates existed
 * (its provider is unknown); it ranks after TVmaze and Simkl and before TMDB,
 * so updating never swaps a calendar time for a bare TMDB date, unless TMDB
 * is the chosen provider.
 */
internal fun resolveAirDate(dates: Map<String, String>, preferred: String, legacy: String? = null): String? {
    // Rows from a retired provider (Trakt) are never shown.
    fun of(source: String) = dates[source]?.takeIf { it.isNotBlank() && source in AirDateSource.all }
    if (preferred == AirDateSource.TMDB) {
        return of(AirDateSource.TMDB) ?: of(AirDateSource.TVMAZE) ?: of(AirDateSource.SIMKL) ?: legacy
    }
    val first = AirDateSource.normalize(preferred)
    val calendar = listOf(first) + listOf(AirDateSource.TVMAZE, AirDateSource.SIMKL).filter { it != first }
    return calendar.firstNotNullOfOrNull(::of) ?: legacy?.takeIf(String::isNotBlank) ?: of(AirDateSource.TMDB)
}

internal typealias EpisodeKey = Triple<Int, Int, Int>

/** Saves [dates] from [source] and returns the date each of those episodes should now show. */
internal suspend fun recordAirDates(
    database: AppDatabase,
    source: String,
    dates: Map<EpisodeKey, String?>,
    preferred: String,
    legacy: Map<EpisodeKey, String?> = emptyMap(),
    now: Long = System.currentTimeMillis(),
): Map<EpisodeKey, String?> {
    val rows = dates.mapNotNull { (key, date) ->
        date?.takeIf(String::isNotBlank)?.let { EpisodeAirDateEntity(key.first, key.second, key.third, source, it, now) }
    }
    if (rows.isNotEmpty()) database.mediaDao().upsertAirDates(rows)
    val kept = database.mediaDao().airDatesForShows(dates.keys.map(EpisodeKey::first).distinct())
        .groupBy { EpisodeKey(it.showId, it.season, it.number) }
    return dates.keys.associateWith { key ->
        resolveAirDate(kept[key].orEmpty().associate { it.source to it.airDate }, preferred, legacy[key])
    }
}

/** After the provider choice changes: every stored episode shows its date from the new choice. */
internal suspend fun reapplyAirDates(database: AppDatabase, preferred: String) {
    val kept = database.mediaDao().allAirDates().groupBy { EpisodeKey(it.showId, it.season, it.number) }
    kept.forEach { (key, rows) ->
        val date = resolveAirDate(rows.associate { it.source to it.airDate }, preferred) ?: return@forEach
        database.mediaDao().setEpisodeAirDate(key.first, key.second, key.third, date)
    }
}
