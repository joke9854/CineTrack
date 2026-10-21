package com.cinetrack.data.repository

import androidx.room.Room
import androidx.work.testing.WorkManagerTestInitHelper
import com.cinetrack.data.library.RoomLibraryRepository
import com.cinetrack.data.local.AppDatabase
import com.cinetrack.data.local.MediaEntity
import com.cinetrack.data.local.PlaybackEntity
import com.cinetrack.data.local.UserMediaStateEntity
import com.cinetrack.data.local.WatchHistoryEntity
import com.cinetrack.data.remote.ApiServices
import com.cinetrack.data.schedule.DefaultReleaseScheduleRepository
import com.cinetrack.data.sync.DurableSyncOperationWriter
import com.cinetrack.data.sync.DurableTrackingQueue
import com.cinetrack.data.sync.RoomSyncOperationRepository
import com.cinetrack.data.sync.SyncCoordinator
import com.cinetrack.data.sync.TrackingConfiguration
import com.cinetrack.data.sync.TrackingProvider
import com.cinetrack.data.sync.TrackingProviderId
import com.cinetrack.data.sync.TrackingProviderRegistry
import com.cinetrack.data.sync.TrackingRoutingMutex
import com.cinetrack.domain.LibraryStatus
import com.cinetrack.domain.MediaType
import com.cinetrack.ui.settings.NotificationSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.InputStream
import java.io.OutputStream
import java.lang.reflect.Proxy
import java.security.Key
import java.security.KeyStoreSpi
import java.security.Provider
import java.security.Security
import java.security.cert.Certificate
import java.util.Collections
import java.util.Date

