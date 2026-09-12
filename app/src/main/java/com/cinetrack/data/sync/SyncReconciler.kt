package com.cinetrack.data.sync

import com.cinetrack.domain.LibraryStatus
import com.cinetrack.domain.MediaType
import java.time.Instant

/** Pure, provider-neutral reconciliation policy. It performs no network or DB work. */
class SyncReconciler(
    private val identity: MediaIdentityResolver = DefaultMediaIdentityResolver,
) {
    fun reconcile(
        local: LocalTrackingSnapshot,
        remote: TrackingSnapshot,
        provider: TrackingProviderId,
    ): ReconciliationResult {
        val mutations = mutableListOf<LocalMutation>()
        val outbound = mutableListOf<SyncOperation>()
        val conflicts = mutableListOf<SyncConflict>()

        local.state.movies.forEach { localMovie ->
            val remoteMovie = remote.movies.firstOrNull { identity.matches(localMovie.ids, it.ids) }
            if (remoteMovie == null) return@forEach
            val key = identity.key(localMovie.ids)
            if (hasPending(local, localMovie.ids, MediaType.MOVIE) || identity.key(localMovie.ids) in local.dirtyMediaKeys) return@forEach
            compareLibrary(
                mediaType = MediaType.MOVIE,
                mediaId = localMovie.ids.tmdb ?: remoteMovie.ids.tmdb,
                key = key,
                localValue = localMovie.libraryState,
                remoteValue = remoteMovie.libraryState,
                localUpdatedAt = localMovie.updatedAt,
                remoteUpdatedAt = remoteMovie.updatedAt,
                watchedLocal = localMovie.watched,
                watchedRemote = remoteMovie.watched,
                watchedAtLocal = localMovie.watchedAt,
                watchedAtRemote = remoteMovie.watchedAt,
                provider = provider,
                mutations = mutations,
                outbound = outbound,
                conflicts = conflicts,
            )
        }

        local.state.shows.forEach { localShow ->
            val remoteShow = remote.shows.firstOrNull { identity.matches(localShow.ids, it.ids) }
            if (remoteShow == null) return@forEach
            val key = identity.key(localShow.ids)
            if (hasPending(local, localShow.ids, MediaType.TV) || identity.key(localShow.ids) in local.dirtyMediaKeys) return@forEach
            compareLibrary(
                mediaType = MediaType.TV,
                mediaId = localShow.ids.tmdb ?: remoteShow.ids.tmdb,
                key = key,
                localValue = localShow.libraryState,
                remoteValue = remoteShow.libraryState,
                localUpdatedAt = localShow.updatedAt,
                remoteUpdatedAt = remoteShow.updatedAt,
                watchedLocal = null,
                watchedRemote = null,
                watchedAtLocal = null,
                watchedAtRemote = null,
                provider = provider,
                mutations = mutations,
                outbound = outbound,
                conflicts = conflicts,
            )
        }

        local.state.episodes.forEach { localEpisode ->
            val remoteEpisode = remote.episodes.firstOrNull {
                it.season == localEpisode.season &&
                    it.episode == localEpisode.episode &&
                    identity.matches(localEpisode.showIds, it.showIds)
            } ?: return@forEach
            if (hasPendingEpisode(local, localEpisode)) return@forEach
            val key = "${identity.key(localEpisode.showIds)}:${localEpisode.season}:${localEpisode.episode}"
            val decision = compareVersions(localEpisode.updatedAt, remoteEpisode.updatedAt)
            if (localEpisode.watched == remoteEpisode.watched) return@forEach
            when (decision) {
                VersionDecision.LOCAL -> outbound += episodeOperation(localEpisode, key)
                VersionDecision.REMOTE -> mutations += LocalMutation.SetWatched(
                    mediaType = MediaType.TV,
                    mediaId = (localEpisode.showIds.tmdb ?: remoteEpisode.showIds.tmdb ?: 0L),
                    watched = remoteEpisode.watched,
                    season = localEpisode.season,
                    episode = localEpisode.episode,
                    watchedAt = remoteEpisode.watchedAt,
                )
                VersionDecision.CONFLICT -> conflicts += SyncConflict(
                    mediaKey = key,
                    field = ConflictField.EPISODE_WATCHED,
                    localValue = localEpisode.watched.toString(),
                    remoteValue = remoteEpisode.watched.toString(),
                    localUpdatedAt = localEpisode.updatedAt,
                    remoteUpdatedAt = remoteEpisode.updatedAt,
                    providerId = provider,
                    mediaType = MediaType.TV,
                )
            }
        }
        return ReconciliationResult(mutations, outbound.distinctBy(SyncOperation::id), conflicts)
    }

    private fun compareLibrary(
        mediaType: MediaType,
        mediaId: Long?,
        key: String,
        localValue: LibraryStatus?,
        remoteValue: LibraryStatus?,
        localUpdatedAt: Instant?,
        remoteUpdatedAt: Instant?,
        watchedLocal: Boolean?,
        watchedRemote: Boolean?,
        watchedAtLocal: Instant?,
        watchedAtRemote: Instant?,
        provider: TrackingProviderId,
        mutations: MutableList<LocalMutation>,
        outbound: MutableList<SyncOperation>,
        conflicts: MutableList<SyncConflict>,
    ) {
        val id = mediaId?.toInt() ?: return
        if (localValue != remoteValue && localValue != null && remoteValue != null) {
            when (compareVersions(localUpdatedAt, remoteUpdatedAt)) {
                VersionDecision.LOCAL -> outbound += libraryOperation(mediaType, id, localValue, key)
                VersionDecision.REMOTE -> mutations += LocalMutation.SetLibraryStatus(mediaType, id.toLong(), remoteValue)
                VersionDecision.CONFLICT -> conflicts += SyncConflict(
                    key, ConflictField.LIBRARY_STATUS, localValue.name, remoteValue.name,
                    localUpdatedAt, remoteUpdatedAt, provider, mediaType,
                )
            }
        }
        if (watchedLocal != null && watchedRemote != null && watchedLocal != watchedRemote) {
            when (compareVersions(watchedAtLocal ?: localUpdatedAt, watchedAtRemote ?: remoteUpdatedAt)) {
                VersionDecision.LOCAL -> outbound += movieOperation(id, watchedLocal, watchedAtLocal, key)
                VersionDecision.REMOTE -> mutations += LocalMutation.SetWatched(MediaType.MOVIE, id.toLong(), watchedRemote, watchedAt = watchedAtRemote)
                VersionDecision.CONFLICT -> conflicts += SyncConflict(
                    key, ConflictField.WATCHED, watchedLocal.toString(), watchedRemote.toString(),
                    watchedAtLocal ?: localUpdatedAt, watchedAtRemote ?: remoteUpdatedAt, provider, MediaType.MOVIE,
                )
            }
        }
    }

    private fun compareVersions(local: Instant?, remote: Instant?): VersionDecision = when {
        local != null && remote != null && local.isAfter(remote) -> VersionDecision.LOCAL
        local != null && remote != null && remote.isAfter(local) -> VersionDecision.REMOTE
        local == null && remote != null -> VersionDecision.REMOTE
        local != null && remote == null -> VersionDecision.LOCAL
        else -> VersionDecision.CONFLICT
    }

    private fun hasPending(local: LocalTrackingSnapshot, ids: MediaIds, type: MediaType): Boolean =
        local.pendingOperations.any { it.mediaType == type && identity.matches(ids, MediaIds(tmdb = it.mediaId.toLong())) }

    private fun hasPendingEpisode(local: LocalTrackingSnapshot, episode: TrackedEpisodeState): Boolean =
        local.pendingOperations.any {
            it.mediaType == MediaType.TV && it.mediaId.toLong() == (episode.showIds.tmdb ?: -1L) &&
                (it.type == SyncOperationType.EPISODE_WATCHED || it.type == SyncOperationType.EPISODE_UNWATCHED) &&
                it.payload.orEmpty().startsWith("${episode.season}:${episode.episode}")
        }

    private fun libraryOperation(type: MediaType, id: Int, status: LibraryStatus, key: String) = SyncOperation(
        id = "reconcile:library:$key:${status.name}", type = SyncOperationType.LIBRARY_STATUS,
        mediaType = type, mediaId = id, title = "", value = status.name, sourceVersion = System.currentTimeMillis(),
    )

    private fun movieOperation(id: Int, watched: Boolean, watchedAt: Instant?, key: String) = SyncOperation(
        id = "reconcile:movie:$key:${watched}", type = if (watched) SyncOperationType.MOVIE_WATCHED else SyncOperationType.MOVIE_UNWATCHED,
        mediaType = MediaType.MOVIE, mediaId = id, title = "", value = watchedAt?.toString(), sourceVersion = System.currentTimeMillis(),
    )

    private fun episodeOperation(state: TrackedEpisodeState, key: String) = SyncOperation(
        id = "reconcile:episode:$key:${state.watched}",
        type = if (state.watched) SyncOperationType.EPISODE_WATCHED else SyncOperationType.EPISODE_UNWATCHED,
        mediaType = MediaType.TV, mediaId = (state.showIds.tmdb ?: 0L).toInt(), title = "",
        payload = "${state.season}:${state.episode}:${state.watchedAt ?: ""}", sourceVersion = System.currentTimeMillis(),
    )

    private enum class VersionDecision { LOCAL, REMOTE, CONFLICT }
}

