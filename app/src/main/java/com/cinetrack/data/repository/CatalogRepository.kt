package com.cinetrack.data.repository

import androidx.room.withTransaction
import com.cinetrack.BuildConfig
import com.cinetrack.data.local.AppDatabase
import com.cinetrack.data.local.MediaEntity
import com.cinetrack.data.local.toDomain
import com.cinetrack.data.local.toEntity
import com.cinetrack.data.remote.ApiServices
import com.cinetrack.data.remote.TmdbMediaDto
import com.cinetrack.domain.DiscoverMovieFilters
import com.cinetrack.domain.LibraryStatus
import com.cinetrack.domain.MediaCard
import com.cinetrack.domain.MediaType
import com.cinetrack.domain.PersonCard
import com.cinetrack.domain.RailIds
import com.cinetrack.domain.StreamingProvider
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/**
 * TMDB catalog browsing moved out of [CineTrackRepository]: Discover
 * rails/pages/filters, title and people search, and streaming provider lists.
 */
class CatalogRepository(
    private val database: AppDatabase,
    private val services: ApiServices,
    private val preferences: AppPreferences,
    private val tmdbApiKey: () -> String = { BuildConfig.TMDB_API_TOKEN },
) {
    private suspend fun localZone(): ZoneId {
        val configured = preferences.metadataTimezone.first()
        return if (configured == "system") ZoneId.systemDefault()
        else runCatching { ZoneId.of(configured) }.getOrDefault(ZoneId.systemDefault())
    }

    private suspend fun localToday(): LocalDate = LocalDate.now(localZone())

    suspend fun refreshDiscover() = coroutineScope {
        check(tmdbApiKey().isNotBlank()) { "TMDB API credential is missing" }
        val allowedRegions = preferences.contentRegions.first()
        val hiddenDiscovery = preferences.hiddenDiscovery.first()
        val regionQuery = allowedRegions.takeIf { it.isNotEmpty() }?.sorted()?.joinToString("|")
        fun List<com.cinetrack.data.remote.TmdbMediaDto>.inAllowedRegions() = filter { dto ->
            dto.inContentRegions(allowedRegions)
        }
        // Bound all Discover requests, and reuse Popular's first page when a
        // country-filtered Trending list needs filling. No extra fallback call.
        val requests = Semaphore(6)
        suspend fun pages(load: suspend (Int) -> List<com.cinetrack.data.remote.TmdbMediaDto>) = coroutineScope {
            (1..3).map { page -> async { requests.withPermit { load(page) } } }
                .map { it.await() }.flatten()
        }
        val popularTv = async {
            pages { services.tmdb.discoverTv(regionQuery, sortBy = "popularity.desc", page = it).results }
                .inAllowedRegions().distinctBy { it.id }
        }
        val popularMovies = async {
            pages { services.tmdb.discoverMovies(regionQuery, sortBy = "popularity.desc", page = it).results }
                .inAllowedRegions().distinctBy { it.id }
        }
        val tv = async {
            loadTrendingCandidates(
                allowedOrigins = allowedRegions,
                loadPage = { page -> requests.withPermit { services.tmdb.trendingTv(page).results } },
                loadFallback = { popularTv.await().take(20) },
                include = { "${MediaType.TV.name}:${it.id}" !in hiddenDiscovery },
            ).map { it.toEntity(MediaType.TV) }
        }
        val movies = async {
            loadTrendingCandidates(
                allowedOrigins = allowedRegions,
                loadPage = { page -> requests.withPermit { services.tmdb.trendingMovies(page).results } },
                loadFallback = { popularMovies.await().take(20) },
                include = { "${MediaType.MOVIE.name}:${it.id}" !in hiddenDiscovery },
            ).map { it.toEntity(MediaType.MOVIE) }
        }
        val today = localToday()
        val upcomingMovies = async {
            pages { page ->
                services.tmdb.discoverMovies(
                    originCountries = regionQuery,
                    sortBy = "popularity.desc",
                    dateFrom = today.plusDays(1).toString(),
                    page = page,
                ).results
            }.inAllowedRegions().map { it.toEntity(MediaType.MOVIE) }
                .filterNot { "${it.mediaType}:${it.tmdbId}" in hiddenDiscovery }
        }
        val upcomingTv = async {
            cancellableResult {
                pages { page ->
                    services.tmdb.upcomingTv(
                        dateFrom = today.plusDays(1).toString(),
                        sortBy = "popularity.desc",
                        originCountries = regionQuery,
                        page = page,
                    ).results
                }.inAllowedRegions().map { it.toEntity(MediaType.TV) }
                    .filterNot { "${it.mediaType}:${it.tmdbId}" in hiddenDiscovery }
            }
        }
        fun List<com.cinetrack.data.remote.TmdbMediaDto>.asRail(type: MediaType) =
            filterNot { "${type.name}:${it.id}" in hiddenDiscovery }.map { it.toEntity(type) }
        // Await every network result before taking Room's write lock.
        val upcomingTvResult = upcomingTv.await()
        val upcomingTvItems = upcomingTvResult.getOrElse {
            // A partial Discover refresh must not erase a previously valid rail.
            // Keep cached TV releases while fresh movie data can still update.
            database.mediaDao().railMedia(RailIds.UPCOMING).filter { it.mediaType == MediaType.TV.name }
        }
        val rails = linkedMapOf(
            RailIds.TRENDING_TV to tv.await(),
            RailIds.TRENDING_MOVIES to movies.await(),
            RailIds.POPULAR_TV to popularTv.await().asRail(MediaType.TV),
            RailIds.POPULAR_MOVIES to popularMovies.await().asRail(MediaType.MOVIE),
            RailIds.UPCOMING to (upcomingMovies.await() + upcomingTvItems)
                .filter { entity ->
                    entity.releaseDate?.let { raw -> runCatching { LocalDate.parse(raw.take(10)).isAfter(today) }.getOrDefault(false) } == true
                }
                .distinctBy { "${it.mediaType}:${it.tmdbId}" }
                .sortedBy(MediaEntity::releaseDate),
        )
        database.withTransaction {
            rails.forEach { (id, items) -> database.mediaDao().replaceRail(id, items) }
        }
    }

    /** Browse on demand without enlarging refresh rails or overwriting detailed cached metadata. */
    suspend fun loadDiscoverPage(railId: String, page: Int): com.cinetrack.domain.DiscoverPage = coroutineScope {
        check(tmdbApiKey().isNotBlank()) { "TMDB API credential is missing" }
        require(page in 1..500)
        val regions = preferences.contentRegions.first()
        val origins = regions.takeIf { it.isNotEmpty() }?.sorted()?.joinToString("|")
        val hidden = preferences.hiddenDiscovery.first()
        val today = localToday()
        val responses: List<Pair<MediaType, com.cinetrack.data.remote.TmdbPage>> = when (railId) {
            RailIds.TRENDING_TV -> listOf(MediaType.TV to services.tmdb.trendingTv(page))
            RailIds.TRENDING_MOVIES -> listOf(MediaType.MOVIE to services.tmdb.trendingMovies(page))
            RailIds.POPULAR_TV -> listOf(MediaType.TV to services.tmdb.discoverTv(origins, page = page))
            RailIds.POPULAR_MOVIES -> listOf(MediaType.MOVIE to services.tmdb.discoverMovies(origins, page = page))
            RailIds.UPCOMING -> {
                val movies = async { services.tmdb.discoverMovies(origins, dateFrom = today.plusDays(1).toString(), page = page) }
                val tv = async { services.tmdb.upcomingTv(today.plusDays(1).toString(), originCountries = origins, page = page) }
                listOf(MediaType.MOVIE to movies.await(), MediaType.TV to tv.await())
            }
            else -> error("Unknown Discover list")
        }
        val states = database.stateDao().observeAll().first().associateBy { "${it.mediaType}:${it.mediaId}" }
        val cards = responses.flatMap { (type, response) ->
            response.results.filter { dto ->
                dto.inContentRegions(regions)
            }.map { it.toEntity(type) }.map { it.toDomain(states["${it.mediaType}:${it.tmdbId}"]) }
        }.distinctBy(MediaCard::stableKey).filterNot { it.stableKey in hidden }
            .filter { railId != RailIds.UPCOMING || it.releaseDate?.let { raw ->
                runCatching { LocalDate.parse(raw.take(10)).isAfter(today) }.getOrDefault(false)
            } == true }
        com.cinetrack.domain.DiscoverPage(cards, page < 500 && responses.any { page < it.second.totalPages })
    }

    suspend fun discoverMovies(filters: DiscoverMovieFilters): List<MediaCard> = discoverBatch(filters, 1).items

    /**
     * One batch of discover results: batch n covers TMDB pages 3n-2..3n. [DiscoverPage.hasMore]
     * says whether TMDB has pages beyond this batch, so the mosaic can keep loading.
     */
    suspend fun discoverBatch(filters: DiscoverMovieFilters, batch: Int): com.cinetrack.domain.DiscoverPage = coroutineScope {
        check(tmdbApiKey().isNotBlank()) { "TMDB API credential is missing" }
        val firstPage = (batch - 1) * 3 + 1
        // Unknown until TMDB answers; each response narrows it.
        var totalPages = Int.MAX_VALUE
        val allowedRegions = preferences.contentRegions.first()
        val hiddenDiscovery = preferences.hiddenDiscovery.first()
        val regionQuery = allowedRegions.takeIf { it.isNotEmpty() }?.sorted()?.joinToString("|")
        val states = database.stateDao().observeAll().first().associateBy { "${it.mediaType}:${it.mediaId}" }
        val genreQuery = filters.genreIds.takeIf { it.isNotEmpty() }?.sorted()?.joinToString(",")
        val excludedGenreQuery = filters.excludedGenreIds.takeIf { it.isNotEmpty() }?.sorted()?.joinToString(",")
        val providerQuery = filters.providerIds.takeIf { it.isNotEmpty() }?.sorted()?.joinToString("|")
        val watchRegion = preferences.effectiveProviderRegion()
        val decadeFrom = filters.dateFrom ?: filters.decadeStart?.let { "$it-01-01" }
        val decadeTo = filters.dateTo ?: filters.decadeStart?.let { "${it + 9}-12-31" }
        val minimumVotes = filters.minimumVotes ?: filters.minimumRating?.let { 50 }
        val entities = (firstPage until firstPage + 3).filter { it <= 500 }.flatMap { page ->
            if (page > totalPages) return@flatMap emptyList()
            val window = filters.trendingWindow
            if (window != null) {
                services.tmdb.trending(if (filters.mediaType == MediaType.TV) "tv" else "movie", window, page)
                    .also { totalPages = it.totalPages }.results
                    .filter { dto -> filters.genreIds.isEmpty() || dto.genreIds.any(filters.genreIds::contains) }
            } else if (filters.mediaType == MediaType.TV) {
                services.tmdb.discoverTv(
                    originCountries = regionQuery,
                    sortBy = filters.sortBy.replace("primary_release_date", "first_air_date"),
                    releaseYear = filters.releaseYear,
                    genreIds = genreQuery,
                    excludedGenreIds = excludedGenreQuery,
                    minimumRating = filters.minimumRating,
                    minimumVotes = minimumVotes,
                    maximumVotes = filters.maximumVotes,
                    providerIds = providerQuery,
                    watchRegion = watchRegion,
                    maximumRuntime = filters.maximumRuntime,
                    originalLanguage = filters.originalLanguage,
                    dateFrom = decadeFrom,
                    dateTo = decadeTo,
                    page = page,
                ).also { totalPages = it.totalPages }.results
            } else {
                services.tmdb.discoverMovies(
                    originCountries = regionQuery,
                    sortBy = filters.sortBy,
                    releaseYear = filters.releaseYear,
                    genreIds = genreQuery,
                    excludedGenreIds = excludedGenreQuery,
                    minimumRating = filters.minimumRating,
                    minimumVotes = minimumVotes,
                    maximumVotes = filters.maximumVotes,
                    providerIds = providerQuery,
                    watchRegion = watchRegion,
                    maximumRuntime = filters.maximumRuntime,
                    originalLanguage = filters.originalLanguage,
                    dateFrom = decadeFrom,
                    dateTo = decadeTo,
                    page = page,
                ).also { totalPages = it.totalPages }.results
            }
        }.filter { dto ->
            val regionAllowed = dto.inContentRegions(allowedRegions)
            val isAnime = dto.originalLanguage.equals("ja", ignoreCase = true) && 16 in dto.genreIds
            regionAllowed && when (filters.animeMode) {
                "only" -> isAnime
                "exclude" -> !isAnime
                else -> true
            }
        }.distinctBy(TmdbMediaDto::id)
            .map { it.toEntity(filters.mediaType) }
        database.mediaDao().upsertMedia(entities)
        val items = entities.map { entity -> entity.toDomain(states["${entity.mediaType}:${entity.tmdbId}"]) }
            .filterNot { it.stableKey in hiddenDiscovery }
            .filterNot { filters.hideWatched && (it.watched || it.status == LibraryStatus.COMPLETED) }
            .filterNot { filters.hideDropped && it.status == LibraryStatus.DROPPED }
        val lastPage = firstPage + 2
        com.cinetrack.domain.DiscoverPage(items, lastPage < 500 && lastPage < totalPages)
    }

    suspend fun loadStreamingProviders(mediaType: MediaType, selectedRegion: String? = null, failOnError: Boolean = false): List<StreamingProvider> {
        if (tmdbApiKey().isBlank()) return emptyList()
        val region = selectedRegion ?: preferences.effectiveProviderRegion()
        val result = cancellableResult {
            val providers = if (mediaType == MediaType.TV) {
                services.tmdb.tvProviders(region).results
            } else {
                services.tmdb.movieProviders(region).results
            }
            providers.distinctBy { it.id }
                .sortedBy { it.name.lowercase(Locale.getDefault()) }
                .map { provider ->
                    StreamingProvider(
                        id = provider.id,
                        name = provider.name,
                        logoUrl = provider.logoPath?.let { "https://image.tmdb.org/t/p/w92$it" },
                    )
                }
        }
        return if (failOnError) result.getOrThrow() else result.getOrDefault(emptyList())
    }

    suspend fun loadSettingsStreamingProviders(region: String): List<StreamingProvider> = coroutineScope {
        val movies = async { loadStreamingProviders(MediaType.MOVIE, region, failOnError = true) }
        val shows = async { loadStreamingProviders(MediaType.TV, region, failOnError = true) }
        (movies.await() + shows.await())
            .distinctBy(StreamingProvider::id)
            .sortedBy { it.name.lowercase(Locale.getDefault()) }
    }

    suspend fun search(query: String): List<MediaCard> {
        if (query.isBlank()) return emptyList()
        val allowedRegions = preferences.contentRegions.first().map { it.uppercase() }.toSet()
        val hiddenDiscovery = preferences.hiddenDiscovery.first()
        val states = database.stateDao().observeAll().first().associateBy { "${it.mediaType}:${it.mediaId}" }
        val remote = if (tmdbApiKey().isNotBlank()) runCatching {
            val candidates = services.tmdb.search(query).results
                .filter { it.mediaType == "movie" || it.mediaType == "tv" }
            val regionFiltered = if (allowedRegions.isEmpty()) candidates else coroutineScope {
                val requests = Semaphore(6)
                candidates.map { candidate ->
                    async {
                        requests.withPermit {
                            val embeddedCountries = candidate.originCountries +
                                candidate.productionCountries.map { it.code }
                            val countries = embeddedCountries.ifEmpty {
                                runCatching {
                                    val details = if (candidate.mediaType == "tv") {
                                        services.tmdb.show(candidate.id, append = "")
                                    } else {
                                        services.tmdb.movie(candidate.id, append = "")
                                    }
                                    details.originCountries + details.productionCountries.map { it.code }
                                }.getOrElse { error ->
                                    if (error is kotlinx.coroutines.CancellationException) throw error
                                    emptyList()
                                }
                            }
                            candidate.takeIf {
                                countries.any { country -> country.uppercase() in allowedRegions }
                            }
                        }
                    }
                }.mapNotNull { it.await() }
            }
            val items = regionFiltered
                .map { it.toEntity(if (it.mediaType == "tv") MediaType.TV else MediaType.MOVIE) }
            database.mediaDao().upsertMedia(items)
            items
        }.getOrElse { error ->
            if (error is kotlinx.coroutines.CancellationException) throw error
            emptyList()
        } else emptyList()
        // Discover search follows the TMDB content-region allowlist. When an
        // allowlist is active, do not merge unclassified local/imported rows back
        // into these results; Library and Progress searches remain unfiltered.
        val local = if (allowedRegions.isEmpty()) database.mediaDao().searchLocal(query).first() else emptyList()
        return (remote + local).distinctBy { "${it.mediaType}:${it.tmdbId}" }
            .filterNot { "${it.mediaType}:${it.tmdbId}" in hiddenDiscovery }
            .map { it.toDomain(states["${it.mediaType}:${it.tmdbId}"]) }
    }

    suspend fun searchPeople(query: String): List<PersonCard> {
        if (query.isBlank() || tmdbApiKey().isBlank()) return emptyList()
        return runCatching {
            services.tmdb.search(query).results.asSequence()
                .filter { it.mediaType == "person" && !it.name.isNullOrBlank() }
                .map { person ->
                    PersonCard(
                        id = person.id,
                        name = person.name.orEmpty(),
                        role = person.knownForDepartment.orEmpty(),
                        profileUrl = person.profilePath?.let { "https://image.tmdb.org/t/p/w500$it" },
                    )
                }
                .distinctBy(PersonCard::id)
                .toList()
        }.getOrElse { error ->
            if (error is kotlinx.coroutines.CancellationException) throw error
            emptyList()
        }
    }
}
