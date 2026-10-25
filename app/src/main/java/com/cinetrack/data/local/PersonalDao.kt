package com.cinetrack.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Your own score (1-10) and note for a title. Local only; included in backups. */
@Entity(tableName = "user_ratings", primaryKeys = ["mediaType", "mediaId"])
data class UserRatingEntity(
    val mediaType: String,
    val mediaId: Int,
    val score: Int?,
    val note: String,
    val updatedAt: Long,
)

/** A list you made yourself, next to the five library statuses. */
@Entity(tableName = "custom_lists")
data class CustomListEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
)

@Entity(
    tableName = "custom_list_items",
    primaryKeys = ["listId", "mediaType", "mediaId"],
    indices = [Index(value = ["mediaType", "mediaId"])],
)
data class CustomListItemEntity(
    val listId: Long,
    val mediaType: String,
    val mediaId: Int,
    val addedAt: Long,
)

/** One list's title with its poster, newest first, for the list covers. */
data class CustomListPoster(val listId: Long, val posterPath: String?)

@Dao
interface PersonalDao {
    @Query("SELECT * FROM user_ratings")
    fun ratings(): Flow<List<UserRatingEntity>>

    @Query("SELECT * FROM user_ratings")
    suspend fun ratingsSnapshot(): List<UserRatingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRatings(items: List<UserRatingEntity>)

    @Query("DELETE FROM user_ratings WHERE mediaType = :type AND mediaId = :id")
    suspend fun deleteRating(type: String, id: Int)

    @Query("SELECT * FROM custom_lists ORDER BY createdAt")
    fun lists(): Flow<List<CustomListEntity>>

    @Query("SELECT * FROM custom_lists ORDER BY createdAt")
    suspend fun listsSnapshot(): List<CustomListEntity>

    @Insert
    suspend fun insertList(item: CustomListEntity): Long

    @Query("UPDATE custom_lists SET name = :name WHERE id = :id")
    suspend fun renameList(id: Long, name: String)

    @Query("DELETE FROM custom_lists WHERE id = :id")
    suspend fun deleteList(id: Long)

    @Query("SELECT * FROM custom_list_items ORDER BY addedAt DESC")
    fun listItems(): Flow<List<CustomListItemEntity>>

    @Query("SELECT * FROM custom_list_items ORDER BY addedAt DESC")
    suspend fun listItemsSnapshot(): List<CustomListItemEntity>

    @Query(
        "SELECT i.listId AS listId, m.posterPath AS posterPath FROM custom_list_items i " +
            "JOIN media m ON m.mediaType = i.mediaType AND m.tmdbId = i.mediaId ORDER BY i.addedAt DESC",
    )
    fun listPosters(): Flow<List<CustomListPoster>>

    @Query(
        "SELECT m.* FROM custom_list_items i JOIN media m ON m.mediaType = i.mediaType AND m.tmdbId = i.mediaId " +
            "WHERE i.listId = :listId ORDER BY i.addedAt DESC",
    )
    fun listMedia(listId: Long): Flow<List<MediaEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addItems(items: List<CustomListItemEntity>)

    @Query("DELETE FROM custom_list_items WHERE listId = :listId AND mediaType = :type AND mediaId = :id")
    suspend fun removeItem(listId: Long, type: String, id: Int)

    @Query("DELETE FROM custom_list_items WHERE listId = :listId")
    suspend fun clearList(listId: Long)

    /** Plan-to-watch titles, newest first, for the streaming availability check. */
    @Query(
        "SELECT m.* FROM user_media_state s JOIN media m ON m.mediaType = s.mediaType AND m.tmdbId = s.mediaId " +
            "WHERE s.status = 'PLAN_TO_WATCH' ORDER BY s.updatedAt DESC LIMIT :limit",
    )
    suspend fun plannedMedia(limit: Int): List<MediaEntity>

    @Query("SELECT COUNT(*) FROM media WHERE mediaType = :type AND tmdbId = :id")
    suspend fun mediaCount(type: String, id: Int): Int
}
