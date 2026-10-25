package com.cinetrack.data.repository

import kotlinx.coroutines.CancellationException
import com.cinetrack.BuildConfig
import com.cinetrack.data.local.AppDatabase
import com.cinetrack.data.local.EpisodeEntity
import com.cinetrack.data.local.MediaEntity
import com.cinetrack.data.local.toDomain
import com.cinetrack.data.local.toEntity
import com.cinetrack.data.remote.ApiServices
import com.cinetrack.data.remote.MdbListRatingRequest
import com.cinetrack.data.remote.TmdbMediaDto
import com.cinetrack.domain.EpisodeCard
import com.cinetrack.domain.MediaCard
import com.cinetrack.domain.MediaType
import com.cinetrack.domain.PersonCard
import com.cinetrack.domain.RatingScore
import com.cinetrack.domain.SeasonDetails
import com.cinetrack.domain.SeasonCard
import com.cinetrack.domain.TimelineCard
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate
import java.util.Locale
import com.cinetrack.data.remote.tmdbImageUrl

/**
 * Detail-page metadata (details, seasons, episodes, people, cast, ratings,
 * trailers, collections, recommendations) moved out of the former sync-host repository.
 * Episodes read through here are written to Room, the same as before.
 */
private val YOUTUBE_KEY = Regex("[A-Za-z0-9_-]{11}")