/**
 * Export from one database, restore into a fresh one: what a user gets after
 * reinstalling. Watch history must come back event by event with the exact
 * stored timestamps, and restoring never queues anything for a tracker.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class BackupRoundTripTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val preferences by lazy { AppPreferences(context) }
    // In-memory databases stay open: the repository's delayed automatic backup may still read them.

    /** Robolectric has no Android Keystore; no credential is stored here, so an empty one is enough. */
    class EmptyKeyStore : KeyStoreSpi() {
        override fun engineGetKey(alias: String?, password: CharArray?): Key? = null
        override fun engineGetCertificateChain(alias: String?): Array<Certificate>? = null
        override fun engineGetCertificate(alias: String?): Certificate? = null
        override fun engineGetCreationDate(alias: String?): Date? = null
        override fun engineSetKeyEntry(alias: String?, key: Key?, password: CharArray?, chain: Array<out Certificate>?) = Unit
        override fun engineSetKeyEntry(alias: String?, key: ByteArray?, chain: Array<out Certificate>?) = Unit
        override fun engineSetCertificateEntry(alias: String?, cert: Certificate?) = Unit
        override fun engineDeleteEntry(alias: String?) = Unit
        override fun engineAliases(): java.util.Enumeration<String> = Collections.emptyEnumeration()
        override fun engineContainsAlias(alias: String?) = false
        override fun engineSize() = 0
        override fun engineIsKeyEntry(alias: String?) = false
        override fun engineIsCertificateEntry(alias: String?) = false
        override fun engineGetCertificateAlias(cert: Certificate?): String? = null
        override fun engineStore(stream: OutputStream?, password: CharArray?) = Unit
        override fun engineLoad(stream: InputStream?, password: CharArray?) = Unit
    }

    @Before fun installKeyStore() {
        // Restoring settings reschedules background sync.
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        if (Security.getProvider("AndroidKeyStore") == null) {
            Security.addProvider(object : Provider("AndroidKeyStore", 1.0, "test") {
                init { put("KeyStore.AndroidKeyStore", EmptyKeyStore::class.java.name) }
            })
        }
    }

    private object NoProviders : TrackingProviderRegistry {
        override fun getProvider(id: TrackingProviderId): TrackingProvider? = null
        override suspend fun configuration() = TrackingConfiguration(mainProvider = null)
    }

    private inline fun <reified T> offline(): T = Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
        when (method.name) {
            "toString" -> "offline ${T::class.java.simpleName}"
            "hashCode" -> 0
            "equals" -> false
            else -> throw UnsupportedOperationException("No network in this test: ${method.name}")
        }
    } as T

    private class Store(val database: AppDatabase, val operations: RoomSyncOperationRepository, val repository: CineTrackRepository, val backup: BackupRepository)

    private fun store(): Store {
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val services = ApiServices(offline(), offline(), offline(), offline(), offline())
        val mutex = TrackingRoutingMutex()
        val operations = RoomSyncOperationRepository(database, preferences)
        val coordinator = SyncCoordinator(NoProviders, operations, routingMutex = mutex)
        val repository = CineTrackRepository(
            database = database,
            services = services,
            preferences = preferences,
            tmdbApiKey = { "" },
            mdbListApiKey = { "" },
            syncOperationRepository = operations,
            durableOperationWriter = DurableSyncOperationWriter(operations, DurableTrackingQueue(NoProviders, mutex), mutex),
            syncCoordinator = coordinator,
            releaseScheduleRepository = DefaultReleaseScheduleRepository(database, services),
            libraryRepository = RoomLibraryRepository(database, preferences, coordinator, {}, NoProviders, mutex),
            trackingProviderRegistry = NoProviders,
            trackingRoutingMutex = mutex,
        )
        val backup = BackupRepository(
            database = database,
            preferences = preferences,
            metadataSettings = { MetadataSettingsRepository(database, services, preferences, clearLanguageCaches = repository::clearLanguageCaches) },
            loadState = repository::loadCachedState,
            rebuildLibraryRail = repository::rebuildLibraryRailInTransaction,
        )
        return Store(database, operations, repository, backup)
    }

    private fun media(type: MediaType, id: Int, title: String) =
        MediaEntity(type.name, id, title, "", null, null, "2020-01-01", null, null, "", "", null)

    private val tv = MediaType.TV.name
    private val movie = MediaType.MOVIE.name

    /** What a history row is, apart from its Room id. */
    private fun WatchHistoryEntity.event() = listOf(mediaType, mediaId, season, episodeNumber, watchedAt)

    private val seededHistory = listOf(
        // A rewatch: same episode, two distinct events.
        WatchHistoryEntity(mediaType = tv, mediaId = 10, season = 1, episodeNumber = 1, watchedAt = "2026-03-01T20:15:30.123Z"),
        WatchHistoryEntity(mediaType = tv, mediaId = 10, season = 1, episodeNumber = 1, watchedAt = "2026-05-02T21:00:00Z"),
        // A bulk import gives every episode the same instant; both must survive.
        WatchHistoryEntity(mediaType = tv, mediaId = 10, season = 1, episodeNumber = 2, watchedAt = "2025-12-31T23:59:59.999Z"),
        WatchHistoryEntity(mediaType = tv, mediaId = 10, season = 1, episodeNumber = 3, watchedAt = "2025-12-31T23:59:59.999Z"),
        // A movie watched twice.
        WatchHistoryEntity(mediaType = movie, mediaId = 20, watchedAt = "2024-07-14T18:30:00Z"),
        WatchHistoryEntity(mediaType = movie, mediaId = 20, watchedAt = "2026-01-06T09:05:07Z"),
    )

    private suspend fun seed(source: Store) {
        val database = source.database
        database.mediaDao().upsertMedia(
            listOf(
                media(MediaType.TV, 10, "Dark"),
                media(MediaType.MOVIE, 20, "Dune"),
                media(MediaType.TV, 30, "Finished Show"),
                media(MediaType.MOVIE, 40, "Paused Movie"),
            ),
        )
        database.stateDao().upsertAll(
            listOf(
                UserMediaStateEntity(tv, 10, LibraryStatus.WATCHING.name, watched = false),
                UserMediaStateEntity(movie, 20, LibraryStatus.COMPLETED.name, watched = true),
                // Completed with no episode history: restore must not invent any.
                UserMediaStateEntity(tv, 30, LibraryStatus.COMPLETED.name, watched = true),
                UserMediaStateEntity(movie, 40, LibraryStatus.WATCHING.name, watched = false),
            ),
        )
        database.timelineDao().insertHistoryItems(seededHistory)
        database.timelineDao().upsertPlayback(
            listOf(PlaybackEntity(movie, 40, progress = .25f, positionSeconds = 900, durationSeconds = 3600, updatedAt = "2026-10-01T10:00:00Z")),
        )
        source.repository.rebuildLibraryRailInTransaction()
        preferences.setNotification("movies", false)
        preferences.setQuietHours(true, 22, 7)
        preferences.setContentRegions(setOf("IT", "US"))
        preferences.setExcludeSpecials(true)
    }

    @Test fun reinstallRestoresEveryEventWithItsExactTimestamp() = runBlocking {
        val source = store()
        seed(source)
        val backup = source.backup.exportBackupFiles(emptySet())

        // Settings changed after the backup, then the app is reinstalled.
        preferences.setNotification("movies", true)
        preferences.setQuietHours(false, 0, 8)
        preferences.setContentRegions(emptySet())
        preferences.setExcludeSpecials(false)
        val restored = store()
        val count = restored.backup.restoreBackupFiles(backup)

        assertEquals(
            seededHistory.map { it.event() }.sortedBy(Any::toString),
            restored.database.timelineDao().historySnapshot().map { it.event() }.sortedBy(Any::toString),
        )
        assertEquals(
            mapOf("$tv:10" to LibraryStatus.WATCHING.name, "$movie:20" to LibraryStatus.COMPLETED.name, "$tv:30" to LibraryStatus.COMPLETED.name, "$movie:40" to LibraryStatus.WATCHING.name),
            restored.database.stateDao().stateSnapshot().associate { "${it.mediaType}:${it.mediaId}" to it.status },
        )
        assertTrue(restored.database.timelineDao().historySnapshot().none { it.mediaType == tv && it.mediaId == 30 })
        assertEquals(
            listOf(listOf<Any?>(movie, 40, .25f)),
            restored.database.timelineDao().playbackSnapshot().map { listOf(it.mediaType, it.mediaId, it.progress) },
        )
        assertEquals(4 + seededHistory.size, count)
        assertEquals(NotificationSettings(movies = false, quietHoursStart = 22, quietHoursEnd = 7), preferences.notificationSettings.first())
        assertEquals(setOf("IT", "US"), preferences.contentRegions.first())
        assertTrue(preferences.excludeSpecials.first())
        // Restoring is local only: nothing is queued for Simkl or Floppy.
        assertTrue(restored.operations.pending().isEmpty())

        // The restored install exports the same history, so a second round trip loses nothing.
        val again = restored.backup.exportBackupFiles(setOf("history"))
        assertEquals(backup["data/history.jsonl"]!!.lines().toSet(), again["data/history.jsonl"]!!.lines().toSet())
    }

    @Test fun historyOfATitleRemovedFromTheLibrarySurvivesARestore() = runBlocking {
        val source = store()
        source.database.mediaDao().upsertMedia(listOf(media(MediaType.MOVIE, 50, "Removed Movie")))
        source.database.stateDao().upsertAll(listOf(UserMediaStateEntity(movie, 50, LibraryStatus.NONE.name, watched = true)))
        source.database.timelineDao().insertHistoryItems(listOf(WatchHistoryEntity(mediaType = movie, mediaId = 50, watchedAt = "2026-02-02T02:02:02Z")))
        val backup = source.backup.exportBackupFiles(setOf("library", "history"))
        val restored = store()
        restored.backup.restoreBackupFiles(backup)
        // The row is back, and the restored install shows and exports it again.
        assertEquals(1, restored.database.timelineDao().historySnapshot().size)
        assertEquals(1, restored.repository.loadCachedState().history.size)
    }

    @Test fun restoringTheSameBackupTwiceAddsNothing() = runBlocking {
        val source = store()
        seed(source)
        val backup = source.backup.exportBackupFiles(setOf("library", "history"))
        val restored = store()
        restored.backup.restoreBackupFiles(backup)
        val second = restored.backup.restoreBackupFiles(backup)
        assertEquals(4, second) // the library states are upserted again; no history is added
        assertEquals(seededHistory.size, restored.database.timelineDao().historySnapshot().size)
    }
}
