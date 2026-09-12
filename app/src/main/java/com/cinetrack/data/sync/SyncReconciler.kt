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
            mergeField(l.libraryState, r.libraryState, b?.libraryState, b != null, hasValidLibraryIntent(local, l, MediaType.MOVIE),
                onLocal = { /* The durable state:* row owns delivery. */ },
                onRemote = { mutations += LocalMutation.SetLibraryStatus(MediaType.MOVIE, l.ids.tmdb ?: r.ids.tmdb ?: 0L, r.libraryState ?: LibraryStatus.NONE) },
                onConflict = { conflicts += conflict(key, l.ids, MediaType.MOVIE, ConflictField.LIBRARY_STATUS, l.libraryState?.name, r.libraryState?.name, b?.libraryState?.name, provider) })
            mergeField(l.watched, r.watched, b?.watched, b != null, hasValidMovieWatchedIntent(local, l),
                onLocal = { /* The durable movie-history row owns delivery. */ },
                onRemote = { mutations += LocalMutation.SetWatched(MediaType.MOVIE, l.ids.tmdb ?: r.ids.tmdb ?: 0L, r.watched, watchedAt = r.watchedAt) },
                onConflict = { conflicts += conflict(key, l.ids, MediaType.MOVIE, ConflictField.WATCHED, l.watched.toString(), r.watched.toString(), b?.watched?.toString(), provider) })
        }
        local.state.shows.forEach { l ->
            val r = remote.shows.firstOrNull { identity.matches(l.ids, it.ids) } ?: return@forEach
            val b = local.baseline?.shows?.firstOrNull { identity.matches(l.ids, it.ids) }
            val key = identity.key(l.ids)
            mergeField(l.libraryState, r.libraryState, b?.libraryState, b != null, hasValidLibraryIntent(local, l, MediaType.TV),
                onLocal = { /* The durable state:* row owns delivery. */ },
                onRemote = { mutations += LocalMutation.SetLibraryStatus(MediaType.TV, l.ids.tmdb ?: r.ids.tmdb ?: 0L, r.libraryState ?: LibraryStatus.NONE) },
                onConflict = { conflicts += conflict(key, l.ids, MediaType.TV, ConflictField.LIBRARY_STATUS, l.libraryState?.name, r.libraryState?.name, b?.libraryState?.name, provider) })
        }
        local.state.episodes.forEach { l ->
            val r = remote.episodes.firstOrNull { it.season == l.season && it.episode == l.episode && identity.matches(l.showIds, it.showIds) } ?: return@forEach
            val b = local.baseline?.episodes?.firstOrNull { it.season == l.season && it.episode == l.episode && identity.matches(l.showIds, it.showIds) }
            val key = "${identity.key(l.showIds)}:${l.season}:${l.episode}"
            mergeField(l.watched, r.watched, b?.watched, b != null, hasValidEpisodeIntent(local, l),
                onLocal = { /* The durable write:* row owns delivery. */ },
                onRemote = { mutations += LocalMutation.SetWatched(MediaType.TV, l.showIds.tmdb ?: r.showIds.tmdb ?: 0L, r.watched, l.season, l.episode, r.watchedAt) },
                onConflict = { conflicts += conflict(key, l.showIds, MediaType.TV, ConflictField.EPISODE_WATCHED, l.watched.toString(), r.watched.toString(), b?.watched?.toString(), provider, l.season, l.episode) })
        }
        // Reconciliation is a direction decision only. Existing state:/write:
        // operations are the single durable representation of a local edit;
        // manufacturing reconcile:* rows here would duplicate that edit.
        return ReconciliationResult(mutations.distinct(), outbound, conflicts.distinctBy(SyncConflict::conflictId))
    }

    private fun <T> mergeField(local: T, remote: T, baseline: T?, hasBaseline: Boolean, localProtected: Boolean,
        onLocal: () -> Unit, onRemote: () -> Unit, onConflict: () -> Unit) {
        if (local == remote) return
        if (localProtected) {
            if (!hasBaseline || remote == baseline) {
                // The existing durable local operation is responsible for the
                // push; never create a second operation for it.
                onLocal()
            } else {
                onConflict()
            }
            return
        }
        // Without a current, value-matching local intent, local is not
        // authoritative. This includes stale dirty flags and old queue rows.
        // MAIN wins whenever the snapshots do not converge.
        onRemote()
    }

    private fun conflict(key: String, ids: MediaIds, type: MediaType, field: ConflictField, local: String?, remote: String?, baseline: String?, provider: TrackingProviderId, season: Int? = null, episode: Int? = null) =
        SyncConflict(mediaKey = key, field = field, localValue = local, remoteValue = remote, localUpdatedAt = null, remoteUpdatedAt = null, providerId = provider, mediaType = type, conflictId = "$key:${field.name}:${season ?: 0}:${episode ?: 0}", ids = ids, baselineValue = baseline, season = season, episode = episode, createdAt = Instant.now())

    private fun hasValidLibraryIntent(local: LocalTrackingSnapshot, state: TrackedMovieState, type: MediaType): Boolean =
        hasValidLibraryIntent(local, state.ids, type, state.libraryState, state.updatedAt)

    private fun hasValidLibraryIntent(local: LocalTrackingSnapshot, state: TrackedShowState, type: MediaType): Boolean =
        hasValidLibraryIntent(local, state.ids, type, state.libraryState, state.updatedAt)

    private fun hasValidLibraryIntent(
        local: LocalTrackingSnapshot,
        ids: MediaIds,
        type: MediaType,
        value: LibraryStatus?,
        updatedAt: Instant?,
    ): Boolean {
        val mediaId = ids.tmdb?.toInt() ?: return false
        if ("${type.name}:$mediaId" !in local.dirtyMediaKeys) return false
        return local.pendingOperations.any { operation ->
            operation.mediaType == type && operation.mediaId == mediaId &&
                operation.type == SyncOperationType.LIBRARY_STATUS &&
                operation.value == value?.name &&
                isCurrent(operation, updatedAt)
        }
    }

    private fun hasValidMovieWatchedIntent(local: LocalTrackingSnapshot, state: TrackedMovieState): Boolean {
        val mediaId = state.ids.tmdb?.toInt() ?: return false
        return local.pendingOperations.any { operation ->
            operation.mediaType == MediaType.MOVIE && operation.mediaId == mediaId &&
                operation.type in setOf(SyncOperationType.MOVIE_WATCHED, SyncOperationType.MOVIE_UNWATCHED) &&
                (operation.type == SyncOperationType.MOVIE_WATCHED) == state.watched &&
                isCurrent(operation, state.updatedAt)
        }
    }

    private fun hasValidEpisodeIntent(local: LocalTrackingSnapshot, state: TrackedEpisodeState): Boolean {
        val mediaId = state.showIds.tmdb?.toInt() ?: return false
        return local.pendingOperations.any { operation ->
            operation.mediaType == MediaType.TV && operation.mediaId == mediaId &&
                operation.type in setOf(SyncOperationType.EPISODE_WATCHED, SyncOperationType.EPISODE_UNWATCHED) &&
                (operation.type == SyncOperationType.EPISODE_WATCHED) == state.watched &&
                operation.payload.orEmpty().startsWith("${state.season}:${state.episode}") &&
                isCurrent(operation, state.updatedAt)
        }
    }

    private fun isCurrent(operation: SyncOperation, updatedAt: Instant?): Boolean {
        val current = updatedAt?.toEpochMilli() ?: return true
        return operation.sourceVersion == current
    }
}

