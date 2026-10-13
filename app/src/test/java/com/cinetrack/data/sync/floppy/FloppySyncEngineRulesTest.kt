package com.cinetrack.data.sync.floppy

import com.cinetrack.data.local.WatchHistoryEntity
import com.cinetrack.domain.LibraryStatus
import com.cinetrack.domain.MediaType
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FloppySyncEngineRulesTest {
    private fun play(watchedAt: String?) = FloppySyncPlayDto(key = "episode:1", watchedAt = watchedAt)

    private fun row(id: Long, watchedAt: String) = WatchHistoryEntity(
        id = id,
        mediaType = MediaType.TV.name,
        mediaId = 1001,
        season = 1,
        episodeNumber = 1,
        watchedAt = watchedAt,
    )

    @Test
    fun `rewatches stay distinct plays at their exact instants`() {
        val plan = planFloppyPlays(
            listOf(play("2024-01-03T21:13:00.987654+00:00"), play("2024-02-03T21:13:00+00:00")),
            local = emptyList(),
            replace = true,
        )

        assertEquals(
            listOf(Instant.parse("2024-01-03T21:13:00.987654Z"), Instant.parse("2024-02-03T21:13:00Z")),
            plan.added,
        )
        assertEquals(2, plan.remaining)
        assertEquals(Instant.parse("2024-02-03T21:13:00Z"), plan.latestAdded)
    }

    @Test
    fun `the same instant in another format is the same play`() {
        val plan = planFloppyPlays(
            listOf(play("2024-01-03T21:13:00.500000+00:00")),
            local = listOf(row(7, "2024-01-03T21:13:00.500Z")),
            replace = true,
        )

        assertFalse(plan.changed)
        assertEquals(1, plan.remaining)
    }

    @Test
    fun `the first snapshot only adds and never removes local plays`() {
        val plan = planFloppyPlays(
            listOf(play("2024-02-03T21:13:00Z")),
            local = listOf(row(7, "2024-01-03T21:13:00Z")),
            replace = false,
        )

        assertEquals(listOf(Instant.parse("2024-02-03T21:13:00Z")), plan.added)
        assertTrue(plan.removed.isEmpty())
        assertEquals(2, plan.remaining)
    }

    @Test
    fun `the change feed removes plays Floppy no longer has`() {
        val plan = planFloppyPlays(
            emptyList(),
            local = listOf(row(7, "2024-01-03T21:13:00Z"), row(8, "2024-02-03T21:13:00Z")),
            replace = true,
        )

        assertEquals(listOf(7L, 8L), plan.removed.map { it.id })
        assertEquals(0, plan.remaining)
        assertNull(plan.latest)
    }

    @Test
    fun `an untimed Floppy play is never invented and never removes local plays`() {
        val plan = planFloppyPlays(
            listOf(play(null)),
            local = listOf(row(7, "2024-01-03T21:13:00Z")),
            replace = true,
        )

        assertFalse(plan.changed)
        assertEquals(1, plan.remaining)
    }

    @Test
    fun `a play already filed under another coordinate is not added twice`() {
        val instant = Instant.parse("2024-01-03T21:13:00Z")
        val plan = planFloppyPlays(
            listOf(play("2024-01-03T21:13:00+00:00")),
            local = emptyList(),
            replace = false,
            elsewhere = setOf(instant),
        )

        assertTrue(plan.added.isEmpty())
    }

    @Test
    fun `only an explicit removal in the change feed removes local plays`() {
        val present = FloppySyncItemDto(type = "movie", source = "tmdb", mediaId = "603", inLibrary = true)
        val removed = present.copy(inLibrary = false)

        // Floppy may hold fewer plays than this device: a missing play is not a deletion.
        assertFalse(removedOnFloppy(present, snapshotMode = false))
        assertFalse(removedOnFloppy(removed, snapshotMode = true))
        assertTrue(removedOnFloppy(removed, snapshotMode = false))
    }

    @Test
    fun `the cursor never moves past an item skipped for a local intent`() {
        assertEquals(40L, floppyCursorToStore(storedCursor = 10, nextCursor = 40, skippedSequence = null))
        assertEquals(24L, floppyCursorToStore(storedCursor = 10, nextCursor = 40, skippedSequence = 25))
        assertEquals(10L, floppyCursorToStore(storedCursor = 10, nextCursor = 40, skippedSequence = 5))
        assertEquals(0L, floppyCursorToStore(storedCursor = null, nextCursor = 40, skippedSequence = 0))
    }

    @Test
    fun `items map to CineTrack TMDB keys and statuses`() {
        fun item(type: String, source: String, ids: Map<String, String> = emptyMap(), status: String? = null) =
            FloppySyncItemDto(type = type, source = source, mediaId = "603", ids = ids, status = status)

        assertEquals(603, item("movie", "tmdb").localTmdbId())
        assertEquals(604, item("show", "tvdb", ids = mapOf("tmdb" to "604")).localTmdbId())
        // An episode's ids are the episode's own, never its show's.
        assertNull(item("episode", "tvdb", ids = mapOf("tmdb" to "604")).localTmdbId())
        assertNull(item("movie", "mal").localTmdbId())
        assertEquals(MediaType.TV, item("episode", "tmdb").mediaType())
        assertEquals(LibraryStatus.PLAN_TO_WATCH, item("show", "tmdb", status = "planning").libraryStatus())
        assertEquals(LibraryStatus.WATCHING, item("show", "tmdb", status = "in_progress").libraryStatus())
        assertNull(item("episode", "tmdb").libraryStatus())
    }
}
