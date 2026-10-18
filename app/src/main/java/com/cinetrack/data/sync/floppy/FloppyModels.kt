package com.cinetrack.data.sync.floppy

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** DTOs mirror the current Floppy `/api/docs/` and `/api/schema/` contract. */
@Serializable
data class FloppyInfoDto(
    val version: String = "",
    val debug: Boolean = false,
    @SerialName("frontend_url") val frontendUrl: String = "",
    val language: String = "",
    val timezone: String = "",
    @SerialName("admin_enabled") val adminEnabled: Boolean = false,
    @SerialName("track_time") val trackTime: Boolean = false,
    @SerialName("api_extensions") val apiExtensions: FloppyApiExtensions? = null,
)

@Serializable
data class FloppyApiExtensions(
    @SerialName("cinetrack_episode_events_v1") val cinetrackEpisodeEventsV1: Boolean = false,
    @SerialName("cinetrack_bootstrap_v2") val cinetrackBootstrapV2: Boolean = false,
    @SerialName("episode_sql_pagination") val episodeSqlPagination: Boolean = false,
    @SerialName("sync_app_connections_v1") val syncAppConnectionsV1: Boolean = false,
    @SerialName("sync_items_v1") val syncItemsV1: Boolean = false,
    @SerialName("cinetrack_episode_translate_v1") val cinetrackEpisodeTranslateV1: Boolean = false,
    @SerialName("cinetrack_crash_reports_v1") val cinetrackCrashReportsV1: Boolean = false,
    @SerialName("cinetrack_characters_v1") val cinetrackCharactersV1: Boolean = false,
    /** App sign-in (OAuth 2.0 + PKCE) for the `cinetrack` client. */
    @SerialName("oauth_v1") val oauthV1: Boolean = false,
)

@Serializable
data class FloppyConnectionProbeDto(
    val authenticated: Boolean = false,
    @SerialName("account_id") val accountId: String = "",
    // Display only ("Connected as ..."); the account is identified by accountId.
    val username: String? = null,
    @SerialName("server_version") val serverVersion: String = "",
    @SerialName("api_extensions") val apiExtensions: FloppyApiExtensions = FloppyApiExtensions(),
)

@Serializable
data class FloppyPagination(
    val total: Int = 0,
    val limit: Int = 0,
    val offset: Int = 0,
    val next: String? = null,
    val previous: String? = null,
)

@Serializable
data class FloppyTrackedMedia(
    val id: Int? = null,
    @SerialName("consumption_id") val consumptionId: Int? = null,
    val item: JsonObject? = null,
    @SerialName("item_id") val itemId: String? = null,
    @SerialName("parent_id") val parentId: String? = null,
    val tracked: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
    val score: Double? = null,
    val status: Int? = null,
    val progress: Double? = null,
    @SerialName("start_date") val startDate: String? = null,
    @SerialName("end_date") val endDate: String? = null,
    @SerialName("progressed_at") val progressedAt: String? = null,
    val show: FloppyShow? = null,
)

/** A single Floppy Consumption. Multiple consumptions may belong to one item. */
@Serializable
data class FloppyConsumption(
    @SerialName("consumption_id") val consumptionId: Int,
    val created: String? = null,
    val score: Double? = null,
    val progress: Double? = null,
    @SerialName("progressed_at") val progressedAt: String? = null,
    val status: Int? = null,
    @SerialName("start_date") val startDate: String? = null,
    @SerialName("end_date") val endDate: String? = null,
    val notes: String? = null,
)

/** Detail response for one media item, including every known Consumption. */
@Serializable
data class FloppyMediaDetail(
    val id: Int? = null,
    @SerialName("media_id") val mediaId: String? = null,
    val source: String? = null,
    @SerialName("media_type") val mediaType: String? = null,
    val title: String? = null,
    val tracked: Boolean = false,
    @SerialName("consumptions_number") val consumptionsNumber: Int = 0,
    val consumptions: List<FloppyConsumption> = emptyList(),
    val itemId: String? = null,
    val parentId: String? = null,
)

@Serializable
data class FloppyConsumptionPage(
    val pagination: FloppyPagination = FloppyPagination(),
    val results: List<FloppyConsumption> = emptyList(),
)

