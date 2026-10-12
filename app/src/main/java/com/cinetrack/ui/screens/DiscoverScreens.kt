@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.cinetrack.ui.screens

import com.cinetrack.ui.components.sharedBackdrop
import com.cinetrack.ui.components.directionalSlide
import com.cinetrack.ui.components.rememberDirectionalSlide
import com.cinetrack.ui.components.slidingTabIndicator
import com.cinetrack.ui.components.tabSpan
import com.cinetrack.ui.components.rememberTabIndicator
import com.cinetrack.ui.components.sharedPoster
import com.cinetrack.ui.components.softTab
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.ExpandMore
import com.cinetrack.ui.components.tonalPill
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.draw.shadow
import androidx.compose.material.icons.filled.Star
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.cinetrack.domain.PersonCard
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
import com.cinetrack.ui.components.liquidGlassIcon
import com.cinetrack.ui.components.liquidGlassPill
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
    mosaicResults: Pair<com.cinetrack.domain.DiscoverMovieFilters, List<MediaCard>>? = null,
    onMosaicQuery: (com.cinetrack.domain.DiscoverMovieFilters) -> Unit = {},
    mosaicCanLoadMore: Boolean = false,
    onMosaicMore: () -> Unit = {},
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
    val mosaic = state.discoverLayout == "b"
    var mosaicTv by rememberSaveable { mutableStateOf(false) }
    var mosaicKind by rememberSaveable { mutableStateOf(MosaicKind.TRENDING) }
    var mosaicWindow by rememberSaveable { mutableStateOf(MosaicWindow.DAY) }
    var mosaicGenreIds by rememberSaveable { mutableStateOf("") }
    val mosaicGenres = remember(mosaicGenreIds) { mosaicGenreIds.split(',').mapNotNull(String::toIntOrNull).toSet() }
    val mosaicQuery = remember(mosaicTv, mosaicKind, mosaicWindow, mosaicGenres) {
        mosaicFilters(mosaicTv, mosaicKind, mosaicWindow, mosaicGenres, java.time.LocalDate.now())
    }
    LaunchedEffect(mosaic, mosaicQuery) { if (mosaic && mosaicQuery != null) onMosaicQuery(mosaicQuery) }
    val mosaicLoading = mosaicQuery != null && mosaicResults?.first != mosaicQuery
    // Film / Serie turns the grid like a page, from the side of the new choice.
    val mosaicSlide = rememberDirectionalSlide(if (mosaicTv) 1 else 0)
    val mosaicItems = remember(state.rails, mosaicTv, mosaicQuery, mosaicResults) {
        val defaultView = mosaicGenres.isEmpty() &&
            ((mosaicKind == MosaicKind.TRENDING && mosaicWindow == MosaicWindow.DAY) || (mosaicKind == MosaicKind.POPULAR && mosaicWindow == MosaicWindow.ALL))
        when {
            mosaicResults != null && mosaicQuery != null && mosaicResults.first == mosaicQuery -> mosaicResults.second
            // Until the first batch arrives the default views show the rows Discover already loaded.
            defaultView && mosaicKind == MosaicKind.POPULAR -> state.rails[if (mosaicTv) RailIds.POPULAR_TV else RailIds.POPULAR_MOVIES].orEmpty()
            defaultView -> state.rails[if (mosaicTv) RailIds.TRENDING_TV else RailIds.TRENDING_MOVIES].orEmpty()
            else -> emptyList()
        }.distinctBy(MediaCard::stableKey)
    }
    val backgroundHero = if (mosaic) mosaicItems.firstOrNull() else heroes.getOrNull(heroIndex) ?: heroes.firstOrNull()
    AdaptiveBackground(artworkUrl = backgroundHero?.backdropUrl ?: backgroundHero?.posterUrl) {
        LongPullRefreshContainer(refreshing = state.refreshing, onRefresh = onRefresh, enabled = state.tmdbApiConfigured) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().statusBarsPadding(),
                contentPadding = PaddingValues(bottom = 112.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                if (mosaic) mosaicHeader(
                    MosaicChoice(mosaicTv, mosaicKind, mosaicWindow, mosaicGenres),
                    onChange = { choice ->
                        mosaicTv = choice.tv
                        mosaicKind = choice.kind
                        mosaicWindow = choice.window
                        mosaicGenreIds = choice.genres.sorted().joinToString(",")
                    },
                    onSearch = onSearch,
                    onFilters = onFilters,
                )
                if (!mosaic) item {
                    PageTitle(stringResource(R.string.discover), Modifier.padding(start = com.cinetrack.ui.theme.Spacing.xl, end = com.cinetrack.ui.theme.Spacing.xl, top = com.cinetrack.ui.theme.Spacing.lg, bottom = com.cinetrack.ui.theme.Spacing.lg))
                }
                if (!mosaic) item {
                    Row(
                        Modifier.fillMaxWidth().padding(start = com.cinetrack.ui.theme.Spacing.xl, end = com.cinetrack.ui.theme.Spacing.xl, bottom = com.cinetrack.ui.theme.Spacing.xs),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            Modifier.weight(1f).height(48.dp).liquidGlassPill().clickable(onClick = rememberUiAction(onSearch)).padding(horizontal = com.cinetrack.ui.theme.Spacing.lg),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Search, stringResource(R.string.accessibility_search), tint = TextSecondary, modifier = Modifier.size(21.dp))
                            Spacer(Modifier.width(9.dp))
                            Text(stringResource(R.string.search_hint), color = TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = rememberUiAction(onFilters), modifier = Modifier.size(48.dp).liquidGlassIcon()) {
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
                if (mosaic && mosaicLoading && mosaicItems.isEmpty()) item {
                    com.cinetrack.ui.components.LoadingPane(Modifier.fillMaxWidth().height(360.dp))
                }
                if (mosaic && !mosaicLoading && mosaicItems.isEmpty() && state.allMedia.isNotEmpty()) item {
                    Text(stringResource(R.string.no_filter_results), color = TextMuted, modifier = Modifier.padding(horizontal = com.cinetrack.ui.theme.Spacing.xl, vertical = com.cinetrack.ui.theme.Spacing.xxl))
                }
                if (mosaic) mosaicGrid(mosaicItems, MosaicActions(onMedia, onStatus, onNotInterested), mosaicSlide)
                // Reaching the end asks for the next batch while TMDB has more.
                if (mosaic && mosaicItems.isNotEmpty() && mosaicCanLoadMore && mosaicResults?.first == mosaicQuery) item(key = "mosaic_more") {
                    LaunchedEffect(mosaicItems.size) { onMosaicMore() }
                    Box(Modifier.fillMaxWidth().height(72.dp), contentAlignment = Alignment.Center) {
                        androidx.compose.material3.CircularProgressIndicator(Modifier.size(24.dp), color = TextMuted, strokeWidth = 2.dp)
                    }
                }
                if (!mosaic && heroes.isNotEmpty()) item { HeroCarousel(heroes, heroIndex, { heroIndex = it }, onMedia, onStatus, onNotInterested, loadTagline, state.metadataLanguage) }
                if (state.allMedia.isEmpty() && !state.loading) item {
                    Text(
                        state.error ?: stringResource(R.string.no_catalog_data),
                        color = TextSecondary,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = com.cinetrack.ui.theme.Spacing.xl).glass(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Small)).padding(com.cinetrack.ui.theme.Spacing.md),
                    )
                }
                if (!mosaic) {
                    railSection(trendingTvTitle, seeAll, RailIds.TRENDING_TV, state, onSeeAll, onMedia, onStatus, onNotInterested)
                    railSection(trendingMoviesTitle, seeAll, RailIds.TRENDING_MOVIES, state, onSeeAll, onMedia, onStatus, onNotInterested)
                    railSection(popularTvTitle, seeAll, RailIds.POPULAR_TV, state, onSeeAll, onMedia, onStatus, onNotInterested)
                    railSection(popularMoviesTitle, seeAll, RailIds.POPULAR_MOVIES, state, onSeeAll, onMedia, onStatus, onNotInterested)
                    railSection(upcomingTitle, seeAll, RailIds.UPCOMING, state, onSeeAll, onMedia, onStatus, onNotInterested)
                }
            }
        }
    }
}

