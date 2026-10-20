@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.cinetrack.ui.screens

import androidx.compose.animation.core.animateFloat
import com.cinetrack.ui.components.sharedBackdrop
import com.cinetrack.ui.components.sharedPoster
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.animateContentSize
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.horizontalScroll
import com.cinetrack.ui.components.liquidGlassPill
import com.cinetrack.ui.components.softTab
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.RemoveDone
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import com.cinetrack.domain.WatchSummary
import com.cinetrack.domain.episodeWatchCounts
import com.cinetrack.domain.watchSummary
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.ui.text.withStyle
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.flow.map
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.cinetrack.R
import com.cinetrack.domain.EpisodeCard
import com.cinetrack.domain.LibraryStatus
import com.cinetrack.domain.latestEpisodeWatchTimes
import com.cinetrack.domain.MediaCard
import com.cinetrack.domain.MediaType
import com.cinetrack.domain.PersonCard
import com.cinetrack.domain.RatingScore
import com.cinetrack.domain.TimelineCard
import com.cinetrack.ui.CineTrackViewModel
import com.cinetrack.ui.components.AdaptiveBackground
import com.cinetrack.ui.components.GlassBackButton
import com.cinetrack.ui.components.GlassDivider
import com.cinetrack.ui.components.LoadingPane
import com.cinetrack.ui.components.LibraryStatusSheet
import com.cinetrack.ui.components.MediaPoster
import com.cinetrack.ui.components.MediaRail
import com.cinetrack.ui.components.PrimaryAction
import com.cinetrack.ui.components.SectionHeader
import com.cinetrack.ui.components.SharedGlassSheet
import com.cinetrack.ui.components.GlassMaterial
import com.cinetrack.ui.components.rememberDetailGlassState
import com.cinetrack.ui.components.glass
import com.cinetrack.ui.components.blueEdgeClickable
import com.cinetrack.ui.components.libraryStatusColor
import com.cinetrack.ui.components.libraryStatusIcon
import com.cinetrack.ui.components.rememberUiAction
import com.cinetrack.ui.theme.Gold
import com.cinetrack.ui.theme.Info
import com.cinetrack.ui.theme.Success
import com.cinetrack.ui.theme.TextMuted
import com.cinetrack.ui.theme.TextPrimary
import com.cinetrack.ui.theme.TextSecondary
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope

/** On detail pages the accent follows the poster (see [com.cinetrack.ui.theme.LocalDetailAccent]). */
private val Accent: Color
    @Composable get() = com.cinetrack.ui.theme.LocalDetailAccent.current ?: com.cinetrack.ui.theme.Accent
private val AccentLight: Color
    @Composable get() = com.cinetrack.ui.theme.LocalDetailAccent.current?.let { androidx.compose.ui.graphics.lerp(it, Color.White, .32f) }
        ?: com.cinetrack.ui.theme.AccentLight

@Composable
private fun rememberPosterAccentEnabled(viewModel: CineTrackViewModel): Boolean {
    val enabled by remember(viewModel) { viewModel.state.map { it.posterAccent }.distinctUntilChanged() }
        .collectAsState(initial = true)
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
        AdaptiveBackground { LoadingPane(); GlassBackButton(onClick = onBack, modifier = Modifier.statusBarsPadding().padding(com.cinetrack.ui.theme.Spacing.md)) }
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
    androidx.compose.runtime.CompositionLocalProvider(com.cinetrack.ui.theme.LocalDetailAccent provides posterAccent) {
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
                                containerColor = if (completed) Success else com.cinetrack.ui.theme.SurfacePalette.NeutralControl,
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
                            }.collectAsState(initial = "text")
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

@Composable
private fun TrailerActionButton(onClick: () -> Unit) {
    val clickAction = rememberUiAction(onClick)
    Box(
        Modifier.size(48.dp)
            .liquidGlassPill(under = com.cinetrack.ui.components.GlassMaterial.DetailSurface)
            .clickable(onClick = clickAction),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.PlayArrow, stringResource(R.string.trailer), tint = TextSecondary, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun TrailerPlayerSheet(
    title: String,
    trailerKey: String?,
    loading: Boolean,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
) {
    SharedGlassSheet(onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = com.cinetrack.ui.theme.Spacing.lg)) {
            Text(
                stringResource(R.string.watch_trailer),
                color = TextPrimary,
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
            )
            Text(title, color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Large))
                    .background(com.cinetrack.ui.theme.SurfacePalette.BottomFade),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    loading -> com.cinetrack.ui.components.SkeletonBox(Modifier.fillMaxSize(), description = stringResource(R.string.loading))
                    trailerKey.isNullOrBlank() -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.trailer_unavailable), color = TextSecondary, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))
                        PrimaryAction(stringResource(R.string.retry), Icons.Filled.PlayArrow, Modifier.width(150.dp), onClick = onRetry)
                    }
                    else -> YouTubeTrailerPlayer(trailerKey)
                }
            }
        }
    }
}

@Composable
private fun YouTubeTrailerPlayer(videoKey: String) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var playerView by remember(videoKey) { mutableStateOf<YouTubePlayerView?>(null) }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = {
            YouTubePlayerView(context).apply {
                lifecycleOwner.lifecycle.addObserver(this)
                addYouTubePlayerListener(object : AbstractYouTubePlayerListener() {
                    override fun onReady(youTubePlayer: YouTubePlayer) {
                        youTubePlayer.loadVideo(videoKey, 0f)
                    }
                })
                playerView = this
            }
        },
        update = { },
    )
    DisposableEffect(videoKey, lifecycleOwner) {
        onDispose {
            playerView?.let { view ->
                lifecycleOwner.lifecycle.removeObserver(view)
                view.release()
            }
            playerView = null
        }
    }
}

@Composable
private fun DetailHero(media: MediaCard) {
    // A tapped wide cover grows into this header.
    // Sized like Trakt's: the poster spans about two thirds of the width and
    // starts just below the back button row.
    Box(Modifier.fillMaxWidth().sharedBackdrop(media.stableKey, slot = null)) {
        // The backdrop art, blurred, fades into the page behind the poster.
        media.backdropUrl?.takeIf(String::isNotBlank)?.let { backdrop ->
            // matchParentSize: the hero now takes its height from the poster.
            AsyncImage(backdrop, null, Modifier.matchParentSize().blur(18.dp).fadeOutBottom(.72f), contentScale = ContentScale.Crop)
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(0f to Color(0x33080A0C), .55f to Color(0x4D080A0C), 1f to Color.Transparent)))
        }
        Box(
            Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 64.dp, bottom = 24.dp).fillMaxWidth(.68f).aspectRatio(com.cinetrack.ui.theme.PosterAspectRatio)
                // The tapped poster flies into this slot.
                .sharedPoster(media.stableKey, slot = null)
                .shadow(18.dp, RoundedCornerShape(com.cinetrack.ui.theme.Radius.Large), clip = false)
                .clip(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Large)).background(Brush.linearGradient(listOf(Accent, com.cinetrack.ui.theme.SurfacePalette.BlueSurface)))
        ) {
                if (!media.posterUrl.isNullOrBlank()) com.cinetrack.ui.components.SharedPosterTargetImage(media.posterUrl, media.title, media.stableKey, Modifier.fillMaxSize(), ContentScale.FillBounds)
        }
    }
}

@Composable
private fun DetailIdentity(media: MediaCard, loaded: Boolean = true) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = DetailLayout.Gutter),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            media.title,
            color = TextPrimary,
            style = androidx.compose.material3.MaterialTheme.typography.displaySmall,
            lineHeight = 33.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        // Year, runtime and the first genres on one line; details that sharpen once loaded crossfade in place.
        val facts = listOfNotNull(
            media.year.takeIf(String::isNotBlank),
            formatDurationMinutes(media.runtimeMinutes).takeIf { media.type == MediaType.MOVIE && it.isNotBlank() },
            media.genres.take(2).joinToString(", ").takeIf(String::isNotBlank),
        ).joinToString(" · ")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.animation.Crossfade(facts, label = "detailFacts", modifier = Modifier.weight(1f, fill = false)) { text ->
                Text(text, color = TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, maxLines = 2)
            }
            if (media.type == MediaType.TV) media.tmdbStatus?.takeIf(String::isNotBlank)?.let { SeriesStatusChip(it) }
        }
        if (!loaded && media.genres.isEmpty()) {
            Spacer(Modifier.height(6.dp))
            SkeletonPill(Modifier.width(160.dp).height(12.dp))
        }
    }
}

/** "In corso" / "Conclusa": tinted while a series is still running, neutral once it has ended. */
@Composable
private fun SeriesStatusChip(status: String) {
    val running = status.lowercase() in setOf("returning series", "in production", "post production", "planned", "pilot")
    val tint = if (running) Success else TextMuted
    Text(
        tmdbStatusLabel(status),
        color = if (running) androidx.compose.ui.graphics.lerp(Success, Color.White, .35f) else TextSecondary,
        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Pill)).background(tint.copy(alpha = .18f))
            .padding(horizontal = 9.dp, vertical = 3.dp),
    )
}

