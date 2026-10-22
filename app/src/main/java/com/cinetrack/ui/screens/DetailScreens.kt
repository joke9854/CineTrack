@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.cinetrack.ui.screens

import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.animateContentSize
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import com.cinetrack.ui.components.liquidGlassPill
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Check
import com.cinetrack.domain.watchSummary
import androidx.compose.material.icons.filled.SkipNext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.map
import androidx.compose.ui.draw.alpha
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cinetrack.R
import com.cinetrack.domain.EpisodeCard
import com.cinetrack.domain.LibraryStatus
import com.cinetrack.domain.MediaCard
import com.cinetrack.domain.MediaType
import com.cinetrack.domain.PersonCard
import com.cinetrack.domain.TimelineCard
import com.cinetrack.ui.CineTrackViewModel
import com.cinetrack.ui.components.AdaptiveBackground
import com.cinetrack.ui.components.GlassBackButton
import com.cinetrack.ui.components.LoadingPane
import com.cinetrack.ui.components.LibraryStatusSheet
import com.cinetrack.ui.components.MediaRail
import com.cinetrack.ui.components.PrimaryAction
import com.cinetrack.ui.components.SectionHeader
import com.cinetrack.ui.components.GlassMaterial
import com.cinetrack.ui.components.rememberDetailGlassState
import com.cinetrack.ui.components.libraryStatusIcon
import com.cinetrack.ui.components.rememberUiAction
import com.cinetrack.ui.theme.Success
import com.cinetrack.ui.theme.TextMuted
import com.cinetrack.ui.theme.TextPrimary
import com.cinetrack.ui.theme.TextSecondary
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import com.cinetrack.ui.theme.LocalDetailAccent
import com.cinetrack.ui.theme.Motion
import com.cinetrack.ui.theme.Radius
import com.cinetrack.ui.theme.Spacing
import com.cinetrack.ui.theme.SurfacePalette

/** On detail pages the accent follows the poster (see [LocalDetailAccent]). */
private val Accent: Color
    @Composable get() = LocalDetailAccent.current?.value ?: com.cinetrack.ui.theme.Accent
private val AccentLight: Color
    @Composable get() = LocalDetailAccent.current?.value?.let { androidx.compose.ui.graphics.lerp(it, Color.White, .32f) }
        ?: com.cinetrack.ui.theme.AccentLight

@Composable
internal fun rememberPosterAccentEnabled(viewModel: CineTrackViewModel): Boolean {
    val enabled by remember(viewModel) { viewModel.state.map { it.posterAccent }.distinctUntilChanged() }
        .collectAsStateWithLifecycle(initialValue = true)
    return enabled
}

