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
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cinetrack.BuildConfig
import com.cinetrack.data.remote.SimklAuthService
import com.cinetrack.data.sync.ProviderBootstrapState
import com.cinetrack.data.sync.MediaIds
import com.cinetrack.data.sync.TrackedMovieState
import com.cinetrack.data.sync.TrackedShowState
import com.cinetrack.data.sync.TrackedEpisodeState
import com.cinetrack.data.sync.TrackingSnapshot
import com.cinetrack.data.sync.TrackingWorkScheduler
import com.cinetrack.data.sync.floppy.FloppyCapabilities
import com.cinetrack.data.sync.floppy.FloppyConnectionSettings
import com.cinetrack.data.sync.floppy.sameFloppyRemoteTarget
import com.cinetrack.data.sync.TrackingProviderId
import com.cinetrack.data.sync.simkl.SimklFirstSyncMode
import com.cinetrack.domain.SimklAuthRules
import com.cinetrack.domain.SyncReport
import com.cinetrack.ui.search.SearchHistoryStore
import com.cinetrack.ui.settings.SettingsPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
private const val ERROR_LOG_LINES = 500

class AppPreferences(private val context: Context) : SettingsPreferences, SearchHistoryStore {
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
        val simklTokenExpiresAt = longPreferencesKey("simkl_token_expires_at")
        /** Why a Simkl grant was dropped ("upgrade" for a retired V1 token, "signed_out" otherwise), until the user reconnects. */
        val simklReconnectReason = stringPreferencesKey("simkl_reconnect_reason")
        /** How the next sync combines a newly connected account with this device; absent once done. */
        val simklFirstSyncMode = stringPreferencesKey("simkl_first_sync_mode")
        val floppyPkceVerifier = stringPreferencesKey("floppy_pkce_verifier")
        val floppyPkceState = stringPreferencesKey("floppy_pkce_state")
        val floppyPendingBaseUrl = stringPreferencesKey("floppy_pending_base_url")
        val floppyPendingAllowHttp = booleanPreferencesKey("floppy_pending_allow_http")
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
        val airDateSource = stringPreferencesKey("air_date_source")
        val simklUsername = stringPreferencesKey("simkl_username")
        val floppyUsername = stringPreferencesKey("floppy_username")
        val syncReport = stringPreferencesKey("sync_report")
        val excludeSpecials = booleanPreferencesKey("exclude_specials")
        val hideAdultTitles = booleanPreferencesKey("hide_adult_titles")
        val posterAccent = booleanPreferencesKey("poster_accent")
        val preferredProviders = stringPreferencesKey("preferred_providers")
        val visibleProviderTypes = stringPreferencesKey("visible_provider_types")
        val heroLayout = stringPreferencesKey("hero_layout")
        val discoverLayout = stringPreferencesKey("discover_layout")
        val seasonPicker = stringPreferencesKey("season_picker")
        val releaseReminders = stringPreferencesKey("release_reminders")
        val posterFormat = stringPreferencesKey("poster_format")
        val castPhotoShape = stringPreferencesKey("cast_photo_shape")
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
        val floppyBaseUrl = stringPreferencesKey("floppy_base_url")
        val floppyServerVersion = stringPreferencesKey("floppy_server_version")
        val floppyServerIdentity = stringPreferencesKey("floppy_server_identity")
        val floppyAccountIdentity = stringPreferencesKey("floppy_account_identity")
        val floppyConnectionId = stringPreferencesKey("floppy_connection_id")
        val floppyCredentialAlias = stringPreferencesKey("floppy_credential_alias")
        val floppyCapabilities = stringPreferencesKey("floppy_capabilities_v1")
        val floppyConnectedAt = longPreferencesKey("floppy_connected_at")
        val floppyLibraryCheckedAt = longPreferencesKey("floppy_library_checked_at")
        val floppyLibraryResent = androidx.datastore.preferences.core.stringSetPreferencesKey("floppy_library_resent")
        val floppyBootstrapPlan = stringPreferencesKey("floppy_bootstrap_plan_v1")
        /** What a finished bootstrap left unplaced, kept for later retries. */
        val floppyBootstrapResidual = stringPreferencesKey("floppy_bootstrap_residual_v1")
        /** "showId:season:episode" of "Not on Floppy" entries the user dismissed. */
        val floppyUnplacedDismissed = stringSetPreferencesKey("floppy_unplaced_dismissed_v1")
        val floppyAllowInsecureLocalHttp = booleanPreferencesKey("floppy_allow_insecure_local_http")
        val animeNumberingRepairDone = booleanPreferencesKey("anime_numbering_repair_v1")
        val animeNumberingRepairShows = stringSetPreferencesKey("anime_numbering_repair_v1_shows")
    }

    val simklToken: Flow<String?> = context.cineTrackDataStore.data.map {
        secureCredential("simkl_token")?.takeIf(SimklAuthRules::isV2AccessToken)
    }
    val simklConnected: Flow<Boolean> = simklToken.map { !it.isNullOrBlank() }
    val simklReconnectReason: Flow<String?> = context.cineTrackDataStore.data.map { it[Keys.simklReconnectReason] }
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
    /** Which provider's episode air dates are shown (see AirDateSource). */
    val airDateSource: Flow<String> = context.cineTrackDataStore.data.map { it[Keys.airDateSource] ?: com.cinetrack.data.schedule.AirDateSource.DEFAULT }
    val excludeSpecials: Flow<Boolean> = context.cineTrackDataStore.data.map { it[Keys.excludeSpecials] ?: true }
    val hideAdultTitles: Flow<Boolean> = context.cineTrackDataStore.data.map { it[Keys.hideAdultTitles] ?: true }
    /** Detail pages take their accent from the poster; on unless turned off in Aspetto. */
    val posterAccent: Flow<Boolean> = context.cineTrackDataStore.data.map { it[Keys.posterAccent] ?: true }

    suspend fun setPosterAccent(enabled: Boolean) {
        context.cineTrackDataStore.edit { it[Keys.posterAccent] = enabled }
    }
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
    val discoverLayout: Flow<String> = context.cineTrackDataStore.data.map { if (it[Keys.discoverLayout] == "b") "b" else "a" }
    /** How a show's seasons are picked: "text" pills or "posters" covers. */
    val seasonPicker: Flow<String> = context.cineTrackDataStore.data.map { if (it[Keys.seasonPicker] == "posters") "posters" else "text" }
    val heroLayout: Flow<String> = context.cineTrackDataStore.data.map { com.cinetrack.domain.CardAppearance.normalizeHero(it[Keys.heroLayout] ?: "standard") }
    val posterFormat: Flow<String> = context.cineTrackDataStore.data.map { com.cinetrack.domain.CardAppearance.normalizeFormat(it[Keys.posterFormat] ?: "classic") }
    val castPhotoShape: Flow<String> = context.cineTrackDataStore.data.map { com.cinetrack.domain.CardAppearance.normalizeCastShape(it[Keys.castPhotoShape] ?: "circle") }
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
    override val searchHistory: Flow<List<String>> = context.cineTrackDataStore.data.map { prefs ->
        prefs[Keys.searchHistory].orEmpty().split(SEARCH_HISTORY_SEPARATOR).filter(String::isNotBlank)
    }
    val introductionCompleted: Flow<Boolean> = context.cineTrackDataStore.data.map {
        it[Keys.introductionCompleted] ?: false
    }

    /**
     * The Simkl AUTH V2 access token. A stored AUTH V1 token (no `simkl_at_` prefix, or the
     * old DataStore copy) cannot be used with the V2 client ID, so it is dropped and the user
     * is asked to reconnect once; the local library and queued changes are kept.
     */
    suspend fun tokenNow(): String? {
        val stored = secureCredential("simkl_token")?.takeIf(String::isNotBlank)
        if (SimklAuthRules.isV2AccessToken(stored)) return stored
        val legacy = context.cineTrackDataStore.data.first()[Keys.simklToken]?.takeIf(String::isNotBlank)
        if (stored != null || legacy != null) {
            setToken(null)
            context.cineTrackDataStore.edit { it[Keys.simklReconnectReason] = "upgrade" }
        }
        return null
    }

    /** Simkl rejected the refresh token (revoked or unused for 180 days): sign out and ask to reconnect. */
    suspend fun dropSimklGrant() {
        setToken(null)
        context.cineTrackDataStore.edit { it[Keys.simklReconnectReason] = "signed_out" }
    }

    val simklFirstSyncMode: Flow<SimklFirstSyncMode?> = context.cineTrackDataStore.data.map { prefs ->
        prefs[Keys.simklFirstSyncMode]?.let { runCatching { SimklFirstSyncMode.valueOf(it) }.getOrNull() }
    }

    suspend fun simklFirstSyncModeNow(): SimklFirstSyncMode? = simklFirstSyncMode.first()

    suspend fun setSimklFirstSyncMode(mode: SimklFirstSyncMode?) {
        context.cineTrackDataStore.edit { prefs ->
            if (mode == null) prefs.remove(Keys.simklFirstSyncMode) else prefs[Keys.simklFirstSyncMode] = mode.name
        }
    }

    fun simklRefreshTokenNow(): String? = secureCredential("simkl_refresh_token")?.takeIf(String::isNotBlank)

    suspend fun simklTokenExpiresAtNow(): Long? = context.cineTrackDataStore.data.first()[Keys.simklTokenExpiresAt]

    /** Stores a refreshed grant without touching sync state: it is the same account and connection. */
    suspend fun storeRefreshedSimklTokens(accessToken: String, refreshToken: String?, expiresAt: Long?) {
        setSecureCredential("simkl_token", accessToken)
        refreshToken?.takeIf(String::isNotBlank)?.let { setSecureCredential("simkl_refresh_token", it) }
        context.cineTrackDataStore.edit { prefs ->
            if (expiresAt != null) prefs[Keys.simklTokenExpiresAt] = expiresAt else prefs.remove(Keys.simklTokenExpiresAt)
        }
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

    /** Trakt client ID (public API key) used only to read exact air times. */
    suspend fun traktClientIdNow(): String = secureCredential("trakt_client_id").orEmpty()

    suspend fun setTraktClientId(value: String?) {
        setSecureCredential("trakt_client_id", value?.trim()?.takeIf(String::isNotBlank))
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

    val floppyBaseUrl: Flow<String?> = context.cineTrackDataStore.data.map { it[Keys.floppyBaseUrl] }

    suspend fun floppyApiKeyNow(): String? {
        val alias = context.cineTrackDataStore.data.first()[Keys.floppyCredentialAlias] ?: "floppy_api_key"
        return secureCredential(alias)?.takeIf(String::isNotBlank)
    }

    suspend fun floppyApiKeyNow(alias: String): String? = secureCredential(alias)?.takeIf(String::isNotBlank)

    suspend fun floppySettingsNow(): FloppyConnectionSettings? {
        val values = context.cineTrackDataStore.data.first()
        val baseUrl = values[Keys.floppyBaseUrl]?.takeIf(String::isNotBlank) ?: return null
        val capabilities = values[Keys.floppyCapabilities].orEmpty().split(',').toSet()
        return FloppyConnectionSettings(
            baseUrl = baseUrl,
            serverVersion = values[Keys.floppyServerVersion],
            serverIdentity = values[Keys.floppyServerIdentity].orEmpty(),
            accountIdentity = values[Keys.floppyAccountIdentity],
            capabilities = FloppyCapabilities(
                canReadLibrary = "read_library" in capabilities,
                canWriteLibrary = "write_library" in capabilities,
                canReadHistory = "read_history" in capabilities,
                canWriteMovieHistory = "write_movie_history" in capabilities,
                canWriteEpisodeHistory = "write_episode_history" in capabilities,
                canRemoveHistory = "remove_history" in capabilities,
                canReadCompleteSnapshot = "complete_snapshot" in capabilities,
                canEnsureEpisodeEvents = "ensure_episode_events" in capabilities,
                canBootstrapV2 = "bootstrap_v2" in capabilities,
                canSyncItems = "sync_items" in capabilities,
                canTranslateEpisodes = "translate_episodes" in capabilities,
                canCrashReports = "crash_reports_v1" in capabilities,
                canCharacterImages = "character_images_v1" in capabilities,
            ),
            connectedAt = values[Keys.floppyConnectedAt],
            connectionId = values[Keys.floppyConnectionId] ?: values[Keys.floppyServerIdentity].orEmpty(),
            credentialAlias = values[Keys.floppyCredentialAlias] ?: "floppy_api_key",
            allowInsecureLocalHttp = values[Keys.floppyAllowInsecureLocalHttp] ?: false,
        )
    }

    /** Replaces the saved account identity of [connectionId] (and nothing else). */
    suspend fun setFloppyAccountIdentity(connectionId: String, accountIdentity: String) {
        context.cineTrackDataStore.edit { values ->
            val current = values[Keys.floppyConnectionId] ?: values[Keys.floppyServerIdentity]
            if (current == connectionId) values[Keys.floppyAccountIdentity] = accountIdentity
        }
    }

    suspend fun setFloppyConnection(
        settings: FloppyConnectionSettings?,
        apiKey: String? = null,
        acceptAddressMove: Boolean = false,
    ) {
        settings?.let { require(it.baseUrl == it.baseUrl.trim()) { "Floppy URL must be normalized before saving" } }
        val previous = floppySettingsNow()
        val oldAlias = previous?.credentialAlias ?: "floppy_api_key"
        val identityChanged = settings != null && previous != null &&
            !sameFloppyRemoteTarget(previous, settings, floppyApiKeyNow(), apiKey, acceptAddressMove)
        val effectiveAlias = when {
            settings == null -> oldAlias
            identityChanged -> "floppy_api_key_${UUID.randomUUID()}"
            else -> previous?.credentialAlias ?: settings.credentialAlias.takeIf(String::isNotBlank)
                ?: "floppy_api_key_${UUID.randomUUID()}"
        }
        // Stage credentials under an immutable alias before changing the
        // active metadata pointer. A crash before the DataStore edit leaves
        // the old URL + old alias fully usable.
        if (settings != null && !apiKey.isNullOrBlank()) setSecureCredential(effectiveAlias, apiKey)
        val effectiveConnectionId = when {
            settings == null -> null
            !identityChanged && previous != null -> previous.connectionId
            else -> settings.connectionId.ifBlank { UUID.randomUUID().toString() }
        }
        context.cineTrackDataStore.edit { values ->
            if (settings == null) {
                values.remove(Keys.floppyBaseUrl)
                values.remove(Keys.floppyServerVersion)
                values.remove(Keys.floppyServerIdentity)
                values.remove(Keys.floppyAccountIdentity)
                values.remove(Keys.floppyConnectionId)
                values.remove(Keys.floppyCredentialAlias)
                values.remove(Keys.floppyCapabilities)
                values.remove(Keys.floppyConnectedAt)
                values.remove(Keys.floppyBootstrapPlan)
                values.remove(Keys.floppyBootstrapResidual)
                values.remove(Keys.floppyAllowInsecureLocalHttp)
                values.remove(trackingLastCheckKey(TrackingProviderId.FLOPPY))
                values.remove(syncBaselineKey(TrackingProviderId.FLOPPY))
                values[providerBootstrapKey(TrackingProviderId.FLOPPY)] = ProviderBootstrapState.NOT_STARTED.name
            } else {
                values[Keys.floppyBaseUrl] = settings.baseUrl
                settings.serverVersion?.let { values[Keys.floppyServerVersion] = it } ?: values.remove(Keys.floppyServerVersion)
                values[Keys.floppyServerIdentity] = settings.serverIdentity
                settings.accountIdentity?.let { values[Keys.floppyAccountIdentity] = it } ?: values.remove(Keys.floppyAccountIdentity)
                values[Keys.floppyConnectionId] = effectiveConnectionId!!
                values[Keys.floppyCredentialAlias] = effectiveAlias
                val capabilities = buildList {
                    if (settings.capabilities.canReadLibrary) add("read_library")
                    if (settings.capabilities.canWriteLibrary) add("write_library")
                    if (settings.capabilities.canReadHistory) add("read_history")
                    if (settings.capabilities.canWriteMovieHistory) add("write_movie_history")
                    if (settings.capabilities.canWriteEpisodeHistory) add("write_episode_history")
                    if (settings.capabilities.canRemoveHistory) add("remove_history")
                    if (settings.capabilities.canReadCompleteSnapshot) add("complete_snapshot")
                    if (settings.capabilities.canEnsureEpisodeEvents) add("ensure_episode_events")
                    if (settings.capabilities.canBootstrapV2) add("bootstrap_v2")
                    if (settings.capabilities.canSyncItems) add("sync_items")
                    if (settings.capabilities.canTranslateEpisodes) add("translate_episodes")
                    if (settings.capabilities.canCrashReports) add("crash_reports_v1")
                    if (settings.capabilities.canCharacterImages) add("character_images_v1")
                }
                values[Keys.floppyCapabilities] = capabilities.joinToString(",")
                settings.connectedAt?.let { values[Keys.floppyConnectedAt] = it } ?: values.remove(Keys.floppyConnectedAt)
                values[Keys.floppyAllowInsecureLocalHttp] = settings.allowInsecureLocalHttp
                if (identityChanged) {
                    values.remove(Keys.floppyBootstrapPlan)
                    values.remove(Keys.floppyBootstrapResidual)
                    values.remove(trackingLastCheckKey(TrackingProviderId.FLOPPY))
                    values.remove(syncBaselineKey(TrackingProviderId.FLOPPY))
                    values[providerBootstrapKey(TrackingProviderId.FLOPPY)] = ProviderBootstrapState.NOT_STARTED.name
                }
            }
        }
        // Cleanup is intentionally last: an interrupted replacement still has
        // a valid old alias or a valid new active alias after restart.
        if (settings == null || identityChanged) setSecureCredential(oldAlias, null)
    }

    suspend fun setFloppyAllowInsecureLocalHttp(enabled: Boolean) {
        context.cineTrackDataStore.edit { it[Keys.floppyAllowInsecureLocalHttp] = enabled }
    }

    suspend fun floppyAllowInsecureLocalHttpNow(): Boolean =
        context.cineTrackDataStore.data.first()[Keys.floppyAllowInsecureLocalHttp] ?: false

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

    private fun providerBootstrapKey(provider: TrackingProviderId) = stringPreferencesKey("provider_bootstrap_${provider.name.lowercase()}")

    fun providerBootstrapState(provider: TrackingProviderId): Flow<ProviderBootstrapState> =
        context.cineTrackDataStore.data.map { prefs ->
            prefs[providerBootstrapKey(provider)]?.let { value ->
                runCatching { ProviderBootstrapState.valueOf(value) }.getOrNull()
            } ?: ProviderBootstrapState.NOT_STARTED
        }

    suspend fun providerBootstrapStateNow(provider: TrackingProviderId): ProviderBootstrapState =
        providerBootstrapState(provider).first()

    suspend fun setProviderBootstrapState(provider: TrackingProviderId, state: ProviderBootstrapState) {
        context.cineTrackDataStore.edit { it[providerBootstrapKey(provider)] = state.name }
    }

    suspend fun floppyBootstrapPlanNow(): Pair<String, Int>? {
        val raw = context.cineTrackDataStore.data.first()[Keys.floppyBootstrapPlan] ?: return null
        val separator = raw.lastIndexOf('|')
        if (separator <= 0) return null
        val instance = raw.substring(0, separator).takeIf(String::isNotBlank) ?: return null
        val count = raw.substring(separator + 1).toIntOrNull()?.takeIf { it >= 0 } ?: return null
        return instance to count
    }

    suspend fun floppyBootstrapPlanRawNow(): String? =
        context.cineTrackDataStore.data.first()[Keys.floppyBootstrapPlan]

    suspend fun setFloppyBootstrapPlanRaw(raw: String) {
        require(raw.isNotBlank()) { "Floppy bootstrap plan cannot be blank" }
        context.cineTrackDataStore.edit { it[Keys.floppyBootstrapPlan] = raw }
    }

    suspend fun setFloppyBootstrapPlan(instanceId: String, operationCount: Int) {
        require(instanceId.isNotBlank()) { "Floppy bootstrap instance is required" }
        require(operationCount >= 0) { "Floppy bootstrap operation count cannot be negative" }
        context.cineTrackDataStore.edit { it[Keys.floppyBootstrapPlan] = "$instanceId|$operationCount" }
    }

    suspend fun clearFloppyBootstrapPlan() {
        context.cineTrackDataStore.edit { it.remove(Keys.floppyBootstrapPlan) }
    }

    suspend fun floppyUnplacedDismissedNow(): Set<String> =
        context.cineTrackDataStore.data.first()[Keys.floppyUnplacedDismissed].orEmpty()

    suspend fun dismissFloppyUnplaced(key: String) {
        context.cineTrackDataStore.edit { values ->
            values[Keys.floppyUnplacedDismissed] = values[Keys.floppyUnplacedDismissed].orEmpty() + key
        }
    }

    /** When the after-sync Floppy library check last read Floppy (epoch ms). */
    suspend fun floppyLibraryCheckedAtNow(): Long =
        context.cineTrackDataStore.data.first()[Keys.floppyLibraryCheckedAt] ?: 0L

    suspend fun setFloppyLibraryCheckedAt(at: Long) {
        context.cineTrackDataStore.edit { values -> values[Keys.floppyLibraryCheckedAt] = at }
    }

    /** Items the library check re-sent and that were still missing since. */
    suspend fun floppyLibraryResentNow(): Set<String> =
        context.cineTrackDataStore.data.first()[Keys.floppyLibraryResent].orEmpty()

    suspend fun setFloppyLibraryResent(keys: Set<String>) {
        context.cineTrackDataStore.edit { values -> values[Keys.floppyLibraryResent] = keys }
    }

    suspend fun floppyBootstrapResidualRawNow(): String? =
        context.cineTrackDataStore.data.first()[Keys.floppyBootstrapResidual]

    suspend fun setFloppyBootstrapResidualRaw(raw: String?) {
        context.cineTrackDataStore.edit { values ->
            if (raw.isNullOrBlank()) values.remove(Keys.floppyBootstrapResidual) else values[Keys.floppyBootstrapResidual] = raw
        }
    }

    /** One-time anime renumbering (Simkl numbers to TMDB numbers): finished for every show. */
    suspend fun animeNumberingRepairDoneNow(): Boolean =
        context.cineTrackDataStore.data.first()[Keys.animeNumberingRepairDone] == true

    suspend fun setAnimeNumberingRepairDone() {
        context.cineTrackDataStore.edit { values -> values[Keys.animeNumberingRepairDone] = true }
    }

    /** Shows (TMDB ids) already renumbered, so partial progress survives a restart. */
    suspend fun animeNumberingRepairShowsNow(): Set<String> =
        context.cineTrackDataStore.data.first()[Keys.animeNumberingRepairShows].orEmpty()

    suspend fun addAnimeNumberingRepairShow(showId: Int) {
        context.cineTrackDataStore.edit { values ->
            values[Keys.animeNumberingRepairShows] = values[Keys.animeNumberingRepairShows].orEmpty() + showId.toString()
        }
    }

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
        require(secondary == null || main != null) { "A SECONDARY provider requires a MAIN provider" }
        require(main == null || main != secondary) { "The same tracking provider cannot be both MAIN and SECONDARY" }
        context.cineTrackDataStore.edit { prefs ->
            prefs[Keys.mainTrackingProvider] = main?.name ?: "NONE"
            if (secondary == null) prefs.remove(Keys.secondaryTrackingProvider)
            else prefs[Keys.secondaryTrackingProvider] = secondary.name
        }
    }

    suspend fun setToken(value: String?) {
        setSecureCredential("simkl_token", value)
        if (value == null) setSecureCredential("simkl_refresh_token", null)
        context.cineTrackDataStore.edit { prefs ->
            prefs.remove(Keys.simklToken)
            if (value == null) prefs.remove(Keys.simklTokenExpiresAt)
            // A different account must always receive its own initial activity check.
            prefs.remove(Keys.simklLastCheckAt)
            prefs.remove(trackingLastCheckKey(TrackingProviderId.SIMKL))
            prefs.remove(syncBaselineKey(TrackingProviderId.SIMKL))
            prefs.remove(Keys.syncBaseline)
            prefs[providerBootstrapKey(TrackingProviderId.SIMKL)] = ProviderBootstrapState.NOT_STARTED.name
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
    override suspend fun setLanguage(value: String) {
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

    val simklUsername: Flow<String?> = context.cineTrackDataStore.data.map { it[Keys.simklUsername] }

    suspend fun setSimklUsername(value: String?) {
        context.cineTrackDataStore.edit { if (value.isNullOrBlank()) it.remove(Keys.simklUsername) else it[Keys.simklUsername] = value }
    }

    val floppyUsername: Flow<String?> = context.cineTrackDataStore.data.map { it[Keys.floppyUsername] }

    suspend fun setFloppyUsername(value: String?) {
        context.cineTrackDataStore.edit { if (value.isNullOrBlank()) it.remove(Keys.floppyUsername) else it[Keys.floppyUsername] = value }
    }

    suspend fun setMetadataTimezone(value: String) {
        context.cineTrackDataStore.edit { it[Keys.metadataTimezone] = value }
    }

    suspend fun setAirDateSource(value: String) {
        context.cineTrackDataStore.edit { it[Keys.airDateSource] = value }
    }

    suspend fun setHideAdultTitles(value: Boolean) {
        context.cineTrackDataStore.edit { it[Keys.hideAdultTitles] = value }
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

    /** Releases the user asked to be reminded about, by [com.cinetrack.data.sync.ReleaseNotifier.reminderKey]. */
    val releaseReminders: Flow<Set<String>> = context.cineTrackDataStore.data.map { values ->
        values[Keys.releaseReminders].orEmpty().split('|').filter(String::isNotBlank).toSet()
    }

    suspend fun setReleaseReminder(key: String, enabled: Boolean) {
        context.cineTrackDataStore.edit { values ->
            val current = values[Keys.releaseReminders].orEmpty().split('|').filter(String::isNotBlank).toMutableSet()
            if (enabled) current.add(key) else current.remove(key)
            values[Keys.releaseReminders] = current.toList().takeLast(200).joinToString("|")
        }
    }

    suspend fun setSeasonPicker(value: String) {
        context.cineTrackDataStore.edit { it[Keys.seasonPicker] = if (value == "posters") "posters" else "text" }
    }

    suspend fun setDiscoverLayout(value: String) {
        context.cineTrackDataStore.edit { it[Keys.discoverLayout] = if (value == "b") "b" else "a" }
    }

    suspend fun setHeroLayout(value: String) {
        context.cineTrackDataStore.edit { it[Keys.heroLayout] = com.cinetrack.domain.CardAppearance.normalizeHero(value) }
    }

    suspend fun setPosterFormat(value: String) {
        context.cineTrackDataStore.edit { it[Keys.posterFormat] = com.cinetrack.domain.CardAppearance.normalizeFormat(value) }
    }

    suspend fun setCastPhotoShape(value: String) {
        context.cineTrackDataStore.edit { it[Keys.castPhotoShape] = com.cinetrack.domain.CardAppearance.normalizeCastShape(value) }
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

    override suspend fun addSearchHistory(query: String) {
        val normalized = query.trim().replace(SEARCH_HISTORY_SEPARATOR, " ").replace(Regex("\\s+"), " ")
        if (normalized.isBlank()) return
        context.cineTrackDataStore.edit { prefs ->
            val current = prefs[Keys.searchHistory].orEmpty().split(SEARCH_HISTORY_SEPARATOR).filter(String::isNotBlank)
            val updated = listOf(normalized) + current.filterNot { it.equals(normalized, ignoreCase = true) }
            prefs[Keys.searchHistory] = updated.take(10).joinToString(SEARCH_HISTORY_SEPARATOR)
        }
    }

    override suspend fun clearSearchHistory() {
        context.cineTrackDataStore.edit { prefs -> prefs.remove(Keys.searchHistory) }
    }

    override suspend fun removeSearchHistory(query: String) {
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

    private val errorLogVersion = MutableStateFlow(0L)
    /** Bumped on every append or clear, so an open Logs page re-reads the file. */
    override val errorLogChanges: StateFlow<Long> = errorLogVersion.asStateFlow()

    override fun readErrorLogs(): List<String> = runCatching {
        if (errorLogFile.exists()) errorLogFile.readLines().filter(String::isNotBlank).takeLast(ERROR_LOG_LINES) else emptyList()
    }.getOrDefault(emptyList())

    /** Synchronized: the UI and background workers append concurrently, and
     * this is a read-modify-write of one file. */
    @Synchronized
    fun appendErrorLog(line: String) {
        runCatching {
            val lines = (readErrorLogs() + line).takeLast(ERROR_LOG_LINES)
            errorLogFile.writeText(lines.joinToString(separator = "\n", postfix = "\n"))
        }
        errorLogVersion.update { it + 1 }
    }

    @Synchronized
    override fun clearErrorLog() {
        runCatching { errorLogFile.delete() }
        errorLogVersion.update { it + 1 }
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

    /** Encrypted, durable storage for Floppy sign-in tokens (one entry per sign-in). */
    val floppyOAuthTokenStore: com.cinetrack.data.sync.floppy.network.FloppyOAuthTokenStore =
        object : com.cinetrack.data.sync.floppy.network.FloppyOAuthTokenStore {
            private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

            override fun load(grantId: String) = credentialStore.get("floppy_oauth:$grantId")?.let { raw ->
                runCatching { json.decodeFromString<com.cinetrack.data.sync.floppy.network.FloppyOAuthTokens>(raw) }.getOrNull()
            }

            override fun save(grantId: String, tokens: com.cinetrack.data.sync.floppy.network.FloppyOAuthTokens?) =
                credentialStore.put("floppy_oauth:$grantId", tokens?.let(json::encodeToString), durable = true)
        }

    data class FloppyPendingSignIn(val baseUrl: String, val allowInsecureLocalHttp: Boolean, val codeVerifier: String)

    /** Opens Floppy's sign-in page for [baseUrl] (already normalized). */
    suspend fun beginFloppyLogin(context: Context, baseUrl: String, allowInsecureLocalHttp: Boolean): Result<Unit> = runCatching {
        val verifierBytes = ByteArray(48).also(SecureRandom()::nextBytes)
        val verifier = Base64.encodeToString(verifierBytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        val challenge = Base64.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()),
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP,
        )
        val state = UUID.randomUUID().toString()
        this.context.cineTrackDataStore.edit {
            it[Keys.floppyPkceVerifier] = verifier
            it[Keys.floppyPkceState] = state
            it[Keys.floppyPendingBaseUrl] = baseUrl
            it[Keys.floppyPendingAllowHttp] = allowInsecureLocalHttp
        }
        val uri = Uri.parse(com.cinetrack.data.sync.floppy.network.FloppyOAuthRules.authorizeUrl(baseUrl, challenge, state))
        CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, uri)
    }

    /**
     * Validates a Floppy sign-in callback and consumes the pending sign-in.
     * The code is single-use, so the pending state is cleared even when the
     * callback is refused.
     */
    suspend fun takeFloppyPendingLogin(returnedState: String?, error: String?, issuer: String?): Result<FloppyPendingSignIn> = runCatching {
        val values = context.cineTrackDataStore.data.first()
        val verifier = values[Keys.floppyPkceVerifier]
        val baseUrl = values[Keys.floppyPendingBaseUrl]
        val allowHttp = values[Keys.floppyPendingAllowHttp] ?: false
        context.cineTrackDataStore.edit {
            it.remove(Keys.floppyPkceVerifier)
            it.remove(Keys.floppyPkceState)
            it.remove(Keys.floppyPendingBaseUrl)
            it.remove(Keys.floppyPendingAllowHttp)
        }
        com.cinetrack.data.sync.floppy.network.FloppyOAuthRules.callbackProblem(
            expectedState = values[Keys.floppyPkceState]?.takeIf { verifier != null && baseUrl != null },
            returnedState = returnedState,
            error = error,
            issuer = issuer,
            baseUrl = baseUrl.orEmpty(),
        )?.let(::error)
        FloppyPendingSignIn(checkNotNull(baseUrl), allowHttp, checkNotNull(verifier))
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
        val uri = Uri.parse(SimklAuthRules.AUTHORIZE_URL).buildUpon()
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", BuildConfig.SIMKL_CLIENT_ID)
            .appendQueryParameter("redirect_uri", BuildConfig.SIMKL_REDIRECT_URI)
            .appendQueryParameter("scope", SimklAuthRules.REQUESTED_SCOPE)
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("state", state)
            .appendQueryParameter("app-name", "cinetrack")
            .appendQueryParameter("app-version", BuildConfig.VERSION_NAME)
            .build()
        CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, uri)
    }

    suspend fun completeSimklLogin(code: String, returnedState: String?, issuer: String?, service: SimklAuthService): Result<Unit> = runCatching {
        val session = context.cineTrackDataStore.data.first()
        val verifier = session[Keys.pkceVerifier]
        SimklAuthRules.callbackProblem(session[Keys.pkceState]?.takeIf { verifier != null }, returnedState, issuer)?.let(::error)
        // The code is single-use and burned even by a failed exchange, so this is never retried.
        context.cineTrackDataStore.edit {
            it.remove(Keys.pkceVerifier)
            it.remove(Keys.pkceState)
        }
        val response = service.exchangeCode(
            code = code,
            clientId = BuildConfig.SIMKL_CLIENT_ID,
            codeVerifier = checkNotNull(verifier),
            redirectUri = BuildConfig.SIMKL_REDIRECT_URI,
        )
        if (!SimklAuthRules.grantsWrite(response.scope)) {
            runCatching { service.revoke(response.refreshToken ?: response.accessToken, BuildConfig.SIMKL_CLIENT_ID) }
            error("Simkl granted read-only access, so CineTrack could not sync changes. Please connect again and allow access")
        }
        check(SimklAuthRules.isV2AccessToken(response.accessToken)) { "Simkl returned an unexpected access token. Please connect again" }
        // A new grant replaces any previous one this device held.
        simklRefreshTokenNow()?.let { previous -> runCatching { service.revoke(previous, BuildConfig.SIMKL_CLIENT_ID) } }
        setSecureCredential("simkl_token", response.accessToken)
        setSecureCredential("simkl_refresh_token", response.refreshToken)
        context.cineTrackDataStore.edit {
            it.remove(Keys.simklToken)
            it.remove(Keys.simklReconnectReason)
            // Nothing syncs until the user chooses how to combine this account with the device.
            it[Keys.simklFirstSyncMode] = SimklFirstSyncMode.ASK.name
            SimklAuthRules.expiresAt(response.expiresIn, System.currentTimeMillis())
                ?.let { expiry -> it[Keys.simklTokenExpiresAt] = expiry } ?: it.remove(Keys.simklTokenExpiresAt)
            it.remove(Keys.simklLastCheckAt)
            it.remove(trackingLastCheckKey(TrackingProviderId.SIMKL))
            it.remove(syncBaselineKey(TrackingProviderId.SIMKL))
            it.remove(Keys.syncBaseline)
            it[providerBootstrapKey(TrackingProviderId.SIMKL)] = ProviderBootstrapState.NOT_STARTED.name
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