@Composable
private fun PlotSection(text: String) {
    var expanded by rememberSaveable(text) { mutableStateOf(false) }
    var overflows by remember(text) { mutableStateOf(false) }
    Column(
        Modifier.padding(horizontal = DetailLayout.Gutter).fillMaxWidth().animateContentSize()
            .clickable(enabled = overflows || expanded, interactionSource = remember { MutableInteractionSource() }, indication = null) { expanded = !expanded },
    ) {
        SectionHeader(stringResource(R.string.overview))
        Spacer(Modifier.height(8.dp))
        Text(
            text, color = TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, lineHeight = 22.sp,
            maxLines = if (expanded) Int.MAX_VALUE else 4, overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow },
        )
        if (overflows || expanded) {
            Text(
                stringResource(if (expanded) R.string.plot_less else R.string.plot_more), color = AccentLight,
                style = androidx.compose.material3.MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun RatingsSection(ratings: List<RatingScore>, tmdbScore: Double?, pending: Boolean) {
    // TMDB comes from the card straight away, in the card's own format; the other sources
    // hold their cell as a skeleton and fill in as they load.
    val others = ratings.filter { it.score.any(Char::isDigit) && (tmdbScore == null || !it.source.equals("TMDB", ignoreCase = true)) }
    val shown = (listOfNotNull(tmdbScore?.let { RatingScore("TMDB", String.format(com.cinetrack.ui.currentLocale(), "%.1f", it)) }) + others).take(4)
    if (shown.isEmpty() && !pending) return
    val cells = if (pending) 4 else shown.size
    Row(
        Modifier.padding(horizontal = DetailLayout.Gutter).fillMaxWidth().height(58.dp)
            .liquidGlassPill(under = com.cinetrack.ui.components.GlassMaterial.DetailSurface),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(cells) { index ->
            if (index > 0) Box(Modifier.width(.6.dp).fillMaxHeight(.46f).background(Color.White.copy(alpha = .12f)))
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                val rating = shown.getOrNull(index)
                if (rating != null) {
                    androidx.compose.runtime.key(rating.source) { RatingCell(rating) }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        SkeletonPill(Modifier.width(30.dp).height(14.dp))
                        Spacer(Modifier.height(6.dp))
                        SkeletonPill(Modifier.width(44.dp).height(9.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun RatingCell(rating: RatingScore) {
    // Fades in where its placeholder was.
    val appear = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, androidx.compose.animation.core.tween(com.cinetrack.ui.theme.Motion.Medium)) }
    Column(Modifier.graphicsLayer { alpha = appear.value }, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(rating.score, color = TextPrimary, fontWeight = FontWeight.ExtraBold, style = androidx.compose.material3.MaterialTheme.typography.titleSmall, maxLines = 1)
        Text(ratingSourceLabel(rating.source), color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun ratingSourceLabel(source: String): String {
    val key = source.lowercase().replace(".", "").replace(" ", "")
    return when {
        "rotten" in key || key.startsWith("rtom") -> "Rotten T."
        "imdb" in key -> "IMDb"
        "tmdb" in key -> "TMDB"
        "metacritic" in key -> "Metacritic"
        "letterboxd" in key -> "Letterboxd"
        "trakt" in key -> "Trakt"
        "mal" == key || "myanimelist" in key -> "MAL"
        else -> source.lowercase().replaceFirstChar { it.titlecase() }
    }
}

/** Fades from a skeleton to the real section once its data is known. */
@Composable
private fun LoadedSection(loaded: Boolean, skeleton: @Composable () -> Unit, content: @Composable () -> Unit) {
    androidx.compose.animation.Crossfade(loaded, animationSpec = androidx.compose.animation.core.tween(220), label = "loadedSection") { ready ->
        if (ready) Column { content() } else skeleton()
    }
}

@Composable
private fun OverviewSkeleton() {
    Column(Modifier.padding(horizontal = DetailLayout.Gutter).fillMaxWidth()) {
        // Same heading as the loaded plot, so nothing moves when the text arrives.
        SectionHeader(stringResource(R.string.overview))
        Spacer(Modifier.height(10.dp))
        listOf(1f, .96f, .92f, .58f).forEach { width ->
            SkeletonPill(Modifier.fillMaxWidth(width).height(12.dp))
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun CastSkeleton() {
    Column {
        // Same header as the loaded section, so the title doesn't move when the cast arrives.
        SectionHeader(stringResource(R.string.cast_title), Modifier.padding(horizontal = DetailLayout.Gutter), stringResource(R.string.see_all)) {}
        Spacer(Modifier.height(12.dp))
        Row(Modifier.padding(horizontal = DetailLayout.Gutter - 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(4) {
                Column(Modifier.width(DetailLayout.CastWidth).padding(horizontal = 4.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    SkeletonPill(Modifier.size(DetailLayout.CastPhoto), CircleShape)
                    Spacer(Modifier.height(10.dp))
                    SkeletonPill(Modifier.fillMaxWidth(.8f).height(11.dp))
                    Spacer(Modifier.height(6.dp))
                    SkeletonPill(Modifier.fillMaxWidth(.55f).height(9.dp))
                }
            }
        }
    }
}

/** A soft pulsing placeholder in a pill shape, for values that are still loading. */
@Composable
private fun SkeletonPill(modifier: Modifier, shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(com.cinetrack.ui.theme.Radius.Pill)) {
    val pulse = androidx.compose.animation.core.rememberInfiniteTransition(label = "skeleton")
    val alpha by pulse.animateFloat(
        initialValue = .05f,
        targetValue = .11f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween<Float>(800), androidx.compose.animation.core.RepeatMode.Reverse),
        label = "skeletonAlpha",
    )
    Box(modifier.clip(shape).drawBehind { drawRect(Color.White.copy(alpha = alpha)) })

}

/**
 * The best way to watch (subscription first) as the page's one card, with Apri.
 * Every other service folds into a quiet line that opens the full list.
 */
@Composable
private fun WatchSection(media: MediaCard) {
    val uriHandler = LocalUriHandler.current
    val offers = com.cinetrack.domain.visibleProviderOffers(media)
    val order = listOf(
        "flatrate" to R.string.offer_flatrate, "free" to R.string.offer_free, "ads" to R.string.offer_ads,
        "rent" to R.string.offer_rent, "buy" to R.string.offer_buy,
    )
    val best = order.firstNotNullOfOrNull { (type, label) -> offers[type].orEmpty().firstOrNull()?.let { it to label } }
    val link = media.providerLink?.takeIf(String::isNotBlank)
    var expanded by remember(media.stableKey) { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = DetailLayout.Gutter).fillMaxWidth()) {
        SectionHeader(stringResource(if (media.type == MediaType.MOVIE) R.string.where_to_watch_movie else R.string.where_to_watch_tv))
        Spacer(Modifier.height(10.dp))
        if (best == null) {
            Text(
                stringResource(if (media.providerAvailabilityExists) R.string.no_matching_watch_providers else R.string.no_watch_providers),
                color = TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            )
            return@Column
        }
        val (provider, label) = best
        val others = order.flatMap { offers[it.first].orEmpty() }.distinct().filterNot { it == provider }
        Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(18.dp))) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                ProviderLogo(provider, media.providerLogos[provider], 44.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.watch_on, provider), color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(stringResource(label), color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
                }
                if (link != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.open_action),
                        color = if (Accent.luminance() > .6f) Color.Black else Color.White,
                        style = androidx.compose.material3.MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Pill)).background(Accent)
                            .clickable(role = androidx.compose.ui.semantics.Role.Button) { runCatching { uriHandler.openUri(link) } }
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                    )
                }
            }
            if (others.isNotEmpty()) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(.5.dp).background(Color.White.copy(alpha = .08f)))
                val turn by androidx.compose.animation.core.animateFloatAsState(if (expanded) 180f else 0f, label = "providersChevron")
                Row(
                    Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(horizontal = 12.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val names = others.take(3).joinToString(", ") + if (others.size > 3) " +${others.size - 3}" else ""
                    Text(stringResource(R.string.also_on, names), color = TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Icon(Icons.Filled.KeyboardArrowDown, null, tint = TextMuted, modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = turn })
                }
                AnimatedVisibility(expanded) {
                    Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                        listOf("flatrate" to R.string.subscription, "rent" to R.string.rent, "buy" to R.string.buy,
                            "free" to R.string.provider_free, "ads" to R.string.provider_ads).forEach { (type, category) ->
                            ProviderCategory(stringResource(category), offers[type].orEmpty(), media.providerLogos)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderLogo(provider: String, logo: String?, size: androidx.compose.ui.unit.Dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(size * .24f)).background(Accent.copy(alpha = .26f)), contentAlignment = Alignment.Center) {
        if (!logo.isNullOrBlank()) AsyncImage(logo, provider, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Text(provider.take(1), color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun ProviderCategory(label: String, providers: List<String>, logos: Map<String, String>) {
    if (providers.isEmpty()) return
    Text(label, color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = com.cinetrack.ui.theme.Spacing.md, bottom = com.cinetrack.ui.theme.Spacing.sm))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        items(providers, key = { it }) { provider ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProviderLogo(provider, logos[provider], 26.dp)
                    Spacer(Modifier.width(7.dp))
                    Text(provider, color = TextPrimary, fontWeight = FontWeight.SemiBold, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                }
        }
    }
}

@Composable
private fun EpisodesSection(
    media: MediaCard,
    episodes: List<EpisodeCard>,
    history: List<TimelineCard>,
    onEpisode: (EpisodeCard) -> Unit,
    initialSeason: Int?,
    initialEpisode: Int?,
    onEpisodeWatched: (EpisodeCard, Boolean) -> Unit,
    onSeasonWatched: (List<EpisodeCard>, Boolean) -> Unit,
    onSeasonInfo: (Int) -> Unit,
    posterPicker: Boolean,
    onPosterPicker: (Boolean) -> Unit,
) {
    val grouped = episodes.groupBy(EpisodeCard::season).toSortedMap()
    val watchedAtByEpisode = remember(history) { latestEpisodeWatchTimes(history) }
    val watchCounts = remember(history, media.id) { episodeWatchCounts(history, media.id) }
    val nextEpisode = remember(episodes) {
        val ordered = episodes.filter { it.season > 0 }.sortedWith(compareBy(EpisodeCard::season, EpisodeCard::number))
        // Only a show you've started has a next episode to point at.
        val lastWatched = ordered.lastOrNull(EpisodeCard::watched) ?: return@remember null
        ordered.firstOrNull { candidate ->
            !candidate.watched && (
                candidate.season > lastWatched.season ||
                    (candidate.season == lastWatched.season && candidate.number > lastWatched.number)
                )
        }
    }
    val defaultSeason = initialSeason?.takeIf { it in grouped }
        ?: nextEpisode?.season?.takeIf { it in grouped }
        ?: grouped.keys.firstOrNull { it > 0 } ?: grouped.keys.firstOrNull()
    var selectedSeason by rememberSaveable(media.stableKey) { mutableStateOf(defaultSeason) }
    LaunchedEffect(initialSeason, grouped.keys) {
        if (initialSeason != null && grouped.containsKey(initialSeason)) selectedSeason = initialSeason
    }
    val season = selectedSeason?.takeIf { it in grouped } ?: defaultSeason ?: return
    val seasonEpisodes = grouped[season].orEmpty()
    val summary = media.seasons.firstOrNull { it.number == season }
    val total = (summary?.episodeCount ?: seasonEpisodes.size).coerceAtLeast(seasonEpisodes.size).coerceAtLeast(1)
    val watched = seasonEpisodes.count(EpisodeCard::watched)
    val allWatched = seasonEpisodes.isNotEmpty() && watched == seasonEpisodes.size
    val seasonKeys = grouped.keys.toList()
    // Pills and covers each keep their own row state: one state shared by both
    // rows while they crossfade leaves the incoming row blank for a few frames.
    val pillState = rememberLazyListState()
    val coverState = rememberLazyListState()
    LaunchedEffect(season, posterPicker) {
        // Keep the chosen season in view, with its neighbour before it.
        val target = (seasonKeys.indexOf(season) - 1).coerceAtLeast(0)
        val (shown, hidden) = if (posterPicker) coverState to pillState else pillState to coverState
        hidden.scrollToItem(target)
        shown.animateScrollToItem(target)
    }
    val progress by androidx.compose.animation.core.animateFloatAsState(
        watched.toFloat() / total,
        androidx.compose.animation.core.tween(420, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "seasonProgress",
    )
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = DetailLayout.Gutter), verticalAlignment = Alignment.CenterVertically) {
            SectionHeader(stringResource(R.string.episodes), Modifier.weight(1f))
            Text(
                stringResource(if (posterPicker) R.string.season_style_text else R.string.season_style_posters),
                color = TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                modifier = Modifier.glass(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Pill))
                    .clickable(role = androidx.compose.ui.semantics.Role.Switch) { onPosterPicker(!posterPicker) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        // Switching style: the old row fades out, the height glides to the new
        // row's, and the new row fades and grows in, so the list below moves once.
        androidx.compose.animation.AnimatedContent(
            posterPicker,
            transitionSpec = {
                val enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(240, delayMillis = 90)) +
                    androidx.compose.animation.scaleIn(androidx.compose.animation.core.tween(280, delayMillis = 90, easing = androidx.compose.animation.core.FastOutSlowInEasing), initialScale = .94f, transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, .5f))
                val exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(90))
                androidx.compose.animation.ContentTransform(
                    enter, exit,
                    sizeTransform = androidx.compose.animation.SizeTransform(clip = false) { _, _ ->
                        androidx.compose.animation.core.tween(320, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                    },
                )
            },
            label = "seasonPickerStyle",
        ) { covers ->
            LazyRow(state = if (covers) coverState else pillState, contentPadding = PaddingValues(horizontal = DetailLayout.Gutter), horizontalArrangement = Arrangement.spacedBy(if (covers) 10.dp else 8.dp)) {
                items(seasonKeys, key = { it }) { number ->
                    val chosen = number == season
                    // The accent fades between choices instead of jumping.
                    val fill by androidx.compose.animation.core.animateFloatAsState(
                        if (chosen) 1f else 0f, androidx.compose.animation.core.tween(240), label = "seasonChoice",
                    )
                    val label = if (number == 0) stringResource(R.string.specials_short)
                    else stringResource(if (covers) R.string.season_number else R.string.season_short, number)
                    if (covers) {
                        val cover = media.seasons.firstOrNull { it.number == number }?.posterUrl ?: media.posterUrl
                        val shape = RoundedCornerShape(com.cinetrack.ui.theme.Radius.Small)
                        Column(
                            Modifier.width(92.dp).clip(shape)
                                .clickable(role = androidx.compose.ui.semantics.Role.Tab) { selectedSeason = number },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(shape)
                                    .background(com.cinetrack.ui.theme.SurfacePalette.ArtworkBackdrop)
                                    .border(2.dp, Accent.copy(alpha = fill), shape),
                            ) {
                                if (!cover.isNullOrBlank()) AsyncImage(cover, label, Modifier.fillMaxSize().padding(2.dp).clip(shape), contentScale = ContentScale.Crop)
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                label, color = androidx.compose.ui.graphics.lerp(TextMuted, TextPrimary, fill), maxLines = 1,
                                style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                                fontWeight = if (chosen) FontWeight.Bold else FontWeight.Medium,
                            )
                        }
                    } else {
                        val pill = RoundedCornerShape(com.cinetrack.ui.theme.Radius.Pill)
                        val accent = Accent
                        val ink = if (accent.luminance() > .6f) Color.Black else Color.White
                        Text(
                            label,
                            color = androidx.compose.ui.graphics.lerp(TextSecondary, ink, fill),
                            style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                            fontWeight = if (chosen) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.glass(pill).drawBehind { drawRect(accent.copy(alpha = fill)) }
                                .clickable(role = androidx.compose.ui.semantics.Role.Tab) { selectedSeason = number }
                                .padding(horizontal = 16.dp, vertical = 9.dp),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().padding(start = DetailLayout.Gutter, end = DetailLayout.Gutter - 8.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.animation.Crossfade(stringResource(R.string.season_progress, watched, total), label = "seasonCount") { label ->
                Text(label, color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(com.cinetrack.ui.theme.SurfacePalette.ProgressTrack)) {
                Box(Modifier.fillMaxWidth(progress).fillMaxHeight().clip(CircleShape).background(Accent))
            }
            Spacer(Modifier.width(6.dp))
            Box(Modifier.size(44.dp).clip(CircleShape).clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = rememberUiAction { onSeasonInfo(season) }), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Info, stringResource(R.string.show_season_info, season), tint = TextSecondary, modifier = Modifier.size(19.dp))
            }
            Box(Modifier.size(44.dp).clip(CircleShape).clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = rememberUiAction { onSeasonWatched(seasonEpisodes, !allWatched) }), contentAlignment = Alignment.Center) {
                WatchedCircle(allWatched, stringResource(if (allWatched) R.string.mark_unwatched else R.string.mark_watched))
            }
        }
        // The list slides in from the side of the season you picked; its rows follow one after another.
        androidx.compose.animation.AnimatedContent(
            season,
            transitionSpec = {
                val forward = targetState > initialState
                val enter = androidx.compose.animation.slideInHorizontally(
                    androidx.compose.animation.core.tween(320, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                ) { width -> if (forward) width / 4 else -width / 4 } +
                    androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(260, delayMillis = 60))
                val exit = androidx.compose.animation.slideOutHorizontally(
                    androidx.compose.animation.core.tween(220, easing = androidx.compose.animation.core.FastOutLinearInEasing),
                ) { width -> if (forward) -width / 6 else width / 6 } +
                    androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(160))
                androidx.compose.animation.ContentTransform(
                    enter, exit,
                    sizeTransform = androidx.compose.animation.SizeTransform(clip = false) { _, _ ->
                        androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow)
                    },
                )
            },
            label = "seasonEpisodes",
        ) { shown ->
            val shownEpisodes = grouped[shown].orEmpty()
            var showAll by rememberSaveable(media.stableKey, shown) { mutableStateOf(false) }
            // A long season opens on the episode you came from, or your next one, with one before it.
            val anchor = shownEpisodes.indexOfFirst { shown == initialSeason && it.number == initialEpisode }.takeIf { it >= 0 }
                ?: shownEpisodes.indexOfFirst { it.season == nextEpisode?.season && it.number == nextEpisode.number }.takeIf { it >= 0 }
                ?: 0
            val start = (anchor - 1).coerceIn(0, (shownEpisodes.size - FoldedEpisodeCount).coerceAtLeast(0))
            val folded = !showAll && shownEpisodes.size > FoldEpisodesAbove
            val visible = if (folded) shownEpisodes.subList(start, (start + FoldedEpisodeCount).coerceAtMost(shownEpisodes.size)) else shownEpisodes
            Column(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = DetailLayout.Gutter)) {
                    visible.forEachIndexed { index, episode ->
                        val returnTarget = episode.season == initialSeason && episode.number == initialEpisode
                        val isNext = episode.season == nextEpisode?.season && episode.number == nextEpisode.number
                        val watchedAt = watchedAtByEpisode[episode.season to episode.number]
                        val delay = 50 + index * 45
                        Column(
                            Modifier.animateEnterExit(
                                enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(240, delayMillis = delay)) +
                                    androidx.compose.animation.slideInVertically(
                                        androidx.compose.animation.core.tween(300, delayMillis = delay, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                                    ) { it / 5 },
                                exit = androidx.compose.animation.ExitTransition.None,
                            ),
                        ) {
                            if (index > 0) EpisodeDivider()
                            EpisodeRow(
                                episode,
                                subtitle = when {
                                    isNext -> listOfNotNull(stringResource(R.string.next_short), formatDurationMinutes(episode.runtimeMinutes).takeIf(String::isNotBlank)).joinToString(" · ")
                                    episode.watched && !watchedAt.isNullOrBlank() && (watchCounts[episode.season to episode.number] ?: 0) > 1 -> {
                                        val times = watchCounts[episode.season to episode.number] ?: 0
                                        androidx.compose.ui.res.pluralStringResource(R.plurals.watched_times, times, times, formatFullDate(watchedAt))
                                    }
                                    episode.watched && !watchedAt.isNullOrBlank() -> stringResource(R.string.watched_on, formatFullDate(watchedAt))
                                    else -> formatFullDate(episode.airDate)
                                },
                                subtitleAccent = isNext,
                                highlight = if (returnTarget || isNext) Accent else null,
                                onClick = { onEpisode(episode) },
                                onWatched = { onEpisodeWatched(episode, !episode.watched) },
                            )
                        }
                    }
                }
                if (folded) {
                    Text(
                        stringResource(R.string.show_all_episodes, shownEpisodes.size), color = AccentLight,
                        style = androidx.compose.material3.MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = DetailLayout.Gutter - 8.dp).clip(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Pill))
                            .clickable { showAll = true }.padding(horizontal = 8.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

private fun stepLabel(target: EpisodeCard, from: EpisodeCard): String =
    if (target.season == from.season) "E${target.number}" else "S${target.season} E${target.number}"

@Composable
private fun EpisodeStepButton(label: String?, forward: Boolean, description: String, onClick: () -> Unit) {
    val enabled = label != null
    Row(
        Modifier.height(50.dp).liquidGlassPill(under = GlassMaterial.DetailSurface)
            .clip(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Pill))
            .clickable(enabled = enabled, role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tint = if (enabled) TextPrimary else TextMuted
        if (!forward) Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = tint, modifier = Modifier.size(16.dp))
        if (label != null) {
            Text(label, color = tint, style = androidx.compose.material3.MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 5.dp))
        }
        if (forward) Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = tint, modifier = Modifier.size(16.dp))
    }
}

/** One dash per episode of the season: watched in accent, this one bright, the rest dim. */
@Composable
private fun SeasonDashes(episodes: List<EpisodeCard>, current: Int, currentWatched: Boolean) {
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 56.dp), contentAlignment = Alignment.Center) {
        val gap = 3.dp
        val width = ((maxWidth - gap * (episodes.size - 1)) / episodes.size).coerceIn(3.dp, 16.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            episodes.forEach { episode ->
                val isCurrent = episode.number == current
                val seen = if (isCurrent) currentWatched else episode.watched
                val color by androidx.compose.animation.animateColorAsState(
                    when {
                        isCurrent && !seen -> TextPrimary
                        seen -> Accent
                        else -> Color.White.copy(alpha = .14f)
                    },
                    label = "seasonDash",
                )
                Box(Modifier.width(width).height(if (isCurrent) 4.dp else 3.dp).clip(CircleShape).background(color))
            }
        }
    }
}

/** Whole days from today until [raw]'s date; negative once it has passed, null without a date. */
private fun daysUntil(raw: String?): Int? {
    val day = raw?.take(10)?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() } ?: return null
    return java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.now(), day).toInt()
}

/** "Esce tra 5 giorni" for an upcoming film or episode, with a bell that reminds you on the day. */
@Composable
private fun ReleaseCountdown(media: MediaCard, release: String, episode: EpisodeCard?, viewModel: CineTrackViewModel, progressViewModel: com.cinetrack.ui.progress.ProgressViewModel) {
    val days = daysUntil(release) ?: return
    val context = LocalContext.current
    val reminders by progressViewModel.releaseReminders.collectAsState()
    val reminderOn = progressViewModel.isReleaseReminderOn(
        reminders, media, release, episode?.season, episode?.number,
        com.cinetrack.ui.progress.notifiesByDefault(viewModel.state.value, media, episode?.season, episode?.number),
    )
    val permission = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }
    val whenText = when (days) {
        0 -> stringResource(R.string.release_today)
        1 -> stringResource(R.string.release_tomorrow)
        else -> androidx.compose.ui.res.pluralStringResource(R.plurals.release_in_days, days, days)
    }
    val label = if (episode != null) "S${episode.season} E${episode.number} · $whenText" else "$whenText · ${formatFullDate(release)}"
    Row(
        Modifier.padding(horizontal = DetailLayout.Gutter).fillMaxWidth().glass(RoundedCornerShape(16.dp))
            .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.CalendarMonth, null, tint = AccentLight, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Box(
            Modifier.size(44.dp).clip(CircleShape).clickable(role = androidx.compose.ui.semantics.Role.Switch) {
                val enable = !reminderOn
                if (enable && android.os.Build.VERSION.SDK_INT >= 33 &&
                    androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
                ) permission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                progressViewModel.setReleaseReminder(media, release, episode?.season, episode?.number, episode?.title, enable, com.cinetrack.ui.progress.notifiesByDefault(viewModel.state.value, media, episode?.season, episode?.number))
            },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (reminderOn) Icons.Filled.NotificationsActive else Icons.Outlined.NotificationsNone,
                stringResource(if (reminderOn) R.string.reminder_on else R.string.reminder_off),
                tint = if (reminderOn) AccentLight else TextSecondary, modifier = Modifier.size(21.dp),
            )
        }
    }
}

/** "Visto il 12/10/2026", or "Visto 2 volte · ultimo …" once rewatched. */
@Composable
private fun WatchSummaryLine(summary: WatchSummary) {
    val date = formatFullDate(summary.last)
    Row(Modifier.fillMaxWidth().padding(horizontal = DetailLayout.Gutter), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Check, null, tint = Success, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            if (summary.count > 1) androidx.compose.ui.res.pluralStringResource(R.plurals.watched_times, summary.count, summary.count, date)
            else stringResource(R.string.watched_on, date),
            color = TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
        )
    }
}

/** The first aired episode after the last one you watched, or null for a show you haven't started. */
/** Regular episodes that have aired (today included) and are not watched yet. */
private fun unwatchedAiredEpisodes(episodes: List<EpisodeCard>): List<EpisodeCard> {
    val today = java.time.LocalDate.now()
    return episodes
        .filter { episode ->
            episode.season > 0 && !episode.watched &&
                episode.airDate?.take(10)?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }?.let { !it.isAfter(today) } == true
        }
        .sortedWith(compareBy(EpisodeCard::season, EpisodeCard::number))
}

