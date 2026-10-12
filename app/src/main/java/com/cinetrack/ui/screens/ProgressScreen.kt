@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.cinetrack.ui.screens

import com.cinetrack.ui.components.sharedBackdrop
import com.cinetrack.ui.components.directionalSlide
import com.cinetrack.ui.components.rememberDirectionalSlide
import com.cinetrack.ui.components.slidingTabIndicator
import com.cinetrack.ui.components.tabSpan
import com.cinetrack.ui.components.rememberTabIndicator
import com.cinetrack.ui.components.softTab
import com.cinetrack.ui.components.collapsingTitle
import com.cinetrack.ui.components.pinnedHeader
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.zIndex
import com.cinetrack.ui.components.LoadingPane
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SortByAlpha
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.foundation.gestures.draggable
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.runtime.produceState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.CloudSync

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.blur
import com.cinetrack.R
import com.cinetrack.domain.AppUiState
import com.cinetrack.domain.EpisodeCard
import com.cinetrack.domain.MediaCard
import com.cinetrack.domain.MediaType
import com.cinetrack.domain.PersonCard
import com.cinetrack.domain.PlaybackCard
import com.cinetrack.domain.SyncProgress
import com.cinetrack.domain.SyncStage
import com.cinetrack.domain.TimelineCard
import com.cinetrack.domain.releaseDateTime
import com.cinetrack.domain.hasExplicitReleaseTime
import com.cinetrack.domain.ViewingPeopleInsights
import com.cinetrack.ui.components.AdaptiveBackground
import com.cinetrack.ui.components.MediaRail
import com.cinetrack.ui.components.PageTitle
import com.cinetrack.ui.components.PrimaryAction
import com.cinetrack.ui.components.RevealOnMount
import com.cinetrack.ui.components.SectionHeader
import com.cinetrack.ui.components.SharedGlassSheet
import com.cinetrack.ui.components.SharedGlassDialog
import com.cinetrack.ui.components.glass
import com.cinetrack.ui.components.glassIcon
import com.cinetrack.ui.components.GlassMaterial
import com.cinetrack.ui.components.liquidGlass
import com.cinetrack.ui.components.liquidGlassIcon
import com.cinetrack.ui.components.liquidGlassPill
import com.cinetrack.ui.components.rememberUiAction
import com.cinetrack.ui.theme.Accent
import com.cinetrack.ui.theme.AccentLight
import com.cinetrack.ui.theme.DesignTokens
import com.cinetrack.ui.theme.Info
import com.cinetrack.ui.theme.Success
import com.cinetrack.ui.theme.TextMuted
import com.cinetrack.ui.theme.TextPrimary
import com.cinetrack.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

private enum class ProgressTab { IN_PROGRESS, CALENDAR, HISTORY, STATISTICS }
private enum class PlaybackOrder { RECENT, TITLE, TOP_RATED, REMAINING, RANDOM }

@Composable
fun ProgressScreen(
    state: AppUiState,
    syncProgress: StateFlow<SyncProgress>,
    syncRunning: StateFlow<Boolean>,
    onSearch: () -> Unit,
    onSync: () -> Unit,
    onSyncOperations: () -> Unit,
    onSyncSettings: () -> Unit,
    onMedia: (MediaCard) -> Unit,
    onWatched: (PlaybackCard) -> Unit,
    onEpisode: (EpisodeCard) -> Unit,
    onHideUpcoming: (EpisodeCard) -> Unit,
    viewingInsights: ViewingPeopleInsights,
    onLoadViewingInsights: () -> Unit,
    onCompactNav: (Boolean) -> Unit,
) {
    val isSyncRunning by syncRunning.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(ProgressTab.IN_PROGRESS) }
    val progressListState = rememberLazyListState()
    val calendarListState = rememberLazyListState()
    val historyListState = rememberLazyListState()
    val statisticsListState = rememberLazyListState()
    val activeListState = when (tab) {
        ProgressTab.IN_PROGRESS -> progressListState
        ProgressTab.CALENDAR -> calendarListState
        ProgressTab.HISTORY -> historyListState
        ProgressTab.STATISTICS -> statisticsListState
    }
    NavCollapseEffect(activeListState, onCompactNav)
    LaunchedEffect(tab) {
        if (tab == ProgressTab.STATISTICS) onLoadViewingInsights()
    }
    val artwork = state.playbackTv.firstOrNull()?.media?.posterUrl
        ?: state.playbackMovies.firstOrNull()?.media?.posterUrl
        ?: state.episodes.firstOrNull()?.stillUrl
    val releaseZone = remember(state.metadataTimezone) {
        if (state.metadataTimezone == "system") ZoneId.systemDefault()
        else runCatching { ZoneId.of(state.metadataTimezone) }.getOrDefault(ZoneId.systemDefault())
    }
    // Scanning every tracked episode and parsing its air date is heavy (thousands of
    // episodes), so it runs off the main thread. The last result is kept so returning to
    // Progress shows it at once; null means "not computed yet" (no empty-state flash).
    val upcomingInputs = UpcomingInputs(state.episodes, state.rails, state.playbackTv, state.excludeSpecials, state.hiddenUpcoming, releaseZone)
    val upcomingResult by produceState(
        initialValue = UpcomingCache.result.takeIf { UpcomingCache.inputs == upcomingInputs },
        upcomingInputs,
    ) {
        val computed = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { computeUpcoming(upcomingInputs, java.time.Instant.now()) }
        UpcomingCache.inputs = upcomingInputs
        UpcomingCache.result = computed
        value = computed
    }
    val upcomingEpisodes = upcomingResult.orEmpty()
    val comingSoon = upcomingEpisodes
    val libraryItems = remember(state.rails) {
        state.rails[com.cinetrack.domain.RailIds.LIBRARY].orEmpty()
    }
    val tvList = remember(libraryItems) {
        libraryItems.filter {
            it.type == com.cinetrack.domain.MediaType.TV && !it.watched &&
                it.status != com.cinetrack.domain.LibraryStatus.COMPLETED
        }
    }
    val movieList = remember(libraryItems) {
        libraryItems.filter {
            it.type == com.cinetrack.domain.MediaType.MOVIE && !it.watched &&
                it.status != com.cinetrack.domain.LibraryStatus.COMPLETED
        }
    }
    val trackedShowCount = remember(libraryItems) {
        libraryItems.count {
            it.type == com.cinetrack.domain.MediaType.TV &&
                it.status != com.cinetrack.domain.LibraryStatus.NONE
        }
    }
    val allMedia = remember(
        state.rails,
        state.playbackTv,
        state.playbackMovies,
        state.history,
        state.calendar,
    ) { state.allMedia }
    AdaptiveBackground(artworkUrl = artwork, liquidGlass = true) {
        LongPullRefreshContainer(
            refreshing = isSyncRunning,
            onRefresh = onSync,
            enabled = state.simklConnected,
        ) {
            Box(Modifier.fillMaxSize().statusBarsPadding()) {
                // The title row scrolls away with the list; tabs and sync card stay pinned and
                // gain a translucent backing once content passes beneath them.
                var titlePx by remember { mutableIntStateOf(0) }
                var headerPx by remember { mutableIntStateOf(0) }
                val headerScroll = remember(activeListState) {
                    { if (activeListState.firstVisibleItemIndex > 0) Int.MAX_VALUE else activeListState.firstVisibleItemScrollOffset }
                }
                Column(
                    Modifier.zIndex(1f).fillMaxWidth()
                        .onSizeChanged { headerPx = it.height }
                        .pinnedHeader({ titlePx }, headerScroll),
                ) {
                Row(
                    Modifier.fillMaxWidth().onSizeChanged { titlePx = it.height }
                        .collapsingTitle({ titlePx }, headerScroll)
                        .padding(start = com.cinetrack.ui.theme.Spacing.xl, end = com.cinetrack.ui.theme.Spacing.xl, top = com.cinetrack.ui.theme.Spacing.lg, bottom = com.cinetrack.ui.theme.Spacing.lg),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PageTitle(stringResource(R.string.progress), Modifier.weight(1f))
                    IconButton(onClick = rememberUiAction(onSearch), modifier = Modifier.size(48.dp).liquidGlassIcon()) {
                        Icon(Icons.Filled.Search, stringResource(R.string.accessibility_search), tint = TextPrimary, modifier = Modifier.size(21.dp))
                    }
                }
                ProgressTabs(tab) { selected ->
                    if (selected == tab) {
                        // Reselecting the visible list returns it to the top.
                        activeListState.requestScrollToItem(0)
                        onCompactNav(false)
                    } else {
                        // Each tab keeps its own remembered scroll position.
                        tab = selected
                    }
                }
                SyncCard(syncProgress, state.simklConnected, onSync, onSyncOperations, onSyncSettings)
                }
                val headerHeight = with(androidx.compose.ui.platform.LocalDensity.current) { headerPx.toDp() }
            AnimatedContent(
                targetState = tab,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    if (targetState.ordinal > initialState.ordinal) {
                        (slideInHorizontally(tween(com.cinetrack.ui.theme.Motion.Long)) { it / 3 } + fadeIn(tween(com.cinetrack.ui.theme.Motion.Medium))) togetherWith
                            (slideOutHorizontally(tween(com.cinetrack.ui.theme.Motion.Medium)) { -it / 4 } + fadeOut(tween(com.cinetrack.ui.theme.Motion.Short)))
                    } else {
                        (slideInHorizontally(tween(com.cinetrack.ui.theme.Motion.Long)) { -it / 3 } + fadeIn(tween(com.cinetrack.ui.theme.Motion.Medium))) togetherWith
                            (slideOutHorizontally(tween(com.cinetrack.ui.theme.Motion.Medium)) { it / 4 } + fadeOut(tween(com.cinetrack.ui.theme.Motion.Short)))
                    }
                },
                label = "progressTabPage",
            ) { activeTab ->
                LazyColumn(
                    state = when (activeTab) {
                        ProgressTab.IN_PROGRESS -> progressListState
                        ProgressTab.CALENDAR -> calendarListState
                        ProgressTab.HISTORY -> historyListState
                        ProgressTab.STATISTICS -> statisticsListState
                    },
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = headerHeight, bottom = 116.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                when (activeTab) {
                    ProgressTab.IN_PROGRESS -> {
                        item {
                            ExpandablePlaybackSection(
                                title = "${stringResource(R.string.tv_shows)} ${stringResource(R.string.in_progress).lowercase()}",
                                items = state.playbackTv,
                                showEpisodeControl = true,
                            showAllLabel = stringResource(R.string.show_all_episodes),
                            onMedia = onMedia,
                            onEpisode = onEpisode,
                            onWatched = onWatched,
                            )
                        }
                        item {
                            ExpandablePlaybackSection(
                                title = "${stringResource(R.string.movies)} ${stringResource(R.string.in_progress).lowercase()}",
                                items = state.playbackMovies,
                                showEpisodeControl = true,
                            showAllLabel = stringResource(R.string.show_all_movies),
                            onMedia = onMedia,
                            onEpisode = onEpisode,
                            onWatched = onWatched,
                            )
                        }
                    if (comingSoon.isNotEmpty()) {
                        item { UpcomingEpisodesRail(stringResource(R.string.coming_soon), comingSoon, allMedia, onEpisode, onHideUpcoming) }
                    }
                    if (upcomingResult?.isEmpty() == true) {
                        item { UpcomingDiagnostics(trackedShowCount) }
                    }
                        if (tvList.isNotEmpty()) {
                            item { SectionHeader(stringResource(R.string.your_tv_list), Modifier.padding(start = com.cinetrack.ui.theme.Spacing.xl, end = com.cinetrack.ui.theme.Spacing.xl, top = com.cinetrack.ui.theme.Spacing.sm, bottom = com.cinetrack.ui.theme.Spacing.md)) }
                            item { MediaRail(tvList, onMedia, showYear = false) }
                        }
                        if (movieList.isNotEmpty()) {
                            item { SectionHeader(stringResource(R.string.your_movie_list), Modifier.padding(start = com.cinetrack.ui.theme.Spacing.xl, end = com.cinetrack.ui.theme.Spacing.xl, top = com.cinetrack.ui.theme.Spacing.xxl, bottom = com.cinetrack.ui.theme.Spacing.md)) }
                            item { MediaRail(movieList, onMedia, showYear = false) }
                        }
                    }
                    ProgressTab.HISTORY -> {
                        if (state.history.isEmpty()) item { ProgressEmpty(stringResource(R.string.no_history)) }
                        else itemsIndexed(
                            state.history,
                            key = { index, item -> "history:${item.media.stableKey}:${item.timestamp}:${item.episodeLabel.orEmpty()}:$index" },
                        ) { index, item ->
                            val previous = state.history.getOrNull(index - 1)
                            HistoryTimelineItem(item, previous, onMedia, onEpisode)
                        }
                    }
                    ProgressTab.CALENDAR -> {
                        if (state.calendar.isEmpty()) item { ProgressEmpty(stringResource(R.string.no_calendar_items)) }
                        else itemsIndexed(
                            state.calendar,
                            key = { index, item -> "calendar:${item.media.stableKey}:${item.timestamp}:${item.episodeLabel.orEmpty()}:$index" },
                        ) { _, item -> TimelineRow(item, onMedia, onEpisode, history = false) }
                    }
                    ProgressTab.STATISTICS -> item { StatisticsSection(state, viewingInsights) }
                }
            }
            }
            }
        }
    }
}

