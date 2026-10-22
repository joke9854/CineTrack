package com.cinetrack.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.cinetrack.domain.LibraryStatus
import com.cinetrack.domain.MediaCard
import com.cinetrack.domain.MediaType
import com.cinetrack.data.remote.tmdbImageUrl

@Entity(tableName = "media", primaryKeys = ["mediaType", "tmdbId"])
data class MediaEntity(
    val mediaType: String,
    val tmdbId: Int,
    val title: String,
    val overview: String,
    val posterPath: String?,
    val backdropPath: String?,
    val releaseDate: String?,
    val score: Double?,
    val runtimeMinutes: Int?,
    val genres: String,
    val providers: String,
    val collectionId: Int?,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "media_rails", primaryKeys = ["railId", "mediaType", "mediaId"])
data class MediaRailEntity(
    val railId: String,
    val mediaType: String,
    val mediaId: Int,
    val position: Int,
)

@Entity(tableName = "seasons", primaryKeys = ["showId", "number"])
data class SeasonEntity(val showId: Int, val number: Int, val title: String, val episodeCount: Int)

@Entity(tableName = "episodes", primaryKeys = ["showId", "season", "number"])
data class EpisodeEntity(
    val showId: Int,
    val season: Int,
    val number: Int,
    val tmdbId: Int?,
    val title: String,
    val overview: String,
    val airDate: String?,
    val stillPath: String?,
    val runtimeMinutes: Int?,
)

/** One provider's air date for an episode; the date shown comes from the provider chosen in Settings. */
@Entity(tableName = "episode_air_dates", primaryKeys = ["showId", "season", "number", "source"])
data class EpisodeAirDateEntity(
    val showId: Int,
    val season: Int,
    val number: Int,
    val source: String,
    val airDate: String,
    val updatedAt: Long,
)

/**
 * Per-episode numbering translation between a tracker's own scheme (Simkl) and
 * TMDB numbering. Filled from Floppy's translate endpoint; TMDB numbers are what
 * the app stores, the tracker numbers exist only at the tracker boundary.
 */
@Entity(
    tableName = "episode_numbering",
    primaryKeys = ["showId", "scheme", "schemeSeason", "schemeEpisode"],
    indices = [Index(value = ["showId", "tmdbSeason", "tmdbEpisode"])],
)
data class EpisodeNumberingEntity(
    val showId: Int,
    val scheme: String,
    val schemeSeason: Int,
    val schemeEpisode: Int,
    val status: String,
    val tmdbSeason: Int? = null,
    val tmdbEpisode: Int? = null,
    val absolute: Int? = null,
    val tvdbSeason: Int? = null,
    val tvdbEpisode: Int? = null,
    val updatedAt: Long,
)

@Entity(tableName = "people")
data class PersonEntity(
    @PrimaryKey val tmdbId: Int,
    val name: String,
    val biography: String,
    val birthday: String?,
    val placeOfBirth: String?,
    val profilePath: String?,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "person_movie_credits", primaryKeys = ["personId", "movieId"])
data class PersonMovieCreditEntity(
    val personId: Int,
    val movieId: Int,
    val title: String,
    val character: String?,
    val releaseDate: String?,
    val posterPath: String?,
)

@Entity(tableName = "user_media_state", primaryKeys = ["mediaType", "mediaId"], indices = [Index(value = ["mediaType", "status"])])
data class UserMediaStateEntity(
    val mediaType: String,
    val mediaId: Int,
    val status: String,
    val watched: Boolean,
    val simklId: Long? = null,
    val updatedAt: Long = System.currentTimeMillis(),
    val dirty: Boolean = false,
)

@Entity(tableName = "playback", primaryKeys = ["mediaType", "mediaId", "episodeId"], indices = [Index("updatedAt")])
data class PlaybackEntity(
    val mediaType: String,
    val mediaId: Int,
    val episodeId: Int = 0,
    val progress: Float,
    val positionSeconds: Long,
    val durationSeconds: Long,
    val updatedAt: String,
    val season: Int? = null,
    val episodeNumber: Int? = null,
    val episodeTitle: String? = null,
)

