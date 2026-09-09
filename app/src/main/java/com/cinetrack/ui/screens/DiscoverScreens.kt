@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.cinetrack.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cinetrack.R
import com.cinetrack.domain.AppUiState
import com.cinetrack.domain.MediaCard
import com.cinetrack.domain.MediaType
import com.cinetrack.domain.RailIds
import com.cinetrack.ui.components.AdaptiveBackground
import com.cinetrack.ui.components.GlassBackButton
import com.cinetrack.ui.components.MediaPoster
import com.cinetrack.ui.components.MediaRail
import com.cinetrack.ui.components.LibraryStatusSheet
import com.cinetrack.ui.components.MediaStatusPopup
import com.cinetrack.ui.components.PageTitle
import com.cinetrack.ui.components.PrimaryAction
import com.cinetrack.ui.components.SectionHeader
import com.cinetrack.ui.components.libraryStatusIcon
import com.cinetrack.ui.components.libraryStatusColor
import com.cinetrack.ui.components.glass
import com.cinetrack.ui.components.glassIcon
import com.cinetrack.ui.components.rememberUiAction
import com.cinetrack.ui.theme.AccentLight
import com.cinetrack.ui.theme.TextMuted
import com.cinetrack.ui.theme.TextPrimary
import com.cinetrack.ui.theme.TextSecondary
import dev.chrisbanes.haze.hazeSource
import com.cinetrack.ui.components.liveActionGlass
import com.cinetrack.ui.components.rememberDetailGlassState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import kotlinx.coroutines.delay

@Composable
fun DiscoverScreen(
    state: AppUiState,
    onRefresh: () -> Unit,
    loadTagline: suspend (MediaCard) -> String?,
    onSearch: () -> Unit,
    onFilters: () -> Unit,
    onSeeAll: (String) -> Unit,
    onMedia: (MediaCard) -> Unit,
    onStatus: (MediaCard, com.cinetrack.domain.LibraryStatus) -> Unit,
    onNotInterested: (MediaCard) -> Unit,
    onOpenTmdbSettings: () -> Unit,
    onCompactNav: (Boolean) -> Unit,
) {
    val listState = rememberLazyListState()
    NavCollapseEffect(listState, onCompactNav)
    val heroes = remember(state.rails) {
        (state.rails[RailIds.TRENDING_MOVIES].orEmpty().take(3) + state.rails[RailIds.TRENDING_TV].orEmpty().take(3))
            .distinctBy(MediaCard::stableKey)
            .take(5)
    }
    var heroIndex by remember { mutableStateOf(0) }
    val trendingTvTitle = stringResource(R.string.trending_tv)
    val trendingMoviesTitle = stringResource(R.string.trending_movies)
    val popularTvTitle = stringResource(R.string.popular_tv)
    val popularMoviesTitle = stringResource(R.string.popular_movies)
    val upcomingTitle = stringResource(R.string.upcoming)
    val seeAll = stringResource(R.string.see_all)
    val backgroundHero = heroes.getOrNull(heroIndex) ?: heroes.firstOrNull()
    AdaptiveBackground(artworkUrl = backgroundHero?.backdropUrl ?: backgroundHero?.posterUrl) {
        LongPullRefreshContainer(refreshing = state.refreshing, onRefresh = onRefresh, enabled = state.tmdbApiConfigured) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().statusBarsPadding(),
                contentPadding = PaddingValues(bottom = 112.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                item {
                    PageTitle(stringResource(R.string.discover), Modifier.padding(start = com.cinetrack.ui.theme.Spacing.xl, end = com.cinetrack.ui.theme.Spacing.xl, top = com.cinetrack.ui.theme.Spacing.lg, bottom = com.cinetrack.ui.theme.Spacing.lg))
                }
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(start = com.cinetrack.ui.theme.Spacing.xl, end = com.cinetrack.ui.theme.Spacing.xl, bottom = com.cinetrack.ui.theme.Spacing.xs),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            Modifier.weight(1f).height(48.dp).glass(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Pill)).clickable(onClick = rememberUiAction(onSearch)).padding(horizontal = com.cinetrack.ui.theme.Spacing.lg),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Search, stringResource(R.string.accessibility_search), tint = TextSecondary, modifier = Modifier.size(21.dp))
                            Spacer(Modifier.width(9.dp))
                            Text(stringResource(R.string.search_hint), color = TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = rememberUiAction(onFilters), modifier = Modifier.size(48.dp).glassIcon()) {
                            Icon(Icons.Filled.Tune, stringResource(R.string.filters), tint = TextSecondary, modifier = Modifier.size(17.dp))
                        }
                    }
                }
                if (!state.tmdbApiConfigured) item {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = com.cinetrack.ui.theme.Spacing.xl, vertical = com.cinetrack.ui.theme.Spacing.md)
                            .glass(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Medium))
                            .background(com.cinetrack.ui.theme.SurfacePalette.WarningDark.copy(alpha = .20f), RoundedCornerShape(com.cinetrack.ui.theme.Radius.Medium))
                            .border(.8.dp, com.cinetrack.ui.theme.StatusPlanned.copy(alpha = .45f), RoundedCornerShape(com.cinetrack.ui.theme.Radius.Medium))
                            .padding(com.cinetrack.ui.theme.Spacing.lg),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.WarningAmber, null, tint = com.cinetrack.ui.theme.SurfacePalette.WarningLight, modifier = Modifier.size(23.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(stringResource(R.string.discover_tmdb_warning_title), color = Color.White, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.ExtraBold)
                        }
                        Text(
                            stringResource(R.string.discover_tmdb_warning_message),
                            color = TextSecondary,
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            lineHeight = 17.sp,
                            modifier = Modifier.padding(top = com.cinetrack.ui.theme.Spacing.sm, bottom = com.cinetrack.ui.theme.Spacing.md),
                        )
                        PrimaryAction(
                            text = stringResource(R.string.open_api_settings),
                            icon = Icons.Filled.Settings,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = onOpenTmdbSettings,
                        )
                    }
                }
                if (heroes.isNotEmpty()) item { HeroCarousel(heroes, heroIndex, { heroIndex = it }, onMedia, onStatus, onNotInterested, loadTagline, state.metadataLanguage) }
                if (state.allMedia.isEmpty() && !state.loading) item {
                    Text(
                        state.error ?: stringResource(R.string.no_catalog_data),
                        color = TextSecondary,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = com.cinetrack.ui.theme.Spacing.xl).glass(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Small)).padding(com.cinetrack.ui.theme.Spacing.md),
                    )
                }
                railSection(trendingTvTitle, seeAll, RailIds.TRENDING_TV, state, onSeeAll, onMedia, onStatus, onNotInterested)
                railSection(trendingMoviesTitle, seeAll, RailIds.TRENDING_MOVIES, state, onSeeAll, onMedia, onStatus, onNotInterested)
                railSection(popularTvTitle, seeAll, RailIds.POPULAR_TV, state, onSeeAll, onMedia, onStatus, onNotInterested)
                railSection(popularMoviesTitle, seeAll, RailIds.POPULAR_MOVIES, state, onSeeAll, onMedia, onStatus, onNotInterested)
                railSection(upcomingTitle, seeAll, RailIds.UPCOMING, state, onSeeAll, onMedia, onStatus, onNotInterested)
            }
        }
    }
}

