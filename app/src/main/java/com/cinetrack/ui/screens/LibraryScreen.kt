@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.cinetrack.ui.screens

import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import com.cinetrack.ui.components.slidingTabIndicator
import com.cinetrack.ui.components.tabSpan
import com.cinetrack.ui.components.rememberTabIndicator
import com.cinetrack.ui.components.sharedPoster
import com.cinetrack.ui.components.softTab
import com.cinetrack.ui.components.collapsingTitle
import com.cinetrack.ui.components.pinnedHeader
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.draw.shadow
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.graphics.Brush
import com.cinetrack.ui.components.liquidGlassIcon
import com.cinetrack.ui.components.liquidGlassPill
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.PlaylistAddCheck
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cinetrack.R
import com.cinetrack.domain.AppUiState
import com.cinetrack.domain.LibraryStatus
import com.cinetrack.domain.MediaCard
import com.cinetrack.domain.MediaType
import com.cinetrack.domain.RailIds
import com.cinetrack.ui.components.AdaptiveBackground
import com.cinetrack.ui.components.MediaPoster
import com.cinetrack.ui.components.PrimaryAction
import com.cinetrack.ui.components.SharedGlassSheet
import com.cinetrack.ui.components.glass
import com.cinetrack.ui.components.libraryStatusColor
import com.cinetrack.ui.components.rememberUiAction
import com.cinetrack.ui.theme.Accent
import com.cinetrack.ui.theme.AccentLight
import com.cinetrack.ui.theme.TextMuted
import com.cinetrack.ui.theme.TextPrimary
import com.cinetrack.ui.theme.TextSecondary
import com.cinetrack.ui.theme.Motion
import com.cinetrack.ui.theme.Radius
import com.cinetrack.ui.theme.Spacing
import com.cinetrack.ui.theme.SurfacePalette

private enum class LibraryOrder { TITLE, RATING, YEAR, RECENTLY_WATCHED, RECENTLY_ADDED }