@Composable
private fun SyncCard(
    syncProgress: StateFlow<SyncProgress>,
    connected: Boolean,
    onSync: () -> Unit,
    onOperations: () -> Unit,
    onSettings: () -> Unit,
) {
    val sync by syncProgress.collectAsStateWithLifecycle()
    val statusInteraction = remember { MutableInteractionSource() }
    val statusPressed by statusInteraction.collectIsPressedAsState()
    val needsAttention = sync.stage == SyncStage.ERROR || sync.report.failedOperations > 0 || sync.report.conflicts > 0
    Column(Modifier.padding(horizontal = com.cinetrack.ui.theme.Spacing.xl)
        .padding(bottom = com.cinetrack.ui.theme.Spacing.lg).fillMaxWidth()
        .glass(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Medium))
        .animateContentSize().padding(horizontal = com.cinetrack.ui.theme.Spacing.md, vertical = com.cinetrack.ui.theme.Spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CloudSync, null, tint = if (needsAttention) MaterialTheme.colorScheme.error else AccentLight, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(com.cinetrack.ui.theme.Spacing.md))
            Column(Modifier.weight(1f).clip(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Small))
                .background(if (statusPressed) com.cinetrack.ui.theme.GlassStrong else Color.Transparent)
                .clickable(interactionSource = statusInteraction, indication = null, onClick = if (connected) onOperations else onSettings)
                .padding(horizontal = com.cinetrack.ui.theme.Spacing.sm, vertical = com.cinetrack.ui.theme.Spacing.sm)) {
                Text(
                    when {
                        !connected -> stringResource(R.string.connect_simkl)
                        sync.running -> syncStageLabel(sync.stage)
                        needsAttention -> stringResource(R.string.sync_needs_attention)
                        else -> relativeSyncLabel(sync.lastSuccessfulSync)
                    },
                    color = TextPrimary, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold,
                )
                if (needsAttention || sync.report.pendingLocalChanges > 0) {
                    Text(stringResource(R.string.sync_operations), color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }
            IconButton(onClick = onSync, enabled = connected && !sync.running, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.Refresh, stringResource(R.string.accessibility_sync), tint = if (connected) AccentLight else TextMuted, modifier = Modifier.size(21.dp))
            }
        }
        AnimatedVisibility(sync.running || needsAttention) {
            Column(Modifier.padding(bottom = com.cinetrack.ui.theme.Spacing.md)) {
                if (sync.running) {
                    LinearProgressIndicator(
                        progress = { sync.progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
                        color = AccentLight, trackColor = com.cinetrack.ui.theme.Glass,
                    )
                    Spacer(Modifier.height(com.cinetrack.ui.theme.Spacing.sm))
                }
                Text(
                    if (sync.running) stringResource(R.string.sync_progress_detail)
                    else stringResource(R.string.sync_operations_summary),
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun SimklMark() {
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Small))
            .background(com.cinetrack.ui.theme.SurfacePalette.NeutralText.copy(alpha = .92f))
            .border(.55.dp, com.cinetrack.ui.theme.GlassStrong, RoundedCornerShape(com.cinetrack.ui.theme.Radius.Small)),
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(R.drawable.ic_service_simkl), contentDescription = "Simkl", modifier = Modifier.size(25.dp))
    }
}

@Composable
private fun syncStageLabel(stage: SyncStage): String = when (stage) {
    SyncStage.AUTH -> stringResource(R.string.sync_stage_auth)
    SyncStage.ACTIVITY -> stringResource(R.string.sync_stage_activity)
    SyncStage.PLAYBACK -> stringResource(R.string.sync_stage_playback)
    SyncStage.HISTORY -> stringResource(R.string.sync_stage_history)
    SyncStage.CALENDAR -> stringResource(R.string.sync_stage_calendar)
    SyncStage.COMMIT -> stringResource(R.string.sync_stage_commit)
    SyncStage.PROCESSING -> stringResource(R.string.sync_stage_processing)
    else -> stringResource(R.string.syncing)
}