@Entity(
    tableName = "watch_history",
    indices = [Index(value = ["mediaType", "mediaId", "season", "episodeNumber"]), Index("watchedAt")],
)
data class WatchHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaType: String,
    val mediaId: Int,
    val episodeId: Int? = null,
    val season: Int? = null,
    val episodeNumber: Int? = null,
    val episodeTitle: String? = null,
    val watchedAt: String,
)

/**
 * Durable, derived next-episode selection. Keeping this separate from playback
 * avoids rescanning the complete episode/history tables every time AppUiState is
 * rebuilt after a synchronization or process recreation.
 */
@Entity(tableName = "up_next")
data class UpNextEntity(
    @PrimaryKey val showId: Int,
    val episodeId: Int?,
    val season: Int,
    val episodeNumber: Int,
    val episodeTitle: String,
    val episodeAirDate: String?,
    val durationMinutes: Int?,
    val refreshedAt: Long,
)

@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val area: String,
    val remoteTimestamp: String?,
    val lastSuccessfulSync: Long,
)

@Entity(tableName = "pending_writes")
data class PendingWriteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val operation: String,
    val mediaType: String,
    val mediaId: Int,
    val payload: String,
    val createdAt: Long = System.currentTimeMillis(),
    val attemptCount: Int = 0,
)

/** User-visible state for durable synchronization work and conflicts. */
@Entity(
    tableName = "sync_operations",
    indices = [Index("status"), Index(value = ["mediaType", "mediaId"])],
)
data class SyncOperationEntity(
    @PrimaryKey val operationId: String,
    val operation: String,
    val mediaType: String,
    val mediaId: Int,
    val title: String,
    val status: String,
    val message: String? = null,
    val localValue: String? = null,
    val remoteValue: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val attemptCount: Int = 0,
    val providerId: String = "SIMKL",
    val season: Int? = null,
    val episode: Int? = null,
    val payload: String? = null,
)

