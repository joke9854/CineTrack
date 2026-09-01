@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.cinetrack.ui.screens

import android.view.HapticFeedbackConstants
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
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalView
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
import com.cinetrack.ui.components.glass
import com.cinetrack.ui.components.glassIcon
import com.cinetrack.ui.components.rememberLightHapticAction
import com.cinetrack.ui.theme.AccentLight
import com.cinetrack.ui.theme.TextMuted
import com.cinetrack.ui.theme.TextPrimary
import com.cinetrack.ui.theme.TextSecondary
import kotlinx.coroutines.delay

@Composable
fun DiscoverScreen(
    state: AppUiState,
    onRefresh: () -> Unit,
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
    val upcomingTitle = stringResource(R.string.upcoming)
    val seeAll = stringResource(R.string.see_all)
    val activeHero = heroes.getOrNull(heroIndex)
    AdaptiveBackground(artworkUrl = activeHero?.backdropUrl ?: activeHero?.posterUrl) {
        LongPullRefreshContainer(refreshing = state.refreshing, onRefresh = onRefresh) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().statusBarsPadding(),
                contentPadding = PaddingValues(bottom = 112.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                item {
                    PageTitle(stringResource(R.string.discover), Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 16.dp))
                }
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            Modifier.weight(1f).height(38.dp).glass(RoundedCornerShape(999.dp)).clickable(onClick = rememberLightHapticAction(onSearch)).padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Search, stringResource(R.string.accessibility_search), tint = TextSecondary, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(9.dp))
                            Text(stringResource(R.string.search_hint), color = TextMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = rememberLightHapticAction(onFilters), modifier = Modifier.size(46.dp).glassIcon()) {
                            Icon(Icons.Filled.Tune, stringResource(R.string.filters), tint = TextSecondary, modifier = Modifier.size(17.dp))
                        }
                    }
                }
                if (!state.tmdbApiConfigured) item {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)
                            .glass(RoundedCornerShape(18.dp))
                            .background(Color(0xFF8A5B00).copy(alpha = .20f), RoundedCornerShape(18.dp))
                            .border(.8.dp, Color(0xFFFFC75D).copy(alpha = .45f), RoundedCornerShape(18.dp))
                            .padding(15.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.WarningAmber, null, tint = Color(0xFFFFD37A), modifier = Modifier.size(23.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(stringResource(R.string.discover_tmdb_warning_title), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        Text(
                            stringResource(R.string.discover_tmdb_warning_message),
                            color = TextSecondary,
                            fontSize = 12.5.sp,
                            lineHeight = 17.sp,
                            modifier = Modifier.padding(top = 9.dp, bottom = 12.dp),
                        )
                        PrimaryAction(
                            text = stringResource(R.string.open_api_settings),
                            icon = Icons.Filled.Settings,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = onOpenTmdbSettings,
                        )
                    }
                }
                if (heroes.isNotEmpty()) item { HeroCarousel(heroes, heroIndex, { heroIndex = it }, onMedia, onStatus, onNotInterested) }
                if (state.allMedia.isEmpty() && !state.loading) item {
                    Text(
                        state.error ?: stringResource(R.string.no_catalog_data),
                        color = TextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 20.dp).glass(RoundedCornerShape(14.dp)).padding(12.dp),
                    )
                }
                railSection(trendingTvTitle, seeAll, RailIds.TRENDING_TV, state, onSeeAll, onMedia, onStatus, onNotInterested)
                railSection(trendingMoviesTitle, seeAll, RailIds.TRENDING_MOVIES, state, onSeeAll, onMedia, onStatus, onNotInterested)
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
    item { SectionHeader(title, Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 13.dp), seeAll, { onSeeAll(railId) }) }
    item { MediaRail(items, onMedia, showAirDate = railId == RailIds.UPCOMING, onStatus = onStatus, onNotInterested = onNotInterested) }
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
) {
    val pagerState = rememberPagerState(initialPage = selectedPage, pageCount = { items.size })
    LaunchedEffect(pagerState.currentPage) { onPage(pagerState.currentPage) }
    LaunchedEffect(items.size) {
        if (items.size > 1) while (true) {
            delay(5_000)
            pagerState.animateScrollToPage((pagerState.currentPage + 1) % items.size)
        }
    }
    Column(Modifier.padding(top = 18.dp, bottom = 4.dp)) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 20.dp),
            pageSpacing = 10.dp,
            key = { items[it].stableKey },
        ) { page ->
            HeroCard(items[page], onMedia, onStatus, onNotInterested)
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp), horizontalArrangement = Arrangement.Center) {
            items.indices.forEach { page ->
                Box(
                    Modifier.width(if (pagerState.currentPage == page) 16.dp else 6.dp).height(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (pagerState.currentPage == page) com.cinetrack.ui.theme.Accent else Color.White.copy(alpha = .24f)),
                )
                if (page != items.lastIndex) Spacer(Modifier.width(6.dp))
            }
        }
    }
}

