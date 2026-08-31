package com.cinetrack.data.repository

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.browser.customtabs.CustomTabsIntent
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cinetrack.BuildConfig
import com.cinetrack.data.remote.SimklAuthService
import com.cinetrack.data.sync.SimklWorkScheduler
import com.cinetrack.domain.SyncReport
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import retrofit2.HttpException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import java.io.File
import java.io.FileOutputStream
import java.io.FileInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

private val Context.cineTrackDataStore by preferencesDataStore("cinetrack_preferences")

class AppPreferences(private val context: Context) {
    private val errorLogFile: File get() = File(context.filesDir, "cinetrack-error-log.txt")
    private val securePreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "cinetrack_secure_credentials",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }
    private object Keys {
        val simklToken = stringPreferencesKey("simkl_token")
        val simklLastCheckAt = longPreferencesKey("simkl_last_check_at")
        val pkceVerifier = stringPreferencesKey("simkl_pkce_verifier")
        val pkceState = stringPreferencesKey("simkl_pkce_state")
        val backgroundSync = booleanPreferencesKey("background_sync")
        val wifiOnly = booleanPreferencesKey("wifi_only")
        val language = stringPreferencesKey("language")
        val notifyEpisodes = booleanPreferencesKey("notify_episodes")
        val notifyMovies = booleanPreferencesKey("notify_movies")
        val notifySync = booleanPreferencesKey("notify_sync")
        val imdb = booleanPreferencesKey("rating_imdb")
        val tmdb = booleanPreferencesKey("rating_tmdb")
        val metacritic = booleanPreferencesKey("rating_metacritic")
        val rottenTomatoes = booleanPreferencesKey("rating_rotten_tomatoes")
        val letterboxd = booleanPreferencesKey("rating_letterboxd")
        val contentRegions = stringPreferencesKey("content_regions")
        val uiAccent = stringPreferencesKey("ui_accent")
        val tmdbApiOverride = stringPreferencesKey("tmdb_api_override")
        val mdbListApiOverride = stringPreferencesKey("mdblist_api_override")
        val metadataLanguage = stringPreferencesKey("metadata_language")
        val metadataRegion = stringPreferencesKey("metadata_region")
        val metadataTimezone = stringPreferencesKey("metadata_timezone")
        val syncReport = stringPreferencesKey("sync_report")
        val excludeSpecials = booleanPreferencesKey("exclude_specials")
        val discoverFilterPreset = stringPreferencesKey("discover_filter_preset")
        val preferredProviders = stringPreferencesKey("preferred_providers")
        val cardDensity = stringPreferencesKey("card_density")
        val notifiedReleases = stringPreferencesKey("notified_releases")
        val hiddenUpcoming = stringPreferencesKey("hidden_upcoming")
        val hiddenDiscovery = stringPreferencesKey("hidden_discovery")
    }

    val simklToken: Flow<String?> = context.cineTrackDataStore.data.map { prefs ->
        securePreferences.getString("simkl_token", null) ?: prefs[Keys.simklToken]
    }
    val simklConnected: Flow<Boolean> = simklToken.map { !it.isNullOrBlank() }
    val backgroundSync: Flow<Boolean> = context.cineTrackDataStore.data.map { it[Keys.backgroundSync] ?: true }
    val wifiOnly: Flow<Boolean> = context.cineTrackDataStore.data.map { it[Keys.wifiOnly] ?: false }
    val language: Flow<String> = context.cineTrackDataStore.data.map { it[Keys.language] ?: "system" }
    val ratingSources: Flow<Set<String>> = context.cineTrackDataStore.data.map { prefs ->
        buildSet {
            if (prefs[Keys.imdb] ?: true) add("imdb")
            if (prefs[Keys.tmdb] ?: true) add("tmdb")
            if (prefs[Keys.metacritic] ?: true) add("metacritic")
            if (prefs[Keys.rottenTomatoes] ?: true) add("tomatoes")
            if (prefs[Keys.letterboxd] ?: false) add("letterboxd")
        }
    }
    val contentRegions: Flow<Set<String>> = context.cineTrackDataStore.data.map { prefs ->
        prefs[Keys.contentRegions].orEmpty().split(',').map(String::trim).filter(String::isNotBlank).toSet()
    }
    val uiAccent: Flow<String> = context.cineTrackDataStore.data.map { it[Keys.uiAccent] ?: "watching" }
    val metadataLanguage: Flow<String> = context.cineTrackDataStore.data.map { it[Keys.metadataLanguage] ?: "system" }
    val metadataRegion: Flow<String> = context.cineTrackDataStore.data.map { it[Keys.metadataRegion] ?: "system" }
    val metadataTimezone: Flow<String> = context.cineTrackDataStore.data.map { it[Keys.metadataTimezone] ?: "system" }
    val excludeSpecials: Flow<Boolean> = context.cineTrackDataStore.data.map { it[Keys.excludeSpecials] ?: true }
    val discoverFilterPreset: Flow<String> = context.cineTrackDataStore.data.map { it[Keys.discoverFilterPreset].orEmpty() }
    val preferredProviders: Flow<Set<String>> = context.cineTrackDataStore.data.map {
        it[Keys.preferredProviders].orEmpty().split('|').filter(String::isNotBlank).toSet()
    }
    val cardDensity: Flow<String> = context.cineTrackDataStore.data.map { it[Keys.cardDensity] ?: "standard" }
    val notificationEpisodes: Flow<Boolean> = context.cineTrackDataStore.data.map { it[Keys.notifyEpisodes] ?: true }
    val notificationMovies: Flow<Boolean> = context.cineTrackDataStore.data.map { it[Keys.notifyMovies] ?: true }
    val hiddenUpcoming: Flow<Set<String>> = context.cineTrackDataStore.data.map {
        it[Keys.hiddenUpcoming].orEmpty().split('|').filter(String::isNotBlank).toSet()
    }
    val hiddenDiscovery: Flow<Set<String>> = context.cineTrackDataStore.data.map {
        it[Keys.hiddenDiscovery].orEmpty().split('|').filter(String::isNotBlank).toSet()
    }

    suspend fun tokenNow(): String? = simklToken.first()
    suspend fun tmdbApiKeyNow(): String = securePreferences.getString("tmdb_api_override", null)
        ?.takeIf(String::isNotBlank)
        ?: context.cineTrackDataStore.data.first()[Keys.tmdbApiOverride]?.takeIf(String::isNotBlank)
        ?: BuildConfig.TMDB_API_TOKEN
    suspend fun mdbListApiKeyNow(): String = securePreferences.getString("mdblist_api_override", null)
        ?.takeIf(String::isNotBlank)
        ?: context.cineTrackDataStore.data.first()[Keys.mdbListApiOverride]?.takeIf(String::isNotBlank)
        ?: BuildConfig.MDBLIST_API_KEY

    suspend fun simklLastCheckAt(): Long? = context.cineTrackDataStore.data.first()[Keys.simklLastCheckAt]

    suspend fun syncReportNow(): SyncReport {
        val values = context.cineTrackDataStore.data.first()[Keys.syncReport].orEmpty().split('|')
        fun intAt(index: Int) = values.getOrNull(index)?.toIntOrNull() ?: 0
        fun longAt(index: Int) = values.getOrNull(index)?.toLongOrNull()?.takeIf { it > 0L }
        return SyncReport(
            downloaded = intAt(0), uploaded = intAt(1), added = intAt(2), removed = intAt(3),
            unchanged = intAt(4), pendingLocalChanges = intAt(5), failedOperations = intAt(6),
            conflicts = intAt(7), lastFullSync = longAt(8), lastIncrementalSync = longAt(9),
            databaseUntouched = values.getOrNull(10) == "1",
        )
    }

    suspend fun saveSyncReport(report: SyncReport) {
        val encoded = listOf(
            report.downloaded, report.uploaded, report.added, report.removed, report.unchanged,
            report.pendingLocalChanges, report.failedOperations, report.conflicts,
            report.lastFullSync ?: 0L, report.lastIncrementalSync ?: 0L,
            if (report.databaseUntouched) 1 else 0,
        ).joinToString("|")
        context.cineTrackDataStore.edit { it[Keys.syncReport] = encoded }
    }

    suspend fun markSimklChecked(at: Long = System.currentTimeMillis()) {
        context.cineTrackDataStore.edit { it[Keys.simklLastCheckAt] = at }
    }

    suspend fun setToken(value: String?) {
        securePreferences.edit().apply {
            if (value.isNullOrBlank()) remove("simkl_token") else putString("simkl_token", value)
        }.apply()
        context.cineTrackDataStore.edit { prefs ->
            prefs.remove(Keys.simklToken)
            // A different account must always receive its own initial activity check.
            prefs.remove(Keys.simklLastCheckAt)
        }
    }

    suspend fun setBackgroundSync(enabled: Boolean) {
        context.cineTrackDataStore.edit { it[Keys.backgroundSync] = enabled }
        SimklWorkScheduler.update(context, enabled = enabled, wifiOnly = wifiOnly.first())
    }

    suspend fun setWifiOnly(enabled: Boolean) {
        context.cineTrackDataStore.edit { it[Keys.wifiOnly] = enabled }
        SimklWorkScheduler.update(context, enabled = backgroundSync.first(), wifiOnly = enabled)
    }
    suspend fun setLanguage(value: String) {
        context.cineTrackDataStore.edit { it[Keys.language] = value }
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(value))
    }

    suspend fun setNotification(kind: String, enabled: Boolean) {
        val key = when (kind) {
            "episodes" -> Keys.notifyEpisodes
            "movies" -> Keys.notifyMovies
            else -> Keys.notifySync
        }
        context.cineTrackDataStore.edit { it[key] = enabled }
    }

    suspend fun setRatingSource(source: String, enabled: Boolean) {
        val key = when (source.lowercase()) {
            "imdb" -> Keys.imdb
            "tmdb" -> Keys.tmdb
            "metacritic" -> Keys.metacritic
            "rotten tomatoes", "tomatoes" -> Keys.rottenTomatoes
            else -> Keys.letterboxd
        }
        context.cineTrackDataStore.edit { it[key] = enabled }
    }

    suspend fun setContentRegions(regions: Set<String>) {
        context.cineTrackDataStore.edit { prefs ->
            if (regions.isEmpty()) prefs.remove(Keys.contentRegions)
            else prefs[Keys.contentRegions] = regions.map { it.uppercase() }.sorted().joinToString(",")
        }
    }

    suspend fun setUiAccent(value: String) {
        context.cineTrackDataStore.edit { it[Keys.uiAccent] = value }
    }

    suspend fun setTmdbApiKey(value: String?) {
        securePreferences.edit().apply {
            if (value.isNullOrBlank()) remove("tmdb_api_override") else putString("tmdb_api_override", value.trim())
        }.apply()
        context.cineTrackDataStore.edit { prefs ->
            prefs.remove(Keys.tmdbApiOverride)
        }
    }

    suspend fun setMdbListApiKey(value: String?) {
        securePreferences.edit().apply {
            if (value.isNullOrBlank()) remove("mdblist_api_override") else putString("mdblist_api_override", value.trim())
        }.apply()
        context.cineTrackDataStore.edit { prefs ->
            prefs.remove(Keys.mdbListApiOverride)
        }
    }

    suspend fun setMetadataLanguage(value: String) {
        context.cineTrackDataStore.edit { it[Keys.metadataLanguage] = value }
    }

    suspend fun setMetadataRegion(value: String) {
        context.cineTrackDataStore.edit { it[Keys.metadataRegion] = value }
    }

    suspend fun setMetadataTimezone(value: String) {
        context.cineTrackDataStore.edit { it[Keys.metadataTimezone] = value }
    }

    suspend fun setExcludeSpecials(value: Boolean) {
        context.cineTrackDataStore.edit { it[Keys.excludeSpecials] = value }
    }

    suspend fun setDiscoverFilterPreset(value: String) {
        context.cineTrackDataStore.edit { it[Keys.discoverFilterPreset] = value }
    }

    suspend fun setPreferredProviders(values: Set<String>) {
        context.cineTrackDataStore.edit { prefs ->
            if (values.isEmpty()) prefs.remove(Keys.preferredProviders)
            else prefs[Keys.preferredProviders] = values.sorted().joinToString("|")
        }
    }

    suspend fun setCardDensity(value: String) {
        context.cineTrackDataStore.edit { it[Keys.cardDensity] = value }
    }

    suspend fun notifiedReleaseKeys(): Set<String> = context.cineTrackDataStore.data.first()[Keys.notifiedReleases]
        .orEmpty().split('|').filter(String::isNotBlank).toSet()

    suspend fun setNotifiedReleaseKeys(values: Set<String>) {
        context.cineTrackDataStore.edit { it[Keys.notifiedReleases] = values.toList().takeLast(200).joinToString("|") }
    }

    suspend fun setHiddenUpcoming(values: Set<String>) {
        context.cineTrackDataStore.edit { prefs ->
            if (values.isEmpty()) prefs.remove(Keys.hiddenUpcoming)
            else prefs[Keys.hiddenUpcoming] = values.joinToString("|")
        }
    }

    suspend fun setHiddenDiscovery(values: Set<String>) {
        context.cineTrackDataStore.edit { prefs ->
            if (values.isEmpty()) prefs.remove(Keys.hiddenDiscovery)
            else prefs[Keys.hiddenDiscovery] = values.joinToString("|")
        }
    }

    fun readErrorLogs(): List<String> = runCatching {
        if (errorLogFile.exists()) errorLogFile.readLines().filter(String::isNotBlank).takeLast(200) else emptyList()
    }.getOrDefault(emptyList())

    fun appendErrorLog(line: String) {
        runCatching {
            val lines = (readErrorLogs() + line).takeLast(200)
            errorLogFile.writeText(lines.joinToString(separator = "\n", postfix = "\n"))
        }
    }

    fun saveAutomaticBackup(files: Map<String, String>) {
        runCatching {
            val directory = File(context.filesDir, "backups").apply { mkdirs() }
            val target = File(directory, "cinetrack-auto-backup.zip")
            val temporary = File(directory, "cinetrack-auto-backup.tmp")
            ZipOutputStream(FileOutputStream(temporary)).use { archive ->
                files.forEach { (path, contents) ->
                    archive.putNextEntry(ZipEntry(path))
                    archive.write(contents.toByteArray())
                    archive.closeEntry()
                }
            }
            check(temporary.length() > 0L) { "Automatic backup is empty" }
            if (target.exists()) target.delete()
            check(temporary.renameTo(target)) { "Could not publish automatic backup" }
        }.onFailure { appendErrorLog("${java.time.Instant.now()}  Automatic backup: ${it.message}") }
    }

    fun readAutomaticBackup(): Map<String, String> {
        val source = File(context.filesDir, "backups/cinetrack-auto-backup.zip")
        check(source.exists()) { "No automatic backup is available" }
        return linkedMapOf<String, String>().also { entries ->
            ZipInputStream(FileInputStream(source)).use { archive ->
                var entry = archive.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) entries[entry.name] = archive.readBytes().decodeToString()
                    archive.closeEntry()
                    entry = archive.nextEntry
                }
            }
        }
    }

    suspend fun beginSimklLogin(context: Context): Result<Unit> = runCatching {
        check(BuildConfig.SIMKL_CLIENT_ID.isNotBlank()) { "SIMKL_CLIENT_ID is missing" }
        val verifierBytes = ByteArray(48).also(SecureRandom()::nextBytes)
        val verifier = Base64.encodeToString(verifierBytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        val challenge = Base64.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()),
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP,
        )
        val state = UUID.randomUUID().toString()
        context.cineTrackDataStore.edit {
            it[Keys.pkceVerifier] = verifier
            it[Keys.pkceState] = state
        }
        val uri = Uri.parse("https://simkl.com/oauth/authorize").buildUpon()
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", BuildConfig.SIMKL_CLIENT_ID)
            .appendQueryParameter("redirect_uri", BuildConfig.SIMKL_REDIRECT_URI)
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("state", state)
            .appendQueryParameter("app-name", "cinetrack")
            .appendQueryParameter("app-version", BuildConfig.VERSION_NAME)
            .build()
        CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, uri)
    }

    suspend fun completeSimklLogin(code: String, returnedState: String?, service: SimklAuthService): Result<Unit> = runCatching {
        val session = context.cineTrackDataStore.data.first()
        val verifier = session[Keys.pkceVerifier]
            ?: error("The Simkl login session has expired")
        val expectedState = session[Keys.pkceState]
            ?: error("The Simkl login state is missing. Please connect again")
        check(!returnedState.isNullOrBlank() && returnedState == expectedState) {
            "Simkl returned an invalid login state. Please connect again"
        }
        val response = service.exchangeCode(
            code = code,
            clientId = BuildConfig.SIMKL_CLIENT_ID,
            codeVerifier = verifier,
            redirectUri = BuildConfig.SIMKL_REDIRECT_URI,
        )
        securePreferences.edit().putString("simkl_token", response.accessToken).apply()
        context.cineTrackDataStore.edit {
            it.remove(Keys.simklToken)
            it.remove(Keys.pkceVerifier)
            it.remove(Keys.pkceState)
            it.remove(Keys.simklLastCheckAt)
        }
        Unit
    }.recoverCatching { cause ->
        if (cause is HttpException) {
            val serverMessage = cause.response()?.errorBody()?.string()?.take(500).orEmpty()
            error("Simkl authorization failed (${cause.code()})${serverMessage.takeIf(String::isNotBlank)?.let { ": $it" }.orEmpty()}")
        }
        throw cause
    }
}