private fun LazyListScope.railSection(
    title: String,
    seeAll: String,
    railId: String,
    state: AppUiState,
    onSeeAll: (String) -> Unit,
    onMedia: (MediaCard) -> Unit,
    onStatus: (MediaCard, com.cinetrack.domain.LibraryStatus) -> Unit,
    onNotInterested: (MediaCard) -> Unit,
) {
    val items = state.rails[railId].orEmpty()
    if (items.isEmpty()) return
    item { SectionHeader(title, Modifier.padding(start = com.cinetrack.ui.theme.Spacing.xl, end = com.cinetrack.ui.theme.Spacing.xl, top = com.cinetrack.ui.theme.Spacing.xl, bottom = com.cinetrack.ui.theme.Spacing.md), seeAll, { onSeeAll(railId) }) }
    item { MediaRail(items, onMedia, showYear = false, showAirDate = railId == RailIds.UPCOMING, onStatus = onStatus, onNotInterested = onNotInterested) }
    item { Spacer(Modifier.height(4.dp)) }
}

@Composable
private fun HeroCarousel(
    items: List<MediaCard>,
    selectedPage: Int,
    onPage: (Int) -> Unit,
    onMedia: (MediaCard) -> Unit,
    onStatus: (MediaCard, com.cinetrack.domain.LibraryStatus) -> Unit,
    onNotInterested: (MediaCard) -> Unit,
    loadTagline: suspend (MediaCard) -> String?,
    metadataLanguage: String,
) {
    val pagerState = rememberPagerState(initialPage = selectedPage, pageCount = { items.size })
    LaunchedEffect(pagerState.currentPage) { onPage(pagerState.currentPage) }
    val dragging by pagerState.interactionSource.collectIsDraggedAsState()
    var interactingCards by remember { mutableStateOf(emptySet<String>()) }
    var touching by remember { mutableStateOf(false) }
    val motionEnabled = remember { android.os.Build.VERSION.SDK_INT < 26 || android.animation.ValueAnimator.areAnimatorsEnabled() }
    LaunchedEffect(items.map { it.stableKey }, dragging, interactingCards, touching) {
        if (items.size > 1 && motionEnabled && !dragging && interactingCards.isEmpty() && !touching) {
            while (true) {
                delay(5_000)
                pagerState.animateScrollToPage((pagerState.currentPage + 1) % items.size)
            }
        }
    }
    Column(Modifier.padding(top = com.cinetrack.ui.theme.Spacing.lg, bottom = com.cinetrack.ui.theme.Spacing.xs)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.pointerInput(Unit) {
                try {
                    awaitPointerEventScope {
                        while (true) touching = awaitPointerEvent(PointerEventPass.Initial).changes.any { it.pressed }
                    }
                } finally { touching = false }
            },
            contentPadding = PaddingValues(horizontal = 20.dp),
            pageSpacing = 10.dp,
            key = { items[it].stableKey },
        ) { page ->
            HeroCard(items[page], onMedia, onStatus, onNotInterested, loadTagline, metadataLanguage) {
                val key = items[page].stableKey
                interactingCards = if (it) interactingCards + key else interactingCards - key
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = com.cinetrack.ui.theme.Spacing.md, bottom = com.cinetrack.ui.theme.Spacing.xs), horizontalArrangement = Arrangement.Center) {
            items.indices.forEach { page ->
                Box(
                    Modifier.width(if (pagerState.currentPage == page) 16.dp else 6.dp).height(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (pagerState.currentPage == page) com.cinetrack.ui.theme.Accent else com.cinetrack.ui.theme.GlassStrokeMedium),
                )
                if (page != items.lastIndex) Spacer(Modifier.width(6.dp))
            }
        }
    }
}