@Composable
private fun ProgressTabs(selected: ProgressTab, onSelected: (ProgressTab) -> Unit) {
    // Underlined sub-tabs: the accent line slides in under the active section.
    val indicator = rememberTabIndicator()
    Column(Modifier.fillMaxWidth().padding(bottom = com.cinetrack.ui.theme.Spacing.sm)) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp)
                .slidingTabIndicator(indicator, selected.ordinal),
            horizontalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            ProgressTab.entries.forEach { tab ->
                val active = tab == selected
                val selectAction = rememberUiAction { onSelected(tab) }
                val contentColor by animateColorAsState(if (active) TextPrimary else TextPrimary.copy(alpha = .5f), tween(com.cinetrack.ui.theme.Motion.Medium), label = "progressTabText")
                Text(
                    when (tab) {
                        ProgressTab.IN_PROGRESS -> stringResource(R.string.in_progress)
                        ProgressTab.CALENDAR -> stringResource(R.string.calendar)
                        ProgressTab.HISTORY -> stringResource(R.string.history)
                        ProgressTab.STATISTICS -> stringResource(R.string.statistics)
                    },
                    color = contentColor,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier
                        .tabSpan(indicator, tab.ordinal)
                        .softTab(active, selectAction)
                        .heightIn(min = 44.dp)
                        .padding(vertical = 12.dp),
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(.5.dp).background(Color.White.copy(alpha = .08f)))
    }
}

