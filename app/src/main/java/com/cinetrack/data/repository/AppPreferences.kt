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
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cinetrack.BuildConfig
import com.cinetrack.data.remote.SimklAuthService
import com.cinetrack.data.sync.TrackingProviderId
import com.cinetrack.data.sync.MediaIds
import com.cinetrack.data.sync.TrackedMovieState
import com.cinetrack.data.sync.TrackedShowState
import com.cinetrack.data.sync.TrackedEpisodeState
import com.cinetrack.data.sync.TrackingSnapshot
import com.cinetrack.data.sync.TrackingWorkScheduler
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
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

private val Context.cineTrackDataStore by preferencesDataStore("cinetrack_preferences")
private const val SEARCH_HISTORY_SEPARATOR = "\u001F"

class AppPreferences(private val context: Context) {
    fun discoverTimeoutMessage(): String = context.getString(com.cinetrack.R.string.discover_refresh_timeout)
    private val errorLogFile: File get() = File(context.filesDir, "cinetrack-error-log.txt")
    private val credentialStore by lazy { SecureCredentialStore(context) }
    /** Read-only compatibility bridge for credentials saved before 0.76. */
    private val legacySecurePreferences by lazy {
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
    private fun secureCredential(key: String): String? {
        credentialStore.get(key)?.let { return it }
        val legacy = runCatching { legacySecurePreferences.getString(key, null) }.getOrNull() ?: return null
        credentialStore.put(key, legacy)
        runCatching { legacySecurePreferences.edit().remove(key).apply() }
        return legacy
    }

    private fun setSecureCredential(key: String, value: String?) {
        credentialStore.put(key, value)
        runCatching { legacySecurePreferences.edit().remove(key).apply() }
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
        val quietHoursEnabled = booleanPreferencesKey("quiet_hours_enabled")
        val quietHoursStart = intPreferencesKey("quiet_hours_start")
        val quietHoursEnd = intPreferencesKey("quiet_hours_end")
        val imdb = booleanPreferencesKey("rating_imdb")
        val tmdb = booleanPreferencesKey("rating_tmdb")
        val metacritic = booleanPreferencesKey("rating_metacritic")
        val rottenTomatoes = booleanPreferencesKey("rating_rotten_tomatoes")
        val contentRegions = stringPreferencesKey("content_regions")
        val uiAccent = stringPreferencesKey("ui_accent")
        val tmdbApiOverride = stringPreferencesKey("tmdb_api_override")
        val mdbListApiOverride = stringPreferencesKey("mdblist_api_override")
        val metadataLanguage = stringPreferencesKey("metadata_language")
        val metadataRegion = stringPreferencesKey("metadata_region")
        val providerRegion = stringPreferencesKey("provider_region")
        val metadataTimezone = stringPreferencesKey("metadata_timezone")
        val syncReport = stringPreferencesKey("sync_report")
        val excludeSpecials = booleanPreferencesKey("exclude_specials")
        val preferredProviders = stringPreferencesKey("preferred_providers")
        val visibleProviderTypes = stringPreferencesKey("visible_provider_types")
        val heroLayout = stringPreferencesKey("hero_layout")
        val posterFormat = stringPreferencesKey("poster_format")
        val posterSize = stringPreferencesKey("poster_size")
        val cardDensity = stringPreferencesKey("card_density")
        val notifiedReleases = stringPreferencesKey("notified_releases")
        val hiddenUpcoming = stringPreferencesKey("hidden_upcoming")
        val hiddenDiscovery = stringPreferencesKey("hidden_discovery")
        val searchHistory = stringPreferencesKey("search_history")
        val introductionCompleted = booleanPreferencesKey("introduction_completed")
        val mainTrackingProvider = stringPreferencesKey("main_tracking_provider")
        val secondaryTrackingProvider = stringPreferencesKey("secondary_tracking_provider")
        val syncBaseline = stringPreferencesKey("sync_baseline_v1")
    }

    val simklToken: Flow<String?> = context.cineTrackDataStore.data.map { prefs ->
        secureCredential("simkl_token") ?: prefs[Keys.simklToken]
    }
    val simklConnected: Flow<Boolean> = simklToken.map { !it.isNullOrBlank() }
    val mainTrackingProvider: Flow<TrackingProviderId?> = context.cineTrackDataStore.data.map { prefs ->
        when (val stored = prefs[Keys.mainTrackingProvider]) {
            null -> TrackingProviderId.SIMKL
            "NONE" -> null
            else -> runCatching { TrackingProviderId.valueOf(stored) }.getOrNull()
        }
    }
    val secondaryTrackingProvider: Flow<TrackingProviderId?> = context.cineTrackDataStore.data.map { prefs ->
        prefs[Keys.secondaryTrackingProvider]
            ?.let { runCatching { TrackingProviderId.valueOf(it) }.getOrNull() }
    }
    val backgroundSync: Flow<Boolean> = context.cineTrackDataStore.data.map { it[Keys.backgroundSync] ?: true }
    val wifiOnly: Flow<Boolean> = context.cineTrackDataStore.data.map { it[Keys.wifiOnly] ?: false }
    val language: Flow<String> = context.cineTrackDataStore.data.map { it[Keys.language] ?: "system" }
    val ratingSources: Flow<Set<String>> = context.cineTrackDataStore.data.map { prefs ->
        buildSet {
            if (prefs[Keys.imdb] ?: true) add("imdb")
            if (prefs[Keys.tmdb] ?: true) add("tmdb")
            if (prefs[Keys.metacritic] ?: true) add("metacritic")
            if (prefs[Keys.rottenTomatoes] ?: true) add("tomatoes")
        }
    }
    val contentRegions: Flow<Set<String>> = context.cineTrackDataStore.data.map { prefs ->
        prefs[Keys.contentRegions].orEmpty().split(',').map(String::trim).filter(String::isNotBlank).toSet()
    }
    val uiAccent: Flow<String> = context.cineTrackDataStore.data.map { it[Keys.uiAccent] ?: "watching" }
    val metadataLanguage: Flow<String> = context.cineTrackDataStore.data.map { it[Keys.metadataLanguage] ?: "system" }
    val providerRegion: Flow<String> = context.cineTrackDataStore.data.map { it[Keys.providerRegion] ?: "system" }
    suspend fun setProviderRegion(value: String) {
        context.cineTrackDataStore.edit { it[Keys.providerRegion] = value }
    }
    val metadataRegion: Flow<String> = context.cineTrackDataStore.data.map { it[Keys.metadataRegion] ?: "system" }
    val metadataTimezone: Flow<String> = context.cineTrackDataStore.data.map { it[Keys.metadataTimezone] ?: "system" }
    val excludeSpecials: Flow<Boolean> = context.cineTrackDataStore.data.map { it[Keys.excludeSpecials] ?: true }
    val visibleProviderTypes: Flow<Set<String>> = context.cineTrackDataStore.data.map { prefs ->
        prefs[Keys.visibleProviderTypes]?.split('|')?.filter(String::isNotBlank)?.toSet()
            ?: setOf("flatrate", "rent", "buy", "free", "ads")
    }
    suspend fun setVisibleProviderTypes(values: Set<String>) {
        context.cineTrackDataStore.edit { it[Keys.visibleProviderTypes] = values.sorted().joinToString("|") }
    }

    val preferredProviders: Flow<Set<String>> = context.cineTrackDataStore.data.map {
        it[Keys.preferredProviders].orEmpty().split('|').filter(String::isNotBlank).toSet()
    }
    val heroLayout: Flow<String> = context.cineTrackDataStore.data.map { com.cinetrack.domain.CardAppearance.normalizeHero(it[Keys.heroLayout] ?: "standard") }
    val posterFormat: Flow<String> = context.cineTrackDataStore.data.map { com.cinetrack.domain.CardAppearance.normalizeFormat(it[Keys.posterFormat] ?: "classic") }
    val posterSize: Flow<String> = context.cineTrackDataStore.data.map { com.cinetrack.domain.CardAppearance.normalizeSize(it[Keys.posterSize] ?: "standard") }
    val cardDensity: Flow<String> = context.cineTrackDataStore.data.map { it[Keys.cardDensity] ?: "standard" }
    val notificationEpisodes: Flow<Boolean> = context.cineTrackDataStore.data.map { it[Keys.notifyEpisodes] ?: true }
    val notificationMovies: Flow<Boolean> = context.cineTrackDataStore.data.map { it[Keys.notifyMovies] ?: true }
    val notificationSync: Flow<Boolean> = context.cineTrackDataStore.data.map { it[Keys.notifySync] ?: true }
    val quietHoursEnabled: Flow<Boolean> = context.cineTrackDataStore.data.map { it[Keys.quietHoursEnabled] ?: true }
    val quietHoursStart: Flow<Int> = context.cineTrackDataStore.data.map { it[Keys.quietHoursStart] ?: 23 }
    val quietHoursEnd: Flow<Int> = context.cineTrackDataStore.data.map { it[Keys.quietHoursEnd] ?: 8 }
    val hiddenUpcoming: Flow<Set<String>> = context.cineTrackDataStore.data.map {
        it[Keys.hiddenUpcoming].orEmpty().split('|').filter(String::isNotBlank).toSet()
    }
    val hiddenDiscovery: Flow<Set<String>> = context.cineTrackDataStore.data.map {
        it[Keys.hiddenDiscovery].orEmpty().split('|').filter(String::isNotBlank).toSet()
    }
    val searchHistory: Flow<List<String>> = context.cineTrackDataStore.data.map { prefs ->
        prefs[Keys.searchHistory].orEmpty().split(SEARCH_HISTORY_SEPARATOR).filter(String::isNotBlank)
    }
    val introductionCompleted: Flow<Boolean> = context.cineTrackDataStore.data.map {
        it[Keys.introductionCompleted] ?: false
    }

    suspend fun tokenNow(): String? {
        secureCredential("simkl_token")?.takeIf(String::isNotBlank)?.let { return it }
        val legacy = context.cineTrackDataStore.data.first()[Keys.simklToken]?.takeIf(String::isNotBlank) ?: return null
        setSecureCredential("simkl_token", legacy)
        context.cineTrackDataStore.edit { it.remove(Keys.simklToken) }
        return legacy
    }

    suspend fun tmdbApiKeyNow(): String {
        secureCredential("tmdb_api_override")?.takeIf(String::isNotBlank)?.let { return it }
        val legacy = context.cineTrackDataStore.data.first()[Keys.tmdbApiOverride]?.takeIf(String::isNotBlank)
        if (legacy != null) {
            setSecureCredential("tmdb_api_override", legacy)
            context.cineTrackDataStore.edit { it.remove(Keys.tmdbApiOverride) }
            return legacy
        }
        return BuildConfig.TMDB_API_TOKEN
    }

    suspend fun mdbListApiKeyNow(): String {
        secureCredential("mdblist_api_override")?.takeIf(String::isNotBlank)?.let { return it }
        val legacy = context.cineTrackDataStore.data.first()[Keys.mdbListApiOverride]?.takeIf(String::isNotBlank)
        if (legacy != null) {
            setSecureCredential("mdblist_api_override", legacy)
            context.cineTrackDataStore.edit { it.remove(Keys.mdbListApiOverride) }
            return legacy
        }
        return BuildConfig.MDBLIST_API_KEY
    }

    private fun trackingLastCheckKey(provider: TrackingProviderId) = longPreferencesKey("tracking_last_sync_${provider.name}")

    suspend fun trackingLastCheckAt(provider: TrackingProviderId): Long? {
        val values = context.cineTrackDataStore.data.first()
        return values[trackingLastCheckKey(provider)]
            ?: values.takeIf { provider == TrackingProviderId.SIMKL }?.get(Keys.simklLastCheckAt)
    }

    suspend fun simklLastCheckAt(): Long? = trackingLastCheckAt(TrackingProviderId.SIMKL)

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
        markTrackingChecked(TrackingProviderId.SIMKL, at)
    }