@Entity(
    tableName = "sync_operation_deliveries",
    primaryKeys = ["operationId", "operationVersion", "providerId"],
    indices = [Index("providerId", "status"), Index("operationId"), Index("status")],
)
data class SyncOperationDeliveryEntity(
    val operationId: String,
    val operationVersion: Long,
    val providerId: String,
    val status: String,
    val required: Boolean,
    val roleAtEnqueue: String,
    val providerInstanceId: String? = null,
    val attemptCount: Int = 0,
    val lastError: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Database(
    entities = [
        MediaEntity::class,
        MediaRailEntity::class,
        SeasonEntity::class,
        EpisodeEntity::class,
        PersonEntity::class,
        PersonMovieCreditEntity::class,
        UserMediaStateEntity::class,
        PlaybackEntity::class,
        WatchHistoryEntity::class,
        UpNextEntity::class,
        SyncStateEntity::class,
        PendingWriteEntity::class,
        SyncOperationEntity::class,
        SyncOperationDeliveryEntity::class,
        EpisodeAirDateEntity::class,
        EpisodeNumberingEntity::class,
    ],
    version = 14,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun snapshotDao(): AppSnapshotDao
    abstract fun progressSnapshotDao(): ProgressSnapshotDao
    abstract fun mediaDao(): MediaDao
    abstract fun stateDao(): StateDao
    abstract fun timelineDao(): TimelineDao
    abstract fun upNextDao(): UpNextDao
    abstract fun syncDao(): SyncDao
    abstract fun peopleDao(): PeopleDao
    abstract fun episodeNumberingDao(): EpisodeNumberingDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        val migration3To4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS `up_next` (
                       `showId` INTEGER NOT NULL,
                       `episodeId` INTEGER,
                       `season` INTEGER NOT NULL,
                       `episodeNumber` INTEGER NOT NULL,
                       `episodeTitle` TEXT NOT NULL,
                       `episodeAirDate` TEXT,
                       `durationMinutes` INTEGER,
                       `refreshedAt` INTEGER NOT NULL,
                       PRIMARY KEY(`showId`))""",
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_watch_history_mediaType_mediaId_season_episodeNumber` " +
                        "ON `watch_history` (`mediaType`, `mediaId`, `season`, `episodeNumber`)",
                )
            }
        }

        val migration4To5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_watch_history_watchedAt` ON `watch_history` (`watchedAt`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_playback_updatedAt` ON `playback` (`updatedAt`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_user_media_state_mediaType_status` ON `user_media_state` (`mediaType`, `status`)")
            }
        }

        val migration5To6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS `sync_operations` (
                       `operationId` TEXT NOT NULL,
                       `operation` TEXT NOT NULL,
                       `mediaType` TEXT NOT NULL,
                       `mediaId` INTEGER NOT NULL,
                       `title` TEXT NOT NULL,
                       `status` TEXT NOT NULL,
                       `message` TEXT,
                       `localValue` TEXT,
                       `remoteValue` TEXT,
                       `createdAt` INTEGER NOT NULL,
                       `updatedAt` INTEGER NOT NULL,
                       `attemptCount` INTEGER NOT NULL,
                       PRIMARY KEY(`operationId`))""",
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_operations_status` ON `sync_operations` (`status`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_operations_mediaType_mediaId` ON `sync_operations` (`mediaType`, `mediaId`)")
            }
        }

        val migration6To7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE sync_operations ADD COLUMN providerId TEXT NOT NULL DEFAULT 'SIMKL'")
            }
        }

        val migration7To8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE sync_operations ADD COLUMN season INTEGER")
                database.execSQL("ALTER TABLE sync_operations ADD COLUMN episode INTEGER")
            }
        }

        val migration8To9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""CREATE TABLE IF NOT EXISTS `sync_operation_deliveries` (
                    `operationId` TEXT NOT NULL,
                    `providerId` TEXT NOT NULL,
                    `status` TEXT NOT NULL,
                    `required` INTEGER NOT NULL,
                    `roleAtEnqueue` TEXT NOT NULL,
                    `attemptCount` INTEGER NOT NULL,
                    `lastError` TEXT,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`operationId`, `providerId`))""")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_operation_deliveries_providerId_status` ON `sync_operation_deliveries` (`providerId`, `status`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_operation_deliveries_operationId` ON `sync_operation_deliveries` (`operationId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_operation_deliveries_status` ON `sync_operation_deliveries` (`status`)")
            }
        }

        val migration9To10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""CREATE TABLE IF NOT EXISTS `sync_operation_deliveries_new` (
                    `operationId` TEXT NOT NULL,
                    `operationVersion` INTEGER NOT NULL,
                    `providerId` TEXT NOT NULL,
                    `status` TEXT NOT NULL,
                    `required` INTEGER NOT NULL,
                    `roleAtEnqueue` TEXT NOT NULL,
                    `attemptCount` INTEGER NOT NULL,
                    `lastError` TEXT,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`operationId`, `operationVersion`, `providerId`))""")
                database.execSQL("""INSERT OR IGNORE INTO `sync_operation_deliveries_new`
                    (`operationId`,`operationVersion`,`providerId`,`status`,`required`,`roleAtEnqueue`,`attemptCount`,`lastError`,`createdAt`,`updatedAt`)
                    SELECT d.`operationId`, COALESCE(o.`createdAt`, 0), d.`providerId`, d.`status`, d.`required`, d.`roleAtEnqueue`, d.`attemptCount`, d.`lastError`, d.`createdAt`, d.`updatedAt`
                    FROM `sync_operation_deliveries` d INNER JOIN `sync_operations` o ON o.`operationId` = d.`operationId`""")
                database.execSQL("DROP TABLE IF EXISTS `sync_operation_deliveries`")
                database.execSQL("ALTER TABLE `sync_operation_deliveries_new` RENAME TO `sync_operation_deliveries`")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_operation_deliveries_providerId_status` ON `sync_operation_deliveries` (`providerId`, `status`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_operation_deliveries_operationId` ON `sync_operation_deliveries` (`operationId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_operation_deliveries_status` ON `sync_operation_deliveries` (`status`)")
            }
        }

        val migration10To11 = object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE sync_operations ADD COLUMN payload TEXT")
            }
        }

        val migration11To12 = object : Migration(11, 12) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE sync_operation_deliveries ADD COLUMN providerInstanceId TEXT")
                // Legacy Floppy rows have no trustworthy target identity and
                // must never be replayed against a newly connected server.
                database.execSQL("UPDATE sync_operation_deliveries SET status = 'CANCELLED_PROVIDER_INSTANCE_CHANGED', lastError = 'Legacy Floppy target instance is unknown' WHERE providerId = 'FLOPPY' AND providerInstanceId IS NULL AND status IN ('PENDING','FAILED')")
            }
        }

        /** Per-provider air dates; existing episode dates stay as they are until providers report again. */
        val migration12To13 = object : Migration(12, 13) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `episode_air_dates` (`showId` INTEGER NOT NULL, `season` INTEGER NOT NULL, `number` INTEGER NOT NULL, `source` TEXT NOT NULL, `airDate` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`showId`, `season`, `number`, `source`))")
            }
        }

        /** Tracker-to-TMDB episode numbering table; no existing data changes. */
        val migration13To14 = object : Migration(13, 14) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `episode_numbering` (`showId` INTEGER NOT NULL, `scheme` TEXT NOT NULL, `schemeSeason` INTEGER NOT NULL, `schemeEpisode` INTEGER NOT NULL, `status` TEXT NOT NULL, `tmdbSeason` INTEGER, `tmdbEpisode` INTEGER, `absolute` INTEGER, `tvdbSeason` INTEGER, `tvdbEpisode` INTEGER, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`showId`, `scheme`, `schemeSeason`, `schemeEpisode`))")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_episode_numbering_showId_tmdbSeason_tmdbEpisode` ON `episode_numbering` (`showId`, `tmdbSeason`, `tmdbEpisode`)")
            }
        }

        fun create(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "cinetrack-v27.db",
            ).addMigrations(migration3To4, migration4To5, migration5To6, migration6To7, migration7To8, migration8To9, migration9To10, migration10To11, migration11To12, migration12To13, migration13To14).build().also { instance = it }
        }
    }
}