@Composable
private fun ExpandablePlaybackSection(
    title: String,
    items: List<PlaybackCard>,
    showEpisodeControl: Boolean = false,
    showAllLabel: String,
    onMedia: (MediaCard) -> Unit,
    onEpisode: (EpisodeCard) -> Unit,
    onWatched: (PlaybackCard) -> Unit,
) {
    var expanded by rememberSaveable(title) { mutableStateOf(false) }
    var order by rememberSaveable(title) { mutableStateOf(PlaybackOrder.RECENT) }
    var ascending by rememberSaveable(title) { mutableStateOf(true) }
    var showOrderSheet by rememberSaveable(title) { mutableStateOf(false) }
    val orderedItems = remember(items, order, ascending) {
        val sorted = when (order) {
            PlaybackOrder.RECENT -> items
            PlaybackOrder.TITLE -> items.sortedBy { it.media.title.lowercase() }
            PlaybackOrder.TOP_RATED -> items.sortedByDescending { it.media.score ?: -1.0 }
            PlaybackOrder.REMAINING -> items.sortedBy { it.remainingMinutes ?: Int.MAX_VALUE }
            PlaybackOrder.RANDOM -> items.shuffled()
        }
        if (ascending) sorted else sorted.reversed()
    }
    // One clock drives the peeking card, the rows below it and the button, so the
    // button only ever travels in one direction while the list opens or closes.
    val expandSpec = tween<Float>(com.cinetrack.ui.theme.Motion.Extended, easing = FastOutSlowInEasing)
    val reveal by animateFloatAsState(if (expanded) 1f else 0f, expandSpec, label = "showAllReveal")
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = com.cinetrack.ui.theme.Spacing.xl), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f).padding(bottom = com.cinetrack.ui.theme.Spacing.md))
            if (showEpisodeControl) {
                Row(
                    Modifier.padding(bottom = com.cinetrack.ui.theme.Spacing.sm).heightIn(min = 48.dp)
                        .liquidGlassPill()
                        .clickable(onClick = rememberUiAction { showOrderSheet = true })
                        .padding(horizontal = com.cinetrack.ui.theme.Spacing.sm, vertical = com.cinetrack.ui.theme.Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Sort, null, tint = AccentLight, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(
                        when (order) {
                            PlaybackOrder.RECENT -> stringResource(R.string.order_recent)
                            PlaybackOrder.TITLE -> stringResource(R.string.order_title)
                            PlaybackOrder.TOP_RATED -> stringResource(R.string.order_top_rated)
                            PlaybackOrder.REMAINING -> stringResource(R.string.order_remaining)
                            PlaybackOrder.RANDOM -> stringResource(R.string.order_random)
                        },
                        color = TextSecondary,
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            if (expanded) {
                IconButton(onClick = { expanded = false }, modifier = Modifier.padding(bottom = com.cinetrack.ui.theme.Spacing.sm).size(48.dp)) {
                    Icon(Icons.Filled.ExpandLess, stringResource(R.string.collapse), tint = TextSecondary, modifier = Modifier.size(18.dp))
                }
            }
        }
        if (items.isEmpty()) {
            Text(
                stringResource(R.string.no_playback),
                color = TextMuted,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = com.cinetrack.ui.theme.Spacing.xl, end = com.cinetrack.ui.theme.Spacing.xl, bottom = com.cinetrack.ui.theme.Spacing.md).fillMaxWidth().glass(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Medium)).padding(com.cinetrack.ui.theme.Spacing.md),
            )
        }
        orderedItems.take(3).forEachIndexed { index, item ->
            AnimatedPlaybackSlot(index, item, onMedia, onEpisode, onWatched)
        }
        if (orderedItems.size > 3) {
            // The fourth card peeks under a fade while collapsed and grows to full height.
            val peek = with(androidx.compose.ui.platform.LocalDensity.current) { 132.dp.roundToPx() }
            Box(
                Modifier.fillMaxWidth()
                    .clipToBounds()
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val height = androidx.compose.ui.util.lerp(minOf(peek, placeable.height).toFloat(), placeable.height.toFloat(), reveal).roundToInt()
                        layout(placeable.width, height) { placeable.place(0, 0) }
                    }
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        val fade = 1f - reveal
                        if (fade > 0f) drawRect(
                            brush = Brush.verticalGradient(
                                0f to Color.White,
                                .44f to Color.White.copy(alpha = 1f - .62f * fade),
                                .78f to Color.White.copy(alpha = 1f - .86f * fade),
                                1f to Color.White.copy(alpha = 1f - fade),
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                    },
            ) {
                PlaybackRow(orderedItems[3], onMedia, onEpisode, onWatched)
            }
        }
        if (orderedItems.size > 4) {
            Column(
                Modifier.fillMaxWidth().clipToBounds()
                    .layout { measurable, constraints ->
                        if (reveal <= 0f) return@layout layout(constraints.maxWidth, 0) {}
                        val placeable = measurable.measure(constraints)
                        layout(placeable.width, (placeable.height * reveal).roundToInt()) { placeable.place(0, 0) }
                    }
                    .graphicsLayer { alpha = reveal },
            ) {
                if (reveal > 0f) orderedItems.drop(4).forEach { PlaybackRow(it, onMedia, onEpisode, onWatched) }
            }
        }
        if (orderedItems.size > 3) {
            Row(
                Modifier.padding(start = com.cinetrack.ui.theme.Spacing.xl, end = com.cinetrack.ui.theme.Spacing.xl, bottom = com.cinetrack.ui.theme.Spacing.xl).offset { androidx.compose.ui.unit.IntOffset(0, ((-42).dp.toPx() * (1f - reveal)).roundToInt()) }.fillMaxWidth().height(48.dp)
                    .liquidGlassPill()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(if (expanded) stringResource(R.string.collapse) else showAllLabel, color = TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(7.dp))
                Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
            }
        } else {
            Spacer(Modifier.height(8.dp))
        }
    }
    if (showOrderSheet) {
        PlaybackOrderSheet(
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
}

@Composable
private fun AnimatedPlaybackSlot(
    slot: Int,
    item: PlaybackCard,
    onMedia: (MediaCard) -> Unit,
    onEpisode: (EpisodeCard) -> Unit,
    onWatched: (PlaybackCard) -> Unit,
) {
    AnimatedContent(
        targetState = item,
        contentKey = { card -> "$slot:${card.media.stableKey}:${card.season}:${card.episodeNumber}" },
        transitionSpec = {
            (slideInHorizontally(tween(com.cinetrack.ui.theme.Motion.Long, easing = FastOutSlowInEasing)) { it / 7 } + fadeIn(tween(com.cinetrack.ui.theme.Motion.Medium))) togetherWith
                (slideOutHorizontally(tween(com.cinetrack.ui.theme.Motion.Medium, easing = FastOutSlowInEasing)) { -it / 8 } + fadeOut(tween(com.cinetrack.ui.theme.Motion.Short)))
        },
        label = "nextPlaybackCard",
    ) { card ->
        PlaybackRow(card, onMedia, onEpisode, onWatched)
    }
}

@Composable
private fun PlaybackOrderSheet(
    initialOrder: PlaybackOrder,
    initialAscending: Boolean,
    onDismiss: () -> Unit,
    onApply: (PlaybackOrder, Boolean) -> Unit,
) {
    var selected by remember(initialOrder) { mutableStateOf(initialOrder) }
    var ascending by remember(initialAscending) { mutableStateOf(initialAscending) }
    SharedGlassSheet(onDismiss) {
        Column(Modifier.padding(horizontal = 12.dp)) {
            com.cinetrack.ui.components.SheetTitle(stringResource(R.string.sort_by))
            com.cinetrack.ui.components.GlassMenuGroup {
                PlaybackOrder.entries.forEachIndexed { index, option ->
                    com.cinetrack.ui.components.GlassMenuRow(
                        label = when (option) {
                            PlaybackOrder.RECENT -> stringResource(R.string.order_recent)
                            PlaybackOrder.TITLE -> stringResource(R.string.order_title)
                            PlaybackOrder.TOP_RATED -> stringResource(R.string.order_top_rated)
                            PlaybackOrder.REMAINING -> stringResource(R.string.order_remaining)
                            PlaybackOrder.RANDOM -> stringResource(R.string.order_random)
                        },
                        icon = when (option) {
                            PlaybackOrder.RECENT -> Icons.Filled.History
                            PlaybackOrder.TITLE -> Icons.Filled.SortByAlpha
                            PlaybackOrder.TOP_RATED -> Icons.Filled.Star
                            PlaybackOrder.REMAINING -> Icons.Filled.Schedule
                            PlaybackOrder.RANDOM -> Icons.Filled.Shuffle
                        },
                        tint = AccentLight,
                        selected = option == selected,
                        divider = index < PlaybackOrder.entries.lastIndex,
                    ) { selected = option }
                }
            }
            Spacer(Modifier.height(10.dp))
            com.cinetrack.ui.components.GlassMenuGroup {
                com.cinetrack.ui.components.GlassMenuRow(
                    label = if (ascending) stringResource(R.string.ascending) else stringResource(R.string.descending),
                    icon = if (ascending) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                    tint = AccentLight,
                    divider = false,
                ) { ascending = !ascending }
            }
            Spacer(Modifier.height(18.dp))
            PrimaryAction(stringResource(R.string.apply), Icons.Filled.Sort, Modifier.fillMaxWidth()) { onApply(selected, ascending) }
        }
    }
}

@Composable
private fun PlaybackRow(
    item: PlaybackCard,
    onMedia: (MediaCard) -> Unit,
    onEpisode: (EpisodeCard) -> Unit,
    onWatched: (PlaybackCard) -> Unit,
) {
    val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale.coerceAtLeast(1f)
    var confirming by remember(item.media.stableKey) { mutableStateOf(false) }
    LaunchedEffect(confirming) {
        if (confirming) {
            delay(DesignTokens.WatchedConfirmationMs)
            onWatched(item)
            confirming = false
        }
    }
    val checkScale by animateFloatAsState(
        if (confirming) 1.25f else 1f,
        spring(dampingRatio = .52f, stiffness = Spring.StiffnessMedium),
        label = "watchedScale",
    )
    val timelineProgress = when {
        item.progress > 0f -> item.progress
        item.remainingMinutes != null && item.durationMinutes != null && item.durationMinutes > 0 ->
            (1f - item.remainingMinutes.toFloat() / item.durationMinutes.toFloat()).coerceIn(0f, 1f)
        else -> 0f
    }
    val remainingMinutes = item.remainingMinutes ?: item.durationMinutes
        ?.takeIf { it > 0 && item.progress > 0f }
        ?.let { duration -> (duration * (1f - item.progress.coerceIn(0f, 1f))).toInt().coerceAtLeast(0) }
    val openItem = {
        val season = item.season
        val number = item.episodeNumber
        if (item.media.type == com.cinetrack.domain.MediaType.TV && season != null && number != null) {
            onEpisode(
                EpisodeCard(
                    id = item.episodeId ?: -1,
                    showId = item.media.id,
                    season = season,
                    number = number,
                    title = item.episodeTitle.orEmpty(),
                    overview = "",
                    airDate = null,
                    runtimeMinutes = item.durationMinutes,
                ),
            )
        } else {
            onMedia(item.media)
        }
    }
    val cardInteraction = remember(item.media.stableKey, item.season, item.episodeNumber) { MutableInteractionSource() }
    val cardPressed by cardInteraction.collectIsPressedAsState()
    // The card grows into the episode's still (or a film's header) when opened.
    val cardSlot = com.cinetrack.ui.components.rememberPosterSlot()
    val sharedKey = if (item.media.type == com.cinetrack.domain.MediaType.TV && item.season != null && item.episodeNumber != null) {
        "ep:${item.media.id}:${item.season}:${item.episodeNumber}"
    } else item.media.stableKey
    val openItemAction = rememberUiAction {
        com.cinetrack.ui.components.SharedPosterSource.active = cardSlot
        openItem()
    }
    RevealOnMount("${item.media.stableKey}:${item.season}:${item.episodeNumber}") {
    // Artwork card: the show's art fills the card behind a dark fade, the poster sits on
    // it, the remaining time sits beside the title and progress runs along the bottom edge.
    val cardShape = RoundedCornerShape(20.dp)
    val backdrop = item.media.backdropUrl ?: item.media.posterUrl
    val posterArt = item.media.posterUrl ?: item.media.backdropUrl
    val animatedProgress by animateFloatAsState(timelineProgress.coerceIn(0f, 1f), tween(com.cinetrack.ui.theme.Motion.Extended), label = "progressValue")
    // Swipe right to tick: the card slides over a green check and snaps back once released.
    val swipe = remember(item.media.stableKey, item.season, item.episodeNumber) { androidx.compose.animation.core.Animatable(0f) }
    val swipeScope = rememberCoroutineScope()
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    var cardWidth by remember { mutableStateOf(1f) }
    val swipeThreshold = cardWidth * .34f
    var swipeArmed by remember { mutableStateOf(false) }
    val checkPainter = androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Filled.Check)
    val swipeLabel = androidx.compose.ui.res.stringResource(com.cinetrack.R.string.mark_watched)
    Box(
        Modifier.padding(start = com.cinetrack.ui.theme.Spacing.xl, end = com.cinetrack.ui.theme.Spacing.xl, bottom = com.cinetrack.ui.theme.Spacing.md).fillMaxWidth().height((150 * fontScale).dp)
            .onSizeChanged { cardWidth = it.width.toFloat().coerceAtLeast(1f) }
            .drawBehind {
                // Revealed behind the card: green, with the check growing as the threshold nears.
                val reveal = (swipe.value / swipeThreshold).coerceIn(0f, 1f)
                if (swipe.value > 0f) {
                    drawRoundRect(com.cinetrack.ui.theme.Success.copy(alpha = .25f + .6f * reveal), cornerRadius = androidx.compose.ui.geometry.CornerRadius(20.dp.toPx()))
                    val icon = (22.dp.toPx() * (.7f + .45f * reveal))
                    translate(left = 26.dp.toPx(), top = (size.height - icon) / 2f) {
                        with(checkPainter) { draw(androidx.compose.ui.geometry.Size(icon, icon), alpha = .5f + .5f * reveal, colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color.White)) }
                    }
                }
            }
            .draggable(
                orientation = androidx.compose.foundation.gestures.Orientation.Horizontal,
                state = androidx.compose.foundation.gestures.rememberDraggableState { delta ->
                    swipeScope.launch {
                        // Past the threshold the card resists, so it never runs off the screen.
                        val next = swipe.value + if (swipe.value > swipeThreshold) delta * .35f else delta
                        swipe.snapTo(next.coerceIn(0f, cardWidth * .6f))
                        val armed = swipe.value >= swipeThreshold
                        if (armed && !swipeArmed) haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        swipeArmed = armed
                    }
                },
                onDragStopped = {
                    if (swipe.value >= swipeThreshold) confirming = true
                    swipeArmed = false
                    swipe.animateTo(0f, spring(dampingRatio = .72f, stiffness = Spring.StiffnessMediumLow))
                },
            )
            .semantics {
                customActions = listOf(androidx.compose.ui.semantics.CustomAccessibilityAction(swipeLabel) { confirming = true; true })
            }
            .offset { androidx.compose.ui.unit.IntOffset(swipe.value.roundToInt(), 0) }
            .shadow(14.dp, cardShape, clip = false, ambientColor = Color.Black, spotColor = Color.Black)
            .sharedBackdrop(sharedKey, cardSlot)
            .clip(cardShape)
            .background(Brush.linearGradient(listOf(com.cinetrack.ui.theme.SurfacePalette.PosterBrown, com.cinetrack.ui.theme.Background0)))
            .border(if (cardPressed) 1.5.dp else .5.dp, if (cardPressed) Accent.copy(alpha = .9f) else Color.White.copy(alpha = .10f), cardShape)
            .clickable(interactionSource = cardInteraction, indication = null, onClick = openItemAction),
    ) {
        if (!backdrop.isNullOrBlank()) {
            com.cinetrack.ui.components.SharedPosterImage(
                backdrop, null, cardSlot, Modifier.fillMaxSize()
                    .then(if (item.media.backdropUrl == null) Modifier.blur(12.dp) else Modifier),
                ContentScale.Crop,
            )
        }
        Box(Modifier.matchParentSize().background(Brush.horizontalGradient(
            0f to Color(0x40080A0C), .38f to Color(0xD1080A0C), 1f to Color(0xF0080A0C),
        )))
        Box(
            Modifier.padding(14.dp).width(82.dp).fillMaxHeight()
                .shadow(10.dp, RoundedCornerShape(10.dp), clip = false)
                .clip(RoundedCornerShape(10.dp))
                .background(Brush.linearGradient(listOf(com.cinetrack.ui.theme.SurfacePalette.PosterBrown, com.cinetrack.ui.theme.SurfacePalette.OceanMid)))
                .border(.5.dp, Color.White.copy(alpha = .14f), RoundedCornerShape(10.dp)),
        ) {
            if (!posterArt.isNullOrBlank()) AsyncImage(posterArt, item.media.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Column(Modifier.fillMaxSize().padding(start = 112.dp, end = 12.dp, top = 16.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(item.media.title, color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                remainingMinutes?.let {
                    Spacer(Modifier.width(8.dp))
                    Text(formatDurationMinutes(it), color = AccentLight, style = androidx.compose.material3.MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
            val detail = if (item.media.type == com.cinetrack.domain.MediaType.TV) {
                val numbered = item.episodeLabel ?: listOfNotNull(item.season?.let { "S$it" }, item.episodeNumber?.let { "E$it" }).joinToString(" ")
                listOfNotNull(numbered.takeIf(String::isNotBlank), item.episodeTitle?.takeIf(String::isNotBlank)).joinToString(" · ")
            } else stringResource(R.string.movies)
            Text(detail.ifBlank { stringResource(R.string.in_progress) }, color = TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    (if (timelineProgress > 0f) stringResource(R.string.resume)
                    else if (item.media.type == com.cinetrack.domain.MediaType.TV) stringResource(R.string.up_next)
                    else stringResource(R.string.in_progress)).uppercase(com.cinetrack.ui.currentLocale()),
                    color = TextMuted,
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.4.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = rememberUiAction { confirming = true },
                    enabled = !confirming,
                    modifier = Modifier.size(48.dp),
                ) {
                    // Flat glass: the button sits on artwork, which the page backdrop cannot show.
                    Box(
                        Modifier.size(40.dp).clip(CircleShape)
                            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = .20f), Color.White.copy(alpha = .08f))))
                            .border(.6.dp, Color.White.copy(alpha = .30f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Check, stringResource(R.string.mark_watched), tint = TextPrimary, modifier = Modifier.size(17.dp).scale(checkScale))
                    }
                }
            }
        }
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = .10f))) {
            Box(Modifier.fillMaxWidth(animatedProgress).fillMaxHeight().background(Accent))
        }
        AnimatedVisibility(
            visible = confirming,
            modifier = Modifier.matchParentSize(),
            enter = slideInHorizontally(tween(com.cinetrack.ui.theme.Motion.Long, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(com.cinetrack.ui.theme.Motion.Short)),
            exit = slideOutHorizontally(tween(com.cinetrack.ui.theme.Motion.Medium, easing = FastOutSlowInEasing)) { -it / 5 } + fadeOut(tween(com.cinetrack.ui.theme.Motion.Short)),
        ) {
            Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)).background(Success.copy(alpha = .96f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(34.dp))
                    Text(
                        stringResource(if (item.media.type == MediaType.MOVIE) R.string.watched_movie else R.string.watched_tv),
                        color = Color.White,
                        style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
        }
    }
    }
}

