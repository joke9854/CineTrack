@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.cinetrack.ui.screens

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
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
import coil.compose.AsyncImage
import com.cinetrack.R
import com.cinetrack.domain.EpisodeCard
import com.cinetrack.domain.LibraryStatus
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
import com.cinetrack.ui.theme.Accent
import com.cinetrack.ui.theme.AccentLight
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope

@Composable
fun DetailScreen(
    media: MediaCard?,
    people: List<PersonCard>,
    episodes: List<EpisodeCard>,
    history: List<TimelineCard>,
    recommended: List<MediaCard>,
    viewModel: CineTrackViewModel,
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
    val cachedDetail = viewModel.cachedDetails(media)
    val cachedEpisodes = viewModel.cachedEpisodes(media.id)
    var detail by remember(media.stableKey) {
        mutableStateOf(
            (cachedDetail ?: media).copy(
                status = media.status,
                watched = media.watched,
                libraryUpdatedAt = media.libraryUpdatedAt,
            ),
        )
    }
    var ratings by remember(media.stableKey) { mutableStateOf(viewModel.cachedRatings(media)) }
    var detailPeople by remember(media.stableKey) { mutableStateOf(viewModel.cachedCast(media).ifEmpty { people }) }
    var detailEpisodes by remember(media.stableKey) {
        mutableStateOf(mergeWatchedEpisodes(cachedEpisodes.ifEmpty { episodes }, episodes, history))
    }
    var collectionItems by remember(media.stableKey) { mutableStateOf<List<MediaCard>>(emptyList()) }
    var moreLikeThis by remember(media.stableKey) { mutableStateOf(recommended) }
    var librarySheet by remember { mutableStateOf(false) }
    var trailerSheet by remember(media.stableKey) { mutableStateOf(false) }
    var trailerKey by remember(media.stableKey) { mutableStateOf<String?>(null) }
    var trailerLoading by remember(media.stableKey) { mutableStateOf(false) }
    var trailerAttempt by remember(media.stableKey) { mutableStateOf(0) }
    var selectedSeason by remember(media.stableKey) { mutableStateOf<Int?>(null) }
    var selectedPerson by remember { mutableStateOf<PersonCard?>(null) }
    var showFullCast by remember(media.stableKey) { mutableStateOf(false) }
    var pendingPreviousEpisodes by remember(media.stableKey) {
        mutableStateOf<Pair<EpisodeCard, List<EpisodeCard>>?>(null)
    }
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
    LaunchedEffect(media.stableKey) {
        val loadedDetail = viewModel.loadDetails(media)
        detail = loadedDetail.copy(
            status = media.status,
            watched = media.watched,
            libraryUpdatedAt = media.libraryUpdatedAt,
        )
        coroutineScope {
            launch { ratings = viewModel.loadRatings(loadedDetail).ifEmpty { ratings } }
            launch { detailPeople = viewModel.loadCast(loadedDetail).ifEmpty { detailPeople } }
            launch {
                if (loadedDetail.type == MediaType.TV) {
                    detailEpisodes = mergeWatchedEpisodes(viewModel.loadAllEpisodes(loadedDetail), episodes, history)
                }
            }
            launch { collectionItems = viewModel.loadCollection(loadedDetail) }
            launch { moreLikeThis = viewModel.loadRecommendations(loadedDetail).ifEmpty { recommended } }
        }
    }
    LaunchedEffect(episodes, history) {
        if (detail.type == MediaType.TV) {
            detailEpisodes = mergeWatchedEpisodes(
                viewModel.cachedEpisodes(detail.id).ifEmpty { detailEpisodes },
                episodes,
                history,
            )
        }
    }
    val detailGlassState = rememberDetailGlassState()
    AdaptiveBackground(
        artworkUrl = detail.posterUrl ?: detail.backdropUrl,
        hazeState = detailGlassState,
        blurBackdrop = true,
    ) {
        val detailListState = rememberLazyListState()
        LazyColumn(state = detailListState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 0.dp)) {
            item { DetailHero(detail, onBack) }
            item(key = "detail_identity") {
                DetailSectionSurface(first = true) {
                    DetailIdentity(detail, detailEpisodes)
                }
            }
            if (ratings.isNotEmpty()) {
                item(key = "detail_ratings") {
                    DetailSectionSurface(bottomGap = if (detail.overview.isNotBlank()) DetailLayout.RatingsGap else DetailLayout.SectionGap) {
                        RatingsSection(ratings)
                    }
                }
            }
            if (detail.overview.isNotBlank()) {
                item(key = "detail_overview") {
                    DetailSectionSurface {
                        GlassTextSection(stringResource(R.string.overview), detail.overview)
                    }
                }
            }
            item(key = "detail_actions") {
                DetailSectionSurface {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            Modifier.padding(horizontal = DetailLayout.Gutter).fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TrailerActionButton { trailerSheet = true }
                            val completed = detail.status == LibraryStatus.COMPLETED || detail.watched
                            PrimaryAction(
                                text = stringResource(if (completed) R.string.watched else R.string.mark_watched),
                                icon = Icons.Filled.Check,
                                modifier = Modifier.weight(1f),
                                containerColor = if (completed) Success else com.cinetrack.ui.theme.SurfacePalette.NeutralControl,
                                compact = true,
                                liveGlass = true,
                                hazeState = detailGlassState,
                            ) {
                                val target = if (completed) {
                                    if (detail.type == MediaType.TV) LibraryStatus.WATCHING else LibraryStatus.PLAN_TO_WATCH
                                } else LibraryStatus.COMPLETED
                                detail = detail.copy(status = target, watched = target == LibraryStatus.COMPLETED)
                                onStatus(detail, target)
                            }
                            Box(
                                Modifier.size(48.dp).glass(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Pill))
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
                    }
                }
            }
            if (detail.type == MediaType.TV && detailEpisodes.isNotEmpty()) {
                item(key = "detail_episodes") {
                    DetailSectionSurface {
                        if (detail.type == MediaType.TV && detailEpisodes.isNotEmpty()) {
                            EpisodesSection(
                                detail,
                                detailEpisodes,
                                onEpisode,
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
            if (detailPeople.isNotEmpty()) {
                item(key = "detail_cast") {
                    DetailSectionSurface {
                        if (detailPeople.isNotEmpty()) {
                            CastSection(detailPeople, onViewAll = { showFullCast = true }) { selectedPerson = it }
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
            item(key = "detail_providers") {
                DetailSectionSurface {
                    ProviderSection(detail)
                }
            }
            item(key = "detail_information") {
                DetailSectionSurface {
                    UsefulInfoSection(detail, detailEpisodes)
                }
            }
            if (moreLikeThis.isNotEmpty()) {
                item(key = "detail_recommendations") {
                    DetailSectionSurface {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (moreLikeThis.isNotEmpty()) {
                                SectionHeader(stringResource(R.string.more_like_this), Modifier.padding(horizontal = DetailLayout.HeadingInset))
                                MediaRail(moreLikeThis.filterNot { it.stableKey == detail.stableKey }, onMedia)
                            }
                        }
                    }
                }
            }
            item(key = "detail_bottom") {
                Box(Modifier.fillMaxWidth().background(GlassMaterial.DetailSurface)
                    .navigationBarsPadding().height(72.dp))
            }
        }
    }
    if (librarySheet) {
        LibraryStatusSheet(detail, onDismiss = { librarySheet = false }) { status ->
            detail = detail.copy(status = status, watched = status == LibraryStatus.COMPLETED)
            onStatus(detail, status)
            librarySheet = false
        }
    }
    if (trailerSheet) {
        LaunchedEffect(detail.stableKey, trailerAttempt) {
            trailerLoading = true
            trailerKey = viewModel.loadTrailerKey(detail)
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
        SeasonInfoSheet(detail, number, viewModel, onDismiss = { selectedSeason = null }) { person ->
            selectedSeason = null
            selectedPerson = person
        }
    }
    selectedPerson?.let { person ->
        ActorSheet(person, viewModel, onDismiss = { selectedPerson = null }, onMedia = onMedia)
    }
    if (showFullCast) {
        FullCastSheet(detailPeople, onDismiss = { showFullCast = false }) { person ->
            showFullCast = false
            selectedPerson = person
        }
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
            .glass(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Pill))
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
private fun DetailHero(media: MediaCard, onBack: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(438.dp)) {
        // AdaptiveBackground is the single artwork-derived base layer. Keeping
        // the hero transparent avoids a second tinted seam above the sheet.
        GlassBackButton(
            onClick = onBack,
            modifier = Modifier.statusBarsPadding().padding(start = com.cinetrack.ui.theme.Spacing.lg, top = com.cinetrack.ui.theme.Spacing.sm),
        )
        Box(
            Modifier.align(Alignment.Center).padding(top = 60.dp).width(214.dp).aspectRatio(com.cinetrack.ui.theme.PosterAspectRatio)
                .shadow(18.dp, RoundedCornerShape(com.cinetrack.ui.theme.Radius.Large), clip = false)
                .clip(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Large)).background(Brush.linearGradient(listOf(Accent, com.cinetrack.ui.theme.SurfacePalette.BlueSurface)))
        ) {
                if (!media.posterUrl.isNullOrBlank()) AsyncImage(media.posterUrl, media.title, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        }
    }
}

@Composable
private fun DetailIdentity(media: MediaCard, episodes: List<EpisodeCard>) {
    val seasonCount = remember(media.stableKey, episodes) {
        (media.seasons.map { it.number } + episodes.map { it.season }).filter { it > 0 }.distinct().size
    }
    val episodeCount = remember(media.stableKey, episodes) {
        media.seasons.sumOf { it.episodeCount }.takeIf { it > 0 } ?: episodes.size
    }
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
        if (media.status != LibraryStatus.NONE || !media.tmdbStatus.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (media.status != LibraryStatus.NONE) {
                    val statusColor = libraryStatusColor(media.status)
                    Row(
                        Modifier.clip(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Pill)).background(statusColor.copy(alpha = .15f)).padding(horizontal = com.cinetrack.ui.theme.Spacing.md, vertical = com.cinetrack.ui.theme.Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(statusColor))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            detailLibraryActionLabel(media.status).uppercase(),
                            color = Color.White,
                            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                }
                media.tmdbStatus?.takeIf(String::isNotBlank)?.let { status ->
                    Row(
                        Modifier.clip(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Pill)).background(Accent.copy(alpha = .15f)).padding(horizontal = com.cinetrack.ui.theme.Spacing.md, vertical = com.cinetrack.ui.theme.Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(AccentLight))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "TMDB · ${tmdbStatusLabel(status)}".uppercase(),
                            color = Color.White,
                            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(
            if (media.type == MediaType.TV) {
                listOfNotNull(
                    media.year.takeIf(String::isNotBlank),
                    seasonCount.takeIf { it > 0 }?.let { androidx.compose.ui.res.pluralStringResource(R.plurals.season_count, it, it) },
                    episodeCount.takeIf { it > 0 }?.let { androidx.compose.ui.res.pluralStringResource(R.plurals.episode_count, it, it) },
                ).joinToString(" · ")
            } else {
                listOfNotNull(
                    media.year.takeIf(String::isNotBlank),
                    formatDurationMinutes(media.runtimeMinutes).takeIf(String::isNotBlank),
                ).joinToString(" · ")
            },
            color = TextSecondary,
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
        )
        if (media.genres.isNotEmpty()) {
            Spacer(Modifier.height(5.dp))
            Text(
                media.genres.joinToString(" · "),
                color = TextPrimary,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun GlassTextSection(title: String, body: String) {
    Column(Modifier.padding(horizontal = DetailLayout.Gutter).fillMaxWidth().glass().padding(com.cinetrack.ui.theme.Spacing.md)) {
        SectionHeader(title)
        Spacer(Modifier.height(8.dp))
        Text(body, color = TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, lineHeight = 19.sp)
    }
}

@Composable
private fun RatingsSection(ratings: List<RatingScore>) {
    Row(
        Modifier.padding(horizontal = DetailLayout.Gutter).fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        ratings.take(4).forEach { rating ->
            Column(
                Modifier.weight(1f).glass(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Small)).padding(horizontal = com.cinetrack.ui.theme.Spacing.xs, vertical = com.cinetrack.ui.theme.Spacing.sm),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(rating.source, color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(rating.score, color = TextPrimary, fontWeight = FontWeight.ExtraBold, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun ProviderSection(media: MediaCard) {
    val uriHandler = LocalUriHandler.current
    Column(Modifier.padding(horizontal = DetailLayout.Gutter).fillMaxWidth().glass().padding(com.cinetrack.ui.theme.Spacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.PlayArrow, null, tint = AccentLight, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
            SectionHeader(stringResource(R.string.where_to_watch), Modifier.weight(1f))
        }
        val offers = com.cinetrack.domain.visibleProviderOffers(media)
        if (offers.values.all { it.isEmpty() }) {
            Text(stringResource(if (media.providerAvailabilityExists) R.string.no_matching_watch_providers else R.string.no_watch_providers), color = TextSecondary,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 12.dp))
        }
        listOf("flatrate" to R.string.subscription, "rent" to R.string.rent, "buy" to R.string.buy,
            "free" to R.string.provider_free, "ads" to R.string.provider_ads).forEach { (type, label) ->
            ProviderCategory(stringResource(label), offers[type].orEmpty(), media.providerLogos)
        }
        media.providerLink?.takeIf(String::isNotBlank)?.let { link ->
            Spacer(Modifier.height(9.dp))
            Text(
                stringResource(R.string.open_provider_options),
                color = AccentLight,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { runCatching { uriHandler.openUri(link) } },
            )
        }
    }
}

@Composable
private fun ProviderCategory(label: String, providers: List<String>, logos: Map<String, String>) {
    if (providers.isEmpty()) return
    Text(label, color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = com.cinetrack.ui.theme.Spacing.lg, bottom = com.cinetrack.ui.theme.Spacing.sm))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(providers, key = { it }) { provider ->
                Row(
                    Modifier.glass(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Pill)).padding(horizontal = com.cinetrack.ui.theme.Spacing.sm, vertical = com.cinetrack.ui.theme.Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val logo = logos[provider]
                    Box(Modifier.size(24.dp).clip(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Compact)).background(Accent.copy(alpha = .26f)), contentAlignment = Alignment.Center) {
                        if (!logo.isNullOrBlank()) AsyncImage(logo, provider, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                        else Text(provider.take(1), color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
                    }
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
    onEpisode: (EpisodeCard) -> Unit,
    initialSeason: Int?,
    initialEpisode: Int?,
    onEpisodeWatched: (EpisodeCard, Boolean) -> Unit,
    onSeasonWatched: (List<EpisodeCard>, Boolean) -> Unit,
    onSeasonInfo: (Int) -> Unit,
) {
    val grouped = episodes.groupBy(EpisodeCard::season).toSortedMap()
    val watchingSeason = remember(media.status, episodes) {
        if (media.status != LibraryStatus.WATCHING) null
        else {
            val ordered = episodes.filter { it.season > 0 }
                .sortedWith(compareBy(EpisodeCard::season, EpisodeCard::number))
            val lastWatched = ordered.lastOrNull(EpisodeCard::watched)
            ordered.firstOrNull { candidate ->
                !candidate.watched && (
                    lastWatched == null || candidate.season > lastWatched.season ||
                        (candidate.season == lastWatched.season && candidate.number > lastWatched.number)
                    )
            }?.season ?: lastWatched?.season ?: ordered.firstOrNull()?.season
        }
    }
    var expandedSeason by rememberSaveable(media.stableKey, media.status) {
        mutableStateOf<Int?>(
            initialSeason?.takeIf { it in grouped }
                ?: watchingSeason?.takeIf { it in grouped },
        )
    }
    var expandedInfo by remember(media.stableKey) { mutableStateOf<Pair<Int, Int>?>(null) }
    LaunchedEffect(initialSeason, grouped.keys) {
        if (initialSeason != null && grouped.containsKey(initialSeason)) expandedSeason = initialSeason
    }
    Column(Modifier.padding(horizontal = DetailLayout.Gutter).fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = DetailLayout.Inner), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Tv, null, tint = AccentLight, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
            SectionHeader(stringResource(R.string.seasons_episodes), Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            grouped.forEach { (seasonNumber, seasonEpisodes) ->
                val expanded = expandedSeason == seasonNumber
                val seasonInteraction = remember(media.stableKey, seasonNumber) { MutableInteractionSource() }
                val seasonPressed by seasonInteraction.collectIsPressedAsState()
                val watched = seasonEpisodes.count(EpisodeCard::watched)
                val allWatched = watched == seasonEpisodes.size && seasonEpisodes.isNotEmpty()
                val summary = media.seasons.firstOrNull { it.number == seasonNumber }
                val expandAction = rememberUiAction { expandedSeason = if (expanded) null else seasonNumber }
                val seasonWatchedAction = rememberUiAction { onSeasonWatched(seasonEpisodes, !allWatched) }
                Column(
                    Modifier.fillMaxWidth().glass(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Medium))
                        .border(if (seasonPressed) 1.6.dp else 0.dp, Accent.copy(alpha = if (seasonPressed) .9f else 0f), RoundedCornerShape(com.cinetrack.ui.theme.Radius.Medium)),
                ) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 114.dp)
                            .clickable(
                                interactionSource = seasonInteraction,
                                indication = null,
                            ) { expandAction() }
                            .padding(com.cinetrack.ui.theme.Spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.width(62.dp).height(92.dp).clip(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Small)).background(Brush.linearGradient(listOf(Info.copy(alpha = .36f), com.cinetrack.ui.theme.SurfacePalette.ArtworkBackdrop)))) {
                            summary?.posterUrl?.let { AsyncImage(it, summary.title, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
                        }
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text(summary?.title ?: stringResource(R.string.season_number, seasonNumber), color = TextPrimary, fontWeight = FontWeight.ExtraBold, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                            Text(stringResource(R.string.season_progress, watched, summary?.episodeCount ?: seasonEpisodes.size), color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            Box(Modifier.size(48.dp).clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = rememberUiAction { onSeasonInfo(seasonNumber) })
                                .padding(10.dp).clip(CircleShape).border(.8.dp, Info.copy(alpha = .75f), CircleShape), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Info, stringResource(R.string.show_season_info, seasonNumber), tint = TextSecondary, modifier = Modifier.size(14.dp))
                            }
                        Box(Modifier.size(48.dp).clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = seasonWatchedAction).padding(8.dp).clip(CircleShape).background(if (allWatched) Success.copy(alpha = .28f) else com.cinetrack.ui.theme.SurfacePalette.NeutralControl.copy(alpha = .60f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Check, stringResource(if (allWatched) R.string.mark_unwatched else R.string.mark_watched), tint = if (allWatched) Success else TextSecondary, modifier = Modifier.size(18.dp))
                        }
                        }
                    }
                    val total = (summary?.episodeCount ?: seasonEpisodes.size).coerceAtLeast(1)
                    Box(Modifier.fillMaxWidth().height(4.dp).background(com.cinetrack.ui.theme.SurfacePalette.ProgressTrack)) {
                        Box(Modifier.fillMaxWidth(watched.toFloat() / total).fillMaxSize().background(Brush.horizontalGradient(listOf(AccentLight, Accent))))
                    }
                    AnimatedVisibility(expanded) {
                        Column(Modifier.padding(com.cinetrack.ui.theme.Spacing.sm), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            seasonEpisodes.forEach { episode ->
                                val infoVisible = expandedInfo == (episode.season to episode.number)
                                val returnTarget = episode.season == initialSeason && episode.number == initialEpisode
                                val infoAction = rememberUiAction {
                                    expandedInfo = if (infoVisible) null else episode.season to episode.number
                                }
                                val watchedAction = rememberUiAction { onEpisodeWatched(episode, !episode.watched) }
                                Column(
                                    Modifier.fillMaxWidth().glass(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Small))
                                        .then(
                                            if (returnTarget) Modifier.border(.8.dp, Accent.copy(alpha = .72f), RoundedCornerShape(com.cinetrack.ui.theme.Radius.Small))
                                            else Modifier,
                                        )
                                        .blueEdgeClickable(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Small)) { onEpisode(episode) },
                                ) {
                                Row(Modifier.fillMaxWidth().padding(com.cinetrack.ui.theme.Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.width(66.dp).height(44.dp).clip(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Compact)).background(com.cinetrack.ui.theme.SurfacePalette.ArtworkBackdrop)) {
                                        episode.stillUrl?.let { AsyncImage(it, episode.title, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
                                    }
                                    Spacer(Modifier.width(9.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text("S$seasonNumber E${episode.number}", color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                        Text(episode.title, color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(formatFullDate(episode.airDate), color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                                    }
                                    Box(Modifier.size(48.dp).clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = infoAction).padding(10.dp).clip(CircleShape).border(.8.dp, Info.copy(alpha = .75f), CircleShape), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Filled.Info, stringResource(if (infoVisible) R.string.hide_episode_info else R.string.show_episode_info, episode.title), tint = TextSecondary, modifier = Modifier.size(14.dp))
                                    }
                                    Spacer(Modifier.width(7.dp))
                                    Box(Modifier.size(48.dp).clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = watchedAction).padding(10.dp).clip(CircleShape).background(if (episode.watched) Success else com.cinetrack.ui.theme.SurfacePalette.EpisodeToggle), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Filled.Check, stringResource(if (episode.watched) R.string.mark_unwatched else R.string.mark_watched), tint = if (episode.watched) com.cinetrack.ui.theme.SurfacePalette.WatchedInk else TextSecondary, modifier = Modifier.size(15.dp))
                                    }
                                }
                                AnimatedVisibility(infoVisible) {
                                    Text(episode.overview.ifBlank { stringResource(R.string.overview) }, color = TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, lineHeight = 16.sp, modifier = Modifier.padding(start = 75.dp, end = com.cinetrack.ui.theme.Spacing.md, bottom = com.cinetrack.ui.theme.Spacing.sm))
                                }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Sections are lazy, while the backdrop blur is rendered once at viewport size. */
@Composable
private fun DetailSectionSurface(
    first: Boolean = false,
    bottomGap: androidx.compose.ui.unit.Dp = DetailLayout.SectionGap,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(
        topStart = if (first) com.cinetrack.ui.theme.Radius.TallSheet else 0.dp,
        topEnd = if (first) com.cinetrack.ui.theme.Radius.TallSheet else 0.dp,
    )
    Box(Modifier.fillMaxWidth()
        .background(GlassMaterial.DetailSurface, shape)
        .padding(top = if (first) 32.dp else 0.dp, bottom = bottomGap)) { content() }
}

private object DetailLayout {
    val Gutter = 20.dp
    val Inner = 12.dp
    val HeadingInset = Gutter + Inner
    val SectionGap = 24.dp
    val RatingsGap = 12.dp
    val CastWidth = 132.dp
    val CastPhotoHeight = 160.dp
}

@Composable
private fun CastPersonCard(person: PersonCard, onPerson: (PersonCard) -> Unit) {
    val shape = RoundedCornerShape(com.cinetrack.ui.theme.Radius.Medium)
    Column(
        Modifier.width(DetailLayout.CastWidth).glass(shape)
            .blueEdgeClickable(shape) { onPerson(person) },
    ) {
        Box(Modifier.fillMaxWidth().height(DetailLayout.CastPhotoHeight)
            .background(com.cinetrack.ui.theme.GlassBare), contentAlignment = Alignment.Center) {
            if (!person.profileUrl.isNullOrBlank()) {
                AsyncImage(person.profileUrl, person.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Icon(Icons.Filled.Person, null, tint = TextSecondary, modifier = Modifier.size(48.dp))
            }
        }
        Column(Modifier.fillMaxWidth().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            // Fixed line slots give every card the same height, also at larger font scales.
            Text(person.name, color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(4.dp))
            Text(person.role, color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun CastSection(people: List<PersonCard>, onViewAll: () -> Unit, onPerson: (PersonCard) -> Unit) {
    Column {
        SectionHeader(stringResource(R.string.cast_and_crew), Modifier.padding(horizontal = DetailLayout.HeadingInset), stringResource(R.string.see_all), onViewAll)
        Spacer(Modifier.height(12.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = DetailLayout.Gutter), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(people.take(18), key = PersonCard::id) { person ->
                CastPersonCard(person, onPerson)
            }
        }
    }
}

@Composable
private fun SeasonInfoSheet(
    show: MediaCard,
    number: Int,
    viewModel: CineTrackViewModel,
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
        viewModel.loadSeasonDetails(show, number).fold(
            onSuccess = { details = it },
            onFailure = { failed = true },
        )
        loading = false
    }
    SharedGlassSheet(onDismiss) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 600.dp),
            contentPadding = PaddingValues(start = DetailLayout.Gutter, end = DetailLayout.Gutter, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                SectionHeader(details?.title?.takeIf(String::isNotBlank) ?: stringResource(R.string.season_number, number))
                Text(show.title, color = TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
            }
            if (loading) item {
                Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.CircularProgressIndicator(color = AccentLight)
                }
            }
            if (failed) item {
                Text(stringResource(R.string.season_info_error), color = TextSecondary)
                androidx.compose.material3.TextButton(onClick = { attempt++ }) { Text(stringResource(R.string.retry), color = TextPrimary) }
            }
            details?.let { season ->
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        season.posterUrl?.let { url ->
                            AsyncImage(url, season.title, Modifier.width(80.dp).height(120.dp).clip(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Small)), contentScale = ContentScale.Crop)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            InfoCell(Icons.Filled.Star, "TMDB", season.score?.let { "%.1f / 10".format(it) } ?: "—")
                            InfoCell(Icons.Filled.CalendarMonth, stringResource(R.string.release_date), formatFullDate(season.airDate), maxLines = 2)
                        }
                    }
                }
                item {
                    SectionHeader(stringResource(R.string.overview))
                    Spacer(Modifier.height(8.dp))
                    Text(season.overview.ifBlank { stringResource(R.string.season_no_description) }, color = TextSecondary,
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                }
                item {
                    SectionHeader(stringResource(R.string.useful_information))
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        InfoCell(Icons.Filled.Tv, stringResource(R.string.episodes), season.episodeCount.toString(), Modifier.weight(1f))
                        InfoCell(Icons.Filled.Schedule, stringResource(R.string.average_runtime), formatDurationMinutes(season.runtimeMinutes), Modifier.weight(1f), maxLines = 2)
                    }
                }
                item { SectionHeader(stringResource(R.string.season_cast)) }
                if (season.cast.isEmpty()) item { Text(stringResource(R.string.season_no_cast), color = TextSecondary) }
                else item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(season.cast, key = PersonCard::id) { CastPersonCard(it, onPerson) }
                    }
                }
            }
        }
    }
}

@Composable
private fun FullCastSheet(people: List<PersonCard>, onDismiss: () -> Unit, onPerson: (PersonCard) -> Unit) {
    SharedGlassSheet(onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = DetailLayout.Gutter)) {
            SectionHeader(stringResource(R.string.full_cast), Modifier.padding(horizontal = DetailLayout.Inner))
            Spacer(Modifier.height(12.dp))
            LazyVerticalGrid(
                columns = GridCells.Adaptive(DetailLayout.CastWidth),
                modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 20.dp),
            ) {
                gridItems(people, key = PersonCard::id) { person ->
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                        CastPersonCard(person, onPerson)
                    }
                }
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
    Column(Modifier.padding(horizontal = DetailLayout.Gutter).fillMaxWidth().glass().padding(vertical = com.cinetrack.ui.theme.Spacing.lg)) {
        Row(Modifier.padding(horizontal = DetailLayout.Inner), verticalAlignment = Alignment.CenterVertically) {
            SectionHeader(stringResource(R.string.collections_related), Modifier.weight(1f))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.width(4.dp).height(34.dp).clip(CircleShape).background(Gold))
        }
        Spacer(Modifier.height(12.dp))
        LazyRow(
            state = collectionState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(collection, key = MediaCard::stableKey) { item ->
                Column {
                    MediaPoster(
                        item,
                        width = 104.dp,
                        selectedBorder = Gold.takeIf { item.stableKey == current.stableKey },
                        onClick = { onMedia(item) },
                    )
                    Text(
                        stringResource(R.string.movie_n_of_n, collection.indexOf(item) + 1, collection.size),
                        color = if (item.stableKey == current.stableKey) Gold else TextMuted,
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
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

@Composable
private fun UsefulInfoSection(media: MediaCard, episodes: List<com.cinetrack.domain.EpisodeCard>) {
    val runtime = remember(media.id, media.runtimeMinutes, episodes) {
        if (media.type == MediaType.TV) com.cinetrack.domain.averageEpisodeRuntime(media.id, episodes, media.runtimeMinutes) else media.runtimeMinutes
    }
    Column(Modifier.padding(horizontal = DetailLayout.Gutter).fillMaxWidth().glass().padding(com.cinetrack.ui.theme.Spacing.md)) {
        SectionHeader(stringResource(R.string.useful_information))
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth()) {
            InfoCell(
                if (media.type == MediaType.TV) Icons.Filled.Tv else Icons.Filled.Movie,
                stringResource(R.string.status),
                media.tmdbStatus?.takeIf(String::isNotBlank)?.let { tmdbStatusLabel(it) }.orEmpty(),
                Modifier.weight(1f),
            )
            InfoCell(Icons.Filled.CalendarMonth, stringResource(R.string.release_date), formatFullDate(media.releaseDate), Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth()) {
            InfoCell(Icons.Filled.Schedule, stringResource(if (media.type == MediaType.TV) R.string.average_runtime else R.string.runtime), formatDurationMinutes(runtime), Modifier.weight(1f))
            InfoCell(Icons.Filled.Star, stringResource(R.string.rating), media.score?.let { "%.1f / 10".format(it) }.orEmpty(), Modifier.weight(1f))
        }
        if (media.type == MediaType.TV && media.seasons.isNotEmpty()) {
            val regularSeasons = media.seasons.filter { it.number > 0 }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth()) {
                InfoCell(Icons.Filled.Tv, stringResource(R.string.seasons), regularSeasons.size.toString(), Modifier.weight(1f))
                InfoCell(
                    Icons.Filled.Info,
                    stringResource(R.string.episodes),
                    regularSeasons.sumOf { it.episodeCount }.toString(),
                    Modifier.weight(1f),
                )
            }
        }
        if (media.networks.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            InfoCell(
                Icons.Filled.Tv,
                stringResource(R.string.networks),
                media.networks.joinToString(" · "),
                Modifier.fillMaxWidth(),
                maxLines = 2,
            )
        }
        if (media.type == MediaType.MOVIE && (media.budget != null || media.boxOffice != null)) {
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth()) {
                InfoCell(Icons.Filled.AttachMoney, stringResource(R.string.budget), formatUsd(media.budget), Modifier.weight(1f))
                InfoCell(Icons.Filled.AttachMoney, stringResource(R.string.box_office), formatUsd(media.boxOffice), Modifier.weight(1f))
            }
        }
        if (media.productionCountries.isNotEmpty() || !media.originalLanguage.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth()) {
                InfoCell(
                    Icons.Filled.Public,
                    stringResource(R.string.production_countries),
                    media.productionCountries.joinToString(" · "),
                    Modifier.weight(1f),
                    maxLines = 2,
                )
                InfoCell(
                    Icons.Filled.Language,
                    stringResource(R.string.original_language),
                    media.originalLanguage.orEmpty(),
                    Modifier.weight(1f),
                )
            }
        }
        if (media.productionCompanies.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            InfoCell(
                Icons.Filled.Business,
                stringResource(R.string.production_companies),
                media.productionCompanies.joinToString(" · "),
                Modifier.fillMaxWidth(),
                maxLines = 2,
            )
        }
        if (media.genres.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            InfoCell(
                Icons.Filled.Movie,
                stringResource(R.string.genres),
                media.genres.joinToString(" · "),
                Modifier.fillMaxWidth(),
                maxLines = 2,
            )
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
private fun InfoCell(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
) {
    Row(modifier.heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Small)).background(Accent.copy(alpha = .18f)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = AccentLight, modifier = Modifier.size(18.dp)) }
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) { Text(label, color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelSmall); Text(value.ifBlank { "—" }, color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, maxLines = maxLines, overflow = TextOverflow.Ellipsis) }
    }
}

@Composable
private fun ActorSheet(person: PersonCard, viewModel: CineTrackViewModel, onDismiss: () -> Unit, onMedia: (MediaCard) -> Unit) {
    var details by remember(person.id) { mutableStateOf(person) }
    var biographyExpanded by remember(person.id) { mutableStateOf(false) }
    var biographyOverflowing by remember(person.id) { mutableStateOf(false) }
    LaunchedEffect(person.id) { details = viewModel.loadPerson(person) }
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
                            .blueEdgeClickable(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Small)) { onDismiss(); onMedia(movie) }.padding(com.cinetrack.ui.theme.Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
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
    onBack: () -> Unit,
    onSeries: () -> Unit,
    onMedia: (MediaCard) -> Unit,
    onWatched: (EpisodeCard, Boolean) -> Unit,
) {
    var loadedEpisode by remember(show?.id) { mutableStateOf(episode) }
    var loadedEpisodes by remember(show?.id) { mutableStateOf(allEpisodes) }
    var loadedPeople by remember(show?.id) { mutableStateOf(people) }
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
            loadedEpisode = viewModel.loadEpisode(it, requestedSeason, requestedNumber)
                ?: loadedEpisode
                ?: EpisodeCard(
                    id = -1,
                    showId = it.id,
                    season = requestedSeason,
                    number = requestedNumber,
                    title = "S${requestedSeason.toString().padStart(2, '0')} E${requestedNumber.toString().padStart(2, '0')}",
                    overview = "",
                    airDate = null,
                )
            val seasonEpisodes = viewModel.loadEpisodes(it, requestedSeason)
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
    val currentIndex = orderedEpisodes.indexOfFirst {
        it.season == currentEpisode.season && it.number == currentEpisode.number
    }
    val previous = orderedEpisodes.getOrNull(currentIndex - 1)
    val next = orderedEpisodes.getOrNull(currentIndex + 1)
    val pagerState = rememberPagerState(initialPage = 1, pageCount = { 3 })
    val pagerScope = rememberCoroutineScope()
    LaunchedEffect(show?.stableKey, currentEpisode.season, currentEpisode.number) {
        show?.let {
            viewModel.loadEpisodeCast(it, currentEpisode.season, currentEpisode.number)
                .takeIf { cast -> cast.isNotEmpty() }
                ?.let { cast -> loadedPeople = cast }
        }
    }
    LaunchedEffect(pagerState, orderedEpisodes) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                if (page == 1) return@collect
                val active = loadedEpisode ?: return@collect
                val activeIndex = orderedEpisodes.indexOfFirst {
                    it.season == active.season && it.number == active.number
                }
                val target = if (page < 1) {
                    orderedEpisodes.getOrNull(activeIndex - 1)
                } else {
                    orderedEpisodes.getOrNull(activeIndex + 1)
                }
                if (target == null) {
                    pagerState.animateScrollToPage(1)
                    return@collect
                }
                // The new current page and the pager reset are applied before
                // the next frame, so page 0/2 becomes page 1 without a visual jump.
                loadedEpisode = target
                pagerState.scrollToPage(1)
            }
    }
    var selectedPerson by remember { mutableStateOf<PersonCard?>(null) }
    var showFullCast by remember(show?.stableKey) { mutableStateOf(false) }
    val detailGlassState = rememberDetailGlassState()
    AdaptiveBackground(
        artworkUrl = currentEpisode.stillUrl ?: show?.backdropUrl ?: show?.posterUrl,
        hazeState = detailGlassState,
        blurBackdrop = true,
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 0,
            key = { it },
        ) { page ->
            val displayedEpisode = when (page) {
                0 -> previous ?: currentEpisode
                2 -> next ?: currentEpisode
                else -> currentEpisode
            }
            var watched by remember(displayedEpisode.season, displayedEpisode.number, displayedEpisode.watched) {
                mutableStateOf(displayedEpisode.watched)
            }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 0.dp),
            ) {
                item {
                    Box(Modifier.fillMaxWidth().statusBarsPadding().height(330.dp)) {
                        Box(Modifier.fillMaxWidth().padding(start = com.cinetrack.ui.theme.Spacing.xl, end = com.cinetrack.ui.theme.Spacing.xl, top = 74.dp, bottom = 46.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Large)).background(Brush.linearGradient(listOf(com.cinetrack.ui.theme.SurfacePalette.EpisodeTeal, com.cinetrack.ui.theme.SurfacePalette.EpisodeSurface)))) {
                            if (!displayedEpisode.stillUrl.isNullOrBlank()) AsyncImage(displayedEpisode.stillUrl, displayedEpisode.title, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .54f)))))
                        }
                        GlassBackButton(onClick = onBack, modifier = Modifier.padding(start = com.cinetrack.ui.theme.Spacing.lg, top = 2.dp))
                    }
                }
                item {
                    val sheetShape = RoundedCornerShape(topStart = com.cinetrack.ui.theme.Radius.TallSheet, topEnd = com.cinetrack.ui.theme.Radius.TallSheet)
                    Column(
                        Modifier.fillMaxWidth()
                            .background(GlassMaterial.DetailSurface, sheetShape)
                            .navigationBarsPadding()
                            .padding(top = com.cinetrack.ui.theme.Spacing.xxxl, bottom = 72.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        Column(Modifier.padding(horizontal = DetailLayout.HeadingInset)) {
                            Text(show?.title.orEmpty().uppercase(), color = AccentLight, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, letterSpacing = .7.sp, fontWeight = FontWeight.ExtraBold)
                            Text(displayedEpisode.title, color = Color.White, style = androidx.compose.material3.MaterialTheme.typography.displaySmall, lineHeight = 31.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text("${displayedEpisode.label} · ${formatDurationMinutes(displayedEpisode.runtimeMinutes)}", color = TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                        }
                        Column(Modifier.fillMaxWidth().padding(horizontal = DetailLayout.Gutter)) {
                            OutlinedButton(onClick = onSeries, modifier = Modifier.height(40.dp), shape = RoundedCornerShape(com.cinetrack.ui.theme.Radius.Small), border = BorderStroke(.7.dp, com.cinetrack.ui.theme.GlassStrokeStrong)) {
                                Icon(Icons.Filled.Info, null, tint = TextSecondary, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(7.dp))
                                Text(stringResource(R.string.series_details), color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, maxLines = 1)
                            }
                        }
                        GlassTextSection(stringResource(R.string.overview), displayedEpisode.overview)
                        PrimaryAction(
                            if (watched) stringResource(R.string.watched) else stringResource(R.string.mark_watched),
                            Icons.Filled.Check,
                            Modifier.fillMaxWidth().padding(horizontal = DetailLayout.Gutter),
                            containerColor = if (watched) Success else com.cinetrack.ui.theme.SurfacePalette.NeutralControl,
                            liveGlass = true,
                            hazeState = detailGlassState,
                        ) {
                            val newWatched = !watched
                            watched = newWatched
                            onWatched(displayedEpisode.copy(watched = newWatched), newWatched)
                        }
                        if (loadedPeople.isNotEmpty()) CastSection(loadedPeople, onViewAll = { showFullCast = true }) { selectedPerson = it }
                        Column(Modifier.padding(horizontal = DetailLayout.Gutter).fillMaxWidth().glass().padding(com.cinetrack.ui.theme.Spacing.md)) {
                            SectionHeader(stringResource(R.string.useful_information))
                            Spacer(Modifier.height(12.dp))
                            Row {
                                InfoCell(Icons.Filled.CalendarMonth, stringResource(R.string.release_date), formatFullDate(displayedEpisode.airDate), Modifier.weight(1f))
                                InfoCell(Icons.Filled.Visibility, stringResource(R.string.status), if (watched) stringResource(R.string.watched) else stringResource(R.string.not_watched), Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(12.dp))
                            Row {
                                InfoCell(Icons.Filled.Schedule, stringResource(R.string.runtime), formatDurationMinutes(displayedEpisode.runtimeMinutes), Modifier.weight(1f))
                                InfoCell(Icons.Filled.Star, stringResource(R.string.rating), show?.score?.let { "%.1f / 10".format(it) } ?: "—", Modifier.weight(1f))
                            }
                        }
                        Row(Modifier.fillMaxWidth().padding(horizontal = DetailLayout.Gutter), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = { pagerScope.launch { pagerState.animateScrollToPage(0) } }, enabled = previous != null, modifier = Modifier.weight(1f).height(44.dp), shape = RoundedCornerShape(com.cinetrack.ui.theme.Radius.Pill)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(5.dp)); Text(stringResource(R.string.previous_episode), style = androidx.compose.material3.MaterialTheme.typography.labelSmall, maxLines = 1) }
                            OutlinedButton(onClick = { pagerScope.launch { pagerState.animateScrollToPage(2) } }, enabled = next != null, modifier = Modifier.weight(1f).height(44.dp), shape = RoundedCornerShape(com.cinetrack.ui.theme.Radius.Pill)) { Text(stringResource(R.string.next_episode), style = androidx.compose.material3.MaterialTheme.typography.labelSmall, maxLines = 1); Spacer(Modifier.width(5.dp)); Icon(Icons.AutoMirrored.Filled.ArrowForward, null, modifier = Modifier.size(17.dp)) }
                        }
                    }
                }
            }
        }
    }
    selectedPerson?.let { person ->
        ActorSheet(person, viewModel, onDismiss = { selectedPerson = null }, onMedia = onMedia)
    }
    if (showFullCast) {
        FullCastSheet(loadedPeople, onDismiss = { showFullCast = false }) { person ->
            showFullCast = false
            selectedPerson = person
        }
    }
}
