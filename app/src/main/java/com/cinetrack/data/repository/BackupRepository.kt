package com.cinetrack.data.repository

import androidx.room.withTransaction
import com.cinetrack.BuildConfig
import com.cinetrack.data.local.AppDatabase
import com.cinetrack.data.local.MediaEntity
import com.cinetrack.data.local.PlaybackEntity
import com.cinetrack.data.local.UserMediaStateEntity
import com.cinetrack.data.local.WatchHistoryEntity
import com.cinetrack.domain.AppUiState
import com.cinetrack.domain.LibraryStatus
import com.cinetrack.domain.MediaType
import com.cinetrack.domain.RailIds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.security.MessageDigest
import java.time.Instant

/**
 * Portable backups: export, restore from a file or the automatic copy, and the delayed
 * automatic backup after library changes. Restore inserts watch history event by event with
 * the stored timestamps and never queues anything for a tracker. Moved out of
 * the former sync-host repository (roadmap Q2).
 */
class BackupRepository(
    private val database: AppDatabase,
    private val preferences: AppPreferences,
    /** Restored metadata settings go through it so the network clients use them at once. */
    private val metadataSettings: () -> MetadataSettingsRepository,
    /** The library, progress, history and settings as the app shows them (AppStateRepository.loadCachedState). */
    private val loadState: suspend () -> AppUiState,
    /** Rebuilds the Library rail from the restored rows; runs inside the restore transaction. */
    private val rebuildLibraryRail: suspend () -> Unit,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private var automaticBackupJob: Job? = null

    @Synchronized
    fun scheduleAutomaticBackup() {
        automaticBackupJob?.cancel()
        automaticBackupJob = scope.launch {
            delay(1_500)
            createAutomaticBackup()
        }
    }

    /**
     * Creates the logical files for a portable CineTrack backup. JSONL is the
     * authoritative representation; CSV mirrors it for spreadsheet inspection.
     * API credentials and the Simkl token are deliberately never exported.
     */
    suspend fun exportBackupFiles(sections: Set<String>): Map<String, String> {
        val selected = sections.ifEmpty { setOf("library", "progress", "history", "personal", "settings") }
        val state = loadState()
        val payload = linkedMapOf<String, String>()
        val counts = linkedMapOf<String, Int>()

        if ("library" in selected) {
            val items = state.rails[RailIds.LIBRARY].orEmpty()
            payload["data/library.jsonl"] = items.joinToString("\n") { media ->
                buildJsonObject {
                    put("tmdbId", media.id)
                    put("type", media.type.name.lowercase())
                    put("title", media.title)
                    put("status", media.status.name.lowercase())
                    put("watched", media.watched)
                    media.releaseDate?.let { put("releaseDate", it) }
                }.toString()
            }.withTrailingLine()
            payload["readable/library.csv"] = buildString {
                appendLine(csvRow("tmdb_id", "type", "title", "status", "watched", "release_date"))
                items.forEach { media ->
                    appendLine(csvRow(media.id, media.type.name.lowercase(), media.title, media.status.name.lowercase(), media.watched, media.releaseDate.orEmpty()))
                }
            }
            counts["library"] = items.size
        }

        if ("progress" in selected) {
            val items = state.playbackTv + state.playbackMovies
            payload["data/progress.jsonl"] = items.joinToString("\n") { item ->
                buildJsonObject {
                    put("tmdbId", item.media.id)
                    put("type", item.media.type.name.lowercase())
                    put("title", item.media.title)
                    item.season?.let { put("season", it) }
                    item.episodeNumber?.let { put("episode", it) }
                    item.episodeTitle?.let { put("episodeTitle", it) }
                    put("progress", item.progress.toDouble())
                }.toString()
            }.withTrailingLine()
            payload["readable/progress.csv"] = buildString {
                appendLine(csvRow("tmdb_id", "type", "title", "season", "episode", "episode_title", "progress"))
                items.forEach { item ->
                    appendLine(csvRow(item.media.id, item.media.type.name.lowercase(), item.media.title, item.season ?: "", item.episodeNumber ?: "", item.episodeTitle.orEmpty(), item.progress))
                }
            }
            counts["progress"] = items.size
        }

        if ("history" in selected) {
            val items = state.history
            payload["data/history.jsonl"] = items.joinToString("\n") { item ->
                buildJsonObject {
                    put("tmdbId", item.media.id)
                    put("type", item.media.type.name.lowercase())
                    put("title", item.media.title)
                    put("watchedAt", item.timestamp)
                    item.season?.let { put("season", it) }
                    item.episodeNumber?.let { put("episode", it) }
                    item.episodeLabel?.let { put("episodeLabel", it) }
                }.toString()
            }.withTrailingLine()
            payload["readable/history.csv"] = buildString {
                appendLine(csvRow("tmdb_id", "type", "title", "watched_at", "season", "episode", "episode_label"))
                items.forEach { item ->
                    appendLine(csvRow(item.media.id, item.media.type.name.lowercase(), item.media.title, item.timestamp, item.season ?: "", item.episodeNumber ?: "", item.episodeLabel.orEmpty()))
                }
            }
            counts["history"] = items.size
        }

        if ("personal" in selected) {
            val personal = database.personalDao()
            val known = database.mediaDao().mediaSnapshot().associateBy { "${it.mediaType}:${it.tmdbId}" }
            fun kotlinx.serialization.json.JsonObjectBuilder.title(type: String, id: Int) {
                put("type", type.lowercase())
                put("tmdbId", id)
                known["$type:$id"]?.let { media ->
                    put("title", media.title)
                    media.posterPath?.let { put("posterPath", it) }
                    media.releaseDate?.let { put("releaseDate", it) }
                }
            }
            val ratings = personal.ratingsSnapshot()
            val lists = personal.listsSnapshot()
            val listItems = personal.listItemsSnapshot()
            payload["data/personal.jsonl"] = (
                ratings.map { rating ->
                    buildJsonObject {
                        put("kind", "rating")
                        title(rating.mediaType, rating.mediaId)
                        rating.score?.let { put("score", it) }
                        put("note", rating.note)
                        put("updatedAt", rating.updatedAt)
                    }.toString()
                } + lists.map { list ->
                    buildJsonObject {
                        put("kind", "list")
                        put("listId", list.id)
                        put("name", list.name)
                        put("createdAt", list.createdAt)
                    }.toString()
                } + listItems.map { item ->
                    buildJsonObject {
                        put("kind", "listItem")
                        put("listId", item.listId)
                        title(item.mediaType, item.mediaId)
                        put("addedAt", item.addedAt)
                    }.toString()
                }
            ).joinToString("\n").withTrailingLine()
            val listNames = lists.associate { it.id to it.name }
            payload["readable/personal.csv"] = buildString {
                appendLine(csvRow("kind", "tmdb_id", "type", "title", "score", "note", "list"))
                ratings.forEach { appendLine(csvRow("rating", it.mediaId, it.mediaType.lowercase(), known["${it.mediaType}:${it.mediaId}"]?.title.orEmpty(), it.score ?: "", it.note, "")) }
                listItems.forEach { appendLine(csvRow("list", it.mediaId, it.mediaType.lowercase(), known["${it.mediaType}:${it.mediaId}"]?.title.orEmpty(), "", "", listNames[it.listId].orEmpty())) }
            }
            counts["personal"] = ratings.size + lists.size + listItems.size
        }

        if ("settings" in selected) {
            val settings = buildJsonObject {
                put("uiAccent", state.uiAccent)
                put("metadataLanguage", state.metadataLanguage)
                put("metadataRegion", state.metadataRegion)
                put("metadataTimezone", state.metadataTimezone)
                put("backgroundSync", state.backgroundSync)
                put("wifiOnly", state.wifiOnly)
                put("notificationEpisodes", state.notificationEpisodes)
                put("notificationMovies", state.notificationMovies)
                put("notificationSync", state.notificationSync)
                put("quietHoursEnabled", state.quietHoursEnabled)
                put("quietHoursStart", state.quietHoursStart)
                put("quietHoursEnd", state.quietHoursEnd)
                put("contentRegions", state.contentRegions.sorted().joinToString(","))
                put("ratingSources", state.ratingSources.sorted().joinToString(","))
                put("excludeSpecials", state.excludeSpecials)
                put("preferredProviders", state.preferredProviders.sorted().joinToString("|"))
                put("providerRegion", state.providerRegion)
                put("visibleProviderTypes", state.visibleProviderTypes.sorted().joinToString("|"))
                put("heroLayout", state.heroLayout)
                put("discoverLayout", state.discoverLayout)
                put("seasonPicker", state.seasonPicker)
                put("posterAccent", state.posterAccent)
                put("posterFormat", state.posterFormat)
                put("castPhotoShape", preferences.castPhotoShape.first())
                put("posterTitles", preferences.posterTitles.first())
                put("hideSpoilers", preferences.appearanceSettings.first().hideSpoilers)
                put("profileName", preferences.profileName.first())
                // Your photo and banner, as base64 JPEG ("" = none): small, and the settings line stays one file.
                ProfileImageKind.entries.forEach { kind ->
                    put(profileImageField(kind), preferences.profileImageBytes(kind)?.let { kotlin.io.encoding.Base64.encode(it) }.orEmpty())
                }
                put("posterSize", state.posterSize)
                put("cardDensity", state.cardDensity)
                put("hiddenDiscovery", state.hiddenDiscovery.sorted().joinToString("|"))
            }.toString()
            payload["data/settings.jsonl"] = "$settings\n"
            payload["readable/settings.csv"] = buildString {
                appendLine(csvRow("ui_accent", "metadata_language", "metadata_region", "metadata_timezone", "background_sync", "wifi_only", "content_regions", "rating_sources"))
                appendLine(csvRow(state.uiAccent, state.metadataLanguage, state.metadataRegion, state.metadataTimezone, state.backgroundSync, state.wifiOnly, state.contentRegions.sorted().joinToString("|"), state.ratingSources.sorted().joinToString("|")))
            }
            counts["settings"] = 1
        }

        val generatedAt = Instant.now().toString()
        val manifest = buildJsonObject {
            put("formatVersion", "1.0")
            put("generatedAt", generatedAt)
            put("generator", "CineTrack ${BuildConfig.VERSION_NAME}")
            putJsonObject("sections") {
                counts.forEach { (section, count) ->
                    putJsonObject(section) {
                        put("records", count)
                        put("dataFile", "data/$section.jsonl")
                        put("readableFile", "readable/$section.csv")
                        put("sha256", payload["data/$section.jsonl"].orEmpty().sha256())
                    }
                }
            }
        }.toString()
        return linkedMapOf(
            "manifest.json" to manifest,
            "README.md" to "# CineTrack backup\n\nGenerated $generatedAt. Files in `data/` are authoritative JSON Lines; `readable/` contains matching CSV exports. Authentication tokens and API credentials are excluded.\n",
        ).apply { putAll(payload) }
    }

    suspend fun createAutomaticBackup() {
        preferences.saveAutomaticBackup(exportBackupFiles(setOf("library", "progress", "history", "personal", "settings")))
        preferences.copyAutomaticBackupToFolder()
    }

    val autoBackupFolder get() = preferences.autoBackupFolder
    suspend fun setAutoBackupFolder(uri: String?) = preferences.setAutoBackupFolder(uri)
    fun autoBackupFolderName(uri: String) = preferences.autoBackupFolderName(uri)
    val autoBackupFolderFrequency get() = preferences.autoBackupFolderFrequency
    suspend fun setAutoBackupFolderFrequency(frequency: BackupFolderFrequency) = preferences.setAutoBackupFolderFrequency(frequency)

    suspend fun restoreAutomaticBackup(): Int = restoreBackupFiles(preferences.readAutomaticBackup())

    /** Validates the complete archive before a single Room row is changed. */
    /** [onStage] is told "verify" before checksums are checked and "write" before the database write. */
    suspend fun restoreBackupFiles(files: Map<String, String>, onStage: (String) -> Unit = {}): Int {
        onStage("verify")
        val manifestText = files["manifest.json"] ?: error("Backup manifest is missing")
        val manifest = Json.parseToJsonElement(manifestText).jsonObject
        check(manifest["formatVersion"]?.jsonPrimitive?.contentOrNull?.startsWith("1.") == true) {
            "Unsupported backup format"
        }
        val sections = manifest["sections"]?.jsonObject ?: error("Backup sections are missing")
        sections.forEach { (_, value) ->
            val section = value.jsonObject
            val path = section["dataFile"]?.jsonPrimitive?.contentOrNull ?: error("Backup data path is missing")
            val expected = section["sha256"]?.jsonPrimitive?.contentOrNull ?: error("Backup checksum is missing")
            val contents = files[path] ?: error("Backup file $path is missing")
            check(contents.sha256() == expected) { "Backup checksum failed for $path" }
        }

        val media = mutableListOf<MediaEntity>()
        val states = mutableListOf<UserMediaStateEntity>()
        files["data/library.jsonl"].orEmpty().lineSequence().filter(String::isNotBlank).forEach { line ->
            val item = Json.parseToJsonElement(line).jsonObject
            val id = item["tmdbId"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: error("Invalid library id")
            val type = item["type"]?.jsonPrimitive?.contentOrNull?.uppercase()?.let(MediaType::valueOf) ?: error("Invalid media type")
            val title = item["title"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank) ?: error("Invalid title")
            val status = item["status"]?.jsonPrimitive?.contentOrNull?.uppercase()?.let(LibraryStatus::valueOf) ?: LibraryStatus.NONE
            val watched = item["watched"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false
            media += MediaEntity(type.name, id, title, "", null, null, item["releaseDate"]?.jsonPrimitive?.contentOrNull, null, null, "", "", null)
            states += UserMediaStateEntity(type.name, id, status.name, watched, dirty = false)
        }
        val playback = mutableListOf<PlaybackEntity>()
        files["data/progress.jsonl"].orEmpty().lineSequence().filter(String::isNotBlank).forEach { line ->
            val item = Json.parseToJsonElement(line).jsonObject
            val id = item["tmdbId"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: error("Invalid progress id")
            val type = item["type"]?.jsonPrimitive?.contentOrNull?.uppercase()?.let(MediaType::valueOf) ?: error("Invalid progress type")
            val title = item["title"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank) ?: error("Invalid progress title")
            media += MediaEntity(type.name, id, title, "", null, null, null, null, null, "", "", null)
            playback += PlaybackEntity(
                mediaType = type.name,
                mediaId = id,
                progress = item["progress"]?.jsonPrimitive?.contentOrNull?.toFloatOrNull()?.coerceIn(0f, 1f) ?: 0f,
                positionSeconds = 0L,
                durationSeconds = 0L,
                updatedAt = Instant.now().toString(),
                season = item["season"]?.jsonPrimitive?.contentOrNull?.toIntOrNull(),
                episodeNumber = item["episode"]?.jsonPrimitive?.contentOrNull?.toIntOrNull(),
                episodeTitle = item["episodeTitle"]?.jsonPrimitive?.contentOrNull,
            )
        }
        val history = mutableListOf<WatchHistoryEntity>()
        val historyMedia = mutableListOf<MediaEntity>()
        files["data/history.jsonl"].orEmpty().lineSequence().filter(String::isNotBlank).forEach { line ->
            val item = Json.parseToJsonElement(line).jsonObject
            val id = item["tmdbId"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: error("Invalid history id")
            val type = item["type"]?.jsonPrimitive?.contentOrNull?.uppercase()?.let(MediaType::valueOf) ?: error("Invalid media type")
            val watchedAt = item["watchedAt"]?.jsonPrimitive?.contentOrNull ?: error("Invalid watch timestamp")
            item["title"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)?.let { title ->
                historyMedia += MediaEntity(type.name, id, title, "", null, null, null, null, null, "", "", null)
            }
            history += WatchHistoryEntity(
                mediaType = type.name,
                mediaId = id,
                season = item["season"]?.jsonPrimitive?.contentOrNull?.toIntOrNull(),
                episodeNumber = item["episode"]?.jsonPrimitive?.contentOrNull?.toIntOrNull(),
                episodeTitle = item["episodeLabel"]?.jsonPrimitive?.contentOrNull?.substringAfter(" · ", "")?.takeIf(String::isNotBlank),
                watchedAt = watchedAt,
            )
        }
        val settings = files["data/settings.jsonl"]?.lineSequence()?.firstOrNull(String::isNotBlank)
            ?.let { Json.parseToJsonElement(it).jsonObject }
        val personalLines = files["data/personal.jsonl"].orEmpty().lineSequence().filter(String::isNotBlank)
            .map { Json.parseToJsonElement(it).jsonObject }.toList()
        val personalMedia = personalLines.mapNotNull { item ->
            val id = item["tmdbId"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: return@mapNotNull null
            val type = item["type"]?.jsonPrimitive?.contentOrNull?.uppercase()?.let { runCatching { MediaType.valueOf(it) }.getOrNull() } ?: return@mapNotNull null
            val title = item["title"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            MediaEntity(type.name, id, title, "", item["posterPath"]?.jsonPrimitive?.contentOrNull, null, item["releaseDate"]?.jsonPrimitive?.contentOrNull, null, null, "", "", null)
        }
        check(media.isNotEmpty() || history.isNotEmpty() || playback.isNotEmpty() || settings != null || personalLines.isNotEmpty()) {
            "The backup has no restorable records"
        }
        fun WatchHistoryEntity.restoreKey() = listOf(
            mediaType,
            mediaId.toString(),
            season?.toString().orEmpty(),
            episodeNumber?.toString().orEmpty(),
            watchedAt,
        ).joinToString(":")
        val existingHistoryKeys = database.timelineDao().historySnapshot().mapTo(mutableSetOf()) { it.restoreKey() }
        val historyToInsert = history.distinctBy { it.restoreKey() }.filter { existingHistoryKeys.add(it.restoreKey()) }
        onStage("write")
        database.withTransaction {
            if (media.isNotEmpty()) database.mediaDao().upsertMedia(media.distinctBy { "${it.mediaType}:${it.tmdbId}" })
            // A title removed from the library keeps its history; without a media row the
            // history is hidden and the next backup drops it. Never overwrite a cached row.
            if (historyMedia.isNotEmpty()) {
                val known = database.mediaDao().mediaSnapshot().mapTo(mutableSetOf()) { "${it.mediaType}:${it.tmdbId}" }
                val missing = historyMedia.distinctBy { "${it.mediaType}:${it.tmdbId}" }.filter { known.add("${it.mediaType}:${it.tmdbId}") }
                if (missing.isNotEmpty()) database.mediaDao().upsertMedia(missing)
            }
            if (states.isNotEmpty()) database.stateDao().upsertAll(states.distinctBy { "${it.mediaType}:${it.mediaId}" })
            if (historyToInsert.isNotEmpty()) database.timelineDao().insertHistoryItems(historyToInsert)
            if (personalLines.isNotEmpty()) restorePersonal(personalLines, personalMedia)
            if (files.containsKey("data/progress.jsonl")) {
                database.timelineDao().clearPlayback()
                if (playback.isNotEmpty()) database.timelineDao().upsertPlayback(playback)
            }
            database.upNextDao().clear()
            database.syncDao().delete("up_next_cache_v1")
            rebuildLibraryRail()
        }
        settings?.let { saved ->
            saved["uiAccent"]?.jsonPrimitive?.contentOrNull?.let { preferences.setUiAccent(it) }
            saved["metadataLanguage"]?.jsonPrimitive?.contentOrNull?.let { metadataSettings().setMetadataLanguage(it) }
            saved["metadataRegion"]?.jsonPrimitive?.contentOrNull?.let { metadataSettings().setMetadataRegion(it) }
            saved["metadataTimezone"]?.jsonPrimitive?.contentOrNull?.let { metadataSettings().setMetadataTimezone(it) }
            saved["backgroundSync"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()?.let { preferences.setBackgroundSync(it) }
            saved["wifiOnly"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()?.let { preferences.setWifiOnly(it) }
            saved["notificationEpisodes"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()?.let { preferences.setNotification("episodes", it) }
            saved["notificationMovies"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()?.let { preferences.setNotification("movies", it) }
            saved["notificationSync"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()?.let { preferences.setNotification("sync", it) }
            val quietEnabled = saved["quietHoursEnabled"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()
            if (quietEnabled != null) {
                preferences.setQuietHours(
                    quietEnabled,
                    saved["quietHoursStart"]?.jsonPrimitive?.contentOrNull?.toIntOrNull(),
                    saved["quietHoursEnd"]?.jsonPrimitive?.contentOrNull?.toIntOrNull(),
                )
            }
            saved["contentRegions"]?.jsonPrimitive?.contentOrNull?.split(',')?.filter(String::isNotBlank)?.toSet()
                ?.let { preferences.setContentRegions(it) }
            saved["excludeSpecials"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()?.let { preferences.setExcludeSpecials(it) }
            saved["preferredProviders"]?.jsonPrimitive?.contentOrNull?.split('|')?.filter(String::isNotBlank)?.toSet()
                ?.let { preferences.setPreferredProviders(it) }
            saved["providerRegion"]?.jsonPrimitive?.contentOrNull?.let { preferences.setProviderRegion(it) }
            saved["visibleProviderTypes"]?.jsonPrimitive?.contentOrNull?.split('|')?.filter(String::isNotBlank)?.toSet()
                ?.let { preferences.setVisibleProviderTypes(it) }
            saved["heroLayout"]?.jsonPrimitive?.contentOrNull?.let { preferences.setHeroLayout(it) }
            saved["discoverLayout"]?.jsonPrimitive?.contentOrNull?.let { preferences.setDiscoverLayout(it) }
            saved["seasonPicker"]?.jsonPrimitive?.contentOrNull?.let { preferences.setSeasonPicker(it) }
            saved["posterAccent"]?.jsonPrimitive?.booleanOrNull?.let { preferences.setPosterAccent(it) }
            saved["posterFormat"]?.jsonPrimitive?.contentOrNull?.let { preferences.setPosterFormat(it) }
            saved["castPhotoShape"]?.jsonPrimitive?.contentOrNull?.let { preferences.setCastPhotoShape(it) }
            saved["posterTitles"]?.jsonPrimitive?.booleanOrNull?.let { preferences.setPosterTitles(it) }
            saved["hideSpoilers"]?.jsonPrimitive?.booleanOrNull?.let { preferences.setHideSpoilers(it) }
            saved["profileName"]?.jsonPrimitive?.contentOrNull?.let { preferences.setProfileName(it) }
            // Backups from before 0.99.78 have no images: leave the current ones.
            ProfileImageKind.entries.forEach { kind ->
                saved[profileImageField(kind)]?.jsonPrimitive?.contentOrNull?.let { encoded ->
                    val bytes = encoded.takeIf(String::isNotBlank)?.let { runCatching { kotlin.io.encoding.Base64.decode(it) }.getOrNull() }
                    runCatching { preferences.restoreProfileImage(kind, bytes) }
                }
            }
            saved["posterSize"]?.jsonPrimitive?.contentOrNull?.let { preferences.setPosterSize(it) }
            saved["cardDensity"]?.jsonPrimitive?.contentOrNull?.let { preferences.setCardDensity(it) }
            saved["hiddenDiscovery"]?.jsonPrimitive?.contentOrNull?.split('|')?.filter(String::isNotBlank)?.toSet()
                ?.let { preferences.setHiddenDiscovery(it) }
            val sources = saved["ratingSources"]?.jsonPrimitive?.contentOrNull?.split(',')?.filter(String::isNotBlank)?.toSet()
            if (sources != null) listOf("imdb", "tmdb", "metacritic", "tomatoes").forEach { source ->
                preferences.setRatingSource(source, source in sources)
            }
        }
        scheduleAutomaticBackup()
        return states.size + historyToInsert.size
    }

    /** Merges ratings (newer wins) and lists (same name = same list) into what is already here. */
    private suspend fun restorePersonal(lines: List<kotlinx.serialization.json.JsonObject>, titles: List<MediaEntity>) {
        val personal = database.personalDao()
        val known = database.mediaDao().mediaSnapshot().mapTo(mutableSetOf()) { "${it.mediaType}:${it.tmdbId}" }
        titles.distinctBy { "${it.mediaType}:${it.tmdbId}" }.filter { known.add("${it.mediaType}:${it.tmdbId}") }
            .takeIf { it.isNotEmpty() }?.let { database.mediaDao().upsertMedia(it) }
        fun kotlinx.serialization.json.JsonObject.str(key: String) = this[key]?.jsonPrimitive?.contentOrNull
        fun kotlinx.serialization.json.JsonObject.mediaKey(): Pair<String, Int>? {
            val id = str("tmdbId")?.toIntOrNull() ?: return null
            val type = str("type")?.uppercase()?.let { runCatching { MediaType.valueOf(it) }.getOrNull() } ?: return null
            return type.name to id
        }
        val current = personal.ratingsSnapshot().associateBy { it.mediaType to it.mediaId }
        val ratings = lines.filter { it.str("kind") == "rating" }.mapNotNull { item ->
            val (type, id) = item.mediaKey() ?: return@mapNotNull null
            val updatedAt = item.str("updatedAt")?.toLongOrNull() ?: 0L
            if ((current[type to id]?.updatedAt ?: -1L) >= updatedAt) return@mapNotNull null
            com.cinetrack.data.local.UserRatingEntity(type, id, item.str("score")?.toIntOrNull()?.coerceIn(1, 10), item.str("note").orEmpty(), updatedAt)
        }
        if (ratings.isNotEmpty()) personal.upsertRatings(ratings)
        val existingByName = personal.listsSnapshot().associate { it.name to it.id }.toMutableMap()
        val listIds = mutableMapOf<Long, Long>()
        lines.filter { it.str("kind") == "list" }.forEach { item ->
            val oldId = item.str("listId")?.toLongOrNull() ?: return@forEach
            val name = item.str("name")?.takeIf(String::isNotBlank) ?: return@forEach
            listIds[oldId] = existingByName.getOrPut(name) {
                personal.insertList(com.cinetrack.data.local.CustomListEntity(name = name, createdAt = item.str("createdAt")?.toLongOrNull() ?: System.currentTimeMillis()))
            }
        }
        val items = lines.filter { it.str("kind") == "listItem" }.mapNotNull { item ->
            val listId = item.str("listId")?.toLongOrNull()?.let(listIds::get) ?: return@mapNotNull null
            val (type, id) = item.mediaKey() ?: return@mapNotNull null
            com.cinetrack.data.local.CustomListItemEntity(listId, type, id, item.str("addedAt")?.toLongOrNull() ?: System.currentTimeMillis())
        }
        if (items.isNotEmpty()) personal.addItems(items)
    }

    private fun String.withTrailingLine(): String = if (isBlank()) "" else trimEnd() + "\n"

    private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(toByteArray())
        .joinToString("") { byte -> "%02x".format(byte) }

    private fun csvRow(vararg values: Any): String = values.joinToString(",") { value ->
        val escaped = value.toString().replace("\"", "\"\"")
        "\"$escaped\""
    }
}

/** The settings field holding the base64 JPEG of your profile [kind]. */
private fun profileImageField(kind: ProfileImageKind) = if (kind == ProfileImageKind.AVATAR) "profileAvatarJpeg" else "profileBannerJpeg"
