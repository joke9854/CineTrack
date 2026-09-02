package com.cinetrack.domain

import java.time.LocalDate
import java.time.Period

enum class MediaType { MOVIE, TV }

enum class LibraryStatus {
    NONE,
    WATCHING,
    PLAN_TO_WATCH,
    PAUSED,
    COMPLETED,
    DROPPED,
}

data class MediaCard(
    val id: Int,
    val type: MediaType,
    val title: String,
    val overview: String = "",
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val releaseDate: String? = null,
    val score: Double? = null,
    val status: LibraryStatus = LibraryStatus.NONE,
    val watched: Boolean = false,
    val runtimeMinutes: Int? = null,
    val genres: List<String> = emptyList(),
    val providers: List<String> = emptyList(),
    val providerLogos: Map<String, String> = emptyMap(),
    val subscriptionProviders: List<String> = emptyList(),
    val rentProviders: List<String> = emptyList(),
    val buyProviders: List<String> = emptyList(),
    val providerLink: String? = null,
    val seasons: List<SeasonCard> = emptyList(),
    val collectionId: Int? = null,
    val libraryUpdatedAt: Long? = null,
    val tmdbStatus: String? = null,
    val networks: List<String> = emptyList(),
    val budget: Long? = null,
    val boxOffice: Long? = null,
    val productionCompanies: List<String> = emptyList(),
    val productionCountries: List<String> = emptyList(),
    val originalLanguage: String? = null,
) {
    val stableKey: String get() = "${type.name}:$id"
    val year: String get() = releaseDate?.take(4).orEmpty()
}

data class SeasonCard(
    val number: Int,
    val title: String,
    val episodeCount: Int,
    val posterUrl: String? = null,
)

data class PlaybackCard(
    val media: MediaCard,
    val episodeId: Int? = null,
    val episodeLabel: String? = null,
    val episodeTitle: String? = null,
    val season: Int? = null,
    val episodeNumber: Int? = null,
    val progress: Float,
    val remainingMinutes: Int? = null,
    val durationMinutes: Int? = null,
    val episodeAirDate: String? = null,
)

data class StreamingProvider(
    val id: Int,
    val name: String,
    val logoUrl: String? = null,
)

data class EpisodeCard(
    val id: Int,
    val showId: Int,
    val season: Int,
    val number: Int,
    val title: String,
    val overview: String,
    val airDate: String?,
    val stillUrl: String? = null,
    val runtimeMinutes: Int? = null,
    val watched: Boolean = false,
) {
    val label: String get() = "S${season.toString().padStart(2, '0')} · E${number.toString().padStart(2, '0')}"
    val scheduleKey: String get() = "$showId:$season:$number"
}

data class PersonCard(
    val id: Int,
    val name: String,
    val role: String,
    val profileUrl: String? = null,
    val biography: String = "",
    val birthday: String? = null,
    val placeOfBirth: String? = null,
    val movieCredits: List<MediaCard> = emptyList(),
) {
    fun age(on: LocalDate = LocalDate.now()): Int? = runCatching {
        birthday?.let { Period.between(LocalDate.parse(it), on).years }
    }.getOrNull()
}

data class RatingScore(
    val source: String,
    val score: String,
    val voteCount: String? = null,
)

data class TimelineCard(
    val media: MediaCard,
    val label: String,
    val timestamp: String,
    val episodeLabel: String? = null,
    val episodeId: Int? = null,
    val season: Int? = null,
    val episodeNumber: Int? = null,
)

enum class SyncStage {
    IDLE,
    AUTH,
    ACTIVITY,
    PLAYBACK,
    HISTORY,
    CALENDAR,
    COMMIT,
    PROCESSING,
    COMPLETE,
    ERROR,
}

data class SyncProgress(
    val running: Boolean = false,
    val progress: Float = 0f,
    val stage: SyncStage = SyncStage.IDLE,
    val message: String? = null,
    val lastSuccessfulSync: Long? = null,
    val report: SyncReport = SyncReport(),
)

data class SyncReport(
    val downloaded: Int = 0,
    val uploaded: Int = 0,
    val added: Int = 0,
    val removed: Int = 0,
    val unchanged: Int = 0,
    val pendingLocalChanges: Int = 0,
    val failedOperations: Int = 0,
    val conflicts: Int = 0,
    val lastFullSync: Long? = null,
    val lastIncrementalSync: Long? = null,
    val databaseUntouched: Boolean = false,
)

data class ViewingPeopleInsights(
    val actors: List<PersonCard> = emptyList(),
    val directors: List<PersonCard> = emptyList(),
    val loading: Boolean = false,
)

data class AppUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val rails: Map<String, List<MediaCard>> = emptyMap(),
    val playbackTv: List<PlaybackCard> = emptyList(),
    val playbackMovies: List<PlaybackCard> = emptyList(),
    val history: List<TimelineCard> = emptyList(),
    val calendar: List<TimelineCard> = emptyList(),
    val episodes: List<EpisodeCard> = emptyList(),
    val people: List<PersonCard> = emptyList(),
    val sync: SyncProgress = SyncProgress(),
    val simklConnected: Boolean = false,
    val backgroundSync: Boolean = true,
    val wifiOnly: Boolean = false,
    val notificationEpisodes: Boolean = true,
    val notificationMovies: Boolean = true,
    val notificationSync: Boolean = true,
    val ratingSources: Set<String> = setOf("imdb", "tmdb", "metacritic", "tomatoes"),
    val contentRegions: Set<String> = emptySet(),
    val uiAccent: String = "watching",
    val tmdbApiConfigured: Boolean = false,
    val mdbListApiConfigured: Boolean = false,
    val metadataLanguage: String = "system",
    val metadataRegion: String = "system",
    val metadataTimezone: String = "system",
    val excludeSpecials: Boolean = true,
    val preferredProviders: Set<String> = emptySet(),
    val cardDensity: String = "standard",
    val hiddenUpcoming: Set<String> = emptySet(),
    val hiddenDiscovery: Set<String> = emptySet(),
    val introductionCompleted: Boolean = false,
) {
    val allMedia: List<MediaCard>
        get() = (
            rails.values.flatten() +
                playbackTv.map(PlaybackCard::media) +
                playbackMovies.map(PlaybackCard::media) +
                history.map(TimelineCard::media) +
                calendar.map(TimelineCard::media)
            ).distinctBy(MediaCard::stableKey)

    fun findMedia(type: MediaType, id: Int): MediaCard? =
        allMedia.firstOrNull { it.type == type && it.id == id }
}

data class DiscoverMovieFilters(
    val mediaType: MediaType = MediaType.MOVIE,
    val genreIds: Set<Int> = emptySet(),
    val excludedGenreIds: Set<Int> = emptySet(),
    val releaseYear: Int? = null,
    val minimumRating: Double? = null,
    val sortBy: String = "popularity.desc",
    val animeMode: String = "all",
    val hideWatched: Boolean = false,
    val hideDropped: Boolean = false,
    val providerIds: Set<Int> = emptySet(),
    val maximumRuntime: Int? = null,
    val originalLanguage: String? = null,
    val decadeStart: Int? = null,
)

object RailIds {
    const val TRENDING_TV = "trending-tv"
    const val TRENDING_MOVIES = "trending-movies"
    const val UPCOMING = "upcoming"
    const val LIBRARY = "library"
    const val RECOMMENDED = "recommended"
}