fun MediaEntity.toDomain(state: UserMediaStateEntity? = null): MediaCard = MediaCard(
    id = tmdbId,
    type = MediaType.valueOf(mediaType),
    title = title,
    overview = overview,
    posterUrl = tmdbImageUrl(posterPath, "w500"),
    backdropUrl = tmdbImageUrl(backdropPath, "w1280"),
    releaseDate = releaseDate,
    score = score,
    status = state?.status?.let { runCatching { LibraryStatus.valueOf(it) }.getOrDefault(LibraryStatus.NONE) }
        ?: LibraryStatus.NONE,
    watched = state?.watched == true,
    runtimeMinutes = runtimeMinutes,
    genres = genres.split('|').filter(String::isNotBlank),
    providers = providers.split('|').filter(String::isNotBlank),
    collectionId = collectionId,
    libraryUpdatedAt = state?.updatedAt,
)

fun MediaCard.toEntity() = MediaEntity(
    mediaType = type.name,
    tmdbId = id,
    title = title,
    overview = overview,
    posterPath = posterUrl?.substringAfter("/w500", posterUrl),
    backdropPath = backdropUrl?.substringAfter("/w1280", backdropUrl),
    releaseDate = releaseDate,
    score = score,
    runtimeMinutes = runtimeMinutes,
    genres = genres.joinToString("|"),
    providers = providers.joinToString("|"),
    collectionId = collectionId,
)

