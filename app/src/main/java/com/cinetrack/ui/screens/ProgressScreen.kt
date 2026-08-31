@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.cinetrack.ui.screens

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
import androidx.compose.material.icons.filled.NotificationsNone
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cinetrack.R
import com.cinetrack.domain.AppUiState
import com.cinetrack.domain.EpisodeCard
import com.cinetrack.domain.MediaCard
import com.cinetrack.domain.PlaybackCard
import com.cinetrack.domain.SyncProgress
import com.cinetrack.domain.SyncStage
import com.cinetrack.domain.TimelineCard
import com.cinetrack.domain.ViewingPeopleInsights
import com.cinetrack.ui.components.AdaptiveBackground
import com.cinetrack.ui.components.MediaRail
import com.cinetrack.ui.components.PageTitle
import com.cinetrack.ui.components.PrimaryAction
import com.cinetrack.ui.components.RevealOnMount
import com.cinetrack.ui.components.SectionHeader
import com.cinetrack.ui.components.SharedGlassSheet
import com.cinetrack.ui.components.glass
import com.cinetrack.ui.components.glassIcon
import com.cinetrack.ui.components.rememberLightHapticAction
import com.cinetrack.ui.theme.Accent
import com.cinetrack.ui.theme.AccentLight
import com.cinetrack.ui.theme.DesignTokens
import com.cinetrack.ui.theme.Info
import com.cinetrack.ui.theme.Success
import com.cinetrack.ui.theme.TextMuted
import com.cinetrack.ui.theme.TextPrimary
import com.cinetrack.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class ProgressTab { IN_PROGRESS, CALENDAR, HISTORY, STATISTICS }
private enum class PlaybackOrder { RECENT, TITLE, TOP_RATED, REMAINING, RANDOM }

