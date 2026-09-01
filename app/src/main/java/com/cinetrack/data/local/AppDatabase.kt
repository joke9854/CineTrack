package com.cinetrack.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.Index
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.cinetrack.domain.LibraryStatus
import com.cinetrack.domain.MediaCard
import com.cinetrack.domain.MediaType
import kotlinx.coroutines.flow.Flow

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

@Entity(tableName = "user_media_state", primaryKeys = ["mediaType", "mediaId"])
data class UserMediaStateEntity(
    val mediaType: String,
    val mediaId: Int,
    val status: String,
    val watched: Boolean,
    val simklId: Long? = null,
    val updatedAt: Long = System.currentTimeMillis(),
    val dirty: Boolean = false,
)

@Entity(tableName = "playback", primaryKeys = ["mediaType", "mediaId", "episodeId"])
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
    indices = [Index(value = ["mediaType", "mediaId", "season", "episodeNumber"])],
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

/**
 * One transactionally consistent view of every table used to build AppUiState.
 * Reading these tables independently allowed a sync commit to land between two
 * queries, producing combinations that never actually existed in the database.
 */
data class AppDatabaseSnapshot(
    val media: List<MediaEntity>,
    val rails: List<MediaRailEntity>,
    val states: List<UserMediaStateEntity>,
    val episodes: List<EpisodeEntity>,
    val playback: List<PlaybackEntity>,
    val history: List<WatchHistoryEntity>,
    val upNext: List<UpNextEntity>,
    val syncStates: List<SyncStateEntity>,
)

/** Small transactional input set used only by the post-import Progress rebuild. */
data class ProgressDatabaseSnapshot(
    val media: List<MediaEntity>,
    val states: List<UserMediaStateEntity>,
    val history: List<WatchHistoryEntity>,
)

@Dao
interface AppSnapshotDao {
    @Query("SELECT * FROM media")
    suspend fun media(): List<MediaEntity>

    @Query("SELECT * FROM media_rails ORDER BY railId, position")
    suspend fun rails(): List<MediaRailEntity>

    @Query("SELECT * FROM user_media_state")
    suspend fun states(): List<UserMediaStateEntity>

    @Query("SELECT * FROM episodes ORDER BY showId, season, number")
    suspend fun episodes(): List<EpisodeEntity>

    @Query("SELECT * FROM playback ORDER BY updatedAt DESC")
    suspend fun playback(): List<PlaybackEntity>

    @Query("SELECT * FROM watch_history ORDER BY watchedAt DESC")
    suspend fun history(): List<WatchHistoryEntity>

    @Query("SELECT * FROM up_next ORDER BY showId")
    suspend fun upNext(): List<UpNextEntity>

    @Query("SELECT * FROM sync_state")
    suspend fun syncStates(): List<SyncStateEntity>

    @Transaction
    suspend fun snapshot(): AppDatabaseSnapshot = AppDatabaseSnapshot(
        media = media(),
        rails = rails(),
        states = states(),
        episodes = episodes(),
        playback = playback(),
        history = history(),
        upNext = upNext(),
        syncStates = syncStates(),
    )
}

@Dao
interface ProgressSnapshotDao {
    @Query(
        """SELECT media.* FROM media
           INNER JOIN user_media_state
             ON media.mediaType = user_media_state.mediaType AND media.tmdbId = user_media_state.mediaId
           WHERE media.mediaType = 'TV' AND user_media_state.status NOT IN ('NONE', 'DROPPED')""",
    )
    suspend fun activeTvMedia(): List<MediaEntity>

    @Query("SELECT * FROM user_media_state WHERE mediaType = 'TV' AND status NOT IN ('NONE', 'DROPPED')")
    suspend fun activeTvStates(): List<UserMediaStateEntity>

    @Query(
        """SELECT watch_history.* FROM watch_history
           INNER JOIN user_media_state
             ON user_media_state.mediaType = 'TV' AND watch_history.mediaId = user_media_state.mediaId
           WHERE watch_history.mediaType = 'TV'
             AND watch_history.season IS NOT NULL
             AND watch_history.episodeNumber IS NOT NULL
             AND user_media_state.status NOT IN ('NONE', 'DROPPED')
           ORDER BY watch_history.watchedAt DESC""",
    )
    suspend fun activeTvHistory(): List<WatchHistoryEntity>