class MediaMetadataRepository(
    private val database: AppDatabase,
    private val services: ApiServices,
    private val preferences: AppPreferences,
    private val releaseSchedule: com.cinetrack.data.schedule.ReleaseScheduleRepository,
    private val tmdbApiKey: () -> String = { BuildConfig.TMDB_API_TOKEN },
    private val mdbListApiKey: () -> String = { BuildConfig.MDBLIST_API_KEY },
    /** TVDB character pictures through Floppy; null (or a failure) keeps the TMDB-only cast. */
    private val characterImages: (suspend (MediaType, Int) -> List<com.cinetrack.domain.CharacterImage>?)? = null,
) {
    private val castCache = BoundedLruCache<String, List<PersonCard>>(32)
    private val characterPictureCache = BoundedLruCache<String, List<com.cinetrack.domain.CharacterImage>>(32)
    private val recommendationCandidatesCache = BoundedLruCache<String, List<TmdbMediaDto>>(32)

    private val taglineCache = BoundedLruCache<String, String>(64)
    private val seasonDetailsCache = BoundedLruCache<String, SeasonDetails>(32)

    /** Hero enrichment is independent of the catalogue refresh and fetches no appended resources. */
    suspend fun loadTagline(media: MediaCard): String? {
        if (tmdbApiKey().isBlank()) return null
        val language = preferences.metadataLanguage.first()
        val key = "$language:${media.stableKey}"
        taglineCache[key]?.let { return it.takeIf(String::isNotBlank) }
        return kotlinx.coroutines.withTimeoutOrNull(8_000) {
            cancellableResult {
                val dto = if (media.type == MediaType.MOVIE) services.tmdb.movie(media.id, append = "")
                    else services.tmdb.show(media.id, append = "")
                dto.tagline.orEmpty().also { taglineCache[key] = it }.takeIf(String::isNotBlank)
            }.getOrNull()
        }
    }

    suspend fun loadSeasonDetails(show: MediaCard, number: Int): Result<SeasonDetails> = cancellableResult {
        check(tmdbApiKey().isNotBlank()) { "TMDB credential required" }
        val key = "${preferences.metadataLanguage.first()}:${show.id}:$number"
        seasonDetailsCache[key] ?: (kotlinx.coroutines.withTimeoutOrNull(15_000) {
            val dto = services.tmdb.season(show.id, number, append = "aggregate_credits")
            SeasonDetails(
                number = number,
                title = dto.name,
                overview = dto.overview,
                posterUrl = tmdbImageUrl(dto.posterPath, "w342"),
                airDate = dto.airDate,
                episodeCount = dto.episodes.size,
                runtimeMinutes = dto.episodes.mapNotNull { it.runtime?.takeIf { minutes -> minutes > 0 } }
                    .takeIf { it.isNotEmpty() }?.average()?.toInt(),
                score = dto.voteAverage?.takeIf { it > 0 },
                cast = castCards(dto.aggregateCredits).filter { it.isCastMember },
            ).also { seasonDetailsCache[key] = it }
        } ?: error("Season request timed out"))
    }

    suspend fun loadDetails(media: MediaCard): MediaCard {
        if (tmdbApiKey().isBlank()) return media
        return cancellableResult {
            val localized = if (media.type == MediaType.MOVIE) services.tmdb.movie(media.id, append = "credits,recommendations,watch/providers,videos,release_dates") else services.tmdb.show(media.id, append = "aggregate_credits,recommendations,watch/providers,videos")
            val english = if (localized.overview.isBlank() || (localized.title ?: localized.name).isNullOrBlank()) {
                runCatching {
                    if (media.type == MediaType.MOVIE) services.tmdb.movie(media.id, language = "en-US")
                    else services.tmdb.show(media.id, language = "en-US")
                }.getOrNull()
            } else null
            val dto = localized.copy(
                title = localized.title?.takeIf(String::isNotBlank) ?: english?.title,
                name = localized.name?.takeIf(String::isNotBlank) ?: english?.name,
                overview = localized.overview.ifBlank { english?.overview.orEmpty() },
                seasons = localized.seasons.ifEmpty { english?.seasons.orEmpty() },
            )
            recommendationCandidatesCache[media.stableKey] = dto.recommendations?.results.orEmpty()
            castCards(dto.aggregateCredits ?: dto.credits).takeIf { it.isNotEmpty() }?.let {
                castCache["${preferences.metadataLanguage.first()}:${media.stableKey}"] = it
            }
            val providerRegion = preferences.effectiveProviderRegion()
            val providerCountry = dto.watchProviders?.results?.get(providerRegion)
            val preferredProviders = preferences.preferredProviders.first()
            val visibleTypes = preferences.visibleProviderTypes.first()
            fun List<com.cinetrack.data.remote.TmdbProviderDto>.visibleProviders() =
                distinctBy { it.id }
                    .let { providers ->
                        if (preferredProviders.isEmpty()) providers
                        else providers.filter { it.name in preferredProviders }
                    }
                    .sortedWith(
                    compareByDescending<com.cinetrack.data.remote.TmdbProviderDto> { it.name in preferredProviders }
                        .thenBy { it.name.lowercase() },
                )
            val digitalRelease = if (media.type == MediaType.MOVIE) digitalReleaseFor(dto.releaseDates, providerRegion) else null
            val providers = providerCountry
                ?.let { it.flatrate + it.rent + it.buy + it.free + it.ads }
                .orEmpty()
                .visibleProviders()
            dto.toEntity(media.type).toDomain().copy(
                status = media.status,
                watched = media.watched,
                libraryUpdatedAt = media.libraryUpdatedAt,
                tmdbStatus = dto.status,
                networks = dto.networks.map { it.name }.filter(String::isNotBlank),
                budget = dto.budget?.takeIf { it > 0L },
                boxOffice = dto.revenue?.takeIf { it > 0L },
                digitalRelease = digitalRelease?.first,
                digitalReleaseCountry = digitalRelease?.second?.takeIf { !it.equals(providerRegion, ignoreCase = true) },
                productionCompanies = dto.productionCompanies.map { it.name }.filter(String::isNotBlank),
                productionCountries = dto.productionCountries.map { country -> country.name.ifBlank { country.code } },
                originalLanguage = dto.originalLanguage?.takeIf(String::isNotBlank)?.uppercase(),
                providers = providers.map { it.name },
                providerLogos = providers.mapNotNull { provider ->
                    tmdbImageUrl(provider.logoPath, "w92")?.let { provider.name to it }
                }.toMap(),
                subscriptionProviders = providerCountry?.flatrate.orEmpty().visibleProviders().map { it.name },
                rentProviders = providerCountry?.rent.orEmpty().visibleProviders().map { it.name },
                buyProviders = providerCountry?.buy.orEmpty().visibleProviders().map { it.name },
                freeProviders = providerCountry?.free.orEmpty().visibleProviders().map { it.name },
                adsProviders = providerCountry?.ads.orEmpty().visibleProviders().map { it.name },
                providerLink = providerCountry?.link?.takeIf { it.startsWith("https://", ignoreCase = true) },
                visibleProviderTypes = visibleTypes,
                providerAvailabilityExists = providerCountry?.let { (it.flatrate + it.rent + it.buy + it.free + it.ads).isNotEmpty() } == true,
                seasons = dto.seasons.filter { it.number > 0 }.map { season ->
                    SeasonCard(
                        number = season.number,
                        title = season.name,
                        episodeCount = season.episodeCount,
                        posterUrl = tmdbImageUrl(season.posterPath, "w342"),
                    )
                },
            )
        }.getOrDefault(media)
    }

    suspend fun loadMedia(type: MediaType, id: Int): MediaCard? {
        val state = database.stateDao().stateSnapshot().firstOrNull {
            it.mediaType == type.name && it.mediaId == id
        }
        database.mediaDao().get(type.name, id)?.let { return it.toDomain(state) }
        if (tmdbApiKey().isBlank()) return null
        return cancellableResult {
            val dto = if (type == MediaType.MOVIE) services.tmdb.movie(id) else services.tmdb.show(id)
            val entity = dto.toEntity(type)
            database.mediaDao().upsertMedia(listOf(entity))
            entity.toDomain(state)
        }.getOrNull()
    }

    suspend fun loadAllEpisodes(show: MediaCard): List<EpisodeCard> {
        val detailedShow = if (show.seasons.isEmpty()) loadDetails(show) else show
        val seasons = detailedShow.seasons.map(SeasonCard::number).ifEmpty { listOf(1) }
        val cached = loadCachedEpisodes(show.id)
        val expectedCount = detailedShow.seasons.sumOf(SeasonCard::episodeCount)
        val tmdbBackedCount = cached.count { it.id > 0 }
        if (tmdbBackedCount == 0 || expectedCount <= 0 || tmdbBackedCount < expectedCount) {
            // TMDB remains the independent source for the complete seasons and
            // episodes catalogue. Simkl is applied afterwards only as a date
            // overlay, so disconnecting a Simkl account never hides this section.
            val requests = Semaphore(4)
            coroutineScope {
                seasons.distinct().sorted().map { season ->
                    async { requests.withPermit { loadEpisodes(detailedShow, season) } }
                }.awaitAll()
            }
        }
        releaseSchedule.refresh(listOf(detailedShow), false)
        return loadCachedEpisodes(show.id)
            .filter { it.id > 0 }
            .sortedWith(compareBy(EpisodeCard::season, EpisodeCard::number))
    }

    suspend fun loadPerson(person: PersonCard): PersonCard {
        if (tmdbApiKey().isBlank()) return person
        return cancellableResult {
            val localized = services.tmdb.person(person.id)
            val english = if (localized.biography.isBlank()) {
                runCatching { services.tmdb.person(person.id, language = "en-US") }.getOrNull()
            } else null
            val details = localized.copy(
                name = localized.name.ifBlank { english?.name.orEmpty() },
                biography = localized.biography.ifBlank { english?.biography.orEmpty() },
                placeOfBirth = localized.placeOfBirth?.takeIf(String::isNotBlank) ?: english?.placeOfBirth,
                profilePath = localized.profilePath ?: english?.profilePath,
            )
            val credits = services.tmdb.combinedCredits(person.id).cast
                .mapNotNull { credit ->
                    val type = if (credit.mediaType == "tv") MediaType.TV else MediaType.MOVIE
                    val title = if (type == MediaType.TV) credit.name else credit.title
                    val date = if (type == MediaType.TV) credit.firstAirDate else credit.releaseDate
                    if (title.isNullOrBlank()) return@mapNotNull null
                    MediaCard(
                        id = credit.id,
                        type = type,
                        title = title,
                        releaseDate = date,
                        posterUrl = tmdbImageUrl(credit.posterPath, "w500"),
                        score = credit.voteAverage,
                    )
                }
                .distinctBy(MediaCard::stableKey)
                .sortedByDescending { credit ->
                    credit.releaseDate?.let { raw -> runCatching { LocalDate.parse(raw) }.getOrNull() }
                }
            PersonCard(
                id = details.id,
                name = details.name,
                role = person.role,
                profileUrl = tmdbImageUrl(details.profilePath, "w500"),
                biography = details.biography,
                birthday = details.birthday,
                deathday = details.deathday,
                placeOfBirth = details.placeOfBirth,
                movieCredits = credits,
            )
        }.getOrDefault(person)
    }

    private fun castCards(credits: com.cinetrack.data.remote.TmdbCreditsDto?): List<PersonCard> {
        val castIds = credits?.cast.orEmpty().map { it.id }.toSet()
        val directingJobs = setOf("Director", "Creator", "Executive Producer")
        return (credits?.cast.orEmpty() + credits?.crew.orEmpty()).groupBy { it.id }.map { (_, roles) ->
            val person = roles.first()
            PersonCard(
                id = person.id,
                isCastMember = person.id in castIds,
                isDirector = roles.any { credit ->
                    credit.job in directingJobs || credit.jobs.any { it.job in directingJobs }
                },
                name = person.name,
                role = roles.flatMap { credit ->
                    listOfNotNull(credit.character, credit.job) +
                        (credit.roles + credit.jobs).mapNotNull { it.character ?: it.job }
                }.filter { it.isNotBlank() }.distinct().joinToString(" · "),
                character = roles.flatMap { credit -> listOfNotNull(credit.character) + credit.roles.mapNotNull { it.character } }
                    .filter { it.isNotBlank() }.distinct().joinToString(" · "),
                profileUrl = tmdbImageUrl(person.profilePath, "w500"),
            )
        }
    }

    suspend fun loadCast(media: MediaCard): List<PersonCard> {
        if (tmdbApiKey().isBlank()) return emptyList()
        val key = "${preferences.metadataLanguage.first()}:${media.stableKey}"
        castCache[key]?.let { return it }
        return cancellableResult {
            val dto = if (media.type == MediaType.MOVIE) services.tmdb.movie(media.id) else services.tmdb.show(media.id, append = "aggregate_credits")
            castCards(dto.aggregateCredits ?: dto.credits).also { if (it.isNotEmpty()) castCache[key] = it }
        }.getOrDefault(emptyList())
    }

    /**
     * TVDB character pictures through Floppy, loaded after the TMDB cast so the
     * row never waits for them. A failure returns nothing and retries on the next open.
     */
    suspend fun loadCharacterPictures(media: MediaCard): List<com.cinetrack.domain.CharacterImage> {
        val fetch = characterImages ?: return emptyList()
        characterPictureCache[media.stableKey]?.let { return it }
        return cancellableResult { fetch(media.type, media.id).orEmpty() }
            .onSuccess { characterPictureCache[media.stableKey] = it }
            .getOrDefault(emptyList())
    }

    suspend fun loadEpisodes(show: MediaCard, season: Int = 1): List<EpisodeCard> {
        val watchedNumbers = watchedEpisodeNumbers(show.id)
        val cachedEntities = database.mediaDao().episodesForShow(show.id)
            .filter { it.season == season }
        val cachedByNumber = cachedEntities.associateBy(EpisodeEntity::number)
        val cached = cachedEntities
            .map { entity ->
                entity.toDomain().copy(watched = (entity.season to entity.number) in watchedNumbers)
            }
        if (tmdbApiKey().isBlank()) return cached
        return cancellableResult {
            val localized = services.tmdb.season(show.id, season).episodes
            val englishByNumber = if (localized.any { it.name.isBlank() || it.overview.isBlank() }) {
                runCatching { services.tmdb.season(show.id, season, language = "en-US").episodes }
                    .getOrDefault(emptyList())
                    .associateBy { it.number }
            } else emptyMap()
            val tmdbDates = localized.associate { com.cinetrack.data.schedule.EpisodeKey(show.id, it.season, it.number) to it.airDate }
            val shownDates = com.cinetrack.data.schedule.recordAirDates(
                database, com.cinetrack.data.schedule.AirDateSource.TMDB, tmdbDates, preferences.airDateSource.first(),
                legacy = cachedByNumber.values.associate { com.cinetrack.data.schedule.EpisodeKey(it.showId, it.season, it.number) to it.airDate },
            )
            localized.map { source ->
                val english = englishByNumber[source.number]
                val episode = source.copy(
                    name = source.name.ifBlank { english?.name.orEmpty() },
                    overview = source.overview.ifBlank { english?.overview.orEmpty() },
                )
                EpisodeCard(
                    id = episode.id,
                    showId = show.id,
                    season = episode.season,
                    number = episode.number,
                    title = episode.name,
                    overview = episode.overview,
                    // The chosen provider's date (TMDB, TVmaze or Simkl), with fallback.
                    airDate = shownDates[com.cinetrack.data.schedule.EpisodeKey(show.id, episode.season, episode.number)] ?: episode.airDate,
                    stillUrl = tmdbImageUrl(episode.stillPath, "w780"),
                    runtimeMinutes = episode.runtime,
                    watched = (episode.season to episode.number) in watchedNumbers,
                )
            }.also { episodes ->
                if (episodes.isNotEmpty()) database.mediaDao().upsertEpisodes(episodes.map { it.toEntity() })
            }
        }.getOrDefault(cached)
    }

    suspend fun loadCachedEpisodes(showId: Int): List<EpisodeCard> {
        val watchedNumbers = watchedEpisodeNumbers(showId)
        return database.mediaDao().episodesForShow(showId).map { entity ->
            entity.toDomain().copy(watched = (entity.season to entity.number) in watchedNumbers)
        }
    }

    internal suspend fun watchedEpisodeNumbers(showId: Int): Set<Pair<Int, Int>> =
        database.timelineDao().episodeHistoryForShow(MediaType.TV.name, showId)
            .mapNotNull { history ->
                val season = history.season ?: return@mapNotNull null
                val episode = history.episodeNumber ?: return@mapNotNull null
                season to episode
            }
            .toSet()

    suspend fun loadEpisode(show: MediaCard, season: Int, number: Int): EpisodeCard? {
        val watched = (season to number) in watchedEpisodeNumbers(show.id)
        val cached = database.mediaDao().episode(show.id, season, number)?.toDomain()?.copy(watched = watched)
        if (tmdbApiKey().isBlank()) return cached
        return cancellableResult {
            val localized = services.tmdb.episode(show.id, season, number)
            val english = if (localized.name.isBlank() || localized.overview.isBlank()) {
                runCatching { services.tmdb.episode(show.id, season, number, language = "en-US") }.getOrNull()
            } else null
            localized.copy(
                name = localized.name.ifBlank { english?.name.orEmpty() },
                overview = localized.overview.ifBlank { english?.overview.orEmpty() },
            ).let {
                EpisodeCard(
                    id = it.id,
                    showId = show.id,
                    season = it.season,
                    number = it.number,
                    title = it.name,
                    overview = it.overview,
                    // The chosen provider's date: opening an episode never swaps a
                    // calendar time for TMDB's bare date unless TMDB is chosen.
                    airDate = com.cinetrack.data.schedule.recordAirDates(
                        database, com.cinetrack.data.schedule.AirDateSource.TMDB,
                        mapOf(Triple(show.id, it.season, it.number) to it.airDate),
                        preferences.airDateSource.first(),
                        legacy = mapOf(Triple(show.id, it.season, it.number) to cached?.airDate),
                    ).values.firstOrNull() ?: it.airDate,
                    stillUrl = tmdbImageUrl(it.stillPath, "w1280"),
                    runtimeMinutes = it.runtime,
                    watched = watched,
                    score = it.voteAverage?.takeIf { score -> score > 0 },
                ).also { episode -> database.mediaDao().upsertEpisodes(listOf(episode.toEntity())) }
            }
        }.getOrNull() ?: cached
    }

    suspend fun loadTrailerKey(media: MediaCard): String? {
        if (tmdbApiKey().isBlank()) return null
        return cancellableResult {
            val videos = if (media.type == MediaType.TV) {
                services.tmdb.show(media.id).videos?.results.orEmpty()
            } else {
                services.tmdb.movie(media.id).videos?.results.orEmpty()
            }
            videos.asSequence()
                // The key is handed to the player's JavaScript, so accept only the 11-character YouTube id shape.
                .filter { it.site.equals("YouTube", ignoreCase = true) && YOUTUBE_KEY.matches(it.key) }
                .sortedWith(
                    compareByDescending<com.cinetrack.data.remote.TmdbVideoDto> { it.official }
                        .thenByDescending { it.type.equals("Trailer", ignoreCase = true) }
                        .thenByDescending { it.publishedAt.orEmpty() },
                )
                .firstOrNull()
                ?.key
        }.getOrNull()
    }

    suspend fun loadEpisodeCast(show: MediaCard, season: Int, number: Int): List<PersonCard> {
        if (tmdbApiKey().isBlank()) return emptyList()
        return cancellableResult {
            val episode = services.tmdb.episode(show.id, season, number)
            (episode.credits?.cast.orEmpty() + episode.guestStars + episode.credits?.crew.orEmpty() + episode.crew).distinctBy { it.id }.map {
                PersonCard(
                    id = it.id,
                    name = it.name,
                    role = it.character ?: it.job.orEmpty(),
                    profileUrl = tmdbImageUrl(it.profilePath, "w500"),
                    // Cast and guest stars carry a character; crew only a job.
                    isCastMember = it.character != null,
                    character = it.character.orEmpty(),
                )
            }
        }.getOrDefault(emptyList())
    }

    suspend fun loadCollection(media: MediaCard): List<MediaCard> {
        val collectionId = media.collectionId ?: return emptyList()
        if (tmdbApiKey().isBlank()) return emptyList()
        return cancellableResult {
            services.tmdb.collection(collectionId).parts
                .sortedBy { it.releaseDate.orEmpty() }
                .map { it.toEntity(MediaType.MOVIE).toDomain() }
        }.getOrDefault(emptyList())
    }

    suspend fun loadRecommendations(media: MediaCard): List<MediaCard> {
        if (tmdbApiKey().isBlank()) return emptyList()
        return cancellableResult {
            val allowedRegions = preferences.contentRegions.first()
            val hiddenDiscovery = preferences.hiddenDiscovery.first()
            val candidates = (recommendationCandidatesCache[media.stableKey] ?: run {
                val dto = if (media.type == MediaType.MOVIE) services.tmdb.movie(media.id) else services.tmdb.show(media.id)
                dto.recommendations?.results.orEmpty().also { recommendationCandidatesCache[media.stableKey] = it }
            }).take(18)
            val filteredCandidates = if (allowedRegions.isEmpty()) candidates else coroutineScope {
                val requests = Semaphore(4)
                candidates.map { candidate -> async {
                    requests.withPermit {
                        val countries = candidate.originCountries.ifEmpty {
                            runCatching {
                                val details = if (media.type == MediaType.MOVIE) services.tmdb.movie(candidate.id) else services.tmdb.show(candidate.id)
                                details.originCountries + details.productionCountries.map { it.code }
                            }.getOrDefault(emptyList())
                        }
                        candidate.takeIf { countries.any { it.uppercase() in allowedRegions } }
                    }
                } }.mapNotNull { it.await() }
            }
            filteredCandidates
                .map { it.toEntity(media.type).toDomain() }
                .filterNot { it.id == media.id }
                .filterNot { it.stableKey in hiddenDiscovery }
                .distinctBy(MediaCard::stableKey)
                .take(18)
        }.getOrDefault(emptyList())
    }

    /** Loads cast/crew only when the Statistics tab requests it, avoiding startup and scrolling work. */
    suspend fun loadViewingPeople(history: List<TimelineCard>): Pair<List<PersonCard>, List<PersonCard>> = coroutineScope {
        val weights = history.groupingBy { it.media.stableKey }.eachCount()
        val media = history.map(TimelineCard::media).distinctBy(MediaCard::stableKey).take(12)
        val requests = Semaphore(4)
        val credits = media.map { item -> async {
            item to requests.withPermit { loadCast(item) }
        } }.map { it.await() }
        val actorCounts = mutableMapOf<Int, Pair<PersonCard, Int>>()
        val directorCounts = mutableMapOf<Int, Pair<PersonCard, Int>>()
        credits.forEach { (item, people) ->
            val weight = weights[item.stableKey] ?: 1
            people.distinctBy(PersonCard::id).forEach { person ->
                // Full credits include writers, camera, sound and other crew.
                // Only actual cast members belong in the actor statistics.
                if (person.isCastMember) {
                    actorCounts[person.id] = person to ((actorCounts[person.id]?.second ?: 0) + weight)
                }
                if (person.isDirector) {
                    directorCounts[person.id] = person to ((directorCounts[person.id]?.second ?: 0) + weight)
                }
            }
        }
        fun Map<Int, Pair<PersonCard, Int>>.top() = values
            .sortedWith(compareByDescending<Pair<PersonCard, Int>> { it.second }.thenBy { it.first.name })
            .take(5)
            .map { it.first }
        actorCounts.top() to directorCounts.top()
    }

    suspend fun loadRatings(media: MediaCard): List<RatingScore> {
        val enabledSources = preferences.ratingSources.first()
        val displayNames = linkedMapOf(
            "tmdb" to "TMDB",
            "imdb" to "IMDb",
            "metacritic" to "Metacritic",
            "tomatoes" to "R.Tomatoes",
        )
        val enabledDisplayNames = displayNames.filterKeys(enabledSources::contains)
        val base = enabledDisplayNames.map { (source, label) ->
            RatingScore(label, if (source == "tmdb" && media.score != null) "%.1f".format(media.score) else "—")
        }
        if (mdbListApiKey().isBlank()) return base
        val type = if (media.type == MediaType.MOVIE) "movie" else "show"
        fun formatted(source: String, value: Double): String = when (source) {
            "metacritic" -> value.toInt().toString()
            "tomatoes" -> "${value.toInt()}%"
            else -> "%.1f".format(value)
        }
        val remote = linkedMapOf<String, String>()
        // MDBList's single-title response carries every available rating in one
        // request. Prefer it so free-tier rate limiting cannot turn four parallel
        // requests into four dashes.
        val mediaInfoLoaded = runCatching {
            services.mdbList.mediaInfo(type, media.id, mdbListApiKey())["ratings"]?.jsonArray.orEmpty().forEach { element ->
                    val rating = element.jsonObject
                    val rawSource = listOf("source", "provider", "name").firstNotNullOfOrNull { key ->
                        rating[key]?.jsonPrimitive?.contentOrNull
                    }?.lowercase()?.replace(" ", "")
                    val source = when (rawSource) {
                        "rottentomatoes", "rtomatoes" -> "tomatoes"
                        else -> rawSource
                    }
                    val value = listOf("value", "rating", "score").firstNotNullOfOrNull { key ->
                        rating[key]?.jsonPrimitive?.doubleOrNull
                    }
                    if (source != null && value != null && source in displayNames) remote[source] = formatted(source, value)
                }
        }.isSuccess && remote.isNotEmpty()
        if (!mediaInfoLoaded) coroutineScope {
            val fallback = displayNames.keys.map { source -> async {
                runCatching {
                    services.mdbList.rating(
                        mediaType = type,
                        ratingSource = source,
                        apiKey = mdbListApiKey(),
                        request = MdbListRatingRequest(listOf(media.id.toString()), "tmdb"),
                    ).ratings.firstOrNull()?.rating?.let { source to formatted(source, it) }
                }.getOrNull()
            } }.mapNotNull { it.await() }
            remote.putAll(fallback)
        }
        return enabledDisplayNames.map { (source, label) ->
            RatingScore(label, remote[source] ?: if (source == "tmdb" && media.score != null) "%.1f".format(media.score) else "—")
        }
    }

    /** The metadata language changed (MetadataSettingsRepository): drop what was cached in the old one. */
    fun clearLanguageCaches() {
        castCache.clear()
        recommendationCandidatesCache.clear()
    }
}

