package com.cinetrack.data.sync.floppy

import com.cinetrack.data.sync.MediaIds
import com.cinetrack.data.sync.TrackedEpisodeState
import com.cinetrack.data.sync.TrackedMovieState
import com.cinetrack.data.sync.TrackedShowState
import com.cinetrack.data.sync.TrackingSnapshot
import com.cinetrack.domain.LibraryStatus
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FloppyLibraryCheckTest {
    private val movieAt = Instant.parse("2026-10-01T20:00:00Z")
    private val episodeAt = Instant.parse("2026-10-02T21:30:00Z")

    private fun movie(id: Long, at: Instant? = movieAt) =
        TrackedMovieState(MediaIds(tmdb = id), LibraryStatus.COMPLETED, watched = true, watchedAt = at)

    private fun show(id: Long, status: LibraryStatus = LibraryStatus.WATCHING) = TrackedShowState(MediaIds(tmdb = id), status)

    private fun episode(show: Long, season: Int, number: Int, at: Instant? = episodeAt) =
        TrackedEpisodeState(MediaIds(tmdb = show), season, number, watched = true, watchedAt = at)

    private fun remoteMovie(id: Long, vararg plays: Instant) =
        FloppySyncItemDto(type = "movie", source = "tmdb", mediaId = "$id", status = "completed", plays = plays.map { FloppySyncPlayDto("p", it.toString()) })

    private fun remoteShow(id: Long, status: String = "in_progress") =
        FloppySyncItemDto(type = "show", source = "tmdb", mediaId = "$id", status = status)

    private fun remoteEpisode(show: Long, season: Int, number: Int, vararg plays: Instant) = FloppySyncItemDto(
        type = "episode",
        source = "tmdb",
        mediaId = "$show",
        seasonNumber = season,
        episodeNumber = number,
        // Floppy writes offsets, not "Z".
        plays = plays.map { FloppySyncPlayDto("e", it.toString().replace("Z", "+00:00")) },
    )

    @Test
    fun `a library Floppy fully holds has nothing missing`() {
        val expected = TrackingSnapshot(
            movies = listOf(movie(1)),
            shows = listOf(show(10)),
            // Stored by Floppy under other numbering: same show, same instant.
            episodes = listOf(episode(10, 1, 212)),
        )
        val remote = listOf(remoteMovie(1, movieAt), remoteShow(10), remoteEpisode(10, 4, 212, episodeAt))

        assertTrue(floppyMissing(expected, remote).isEmpty)
    }

    @Test
    fun `missing plays, items and statuses are listed`() {
        val expected = TrackingSnapshot(
            movies = listOf(movie(1), movie(2)),
            shows = listOf(show(10), show(11, LibraryStatus.PAUSED)),
            episodes = listOf(episode(10, 1, 1), episode(10, 1, 2), episode(10, 1, 3)),
        )
        val remote = listOf(
            remoteMovie(1, Instant.parse("2026-10-01T20:00:05Z")), // another instant
            remoteShow(10),
            remoteShow(11, status = "in_progress"),
            // Three episodes at one instant: Floppy has E01, and E03 at another time.
            remoteEpisode(10, 1, 1, episodeAt),
            remoteEpisode(10, 1, 3, Instant.parse("2026-10-02T21:31:00Z")),
        )

        val result = floppyMissing(expected, remote)

        assertEquals(listOf("MOVIE:2", "TV:11", "TV:10:1:2"), result.missing.map { it.key })
        assertEquals(listOf("MOVIE:1", "TV:10:1:3"), result.differing.map { it.key })
        assertEquals(listOf(2), result.snapshotFor(setOf("TV:10:1:2")).episodes.map { it.episode })
    }

    @Test
    fun `one matching play is enough for an episode watched more than once`() {
        val earlier = Instant.parse("2026-09-01T20:00:00Z")
        val expected = TrackingSnapshot(episodes = listOf(episode(10, 6, 20, earlier), episode(10, 6, 20)))

        val result = floppyMissing(expected, listOf(remoteEpisode(10, 6, 20, episodeAt)))

        assertTrue(result.isEmpty)
    }

    @Test
    fun `a play that lost its milliseconds through Simkl still matches`() {
        val onPhone = Instant.parse("2026-10-14T14:50:36Z")
        val expected = TrackingSnapshot(episodes = listOf(episode(10, 1, 6, onPhone)))

        val result = floppyMissing(expected, listOf(remoteEpisode(10, 1, 6, Instant.parse("2026-10-14T14:50:36.995Z"))))

        assertTrue(result.isEmpty)
    }

    @Test
    fun `a play Simkl rounded up to the next second still matches`() {
        val onPhone = Instant.parse("2026-10-14T18:21:20Z")
        val expected = TrackingSnapshot(episodes = listOf(episode(10, 4, 10, onPhone)))

        val result = floppyMissing(expected, listOf(remoteEpisode(10, 4, 10, Instant.parse("2026-10-14T18:21:19.640Z"))))

        assertTrue(result.isEmpty)
    }

    @Test
    fun `items outside the library or not watched are not expected`() {
        val expected = TrackingSnapshot(
            movies = listOf(TrackedMovieState(MediaIds(tmdb = 3), LibraryStatus.NONE, watched = false)),
            shows = listOf(show(12, LibraryStatus.NONE)),
            episodes = listOf(TrackedEpisodeState(MediaIds(tmdb = 12), 1, 1, watched = false)),
        )

        assertTrue(floppyMissing(expected, emptyList()).isEmpty)
    }
}