@Composable
private fun AnimatedProgressBar(progress: Float, modifier: Modifier = Modifier) {
    val safeProgress = progress.coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(safeProgress, tween(com.cinetrack.ui.theme.Motion.Extended), label = "progressValue")
    Box(
        modifier.fillMaxWidth().height(10.dp)
            .liquidGlass(surface = com.cinetrack.ui.theme.Glass, refraction = 3.dp, under = GlassMaterial.Content, shadow = false) {
                clip(CircleShape).background(com.cinetrack.ui.theme.Glass)
            },
    ) {
        Box(Modifier.fillMaxWidth(animatedProgress).fillMaxHeight().clip(CircleShape).background(Brush.horizontalGradient(listOf(AccentLight, Accent))))
    }
}

@Composable
private fun ProgressEmpty(message: String) {
    Text(
        message,
        color = TextSecondary,
        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(horizontal = com.cinetrack.ui.theme.Spacing.xl).fillMaxWidth().glass(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Medium)).padding(com.cinetrack.ui.theme.Spacing.lg),
    )
}

@Composable
private fun UpcomingEpisodesRail(
    title: String,
    episodes: List<EpisodeCard>,
    shows: List<MediaCard>,
    onEpisode: (EpisodeCard) -> Unit,
    onHide: (EpisodeCard) -> Unit,
) {
    var pendingHide by remember { mutableStateOf<EpisodeCard?>(null) }
    Column(Modifier.padding(top = com.cinetrack.ui.theme.Spacing.sm, bottom = com.cinetrack.ui.theme.Spacing.lg)) {
        SectionHeader(title, Modifier.padding(horizontal = com.cinetrack.ui.theme.Spacing.xl))
        Spacer(Modifier.height(13.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(episodes.distinctBy(EpisodeCard::scheduleKey), key = EpisodeCard::scheduleKey) { episode ->
                val showTitle = shows.firstOrNull { it.id == episode.showId && it.type == com.cinetrack.domain.MediaType.TV }?.title.orEmpty()
                val interaction = remember(episode.showId, episode.season, episode.number) { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                val openEpisodeAction = rememberUiAction { onEpisode(episode) }
                Column(
                    Modifier.width(236.dp).combinedClickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = openEpisodeAction,
                        onLongClick = { pendingHide = episode },
                    ),
                ) {
                    Box(Modifier.fillMaxWidth().height(132.dp).clip(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Medium)).background(Brush.linearGradient(listOf(Accent.copy(alpha = .65f), Info.copy(alpha = .28f))))
                        .border(if (pressed) 1.7.dp else 0.dp, Accent.copy(alpha = if (pressed) .9f else 0f), RoundedCornerShape(com.cinetrack.ui.theme.Radius.Medium))) {
                        if (!episode.stillUrl.isNullOrBlank()) AsyncImage(episode.stillUrl, episode.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .74f)))))
                        Text(shortAirDate(episode.airDate), color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, lineHeight = 10.sp, fontWeight = FontWeight.ExtraBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(com.cinetrack.ui.theme.Spacing.sm).glass(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Compact)).padding(horizontal = com.cinetrack.ui.theme.Spacing.sm, vertical = com.cinetrack.ui.theme.Spacing.xs))
                        IconButton(
                            onClick = { pendingHide = episode },
                            modifier = Modifier.align(Alignment.TopEnd).padding(2.dp).size(40.dp),
                        ) {
                            Box(
                                Modifier.size(26.dp).glass(RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Filled.VisibilityOff,
                                    stringResource(R.string.hide_upcoming_episode),
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                        Text(showTitle, color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.BottomStart).padding(com.cinetrack.ui.theme.Spacing.md))
                    }
                    Text("${episode.label.replace(" · ", " ")} · ${episode.title}", color = TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = com.cinetrack.ui.theme.Spacing.sm))
                }
            }
        }
    }
    pendingHide?.let { episode ->
        SharedGlassDialog(onDismiss = { pendingHide = null }) {
            Column(Modifier.padding(com.cinetrack.ui.theme.Spacing.lg)) {
                Text(stringResource(R.string.hide_upcoming_title), color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(7.dp))
                Text(stringResource(R.string.hide_upcoming_message, episode.label.replace(" · ", " "), episode.title), color = TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(16.dp))
                PrimaryAction(
                    stringResource(R.string.hide_upcoming_episode),
                    Icons.Filled.VisibilityOff,
                    Modifier.fillMaxWidth(),
                ) {
                    onHide(episode)
                    pendingHide = null
                }
                androidx.compose.material3.TextButton(onClick = { pendingHide = null }, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.cancel), color = TextPrimary)
                }
            }
        }
    }
}

