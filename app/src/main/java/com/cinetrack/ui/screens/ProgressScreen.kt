@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.cinetrack.ui.screens

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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.produceState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cinetrack.R
import com.cinetrack.domain.AppUiState
import com.cinetrack.domain.EpisodeCard
import com.cinetrack.domain.MediaCard
import com.cinetrack.domain.MediaType
import com.cinetrack.domain.PlaybackCard
import com.cinetrack.domain.SyncProgress
import com.cinetrack.domain.SyncStage
import com.cinetrack.domain.TimelineCard
import com.cinetrack.domain.releaseDateTime
import com.cinetrack.domain.ViewingPeopleInsights
import com.cinetrack.ui.components.AdaptiveBackground
import com.cinetrack.ui.components.MediaRail
import com.cinetrack.ui.components.PageTitle
import com.cinetrack.ui.components.SectionHeader
import com.cinetrack.ui.components.glass
import com.cinetrack.ui.components.liquidGlass
import com.cinetrack.ui.components.liquidGlassIcon
import com.cinetrack.ui.components.rememberUiAction
import com.cinetrack.ui.theme.AccentLight
import com.cinetrack.ui.theme.TextMuted
import com.cinetrack.ui.theme.TextPrimary
import com.cinetrack.ui.theme.TextSecondary
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import com.cinetrack.ui.theme.Glass
import com.cinetrack.ui.theme.GlassStrong
import com.cinetrack.ui.theme.Motion
import com.cinetrack.ui.theme.Radius
import com.cinetrack.ui.theme.Spacing