    @Query(
        """SELECT episodes.* FROM episodes
           INNER JOIN user_media_state
             ON user_media_state.mediaType = 'TV' AND episodes.showId = user_media_state.mediaId
           WHERE user_media_state.status NOT IN ('NONE', 'DROPPED')
           ORDER BY episodes.showId, episodes.season, episodes.number""",
    )
    suspend fun activeTvEpisodes(): List<EpisodeEntity>

    @Transaction
    suspend fun snapshot(): ProgressDatabaseSnapshot = ProgressDatabaseSnapshot(
        media = activeTvMedia(),
        states = activeTvStates(),
        history = activeTvHistory(),
    )
}

@Dao
interface MediaDao {
    @Query("SELECT * FROM media")
    suspend fun mediaSnapshot(): List<MediaEntity>

    @Query("SELECT * FROM media_rails ORDER BY railId, position")
    suspend fun railSnapshot(): List<MediaRailEntity>

    @Query("SELECT * FROM episodes ORDER BY showId, season, number")
    suspend fun episodeSnapshot(): List<EpisodeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMedia(items: List<MediaEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRails(items: List<MediaRailEntity>)

    @Query("DELETE FROM media_rails WHERE railId = :railId")
    suspend fun clearRail(railId: String)

    @Query(
        """SELECT media.* FROM media
           INNER JOIN media_rails ON media.mediaType = media_rails.mediaType AND media.tmdbId = media_rails.mediaId
           WHERE media_rails.railId = :railId ORDER BY media_rails.position""",
    )
    fun observeRail(railId: String): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media WHERE mediaType = :type AND tmdbId = :id LIMIT 1")
    suspend fun get(type: String, id: Int): MediaEntity?

    @Query("SELECT COUNT(*) FROM media")
    suspend fun count(): Int

    @Query("SELECT * FROM media WHERE title LIKE '%' || :query || '%' ORDER BY score DESC LIMIT 60")
    fun searchLocal(query: String): Flow<List<MediaEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEpisodes(items: List<EpisodeEntity>)

    @Query("SELECT * FROM episodes WHERE showId = :showId ORDER BY season, number")
    suspend fun episodesForShow(showId: Int): List<EpisodeEntity>

    @Query("SELECT * FROM episodes WHERE showId = :showId AND season = :season AND number = :number LIMIT 1")
    suspend fun episode(showId: Int, season: Int, number: Int): EpisodeEntity?

    @Transaction
    suspend fun replaceRail(railId: String, media: List<MediaEntity>) {
        upsertMedia(media)
        clearRail(railId)
        upsertRails(media.mapIndexed { index, item -> MediaRailEntity(railId, item.mediaType, item.tmdbId, index) })
    }
}

@Dao
interface StateDao {
    @Query("SELECT * FROM user_media_state")
    suspend fun stateSnapshot(): List<UserMediaStateEntity>

    @Query("SELECT * FROM user_media_state WHERE mediaType = :type AND mediaId = :id LIMIT 1")
    suspend fun get(type: String, id: Int): UserMediaStateEntity?

    @Query("SELECT * FROM user_media_state")
    fun observeAll(): Flow<List<UserMediaStateEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: UserMediaStateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<UserMediaStateEntity>)

    @Query("SELECT * FROM user_media_state WHERE dirty = 1 ORDER BY updatedAt")
    suspend fun pendingStates(): List<UserMediaStateEntity>

    @Query("UPDATE user_media_state SET dirty = 0 WHERE mediaType = :type AND mediaId = :id AND updatedAt = :updatedAt")
    suspend fun markCleanIfUnchanged(type: String, id: Int, updatedAt: Long)
}

@Dao
interface TimelineDao {
    @Query("SELECT * FROM playback ORDER BY updatedAt DESC")
    suspend fun playbackSnapshot(): List<PlaybackEntity>

    @Query("SELECT * FROM watch_history ORDER BY watchedAt DESC")
    suspend fun historySnapshot(): List<WatchHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlayback(items: List<PlaybackEntity>)

