package com.cinetrack.data.sync.floppy

import com.cinetrack.data.sync.TrackingSnapshot

/** Semantic verification for a Floppy SECONDARY merge/seed bootstrap. */
class FloppyBootstrapVerifier {
    fun verify(expected: TrackingSnapshot, actual: TrackingSnapshot): Boolean {
        val actualMovies = actual.movies.associateBy { it.ids.tmdb }
        val actualShows = actual.shows.associateBy { it.ids.tmdb }
        val actualEpisodes = actual.episodes.associateBy { Triple(it.showIds.tmdb, it.season, it.episode) }
        val moviesOk = expected.movies.all { item ->
            val remote = actualMovies[item.ids.tmdb]
            val libraryOk = when {
                item.libraryState == null -> true
                item.libraryState.name == "NONE" -> remote?.libraryState == null
                else -> remote?.libraryState == item.libraryState
            }
            libraryOk && (!item.watched || remote?.watched == true)
        }
        val showsOk = expected.shows.all { item ->
            val remote = actualShows[item.ids.tmdb]
            when {
                item.libraryState == null -> true
                item.libraryState.name == "NONE" -> remote?.libraryState == null
                else -> remote?.libraryState == item.libraryState
            }
        }
        val episodesOk = expected.episodes.filter { it.watched }.all { item ->
            actualEpisodes[Triple(item.showIds.tmdb, item.season, item.episode)]?.watched == true
        }
        return moviesOk && showsOk && episodesOk
    }
}

