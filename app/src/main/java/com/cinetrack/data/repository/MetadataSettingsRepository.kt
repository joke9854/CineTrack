package com.cinetrack.data.repository

import com.cinetrack.data.local.AppDatabase
import com.cinetrack.data.remote.ApiServices
import com.cinetrack.data.remote.NetworkFactory
import kotlinx.coroutines.CancellationException

/**
 * Metadata settings and API credentials: each write saves the preference and then updates
 * the live value the network clients read. Credentials are checked against their service
 * before they are saved. Moved out of CineTrackRepository (roadmap Q2).
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
    /** Drops metadata cached in the previous language (CineTrackRepository.clearLanguageCaches). */
    private val clearLanguageCaches: () -> Unit = {},
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

    /** Checks the client ID against Trakt's public calendar before saving it. */
    suspend fun verifyAndSetTraktClientId(value: String): Result<Unit> = cancellableResult {
        val candidate = value.trim()
        require(candidate.isNotBlank()) { "Trakt client ID cannot be empty" }
        val service = checkNotNull(services.traktCalendar) { "Trakt is unavailable" }
        service.shows(candidate, java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString(), 1)
        preferences.setTraktClientId(candidate)
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