@Composable
fun ProgressScreen(
    state: AppUiState,
    onSearch: () -> Unit,
    onSync: () -> Unit,
    onMedia: (MediaCard) -> Unit,
    onWatched: (PlaybackCard) -> Unit,
    onEpisode: (EpisodeCard) -> Unit,
    onHideUpcoming: (EpisodeCard) -> Unit,
    viewingInsights: ViewingPeopleInsights,
    onLoadViewingInsights: () -> Unit,
    onCompactNav: (Boolean) -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(ProgressTab.IN_PROGRESS) }
    val progressListState = rememberLazyListState()
    val calendarListState = rememberLazyListState()
    val historyListState = rememberLazyListState()
    val activeListState = when (tab) {
        ProgressTab.IN_PROGRESS -> progressListState
        ProgressTab.CALENDAR -> calendarListState
        ProgressTab.HISTORY -> historyListState
        ProgressTab.STATISTICS -> historyListState
    }
    NavCollapseEffect(activeListState, onCompactNav)
    LaunchedEffect(tab) {
        if (tab == ProgressTab.STATISTICS) onLoadViewingInsights()
    }
    val artwork = state.playbackTv.firstOrNull()?.media?.posterUrl
        ?: state.playbackMovies.firstOrNull()?.media?.posterUrl
        ?: state.episodes.firstOrNull()?.stillUrl
    val today = remember(state.metadataTimezone) {
        val zone = if (state.metadataTimezone == "system") ZoneId.systemDefault()
        else runCatching { ZoneId.of(state.metadataTimezone) }.getOrDefault(ZoneId.systemDefault())
        LocalDate.now(zone)
    }
    val upcomingEpisodes = remember(state.episodes, state.rails, state.playbackTv, state.excludeSpecials, state.hiddenUpcoming, today) {
        val trackedShowIds = (
            state.rails[com.cinetrack.domain.RailIds.LIBRARY].orEmpty()
                .filter {
                    it.type == com.cinetrack.domain.MediaType.TV &&
                        it.status != com.cinetrack.domain.LibraryStatus.NONE &&
                        it.status != com.cinetrack.domain.LibraryStatus.DROPPED
                }
                .map(com.cinetrack.domain.MediaCard::id) +
                state.playbackTv.map { it.media.id }
            ).toSet()
        state.episodes.asSequence()
            .filter { it.showId in trackedShowIds }
            .filter { !state.excludeSpecials || it.season > 0 }
            .filter { it.scheduleKey !in state.hiddenUpcoming }
            .filter { episode ->
                episode.airDate?.take(10)?.let { raw ->
                    runCatching { !LocalDate.parse(raw).isBefore(today) }.getOrDefault(false)
                } == true
            }
            .distinctBy { "${it.showId}:${it.season}:${it.number}" }
            .sortedWith(
                compareBy<com.cinetrack.domain.EpisodeCard> { it.airDate ?: "9999-99-99" }
                    .thenBy(com.cinetrack.domain.EpisodeCard::showId)
                    .thenBy(com.cinetrack.domain.EpisodeCard::season)
                    .thenBy(com.cinetrack.domain.EpisodeCard::number),
            )
            .toList()
    }
    AdaptiveBackground(artworkUrl = artwork) {
        LongPullRefreshContainer(
            refreshing = state.sync.running,
            onRefresh = onSync,
            enabled = state.simklConnected,
        ) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PageTitle(stringResource(R.string.progress), Modifier.weight(1f))
                    IconButton(onClick = rememberLightHapticAction(onSearch), modifier = Modifier.size(42.dp).glassIcon()) {
                        Icon(Icons.Filled.Search, stringResource(R.string.accessibility_search), tint = TextPrimary, modifier = Modifier.size(21.dp))
                    }
                }
                ProgressTabs(tab) { tab = it }
                SyncCard(state.sync, state.simklConnected, onSync)
            AnimatedContent(
                targetState = tab,
                modifier = Modifier.fillMaxWidth().weight(1f),
                transitionSpec = {
                    if (targetState.ordinal > initialState.ordinal) {
                        (slideInHorizontally(tween(310)) { it / 3 } + fadeIn(tween(230))) togetherWith
                            (slideOutHorizontally(tween(260)) { -it / 4 } + fadeOut(tween(190)))
                    } else {
                        (slideInHorizontally(tween(310)) { -it / 3 } + fadeIn(tween(230))) togetherWith
                            (slideOutHorizontally(tween(260)) { it / 4 } + fadeOut(tween(190)))
                    }
                },
                label = "progressTabPage",
            ) { activeTab ->
                LazyColumn(
                    state = when (activeTab) {
                        ProgressTab.IN_PROGRESS -> progressListState
                        ProgressTab.CALENDAR -> calendarListState
                        ProgressTab.HISTORY -> historyListState
                    },
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 116.dp),
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
                    val availableToday = upcomingEpisodes.filter { it.airDate?.take(10) == today.toString() }
                    val comingSoon = upcomingEpisodes.filterNot { it.airDate?.take(10) == today.toString() }
                    if (availableToday.isNotEmpty()) {
                        item { UpcomingEpisodesRail(stringResource(R.string.already_available), availableToday, state.allMedia, onEpisode, onHideUpcoming) }
                    }
                    if (comingSoon.isNotEmpty()) {
                        item { UpcomingEpisodesRail(stringResource(R.string.coming_soon), comingSoon, state.allMedia, onEpisode, onHideUpcoming) }
                    }
                    if (upcomingEpisodes.isEmpty()) {
                        item { UpcomingDiagnostics(state) }
                    }
                        val tvList = state.rails[com.cinetrack.domain.RailIds.LIBRARY].orEmpty().filter { it.type == com.cinetrack.domain.MediaType.TV && !it.watched && it.status != com.cinetrack.domain.LibraryStatus.COMPLETED }
                        val movieList = state.rails[com.cinetrack.domain.RailIds.LIBRARY].orEmpty().filter { it.type == com.cinetrack.domain.MediaType.MOVIE && !it.watched && it.status != com.cinetrack.domain.LibraryStatus.COMPLETED }
                        if (tvList.isNotEmpty()) {
                            item { SectionHeader(stringResource(R.string.your_tv_list), Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 12.dp)) }
                            item { MediaRail(tvList, onMedia) }
                        }
                        if (movieList.isNotEmpty()) {
                            item { SectionHeader(stringResource(R.string.your_movie_list), Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 12.dp)) }
                            item { MediaRail(movieList, onMedia) }
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
private fun SyncCard(sync: SyncProgress, connected: Boolean, onSync: () -> Unit) {
    Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp).fillMaxWidth().glass(RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                SimklMark()
                Box(Modifier.align(Alignment.TopEnd).size(9.dp).clip(CircleShape).background(if (connected) Success else TextMuted).border(2.dp, Color(0xFF4B4746), CircleShape))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("SIMKL", color = TextMuted, fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    if (connected) relativeSyncLabel(sync.lastSuccessfulSync) else stringResource(R.string.not_connected),
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            IconButton(onClick = onSync, enabled = connected && !sync.running) {
                Icon(Icons.Filled.Refresh, stringResource(R.string.accessibility_sync), tint = if (connected) AccentLight else TextMuted)
            }
        }
        AnimatedVisibility(sync.running || sync.stage == SyncStage.ERROR) {
            Column(Modifier.padding(top = 13.dp)) {
                LinearProgressIndicator(
                    progress = { sync.progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
                    color = if (sync.stage == SyncStage.ERROR) MaterialTheme.colorScheme.error else AccentLight,
                    trackColor = Color.White.copy(alpha = .10f),
                )
                Spacer(Modifier.height(7.dp))
                Text(sync.message ?: syncStageLabel(sync.stage), color = TextSecondary, fontSize = 11.sp)
            }
        }
        AnimatedVisibility(!sync.running && sync.report.lastIncrementalSync != null) {
            val report = sync.report
            Text(
                if (report.databaseUntouched) stringResource(R.string.database_untouched)
                else stringResource(R.string.sync_summary, report.downloaded, report.uploaded, report.added, report.removed),
                color = if (report.failedOperations > 0) MaterialTheme.colorScheme.error else TextMuted,
                fontSize = 10.5.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun SimklMark() {
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFF2F2F4).copy(alpha = .92f))
            .border(.55.dp, Color.White.copy(alpha = .18f), RoundedCornerShape(12.dp)),
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
    else -> stringResource(R.string.syncing)
}

@Composable
private fun ProgressTabs(selected: ProgressTab, onSelected: (ProgressTab) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(ProgressTab.entries) { tab ->
            val active = tab == selected
            val hapticSelect = rememberLightHapticAction { onSelected(tab) }
            val pillColor by animateColorAsState(
                if (active) Accent.copy(alpha = .30f) else Color.Black.copy(alpha = .08f),
                tween(240, easing = FastOutSlowInEasing),
                label = "progressPillColor",
            )
            val contentColor by animateColorAsState(
                if (active) TextPrimary else TextSecondary,
                tween(220),
                label = "progressPillContent",
            )
            val pillScale by animateFloatAsState(
                if (active) 1f else .985f,
                tween(240, easing = FastOutSlowInEasing),
                label = "progressPillScale",
            )
            Row(
                Modifier.scale(pillScale).glass(RoundedCornerShape(999.dp))
                    .background(pillColor)
                    .clickable(onClick = hapticSelect).padding(vertical = 10.dp, horizontal = 15.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    when (tab) {
                        ProgressTab.IN_PROGRESS -> Icons.Filled.PlayCircle
                        ProgressTab.CALENDAR -> Icons.Filled.CalendarMonth
                        ProgressTab.HISTORY -> Icons.Filled.History
                        ProgressTab.STATISTICS -> Icons.Filled.Insights
                    },
                    null,
                    tint = if (active) AccentLight else TextMuted,
                    modifier = Modifier.size(17.dp),
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    when (tab) {
                        ProgressTab.IN_PROGRESS -> stringResource(R.string.in_progress)
                        ProgressTab.CALENDAR -> stringResource(R.string.calendar)
                        ProgressTab.HISTORY -> stringResource(R.string.history)
                        ProgressTab.STATISTICS -> stringResource(R.string.statistics)
                    },
                    color = contentColor,
                    fontSize = 13.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
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
    val buttonOffset by animateDpAsState(
        if (!expanded && orderedItems.size > 3) (-42).dp else 0.dp,
        spring(dampingRatio = .82f, stiffness = Spring.StiffnessMediumLow),
        label = "showAllOffset",
    )
    Column(
        Modifier.animateContentSize(
            animationSpec = spring(dampingRatio = .86f, stiffness = Spring.StiffnessLow),
        ),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = .2.sp, modifier = Modifier.weight(1f).padding(bottom = 12.dp))
            if (showEpisodeControl) {
                Row(
                    Modifier.padding(bottom = 8.dp).glass(RoundedCornerShape(999.dp)).clickable(onClick = rememberLightHapticAction { showOrderSheet = true })
                        .padding(horizontal = 10.dp, vertical = 6.dp),
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
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            if (expanded) {
                IconButton(onClick = { expanded = false }, modifier = Modifier.padding(bottom = 7.dp).size(30.dp)) {
                    Icon(Icons.Filled.ExpandLess, stringResource(R.string.collapse), tint = TextSecondary, modifier = Modifier.size(18.dp))
                }
            }
        }
        if (items.isEmpty()) {
            Text(
                stringResource(R.string.no_playback),
                color = TextMuted,
                fontSize = 11.5.sp,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 14.dp).fillMaxWidth().glass(RoundedCornerShape(16.dp)).padding(14.dp),
            )
        }
        orderedItems.take(3).forEachIndexed { index, item ->
            AnimatedPlaybackSlot(index, item, onMedia, onEpisode, onWatched)
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(expandFrom = Alignment.Top, animationSpec = tween(330)) + fadeIn(tween(250)),
            exit = shrinkVertically(shrinkTowards = Alignment.Top, animationSpec = tween(280)) + fadeOut(tween(190)),
        ) {
            Column {
                orderedItems.drop(3).forEach { PlaybackRow(it, onMedia, onEpisode, onWatched) }
            }
        }
        if (!expanded && orderedItems.size > 3) {
            // Fade the next complete card as one offscreen layer. DstIn makes the
            // fade independent of the adaptive page colour and avoids a hard clip.
            Box(
                Modifier.fillMaxWidth().height(132.dp)
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            brush = Brush.verticalGradient(
                                0f to Color.White.copy(alpha = .66f),
                                .44f to Color.White.copy(alpha = .40f),
                                .78f to Color.White.copy(alpha = .14f),
                                1f to Color.Transparent,
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                    },
            ) {
                Box(Modifier.fillMaxWidth()) {
                    PlaybackRow(orderedItems[3], onMedia, onEpisode, onWatched)
                }
            }
        }
        if (items.size > 3 || (showEpisodeControl && items.isNotEmpty())) {
            Row(
                Modifier.padding(start = 20.dp, end = 20.dp, bottom = 22.dp).offset(y = buttonOffset).fillMaxWidth().height(40.dp)
                    .glass(RoundedCornerShape(999.dp)).clickable {
                        if (orderedItems.size > 3) expanded = !expanded else orderedItems.firstOrNull()?.media?.let(onMedia)
                    },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(if (expanded) stringResource(R.string.collapse) else showAllLabel, color = TextSecondary, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
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
            (slideInHorizontally(tween(340, easing = FastOutSlowInEasing)) { it / 7 } + fadeIn(tween(250))) togetherWith
                (slideOutHorizontally(tween(250, easing = FastOutSlowInEasing)) { -it / 8 } + fadeOut(tween(180)))
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
        Column(Modifier.padding(horizontal = 18.dp)) {
            Text(stringResource(R.string.sort_by), color = AccentLight, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = .8.sp)
            Spacer(Modifier.height(12.dp))
            PlaybackOrder.entries.forEach { option ->
                val active = option == selected
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp).glass(RoundedCornerShape(16.dp))
                        .background(if (active) Accent.copy(alpha = .18f) else Color.Transparent)
                        .clickable { selected = option }.padding(horizontal = 15.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        when (option) {
                            PlaybackOrder.RECENT -> stringResource(R.string.order_recent)
                            PlaybackOrder.TITLE -> stringResource(R.string.order_title)
                            PlaybackOrder.TOP_RATED -> stringResource(R.string.order_top_rated)
                            PlaybackOrder.REMAINING -> stringResource(R.string.order_remaining)
                            PlaybackOrder.RANDOM -> stringResource(R.string.order_random)
                        },
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
                Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 14.dp).glass(RoundedCornerShape(16.dp))
                    .clickable { ascending = !ascending }.padding(horizontal = 15.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (ascending) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward, null, tint = AccentLight, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(10.dp))
                Text(if (ascending) stringResource(R.string.ascending) else stringResource(R.string.descending), color = TextPrimary, fontWeight = FontWeight.Bold)
            }
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
    val hapticOpenItem = rememberLightHapticAction(openItem)
    RevealOnMount("${item.media.stableKey}:${item.season}:${item.episodeNumber}") {
    Box(
        Modifier.padding(start = 20.dp, end = 20.dp, bottom = 14.dp).fillMaxWidth().height(146.dp)
            .glass(RoundedCornerShape(21.dp))
            .border(if (cardPressed) 1.7.dp else 0.dp, Accent.copy(alpha = if (cardPressed) .9f else 0f), RoundedCornerShape(21.dp))
            .clickable(interactionSource = cardInteraction, indication = null, onClick = hapticOpenItem),
    ) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(118.dp).fillMaxHeight().background(Brush.linearGradient(listOf(Color(0xFF705446), Color(0xFF152538))))) {
                val rowArtwork = item.media.posterUrl ?: item.media.backdropUrl
                if (!rowArtwork.isNullOrBlank()) AsyncImage(rowArtwork, item.media.title, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
            }
            Column(Modifier.weight(1f).fillMaxHeight().padding(start = 15.dp, end = 52.dp, top = 14.dp, bottom = 13.dp)) {
                Text(item.media.title.uppercase(), color = TextPrimary, fontSize = 16.sp, letterSpacing = .65.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val detail = if (item.media.type == com.cinetrack.domain.MediaType.TV) {
                    val numbered = item.episodeLabel ?: listOfNotNull(item.season?.let { "S$it" }, item.episodeNumber?.let { "E$it" }).joinToString(" ")
                    listOfNotNull(numbered.takeIf(String::isNotBlank), item.episodeTitle?.takeIf(String::isNotBlank)).joinToString(" · ")
                } else stringResource(R.string.movies)
                Text(detail.ifBlank { stringResource(R.string.in_progress) }, color = TextSecondary, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    item.remainingMinutes?.let { "${formatDurationMinutes(it)} ${stringResource(R.string.remaining).lowercase()}" } ?: if (item.progress > 0f) "${(item.progress * 100).toInt()}%" else if (item.media.type == com.cinetrack.domain.MediaType.TV) stringResource(R.string.up_next) else stringResource(R.string.in_progress),
                    color = AccentLight,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                Spacer(Modifier.weight(1f))
                AnimatedProgressBar(timelineProgress)
                Spacer(Modifier.height(15.dp))
            }
            IconButton(
                onClick = rememberLightHapticAction { confirming = true },
                modifier = Modifier.align(Alignment.Bottom).padding(end = 28.dp, bottom = 20.dp).size(35.dp).clip(CircleShape)
                    .background(Color(0xFF44505A).copy(alpha = .62f)).border(1.1.dp, Accent.copy(alpha = .86f), CircleShape),
            ) {
                Icon(Icons.Filled.Check, stringResource(R.string.mark_watched), tint = TextPrimary, modifier = Modifier.size(18.dp).scale(checkScale))
            }
        }
        AnimatedVisibility(
            visible = confirming,
            modifier = Modifier.matchParentSize(),
            enter = slideInHorizontally(tween(310, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(120)),
            exit = slideOutHorizontally(tween(230, easing = FastOutSlowInEasing)) { -it / 5 } + fadeOut(tween(190)),
        ) {
            Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(21.dp)).background(Success.copy(alpha = .96f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(34.dp))
                    Text(stringResource(R.string.watched), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
    }
}

@Composable
private fun AnimatedProgressBar(progress: Float) {
    val safeProgress = progress.coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(safeProgress, tween(420), label = "progressValue")
    Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(Color.White.copy(alpha = .10f))) {
        Box(Modifier.fillMaxWidth(animatedProgress).fillMaxHeight().clip(CircleShape).background(Brush.horizontalGradient(listOf(AccentLight, Accent))))
    }
}

@Composable
private fun ProgressEmpty(message: String) {
    Text(
        message,
        color = TextSecondary,
        fontSize = 12.sp,
        modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth().glass(RoundedCornerShape(16.dp)).padding(16.dp),
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
    Column(Modifier.padding(top = 8.dp, bottom = 18.dp)) {
        SectionHeader(title, Modifier.padding(horizontal = 20.dp))
        Spacer(Modifier.height(13.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(episodes.distinctBy(EpisodeCard::id), key = EpisodeCard::id) { episode ->
                val showTitle = shows.firstOrNull { it.id == episode.showId && it.type == com.cinetrack.domain.MediaType.TV }?.title.orEmpty()
                val interaction = remember(episode.showId, episode.season, episode.number) { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                val hapticOpenEpisode = rememberLightHapticAction { onEpisode(episode) }
                Column(
                    Modifier.width(236.dp).combinedClickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = hapticOpenEpisode,
                        onLongClick = { onHide(episode) },
                    ),
                ) {
                    Box(Modifier.fillMaxWidth().height(132.dp).clip(RoundedCornerShape(17.dp)).background(Brush.linearGradient(listOf(Accent.copy(alpha = .65f), Info.copy(alpha = .28f))))
                        .border(if (pressed) 1.7.dp else 0.dp, Accent.copy(alpha = if (pressed) .9f else 0f), RoundedCornerShape(17.dp))) {
                        if (!episode.stillUrl.isNullOrBlank()) AsyncImage(episode.stillUrl, episode.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .74f)))))
                        Text(shortAirDate(episode.airDate), color = TextPrimary, fontSize = 9.5.sp, lineHeight = 10.sp, fontWeight = FontWeight.ExtraBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(8.dp).glass(RoundedCornerShape(8.dp)).padding(horizontal = 7.dp, vertical = 4.dp))
                        Box(Modifier.align(Alignment.TopEnd).padding(8.dp).size(24.dp).clip(CircleShape).background(Color(0x99060D13)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.NotificationsNone, null, tint = Color.White, modifier = Modifier.size(13.dp))
                        }
                        Text(showTitle, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.BottomStart).padding(11.dp))
                    }
                    Text("${episode.label.replace(" · ", " ")} · ${episode.title}", color = TextSecondary, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun UpcomingDiagnostics(state: AppUiState) {
    val trackedShows = state.rails[com.cinetrack.domain.RailIds.LIBRARY].orEmpty().count {
        it.type == com.cinetrack.domain.MediaType.TV && it.status != com.cinetrack.domain.LibraryStatus.NONE
    }
    Column(
        Modifier.padding(horizontal = 20.dp, vertical = 10.dp).fillMaxWidth()
            .glass(RoundedCornerShape(16.dp)).padding(14.dp),
    ) {
        Text(stringResource(R.string.why_not_shown), color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(5.dp))
        Text(
            if (trackedShows == 0) stringResource(R.string.no_tracked_shows_for_upcoming)
            else stringResource(R.string.no_announced_episodes, trackedShows),
            color = TextMuted,
            fontSize = 11.sp,
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
    val historyDates = remember(state.history) { state.history.mapNotNull { timelineLocalDate(it.timestamp) } }
    val monthly = historyDates.count { it.year == now.year && it.month == now.month }
    val yearly = historyDates.count { it.year == now.year }
    val watchedMinutes = remember(state.history) {
        state.history.sumOf { event -> event.media.runtimeMinutes ?: if (event.media.type == com.cinetrack.domain.MediaType.TV) 45 else 0 }
    }
    val library = state.rails[com.cinetrack.domain.RailIds.LIBRARY].orEmpty()
    val completed = library.count { it.status == com.cinetrack.domain.LibraryStatus.COMPLETED }
    val abandoned = library.count { it.status == com.cinetrack.domain.LibraryStatus.DROPPED }
    val finishedTotal = completed + abandoned
    val completionRate = if (finishedTotal == 0) 0 else completed * 100 / finishedTotal
    val topGenres = remember(state.history) {
        state.history.flatMap { it.media.genres }.groupingBy { it.lowercase() }.eachCount().entries
            .sortedByDescending { it.value }.take(4).map { it.key.replaceFirstChar { char -> char.uppercase() } }
    }
    val watchedDays = historyDates.groupingBy { it }.eachCount()
    val heatmapStart = now.minusDays(55)
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(stringResource(R.string.statistics))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            StatisticTile(stringResource(R.string.this_month), monthly.toString(), Modifier.weight(1f))
            StatisticTile(stringResource(R.string.this_year), yearly.toString(), Modifier.weight(1f))
            StatisticTile(stringResource(R.string.time_watched), formatDurationMinutes(watchedMinutes), Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            StatisticTile(stringResource(R.string.completion_rate), "$completionRate%", Modifier.weight(1f))
            StatisticTile(stringResource(R.string.completed), completed.toString(), Modifier.weight(1f))
            StatisticTile(stringResource(R.string.dropped), abandoned.toString(), Modifier.weight(1f))
        }
        Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(17.dp)).padding(14.dp)) {
            Text(stringResource(R.string.most_watched_genres), color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(7.dp))
            Text(topGenres.ifEmpty { listOf("—") }.joinToString(" · "), color = AccentLight, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(17.dp)).padding(14.dp)) {
            Text(stringResource(R.string.most_watched_people), color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(7.dp))
            Text(
                if (people.loading) stringResource(R.string.loading)
                else stringResource(R.string.actors_value, people.actors.ifEmpty { listOf("—") }.joinToString(" · ")),
                color = TextSecondary,
                fontSize = 11.5.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (people.loading) ""
                else stringResource(R.string.directors_value, people.directors.ifEmpty { listOf("—") }.joinToString(" · ")),
                color = TextSecondary,
                fontSize = 11.5.sp,
            )
        }
        Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(17.dp)).padding(14.dp)) {
            Text(stringResource(R.string.activity_heatmap), color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(10.dp))
            (0L..55L).map { heatmapStart.plusDays(it) }.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    week.forEach { day ->
                        val count = watchedDays[day] ?: 0
                        Box(
                            Modifier.weight(1f).height(17.dp).clip(RoundedCornerShape(5.dp))
                                .background(Accent.copy(alpha = when { count == 0 -> .08f; count == 1 -> .30f; count <= 3 -> .58f; else -> .90f })),
                        )
                    }
                }
                Spacer(Modifier.height(5.dp))
            }
        }
    }
}

@Composable
private fun StatisticTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.glass(RoundedCornerShape(16.dp)).padding(horizontal = 9.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.ifBlank { "—" }, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        Text(label, color = TextMuted, fontSize = 9.5.sp, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
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
            Text(historyDayLabel(currentDay), color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 8.dp))
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
                    airDate = item.timestamp.take(10),
                ),
            )
        } else {
            onMedia(item.media)
        }
    }
    val interaction = remember(item.media.stableKey, item.timestamp, item.episodeLabel) { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val hapticOpenItem = rememberLightHapticAction(openItem)
    RevealOnMount("${item.media.stableKey}:${item.timestamp}:${item.episodeLabel}") {
    Row(
        Modifier.padding(start = 20.dp, end = 20.dp, bottom = 11.dp).fillMaxWidth().height(if (history) 112.dp else 120.dp)
            .glass(RoundedCornerShape(19.dp))
            .border(if (pressed) 1.7.dp else 0.dp, Accent.copy(alpha = if (pressed) .9f else 0f), RoundedCornerShape(19.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = hapticOpenItem).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!history) {
            val date = timelineLocalDate(item.timestamp)
            Column(Modifier.width(48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(date?.dayOfMonth?.toString()?.padStart(2, '0').orEmpty(), color = AccentLight, fontSize = 14.sp, fontWeight = FontWeight.Black)
                Text(date?.format(DateTimeFormatter.ofPattern("MMM", Locale.getDefault()))?.uppercase(Locale.getDefault()).orEmpty(), color = AccentLight, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
        Box(Modifier.width(68.dp).fillMaxHeight().clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(Accent.copy(alpha = .5f), Info.copy(alpha = .32f))))) {
            if (!item.media.posterUrl.isNullOrBlank()) AsyncImage(item.media.posterUrl, item.media.title, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            val typeLabel = if (item.media.type == com.cinetrack.domain.MediaType.TV) stringResource(R.string.tv_shows) else stringResource(R.string.movies)
            Text(if (history) "$typeLabel · ${timelineTime(item.timestamp)}" else typeLabel, color = TextMuted, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
            Text(item.media.title, color = TextPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 13.5.sp, maxLines = 1)
            val episodeText = item.episodeLabel ?: item.media.year
            Text(episodeText, color = TextMuted, fontSize = 10.5.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (history) Icon(Icons.Filled.Check, null, tint = Success, modifier = Modifier.size(24.dp))
    }
    }
}

private fun timelineLocalDate(raw: String): LocalDate? = runCatching {
    Instant.parse(raw).atZone(ZoneId.systemDefault()).toLocalDate()
}.getOrElse { runCatching { LocalDate.parse(raw.take(10)) }.getOrNull() }

private fun timelineTime(raw: String): String = runCatching {
    Instant.parse(raw).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
}.getOrDefault("")

private fun historyDayLabel(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))

private fun shortAirDate(raw: String?): String = runCatching {
    LocalDate.parse(raw?.take(10)).format(DateTimeFormatter.ofPattern("EEE\ndd MMM", Locale.getDefault())).uppercase(Locale.getDefault())
}.getOrDefault("")

private fun relativeSyncLabel(lastSync: Long?): String {
    if (lastSync == null) return "Connected · not synced yet"
    val elapsed = ((System.currentTimeMillis() - lastSync).coerceAtLeast(0L) / 1_000L)
    return when {
        elapsed < 60 -> "Synced just now"
        elapsed < 3_600 -> "Synced ${elapsed / 60} min ago"
        elapsed < 86_400 -> "Synced ${elapsed / 3_600} h ago"
        else -> "Synced ${elapsed / 86_400} d ago"
    }
}