@Serializable
data class FloppyShow(
    val id: Int? = null,
    val title: String = "",
    val slug: String = "",
    @SerialName("podcast_uuid") val podcastUuid: String? = null,
    val image: String = "",
    @SerialName("website_url") val websiteUrl: String = "",
)

@Serializable
data class FloppyTrackedMediaEnvelope(
    val pagination: FloppyPagination = FloppyPagination(),
    val results: List<FloppyTrackedMedia> = emptyList(),
)

@Serializable
data class FloppyTrackMediaRequest(
    val source: String,
    @SerialName("media_id") val mediaId: String,
    val title: String? = null,
    @SerialName("season_number") val seasonNumber: Int? = null,
    @SerialName("episode_number") val episodeNumber: Int? = null,
    @SerialName("parent_tv") val parentTv: Int? = null,
    @SerialName("parent_season") val parentSeason: Int? = null,
    @SerialName("library_media_type") val libraryMediaType: String? = null,
    val image: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    val status: Int? = null,
    val score: Double? = null,
    val progress: Double? = null,
    @SerialName("start_date") val startDate: String? = null,
    @SerialName("end_date") val endDate: String? = null,
    val notes: String? = null,
)

@Serializable
data class FloppyTrackedMediaUpdateRequest(
    val status: Int? = null,
    val score: Double? = null,
    val progress: Double? = null,
    @SerialName("start_date") val startDate: String? = null,
    @SerialName("end_date") val endDate: String? = null,
    val notes: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
)

@Serializable
data class FloppyEpisodeWatchRequest(
    val score: Double? = null,
    @SerialName("watched_at") val watchedAt: String? = null,
)

@Serializable
data class FloppyMovieWatchRequest(
    @SerialName("end_date") val endDate: String,
    @SerialName("external_id") val externalId: String? = null,
)

/** Non-secret connection state. The API key is deliberately absent. */
data class FloppyConnectionSettings(
    val baseUrl: String,
    val serverVersion: String? = null,
    val serverIdentity: String,
    val accountIdentity: String? = null,
    val capabilities: FloppyCapabilities = FloppyCapabilities(),
    val connectedAt: Long? = null,
    /** Stable CineTrack generation for this connection, never the Floppy version. */
    val connectionId: String = serverIdentity,
    /** Active secure-store alias for the API key. */
    val credentialAlias: String = "floppy_api_key",
    /** Explicit opt-in for private-LAN cleartext HTTP. */
    val allowInsecureLocalHttp: Boolean = false,
)

/** Marks account identities that come from the CineTrack connection probe. */
internal const val FLOPPY_PROBE_ACCOUNT_PREFIX = "floppy-probe-v1:"

/**
 * Resolves whether two validated connections address the same logical Floppy
 * dataset. A credential rotation is not a provider-instance change when the
 * authenticated account proves that the remote dataset is unchanged. If an
 * account identity is unavailable, matching the key is the conservative
 * fallback; a changed key is treated as a new target rather than risking a
 * delivery to an unknown account.
 *
 * Identities from different schemes (a legacy preferences username versus the
 * opaque CineTrack probe account id) cannot be compared, so the unchanged API
 * key is the proof during that one-time migration.
 *
 * The probe's account id is an HMAC of the user id keyed with the server's own
 * secret, so it names one Floppy installation and one account. Equal probe ids
 * at a new address (a new tunnel or domain) are the same dataset only when the
 * user confirms it ([acceptAddressMove]); the connection, its pending deliveries
 * and its finished initial sync then carry over. Unconfirmed, a new address is a
 * new target, as is any other account. A username is not unique across servers.
 */
internal fun sameFloppyRemoteTarget(
    previous: FloppyConnectionSettings,
    candidate: FloppyConnectionSettings,
    previousApiKey: String?,
    candidateApiKey: String?,
    acceptAddressMove: Boolean = false,
): Boolean {
    if (acceptAddressMove && isFloppyAddressMove(previous, candidate)) return true
    if (previous.serverIdentity != candidate.serverIdentity) return false
    val previousAccount = previous.accountIdentity?.trim().orEmpty()
    val candidateAccount = candidate.accountIdentity?.trim().orEmpty()
    val sameScheme = previousAccount.startsWith(FLOPPY_PROBE_ACCOUNT_PREFIX) ==
        candidateAccount.startsWith(FLOPPY_PROBE_ACCOUNT_PREFIX)
    return if (previousAccount.isNotBlank() && candidateAccount.isNotBlank() && sameScheme) {
        previousAccount == candidateAccount
    } else {
        previousApiKey?.takeIf(String::isNotBlank) == candidateApiKey?.takeIf(String::isNotBlank)
    }
}

