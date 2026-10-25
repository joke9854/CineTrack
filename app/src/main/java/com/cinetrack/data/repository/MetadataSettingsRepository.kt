package com.cinetrack.data.repository

import com.cinetrack.data.local.AppDatabase
import com.cinetrack.data.remote.ApiServices
import com.cinetrack.data.remote.NetworkFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/**
 * Metadata settings and API credentials: each write saves the preference and then updates
 * the live value the network clients read. Credentials are checked against their service
 * before they are saved. Moved out of the former sync-host repository (roadmap Q2).
 */
class MetadataSettingsRepository(
    private val database: AppDatabase,
    private val services: ApiServices,
    private val preferences: AppPreferences,
    private val onTmdbApiKeyChanged: (String) -> Unit = {},
    private val onMdbListApiKeyChanged: (String) -> Unit = {},
    private val onMetadataLanguageChanged: (String) -> Unit = {},
    private val onMetadataRegionChanged: (String) -> Unit = {},
    private val onMetadataTimezoneChanged: (String) -> Unit = {},
    /** Drops metadata cached in the previous language (ProgressCacheRepository.clearEpisodeTitleCache). */
    private val clearLanguageCaches: () -> Unit = {},
    /** The direct TVDB client the character pictures use (shared, so a new key drops its token). */
    private val tvdbCharacters: com.cinetrack.data.media.TvdbCharacterSource = com.cinetrack.data.media.TvdbCharacterSource(
        tvdb = { com.cinetrack.data.remote.ExtraMetadataClients.tvdb },
        tmdb = { services.tmdb },
        credentials = { preferences.tvdbApiKeyNow() to preferences.tvdbPinNow() },
    ),
) {
    private suspend fun <T> cancellableResult(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        Result.failure(error)
    }

    suspend fun verifyAndSetTmdbApiKey(value: String): Result<Unit> = cancellableResult {
        val candidate = value.trim()
        require(candidate.isNotBlank()) { "TMDB API credential cannot be empty" }
        NetworkFactory.create(
            token = { null },
            tmdbApiKey = { candidate },
            metadataLanguage = { "en-US" },
            metadataRegion = { "US" },
            metadataTimezone = { "UTC" },
        ).tmdb.trendingMovies()
        preferences.setTmdbApiKey(candidate)
        onTmdbApiKeyChanged(candidate)
    }

    suspend fun verifyAndSetMdbListApiKey(value: String): Result<Unit> = cancellableResult {
        val candidate = value.trim()
        require(candidate.isNotBlank()) { "MDBList API credential cannot be empty" }
        services.mdbList.mediaInfo(mediaType = "movie", id = 550, apiKey = candidate)
        preferences.setMdbListApiKey(candidate)
        onMdbListApiKeyChanged(candidate)
    }

    /** Checks the key against a well-known movie before saving it. */
    suspend fun verifyAndSetFanartApiKey(value: String): Result<Unit> = cancellableResult {
        val candidate = value.trim()
        require(candidate.isNotBlank()) { "Fanart.tv API key cannot be empty" }
        com.cinetrack.data.remote.ExtraMetadataClients.fanart.movie(550, candidate)
        preferences.setFanartApiKey(candidate)
    }

    /** Checks the premium credentials with TVmaze's user API before saving them. */
    suspend fun verifyAndSetTvmazeUser(username: String, apiKey: String): Result<Unit> = cancellableResult {
        val user = username.trim()
        val key = apiKey.trim()
        require(user.isNotBlank()) { "TVmaze username cannot be empty" }
        require(key.isNotBlank()) { "TVmaze API key cannot be empty" }
        val premium = try {
            com.cinetrack.data.remote.ExtraMetadataClients.tvmaze.validate(com.cinetrack.data.remote.tvmazeBasicAuth(user, key)).premium
        } catch (error: retrofit2.HttpException) {
            throw IllegalArgumentException(if (error.code() == 401) "TVmaze did not accept this username and key" else "TVmaze answered ${error.code()}")
        }
        require(premium > 0) { "This TVmaze account has no premium plan" }
        preferences.setTvmazeUser(user, key)
    }

    suspend fun clearTvmazeUser() = preferences.setTvmazeUser(null, null)

    /** Logs in to TVDB with the key (and PIN) before saving them. */
    suspend fun verifyAndSetTvdbKey(apiKey: String, pin: String): Result<Unit> = cancellableResult {
        val key = apiKey.trim()
        require(key.isNotBlank()) { "TVDB API key cannot be empty" }
        tvdbCharacters.verify(key, pin.trim())
        preferences.setTvdbCredentials(key, pin)
        tvdbCharacters.clear()
    }

    suspend fun clearTvdbKey() {
        preferences.setTvdbCredentials(null, null)
        tvdbCharacters.clear()
    }

    /** Once after the update that removed Trakt: its key, its choice and its stored air dates go. */
    suspend fun retireTrakt() {
        if (!preferences.retireTrakt()) return
        database.mediaDao().deleteAirDatesFrom(com.cinetrack.data.schedule.AirDateSource.RETIRED_TRAKT)
        com.cinetrack.data.schedule.reapplyAirDates(database, preferences.airDateSource.first())
    }

    /** Turns TVmaze air times on or off and shows the dates that are then left. */
    suspend fun setTvmazeEnabled(enabled: Boolean) {
        preferences.setTvmazeEnabled(enabled)
        if (!enabled) database.mediaDao().deleteAirDatesFrom(com.cinetrack.data.schedule.AirDateSource.TVMAZE)
        com.cinetrack.data.schedule.reapplyAirDates(database, preferences.airDateSource.first())
    }

    suspend fun setMetadataLanguage(value: String) {
        preferences.setMetadataLanguage(value)
        onMetadataLanguageChanged(value)
        clearLanguageCaches()
    }

    suspend fun setMetadataRegion(value: String) {
        preferences.setMetadataRegion(value)
        onMetadataRegionChanged(value)
    }

    /** Switches the shown air dates to [value]'s, from the dates already kept per provider. */
    suspend fun setAirDateSource(value: String) {
        preferences.setAirDateSource(value)
        com.cinetrack.data.schedule.reapplyAirDates(database, value)
    }

    suspend fun setMetadataTimezone(value: String) {
        preferences.setMetadataTimezone(value)
        onMetadataTimezoneChanged(value)
    }
}