@Composable
fun DetailScreen(
    media: MediaCard?,
    people: List<PersonCard>,
    episodes: List<EpisodeCard>,
    history: List<TimelineCard>,
    recommended: List<MediaCard>,
    viewModel: CineTrackViewModel,
    detailViewModel: com.cinetrack.ui.detail.DetailViewModel,
    progressViewModel: com.cinetrack.ui.progress.ProgressViewModel,
    onBack: () -> Unit,
    onStatus: (MediaCard, LibraryStatus) -> Unit,
    onMedia: (MediaCard) -> Unit,
    onEpisode: (EpisodeCard) -> Unit,
    initialSeason: Int? = null,
    initialEpisode: Int? = null,
) {
    if (media == null) {
        AdaptiveBackground { LoadingPane(); GlassBackButton(onClick = onBack, modifier = Modifier.statusBarsPadding().padding(Spacing.md)) }
        return
    }
    val cachedDetail = detailViewModel.cachedDetails(media)
    val cachedEpisodes = detailViewModel.cachedEpisodes(media.id)
    var detail by remember(media.stableKey) {
        mutableStateOf(
            (cachedDetail ?: media).copy(
                status = media.status,
                watched = media.watched,
                libraryUpdatedAt = media.libraryUpdatedAt,
            ),
        )
    }
    var ratings by remember(media.stableKey) { mutableStateOf(detailViewModel.cachedRatings(media)) }
    // Until these are known the page keeps their place, so nothing shifts as data arrives.
    var detailLoaded by remember(media.stableKey) { mutableStateOf(cachedDetail != null) }
    var ratingsLoaded by remember(media.stableKey) { mutableStateOf(ratings.isNotEmpty()) }
    val ratingsExpected = remember { viewModel.state.value.mdbListApiConfigured }
    var detailPeople by remember(media.stableKey) { mutableStateOf(detailViewModel.cachedCast(media).ifEmpty { people }) }
    var castLoaded by remember(media.stableKey) { mutableStateOf(detailViewModel.cachedCast(media).isNotEmpty()) }
    var detailEpisodes by remember(media.stableKey) {
        mutableStateOf(mergeWatchedEpisodes(cachedEpisodes.ifEmpty { episodes }, episodes, history))
    }
    var collectionItems by remember(media.stableKey) { mutableStateOf(detailViewModel.cachedCollection(media)) }
    var moreLikeThis by remember(media.stableKey) { mutableStateOf(detailViewModel.cachedRecommendations(media).ifEmpty { recommended }) }
    var librarySheet by remember { mutableStateOf(false) }
    var trailerSheet by remember(media.stableKey) { mutableStateOf(false) }
    var trailerKey by remember(media.stableKey) { mutableStateOf<String?>(null) }
    var trailerLoading by remember(media.stableKey) { mutableStateOf(false) }
    var trailerAttempt by remember(media.stableKey) { mutableStateOf(0) }
    var selectedSeason by remember(media.stableKey) { mutableStateOf<Int?>(null) }
    var selectedPerson by remember { mutableStateOf<PersonCard?>(null) }
    var returnToSeason by remember(media.stableKey) { mutableStateOf<Int?>(null) }
    var showFullCast by remember(media.stableKey) { mutableStateOf(false) }
    // Characters is the first headline, so it is selected whenever pictures exist.
    var showCharacters by remember(media.stableKey) { mutableStateOf(true) }
    var pendingPreviousEpisodes by remember(media.stableKey) {
        mutableStateOf<Pair<EpisodeCard, List<EpisodeCard>>?>(null)
    }
    /** Aired, unwatched episodes waiting for the user to confirm marking the whole show. */
    var pendingShowEpisodes by remember(media.stableKey) { mutableStateOf<List<EpisodeCard>?>(null) }
    var confirmUnmarkShow by remember(media.stableKey) { mutableStateOf(false) }
    val openLibrarySheet = rememberUiAction { librarySheet = true }
    LaunchedEffect(media.status, media.watched, media.libraryUpdatedAt) {
        // Keep the action label/pill tied to the shared Room state even when a
        // Simkl push or another screen changes this title while detail stays open.
        detail = detail.copy(
            status = media.status,
            watched = media.watched,
            libraryUpdatedAt = media.libraryUpdatedAt,
        )
    }
    var refreshNonce by remember(media.stableKey) { mutableStateOf(0) }
    var refreshing by remember(media.stableKey) { mutableStateOf(false) }
    val refreshScope = rememberCoroutineScope()
    LaunchedEffect(media.stableKey, refreshNonce) {
        val loadedDetail = detailViewModel.loadDetails(media)
        detail = loadedDetail.copy(
            status = media.status,
            watched = media.watched,
            libraryUpdatedAt = media.libraryUpdatedAt,
        )
        detailLoaded = true
        coroutineScope {
            launch {
                ratings = detailViewModel.loadRatings(loadedDetail).ifEmpty { ratings }
                ratingsLoaded = true
            }
            launch {
                detailPeople = detailViewModel.loadCast(loadedDetail).ifEmpty { detailPeople }
                castLoaded = true
                // Character pictures arrive later through Floppy; the TMDB cast is already on screen.
                detailPeople = detailViewModel.withCharacterPictures(loadedDetail, detailPeople)
            }
            launch {
                if (loadedDetail.type == MediaType.TV) {
                    detailEpisodes = mergeWatchedEpisodes(detailViewModel.loadAllEpisodes(loadedDetail), episodes, history)
                }
            }
            launch { collectionItems = detailViewModel.loadCollection(loadedDetail) }
            launch { moreLikeThis = detailViewModel.loadRecommendations(loadedDetail).ifEmpty { recommended } }
        }
    }
    LaunchedEffect(episodes, history) {
        if (detail.type == MediaType.TV) {
            detailEpisodes = mergeWatchedEpisodes(
                detailViewModel.cachedEpisodes(detail.id).ifEmpty { detailEpisodes },
                episodes,
                history,
            )
        }
    }
    val posterAccent = com.cinetrack.ui.components.rememberPosterAccent(detail.posterUrl ?: detail.backdropUrl, rememberPosterAccentEnabled(viewModel))
    androidx.compose.runtime.CompositionLocalProvider(LocalDetailAccent provides posterAccent) {
    val detailGlassState = rememberDetailGlassState()
    AdaptiveBackground(
        artworkUrl = detail.posterUrl ?: detail.backdropUrl,
        hazeState = detailGlassState,
        blurBackdrop = true,
    ) {
        val detailListState = rememberLazyListState()
        val headerHaze = remember { dev.chrisbanes.haze.HazeState() }
        Box(Modifier.fillMaxSize()) {
        LazyColumn(state = detailListState, modifier = Modifier.fillMaxSize().hazeSource(headerHaze), contentPadding = PaddingValues(bottom = 0.dp)) {
            item { DetailHero(detail) }
            item(key = "detail_identity") {
                DetailSectionSurface(first = true) {
                    DetailIdentity(detail, loaded = detailLoaded)
                }
            }
            val tmdbScore = detail.score?.takeIf { it > 0 }
            val pendingRatings = !ratingsLoaded && ratingsExpected
            if (ratings.any { it.score.any(Char::isDigit) } || tmdbScore != null || pendingRatings) {
                item(key = "detail_ratings") {
                    DetailSectionSurface(bottomGap = if (detail.overview.isNotBlank()) DetailLayout.RatingsGap else DetailLayout.SectionGap) {
                        RatingsSection(ratings, tmdbScore, pending = pendingRatings)
                    }
                }
            }
            item(key = "detail_actions") {
                DetailSectionSurface {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        val started = detail.type == MediaType.TV && detailEpisodes.any(EpisodeCard::watched)
                        val nextUp = remember(detailEpisodes) { if (detail.type == MediaType.TV) nextAiredEpisode(detailEpisodes) else null }
                        // What comes out next: the film itself, or the series' next unaired episode.
                        val upcomingEpisode = remember(detailEpisodes) {
                            if (detail.type != MediaType.TV) null
                            else detailEpisodes.filter { it.season > 0 && !it.watched && daysUntil(it.airDate)?.let { days -> days >= 0 } == true }
                                .minWithOrNull(compareBy(EpisodeCard::season, EpisodeCard::number))
                        }
                        val releaseRaw = if (detail.type == MediaType.MOVIE) detail.releaseDate?.takeIf { daysUntil(it)?.let { days -> days >= 0 } == true }
                        else upcomingEpisode?.airDate
                        if (releaseRaw != null) {
                            ReleaseCountdown(detail, releaseRaw, upcomingEpisode, viewModel, progressViewModel)
                        }
                        if (started) SeriesProgressCard(
                            detail,
                            detailEpisodes,
                            nextUp,
                            // Continue stays the main action; finishing the series lives here.
                            onMarkAll = unwatchedAiredEpisodes(detailEpisodes).takeIf { it.isNotEmpty() }?.let { remaining -> { pendingShowEpisodes = remaining } },
                        )
                        Row(
                            Modifier.padding(horizontal = DetailLayout.Gutter).fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TrailerActionButton { trailerSheet = true }
                            val completed = detail.status == LibraryStatus.COMPLETED || detail.watched
                            // A started series continues where you left off; finishing it lives in the bookmark menu.
                            if (nextUp != null && !completed) PrimaryAction(
                                text = stringResource(R.string.continue_episode, nextUp.season, nextUp.number),
                                icon = Icons.Filled.SkipNext,
                                modifier = Modifier.weight(1f),
                                containerColor = Accent,
                                compact = true,
                                liveGlass = true,
                                hazeState = detailGlassState,
                            ) { onEpisode(nextUp) } else PrimaryAction(
                                text = stringResource(
                                    if (!completed) R.string.mark_watched
                                    else if (detail.type == MediaType.MOVIE) R.string.watched_movie
                                    else R.string.watched_tv,
                                ),
                                icon = Icons.Filled.Check,
                                modifier = Modifier.weight(1f),
                                containerColor = if (completed) Success else SurfacePalette.NeutralControl,
                                compact = true,
                                liveGlass = true,
                                hazeState = detailGlassState,
                            ) {
                                // Marking a series completed makes Simkl mark every episode, so
                                // it asks first and then marks only the aired episodes, one by one.
                                val unwatchedAired = if (detail.type == MediaType.TV && !completed) unwatchedAiredEpisodes(detailEpisodes) else emptyList()
                                if (unwatchedAired.isNotEmpty()) {
                                    pendingShowEpisodes = unwatchedAired
                                } else if (detail.type == MediaType.TV && !completed && detailEpisodes.isEmpty()) {
                                    // Episodes still loading: never fall back to a blanket "completed".
                                } else if (detail.type == MediaType.TV && completed) {
                                    // Undoing a watched series removes it from every list and unmarks its episodes.
                                    confirmUnmarkShow = true
                                } else {
                                    val target = if (completed) {
                                        if (detail.type == MediaType.TV) LibraryStatus.WATCHING else LibraryStatus.PLAN_TO_WATCH
                                    } else LibraryStatus.COMPLETED
                                    detail = detail.copy(status = target, watched = target == LibraryStatus.COMPLETED)
                                    onStatus(detail, target)
                                }
                            }
                            Box(
                                Modifier.size(48.dp).liquidGlassPill(under = com.cinetrack.ui.components.GlassMaterial.DetailSurface)
                                .clickable(onClick = openLibrarySheet),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    if (detail.status == LibraryStatus.NONE) Icons.Outlined.BookmarkBorder else libraryStatusIcon(detail.status),
                                    stringResource(R.string.choose_library_status),
                                    tint = TextSecondary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        if (detail.type == MediaType.MOVIE) {
                            remember(history, detail.stableKey) { watchSummary(history, detail.stableKey) }?.let { WatchSummaryLine(it) }
                        }
                    }
                }
            }
            if (detail.overview.isNotBlank() || !detailLoaded) {
                item(key = "detail_overview") {
                    DetailSectionSurface {
                        LoadedSection(detailLoaded, { OverviewSkeleton() }) {
                            PlotSection(detail.overview)
                        }
                    }
                }
            }
            item(key = "detail_providers") {
                DetailSectionSurface {
                    WatchSection(detail)
                }
            }
            if (detail.type == MediaType.TV && detailEpisodes.isNotEmpty()) {
                item(key = "detail_episodes") {
                    DetailSectionSurface {
                        if (detail.type == MediaType.TV && detailEpisodes.isNotEmpty()) {
                            val seasonPicker by remember(viewModel) {
                                viewModel.state.map { it.seasonPicker }.distinctUntilChanged()
                            }.collectAsStateWithLifecycle(initialValue = "text")
                            EpisodesSection(
                                detail,
                                detailEpisodes,
                                history,
                                onEpisode,
                                posterPicker = seasonPicker == "posters",
                                onPosterPicker = { viewModel.setSeasonPicker(if (it) "posters" else "text") },
                                onSeasonInfo = { selectedSeason = it },
                                initialSeason = initialSeason,
                                initialEpisode = initialEpisode,
                                onEpisodeWatched = { episode, watched ->
                                    val previousUnwatched = if (watched) detailEpisodes.filter {
                                        !it.watched &&
                                        (it.season < episode.season || (it.season == episode.season && it.number < episode.number))
                                    } else emptyList()
                                    if (previousUnwatched.isNotEmpty()) {
                                        pendingPreviousEpisodes = episode to previousUnwatched
                                    } else {
                                        detailEpisodes = detailEpisodes.map {
                                            if (it.season == episode.season && it.number == episode.number) it.copy(watched = watched) else it
                                        }
                                        viewModel.setEpisodeWatched(episode, watched)
                                    }
                                },
                                onSeasonWatched = { seasonEpisodes, watched ->
                                    val numbers = seasonEpisodes.map { it.season to it.number }.toSet()
                                    detailEpisodes = detailEpisodes.map { if ((it.season to it.number) in numbers) it.copy(watched = watched) else it }
                                    viewModel.setSeasonWatched(seasonEpisodes, watched)
                                },
                            )
                        }
                    }
                }
            }
            if (detailPeople.isNotEmpty() || !castLoaded) {
                item(key = "detail_cast") {
                    DetailSectionSurface {
                        LoadedSection(castLoaded, { CastSkeleton() }) {
                            if (detailPeople.isNotEmpty()) {
                                CastSection(detailPeople, showCharacters, { showCharacters = it }, onViewAll = { showFullCast = true }) { selectedPerson = it }
                            }
                        }
                    }
                }
            }
            if (collectionItems.isNotEmpty()) {
                item(key = "detail_collection") {
                    DetailSectionSurface {
                        if (collectionItems.isNotEmpty()) {
                            CollectionSection(detail, collectionItems, onMedia)
                        }
                    }
                }
            }
            if (moreLikeThis.isNotEmpty()) {
                item(key = "detail_recommendations") {
                    DetailSectionSurface {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (moreLikeThis.isNotEmpty()) {
                                SectionHeader(stringResource(R.string.more_like_this), Modifier.padding(horizontal = DetailLayout.Gutter))
                                MediaRail(moreLikeThis.filterNot { it.stableKey == detail.stableKey }, onMedia, showYear = false)
                            }
                        }
                    }
                }
            }
            item(key = "detail_information") {
                DetailSectionSurface {
                    UsefulInfoSection(detail)
                }
            }
            item(key = "detail_bottom") {
                Box(Modifier.fillMaxWidth().background(GlassMaterial.DetailSurface)
                    .navigationBarsPadding().height(72.dp))
            }
        }
        DetailTopBar(
            title = detail.title,
            subtitle = listOf(detail.year, detail.genres.firstOrNull().orEmpty()).filter(String::isNotBlank).joinToString(" · "),
            progress = rememberHeaderProgress(detailListState, revealStart = 470.dp),
            haze = headerHaze,
            onBack = onBack,
            refreshing = refreshing,
            onRefresh = {
                refreshScope.launch {
                    refreshing = true
                    // Fetches this title (and its seasons) again, then reloads the page from it.
                    runCatching {
                        detailViewModel.refreshTitle(detail)
                        if (detail.type == MediaType.TV) viewModel.onTitleRefreshed()
                    }
                    refreshNonce++
                    refreshing = false
                }
            },
        )
        }
    }
    if (librarySheet) {
        LibraryStatusSheet(detail, onDismiss = { librarySheet = false }) { status ->
            detail = detail.copy(status = status, watched = status == LibraryStatus.COMPLETED)
            onStatus(detail, status)
            librarySheet = false
            if (detail.type == MediaType.TV && status == LibraryStatus.COMPLETED) {
                // Like Simkl: a completed show has every aired episode watched,
                // each as its own play now, sent to every tracker.
                val remaining = unwatchedAiredEpisodes(detailEpisodes)
                if (remaining.isNotEmpty()) {
                    val numbers = remaining.map { it.season to it.number }.toSet()
                    detailEpisodes = detailEpisodes.map { if ((it.season to it.number) in numbers) it.copy(watched = true) else it }
                    viewModel.setEpisodesWatched(remaining, true)
                }
            }
            if (detail.type == MediaType.TV && status == LibraryStatus.NONE) {
                // Removing the show removes its episode history too.
                detailEpisodes = detailEpisodes.map { it.copy(watched = false) }
                detailViewModel.forgetWatchedEpisodes(detail.id)
            }
        }
    }
    if (trailerSheet) {
        LaunchedEffect(detail.stableKey, trailerAttempt) {
            trailerLoading = true
            trailerKey = detailViewModel.loadTrailerKey(detail)
            trailerLoading = false
        }
        TrailerPlayerSheet(
            title = detail.title,
            trailerKey = trailerKey,
            loading = trailerLoading,
            onDismiss = { trailerSheet = false },
            onRetry = { trailerAttempt += 1 },
        )
    }
    selectedSeason?.let { number ->
        SeasonInfoSheet(detail, number, detailViewModel, onDismiss = { selectedSeason = null }) { person ->
            returnToSeason = number
            selectedSeason = null
            selectedPerson = person
        }
    }
    selectedPerson?.let { person ->
        ActorSheet(
            person,
            detailViewModel,
            onDismiss = {
                selectedPerson = null
                selectedSeason = returnToSeason
                returnToSeason = null
            },
            onMedia = { selected ->
                returnToSeason = null
                onMedia(selected)
            },
        )
    }
    if (showFullCast) {
        FullCastSheet(detailPeople, showCharacters, onDismiss = { showFullCast = false }) { person ->
            showFullCast = false
            selectedPerson = person
        }
    }
    if (confirmUnmarkShow) {
        val watchedCount = detailEpisodes.count { it.season > 0 && it.watched }
        UnmarkShowPrompt(
            title = detail.title,
            watchedCount = watchedCount,
            onDismiss = { confirmUnmarkShow = false },
            onConfirm = {
                // Removing from every list clears the series' episode history here and on Simkl.
                detailEpisodes = detailEpisodes.map { it.copy(watched = false) }
                // Without this the cached episodes put the watched marks back on the next refresh.
                detailViewModel.forgetWatchedEpisodes(detail.id)
                detail = detail.copy(status = LibraryStatus.NONE, watched = false)
                onStatus(detail, LibraryStatus.NONE)
                confirmUnmarkShow = false
            },
        )
    }
    pendingShowEpisodes?.let { episodes ->
        MarkShowWatchedPrompt(
            title = detail.title,
            episodeCount = episodes.size,
            onDismiss = { pendingShowEpisodes = null },
            onConfirm = {
                val numbers = episodes.map { it.season to it.number }.toSet()
                detailEpisodes = detailEpisodes.map { if ((it.season to it.number) in numbers) it.copy(watched = true) else it }
                viewModel.setEpisodesWatched(episodes, true)
                // Completed only once nothing is left to air; otherwise the series keeps going.
                if (detailEpisodes.none { it.season > 0 && !it.watched }) {
                    detail = detail.copy(status = LibraryStatus.COMPLETED, watched = true)
                    onStatus(detail, LibraryStatus.COMPLETED)
                }
                pendingShowEpisodes = null
            },
        )
    }
    pendingPreviousEpisodes?.let { (episode, previous) ->
        PreviousEpisodesPrompt(
            previousCount = previous.size,
            onDismiss = { pendingPreviousEpisodes = null },
            onOnlyThis = {
                detailEpisodes = detailEpisodes.map {
                    if (it.season == episode.season && it.number == episode.number) it.copy(watched = true) else it
                }
                viewModel.setEpisodeWatched(episode, true)
                pendingPreviousEpisodes = null
            },
            onIncludePrevious = {
                val affected = (previous + episode).distinctBy { it.season to it.number }
                val numbers = affected.map { it.season to it.number }.toSet()
                detailEpisodes = detailEpisodes.map { if ((it.season to it.number) in numbers) it.copy(watched = true) else it }
                viewModel.setEpisodesWatched(affected, true)
                pendingPreviousEpisodes = null
            },
        )
    }
    }
}