@Composable
fun LibraryScreen(
    state: com.cinetrack.ui.library.LibraryUiState,
    artwork: com.cinetrack.ui.library.LibraryArtworkState,
    onSearch: () -> Unit,
    onMedia: (MediaCard) -> Unit,
    onStatus: (MediaCard, LibraryStatus) -> Unit,
    onBulkStatus: (List<MediaCard>, LibraryStatus) -> Unit,
    onRefreshArtwork: () -> Unit,
    onCompactNav: (Boolean) -> Unit,
) {
    var type by rememberSaveable { mutableStateOf(MediaType.TV) }
    var status by rememberSaveable { mutableStateOf<LibraryStatus?>(null) }
    var order by rememberSaveable { mutableStateOf(LibraryOrder.RECENTLY_ADDED) }
    var ascending by rememberSaveable { mutableStateOf(false) }
    var showOrderSheet by rememberSaveable { mutableStateOf(false) }
    var bulkMode by rememberSaveable { mutableStateOf(false) }
    var selectedKeys by remember { mutableStateOf(emptySet<String>()) }
    // System back leaves selection first, like every other Android list.
    androidx.activity.compose.BackHandler(enabled = bulkMode) {
        bulkMode = false
        selectedKeys = emptySet()
    }
    var showBulkSheet by rememberSaveable { mutableStateOf(false) }
    var listView by rememberSaveable { mutableStateOf(false) }
    // Each tab has its own grid and list (two are on screen while tabs slide), kept by a
    // saveable holder so a tab's scroll survives switching away and opening a title.
    val tabStates = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    var gridState by remember { mutableStateOf(androidx.compose.foundation.lazy.grid.LazyGridState()) }
    var listState by remember { mutableStateOf(androidx.compose.foundation.lazy.LazyListState()) }
    NavCollapseGridEffect(gridState, onCompactNav)
    NavCollapseEffect(listState, onCompactNav)
    val typeItems = state.library.filter { it.type == type }
    val watchedAtByKey = remember(state.history) {
        state.history.groupBy { it.media.stableKey }.mapValues { (_, events) ->
            events.maxOfOrNull { it.timestamp }.orEmpty()
        }
    }
    fun sortedItems(filteredItems: List<MediaCard>): List<MediaCard> {
        val sorted = when (order) {
            LibraryOrder.TITLE -> filteredItems.sortedBy { it.title.lowercase() }
            LibraryOrder.RATING -> filteredItems.sortedBy { it.score ?: -1.0 }
            LibraryOrder.YEAR -> filteredItems.sortedBy { it.year.toIntOrNull() ?: 0 }
            LibraryOrder.RECENTLY_WATCHED -> filteredItems.sortedBy { watchedAtByKey[it.stableKey].orEmpty() }
            LibraryOrder.RECENTLY_ADDED -> filteredItems.sortedBy { it.libraryUpdatedAt ?: 0L }
        }
        return if (ascending) sorted else sorted.reversed()
    }
    val items = remember(typeItems, status, order, ascending, watchedAtByKey) {
        sortedItems(typeItems.filter { status == null || it.status == status })
    }
    val filterChoices = listOf(
        Triple<LibraryStatus?, String, Color>(null, stringResource(R.string.all), Accent),
    ) + listOf(LibraryStatus.WATCHING, LibraryStatus.PLAN_TO_WATCH, LibraryStatus.PAUSED, LibraryStatus.COMPLETED, LibraryStatus.DROPPED)
        .map { Triple<LibraryStatus?, String, Color>(it, stringResource(com.cinetrack.ui.components.libraryStatusLabel(it)), libraryStatusColor(it)) }
    val progressByKey = remember(state.playbackTv, state.playbackMovies) {
        (state.playbackTv + state.playbackMovies).associate { it.media.stableKey to it.progress }
    }
    LaunchedEffect(type, status, bulkMode) { selectedKeys = emptySet() }
    val backgroundMedia = state.library.minByOrNull { it.stableKey }
    AdaptiveBackground(artworkUrl = backgroundMedia?.posterUrl) {
        LongPullRefreshContainer(
            refreshing = artwork.refreshing,
            onRefresh = onRefreshArtwork,
            enabled = state.tmdbApiConfigured,
        ) {
        Box(Modifier.fillMaxSize().statusBarsPadding()) {
            val statusIndex = filterChoices.indexOfFirst { it.first == status }.coerceAtLeast(0)
            // Warm the image cache with the first posters of every tab, so switching tabs
            // shows artwork straight away instead of popping it in.
            val prefetchContext = LocalContext.current
            val posterPx = with(androidx.compose.ui.platform.LocalDensity.current) {
                val columns = com.cinetrack.domain.CardAppearance.gridColumns(state.cardDensity)
                ((androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp - 40.dp - 12.dp * (columns - 1)) / columns).roundToPx()
            }
            LaunchedEffect(typeItems, posterPx) {
                val loader = coil3.SingletonImageLoader.get(prefetchContext)
                filterChoices.forEach { (value, _, _) ->
                    typeItems.filter { value == null || it.status == value }.take(12).forEach { media ->
                        media.posterUrl?.let { url ->
                            loader.enqueue(coil3.request.ImageRequest.Builder(prefetchContext).data(url).size(posterPx, posterPx * 3 / 2).build())
                        }
                    }
                }
            }
            // Items glide when one of them changes in place (a status change), but not when a
            // tab, type or sort swaps the whole list: titles in both lists would fly across the
            // grid to their new places while the rest fade. The tab slide is the only motion then.
            val listContent = "$type:$status:$order"
            var settledContent by remember { mutableStateOf(listContent) }
            val itemMotion = settledContent == listContent
            LaunchedEffect(listContent) {
                kotlinx.coroutines.delay(Motion.Long.toLong())
                settledContent = listContent
            }
            val scrollToTop = {
                gridState.requestScrollToItem(0)
                listState.requestScrollToItem(0)
                onCompactNav(false)
            }
            // As in Progress: the headline row scrolls away, tabs and toolbar stay pinned.
            // Saved, so a page rebuilt on going back lays the grid out below the header in its
            // very first frame; measured afresh it started at 0, and a poster flying home aimed
            // at a tile one header too high, then had to chase it.
            var titlePx by rememberSaveable { androidx.compose.runtime.mutableIntStateOf(0) }
            var headerPx by rememberSaveable { androidx.compose.runtime.mutableIntStateOf(0) }
            val headerScroll = remember(listView, gridState, listState) {
                {
                    if (listView) { if (listState.firstVisibleItemIndex > 0) Int.MAX_VALUE else listState.firstVisibleItemScrollOffset }
                    else { if (gridState.firstVisibleItemIndex > 0) Int.MAX_VALUE else gridState.firstVisibleItemScrollOffset }
                }
            }
            Column(
                Modifier.zIndex(1f).fillMaxWidth()
                    .onSizeChanged { headerPx = it.height }
                    .pinnedHeader({ titlePx }, headerScroll, com.cinetrack.ui.components.LocalPageTint.current),
            ) {
            // Headline toggle (Serie TV / Film) and the list / grid switch.
            Row(Modifier.fillMaxWidth().onSizeChanged { titlePx = it.height }.collapsingTitle({ titlePx }, headerScroll).padding(start = Spacing.xl, end = Spacing.lg, top = Spacing.lg, bottom = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                listOf(MediaType.MOVIE, MediaType.TV).forEach { item ->
                    val selected = item == type
                    val headlineColor by androidx.compose.animation.animateColorAsState(
                        if (selected) TextPrimary else TextPrimary.copy(alpha = .42f),
                        tween(Motion.Long),
                        label = "libraryHeadline",
                    )
                    Text(
                        stringResource(if (item == MediaType.TV) R.string.tv_shows else R.string.movies),
                        color = headlineColor,
                        style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(end = 18.dp).softTab(selected) {
                            if (selected) scrollToTop() else type = item
                        },
                    )
                }
                Spacer(Modifier.weight(1f))
                val layoutHaptics = com.cinetrack.ui.components.rememberAppHaptics()
                Row(Modifier.height(44.dp).liquidGlassPill().padding(horizontal = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    listOf(true to Icons.Filled.ViewList, false to Icons.Filled.GridView).forEach { (list, icon) ->
                        val active = listView == list
                        Box(
                            Modifier.size(38.dp).clip(CircleShape).background(if (active) Accent.copy(alpha = .30f) else Color.Transparent)
                                .selectable(selected = active, role = Role.RadioButton) { if (!active) layoutHaptics.tick(); listView = list },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(icon, stringResource(if (list) R.string.view_list else R.string.view_grid), tint = if (active) TextPrimary else TextMuted, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
            // Status as underlined tabs with counts; one underline glides between them.
            val indicator = rememberTabIndicator()
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.xl)
                    .slidingTabIndicator(indicator, statusIndex),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xl),
            ) {
                filterChoices.forEachIndexed { index, (value, label, _) ->
                    val selected = status == value
                    val count = if (value == null) typeItems.size else typeItems.count { it.status == value }
                    val labelColor by androidx.compose.animation.animateColorAsState(
                        if (selected) TextPrimary else TextPrimary.copy(alpha = .5f),
                        tween(Motion.Long),
                        label = "libraryTabLabel",
                    )
                    Row(
                        Modifier.tabSpan(indicator, index)
                            .softTab(selected) { if (selected) scrollToTop() else status = value }
                            .heightIn(min = 44.dp)
                            .padding(vertical = Spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(label, color = labelColor, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold, maxLines = 1)
                        Spacer(Modifier.width(5.dp))
                        Text(count.toString(), color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(.5.dp).background(Color.White.copy(alpha = .08f)))
            // Sort chip, search, artwork refresh and bulk edit.
            Row(
                Modifier.fillMaxWidth().padding(start = Spacing.xl, end = Spacing.lg, top = Spacing.md, bottom = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Row(
                    Modifier.height(40.dp).liquidGlassPill().clickable(onClick = rememberUiAction { showOrderSheet = true }).padding(start = 14.dp, end = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(libraryOrderLabel(order), color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Spacer(Modifier.width(Spacing.xs))
                    Icon(Icons.Filled.ExpandMore, stringResource(R.string.sort_by), tint = TextSecondary, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = rememberUiAction(onSearch), modifier = Modifier.size(48.dp).liquidGlassIcon()) {
                    Icon(Icons.Filled.Search, stringResource(R.string.accessibility_search), tint = TextSecondary, modifier = Modifier.size(19.dp))
                }
                if (artwork.progress != null && !artwork.progressVisible) {
                    IconButton(onClick = rememberUiAction(onRefreshArtwork), modifier = Modifier.size(48.dp).liquidGlassIcon()) {
                        Icon(Icons.Filled.Refresh, stringResource(R.string.refresh), tint = AccentLight, modifier = Modifier.size(19.dp))
                    }
                }
                IconButton(onClick = { bulkMode = !bulkMode; selectedKeys = emptySet() }, modifier = Modifier.size(48.dp).liquidGlassIcon()) {
                    Icon(
                        if (bulkMode) Icons.Filled.Close else Icons.Filled.Checklist,
                        stringResource(if (bulkMode) R.string.cancel else R.string.bulk_edit),
                        tint = if (bulkMode) AccentLight else TextSecondary,
                        modifier = Modifier.size(19.dp),
                    )
                }
            }
            if (bulkMode) {
                PrimaryAction(
                    stringResource(R.string.edit_selected_count, selectedKeys.size),
                    Icons.Filled.PlaylistAddCheck,
                    Modifier.fillMaxWidth().padding(horizontal = Spacing.xl, vertical = Spacing.sm),
                    enabled = selectedKeys.isNotEmpty(),
                ) { showBulkSheet = true }
            }
            }
            val headerHeight = with(androidx.compose.ui.platform.LocalDensity.current) { headerPx.toDp() }
            // Tabs slide as whole pages, the same motion as Progress's tabs.
            androidx.compose.animation.AnimatedContent(
                targetState = LibraryTab(type, status),
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    val forward = if (targetState.type != initialState.type) targetState.type == MediaType.TV
                    else filterChoices.indexOfFirst { it.first == targetState.status } > filterChoices.indexOfFirst { it.first == initialState.status }
                    Motion.tabEnter(forward) togetherWith Motion.tabExit(forward)
                },
                label = "libraryTabPage",
            ) { tab ->
            tabStates.SaveableStateProvider("${tab.type}:${tab.status}") {
            val tabGrid = rememberLazyGridState()
            val tabList = rememberLazyListState()
            if (tab == LibraryTab(type, status)) androidx.compose.runtime.SideEffect {
                gridState = tabGrid
                listState = tabList
            }
            val items = if (tab == LibraryTab(type, status)) items
            else remember(typeItems, tab, order, ascending, watchedAtByKey) {
                sortedItems(state.library.filter { it.type == tab.type && (tab.status == null || it.status == tab.status) })
            }
            // Empty state, list and grid crossfade instead of swapping in one frame.
            val layoutMode = when {
                items.isEmpty() -> 0
                listView -> 1
                else -> 2
            }
            androidx.compose.animation.Crossfade(layoutMode, animationSpec = tween(Motion.Medium), label = "libraryLayout") { mode ->
            if (mode == 0) {
                Box(Modifier.fillMaxSize().padding(top = headerHeight), contentAlignment = Alignment.Center) {
                    com.cinetrack.ui.components.EmptyState(
                        icon = Icons.Filled.AutoAwesome,
                        title = stringResource(if (tab.status != null) R.string.library_no_matches else R.string.empty_library),
                        body = stringResource(if (tab.status != null) R.string.library_no_matches_description else R.string.empty_library_description),
                        actionLabel = stringResource(if (tab.status != null) R.string.clear_filters else R.string.accessibility_search),
                        onAction = if (tab.status != null) { { status = null } } else onSearch,
                    )
                }
            } else if (mode == 1) {
                val statusLabels = filterChoices.mapNotNull { (value, label, _) -> value?.let { it to label } }.toMap()
                LibraryList(items, tabList, bulkMode, selectedKeys, progressByKey, statusLabels, onMedia, topPadding = headerHeight, itemMotion = itemMotion) { media ->
                    selectedKeys = if (media.stableKey in selectedKeys) selectedKeys - media.stableKey else selectedKeys + media.stableKey
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(com.cinetrack.domain.CardAppearance.gridColumns(state.cardDensity)),
                    state = tabGrid,
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = headerHeight + 12.dp, bottom = 112.dp),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                ) {
                    items(items, key = MediaCard::stableKey) { media ->
                        BoxWithConstraints(Modifier.fillMaxWidth().animateItem(fadeInSpec = if (itemMotion) Motion.enter(Motion.Medium) else null, placementSpec = if (itemMotion) Motion.gentle(androidx.compose.ui.unit.IntOffset.VisibilityThreshold) else null, fadeOutSpec = if (itemMotion) Motion.exit() else null)) {
                            val visibleProgress = progressByKey[media.stableKey]
                            MediaPoster(
                                media,
                                width = maxWidth,
                                showTitle = com.cinetrack.ui.theme.LocalCardAppearance.current.posterTitles,
                                showYear = false,
                                progress = visibleProgress,
                                selectedBorder = AccentLight.takeIf { bulkMode && media.stableKey in selectedKeys },
                                selectionMode = bulkMode,
                                onStatus = if (bulkMode) null else ({ selected: LibraryStatus -> onStatus(media, selected) }),
                                onClick = {
                                    if (bulkMode) {
                                        selectedKeys = if (media.stableKey in selectedKeys) selectedKeys - media.stableKey else selectedKeys + media.stableKey
                                    } else onMedia(media)
                                },
                            )
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
    if (showOrderSheet) {
        LibraryOrderSheet(
            initialOrder = order,
            initialAscending = ascending,
            onDismiss = { showOrderSheet = false },
            onApply = { selected, isAscending ->
                order = selected
                ascending = isAscending
                showOrderSheet = false
            },
        )
    }
    if (showBulkSheet) {
        BulkStatusSheet(
            selectedCount = selectedKeys.size,
            onDismiss = { showBulkSheet = false },
        ) { selectedStatus ->
            onBulkStatus(items.filter { it.stableKey in selectedKeys }, selectedStatus)
            selectedKeys = emptySet()
            bulkMode = false
            showBulkSheet = false
        }
    }
}

/** Library list view: poster, year and genre, title, status and progress. */
@Composable
private fun LibraryList(
    items: List<MediaCard>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    bulkMode: Boolean,
    selectedKeys: Set<String>,
    progressByKey: Map<String, Float>,
    statusLabels: Map<LibraryStatus, String>,
    onMedia: (MediaCard) -> Unit,
    topPadding: androidx.compose.ui.unit.Dp = 0.dp,
    itemMotion: Boolean = true,
    onToggle: (MediaCard) -> Unit,
) {
    LazyColumn(state = listState, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topPadding + 4.dp, bottom = 112.dp)) {
        items(items, key = MediaCard::stableKey) { media ->
            val selected = bulkMode && media.stableKey in selectedKeys
            val rowShape = RoundedCornerShape(14.dp)
            val posterSlot = com.cinetrack.ui.components.rememberPosterSlot()
            Row(
                Modifier.animateItem(fadeInSpec = if (itemMotion) Motion.enter(Motion.Medium) else null, placementSpec = if (itemMotion) Motion.gentle(androidx.compose.ui.unit.IntOffset.VisibilityThreshold) else null, fadeOutSpec = if (itemMotion) Motion.exit() else null).fillMaxWidth().clip(rowShape)
                    .then(if (selected) Modifier.border(1.5.dp, AccentLight, rowShape) else Modifier)
                    .clickable {
                        if (bulkMode) onToggle(media)
                        else {
                            com.cinetrack.ui.components.SharedPosterSource.select(posterSlot, media.stableKey)
                            onMedia(media)
                        }
                    }
                    .padding(horizontal = Spacing.xs, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.width(70.dp).height(105.dp).sharedPoster(media.stableKey, posterSlot).shadow(10.dp, RoundedCornerShape(10.dp), clip = false).clip(RoundedCornerShape(10.dp))
                        .background(Brush.linearGradient(listOf(SurfacePalette.PosterBrown, SurfacePalette.OceanMid))),
                    contentAlignment = Alignment.Center,
                ) {
                    val art = media.posterUrl ?: media.backdropUrl
                    if (!art.isNullOrBlank()) com.cinetrack.ui.components.SharedPosterImage(art, media.title, posterSlot, Modifier.fillMaxSize(), androidx.compose.ui.layout.ContentScale.Crop)
                    else Text(media.title.take(1).uppercase(), color = TextPrimary.copy(alpha = .8f), style = androidx.compose.material3.MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                }
                Spacer(Modifier.width(Spacing.lg))
                Column(Modifier.weight(1f)) {
                    val meta = listOfNotNull(media.year.takeIf(String::isNotBlank), media.genres.firstOrNull()).joinToString(" · ")
                    if (meta.isNotBlank()) Text(meta.uppercase(com.cinetrack.ui.currentLocale()), color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = com.cinetrack.ui.theme.EyebrowLetterSpacing, maxLines = 1)
                    Text(media.title, color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = Spacing.xs))
                    statusLabels[media.status]?.let { label ->
                        Row(Modifier.padding(top = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(7.dp).clip(CircleShape).background(libraryStatusColor(media.status)))
                            Spacer(Modifier.width(7.dp))
                            Text(label, color = TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    progressByKey[media.stableKey]?.takeIf { it > 0f }?.let { progress ->
                        Box(Modifier.padding(top = 10.dp).fillMaxWidth().height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = .08f))) {
                            Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(Accent))
                        }
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(.5.dp).background(Color.White.copy(alpha = .06f)))
        }
    }
}

@Composable
private fun BulkStatusSheet(selectedCount: Int, onDismiss: () -> Unit, onApply: (LibraryStatus) -> Unit) {
    SharedGlassSheet(onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg)) {
            Text(stringResource(R.string.bulk_edit_count, selectedCount), color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(Spacing.md))
            (listOf(
                LibraryStatus.WATCHING, LibraryStatus.PLAN_TO_WATCH, LibraryStatus.PAUSED, LibraryStatus.COMPLETED, LibraryStatus.DROPPED,
            ).map { it to stringResource(com.cinetrack.ui.components.libraryStatusLabel(it)) } +
                (LibraryStatus.NONE to stringResource(R.string.remove_from_library))).forEach { (status, label) ->
                Row(
                    Modifier.fillMaxWidth().padding(bottom = Spacing.sm).glass(RoundedCornerShape(Radius.Medium))
                        .clickable { onApply(status) }.padding(horizontal = Spacing.md, vertical = Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(if (status == LibraryStatus.NONE) TextMuted else libraryStatusColor(status)))
                    Spacer(Modifier.width(10.dp))
                    Text(label, color = TextPrimary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun libraryOrderLabel(order: LibraryOrder): String = stringResource(when (order) {
    LibraryOrder.TITLE -> R.string.order_title
    LibraryOrder.RATING -> R.string.order_rating
    LibraryOrder.YEAR -> R.string.order_year
    LibraryOrder.RECENTLY_WATCHED -> R.string.order_recently_watched
    LibraryOrder.RECENTLY_ADDED -> R.string.order_recently_added
})

@Composable
private fun LibraryOrderSheet(
    initialOrder: LibraryOrder,
    initialAscending: Boolean,
    onDismiss: () -> Unit,
    onApply: (LibraryOrder, Boolean) -> Unit,
) {
    var selected by remember(initialOrder) { mutableStateOf(initialOrder) }
    var ascending by remember(initialAscending) { mutableStateOf(initialAscending) }
    SharedGlassSheet(onDismiss) {
        Column(Modifier.padding(horizontal = Spacing.lg)) {
            com.cinetrack.ui.components.SectionHeader(stringResource(R.string.sort_by))
            Spacer(Modifier.height(Spacing.md))
            LibraryOrder.entries.forEach { option ->
                val active = selected == option
                Row(
                    Modifier.fillMaxWidth().padding(bottom = Spacing.sm).glass(RoundedCornerShape(Radius.Medium))
                        .background(if (active) Accent.copy(alpha = .18f) else Color.Transparent)
                        .clickable {
                            selected = option
                            if (option == LibraryOrder.RECENTLY_WATCHED || option == LibraryOrder.RECENTLY_ADDED) {
                                ascending = false
                            }
                        }.padding(horizontal = Spacing.lg, vertical = Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        libraryOrderLabel(option),
                        color = if (active) TextPrimary else TextSecondary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        Modifier.size(23.dp).clip(CircleShape).border(1.dp, if (active) AccentLight else TextMuted, CircleShape)
                            .background(if (active) Accent.copy(alpha = .8f) else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) { if (active) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(15.dp)) }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 2.dp, bottom = Spacing.md).glass(RoundedCornerShape(Radius.Medium))
                    .clickable { ascending = !ascending }.padding(horizontal = Spacing.lg, vertical = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (ascending) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward, null, tint = AccentLight, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(10.dp))
                Text(if (ascending) stringResource(R.string.ascending) else stringResource(R.string.descending), color = TextPrimary, fontWeight = FontWeight.Bold)
            }
            PrimaryAction(stringResource(R.string.apply), Icons.Filled.FilterList, Modifier.fillMaxWidth()) { onApply(selected, ascending) }
        }
    }
}

/** One Library page: a media type and a status filter (null for all). */
private data class LibraryTab(val type: MediaType, val status: LibraryStatus?)
