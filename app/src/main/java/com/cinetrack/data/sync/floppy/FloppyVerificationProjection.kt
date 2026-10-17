package com.cinetrack.data.sync.floppy

import com.cinetrack.data.sync.TrackingSnapshot
import com.cinetrack.domain.LibraryStatus
import java.time.Instant

/** Floppy's merge-oriented view used only for SECONDARY bootstrap checks. */
data class FloppyVerificationProjection(
    val movies: Map<Long, Movie>,
    val shows: Map<Long, Show>,
    val episodes: Set<Episode>,
) {
    data class Movie(
        val activeLibraryStatus: LibraryStatus?,
        val completedAt: Set<Instant> = emptySet(),
        val completedConsumptions: Int = 0,
    )
    data class Show(
        val activeLibraryStatus: LibraryStatus?,
        val completedConsumptions: Int = 0,
    )
    data class Episode(
        val showTmdbId: Long,
        val season: Int,
        val episode: Int,
        val watched: Boolean,
        val watchedAt: Instant? = null,
    )

    companion object {
        /** Compatibility projection for tests/providers that already expose a snapshot. */
        fun fromSnapshot(snapshot: TrackingSnapshot): FloppyVerificationProjection =
            FloppyVerificationProjection(
                movies = snapshot.movies.mapNotNull { movie -> movie.ids.tmdb?.let { id ->
                    id to Movie(
                        activeLibraryStatus = movie.libraryState,
                        completedAt = movie.watchedAt?.let(::setOf).orEmpty(),
                        completedConsumptions = if (movie.watched) 1 else 0,
                    )
                } }.toMap(),
                shows = snapshot.shows.mapNotNull { show -> show.ids.tmdb?.let { id ->
                    id to Show(show.libraryState, if (show.libraryState == LibraryStatus.COMPLETED) 1 else 0)
                } }.toMap(),
                episodes = snapshot.episodes.filter { it.showIds.tmdb != null }.mapTo(linkedSetOf()) {
                    Episode(it.showIds.tmdb!!, it.season, it.episode, it.watched, it.watchedAt)
                },
            )
    }
}

