package com.cinetrack.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

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
    suspend fun railMedia(railId: String): List<MediaEntity>

    @Query("SELECT * FROM media WHERE mediaType = :type AND tmdbId = :id LIMIT 1")
    suspend fun get(type: String, id: Int): MediaEntity?

    @Query("SELECT COUNT(*) FROM media")
    suspend fun count(): Int

    @Query("SELECT * FROM media WHERE title LIKE '%' || :query || '%' ORDER BY score DESC LIMIT 60")
    fun searchLocal(query: String): Flow<List<MediaEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEpisodes(items: List<EpisodeEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAirDates(items: List<EpisodeAirDateEntity>)

    @Query("SELECT * FROM episode_air_dates WHERE showId IN (:showIds)")
    suspend fun airDatesForShows(showIds: List<Int>): List<EpisodeAirDateEntity>

    @Query("SELECT * FROM episode_air_dates")
    suspend fun allAirDates(): List<EpisodeAirDateEntity>

    @Query("UPDATE episodes SET airDate = :airDate WHERE showId = :showId AND season = :season AND number = :number")
    suspend fun setEpisodeAirDate(showId: Int, season: Int, number: Int, airDate: String)

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

    @Query("UPDATE user_media_state SET updatedAt = :updatedAt WHERE mediaType = :type AND mediaId = :id")
    suspend fun touch(type: String, id: Int, updatedAt: Long)

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

    @Query("SELECT * FROM watch_history WHERE mediaType = :mediaType AND mediaId = :mediaId AND season IS NOT NULL AND episodeNumber IS NOT NULL")
    suspend fun episodeHistoryForShow(mediaType: String, mediaId: Int): List<WatchHistoryEntity>

    @Query("SELECT * FROM watch_history WHERE mediaType = :mediaType AND mediaId = :mediaId AND season IS NULL AND episodeNumber IS NULL ORDER BY watchedAt DESC")
    suspend fun movieHistory(mediaType: String, mediaId: Int): List<WatchHistoryEntity>

    @Query("DELETE FROM watch_history WHERE mediaType = :mediaType AND mediaId = :mediaId AND season = :season AND episodeNumber = :episodeNumber")
    suspend fun deleteEpisodeHistory(mediaType: String, mediaId: Int, season: Int, episodeNumber: Int)

    @Query("DELETE FROM watch_history WHERE mediaType = :mediaType AND mediaId = :mediaId")
    suspend fun deleteMediaHistory(mediaType: String, mediaId: Int)

    @Query("DELETE FROM watch_history WHERE mediaType = :mediaType AND mediaId IN (:mediaIds)")
    suspend fun deleteMediaHistories(mediaType: String, mediaIds: List<Int>)

    @Query("DELETE FROM watch_history WHERE id IN (:ids)")
    suspend fun deleteHistoryRows(ids: List<Long>)

    /** Renumbers one play in place; id and watchedAt never change. */
    @Query("UPDATE watch_history SET season = :season, episodeNumber = :episodeNumber WHERE id = :id")
    suspend fun moveEpisodeHistoryRow(id: Long, season: Int, episodeNumber: Int)

    @Query("UPDATE watch_history SET episodeTitle = :title WHERE mediaType = :mediaType AND mediaId = :mediaId AND season = :season AND episodeNumber = :episodeNumber")
    suspend fun updateEpisodeTitle(mediaType: String, mediaId: Int, season: Int, episodeNumber: Int, title: String)
}

@Dao
interface UpNextDao {
    @Query("SELECT * FROM up_next ORDER BY showId")
    suspend fun snapshot(): List<UpNextEntity>

    @Query(
        """SELECT up_next.* FROM up_next
           INNER JOIN user_media_state ON user_media_state.mediaType = 'TV' AND user_media_state.mediaId = up_next.showId
           WHERE user_media_state.status NOT IN ('NONE', 'DROPPED')
           ORDER BY user_media_state.updatedAt DESC LIMIT 1""",
    )
    suspend fun firstForWidget(): UpNextEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<UpNextEntity>)

    @Query("DELETE FROM up_next WHERE showId = :showId")
    suspend fun delete(showId: Int)

    @Query("DELETE FROM up_next")
    suspend fun clear()

    @Transaction
    suspend fun replaceAll(items: List<UpNextEntity>) {
        clear()
        if (items.isNotEmpty()) upsertAll(items)
    }
}

@Dao
interface EpisodeNumberingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(items: List<EpisodeNumberingEntity>)

    @Query("SELECT * FROM episode_numbering WHERE showId = :showId AND scheme = :scheme")
    suspend fun forShow(showId: Int, scheme: String): List<EpisodeNumberingEntity>

    @Query("SELECT * FROM episode_numbering WHERE showId = :showId AND scheme = :scheme AND schemeSeason = :schemeSeason AND schemeEpisode = :schemeEpisode")
    suspend fun row(showId: Int, scheme: String, schemeSeason: Int, schemeEpisode: Int): EpisodeNumberingEntity?

    @Query("SELECT * FROM episode_numbering WHERE showId = :showId AND scheme = :scheme AND tmdbSeason = :tmdbSeason AND tmdbEpisode = :tmdbEpisode")
    suspend fun byTmdb(showId: Int, scheme: String, tmdbSeason: Int, tmdbEpisode: Int): List<EpisodeNumberingEntity>
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
