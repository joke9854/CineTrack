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
data class FloppyHistoryEnvelope(
    val pagination: FloppyPagination = FloppyPagination(),
    val results: List<FloppyHistoryEntry> = emptyList(),
)

/** Flat history fields are intentionally nullable: Floppy adds media types over time. */
@Serializable
data class FloppyHistoryEntry(
    @SerialName("media_id") val mediaId: String? = null,
    @SerialName("media_type") val mediaType: String? = null,
    val source: String? = null,
    val status: Int? = null,
    @SerialName("created_at") val createdAt: String? = null,
    val watched: Boolean? = null,
    @SerialName("watched_at") val watchedAt: String? = null,
    @SerialName("end_date") val endDate: String? = null,
    val item: JsonObject? = null,
    val season: Int? = null,
    val episode: Int? = null,
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
)

/** Immutable credentials and capabilities captured for one provider pass. */
data class FloppySession(
    val instanceId: String,
    val baseUrl: String,
    val accountIdentity: String?,
    val credentialAlias: String,
    val apiKey: String,
    val capabilities: FloppyCapabilities,
    val serverVersion: String?,
)

data class FloppyCapabilities(
    val canReadLibrary: Boolean = false,
    val canWriteLibrary: Boolean = false,
    val canReadHistory: Boolean = false,
    val canWriteMovieHistory: Boolean = false,
    val canWriteEpisodeHistory: Boolean = false,
    val canRemoveHistory: Boolean = false,
    val canReadCompleteSnapshot: Boolean = false,
)
