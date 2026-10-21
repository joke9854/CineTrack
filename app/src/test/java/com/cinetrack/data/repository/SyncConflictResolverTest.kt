package com.cinetrack.data.repository

import androidx.room.Room
import androidx.work.testing.WorkManagerTestInitHelper
import com.cinetrack.data.local.AppDatabase
import com.cinetrack.data.local.MediaEntity
import com.cinetrack.data.local.PendingWriteEntity
import com.cinetrack.data.local.SyncOperationEntity
import com.cinetrack.data.sync.DurableSyncOperationWriter
import com.cinetrack.data.sync.DurableTrackingQueue
import com.cinetrack.data.sync.RoomSyncOperationRepository
import com.cinetrack.data.sync.SyncCoordinator
import com.cinetrack.data.sync.SyncOperation
import com.cinetrack.data.sync.SyncOperationType
import com.cinetrack.data.sync.TrackingConfiguration
import com.cinetrack.data.sync.TrackingProvider
import com.cinetrack.data.sync.TrackingProviderId
import com.cinetrack.data.sync.TrackingProviderRegistry
import com.cinetrack.data.sync.TrackingRoutingMutex
import com.cinetrack.domain.MediaType
import com.cinetrack.domain.SyncConflictChoice
import com.cinetrack.domain.SyncOperationStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.InputStream
import java.io.OutputStream
import java.security.Key
import java.security.KeyStoreSpi
import java.security.Provider
import java.security.Security
import java.security.cert.Certificate
import java.time.Instant
import java.util.Collections
import java.util.Date

/**
 * "Use remote" on an episode conflict applies the exact remote instant, removes only the
 * local intent the conflict was raised for, and refuses to act on a conflict that went stale.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class SyncConflictResolverTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val preferences by lazy { AppPreferences(context) }

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

    private class Harness(val database: AppDatabase, val resolver: SyncConflictResolver, val mirrors: List<SyncOperation>)

    private fun harness(): Harness {
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val mutex = TrackingRoutingMutex()
        val operations = RoomSyncOperationRepository(database, preferences)
        val mirrors = mutableListOf<SyncOperation>()
        val resolver = SyncConflictResolver(
            database = database,
            durableOperationWriter = DurableSyncOperationWriter(operations, DurableTrackingQueue(NoProviders, mutex), mutex),
            syncCoordinator = SyncCoordinator(NoProviders, operations, routingMutex = mutex),
            trackingRoutingMutex = mutex,
            stateOperationId = { type, id -> "state:$type:$id" },
            rebuildLibraryRailInTransaction = {},
            enqueueSecondaryMirror = { mirrors += it },
        )
        return Harness(database, resolver, mirrors)
    }

    private val tv = MediaType.TV.name
    private val showId = 10
    private val createdAt = 1_700_000_000_000L
    private val remoteInstantMillis = 1_750_000_123_456L

    private suspend fun seed(db: AppDatabase, withPendingWrite: Boolean) {
        db.mediaDao().upsertMedia(
            listOf(MediaEntity(tv, showId, "Show", "", null, null, "2020-01-01", null, null, "", "", null)),
        )
        db.syncDao().upsertOperation(
            SyncOperationEntity(
                operationId = "conflict:1",
                operation = "EPISODE_WATCHED_CONFLICT",
                mediaType = tv,
                mediaId = showId,
                title = "Show",
                status = SyncOperationStatus.CONFLICT.name,
                localValue = "true",
                remoteValue = "true",
                createdAt = createdAt,
                updatedAt = remoteInstantMillis,
                season = 1,
                episode = 2,
            ),
        )
        if (withPendingWrite) {
            db.syncDao().queue(
                PendingWriteEntity(
                    operation = SyncOperationType.EPISODE_WATCHED.name,
                    mediaType = tv,
                    mediaId = showId,
                    payload = "1:2:${Instant.ofEpochMilli(createdAt)}",
                    createdAt = createdAt,
                ),
            )
        }
    }

    @Test fun useRemoteAppliesTheExactRemoteInstantAndRemovesTheLocalIntent() = runBlocking {
        val h = harness()
        seed(h.database, withPendingWrite = true)
        val floorBefore = persistedGenerationFloor(h.database)

        val result = h.resolver.resolveSyncConflict("conflict:1", SyncConflictChoice.USE_REMOTE)

        assertTrue(result.exceptionOrNull()?.toString(), result.isSuccess)
        val history = h.database.timelineDao().episodeHistoryForShow(tv, showId)
            .filter { it.season == 1 && it.episodeNumber == 2 }
        assertEquals(1, history.size)
        assertEquals(Instant.ofEpochMilli(remoteInstantMillis).toString(), history.single().watchedAt)
        assertNull(h.database.syncDao().syncOperation("conflict:1"))
        assertTrue(h.database.syncDao().pendingWrites().isEmpty())
        val mirror = h.mirrors.single()
        assertEquals("mirror:episode-watched:$showId:1:2", mirror.id)
        assertEquals("1:2:${Instant.ofEpochMilli(remoteInstantMillis)}", mirror.payload)
        assertTrue(mirror.sourceVersion > floorBefore!!)
        assertTrue(mirror.sourceVersion > createdAt)
        assertTrue(mirror.sourceVersion > (persistedGenerationFloor(h.database) ?: 0L))
    }

    @Test fun useRemoteIsRejectedWhenTheConflictWentStale() = runBlocking {
        val h = harness()
        seed(h.database, withPendingWrite = false)

        val result = h.resolver.resolveSyncConflict("conflict:1", SyncConflictChoice.USE_REMOTE)

        assertTrue(result.isFailure)
        assertEquals("Conflict changed while resolving", result.exceptionOrNull()?.message)
        assertNotNull(h.database.syncDao().syncOperation("conflict:1"))
        assertTrue(h.database.timelineDao().episodeHistoryForShow(tv, showId).isEmpty())
        assertTrue(h.mirrors.isEmpty())
    }
}