private fun mergeWatchedEpisodes(
    loaded: List<EpisodeCard>,
    localEpisodes: List<EpisodeCard>,
    history: List<TimelineCard>,
): List<EpisodeCard> {
    val watchedIds = localEpisodes.asSequence().filter(EpisodeCard::watched).map(EpisodeCard::id).toSet()
    val watchedNumbers = buildSet {
        localEpisodes.asSequence().filter(EpisodeCard::watched).forEach { add(it.season to it.number) }
        history.asSequence().filter { it.media.type == MediaType.TV }.forEach eventLoop@{ event ->
            val season = event.season ?: return@eventLoop
            val number = event.episodeNumber ?: return@eventLoop
            add(season to number)
        }
    }
    return (loaded + localEpisodes).distinctBy { it.season to it.number }.map { episode ->
        if (episode.watched || episode.id in watchedIds || (episode.season to episode.number) in watchedNumbers) {
            episode.copy(watched = true)
        } else episode
    }
}

/** Sections are lazy, while the backdrop blur is rendered once at viewport size. */
@Composable
private fun androidx.compose.foundation.lazy.LazyItemScope.DetailSectionSurface(
    first: Boolean = false,
    bottomGap: androidx.compose.ui.unit.Dp = DetailLayout.SectionGap,
    content: @Composable () -> Unit,
) {
    // Sections that arrive with the loaded details fade in, and the ones below glide
    // to their new place instead of jumping; growing sections resize smoothly.
    Box(Modifier.fillMaxWidth()
        .animateItem(
            fadeInSpec = androidx.compose.animation.core.tween(Motion.Long),
            placementSpec = androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow, visibilityThreshold = androidx.compose.ui.unit.IntOffset.VisibilityThreshold),
            fadeOutSpec = null,
        )
        .animateContentSize(androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow))
        .then(if (first) Modifier.shelfFeather() else Modifier.background(GlassMaterial.DetailSurface))
        .padding(top = if (first) 32.dp else 0.dp, bottom = bottomGap)) { content() }
}

