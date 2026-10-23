package com.cinetrack.data.local

import androidx.room.Room
import com.cinetrack.domain.RailIds
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The AppUiState input reads only the media rows something points at, and a reload after
 * an invalidation re-reads only the tables Room named.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class AppStateSnapshotTest {
    private val database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    @After
    fun close() = database.close()

    private fun media(id: Int, title: String = "Title $id") = MediaEntity(
        mediaType = "TV", tmdbId = id, title = title, overview = "", posterPath = null, backdropPath = null,
        releaseDate = null, score = null, runtimeMinutes = null, genres = "", providers = "", collectionId = null,
    )

    private fun seed() = runBlocking {
        // 4 is a search/Discover cache row nothing points at.
        database.mediaDao().upsertMedia(listOf(media(1), media(2), media(3), media(4)))
        database.mediaDao().upsertRails(listOf(MediaRailEntity(RailIds.LIBRARY, "TV", 1, 0)))
        database.timelineDao().upsertPlayback(
            listOf(PlaybackEntity("TV", 2, progress = 0.5f, positionSeconds = 60, durationSeconds = 120, updatedAt = "2026-10-20T10:00:00Z")),
        )
        database.timelineDao().insertHistory(WatchHistoryEntity(mediaType = "TV", mediaId = 3, season = 1, episodeNumber = 1, watchedAt = "2026-10-20T10:00:00.123Z"))
        database.timelineDao().insertHistory(WatchHistoryEntity(mediaType = "TV", mediaId = 1, season = 1, episodeNumber = 1, watchedAt = "2026-10-20T10:00:00.123Z"))
        database.syncDao().upsert(SyncStateEntity("all", null, 1L))
    }

    @Test
    fun fullReadKeepsOnlyReferencedMediaOnce() = runBlocking {
        seed()
        val snapshot = database.snapshotDao().appStateSnapshot(previous = null, changedTables = null)
        assertEquals(listOf(1, 2, 3), snapshot.media.map { it.tmdbId }.sorted())
        val full = database.snapshotDao().snapshot()
        assertEquals(full.rails, snapshot.rails)
        assertEquals(full.playback, snapshot.playback)
        assertEquals(full.history, snapshot.history)
        assertEquals(full.syncStates, snapshot.syncStates)
    }

    @Test
    fun partialReadReloadsOnlyNamedTables() = runBlocking {
        seed()
        val first = database.snapshotDao().appStateSnapshot(previous = null, changedTables = null)
        database.mediaDao().upsertMedia(listOf(media(1, "Renamed")))
        database.syncDao().upsert(SyncStateEntity("all", null, 2L))

        val syncOnly = database.snapshotDao().appStateSnapshot(first, setOf("sync_state"))
        assertEquals(2L, syncOnly.syncStates.single().lastSuccessfulSync)
        assertEquals("Title 1", syncOnly.media.first { it.tmdbId == 1 }.title)

        val withMedia = database.snapshotDao().appStateSnapshot(syncOnly, setOf("media"))
        assertEquals("Renamed", withMedia.media.first { it.tmdbId == 1 }.title)

        // A new history row brings its media into the referenced set.
        database.timelineDao().insertHistory(WatchHistoryEntity(mediaType = "TV", mediaId = 4, watchedAt = "2026-10-21T08:00:00Z"))
        val withHistory = database.snapshotDao().appStateSnapshot(withMedia, setOf("watch_history"))
        assertEquals(listOf(1, 2, 3, 4), withHistory.media.map { it.tmdbId }.sorted())
        assertEquals(database.snapshotDao().snapshot().history, withHistory.history)
    }
}
