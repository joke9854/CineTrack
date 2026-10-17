package com.cinetrack.data.sync.floppy.network

import com.cinetrack.data.sync.SyncOperation
import com.cinetrack.data.sync.SyncOperationType
import com.cinetrack.data.sync.floppy.bootstrapTransportUnit
import com.cinetrack.data.sync.floppy.bootstrapMovieWave
import com.cinetrack.domain.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FloppyBootstrapBatchingTest {
    private val remote = FloppyRemoteDataSource(FloppyApiClientFactory())
    private fun op(id: String, show: Int, season: Int, episode: Int) = SyncOperation(
        id = id,
        type = SyncOperationType.EPISODE_WATCHED,
        mediaType = MediaType.TV,
        mediaId = show,
        title = "show",
        payload = "$season:$episode:2026-09-16T12:00:00Z",
        sourceVersion = 1L,
    )

    @Test
    fun contiguousEpisodesForOneShowShareOneRun() {
        val runs = remote.contiguousEpisodeRuns(
            listOf(op("3", 10, 1, 3), op("1", 10, 1, 1), op("2", 10, 1, 2)),
        )
        assertEquals(listOf(listOf("1", "2", "3")), runs.map { it.map(SyncOperation::id) })
    }

    @Test
    fun gapsAndDifferentShowsAreNeverMixed() {
        val runs = remote.contiguousEpisodeRuns(
            listOf(op("a", 10, 1, 1), op("b", 10, 1, 3), op("c", 11, 1, 2)),
        )
        assertEquals(setOf("a", "b", "c"), runs.flatten().map(SyncOperation::id).toSet())
        assertTrue(runs.all { run -> run.map { it.mediaId }.toSet().size == 1 })
    }
    @Test
    fun crossSeasonEpisodesNeverCreateAnInclusiveRange() {
        val runs = remote.contiguousEpisodeRuns(
            listOf(op("s1e3", 10, 1, 3), op("s2e1", 10, 2, 1)),
        )
        assertEquals(listOf(listOf("s1e3"), listOf("s2e1")), runs.map { it.map(SyncOperation::id) })
    }

    @Test
    fun workerTransportUnitKeepsAContiguousSeasonRangeTogether() {
        val unit = listOf(op("3", 10, 1, 3), op("4", 10, 1, 4), op("5", 10, 1, 5)).bootstrapTransportUnit()
        assertEquals(listOf("3", "4", "5"), unit.map(SyncOperation::id))
    }

    @Test
    fun workerTransportUnitSplitsGapsAndSeasonBoundaries() {
        val gap = listOf(op("3", 10, 1, 3), op("5", 10, 1, 5)).bootstrapTransportUnit()
        assertEquals(listOf("3"), gap.map(SyncOperation::id))
        val boundary = listOf(op("s1", 10, 1, 3), op("s2", 10, 2, 1)).bootstrapTransportUnit()
        assertEquals(listOf("s1"), boundary.map(SyncOperation::id))
    }

    @Test
    fun movieWaveUsesDistinctMoviesAndIsBounded() {
        val rows = (1..10).map { movie -> SyncOperation(
            id = "movie-$movie",
            type = SyncOperationType.MOVIE_WATCHED,
            mediaType = MediaType.MOVIE,
            mediaId = movie,
            title = "movie",
            payload = "2026-09-16T12:00:00Z",
            sourceVersion = movie.toLong(),
        ) }
        val wave = rows.bootstrapMovieWave(4)
        assertEquals(4, wave.size)
        assertEquals(4, wave.map { it.single().mediaId }.toSet().size)
    }

}