@Composable
private fun HeroCard(
    media: MediaCard,
    onMedia: (MediaCard) -> Unit,
    onStatus: (MediaCard, com.cinetrack.domain.LibraryStatus) -> Unit,
    onNotInterested: (MediaCard) -> Unit,
) {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    var statusPopup by remember(media.stableKey) { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxWidth()
            .height(250.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF705446), Color(0xFF172331), Color(0xFF07101C))))
            .glass(RoundedCornerShape(22.dp))
            .border(if (pressed || statusPopup) 2.dp else .5.dp, if (pressed || statusPopup) com.cinetrack.ui.theme.Accent else Color.Transparent, RoundedCornerShape(22.dp))
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    onMedia(media)
                },
                onLongClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    statusPopup = true
                },
            ),
    ) {
        if (!media.backdropUrl.isNullOrBlank()) {
            AsyncImage(media.backdropUrl, media.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = .76f), Color.Black.copy(alpha = .34f), Color.Transparent))))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .58f)))))
        Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
            val type = if (media.type == MediaType.TV) stringResource(R.string.tv_shows) else stringResource(R.string.movies)
            val genre = media.genres.firstOrNull().orEmpty()
            val metadata = listOf(type, genre, media.year).filter(String::isNotBlank).joinToString(" · ").uppercase()
            Text(metadata, color = TextSecondary, fontSize = 11.sp, letterSpacing = .6.sp, fontWeight = FontWeight.ExtraBold)
            Text(media.title.uppercase(), color = Color.White, fontSize = 38.sp, lineHeight = 40.sp, letterSpacing = .8.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
            media.score?.let { Text("★  %.1f".format(it), color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 4.dp)) }
            media.overview.takeIf(String::isNotBlank)?.let {
                Text(it, color = TextSecondary, fontSize = 13.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    Modifier.height(46.dp).glass(RoundedCornerShape(999.dp))
                        .background(if (media.status != com.cinetrack.domain.LibraryStatus.NONE) com.cinetrack.ui.theme.Success.copy(alpha = .12f) else Color.Transparent)
                        .clickable(onClick = rememberLightHapticAction { statusPopup = true }).padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (media.status == com.cinetrack.domain.LibraryStatus.NONE) Icons.Filled.Add else Icons.Filled.Check,
                        null,
                        tint = if (media.status == com.cinetrack.domain.LibraryStatus.NONE) Color.White else com.cinetrack.ui.theme.Success,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        if (media.status == com.cinetrack.domain.LibraryStatus.NONE) stringResource(R.string.add_to_library) else stringResource(R.string.in_library),
                        color = Color.White,
                        fontSize = 13.5.sp,
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

@Composable
fun DiscoverListScreen(
    railId: String,
    state: AppUiState,
    onBack: () -> Unit,
    onMedia: (MediaCard) -> Unit,
    onStatus: (MediaCard, com.cinetrack.domain.LibraryStatus) -> Unit,
    onNotInterested: (MediaCard) -> Unit,
) {
    val title = when (railId) {
        RailIds.TRENDING_TV -> stringResource(R.string.trending_tv)
        RailIds.TRENDING_MOVIES -> stringResource(R.string.trending_movies)
        else -> stringResource(R.string.upcoming)
    }
    val backgroundItem = state.rails[railId].orEmpty().firstOrNull()
    AdaptiveBackground(artworkUrl = backgroundItem?.backdropUrl ?: backgroundItem?.posterUrl) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassBackButton(onClick = onBack)
                PageTitle(title, Modifier.padding(start = 12.dp))
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 112.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(state.rails[railId].orEmpty(), key = MediaCard::stableKey) { media ->
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        MediaPoster(
                            media,
                            width = maxWidth,
                            onStatus = { onStatus(media, it) },
                            onNotInterested = { onNotInterested(media) },
                            onClick = { onMedia(media) },
                        )
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
    onQuery: (String) -> Unit,
    onBack: () -> Unit,
    onMedia: (MediaCard) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val visibleResults = remember(query, results, sourceItems, remoteSearch) {
        if (remoteSearch) results else sourceItems.filter { it.title.contains(query, ignoreCase = true) }
    }
    val backgroundItem = visibleResults.firstOrNull()
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }
    AdaptiveBackground(artworkUrl = backgroundItem?.backdropUrl ?: backgroundItem?.posterUrl) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassBackButton(onClick = onBack)
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it; if (remoteSearch) onQuery(it) },
                    modifier = Modifier.weight(1f).focusRequester(focusRequester),
                    placeholder = { Text(stringResource(R.string.search_hint)) },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentLight,
                        unfocusedBorderColor = Color.White.copy(alpha = .18f),
                        focusedContainerColor = Color.White.copy(alpha = .08f),
                        unfocusedContainerColor = Color.White.copy(alpha = .06f),
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { if (remoteSearch) onQuery(query) }),
                )
            }
            if (query.isNotBlank() && visibleResults.isEmpty()) {
                Text(stringResource(R.string.loading), color = TextMuted, modifier = Modifier.padding(24.dp))
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(visibleResults, key = MediaCard::stableKey) { media ->
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            MediaPoster(media, width = maxWidth, onClick = { onMedia(media) })
                        }
                    }
                }
            }
        }
    }
}