@Composable
internal fun HeroCard(
    media: MediaCard,
    onMedia: (MediaCard) -> Unit,
    onStatus: (MediaCard, com.cinetrack.domain.LibraryStatus) -> Unit,
    onNotInterested: (MediaCard) -> Unit,
    loadTagline: suspend (MediaCard) -> String?,
    metadataLanguage: String,
    preview: Boolean = false,
    previewTagline: String? = null,
    onInteraction: (Boolean) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    var statusPopup by remember(media.stableKey) { mutableStateOf(false) }
    var tagline by remember(media.stableKey, metadataLanguage) { mutableStateOf<String?>(null) }
    LaunchedEffect(media.stableKey, metadataLanguage, preview, previewTagline) {
        tagline = if (preview) previewTagline else loadTagline(media)
    }
    LaunchedEffect(pressed, statusPopup) { onInteraction(pressed || statusPopup) }
    DisposableEffect(Unit) { onDispose { onInteraction(false) } }
    val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale.coerceAtLeast(1f)
    val heroGlassState = rememberDetailGlassState()
    val appearance = com.cinetrack.ui.theme.LocalCardAppearance.current
    val compact = appearance.heroLayout == "landscape"
    val titleSize = if (compact) 20.sp else if (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 380) 26.sp else 30.sp
    BoxWithConstraints(Modifier.fillMaxWidth()) {
    val cardHeight = appearance.heroAspectRatio?.let { ratio ->
        // Preserve the selected ratio unless larger accessibility text needs more room.
        (maxWidth / ratio).coerceAtLeast(((if (compact) 180 else 270) * fontScale).dp)
    } ?: (310 * fontScale).dp
    Box(
        Modifier
            .fillMaxWidth()
            .height(cardHeight)
            .clip(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Large))
            .background(Brush.linearGradient(listOf(com.cinetrack.ui.theme.SurfacePalette.PosterBrown, com.cinetrack.ui.theme.SurfacePalette.SeaSurface, com.cinetrack.ui.theme.SurfacePalette.PosterShadow)))
            .glass(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Large))
            .border(if (pressed || statusPopup) 2.dp else .5.dp, if (pressed || statusPopup) com.cinetrack.ui.theme.Accent else Color.Transparent, RoundedCornerShape(com.cinetrack.ui.theme.Radius.Large))
            .combinedClickable(
                enabled = !preview,
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    onMedia(media)
                },
                onLongClick = {
                    statusPopup = true
                },
            ),
    ) {
        Box(Modifier.matchParentSize().then(if (heroGlassState != null) Modifier.hazeSource(heroGlassState) else Modifier)) {
        if (!media.backdropUrl.isNullOrBlank()) {
            AsyncImage(media.backdropUrl, media.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = .76f), Color.Black.copy(alpha = .34f), Color.Transparent))))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .58f)))))
        }
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(if (compact) 12.dp else com.cinetrack.ui.theme.Spacing.xl)) {
            val type = if (media.type == MediaType.TV) stringResource(R.string.tv_shows) else stringResource(R.string.movies)
            val genre = media.genres.firstOrNull().orEmpty()
            val metadata = listOf(type, genre, media.year).filter(String::isNotBlank).joinToString(" · ").uppercase()
            Text(metadata, maxLines = 1, overflow = TextOverflow.Ellipsis, color = TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, letterSpacing = .6.sp, fontWeight = FontWeight.ExtraBold)
            Text(media.title, color = TextPrimary, fontSize = titleSize, lineHeight = titleSize * 1.12f, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = com.cinetrack.ui.theme.Spacing.xs))
            if (!compact) media.score?.let { Text("★  %.1f".format(it), color = Color.White, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = com.cinetrack.ui.theme.Spacing.xs)) }
            tagline?.takeIf(String::isNotBlank)?.let {
                Text(it, color = TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = com.cinetrack.ui.theme.Spacing.xs))
            }
            Spacer(Modifier.height(if (compact) 6.dp else 14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (compact) {
                    Text(media.score?.let { "★  %.1f".format(it) }.orEmpty(), color = TextPrimary,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f), maxLines = 1)
                }
                Row(
                    Modifier.height(48.dp).liveActionGlass(heroGlassState, media.watched || media.status == com.cinetrack.domain.LibraryStatus.COMPLETED)
                        .clickable(enabled = !preview, onClick = rememberUiAction { statusPopup = true }).padding(horizontal = com.cinetrack.ui.theme.Spacing.lg),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (media.status == com.cinetrack.domain.LibraryStatus.NONE) Icons.Filled.Add else libraryStatusIcon(media.status),
                        null,
                        tint = TextPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        if (media.status == com.cinetrack.domain.LibraryStatus.NONE) stringResource(R.string.add_to_library) else stringResource(when (media.status) {
                            com.cinetrack.domain.LibraryStatus.WATCHING -> R.string.in_progress
                            com.cinetrack.domain.LibraryStatus.PLAN_TO_WATCH -> R.string.plan_to_watch
                            com.cinetrack.domain.LibraryStatus.PAUSED -> R.string.paused
                            com.cinetrack.domain.LibraryStatus.COMPLETED -> R.string.completed
                            com.cinetrack.domain.LibraryStatus.DROPPED -> R.string.dropped
                            else -> R.string.in_library
                        }),
                        color = Color.White,
                        style = if (compact) androidx.compose.material3.MaterialTheme.typography.bodySmall else androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
        }
        MediaStatusPopup(
            expanded = statusPopup,
            currentStatus = media.status,
            onDismiss = { statusPopup = false },
            onStatus = { status -> onStatus(media, status); statusPopup = false },
            onNotInterested = { onNotInterested(media); statusPopup = false },
        )
    }
}
}