/** The same Floppy account (equal probe ids) reached at a different address. */
internal fun isFloppyAddressMove(previous: FloppyConnectionSettings, candidate: FloppyConnectionSettings): Boolean {
    val previousAccount = previous.accountIdentity?.trim().orEmpty()
    return previous.serverIdentity != candidate.serverIdentity &&
        previousAccount.startsWith(FLOPPY_PROBE_ACCOUNT_PREFIX) &&
        previousAccount.length > FLOPPY_PROBE_ACCOUNT_PREFIX.length &&
        previousAccount == candidate.accountIdentity?.trim()
}

/** Immutable credentials and capabilities captured for one provider pass. */
data class FloppySession(
    val instanceId: String,
    val baseUrl: String,
    val accountIdentity: String?,
    val credentialAlias: String,
    val apiKey: String,
    val capabilities: FloppyCapabilities,
    val serverVersion: String?,
    val allowInsecureLocalHttp: Boolean = false,
)

data class FloppyCapabilities(
    val canReadLibrary: Boolean = false,
    val canWriteLibrary: Boolean = false,
    val canReadHistory: Boolean = false,
    val canWriteMovieHistory: Boolean = false,
    val canWriteEpisodeHistory: Boolean = false,
    val canRemoveHistory: Boolean = false,
    val canReadCompleteSnapshot: Boolean = false,
    /** Fork capability: exact event timestamps + durable idempotent replay. */
    val canEnsureEpisodeEvents: Boolean = false,
    /** Fork capability: set-based movie/show import with no remote prefetch. */
    val canBootstrapV2: Boolean = false,
    /** Fork capability: exact per-item snapshot + change feed, so Floppy can be MAIN. */
    val canSyncItems: Boolean = false,
    /** Fork capability: episode numbering translation (tracker/TMDB). */
    val canTranslateEpisodes: Boolean = false,
    /** Fork capability: accepts CineTrack crash/ANR reports. */
    val canCrashReports: Boolean = false,
    /** Fork capability: TVDB character pictures for the cast row. */
    val canCharacterImages: Boolean = false,
)

/** One play of a synced item, at its exact instant. */
@Serializable
data class FloppySyncPlayDto(
    val key: String = "",
    @SerialName("watched_at") val watchedAt: String? = null,
    @SerialName("client_event_id") val clientEventId: String? = null,
)

/** The whole Floppy sync state of one movie, episode or show. */
@Serializable
data class FloppySyncItemDto(
    val sequence: Long? = null,
    val type: String,
    val source: String,
    @SerialName("media_id") val mediaId: String,
    val title: String = "",
    @SerialName("season_number") val seasonNumber: Int? = null,
    @SerialName("episode_number") val episodeNumber: Int? = null,
    val ids: Map<String, String> = emptyMap(),
    @SerialName("in_library") val inLibrary: Boolean = true,
    val status: String? = null,
    val score: String? = null,
    val plays: List<FloppySyncPlayDto> = emptyList(),
)

@Serializable
data class FloppySyncItemsSnapshotPage(
    val results: List<FloppySyncItemDto> = emptyList(),
    @SerialName("next_after") val nextAfter: Long? = null,
    val cursor: Long = 0,
)

@Serializable
data class FloppySyncItemsChangesPage(
    val results: List<FloppySyncItemDto> = emptyList(),
    val cursor: Long = 0,
    @SerialName("has_more") val hasMore: Boolean = false,
)

@Serializable
data class FloppyAppConnectionRequest(
    val client: String,
    @SerialName("instance_key") val instanceKey: String,
    val label: String? = null,
)

@Serializable
data class FloppyBootstrapMovieWatch(
    @SerialName("watched_at") val watchedAt: String,
    @SerialName("client_event_id") val clientEventId: String,
)

@Serializable
data class FloppyBootstrapMovie(
    val source: String,
    @SerialName("media_id") val mediaId: String,
    val title: String? = null,
    val image: String? = null,
    val status: Int? = null,
    val watch: FloppyBootstrapMovieWatch? = null,
)