private fun nextAiredEpisode(episodes: List<EpisodeCard>): EpisodeCard? {
    val ordered = episodes.filter { it.season > 0 }.sortedWith(compareBy(EpisodeCard::season, EpisodeCard::number))
    val last = ordered.lastOrNull(EpisodeCard::watched) ?: return null
    val today = java.time.LocalDate.now()
    return ordered.firstOrNull { candidate ->
        !candidate.watched && (candidate.season > last.season || (candidate.season == last.season && candidate.number > last.number))
    }?.takeIf { candidate ->
        candidate.airDate?.take(10)?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }?.let { !it.isAfter(today) } == true
    }
}

/** Where you are in a started series: the current season on the left, the whole show on the right. */
@Composable
private fun SeriesProgressCard(media: MediaCard, episodes: List<EpisodeCard>, nextUp: EpisodeCard?, onMarkAll: (() -> Unit)? = null) {
    val regular = episodes.filter { it.season > 0 }
    val focusSeason = nextUp?.season ?: regular.filter(EpisodeCard::watched).maxOfOrNull(EpisodeCard::season) ?: return
    val inSeason = regular.filter { it.season == focusSeason }
    val seasonTotal = (media.seasons.firstOrNull { it.number == focusSeason }?.episodeCount ?: 0).coerceAtLeast(inSeason.size)
    val total = media.seasons.filter { it.number > 0 }.sumOf { it.episodeCount }.coerceAtLeast(regular.size).coerceAtLeast(1)
    val watched = regular.count(EpisodeCard::watched)
    val progress by androidx.compose.animation.core.animateFloatAsState(watched.toFloat() / total, label = "seriesProgress")
    Column(Modifier.padding(horizontal = DetailLayout.Gutter).fillMaxWidth().glass(RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.series_progress_season, focusSeason, inSeason.count(EpisodeCard::watched), seasonTotal),
                color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.series_progress_total, watched, total), color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.height(9.dp))
        Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(com.cinetrack.ui.theme.SurfacePalette.ProgressTrack)) {
            Box(Modifier.fillMaxWidth(progress).fillMaxHeight().clip(CircleShape).background(Accent))
        }
        if (onMarkAll != null) {
            Row(
                Modifier.padding(top = 10.dp).align(Alignment.End).clip(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Pill))
                    .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onMarkAll)
                    .glass(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Pill))
                    .heightIn(min = 36.dp).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.DoneAll, null, tint = AccentLight, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.mark_all_aired_watched), color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Seasons longer than this open on a window around your episode, with Mostra tutti. */