/** TMDB accepts one watch region. Make Discover/provider availability follow
 * the content-region filter, then fall back to metadata and device regions. */
internal suspend fun AppPreferences.effectiveProviderRegion(): String = com.cinetrack.domain.resolveProviderRegion(
    providerRegion.first(), contentRegions.first(),
    metadataRegion.first(), Locale.getDefault().country,
)

internal fun TmdbMediaDto.toEntity(type: MediaType) = MediaEntity(
    mediaType = type.name,
    tmdbId = id,
    title = title ?: name ?: "Untitled",
    overview = overview,
    posterPath = posterPath,
    backdropPath = backdropPath,
    releaseDate = releaseDate ?: firstAirDate,
    score = voteAverage,
    runtimeMinutes = runtime ?: episodeRunTime.firstOrNull(),
    genres = if (genres.isNotEmpty()) genres.joinToString("|") { it.name }
    else genreIds.mapNotNull(::tmdbGenreName).joinToString("|"),
    providers = watchProviders?.results?.get("IT")?.let { (it.flatrate + it.rent + it.buy).distinctBy { p -> p.id }.joinToString("|") { p -> p.name } }.orEmpty(),
    collectionId = collection?.id,
)

internal fun tmdbGenreName(id: Int): String? = when (id) {
    12 -> "Adventure"
    14 -> "Fantasy"
    16 -> "Animation"
    18 -> "Drama"
    27 -> "Horror"
    28 -> "Action"
    35 -> "Comedy"
    36 -> "History"
    37 -> "Western"
    53 -> "Thriller"
    80 -> "Crime"
    99 -> "Documentary"
    878 -> "Science Fiction"
    9648 -> "Mystery"
    10402 -> "Music"
    10749 -> "Romance"
    10751 -> "Family"
    10752 -> "War"
    10759 -> "Action & Adventure"
    10762 -> "Kids"
    10763 -> "News"
    10764 -> "Reality"
    10765 -> "Sci-Fi & Fantasy"
    10766 -> "Soap"
    10767 -> "Talk"
    10768 -> "War & Politics"
    else -> null
}

internal fun EpisodeCard.toEntity() = EpisodeEntity(
    showId = showId,
    season = season,
    number = number,
    tmdbId = id.takeIf { it > 0 },
    title = title,
    overview = overview,
    airDate = airDate,
    stillPath = stillUrl?.substringAfter("/w780", stillUrl)?.substringAfter("/w1280", stillUrl),
    runtimeMinutes = runtimeMinutes,
)

internal fun EpisodeEntity.toDomain() = EpisodeCard(
    id = tmdbId ?: 0,
    showId = showId,
    season = season,
    number = number,
    title = title,
    overview = overview,
    airDate = airDate,
    stillUrl = tmdbImageUrl(stillPath, "w780"),
    runtimeMinutes = runtimeMinutes,
)

internal suspend fun <T> cancellableResult(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Throwable) {
    Result.failure(error)
}