@Serializable
data class FloppyBootstrapMoviesRequest(val movies: List<FloppyBootstrapMovie>)

@Serializable
data class FloppyBootstrapShow(
    val source: String,
    @SerialName("media_id") val mediaId: String,
    val title: String? = null,
    val image: String? = null,
    val status: Int? = null,
)

@Serializable
data class FloppyBootstrapShowsRequest(val shows: List<FloppyBootstrapShow>)

@Serializable
data class FloppyBootstrapEnsureResult(
    val source: String,
    @SerialName("media_id") val mediaId: String,
    val status: String,
)

@Serializable
data class FloppyBootstrapEnsureResponse(val results: List<FloppyBootstrapEnsureResult> = emptyList())

@Serializable
data class FloppyEpisodeEnsureEvent(
    @SerialName("season_number") val seasonNumber: Int,
    @SerialName("episode_number") val episodeNumber: Int,
    @SerialName("watched_at") val watchedAt: String,
    @SerialName("client_event_id") val clientEventId: String,
)

@Serializable
data class FloppyEpisodeEnsureRequest(
    val events: List<FloppyEpisodeEnsureEvent>,
    /** One-time repair re-send: Floppy places each event by its rules and
     * moves the event's own play there if an older version stored it on
     * another episode (e.g. after a TMDB-merged two-part episode). */
    @SerialName("repair_coordinates") val repairCoordinates: Boolean = false,
)

@Serializable
data class FloppyEpisodeEnsureResult(
    @SerialName("client_event_id") val clientEventId: String,
    @SerialName("season_number") val seasonNumber: Int,
    @SerialName("episode_number") val episodeNumber: Int,
    val status: String,
    /** Where Floppy stored the watch when it differs from the submitted
     * coordinate (continuous anime numbering translated to TMDB seasons). */
    @SerialName("stored_season_number") val storedSeasonNumber: Int? = null,
    @SerialName("stored_episode_number") val storedEpisodeNumber: Int? = null,
    /** Why an event was not placed (not_found/metadata_unavailable). */
    val reason: String? = null,
    /** TVDB title for an event that was not placed. */
    val title: String? = null,
)

@Serializable
data class FloppyEpisodeEnsureResponse(val results: List<FloppyEpisodeEnsureResult> = emptyList())

/** One on-screen character from TVDB, read through Floppy; either picture may be missing. */
@Serializable
data class FloppyCharacterDto(
    val name: String = "",
    @SerialName("person_name") val personName: String = "",
    @SerialName("character_image") val characterImage: String? = null,
    @SerialName("person_image") val personImage: String? = null,
)

@Serializable
data class FloppyCharactersResponse(
    @SerialName("tvdb_id") val tvdbId: String? = null,
    val reason: String? = null,
    val characters: List<FloppyCharacterDto> = emptyList(),
)

@Serializable
data class FloppyEpisodeTranslateCoord(val season: Int, val episode: Int)

@Serializable
data class FloppyEpisodeTranslateRequest(
    val from: String,
    val episodes: List<FloppyEpisodeTranslateCoord>,
)

@Serializable
data class FloppyEpisodeTranslateResult(
    val season: Int,
    val episode: Int,
    val status: String,
    val tmdb: FloppyEpisodeTranslateCoord? = null,
    val absolute: Int? = null,
    val tvdb: FloppyEpisodeTranslateCoord? = null,
    val reason: String? = null,
)

@Serializable
data class FloppyEpisodeTranslateResponse(val results: List<FloppyEpisodeTranslateResult> = emptyList())

@Serializable
data class FloppyEpisodeBulkRequest(
    @SerialName("first_season_number") val firstSeasonNumber: Int,
    @SerialName("first_episode_number") val firstEpisodeNumber: Int,
    @SerialName("last_season_number") val lastSeasonNumber: Int,
    @SerialName("last_episode_number") val lastEpisodeNumber: Int,
    @SerialName("start_date") val startDate: String,
    @SerialName("end_date") val endDate: String,
    @SerialName("write_mode") val writeMode: String = "add",
    @SerialName("distribution_mode") val distributionMode: String = "air_date",
)

@Serializable
data class FloppyBulkTaskResponse(
    @SerialName("task_id") val taskId: String? = null,
)

@Serializable
data class FloppyTaskStatusResponse(
    @SerialName("status") val status: String? = null,
)
