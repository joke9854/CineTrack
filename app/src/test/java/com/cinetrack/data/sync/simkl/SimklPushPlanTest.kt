package com.cinetrack.data.sync.simkl

import com.cinetrack.data.sync.SyncOperation
import com.cinetrack.data.sync.SyncOperationType
import com.cinetrack.domain.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SimklPushPlanTest {
    private fun watched(show: Int, season: Int, episode: Int, at: String = "2026-10-12T15:00:00Z", id: String = "write:$show$season$episode") =
        SyncOperation(id, SyncOperationType.EPISODE_WATCHED, MediaType.TV, show, "s", payload = "$season:$episode:$at", sourceVersion = 1L)

    private fun unwatched(show: Int, season: Int, episode: Int) =
        SyncOperation("write:u$show$season$episode", SyncOperationType.EPISODE_UNWATCHED, MediaType.TV, show, "s", payload = "$season:$episode", sourceVersion = 1L)

    private fun status(show: Int) =
        SyncOperation("state:TV:$show", SyncOperationType.LIBRARY_STATUS, MediaType.TV, show, "s", value = "COMPLETED", sourceVersion = 1L)

    private fun List<SimklPushStep>.shape() = map { step ->
        when (step) {
            is SimklPushStep.Single -> "1:${step.operation.type}"
            is SimklPushStep.EpisodeBatch -> "${step.operations.size}:${if (step.watched) "W" else "U"}"
        }
    }

    @Test
    fun `a single change is still sent on its own`() {
        assertEquals(listOf("1:EPISODE_WATCHED"), planSimklPush(listOf(watched(1, 1, 1))).shape())
    }

    @Test
    fun `a whole series becomes one request`() {
        val series = (1..13).map { watched(7, 1, it) }
        val steps = planSimklPush(series)
        assertEquals(listOf("13:W"), steps.shape())
        assertEquals(series, (steps.single() as SimklPushStep.EpisodeBatch).operations)
    }

    @Test
    fun `other changes keep their place and split batches`() {
        val plan = planSimklPush(listOf(watched(1, 1, 1), watched(1, 1, 2), status(1), watched(1, 1, 3), watched(1, 1, 4)))
        assertEquals(listOf("2:W", "1:LIBRARY_STATUS", "2:W"), plan.shape())
    }

    @Test
    fun `watched and unwatched are never mixed and keep their order`() {
        val plan = planSimklPush(listOf(unwatched(1, 1, 1), unwatched(1, 1, 2), watched(1, 1, 1), watched(1, 1, 2)))
        assertEquals(listOf("2:U", "2:W"), plan.shape())
    }

    @Test
    fun `a rewatch of the same episode stays a separate request`() {
        val plan = planSimklPush(listOf(
            watched(1, 1, 1, "2026-01-01T00:00:00Z", "write:a"),
            watched(1, 1, 2),
            watched(1, 1, 1, "2026-02-01T00:00:00Z", "write:b"),
        ))
        assertEquals(listOf("2:W", "1:EPISODE_WATCHED"), plan.shape())
    }

    @Test
    fun `batches are capped`() {
        val plan = planSimklPush((1..1200).map { watched(1, it / 100 + 1, it % 100 + 1) }, maxEpisodes = 500)
        assertEquals(listOf("500:W", "500:W", "200:W"), plan.shape())
    }

    @Test
    fun `several shows can share a batch`() {
        assertEquals(listOf("3:W"), planSimklPush(listOf(watched(1, 1, 1), watched(2, 1, 1), watched(3, 2, 5))).shape())
    }

    @Test
    fun `an unreadable change is left to the single path`() {
        val broken = SyncOperation("write:x", SyncOperationType.EPISODE_WATCHED, MediaType.TV, 1, "s", payload = "x:y", sourceVersion = 1L)
        assertEquals(listOf("1:EPISODE_WATCHED", "1:EPISODE_WATCHED"), planSimklPush(listOf(broken, watched(1, 1, 1))).shape())
    }

    @Test
    fun `targets keep the exact time and fall back to the generation`() {
        val exact = watched(1, 2, 3, "2026-10-12T15:00:00.123Z").simklEpisodeTarget()
        assertEquals(SimklEpisodeTarget(2, 3, "2026-10-12T15:00:00.123Z"), exact)
        val noTime = SyncOperation("write:n", SyncOperationType.EPISODE_WATCHED, MediaType.TV, 1, "s", payload = "1:2", sourceVersion = 1_000L)
        assertEquals("1970-01-01T00:00:01Z", noTime.simklEpisodeTarget().watchedAt)
        assertNull(unwatched(1, 1, 1).simklEpisodeTarget().watchedAt)
        val mirror = SyncOperation("mirror:episode-watched:9:4:7", SyncOperationType.EPISODE_WATCHED, MediaType.TV, 9, "s", sourceVersion = 1L)
        assertTrue(mirror.simklEpisodeTarget().let { it.season == 4 && it.episode == 7 })
    }
}
