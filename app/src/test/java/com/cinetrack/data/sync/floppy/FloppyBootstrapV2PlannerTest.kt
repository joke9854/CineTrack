package com.cinetrack.data.sync.floppy

import com.cinetrack.data.sync.MediaIds
import com.cinetrack.data.sync.SyncOperation
import com.cinetrack.data.sync.SyncOperationType
import com.cinetrack.data.sync.TrackedEpisodeState
import com.cinetrack.data.sync.TrackingSnapshot
import com.cinetrack.data.sync.floppyBootstrapShowEpisodePrefix
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

    @Test
    fun specialsNeverShareARequestWithRegularEpisodes() {
        val mixed = listOf(episode("s0e1", 0, 1), episode("s0e2", 0, 2), episode("e1", 1, 1), episode("e202", 2, 2))

        val regular = mixed.bootstrapTransportUnit(canEnsureEpisodeEvents = true, canBootstrapV2 = true)
        val specials = mixed.filterNot { it in regular }.bootstrapTransportUnit(canEnsureEpisodeEvents = true, canBootstrapV2 = true)

        assertEquals(listOf("e1", "e202"), regular.map { it.id })
        assertEquals(listOf("s0e1", "s0e2"), specials.map { it.id })
    }

    @Test
    fun historyOrderedPlanStillFillsOneShowRequestToFifty() {
        // Watch history interleaves shows 12 and 123, so a small plan window
        // holds only a few episodes of either show.
        val history = (1..60).flatMap { n ->
            listOf(
                TrackedEpisodeState(MediaIds(tmdb = 12), 1 + (n - 1) / 20, 1 + (n - 1) % 20, true, java.time.Instant.parse("2025-01-01T00:00:00Z").plusSeconds(n * 60L)),
                TrackedEpisodeState(MediaIds(tmdb = 123), 1, n, true, java.time.Instant.parse("2025-02-01T00:00:00Z").plusSeconds(n * 60L)),
            )
        }.reversed()
        val plan = buildFloppyBootstrapOperations("instance-a", TrackingSnapshot(episodes = history))

        val window = plan.take(10).bootstrapTransportUnit(canEnsureEpisodeEvents = true, canBootstrapV2 = true)
        val showUnit = plan
            .filter { it.id.startsWith(floppyBootstrapShowEpisodePrefix("instance-a", window.first().mediaId)) }
            .bootstrapTransportUnit(canEnsureEpisodeEvents = true, canBootstrapV2 = true)

        assertEquals(5, window.size)
        assertEquals(50, showUnit.size)
        assertTrue(showUnit.all { it.mediaId == 12 })
        assertEquals("1:1", showUnit.first().payload!!.split(":").take(2).joinToString(":"))
        assertTrue(plan.filter { it.id.startsWith(floppyBootstrapShowEpisodePrefix("instance-a", 12)) }.none { it.mediaId == 123 })
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
