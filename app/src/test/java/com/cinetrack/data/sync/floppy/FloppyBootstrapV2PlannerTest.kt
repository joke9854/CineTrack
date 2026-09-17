package com.cinetrack.data.sync.floppy

import com.cinetrack.data.sync.SyncOperation
import com.cinetrack.data.sync.SyncOperationType
import com.cinetrack.domain.LibraryStatus
import com.cinetrack.domain.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FloppyBootstrapV2PlannerTest {
    @Test
    fun modernEpisodePlannerAcceptsGapsAndMultipleSeasons() {
        val operations = listOf(
            episode("e1", 1, 1),
            episode("e3", 1, 3),
            episode("e202", 2, 2),
            episode("e407", 4, 7),
        )

        val batch = operations.bootstrapTransportUnit(canEnsureEpisodeEvents = true, canBootstrapV2 = true)

        assertEquals(listOf("e1", "e3", "e202", "e407"), batch.map { it.id })
    }

    @Test
    fun legacyEpisodePlannerKeepsContiguousSingleSeasonRestriction() {
        val operations = listOf(
            episode("e1", 1, 1),
            episode("e3", 1, 3),
            episode("e202", 2, 2),
        )

        val batch = operations.bootstrapTransportUnit(canEnsureEpisodeEvents = false, canBootstrapV2 = false)

        assertEquals(listOf("e1"), batch.map { it.id })
    }

    @Test
    fun modernShowPlannerCapsBatchAtFiftyDistinctShows() {
        val operations = (1..60).map { id ->
            SyncOperation(
                id = "show-$id",
                type = SyncOperationType.LIBRARY_STATUS,
                mediaType = MediaType.TV,
                mediaId = id,
                title = "Show $id",
                value = LibraryStatus.WATCHING.name,
                sourceVersion = id.toLong(),
            )
        }

        val batch = operations.bootstrapTransportUnit(canBootstrapV2 = true)

        assertEquals(50, batch.size)
        assertEquals(50, batch.map { it.mediaId }.toSet().size)
    }

    @Test
    fun modernMovieWaveCapsAtFiftyLogicalMoviesAndPreservesCompletedPairs() {
        val operations = (1..60).flatMap { id ->
            val version = id.toLong()
            listOf(
                SyncOperation(
                    id = "movie-$id-library",
                    type = SyncOperationType.LIBRARY_STATUS,
                    mediaType = MediaType.MOVIE,
                    mediaId = id,
                    title = "Movie $id",
                    value = LibraryStatus.COMPLETED.name,
                    sourceVersion = version,
                ),
                SyncOperation(
                    id = "movie-$id-watch",
                    type = SyncOperationType.MOVIE_WATCHED,
                    mediaType = MediaType.MOVIE,
                    mediaId = id,
                    title = "Movie $id",
                    payload = "2026-01-01T00:00:00Z",
                    sourceVersion = version,
                ),
            )
        }

        val wave = operations.bootstrapMovieWave(50)

        assertEquals(50, wave.size)
        assertTrue(wave.all { unit -> unit.size == 2 })
    }

    private fun episode(id: String, season: Int, episode: Int) = SyncOperation(
        id = id,
        type = SyncOperationType.EPISODE_WATCHED,
        mediaType = MediaType.TV,
        mediaId = 42,
        title = "Show",
        payload = "$season:$episode:2026-01-01T00:00:00Z",
        sourceVersion = "$season$episode".toLong(),
    )
}