private const val FoldEpisodesAbove = 30
private const val FoldedEpisodeCount = 10

/** One episode as a flat row: its still, number and title, a status line and a check circle. */
@Composable
private fun EpisodeRow(
    episode: EpisodeCard,
    subtitle: String,
    subtitleAccent: Boolean,
    highlight: Color?,
    onClick: () -> Unit,
    onWatched: () -> Unit,
) {
    val stillShape = RoundedCornerShape(com.cinetrack.ui.theme.Radius.Compact)
    var infoVisible by rememberSaveable(episode.showId, episode.season, episode.number) { mutableStateOf(false) }
    // The still grows into the episode page's large still when the row is opened.
    val stillSlot = com.cinetrack.ui.components.rememberPosterSlot()
    val sharedKey = "ep:${episode.showId}:${episode.season}:${episode.number}"
    Column(Modifier.fillMaxWidth()) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Small))
            .clickable {
                com.cinetrack.ui.components.SharedPosterSource.select(stillSlot, sharedKey)
                onClick()
            }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.width(96.dp).aspectRatio(16f / 9f)
                .sharedBackdrop(sharedKey, stillSlot)
                .clip(stillShape).background(com.cinetrack.ui.theme.SurfacePalette.ArtworkBackdrop)
                .then(if (highlight != null) Modifier.border(1.6.dp, highlight, stillShape) else Modifier),
        ) {
            episode.stillUrl?.let { com.cinetrack.ui.components.SharedPosterImage(it, episode.title, stillSlot, Modifier.fillMaxSize(), ContentScale.Crop) }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("${episode.number} · ${episode.title}", color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank() && subtitle != "—") {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, color = if (subtitleAccent) AccentLight else TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    fontWeight = if (subtitleAccent) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
            }
        }
        if (episode.overview.isNotBlank()) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).clickable(role = androidx.compose.ui.semantics.Role.Button) { infoVisible = !infoVisible },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Info,
                    stringResource(if (infoVisible) R.string.hide_episode_info else R.string.show_episode_info, episode.title),
                    tint = if (infoVisible) AccentLight else TextMuted, modifier = Modifier.size(19.dp),
                )
            }
        }
        Box(Modifier.size(48.dp).clip(CircleShape).clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onWatched), contentAlignment = Alignment.Center) {
            WatchedCircle(episode.watched, stringResource(if (episode.watched) R.string.mark_unwatched else R.string.mark_watched))
        }
    }
    AnimatedVisibility(infoVisible) {
        Text(
            episode.overview, color = TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, lineHeight = 18.sp,
            modifier = Modifier.padding(start = 108.dp, end = 8.dp, bottom = 10.dp),
        )
    }
    }
}

