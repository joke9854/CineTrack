package com.cinetrack.data.personal

import androidx.compose.runtime.Immutable
import androidx.room.withTransaction
import com.cinetrack.data.local.AppDatabase
import com.cinetrack.data.local.CustomListEntity
import com.cinetrack.data.local.CustomListItemEntity
import com.cinetrack.data.local.UserRatingEntity
import com.cinetrack.data.local.toDomain
import com.cinetrack.data.local.toEntity
import com.cinetrack.data.remote.tmdbImageUrl
import com.cinetrack.domain.MediaCard
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** Your score (1-10) and note for one title. */
@Immutable
data class UserRating(val score: Int?, val note: String)

/** A list you made, with up to three covers for its card. */
@Immutable
data class CustomList(val id: Long, val name: String, val count: Int, val posters: List<String>)

/**
 * Ratings, notes and your own lists. They live only on this phone (and in backups):
 * Simkl and Floppy have their own rating and list models, which a later sync can map.
 */
class PersonalRepository(private val database: AppDatabase) {
    private val dao get() = database.personalDao()

    /** Keyed by [MediaCard.stableKey]-style "TYPE:id". */
    val ratings: Flow<Map<String, UserRating>> = dao.ratings().map { rows ->
        rows.associate { "${it.mediaType}:${it.mediaId}" to UserRating(it.score, it.note) }
    }

    val lists: Flow<List<CustomList>> = combine(dao.lists(), dao.listPosters()) { lists, posters ->
        val byList = posters.groupBy { it.listId }
        lists.map { list ->
            val items = byList[list.id].orEmpty()
            CustomList(list.id, list.name, items.size, items.mapNotNull { tmdbImageUrl(it.posterPath, "w342") }.take(3))
        }
    }

    /** Which lists each title is in, keyed "TYPE:id". */
    val memberships: Flow<Map<String, Set<Long>>> = dao.listItems().map { items ->
        items.groupBy({ "${it.mediaType}:${it.mediaId}" }, { it.listId }).mapValues { (_, ids) -> ids.toSet() }
    }

    fun listMedia(listId: Long): Flow<List<MediaCard>> = dao.listMedia(listId).map { rows -> rows.map { it.toDomain() } }

    /** A null score with a blank note removes the rating. */
    suspend fun setRating(media: MediaCard, score: Int?, note: String) = database.withTransaction {
        val cleanNote = note.trim().take(NOTE_LIMIT)
        if (score == null && cleanNote.isEmpty()) {
            dao.deleteRating(media.type.name, media.id)
        } else {
            keepMedia(media)
            dao.upsertRatings(listOf(UserRatingEntity(media.type.name, media.id, score?.coerceIn(1, 10), cleanNote, System.currentTimeMillis())))
        }
    }

    /** For imports: sets a score only where you have none, so your own scores and notes win. */
    suspend fun setScoreIfMissing(media: MediaCard, score: Int): Boolean = database.withTransaction {
        if (dao.ratingsSnapshot().any { it.mediaType == media.type.name && it.mediaId == media.id }) return@withTransaction false
        keepMedia(media)
        dao.upsertRatings(listOf(UserRatingEntity(media.type.name, media.id, score.coerceIn(1, 10), "", System.currentTimeMillis())))
        true
    }

    suspend fun createList(name: String): Long =
        dao.insertList(CustomListEntity(name = name.trim().take(NAME_LIMIT), createdAt = System.currentTimeMillis()))

    suspend fun renameList(id: Long, name: String) = dao.renameList(id, name.trim().take(NAME_LIMIT))

    suspend fun deleteList(id: Long) = database.withTransaction {
        dao.clearList(id)
        dao.deleteList(id)
    }

    suspend fun setInList(listId: Long, media: MediaCard, inList: Boolean) = database.withTransaction {
        if (inList) {
            keepMedia(media)
            dao.addItems(listOf(CustomListItemEntity(listId, media.type.name, media.id, System.currentTimeMillis())))
        } else {
            dao.removeItem(listId, media.type.name, media.id)
        }
    }

    /** Lists and ratings join the media table, so a title outside the library keeps a row. */
    private suspend fun keepMedia(media: MediaCard) {
        if (dao.mediaCount(media.type.name, media.id) == 0) database.mediaDao().upsertMedia(listOf(media.toEntity()))
    }

    companion object {
        const val NOTE_LIMIT = 2000
        const val NAME_LIMIT = 60
    }
}