    suspend fun markTrackingChecked(provider: TrackingProviderId, at: Long = System.currentTimeMillis()) {
        context.cineTrackDataStore.edit {
            it[trackingLastCheckKey(provider)] = at
            if (provider == TrackingProviderId.SIMKL) it[Keys.simklLastCheckAt] = at
        }
    }

    private fun syncBaselineKey(provider: TrackingProviderId) = stringPreferencesKey("sync_baseline_${provider.name.lowercase()}_v1")

    suspend fun syncBaselineNow(provider: TrackingProviderId = TrackingProviderId.SIMKL): TrackingSnapshot? {
        val values = context.cineTrackDataStore.data.first()
        val raw = values[syncBaselineKey(provider)]
            ?: values.takeIf { provider == TrackingProviderId.SIMKL }?.get(Keys.syncBaseline)
            ?: return null
        val movies = mutableListOf<TrackedMovieState>()
        val shows = mutableListOf<TrackedShowState>()
        val episodes = mutableListOf<TrackedEpisodeState>()
        raw.lineSequence().forEach { line ->
            val p = line.split('|')
            when (p.firstOrNull()) {
                "M" -> p.getOrNull(1)?.toLongOrNull()?.let { movies += TrackedMovieState(MediaIds(tmdb = it), p.getOrNull(2)?.let { s -> runCatching { com.cinetrack.domain.LibraryStatus.valueOf(s) }.getOrNull() }, p.getOrNull(3) == "1") }
                "S" -> p.getOrNull(1)?.toLongOrNull()?.let { shows += TrackedShowState(MediaIds(tmdb = it), p.getOrNull(2)?.let { s -> runCatching { com.cinetrack.domain.LibraryStatus.valueOf(s) }.getOrNull() }) }
                "E" -> if (p.size >= 5) p[1].toLongOrNull()?.let { tmdb -> p[2].toIntOrNull()?.let { season -> p[3].toIntOrNull()?.let { episode -> episodes += TrackedEpisodeState(MediaIds(tmdb = tmdb), season, episode, p[4] == "1") } } }
            }
        }
        return TrackingSnapshot(movies = movies, shows = shows, episodes = episodes)
    }

