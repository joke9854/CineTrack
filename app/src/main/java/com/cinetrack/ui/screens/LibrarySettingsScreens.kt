@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.cinetrack.ui.screens

import com.cinetrack.ui.components.tonalPill
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.foundation.layout.widthIn
import androidx.compose.animation.core.tween
import com.cinetrack.ui.components.directionalSlide
import com.cinetrack.ui.components.rememberDirectionalSlide
import com.cinetrack.ui.components.slidingTabIndicator
import com.cinetrack.ui.components.tabSpan
import com.cinetrack.ui.components.rememberTabIndicator
import com.cinetrack.ui.components.sharedPoster
import com.cinetrack.ui.components.softTab
import androidx.compose.ui.graphics.graphicsLayer
import com.cinetrack.ui.components.collapsingTitle
import com.cinetrack.ui.components.pinnedHeader
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.shadow
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.Brush
import com.cinetrack.ui.components.liquidGlass
import com.cinetrack.ui.components.liquidGlassIcon
import com.cinetrack.ui.components.liquidGlassPill
import androidx.compose.ui.graphics.compositeOver
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.PlaylistAddCheck
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.appcompat.app.AppCompatDelegate
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.LocaleListCompat
import androidx.core.content.ContextCompat
import com.cinetrack.BuildConfig
import com.cinetrack.R
import com.cinetrack.data.update.AppUpdateState
import com.cinetrack.data.update.AppChangelogState
import com.cinetrack.domain.AppUiState
import com.cinetrack.domain.FloppyUiState
import com.cinetrack.domain.FloppyConnectionError
import com.cinetrack.domain.FloppyConnectionStage
import com.cinetrack.domain.FloppyConnectionUiState
import com.cinetrack.domain.LibraryStatus
import com.cinetrack.domain.LibraryArtworkRefreshProgress
import com.cinetrack.domain.LibraryArtworkRefreshStage
import com.cinetrack.domain.FloppyBootstrapStage
import com.cinetrack.domain.FloppyBootstrapProgress
import com.cinetrack.domain.allowsRetry
import com.cinetrack.domain.isManagedActive
import com.cinetrack.domain.MediaCard
import com.cinetrack.domain.MediaType
import com.cinetrack.domain.RailIds
import com.cinetrack.domain.SyncProgress
import com.cinetrack.domain.SyncConflictChoice
import com.cinetrack.domain.SyncOperationCard
import com.cinetrack.domain.SyncDeliveryCard
import com.cinetrack.domain.SyncOperationStatus
import com.cinetrack.ui.CineTrackViewModel
import com.cinetrack.ui.components.AdaptiveBackground
import com.cinetrack.ui.components.GlassBackButton
import com.cinetrack.ui.components.BrandMark
import com.cinetrack.ui.components.GlassDivider
import com.cinetrack.ui.components.MediaPoster
import com.cinetrack.ui.components.PageTitle
import com.cinetrack.ui.components.PrimaryAction
import com.cinetrack.ui.components.SharedGlassSheet
import com.cinetrack.ui.components.glass
import com.cinetrack.ui.components.glassIcon
import com.cinetrack.ui.components.libraryStatusColor
import com.cinetrack.ui.components.rememberUiAction
import com.cinetrack.ui.theme.Accent
import com.cinetrack.ui.theme.AccentLight
import com.cinetrack.ui.theme.Success
import com.cinetrack.ui.theme.StatusDropped
import com.cinetrack.ui.theme.StatusPaused
import com.cinetrack.ui.theme.StatusPlanned
import com.cinetrack.ui.theme.StatusWatching
import com.cinetrack.ui.theme.TextMuted
import com.cinetrack.ui.theme.TextPrimary
import com.cinetrack.ui.theme.TextSecondary
import java.util.Locale

object SettingsPages {
    const val Streaming = "streaming"
    const val Providers = "providers"
    const val Sync = "sync"
    const val SyncOperations = "sync-operations"
    const val Integrations = "integrations"
    const val Notifications = "notifications"
    const val Appearance = "appearance"
    const val Language = "language"
    const val Ratings = "ratings"
    const val Export = "export"
    const val About = "about"
    const val ServiceSimkl = "service-simkl"
    const val ServiceFloppy = "service-floppy"
    const val ServiceTmdb = "service-tmdb"
    const val ServiceMdblist = "service-mdblist"
    const val ServiceTrakt = "service-trakt"
    const val Logs = "logs"
}

private const val FLOPPY_API_GUIDE_URL = "https://github.com/dannyvfilms/Floppy/wiki/7.-API-and-MCP-Server"
private const val TMDB_API_KEY_URL = "https://www.themoviedb.org/settings/api"
private const val MDBLIST_API_KEY_URL = "https://mdblist.com/preferences"
private const val TRAKT_API_APPS_URL = "https://trakt.tv/oauth/applications"

private enum class LibraryOrder { TITLE, RATING, YEAR, RECENTLY_WATCHED, RECENTLY_ADDED }

