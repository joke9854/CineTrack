package com.cinetrack.data.sync

import com.cinetrack.domain.LibraryStatus
import com.cinetrack.domain.MediaType
import java.time.Instant

/** Provider-neutral identity used by reconciliation and provider adapters. */
data class MediaIds(
    val tmdb: Long? = null,
    val tvdb: Long? = null,
    val imdb: String? = null,
    val simkl: Long? = null,
) {
    fun stableKeys(): List<String> = buildList {
        tmdb?.let { add("tmdb:$it") }
        tvdb?.let { add("tvdb:$it") }
        imdb?.takeIf(String::isNotBlank)?.let { add("imdb:${it.lowercase()}") }
        simkl?.let { add("simkl:$it") }
    }
}

data class TrackedMovieState(
    val ids: MediaIds,
    val libraryState: LibraryStatus? = null,
    val watched: Boolean = false,
    val watchedAt: Instant? = null,
    val updatedAt: Instant? = null,
)

data class TrackedShowState(
    val ids: MediaIds,
    val libraryState: LibraryStatus? = null,
    val updatedAt: Instant? = null,
)

data class TrackedEpisodeState(
    val showIds: MediaIds,
    val season: Int,
    val episode: Int,
    val watched: Boolean,
    val watchedAt: Instant? = null,
    val updatedAt: Instant? = null,
)

data class TrackingSnapshot(
    val movies: List<TrackedMovieState> = emptyList(),
    val shows: List<TrackedShowState> = emptyList(),
    val episodes: List<TrackedEpisodeState> = emptyList(),
    val generatedAt: Instant? = null,
    /** True only when the provider guarantees absence means an explicit removal. */
    val completeHistory: Boolean = false,
)

/** Local state is deliberately separate so dirty/pending information cannot be lost. */
data class LocalTrackingSnapshot(
    val state: TrackingSnapshot,
    val dirtyMediaKeys: Set<String> = emptySet(),
    val pendingOperations: List<SyncOperation> = emptyList(),
)

sealed interface LocalMutation {
    val mediaType: MediaType
    val mediaId: Long
    val origin: MutationOrigin

    data class SetLibraryStatus(
        override val mediaType: MediaType,
        override val mediaId: Long,
        val status: LibraryStatus,
        override val origin: MutationOrigin = MutationOrigin.REMOTE_SYNC,
    ) : LocalMutation

    data class SetWatched(
        override val mediaType: MediaType,
        override val mediaId: Long,
        val watched: Boolean,
        val season: Int? = null,
        val episode: Int? = null,
        val watchedAt: Instant? = null,
        override val origin: MutationOrigin = MutationOrigin.REMOTE_SYNC,
    ) : LocalMutation
}

enum class MutationOrigin { USER, LOCAL_SYSTEM, REMOTE_SYNC }

enum class ConflictField { LIBRARY_STATUS, WATCHED, EPISODE_WATCHED }

data class SyncConflict(
    val mediaKey: String,
    val field: ConflictField,
    val localValue: String?,
    val remoteValue: String?,
    val localUpdatedAt: Instant?,
    val remoteUpdatedAt: Instant?,
    val providerId: TrackingProviderId,
    val mediaType: MediaType = MediaType.MOVIE,
)

data class ReconciliationResult(
    val localMutations: List<LocalMutation> = emptyList(),
    val remoteOperations: List<SyncOperation> = emptyList(),
    val conflicts: List<SyncConflict> = emptyList(),
)

interface MediaIdentityResolver {
    fun matches(local: MediaIds, remote: MediaIds): Boolean
    fun key(ids: MediaIds): String = ids.stableKeys().firstOrNull() ?: "unknown"
}

object DefaultMediaIdentityResolver : MediaIdentityResolver {
    override fun matches(local: MediaIds, remote: MediaIds): Boolean {
        val left = local.stableKeys().toSet()
        val right = remote.stableKeys().toSet()
        return left.isNotEmpty() && left.intersect(right).isNotEmpty()
    }
}

