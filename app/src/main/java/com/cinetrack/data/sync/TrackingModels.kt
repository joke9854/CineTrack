package com.cinetrack.data.sync

import com.cinetrack.domain.MediaType
import com.cinetrack.domain.SyncProgress
import com.cinetrack.domain.SyncReport

/** Stable identifiers used by CineTrack configuration and persisted sync state. */
enum class TrackingProviderId { SIMKL, FLOPPY }

/** MAIN is bidirectional; SECONDARY is deliberately outbound-only. */
enum class TrackingDirection { BIDIRECTIONAL, OUTBOUND_ONLY }

data class TrackingConfiguration(
    val mainProvider: TrackingProviderId? = TrackingProviderId.SIMKL,
    val secondaryProvider: TrackingProviderId? = null,
) {
    init {
        require(mainProvider == null || mainProvider != secondaryProvider) {
            "The same tracking provider cannot be both MAIN and SECONDARY"
        }
    }
}

enum class SyncOperationType {
    LIBRARY_STATUS,
    MOVIE_WATCHED,
    MOVIE_UNWATCHED,
    EPISODE_WATCHED,
    EPISODE_UNWATCHED,
    MEDIA_HISTORY_REMOVE,
    SET_RATING,
}

enum class TrackingCapability {
    PULL_LIBRARY,
    PUSH_LIBRARY,
    PULL_MOVIE_HISTORY,
    PUSH_MOVIE_HISTORY,
    PULL_EPISODE_HISTORY,
    PUSH_EPISODE_HISTORY,
    REMOVE_REMOTE_STATE,
    TIMESTAMPS,
    FULL_HISTORY,
}

/** CineTrack-owned operation. Provider request/response DTOs never cross this boundary. */
data class SyncOperation(
    val id: String,
    val type: SyncOperationType,
    val mediaType: MediaType,
    val mediaId: Int,
    val title: String,
    val value: String? = null,
    val payload: String? = null,
    val sourceVersion: Long,
)

data class TrackingCapabilities(
    val supportsMovies: Boolean = true,
    val supportsShows: Boolean = true,
    val supportsWatchHistory: Boolean = true,
    val supportsRatings: Boolean = false,
    val supportsLibrary: Boolean = true,
    val supportsTwoWaySync: Boolean = true,
    val supported: Set<TrackingCapability> = setOf(
        TrackingCapability.PULL_LIBRARY,
        TrackingCapability.PUSH_LIBRARY,
        TrackingCapability.PULL_MOVIE_HISTORY,
        TrackingCapability.PUSH_MOVIE_HISTORY,
        TrackingCapability.PULL_EPISODE_HISTORY,
        TrackingCapability.PUSH_EPISODE_HISTORY,
        TrackingCapability.REMOVE_REMOTE_STATE,
        TrackingCapability.TIMESTAMPS,
        TrackingCapability.FULL_HISTORY,
    ),
) {
    fun supports(capability: TrackingCapability): Boolean = capability in supported
    fun supports(operation: SyncOperation): Boolean = when (operation.type) {
        SyncOperationType.LIBRARY_STATUS -> supportsLibrary
        SyncOperationType.MOVIE_WATCHED,
        SyncOperationType.MOVIE_UNWATCHED,
        SyncOperationType.EPISODE_WATCHED,
        SyncOperationType.EPISODE_UNWATCHED,
        SyncOperationType.MEDIA_HISTORY_REMOVE -> supportsWatchHistory
        SyncOperationType.SET_RATING -> supportsRatings
    } && when (operation.mediaType) {
        MediaType.MOVIE -> supportsMovies
        MediaType.TV -> supportsShows
    }
}

sealed interface ConnectionResult {
    data object Connected : ConnectionResult
    data object AuthenticationRequired : ConnectionResult
    data class Failed(val error: TrackingSyncError) : ConnectionResult
}

sealed class TrackingSyncError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class AuthenticationRequired(provider: TrackingProviderId) :
        TrackingSyncError("Connect ${provider.name.lowercase().replaceFirstChar(Char::uppercase)} first")
    class NetworkUnavailable(cause: Throwable) : TrackingSyncError("Network unavailable", cause)
    class ProviderUnavailable(provider: TrackingProviderId, cause: Throwable? = null) :
        TrackingSyncError("${provider.name.lowercase().replaceFirstChar(Char::uppercase)} is unavailable", cause)
    class UnsupportedOperation(provider: TrackingProviderId, operation: SyncOperationType) :
        TrackingSyncError("${provider.name} does not support $operation")
    class Conflict(message: String) : TrackingSyncError(message)
    class InvalidRemoteData(message: String) : TrackingSyncError(message)
    class Unknown(cause: Throwable) : TrackingSyncError(cause.message ?: cause::class.java.simpleName, cause)
}

data class ProviderPushResult(val completedOperationIds: Set<String>)

/** Result of a provider's bidirectional MAIN pass after applying remote changes locally. */
data class ProviderSyncOutcome(
    val itemsChanged: Boolean,
    val report: SyncReport = SyncReport(),
)

data class SyncCoordinatorOutcome(
    val itemsChanged: Boolean,
    val report: SyncReport = SyncReport(),
)

interface TrackingProvider {
    val id: TrackingProviderId
    val capabilities: TrackingCapabilities

    suspend fun isAuthenticated(): Boolean
    suspend fun push(operations: List<SyncOperation>): ProviderPushResult

    /** Optional normalized pull for providers that expose snapshot APIs. */
    suspend fun pullSnapshot(): TrackingSnapshot = TrackingSnapshot()

    /**
     * Only the registry-selected MAIN provider is ever called here. Implementations
     * may combine upload and download to preserve an existing atomic sync protocol.
     */
    suspend fun syncBidirectionally(
        operations: List<SyncOperation>,
        onProgress: (SyncProgress) -> Unit,
    ): ProviderSyncOutcome

    suspend fun testConnection(): ConnectionResult
}