    suspend fun saveSyncBaseline(snapshot: TrackingSnapshot, provider: TrackingProviderId = TrackingProviderId.SIMKL) {
        val encoded = buildString {
            snapshot.movies.forEach { append("M|").append(it.ids.tmdb ?: return@forEach).append('|').append(it.libraryState?.name.orEmpty()).append('|').append(if (it.watched) '1' else '0').append('\n') }
            snapshot.shows.forEach { append("S|").append(it.ids.tmdb ?: return@forEach).append('|').append(it.libraryState?.name.orEmpty()).append('\n') }
            snapshot.episodes.forEach { append("E|").append(it.showIds.tmdb ?: return@forEach).append('|').append(it.season).append('|').append(it.episode).append('|').append(if (it.watched) '1' else '0').append('\n') }
        }
        context.cineTrackDataStore.edit { it[syncBaselineKey(provider)] = encoded }
    }

    suspend fun setTrackingProviders(main: TrackingProviderId?, secondary: TrackingProviderId?) {
        require(main == null || main != secondary) { "The same tracking provider cannot be both MAIN and SECONDARY" }
        context.cineTrackDataStore.edit { prefs ->
            prefs[Keys.mainTrackingProvider] = main?.name ?: "NONE"
            if (secondary == null) prefs.remove(Keys.secondaryTrackingProvider)
            else prefs[Keys.secondaryTrackingProvider] = secondary.name
        }
    }