@Composable
fun LibraryScreen(
    state: AppUiState,
    onSearch: () -> Unit,
    onMedia: (MediaCard) -> Unit,
    onStatus: (MediaCard, LibraryStatus) -> Unit,
    onRefreshArtwork: () -> Unit,
    onDismissArtworkProgress: () -> Unit = {},
    onCompactNav: (Boolean) -> Unit,
) {
    var type by rememberSaveable { mutableStateOf(MediaType.TV) }
    var status by rememberSaveable { mutableStateOf<LibraryStatus?>(null) }
    var order by rememberSaveable { mutableStateOf(LibraryOrder.RECENTLY_ADDED) }
    var ascending by rememberSaveable { mutableStateOf(false) }
    var showOrderSheet by rememberSaveable { mutableStateOf(false) }
    var bulkMode by rememberSaveable { mutableStateOf(false) }
    var selectedKeys by remember { mutableStateOf(emptySet<String>()) }
    var showBulkSheet by rememberSaveable { mutableStateOf(false) }
    var listView by rememberSaveable { mutableStateOf(false) }
    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()
    NavCollapseGridEffect(gridState, onCompactNav)
    NavCollapseEffect(listState, onCompactNav)
    val typeItems = state.rails[RailIds.LIBRARY].orEmpty().filter { it.type == type }
    val filteredItems = typeItems.filter { status == null || it.status == status }
    val watchedAtByKey = remember(state.history) {
        state.history.groupBy { it.media.stableKey }.mapValues { (_, events) ->
            events.maxOfOrNull { it.timestamp }.orEmpty()
        }
    }
    val items = remember(filteredItems, order, ascending, watchedAtByKey) {
        val sorted = when (order) {
            LibraryOrder.TITLE -> filteredItems.sortedBy { it.title.lowercase() }
            LibraryOrder.RATING -> filteredItems.sortedBy { it.score ?: -1.0 }
            LibraryOrder.YEAR -> filteredItems.sortedBy { it.year.toIntOrNull() ?: 0 }
            LibraryOrder.RECENTLY_WATCHED -> filteredItems.sortedBy { watchedAtByKey[it.stableKey].orEmpty() }
            LibraryOrder.RECENTLY_ADDED -> filteredItems.sortedBy { it.libraryUpdatedAt ?: 0L }
        }
        if (ascending) sorted else sorted.reversed()
    }
    val filterChoices = listOf(
        Triple<LibraryStatus?, String, Color>(null, stringResource(R.string.all), Accent),
        Triple(LibraryStatus.WATCHING, stringResource(R.string.in_progress), libraryStatusColor(LibraryStatus.WATCHING)),
        Triple(LibraryStatus.PLAN_TO_WATCH, stringResource(R.string.plan_to_watch), libraryStatusColor(LibraryStatus.PLAN_TO_WATCH)),
        Triple(LibraryStatus.PAUSED, stringResource(R.string.paused), libraryStatusColor(LibraryStatus.PAUSED)),
        Triple(LibraryStatus.COMPLETED, stringResource(R.string.completed), libraryStatusColor(LibraryStatus.COMPLETED)),
        Triple(LibraryStatus.DROPPED, stringResource(R.string.dropped), libraryStatusColor(LibraryStatus.DROPPED)),
    )
    val progressByKey = remember(state.playbackTv, state.playbackMovies) {
        (state.playbackTv + state.playbackMovies).associate { it.media.stableKey to it.progress }
    }
    LaunchedEffect(type, status, bulkMode) { selectedKeys = emptySet() }
    val backgroundMedia = state.rails[RailIds.LIBRARY].orEmpty().minByOrNull { it.stableKey }
    AdaptiveBackground(artworkUrl = backgroundMedia?.posterUrl) {
        LongPullRefreshContainer(
            refreshing = state.libraryArtworkRefreshing,
            onRefresh = onRefreshArtwork,
            enabled = state.tmdbApiConfigured,
        ) {
        Box(Modifier.fillMaxSize().statusBarsPadding()) {
            val statusIndex = filterChoices.indexOfFirst { it.first == status }.coerceAtLeast(0)
            // Each tab keeps its own scroll position. The grid would otherwise keep the
            // first visible item by key, which lands mid-list when returning to "Tutti".
            val tabScroll = remember { mutableMapOf<String, Pair<Int, Int>>() }
            var shownTab by remember { mutableStateOf("$type:$status") }
            val currentTab = "$type:$status"
            if (shownTab != currentTab) {
                tabScroll[shownTab] = if (listView) listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
                else gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset
                shownTab = currentTab
                val (index, offset) = tabScroll[currentTab] ?: (0 to 0)
                gridState.requestScrollToItem(index, offset)
                listState.requestScrollToItem(index, offset)
            }
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
            val typeSlide = rememberDirectionalSlide(if (type == MediaType.TV) 0 else 1)
            val statusSlide = rememberDirectionalSlide(statusIndex)
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
            val headerScroll = remember(listView) {
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
            Row(Modifier.fillMaxWidth().onSizeChanged { titlePx = it.height }.collapsingTitle({ titlePx }, headerScroll).padding(start = com.cinetrack.ui.theme.Spacing.xl, end = com.cinetrack.ui.theme.Spacing.lg, top = com.cinetrack.ui.theme.Spacing.lg, bottom = com.cinetrack.ui.theme.Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                listOf(MediaType.MOVIE, MediaType.TV).forEach { item ->
                    val selected = item == type
                    val headlineColor by androidx.compose.animation.animateColorAsState(
                        if (selected) TextPrimary else TextPrimary.copy(alpha = .42f),
                        tween(com.cinetrack.ui.theme.Motion.Extended),
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
                Row(Modifier.height(44.dp).liquidGlassPill().padding(horizontal = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    listOf(true to Icons.Filled.ViewList, false to Icons.Filled.GridView).forEach { (list, icon) ->
                        val active = listView == list
                        Box(
                            Modifier.size(38.dp).clip(CircleShape).background(if (active) Accent.copy(alpha = .30f) else Color.Transparent)
                                .selectable(selected = active, role = Role.RadioButton) { listView = list },
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
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = com.cinetrack.ui.theme.Spacing.xl)
                    .slidingTabIndicator(indicator, statusIndex),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                filterChoices.forEachIndexed { index, (value, label, _) ->
                    val selected = status == value
                    val count = if (value == null) typeItems.size else typeItems.count { it.status == value }
                    val labelColor by androidx.compose.animation.animateColorAsState(
                        if (selected) TextPrimary else TextPrimary.copy(alpha = .5f),
                        tween(com.cinetrack.ui.theme.Motion.Long),
                        label = "libraryTabLabel",
                    )
                    Row(
                        Modifier.tabSpan(indicator, index)
                            .softTab(selected) { if (selected) scrollToTop() else status = value }
                            .heightIn(min = 44.dp)
                            .padding(vertical = 12.dp),
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
                Modifier.fillMaxWidth().padding(start = com.cinetrack.ui.theme.Spacing.xl, end = com.cinetrack.ui.theme.Spacing.lg, top = com.cinetrack.ui.theme.Spacing.md, bottom = com.cinetrack.ui.theme.Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    Modifier.height(40.dp).liquidGlassPill().clickable(onClick = rememberUiAction { showOrderSheet = true }).padding(start = 14.dp, end = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(libraryOrderLabel(order), color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Filled.ExpandMore, stringResource(R.string.sort_by), tint = TextSecondary, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = rememberUiAction(onSearch), modifier = Modifier.size(48.dp).liquidGlassIcon()) {
                    Icon(Icons.Filled.Search, stringResource(R.string.accessibility_search), tint = TextSecondary, modifier = Modifier.size(19.dp))
                }
                if (state.libraryArtworkProgress != null && !state.libraryArtworkProgressVisible) {
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
                    Modifier.fillMaxWidth().padding(horizontal = com.cinetrack.ui.theme.Spacing.xl, vertical = com.cinetrack.ui.theme.Spacing.sm),
                    enabled = selectedKeys.isNotEmpty(),
                ) { showBulkSheet = true }
            }
            }
            val headerHeight = with(androidx.compose.ui.platform.LocalDensity.current) { headerPx.toDp() }
            Box(Modifier.fillMaxSize().directionalSlide(typeSlide, statusSlide)) {
            if (items.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(top = headerHeight), contentAlignment = Alignment.Center) {
                    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.AutoAwesome, null, tint = AccentLight, modifier = Modifier.size(40.dp))
                        Text(stringResource(if (status != null) R.string.library_no_matches else R.string.empty_library), color = TextPrimary, fontWeight = FontWeight.Bold, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                        Text(stringResource(if (status != null) R.string.library_no_matches_description else R.string.empty_library_description), color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                        if (status != null) TextButton(onClick = { status = null }) { Text(stringResource(R.string.clear_filters), color = TextPrimary) }
                        else TextButton(onClick = onSearch) { Text(stringResource(R.string.accessibility_search), color = TextPrimary) }
                    }
                }
            } else if (listView) {
                val statusLabels = filterChoices.mapNotNull { (value, label, _) -> value?.let { it to label } }.toMap()
                LibraryList(items, listState, bulkMode, selectedKeys, progressByKey, statusLabels, onMedia, topPadding = headerHeight) { media ->
                    selectedKeys = if (media.stableKey in selectedKeys) selectedKeys - media.stableKey else selectedKeys + media.stableKey
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(com.cinetrack.domain.CardAppearance.gridColumns(state.cardDensity)),
                    state = gridState,
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = headerHeight + 12.dp, bottom = 112.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(items, key = MediaCard::stableKey) { media ->
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            val visibleProgress = progressByKey[media.stableKey]
                            MediaPoster(
                                media,
                                width = maxWidth,
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
            items.filter { it.stableKey in selectedKeys }.forEach { onStatus(it, selectedStatus) }
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
    onToggle: (MediaCard) -> Unit,
) {
    LazyColumn(state = listState, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topPadding + 4.dp, bottom = 112.dp)) {
        items(items, key = MediaCard::stableKey) { media ->
            val selected = bulkMode && media.stableKey in selectedKeys
            val rowShape = RoundedCornerShape(14.dp)
            val posterSlot = com.cinetrack.ui.components.rememberPosterSlot()
            Row(
                Modifier.fillMaxWidth().clip(rowShape)
                    .then(if (selected) Modifier.border(1.5.dp, AccentLight, rowShape) else Modifier)
                    .clickable {
                        if (bulkMode) onToggle(media)
                        else {
                            com.cinetrack.ui.components.SharedPosterSource.select(posterSlot, media.stableKey)
                            onMedia(media)
                        }
                    }
                    .padding(horizontal = 4.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.width(70.dp).height(105.dp).sharedPoster(media.stableKey, posterSlot).shadow(10.dp, RoundedCornerShape(10.dp), clip = false).clip(RoundedCornerShape(10.dp))
                        .background(Brush.linearGradient(listOf(com.cinetrack.ui.theme.SurfacePalette.PosterBrown, com.cinetrack.ui.theme.SurfacePalette.OceanMid))),
                    contentAlignment = Alignment.Center,
                ) {
                    val art = media.posterUrl ?: media.backdropUrl
                    if (!art.isNullOrBlank()) com.cinetrack.ui.components.SharedPosterImage(art, media.title, posterSlot, Modifier.fillMaxSize(), androidx.compose.ui.layout.ContentScale.Crop)
                    else Text(media.title.take(1).uppercase(), color = TextPrimary.copy(alpha = .8f), style = androidx.compose.material3.MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    val meta = listOfNotNull(media.year.takeIf(String::isNotBlank), media.genres.firstOrNull()).joinToString(" · ")
                    if (meta.isNotBlank()) Text(meta.uppercase(com.cinetrack.ui.currentLocale()), color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, maxLines = 1)
                    Text(media.title, color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                    statusLabels[media.status]?.let { label ->
                        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
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
        Column(Modifier.fillMaxWidth().padding(horizontal = com.cinetrack.ui.theme.Spacing.lg)) {
            Text(stringResource(R.string.bulk_edit_count, selectedCount), color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(12.dp))
            listOf(
                LibraryStatus.WATCHING to stringResource(R.string.in_progress),
                LibraryStatus.PLAN_TO_WATCH to stringResource(R.string.plan_to_watch),
                LibraryStatus.PAUSED to stringResource(R.string.paused),
                LibraryStatus.COMPLETED to stringResource(R.string.completed),
                LibraryStatus.DROPPED to stringResource(R.string.dropped),
                LibraryStatus.NONE to stringResource(R.string.remove_from_library),
            ).forEach { (status, label) ->
                Row(
                    Modifier.fillMaxWidth().padding(bottom = com.cinetrack.ui.theme.Spacing.sm).glass(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Medium))
                        .clickable { onApply(status) }.padding(horizontal = com.cinetrack.ui.theme.Spacing.md, vertical = com.cinetrack.ui.theme.Spacing.md),
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
        Column(Modifier.padding(horizontal = com.cinetrack.ui.theme.Spacing.lg)) {
            com.cinetrack.ui.components.SectionHeader(stringResource(R.string.sort_by))
            Spacer(Modifier.height(12.dp))
            LibraryOrder.entries.forEach { option ->
                val active = selected == option
                Row(
                    Modifier.fillMaxWidth().padding(bottom = com.cinetrack.ui.theme.Spacing.sm).glass(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Medium))
                        .background(if (active) Accent.copy(alpha = .18f) else Color.Transparent)
                        .clickable {
                            selected = option
                            if (option == LibraryOrder.RECENTLY_WATCHED || option == LibraryOrder.RECENTLY_ADDED) {
                                ascending = false
                            }
                        }.padding(horizontal = com.cinetrack.ui.theme.Spacing.lg, vertical = com.cinetrack.ui.theme.Spacing.md),
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
                Modifier.fillMaxWidth().padding(top = 2.dp, bottom = com.cinetrack.ui.theme.Spacing.md).glass(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Medium))
                    .clickable { ascending = !ascending }.padding(horizontal = com.cinetrack.ui.theme.Spacing.lg, vertical = com.cinetrack.ui.theme.Spacing.md),
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

private data class SettingsItem(val page: String, val title: String, val subtitle: String, val icon: ImageVector)

@Composable
fun SettingsScreen(state: AppUiState, onPage: (String) -> Unit, onCompactNav: (Boolean) -> Unit) {
    val listState = rememberLazyListState()
    NavCollapseEffect(listState, onCompactNav)
    val accentName = stringResource(
        when (com.cinetrack.ui.theme.AccentPalette.normalize(state.uiAccent)) {
            "green" -> R.string.color_green
            "silver" -> R.string.color_silver
            "champagne" -> R.string.color_champagne
            else -> R.string.color_blue
        },
    )
    val layoutName = stringResource(if (state.discoverLayout == "b") R.string.discover_layout_mosaic else R.string.discover_layout_classic)
    val preferences = listOf(
        SettingsItem(SettingsPages.Appearance, stringResource(R.string.appearance), "$accentName · $layoutName", Icons.Filled.Palette),
        SettingsItem(SettingsPages.Notifications, stringResource(R.string.notifications), stringResource(R.string.notifications_summary), Icons.Filled.Notifications),
        SettingsItem(SettingsPages.Language, stringResource(R.string.language), currentAppLanguageLabel(), Icons.Filled.Language),
    )
    val dataAndDiagnostics = listOf(
        SettingsItem(SettingsPages.Export, stringResource(R.string.export_data), stringResource(R.string.export_data_summary), Icons.Filled.Download),
        SettingsItem(SettingsPages.SyncOperations, stringResource(R.string.sync_operations), stringResource(R.string.sync_health_summary, state.sync.report.pendingLocalChanges, state.sync.report.failedOperations, state.sync.report.conflicts), Icons.Filled.SyncProblem),
        SettingsItem(SettingsPages.Logs, stringResource(R.string.logs), stringResource(R.string.logs_summary), Icons.Filled.BugReport),
    )
    val more = listOf(SettingsItem(SettingsPages.About, stringResource(R.string.about_app), stringResource(R.string.version_label, BuildConfig.VERSION_NAME), Icons.Filled.Info))
    val configured = stringResource(R.string.configured)
    val notConfigured = stringResource(R.string.not_configured)
    val connected = stringResource(R.string.connected)
    val notConnected = stringResource(R.string.not_connected)
    val services = listOf(
        ServiceStatus("TMDB", SettingsPages.ServiceTmdb, state.tmdbApiConfigured, if (state.tmdbApiConfigured) configured else notConfigured),
        ServiceStatus(
            "Simkl", SettingsPages.ServiceSimkl, state.simklConnected,
            when {
                state.simklConnected && !state.simklUsername.isNullOrBlank() -> stringResource(R.string.simkl_connected_as, state.simklUsername.orEmpty())
                state.simklConnected -> connected
                state.simklReconnectReason != null -> stringResource(R.string.simkl_reconnect_needed)
                else -> notConnected
            },
        ),
        ServiceStatus("Trakt", SettingsPages.ServiceTrakt, state.traktConfigured, if (state.traktConfigured) configured else notConfigured),
        ServiceStatus("Floppy", SettingsPages.ServiceFloppy, state.floppyConnected, if (state.floppyConnected) floppyConnectedLabel(state) else notConnected),
        ServiceStatus("MDBList", SettingsPages.ServiceMdblist, state.mdbListApiConfigured, if (state.mdbListApiConfigured) configured else notConfigured),
    )
    // Catalogue, tracking and server get a full brand card; the two add-ons share a row.
    val featured = services.filter { it.name in setOf("TMDB", "Simkl", "Floppy") }.let { list ->
        listOf("TMDB", "Simkl", "Floppy").mapNotNull { name -> list.firstOrNull { it.name == name } }
    }
    val compact = services.filter { it.name in setOf("Trakt", "MDBList") }
    AdaptiveBackground {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(bottom = 112.dp),
        ) {
            item { PageTitle(stringResource(R.string.settings), Modifier.padding(start = com.cinetrack.ui.theme.Spacing.xl, end = com.cinetrack.ui.theme.Spacing.xl, top = com.cinetrack.ui.theme.Spacing.lg, bottom = com.cinetrack.ui.theme.Spacing.md)) }
            // Services first: each one's state at a glance, one tap to set it up.
            item {
                Column(Modifier.padding(horizontal = com.cinetrack.ui.theme.Spacing.xl), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SettingsGroupLabel(stringResource(R.string.settings_services))
                    featured.forEach { ServiceBrandCard(it, onPage) }
                    Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        compact.forEach { ServiceStatusCard(it, Modifier.weight(1f).fillMaxHeight(), onPage) }
                    }
                }
            }
            item { SettingsGroup(stringResource(R.string.settings_preferences), preferences, onPage) }
            item { SettingsGroup(stringResource(R.string.settings_data), dataAndDiagnostics, onPage) }
            item { SettingsGroup(stringResource(R.string.settings_more), more, onPage) }
        }
    }
}

private data class ServiceStatus(val name: String, val page: String, val active: Boolean, val status: String, val icon: ImageVector? = null)

@Composable
private fun ServiceStatusCard(service: ServiceStatus, modifier: Modifier, onPage: (String) -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    val clickAction = rememberUiAction { onPage(service.page) }
    val description = "${service.name}: ${service.status}"
    Column(
        modifier.glass(shape).clickable(onClick = clickAction)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (service.icon != null) {
                Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(Accent.copy(alpha = .20f)), contentAlignment = Alignment.Center) {
                    Icon(service.icon, null, tint = AccentLight, modifier = Modifier.size(18.dp))
                }
            } else {
                ServiceLogo(service.name, size = 32.dp)
            }
            Spacer(Modifier.weight(1f))
            StatusDot(service.active)
        }
        Spacer(Modifier.height(10.dp))
        Text(service.name, color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(service.status, color = if (service.active) TextSecondary else TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** Full-width card in the service's own colours, its mark repeated large and faint on the right. */
@Composable
private fun ServiceBrandCard(service: ServiceStatus, onPage: (String) -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    val (brush, logo) = when (service.name) {
        "TMDB" -> Brush.linearGradient(listOf(Color(0xFF0D253F), Color(0xFF0B5C7A), Color(0xFF01B4E4))) to R.drawable.ic_service_tmdb
        "Simkl" -> Brush.linearGradient(listOf(Color(0xFF151515), Color(0xFF262626), Color(0xFF141414))) to R.drawable.ic_service_simkl
        else -> Brush.linearGradient(listOf(Color(0xFF0E1B33), Color(0xFF1C2F5C), Color(0xFF14204A))) to R.drawable.floppy_app_icon
    }
    val clickAction = rememberUiAction { onPage(service.page) }
    val description = "${service.name}: ${service.status}"
    Box(
        Modifier.fillMaxWidth().height(116.dp).clip(shape).background(brush)
            .border(.6.dp, Color.White.copy(alpha = .10f), shape)
            .clickable(onClick = clickAction)
            .semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Image(
            painterResource(logo),
            null,
            // Rounded like an app icon, so the faint mark has no hard square corners.
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 44.dp).size(84.dp)
                .clip(RoundedCornerShape(24.dp)).graphicsLayer { alpha = .13f },
        )
        Column(Modifier.align(Alignment.CenterStart).padding(start = 20.dp, end = 120.dp)) {
            if (service.name == "Floppy") {
                // Floppy's official wordmark, mascot included.
                Image(painterResource(R.drawable.floppy_wordmark), "Floppy", modifier = Modifier.height(42.dp))
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ServiceLogo(service.name, size = 34.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(service.name, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
                StatusDot(service.active)
                Spacer(Modifier.width(7.dp))
                Text(service.status, color = Color.White.copy(alpha = .82f), style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Icon(Icons.Filled.ChevronRight, null, tint = Color.White.copy(alpha = .8f), modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp).size(24.dp))
    }
}

@Composable
private fun StatusDot(active: Boolean) {
    Box(
        Modifier.size(9.dp).clip(CircleShape)
            .background(if (active) Color(0xFF5DCAA5) else TextMuted.copy(alpha = .45f)),
    )
}

@Composable
private fun SettingsGroupLabel(label: String) {
    Text(
        label.uppercase(com.cinetrack.ui.currentLocale()),
        color = TextMuted,
        style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.6.sp,
        modifier = Modifier.padding(start = 4.dp, top = com.cinetrack.ui.theme.Spacing.sm),
    )
}

@Composable
private fun SettingsGroup(label: String, items: List<SettingsItem>, onPage: (String) -> Unit) {
    Column(Modifier.padding(horizontal = com.cinetrack.ui.theme.Spacing.xl, vertical = com.cinetrack.ui.theme.Spacing.md)) {
        SettingsGroupLabel(label)
        Spacer(Modifier.height(com.cinetrack.ui.theme.Spacing.sm))
        Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(18.dp)).padding(vertical = 4.dp)) {
            items.forEach { item ->
                SettingsRow(item, onPage)
            }
        }
    }
}

@Composable
private fun SettingsRow(item: SettingsItem, onPage: (String) -> Unit) {
    val clickAction = rememberUiAction { onPage(item.page) }
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = clickAction).padding(horizontal = com.cinetrack.ui.theme.Spacing.lg, vertical = com.cinetrack.ui.theme.Spacing.md), verticalAlignment = Alignment.CenterVertically) {
        Icon(item.icon, null, tint = TextSecondary, modifier = Modifier.size(21.dp))
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, color = TextPrimary, fontWeight = FontWeight.Bold, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
            Text(item.subtitle, color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Filled.ChevronRight, null, tint = TextMuted, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun SettingsDetailScreen(
    page: String,
    state: AppUiState,
    viewModel: CineTrackViewModel,
    onBack: () -> Unit,
    onPage: (String) -> Unit,
) {
    if (page == SettingsPages.Providers) {
        StreamingProvidersScreen(state, viewModel, onBack)
        return
    }
    val context = LocalContext.current
    val title = when (page) {
        SettingsPages.Streaming -> stringResource(R.string.streaming_services)
        SettingsPages.Sync -> stringResource(R.string.synchronization)
        SettingsPages.SyncOperations -> stringResource(R.string.sync_operations)
        SettingsPages.Integrations -> stringResource(R.string.integrations)
        SettingsPages.Notifications -> stringResource(R.string.notifications)
        SettingsPages.Appearance -> stringResource(R.string.appearance)
        SettingsPages.Language -> stringResource(R.string.language)
        SettingsPages.Ratings -> stringResource(R.string.rating_sources)
        SettingsPages.Export -> stringResource(R.string.export_data)
        SettingsPages.ServiceSimkl -> "Simkl"
        SettingsPages.ServiceFloppy -> "Floppy"
        SettingsPages.ServiceTmdb -> "TMDB"
        SettingsPages.ServiceMdblist -> "MDBList"
        SettingsPages.ServiceTrakt -> "Trakt"
        SettingsPages.Logs -> stringResource(R.string.logs)
        else -> stringResource(R.string.about_app)
    }
    val brand = serviceBrand(page)
    if (page == SettingsPages.Appearance) {
        // Aspetto keeps its preview fixed under the title while the choices scroll.
        AdaptiveBackground { AppearancePage(title, state, viewModel, onBack) }
        return
    }
    AdaptiveBackground {
        LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(bottom = 40.dp)) {
            item {
                if (brand != null) {
                    // Service pages open on a header in the service's own colours.
                    ServiceHero(brand, state, onBack)
                } else {
                    Row(Modifier.fillMaxWidth().padding(start = com.cinetrack.ui.theme.Spacing.xl, end = com.cinetrack.ui.theme.Spacing.xl, top = com.cinetrack.ui.theme.Spacing.lg, bottom = com.cinetrack.ui.theme.Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
                        GlassBackButton(onClick = onBack)
                        Spacer(Modifier.size(8.dp))
                        PageTitle(title, Modifier.weight(1f))
                    }
                }
            }
            item {
                when (page) {
                    SettingsPages.Streaming -> StreamingSettings(state, viewModel, onPage)
                    SettingsPages.Sync -> TrackingSettingsScreen(state, viewModel, { viewModel.beginSimklLogin(context) })
                    SettingsPages.SyncOperations -> SyncOperationsScreen(viewModel)
                    SettingsPages.Integrations -> IntegrationsSettings(state, onPage)
                    SettingsPages.ServiceSimkl -> SyncSettingsHost(state, viewModel, { viewModel.beginSimklLogin(context) })
                    SettingsPages.ServiceFloppy -> FloppySettingsHost(state, viewModel)
                    SettingsPages.ServiceTmdb -> Column {
                        CredentialBlock("TMDB", state.tmdbApiConfigured, viewModel::verifyAndSetTmdbApiKey)
                        MetadataSettings(state, viewModel)
                        ContentRegionSettings(state, viewModel, onPage)
                    }
                    SettingsPages.ServiceMdblist -> Column {
                        CredentialBlock("MDBList", state.mdbListApiConfigured, viewModel::verifyAndSetMdbListApiKey)
                        RatingSettings(state, viewModel)
                    }
                    SettingsPages.ServiceTrakt -> Column {
                        CredentialBlock("Trakt", state.traktConfigured, viewModel::verifyAndSetTraktClientId)
                        SettingsSection(stringResource(R.string.section_what_it_adds)) {
                            KitRow(Icons.Filled.Schedule, stringResource(R.string.trakt_adds_times), stringResource(R.string.trakt_adds_times_sub))
                            KitRow(Icons.Filled.CalendarMonth, stringResource(R.string.trakt_adds_calendar), stringResource(R.string.trakt_adds_calendar_sub))
                        }
                    }
                    SettingsPages.Notifications -> NotificationSettings(state, viewModel)
                    SettingsPages.Language -> LanguageSettings(viewModel)
                    SettingsPages.Ratings -> RatingSettings(state, viewModel)
                    SettingsPages.Export -> ExportSettings(viewModel)
                    SettingsPages.Logs -> LogsSettings(viewModel)
                    else -> AboutSettings(viewModel)
                }
            }
        }
    }
}

@Composable
internal fun SyncSettingsHost(state: AppUiState, viewModel: CineTrackViewModel, onConnect: () -> Unit) {
    val sync by viewModel.syncProgress.collectAsStateWithLifecycle()
    SyncSettings(state, sync, viewModel, onConnect)
}

@Composable
private fun SyncSettings(
    state: AppUiState,
    sync: SyncProgress,
    viewModel: CineTrackViewModel,
    onConnect: () -> Unit,
) {
    LaunchedEffect(state.simklConnected, state.simklUsername) {
        if (state.simklConnected && state.simklUsername == null) viewModel.loadSimklUsername()
    }
    val library = state.rails[RailIds.LIBRARY].orEmpty()
    if (!state.simklConnected) when (state.simklReconnectReason) {
        "upgrade" -> KitNotice(stringResource(R.string.simkl_reconnect_notice))
        "signed_out" -> KitNotice(stringResource(R.string.simkl_signed_out_notice))
    }
    if (state.simklConnected && state.simklFirstSyncPending) KitNotice(stringResource(R.string.simkl_first_sync_pending))
    if (state.simklConnected) {
        KitStatTiles(
            listOf(
                library.count { it.type == MediaType.TV }.toString() to stringResource(R.string.stat_shows),
                library.count { it.type == MediaType.MOVIE }.toString() to stringResource(R.string.stat_movies),
                (sync.report.lastIncrementalSync ?: sync.report.lastFullSync)?.let { relativeSyncTime(it) } .orEmpty().ifBlank { stringResource(R.string.never) } to stringResource(R.string.stat_last_sync),
            ),
        )
    }
    KitPrimaryButton(
        text = when {
            !state.simklConnected -> stringResource(R.string.connect_simkl)
            sync.report.failedOperations > 0 -> stringResource(R.string.retry_failed_sync)
            else -> stringResource(R.string.sync_now)
        },
        icon = if (state.simklConnected) Icons.Filled.Refresh else Icons.Filled.Link,
        color = Color(0xFF3B8F5A),
        onClick = if (state.simklConnected) viewModel::sync else onConnect,
    )
    SettingsSection(stringResource(R.string.synchronization)) {
        KitToggleRow(Icons.Filled.Refresh, stringResource(R.string.background_sync), stringResource(R.string.background_sync_sub), state.backgroundSync, viewModel::setBackgroundSync)
        KitToggleRow(Icons.Filled.Wifi, stringResource(R.string.wifi_only), stringResource(R.string.wifi_only_sub), state.wifiOnly, viewModel::setWifiOnly)
    }
    if (sync.report.lastIncrementalSync != null || sync.report.failedOperations > 0) {
        val report = sync.report
        SettingsSection(stringResource(R.string.sync_activity)) {
            KitRow(Icons.Filled.Download, stringResource(R.string.downloaded), value = report.downloaded.toString())
            KitRow(Icons.Filled.Upload, stringResource(R.string.uploaded), value = report.uploaded.toString())
            KitRow(Icons.Filled.SwapVert, stringResource(R.string.added_removed), value = "${report.added} / ${report.removed}")
            KitRow(Icons.Filled.Schedule, stringResource(R.string.pending_local_changes), value = report.pendingLocalChanges.toString())
            KitRow(Icons.Filled.ErrorOutline, stringResource(R.string.failed_operations), value = report.failedOperations.toString())
            KitRow(Icons.Filled.SyncProblem, stringResource(R.string.sync_conflicts), value = report.conflicts.toString())
            report.lastFullSync?.let {
                KitRow(Icons.Filled.History, stringResource(R.string.last_full_sync), value = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(it)))
            }
        }
    }
    state.error?.takeIf(String::isNotBlank)?.let { KitNotice(it) }
    sync.message?.takeIf { sync.stage == com.cinetrack.domain.SyncStage.ERROR && it.isNotBlank() }?.let { KitNotice(it) }
    if (state.simklConnected) {
        ConfirmedDangerText(
            text = stringResource(R.string.disconnect_account),
            title = stringResource(R.string.disconnect_simkl_title),
            body = stringResource(R.string.disconnect_simkl_body),
            onConfirm = viewModel::disconnectSimkl,
        )
    }
}

@Composable
internal fun SyncOperationsSettings(viewModel: CineTrackViewModel) {
    val operations by viewModel.syncOperations.collectAsStateWithLifecycle()
    val bootstrap by viewModel.managedBootstrapSummary.collectAsStateWithLifecycle()
    val bootstrapProgress by viewModel.floppyBootstrapProgress.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refreshSyncOperations() }
    val conflicts = operations.filter { it.status == SyncOperationStatus.CONFLICT }
    val failed = operations.filter { it.status in setOf(SyncOperationStatus.FAILED, SyncOperationStatus.PARTIAL) }
    val pending = operations.filter { it.status == SyncOperationStatus.PENDING }

    val effectiveProgress = bootstrapProgress ?: bootstrap?.let { summary ->
        FloppyBootstrapProgress(
            stage = when (summary.state) {
                com.cinetrack.data.sync.ProviderBootstrapState.READY -> FloppyBootstrapStage.COMPLETE
                com.cinetrack.data.sync.ProviderBootstrapState.FAILED -> FloppyBootstrapStage.NEEDS_ATTENTION
                com.cinetrack.data.sync.ProviderBootstrapState.RUNNING -> FloppyBootstrapStage.SYNCING
                com.cinetrack.data.sync.ProviderBootstrapState.NOT_STARTED -> FloppyBootstrapStage.PREPARING
            },
            processed = summary.completed,
            total = summary.total,
            succeeded = summary.completed,
            failed = summary.failed,
            providerInstanceId = summary.connectionId,
        )
    }
    // Managed bootstrap rows are intentionally compacted into one card, but
    // their terminal failure must still be reflected in this screen's status
    // summary. A Floppy HTTP failure is transport attention, never a local
    // reconciliation conflict.
    val bootstrapFailed = effectiveProgress?.stage == FloppyBootstrapStage.NEEDS_ATTENTION
    KitStatTiles(
        listOf(
            pending.size.toString() to stringResource(R.string.pending_writes),
            (failed.size + if (bootstrapFailed) 1 else 0).toString() to stringResource(R.string.failed_actions),
            conflicts.size.toString() to stringResource(R.string.sync_conflicts),
        ),
    )

    // A finished initial sync has nothing left to show or retry.
    if (effectiveProgress != null && effectiveProgress.stage != FloppyBootstrapStage.COMPLETE) {
        SettingsSection(stringResource(R.string.initial_synchronization)) {
            ManagedFloppyBootstrapCard(effectiveProgress, bootstrap, viewModel::retryFloppyInitialSync)
        }
    }

    val managedActive = effectiveProgress?.stage?.isManagedActive() == true
    if (operations.isEmpty() && !managedActive) {
        KitStatusCard(ok = true, title = stringResource(R.string.ops_all_synced), subtitle = stringResource(R.string.ops_all_synced_sub))
    }
    if (conflicts.isNotEmpty()) SyncOperationSection(stringResource(R.string.conflicts_to_resolve), conflicts, viewModel)
    if (failed.isNotEmpty()) SyncOperationSection(stringResource(R.string.failed_actions), failed, viewModel)
    if (pending.isNotEmpty()) SyncOperationSection(stringResource(R.string.pending_writes), pending, viewModel)
}

@Composable
private fun ManagedFloppyBootstrapCard(
    progress: com.cinetrack.domain.FloppyBootstrapProgress,
    summary: com.cinetrack.data.sync.ManagedBootstrapSummary?,
    onRetry: () -> Unit,
) {
    val stage = progress.stage
    val determinate = stage == FloppyBootstrapStage.SYNCING && progress.total > 0
    val planningDeterminate = stage == FloppyBootstrapStage.MATERIALIZING_QUEUE && progress.planningTotal > 0
    val percent = if (determinate) (progress.processed * 100 / progress.total).coerceIn(0, 100) else 0
    val retryable = stage.allowsRetry()
    Column(
        Modifier.fillMaxWidth().padding(com.cinetrack.ui.theme.Spacing.lg)
            .glass(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Medium))
            .padding(com.cinetrack.ui.theme.Spacing.lg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CloudSync, contentDescription = null, tint = AccentLight, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(com.cinetrack.ui.theme.Spacing.sm))
            Column(Modifier.weight(1f)) {
                Text("Floppy", color = TextPrimary, fontWeight = FontWeight.Bold, style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.initial_synchronization), color = TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
            }
            Text(
                when (stage) {
                    FloppyBootstrapStage.COMPLETE -> stringResource(R.string.floppy_ready_short)
                    FloppyBootstrapStage.NEEDS_ATTENTION -> stringResource(R.string.floppy_initial_sync_needs_attention)
                    FloppyBootstrapStage.STALLED -> stringResource(R.string.floppy_taking_longer)
                    else -> stringResource(R.string.floppy_syncing_with_floppy)
                },
                color = if (retryable) androidx.compose.material3.MaterialTheme.colorScheme.error else TextSecondary,
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(com.cinetrack.ui.theme.Spacing.md))
        Text(
            when (stage) {
                FloppyBootstrapStage.PREPARING,
                FloppyBootstrapStage.BUILDING_PLAN -> stringResource(R.string.floppy_building_sync_plan)
                FloppyBootstrapStage.MATERIALIZING_QUEUE -> stringResource(R.string.floppy_preparing_sync_queue)
                FloppyBootstrapStage.CHECKING_REMOTE_STATE -> stringResource(R.string.floppy_checking_existing_history)
                FloppyBootstrapStage.QUEUED -> stringResource(R.string.floppy_waiting_for_server)
                FloppyBootstrapStage.SYNCING -> stringResource(R.string.floppy_syncing_with_floppy)
                FloppyBootstrapStage.VERIFYING -> stringResource(R.string.floppy_verifying_sync)
                FloppyBootstrapStage.WAITING_FOR_SERVER -> stringResource(R.string.floppy_waiting_for_server)
                FloppyBootstrapStage.STALLED -> stringResource(R.string.floppy_taking_longer)
                FloppyBootstrapStage.NEEDS_ATTENTION -> stringResource(R.string.floppy_initial_sync_needs_attention)
                FloppyBootstrapStage.COMPLETE -> stringResource(R.string.floppy_ready_short)
            },
            color = TextPrimary,
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        if (determinate) {
            Spacer(Modifier.height(com.cinetrack.ui.theme.Spacing.sm))
            LinearProgressIndicator(
                progress = { (progress.processed.toFloat() / progress.total).coerceIn(0f, 1f) },
                color = AccentLight,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth().padding(top = com.cinetrack.ui.theme.Spacing.xs), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.floppy_progress_count, progress.processed, progress.total), color = TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                Text("$percent%", color = TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
            }
        } else if (planningDeterminate) {
            Spacer(Modifier.height(com.cinetrack.ui.theme.Spacing.sm))
            LinearProgressIndicator(
                progress = { (progress.planningProcessed.toFloat() / progress.planningTotal).coerceIn(0f, 1f) },
                color = AccentLight,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth().padding(top = com.cinetrack.ui.theme.Spacing.xs), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.floppy_progress_count, progress.planningProcessed, progress.planningTotal), color = TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                Text("${(progress.planningProcessed * 100 / progress.planningTotal).coerceIn(0, 100)}%", color = TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
            }
        } else if (stage != FloppyBootstrapStage.COMPLETE && stage != FloppyBootstrapStage.NEEDS_ATTENTION) {
            Spacer(Modifier.height(com.cinetrack.ui.theme.Spacing.sm))
            LinearProgressIndicator(color = AccentLight, modifier = Modifier.fillMaxWidth())
        }
        progress.currentTitle?.takeIf(String::isNotBlank)?.let { title ->
            Spacer(Modifier.height(com.cinetrack.ui.theme.Spacing.sm))
            Text("${stringResource(R.string.current)}: $title", color = TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
        }
        if (stage == FloppyBootstrapStage.SYNCING) {
            Spacer(Modifier.height(com.cinetrack.ui.theme.Spacing.sm))
            Text(stringResource(R.string.floppy_leave_screen_copy), color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
        }
        summary?.lastError?.takeIf(String::isNotBlank)?.let { error ->
            Spacer(Modifier.height(com.cinetrack.ui.theme.Spacing.sm))
            Text(error, color = androidx.compose.material3.MaterialTheme.colorScheme.error, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
        }
        if (retryable) {
            Spacer(Modifier.height(com.cinetrack.ui.theme.Spacing.xs))
            TextButton(onClick = onRetry) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(com.cinetrack.ui.theme.Spacing.xs))
                Text(if (stage == FloppyBootstrapStage.NEEDS_ATTENTION) stringResource(R.string.retry) else stringResource(R.string.retry_now))
            }
        }
    }
}

@Composable
private fun SyncOperationSection(
    title: String,
    operations: List<SyncOperationCard>,
    viewModel: CineTrackViewModel,
) {
    SettingsSection(title) {
        operations.forEachIndexed { index, operation ->
            SyncOperationRow(operation, viewModel)
        }
    }
}

@Composable
private fun SyncOperationRow(operation: SyncOperationCard, viewModel: CineTrackViewModel) {
    val terminalCancelled = operation.deliveries.isNotEmpty() && operation.deliveries
        .filter { it.required }
        .all { it.status in setOf("CANCELLED_PROVIDER_INSTANCE_CHANGED", "CANCELLED_PROVIDER_REMOVED", "SUPERSEDED", "SKIPPED_UNSUPPORTED", "ACKNOWLEDGED") }
    val statusColor = when (operation.status) {
        SyncOperationStatus.CONFLICT -> StatusPaused
        SyncOperationStatus.FAILED -> androidx.compose.material3.MaterialTheme.colorScheme.error
        SyncOperationStatus.PARTIAL -> androidx.compose.material3.MaterialTheme.colorScheme.error
        SyncOperationStatus.PENDING -> AccentLight
    }
    val providerName = operation.providerId.lowercase().replaceFirstChar { it.titlecase(com.cinetrack.ui.currentLocale()) }
    val actionLabel = when (operation.operation) {
        "LIBRARY_STATUS" -> stringResource(R.string.sync_library_change)
        "LIBRARY_STATUS_CONFLICT", "LIBRARY_CONFLICT" -> stringResource(R.string.sync_library_conflict)
        "WATCHED_CONFLICT" -> stringResource(R.string.sync_watched_conflict)
        "EPISODE_WATCHED_CONFLICT" -> stringResource(R.string.sync_episode_watched_conflict)
        "EPISODE_WATCHED" -> stringResource(R.string.sync_episode_watched)
        "EPISODE_UNWATCHED" -> stringResource(R.string.sync_episode_unwatched)
        "MEDIA_HISTORY_REMOVE" -> stringResource(R.string.sync_history_removed)
        else -> stringResource(R.string.sync_library_change)
    }
    Column(Modifier.fillMaxWidth().padding(com.cinetrack.ui.theme.Spacing.lg)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(operation.title, color = TextPrimary, fontWeight = FontWeight.Bold, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                Text(actionLabel, color = statusColor, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
            Text(
                java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT)
                    .format(java.util.Date(operation.updatedAt)),
                color = TextMuted,
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            )
        }
        val conflictMessage = when (operation.operation) {
            "LIBRARY_STATUS_CONFLICT", "LIBRARY_CONFLICT" -> stringResource(R.string.sync_library_conflict_message, providerName)
            "WATCHED_CONFLICT" -> stringResource(R.string.sync_watched_conflict_message, providerName)
            "EPISODE_WATCHED_CONFLICT" -> stringResource(R.string.sync_episode_watched_conflict_message, providerName)
            else -> operation.message
        }
        conflictMessage?.takeIf(String::isNotBlank)?.let { message ->
            Spacer(Modifier.height(com.cinetrack.ui.theme.Spacing.sm))
            Text(message, color = TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
        }
        if (operation.deliveries.isNotEmpty()) {
            Spacer(Modifier.height(com.cinetrack.ui.theme.Spacing.sm))
            operation.deliveries.forEach { delivery -> SyncDeliveryRow(delivery) }
        }
        if (operation.status == SyncOperationStatus.CONFLICT) {
            Spacer(Modifier.height(com.cinetrack.ui.theme.Spacing.md))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(com.cinetrack.ui.theme.Spacing.sm)) {
                Button(
                    onClick = { viewModel.resolveSyncConflict(operation.id, SyncConflictChoice.KEEP_LOCAL) },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    shape = RoundedCornerShape(com.cinetrack.ui.theme.Radius.Pill),
                    colors = ButtonDefaults.buttonColors(containerColor = com.cinetrack.ui.theme.GlassSubtle),
                ) { Text(stringResource(R.string.keep_local), style = androidx.compose.material3.MaterialTheme.typography.labelSmall) }
                Button(
                    onClick = { viewModel.resolveSyncConflict(operation.id, SyncConflictChoice.USE_REMOTE) },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    shape = RoundedCornerShape(com.cinetrack.ui.theme.Radius.Pill),
                ) { Text(stringResource(R.string.use_provider, providerName), style = androidx.compose.material3.MaterialTheme.typography.labelSmall) }
            }
            Row(Modifier.fillMaxWidth().padding(top = com.cinetrack.ui.theme.Spacing.xs), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.local_value, localizedSyncValue(operation.localValue)), color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                Text(stringResource(R.string.provider_value, providerName, localizedSyncValue(operation.remoteValue)), color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
            }
        } else if (!terminalCancelled) {
            Spacer(Modifier.height(com.cinetrack.ui.theme.Spacing.md))
            Row(verticalAlignment = Alignment.CenterVertically) {
                operation.localValue?.takeIf(String::isNotBlank)?.let { value ->
                    Text(localizedSyncValue(value), color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                } ?: Spacer(Modifier.weight(1f))
                Button(
                    onClick = { viewModel.retrySyncOperation(operation.id) },
                    modifier = Modifier.heightIn(min = 48.dp),
                    shape = RoundedCornerShape(com.cinetrack.ui.theme.Radius.Pill),
                ) {
                    Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(com.cinetrack.ui.theme.Spacing.xs))
                    Text(if (operation.status == SyncOperationStatus.FAILED) stringResource(R.string.retry) else stringResource(R.string.try_now))
                }
            }
        } else {
            Spacer(Modifier.height(com.cinetrack.ui.theme.Spacing.sm))
            Text(stringResource(R.string.sync_delivery_cancelled_instance), color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun SyncDeliveryRow(delivery: SyncDeliveryCard) {
    val provider = delivery.providerId.lowercase().replaceFirstChar { it.titlecase(com.cinetrack.ui.currentLocale()) }
    val label = when (delivery.status) {
        "ACKNOWLEDGED" -> stringResource(R.string.sync_delivery_synced)
        "FAILED" -> stringResource(R.string.sync_delivery_retry)
        "PENDING" -> stringResource(R.string.sync_delivery_pending)
        "SKIPPED_UNSUPPORTED" -> stringResource(R.string.sync_delivery_skipped)
        "CANCELLED_PROVIDER_REMOVED" -> stringResource(R.string.sync_delivery_cancelled)
        "CANCELLED_PROVIDER_INSTANCE_CHANGED" -> stringResource(R.string.sync_delivery_cancelled_instance)
        else -> stringResource(R.string.sync_delivery_pending)
    }
    val color = when (delivery.status) {
        "ACKNOWLEDGED" -> Success
        "FAILED" -> androidx.compose.material3.MaterialTheme.colorScheme.error
        "CANCELLED_PROVIDER_INSTANCE_CHANGED", "CANCELLED_PROVIDER_REMOVED", "SUPERSEDED" -> TextMuted
        else -> TextSecondary
    }
    Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(provider, color = TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
        Text(label, color = color, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
    }
    delivery.lastError?.takeIf(String::isNotBlank)?.let { error ->
        Text(error, color = if (delivery.status == "FAILED") androidx.compose.material3.MaterialTheme.colorScheme.error else TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun localizedSyncValue(value: String?): String = when (value) {
    "PLAN_TO_WATCH" -> stringResource(R.string.status_plan_to_watch)
    "COMPLETED" -> stringResource(R.string.status_completed)
    "DROPPED" -> stringResource(R.string.status_dropped)
    "PAUSED" -> stringResource(R.string.status_paused)
    "true" -> stringResource(R.string.watched)
    "false" -> stringResource(R.string.not_watched)
    else -> value.orEmpty().replace(':', ' ').replace('_', ' ')
}
    .replaceFirstChar { it.titlecase(com.cinetrack.ui.currentLocale()) }

@Composable
private fun IntegrationsSettings(state: AppUiState, onPage: (String) -> Unit) {
    SettingsSection(stringResource(R.string.integrations)) {
        ProviderRow("TMDB", stringResource(R.string.tmdb_description), state.tmdbApiConfigured) { onPage(SettingsPages.ServiceTmdb) }
        ProviderRow("MDBList", stringResource(R.string.mdblist_description), state.mdbListApiConfigured) { onPage(SettingsPages.ServiceMdblist) }
        ProviderRow("Trakt", stringResource(R.string.trakt_description), state.traktConfigured) { onPage(SettingsPages.ServiceTrakt) }
        ProviderRow("Simkl", stringResource(R.string.simkl_description), state.simklConnected) { onPage(SettingsPages.ServiceSimkl) }
        ProviderRow("Floppy", floppyIntegrationSubtitle(state), state.floppyConnected) { onPage(SettingsPages.ServiceFloppy) }
    }
}

@Composable
private fun floppyIntegrationSubtitle(state: AppUiState): String = if (state.floppyIsMain) stringResource(R.string.floppy_main) else when (state.floppyUiState) {
    FloppyUiState.NOT_CONNECTED -> stringResource(R.string.floppy_self_hosted_tracking)
    FloppyUiState.CONNECTED_INACTIVE -> stringResource(R.string.floppy_connected_inactive)
    FloppyUiState.SETTING_UP -> stringResource(R.string.floppy_setting_up)
    FloppyUiState.READY -> stringResource(R.string.floppy_ready)
    FloppyUiState.NEEDS_ATTENTION -> stringResource(R.string.floppy_needs_attention)
}

/** "Connected as <name>" once Floppy is ready and has sent a name; otherwise the setup stage. */
@Composable
private fun floppyConnectedLabel(state: AppUiState): String =
    if (state.floppyUiState == FloppyUiState.READY && !state.floppyUsername.isNullOrBlank()) stringResource(R.string.floppy_connected_as, state.floppyUsername.orEmpty())
    else floppyStatusLabel(state.floppyUiState)

@Composable
private fun floppyStatusLabel(state: FloppyUiState): String = when (state) {
    FloppyUiState.NOT_CONNECTED -> stringResource(R.string.floppy_not_connected)
    FloppyUiState.CONNECTED_INACTIVE -> stringResource(R.string.floppy_connected_inactive)
    FloppyUiState.SETTING_UP -> stringResource(R.string.floppy_setting_up)
    FloppyUiState.READY -> stringResource(R.string.floppy_ready)
    FloppyUiState.NEEDS_ATTENTION -> stringResource(R.string.floppy_needs_attention)
}

@Composable
private fun FloppySettingsHost(state: AppUiState, viewModel: CineTrackViewModel) {
    val connectionUiState by viewModel.floppyConnectionUiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.floppyConnected, state.floppyUsername) {
        if (state.floppyConnected && state.floppyUsername == null) viewModel.loadFloppyUsername()
    }
    val observedBootstrapProgress by viewModel.floppyBootstrapProgress.collectAsStateWithLifecycle()
    // The header already says "not connected"; the status details matter once connected.
    if (state.floppyConnected || state.floppyUiState == FloppyUiState.NEEDS_ATTENTION) SettingsSection(stringResource(R.string.floppy_status)) {
        // Whether it's connected; the role and the initial sync have their own rows.
        val attention = state.floppyUiState == FloppyUiState.NEEDS_ATTENTION
        ValueRow(
            stringResource(R.string.floppy_status),
            stringResource(when {
                attention -> R.string.floppy_needs_attention
                state.floppyConnected -> R.string.floppy_connected
                else -> R.string.floppy_not_connected
            }),
            state.floppyConnected && !attention,
        )
        if (state.floppyConnected) {
            ValueRow(stringResource(R.string.floppy_role), when {
                state.floppyIsMain -> stringResource(R.string.floppy_role_main)
                state.floppyUiState == FloppyUiState.CONNECTED_INACTIVE -> stringResource(R.string.floppy_role_connected_inactive)
                else -> stringResource(R.string.floppy_role_secondary)
            })
            state.floppyServerVersion?.let { ValueRow(stringResource(R.string.floppy_server_version), it) }
            ValueRow(stringResource(R.string.floppy_initial_sync), when (state.floppyUiState) {
                FloppyUiState.READY -> stringResource(R.string.floppy_ready_short)
                FloppyUiState.NEEDS_ATTENTION -> stringResource(R.string.floppy_initial_sync_failed)
                else -> stringResource(R.string.floppy_setting_up)
            })
            val bootstrapProgress = observedBootstrapProgress ?: state.floppyBootstrapProgress
            if (bootstrapProgress != null && bootstrapProgress.stage != FloppyBootstrapStage.COMPLETE) {
                Row(Modifier.fillMaxWidth().padding(horizontal = com.cinetrack.ui.theme.Spacing.lg, vertical = com.cinetrack.ui.theme.Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                    val determinate = bootstrapProgress.total > 0 && bootstrapProgress.stage == FloppyBootstrapStage.SYNCING
                    if (determinate) {
                        LinearProgressIndicator(progress = { (bootstrapProgress.processed.toFloat() / bootstrapProgress.total).coerceIn(0f, 1f) }, color = AccentLight, modifier = Modifier.weight(1f))
                    } else CircularProgressIndicator(color = AccentLight, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(com.cinetrack.ui.theme.Spacing.sm))
                    Text(
                        when (bootstrapProgress.stage) {
                            FloppyBootstrapStage.PREPARING,
                            FloppyBootstrapStage.BUILDING_PLAN -> stringResource(R.string.floppy_building_sync_plan)
                            FloppyBootstrapStage.MATERIALIZING_QUEUE -> stringResource(R.string.floppy_preparing_sync_queue)
                            FloppyBootstrapStage.QUEUED -> stringResource(R.string.floppy_waiting_for_server)
                            FloppyBootstrapStage.CHECKING_REMOTE_STATE -> stringResource(R.string.floppy_checking_existing_history)
                            FloppyBootstrapStage.WAITING_FOR_SERVER -> stringResource(R.string.floppy_waiting_for_server)
                            FloppyBootstrapStage.SYNCING -> stringResource(R.string.floppy_syncing_with_floppy) + " · ${bootstrapProgress.processed} / ${bootstrapProgress.total}"
                            FloppyBootstrapStage.VERIFYING -> stringResource(R.string.floppy_verifying_sync)
                            FloppyBootstrapStage.STALLED -> stringResource(R.string.floppy_taking_longer)
                            FloppyBootstrapStage.COMPLETE -> stringResource(R.string.floppy_ready_short)
                            FloppyBootstrapStage.NEEDS_ATTENTION -> stringResource(R.string.floppy_initial_sync_needs_attention)
                        },
                        color = TextMuted,
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    )
                }
                Text(stringResource(R.string.floppy_leave_screen_copy), color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = com.cinetrack.ui.theme.Spacing.lg, vertical = com.cinetrack.ui.theme.Spacing.xs))
            }
        }
        val retryableStage = observedBootstrapProgress?.stage?.allowsRetry() == true
        if (state.floppyUiState == FloppyUiState.NEEDS_ATTENTION || retryableStage) {
            TextButton(onClick = viewModel::retryFloppyInitialSync, modifier = Modifier.padding(horizontal = com.cinetrack.ui.theme.Spacing.lg)) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(com.cinetrack.ui.theme.Spacing.xs))
                Text(if (retryableStage && observedBootstrapProgress?.stage != FloppyBootstrapStage.NEEDS_ATTENTION) stringResource(R.string.retry_now) else stringResource(R.string.retry))
            }
        }
    }
    MainTrackerSection(state, viewModel)
    val unplaced by viewModel.floppyUnplaced.collectAsStateWithLifecycle()
    LaunchedEffect(state.floppyUiState, observedBootstrapProgress?.stage) { viewModel.refreshFloppyUnplaced() }
    val floppyBaseUrl = state.floppyBaseUrl
    if (state.floppyConnected && unplaced.isNotEmpty() && !floppyBaseUrl.isNullOrBlank()) {
        SettingsSection(stringResource(R.string.floppy_unplaced_title) + " (${unplaced.size})") {
            Text(
                stringResource(R.string.floppy_unplaced_copy),
                color = TextMuted,
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = com.cinetrack.ui.theme.Spacing.lg, vertical = com.cinetrack.ui.theme.Spacing.sm),
            )
            unplaced.forEach { item ->
                FloppyUnplacedCard(item, floppyBaseUrl, onDismiss = { viewModel.dismissFloppyUnplaced(item) })
            }
            Spacer(Modifier.height(com.cinetrack.ui.theme.Spacing.sm))
        }
    }
    val signInContext = LocalContext.current
    val sync by viewModel.syncProgress.collectAsStateWithLifecycle()
    FloppyConnectionSettingsForm(
        state,
        connectionUiState,
        viewModel::connectFloppy,
        onSignInRequested = { url, allowHttp -> viewModel.beginFloppySignIn(signInContext, url, allowHttp) },
        syncing = sync.running,
        onSyncRequested = viewModel::sync,
    )
    if (state.floppyConnected) {
        val crashContext = LocalContext.current
        var sendCrashReports by remember { mutableStateOf(com.cinetrack.crash.CrashReportSettings.isEnabled(crashContext)) }
        SettingsSection(stringResource(R.string.floppy_send_crash_reports)) {
            ToggleRow(stringResource(R.string.floppy_send_crash_reports), sendCrashReports) {
                sendCrashReports = it
                com.cinetrack.crash.CrashReporting.setEnabled(crashContext, it)
            }
        }
    }
    val floppyUri = LocalUriHandler.current
    KitSteps(
        stringResource(R.string.where_find_token),
        listOf(stringResource(R.string.floppy_step_1), stringResource(R.string.floppy_step_2), stringResource(R.string.floppy_step_3), stringResource(R.string.floppy_step_4)),
        stringResource(R.string.floppy_api_guide),
    ) { runCatching { floppyUri.openUri(FLOPPY_API_GUIDE_URL) } }
    KitFootnote(stringResource(R.string.floppy_connection_security))
    if (state.floppyConnected) {
        ConfirmedDangerText(
            text = stringResource(R.string.floppy_disconnect),
            title = stringResource(R.string.disconnect_floppy_title),
            body = stringResource(R.string.disconnect_floppy_body),
            onConfirm = viewModel::disconnectFloppy,
        )
    }
}

/** Choose which tracker is MAIN (two-way) and which is SECONDARY (receives
 * CineTrack's changes only). Offered once both are connected and the
 * candidate finished its initial sync. */
@Composable
private fun MainTrackerSection(state: AppUiState, viewModel: CineTrackViewModel) {
    if (!state.floppyConnected || !state.simklConnected) return
    val swap by viewModel.mainTrackerSwap.collectAsStateWithLifecycle()
    var asking by rememberSaveable { mutableStateOf(false) }
    val floppyReady = state.floppyUiState == FloppyUiState.READY
    val canSwap = state.floppyIsMain || (floppyReady && state.floppyCanBeMain)
    SettingsSection(stringResource(R.string.main_tracker_title)) {
        Column(Modifier.fillMaxWidth().padding(com.cinetrack.ui.theme.Spacing.md)) {
            // Two choices side by side; the current one is filled. Picking the
            // other asks first, so Floppy becomes main only by the user's choice.
            val shape = RoundedCornerShape(com.cinetrack.ui.theme.Radius.Pill)
            Row(
                Modifier.fillMaxWidth().clip(shape).background(Color.White.copy(alpha = .06f)).border(.6.dp, Color.White.copy(alpha = .10f), shape).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                listOf(false to "Simkl", true to "Floppy").forEach { (floppy, name) ->
                    val current = state.floppyIsMain == floppy
                    val switchingHere = swap.running && !current
                    val fill by androidx.compose.animation.animateColorAsState(if (current) Accent else Color.Transparent, label = "mainTrackerFill")
                    Row(
                        Modifier.weight(1f).heightIn(min = 44.dp).clip(shape).background(fill)
                            .clickable(enabled = !current && canSwap && !swap.running, role = androidx.compose.ui.semantics.Role.RadioButton) { asking = true }
                            .semantics { selected = current },
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (switchingHere) {
                            CircularProgressIndicator(color = AccentLight, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(com.cinetrack.ui.theme.Spacing.sm))
                        } else if (current) {
                            Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            if (switchingHere) stringResource(R.string.main_tracker_switching) else name,
                            color = when {
                                current -> Color.White
                                canSwap -> TextPrimary
                                else -> TextMuted
                            },
                            style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                            fontWeight = if (current) FontWeight.ExtraBold else FontWeight.SemiBold,
                        )
                    }
                }
            }
            Text(
                stringResource(
                    when {
                        state.floppyIsMain -> R.string.main_tracker_floppy_copy
                        !floppyReady -> R.string.main_tracker_wait_copy
                        !state.floppyCanBeMain -> R.string.main_tracker_update_floppy_copy
                        else -> R.string.main_tracker_simkl_copy
                    },
                ),
                color = TextSecondary,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = com.cinetrack.ui.theme.Spacing.md),
            )
        }
        swap.error?.let { KitNotice(it) }
    }
    if (asking) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text(stringResource(if (state.floppyIsMain) R.string.main_tracker_make_simkl else R.string.main_tracker_make_floppy)) },
            text = {
                Text(
                    stringResource(if (state.floppyIsMain) R.string.main_tracker_confirm_simkl else R.string.main_tracker_confirm_floppy),
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = { asking = false; viewModel.swapMainTracker() }) {
                    Text(stringResource(R.string.main_tracker_switch), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { asking = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/**
 * Floppy recognised the account already connected, at an address CineTrack hasn't
 * used. The same server keeps the connection, pending changes and finished initial
 * sync; a new one starts over with a full initial sync. Tapping outside cancels.
 */
@Composable
internal fun FloppyAddressMoveDialog(viewModel: CineTrackViewModel) {
    val pending by viewModel.floppyAddressMove.collectAsStateWithLifecycle()
    val move = pending ?: return
    val host = remember(move.baseUrl) { runCatching { java.net.URI(move.baseUrl).host }.getOrNull() ?: move.baseUrl }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = viewModel::cancelFloppyAddressMove,
        title = { Text(stringResource(R.string.floppy_move_title)) },
        text = { Text(stringResource(R.string.floppy_move_body, host), style = androidx.compose.material3.MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = { viewModel.answerFloppyAddressMove(same = true) }) {
                Text(stringResource(R.string.floppy_move_same), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.answerFloppyAddressMove(same = false) }) { Text(stringResource(R.string.floppy_move_new)) }
        },
    )
}

@Composable
private fun FloppyUnplacedCard(
    item: com.cinetrack.domain.FloppyUnplacedItem,
    floppyBaseUrl: String,
    onDismiss: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = com.cinetrack.ui.theme.Spacing.md, vertical = com.cinetrack.ui.theme.Spacing.xs)
            .glass(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Medium))
            .padding(com.cinetrack.ui.theme.Spacing.md),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    item.showTitle,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(
                        if (item.season == 0) stringResource(R.string.floppy_unplaced_special, item.label) else item.label,
                        item.episodeTitle,
                    ).joinToString(" · "),
                    color = TextSecondary,
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.floppy_unplaced_dismiss), tint = TextMuted, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.height(com.cinetrack.ui.theme.Spacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(com.cinetrack.ui.theme.Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.FilledTonalButton(
                onClick = { runCatching { uriHandler.openUri(item.floppySearchUrl(floppyBaseUrl)) } },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Icon(Icons.Filled.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.floppy_unplaced_open), style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
            }
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.floppy_unplaced_marked), color = TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
internal fun FloppyConnectionSettingsForm(
    state: AppUiState,
    connectionUiState: FloppyConnectionUiState = FloppyConnectionUiState(),
    onConnectRequested: (String, String, Boolean) -> Unit = { _, _, _ -> },
    onSignInRequested: ((String, Boolean) -> Unit)? = null,
    syncing: Boolean = false,
    onSyncRequested: (() -> Unit)? = null,
) {
    var url by rememberSaveable(state.floppyBaseUrl) { mutableStateOf(state.floppyBaseUrl.orEmpty()) }
    var apiKey by rememberSaveable { mutableStateOf("") }
    var showApiKey by rememberSaveable { mutableStateOf(false) }
    var allowLocalHttp by rememberSaveable(state.floppyAllowInsecureLocalHttp) { mutableStateOf(state.floppyAllowInsecureLocalHttp) }
    var advancedExpanded by rememberSaveable { mutableStateOf(false) }
    val editing = state.floppyConnected
    // Sign-in replaced API keys: the key field is left only where sign-in
    // isn't offered. Once connected, neither shows (like the Simkl section).
    val keyFlow = onSignInRequested == null && !editing
    val signInFlow = onSignInRequested != null && !editing
    // An address typed without a scheme is taken as https://, or http:// when
    // local-network HTTP is on.
    val fullUrl = withDefaultFloppyScheme(url, allowLocalHttp)
    val validUrl = isFloppyUrlValid(fullUrl)
    val canSubmit = validUrl && (apiKey.isNotBlank() || editing) && !connectionUiState.running

    val uriHandler = LocalUriHandler.current
    val focusManager = LocalFocusManager.current

    SettingsSection(stringResource(R.string.floppy_connection)) {
        Column(Modifier.fillMaxWidth().padding(com.cinetrack.ui.theme.Spacing.md)) {
            // Three steps: server address, token, verify. The bar fills as each is done.
            val step = when {
                !validUrl -> 1
                apiKey.isBlank() && !editing -> 2
                else -> 3
            }
            if (keyFlow) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                repeat(3) { index ->
                    Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(if (index < step) Color(0xFF85B7EB) else Color.White.copy(alpha = .12f)))
                }
            }
            Text(
                stringResource(R.string.floppy_step_label, step, 3) + " · " + stringResource(
                    when (step) { 1 -> R.string.floppy_step_server; 2 -> R.string.floppy_step_token; else -> R.string.floppy_step_verify },
                ),
                color = TextSecondary,
                style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
            )
            }
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                modifier = Modifier.fillMaxWidth().testTag("floppy_server_url_field"),
                singleLine = true,
                enabled = !connectionUiState.running,
                label = { Text(stringResource(R.string.floppy_server_url)) },
                prefix = if (url.isNotBlank() && !hasUrlScheme(url)) {
                    { Text(if (allowLocalHttp) "http://" else "https://", color = TextMuted) }
                } else null,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                colors = credentialFieldColors(),
                isError = url.isNotBlank() && !validUrl,
            )
            if (url.isNotBlank() && !validUrl) Text(stringResource(R.string.floppy_error_invalid_url), color = androidx.compose.material3.MaterialTheme.colorScheme.error, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = com.cinetrack.ui.theme.Spacing.xs))
            if (signInFlow) {
                Spacer(Modifier.height(com.cinetrack.ui.theme.Spacing.md))
                // Like Simkl: sign in on Floppy's own page, no key to create or paste.
                Button(
                    onClick = {
                        focusManager.clearFocus()
                        onSignInRequested(fullUrl, allowLocalHttp)
                    },
                    enabled = validUrl && !connectionUiState.running,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("floppy_sign_in_button"),
                    shape = RoundedCornerShape(com.cinetrack.ui.theme.Radius.Pill),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF185FA5), disabledContainerColor = Color.White.copy(alpha = .08f)),
                ) {
                    Icon(Icons.Filled.Link, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(7.dp))
                    Text(stringResource(R.string.floppy_sign_in), fontWeight = FontWeight.ExtraBold)
                }
            }
            if (keyFlow) {
            Spacer(Modifier.height(com.cinetrack.ui.theme.Spacing.md))
            OutlinedTextField(
                value = apiKey,
                 onValueChange = { apiKey = if (apiKey.isEmpty() && editing) it.replace("•", "") else it },
                modifier = Modifier.fillMaxWidth().testTag("floppy_api_key_field"),
                singleLine = true,
                enabled = !connectionUiState.running,
                label = { Text(stringResource(if (editing) R.string.floppy_api_key_keep else R.string.floppy_api_key)) },
                placeholder = { if (editing) Text(stringResource(R.string.floppy_saved_securely), style = androidx.compose.material3.MaterialTheme.typography.bodySmall) },
                visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    focusManager.clearFocus()
                    onConnectRequested(fullUrl, apiKey.trim(), allowLocalHttp)
                }),
                trailingIcon = {
                    IconButton(onClick = { showApiKey = !showApiKey }, enabled = !connectionUiState.running) {
                        Icon(if (showApiKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, contentDescription = stringResource(if (showApiKey) R.string.hide_api_key else R.string.show_api_key), tint = TextSecondary)
                    }
                },
                colors = credentialFieldColors(),
            )
            }
            Spacer(Modifier.height(com.cinetrack.ui.theme.Spacing.sm))
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { advancedExpanded = !advancedExpanded }.padding(vertical = com.cinetrack.ui.theme.Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.floppy_advanced), color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Icon(if (advancedExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = TextMuted)
            }
            if (advancedExpanded) {
                ToggleRow(stringResource(R.string.floppy_allow_http_local_network), allowLocalHttp) { allowLocalHttp = it }
                Text(stringResource(R.string.floppy_http_local_supporting), color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = com.cinetrack.ui.theme.Spacing.sm))
            }
            if (connectionUiState.stage != FloppyConnectionStage.IDLE) {
                Text(
                    floppyConnectionStageLabel(connectionUiState.stage, state.floppyUiState),
                    color = if (connectionUiState.stage == FloppyConnectionStage.FAILED) androidx.compose.material3.MaterialTheme.colorScheme.error else TextMuted,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = com.cinetrack.ui.theme.Spacing.sm),
                )
            }
            connectionUiState.error?.let { error ->
                Text(
                    connectionUiState.message ?: floppyConnectionErrorMessage(error),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = com.cinetrack.ui.theme.Spacing.sm),
                )
            }
            // Connected to this address, the button syncs (like Simkl's); a changed
            // address turns it back into Reconnect.
            val syncAction = editing && onSyncRequested != null && fullUrl == state.floppyBaseUrl.orEmpty().trim()
            if (syncAction) Button(
                onClick = { onSyncRequested?.invoke() },
                enabled = !syncing && !connectionUiState.running,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("floppy_sync_button"),
                shape = RoundedCornerShape(com.cinetrack.ui.theme.Radius.Pill),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF185FA5), disabledContainerColor = Color.White.copy(alpha = .08f)),
            ) {
                if (syncing) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(com.cinetrack.ui.theme.Spacing.sm))
                } else {
                    Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(7.dp))
                }
                Text(stringResource(R.string.sync_now), fontWeight = FontWeight.ExtraBold)
            }
            // Signing in is the connect action until a connection exists.
            else if (!signInFlow) Button(
                onClick = {
                    focusManager.clearFocus()
                    onConnectRequested(fullUrl, apiKey.trim(), allowLocalHttp)
                },
                enabled = canSubmit,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("floppy_connect_button"),
                shape = RoundedCornerShape(com.cinetrack.ui.theme.Radius.Pill),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF185FA5), disabledContainerColor = Color.White.copy(alpha = .08f)),
            ) {
                if (connectionUiState.running) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(com.cinetrack.ui.theme.Spacing.sm))
                } else {
                    Icon(Icons.Filled.Link, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(7.dp))
                }
                Text(stringResource(if (editing) R.string.floppy_reconnect else R.string.floppy_connect), fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

@Composable
private fun floppyConnectionStageLabel(stage: FloppyConnectionStage, persistentState: FloppyUiState): String = when (stage) {
    FloppyConnectionStage.CHECKING_SERVER -> stringResource(R.string.floppy_stage_checking_server)
    FloppyConnectionStage.AUTHENTICATING -> stringResource(R.string.floppy_stage_authenticating)
    FloppyConnectionStage.ACTIVATING -> stringResource(R.string.floppy_stage_activating)
    FloppyConnectionStage.INITIAL_SYNC -> stringResource(R.string.floppy_stage_initial_sync)
    FloppyConnectionStage.COMPLETE -> when (persistentState) {
        FloppyUiState.READY -> stringResource(R.string.floppy_ready)
        FloppyUiState.CONNECTED_INACTIVE -> stringResource(R.string.floppy_connected_inactive)
        else -> stringResource(R.string.floppy_stage_complete)
    }
    FloppyConnectionStage.FAILED -> stringResource(R.string.floppy_connection_failed)
    FloppyConnectionStage.IDLE -> ""
}

@Composable
private fun floppyConnectionErrorMessage(error: FloppyConnectionError): String = when (error) {
    FloppyConnectionError.INVALID_REQUEST -> stringResource(R.string.floppy_error_invalid_url)
    FloppyConnectionError.AUTHENTICATION -> stringResource(R.string.floppy_error_authentication)
    FloppyConnectionError.DNS -> stringResource(R.string.floppy_error_dns)
    FloppyConnectionError.TIMEOUT -> stringResource(R.string.floppy_error_timeout)
    FloppyConnectionError.TLS -> stringResource(R.string.floppy_error_tls)
    FloppyConnectionError.NETWORK -> stringResource(R.string.floppy_error_network)
    FloppyConnectionError.WRONG_API -> stringResource(R.string.floppy_error_wrong_api)
    FloppyConnectionError.INVALID_RESPONSE -> stringResource(R.string.floppy_error_invalid_response)
    FloppyConnectionError.BOOTSTRAP -> stringResource(R.string.floppy_connected_attention)
    FloppyConnectionError.UNKNOWN -> stringResource(R.string.floppy_error_unavailable)
}

@Composable
/** Shared credential/input treatment used by TMDB and Floppy. */
private fun credentialFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Accent,
    unfocusedBorderColor = com.cinetrack.ui.theme.GlassStrong,
    errorBorderColor = androidx.compose.material3.MaterialTheme.colorScheme.error,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
)

internal fun hasUrlScheme(value: String): Boolean = Regex("^[A-Za-z][A-Za-z0-9+.-]*://").containsMatchIn(value.trim())

internal fun withDefaultFloppyScheme(value: String, allowLocalHttp: Boolean): String {
    val trimmed = value.trim()
    if (trimmed.isEmpty() || hasUrlScheme(trimmed)) return trimmed
    return (if (allowLocalHttp) "http://" else "https://") + trimmed
}

private fun isFloppyUrlValid(value: String): Boolean = runCatching {
    val uri = java.net.URI(value.trim())
    uri.scheme?.lowercase() in setOf("http", "https") && !uri.host.isNullOrBlank()
}.getOrDefault(false)

@Composable
private fun FloppyStatePreviewContent(state: AppUiState) {
    SettingsSection(stringResource(R.string.floppy_status)) {
        ValueRow(stringResource(R.string.floppy_status), floppyStatusLabel(state.floppyUiState), state.floppyUiState == FloppyUiState.READY)
        if (state.floppyConnected) {
            ValueRow(stringResource(R.string.floppy_role), if (state.floppyUiState == FloppyUiState.CONNECTED_INACTIVE) stringResource(R.string.floppy_role_connected_inactive) else stringResource(R.string.floppy_role_secondary))
            ValueRow(stringResource(R.string.floppy_initial_sync), if (state.floppyUiState == FloppyUiState.NEEDS_ATTENTION) stringResource(R.string.floppy_initial_sync_failed) else stringResource(R.string.floppy_ready_short))
        }
    }
}

@Preview(name = "Floppy not connected", showBackground = true)
@Composable
private fun FloppyNotConnectedPreview() = FloppyStatePreviewContent(AppUiState(floppyUiState = FloppyUiState.NOT_CONNECTED))

@Preview(name = "Floppy connected inactive", showBackground = true)
@Composable
private fun FloppyConnectedInactivePreview() = FloppyStatePreviewContent(AppUiState(floppyConnected = true, floppyUiState = FloppyUiState.CONNECTED_INACTIVE))

@Preview(name = "Floppy setting up", showBackground = true)
@Composable
private fun FloppySettingUpPreview() = FloppyStatePreviewContent(AppUiState(floppyConnected = true, floppyUiState = FloppyUiState.SETTING_UP))

@Preview(name = "Floppy ready", showBackground = true)
@Composable
private fun FloppyReadyPreview() = FloppyStatePreviewContent(AppUiState(floppyConnected = true, floppyUiState = FloppyUiState.READY))

@Preview(name = "Floppy needs attention", showBackground = true)
@Composable
private fun FloppyNeedsAttentionPreview() = FloppyStatePreviewContent(AppUiState(floppyConnected = true, floppyUiState = FloppyUiState.NEEDS_ATTENTION))

@Composable
private fun AccentChoiceRow(title: String, color: Color, selected: Boolean, onClick: () -> Unit) {
    val clickAction = rememberUiAction(onClick)
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = clickAction).padding(horizontal = com.cinetrack.ui.theme.Spacing.lg, vertical = com.cinetrack.ui.theme.Spacing.md), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(22.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
            if (selected) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
        }
        Spacer(Modifier.width(11.dp))
        Text(title, color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (selected) Text(stringResource(R.string.active), color = color, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold)
    }
}

private const val SavedCredentialMask = "••••••••••••••••••••••••••••••••"

@Composable
private fun ApiCredentialSettings(
    service: String,
    configured: Boolean,
    onSave: (String, (Result<Unit>) -> Unit) -> Unit,
    compact: Boolean = false,
    onSaved: () -> Unit = {},
) {
    var value by remember(service) { mutableStateOf(if (configured) SavedCredentialMask else "") }
    var showingSavedMask by remember(service) { mutableStateOf(configured) }
    var revealNewValue by remember(service) { mutableStateOf(false) }
    var validating by remember(service) { mutableStateOf(false) }
    var validationError by remember(service) { mutableStateOf<String?>(null) }
    LaunchedEffect(configured) {
        if (configured && !validating) {
            value = SavedCredentialMask
            showingSavedMask = true
        }
    }
    val changed = !showingSavedMask && value.isNotBlank()
    // Each service names its own credential and where to get it.
    val help = stringResource(
        when (service) {
            "TMDB" -> R.string.tmdb_api_help
            "Trakt" -> R.string.trakt_api_help
            else -> R.string.mdblist_api_help
        },
    )
    val uriHandler = LocalUriHandler.current
    val credentialUrl = when (service) {
        "TMDB" -> TMDB_API_KEY_URL
        "Trakt" -> TRAKT_API_APPS_URL
        else -> MDBLIST_API_KEY_URL
    }
    val credentialLink = stringResource(
        when (service) {
            "TMDB" -> R.string.get_tmdb_api_key
            "Trakt" -> R.string.get_trakt_client_id
            else -> R.string.get_mdblist_api_key
        },
    )
    val credentialLinkHelp = stringResource(
        when (service) {
            "TMDB" -> R.string.tmdb_api_link_help
            "Trakt" -> R.string.trakt_api_link_help
            else -> R.string.mdblist_api_link_help
        },
    )
    SettingsSection(stringResource(if (service == "Trakt") R.string.client_id_label else R.string.api_credential)) {
        Column(Modifier.padding(com.cinetrack.ui.theme.Spacing.md)) {
            if (!compact) {
            Text(
                if (configured) stringResource(R.string.api_credential_configured, service) else stringResource(R.string.api_credential_missing, service),
                color = if (configured) Color.White else TextSecondary,
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.api_credential_saved_securely),
                color = TextMuted,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                lineHeight = 15.sp,
                modifier = Modifier.padding(top = com.cinetrack.ui.theme.Spacing.xs),
            )
            Text(help, color = TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, lineHeight = 16.sp, modifier = Modifier.padding(top = com.cinetrack.ui.theme.Spacing.sm))
            Text(credentialLinkHelp, color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = com.cinetrack.ui.theme.Spacing.xs))
            TextButton(onClick = { runCatching { uriHandler.openUri(credentialUrl) } }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Link, contentDescription = credentialLink, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(com.cinetrack.ui.theme.Spacing.xs))
                Text(credentialLink, color = AccentLight)
            }
            Spacer(Modifier.height(9.dp))
            }
            OutlinedTextField(
                value = value,
                onValueChange = { newValue ->
                    value = if (showingSavedMask) newValue.replace("•", "") else newValue
                    showingSavedMask = false
                    validationError = null
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !validating,
                label = { Text(stringResource(if (service == "Trakt") R.string.client_id_label else R.string.api_key_label)) },
                visualTransformation = if (revealNewValue && !showingSavedMask) VisualTransformation.None else PasswordVisualTransformation(),
                placeholder = { Text(stringResource(R.string.api_credential_hint, service), style = androidx.compose.material3.MaterialTheme.typography.bodySmall) },
                trailingIcon = {
                    IconButton(
                        onClick = { if (!showingSavedMask) revealNewValue = !revealNewValue },
                        enabled = !showingSavedMask,
                    ) {
                        Icon(
                            if (revealNewValue) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = stringResource(if (revealNewValue) R.string.hide_api_key else R.string.show_api_key),
                            tint = if (showingSavedMask) TextMuted.copy(alpha = .45f) else TextSecondary,
                        )
                    }
                },
                colors = credentialFieldColors(),
            )
            validationError?.let { message ->
                Text(
                    stringResource(R.string.api_credential_invalid, service, message),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(top = com.cinetrack.ui.theme.Spacing.sm),
                )
            }
            Spacer(Modifier.height(9.dp))
            PrimaryAction(
                stringResource(if (compact) R.string.save_and_verify else R.string.save),
                Icons.Filled.Save,
                Modifier.fillMaxWidth(),
                enabled = changed && !validating,
            ) {
                validating = true
                validationError = null
                onSave(value) { result ->
                    validating = false
                    result.onSuccess {
                        value = SavedCredentialMask
                        showingSavedMask = true
                        revealNewValue = false
                        onSaved()
                    }.onFailure { error ->
                        validationError = error.message ?: "Verification failed"
                    }
                }
            }
            if (validating) Text(stringResource(R.string.api_credential_verifying, service), color = AccentLight, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = com.cinetrack.ui.theme.Spacing.sm))
        }
    }
}

@Composable
private fun MetadataSettings(state: AppUiState, viewModel: CineTrackViewModel) {
    var activeChoice by rememberSaveable { mutableStateOf<String?>(null) }
    val locale = com.cinetrack.ui.currentLocale()
    val defaultChoice = "system" to stringResource(R.string.system_default)
    val languages = listOf(defaultChoice) + remember(locale) {
        Locale.getISOLanguages().map { code -> code to Locale(code).getDisplayLanguage(locale) }.sortedBy { it.second }
    }
    val regions = listOf(defaultChoice) + remember(locale) {
        Locale.getISOCountries().map { code -> code to Locale("", code).getDisplayCountry(locale) }.sortedBy { it.second }
    }
    val timezones = listOf(defaultChoice) + remember { java.time.ZoneId.getAvailableZoneIds().sorted().map { it to it.replace('_', ' ') } }
    val language = languages.firstOrNull { it.first == state.metadataLanguage }?.second
        ?: Locale.forLanguageTag(state.metadataLanguage).getDisplayName(locale)
    val region = regions.firstOrNull { it.first == state.metadataRegion }?.second ?: state.metadataRegion
    val timezone = timezones.firstOrNull { it.first == state.metadataTimezone }?.second ?: state.metadataTimezone
    val airDateSources = listOf(
        com.cinetrack.data.schedule.AirDateSource.TMDB to "TMDB",
        com.cinetrack.data.schedule.AirDateSource.TRAKT to "Trakt",
        com.cinetrack.data.schedule.AirDateSource.SIMKL to "Simkl",
    )
    // One group instead of three single-row cards.
    SettingsSection(stringResource(R.string.section_metadata)) {
        KitRow(Icons.Filled.Language, stringResource(R.string.language), value = language, onClick = { activeChoice = "language" })
        KitRow(Icons.Filled.Place, stringResource(R.string.region_short), value = region, onClick = { activeChoice = "region" })
        KitRow(Icons.Filled.Schedule, stringResource(R.string.timezone_short), value = timezone, onClick = { activeChoice = "timezone" })
        // The provider whose episode air dates are shown; the others fill in where it has none.
        KitRow(Icons.Filled.CalendarMonth, stringResource(R.string.air_dates_from), value = airDateSources.firstOrNull { it.first == state.airDateSource }?.second ?: "Trakt", onClick = { activeChoice = "airdates" })
        KitToggleRow(Icons.Filled.VisibilityOff, stringResource(R.string.hide_adult_titles), stringResource(R.string.hide_adult_titles_sub), state.hideAdultTitles, viewModel::setHideAdultTitles)
    }
    activeChoice?.let { key ->
        val choices = when (key) { "language" -> languages; "region" -> regions; "airdates" -> airDateSources; else -> timezones }
        val selected = when (key) { "language" -> state.metadataLanguage; "region" -> state.metadataRegion; "airdates" -> state.airDateSource; else -> state.metadataTimezone }
        // Preserve legacy region-specific language tags as selectable choices.
        val options = if (choices.any { it.first == selected }) choices else listOf(selected to language) + choices
        SearchableChoiceSheet(
            stringResource(when (key) { "language" -> R.string.metadata_language; "region" -> R.string.metadata_region; "airdates" -> R.string.air_dates_sheet_title; else -> R.string.metadata_timezone }),
            stringResource(R.string.search), options, selected, { activeChoice = null },
            onSelected = { value ->
                when (key) { "language" -> viewModel.setMetadataLanguage(value); "region" -> viewModel.setMetadataRegion(value); "airdates" -> viewModel.setAirDateSource(value); else -> viewModel.setMetadataTimezone(value) }
                activeChoice = null
            },
        )
    }
}

@Composable
private fun NotificationSettings(state: AppUiState, viewModel: CineTrackViewModel) {
    val context = LocalContext.current
    val hiddenEpisodes = remember(state.episodes, state.hiddenUpcoming) {
        state.episodes.filter { it.scheduleKey in state.hiddenUpcoming }
            .distinctBy { it.scheduleKey }
            .sortedBy { it.airDate }
    }
    var notificationsGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsGranted = granted
    }
    if (!notificationsGranted && Build.VERSION.SDK_INT >= 33) {
        KitCallout(
            Icons.Filled.NotificationsOff,
            stringResource(R.string.notif_permission_title),
            stringResource(R.string.notif_permission_body),
            stringResource(R.string.allow),
        ) { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
    }
    SettingsSection(stringResource(R.string.notifications)) {
        KitToggleRow(Icons.Filled.Tv, stringResource(R.string.new_episodes), stringResource(R.string.notif_episodes_sub), state.notificationEpisodes) { viewModel.setNotification("episodes", it) }
        KitToggleRow(Icons.Filled.Movie, stringResource(R.string.movie_releases), stringResource(R.string.notif_movies_sub), state.notificationMovies) { viewModel.setNotification("movies", it) }
        KitToggleRow(Icons.Filled.SyncProblem, stringResource(R.string.sync_problems), stringResource(R.string.notif_sync_sub), state.notificationSync) { viewModel.setNotification("sync", it) }
        KitToggleRow(Icons.Filled.Bedtime, stringResource(R.string.quiet_hours), stringResource(R.string.quiet_hours_sub), state.quietHoursEnabled) { viewModel.setQuietHours(it) }
    }
    if (state.quietHoursEnabled) {
        val quietHourOptions = listOf(22 to 7, 23 to 8, 0 to 8)
        SettingsSection(stringResource(R.string.quiet_hours_schedule)) {
            quietHourOptions.forEach { (start, end) ->
                ChoiceRow(
                    "%02d:00–%02d:00".format(start, end),
                    state.quietHoursStart == start && state.quietHoursEnd == end,
                ) { viewModel.setQuietHours(true, start, end) }
            }
        }
    }
    SettingsSection(stringResource(R.string.upcoming_episodes)) {
        KitToggleRow(Icons.Filled.Star, stringResource(R.string.exclude_specials), stringResource(R.string.exclude_specials_sub), state.excludeSpecials, viewModel::setExcludeSpecials)
    }
    KitFootnote(stringResource(R.string.upcoming_diagnostic_explanation))
    if (hiddenEpisodes.isNotEmpty()) {
        SettingsSection(stringResource(R.string.hidden_upcoming)) {
            hiddenEpisodes.forEach { episode ->
                val showTitle = state.allMedia.firstOrNull { it.type == MediaType.TV && it.id == episode.showId }?.title.orEmpty()
                Row(
                    Modifier.fillMaxWidth().padding(start = com.cinetrack.ui.theme.Spacing.lg, end = com.cinetrack.ui.theme.Spacing.sm, top = com.cinetrack.ui.theme.Spacing.sm, bottom = com.cinetrack.ui.theme.Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(showTitle.ifBlank { episode.title }, color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text("${episode.label.replace(" · ", " ")} · ${episode.title}", color = TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = { viewModel.restoreHiddenUpcomingEpisode(episode) }) {
                        Icon(Icons.Filled.Visibility, stringResource(R.string.restore_upcoming_episode, episode.title), tint = AccentLight)
                    }
                }
            }
        }
    }
    SettingsSection(stringResource(R.string.section_actions)) {
        if (state.hiddenUpcoming.isNotEmpty()) {
            KitRow(Icons.Filled.Visibility, stringResource(R.string.restore_hidden_upcoming, state.hiddenUpcoming.size), onClick = viewModel::restoreHiddenUpcoming)
        }
        KitRow(Icons.Filled.CalendarMonth, stringResource(R.string.export_calendar), stringResource(R.string.export_calendar_sub), onClick = { viewModel.exportCalendar(context) })
    }
}

@Composable
private fun LanguageSettings(viewModel: CineTrackViewModel) {
    val currentLanguage = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        .substringBefore(',')
        .takeIf(String::isNotBlank)
        ?.substringBefore('-')
        ?: com.cinetrack.ui.currentLocale().language
    var language by rememberSaveable { mutableStateOf(currentLanguage) }
    SettingsSection(stringResource(R.string.language)) {
        ChoiceRow(stringResource(R.string.italian), language == "it") {
            language = "it"
            viewModel.setLanguage("it")
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("it"))
        }
        ChoiceRow(stringResource(R.string.english), language == "en") {
            language = "en"
            viewModel.setLanguage("en")
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en"))
        }
    }
}

@Composable
private fun RatingSettings(state: AppUiState, viewModel: CineTrackViewModel) {
    val labels = listOf("IMDb", "TMDB", "Metacritic", "Rotten Tomatoes")
    fun sourceKey(label: String) = when (label) {
        "Rotten Tomatoes" -> "tomatoes"
        else -> label.lowercase()
    }
    SettingsSection(stringResource(R.string.section_sources_shown)) {
        androidx.compose.foundation.layout.FlowRow(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            labels.forEach { label ->
                val on = sourceKey(label) in state.ratingSources
                Row(
                    Modifier.heightIn(min = 40.dp).tonalPill(on, Color(0xFF5DCAA5))
                        .toggleable(value = on, role = Role.Checkbox) { viewModel.setRatingSource(label, it) }
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (on) {
                        Icon(Icons.Filled.Check, null, tint = Color(0xFF5DCAA5), modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(label, color = if (on) TextPrimary else TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    // How the ratings row on a detail page will look, with sample values.
    val samples = mapOf("imdb" to ("IMDB" to "7,8"), "tmdb" to ("TMDB" to "8,2"), "metacritic" to ("METACRITIC" to "77"), "tomatoes" to ("RT" to "91%"))
    val shown = labels.map(::sourceKey).filter { it in state.ratingSources }.mapNotNull { samples[it] }
    if (shown.isNotEmpty()) {
        SettingsSection(stringResource(R.string.section_preview)) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                shown.forEach { (source, value) ->
                    Row(Modifier.height(36.dp).tonalPill(false).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(source, color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Spacer(Modifier.width(6.dp))
                        Text(value, color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
        KitFootnote(stringResource(R.string.preview_example))
    }
}

@Composable
private fun ContentRegionSettings(state: AppUiState, viewModel: CineTrackViewModel, onPage: (String) -> Unit = {}) {
    var showRegions by remember { mutableStateOf(false) }
    val locale = com.cinetrack.ui.currentLocale()
    val summary = if (state.contentRegions.isEmpty()) stringResource(R.string.all_regions) else
        state.contentRegions.sorted().joinToString(", ") { java.util.Locale("", it).getDisplayCountry(locale) }
    SettingsSection(stringResource(R.string.section_discover_streaming)) {
        KitRow(Icons.Filled.Public, stringResource(R.string.content_regions), summary, onClick = { showRegions = true })
        KitRow(Icons.Filled.Tv, stringResource(R.string.preferred_providers), stringResource(R.string.providers_selected_count, state.preferredProviders.size), onClick = { onPage(SettingsPages.Streaming) })
        if (state.hiddenDiscovery.isNotEmpty()) {
            KitRow(
                Icons.Filled.Visibility,
                pluralStringResource(R.plurals.restore_hidden_recommendations, state.hiddenDiscovery.size, state.hiddenDiscovery.size),
                onClick = viewModel::restoreHiddenDiscovery,
            )
        }
    }
    KitFootnote(stringResource(R.string.content_regions_scope))
    if (showRegions) ContentRegionsSheet(state.contentRegions, { showRegions = false }) {
        viewModel.setContentRegions(it)
        showRegions = false
    }
}

@Composable
private fun LogsSettings(viewModel: CineTrackViewModel) {
    val context = LocalContext.current
    val logs by viewModel.errorLogs.collectAsStateWithLifecycle()
    // Background workers append to the log file directly; re-read it on open.
    androidx.compose.runtime.LaunchedEffect(Unit) { viewModel.refreshErrorLogs() }
    var showAll by rememberSaveable { mutableStateOf(false) }
    val entries = remember(logs) { logs.map { com.cinetrack.domain.parseLogLine(it) }.asReversed() }
    val problems = entries.count { it.severity != com.cinetrack.domain.LogSeverity.INFO }
    val visible = (if (showAll) entries else entries.filter { it.severity != com.cinetrack.domain.LogSeverity.INFO }).take(150)
    val zone = java.time.ZoneId.systemDefault()
    val today = java.time.LocalDate.now(zone)
    KitStatusCard(
        ok = problems == 0,
        title = if (problems == 0) stringResource(R.string.no_errors_logged) else pluralStringResource(R.plurals.logs_problem_count, problems, problems),
        subtitle = stringResource(if (problems == 0) R.string.logs_ok_sub else R.string.logs_problems_sub),
    )
    SettingsSection(stringResource(R.string.logs)) {
        KitToggleRow(Icons.Filled.Visibility, stringResource(R.string.logs_show_all), stringResource(R.string.logs_show_all_sub), showAll) { showAll = it }
        KitRow(Icons.Filled.Download, stringResource(R.string.export_logs), stringResource(R.string.logs_export_copy), onClick = { if (logs.isNotEmpty()) viewModel.exportLogs(context) })
        // Two taps, so a stray tap never wipes what is needed to report a problem.
        var confirmClear by remember { mutableStateOf(false) }
        KitRow(
            Icons.Filled.Delete,
            stringResource(R.string.clear_logs),
            stringResource(if (confirmClear) R.string.clear_logs_confirm else R.string.clear_logs_sub),
            onClick = {
                if (confirmClear) {
                    viewModel.clearErrorLogs()
                    confirmClear = false
                } else if (logs.isNotEmpty()) confirmClear = true
            },
        )
    }
    if (visible.isNotEmpty()) {
        visible.groupBy { entry -> entry.at?.atZone(zone)?.toLocalDate() }.forEach { (day, dayEntries) ->
            val dayLabel = when (day) {
                null -> stringResource(R.string.logs_undated)
                today -> stringResource(R.string.logs_today)
                today.minusDays(1) -> stringResource(R.string.logs_yesterday)
                else -> day.format(com.cinetrack.ui.UiDateFormatters.current.date)
            }
            SettingsSection(dayLabel) {
                dayEntries.forEachIndexed { index, entry ->
                    LogEntryRow(entry, zone)
                }
            }
        }
    }
}

@Composable
private fun LogEntryRow(entry: com.cinetrack.domain.LogEntry, zone: java.time.ZoneId) {
    var expanded by remember(entry) { mutableStateOf(false) }
    val (tag, color) = when (entry.severity) {
        com.cinetrack.domain.LogSeverity.ERROR -> stringResource(R.string.logs_error) to com.cinetrack.ui.theme.StatusDropped
        com.cinetrack.domain.LogSeverity.WARNING -> stringResource(R.string.logs_warning) to com.cinetrack.ui.theme.StatusPaused
        com.cinetrack.domain.LogSeverity.INFO -> stringResource(R.string.logs_info) to com.cinetrack.ui.theme.Info
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(horizontal = com.cinetrack.ui.theme.Spacing.lg, vertical = com.cinetrack.ui.theme.Spacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                tag,
                color = color,
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .background(color.copy(alpha = .16f), androidx.compose.foundation.shape.RoundedCornerShape(50))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
            Spacer(Modifier.width(com.cinetrack.ui.theme.Spacing.sm))
            entry.at?.let {
                Text(
                    it.atZone(zone).format(com.cinetrack.ui.UiDateFormatters.current.time),
                    color = TextMuted,
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                )
            }
        }
        Text(
            entry.summary,
            color = TextPrimary,
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (expanded && entry.detail != entry.summary) {
            Text(
                entry.detail,
                color = TextSecondary,
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                lineHeight = 14.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun ExportSettings(viewModel: CineTrackViewModel) {
    val context = LocalContext.current
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.restoreData(context, it) }
    }
    val sections = listOf(
        Triple("library", Icons.Filled.Bookmark, stringResource(R.string.library) to stringResource(R.string.export_library_sub)),
        Triple("history", Icons.Filled.History, stringResource(R.string.history) to stringResource(R.string.export_history_sub)),
        Triple("progress", Icons.Filled.PlayArrow, stringResource(R.string.progress) to stringResource(R.string.export_progress_sub)),
        Triple("settings", Icons.Filled.Tune, stringResource(R.string.settings) to stringResource(R.string.export_settings_sub)),
    )
    var selected by remember { mutableStateOf(sections.associate { it.first to true }) }
    SettingsSection(stringResource(R.string.section_contents)) {
        sections.forEach { (key, icon, text) ->
            KitToggleRow(icon, text.first, text.second, selected[key] == true) { selected = selected + (key to it) }
        }
    }
    KitPrimaryButton(stringResource(R.string.create_backup), Icons.Filled.Download, Accent, enabled = selected.values.any { it }) {
        viewModel.exportData(context, selected.filterValues { it }.keys)
    }
    SettingsSection(stringResource(R.string.section_restore)) {
        KitRow(Icons.Filled.FolderOpen, stringResource(R.string.restore_file_title), stringResource(R.string.restore_file_sub), onClick = { restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream")) })
        KitRow(Icons.Filled.Restore, stringResource(R.string.restore_auto_title), stringResource(R.string.restore_auto_sub), onClick = viewModel::restoreAutomaticBackup)
    }
    KitFootnote(stringResource(R.string.restore_backup_explanation))
}

@Composable
private fun AboutSettings(viewModel: CineTrackViewModel) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val updateState by viewModel.appUpdateState.collectAsStateWithLifecycle()
    val changelogState by viewModel.appChangelogState.collectAsStateWithLifecycle()
    var showChangelog by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (updateState is AppUpdateState.Idle) viewModel.checkForAppUpdate()
    }
    val statusText = when (val current = updateState) {
        AppUpdateState.Idle -> stringResource(R.string.updates_from_github)
        AppUpdateState.Checking -> stringResource(R.string.checking_for_updates)
        AppUpdateState.UpToDate -> stringResource(R.string.app_is_up_to_date)
        is AppUpdateState.Available -> stringResource(R.string.update_available, current.update.version)
        is AppUpdateState.Downloading -> stringResource(R.string.downloading_update, (current.progress * 100).toInt())
        is AppUpdateState.Error -> current.message
    }
    val updateAvailable = updateState is AppUpdateState.Available
    val busy = updateState is AppUpdateState.Checking || updateState is AppUpdateState.Downloading
    // The app's own header, in the same style as the service pages.
    Box(
        Modifier.padding(horizontal = 12.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(Accent.copy(alpha = .38f), Color(0xFF10141A), Color(0xFF0B0D11))))
            .border(.6.dp, Color.White.copy(alpha = .10f), RoundedCornerShape(28.dp))
            .padding(vertical = 26.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BrandMark(76.dp)
            Spacer(Modifier.height(12.dp))
            Text("CineTrack", color = TextPrimary, fontWeight = FontWeight.ExtraBold, style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.version_label, BuildConfig.VERSION_NAME), color = TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
            KitStatusPill(active = updateState is AppUpdateState.UpToDate, text = statusText, modifier = Modifier.padding(top = 10.dp))
        }
    }
    SettingsSection(stringResource(R.string.about_updates)) {
        KitRow(
            if (updateAvailable) Icons.Filled.Download else Icons.Filled.Refresh,
            if (updateAvailable) stringResource(R.string.download_and_install) else stringResource(R.string.check_for_updates),
            statusText,
            onClick = { if (!busy) { if (updateAvailable) viewModel.openAppUpdate(context) else viewModel.checkForAppUpdate() } },
        )
        KitRow(Icons.Filled.History, stringResource(R.string.changelog), onClick = {
            showChangelog = true
            viewModel.loadAppChangelog()
        })
    }
    SettingsSection(stringResource(R.string.about_services)) {
        listOf(
            Triple("TMDB", "https://www.themoviedb.org/", R.string.tmdb_description),
            Triple("Simkl", "https://simkl.com/", R.string.simkl_description),
            Triple("Trakt", "https://trakt.tv/", R.string.trakt_description),
            Triple("MDBList", "https://mdblist.com/", R.string.mdblist_description),
        ).forEach { (name, url, description) ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 58.dp).clickable { runCatching { uriHandler.openUri(url) } }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ServiceLogo(name, size = 32.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Text(stringResource(description), color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(Icons.AutoMirrored.Filled.OpenInNew, null, tint = TextMuted, modifier = Modifier.size(16.dp))
            }
        }
    }
    if (showChangelog) {
        ChangelogDialog(changelogState) { showChangelog = false }
    }
}

@Composable
private fun ChangelogDialog(state: AppChangelogState, onDismiss: () -> Unit) {
    com.cinetrack.ui.components.SharedGlassDialog(onDismiss) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 620.dp)
                .padding(com.cinetrack.ui.theme.Spacing.lg),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.History, null, tint = AccentLight, modifier = Modifier.size(23.dp))
                Spacer(Modifier.width(9.dp))
                Text(
                    stringResource(R.string.changelog),
                    color = TextPrimary,
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(38.dp)) {
                    Icon(Icons.Filled.Close, stringResource(R.string.close), tint = TextSecondary)
                }
            }
            Spacer(Modifier.height(10.dp))
            when (state) {
                AppChangelogState.Idle, AppChangelogState.Loading -> Box(
                    Modifier.fillMaxWidth().height(150.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    com.cinetrack.ui.components.SkeletonLines(Modifier.fillMaxWidth().padding(com.cinetrack.ui.theme.Spacing.lg))
                }
                AppChangelogState.Empty -> Text(
                    stringResource(R.string.changelog_empty),
                    color = TextSecondary,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                )
                is AppChangelogState.Error -> Text(
                    state.message,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                )
                is AppChangelogState.Available -> Column(
                    Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()),
                ) {
                    Text(state.update.title, color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(R.string.changelog_version, state.update.version),
                        color = AccentLight,
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        state.update.notes.ifBlank { stringResource(R.string.changelog_empty) },
                        color = TextSecondary,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        lineHeight = 17.sp,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            PrimaryAction(
                text = stringResource(R.string.close),
                icon = Icons.Filled.Close,
                modifier = Modifier.fillMaxWidth(),
                onClick = onDismiss,
            )
        }
    }
}

@Composable
internal fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(horizontal = com.cinetrack.ui.theme.Spacing.xl, vertical = com.cinetrack.ui.theme.Spacing.md)) {
        SettingsGroupLabel(title)
        Spacer(Modifier.height(com.cinetrack.ui.theme.Spacing.sm))
        Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(18.dp)).padding(vertical = 4.dp)) { content() }
    }
}

@Composable
private fun currentAppLanguageLabel(): String {
    val language = AppCompatDelegate.getApplicationLocales().toLanguageTags().substringBefore('-').substringBefore(',')
        .ifBlank { com.cinetrack.ui.currentLocale().language }
    return stringResource(if (language == "it") R.string.italian else R.string.english)
}

@Composable
private fun ToggleRow(title: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(value = checked, role = Role.Switch, onValueChange = onChecked).padding(horizontal = com.cinetrack.ui.theme.Spacing.lg, vertical = com.cinetrack.ui.theme.Spacing.md), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        CompactSwitch(checked)
    }
}

@Composable
private fun CompactSwitch(checked: Boolean) {
    val knobOffset by animateDpAsState(if (checked) 22.dp else 3.dp, label = "switchKnob")
    Box(
        Modifier.width(52.dp).height(32.dp).clip(CircleShape)
            .background(if (checked) Accent else com.cinetrack.ui.theme.Glass)
            .border(.7.dp, AccentLight.copy(alpha = .24f), CircleShape),
    ) {
        // Glass knob over the track; the track and card it covers are redrawn under it.
        val track = if (checked) Accent else com.cinetrack.ui.theme.Glass
        Box(
            Modifier.offset(x = knobOffset, y = 3.dp).size(26.dp)
                .liquidGlass(
                    surface = if (checked) Color.White.copy(alpha = .78f) else TextMuted.copy(alpha = .45f),
                    refraction = 6.dp,
                    under = track.compositeOver(com.cinetrack.ui.components.GlassMaterial.Content),
                    shadow = false,
                ) { clip(CircleShape).background(if (checked) Color.White else TextMuted.copy(alpha = .55f)) },
        )
    }
}

@Composable
private fun ValueRow(title: String, value: String, success: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(horizontal = com.cinetrack.ui.theme.Spacing.lg, vertical = com.cinetrack.ui.theme.Spacing.md), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text(value, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End, color = if (success) TextPrimary else TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ProviderRow(name: String, subtitle: String, configured: Boolean, onClick: () -> Unit) {
    val clickAction = rememberUiAction(onClick)
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = clickAction).padding(horizontal = com.cinetrack.ui.theme.Spacing.lg, vertical = com.cinetrack.ui.theme.Spacing.md), verticalAlignment = Alignment.CenterVertically) {
        ServiceLogo(name)
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) { Text(name, color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold); Text(subtitle, color = TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        StatusDot(configured)
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Filled.ChevronRight, null, tint = TextMuted, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun ServiceLogo(name: String, siteUrl: String? = null, size: androidx.compose.ui.unit.Dp = 38.dp) {
    val uriHandler = LocalUriHandler.current
    val (logo, background, logoSize) = when (name.lowercase()) {
        "tmdb" -> Triple(R.drawable.ic_service_tmdb, com.cinetrack.ui.theme.SurfacePalette.OceanDeep, 29.dp)
        "mdblist" -> Triple(R.drawable.ic_service_mdblist, com.cinetrack.ui.theme.SurfacePalette.CoolText, 24.dp)
        // Floppy's own app icon, full bleed.
        "floppy" -> Triple(R.drawable.floppy_app_icon, Color.Transparent, 38.dp)
        "trakt" -> Triple(R.drawable.ic_service_trakt, androidx.compose.ui.graphics.Color(0xFFED1C24), 26.dp)
        else -> Triple(R.drawable.ic_service_simkl, com.cinetrack.ui.theme.SurfacePalette.WarmText, 24.dp)
    }
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * .3f)).background(background)
            .border(.6.dp, Color.White.copy(alpha = .10f), RoundedCornerShape(size * .3f))
            .then(
                if (siteUrl != null) Modifier.clickable(onClick = rememberUiAction { runCatching { uriHandler.openUri(siteUrl) } })
                else Modifier,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(logo),
            contentDescription = name,
            modifier = Modifier.size(logoSize * (size / 38.dp)),
        )
    }
}

@Composable
internal fun ChoiceRow(title: String, selected: Boolean, description: String? = null, onClick: () -> Unit) {
    val clickAction = rememberUiAction(onClick)
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = clickAction).padding(horizontal = com.cinetrack.ui.theme.Spacing.lg, vertical = com.cinetrack.ui.theme.Spacing.md), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = if (selected) AccentLight else TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Bold)
            if (!description.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(description, color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, lineHeight = 14.sp)
            }
        }
        if (selected) Icon(Icons.Filled.Check, null, tint = AccentLight, modifier = Modifier.size(20.dp))
    }
}

// ---------------------------------------------------------------- settings kit
// Shared pieces for the settings pages: a brand header for services, grouped icon rows,
// toggles that say what they do, numbered steps and status cards.

internal data class ServiceBrand(
    val name: String,
    val brush: Brush,
    val button: Color,
    val logo: Int,
    val wordmark: Int? = null,
)

private fun serviceBrand(page: String): ServiceBrand? = when (page) {
    SettingsPages.ServiceTmdb -> ServiceBrand("TMDB", Brush.linearGradient(listOf(Color(0xFF0D253F), Color(0xFF0B5C7A), Color(0xFF01B4E4))), Color(0xFF0B7FA6), R.drawable.ic_service_tmdb)
    SettingsPages.ServiceSimkl -> ServiceBrand("Simkl", Brush.linearGradient(listOf(Color(0xFF151515), Color(0xFF2A2A2A), Color(0xFF141414))), Color(0xFF3B8F5A), R.drawable.ic_service_simkl)
    SettingsPages.ServiceFloppy -> ServiceBrand("Floppy", Brush.linearGradient(listOf(Color(0xFF0E1B33), Color(0xFF1C2F5C), Color(0xFF14204A))), Color(0xFF185FA5), R.drawable.floppy_app_icon, R.drawable.floppy_wordmark)
    SettingsPages.ServiceTrakt -> ServiceBrand("Trakt", Brush.linearGradient(listOf(Color(0xFF7D2C92), Color(0xFFC2185B), Color(0xFFED1C24))), Color(0xFFC2185B), R.drawable.ic_service_trakt)
    SettingsPages.ServiceMdblist -> ServiceBrand("MDBList", Brush.linearGradient(listOf(Color(0xFF0C447C), Color(0xFF2A6FB5))), Color(0xFF2A6FB5), R.drawable.ic_service_mdblist)
    else -> null
}

/** The service's header: its colours, its mark repeated faintly, and where things stand. */
@Composable
private fun ServiceHero(brand: ServiceBrand, state: AppUiState, onBack: () -> Unit) {
    val active = when (brand.name) {
        "TMDB" -> state.tmdbApiConfigured
        "Simkl" -> state.simklConnected
        "Floppy" -> state.floppyConnected
        "Trakt" -> state.traktConfigured
        else -> state.mdbListApiConfigured
    }
    val status = when {
        brand.name == "Simkl" && active && !state.simklUsername.isNullOrBlank() -> stringResource(R.string.simkl_connected_as, state.simklUsername.orEmpty())
        brand.name == "Floppy" && active -> floppyConnectedLabel(state)
        brand.name in setOf("Simkl", "Floppy") -> stringResource(if (active) R.string.connected else R.string.not_connected)
        else -> stringResource(if (active) R.string.configured else R.string.not_configured)
    }
    val description = stringResource(
        when (brand.name) {
            "TMDB" -> R.string.tmdb_description
            "Simkl" -> R.string.simkl_description
            "Floppy" -> R.string.floppy_self_hosted_description
            "Trakt" -> R.string.trakt_description
            else -> R.string.mdblist_description
        },
    )
    val shape = RoundedCornerShape(28.dp)
    Box(
        Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 6.dp).fillMaxWidth().clip(shape)
            .background(brand.brush).border(.6.dp, Color.White.copy(alpha = .10f), shape),
    ) {
        Image(
            painterResource(brand.logo), null,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 40.dp, end = 18.dp).size(96.dp)
                .clip(RoundedCornerShape(26.dp)).graphicsLayer { alpha = .14f },
        )
        Column(Modifier.padding(start = 10.dp, end = 18.dp, top = 10.dp, bottom = 20.dp)) {
            GlassBackButton(onClick = onBack)
            Spacer(Modifier.height(18.dp))
            Column(Modifier.padding(start = 8.dp)) {
                if (brand.wordmark != null) {
                    Image(painterResource(brand.wordmark), brand.name, modifier = Modifier.height(46.dp))
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ServiceLogo(brand.name, size = 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(brand.name, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
                KitStatusPill(active, status, Modifier.padding(top = 12.dp))
                Text(description, color = Color.White.copy(alpha = .78f), style = androidx.compose.material3.MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 10.dp))
            }
        }
    }
}

@Composable
private fun KitStatusPill(active: Boolean, text: String, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Pill)).background(Color.Black.copy(alpha = .28f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(active)
        Spacer(Modifier.width(7.dp))
        Text(text, color = Color.White, style = androidx.compose.material3.MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Icon, title, optional description, then a value, a chevron when it opens something, or [trailing]. */
@Composable
private fun KitRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val clickAction = onClick?.let { rememberUiAction(it) }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .then(if (clickAction != null) Modifier.clickable(onClick = clickAction) else Modifier)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(Color.White.copy(alpha = .07f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            if (!subtitle.isNullOrBlank()) Text(subtitle, color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (value != null) {
            Spacer(Modifier.width(8.dp))
            Text(value, color = TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 150.dp))
        }
        when {
            trailing != null -> { Spacer(Modifier.width(8.dp)); trailing() }
            clickAction != null -> { Spacer(Modifier.width(4.dp)); Icon(Icons.Filled.ChevronRight, null, tint = TextMuted, modifier = Modifier.size(18.dp)) }
        }
    }
}

@Composable
private fun KitToggleRow(icon: ImageVector, title: String, subtitle: String?, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).toggleable(value = checked, role = Role.Switch, onValueChange = onChecked)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(Color.White.copy(alpha = .07f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            if (!subtitle.isNullOrBlank()) Text(subtitle, color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(10.dp))
        CompactSwitch(checked)
    }
}

/** Big numbers side by side, for counts and times. */
@Composable
private fun KitStatTiles(stats: List<Pair<String, String>>) {
    Row(
        Modifier.padding(horizontal = com.cinetrack.ui.theme.Spacing.xl, vertical = com.cinetrack.ui.theme.Spacing.sm).fillMaxWidth()
            .height(androidx.compose.foundation.layout.IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        stats.forEach { (value, label) ->
            Column(Modifier.weight(1f).fillMaxHeight().glass(RoundedCornerShape(18.dp)).padding(horizontal = 12.dp, vertical = 12.dp)) {
                Text(value, color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(label, color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelMedium, maxLines = 2)
            }
        }
    }
}

@Composable
private fun KitPrimaryButton(text: String, icon: ImageVector, color: Color, enabled: Boolean = true, onClick: () -> Unit) {
    val clickAction = rememberUiAction(onClick)
    val shape = RoundedCornerShape(com.cinetrack.ui.theme.Radius.Pill)
    Row(
        Modifier.padding(horizontal = com.cinetrack.ui.theme.Spacing.xl, vertical = com.cinetrack.ui.theme.Spacing.sm).fillMaxWidth().height(52.dp)
            .clip(shape).background(if (enabled) color.copy(alpha = .9f) else Color.White.copy(alpha = .08f))
            .border(.6.dp, Color.White.copy(alpha = .14f), shape)
            .clickable(enabled = enabled, onClick = clickAction),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (enabled) Color.White else TextMuted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = if (enabled) Color.White else TextMuted, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun KitDangerText(text: String, onClick: () -> Unit) {
    val clickAction = rememberUiAction(onClick)
    Box(Modifier.fillMaxWidth().padding(top = com.cinetrack.ui.theme.Spacing.md), contentAlignment = Alignment.Center) {
        Text(
            text,
            color = Color(0xFFF09595),
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Pill)).clickable(onClick = clickAction).padding(horizontal = 18.dp, vertical = 12.dp),
        )
    }
}

/** A destructive text button that asks for confirmation before running [onConfirm]. */
@Composable
private fun ConfirmedDangerText(text: String, title: String, body: String, onConfirm: () -> Unit) {
    var asking by rememberSaveable { mutableStateOf(false) }
    KitDangerText(text) { asking = true }
    if (asking) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text(title) },
            text = { Text(body, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = { asking = false; onConfirm() }) {
                    Text(stringResource(R.string.disconnect), color = Color(0xFFF09595), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { asking = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun KitFootnote(text: String) {
    Text(text, color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, lineHeight = 15.sp,
        modifier = Modifier.padding(horizontal = com.cinetrack.ui.theme.Spacing.xxl, vertical = 2.dp))
}

@Composable
private fun KitNotice(text: String) {
    Text(
        text,
        color = Color(0xFFF7C1C1),
        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(horizontal = com.cinetrack.ui.theme.Spacing.xl, vertical = com.cinetrack.ui.theme.Spacing.xs).fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)).background(Color(0x33E24B4A)).padding(14.dp),
    )
}

/** All-clear or problem summary with a large icon. */
@Composable
private fun KitStatusCard(ok: Boolean, title: String, subtitle: String) {
    val tint = if (ok) Color(0xFF5DCAA5) else Color(0xFFF0997B)
    Row(
        Modifier.padding(horizontal = com.cinetrack.ui.theme.Spacing.xl, vertical = com.cinetrack.ui.theme.Spacing.sm).fillMaxWidth()
            .glass(RoundedCornerShape(20.dp)).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(tint.copy(alpha = .16f)), contentAlignment = Alignment.Center) {
            Icon(if (ok) Icons.Filled.Check else Icons.Filled.ErrorOutline, null, tint = tint, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.ExtraBold)
            Text(subtitle, color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
        }
    }
}

/** A short call to action, e.g. a permission that is missing. */
@Composable
private fun KitCallout(icon: ImageVector, title: String, body: String, action: String, onAction: () -> Unit) {
    val clickAction = rememberUiAction(onAction)
    Row(
        Modifier.padding(horizontal = com.cinetrack.ui.theme.Spacing.xl, vertical = com.cinetrack.ui.theme.Spacing.sm).fillMaxWidth()
            .clip(RoundedCornerShape(20.dp)).background(Accent.copy(alpha = .16f))
            .border(.6.dp, Accent.copy(alpha = .35f), RoundedCornerShape(20.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = AccentLight, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.ExtraBold)
            Text(body, color = TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.width(8.dp))
        Text(action, color = Color.White, style = androidx.compose.material3.MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.clip(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Pill)).background(Accent).clickable(onClick = clickAction).padding(horizontal = 14.dp, vertical = 8.dp))
    }
}

/** Instructions as short numbered steps, with an optional link underneath. */
@Composable
private fun KitSteps(title: String, steps: List<String>, linkLabel: String? = null, onLink: () -> Unit = {}) {
    SettingsSection(title) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            steps.forEachIndexed { index, step ->
                Row(verticalAlignment = Alignment.Top) {
                    Box(Modifier.size(22.dp).clip(CircleShape).background(Color.White.copy(alpha = .10f)), contentAlignment = Alignment.Center) {
                        Text("${index + 1}", color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(step, color = TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp))
                }
            }
            if (linkLabel != null) {
                val clickAction = rememberUiAction(onLink)
                Row(
                    Modifier.clip(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Pill)).clickable(onClick = clickAction).padding(vertical = 6.dp, horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, null, tint = AccentLight, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(linkLabel, color = AccentLight, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * A credential as one row ("Saved and verified · Edit"); the form unfolds when there is
 * none yet or when editing. No characters of the saved value are ever shown.
 */
@Composable
private fun CredentialBlock(service: String, configured: Boolean, onSave: (String, (Result<Unit>) -> Unit) -> Unit) {
    var editing by rememberSaveable(service) { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val showForm = editing || !configured
    if (configured && !editing) {
        SettingsSection(stringResource(if (service == "Trakt") R.string.client_id_label else R.string.api_credential)) {
            KitRow(Icons.Filled.Key, stringResource(R.string.credential_saved_verified), trailing = {
                Text(
                    stringResource(R.string.credential_edit),
                    color = AccentLight,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Pill)).clickable { editing = true }.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            })
        }
    }
    if (showForm) {
        ApiCredentialSettings(service, configured, onSave, compact = true, onSaved = { editing = false })
        val steps = when (service) {
            "TMDB" -> listOf(R.string.tmdb_step_1, R.string.tmdb_step_2, R.string.tmdb_step_3)
            "Trakt" -> listOf(R.string.trakt_step_1, R.string.trakt_step_2, R.string.trakt_step_3)
            else -> listOf(R.string.mdblist_step_1, R.string.mdblist_step_2, R.string.mdblist_step_3)
        }.map { stringResource(it) }
        val (url, link) = when (service) {
            "TMDB" -> TMDB_API_KEY_URL to R.string.get_tmdb_api_key
            "Trakt" -> TRAKT_API_APPS_URL to R.string.get_trakt_client_id
            else -> MDBLIST_API_KEY_URL to R.string.get_mdblist_api_key
        }
        KitSteps(stringResource(if (service == "Trakt") R.string.how_to_get_client_id else R.string.how_to_get_key), steps, stringResource(link)) {
            runCatching { uriHandler.openUri(url) }
        }
    }
}

/** "2 min fa", "3 h fa", or a date for older syncs. */
@Composable
private fun relativeSyncTime(millis: Long): String {
    val elapsed = ((System.currentTimeMillis() - millis) / 1000).coerceAtLeast(0)
    return when {
        elapsed < 60 -> stringResource(R.string.just_now_short)
        elapsed < 3_600 -> stringResource(R.string.minutes_ago_short, elapsed / 60)
        elapsed < 86_400 -> stringResource(R.string.hours_ago_short, elapsed / 3_600)
        else -> java.text.DateFormat.getDateInstance(java.text.DateFormat.SHORT).format(java.util.Date(millis))
    }
}
