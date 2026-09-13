package com.cinetrack.data.sync

import com.cinetrack.data.local.PendingWriteEntity
import com.cinetrack.data.local.SyncOperationEntity
import com.cinetrack.domain.MediaType

/**
 * Provider-neutral identity for one mutable CineTrack field.
 *
 * Operation ids are durable implementation details and may be reused or
 * regenerated for successive generations.  Supersession, conflict cleanup and
 * mirror replacement must compare this identity instead.
 */
@JvmInline
value class SyncLogicalField(val key: String) {
    companion object {
        fun library(mediaType: MediaType, mediaId: Int) = SyncLogicalField("library:${mediaType.name}:$mediaId")
        fun movieWatched(mediaId: Int) = SyncLogicalField("movie-watched:$mediaId")
        fun episodeWatched(showId: Int, season: Int, episode: Int) = SyncLogicalField("episode-watched:$showId:$season:$episode")
        fun rating(mediaType: MediaType, mediaId: Int) = SyncLogicalField("rating:${mediaType.name}:$mediaId")
    }
}

fun SyncOperation.logicalField(): SyncLogicalField? = when (type) {
    SyncOperationType.LIBRARY_STATUS -> SyncLogicalField.library(mediaType, mediaId)
    SyncOperationType.MOVIE_WATCHED,
    SyncOperationType.MOVIE_UNWATCHED,
    // Kept as an alias so pre-split databases participate in the same
    // supersession and reconciliation rules as modern movie intents.
    SyncOperationType.MEDIA_HISTORY_REMOVE ->
        if (mediaType == MediaType.MOVIE) SyncLogicalField.movieWatched(mediaId) else null
    SyncOperationType.EPISODE_WATCHED,
    SyncOperationType.EPISODE_UNWATCHED -> {
        val parts = payload.orEmpty().split(':', limit = 3)
        val season = parts.getOrNull(0)?.toIntOrNull()
        val episode = parts.getOrNull(1)?.toIntOrNull()
        if (mediaType == MediaType.TV && season != null && episode != null) {
            SyncLogicalField.episodeWatched(mediaId, season, episode)
        } else null
    }
    SyncOperationType.SET_RATING -> SyncLogicalField.rating(mediaType, mediaId)
}

fun SyncOperation.logicalFieldKey(): String? = logicalField()?.key

fun SyncOperationEntity.logicalField(): SyncLogicalField? {
    val media = runCatching { MediaType.valueOf(mediaType) }.getOrNull() ?: return null
    val type = runCatching { SyncOperationType.valueOf(operation) }.getOrNull() ?: return null
    return when (type) {
        SyncOperationType.LIBRARY_STATUS -> SyncLogicalField.library(media, mediaId)
        SyncOperationType.MOVIE_WATCHED,
        SyncOperationType.MOVIE_UNWATCHED,
        SyncOperationType.MEDIA_HISTORY_REMOVE ->
            if (media == MediaType.MOVIE) SyncLogicalField.movieWatched(mediaId) else null
        SyncOperationType.EPISODE_WATCHED,
        SyncOperationType.EPISODE_UNWATCHED -> {
            if (media != MediaType.TV || season == null || episode == null) return null
            SyncLogicalField.episodeWatched(mediaId, season, episode)
        }
        SyncOperationType.SET_RATING -> SyncLogicalField.rating(media, mediaId)
    }
}

fun SyncOperationEntity.logicalFieldKey(): String? = logicalField()?.key

fun PendingWriteEntity.logicalField(): SyncLogicalField? {
    val media = runCatching { MediaType.valueOf(mediaType) }.getOrNull() ?: return null
    val type = runCatching { SyncOperationType.valueOf(operation) }.getOrNull() ?: return null
    return SyncOperation(
        id = "write:$id",
        type = type,
        mediaType = media,
        mediaId = mediaId,
        title = "",
        payload = payload,
        sourceVersion = createdAt,
    ).logicalField()
}

fun PendingWriteEntity.logicalFieldKey(): String? = logicalField()?.key
