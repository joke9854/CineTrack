package com.cinetrack.data.sync

import com.cinetrack.domain.LibraryStatus
import com.cinetrack.domain.MediaType
import java.time.Instant

/** Provider-neutral three-way merge. Baseline is the last canonical MAIN snapshot. */
class SyncReconciler(
    private val identity: MediaIdentityResolver = DefaultMediaIdentityResolver,
) {
    fun reconcile(local: LocalTrackingSnapshot, remote: TrackingSnapshot, provider: TrackingProviderId): ReconciliationResult {
        val mutations = mutableListOf<LocalMutation>()
        val outbound = mutableListOf<SyncOperation>()
        val conflicts = mutableListOf<SyncConflict>()
        local.state.movies.forEach { l ->
            val r = remote.movies.firstOrNull { identity.matches(l.ids, it.ids) } ?: return@forEach
            val b = local.baseline?.movies?.firstOrNull { identity.matches(l.ids, it.ids) }
            val key = identity.key(l.ids)
            val dirty = key in local.dirtyMediaKeys
            mergeField(l.libraryState, r.libraryState, b?.libraryState, b != null, dirty || hasPendingLibrary(local, l.ids, MediaType.MOVIE),
                onLocal = { outbound += libraryOperation(MediaType.MOVIE, l.ids, l.libraryState, key) },
                onRemote = { mutations += LocalMutation.SetLibraryStatus(MediaType.MOVIE, l.ids.tmdb ?: r.ids.tmdb ?: 0L, r.libraryState ?: LibraryStatus.NONE) },
                onConflict = { conflicts += conflict(key, l.ids, MediaType.MOVIE, ConflictField.LIBRARY_STATUS, l.libraryState?.name, r.libraryState?.name, b?.libraryState?.name, provider) })
            mergeField(l.watched, r.watched, b?.watched, b != null, dirty || hasPendingWatched(local, l.ids, MediaType.MOVIE),
                onLocal = { outbound += movieOperation(l.ids, l.watched, l.watchedAt, key) },
                onRemote = { mutations += LocalMutation.SetWatched(MediaType.MOVIE, l.ids.tmdb ?: r.ids.tmdb ?: 0L, r.watched, watchedAt = r.watchedAt) },
                onConflict = { conflicts += conflict(key, l.ids, MediaType.MOVIE, ConflictField.WATCHED, l.watched.toString(), r.watched.toString(), b?.watched?.toString(), provider) })
        }
        local.state.shows.forEach { l ->
            val r = remote.shows.firstOrNull { identity.matches(l.ids, it.ids) } ?: return@forEach
            val b = local.baseline?.shows?.firstOrNull { identity.matches(l.ids, it.ids) }
            val key = identity.key(l.ids)
            mergeField(l.libraryState, r.libraryState, b?.libraryState, b != null, key in local.dirtyMediaKeys || hasPendingLibrary(local, l.ids, MediaType.TV),
                onLocal = { outbound += libraryOperation(MediaType.TV, l.ids, l.libraryState, key) },
                onRemote = { mutations += LocalMutation.SetLibraryStatus(MediaType.TV, l.ids.tmdb ?: r.ids.tmdb ?: 0L, r.libraryState ?: LibraryStatus.NONE) },
                onConflict = { conflicts += conflict(key, l.ids, MediaType.TV, ConflictField.LIBRARY_STATUS, l.libraryState?.name, r.libraryState?.name, b?.libraryState?.name, provider) })
        }
        local.state.episodes.forEach { l ->
            val r = remote.episodes.firstOrNull { it.season == l.season && it.episode == l.episode && identity.matches(l.showIds, it.showIds) } ?: return@forEach
            val b = local.baseline?.episodes?.firstOrNull { it.season == l.season && it.episode == l.episode && identity.matches(l.showIds, it.showIds) }
            val key = "${identity.key(l.showIds)}:${l.season}:${l.episode}"
            mergeField(l.watched, r.watched, b?.watched, b != null, hasPendingEpisode(local, l) || key in local.dirtyMediaKeys,
                onLocal = { outbound += episodeOperation(l, key) },
                onRemote = { mutations += LocalMutation.SetWatched(MediaType.TV, l.showIds.tmdb ?: r.showIds.tmdb ?: 0L, r.watched, l.season, l.episode, r.watchedAt) },
                onConflict = { conflicts += conflict(key, l.showIds, MediaType.TV, ConflictField.EPISODE_WATCHED, l.watched.toString(), r.watched.toString(), b?.watched?.toString(), provider, l.season, l.episode) })
        }
        return ReconciliationResult(mutations.distinct(), outbound.distinctBy(SyncOperation::id), conflicts.distinctBy(SyncConflict::conflictId))
    }

    private fun <T> mergeField(local: T, remote: T, baseline: T?, hasBaseline: Boolean, localProtected: Boolean,
        onLocal: () -> Unit, onRemote: () -> Unit, onConflict: () -> Unit) {
        if (local == remote) return
        if (localProtected) { onLocal(); return }
        if (!hasBaseline) { onRemote(); return }
        when {
            local == baseline -> onRemote()
            remote == baseline -> onLocal()
            else -> onConflict()
        }
    }

    private fun conflict(key: String, ids: MediaIds, type: MediaType, field: ConflictField, local: String?, remote: String?, baseline: String?, provider: TrackingProviderId, season: Int? = null, episode: Int? = null) =
        SyncConflict(mediaKey = key, field = field, localValue = local, remoteValue = remote, localUpdatedAt = null, remoteUpdatedAt = null, providerId = provider, mediaType = type, conflictId = "$key:${field.name}:${season ?: 0}:${episode ?: 0}", ids = ids, baselineValue = baseline, season = season, episode = episode, createdAt = Instant.now())

    private fun hasPendingLibrary(local: LocalTrackingSnapshot, ids: MediaIds, type: MediaType) = local.pendingOperations.any { it.mediaType == type && it.type == SyncOperationType.LIBRARY_STATUS && ids.tmdb?.toInt() == it.mediaId }
    private fun hasPendingWatched(local: LocalTrackingSnapshot, ids: MediaIds, type: MediaType) = local.pendingOperations.any { it.mediaType == type && it.type in setOf(SyncOperationType.MOVIE_WATCHED, SyncOperationType.MOVIE_UNWATCHED) && ids.tmdb?.toInt() == it.mediaId }
    private fun hasPendingEpisode(local: LocalTrackingSnapshot, e: TrackedEpisodeState) = local.pendingOperations.any { it.mediaType == MediaType.TV && it.type in setOf(SyncOperationType.EPISODE_WATCHED, SyncOperationType.EPISODE_UNWATCHED) && it.mediaId == (e.showIds.tmdb ?: -1L).toInt() && it.payload.orEmpty().startsWith("${e.season}:${e.episode}") }

    private fun libraryOperation(type: MediaType, ids: MediaIds, status: LibraryStatus?, key: String) = SyncOperation("reconcile:library:$key:${status?.name}", SyncOperationType.LIBRARY_STATUS, type, (ids.tmdb ?: 0L).toInt(), "", status?.name, sourceVersion = System.currentTimeMillis())
    private fun movieOperation(ids: MediaIds, watched: Boolean, at: Instant?, key: String) = SyncOperation("reconcile:movie:$key:$watched", if (watched) SyncOperationType.MOVIE_WATCHED else SyncOperationType.MOVIE_UNWATCHED, MediaType.MOVIE, (ids.tmdb ?: 0L).toInt(), "", at?.toString(), sourceVersion = System.currentTimeMillis())
    private fun episodeOperation(e: TrackedEpisodeState, key: String) = SyncOperation("reconcile:episode:$key:${e.watched}", if (e.watched) SyncOperationType.EPISODE_WATCHED else SyncOperationType.EPISODE_UNWATCHED, MediaType.TV, (e.showIds.tmdb ?: 0L).toInt(), "", payload = "${e.season}:${e.episode}:${e.watchedAt ?: ""}", sourceVersion = System.currentTimeMillis())
}

