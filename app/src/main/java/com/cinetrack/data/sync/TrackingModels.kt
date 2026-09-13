package com.cinetrack.data.sync

import com.cinetrack.domain.MediaType
import com.cinetrack.domain.SyncProgress
import com.cinetrack.domain.SyncReport

/** Stable identifiers used by CineTrack configuration and persisted sync state. */
enum class TrackingProviderId { SIMKL, FLOPPY }

/** MAIN is bidirectional; SECONDARY is deliberately outbound-only. */
enum class TrackingDirection { BIDIRECTIONAL, OUTBOUND_ONLY }

/** Readiness contract for a provider added as a SECONDARY before promotion. */
enum class ProviderBootstrapState { NOT_STARTED, RUNNING, READY, FAILED }

data class TrackingConfiguration(
    val mainProvider: TrackingProviderId? = TrackingProviderId.SIMKL,
    val secondaryProvider: TrackingProviderId? = null,
) {
    init {
        require(mainProvider != null || secondaryProvider == null) {
            "A SECONDARY provider requires a MAIN provider"
        }
        require(mainProvider == null || mainProvider != secondaryProvider) {
            "The same tracking provider cannot be both MAIN and SECONDARY"
        }
    }

    companion object {
        /** Safely reads legacy DataStore values without constructing an invalid configuration. */
        fun normalized(mainProvider: TrackingProviderId?, secondaryProvider: TrackingProviderId?): TrackingConfiguration =
            when {
                mainProvider == null -> TrackingConfiguration(mainProvider = null, secondaryProvider = null)
                mainProvider == secondaryProvider -> TrackingConfiguration(mainProvider = mainProvider, secondaryProvider = null)
                else -> TrackingConfiguration(mainProvider, secondaryProvider)
            }
    }
}

/**
 * Validates a role transition without guessing authority for a new MAIN.
 * A replacement MAIN must have first been configured as SECONDARY and
 * explicitly bootstrapped by that provider.
 */
fun validateTrackingConfigurationTransition(
    previous: TrackingConfiguration,
    next: TrackingConfiguration,
    promotedBootstrapState: ProviderBootstrapState? = null,
) {
    if (previous.mainProvider != null &&
        next.mainProvider != null &&
        previous.mainProvider != next.mainProvider
    ) {
        require(next.mainProvider == previous.secondaryProvider) {
            "A new MAIN provider must first be configured as SECONDARY"
        }
        require(promotedBootstrapState == ProviderBootstrapState.READY) {
            "The promoted MAIN provider must complete bootstrap first"
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

/** Per-provider delivery state for one logical CineTrack mutation. */
enum class DeliveryStatus {
    PENDING,
    ACKNOWLEDGED,
    FAILED,
    SKIPPED_UNSUPPORTED,
    /** Terminal when a configured provider is explicitly removed. */
    CANCELLED_PROVIDER_REMOVED,
}

enum class TrackingRole { MAIN, SECONDARY }

data class SyncOperationDelivery(
    val operationId: String,
    val operationVersion: Long,
    val providerId: TrackingProviderId,
    val status: DeliveryStatus = DeliveryStatus.PENDING,
    val required: Boolean = true,
    val roleAtEnqueue: TrackingRole = TrackingRole.MAIN,
    val attemptCount: Int = 0,
    val lastError: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

enum class TrackingCapability {
    PULL_LIBRARY,
    PUSH_LIBRARY,
    PULL_MOVIE_HISTORY,
    PUSH_MOVIE_HISTORY,
    PULL_EPISODE_HISTORY,
    PUSH_EPISODE_HISTORY,
    REMOVE_REMOTE_STATE,
    PUSH_RATING,
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
        TrackingCapability.PUSH_RATING,
        TrackingCapability.TIMESTAMPS,
        TrackingCapability.FULL_HISTORY,
    ),
) {
    fun supports(capability: TrackingCapability): Boolean = capability in supported
    fun supports(operation: SyncOperation): Boolean = when (operation.type) {
        SyncOperationType.LIBRARY_STATUS -> supportsLibrary && supports(TrackingCapability.PUSH_LIBRARY)
        SyncOperationType.MOVIE_WATCHED -> supportsWatchHistory && supports(TrackingCapability.PUSH_MOVIE_HISTORY)
        SyncOperationType.MOVIE_UNWATCHED -> supportsWatchHistory && supports(TrackingCapability.REMOVE_REMOTE_STATE)
        SyncOperationType.EPISODE_WATCHED -> supportsWatchHistory && supports(TrackingCapability.PUSH_EPISODE_HISTORY)
        SyncOperationType.EPISODE_UNWATCHED -> supportsWatchHistory && supports(TrackingCapability.REMOVE_REMOTE_STATE)
        SyncOperationType.MEDIA_HISTORY_REMOVE -> supportsWatchHistory && supports(TrackingCapability.REMOVE_REMOTE_STATE)
        SyncOperationType.SET_RATING -> supportsRatings && supports(TrackingCapability.PUSH_RATING)
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
    /** Exact provider-neutral operations acknowledged by the provider pass. */
    val acknowledgedOperationIds: Set<String> = emptySet(),
    /** Valid queued intents intentionally left pending because they conflicted. */
    val deferredOperationIds: Set<String> = emptySet(),
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

