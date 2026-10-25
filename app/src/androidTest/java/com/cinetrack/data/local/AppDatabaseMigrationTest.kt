package com.cinetrack.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the real Room migration chain, including generation-aware rows. */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test fun migration5To10PreservesLegacyData() = migrateAndValidate(5)
    @Test fun migration6To10PreservesSyncOperations() = migrateAndValidate(6)
    @Test fun migration7To10PreservesProviderId() = migrateAndValidate(7)
    @Test fun migration8To10PreservesEpisodeCoordinates() = migrateAndValidate(8)
    @Test fun migration9To10PreservesDeliveryGeneration() = migrateAndValidate(9)
    @Test fun migration10To12AddsOperationPayloadAndInstanceBinding() = migrateAndValidate(10)

    @Test
    fun migration5To6IsDeclaredForLegacyInstallations() {
        assertNotNull(AppDatabase.migration5To6)
    }

    private fun migrateAndValidate(version: Int) {
        helper.createDatabase("migration-$version", version).use { db ->
            seedCanonicalRows(db, version)
        }
        val migrations = when (version) {
            5 -> arrayOf(AppDatabase.migration5To6, AppDatabase.migration6To7, AppDatabase.migration7To8, AppDatabase.migration8To9, AppDatabase.migration9To10, AppDatabase.migration10To11, AppDatabase.migration11To12, AppDatabase.migration12To13)
            6 -> arrayOf(AppDatabase.migration6To7, AppDatabase.migration7To8, AppDatabase.migration8To9, AppDatabase.migration9To10, AppDatabase.migration10To11, AppDatabase.migration11To12, AppDatabase.migration12To13)
            7 -> arrayOf(AppDatabase.migration7To8, AppDatabase.migration8To9, AppDatabase.migration9To10, AppDatabase.migration10To11, AppDatabase.migration11To12, AppDatabase.migration12To13)
            8 -> arrayOf(AppDatabase.migration8To9, AppDatabase.migration9To10, AppDatabase.migration10To11, AppDatabase.migration11To12, AppDatabase.migration12To13)
            9 -> arrayOf(AppDatabase.migration9To10, AppDatabase.migration10To11, AppDatabase.migration11To12, AppDatabase.migration12To13)
            else -> arrayOf(AppDatabase.migration10To11, AppDatabase.migration11To12, AppDatabase.migration12To13)
        }
        helper.runMigrationsAndValidate("migration-$version", 13, true, *migrations).use { migrated ->
            assertEquals(1, count(migrated, "media"))
            assertEquals(1, count(migrated, "user_media_state"))
            assertEquals(1, count(migrated, "playback"))
            assertEquals(1, count(migrated, "watch_history"))
            assertEquals(1, count(migrated, "pending_writes"))
            if (version >= 6) {
                assertEquals(2, count(migrated, "sync_operations"))
                assertEquals(2, countWhere(migrated, "sync_operations", "status = 'FAILED' OR status = 'CONFLICT'"))
            }
            if (version >= 9) {
                migrated.query("SELECT operationVersion, providerId, status FROM sync_operation_deliveries WHERE operationId='failed-op'").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(100L, cursor.getLong(0))
                    assertEquals("SIMKL", cursor.getString(1))
                    assertEquals("FAILED", cursor.getString(2))
                }
            }
            if (version >= 10) {
                migrated.query("PRAGMA table_info(sync_operations)").use { cursor ->
                    var foundPayload = false
                    while (cursor.moveToNext()) if (cursor.getString(1) == "payload") foundPayload = true
                    assertTrue(foundPayload)
                }
            }
        }
    }

    @Test
    fun migration12To13AddsAirDatesAndKeepsEpisodeDates() {
        helper.createDatabase("migration-12-airdates", 12).use { db ->
            db.execSQL("INSERT INTO episodes(showId,season,number,tmdbId,title,overview,airDate,stillPath,runtimeMinutes) VALUES (1,1,1,NULL,'Pilot','','2026-10-15T01:00:00Z',NULL,NULL)")
        }
        helper.runMigrationsAndValidate("migration-12-airdates", 13, true, AppDatabase.migration12To13).use { migrated ->
            assertEquals(0, count(migrated, "episode_air_dates"))
            migrated.query("SELECT airDate FROM episodes WHERE showId=1").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("2026-10-15T01:00:00Z", cursor.getString(0))
            }
        }
    }

    @Test
    fun migration13To14AddsEpisodeNumberingAndKeepsHistory() {
        helper.createDatabase("migration-13-numbering", 13).use { db ->
            db.execSQL("INSERT INTO watch_history(mediaType,mediaId,episodeId,season,episodeNumber,episodeTitle,watchedAt) VALUES ('TV',46260,NULL,1,183,NULL,'2020-01-01T00:00:00Z')")
        }
        helper.runMigrationsAndValidate("migration-13-numbering", 14, true, AppDatabase.migration13To14).use { migrated ->
            assertEquals(0, count(migrated, "episode_numbering"))
            assertEquals(1, count(migrated, "watch_history"))
            migrated.execSQL("INSERT INTO episode_numbering(showId,scheme,schemeSeason,schemeEpisode,status,tmdbSeason,tmdbEpisode,absolute,tvdbSeason,tvdbEpisode,updatedAt) VALUES (46260,'simkl',1,183,'translated',4,183,183,NULL,NULL,1)")
            assertEquals(1, count(migrated, "episode_numbering"))
        }
    }

    @Test
    fun migration14To15AddsPersonalTablesAndKeepsHistory() {
        helper.createDatabase("migration-14-personal", 14).use { db ->
            db.execSQL("INSERT INTO watch_history(mediaType,mediaId,episodeId,season,episodeNumber,episodeTitle,watchedAt) VALUES ('MOVIE',603,NULL,NULL,NULL,NULL,'2020-01-01T20:15:30.123Z')")
        }
        helper.runMigrationsAndValidate("migration-14-personal", 15, true, AppDatabase.migration14To15).use { migrated ->
            assertEquals(1, count(migrated, "watch_history"))
            assertEquals(0, count(migrated, "user_ratings"))
            migrated.execSQL("INSERT INTO custom_lists(name,createdAt) VALUES ('Halloween',1)")
            migrated.execSQL("INSERT INTO custom_list_items(listId,mediaType,mediaId,addedAt) VALUES (1,'MOVIE',603,1)")
            migrated.execSQL("INSERT INTO user_ratings(mediaType,mediaId,score,note,updatedAt) VALUES ('MOVIE',603,9,'',1)")
            assertEquals(1, count(migrated, "custom_list_items"))
            assertEquals(1, count(migrated, "user_ratings"))
        }
    }

    @Test
    fun migration11To12CancelsUnknownLegacyFloppyDelivery() {
        helper.createDatabase("migration-11-floppy", 11).use { db ->
            db.execSQL("INSERT INTO sync_operation_deliveries(operationId,operationVersion,providerId,status,required,roleAtEnqueue,attemptCount,lastError,createdAt,updatedAt) VALUES ('legacy-floppy',1,'FLOPPY','PENDING',1,'SECONDARY',0,NULL,1,1)")
        }
        helper.runMigrationsAndValidate("migration-11-floppy", 13, true, AppDatabase.migration11To12, AppDatabase.migration12To13).use { migrated ->
            migrated.query("SELECT providerInstanceId,status FROM sync_operation_deliveries WHERE operationId='legacy-floppy'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertTrue(cursor.isNull(0))
                assertEquals("CANCELLED_PROVIDER_INSTANCE_CHANGED", cursor.getString(1))
            }
        }
    }

    private fun seedCanonicalRows(db: SupportSQLiteDatabase, version: Int) {
        db.execSQL("INSERT INTO media(mediaType,tmdbId,title,overview,posterPath,backdropPath,releaseDate,score,runtimeMinutes,genres,providers,collectionId,updatedAt) VALUES ('TV',7,'Show','overview',NULL,NULL,'2020-01-01',8.5,42,'Drama','',NULL,1)")
        db.execSQL("INSERT INTO user_media_state(mediaType,mediaId,status,watched,simklId,updatedAt,dirty) VALUES ('TV',7,'WATCHING',1,77,2,0)")
        db.execSQL("INSERT INTO playback(mediaType,mediaId,episodeId,progress,positionSeconds,durationSeconds,updatedAt,season,episodeNumber,episodeTitle) VALUES ('TV',7,70,0.5,30,60,'2020-01-01T00:00:00Z',2,3,'Episode')")
        db.execSQL("INSERT INTO watch_history(mediaType,mediaId,episodeId,season,episodeNumber,episodeTitle,watchedAt) VALUES ('TV',7,70,2,3,'Episode','2020-01-01T00:00:00Z')")
        db.execSQL("INSERT INTO pending_writes(operation,mediaType,mediaId,payload,createdAt,attemptCount) VALUES ('EPISODE_WATCHED','TV',7,'2:3:2020-01-01T00:00:00Z',100,2)")
        if (version >= 6) {
            if (version == 6) {
                db.execSQL("INSERT INTO sync_operations(operationId,operation,mediaType,mediaId,title,status,message,localValue,remoteValue,createdAt,updatedAt,attemptCount) VALUES ('failed-op','EPISODE_WATCHED','TV',7,'Show','FAILED','offline','WATCHING',NULL,100,101,2)")
                db.execSQL("INSERT INTO sync_operations(operationId,operation,mediaType,mediaId,title,status,message,localValue,remoteValue,createdAt,updatedAt,attemptCount) VALUES ('conflict-op','LIBRARY_STATUS','TV',7,'Show','CONFLICT',NULL,'WATCHING','COMPLETED',102,103,1)")
            } else if (version == 7) {
                db.execSQL("INSERT INTO sync_operations(operationId,operation,mediaType,mediaId,title,status,message,localValue,remoteValue,createdAt,updatedAt,attemptCount,providerId) VALUES ('failed-op','EPISODE_WATCHED','TV',7,'Show','FAILED','offline','WATCHING',NULL,100,101,2,'SIMKL')")
                db.execSQL("INSERT INTO sync_operations(operationId,operation,mediaType,mediaId,title,status,message,localValue,remoteValue,createdAt,updatedAt,attemptCount,providerId) VALUES ('conflict-op','LIBRARY_STATUS','TV',7,'Show','CONFLICT',NULL,'WATCHING','COMPLETED',102,103,1,'FLOPPY')")
            } else {
                db.execSQL("INSERT INTO sync_operations(operationId,operation,mediaType,mediaId,title,status,message,localValue,remoteValue,createdAt,updatedAt,attemptCount,providerId,season,episode) VALUES ('failed-op','EPISODE_WATCHED','TV',7,'Show','FAILED','offline','WATCHING',NULL,100,101,2,'SIMKL',2,3)")
                db.execSQL("INSERT INTO sync_operations(operationId,operation,mediaType,mediaId,title,status,message,localValue,remoteValue,createdAt,updatedAt,attemptCount,providerId,season,episode) VALUES ('conflict-op','LIBRARY_STATUS','TV',7,'Show','CONFLICT',NULL,'WATCHING','COMPLETED',102,103,1,'FLOPPY',NULL,NULL)")
            }
        }
        if (version >= 9) {
            if (version >= 10) {
                db.execSQL("INSERT INTO sync_operation_deliveries(operationId,operationVersion,providerId,status,required,roleAtEnqueue,attemptCount,lastError,createdAt,updatedAt) VALUES ('failed-op',100,'SIMKL','FAILED',1,'MAIN',2,'offline',100,101)")
            } else {
                db.execSQL("INSERT INTO sync_operation_deliveries(operationId,providerId,status,required,roleAtEnqueue,attemptCount,lastError,createdAt,updatedAt) VALUES ('failed-op','SIMKL','FAILED',1,'MAIN',2,'offline',100,101)")
            }
        }
    }

    private fun count(db: SupportSQLiteDatabase, table: String): Int =
        db.query("SELECT COUNT(*) FROM $table").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    private fun countWhere(db: SupportSQLiteDatabase, table: String, where: String): Int =
        db.query("SELECT COUNT(*) FROM $table WHERE $where").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }
}