@Composable
fun DiscoverListScreen(
    railId: String,
    viewModel: com.cinetrack.ui.CineTrackViewModel,
    state: AppUiState,
    onBack: () -> Unit,
    onMedia: (MediaCard) -> Unit,
    onStatus: (MediaCard, com.cinetrack.domain.LibraryStatus) -> Unit,
    onNotInterested: (MediaCard) -> Unit,
) {
    val title = when (railId) {
        RailIds.TRENDING_TV -> stringResource(R.string.trending_tv)
        RailIds.TRENDING_MOVIES -> stringResource(R.string.trending_movies)
        RailIds.POPULAR_TV -> stringResource(R.string.popular_tv)
        RailIds.POPULAR_MOVIES -> stringResource(R.string.popular_movies)
        else -> stringResource(R.string.upcoming)
    }
    val browseStates by viewModel.discoverBrowse.collectAsStateWithLifecycle()
    val browseKey = com.cinetrack.domain.discoverBrowseKey(railId, state)
    val browse = browseStates[browseKey]
    val localMedia = remember(state.allMedia) { state.allMedia.associateBy(MediaCard::stableKey) }
    val items = (browse?.items ?: state.rails[railId].orEmpty())
        .filterNot { it.stableKey in state.hiddenDiscovery }
        .map { media -> localMedia[media.stableKey]?.let { media.copy(status = it.status, watched = it.watched) } ?: media }
    val gridState = rememberLazyGridState()
    LaunchedEffect(browseKey) { if (browseStates[browseKey] == null) viewModel.loadDiscoverMore(railId) }
    val backgroundItem = items.firstOrNull()
    AdaptiveBackground(artworkUrl = backgroundItem?.backdropUrl ?: backgroundItem?.posterUrl) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = com.cinetrack.ui.theme.Spacing.xl, end = com.cinetrack.ui.theme.Spacing.xl, top = com.cinetrack.ui.theme.Spacing.lg, bottom = com.cinetrack.ui.theme.Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
                GlassBackButton(onClick = onBack)
                PageTitle(title, Modifier.padding(start = com.cinetrack.ui.theme.Spacing.md))
            }
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(com.cinetrack.domain.CardAppearance.gridColumns(state.cardDensity)),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 112.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(items, key = MediaCard::stableKey) { media ->
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        MediaPoster(
                            media,
                            width = maxWidth,
                            showYear = false,
                            onStatus = { onStatus(media, it) },
                            onNotInterested = { onNotInterested(media) },
                            onClick = { onMedia(media) },
                        )
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        when {
                            browse == null || browse.loading -> CircularProgressIndicator(color = com.cinetrack.ui.theme.Accent, modifier = Modifier.size(28.dp))
                            browse.failed -> {
                                Text(stringResource(R.string.discover_more_failed), color = TextSecondary)
                                androidx.compose.material3.TextButton(onClick = { viewModel.loadDiscoverMore(railId) }) { Text(stringResource(R.string.retry)) }
                            }
                            browse.hasMore -> androidx.compose.material3.TextButton(onClick = { viewModel.loadDiscoverMore(railId) }) { Text(stringResource(R.string.discover_load_more)) }
                            else -> Text(stringResource(if (items.isEmpty()) R.string.choice_no_results else R.string.discover_list_end), color = TextSecondary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SearchScreen(
    results: List<MediaCard>,
    sourceItems: List<MediaCard> = emptyList(),
    remoteSearch: Boolean = true,
    history: List<String> = emptyList(),
    onQuery: (String) -> Unit,
    onSubmitQuery: (String) -> Unit,
    onRemoveHistory: (String) -> Unit,
    onLeave: () -> Unit,
    onBack: () -> Unit,
    onMedia: (MediaCard) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val visibleResults = remember(query, results, sourceItems, remoteSearch) {
        if (query.isBlank()) emptyList()
        else if (remoteSearch) results else sourceItems.filter { it.title.contains(query, ignoreCase = true) }
    }
    val backgroundItem = visibleResults.firstOrNull()
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }
    DisposableEffect(Unit) { onDispose(onLeave) }
    AdaptiveBackground(artworkUrl = backgroundItem?.backdropUrl ?: backgroundItem?.posterUrl) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(com.cinetrack.ui.theme.Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                GlassBackButton(onClick = onBack)
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it; if (remoteSearch) onQuery(it) },
                    modifier = Modifier.weight(1f).focusRequester(focusRequester),
                    placeholder = { Text(stringResource(R.string.search_hint)) },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    singleLine = true,
                    shape = RoundedCornerShape(com.cinetrack.ui.theme.Radius.Medium),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentLight,
                        unfocusedBorderColor = com.cinetrack.ui.theme.GlassStrong,
                        focusedContainerColor = com.cinetrack.ui.theme.GlassSubtle,
                        unfocusedContainerColor = com.cinetrack.ui.theme.GlassFaint,
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        onSubmitQuery(query)
                        keyboard?.hide()
                    }),
                )
            }
            if (query.isBlank() && history.isNotEmpty()) {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item { SectionHeader(stringResource(R.string.search_history)) }
                    items(history, key = { it.lowercase() }) { previousQuery ->
                        Row(
                            Modifier.fillMaxWidth().glass(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Medium))
                                .clickable {
                                    query = previousQuery
                                    if (remoteSearch) onQuery(previousQuery)
                                }
                                .padding(start = com.cinetrack.ui.theme.Spacing.md),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.History, null, tint = TextMuted, modifier = Modifier.size(19.dp))
                            Text(
                                previousQuery,
                                color = TextPrimary,
                                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f).padding(horizontal = com.cinetrack.ui.theme.Spacing.md),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            IconButton(onClick = { onRemoveHistory(previousQuery) }) {
                                Icon(Icons.Filled.Close, stringResource(R.string.remove_search_history, previousQuery), tint = TextSecondary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            } else if (query.isNotBlank() && visibleResults.isEmpty()) {
                Text(stringResource(R.string.loading), color = TextMuted, modifier = Modifier.padding(com.cinetrack.ui.theme.Spacing.xxl))
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(visibleResults, key = MediaCard::stableKey) { media ->
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            MediaPoster(media, width = maxWidth, onClick = {
                                onSubmitQuery(query)
                                onMedia(media)
                            })
                        }
                    }
                }
            }
        }
    }
}