    suspend fun setToken(value: String?) {
        setSecureCredential("simkl_token", value)
        context.cineTrackDataStore.edit { prefs ->
            prefs.remove(Keys.simklToken)
            // A different account must always receive its own initial activity check.
            prefs.remove(Keys.simklLastCheckAt)
        }
    }

    suspend fun setBackgroundSync(enabled: Boolean) {
        context.cineTrackDataStore.edit { it[Keys.backgroundSync] = enabled }
        TrackingWorkScheduler.update(context, enabled = enabled, wifiOnly = wifiOnly.first())
    }

    suspend fun setWifiOnly(enabled: Boolean) {
        context.cineTrackDataStore.edit { it[Keys.wifiOnly] = enabled }
        TrackingWorkScheduler.update(context, enabled = backgroundSync.first(), wifiOnly = enabled)
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

    suspend fun setQuietHours(enabled: Boolean, startHour: Int? = null, endHour: Int? = null) {
        context.cineTrackDataStore.edit { preferences ->
            preferences[Keys.quietHoursEnabled] = enabled
            startHour?.let { preferences[Keys.quietHoursStart] = it.coerceIn(0, 23) }
            endHour?.let { preferences[Keys.quietHoursEnd] = it.coerceIn(0, 23) }
        }
    }

    suspend fun setRatingSource(source: String, enabled: Boolean) {
        val key = when (source.lowercase()) {
            "imdb" -> Keys.imdb
            "tmdb" -> Keys.tmdb
            "metacritic" -> Keys.metacritic
            "rotten tomatoes", "tomatoes" -> Keys.rottenTomatoes
            else -> return
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
        setSecureCredential("tmdb_api_override", value?.trim())
        context.cineTrackDataStore.edit { prefs ->
            prefs.remove(Keys.tmdbApiOverride)
        }
    }

    suspend fun setMdbListApiKey(value: String?) {
        setSecureCredential("mdblist_api_override", value?.trim())
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

    suspend fun setPreferredProviders(values: Set<String>) {
        context.cineTrackDataStore.edit { prefs ->
            if (values.isEmpty()) prefs.remove(Keys.preferredProviders)
            else prefs[Keys.preferredProviders] = values.sorted().joinToString("|")
        }
    }

    suspend fun setHeroLayout(value: String) {
        context.cineTrackDataStore.edit { it[Keys.heroLayout] = com.cinetrack.domain.CardAppearance.normalizeHero(value) }
    }

    suspend fun setPosterFormat(value: String) {
        context.cineTrackDataStore.edit { it[Keys.posterFormat] = com.cinetrack.domain.CardAppearance.normalizeFormat(value) }
    }

    suspend fun setPosterSize(value: String) {
        context.cineTrackDataStore.edit { it[Keys.posterSize] = com.cinetrack.domain.CardAppearance.normalizeSize(value) }
    }

    suspend fun setCardDensity(value: String) {
        context.cineTrackDataStore.edit { it[Keys.cardDensity] = value }
    }

    suspend fun notifiedReleaseKeys(): Set<String> = context.cineTrackDataStore.data.first()[Keys.notifiedReleases]
        .orEmpty().split('|').filter(String::isNotBlank).toSet()

    suspend fun setNotifiedReleaseKeys(values: Set<String>) {
        context.cineTrackDataStore.edit { it[Keys.notifiedReleases] = values.toList().takeLast(200).joinToString("|") }
    }

    suspend fun markReleaseNotified(key: String): Boolean {
        var added = false
        context.cineTrackDataStore.edit { values ->
            val current = values[Keys.notifiedReleases].orEmpty().split('|').filter(String::isNotBlank).toMutableSet()
            added = current.add(key)
            if (added) values[Keys.notifiedReleases] = current.toList().takeLast(200).joinToString("|")
        }
        return added
    }

    suspend fun setHiddenUpcoming(values: Set<String>) {
        context.cineTrackDataStore.edit { prefs ->
            if (values.isEmpty()) prefs.remove(Keys.hiddenUpcoming)
            else prefs[Keys.hiddenUpcoming] = values.joinToString("|")
        }
    }

    suspend fun addSearchHistory(query: String) {
        val normalized = query.trim().replace(SEARCH_HISTORY_SEPARATOR, " ").replace(Regex("\\s+"), " ")
        if (normalized.isBlank()) return
        context.cineTrackDataStore.edit { prefs ->
            val current = prefs[Keys.searchHistory].orEmpty().split(SEARCH_HISTORY_SEPARATOR).filter(String::isNotBlank)
            val updated = listOf(normalized) + current.filterNot { it.equals(normalized, ignoreCase = true) }
            prefs[Keys.searchHistory] = updated.take(10).joinToString(SEARCH_HISTORY_SEPARATOR)
        }
    }

    suspend fun removeSearchHistory(query: String) {
        context.cineTrackDataStore.edit { prefs ->
            val updated = prefs[Keys.searchHistory].orEmpty().split(SEARCH_HISTORY_SEPARATOR)
                .filter { it.isNotBlank() && !it.equals(query, ignoreCase = true) }
            if (updated.isEmpty()) prefs.remove(Keys.searchHistory)
            else prefs[Keys.searchHistory] = updated.joinToString(SEARCH_HISTORY_SEPARATOR)
        }
    }

    suspend fun setHiddenDiscovery(values: Set<String>) {
        context.cineTrackDataStore.edit { prefs ->
            if (values.isEmpty()) prefs.remove(Keys.hiddenDiscovery)
            else prefs[Keys.hiddenDiscovery] = values.joinToString("|")
        }
    }

    suspend fun setIntroductionCompleted(value: Boolean) {
        context.cineTrackDataStore.edit { it[Keys.introductionCompleted] = value }
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
            val previousOne = File(directory, "cinetrack-auto-backup.1.zip")
            val previousTwo = File(directory, "cinetrack-auto-backup.2.zip")
            if (previousOne.exists()) previousOne.copyTo(previousTwo, overwrite = true)
            if (target.exists()) target.copyTo(previousOne, overwrite = true)
            if (target.exists()) target.delete()
            check(temporary.renameTo(target)) { "Could not publish automatic backup" }
        }.onFailure { appendErrorLog("${java.time.Instant.now()}  Automatic backup: ${it.message}") }
    }

    fun readAutomaticBackup(): Map<String, String> {
        val source = File(context.filesDir, "backups/cinetrack-auto-backup.zip")
        check(source.exists()) { "No automatic backup is available" }
        return linkedMapOf<String, String>().also { entries ->
            ZipInputStream(FileInputStream(source)).use { archive ->
                var totalBytes = 0L
                var entry = archive.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        check(entries.size < 32) { "Automatic backup contains too many entries" }
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var entryBytes = 0L
                        while (true) {
                            val read = archive.read(buffer)
                            if (read <= 0) break
                            entryBytes += read
                            totalBytes += read
                            check(entryBytes <= 32L * 1024 * 1024) { "Automatic backup entry is too large" }
                            check(totalBytes <= 64L * 1024 * 1024) { "Automatic backup is too large" }
                            output.write(buffer, 0, read)
                        }
                        entries[entry.name] = output.toByteArray().decodeToString()
                    }
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
        setSecureCredential("simkl_token", response.accessToken)
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