private enum class ProgressTab { IN_PROGRESS, CALENDAR, HISTORY, STATISTICS }
internal enum class PlaybackOrder { RECENT, TITLE, TOP_RATED, REMAINING, RANDOM }

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
    releaseReminders: Set<String> = emptySet(),
    isReminderOn: (com.cinetrack.domain.TimelineCard) -> Boolean = { false },
    onReminder: (com.cinetrack.domain.TimelineCard, Boolean) -> Unit = { _, _ -> },
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
    val today = remember(releaseZone) { LocalDate.now(releaseZone) }
    val calendarDays = remember(state.calendar, releaseZone) { calendarDays(state.calendar, releaseZone) }
    // Grouping parses every history timestamp, so it runs off the main thread.
    val historyEntries by produceState(initialValue = HistoryCache.result.takeIf { HistoryCache.input === state.history }, state.history) {
        val computed = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { historyEntries(state.history) }
        HistoryCache.input = state.history
        HistoryCache.result = computed
        value = computed
    }
    val timelineScope = rememberCoroutineScope()
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
                // Saved, so the rebuilt page is laid out below its header from the first frame.
                var titlePx by rememberSaveable { mutableIntStateOf(0) }
                var headerPx by rememberSaveable { mutableIntStateOf(0) }
                val headerScroll = remember(activeListState) {
                    { if (activeListState.firstVisibleItemIndex > 0) Int.MAX_VALUE else activeListState.firstVisibleItemScrollOffset }
                }
                Column(
                    Modifier.zIndex(1f).fillMaxWidth()
                        .onSizeChanged { headerPx = it.height }
                        .pinnedHeader({ titlePx }, headerScroll, com.cinetrack.ui.components.LocalPageTint.current),
                ) {
                Row(
                    Modifier.fillMaxWidth().onSizeChanged { titlePx = it.height }
                        .collapsingTitle({ titlePx }, headerScroll)
                        .padding(start = Spacing.xl, end = Spacing.xl, top = Spacing.lg, bottom = Spacing.lg),
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
                        (slideInHorizontally(tween(Motion.Long)) { it / 3 } + fadeIn(tween(Motion.Medium))) togetherWith
                            (slideOutHorizontally(tween(Motion.Medium)) { -it / 4 } + fadeOut(tween(Motion.Short)))
                    } else {
                        (slideInHorizontally(tween(Motion.Long)) { -it / 3 } + fadeIn(tween(Motion.Medium))) togetherWith
                            (slideOutHorizontally(tween(Motion.Medium)) { it / 4 } + fadeOut(tween(Motion.Short)))
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
                        item(contentType = "progress:playback") {
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
                        item(contentType = "progress:playback") {
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
                        item(contentType = "progress:upcoming") { UpcomingEpisodesRail(stringResource(R.string.coming_soon), comingSoon, allMedia, onEpisode, onHideUpcoming) }
                    }
                    if (upcomingResult?.isEmpty() == true) {
                        item { UpcomingDiagnostics(trackedShowCount) }
                    }
                        if (tvList.isNotEmpty()) {
                            item(contentType = "progress:header") { SectionHeader(stringResource(R.string.your_tv_list), Modifier.padding(start = Spacing.xl, end = Spacing.xl, top = Spacing.sm, bottom = Spacing.md)) }
                            item(contentType = "progress:rail") { MediaRail(tvList, onMedia, showYear = false) }
                        }
                        if (movieList.isNotEmpty()) {
                            item(contentType = "progress:header") { SectionHeader(stringResource(R.string.your_movie_list), Modifier.padding(start = Spacing.xl, end = Spacing.xl, top = Spacing.xxl, bottom = Spacing.md)) }
                            item(contentType = "progress:rail") { MediaRail(movieList, onMedia, showYear = false) }
                        }
                    }
                    ProgressTab.HISTORY -> {
                        val entries = historyEntries
                        if (state.history.isEmpty()) item { ProgressEmpty(stringResource(R.string.no_history)) }
                        else if (entries == null) item { LoadingPane(Modifier.height(320.dp)) }
                        else historyTimeline(entries, today, onMedia, onEpisode)
                    }
                    ProgressTab.CALENDAR -> {
                        if (calendarDays.isEmpty()) item { ProgressEmpty(stringResource(R.string.no_calendar_items)) }
                        else calendarTimeline(calendarDays, today, releaseZone, calendarListState, timelineScope, isReminderOn, onReminder, onMedia, onEpisode)
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
    Column(Modifier.padding(horizontal = Spacing.xl)
        .padding(bottom = Spacing.lg).fillMaxWidth()
        .glass(RoundedCornerShape(Radius.Medium))
        .animateContentSize().padding(horizontal = Spacing.md, vertical = Spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CloudSync, null, tint = if (needsAttention) MaterialTheme.colorScheme.error else AccentLight, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f).clip(RoundedCornerShape(Radius.Small))
                .background(if (statusPressed) GlassStrong else Color.Transparent)
                .clickable(interactionSource = statusInteraction, indication = null, onClick = if (connected) onOperations else onSettings)
                .padding(horizontal = Spacing.sm, vertical = Spacing.sm)) {
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
            Column(Modifier.padding(bottom = Spacing.md)) {
                if (sync.running) {
                    LinearProgressIndicator(
                        progress = { sync.progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
                        color = AccentLight, trackColor = Glass,
                    )
                    Spacer(Modifier.height(Spacing.sm))
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
    Column(Modifier.fillMaxWidth().padding(bottom = Spacing.sm)) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp)
                .slidingTabIndicator(indicator, selected.ordinal),
            horizontalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            ProgressTab.entries.forEach { tab ->
                val active = tab == selected
                val selectAction = rememberUiAction { onSelected(tab) }
                val contentColor by animateColorAsState(if (active) TextPrimary else TextPrimary.copy(alpha = .5f), tween(Motion.Medium), label = "progressTabText")
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
internal fun ProgressEmpty(message: String) {
    Text(
        message,
        color = TextSecondary,
        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(horizontal = Spacing.xl).fillMaxWidth().glass(RoundedCornerShape(Radius.Medium)).padding(Spacing.lg),
    )
}

internal fun timelineLocalDate(raw: String): LocalDate? =
    releaseDateTime(raw, ZoneId.systemDefault())?.toLocalDate()

internal fun shortAirDate(raw: String?): String = runCatching {
    LocalDate.parse(raw?.take(10)).format(com.cinetrack.ui.UiDateFormatters.current.weekdayDate).uppercase(Locale.getDefault())
}.getOrDefault("")

@Composable
private fun relativeSyncLabel(lastSync: Long?): String {
    if (lastSync == null) return stringResource(R.string.sync_connected_not_yet)
    return when (val elapsed = com.cinetrack.ui.elapsedSince(lastSync)) {
        com.cinetrack.ui.Elapsed.JustNow -> stringResource(R.string.synced_just_now)
        is com.cinetrack.ui.Elapsed.Minutes -> elapsed.count.toInt().let { pluralStringResource(R.plurals.synced_minutes_ago, it, it) }
        is com.cinetrack.ui.Elapsed.Hours -> elapsed.count.toInt().let { pluralStringResource(R.plurals.synced_hours_ago, it, it) }
        is com.cinetrack.ui.Elapsed.Days -> elapsed.count.toInt().let { pluralStringResource(R.plurals.synced_days_ago, it, it) }
    }
}