/**
 * The shelf starts as a soft fade over the blurred art. A rounded, hard-edged top
 * showed the bright backdrop against the dark shelf where the corners curved.
 */
internal fun Modifier.shelfFeather(fade: androidx.compose.ui.unit.Dp = 112.dp): Modifier = drawBehind {
    drawRect(
        Brush.verticalGradient(
            0f to Color.Transparent,
            .45f to GlassMaterial.DetailSurface.copy(alpha = GlassMaterial.DetailSurface.alpha * .55f),
            1f to GlassMaterial.DetailSurface,
            startY = 0f,
            endY = fade.toPx().coerceAtMost(size.height),
        ),
    )
}

private val DetailHeaderGlass = dev.chrisbanes.haze.HazeStyle(
    backgroundColor = Color(0xFF080A0C),
    tint = dev.chrisbanes.haze.HazeTint(Color(0xFF080A0C).copy(alpha = .66f)),
    blurRadius = 22.dp,
    noiseFactor = .03f,
    fallbackTint = dev.chrisbanes.haze.HazeTint(Color(0xF7080A0C)),
)

/** Fades the blurred hero art out over its lower part, so it never ends on a straight edge. */
internal fun Modifier.fadeOutBottom(alpha: Float): Modifier =
    graphicsLayer { this.alpha = alpha; compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            drawRect(
                Brush.verticalGradient(0f to Color.White, .55f to Color.White, 1f to Color.Transparent),
                blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
            )
        }