@Composable
private fun WatchedCircle(watched: Boolean, description: String) {
    Box(
        Modifier.size(28.dp).semantics { contentDescription = description }.clip(CircleShape)
            .then(if (watched) Modifier.background(Success) else Modifier.border(1.3.dp, TextMuted.copy(alpha = .7f), CircleShape)),
        contentAlignment = Alignment.Center,
    ) {
        if (watched) Icon(Icons.Filled.Check, null, tint = com.cinetrack.ui.theme.SurfacePalette.WatchedInk, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun EpisodeDivider() {
    Box(Modifier.fillMaxWidth().padding(start = 108.dp).height(.5.dp).background(Color.White.copy(alpha = .08f)))
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
            fadeInSpec = androidx.compose.animation.core.tween(com.cinetrack.ui.theme.Motion.Long),
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
private fun Modifier.shelfFeather(fade: androidx.compose.ui.unit.Dp = 112.dp): Modifier = drawBehind {
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
private fun Modifier.fadeOutBottom(alpha: Float): Modifier =
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
private fun rememberHeaderProgress(
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
private fun DetailTopBar(
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
            Modifier.fillMaxWidth().statusBarsPadding().height(56.dp).padding(start = com.cinetrack.ui.theme.Spacing.lg, end = if (onRefresh != null) com.cinetrack.ui.theme.Spacing.lg else 64.dp),
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
                        shape = RoundedCornerShape(com.cinetrack.ui.theme.Radius.Medium),
                        containerColor = com.cinetrack.ui.theme.SurfacePalette.MenuSurface,
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

private object DetailLayout {
    val Gutter = 20.dp
    val Inner = 12.dp
    val HeadingInset = Gutter + Inner
    val SectionGap = 24.dp
    val RatingsGap = 12.dp
    val CastWidth = 96.dp
    val CastPhoto = 84.dp
}

@Composable
private fun CastAvatar(person: PersonCard, onPerson: (PersonCard) -> Unit, showCharacter: Boolean = false) {
    // A character without a TVDB picture keeps the actor photo.
    val photo = person.characterImageUrl?.takeIf { showCharacter && it.isNotBlank() } ?: person.profileUrl
    Column(
        Modifier.width(DetailLayout.CastWidth).clip(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Medium))
            .clickable { onPerson(person) }.padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Settings > Appearance picks a round photo or a 2:3 poster; names use the full card width.
        val poster = com.cinetrack.ui.theme.LocalCardAppearance.current.castPhotoShape == "poster"
        val shape = if (poster) RoundedCornerShape(com.cinetrack.ui.theme.Radius.Small) else CircleShape
        Box(
            (if (poster) Modifier.padding(horizontal = 4.dp).fillMaxWidth().aspectRatio(2f / 3f) else Modifier.size(DetailLayout.CastPhoto))
                .clip(shape).background(com.cinetrack.ui.theme.GlassBare)
                .border(.6.dp, Color.White.copy(alpha = .12f), shape),
            contentAlignment = Alignment.Center,
        ) {
            if (!photo.isNullOrBlank()) {
                androidx.compose.animation.Crossfade(photo, label = "castPhoto") { url ->
                    AsyncImage(url, person.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
            } else {
                Icon(Icons.Filled.Person, null, tint = TextSecondary, modifier = Modifier.size(32.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        // Characters mode leads with the character, then the actor.
        // Character names only, never a crew job such as Executive Producer.
        val characterName = person.character.ifBlank { person.role }
        val (primary, secondary) = if (showCharacter && characterName.isNotBlank()) characterName to person.name else person.name to person.role
        // One line each keeps every name and role aligned across the row.
        Text(primary, color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Text(secondary, color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun CastSection(
    people: List<PersonCard>,
    showCharacters: Boolean = false,
    onShowCharacters: (Boolean) -> Unit = {},
    onViewAll: () -> Unit,
    onPerson: (PersonCard) -> Unit,
) {
    // The Characters / Cast switch appears only when Floppy found character pictures.
    val hasCharacters = people.any { !it.characterImageUrl.isNullOrBlank() }
    Column {
        if (hasCharacters) {
            CastModeHeader(showCharacters, onShowCharacters, onViewAll, Modifier.padding(horizontal = DetailLayout.Gutter))
        } else {
            SectionHeader(stringResource(R.string.cast_title), Modifier.padding(horizontal = DetailLayout.Gutter), stringResource(R.string.see_all), onViewAll)
        }
        Spacer(Modifier.height(12.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = DetailLayout.Gutter - 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            // Directors, producers and other crew appear only under Cast.
            val shown = if (showCharacters && hasCharacters) people.filter(PersonCard::isCastMember) else people
            items(shown.take(18), key = PersonCard::id) { person ->
                CastAvatar(person, onPerson, showCharacter = showCharacters && hasCharacters)
            }
        }
    }
}

/** Characters / Cast headlines that switch like the Film / Serie header. */
@Composable
private fun CastModeHeader(showCharacters: Boolean, onShowCharacters: (Boolean) -> Unit, onViewAll: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        CastModeHeadline(stringResource(R.string.characters_title), showCharacters) { onShowCharacters(true) }
        Spacer(Modifier.width(14.dp))
        CastModeHeadline(stringResource(R.string.cast_title), !showCharacters) { onShowCharacters(false) }
        Spacer(Modifier.weight(1f))
        val viewAll = rememberUiAction(onViewAll)
        Row(
            Modifier.clip(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Pill)).clickable(onClick = viewAll).padding(start = com.cinetrack.ui.theme.Spacing.sm, top = com.cinetrack.ui.theme.Spacing.sm, bottom = com.cinetrack.ui.theme.Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.see_all), color = TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(2.dp))
            Icon(Icons.Filled.ChevronRight, null, tint = TextSecondary, modifier = Modifier.size(15.dp))
        }
    }
}

@Composable
private fun CastModeHeadline(text: String, selected: Boolean, onClick: () -> Unit) {
    val color by androidx.compose.animation.animateColorAsState(
        if (selected) TextPrimary else TextPrimary.copy(alpha = .42f),
        androidx.compose.animation.core.tween(com.cinetrack.ui.theme.Motion.Extended),
        label = "castModeHeadline",
    )
    Text(
        text,
        color = color,
        lineHeight = 21.sp,
        fontWeight = FontWeight.ExtraBold,
        style = androidx.compose.material3.MaterialTheme.typography.titleMedium.copy(
            shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = .62f), androidx.compose.ui.geometry.Offset(0f, 2.5f), 6f),
        ),
        modifier = Modifier.softTab(selected, onClick),
    )
}

@Composable
private fun SeasonInfoSheet(
    show: MediaCard,
    number: Int,
    detailViewModel: com.cinetrack.ui.detail.DetailViewModel,
    onDismiss: () -> Unit,
    onPerson: (PersonCard) -> Unit,
) {
    var details by remember(show.stableKey, number) { mutableStateOf<com.cinetrack.domain.SeasonDetails?>(null) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableStateOf(0) }
    LaunchedEffect(show.stableKey, number, attempt) {
        loading = true
        failed = false
        detailViewModel.loadSeasonDetails(show, number).fold(
            onSuccess = { details = it },
            onFailure = { failed = true },
        )
        loading = false
    }
    SharedGlassSheet(onDismiss) {
        Column(
            Modifier.fillMaxWidth().fillMaxHeight(.72f)
                .padding(horizontal = DetailLayout.Gutter)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column {
                SectionHeader(details?.title?.takeIf(String::isNotBlank) ?: stringResource(R.string.season_number, number))
                Text(show.title, color = TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
            }
            if (loading) {
                Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.CircularProgressIndicator(color = AccentLight)
                }
            }
            if (failed) {
                Column {
                    Text(stringResource(R.string.season_info_error), color = TextSecondary)
                    androidx.compose.material3.TextButton(onClick = { attempt++ }) { Text(stringResource(R.string.retry), color = TextPrimary) }
                }
            }
            details?.let { season ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    season.posterUrl?.let { url ->
                        AsyncImage(url, season.title, Modifier.width(80.dp).height(120.dp).clip(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Small)), contentScale = ContentScale.Crop)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("TMDB " + (season.score?.let { "%.1f".format(it) } ?: "—"), color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text(formatFullDate(season.airDate), color = TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, maxLines = 2)
                    }
                }
                Column {
                    SectionHeader(stringResource(R.string.overview))
                    Spacer(Modifier.height(8.dp))
                    Text(season.overview.ifBlank { stringResource(R.string.season_no_description) }, color = TextSecondary,
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                }
                DetailsList(stringResource(R.string.details), listOfNotNull(
                    stringResource(R.string.episodes) to season.episodeCount.toString(),
                    formatDurationMinutes(season.runtimeMinutes).takeIf(String::isNotBlank)?.let { stringResource(R.string.average_runtime) to it },
                ), inset = 0.dp)
                SectionHeader(stringResource(R.string.season_cast))
                if (season.cast.isEmpty()) Text(stringResource(R.string.season_no_cast), color = TextSecondary)
                else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(season.cast, key = PersonCard::id) { CastAvatar(it, onPerson) }
                    }
                }
            }
        }
    }
}

@Composable
private fun FullCastSheet(people: List<PersonCard>, showCharacters: Boolean = false, onDismiss: () -> Unit, onPerson: (PersonCard) -> Unit) {
    val hasCharacters = people.any { !it.characterImageUrl.isNullOrBlank() }
    SharedGlassSheet(onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = DetailLayout.Gutter)) {
            SectionHeader(stringResource(R.string.full_cast), Modifier.padding(horizontal = DetailLayout.Inner))
            Spacer(Modifier.height(12.dp))
            LazyVerticalGrid(
                columns = GridCells.Adaptive(DetailLayout.CastWidth),
                modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 20.dp),
            ) {
                gridItems(if (showCharacters && hasCharacters) people.filter(PersonCard::isCastMember) else people, key = PersonCard::id) { person ->
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                        CastAvatar(person, onPerson, showCharacter = showCharacters && hasCharacters)
                    }
                }
            }
        }
    }
}

@Composable
private fun MarkShowWatchedPrompt(
    title: String,
    episodeCount: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    SharedGlassSheet(onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = com.cinetrack.ui.theme.Spacing.lg)) {
            Text(stringResource(R.string.mark_show_watched_title), color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(7.dp))
            Text(
                androidx.compose.ui.res.pluralStringResource(R.plurals.mark_show_watched_message, episodeCount, episodeCount, title),
                color = TextSecondary,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(16.dp))
            PrimaryAction(
                androidx.compose.ui.res.pluralStringResource(R.plurals.mark_show_watched_confirm, episodeCount, episodeCount),
                Icons.Filled.Check,
                Modifier.fillMaxWidth(),
                onClick = onConfirm,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(46.dp), shape = RoundedCornerShape(com.cinetrack.ui.theme.Radius.Pill)) {
                Text(stringResource(R.string.cancel), color = TextPrimary, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun UnmarkShowPrompt(
    title: String,
    watchedCount: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    SharedGlassSheet(onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = com.cinetrack.ui.theme.Spacing.lg)) {
            Text(stringResource(R.string.unmark_show_title), color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(7.dp))
            Text(
                androidx.compose.ui.res.pluralStringResource(R.plurals.unmark_show_message, watchedCount, watchedCount, title),
                color = TextSecondary,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(16.dp))
            PrimaryAction(stringResource(R.string.unmark_show_confirm), Icons.Outlined.RemoveDone, Modifier.fillMaxWidth(), containerColor = Color(0xFFB3423F), onClick = onConfirm)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(46.dp), shape = RoundedCornerShape(com.cinetrack.ui.theme.Radius.Pill)) {
                Text(stringResource(R.string.cancel), color = TextPrimary, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PreviousEpisodesPrompt(
    previousCount: Int,
    onDismiss: () -> Unit,
    onOnlyThis: () -> Unit,
    onIncludePrevious: () -> Unit,
) {
    SharedGlassSheet(onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = com.cinetrack.ui.theme.Spacing.lg)) {
            Text(stringResource(R.string.previous_episodes_title), color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(7.dp))
            Text(
                stringResource(R.string.previous_episodes_message, previousCount),
                color = TextSecondary,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(16.dp))
            PrimaryAction(stringResource(R.string.mark_previous_too), Icons.Filled.Check, Modifier.fillMaxWidth(), onClick = onIncludePrevious)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onOnlyThis, modifier = Modifier.fillMaxWidth().height(46.dp), shape = RoundedCornerShape(com.cinetrack.ui.theme.Radius.Pill)) {
                Text(stringResource(R.string.only_this_episode), color = TextPrimary, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun CollectionSection(current: MediaCard, related: List<MediaCard>, onMedia: (MediaCard) -> Unit) {
    val collection = (listOf(current) + related.filter { it.type == current.type })
        .distinctBy(MediaCard::stableKey)
        .sortedWith(
            compareBy<MediaCard> { it.releaseDate.isNullOrBlank() }
                .thenBy { it.releaseDate.orEmpty() }
                .thenBy(MediaCard::title),
        )
    val selectedIndex = collection.indexOfFirst { it.stableKey == current.stableKey }.coerceAtLeast(0)
    val collectionState = rememberLazyListState(
        initialFirstVisibleItemIndex = (selectedIndex - 1).coerceAtLeast(0),
    )
    LaunchedEffect(current.stableKey, collection.size) {
        // Show the selected film immediately with one chronological neighbour
        // before it whenever the rail has enough items.
        collectionState.scrollToItem((selectedIndex - 1).coerceAtLeast(0))
    }
    val today = remember { java.time.LocalDate.now() }
    val seen = collection.count { it.watched || it.status == LibraryStatus.COMPLETED }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = DetailLayout.Gutter), verticalAlignment = Alignment.CenterVertically) {
            SectionHeader(stringResource(R.string.collection_title), Modifier.weight(1f))
            Text(stringResource(R.string.saga_seen, seen, collection.size), color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.height(12.dp))
        LazyRow(
            state = collectionState,
            contentPadding = PaddingValues(horizontal = DetailLayout.Gutter),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(collection, key = MediaCard::stableKey) { item ->
                val selected = item.stableKey == current.stableKey
                // Films that aren't out yet sit back a little.
                val upcoming = item.releaseDate?.take(10)?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }?.isAfter(today) == true
                Column(Modifier.alpha(if (upcoming && !selected) .55f else 1f)) {
                    MediaPoster(
                        item,
                        width = 104.dp,
                        showYear = false,
                        selectedBorder = TextPrimary.takeIf { selected },
                        onClick = { onMedia(item) },
                    )
                    Text(
                        listOfNotNull("${collection.indexOf(item) + 1}", item.releaseDate?.take(4)?.takeIf(String::isNotBlank)).joinToString(" · "),
                        color = if (selected) TextPrimary else TextMuted,
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

@Composable
private fun detailLibraryActionLabel(status: LibraryStatus): String = when (status) {
    LibraryStatus.NONE -> stringResource(R.string.add_to_library)
    LibraryStatus.WATCHING -> stringResource(R.string.in_progress)
    LibraryStatus.PLAN_TO_WATCH -> stringResource(R.string.plan_to_watch)
    LibraryStatus.PAUSED -> stringResource(R.string.paused)
    LibraryStatus.COMPLETED -> stringResource(R.string.completed)
    LibraryStatus.DROPPED -> stringResource(R.string.dropped)
}

@Composable
private fun tmdbStatusLabel(status: String): String = when (status.lowercase()) {
    "returning series" -> stringResource(R.string.tmdb_returning_series)
    "in production" -> stringResource(R.string.tmdb_in_production)
    "post production" -> stringResource(R.string.tmdb_post_production)
    "released" -> stringResource(R.string.tmdb_released)
    "ended" -> stringResource(R.string.tmdb_ended)
    "planned" -> stringResource(R.string.tmdb_planned)
    "canceled", "cancelled" -> stringResource(R.string.tmdb_canceled)
    "pilot" -> stringResource(R.string.tmdb_pilot)
    "rumored", "rumoured" -> stringResource(R.string.tmdb_rumored)
    else -> status
}

/** Only what the header doesn't already say, as a plain label / value list. */
@Composable
private fun UsefulInfoSection(media: MediaCard) {
    val locale = com.cinetrack.ui.currentLocale()
    val rows = buildList {
        formatFullDate(media.releaseDate).takeIf { it.isNotBlank() && it != "—" }?.let {
            add(stringResource(if (media.type == MediaType.TV) R.string.first_aired else R.string.release_date) to it)
        }
        if (media.type == MediaType.MOVIE) {
            formatFullDate(media.digitalRelease).takeIf { it.isNotBlank() && it != "—" }?.let { date ->
                add(stringResource(R.string.digital_release) to (media.digitalReleaseCountry?.let { "$date ($it)" } ?: date))
            }
        }
        if (media.type == MediaType.MOVIE) media.tmdbStatus?.takeIf(String::isNotBlank)?.let { add(stringResource(R.string.status) to tmdbStatusLabel(it)) }
        if (media.type == MediaType.TV) {
            val regular = media.seasons.filter { it.number > 0 }
            if (regular.isNotEmpty()) {
                add(stringResource(R.string.seasons) to regular.size.toString())
                regular.sumOf { it.episodeCount }.takeIf { it > 0 }?.let { add(stringResource(R.string.episodes) to it.toString()) }
            }
            if (media.networks.isNotEmpty()) add(stringResource(R.string.networks) to media.networks.joinToString(" · "))
        } else {
            formatUsd(media.budget).takeIf(String::isNotBlank)?.let { add(stringResource(R.string.budget) to it) }
            formatUsd(media.boxOffice).takeIf(String::isNotBlank)?.let { add(stringResource(R.string.box_office) to it) }
        }
        if (media.productionCountries.isNotEmpty()) add(stringResource(R.string.production_countries) to media.productionCountries.joinToString(" · "))
        media.originalLanguage?.takeIf(String::isNotBlank)?.let { code ->
            val name = java.util.Locale.forLanguageTag(code.lowercase()).getDisplayLanguage(locale)
                .takeIf { it.isNotBlank() && !it.equals(code, ignoreCase = true) }
                ?.replaceFirstChar { it.titlecase(locale) } ?: code.uppercase()
            add(stringResource(R.string.original_language) to name)
        }
        if (media.productionCompanies.isNotEmpty()) add(stringResource(R.string.production_companies) to media.productionCompanies.take(3).joinToString(" · "))
    }
    if (rows.isNotEmpty()) DetailsList(stringResource(R.string.details), rows)
}

@Composable
private fun DetailsList(title: String, rows: List<Pair<String, String>>, inset: androidx.compose.ui.unit.Dp = DetailLayout.Gutter) {
    Column(Modifier.padding(horizontal = inset).fillMaxWidth()) {
        SectionHeader(title)
        Spacer(Modifier.height(4.dp))
        rows.forEachIndexed { index, (label, value) ->
            if (index > 0) Box(Modifier.fillMaxWidth().height(.5.dp).background(Color.White.copy(alpha = .08f)))
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.Top) {
                Text(label, color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 16.dp))
                Text(value, color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.End, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
        }
    }
}

private fun formatUsd(amount: Long?): String {
    val value = amount?.takeIf { it > 0L } ?: return ""
    fun compact(divisor: Double, suffix: String): String {
        val number = value / divisor
        val formatted = if (number >= 100 || number % 1.0 == 0.0) "%.0f".format(java.util.Locale.US, number)
        else "%.1f".format(java.util.Locale.US, number)
        return "\$$formatted$suffix"
    }
    return when {
        value >= 1_000_000_000L -> compact(1_000_000_000.0, "B")
        value >= 1_000_000L -> compact(1_000_000.0, "M")
        else -> java.text.NumberFormat.getCurrencyInstance(java.util.Locale.US).format(value)
    }
}

@Composable
internal fun ActorSheet(person: PersonCard, detailViewModel: com.cinetrack.ui.detail.DetailViewModel, onDismiss: () -> Unit, onMedia: (MediaCard) -> Unit) {
    var details by remember(person.id) { mutableStateOf(person) }
    var biographyExpanded by remember(person.id) { mutableStateOf(false) }
    var biographyOverflowing by remember(person.id) { mutableStateOf(false) }
    LaunchedEffect(person.id) { details = detailViewModel.loadPerson(person) }
    SharedGlassSheet(onDismiss) {
        Column(
            Modifier.fillMaxWidth().fillMaxHeight(.72f).padding(horizontal = com.cinetrack.ui.theme.Spacing.lg)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(80.dp).height(100.dp).clip(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Medium)).background(com.cinetrack.ui.theme.GlassBare)) {
                    if (!details.profileUrl.isNullOrBlank()) AsyncImage(details.profileUrl, details.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else Icon(Icons.Filled.Person, null, tint = Color.White, modifier = Modifier.align(Alignment.Center).size(32.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(details.name, color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, lineHeight = 22.sp, fontWeight = FontWeight.ExtraBold)
                    Text(details.role, color = AccentLight, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    val age = details.age()
                    val born = listOfNotNull(formatFullDate(details.birthday).takeIf(String::isNotBlank), age?.let { stringResource(R.string.years_old, it) }).joinToString(" · ")
                    if (born.isNotBlank()) Text(born, color = TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                    details.placeOfBirth?.let { Text(it, color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, maxLines = 1) }
                }
            }
            if (details.biography.isNotBlank()) {
                Spacer(Modifier.height(13.dp))
                Text(
                    details.biography,
                    color = TextSecondary,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    lineHeight = 17.sp,
                    maxLines = if (biographyExpanded) Int.MAX_VALUE else 5,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { result -> if (!biographyExpanded) biographyOverflowing = result.hasVisualOverflow },
                )
                if (biographyOverflowing || biographyExpanded) {
                    Text(
                        if (biographyExpanded) stringResource(R.string.collapse) else stringResource(R.string.see_all),
                        color = AccentLight,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = com.cinetrack.ui.theme.Spacing.xs).clickable { biographyExpanded = !biographyExpanded },
                    )
                }
            }
            if (details.movieCredits.isNotEmpty()) {
                Spacer(Modifier.height(16.dp)); SectionHeader(stringResource(R.string.recent_credits))
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    details.movieCredits.forEach { movie ->
                        Row(Modifier.fillMaxWidth().glass(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Small))
                            .blueEdgeClickable(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Small)) { onMedia(movie); onDismiss() }.padding(com.cinetrack.ui.theme.Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.width(42.dp).height(60.dp).clip(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Compact)).background(Accent.copy(alpha = .18f))) {
                                if (!movie.posterUrl.isNullOrBlank()) AsyncImage(movie.posterUrl, movie.title, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(movie.title, color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text(movie.year, color = AccentLight, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EpisodeDetailScreen(
    show: MediaCard?,
    episode: EpisodeCard?,
    requestedSeason: Int,
    requestedNumber: Int,
    allEpisodes: List<EpisodeCard>,
    people: List<PersonCard>,
    viewModel: CineTrackViewModel,
    detailViewModel: com.cinetrack.ui.detail.DetailViewModel,
    progressViewModel: com.cinetrack.ui.progress.ProgressViewModel,
    onBack: () -> Unit,
    onSeries: () -> Unit,
    onMedia: (MediaCard) -> Unit,
    onWatched: (EpisodeCard, Boolean) -> Unit,
) {
    var loadedEpisode by remember(show?.id) { mutableStateOf(episode) }
    var loadedEpisodes by remember(show?.id) { mutableStateOf(allEpisodes) }
    // Each episode's cast, kept while the page is open; the episodes beside the
    // current one are fetched ahead, so swiping shows their cast right away.
    val castByEpisode = remember(show?.id) { androidx.compose.runtime.mutableStateMapOf<Pair<Int, Int>, List<PersonCard>>() }
    // Which episode's plot was asked for; others show a skeleton until they load.
    var overviewFor by remember(show?.id) { mutableStateOf<Pair<Int, Int>?>(null) }
    LaunchedEffect(episode?.season, episode?.number, episode?.watched) {
        val latest = episode ?: return@LaunchedEffect
        if (loadedEpisode?.season == latest.season && loadedEpisode?.number == latest.number) {
            loadedEpisode = loadedEpisode?.copy(watched = latest.watched) ?: latest
        }
        loadedEpisodes = loadedEpisodes.map { cached ->
            if (cached.season == latest.season && cached.number == latest.number) latest else cached
        }
    }
    LaunchedEffect(show?.stableKey, requestedSeason, requestedNumber) {
        show?.let {
            loadedEpisode = (detailViewModel.loadEpisode(it, requestedSeason, requestedNumber)
                ?: loadedEpisode)
                .also { overviewFor = requestedSeason to requestedNumber }
                ?: EpisodeCard(
                    id = -1,
                    showId = it.id,
                    season = requestedSeason,
                    number = requestedNumber,
                    title = "S${requestedSeason.toString().padStart(2, '0')} E${requestedNumber.toString().padStart(2, '0')}",
                    overview = "",
                    airDate = null,
                )
            val seasonEpisodes = detailViewModel.loadEpisodes(it, requestedSeason)
            loadedEpisodes = (loadedEpisodes + seasonEpisodes + listOfNotNull(loadedEpisode))
                .distinctBy { candidate -> candidate.season to candidate.number }
                .sortedWith(compareBy(EpisodeCard::season, EpisodeCard::number))
        }
    }
    val currentEpisode = loadedEpisode
    if (currentEpisode == null) {
        AdaptiveBackground { LoadingPane(); GlassBackButton(onClick = onBack, modifier = Modifier.statusBarsPadding().padding(com.cinetrack.ui.theme.Spacing.md)) }
        return
    }
    val orderedEpisodes = remember(loadedEpisodes) {
        loadedEpisodes.distinctBy { it.season to it.number }
            .sortedWith(compareBy(EpisodeCard::season, EpisodeCard::number))
    }
    // One page per episode: the page swiped to is the page that stays, so
    // nothing is swapped or redrawn when a swipe settles.
    val pages = remember(orderedEpisodes, currentEpisode.season, currentEpisode.number) {
        if (orderedEpisodes.any { it.season == currentEpisode.season && it.number == currentEpisode.number }) orderedEpisodes
        else (orderedEpisodes + currentEpisode).sortedWith(compareBy(EpisodeCard::season, EpisodeCard::number))
    }
    fun pageOf(target: EpisodeCard) = pages.indexOfFirst { it.season == target.season && it.number == target.number }
    val pagerState = rememberPagerState(initialPage = pageOf(currentEpisode).coerceAtLeast(0), pageCount = { pages.size })
    val pagerScope = rememberCoroutineScope()
    val showCast by androidx.compose.runtime.rememberUpdatedState(people)
    LaunchedEffect(show?.stableKey, currentEpisode.season, currentEpisode.number, pages.size) {
        val series = show ?: return@LaunchedEffect
        val index = pageOf(currentEpisode)
        listOfNotNull(currentEpisode, pages.getOrNull(index + 1), pages.getOrNull(index - 1)).forEach { target ->
            val key = target.season to target.number
            if (key !in castByEpisode) {
                castByEpisode[key] = runCatching { detailViewModel.loadEpisodeCast(series, target.season, target.number) }.getOrDefault(emptyList()).ifEmpty { showCast }
            }
        }
        // The show's TVDB character pictures fill in after the TMDB cast is on screen.
        listOfNotNull(currentEpisode, pages.getOrNull(index + 1), pages.getOrNull(index - 1)).forEach { target ->
            val key = target.season to target.number
            castByEpisode[key]?.let { cast -> castByEpisode[key] = detailViewModel.withCharacterPictures(series, cast, remember = false) }
        }
    }
    // Episodes arriving (the season loading) move the current one to another index.
    LaunchedEffect(pages) {
        val index = pageOf(currentEpisode)
        if (index >= 0 && index != pagerState.currentPage && !pagerState.isScrollInProgress) pagerState.scrollToPage(index)
    }
    LaunchedEffect(pagerState, pages) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .drop(1) // the page at (re)start, before any swipe
            .collect { page ->
                val target = pages.getOrNull(page) ?: return@collect
                val active = loadedEpisode
                if (active == null || active.season != target.season || active.number != target.number) loadedEpisode = target
            }
    }
    val loadedPeople = castByEpisode[currentEpisode.season to currentEpisode.number] ?: people
    var selectedPerson by remember { mutableStateOf<PersonCard?>(null) }
    var showFullCast by remember(show?.stableKey) { mutableStateOf(false) }
    var showCharacters by remember(show?.stableKey) { mutableStateOf(true) }
    val history by remember(viewModel) { viewModel.state.map { it.history }.distinctUntilChanged() }.collectAsState(initial = emptyList())
    val posterAccent = com.cinetrack.ui.components.rememberPosterAccent(show?.posterUrl ?: show?.backdropUrl, rememberPosterAccentEnabled(viewModel))
    androidx.compose.runtime.CompositionLocalProvider(com.cinetrack.ui.theme.LocalDetailAccent provides posterAccent) {
    val detailGlassState = rememberDetailGlassState()
    var activeListState by remember { mutableStateOf<androidx.compose.foundation.lazy.LazyListState?>(null) }
    AdaptiveBackground(
        artworkUrl = currentEpisode.stillUrl ?: show?.backdropUrl ?: show?.posterUrl,
        hazeState = detailGlassState,
        blurBackdrop = true,
    ) {
        val headerHaze = remember { dev.chrisbanes.haze.HazeState() }
        Box(Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize().hazeSource(headerHaze),
            beyondViewportPageCount = 0,
            key = { index -> pages.getOrNull(index)?.let { "${it.season}:${it.number}" } ?: index },
        ) { page ->
            val displayedEpisode = pages.getOrNull(page) ?: currentEpisode
            val previous = pages.getOrNull(page - 1)
            val next = pages.getOrNull(page + 1)
            var watched by remember(displayedEpisode.season, displayedEpisode.number, displayedEpisode.watched) {
                mutableStateOf(displayedEpisode.watched)
            }
            val pageListState = remember(displayedEpisode.season, displayedEpisode.number) { androidx.compose.foundation.lazy.LazyListState() }
            if (page == pagerState.currentPage) androidx.compose.runtime.SideEffect { activeListState = pageListState }
            LazyColumn(
                Modifier.fillMaxSize(),
                state = pageListState,
                contentPadding = PaddingValues(bottom = 0.dp),
            ) {
                item {
                    Box(Modifier.fillMaxWidth()) {
                        (displayedEpisode.stillUrl ?: show?.backdropUrl)?.takeIf(String::isNotBlank)?.let { art ->
                            AsyncImage(art, null, Modifier.matchParentSize().blur(18.dp).fadeOutBottom(.72f), contentScale = ContentScale.Crop)
                            Box(Modifier.matchParentSize().background(Brush.verticalGradient(0f to Color(0x33080A0C), .55f to Color(0x4D080A0C), 1f to Color.Transparent)))
                        }
                    Box(Modifier.fillMaxWidth().statusBarsPadding().height(330.dp)) {
                        // The tapped Progress card grows into this still; its art stands in until the still loads.
                        Box(Modifier.fillMaxWidth().padding(start = com.cinetrack.ui.theme.Spacing.xl, end = com.cinetrack.ui.theme.Spacing.xl, top = 74.dp, bottom = 46.dp).aspectRatio(16f / 9f)
                            .sharedBackdrop("ep:${displayedEpisode.showId}:${displayedEpisode.season}:${displayedEpisode.number}", slot = null)
                            .shadow(22.dp, RoundedCornerShape(com.cinetrack.ui.theme.Radius.Large), clip = false).clip(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Large)).background(Brush.linearGradient(listOf(com.cinetrack.ui.theme.SurfacePalette.EpisodeTeal, com.cinetrack.ui.theme.SurfacePalette.EpisodeSurface)))) {
                            if (!displayedEpisode.stillUrl.isNullOrBlank()) com.cinetrack.ui.components.SharedPosterTargetImage(displayedEpisode.stillUrl, displayedEpisode.title, "ep:${displayedEpisode.showId}:${displayedEpisode.season}:${displayedEpisode.number}", Modifier.fillMaxSize(), ContentScale.Crop)
                            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .54f)))))
                        }
                    }
                    }
                }
                item {
                    Column(
                        Modifier.fillMaxWidth()
                            .shelfFeather()
                            .navigationBarsPadding()
                            .padding(top = com.cinetrack.ui.theme.Spacing.xxxl, bottom = 72.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = DetailLayout.Gutter), horizontalAlignment = Alignment.CenterHorizontally) {
                            // The series as a small chip with its poster; it opens the series page.
                            Row(
                                Modifier.clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = .08f))
                                    .clickable(onClick = onSeries).padding(start = 4.dp, end = 14.dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // A rounded chip, not a pill: its corners stay concentric with the poster's (12dp = 8dp + the 4dp inset).
                                show?.posterUrl?.takeIf(String::isNotBlank)?.let {
                                    AsyncImage(it, null, Modifier.width(24.dp).height(34.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text(show?.title.orEmpty(), color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = TextMuted, modifier = Modifier.padding(start = 6.dp).size(13.dp))
                            }
                            Text(displayedEpisode.title, color = Color.White, style = androidx.compose.material3.MaterialTheme.typography.displaySmall, lineHeight = 31.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp))
                            val episodeFacts = listOfNotNull(
                                formatFullDate(displayedEpisode.airDate).takeIf { it.isNotBlank() && it != "—" },
                                formatDurationMinutes(displayedEpisode.runtimeMinutes).takeIf(String::isNotBlank),
                                displayedEpisode.score?.let { "★ " + String.format(com.cinetrack.ui.currentLocale(), "%.1f", it) },
                            )
                            Text(
                                androidx.compose.ui.text.buildAnnotatedString {
                                    withStyle(androidx.compose.ui.text.SpanStyle(color = AccentLight, fontWeight = FontWeight.ExtraBold)) {
                                        append("S${displayedEpisode.season} · E${displayedEpisode.number}")
                                    }
                                    episodeFacts.forEach { append(" · "); append(it) }
                                },
                                color = TextSecondary,
                                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                        // Previous episode, watched, next episode; the arrows say where they lead.
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = DetailLayout.Gutter), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            EpisodeStepButton(previous?.let { stepLabel(it, displayedEpisode) }, forward = false, description = stringResource(R.string.previous_episode)) {
                                pagerScope.launch { pagerState.animateScrollToPage(page - 1) }
                            }
                            PrimaryAction(
                                if (watched) stringResource(R.string.watched_tv) else stringResource(R.string.mark_watched),
                                Icons.Filled.Check,
                                Modifier.weight(1f),
                                containerColor = if (watched) Success else com.cinetrack.ui.theme.SurfacePalette.NeutralControl,
                                compact = true,
                                liveGlass = true,
                                hazeState = detailGlassState,
                            ) {
                                val newWatched = !watched
                                watched = newWatched
                                onWatched(displayedEpisode.copy(watched = newWatched), newWatched)
                            }
                            EpisodeStepButton(next?.let { stepLabel(it, displayedEpisode) }, forward = true, description = stringResource(R.string.next_episode)) {
                                pagerScope.launch { pagerState.animateScrollToPage(page + 1) }
                            }
                        }
                        val seasonLine = orderedEpisodes.filter { it.season == displayedEpisode.season }
                        if (seasonLine.size in 2..60) SeasonDashes(seasonLine, displayedEpisode.number, watched)
                        if (watched && show != null) {
                            remember(history, show.stableKey, displayedEpisode.season, displayedEpisode.number) {
                                watchSummary(history, show.stableKey, displayedEpisode.season, displayedEpisode.number)
                            }?.let { WatchSummaryLine(it) }
                        }
                        if (show != null && !watched && daysUntil(displayedEpisode.airDate)?.let { it >= 0 } == true) {
                            ReleaseCountdown(show, displayedEpisode.airDate!!, displayedEpisode, viewModel, progressViewModel)
                        }
                        }
                        val episodeKey = displayedEpisode.season to displayedEpisode.number
                        val pageCast = castByEpisode[episodeKey]
                        LoadedSection(displayedEpisode.overview.isNotBlank() || overviewFor == episodeKey || pageCast != null, { OverviewSkeleton() }) {
                            if (displayedEpisode.overview.isNotBlank()) PlotSection(displayedEpisode.overview)
                        }
                        LoadedSection(pageCast != null, { CastSkeleton() }) {
                            if (!pageCast.isNullOrEmpty()) CastSection(pageCast, showCharacters, { showCharacters = it }, onViewAll = { showFullCast = true }) { selectedPerson = it }
                        }
                        if (pageCast != null) {
                            // Episode credits list crew by their job; show who directed and wrote it.
                            val directors = pageCast.filter { it.role.equals("Director", ignoreCase = true) }.map(PersonCard::name).distinct()
                            val writers = pageCast.filter { person -> listOf("Writer", "Teleplay", "Screenplay", "Story").any { person.role.equals(it, ignoreCase = true) } }
                                .map(PersonCard::name).distinct()
                            val rows = buildList {
                                if (directors.isNotEmpty()) add(stringResource(R.string.director_label) to directors.joinToString(", "))
                                if (writers.isNotEmpty()) add(stringResource(R.string.writer_label) to writers.take(3).joinToString(", "))
                            }
                            if (rows.isNotEmpty()) DetailsList(stringResource(R.string.details), rows)
                        }
                        val seasonRows = orderedEpisodes.filter { it.season == displayedEpisode.season }
                        if (seasonRows.size > 1) {
                            val index = seasonRows.indexOfFirst { it.number == displayedEpisode.number }.coerceAtLeast(0)
                            val start = (index - 1).coerceIn(0, (seasonRows.size - 3).coerceAtLeast(0))
                            Column(Modifier.fillMaxWidth().padding(horizontal = DetailLayout.Gutter)) {
                                SectionHeader(stringResource(R.string.season_number, displayedEpisode.season))
                                seasonRows.drop(start).take(3).forEachIndexed { rowIndex, episode ->
                                    if (rowIndex > 0) EpisodeDivider()
                                    val isThis = episode.number == displayedEpisode.number
                                    EpisodeRow(
                                        episode.copy(watched = if (isThis) watched else episode.watched),
                                        subtitle = if (isThis) stringResource(R.string.this_episode) else formatFullDate(episode.airDate),
                                        subtitleAccent = false,
                                        highlight = TextPrimary.takeIf { isThis },
                                        onClick = {
                                            val target = pageOf(episode)
                                            if (!isThis && target >= 0) pagerScope.launch { pagerState.animateScrollToPage(target) }
                                        },
                                        onWatched = {
                                            if (isThis) {
                                                val newWatched = !watched
                                                watched = newWatched
                                                onWatched(displayedEpisode.copy(watched = newWatched), newWatched)
                                            } else {
                                                val newWatched = !episode.watched
                                                loadedEpisodes = loadedEpisodes.map {
                                                    if (it.season == episode.season && it.number == episode.number) it.copy(watched = newWatched) else it
                                                }
                                                onWatched(episode.copy(watched = newWatched), newWatched)
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        val headerProgress = rememberHeaderProgress(activeListState, revealStart = 230.dp)
        DetailTopBar(
            title = currentEpisode.title,
            subtitle = listOfNotNull(show?.title, "S%02d · E%02d".format(currentEpisode.season, currentEpisode.number)).joinToString(" · "),
            progress = headerProgress,
            haze = headerHaze,
            onBack = onBack,
        )
        }
    }
    selectedPerson?.let { person ->
        ActorSheet(person, detailViewModel, onDismiss = { selectedPerson = null }, onMedia = onMedia)
    }
    if (showFullCast) {
        FullCastSheet(loadedPeople, showCharacters, onDismiss = { showFullCast = false }) { person ->
            showFullCast = false
            selectedPerson = person
        }
    }
}
}
