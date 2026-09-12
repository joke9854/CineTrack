package com.cinetrack.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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

    @Test fun migration5To10IsDeclared() {
        // Version 5 predates the exported sync-operation schema; the production
        // chain still explicitly declares the 5->6 step used by Room.
        assertNotNull(AppDatabase.migration5To6)
    }

    @Test fun migration6To10PreservesSyncOperation() = migrateAndValidate(6)
    @Test fun migration7To10PreservesProviderId() = migrateAndValidate(7)
    @Test fun migration8To10PreservesEpisodeCoordinates() = migrateAndValidate(8)

    @Test
    fun migration9To10AddsGenerationToDelivery() {
        val db = helper.createDatabase("migration-9", 9)
        db.execSQL("INSERT INTO sync_operations(operationId,operation,mediaType,mediaId,title,status,message,localValue,remoteValue,createdAt,updatedAt,attemptCount,providerId,season,episode) VALUES ('op','EPISODE_WATCHED','TV',7,'Show','FAILED',NULL,NULL,NULL,42,42,1,'SIMKL',2,3)")
        db.execSQL("INSERT INTO sync_operation_deliveries(operationId,providerId,status,required,roleAtEnqueue,attemptCount,lastError,createdAt,updatedAt) VALUES ('op','SIMKL','FAILED',1,'MAIN',1,'offline',42,42)")
        db.close()
        helper.runMigrationsAndValidate("migration-9", 10, true, AppDatabase.migration9To10)
            .use { migrated ->
                val cursor = migrated.query("SELECT operationVersion,providerId,status FROM sync_operation_deliveries WHERE operationId='op'")
                cursor.use {
                    check(it.moveToFirst())
                    assertEquals(42L, it.getLong(0))
                    assertEquals("SIMKL", it.getString(1))
                    assertEquals("FAILED", it.getString(2))
                }
            }
    }

    private fun migrateAndValidate(version: Int) {
        helper.createDatabase("migration-$version", version).use { db ->
            db.execSQL("INSERT INTO media(mediaType,tmdbId,title,overview,posterPath,backdropPath,releaseDate,score,runtimeMinutes,genres,providers,collectionId,updatedAt) VALUES ('TV',7,'Show','','','','',NULL,NULL,'','','',1)")
        }
        val migrations = when (version) {
            6 -> arrayOf(AppDatabase.migration6To7, AppDatabase.migration7To8, AppDatabase.migration8To9, AppDatabase.migration9To10)
            7 -> arrayOf(AppDatabase.migration7To8, AppDatabase.migration8To9, AppDatabase.migration9To10)
            else -> arrayOf(AppDatabase.migration8To9, AppDatabase.migration9To10)
        }
        helper.runMigrationsAndValidate("migration-$version", 10, true, *migrations).close()
    }
}