/** 0 while the hero is visible, rising to 1 as the content passes under the top bar. */
@Composable
internal fun rememberHeaderProgress(
    state: androidx.compose.foundation.lazy.LazyListState?,
    revealStart: androidx.compose.ui.unit.Dp,
    revealLength: androidx.compose.ui.unit.Dp = 72.dp,
): () -> Float {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val start = with(density) { revealStart.toPx() }
    val length = with(density) { revealLength.toPx() }
    return remember(state, start, length) {
        {
            if (state == null) 0f
            else if (state.firstVisibleItemIndex > 0) 1f
            else ((state.firstVisibleItemScrollOffset - start) / length).coerceIn(0f, 1f)
        }
    }
}

/**
 * Pinned back button; once the hero scrolls away a dark bar fades in behind it and the
 * title slides up into it, so the page always says what it is.
 */
@Composable
internal fun DetailTopBar(
    title: String,
    subtitle: String?,
    progress: () -> Float,
    haze: dev.chrisbanes.haze.HazeState,
    onBack: () -> Unit,
    refreshing: Boolean = false,
    onRefresh: (() -> Unit)? = null,
) {
    Box(Modifier.fillMaxWidth()) {
        Box(
            Modifier.matchParentSize()
                // Frosted: the page scrolling underneath is blurred into the bar.
                .hazeEffect(haze, style = DetailHeaderGlass) { alpha = progress() }
                .drawWithContent {
                    drawContent()
                    drawLine(
                        Color.White.copy(alpha = .09f * progress()),
                        androidx.compose.ui.geometry.Offset(0f, size.height),
                        androidx.compose.ui.geometry.Offset(size.width, size.height),
                        1f,
                    )
                },
        )
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().height(56.dp).padding(start = Spacing.lg, end = if (onRefresh != null) Spacing.lg else 64.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassBackButton(onClick = onBack)
            Column(
                Modifier.weight(1f).padding(start = 6.dp).graphicsLayer {
                    val p = progress()
                    alpha = p
                    translationY = (1f - p) * 10.dp.toPx()
                },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(title, color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!subtitle.isNullOrBlank()) {
                    Text(subtitle, color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (onRefresh != null) {
                var menuOpen by remember { mutableStateOf(false) }
                Box {
                    com.cinetrack.ui.components.GlassIconButton(Icons.Filled.MoreVert, stringResource(R.string.more_options), { if (!refreshing) menuOpen = true }, busy = refreshing)
                    androidx.compose.material3.DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                        shape = RoundedCornerShape(Radius.Medium),
                        containerColor = SurfacePalette.MenuSurface,
                    ) {
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(stringResource(R.string.refresh_info), color = TextPrimary, fontWeight = FontWeight.Bold) },
                            leadingIcon = { Icon(Icons.Filled.Refresh, null, tint = TextSecondary) },
                            onClick = { menuOpen = false; onRefresh() },
                        )
                    }
                }
            }
        }
    }
}

internal object DetailLayout {
    val Gutter = 20.dp
    val Inner = 12.dp
    val SectionGap = 24.dp
    val RatingsGap = 12.dp
    val CastWidth = 96.dp
    val CastPhoto = 84.dp
}
