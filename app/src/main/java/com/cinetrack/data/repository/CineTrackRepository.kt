package com.cinetrack.data.repository

import androidx.room.withTransaction
import com.cinetrack.BuildConfig
import com.cinetrack.data.library.LibraryRepository
import com.cinetrack.data.local.AppDatabase
import com.cinetrack.data.local.MediaEntity
import com.cinetrack.data.local.PendingWriteEntity
import com.cinetrack.data.local.PlaybackEntity
import com.cinetrack.data.local.SyncStateEntity
import com.cinetrack.data.local.SyncOperationEntity
import com.cinetrack.data.local.UpNextEntity
import com.cinetrack.data.local.UserMediaStateEntity
import com.cinetrack.data.local.WatchHistoryEntity
import com.cinetrack.data.local.toDomain
import com.cinetrack.data.remote.ApiServices
import com.cinetrack.data.remote.SimklIds
import com.cinetrack.data.remote.SimklLibraryItem
import com.cinetrack.data.remote.SimklPlaybackItem
import com.cinetrack.data.remote.SimklSyncItem
import com.cinetrack.data.remote.SimklSyncRequest
import com.cinetrack.data.schedule.ReleaseScheduleRepository
import com.cinetrack.data.sync.SyncCoordinator
import com.cinetrack.data.sync.SyncOperationRepository
import com.cinetrack.data.sync.DurableSyncOperationWriter
import com.cinetrack.data.sync.TrackingProviderRegistry
import com.cinetrack.data.sync.TrackingProviderId
import com.cinetrack.data.sync.floppy.FloppyTrackingProvider
import com.cinetrack.data.sync.floppy.network.FloppyOAuthRules
import com.cinetrack.data.sync.floppy.FloppySecondaryService
import com.cinetrack.data.sync.TrackingRoutingMutex
import com.cinetrack.data.sync.SyncOperation
import com.cinetrack.data.sync.SyncOperationType
import com.cinetrack.data.sync.logicalField
import com.cinetrack.data.sync.MediaIds
import com.cinetrack.data.sync.SyncReconciler
import com.cinetrack.data.sync.TrackingSnapshot
import com.cinetrack.data.sync.TrackedMovieState
import com.cinetrack.data.sync.TrackedShowState
import com.cinetrack.data.sync.TrackedEpisodeState
import com.cinetrack.data.sync.simkl.ResolvedSimklItem
import com.cinetrack.data.sync.simkl.SimklSyncHost
import com.cinetrack.data.sync.simkl.withDeliveredLibraryState
import com.cinetrack.data.sync.SyncConflict
import com.cinetrack.data.sync.ConflictField
import com.cinetrack.data.sync.MutationGeneration
import com.cinetrack.domain.AppUiState
import com.cinetrack.domain.EpisodeCard
import com.cinetrack.domain.LibraryStatus
import com.cinetrack.domain.FloppyUiState
import com.cinetrack.domain.FloppyConnectionStage
import com.cinetrack.domain.MediaCard
import com.cinetrack.domain.MediaType
import com.cinetrack.domain.PlaybackCard
import com.cinetrack.domain.RailIds
import com.cinetrack.domain.SyncProgress
import com.cinetrack.domain.SyncConflictChoice
import com.cinetrack.domain.SyncOperationCard
import com.cinetrack.domain.SyncOperationStatus
import com.cinetrack.domain.TrackingProviderState
import com.cinetrack.domain.TimelineCard
import com.cinetrack.domain.releaseDateTime
import kotlinx.coroutines.async
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class ProgressRefreshRequest(
    val tvLibraryChanged: Boolean = false,
    val episodeHistoryChanged: Boolean = false,
    val tvPlaybackChanged: Boolean = false,
    val force: Boolean = false,
) {
    fun requiresUpNext(scheduleDue: Boolean, cacheMissing: Boolean): Boolean =
        force || scheduleDue || cacheMissing || tvLibraryChanged || episodeHistoryChanged || tvPlaybackChanged

    fun mergedWith(other: ProgressRefreshRequest) = ProgressRefreshRequest(
        tvLibraryChanged = tvLibraryChanged || other.tvLibraryChanged,
        episodeHistoryChanged = episodeHistoryChanged || other.episodeHistoryChanged,
        tvPlaybackChanged = tvPlaybackChanged || other.tvPlaybackChanged,
        force = force || other.force,
    )
}

/** Canonical activity ordering for a progress card. Air dates are schedule
 * metadata and intentionally never contribute to viewing recency. */
internal fun playbackActivityRecency(item: PlaybackCard): Long = maxOf(
    item.media.libraryUpdatedAt ?: 0L,
    item.progressUpdatedAtMillis,
)

/**
 * The digital release (TMDB release type 4) for [region], as (yyyy-MM-dd, country). Falls back to
 * the US date, which TMDB covers best, when the region has none. The earliest digital date wins.
 */
internal fun digitalReleaseFor(releaseDates: com.cinetrack.data.remote.TmdbReleaseDatesDto?, region: String): Pair<String, String>? {
    fun earliest(country: String) = releaseDates?.results
        ?.firstOrNull { it.country.equals(country, ignoreCase = true) }
        ?.dates?.filter { it.type == 4 && it.date.length >= 10 }
        ?.minOfOrNull { it.date.take(10) }
    return earliest(region)?.let { it to region.uppercase() }
        ?: earliest("US")?.let { it to "US" }
}

internal fun compareProgressAttention(
    left: PlaybackCard,
    right: PlaybackCard,
    latestWatchedAtByShow: Map<Int, Long>,
    nowMillis: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): Int {
    fun key(item: PlaybackCard): Triple<Long, Boolean, Long> {
        val activity = maxOf(latestWatchedAtByShow[item.media.id] ?: 0L, playbackActivityRecency(item))
        val air = if (item.media.type == MediaType.TV) {
            releaseDateTime(item.episodeAirDate, zone)?.toInstant()?.toEpochMilli()
        } else null
        val attentionWindow = UPCOMING_PROGRESS_ATTENTION_DAYS * 86_400_000L
        val upcoming = air?.takeIf { it > nowMillis && it - nowMillis <= attentionWindow }
        // A newly aired selected episode is still meaningful attention for a
        // short window. Once it is older than that window, ordinary viewing
        // activity regains priority. This lets an episode airing now surface
        // without allowing schedule metadata to permanently dominate.
        val recentAir = air?.takeIf { it <= nowMillis && nowMillis - it <= attentionWindow }
        // Viewing newer than the air date wins: a show the user just advanced
        // by hand must not drop below one whose episode merely aired recently.
        val event = upcoming ?: maxOf(recentAir ?: 0L, activity)
        return Triple(kotlin.math.abs(event - nowMillis), upcoming != null, event)
    }
    val a = key(left)
    val b = key(right)
    val distanceDelta = a.first - b.first
    // A near tie between a past viewing event and an imminent future episode
    // favors the upcoming attention signal, without allowing distant air dates
    // to dominate real viewing activity.
    if (kotlin.math.abs(distanceDelta) <= 5 * 60_000L && a.second != b.second) {
        return if (a.second) -1 else 1
    }
    if (distanceDelta != 0L) return distanceDelta.compareTo(0L)
    if (a.second != b.second) return if (a.second) -1 else 1
    val eventDelta = b.third - a.third
    if (eventDelta != 0L) return eventDelta.compareTo(0L)
    return left.media.title.lowercase().compareTo(right.media.title.lowercase())
}

private sealed interface RemoteConflictPlan {
    data class Library(val status: LibraryStatus) : RemoteConflictPlan
    data class MovieWatched(val watched: Boolean) : RemoteConflictPlan
    data class EpisodeWatched(
        val watched: Boolean,
        val season: Int,
        val episode: Int,
    ) : RemoteConflictPlan
}