/** What the Mosaic grid lists, like Cinopsys: a list type, a time window and genres. */
private enum class MosaicKind(val label: Int, val moviesOnly: Boolean = false) {
    TRENDING(R.string.discover_trending),
    POPULAR(R.string.discover_popular),
    UPCOMING(R.string.upcoming),
    BOX_OFFICE(R.string.discover_box_office, moviesOnly = true),
    TOP_RATED(R.string.discover_top_rated);

    val windows: List<MosaicWindow>
        get() = when (this) {
            TRENDING -> listOf(MosaicWindow.DAY, MosaicWindow.WEEK, MosaicWindow.MONTH)
            UPCOMING -> listOf(MosaicWindow.WEEK, MosaicWindow.MONTH, MosaicWindow.YEAR)
            else -> listOf(MosaicWindow.MONTH, MosaicWindow.YEAR, MosaicWindow.ALL)
        }
    val defaultWindow: MosaicWindow
        get() = when (this) {
            TRENDING -> MosaicWindow.DAY
            UPCOMING -> MosaicWindow.MONTH
            else -> MosaicWindow.ALL
        }
}

private enum class MosaicWindow(val label: Int) {
    DAY(R.string.window_today),
    WEEK(R.string.window_week),
    MONTH(R.string.window_month),
    YEAR(R.string.window_year),
    ALL(R.string.window_all),
}

