package com.cinetrack.data.sync.floppy

import com.cinetrack.data.sync.TrackingSnapshot

/** Semantic verification for a Floppy SECONDARY merge/seed bootstrap. */
class FloppyBootstrapVerifier {
    fun verify(expected: TrackingSnapshot, actual: TrackingSnapshot): Boolean =
        verify(expected, FloppyVerificationProjection.fromSnapshot(actual))

    fun verify(expected: TrackingSnapshot, actual: FloppyVerificationProjection): Boolean {
        val moviesOk = expected.movies.all { item ->
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
        }
        val showsOk = expected.shows.all { item ->
            val remote = item.ids.tmdb?.let(actual.shows::get)
            when (item.libraryState) {
                null -> true
                com.cinetrack.domain.LibraryStatus.NONE -> remote?.activeLibraryStatus == null
                com.cinetrack.domain.LibraryStatus.COMPLETED -> remote != null && remote.activeLibraryStatus == null && remote.completedConsumptions > 0
                else -> remote?.activeLibraryStatus == item.libraryState
            }
        }
        val episodesOk = expected.episodes.filter { it.watched }.all { item ->
            actual.episodes.any { remote ->
                remote.showTmdbId == item.showIds.tmdb && remote.season == item.season &&
                    remote.episode == item.episode && remote.watched &&
                    (item.watchedAt == null || remote.watchedAt == item.watchedAt)
            }
        }
        return moviesOk && showsOk && episodesOk
    }
}