    @Query("DELETE FROM playback")
    suspend fun clearPlayback()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(item: WatchHistoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistoryItems(items: List<WatchHistoryEntity>)

    @Query("SELECT * FROM playback ORDER BY updatedAt DESC")
    fun observePlayback(): Flow<List<PlaybackEntity>>

    @Query("SELECT * FROM watch_history ORDER BY watchedAt DESC")
    fun observeHistory(): Flow<List<WatchHistoryEntity>>

    @Query("SELECT * FROM watch_history WHERE mediaType = :mediaType AND mediaId = :mediaId AND season IS NOT NULL AND episodeNumber IS NOT NULL")
    suspend fun episodeHistoryForShow(mediaType: String, mediaId: Int): List<WatchHistoryEntity>

    @Query("DELETE FROM watch_history WHERE mediaType = :mediaType AND mediaId = :mediaId AND season = :season AND episodeNumber = :episodeNumber")
    suspend fun deleteEpisodeHistory(mediaType: String, mediaId: Int, season: Int, episodeNumber: Int)

    @Query("DELETE FROM watch_history WHERE mediaType = :mediaType AND mediaId = :mediaId")
    suspend fun deleteMediaHistory(mediaType: String, mediaId: Int)

    @Query("DELETE FROM watch_history WHERE mediaType = :mediaType AND mediaId IN (:mediaIds)")
    suspend fun deleteMediaHistories(mediaType: String, mediaIds: List<Int>)

    @Query("DELETE FROM watch_history WHERE id IN (:ids)")
    suspend fun deleteHistoryRows(ids: List<Long>)

    @Query("UPDATE watch_history SET episodeTitle = :title WHERE mediaType = :mediaType AND mediaId = :mediaId AND season = :season AND episodeNumber = :episodeNumber")
    suspend fun updateEpisodeTitle(mediaType: String, mediaId: Int, season: Int, episodeNumber: Int, title: String)
}

@Dao
interface UpNextDao {
    @Query("SELECT * FROM up_next ORDER BY showId")
    suspend fun snapshot(): List<UpNextEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<UpNextEntity>)

    @Query("DELETE FROM up_next")
    suspend fun clear()

    @Transaction
    suspend fun replaceAll(items: List<UpNextEntity>) {
        clear()
        if (items.isNotEmpty()) upsertAll(items)
    }
}

@Dao
interface SyncDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: SyncStateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(states: List<SyncStateEntity>)

    @Query("SELECT * FROM sync_state WHERE area = :area LIMIT 1")
    suspend fun get(area: String): SyncStateEntity?

    @Query("DELETE FROM sync_state WHERE area = :area")
    suspend fun delete(area: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun queue(write: PendingWriteEntity)

    @Query("SELECT * FROM pending_writes ORDER BY createdAt")
    suspend fun pendingWrites(): List<PendingWriteEntity>

    @Query("DELETE FROM pending_writes WHERE id = :id")
    suspend fun deleteWrite(id: Long)

    @Query("DELETE FROM pending_writes WHERE id IN (:ids)")
    suspend fun deleteWrites(ids: List<Long>)
}

@Dao
interface PeopleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPerson(person: PersonEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCredits(credits: List<PersonMovieCreditEntity>)

    @Query("SELECT * FROM people WHERE tmdbId = :id LIMIT 1")
    suspend fun person(id: Int): PersonEntity?

    @Query("SELECT * FROM person_movie_credits WHERE personId = :id ORDER BY releaseDate DESC")
    suspend fun credits(id: Int): List<PersonMovieCreditEntity>
}

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
    ],
    version = 4,
    exportSchema = false,
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

    companion object {
        @Volatile private var instance: AppDatabase? = null

        private val migration3To4 = object : Migration(3, 4) {
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

        fun create(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "cinetrack-v27.db",
            ).addMigrations(migration3To4).build().also { instance = it }
        }
    }
}

fun MediaEntity.toDomain(state: UserMediaStateEntity? = null): MediaCard = MediaCard(
    id = tmdbId,
    type = MediaType.valueOf(mediaType),
    title = title,
    overview = overview,
    posterUrl = posterPath?.let { "https://image.tmdb.org/t/p/w500$it" },
    backdropUrl = backdropPath?.let { "https://image.tmdb.org/t/p/w1280$it" },
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
