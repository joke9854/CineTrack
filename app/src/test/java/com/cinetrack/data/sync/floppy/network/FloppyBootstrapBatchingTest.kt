package com.cinetrack.data.sync.floppy.network

import com.cinetrack.data.sync.SyncOperation
import com.cinetrack.data.sync.SyncOperationType
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
}