@Composable
private fun UpcomingDiagnostics(trackedShows: Int) {
    Column(
        Modifier.padding(horizontal = com.cinetrack.ui.theme.Spacing.xl, vertical = com.cinetrack.ui.theme.Spacing.sm).fillMaxWidth()
            .glass(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Medium)).padding(com.cinetrack.ui.theme.Spacing.md),
    ) {
        Text(stringResource(R.string.why_not_shown), color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(5.dp))
        Text(
            if (trackedShows == 0) stringResource(R.string.no_tracked_shows_for_upcoming)
            else stringResource(R.string.no_announced_episodes, trackedShows),
            color = TextMuted,
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            lineHeight = 15.sp,
        )
    }
}

@Composable
private fun StatisticsSection(state: AppUiState, people: ViewingPeopleInsights) {
    val now = remember(state.metadataTimezone) {
        val zone = if (state.metadataTimezone == "system") ZoneId.systemDefault()
        else runCatching { ZoneId.of(state.metadataTimezone) }.getOrDefault(ZoneId.systemDefault())
        LocalDate.now(zone)
    }
    // Thousands of history timestamps are parsed off the main thread, once per change;
    // re-entering the tab reuses the last summary instead of stalling the first frame.
    val inputs = StatsInputs(state.history, state.rails[com.cinetrack.domain.RailIds.LIBRARY].orEmpty(), now)
    val stats by produceState(initialValue = StatsCache.result.takeIf { StatsCache.inputs == inputs }, inputs) {
        if (value == null || StatsCache.inputs != inputs) {
            val computed = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { computeStats(inputs) }
            StatsCache.inputs = inputs
            StatsCache.result = computed
            value = computed
        }
    }
    val summary = stats ?: run {
        LoadingPane(Modifier.height(320.dp))
        return
    }
    val watchedDays = summary.watchedDays
    val heatmapWeeks = summary.heatmapWeeks
    val maxDailyActivity = summary.maxDailyActivity
    val longestStreak = summary.longestStreak
    val topGenres = summary.topGenres
    val completed = summary.completed
    val abandoned = summary.abandoned
    val finishedTotal = completed + abandoned
    val completionRate = if (finishedTotal == 0) 0 else completed * 100 / finishedTotal
    Column(Modifier.padding(horizontal = com.cinetrack.ui.theme.Spacing.xl, vertical = com.cinetrack.ui.theme.Spacing.sm), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StatsDashboard(summary, now)
        YearReviewCard(summary.year)
        Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            StatisticTile(stringResource(R.string.completion_rate), "$completionRate%", Modifier.weight(1f))
            StatisticTile(stringResource(R.string.completed), completed.toString(), Modifier.weight(1f))
            StatisticTile(stringResource(R.string.dropped), abandoned.toString(), Modifier.weight(1f))
        }
        Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Medium)).padding(com.cinetrack.ui.theme.Spacing.md)) {
            Text(stringResource(R.string.most_watched_genres), color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(7.dp))
            Text(topGenres.ifEmpty { listOf("—") }.joinToString(" · "), color = AccentLight, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Medium)).padding(com.cinetrack.ui.theme.Spacing.md)) {
            Text(stringResource(R.string.most_watched_people), color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(9.dp))
            if (people.loading) {
                Text(stringResource(R.string.loading), color = TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
            } else {
                PeopleStatisticsRow(stringResource(R.string.actors), people.actors)
                Spacer(Modifier.height(10.dp))
                PeopleStatisticsRow(stringResource(R.string.directors), people.directors)
            }
        }
        Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Medium)).padding(com.cinetrack.ui.theme.Spacing.md)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.activity_heatmap), color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.heatmap_summary, watchedDays.size, longestStreak), color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, maxLines = 1)
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth().padding(start = com.cinetrack.ui.theme.Spacing.xxl), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                heatmapWeeks.forEachIndexed { index, week ->
                    val monthChanged = index == 0 || week.first().month != heatmapWeeks[index - 1].first().month
                    Text(
                        if (monthChanged) week.first().format(com.cinetrack.ui.UiDateFormatters.current.month) else "",
                        color = TextMuted,
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(5.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Column(Modifier.width(22.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    heatmapWeeks.first().forEach { day ->
                        Text(
                            day.format(com.cinetrack.ui.UiDateFormatters.current.narrowWeekday),
                            color = TextMuted,
                            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                            lineHeight = 11.sp,
                            maxLines = 1,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().height(16.dp),
                        )
                    }
                }
                heatmapWeeks.forEach { week ->
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        week.forEach { day ->
                            val count = watchedDays[day] ?: 0
                            val level = if (count == 0) 0 else ((count * 4 + maxDailyActivity - 1) / maxDailyActivity).coerceIn(1, 4)
                            Box(
                                Modifier.fillMaxWidth().height(16.dp).clip(RoundedCornerShape(3.dp))
                                    .background(
                                        Accent.copy(
                                            alpha = when {
                                                day.isAfter(now) -> .035f
                                                level == 0 -> .09f
                                                level == 1 -> .28f
                                                level == 2 -> .48f
                                                level == 3 -> .70f
                                                else -> .96f
                                            },
                                        ),
                                    )
                                    .then(if (day == now) Modifier.border(1.dp, AccentLight, RoundedCornerShape(3.dp)) else Modifier),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.less_activity), color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                Spacer(Modifier.width(5.dp))
                listOf(.09f, .28f, .48f, .70f, .96f).forEach { alpha ->
                    Box(Modifier.padding(horizontal = 1.5.dp).size(9.dp).clip(RoundedCornerShape(2.dp)).background(Accent.copy(alpha = alpha)))
                }
                Spacer(Modifier.width(5.dp))
                Text(stringResource(R.string.more_activity), color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun StatisticTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxHeight().glass(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Medium)).padding(horizontal = com.cinetrack.ui.theme.Spacing.sm, vertical = com.cinetrack.ui.theme.Spacing.md), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(
            value.ifBlank { "—" },
            color = TextPrimary,
            style = MaterialTheme.typography.titleSmall,
            lineHeight = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Text(label, color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun PeopleStatisticsRow(title: String, people: List<PersonCard>) {
    Text(title, color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(6.dp))
    if (people.isEmpty()) {
        Text("—", color = TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
        return
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(people, key = PersonCard::id) { person ->
            Column(Modifier.width(64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Accent.copy(alpha = .55f), Info.copy(alpha = .45f)))),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!person.profileUrl.isNullOrBlank()) {
                        AsyncImage(
                            person.profileUrl,
                            person.name,
                            Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Text(person.name.take(1).uppercase(), color = TextPrimary, fontWeight = FontWeight.Black)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    person.name,
                    color = TextSecondary,
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    lineHeight = 10.5.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun HistoryTimelineItem(
    item: TimelineCard,
    previous: TimelineCard?,
    onMedia: (MediaCard) -> Unit,
    onEpisode: (EpisodeCard) -> Unit,
) {
    val currentDay = timelineLocalDate(item.timestamp)
    val previousDay = previous?.let { timelineLocalDate(it.timestamp) }
    Column {
        if (currentDay != null && currentDay != previousDay) {
            Text(historyDayLabel(currentDay), color = TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = com.cinetrack.ui.theme.Spacing.xl, top = com.cinetrack.ui.theme.Spacing.sm, bottom = com.cinetrack.ui.theme.Spacing.sm))
        }
        TimelineRow(item, onMedia, onEpisode, history = true)
    }
}

@Composable
private fun TimelineRow(
    item: TimelineCard,
    onMedia: (MediaCard) -> Unit,
    onEpisode: (EpisodeCard) -> Unit,
    history: Boolean,
) {
    val openItem = {
        val season = item.season
        val number = item.episodeNumber
        if (item.media.type == com.cinetrack.domain.MediaType.TV && season != null && number != null) {
            onEpisode(
                EpisodeCard(
                    id = item.episodeId ?: -1,
                    showId = item.media.id,
                    season = season,
                    number = number,
                    title = item.episodeLabel?.substringAfter(" · ", "").orEmpty(),
                    overview = "",
                    airDate = item.timestamp,
                ),
            )
        } else {
            onMedia(item.media)
        }
    }
    val interaction = remember(item.media.stableKey, item.timestamp, item.episodeLabel) { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val openItemAction = rememberUiAction(openItem)
    RevealOnMount("${item.media.stableKey}:${item.timestamp}:${item.episodeLabel}") {
    Row(
        Modifier.padding(start = com.cinetrack.ui.theme.Spacing.xl, end = com.cinetrack.ui.theme.Spacing.xl, bottom = com.cinetrack.ui.theme.Spacing.md).fillMaxWidth().height(if (history) 112.dp else 120.dp)
            .glass(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Medium))
            .border(if (pressed) 1.7.dp else 0.dp, Accent.copy(alpha = if (pressed) .9f else 0f), RoundedCornerShape(com.cinetrack.ui.theme.Radius.Medium))
            .clickable(interactionSource = interaction, indication = null, onClick = openItemAction)
            .padding(start = if (history) 12.dp else 6.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!history) {
            val date = timelineLocalDate(item.timestamp)
            Column(Modifier.width(54.dp).padding(end = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(date?.dayOfMonth?.toString()?.padStart(2, '0').orEmpty(), color = AccentLight, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Black)
                Text(date?.format(com.cinetrack.ui.UiDateFormatters.current.month)?.uppercase(com.cinetrack.ui.currentLocale()).orEmpty(), color = AccentLight, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }
        Box(Modifier.width(68.dp).fillMaxHeight().clip(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Small)).background(Brush.linearGradient(listOf(Accent.copy(alpha = .5f), Info.copy(alpha = .32f))))) {
            if (!item.media.posterUrl.isNullOrBlank()) AsyncImage(item.media.posterUrl, item.media.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            val typeLabel = if (item.media.type == com.cinetrack.domain.MediaType.TV) stringResource(R.string.tv_shows) else stringResource(R.string.movies)
            val time = timelineTime(item.timestamp)
            val timingLabel = if (history || hasExplicitReleaseTime(item.timestamp)) listOf(typeLabel, time).filter(String::isNotBlank).joinToString(" · ") else typeLabel
            Text(timingLabel, color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Text(item.media.title, color = TextPrimary, fontWeight = FontWeight.ExtraBold, style = androidx.compose.material3.MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val episodeText = item.episodeLabel ?: item.media.year
            Text(episodeText, color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (history) Icon(Icons.Filled.Check, null, tint = Success, modifier = Modifier.size(24.dp))
    }
    }
}

private fun timelineLocalDate(raw: String): LocalDate? =
    releaseDateTime(raw, ZoneId.systemDefault())?.toLocalDate()

private fun timelineTime(raw: String): String =
    releaseDateTime(raw, ZoneId.systemDefault())?.format(com.cinetrack.ui.UiDateFormatters.current.time).orEmpty()

private fun historyDayLabel(date: LocalDate): String = date.format(com.cinetrack.ui.UiDateFormatters.current.date)

private fun shortAirDate(raw: String?): String = runCatching {
    LocalDate.parse(raw?.take(10)).format(com.cinetrack.ui.UiDateFormatters.current.weekdayDate).uppercase(Locale.getDefault())
}.getOrDefault("")

@Composable
private fun relativeSyncLabel(lastSync: Long?): String {
    if (lastSync == null) return stringResource(R.string.sync_connected_not_yet)
    val elapsed = ((System.currentTimeMillis() - lastSync).coerceAtLeast(0L) / 1_000L)
    return when {
        elapsed < 60 -> stringResource(R.string.synced_just_now)
        elapsed < 3_600 -> (elapsed / 60).toInt().let { pluralStringResource(R.plurals.synced_minutes_ago, it, it) }
        elapsed < 86_400 -> (elapsed / 3_600).toInt().let { pluralStringResource(R.plurals.synced_hours_ago, it, it) }
        else -> (elapsed / 86_400).toInt().let { pluralStringResource(R.plurals.synced_days_ago, it, it) }
    }
}

/** Minutes for one watch event; TV episodes without a runtime count as 45. */
private fun watchedMinutesOf(event: com.cinetrack.domain.TimelineCard): Int =
    event.media.runtimeMinutes ?: if (event.media.type == MediaType.TV) 45 else 0

private class WeekActivity(val minutes: Int, val episodes: Int, val movies: Int, val perDay: List<Int>, val days: List<LocalDate>)

private data class StatsInputs(
    val history: List<com.cinetrack.domain.TimelineCard>,
    val library: List<MediaCard>,
    val today: LocalDate,
)

private class StatsSummary(
    val monthly: Int,
    val yearly: Int,
    val completed: Int,
    val abandoned: Int,
    val topGenres: List<String>,
    val watchedDays: Map<LocalDate, Int>,
    val heatmapWeeks: List<List<LocalDate>>,
    val maxDailyActivity: Int,
    val longestStreak: Int,
    val currentStreak: Int,
    val week: WeekActivity,
    val habits: List<Int>,
    val monthMinutes: Int,
    val year: YearReview,
)

/** The current year at a glance: time, counts, the series watched most, streak, busiest month, genres. */
private class YearReview(
    val year: Int,
    val minutes: Int,
    val episodes: Int,
    val movies: Int,
    val topShow: MediaCard?,
    val topShowEpisodes: Int,
    val longestStreak: Int,
    val busiestMonth: java.time.Month?,
    val topGenres: List<String>,
)

/** Last statistics summary, so returning to the tab draws immediately. */
private object StatsCache {
    @Volatile var inputs: StatsInputs? = null
    @Volatile var result: StatsSummary? = null
}

private fun computeStats(inputs: StatsInputs): StatsSummary {
    val now = inputs.today
    // Parse every timestamp once and reuse it for all figures.
    val dated = inputs.history.mapNotNull { event -> timelineLocalDate(event.timestamp)?.let { it to event } }
    val watchedDays = dated.groupingBy { it.first }.eachCount()
    var longest = 0
    var run = 0
    var previous: LocalDate? = null
    watchedDays.keys.sorted().forEach { day ->
        run = if (previous?.plusDays(1) == day) run + 1 else 1
        longest = maxOf(longest, run)
        previous = day
    }
    var streakDay = if (watchedDays.containsKey(now)) now else now.minusDays(1)
    var streak = 0
    while (watchedDays.containsKey(streakDay)) { streak++; streakDay = streakDay.minusDays(1) }
    val monday = now.with(DayOfWeek.MONDAY)
    val weekDays = (0L until 7L).map { monday.plusDays(it) }
    val weekEvents = dated.filter { it.first >= monday && it.first <= weekDays.last() }
    val firstMonday = now.minusWeeks(15).with(DayOfWeek.MONDAY)
    val tv = inputs.history.filter { it.media.type == MediaType.TV }
    val movies = inputs.history.filter { it.media.type == MediaType.MOVIE }
    return StatsSummary(
        monthly = dated.count { it.first.year == now.year && it.first.month == now.month },
        yearly = dated.count { it.first.year == now.year },
        completed = inputs.library.count { it.status == com.cinetrack.domain.LibraryStatus.COMPLETED },
        abandoned = inputs.library.count { it.status == com.cinetrack.domain.LibraryStatus.DROPPED },
        topGenres = inputs.history.flatMap { it.media.genres }.groupingBy { it.lowercase() }.eachCount().entries
            .sortedByDescending { it.value }.take(4).map { it.key.replaceFirstChar { char -> char.uppercase() } },
        watchedDays = watchedDays,
        heatmapWeeks = (0L until 16L).map { week -> (0L until 7L).map { day -> firstMonday.plusWeeks(week).plusDays(day) } },
        maxDailyActivity = watchedDays.values.maxOrNull()?.coerceAtLeast(1) ?: 1,
        longestStreak = longest,
        currentStreak = streak,
        week = WeekActivity(
            minutes = weekEvents.sumOf { watchedMinutesOf(it.second) },
            episodes = weekEvents.count { it.second.media.type == MediaType.TV },
            movies = weekEvents.count { it.second.media.type == MediaType.MOVIE },
            perDay = weekDays.map { day -> weekEvents.filter { it.first == day }.sumOf { watchedMinutesOf(it.second) } },
            days = weekDays,
        ),
        habits = listOf(tv.sumOf(::watchedMinutesOf), tv.size, movies.sumOf(::watchedMinutesOf), movies.size),
        monthMinutes = dated.filter { it.first.year == now.year && it.first.month == now.month }.sumOf { watchedMinutesOf(it.second) },
        year = yearReview(dated, now),
    )
}

private fun yearReview(dated: List<Pair<LocalDate, com.cinetrack.domain.TimelineCard>>, now: LocalDate): YearReview {
    val events = dated.filter { it.first.year == now.year }
    val shows = events.filter { it.second.media.type == MediaType.TV }.groupBy { it.second.media.stableKey }
    val top = shows.maxByOrNull { it.value.size }
    var longest = 0
    var run = 0
    var previous: LocalDate? = null
    events.map { it.first }.distinct().sorted().forEach { day ->
        run = if (previous?.plusDays(1) == day) run + 1 else 1
        longest = maxOf(longest, run)
        previous = day
    }
    return YearReview(
        year = now.year,
        minutes = events.sumOf { watchedMinutesOf(it.second) },
        episodes = events.count { it.second.media.type == MediaType.TV },
        movies = events.count { it.second.media.type == MediaType.MOVIE },
        topShow = top?.value?.first()?.second?.media,
        topShowEpisodes = top?.value?.size ?: 0,
        longestStreak = longest,
        busiestMonth = events.groupingBy { it.first.month }.eachCount().maxByOrNull { it.value }?.key,
        topGenres = events.flatMap { it.second.media.genres }.groupingBy { it.lowercase() }.eachCount().entries
            .sortedByDescending { it.value }.take(3).map { it.key.replaceFirstChar { char -> char.uppercase() } },
    )
}

@Composable
private fun YearReviewCard(review: YearReview) {
    if (review.episodes + review.movies == 0) return
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(Accent.copy(alpha = .30f), Accent.copy(alpha = .06f))))
            .glass(RoundedCornerShape(20.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column {
            TrackedLabel(stringResource(R.string.year_review_title, review.year))
            Text(
                formatWatchedDurationMinutes(review.minutes).ifBlank { "0m" }, color = TextPrimary,
                style = androidx.compose.material3.MaterialTheme.typography.displaySmall, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(top = 6.dp),
            )
            Text(
                pluralStringResource(R.plurals.episodes_count, review.episodes, review.episodes) + " · " +
                    pluralStringResource(R.plurals.movies_count, review.movies, review.movies),
                color = TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            )
        }
        review.topShow?.let { show ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(40.dp).height(60.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = .06f))) {
                    show.posterUrl?.let { coil.compose.AsyncImage(it, show.title, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop) }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.year_top_show), color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
                    Text(show.title, color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(pluralStringResource(R.plurals.episodes_count, review.topShowEpisodes, review.topShowEpisodes), color = TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            HabitValue(stringResource(R.string.year_longest_streak), pluralStringResource(R.plurals.days_count, review.longestStreak, review.longestStreak), Modifier.weight(1f))
            HabitValue(
                stringResource(R.string.year_busiest_month),
                review.busiestMonth?.getDisplayName(java.time.format.TextStyle.FULL, com.cinetrack.ui.currentLocale())
                    ?.replaceFirstChar { it.titlecase(com.cinetrack.ui.currentLocale()) } ?: "—",
                Modifier.weight(1f),
            )
        }
        if (review.topGenres.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                review.topGenres.forEach { genre ->
                    Text(
                        genre, color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Pill)).background(Color.White.copy(alpha = .08f))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun WeekBars(week: WeekActivity, today: LocalDate, barWidth: androidx.compose.ui.unit.Dp, maxHeight: androidx.compose.ui.unit.Dp, labels: Boolean) {
    val peak = week.perDay.maxOrNull()?.coerceAtLeast(1) ?: 1
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.Bottom) {
        week.days.forEachIndexed { index, day ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.width(barWidth).height(maxOf(4.dp, maxHeight * (week.perDay[index].toFloat() / peak)))
                        .clip(RoundedCornerShape(barWidth / 2))
                        .background(if (day == today) Accent else TextPrimary.copy(alpha = .18f)),
                )
                if (labels) Text(
                    day.format(com.cinetrack.ui.UiDateFormatters.current.narrowWeekday),
                    color = if (day == today) TextPrimary else TextMuted,
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun TrackedLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(com.cinetrack.ui.currentLocale()),
        color = TextMuted,
        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.6.sp,
        maxLines = 1,
        modifier = modifier,
    )
}

/** Statistics: this week in large type, streak and month tiles, then viewing habits. */
@Composable
private fun StatsDashboard(summary: StatsSummary, now: LocalDate) {
    val week = summary.week
    val currentStreak = summary.currentStreak
    val habits = summary.habits
    val monthMinutes = summary.monthMinutes
    val monthly = summary.monthly
    val yearly = summary.yearly
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = com.cinetrack.ui.theme.Spacing.sm), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                TrackedLabel(stringResource(R.string.this_week))
                Text(
                    formatWatchedDurationMinutes(week.minutes).ifBlank { "0m" },
                    color = TextPrimary,
                    style = androidx.compose.material3.MaterialTheme.typography.displaySmall,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Text(
                    pluralStringResource(R.plurals.episodes_count, week.episodes, week.episodes) + " · " +
                        pluralStringResource(R.plurals.movies_count, week.movies, week.movies),
                    color = TextSecondary,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                )
            }
            WeekBars(week, now, 16.dp, 60.dp, labels = true)
        }
        // Side-by-side tiles share the taller one's height.
        Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min).padding(top = com.cinetrack.ui.theme.Spacing.sm), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight().glass(RoundedCornerShape(20.dp)).padding(horizontal = 16.dp, vertical = 14.dp)) {
                TrackedLabel(stringResource(R.string.current_streak))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                    Icon(Icons.Filled.LocalFireDepartment, null, tint = com.cinetrack.ui.theme.StatusPaused, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(pluralStringResource(R.plurals.days_count, currentStreak, currentStreak), color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight().glass(RoundedCornerShape(20.dp)).padding(horizontal = 16.dp, vertical = 14.dp)) {
                TrackedLabel(now.format(com.cinetrack.ui.UiDateFormatters.current.month) + " " + now.year)
                Text(stringResource(R.string.month_recap), color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                Text(
                    listOf(formatWatchedDurationMinutes(monthMinutes).ifBlank { "0m" }, stringResource(R.string.items_watched_count, monthly)).joinToString(" · "),
                    color = TextMuted,
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                )
            }
        }
        TrackedLabel(stringResource(R.string.viewing_habits), Modifier.padding(top = com.cinetrack.ui.theme.Spacing.md))
        Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(20.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Durations each get a full-width line so they never wrap; counts share a grid.
            HabitValue(stringResource(R.string.all_time), formatWatchedDurationMinutes(habits[0] + habits[2]).ifBlank { "0m" }, Modifier.fillMaxWidth())
            HabitValue(stringResource(R.string.tv_time), formatWatchedDurationMinutes(habits[0]).ifBlank { "0m" }, Modifier.fillMaxWidth())
            HabitValue(stringResource(R.string.movie_time), formatWatchedDurationMinutes(habits[2]).ifBlank { "0m" }, Modifier.fillMaxWidth())
            Box(Modifier.fillMaxWidth().height(.6.dp).background(Color.White.copy(alpha = .07f)))
            Row(Modifier.fillMaxWidth()) {
                HabitValue(stringResource(R.string.episodes), habits[1].toString(), Modifier.weight(1f))
                HabitValue(stringResource(R.string.movies), habits[3].toString(), Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth()) {
                HabitValue(stringResource(R.string.this_month), monthly.toString(), Modifier.weight(1f))
                HabitValue(stringResource(R.string.this_year), yearly.toString(), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HabitValue(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
        Text(value, color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
    }
}

private data class UpcomingInputs(
    val episodes: List<com.cinetrack.domain.EpisodeCard>,
    val rails: Map<String, List<MediaCard>>,
    val playbackTv: List<PlaybackCard>,
    val excludeSpecials: Boolean,
    val hiddenUpcoming: Set<String>,
    val zone: ZoneId,
)

/** Last upcoming list, so re-entering Progress does not wait for the background scan. */
private object UpcomingCache {
    @Volatile var inputs: UpcomingInputs? = null
    @Volatile var result: List<com.cinetrack.domain.EpisodeCard>? = null
}

private fun computeUpcoming(inputs: UpcomingInputs, now: java.time.Instant): List<com.cinetrack.domain.EpisodeCard> {
    val trackedShowIds = (
        inputs.rails[com.cinetrack.domain.RailIds.LIBRARY].orEmpty()
            .filter {
                it.type == com.cinetrack.domain.MediaType.TV &&
                    it.status != com.cinetrack.domain.LibraryStatus.NONE &&
                    it.status != com.cinetrack.domain.LibraryStatus.DROPPED
            }
            .map(MediaCard::id) +
            inputs.playbackTv.map { it.media.id }
        ).toSet()
    return inputs.episodes.asSequence()
        .filter { it.showId in trackedShowIds }
        .filter { !inputs.excludeSpecials || it.season > 0 }
        .filter { it.scheduleKey !in inputs.hiddenUpcoming }
        .mapNotNull { episode -> releaseDateTime(episode.airDate, inputs.zone)?.toInstant()?.takeIf { it.isAfter(now) }?.let { episode to it } }
        .distinctBy { (episode, _) -> "${episode.showId}:${episode.season}:${episode.number}" }
        .sortedWith(
            compareBy<Pair<com.cinetrack.domain.EpisodeCard, java.time.Instant>> { it.second }
                .thenBy { it.first.showId }
                .thenBy { it.first.season }
                .thenBy { it.first.number },
        )
        .map { it.first }
        .toList()
}