private data class MosaicChoice(val tv: Boolean, val kind: MosaicKind, val window: MosaicWindow, val genres: Set<Int>)

/** TMDB query for a Mosaic choice; null for the default views Discover has already loaded. */
private fun mosaicFilters(tv: Boolean, kind: MosaicKind, window: MosaicWindow, genres: Set<Int>, today: java.time.LocalDate): com.cinetrack.domain.DiscoverMovieFilters? {
    val base = com.cinetrack.domain.DiscoverMovieFilters(mediaType = if (tv) MediaType.TV else MediaType.MOVIE, genreIds = genres)
    // Past windows end today; upcoming windows start tomorrow.
    val since = when (window) {
        MosaicWindow.DAY -> today.minusDays(1)
        MosaicWindow.WEEK -> today.minusDays(7)
        MosaicWindow.MONTH -> today.minusDays(30)
        MosaicWindow.YEAR -> today.withDayOfYear(1)
        MosaicWindow.ALL -> null
    }
    val past = base.copy(dateFrom = since?.toString(), dateTo = today.toString())
    return when (kind) {
        MosaicKind.TRENDING -> when (window) {
            MosaicWindow.DAY -> base.copy(trendingWindow = "day")
            MosaicWindow.WEEK -> base.copy(trendingWindow = "week")
            else -> base.copy(sortBy = "popularity.desc", dateFrom = today.minusDays(45).toString(), dateTo = today.toString(), minimumVotes = 20)
        }
        MosaicKind.POPULAR -> past.copy(sortBy = "popularity.desc")
        MosaicKind.UPCOMING -> base.copy(
            sortBy = "popularity.desc",
            dateFrom = today.plusDays(1).toString(),
            dateTo = when (window) {
                MosaicWindow.WEEK -> today.plusDays(7)
                MosaicWindow.MONTH -> today.plusDays(30)
                else -> today.plusDays(365)
            }.toString(),
        )
        MosaicKind.BOX_OFFICE -> past.copy(sortBy = "revenue.desc")
        MosaicKind.TOP_RATED -> past.copy(sortBy = "vote_average.desc", minimumVotes = if (window == MosaicWindow.ALL) 1000 else 100)
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
private fun LazyListScope.mosaicHeader(
    choice: MosaicChoice,
    onChange: (MosaicChoice) -> Unit,
    onSearch: () -> Unit,
    onFilters: () -> Unit,
) {
    item {
        Row(
            Modifier.fillMaxWidth().padding(start = com.cinetrack.ui.theme.Spacing.xl, end = com.cinetrack.ui.theme.Spacing.lg, top = com.cinetrack.ui.theme.Spacing.lg, bottom = com.cinetrack.ui.theme.Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MosaicHeadline(stringResource(R.string.movies), !choice.tv) {
                if (choice.tv) onChange(choice.copy(tv = false, genres = emptySet()))
            }
            Spacer(Modifier.width(18.dp))
            MosaicHeadline(stringResource(R.string.tv_shows), choice.tv) {
                if (!choice.tv) {
                    val kind = if (choice.kind.moviesOnly) MosaicKind.TRENDING else choice.kind
                    onChange(choice.copy(tv = true, kind = kind, window = if (kind != choice.kind) kind.defaultWindow else choice.window, genres = emptySet()))
                }
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = rememberUiAction(onSearch), modifier = Modifier.size(48.dp).liquidGlassIcon()) {
                Icon(Icons.Filled.Search, stringResource(R.string.accessibility_search), tint = TextSecondary, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = rememberUiAction(onFilters), modifier = Modifier.size(48.dp).liquidGlassIcon()) {
                Icon(Icons.Filled.Tune, stringResource(R.string.filters), tint = TextSecondary, modifier = Modifier.size(19.dp))
            }
        }
    }
    item {
        var sheet by remember { mutableStateOf(0) } // 1 type, 2 window, 3 genres
        val genres = discoverGenreOptions(if (choice.tv) MediaType.TV else MediaType.MOVIE)
        val genreLabel = when (choice.genres.size) {
            0 -> stringResource(R.string.genres)
            1 -> genres.firstOrNull { it.first in choice.genres }?.second ?: stringResource(R.string.genres)
            else -> stringResource(R.string.genres) + " · " + choice.genres.size
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = com.cinetrack.ui.theme.Spacing.xl),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MosaicDropdownChip(stringResource(choice.kind.label), active = choice.kind != MosaicKind.TRENDING) { sheet = 1 }
            MosaicDropdownChip(stringResource(choice.window.label), active = choice.window != choice.kind.defaultWindow) { sheet = 2 }
            MosaicDropdownChip(genreLabel, active = choice.genres.isNotEmpty()) { sheet = 3 }
        }
        val close = { sheet = 0 }
        when (sheet) {
            1 -> MosaicOptionSheet(
                title = stringResource(if (choice.tv) R.string.discover_type_tv else R.string.discover_type_movies),
                options = MosaicKind.entries.filter { !choice.tv || !it.moviesOnly }.map { it to stringResource(it.label) },
                selected = setOf(choice.kind),
                onDismiss = close,
            ) { kind ->
                onChange(choice.copy(kind = kind, window = if (kind == choice.kind) choice.window else kind.defaultWindow))
                close()
            }
            2 -> MosaicOptionSheet(
                title = stringResource(if (choice.kind == MosaicKind.TRENDING) R.string.window_trending_title else R.string.window_title),
                options = choice.kind.windows.map { it to stringResource(it.label) },
                selected = setOf(choice.window),
                onDismiss = close,
            ) { window ->
                onChange(choice.copy(window = window))
                close()
            }
            3 -> {
                // Several genres can be picked; the grid reloads once the sheet closes.
                var draft by remember { mutableStateOf(choice.genres) }
                MosaicOptionSheet(
                    title = stringResource(R.string.genres),
                    options = listOf(-1 to stringResource(R.string.genres_all)) + genres,
                    selected = draft.ifEmpty { setOf(-1) },
                    onDismiss = {
                        if (draft != choice.genres) onChange(choice.copy(genres = draft))
                        close()
                    },
                ) { id ->
                    draft = if (id == -1) emptySet() else if (id in draft) draft - id else draft + id
                }
            }
        }
    }
    item { Spacer(Modifier.height(12.dp)) }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun <T> MosaicOptionSheet(
    title: String,
    options: List<Pair<T, String>>,
    selected: Set<T>,
    onDismiss: () -> Unit,
    onSelect: (T) -> Unit,
) {
    com.cinetrack.ui.components.SharedGlassSheet(onDismiss = onDismiss) {
        Column(Modifier.padding(horizontal = 12.dp)) {
            com.cinetrack.ui.components.SheetTitle(title)
            // Long lists scroll inside the rounded group, so its corners stay rounded.
            com.cinetrack.ui.components.GlassMenuGroup(maxHeight = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * .5f).dp) {
                options.forEachIndexed { index, (value, label) ->
                    com.cinetrack.ui.components.GlassMenuRow(
                        label = label,
                        icon = null,
                        tint = com.cinetrack.ui.theme.AccentLight,
                        selected = value in selected,
                        divider = index < options.lastIndex,
                    ) { onSelect(value) }
                }
            }
        }
    }
}

@Composable
private fun MosaicDropdownChip(label: String, active: Boolean, onClick: () -> Unit) {
    val dropdown = true
    Row(
        Modifier.height(40.dp).tonalPill(active)
            .clickable(onClick = rememberUiAction(onClick))
            .padding(start = 12.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Tune, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(7.dp))
        Text(label, color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
        if (dropdown) {
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Filled.ExpandMore, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun MosaicHeadline(text: String, selected: Boolean, onClick: () -> Unit) {
    val color by androidx.compose.animation.animateColorAsState(
        if (selected) TextPrimary else TextPrimary.copy(alpha = .42f),
        androidx.compose.animation.core.tween(com.cinetrack.ui.theme.Motion.Extended),
        label = "mosaicHeadline",
    )
    Text(
        text,
        color = color,
        style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier.softTab(selected, onClick),
    )
}

/** One wide feature tile, then rows of three posters, then a poster beside a wide tile. */
private class MosaicActions(
    val onMedia: (MediaCard) -> Unit,
    val onStatus: (MediaCard, com.cinetrack.domain.LibraryStatus) -> Unit,
    val onNotInterested: (MediaCard) -> Unit,
)

private fun LazyListScope.mosaicGrid(items: List<MediaCard>, actions: MosaicActions, slide: androidx.compose.runtime.State<Float>) {
    val pattern = intArrayOf(1, 3, 2, 3)
    val blocks = mutableListOf<List<MediaCard>>()
    var index = 0
    var step = 0
    while (index < items.size) {
        val count = minOf(pattern[step % pattern.size], items.size - index)
        blocks += items.subList(index, index + count)
        index += count
        step++
    }
    items(blocks.size, key = { blocks[it].first().stableKey }) { position ->
        Box(Modifier.directionalSlide(slide)) { MosaicBlock(blocks[position], actions) }
    }
}

@Composable
private fun MosaicBlock(block: List<MediaCard>, actions: MosaicActions) {
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        val gap = 8.dp
        val column = (maxWidth - gap * 2) / 3
        val posterHeight = column * 1.5f
        when (block.size) {
            1 -> MosaicTile(block[0], wide = true, modifier = Modifier.fillMaxWidth().height(206.dp), actions = actions)
            2 -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                MosaicTile(block[0], wide = false, modifier = Modifier.width(column).height(posterHeight), actions = actions)
                MosaicTile(block[1], wide = true, modifier = Modifier.weight(1f).height(posterHeight), actions = actions)
            }
            else -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                block.forEach { MosaicTile(it, wide = false, modifier = Modifier.width(column).height(posterHeight), actions = actions) }
            }
        }
    }
}

@Composable
private fun MosaicTile(media: MediaCard, wide: Boolean, modifier: Modifier, actions: MosaicActions) {
    val shape = RoundedCornerShape(14.dp)
    val art = if (wide) media.backdropUrl ?: media.posterUrl else media.posterUrl ?: media.backdropUrl
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    var statusPopup by remember(media.stableKey) { mutableStateOf(false) }
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val posterSlot = com.cinetrack.ui.components.rememberPosterSlot()
    val pressScale by animateFloatAsState(
        if (pressed || statusPopup) .96f else 1f,
        spring(dampingRatio = .7f, stiffness = Spring.StiffnessMediumLow),
        label = "mosaicPress",
    )
    Box(
        modifier.graphicsLayer { scaleX = pressScale; scaleY = pressScale }
            .shadow(12.dp, shape, clip = false)
            .then(if (wide) Modifier.sharedBackdrop(media.stableKey, posterSlot) else Modifier.sharedPoster(media.stableKey, posterSlot))
            .clip(shape).background(com.cinetrack.ui.theme.Background1)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    com.cinetrack.ui.components.SharedPosterSource.select(posterSlot, media.stableKey)
                    actions.onMedia(media)
                },
                onLongClick = {
                    haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    statusPopup = true
                },
            ),
    ) {
        MediaStatusPopup(
            expanded = statusPopup,
            currentStatus = media.status,
            onDismiss = { statusPopup = false },
            onStatus = { status -> actions.onStatus(media, status); statusPopup = false },
            onNotInterested = { actions.onNotInterested(media); statusPopup = false },
            media = media,
        )
        if (!art.isNullOrBlank()) {
            if (wide) AsyncImage(art, media.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else com.cinetrack.ui.components.SharedPosterImage(art, media.title, posterSlot, Modifier.fillMaxSize(), ContentScale.Crop)
        }
        if (wide) {
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(0.4f to Color.Transparent, 1f to Color(0xE6080A0C))))
            Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
                Text(
                    listOfNotNull(
                        stringResource(if (media.type == MediaType.TV) R.string.tv_shows else R.string.movies),
                        media.releaseDate?.take(4)?.takeIf(String::isNotBlank),
                    ).joinToString(" · ").uppercase(com.cinetrack.ui.currentLocale()),
                    color = TextSecondary,
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                )
                Text(media.title, color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                media.score?.takeIf { it > 0 }?.let { score ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Star, null, tint = com.cinetrack.ui.theme.Gold, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(String.format(com.cinetrack.ui.currentLocale(), "%.1f", score), color = com.cinetrack.ui.theme.Gold, style = androidx.compose.material3.MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        if (media.status != com.cinetrack.domain.LibraryStatus.NONE) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(7.dp).size(24.dp).clip(CircleShape)
                    .background(com.cinetrack.ui.components.libraryStatusColor(media.status).copy(alpha = .8f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(com.cinetrack.ui.components.libraryStatusIcon(media.status), null, tint = Color.White, modifier = Modifier.size(13.dp))
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
            // The 16:9 card uses nearly the full width so the wide format still reads large.
            contentPadding = PaddingValues(horizontal = if (com.cinetrack.ui.theme.LocalCardAppearance.current.heroLayout == "landscape") 12.dp else 20.dp),
            pageSpacing = if (com.cinetrack.ui.theme.LocalCardAppearance.current.heroLayout == "landscape") 8.dp else 10.dp,
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
    val coverSlot = com.cinetrack.ui.components.rememberPosterSlot()
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
    val titleSize = if (compact) 23.sp else if (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 380) 26.sp else 30.sp
    BoxWithConstraints(Modifier.fillMaxWidth()) {
    val cardHeight = appearance.heroAspectRatio?.let { ratio ->
        // Preserve the selected ratio unless larger accessibility text needs more room.
        (maxWidth / ratio).coerceAtLeast(((if (compact) 230 else 270) * fontScale).dp)
    } ?: (310 * fontScale).dp
    Box(
        Modifier
            .fillMaxWidth()
            .height(cardHeight)
            .sharedBackdrop(media.stableKey, coverSlot)
            .clip(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Large))
            .background(Brush.linearGradient(listOf(com.cinetrack.ui.theme.SurfacePalette.PosterBrown, com.cinetrack.ui.theme.SurfacePalette.SeaSurface, com.cinetrack.ui.theme.SurfacePalette.PosterShadow)))
            .glass(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Large))
            .border(if (pressed || statusPopup) 2.dp else .5.dp, if (pressed || statusPopup) com.cinetrack.ui.theme.Accent else Color.Transparent, RoundedCornerShape(com.cinetrack.ui.theme.Radius.Large))
            .combinedClickable(
                enabled = !preview,
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    com.cinetrack.ui.components.SharedPosterSource.select(coverSlot, media.stableKey)
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
            media = media,
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
    peopleResults: List<PersonCard>,
    mediaSearchLoading: Boolean,
    peopleSearchLoading: Boolean,
    sourceItems: List<MediaCard> = emptyList(),
    remoteSearch: Boolean = true,
    history: List<String> = emptyList(),
    onQuery: (String) -> Unit,
    onPeopleQuery: (String) -> Unit,
    onSubmitQuery: (String) -> Unit,
    onRemoveHistory: (String) -> Unit,
    onLeave: () -> Unit,
    onBack: () -> Unit,
    onMedia: (MediaCard) -> Unit,
    onPerson: (PersonCard) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("media") }
    val peopleSelected = remoteSearch && category == "people"
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val visibleResults = remember(query, results, sourceItems, remoteSearch) {
        if (query.isBlank()) emptyList()
        else if (remoteSearch) results else sourceItems.filter { it.title.contains(query, ignoreCase = true) }
    }
    val visiblePeople = if (query.isBlank()) emptyList() else peopleResults
    val activeSearchLoading = if (peopleSelected) peopleSearchLoading else mediaSearchLoading
    val backgroundArtwork = if (peopleSelected) visiblePeople.firstOrNull()?.profileUrl
        else visibleResults.firstOrNull()?.let { it.backdropUrl ?: it.posterUrl }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }
    DisposableEffect(Unit) { onDispose(onLeave) }
    AdaptiveBackground(artworkUrl = backgroundArtwork) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(com.cinetrack.ui.theme.Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                GlassBackButton(onClick = onBack)
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        if (remoteSearch) {
                            if (peopleSelected) onPeopleQuery(it) else onQuery(it)
                        }
                    },
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
                        if (remoteSearch) {
                            if (peopleSelected) onPeopleQuery(query) else onQuery(query)
                        }
                        onSubmitQuery(query)
                        keyboard?.hide()
                    }),
                )
            }
            if (remoteSearch) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SearchCategoryPill(
                        text = stringResource(R.string.movies_tv),
                        icon = Icons.Filled.Movie,
                        selected = !peopleSelected,
                        modifier = Modifier.weight(1f),
                    ) {
                        category = "media"
                        if (query.isNotBlank()) onQuery(query)
                    }
                    SearchCategoryPill(
                        text = stringResource(R.string.people),
                        icon = Icons.Filled.Person,
                        selected = peopleSelected,
                        modifier = Modifier.weight(1f),
                    ) {
                        category = "people"
                        if (query.isNotBlank()) onPeopleQuery(query)
                    }
                }
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
                                    if (remoteSearch) {
                                        if (peopleSelected) onPeopleQuery(previousQuery) else onQuery(previousQuery)
                                    }
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
            } else if (query.isNotBlank() && (if (peopleSelected) visiblePeople.isEmpty() else visibleResults.isEmpty())) {
                Text(
                    stringResource(if (activeSearchLoading) R.string.loading else R.string.no_search_results),
                    color = TextMuted,
                    modifier = Modifier.padding(com.cinetrack.ui.theme.Spacing.xxl),
                )
            } else if (peopleSelected) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(visiblePeople, key = PersonCard::id) { person ->
                        val localizedPerson = person.copy(
                            role = when (person.role.lowercase()) {
                                "acting" -> stringResource(R.string.actor)
                                "directing" -> stringResource(R.string.director)
                                else -> stringResource(R.string.person)
                            },
                        )
                        PersonSearchCard(localizedPerson) {
                            onSubmitQuery(query)
                            keyboard?.hide()
                            onPerson(localizedPerson)
                        }
                    }
                }
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

@Composable
private fun SearchCategoryPill(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier.height(44.dp)
            .liquidGlassPill()
            .background(if (selected) com.cinetrack.ui.theme.Accent.copy(alpha = .25f) else Color.Transparent)
            .clickable(onClick = rememberUiAction(onClick))
            .padding(horizontal = com.cinetrack.ui.theme.Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = if (selected) AccentLight else TextMuted, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(7.dp))
        Text(text, color = if (selected) TextPrimary else TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PersonSearchCard(person: PersonCard, onClick: () -> Unit) {
    val shape = RoundedCornerShape(com.cinetrack.ui.theme.Radius.Medium)
    Column(
        Modifier.fillMaxWidth().glass(shape).clickable(onClick = rememberUiAction(onClick)),
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(.82f).background(com.cinetrack.ui.theme.GlassBare),
            contentAlignment = Alignment.Center,
        ) {
            if (!person.profileUrl.isNullOrBlank()) {
                AsyncImage(person.profileUrl, person.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Icon(Icons.Filled.Person, null, tint = TextMuted, modifier = Modifier.size(42.dp))
            }
        }
        Column(Modifier.fillMaxWidth().padding(com.cinetrack.ui.theme.Spacing.sm)) {
            Text(person.name, color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(person.role.ifBlank { stringResource(R.string.people) }, color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
