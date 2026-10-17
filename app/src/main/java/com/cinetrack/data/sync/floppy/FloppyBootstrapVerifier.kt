package com.cinetrack.data.sync.floppy

import com.cinetrack.data.sync.TrackingSnapshot

/** Semantic verification for a Floppy SECONDARY merge/seed bootstrap. */
class FloppyBootstrapVerifier {
    fun verify(expected: TrackingSnapshot, actual: TrackingSnapshot): Boolean =
        verify(expected, FloppyVerificationProjection.fromSnapshot(actual))

    fun verify(expected: TrackingSnapshot, actual: FloppyVerificationProjection): Boolean =
        failures(expected, actual).isEmpty()

    /** Labels of every expected item Floppy does not hold, for the log. */
    fun failures(expected: TrackingSnapshot, actual: FloppyVerificationProjection): List<String> {
        val movies = expected.movies.filterNot { item ->
            val remote = item.ids.tmdb?.let(actual.movies::get)
            val libraryOk = when (item.libraryState) {
                null -> true
                com.cinetrack.domain.LibraryStatus.NONE -> remote?.activeLibraryStatus == null
                com.cinetrack.domain.LibraryStatus.COMPLETED -> remote != null && remote.activeLibraryStatus == null &&
                    remote.completedConsumptions > 0 &&
                    (item.watchedAt == null || item.watchedAt in remote.completedAt)
                else -> remote?.activeLibraryStatus == item.libraryState
            }
            libraryOk && (!item.watched || item.libraryState == com.cinetrack.domain.LibraryStatus.COMPLETED || remote?.completedAt?.isNotEmpty() == true)
        }.map { "Movie:${it.ids.tmdb}" }
        val shows = expected.shows.filterNot { item ->
            val remote = item.ids.tmdb?.let(actual.shows::get)
            when (item.libraryState) {
                null -> true
                com.cinetrack.domain.LibraryStatus.NONE -> remote?.activeLibraryStatus == null
                com.cinetrack.domain.LibraryStatus.COMPLETED -> remote != null && remote.activeLibraryStatus == null && remote.completedConsumptions > 0
                else -> remote?.activeLibraryStatus == item.libraryState
            }
        }.map { "TV:${it.ids.tmdb}" }
        val episodes = expected.episodes.filter { it.watched }.filterNot { item ->
            actual.episodes.any { remote ->
                remote.showTmdbId == item.showIds.tmdb && remote.season == item.season &&
                    remote.episode == item.episode && remote.watched
            }
        }.map { "TV:${it.showIds.tmdb} S%02dE%02d".format(it.season, it.episode) }
        return movies + shows + episodes
    }
}