class CineTrackRepository(
    override val database: AppDatabase,
    override val services: ApiServices,
    override val preferences: AppPreferences,
    private val awaitStartupReady: suspend () -> Unit = {},
    private val onTokenChanged: (String?) -> Unit = {},
    private val currentToken: () -> String? = { null },
    override val tmdbApiKey: () -> String = { BuildConfig.TMDB_API_TOKEN },
    private val mdbListApiKey: () -> String = { BuildConfig.MDBLIST_API_KEY },
    override val syncOperationRepository: SyncOperationRepository,
    private val durableOperationWriter: DurableSyncOperationWriter,
    private val syncCoordinator: SyncCoordinator,
    private val releaseScheduleRepository: ReleaseScheduleRepository,
    private val libraryRepository: LibraryRepository,
    private val mediaMetadata: MediaMetadataRepository,
    override val syncReconciler: SyncReconciler = SyncReconciler(),
    private val trackingProviderRegistry: TrackingProviderRegistry? = null,
    private val trackingRoutingMutex: TrackingRoutingMutex = TrackingRoutingMutex(),
    private val floppySecondaryService: FloppySecondaryService? = null,
    private val trackingConfigurationService: com.cinetrack.data.sync.TrackingConfigurationService? = null,
    private val floppyOAuth: com.cinetrack.data.sync.floppy.network.FloppyOAuthClient? = null,
    private val floppyRetryBootstrap: (suspend () -> Unit)? = null,
    private val floppyUnplacedEpisodes: (suspend () -> List<com.cinetrack.data.sync.floppy.FloppyUnplacedEpisode>)? = null,
    override val episodeNumbering: com.cinetrack.data.sync.numbering.EpisodeNumbering? = null,
    override val animeNumberingRepair: com.cinetrack.data.sync.numbering.AnimeNumberingRepair? = null,
    /** TVDB character pictures through Floppy; null (or a failure) keeps the TMDB-only cast. */
    private val characterImages: (suspend (MediaType, Int) -> List<com.cinetrack.domain.CharacterImage>?)? = null,
    progressCacheProvider: ProgressCacheRepository? = null,
) : SimklSyncHost {
    /** Shows whose local episode numbers were rewritten, so screens can drop cached episode lists. */
    val renumberedShows = kotlinx.coroutines.flow.MutableSharedFlow<Int>(extraBufferCapacity = 64)

    suspend fun connectFloppy(
        baseUrl: String,
        apiKey: String,
        allowInsecureLocalHttp: Boolean? = null,
        onStage: (FloppyConnectionStage, String?) -> Unit = { _, _ -> },
        sameServerAtNewAddress: Boolean? = null,
    ): com.cinetrack.data.sync.ConnectionResult {
        val previousUrl = preferences.floppySettingsNow()?.baseUrl
        val previousCredential = preferences.floppyApiKeyNow()
        // The account may change; Settings fetches the name again.
        preferences.setFloppyUsername(null)
        val result = floppySecondaryService?.connect(baseUrl.trim(), apiKey.trim(), allowInsecureLocalHttp, onStage, sameServerAtNewAddress)
            ?: (trackingProviderRegistry?.getProvider(TrackingProviderId.FLOPPY) as? FloppyTrackingProvider)
                ?.connect(baseUrl.trim(), apiKey.trim(), allowInsecureLocalHttp, onStage)
            ?: com.cinetrack.data.sync.ConnectionResult.AuthenticationRequired
        // Waiting for the user's answer: the fresh sign-in is still needed.
        if (result == com.cinetrack.data.sync.ConnectionResult.AddressMoveNeedsConfirmation) return result
        val active = preferences.floppyApiKeyNow()
        // End sign-ins nothing uses any more: a fresh one that failed to
        // connect, or the one a new connection just replaced.
        if (FloppyOAuthRules.isOAuthCredential(apiKey) && apiKey.trim() != active) {
            floppyOAuth?.revoke(baseUrl.trim(), apiKey.trim())
        }
        if (FloppyOAuthRules.isOAuthCredential(previousCredential) && previousCredential != active && previousUrl != null) {
            floppyOAuth?.revoke(previousUrl, previousCredential!!)
        }
        return result
    }

    /** Ends a sign-in the user abandoned at the "same Floppy at a new address?" question. */
    suspend fun discardFloppyCredential(baseUrl: String, credential: String) {
        if (FloppyOAuthRules.isOAuthCredential(credential) && credential.trim() != preferences.floppyApiKeyNow()) {
            floppyOAuth?.revoke(baseUrl.trim(), credential.trim())
        }
    }

    /** The normalized server URL when it offers sign-in. */
    suspend fun floppySignInServer(baseUrl: String, allowInsecureLocalHttp: Boolean): Result<String> =
        (trackingProviderRegistry?.getProvider(TrackingProviderId.FLOPPY) as? FloppyTrackingProvider)
            ?.signInServer(baseUrl, allowInsecureLocalHttp)
            ?: Result.failure(IllegalStateException("Floppy is unavailable"))

    data class FloppySignIn(val baseUrl: String, val credential: String, val allowInsecureLocalHttp: Boolean)

    /** Swaps the sign-in callback's code for tokens; the caller then connects with [FloppySignIn.credential]. */
    suspend fun completeFloppySignIn(code: String?, state: String?, error: String?, issuer: String?): Result<FloppySignIn> = runCatching {
        val oauth = checkNotNull(floppyOAuth) { "Floppy sign-in is unavailable" }
        val pending = preferences.takeFloppyPendingLogin(state, error, issuer).getOrThrow()
        val tokens = oauth.exchangeCode(pending.baseUrl, checkNotNull(code) { "Floppy did not return a sign-in code" }, pending.codeVerifier)
        val grantId = java.util.UUID.randomUUID().toString()
        oauth.save(grantId, tokens)
        FloppySignIn(pending.baseUrl, FloppyOAuthRules.credential(grantId), pending.allowInsecureLocalHttp)
    }

    suspend fun disconnectFloppy() {
        val previousUrl = preferences.floppySettingsNow()?.baseUrl
        val previousCredential = preferences.floppyApiKeyNow()
        preferences.setFloppyUsername(null)
        floppySecondaryService?.disconnect()
            ?: (trackingProviderRegistry?.getProvider(TrackingProviderId.FLOPPY) as? FloppyTrackingProvider)?.disconnect()
        if (FloppyOAuthRules.isOAuthCredential(previousCredential) && previousUrl != null) {
            floppyOAuth?.revoke(previousUrl, previousCredential!!)
        }
    }
    suspend fun retryFloppyBootstrap() { floppyRetryBootstrap?.invoke() }

    /** Episodes Floppy could not place, with their show titles. */
    suspend fun floppyUnplacedItems(): List<com.cinetrack.domain.FloppyUnplacedItem> =
        floppyUnplacedEpisodes?.invoke().orEmpty().map { episode ->
            com.cinetrack.domain.FloppyUnplacedItem(
                showId = episode.showId,
                showTitle = database.mediaDao().get(MediaType.TV.name, episode.showId)?.title ?: "TMDB ${episode.showId}",
                season = episode.season,
                episode = episode.episode,
                episodeTitle = episode.title,
            )
        }
    suspend fun awaitStartup() {
        awaitStartupReady()
        // Queue repair is idempotent and also runs defensively before every
        // sync, so upgraded installations cannot expose stale writes to UI.
        repairSyncQueue()
    }

    override fun stateOperationId(type: String, id: Int) = "state:$type:$id"
    override fun writeOperationId(id: Long) = "write:$id"

    /** Provider-neutral canonical state used by a SECONDARY bootstrap plan. */
    suspend fun canonicalTrackingSnapshot(): TrackingSnapshot {
        val snapshot = database.snapshotDao().snapshot()
        val states = snapshot.states.associateBy { it.mediaType to it.mediaId }
        val movieHistory = snapshot.history.filter { it.mediaType == MediaType.MOVIE.name }
            .groupBy { it.mediaId }
        val episodeHistory = snapshot.history.filter { it.mediaType == MediaType.TV.name && it.season != null && it.episodeNumber != null }
        val movies = snapshot.media.filter { it.mediaType == MediaType.MOVIE.name }.map { media ->
            val state = states[MediaType.MOVIE.name to media.tmdbId]
            val latest = movieHistory[media.tmdbId].orEmpty().maxByOrNull { it.watchedAt }
            TrackedMovieState(
                ids = MediaIds(tmdb = media.tmdbId.toLong()),
                libraryState = state?.status?.let { runCatching { LibraryStatus.valueOf(it) }.getOrNull() },
                watched = state?.watched == true || latest != null,
                watchedAt = latest?.watchedAt?.let { runCatching { Instant.parse(it) }.getOrNull() },
                updatedAt = state?.updatedAt?.let { Instant.ofEpochMilli(it) },
            )
        }
        val shows = snapshot.media.filter { it.mediaType == MediaType.TV.name }.map { media ->
            val state = states[MediaType.TV.name to media.tmdbId]
            TrackedShowState(
                ids = MediaIds(tmdb = media.tmdbId.toLong()),
                libraryState = state?.status?.let { runCatching { LibraryStatus.valueOf(it) }.getOrNull() },
                updatedAt = state?.updatedAt?.let { Instant.ofEpochMilli(it) },
            )
        }
        val episodes = episodeHistory.map { history ->
            TrackedEpisodeState(
                showIds = MediaIds(tmdb = history.mediaId.toLong()),
                season = history.season!!,
                episode = history.episodeNumber!!,
                watched = true,
                watchedAt = runCatching { Instant.parse(history.watchedAt) }.getOrNull(),
            )
        }.distinctBy { Triple(it.showIds.tmdb, it.season, it.episode) }
        return TrackingSnapshot(movies, shows, episodes, Instant.now(), completeHistory = true)
    }

    private suspend fun persistedGenerationFloor(): Long? {
        val values = buildList {
            addAll(database.stateDao().stateSnapshot().map(UserMediaStateEntity::updatedAt))
            addAll(database.syncDao().syncOperations().map(SyncOperationEntity::createdAt))
            addAll(database.syncDao().allDeliveries().map { it.operationVersion })
            addAll(database.syncDao().pendingWrites().map { it.createdAt })
        }
        return values.maxOrNull()
    }

    private suspend fun nextCausalGenerationLocked(): Long =
        MutationGeneration.next(previous = persistedGenerationFloor())

    override suspend fun nextCausalGeneration(): Long = trackingRoutingMutex.withLock {
        nextCausalGenerationLocked()
    }

    override suspend fun isMainProviderCurrent(provider: TrackingProviderId): Boolean =
        (trackingProviderRegistry?.configuration()?.mainProvider
            ?: preferences.mainTrackingProvider.first()) == provider

    override suspend fun <T> withMainAuthority(
        provider: TrackingProviderId,
        block: suspend () -> T,
    ): T? = trackingRoutingMutex.withLock {
        if (isMainProviderCurrent(provider)) block() else null
    }

    /**
     * Repairs queue rows written by older reconciliation versions. A stale
     * reconcile:* row is never evidence of current local intent. State rows are
     * retained only when their value still matches the current dirty Room row;
     * dirty rows that already equal MAIN are cleaned, while genuinely changed
     * rows are materialized as one canonical state:* operation.
     */
    override suspend fun repairSyncQueue() {
        trackingRoutingMutex.withLock {
            // Materialize legacy rows and capture provider configuration under
            // the same routing lock used by local mutations and role changes.
            syncOperationRepository.backfillLegacyDeliveries()
            val mainProvider = preferences.mainTrackingProvider.first()
            val baseline = mainProvider?.let { preferences.syncBaselineNow(it) }
            val repairs = mutableListOf<SyncOperation>()
            database.withTransaction {
                val states = database.stateDao().stateSnapshot()
                val operations = database.syncDao().syncOperations()
                val media = database.mediaDao().mediaSnapshot().associateBy { "${it.mediaType}:${it.tmdbId}" }
                operations.filter { it.operationId.startsWith("reconcile:") }.forEach { stale ->
                    database.syncDao().deleteDeliveriesForGeneration(stale.operationId, stale.createdAt)
                    database.syncDao().deleteOperationIfGeneration(stale.operationId, stale.createdAt)
                }
                states.forEach { state ->
                    val operationId = stateOperationId(state.mediaType, state.mediaId)
                    val operation = operations.firstOrNull { it.operationId == operationId }
                    val baselineValue = baselineValue(baseline, state.mediaType, state.mediaId)
                    if (!state.dirty) {
                        operation?.let {
                            database.syncDao().deleteDeliveriesForGeneration(it.operationId, it.createdAt)
                            database.syncDao().deleteOperationIfGeneration(it.operationId, it.createdAt)
                        }
                        return@forEach
                    }
                    if (baselineValue != null && baselineValue == state.status) {
                        database.stateDao().upsert(state.copy(dirty = false))
                        operation?.let {
                            database.syncDao().deleteDeliveriesForGeneration(it.operationId, it.createdAt)
                            database.syncDao().deleteOperationIfGeneration(it.operationId, it.createdAt)
                        }
                        return@forEach
                    }
                    val current = operation != null &&
                        operation.status in setOf(SyncOperationStatus.PENDING.name, SyncOperationStatus.FAILED.name) &&
                        operation.localValue == state.status && operation.createdAt == state.updatedAt
                    if (!current) repairs += SyncOperation(
                        id = operationId,
                        type = SyncOperationType.LIBRARY_STATUS,
                        mediaType = runCatching { MediaType.valueOf(state.mediaType) }.getOrElse { return@forEach },
                        mediaId = state.mediaId,
                        title = media["${state.mediaType}:${state.mediaId}"]?.title ?: "${state.mediaType} #${state.mediaId}",
                        value = state.status,
                        sourceVersion = state.updatedAt,
                    )
                }
                database.syncDao().upsert(SyncStateEntity("sync_queue_repair_087", mainProvider?.name, System.currentTimeMillis()))
            }
            // Persist each repaired operation and its immutable target snapshot
            // atomically while the routing lock is still held.
            repairs.forEach { durableOperationWriter.repairCurrentOperationUnlocked(it) }
        }
    }

    override fun baselineValue(baseline: TrackingSnapshot?, mediaType: String, mediaId: Int): String? = when (mediaType) {
        MediaType.MOVIE.name -> baseline?.movies?.firstOrNull { it.ids.tmdb?.toInt() == mediaId }?.libraryState?.name
        MediaType.TV.name -> baseline?.shows?.firstOrNull { it.ids.tmdb?.toInt() == mediaId }?.libraryState?.name
        else -> null
    }

    override fun advanceSyncBaseline(
        previous: TrackingSnapshot?,
        remote: TrackingSnapshot,
        acknowledgedStates: List<UserMediaStateEntity>,
        acknowledgedMovieHistoryOperations: List<SyncOperation>,
        acknowledgedEpisodeWrites: List<PendingWriteEntity>,
        acknowledgedHistoryRemovals: List<PendingWriteEntity>,
        conflicts: List<SyncConflict>,
    ): TrackingSnapshot {
        var movies = remote.movies
        var shows = remote.shows
        var episodes = remote.episodes
        acknowledgedStates.forEach { state ->
            val status = state.status.fromCineTrackStatus()
            if (state.mediaType == MediaType.MOVIE.name) {
            val index = movies.indexOfFirst { it.ids.tmdb?.toInt() == state.mediaId }
                if (index >= 0) movies = movies.toMutableList().also { it[index] = it[index].copy(libraryState = status) }
            } else if (state.mediaType == MediaType.TV.name) {
                val index = shows.indexOfFirst { it.ids.tmdb?.toInt() == state.mediaId }
                if (index >= 0) shows = shows.toMutableList().also { it[index] = it[index].copy(libraryState = status) }
            }
        }
        acknowledgedMovieHistoryOperations.forEach { operation ->
            if (operation.mediaType != MediaType.MOVIE) return@forEach
            val index = movies.indexOfFirst { it.ids.tmdb?.toInt() == operation.mediaId }
            if (index < 0) return@forEach
            val current = movies[index]
            val watched = operation.type == SyncOperationType.MOVIE_WATCHED
            val watchedAt = if (watched) {
                operation.payload?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: current.watchedAt
            } else null
            movies = movies.toMutableList().also {
                it[index] = current.copy(watched = watched, watchedAt = watchedAt)
            }
        }
        acknowledgedEpisodeWrites.forEach { write ->
            val parts = write.payload.split(':', limit = 3)
            val season = parts.getOrNull(0)?.toIntOrNull() ?: return@forEach
            val episode = parts.getOrNull(1)?.toIntOrNull() ?: return@forEach
            val watched = write.operation == "EPISODE_WATCHED"
            val watchedAt = parts.getOrNull(2)?.let { runCatching { Instant.parse(it) }.getOrNull() }
            val index = episodes.indexOfFirst { it.showIds.tmdb?.toInt() == write.mediaId && it.season == season && it.episode == episode }
            val value = TrackedEpisodeState(MediaIds(tmdb = write.mediaId.toLong()), season, episode, watched, watchedAt, watchedAt)
            episodes = if (index >= 0) episodes.toMutableList().also { it[index] = it[index].copy(watched = watched, watchedAt = watchedAt, updatedAt = watchedAt) }
            else if (watched) episodes + value else episodes
        }
        acknowledgedHistoryRemovals.forEach { write ->
            val index = movies.indexOfFirst { it.ids.tmdb?.toInt() == write.mediaId }
            if (index >= 0) movies = movies.toMutableList().also { it[index] = it[index].copy(watched = false, watchedAt = null) }
        }
        conflicts.forEach { conflict ->
            val old = previous ?: return@forEach
            when (conflict.field) {
                ConflictField.LIBRARY_STATUS -> {
                    val value = conflict.baselineValue?.let { runCatching { LibraryStatus.valueOf(it) }.getOrNull() } ?: return@forEach
                    if (conflict.mediaType == MediaType.MOVIE) {
                        val index = movies.indexOfFirst { it.ids.tmdb?.toInt() == conflict.ids.tmdb?.toInt() }
                        if (index >= 0) movies = movies.toMutableList().also { it[index] = it[index].copy(libraryState = value) }
                    } else {
                        val index = shows.indexOfFirst { it.ids.tmdb?.toInt() == conflict.ids.tmdb?.toInt() }
                        if (index >= 0) shows = shows.toMutableList().also { it[index] = it[index].copy(libraryState = value) }
                    }
                }
                ConflictField.WATCHED -> {
                    val index = movies.indexOfFirst { it.ids.tmdb?.toInt() == conflict.ids.tmdb?.toInt() }
                    if (index >= 0) movies = movies.toMutableList().also { it[index] = it[index].copy(watched = old.movies.firstOrNull { movie -> movie.ids.tmdb?.toInt() == conflict.ids.tmdb?.toInt() }?.watched ?: it[index].watched) }
                }
                ConflictField.EPISODE_WATCHED -> {
                    val index = episodes.indexOfFirst { it.showIds.tmdb?.toInt() == conflict.ids.tmdb?.toInt() && it.season == conflict.season && it.episode == conflict.episode }
                    if (index >= 0) episodes = episodes.toMutableList().also { it[index] = it[index].copy(watched = old.episodes.firstOrNull { e -> e.showIds.tmdb?.toInt() == conflict.ids.tmdb?.toInt() && e.season == conflict.season && e.episode == conflict.episode }?.watched ?: it[index].watched) }
                }
            }
        }
        return remote.copy(movies = movies, shows = shows, episodes = episodes)
    }

    override fun mergePulledSnapshot(
        previous: TrackingSnapshot?,
        pulled: TrackingSnapshot,
        completeMovies: Boolean,
        completeShows: Boolean,
    ): TrackingSnapshot {
        if (previous == null) return pulled
        fun <T> merge(
            old: List<T>,
            incoming: List<T>,
            key: (T) -> String,
            complete: Boolean,
        ): List<T> {
            if (complete) return incoming
            val values = old.associateBy(key).toMutableMap()
            incoming.forEach { values[key(it)] = it }
            return values.values.toList()
        }
        val movies = merge(previous.movies, pulled.movies, { it.ids.stableKeys().joinToString("|") }, completeMovies)
        val shows = merge(previous.shows, pulled.shows, { it.ids.stableKeys().joinToString("|") }, completeShows)
        val changedShowIds = pulled.episodes.map { it.showIds.stableKeys().joinToString("|") }.toSet()
        val oldEpisodes = if (completeShows) emptyList() else previous.episodes.filterNot {
            it.showIds.stableKeys().joinToString("|") in changedShowIds
        }
        return pulled.copy(
            movies = movies,
            shows = shows,
            episodes = oldEpisodes + pulled.episodes,
            generatedAt = pulled.generatedAt ?: Instant.now(),
            completeHistory = previous.completeHistory || pulled.completeHistory,
        )
    }

    override suspend fun validatedPendingLocalStates(): List<UserMediaStateEntity> {
        val operations = database.syncDao().syncOperations().associateBy(SyncOperationEntity::operationId)
        return database.stateDao().pendingStates().filter { state ->
            val operation = operations[stateOperationId(state.mediaType, state.mediaId)]
            operation != null &&
                operation.status in setOf(SyncOperationStatus.PENDING.name, SyncOperationStatus.FAILED.name) &&
                operation.localValue == state.status && operation.createdAt == state.updatedAt
        }
    }

    override suspend fun clearObsoleteOperations(
        mediaType: String,
        mediaId: Int,
        operationNames: Set<String>,
        season: Int?,
        episode: Int?,
    ) {
        val operations = database.syncDao().syncOperations().filter { operation ->
            operation.mediaType == mediaType && operation.mediaId == mediaId &&
                operation.operation in operationNames && operation.status != SyncOperationStatus.CONFLICT.name &&
                (season == null || operation.season == season) && (episode == null || operation.episode == episode)
        }
        if (operations.isNotEmpty()) database.syncDao().deleteOperations(operations.map(SyncOperationEntity::operationId))
        val writes = database.syncDao().pendingWrites().filter { write ->
            write.mediaType == mediaType && write.mediaId == mediaId && write.operation in operationNames &&
                (season == null || write.payload.split(':', limit = 3).getOrNull(0)?.toIntOrNull() == season) &&
                (episode == null || write.payload.split(':', limit = 3).getOrNull(1)?.toIntOrNull() == episode)
        }
        if (writes.isNotEmpty()) {
            database.syncDao().deleteWrites(writes.map(PendingWriteEntity::id))
            database.syncDao().deleteOperations(writes.map { "write:${it.id}" })
        }
    }

    override fun syncError(error: Throwable): String =
        if (error is retrofit2.HttpException && error.code() == 429) {
            "Simkl's daily request limit was reached. Your changes are saved and will be sent automatically."
        } else error.message?.takeIf(String::isNotBlank) ?: error::class.java.simpleName
    private suspend fun localZone(): ZoneId {
        val configured = preferences.metadataTimezone.first()
        return if (configured == "system") ZoneId.systemDefault()
        else runCatching { ZoneId.of(configured) }.getOrDefault(ZoneId.systemDefault())
    }

    override suspend fun localToday(): LocalDate = LocalDate.now(localZone())

    private fun scheduleTime(raw: String?): Long = raw?.takeIf(String::isNotBlank)?.let { value ->
        releaseDateTime(value, ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
    } ?: 0L

    private fun playbackTimestamp(raw: String?): Long = raw?.trim()?.takeIf(String::isNotBlank)?.let {
        runCatching { Instant.parse(it).toEpochMilli() }.getOrDefault(0L)
    } ?: 0L

    private val progressCache by lazy {
        progressCacheProvider ?: ProgressCacheRepository(database, preferences, tmdbApiKey, mediaMetadata::loadEpisodes, services, releaseScheduleRepository, mediaMetadata, ::loadCachedState)
    }
    val progressCacheRefreshing: Boolean get() = progressCache.progressCacheRefreshing

    fun simklConnectedNow(): Boolean = !currentToken().isNullOrBlank()

    fun observeLocalChanges() = database.invalidationTracker.createFlow(
        "media",
        "media_rails",
        "user_media_state",
        "playback",
        "watch_history",
        "up_next",
        "sync_state",
        emitInitialState = false,
    )

    /**
     * Swaps MAIN and SECONDARY. The caller has already run a final sync with
     * the current MAIN. When Floppy takes over, its first pass reads the whole
     * library and only adds, so nothing on this device is removed by the swap.
     */
    suspend fun swapMainTracker(): com.cinetrack.data.sync.TrackingConfiguration {
        val service = checkNotNull(trackingConfigurationService) { "Tracker roles can't be changed here" }
        val current = service.current()
        val oldMain = checkNotNull(current.mainProvider) { "Choose a main tracker first" }
        val newMain = checkNotNull(current.secondaryProvider) { "Connect a second tracker first" }
        if (newMain == TrackingProviderId.FLOPPY) {
            preferences.floppySettingsNow()?.connectionId?.takeIf(String::isNotBlank)?.let { connection ->
                database.syncDao().upsert(
                    SyncStateEntity("${com.cinetrack.data.sync.floppy.FloppySyncEngine.AREA_PREFIX}$connection", null, 0L),
                )
            }
        }
        return service.setProviders(newMain, oldMain)
    }

    suspend fun isMainTrackingSyncDue(maxAgeMillis: Long): Boolean {
        val provider = preferences.mainTrackingProvider.first() ?: return false
        val lastCheck = preferences.trackingLastCheckAt(provider)
            ?: (if (provider == TrackingProviderId.SIMKL) database.syncDao().get("all")?.lastSuccessfulSync else null)
        if (lastCheck == null) return true
        return System.currentTimeMillis() - lastCheck >= maxAgeMillis
    }


    suspend fun loadCachedState(): AppUiState = coroutineScope {
        // The database portion is read in one Room transaction. Preferences can
        // still be collected concurrently because they are independent DataStore
        // values and cannot produce a structurally inconsistent media snapshot.
        val snapshotDeferred = async { database.snapshotDao().snapshot() }
        val backgroundSyncDeferred = async { preferences.backgroundSync.first() }
        val wifiOnlyDeferred = async { preferences.wifiOnly.first() }
        val ratingSourcesDeferred = async { preferences.ratingSources.first() }
        val contentRegionsDeferred = async { preferences.contentRegions.first() }
        val uiAccentDeferred = async { preferences.uiAccent.first() }
        val metadataLanguageDeferred = async { preferences.metadataLanguage.first() }
        val metadataRegionDeferred = async { preferences.metadataRegion.first() }
        val metadataTimezoneDeferred = async { preferences.metadataTimezone.first() }
        val syncReportDeferred = async { preferences.syncReportNow() }
        val excludeSpecialsDeferred = async { preferences.excludeSpecials.first() }
        val providerRegionDeferred = async { preferences.providerRegion.first() }
        val preferredProvidersDeferred = async { preferences.preferredProviders.first() }
        val visibleProviderTypesDeferred = async { preferences.visibleProviderTypes.first() }
        val notificationEpisodesDeferred = async { preferences.notificationEpisodes.first() }
        val notificationMoviesDeferred = async { preferences.notificationMovies.first() }
        val notificationSyncDeferred = async { preferences.notificationSync.first() }
        val quietHoursEnabledDeferred = async { preferences.quietHoursEnabled.first() }
        val quietHoursStartDeferred = async { preferences.quietHoursStart.first() }
        val quietHoursEndDeferred = async { preferences.quietHoursEnd.first() }
        val heroLayoutDeferred = async { preferences.heroLayout.first() }
        val discoverLayoutDeferred = async { preferences.discoverLayout.first() }
        val seasonPickerDeferred = async { preferences.seasonPicker.first() }
        val posterAccentDeferred = async { preferences.posterAccent.first() }
        val posterFormatDeferred = async { preferences.posterFormat.first() }
        val posterSizeDeferred = async { preferences.posterSize.first() }
        val cardDensityDeferred = async { preferences.cardDensity.first() }
        val hiddenUpcomingDeferred = async { preferences.hiddenUpcoming.first() }
        val hiddenDiscoveryDeferred = async { preferences.hiddenDiscovery.first() }
        val introductionCompletedDeferred = async { preferences.introductionCompleted.first() }
        val floppySettingsDeferred = async { preferences.floppySettingsNow() }
        val floppyAllowInsecureHttpDeferred = async { preferences.floppyAllowInsecureLocalHttpNow() }
        val providerStatesDeferred = async { buildTrackingProviderStates() }

        val snapshot = snapshotDeferred.await()
        val states = snapshot.states.associateBy { "${it.mediaType}:${it.mediaId}" }
        val mediaByKey = snapshot.media.associateBy { "${it.mediaType}:${it.tmdbId}" }
        val railEntries = snapshot.rails.groupBy { it.railId }
        val cachedEpisodes = snapshot.episodes
        val episodeByNumber = cachedEpisodes.associateBy { Triple(it.showId, it.season, it.number) }
        val railIds = listOf(
            RailIds.TRENDING_TV,
            RailIds.TRENDING_MOVIES,
            RailIds.POPULAR_TV,
            RailIds.POPULAR_MOVIES,
            RailIds.UPCOMING,
            RailIds.RECOMMENDED,
            RailIds.LIBRARY,
        )
        val rails = railIds.associateWith { rail ->
            railEntries[rail].orEmpty().mapNotNull { entry ->
                mediaByKey["${entry.mediaType}:${entry.mediaId}"]?.let { entity ->
                    entity.toDomain(states["${entity.mediaType}:${entity.tmdbId}"])
                }
            }
        }
        val activeLibrary = rails[RailIds.LIBRARY].orEmpty().filter {
            it.status != LibraryStatus.NONE && it.status != LibraryStatus.DROPPED
        }
        val playback = snapshot.playback.mapNotNull { item ->
            val media = mediaByKey["${item.mediaType}:${item.mediaId}"]
                ?.toDomain(states["${item.mediaType}:${item.mediaId}"]) ?: return@mapNotNull null
            val cachedEpisode = if (item.mediaType == MediaType.TV.name && item.season != null && item.episodeNumber != null) {
                episodeByNumber[Triple(item.mediaId, item.season, item.episodeNumber)]
            } else null
            PlaybackCard(
                media = media,
                episodeId = item.episodeId.takeIf { it != 0 } ?: cachedEpisode?.tmdbId,
                episodeLabel = if ((item.season ?: cachedEpisode?.season) != null && (item.episodeNumber ?: cachedEpisode?.number) != null) {
                    "S${(item.season ?: cachedEpisode?.season).toString().padStart(2, '0')} E${(item.episodeNumber ?: cachedEpisode?.number).toString().padStart(2, '0')}"
                } else null,
                episodeTitle = item.episodeTitle?.takeIf(String::isNotBlank) ?: cachedEpisode?.title,
                season = item.season ?: cachedEpisode?.season,
                episodeNumber = item.episodeNumber ?: cachedEpisode?.number,
                progress = item.progress.coerceIn(0f, 1f),
                remainingMinutes = if (item.durationSeconds > item.positionSeconds) {
                    ((item.durationSeconds - item.positionSeconds) / 60).toInt()
                } else media.runtimeMinutes?.let { runtime -> (runtime * (1f - item.progress.coerceIn(0f, 1f))).toInt().coerceAtLeast(0) },
                durationMinutes = item.durationSeconds.takeIf { it > 0 }?.let { (it / 60).toInt().coerceAtLeast(1) }
                    ?: media.runtimeMinutes,
                episodeAirDate = cachedEpisode?.airDate,
                progressUpdatedAtMillis = playbackTimestamp(item.updatedAt),
            )
        }
        val history = snapshot.history
            // Simkl commonly assigns the same watched_at value to every episode
            // in a bulk import.  episodeId is also null for the all-items payload,
            // so the old key collapsed an entire batch into a single row.  Season
            // and episode number are the stable Simkl episode identity.
            .distinctBy {
                "${it.mediaType}:${it.mediaId}:${it.season ?: -1}:${it.episodeNumber ?: -1}:${it.episodeId ?: -1}:${it.watchedAt}"
            }
            .mapNotNull { item ->
            val media = mediaByKey["${item.mediaType}:${item.mediaId}"]
                ?.toDomain(states["${item.mediaType}:${item.mediaId}"]) ?: return@mapNotNull null
            val cachedTitle = if (item.mediaType == MediaType.TV.name && item.season != null && item.episodeNumber != null) {
                episodeByNumber[Triple(item.mediaId, item.season, item.episodeNumber)]?.title
            } else null
            val episodeLabel = if (item.mediaType == MediaType.TV.name && item.season != null && item.episodeNumber != null) {
                "S${item.season.toString().padStart(2, '0')} E${item.episodeNumber.toString().padStart(2, '0')}" +
                    (item.episodeTitle?.takeIf(String::isNotBlank) ?: cachedTitle?.takeIf(String::isNotBlank))?.let { " · $it" }.orEmpty()
            } else null
            TimelineCard(
                media = media,
                label = item.watchedAt.take(10),
                timestamp = item.watchedAt,
                episodeLabel = episodeLabel,
                episodeTitle = item.episodeTitle?.takeIf(String::isNotBlank) ?: cachedTitle,
                episodeId = item.episodeId,
                season = item.season,
                episodeNumber = item.episodeNumber,
            )
        }
        val configuredTimezone = metadataTimezoneDeferred.await()
        val releaseZone = if (configuredTimezone == "system") ZoneId.systemDefault()
        else runCatching { ZoneId.of(configuredTimezone) }.getOrDefault(ZoneId.systemDefault())
        val releaseNow = Instant.now()
        val today = LocalDate.now(releaseZone)
        // Calendar is strictly a tracked-content surface. Build it from the
        // Simkl-backed Library rail so TMDB content-region preferences can never
        // hide an imported or locally tracked release.
        val movieCalendar = activeLibrary.asSequence().filter { it.type == MediaType.MOVIE }.mapNotNull { media ->
            val date = media.releaseDate?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            val releaseDay = releaseDateTime(date, releaseZone)?.toLocalDate() ?: return@mapNotNull null
            // The Calendar is an upcoming schedule, not a release history: titles out
            // before today never reappear here. Today's stay, marked as out.
            if (releaseDay.isBefore(today)) return@mapNotNull null
            TimelineCard(media = media, label = date, timestamp = date)
        }.toList()
        val activeShowsById = activeLibrary.asSequence()
            .filter { it.type == MediaType.TV }
            .associateBy(MediaCard::id)
        // The episode table is the durable schedule snapshot. Rebuild Calendar
        // from it at process start, just as Showly reads its local episode cache,
        // instead of waiting for a new network enrichment pass.
        val excludeSpecials = excludeSpecialsDeferred.await()
        val hiddenUpcoming = hiddenUpcomingDeferred.await()
        val episodeCalendar = cachedEpisodes.mapNotNull { episode ->
            val show = activeShowsById[episode.showId] ?: return@mapNotNull null
            if (excludeSpecials && episode.season == 0) return@mapNotNull null
            if ("${episode.showId}:${episode.season}:${episode.number}" in hiddenUpcoming) return@mapNotNull null
            val date = episode.airDate?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            // The day it airs here, not the day written in the provider's timestamp:
            // "2026-10-14T21:00-04:00" is already the 15th in Europe.
            val airDay = releaseDateTime(date, releaseZone)?.toLocalDate() ?: return@mapNotNull null
            if (airDay.isBefore(today)) return@mapNotNull null
            TimelineCard(
                media = show,
                label = date,
                timestamp = date,
                episodeLabel = "S${episode.season.toString().padStart(2, '0')} E${episode.number.toString().padStart(2, '0')} · ${episode.title}",
                episodeId = episode.tmdbId,
                season = episode.season,
                episodeNumber = episode.number,
                episodeTitle = episode.title,
            )
        }
        val calendar = (movieCalendar + episodeCalendar)
            .distinctBy { "${it.media.stableKey}:${it.season ?: -1}:${it.episodeNumber ?: -1}" }
            .sortedWith(
                compareBy<TimelineCard> {
                    releaseDateTime(it.timestamp, releaseZone)?.toInstant() ?: Instant.MAX
                }
                    .thenBy { it.media.title.lowercase() }
                    .thenBy { it.season ?: -1 }
                    .thenBy { it.episodeNumber ?: -1 },
            )
        val watchedNumbers = history.asSequence().mapNotNull { event ->
            if (event.media.type == MediaType.TV && event.season != null && event.episodeNumber != null) {
                Triple(event.media.id, event.season, event.episodeNumber)
            } else null
        }.toSet()
        val latestWatchedAtByShow = history.asSequence()
            .filter { it.media.type == MediaType.TV }
            .groupBy { it.media.id }
            .mapValues { (_, events) -> events.maxOfOrNull { scheduleTime(it.timestamp) } ?: 0L }
        val playbackByShow = playback.filter { it.media.type == MediaType.TV }.groupBy { it.media.stableKey }
            .mapValues { (_, items) -> items.maxByOrNull(::playbackActivityRecency) }
        val durableUpNext = snapshot.upNext.associateBy(UpNextEntity::showId)
        val cachedEpisodeCards = cachedEpisodes.map { it.toDomain() }
        
        val cachedUpNext = rails[RailIds.LIBRARY].orEmpty()
            .filter { it.type == MediaType.TV && it.status in setOf(LibraryStatus.WATCHING, LibraryStatus.COMPLETED) }
            .mapNotNull { show ->
                val stored = durableUpNext[show.id]?.takeUnless {
                    Triple(show.id, it.season, it.episodeNumber) in watchedNumbers
                }?.let { row ->
                    EpisodeCard(
                        id = row.episodeId ?: 0,
                        showId = row.showId,
                        season = row.season,
                        number = row.episodeNumber,
                        title = row.episodeTitle,
                        overview = "",
                        airDate = row.episodeAirDate,
                        runtimeMinutes = row.durationMinutes,
                    )
                }
                // mapNotNull is intentional: main's Progress invariant is
                // that a tracked show without a concrete next episode is not
                // a Progress card. selectProgressCard retains beta's genuine
                // partial-playback resume behavior.
                selectProgressCard(
                    show = show,
                    episodes = cachedEpisodeCards,
                    watched = watchedNumbers,
                    playbackSession = playbackByShow[show.stableKey],
                    storedNext = stored,
                    now = releaseNow,
                    zone = releaseZone,
                    excludeSpecials = excludeSpecials,
                )
            }
            // Latest viewing first, so a show advanced locally stays on top
            // without waiting for a sync to rewrite the library rail order.
            .sortedWith { left, right ->
                compareProgressAttention(left, right, latestWatchedAtByShow, releaseNow.toEpochMilli(), releaseZone)
            }
        val moviePlayback = playback.filter { it.media.type == MediaType.MOVIE }.toMutableList()
        val movieKeys = moviePlayback.map { it.media.stableKey }.toSet()
        moviePlayback += rails[RailIds.LIBRARY].orEmpty()
            .filter { it.type == MediaType.MOVIE && it.status == LibraryStatus.WATCHING && it.stableKey !in movieKeys }
            .map { media -> PlaybackCard(media = media, progress = 0f, remainingMinutes = media.runtimeMinutes, durationMinutes = media.runtimeMinutes) }
        moviePlayback.sortWith(compareByDescending<PlaybackCard>(::playbackActivityRecency).thenBy { it.media.title.lowercase() })

        AppUiState(
            rails = rails,
            playbackTv = cachedUpNext,
            playbackMovies = moviePlayback,
            history = history,
            calendar = calendar,
            episodes = cachedEpisodes.map { episode ->
                episode.toDomain().copy(
                    watched = Triple(episode.showId, episode.season, episode.number) in watchedNumbers,
                )
            },
            sync = SyncProgress(
                lastSuccessfulSync = snapshot.syncStates.firstOrNull { it.area == "all" }?.lastSuccessfulSync,
                report = syncReportDeferred.await(),
            ),
            simklConnected = simklConnectedNow(),
            floppyConnected = floppySettingsDeferred.await() != null && preferences.floppyApiKeyNow() != null,
            floppyServerVersion = floppySettingsDeferred.await()?.serverVersion,
            floppyBaseUrl = floppySettingsDeferred.await()?.baseUrl,
            floppyConnectionId = floppySettingsDeferred.await()?.connectionId,
            floppyAllowInsecureLocalHttp = floppyAllowInsecureHttpDeferred.await(),
            floppyUiState = providerStatesDeferred.await().firstOrNull { it.providerId == TrackingProviderId.FLOPPY.name }
                .toFloppyUiState(floppySettingsDeferred.await() != null && preferences.floppyApiKeyNow() != null),
            trackingProviders = providerStatesDeferred.await(),
            backgroundSync = backgroundSyncDeferred.await(),
            wifiOnly = wifiOnlyDeferred.await(),
            notificationEpisodes = notificationEpisodesDeferred.await(),
            notificationMovies = notificationMoviesDeferred.await(),
            notificationSync = notificationSyncDeferred.await(),
            quietHoursEnabled = quietHoursEnabledDeferred.await(),
            quietHoursStart = quietHoursStartDeferred.await(),
            quietHoursEnd = quietHoursEndDeferred.await(),
            ratingSources = ratingSourcesDeferred.await(),
            contentRegions = contentRegionsDeferred.await(),
            uiAccent = uiAccentDeferred.await(),
            tmdbApiConfigured = tmdbApiKey().isNotBlank(),
            mdbListApiConfigured = mdbListApiKey().isNotBlank(),
            traktConfigured = preferences.traktClientIdNow().isNotBlank(),
            metadataLanguage = metadataLanguageDeferred.await(),
            metadataRegion = metadataRegionDeferred.await(),
            providerRegion = providerRegionDeferred.await(),
            metadataTimezone = metadataTimezoneDeferred.await(),
            airDateSource = preferences.airDateSource.first(),
            simklUsername = preferences.simklUsername.first(),
            excludeSpecials = excludeSpecials,
            preferredProviders = preferredProvidersDeferred.await(),
            visibleProviderTypes = visibleProviderTypesDeferred.await(),
            heroLayout = heroLayoutDeferred.await(),
            discoverLayout = discoverLayoutDeferred.await(),
            seasonPicker = seasonPickerDeferred.await(),
            posterAccent = posterAccentDeferred.await(),
            posterFormat = posterFormatDeferred.await(),
            posterSize = posterSizeDeferred.await(),
            cardDensity = cardDensityDeferred.await(),
            hiddenUpcoming = hiddenUpcoming,
            hiddenDiscovery = hiddenDiscoveryDeferred.await(),
            introductionCompleted = introductionCompletedDeferred.await(),
            loading = false,
        )
    }

    private suspend fun buildTrackingProviderStates(): List<TrackingProviderState> {
        val registry = trackingProviderRegistry ?: return emptyList()
        val configuration = registry.configuration()
        return TrackingProviderId.entries.map { id ->
            val provider = registry.getProvider(id)
            val authenticated = provider?.let { runCatching { it.isAuthenticated() }.getOrDefault(false) } == true
            val role = when (id) {
                configuration.mainProvider -> "MAIN"
                configuration.secondaryProvider -> "SECONDARY"
                else -> "NONE"
            }
            TrackingProviderState(
                providerId = id.name,
                role = role,
                configured = id == configuration.mainProvider || id == configuration.secondaryProvider,
                authenticated = authenticated,
                connectionStatus = when {
                    provider == null -> "UNAVAILABLE"
                    authenticated -> "CONNECTED"
                    else -> "DISCONNECTED"
                },
                lastSuccessfulSync = preferences.trackingLastCheckAt(id),
                twoWayCapable = provider?.let { runCatching { it.currentCapabilities().supportsTwoWaySync }.getOrDefault(false) } == true,
                bootstrapState = preferences.providerBootstrapStateNow(id).name,
            )
        }
    }

    private fun TrackingProviderState?.toFloppyUiState(connected: Boolean): FloppyUiState = when {
        !connected -> FloppyUiState.NOT_CONNECTED
        this?.role == "MAIN" -> FloppyUiState.READY
        this?.role != "SECONDARY" -> FloppyUiState.CONNECTED_INACTIVE
        this.bootstrapState == "READY" -> FloppyUiState.READY
        this.bootstrapState == "FAILED" -> FloppyUiState.NEEDS_ATTENTION
        else -> FloppyUiState.SETTING_UP
    }


    override suspend fun pushLibraryState(state: UserMediaStateEntity) {
        val targetStatus = state.status.toSimklStatus()
        val ids = SimklIds(simkl = state.simklId, tmdb = state.mediaId.toString())
        if (state.status == LibraryStatus.NONE.name) {
            // With no season/episode granularity, history/remove deletes the
            // item from the Simkl library as well as its watched history.
            val request = state.syncRequest(SimklSyncItem(ids = ids))
            val response = services.simklSync.removeHistory(request)
            val unmatched = if (state.mediaType == MediaType.MOVIE.name) {
                response.notFound.movies
            } else {
                response.notFound.shows
            }
            check(unmatched.isEmpty()) { "Simkl could not match the item being removed" }
        } else {
            // Library status is its own logical field. Movie/episode history
            // operations are dispatched independently by pushOperation; this
            // adapter call changes only Simkl's library membership/status.
            services.simklSync.addToList(state.syncRequest(SimklSyncItem(ids = ids, to = targetStatus)))
        }
        recordDeliveredLibraryState(state)
    }

    /**
     * Simkl now holds this status, so it is also the last state both sides
     * agree on. Without this, a later sync that downloads only changes (a
     * history removal is not a list change for Simkl) compared against the
     * old value and put a removed or changed show back.
     */
    private suspend fun recordDeliveredLibraryState(state: UserMediaStateEntity) {
        val baseline = preferences.syncBaselineNow(TrackingProviderId.SIMKL) ?: return
        val status = runCatching { LibraryStatus.valueOf(state.status) }.getOrDefault(LibraryStatus.NONE)
        val type = if (state.mediaType == MediaType.MOVIE.name) MediaType.MOVIE else MediaType.TV
        preferences.saveSyncBaseline(
            withDeliveredLibraryState(baseline, type, state.mediaId.toLong(), status, state.watched),
            TrackingProviderId.SIMKL,
        )
    }

    override suspend fun pushOperation(operation: SyncOperation) {
        when (operation.type) {
            SyncOperationType.LIBRARY_STATUS -> {
                val state = database.stateDao().get(operation.mediaType.name, operation.mediaId)
                    ?: error("Local state is no longer available")
                require(state.updatedAt == operation.sourceVersion && state.status == operation.value) {
                    "Local library generation changed before upload"
                }
                pushLibraryState(state)
            }
            SyncOperationType.MOVIE_WATCHED,
            SyncOperationType.MOVIE_UNWATCHED -> {
                require(operation.mediaType == MediaType.MOVIE) { "Movie history operation must target a movie" }
                val watched = operation.type == SyncOperationType.MOVIE_WATCHED
                val state = database.stateDao().get(MediaType.MOVIE.name, operation.mediaId)
                    ?: error("Local movie state is no longer available")
                require(state.updatedAt == operation.sourceVersion && state.watched == watched) {
                    "Local movie history generation changed before upload"
                }
                val watchedAt = if (watched) {
                    operation.payload?.let { runCatching { Instant.parse(it) }.getOrNull() }?.toString()
                        ?: Instant.ofEpochMilli(operation.sourceVersion).toString()
                } else null
                val request = SimklSyncRequest(
                    movies = listOf(
                        SimklSyncItem(
                            ids = SimklIds(tmdb = operation.mediaId.toString()),
                            watchedAt = watchedAt,
                        ),
                    ),
                )
                if (watched) services.simklSync.addHistory(request)
                else {
                    services.simklSync.removeHistory(request)
                    // Simkl may couple history removal with watchlist
                    // membership. Re-apply the canonical library field so a
                    // MOVIE_UNWATCHED retry remains semantically complete on
                    // its own.
                    if (state.status != LibraryStatus.NONE.name) {
                        services.simklSync.addToList(
                            state.syncRequest(
                                SimklSyncItem(
                                    ids = SimklIds(simkl = state.simklId, tmdb = operation.mediaId.toString()),
                                    to = state.status.toSimklStatus(),
                                ),
                            ),
                        )
                    }
                }
            }
            SyncOperationType.EPISODE_WATCHED,
            SyncOperationType.EPISODE_UNWATCHED -> {
                require(operation.mediaType == MediaType.TV) { "Episode history operation must target a show" }
                val write = operation.id.removePrefix("write:").toLongOrNull()
                    ?.let { database.syncDao().pendingWrite(it) }
                if (write != null) {
                    require(write.createdAt == operation.sourceVersion && write.operation == operation.type.name) {
                        "Episode write generation changed before upload"
                    }
                    pushEpisodeWrite(write)
                } else {
                    val parts = operation.payload.orEmpty().split(':', limit = 3)
                    val season = parts.getOrNull(0)?.toIntOrNull() ?: error("Invalid queued season")
                    val episode = parts.getOrNull(1)?.toIntOrNull() ?: error("Invalid queued episode")
                    val watchedAt = parts.getOrNull(2)?.let { runCatching { Instant.parse(it) }.getOrNull() }?.toString()
                    val request = SimklSyncRequest(
                        shows = listOf(
                            SimklSyncItem(
                                ids = SimklIds(tmdb = operation.mediaId.toString()),
                                seasons = listOf(
                                    com.cinetrack.data.remote.SimklSeason(
                                        season,
                                        listOf(com.cinetrack.data.remote.SimklEpisode(episode, watchedAt.takeIf { operation.type == SyncOperationType.EPISODE_WATCHED })),
                                    ),
                                ),
                            ),
                        ),
                    )
                    if (operation.type == SyncOperationType.EPISODE_WATCHED) services.simklSync.addHistory(request)
                    else services.simklSync.removeHistory(request)
                }
            }
            SyncOperationType.MEDIA_HISTORY_REMOVE -> {
                val write = operation.id.removePrefix("write:").toLongOrNull()
                    ?.let { database.syncDao().pendingWrite(it) }
                if (write != null) {
                    require(write.createdAt == operation.sourceVersion && write.operation == operation.type.name) {
                        "History-removal generation changed before upload"
                    }
                    pushMediaHistoryRemoval(write)
                } else {
                    val ids = SimklIds(simkl = operation.payload?.toLongOrNull(), tmdb = operation.mediaId.toString())
                    val item = SimklSyncItem(ids = ids)
                    val request = if (operation.mediaType == MediaType.MOVIE) {
                        SimklSyncRequest(movies = listOf(item))
                    } else {
                        SimklSyncRequest(shows = listOf(item))
                    }
                    services.simklSync.removeHistory(request)
                }
            }
            SyncOperationType.SET_RATING -> throw com.cinetrack.data.sync.TrackingSyncError.UnsupportedOperation(TrackingProviderId.SIMKL, operation.type)
        }
    }

    override suspend fun enqueueSecondaryMirror(operation: SyncOperation, mainProvider: TrackingProviderId) {
        // The writer captures SECONDARY once and performs supersession plus
        // replacement in one Room transaction. Re-read the canonical field
        // while its routing lock is held so an intervening local edit owns the
        // normal MAIN+SECONDARY path and cannot be superseded by this mirror.
        durableOperationWriter.replaceSecondaryMirror(operation) {
            if (!isMainProviderCurrent(mainProvider)) return@replaceSecondaryMirror false
            val field = operation.logicalField()
            if (field == null) {
                false
            } else {
                var newerLocalIntent = false
                for (entity in database.syncDao().syncOperations()) {
                    if (entity.logicalField() != field || entity.operationId == operation.id ||
                        entity.status !in setOf(SyncOperationStatus.PENDING.name, SyncOperationStatus.FAILED.name) ||
                        entity.createdAt <= operation.sourceVersion
                    ) continue
                    if (syncOperationRepository.currentIntentTargetsProvider(
                            entity.operationId,
                            entity.createdAt,
                            mainProvider,
                        )) {
                        newerLocalIntent = true
                        break
                    }
                }
                if (newerLocalIntent) {
                    false
                } else {
                    when (operation.type) {
                        SyncOperationType.LIBRARY_STATUS ->
                            database.stateDao().get(operation.mediaType.name, operation.mediaId)?.status == operation.value
                        SyncOperationType.MOVIE_WATCHED ->
                            database.stateDao().get(MediaType.MOVIE.name, operation.mediaId)?.watched == true
                        SyncOperationType.MOVIE_UNWATCHED,
                        SyncOperationType.MEDIA_HISTORY_REMOVE ->
                            database.stateDao().get(MediaType.MOVIE.name, operation.mediaId)?.watched == false
                        SyncOperationType.EPISODE_WATCHED,
                        SyncOperationType.EPISODE_UNWATCHED -> {
                            val parts = operation.payload.orEmpty().split(':', limit = 3)
                            val season = parts.getOrNull(0)?.toIntOrNull()
                            val episode = parts.getOrNull(1)?.toIntOrNull()
                            season != null && episode != null &&
                                (database.timelineDao().episodeHistoryForShow(MediaType.TV.name, operation.mediaId)
                                    .any { it.season == season && it.episodeNumber == episode } == (operation.type == SyncOperationType.EPISODE_WATCHED))
                        }
                        SyncOperationType.SET_RATING -> true
                    }
                }
            }
        }
    }

    private fun UserMediaStateEntity.syncRequest(item: SimklSyncItem): SimklSyncRequest =
        if (mediaType == MediaType.MOVIE.name) SimklSyncRequest(movies = listOf(item))
        else SimklSyncRequest(shows = listOf(item))

    override suspend fun pushMediaHistoryRemoval(write: PendingWriteEntity) {
        val simklId = write.payload.toLongOrNull()
        val ids = SimklIds(simkl = simklId, tmdb = write.mediaId.toString())
        val request = if (write.mediaType == MediaType.MOVIE.name) {
            SimklSyncRequest(movies = listOf(SimklSyncItem(ids = ids)))
        } else {
            SimklSyncRequest(shows = listOf(SimklSyncItem(ids = ids)))
        }
        services.simklSync.removeHistory(request)
    }

    override suspend fun pushEpisodeWrite(write: PendingWriteEntity) {
        val parts = write.payload.split(':', limit = 3)
        val season = parts.getOrNull(0)?.toIntOrNull() ?: error("Invalid queued season")
        val episode = parts.getOrNull(1)?.toIntOrNull() ?: error("Invalid queued episode")
        val watchedAt = parts.getOrNull(2) ?: Instant.now().toString()
        // The queued coordinate is local (TMDB) numbering; Simkl may count anime differently.
        val target = episodeNumbering?.toSimkl(write.mediaId, season, episode)
            ?: com.cinetrack.data.sync.numbering.SimklTarget.Unchanged
        val request = SimklSyncRequest(
            shows = com.cinetrack.data.sync.simkl.simklShowItems(
                SimklIds(tmdb = write.mediaId.toString()),
                listOf(
                    com.cinetrack.data.sync.simkl.SimklEpisodePlay(
                        season, episode, watchedAt.takeIf { write.operation == "EPISODE_WATCHED" }, target,
                    ),
                ),
            ),
        )
        if (write.operation == "EPISODE_WATCHED") services.simklSync.addHistory(request)
        else services.simklSync.removeHistory(request)
    }

    /** Returns every durable local write, including rows created by older app versions. */
    suspend fun loadSyncOperations(): List<SyncOperationCard> = syncOperationRepository.cards()

    suspend fun loadManagedBootstrapSummary() = syncOperationRepository.managedBootstrapSummary()

    suspend fun retrySyncOperation(operationId: String): Result<Unit> = syncCoordinator.retry(operationId)

    suspend fun resolveSyncConflict(operationId: String, choice: SyncConflictChoice): Result<Unit> =
        if (choice == SyncConflictChoice.KEEP_LOCAL) {
            resolveKeepLocalConflict(operationId)
        } else {
            resolveUseRemoteConflict(operationId)
        }

    private suspend fun resolveKeepLocalConflict(operationId: String): Result<Unit> = try {
        val operation = durableOperationWriter.enqueueFromCurrentState(
            removeConflictId = operationId,
        ) {
            val conflict = database.syncDao().syncOperation(operationId)
                ?: error("Conflict no longer exists")
            require(conflict.status == SyncOperationStatus.CONFLICT.name) {
                "Operation is not a conflict"
            }
            buildKeepLocalOperation(conflict)
        }
        // Network work starts only after the routing lock has been released.
        syncCoordinator.pushPending(setOf(operation.id)).getOrThrow()
        Result.success(Unit)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        Result.failure(error)
    }

    private suspend fun buildKeepLocalOperation(conflict: SyncOperationEntity): SyncOperation {
        val mediaType = runCatching { MediaType.valueOf(conflict.mediaType) }
            .getOrElse { error("Conflict media type is invalid") }
        return when (conflict.operation) {
            "LIBRARY_STATUS_CONFLICT", "LIBRARY_CONFLICT" -> {
                val state = database.stateDao().get(conflict.mediaType, conflict.mediaId)
                    ?: error("Local state is no longer available")
                SyncOperation(
                    id = stateOperationId(mediaType.name, state.mediaId),
                    type = SyncOperationType.LIBRARY_STATUS,
                    mediaType = mediaType,
                    mediaId = state.mediaId,
                    title = conflict.title,
                    value = state.status,
                    sourceVersion = state.updatedAt,
                )
            }
            "WATCHED_CONFLICT" -> {
                val state = database.stateDao().get(conflict.mediaType, conflict.mediaId)
                    ?: error("Local state is no longer available")
                val existing = database.syncDao().syncOperations()
                    .asSequence()
                    .filter { it.status in setOf(SyncOperationStatus.PENDING.name, SyncOperationStatus.FAILED.name) }
                    .filter { it.mediaType == mediaType.name && it.mediaId == state.mediaId }
                    .filter { it.operation in setOf(SyncOperationType.MOVIE_WATCHED.name, SyncOperationType.MOVIE_UNWATCHED.name) }
                    .filter { (it.operation == SyncOperationType.MOVIE_WATCHED.name) == state.watched }
                    .maxByOrNull(SyncOperationEntity::createdAt)
                    ?.let { it.toDomainOperation() }
                existing ?: SyncOperation(
                    id = "resolution:${conflict.operationId}",
                    type = if (state.watched) SyncOperationType.MOVIE_WATCHED else SyncOperationType.MOVIE_UNWATCHED,
                    mediaType = mediaType,
                    mediaId = state.mediaId,
                    title = conflict.title,
                    value = state.watched.toString(),
                    payload = if (state.watched) {
                        database.timelineDao().historySnapshot()
                            .asSequence()
                            .filter { it.mediaType == mediaType.name && it.mediaId == state.mediaId && it.season == null && it.episodeNumber == null }
                            .mapNotNull { it.watchedAt.toInstantOrNull() }
                            .maxOrNull()
                            ?.toString()
                    } else null,
                    sourceVersion = state.updatedAt,
                )
            }
            "EPISODE_WATCHED_CONFLICT" -> buildKeepLocalEpisodeOperation(conflict, mediaType)
            else -> error("Unsupported conflict field")
        }
    }

    private suspend fun buildKeepLocalEpisodeOperation(
        conflict: SyncOperationEntity,
        mediaType: MediaType,
    ): SyncOperation {
        require(mediaType == MediaType.TV) { "Episode conflicts must target TV media" }
        val season = conflict.season ?: error("Episode conflict season is missing")
        val episode = conflict.episode ?: error("Episode conflict number is missing")
        val title = episodeDisplayTitle(conflict.mediaId, season, episode, conflict.title)
        val exactWrite = database.syncDao().pendingWrites()
            .asSequence()
            .filter { it.mediaType == MediaType.TV.name && it.mediaId == conflict.mediaId }
            .filter { it.operation in setOf(SyncOperationType.EPISODE_WATCHED.name, SyncOperationType.EPISODE_UNWATCHED.name) }
            .filter { it.episodeParts() == (season to episode) }
            .maxByOrNull(PendingWriteEntity::createdAt)
        if (exactWrite != null) return exactWrite.toEpisodeOperation(title)
            ?: error("Episode write operation is invalid")

        val exactOperation = database.syncDao().syncOperations()
            .asSequence()
            .filter { it.status in setOf(SyncOperationStatus.PENDING.name, SyncOperationStatus.FAILED.name) }
            .filter { it.mediaType == MediaType.TV.name && it.mediaId == conflict.mediaId }
            .filter { it.season == season && it.episode == episode }
            .filter { it.operation in setOf(SyncOperationType.EPISODE_WATCHED.name, SyncOperationType.EPISODE_UNWATCHED.name) }
            .maxByOrNull(SyncOperationEntity::createdAt)
        if (exactOperation != null) return exactOperation.toDomainOperation()
            ?: error("Episode operation is invalid")

        val history = database.timelineDao().episodeHistoryForShow(MediaType.TV.name, conflict.mediaId)
        val matchingHistory = history.firstOrNull { it.season == season && it.episodeNumber == episode }
        val watched = matchingHistory != null ||
            (matchingHistory == null && conflict.localValue?.toBooleanStrictOrNull() == true)
        val watchedAt = matchingHistory?.watchedAt
            ?: Instant.now().toString()
        // History timestamps are provider event times, not local queue
        // generations. Allocate a fresh causal generation while the caller
        // still holds TrackingRoutingMutex.
        val sourceVersion = nextCausalGenerationLocked()
        val operationType = if (watched) SyncOperationType.EPISODE_WATCHED else SyncOperationType.EPISODE_UNWATCHED
        return SyncOperation(
            id = "resolution:${conflict.operationId}",
            type = operationType,
            mediaType = MediaType.TV,
            mediaId = conflict.mediaId,
            title = title,
            value = watched.toString(),
            payload = if (watched) "$season:$episode:$watchedAt" else "$season:$episode",
            sourceVersion = sourceVersion,
        )
    }

    private suspend fun resolveUseRemoteConflict(operationId: String): Result<Unit> {
        var mirrorOperation: SyncOperation? = null
        val result = trackingRoutingMutex.withLock {
            try {
        val initial = database.syncDao().syncOperation(operationId)
            ?: error("Conflict no longer exists")
        require(initial.status == SyncOperationStatus.CONFLICT.name) { "Operation is not a conflict" }
        val plan = validateRemoteConflict(initial)
        database.withTransaction {
            val conflict = database.syncDao().syncOperation(operationId)
                ?: error("Conflict no longer exists")
            require(conflict.status == SyncOperationStatus.CONFLICT.name) { "Operation is not a conflict" }
            require(
                conflict.operation == initial.operation &&
                    conflict.mediaType == initial.mediaType &&
                    conflict.mediaId == initial.mediaId &&
                    conflict.localValue == initial.localValue &&
                    conflict.remoteValue == initial.remoteValue &&
                    conflict.season == initial.season &&
                    conflict.episode == initial.episode,
            ) { "Conflict changed while resolving" }
            validateConflictGeneration(conflict, plan)
            val mirrorGeneration = nextCausalGenerationLocked()
            val previous = database.stateDao().get(conflict.mediaType, conflict.mediaId)
            mirrorOperation = when (plan) {
                is RemoteConflictPlan.Library -> SyncOperation(
                    id = "mirror:library:${conflict.mediaType}:${conflict.mediaId}",
                    type = SyncOperationType.LIBRARY_STATUS,
                    mediaType = MediaType.valueOf(conflict.mediaType),
                    mediaId = conflict.mediaId,
                    title = conflict.title.orEmpty(),
                    value = plan.status.name,
                    payload = if (plan.status == LibraryStatus.COMPLETED) Instant.ofEpochMilli(conflict.updatedAt).toString() else null,
                    sourceVersion = mirrorGeneration,
                )
                is RemoteConflictPlan.MovieWatched -> SyncOperation(
                    id = "mirror:movie-watched:${conflict.mediaId}",
                    type = if (plan.watched) SyncOperationType.MOVIE_WATCHED else SyncOperationType.MOVIE_UNWATCHED,
                    mediaType = MediaType.MOVIE,
                    mediaId = conflict.mediaId,
                    title = conflict.title.orEmpty(),
                    value = plan.watched.toString(),
                    payload = if (plan.watched) {
                        Instant.ofEpochMilli(conflict.updatedAt).toString()
                    } else {
                        previous?.status
                    },
                    sourceVersion = mirrorGeneration,
                )
                is RemoteConflictPlan.EpisodeWatched -> SyncOperation(
                    id = "mirror:episode-watched:${conflict.mediaId}:${plan.season}:${plan.episode}",
                    type = if (plan.watched) SyncOperationType.EPISODE_WATCHED else SyncOperationType.EPISODE_UNWATCHED,
                    mediaType = MediaType.TV,
                    mediaId = conflict.mediaId,
                    title = conflict.title.orEmpty(),
                    value = plan.watched.toString(),
                    payload = if (plan.watched) "${plan.season}:${plan.episode}:${Instant.ofEpochMilli(conflict.updatedAt)}" else "${plan.season}:${plan.episode}",
                    sourceVersion = mirrorGeneration,
                )
            }
            when (plan) {
                is RemoteConflictPlan.Library -> {
                    database.stateDao().upsert(
                        UserMediaStateEntity(
                            conflict.mediaType,
                            conflict.mediaId,
                            plan.status.name,
                            previous?.watched ?: false,
                            previous?.simklId,
                            System.currentTimeMillis(),
                            dirty = false,
                        ),
                    )
                    if (plan.status == LibraryStatus.NONE) {
                        database.timelineDao().deleteMediaHistory(conflict.mediaType, conflict.mediaId)
                    }
                    removeExactConflictIntent(conflict, plan)
                }
                is RemoteConflictPlan.MovieWatched -> {
                    if (plan.watched) {
                        database.timelineDao().deleteMediaHistory(conflict.mediaType, conflict.mediaId)
                        database.timelineDao().insertHistory(
                            WatchHistoryEntity(
                                mediaType = conflict.mediaType,
                                mediaId = conflict.mediaId,
                                watchedAt = Instant.ofEpochMilli(conflict.updatedAt).toString(),
                            ),
                        )
                    } else {
                        database.timelineDao().deleteMediaHistory(conflict.mediaType, conflict.mediaId)
                    }
                    database.stateDao().upsert(
                        UserMediaStateEntity(
                            conflict.mediaType,
                            conflict.mediaId,
                            previous?.status ?: LibraryStatus.NONE.name,
                            plan.watched,
                            previous?.simklId,
                            System.currentTimeMillis(),
                            dirty = false,
                        ),
                    )
                    removeExactConflictIntent(conflict, plan)
                }
                is RemoteConflictPlan.EpisodeWatched -> {
                    if (plan.watched) {
                        database.timelineDao().deleteEpisodeHistory(MediaType.TV.name, conflict.mediaId, plan.season, plan.episode)
                        database.timelineDao().insertHistory(
                            WatchHistoryEntity(
                                mediaType = MediaType.TV.name,
                                mediaId = conflict.mediaId,
                                season = plan.season,
                                episodeNumber = plan.episode,
                                watchedAt = Instant.ofEpochMilli(conflict.updatedAt).toString(),
                            ),
                        )
                    } else {
                        database.timelineDao().deleteEpisodeHistory(MediaType.TV.name, conflict.mediaId, plan.season, plan.episode)
                    }
                    removeExactConflictIntent(conflict, plan)
                }
            }
            rebuildLibraryRailInTransaction()
            database.syncDao().deleteOperation(operationId)
        }
        Result.success(Unit)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        Result.failure(error)
        }
    }
        if (result.isSuccess) mirrorOperation?.let { enqueueSecondaryMirror(it) }
        return result
    }

    private suspend fun validateRemoteConflict(conflict: SyncOperationEntity): RemoteConflictPlan {
        val mediaType = runCatching { MediaType.valueOf(conflict.mediaType) }
            .getOrElse { error("Conflict media type is invalid") }
        require(database.mediaDao().get(conflict.mediaType, conflict.mediaId) != null) {
            "Media is no longer available"
        }
        return when (conflict.operation) {
            "LIBRARY_STATUS_CONFLICT", "LIBRARY_CONFLICT" -> RemoteConflictPlan.Library(
                conflict.remoteValue?.let { runCatching { LibraryStatus.valueOf(it) }.getOrNull() }
                    ?: error("Remote library status is invalid"),
            )
            "WATCHED_CONFLICT" -> {
                require(mediaType == MediaType.MOVIE) { "Movie watched conflicts must target movies" }
                require(conflict.localValue?.toBooleanStrictOrNull() != null) { "Local watched value is invalid" }
                RemoteConflictPlan.MovieWatched(conflict.remoteValue?.toBooleanStrictOrNull()
                    ?: error("Remote watched value is invalid"))
            }
            "EPISODE_WATCHED_CONFLICT" -> {
                require(mediaType == MediaType.TV) { "Episode conflicts must target TV media" }
                require(conflict.localValue?.toBooleanStrictOrNull() != null) { "Local watched value is invalid" }
                RemoteConflictPlan.EpisodeWatched(
                    watched = conflict.remoteValue?.toBooleanStrictOrNull()
                        ?: error("Remote watched value is invalid"),
                    season = conflict.season ?: error("Episode conflict season is missing"),
                    episode = conflict.episode ?: error("Episode conflict number is missing"),
                )
            }
            else -> error("Unsupported conflict field")
        }
    }

    private suspend fun validateConflictGeneration(
        conflict: SyncOperationEntity,
        plan: RemoteConflictPlan,
    ) {
        val local = conflict.localValue?.toBooleanStrictOrNull()
        when (plan) {
            is RemoteConflictPlan.Library -> {
                val state = database.stateDao().get(conflict.mediaType, conflict.mediaId)
                require(state != null && state.updatedAt == conflict.createdAt && state.status == conflict.localValue) {
                    "Conflict changed while resolving"
                }
            }
            is RemoteConflictPlan.MovieWatched -> {
                val state = database.stateDao().get(conflict.mediaType, conflict.mediaId)
                require(state != null && state.updatedAt == conflict.createdAt && state.watched == local) {
                    "Conflict changed while resolving"
                }
                val matchingOperation = database.syncDao().syncOperations().any { operation ->
                    operation.status != SyncOperationStatus.CONFLICT.name &&
                        operation.mediaType == MediaType.MOVIE.name &&
                        operation.mediaId == conflict.mediaId &&
                        operation.createdAt == conflict.createdAt &&
                        operation.operation in setOf(SyncOperationType.MOVIE_WATCHED.name, SyncOperationType.MOVIE_UNWATCHED.name)
                }
                // A movie watched value may be coupled to state:COMPLETED;
                // absence of a standalone row is valid in that legacy shape.
                require(matchingOperation || state?.status == LibraryStatus.COMPLETED.name || state?.updatedAt == conflict.createdAt) {
                    "Conflict changed while resolving"
                }
            }
            is RemoteConflictPlan.EpisodeWatched -> {
                val matchingWrite = database.syncDao().pendingWrites().any { write ->
                    write.mediaType == MediaType.TV.name &&
                        write.mediaId == conflict.mediaId &&
                        write.createdAt == conflict.createdAt &&
                        write.operation in setOf(SyncOperationType.EPISODE_WATCHED.name, SyncOperationType.EPISODE_UNWATCHED.name) &&
                        write.payload.split(':', limit = 3).let { parts ->
                            parts.getOrNull(0)?.toIntOrNull() == plan.season &&
                                parts.getOrNull(1)?.toIntOrNull() == plan.episode &&
                                (write.operation == SyncOperationType.EPISODE_WATCHED.name) == local
                        }
                }
                val history = database.timelineDao().episodeHistoryForShow(MediaType.TV.name, conflict.mediaId)
                    .firstOrNull { it.season == plan.season && it.episodeNumber == plan.episode }
                val historyGeneration = history?.watchedAt?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
                require(matchingWrite || historyGeneration == conflict.createdAt) {
                    "Conflict changed while resolving"
                }
            }
        }
    }

    private suspend fun removeExactConflictIntent(conflict: SyncOperationEntity, plan: RemoteConflictPlan) {
        val ids = linkedSetOf<String>()
        val writes = database.syncDao().pendingWrites()
        when (plan) {
            is RemoteConflictPlan.Library -> {
                ids += stateOperationId(conflict.mediaType, conflict.mediaId)
            }
            is RemoteConflictPlan.MovieWatched -> {
                database.syncDao().syncOperations()
                    .filter { it.status != SyncOperationStatus.CONFLICT.name }
                    .filter { it.mediaType == MediaType.MOVIE.name && it.mediaId == conflict.mediaId }
                    .filter { it.operation in setOf(
                        SyncOperationType.MOVIE_WATCHED.name,
                        SyncOperationType.MOVIE_UNWATCHED.name,
                        SyncOperationType.MEDIA_HISTORY_REMOVE.name,
                    ) || (it.operation == SyncOperationType.LIBRARY_STATUS.name && conflict.localValue == "true") }
                    .filter {
                        it.operation == SyncOperationType.MEDIA_HISTORY_REMOVE.name ||
                            it.operation == SyncOperationType.LIBRARY_STATUS.name ||
                            (it.operation == SyncOperationType.MOVIE_WATCHED.name) == conflict.localValue?.toBooleanStrictOrNull()
                    }
                    .mapTo(ids, SyncOperationEntity::operationId)
                if (conflict.localValue?.toBooleanStrictOrNull() == true) {
                    ids += stateOperationId(MediaType.MOVIE.name, conflict.mediaId)
                }
            }
            is RemoteConflictPlan.EpisodeWatched -> {
                writes.filter { write ->
                    write.mediaType == MediaType.TV.name &&
                        write.mediaId == conflict.mediaId &&
                        write.operation in setOf(SyncOperationType.EPISODE_WATCHED.name, SyncOperationType.EPISODE_UNWATCHED.name) &&
                        write.episodeParts() == (plan.season to plan.episode) &&
                        (write.operation == SyncOperationType.EPISODE_WATCHED.name) == conflict.localValue?.toBooleanStrictOrNull()
                }.forEach { write ->
                    database.syncDao().deleteWrite(write.id)
                    ids += "write:${write.id}"
                }
                database.syncDao().syncOperations()
                    .filter { it.status != SyncOperationStatus.CONFLICT.name }
                    .filter { it.mediaType == MediaType.TV.name && it.mediaId == conflict.mediaId && it.season == plan.season && it.episode == plan.episode }
                    .filter { it.operation in setOf(SyncOperationType.EPISODE_WATCHED.name, SyncOperationType.EPISODE_UNWATCHED.name) }
                    .filter { (it.operation == SyncOperationType.EPISODE_WATCHED.name) == conflict.localValue?.toBooleanStrictOrNull() }
                    .mapTo(ids, SyncOperationEntity::operationId)
            }
        }
        ids.filter { it.startsWith("write:") }
            .mapNotNull { it.removePrefix("write:").toLongOrNull() }
            .forEach { writeId -> database.syncDao().deleteWrite(writeId) }
        if (ids.isNotEmpty()) {
            database.syncDao().deleteDeliveries(ids.toList())
            database.syncDao().deleteOperations(ids.toList())
        }
    }

    private fun SyncOperationEntity.toDomainOperation(): SyncOperation? {
        val type = runCatching { SyncOperationType.valueOf(operation) }.getOrNull() ?: return null
        val media = runCatching { MediaType.valueOf(mediaType) }.getOrNull() ?: return null
        val payload = entityPayloadOrEpisodeFallback()
        return SyncOperation(
            id = operationId,
            type = type,
            mediaType = media,
            mediaId = mediaId,
            title = title,
            value = localValue,
            payload = payload,
            sourceVersion = createdAt,
        )
    }

    private fun SyncOperationEntity.entityPayloadOrEpisodeFallback(): String? =
        payload ?: if (season != null && episode != null) "$season:$episode" else null

    private fun PendingWriteEntity.episodeParts(): Pair<Int?, Int?> {
        val parts = payload.split(':', limit = 3)
        return parts.getOrNull(0)?.toIntOrNull() to parts.getOrNull(1)?.toIntOrNull()
    }

    private fun PendingWriteEntity.toEpisodeOperation(title: String): SyncOperation? {
        val type = runCatching { SyncOperationType.valueOf(operation) }.getOrNull() ?: return null
        return SyncOperation(
            id = "write:$id",
            type = type,
            mediaType = MediaType.TV,
            mediaId = mediaId,
            title = title,
            value = (type == SyncOperationType.EPISODE_WATCHED).toString(),
            payload = payload,
            sourceVersion = createdAt,
        )
    }

    private suspend fun episodeDisplayTitle(
        showId: Int,
        season: Int,
        episode: Int,
        fallback: String,
    ): String {
        val showTitle = database.mediaDao().get(MediaType.TV.name, showId)?.title
            ?.takeIf(String::isNotBlank)
            ?: fallback.takeIf(String::isNotBlank)
            ?: "TV #$showId"
        val episodeTitle = database.mediaDao().episode(showId, season, episode)?.title
            ?.takeIf(String::isNotBlank)
        val label = "S${season.toString().padStart(2, '0')}E${episode.toString().padStart(2, '0')}"
        return listOf(showTitle, label, episodeTitle).filterNotNull().joinToString(" · ")
    }


    override suspend fun refreshProgressCache(
        request: ProgressRefreshRequest,
        onProgress: ((Float) -> Unit)?,
    ): Boolean = progressCache.refreshProgressCache(request, onProgress)

    suspend fun completeLogin(code: String, state: String?, issuer: String?): Result<Unit> =
        preferences.completeSimklLogin(code, state, issuer, services.simklAuth).onSuccess {
            database.syncDao().delete("all")
            onTokenChanged(preferences.tokenNow())
        }

    /** Reads the Simkl account name for the Settings page; null when it cannot be read. */
    suspend fun refreshFloppyUsername(): String? =
        runCatching { (trackingProviderRegistry?.getProvider(TrackingProviderId.FLOPPY) as? FloppyTrackingProvider)?.displayName() }.getOrNull()
            ?.also { preferences.setFloppyUsername(it) }

    suspend fun refreshSimklUsername(): String? =
        runCatching { services.simklSync.userSettings().user?.name?.trim() }.getOrNull()
            ?.takeIf(String::isNotBlank)
            ?.also { preferences.setSimklUsername(it) }

    suspend fun disconnectSimkl() {
        // Revoking either half ends the grant; a 200 confirms nothing, so the local copy goes regardless.
        (preferences.simklRefreshTokenNow() ?: currentToken())?.let { grant ->
            runCatching { services.simklAuth.revoke(grant, com.cinetrack.BuildConfig.SIMKL_CLIENT_ID) }
        }
        preferences.setSimklUsername(null)
        preferences.setSimklFirstSyncMode(null)
        preferences.setToken(null)
        database.syncDao().delete("all")
        onTokenChanged(null)
    }

    /** The metadata language changed (MetadataSettingsRepository): drop what was cached in the old one. */
    fun clearLanguageCaches() {
        progressCache.clearEpisodeTitleCache()
    }

    override suspend fun rebuildLibraryRailInTransaction() {
        val states = database.stateDao().stateSnapshot()
            .filter { it.status != LibraryStatus.NONE.name }
            .sortedByDescending(UserMediaStateEntity::updatedAt)
        val mediaByKey = database.mediaDao().mediaSnapshot().associateBy { "${it.mediaType}:${it.tmdbId}" }
        val media = states.mapNotNull { mediaByKey["${it.mediaType}:${it.mediaId}"] }
        database.mediaDao().clearRail(RailIds.LIBRARY)
        database.mediaDao().upsertRails(
            media.mapIndexed { index, item ->
                com.cinetrack.data.local.MediaRailEntity(RailIds.LIBRARY, item.mediaType, item.tmdbId, index)
            },
        )
    }

    override fun mergeSimklItems(items: List<SimklLibraryItem>): List<SimklLibraryItem> =
        items.groupBy { item ->
            val media = item.show ?: item.movie
            media?.ids?.tmdb ?: media?.ids?.imdb ?: media?.ids?.tvdb ?: media?.ids?.simkl?.toString()
                ?: "${media?.title.orEmpty().lowercase()}:${media?.year?.toString().orEmpty()}"
        }.values.mapNotNull { duplicates ->
            val preferred = duplicates.maxByOrNull { item -> item.seasons.sumOf { it.episodes.size } } ?: return@mapNotNull null
            val seasons = duplicates.flatMap(SimklLibraryItem::seasons)
                .groupBy { it.number }
                .map { (number, versions) ->
                    val episodes = versions.flatMap { it.episodes }
                        .groupBy { it.number }
                        .mapNotNull { (_, candidates) ->
                            candidates.maxByOrNull { episode ->
                                (if (episode.watchedAt != null) 2 else 0) + (if (episode.title.isNotBlank()) 1 else 0)
                            }
                        }
                        .sortedBy { it.number }
                    com.cinetrack.data.remote.SimklSeason(number, episodes)
                }
                .sortedBy { it.number }
            preferred.copy(seasons = seasons)
        }

    override suspend fun resolveSimklItems(items: List<SimklLibraryItem>, type: MediaType): List<ResolvedSimklItem> {
        val resolved = mutableListOf<ResolvedSimklItem>()
        for (batch in items.chunked(6)) {
            resolved += coroutineScope {
                batch.map { item -> async {
                    resolveSimklTmdbId(item, type)?.let { ResolvedSimklItem(item, type, it) }
                } }.mapNotNull { it.await() }
            }
        }
        return resolved
    }

    private suspend fun resolveSimklTmdbId(item: SimklLibraryItem, type: MediaType): Int? {
        val media = item.show ?: item.movie ?: return null
        media.ids.tmdb?.toIntOrNull()?.takeIf { it > 0 }?.let { return it }
        if (tmdbApiKey().isBlank()) return null

        media.ids.imdb?.takeIf(String::isNotBlank)?.let { imdb ->
            val found = runCatching { services.tmdb.find(imdb, "imdb_id") }.getOrNull()
            (if (type == MediaType.TV) found?.tvResults else found?.movieResults)
                ?.firstOrNull()?.id?.let { return it }
        }
        if (type == MediaType.TV) media.ids.tvdb?.takeIf(String::isNotBlank)?.let { tvdb ->
            runCatching { services.tmdb.find(tvdb, "tvdb_id") }.getOrNull()
                ?.tvResults?.firstOrNull()?.id?.let { return it }
        }

        if (media.title.isBlank()) return null
        val expectedType = if (type == MediaType.TV) "tv" else "movie"
        val normalized = normalizeMediaTitle(media.title)
        val candidates = runCatching { services.tmdb.search(media.title).results }
            .getOrDefault(emptyList())
            .filter { it.mediaType == expectedType }
        val exact = candidates.firstOrNull { candidate ->
            val title = if (type == MediaType.TV) candidate.name else candidate.title
            val year = (if (type == MediaType.TV) candidate.firstAirDate else candidate.releaseDate)?.take(4)?.toIntOrNull()
            normalizeMediaTitle(title.orEmpty()) == normalized && (media.year == null || media.year == year)
        } ?: candidates.firstOrNull { candidate ->
            normalizeMediaTitle((if (type == MediaType.TV) candidate.name else candidate.title).orEmpty()) == normalized
        }
        return exact?.id
    }

    private fun normalizeMediaTitle(value: String): String = value.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), "")

    private fun SimklLibraryItem.toState(type: MediaType, resolvedTmdbId: Int? = null): UserMediaStateEntity? {
        val item = show ?: movie ?: return null
        val tmdbId = resolvedTmdbId ?: item.ids.tmdb?.toIntOrNull() ?: return null
        return UserMediaStateEntity(
            mediaType = type.name,
            mediaId = tmdbId,
            status = status.fromSimklStatus().name,
            watched = status == "completed",
            simklId = item.ids.simkl,
            updatedAt = listOfNotNull(lastWatchedAt, addedAt).firstNotNullOfOrNull { raw ->
                runCatching { Instant.parse(raw).toEpochMilli() }.getOrNull()
            } ?: System.currentTimeMillis(),
            dirty = false,
        )
    }

    private fun SimklLibraryItem.toMediaEntity(type: MediaType, resolvedTmdbId: Int? = null): MediaEntity? {
        val item = show ?: movie ?: return null
        val tmdbId = resolvedTmdbId ?: item.ids.tmdb?.toIntOrNull() ?: return null
        return MediaEntity(
            mediaType = type.name,
            tmdbId = tmdbId,
            title = item.title.ifBlank { "Untitled" },
            overview = "",
            posterPath = null,
            backdropPath = null,
            releaseDate = item.year?.toString(),
            score = null,
            runtimeMinutes = item.runtime,
            genres = "",
            providers = "",
            collectionId = null,
        )
    }

    private fun SimklPlaybackItem.toMediaEntity(): MediaEntity? {
        val item = show ?: movie ?: return null
        val tmdbId = item.ids.tmdb?.toIntOrNull() ?: return null
        return MediaEntity(
            mediaType = if (movie != null) MediaType.MOVIE.name else MediaType.TV.name,
            tmdbId = tmdbId,
            title = item.title.ifBlank { "Untitled" },
            overview = "",
            posterPath = null,
            backdropPath = null,
            releaseDate = item.year?.toString(),
            score = null,
            runtimeMinutes = item.runtime,
            genres = "",
            providers = "",
            collectionId = null,
        )
    }

    private fun SimklPlaybackItem.toPlaybackEntity(): PlaybackEntity? {
        val item = show ?: movie ?: return null
        val tmdbId = item.ids.tmdb?.toIntOrNull() ?: return null
        val episodeNumber = episode?.number ?: episode?.episode
        return PlaybackEntity(
            mediaType = if (movie != null) MediaType.MOVIE.name else MediaType.TV.name,
            mediaId = tmdbId,
            episodeId = id,
            progress = (progress / 100.0).toFloat().coerceIn(0f, 1f),
            positionSeconds = 0,
            durationSeconds = 0,
            updatedAt = pausedAt,
            season = episode?.season,
            episodeNumber = episodeNumber,
            episodeTitle = episode?.title,
        )
    }

    private fun String.fromSimklStatus() = when (this) {
        "watching" -> LibraryStatus.WATCHING
        "hold" -> LibraryStatus.PAUSED
        "plantowatch" -> LibraryStatus.PLAN_TO_WATCH
        "completed" -> LibraryStatus.COMPLETED
        "dropped" -> LibraryStatus.DROPPED
        else -> LibraryStatus.NONE
    }

    private fun String.toSimklStatus() = when (this) {
        LibraryStatus.WATCHING.name -> "watching"
        LibraryStatus.PLAN_TO_WATCH.name -> "plantowatch"
        LibraryStatus.PAUSED.name -> "hold"
        LibraryStatus.COMPLETED.name -> "completed"
        LibraryStatus.DROPPED.name -> "dropped"
        else -> null
    }

    private fun SimklIds.toTrackingIds() = MediaIds(
        tmdb = tmdb?.toLongOrNull(),
        tvdb = tvdb?.toLongOrNull(),
        imdb = imdb,
        simkl = simkl,
    )

    private fun String?.toInstantOrNull(): Instant? = this?.let { raw ->
        runCatching { Instant.parse(raw) }.getOrNull()
    }

    private fun String.fromCineTrackStatus(): LibraryStatus =
        runCatching { LibraryStatus.valueOf(this) }.getOrDefault(LibraryStatus.NONE)
}


