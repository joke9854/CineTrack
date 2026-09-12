@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.cinetrack.ui.screens

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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.PlaylistAddCheck
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
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
import com.cinetrack.domain.LibraryStatus
import com.cinetrack.domain.MediaCard
import com.cinetrack.domain.MediaType
import com.cinetrack.domain.RailIds
import com.cinetrack.domain.SyncProgress
import com.cinetrack.domain.SyncConflictChoice
import com.cinetrack.domain.SyncOperationCard
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
    const val ServiceTmdb = "service-tmdb"
    const val ServiceMdblist = "service-mdblist"
    const val Logs = "logs"
}

private enum class LibraryOrder { TITLE, RATING, YEAR, RECENTLY_WATCHED, RECENTLY_ADDED }

@Composable
fun LibraryScreen(
    state: AppUiState,
    onSearch: () -> Unit,
    onMedia: (MediaCard) -> Unit,
    onStatus: (MediaCard, LibraryStatus) -> Unit,
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
    val gridState = rememberLazyGridState()
    NavCollapseGridEffect(gridState, onCompactNav)
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
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = com.cinetrack.ui.theme.Spacing.xl, end = com.cinetrack.ui.theme.Spacing.xl, top = com.cinetrack.ui.theme.Spacing.lg, bottom = com.cinetrack.ui.theme.Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
                PageTitle(stringResource(R.string.library), Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = rememberUiAction(onSearch), modifier = Modifier.size(48.dp).glassIcon()) {
                        Icon(Icons.Filled.Search, stringResource(R.string.accessibility_search), tint = TextSecondary, modifier = Modifier.size(21.dp))
                    }
                    IconButton(onClick = rememberUiAction { showOrderSheet = true }, modifier = Modifier.size(48.dp).glassIcon()) {
                        Icon(Icons.Filled.Sort, stringResource(R.string.sort_by), tint = TextSecondary, modifier = Modifier.size(21.dp))
                    }
                }
            }
            Row(
                Modifier.padding(horizontal = com.cinetrack.ui.theme.Spacing.xl).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(MediaType.TV, MediaType.MOVIE).forEach { item ->
                    val selected = item == type
                    Row(
                        Modifier.weight(1f).height(48.dp).glass(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Pill))
                            .background(if (selected) Accent.copy(alpha = .25f) else Color.Transparent)
                            .clickable {
                                if (selected) {
                                    gridState.requestScrollToItem(0)
                                    onCompactNav(false)
                                } else type = item
                            },
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(if (item == MediaType.TV) Icons.Filled.Tv else Icons.Filled.Movie, null, tint = if (selected) AccentLight else TextMuted, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        val count = state.rails[RailIds.LIBRARY].orEmpty().count { it.type == item }
                        Text("${if (item == MediaType.TV) stringResource(R.string.tv_shows) else stringResource(R.string.movies)} · $count", color = if (selected) TextPrimary else TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(
                    filterChoices,
                    key = { (value, _, _) -> value?.name ?: "all" },
                ) { (value, label, color) ->
                    val selected = status == value
                    val count = if (value == null) typeItems.size else typeItems.count { it.status == value }
                    Row(
                        modifier = Modifier.heightIn(min = 48.dp).glass(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Pill))
                            .background(if (selected) Accent.copy(alpha = .25f) else Color.Transparent)
                            .clickable {
                                if (selected) {
                                    gridState.requestScrollToItem(0)
                                    onCompactNav(false)
                                } else status = value
                            }.padding(horizontal = com.cinetrack.ui.theme.Spacing.md, vertical = com.cinetrack.ui.theme.Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        if (value != null) Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                        Text("$label · $count", color = if (selected) TextPrimary else TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = com.cinetrack.ui.theme.Spacing.xl), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { bulkMode = !bulkMode; selectedKeys = emptySet() }) {
                    Text(stringResource(if (bulkMode) R.string.cancel else R.string.bulk_edit), color = TextPrimary)
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
            if (items.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.AutoAwesome, null, tint = AccentLight, modifier = Modifier.size(40.dp))
                        Text(stringResource(if (status != null) R.string.library_no_matches else R.string.empty_library), color = TextPrimary, fontWeight = FontWeight.Bold, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                        Text(stringResource(if (status != null) R.string.library_no_matches_description else R.string.empty_library_description), color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                        if (status != null) TextButton(onClick = { status = null }) { Text(stringResource(R.string.clear_filters), color = TextPrimary) }
                        else TextButton(onClick = onSearch) { Text(stringResource(R.string.accessibility_search), color = TextPrimary) }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(com.cinetrack.domain.CardAppearance.gridColumns(state.cardDensity)),
                    state = gridState,
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 112.dp),
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
    val services = listOf(
        SettingsItem(SettingsPages.Integrations, stringResource(R.string.integrations),
            "TMDB · ${stringResource(if (state.tmdbApiConfigured) R.string.configured else R.string.not_configured)}  /  Simkl · ${stringResource(if (state.simklConnected) R.string.connected else R.string.not_connected)}", Icons.Filled.Link),
        SettingsItem(SettingsPages.Streaming, stringResource(R.string.streaming_services),
            stringResource(R.string.providers_selected_count, state.preferredProviders.size), Icons.Filled.Tv),
    )
    val preferences = listOf(
        SettingsItem(SettingsPages.Appearance, stringResource(R.string.appearance), stringResource(R.string.appearance_summary), Icons.Filled.Palette),
        SettingsItem(SettingsPages.Notifications, stringResource(R.string.notifications), stringResource(R.string.notifications_summary), Icons.Filled.Notifications),
        SettingsItem(SettingsPages.Language, stringResource(R.string.language), currentAppLanguageLabel(), Icons.Filled.Language),
    )
    val dataAndDiagnostics = listOf(
        SettingsItem(SettingsPages.SyncOperations, stringResource(R.string.sync_operations), stringResource(R.string.sync_health_summary, state.sync.report.pendingLocalChanges, state.sync.report.failedOperations, state.sync.report.conflicts), Icons.Filled.SyncProblem),
        SettingsItem(SettingsPages.Export, stringResource(R.string.export_data), stringResource(R.string.export_data_summary), Icons.Filled.Download),
        SettingsItem(SettingsPages.Logs, stringResource(R.string.logs), stringResource(R.string.logs_summary), Icons.Filled.BugReport),
    )
    val more = listOf(SettingsItem(SettingsPages.About, stringResource(R.string.about_app), stringResource(R.string.version_label, BuildConfig.VERSION_NAME), Icons.Filled.Info))
    AdaptiveBackground {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(bottom = 112.dp),
        ) {
            item { PageTitle(stringResource(R.string.settings), Modifier.padding(start = com.cinetrack.ui.theme.Spacing.xl, end = com.cinetrack.ui.theme.Spacing.xl, top = com.cinetrack.ui.theme.Spacing.lg, bottom = com.cinetrack.ui.theme.Spacing.lg)) }
            item { SettingsGroup(stringResource(R.string.settings_services), services, onPage) }
            item { SettingsGroup(stringResource(R.string.settings_preferences), preferences, onPage) }
            item { SettingsGroup(stringResource(R.string.settings_data), dataAndDiagnostics, onPage) }
            item { SettingsGroup(stringResource(R.string.settings_more), more, onPage) }
        }
    }
}

@Composable
private fun SettingsGroup(label: String, items: List<SettingsItem>, onPage: (String) -> Unit) {
    Column(Modifier.padding(horizontal = com.cinetrack.ui.theme.Spacing.xl, vertical = com.cinetrack.ui.theme.Spacing.md)) {
        com.cinetrack.ui.components.SectionHeader(label)
        Spacer(Modifier.height(com.cinetrack.ui.theme.Spacing.md))
        Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Medium))) {
            items.forEachIndexed { index, item ->
                SettingsRow(item, onPage)
                if (index != items.lastIndex) GlassDivider()
            }
        }
    }
}

@Composable
private fun SettingsRow(item: SettingsItem, onPage: (String) -> Unit) {
    val clickAction = rememberUiAction { onPage(item.page) }
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = clickAction).padding(horizontal = com.cinetrack.ui.theme.Spacing.lg, vertical = com.cinetrack.ui.theme.Spacing.md), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Compact)).background(Accent.copy(alpha = .18f)), contentAlignment = Alignment.Center) {
            Icon(item.icon, null, tint = AccentLight, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, color = TextPrimary, fontWeight = FontWeight.Bold, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
            Text(item.subtitle, color = TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
        SettingsPages.ServiceTmdb -> "TMDB"
        SettingsPages.ServiceMdblist -> "MDBList"
        SettingsPages.Logs -> stringResource(R.string.logs)
        else -> stringResource(R.string.about_app)
    }
    AdaptiveBackground {
        LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(start = com.cinetrack.ui.theme.Spacing.xl, end = com.cinetrack.ui.theme.Spacing.xl, top = com.cinetrack.ui.theme.Spacing.lg, bottom = com.cinetrack.ui.theme.Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
                    GlassBackButton(onClick = onBack)
                    Spacer(Modifier.size(8.dp))
                    PageTitle(title, Modifier.weight(1f))
                }
            }
            item {
                if (page in setOf(SettingsPages.ServiceSimkl, SettingsPages.ServiceTmdb, SettingsPages.ServiceMdblist)) SettingsDetailHero(page, title)
                when (page) {
                    SettingsPages.Streaming -> StreamingSettings(state, viewModel, onPage)
                    SettingsPages.Sync -> TrackingSettingsScreen(state, viewModel, { viewModel.beginSimklLogin(context) })
                    SettingsPages.SyncOperations -> SyncOperationsScreen(viewModel)
                    SettingsPages.Integrations -> IntegrationsSettings(state, onPage)
                    SettingsPages.ServiceSimkl -> SyncSettingsHost(state, viewModel, { viewModel.beginSimklLogin(context) })
                    SettingsPages.ServiceTmdb -> Column {
                        ApiCredentialSettings("TMDB", state.tmdbApiConfigured, viewModel::verifyAndSetTmdbApiKey)
                        MetadataSettings(state, viewModel)
                        ContentRegionSettings(state, viewModel)
                        SettingsSection(stringResource(R.string.streaming_services)) {
                            SettingsRow(SettingsItem(SettingsPages.Streaming, stringResource(R.string.preferred_providers), stringResource(R.string.providers_selected_count, state.preferredProviders.size), Icons.Filled.Tv), onPage)
                        }
                    }
                    SettingsPages.ServiceMdblist -> Column {
                        ApiCredentialSettings("MDBList", state.mdbListApiConfigured, viewModel::verifyAndSetMdbListApiKey)
                        RatingSettings(state, viewModel)
                    }
                    SettingsPages.Appearance -> AppearanceSettings(state, viewModel)
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
private fun SettingsDetailHero(page: String, title: String) {
    val serviceName = when (page) {
        SettingsPages.ServiceSimkl -> "Simkl"
        SettingsPages.ServiceTmdb -> "TMDB"
        SettingsPages.ServiceMdblist -> "MDBList"
        else -> null
    }
    val icon = when (page) {
        SettingsPages.Sync -> Icons.Filled.CloudSync
        SettingsPages.SyncOperations -> Icons.Filled.SyncProblem
        SettingsPages.Integrations -> Icons.Filled.Link
        SettingsPages.Notifications -> Icons.Filled.Notifications
        SettingsPages.Appearance -> Icons.Filled.Palette
        SettingsPages.Language -> Icons.Filled.Language
        SettingsPages.Ratings -> Icons.Filled.Star
        SettingsPages.Logs -> Icons.Filled.BugReport
        SettingsPages.ServiceSimkl -> Icons.Filled.CloudSync
        SettingsPages.ServiceTmdb -> Icons.Filled.Movie
        SettingsPages.ServiceMdblist -> Icons.Filled.Star
        else -> Icons.Filled.Download
    }
    val description = when (page) {
        SettingsPages.Sync -> stringResource(R.string.simkl_description)
        SettingsPages.SyncOperations -> stringResource(R.string.sync_operations_summary)
        SettingsPages.Integrations -> "TMDB · MDBList · Simkl"
        SettingsPages.Notifications -> stringResource(R.string.new_episodes)
        SettingsPages.Appearance -> stringResource(R.string.appearance_summary)
        SettingsPages.Language -> stringResource(R.string.italian)
        SettingsPages.Ratings -> "IMDb · TMDB · Metacritic · Rotten Tomatoes"
        SettingsPages.ServiceSimkl -> stringResource(R.string.simkl_description)
        SettingsPages.ServiceTmdb -> stringResource(R.string.tmdb_description)
        SettingsPages.ServiceMdblist -> stringResource(R.string.mdblist_description)
        SettingsPages.Logs -> stringResource(R.string.logs_summary)
        else -> "ZIP · JSONL · CSV"
    }
    Row(Modifier.padding(horizontal = com.cinetrack.ui.theme.Spacing.xl, vertical = com.cinetrack.ui.theme.Spacing.xs).fillMaxWidth().glass(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Medium)).padding(com.cinetrack.ui.theme.Spacing.md), verticalAlignment = Alignment.CenterVertically) {
        if (serviceName != null) ServiceLogo(serviceName)
        else Box(Modifier.size(42.dp).clip(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Small)).background(Accent.copy(alpha = .20f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = AccentLight, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.ExtraBold)
            Text(description, color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
    SettingsSection(stringResource(R.string.status)) {
        ValueRow(stringResource(R.string.synchronization), if (state.simklConnected) stringResource(R.string.connected) else stringResource(R.string.not_connected), state.simklConnected)
    }
    SettingsSection(stringResource(R.string.synchronization)) {
        ToggleRow(stringResource(R.string.background_sync), state.backgroundSync, viewModel::setBackgroundSync)
        GlassDivider()
        ToggleRow(stringResource(R.string.wifi_only), state.wifiOnly, viewModel::setWifiOnly)
    }
    if (sync.report.lastIncrementalSync != null || sync.report.failedOperations > 0) {
        val report = sync.report
        SettingsSection(stringResource(R.string.sync_activity)) {
            ValueRow(stringResource(R.string.downloaded), report.downloaded.toString())
            GlassDivider(); ValueRow(stringResource(R.string.uploaded), report.uploaded.toString())
            GlassDivider(); ValueRow(stringResource(R.string.added_removed), "${report.added} / ${report.removed}")
            GlassDivider(); ValueRow(stringResource(R.string.unchanged), report.unchanged.toString())
            GlassDivider(); ValueRow(stringResource(R.string.pending_local_changes), report.pendingLocalChanges.toString(), report.pendingLocalChanges == 0)
            GlassDivider(); ValueRow(stringResource(R.string.failed_operations), report.failedOperations.toString(), report.failedOperations == 0)
            GlassDivider(); ValueRow(stringResource(R.string.sync_conflicts), report.conflicts.toString(), report.conflicts == 0)
            report.lastFullSync?.let {
                GlassDivider(); ValueRow(stringResource(R.string.last_full_sync), java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it)))
            }
            report.lastIncrementalSync?.let {
                GlassDivider(); ValueRow(stringResource(R.string.last_incremental_sync), java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it)))
            }
            if (report.databaseUntouched) {
                GlassDivider(); ValueRow(stringResource(R.string.database_status), stringResource(R.string.database_untouched), true)
            }
        }
    }
    state.error?.takeIf(String::isNotBlank)?.let { error ->
        Text(
            error,
            color = androidx.compose.material3.MaterialTheme.colorScheme.error,
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = com.cinetrack.ui.theme.Spacing.xl, vertical = com.cinetrack.ui.theme.Spacing.xs).fillMaxWidth().glass(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Small)).padding(com.cinetrack.ui.theme.Spacing.md),
        )
    }
    sync.message?.takeIf { sync.stage == com.cinetrack.domain.SyncStage.ERROR && it.isNotBlank() }?.let { error ->
        Text(
            error,
            color = androidx.compose.material3.MaterialTheme.colorScheme.error,
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = com.cinetrack.ui.theme.Spacing.xl, vertical = com.cinetrack.ui.theme.Spacing.xs).fillMaxWidth().glass(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Small)).padding(com.cinetrack.ui.theme.Spacing.md),
        )
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = com.cinetrack.ui.theme.Spacing.xl, vertical = com.cinetrack.ui.theme.Spacing.md), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        PrimaryAction(
            text = when {
                !state.simklConnected -> stringResource(R.string.connect_simkl)
                sync.report.failedOperations > 0 -> stringResource(R.string.retry_failed_sync)
                else -> stringResource(R.string.sync_now)
            },
            icon = if (state.simklConnected) Icons.Filled.Refresh else Icons.Filled.Link,
            modifier = Modifier.weight(1f),
            onClick = if (state.simklConnected) viewModel::sync else onConnect,
        )
        if (state.simklConnected) Button(onClick = viewModel::disconnectSimkl, modifier = Modifier.height(46.dp), shape = RoundedCornerShape(com.cinetrack.ui.theme.Radius.Pill), colors = ButtonDefaults.buttonColors(containerColor = com.cinetrack.ui.theme.GlassSubtle)) { Text(stringResource(R.string.disconnect), style = androidx.compose.material3.MaterialTheme.typography.labelSmall) }
    }
}

@Composable
internal fun SyncOperationsSettings(viewModel: CineTrackViewModel) {
    val operations by viewModel.syncOperations.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refreshSyncOperations() }
    val conflicts = operations.filter { it.status == SyncOperationStatus.CONFLICT }
    val failed = operations.filter { it.status in setOf(SyncOperationStatus.FAILED, SyncOperationStatus.PARTIAL) }
    val pending = operations.filter { it.status == SyncOperationStatus.PENDING }

    SettingsSection(stringResource(R.string.sync_operations_status)) {
        ValueRow(stringResource(R.string.pending_writes), pending.size.toString(), pending.isEmpty())
        GlassDivider()
        ValueRow(stringResource(R.string.failed_actions), failed.size.toString(), failed.isEmpty())
        GlassDivider()
        ValueRow(stringResource(R.string.sync_conflicts), conflicts.size.toString(), conflicts.isEmpty())
    }

    if (operations.isEmpty()) {
        Text(
            stringResource(R.string.no_sync_operations),
            color = TextSecondary,
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = com.cinetrack.ui.theme.Spacing.xl, vertical = com.cinetrack.ui.theme.Spacing.md)
                .fillMaxWidth().glass(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Medium))
                .padding(com.cinetrack.ui.theme.Spacing.lg),
        )
    }
    if (conflicts.isNotEmpty()) SyncOperationSection(stringResource(R.string.conflicts_to_resolve), conflicts, viewModel)
    if (failed.isNotEmpty()) SyncOperationSection(stringResource(R.string.failed_actions), failed, viewModel)
    if (pending.isNotEmpty()) SyncOperationSection(stringResource(R.string.pending_writes), pending, viewModel)
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
            if (index != operations.lastIndex) GlassDivider()
        }
    }
}

@Composable
private fun SyncOperationRow(operation: SyncOperationCard, viewModel: CineTrackViewModel) {
    val statusColor = when (operation.status) {
        SyncOperationStatus.CONFLICT -> StatusPaused
        SyncOperationStatus.FAILED -> androidx.compose.material3.MaterialTheme.colorScheme.error
        SyncOperationStatus.PARTIAL -> androidx.compose.material3.MaterialTheme.colorScheme.error
        SyncOperationStatus.PENDING -> AccentLight
    }
    val providerName = operation.providerId.lowercase().replaceFirstChar { it.titlecase(Locale.getDefault()) }
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
        } else {
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
        }
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
    .replaceFirstChar { it.titlecase(Locale.getDefault()) }

@Composable
private fun IntegrationsSettings(state: AppUiState, onPage: (String) -> Unit) {
    SettingsSection(stringResource(R.string.integrations)) {
        ProviderRow("TMDB", stringResource(R.string.tmdb_description), state.tmdbApiConfigured) { onPage(SettingsPages.ServiceTmdb) }
        GlassDivider()
        ProviderRow("MDBList", stringResource(R.string.mdblist_description), state.mdbListApiConfigured) { onPage(SettingsPages.ServiceMdblist) }
        GlassDivider()
        ProviderRow("Simkl", stringResource(R.string.simkl_description), state.simklConnected) { onPage(SettingsPages.ServiceSimkl) }
    }
}

@Composable
private fun AppearanceSettings(state: AppUiState, viewModel: CineTrackViewModel) {
    CardAppearanceSettings(state, viewModel)
}

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
    val help = if (service == "TMDB") stringResource(R.string.tmdb_api_help) else stringResource(R.string.mdblist_api_help)
    SettingsSection(stringResource(R.string.api_credential)) {
        Column(Modifier.padding(com.cinetrack.ui.theme.Spacing.md)) {
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
            Spacer(Modifier.height(9.dp))
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
                label = { Text(stringResource(R.string.api_key_label)) },
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
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Accent,
                    unfocusedBorderColor = com.cinetrack.ui.theme.GlassStrong,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                ),
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
                stringResource(R.string.save),
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
    val locale = Locale.getDefault()
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
    listOf(
        Triple("language", stringResource(R.string.metadata_language), language),
        Triple("region", stringResource(R.string.metadata_region), region),
        Triple("timezone", stringResource(R.string.metadata_timezone), timezone),
    ).forEach { (key, title, value) ->
        SettingsSection(title) {
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { activeChoice = key }
                .padding(horizontal = com.cinetrack.ui.theme.Spacing.lg, vertical = com.cinetrack.ui.theme.Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                Text(value, color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.ChevronRight, title, tint = TextMuted)
            }
        }
    }
    activeChoice?.let { key ->
        val choices = when (key) { "language" -> languages; "region" -> regions; else -> timezones }
        val selected = when (key) { "language" -> state.metadataLanguage; "region" -> state.metadataRegion; else -> state.metadataTimezone }
        // Preserve legacy region-specific language tags as selectable choices.
        val options = if (choices.any { it.first == selected }) choices else listOf(selected to language) + choices
        SearchableChoiceSheet(
            stringResource(when (key) { "language" -> R.string.metadata_language; "region" -> R.string.metadata_region; else -> R.string.metadata_timezone }),
            stringResource(R.string.search), options, selected, { activeChoice = null },
            onSelected = { value ->
                when (key) { "language" -> viewModel.setMetadataLanguage(value); "region" -> viewModel.setMetadataRegion(value); else -> viewModel.setMetadataTimezone(value) }
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
        PrimaryAction(
            stringResource(R.string.allow_notifications),
            Icons.Filled.Notifications,
            Modifier.fillMaxWidth().padding(horizontal = com.cinetrack.ui.theme.Spacing.xl, vertical = com.cinetrack.ui.theme.Spacing.xs),
        ) { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
    }
    SettingsSection(stringResource(R.string.notifications)) {
        ToggleRow(stringResource(R.string.new_episodes), state.notificationEpisodes) { viewModel.setNotification("episodes", it) }
        GlassDivider(); ToggleRow(stringResource(R.string.movie_releases), state.notificationMovies) { viewModel.setNotification("movies", it) }
        GlassDivider(); ToggleRow(stringResource(R.string.sync_problems), state.notificationSync) { viewModel.setNotification("sync", it) }
        GlassDivider(); ToggleRow(stringResource(R.string.quiet_hours), state.quietHoursEnabled) { viewModel.setQuietHours(it) }
    }
    if (state.quietHoursEnabled) {
        val quietHourOptions = listOf(22 to 7, 23 to 8, 0 to 8)
        SettingsSection(stringResource(R.string.quiet_hours_schedule)) {
            quietHourOptions.forEachIndexed { index, (start, end) ->
                ChoiceRow(
                    "%02d:00–%02d:00".format(start, end),
                    state.quietHoursStart == start && state.quietHoursEnd == end,
                ) { viewModel.setQuietHours(true, start, end) }
                if (index != quietHourOptions.lastIndex) GlassDivider()
            }
        }
    }
    SettingsSection(stringResource(R.string.upcoming_episodes)) {
        ToggleRow(stringResource(R.string.exclude_specials), state.excludeSpecials, viewModel::setExcludeSpecials)
        GlassDivider()
        Text(
            stringResource(R.string.upcoming_diagnostic_explanation),
            color = TextMuted,
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            lineHeight = 15.sp,
            modifier = Modifier.padding(com.cinetrack.ui.theme.Spacing.md),
        )
    }
    if (state.hiddenUpcoming.isNotEmpty()) {
        if (hiddenEpisodes.isNotEmpty()) {
            SettingsSection(stringResource(R.string.hidden_upcoming)) {
                hiddenEpisodes.forEachIndexed { index, episode ->
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
                    if (index != hiddenEpisodes.lastIndex) GlassDivider()
                }
            }
        }
        PrimaryAction(
            stringResource(R.string.restore_hidden_upcoming, state.hiddenUpcoming.size),
            Icons.Filled.Refresh,
            Modifier.fillMaxWidth().padding(horizontal = com.cinetrack.ui.theme.Spacing.xl, vertical = com.cinetrack.ui.theme.Spacing.xs),
        ) { viewModel.restoreHiddenUpcoming() }
    }
    PrimaryAction(
        stringResource(R.string.export_calendar),
        Icons.Filled.CalendarMonth,
        Modifier.fillMaxWidth().padding(horizontal = com.cinetrack.ui.theme.Spacing.xl, vertical = com.cinetrack.ui.theme.Spacing.sm),
    ) { viewModel.exportCalendar(context) }
}

@Composable
private fun LanguageSettings(viewModel: CineTrackViewModel) {
    val currentLanguage = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        .substringBefore(',')
        .takeIf(String::isNotBlank)
        ?.substringBefore('-')
        ?: Locale.getDefault().language
    var language by rememberSaveable { mutableStateOf(currentLanguage) }
    SettingsSection(stringResource(R.string.language)) {
        ChoiceRow(stringResource(R.string.italian), language == "it") {
            language = "it"
            viewModel.setLanguage("it")
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("it"))
        }
        GlassDivider(); ChoiceRow(stringResource(R.string.english), language == "en") {
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
    SettingsSection(stringResource(R.string.rating_sources)) {
        labels.forEachIndexed { index, label ->
            ToggleRow(label, sourceKey(label) in state.ratingSources) { value -> viewModel.setRatingSource(label, value) }
            if (index != labels.lastIndex) GlassDivider()
        }
    }
}

@Composable
private fun ContentRegionSettings(state: AppUiState, viewModel: CineTrackViewModel) {
    var showRegions by remember { mutableStateOf(false) }
    val locale = java.util.Locale.getDefault()
    val summary = if (state.contentRegions.isEmpty()) stringResource(R.string.all_regions) else
        state.contentRegions.sorted().joinToString(", ") { java.util.Locale("", it).getDisplayCountry(locale) }
    SettingsSection(stringResource(R.string.content_regions)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { showRegions = true }
            .padding(com.cinetrack.ui.theme.Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(summary, color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.content_regions_picker_hint), color = TextMuted,
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
            }
            Icon(Icons.Filled.ChevronRight, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
        }
    }
    if (showRegions) ContentRegionsSheet(state.contentRegions, { showRegions = false }) {
        viewModel.setContentRegions(it)
        showRegions = false
    }
    Text(
        stringResource(R.string.content_regions_scope),
        color = TextMuted,
        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
        lineHeight = 15.sp,
        modifier = Modifier.padding(horizontal = com.cinetrack.ui.theme.Spacing.xxl, vertical = com.cinetrack.ui.theme.Spacing.xs),
    )
    if (state.hiddenDiscovery.isNotEmpty()) {
        PrimaryAction(
            pluralStringResource(R.plurals.restore_hidden_recommendations, state.hiddenDiscovery.size, state.hiddenDiscovery.size),
            Icons.Filled.Refresh,
            Modifier.fillMaxWidth().padding(horizontal = com.cinetrack.ui.theme.Spacing.xl, vertical = com.cinetrack.ui.theme.Spacing.sm),
        ) { viewModel.restoreHiddenDiscovery() }
    }
}

@Composable
private fun LogsSettings(viewModel: CineTrackViewModel) {
    val context = LocalContext.current
    val logs by viewModel.errorLogs.collectAsStateWithLifecycle()
    SettingsSection(stringResource(R.string.logs)) {
        if (logs.isEmpty()) {
            ValueRow(stringResource(R.string.logs), stringResource(R.string.no_errors_logged), success = true)
        } else {
            logs.takeLast(20).asReversed().forEachIndexed { index, entry ->
                Text(entry, color = TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, lineHeight = 14.sp, modifier = Modifier.padding(horizontal = com.cinetrack.ui.theme.Spacing.md, vertical = com.cinetrack.ui.theme.Spacing.sm))
                if (index != logs.takeLast(20).lastIndex) GlassDivider()
            }
        }
    }
    PrimaryAction(
        text = stringResource(R.string.export_logs),
        icon = Icons.Filled.Download,
        modifier = Modifier.fillMaxWidth().padding(horizontal = com.cinetrack.ui.theme.Spacing.xl, vertical = com.cinetrack.ui.theme.Spacing.md),
        enabled = logs.isNotEmpty(),
    ) { viewModel.exportLogs(context) }
}

@Composable
private fun ExportSettings(viewModel: CineTrackViewModel) {
    val context = LocalContext.current
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.restoreData(context, it) }
    }
    val labels = linkedMapOf(
        "library" to stringResource(R.string.library),
        "history" to stringResource(R.string.history),
        "progress" to stringResource(R.string.progress),
        "settings" to stringResource(R.string.settings),
    )
    var selected by remember { mutableStateOf(labels.keys.associateWith { true }) }
    SettingsSection(stringResource(R.string.export_data)) {
        labels.entries.forEachIndexed { index, (key, label) ->
            ToggleRow(label, selected[key] == true) { selected = selected + (key to it) }
            if (index != labels.size - 1) GlassDivider()
        }
    }
    PrimaryAction(
        text = stringResource(R.string.create_export),
        icon = Icons.Filled.Download,
        modifier = Modifier.fillMaxWidth().padding(horizontal = com.cinetrack.ui.theme.Spacing.xl, vertical = com.cinetrack.ui.theme.Spacing.md),
    ) { viewModel.exportData(context, selected.filterValues { it }.keys) }
    PrimaryAction(
        text = stringResource(R.string.restore_backup),
        icon = Icons.Filled.Refresh,
        modifier = Modifier.fillMaxWidth().padding(horizontal = com.cinetrack.ui.theme.Spacing.xl, vertical = com.cinetrack.ui.theme.Spacing.xs),
    ) { restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream")) }
    PrimaryAction(
        text = stringResource(R.string.restore_automatic_backup),
        icon = Icons.Filled.Refresh,
        modifier = Modifier.fillMaxWidth().padding(horizontal = com.cinetrack.ui.theme.Spacing.xl, vertical = com.cinetrack.ui.theme.Spacing.xs),
    ) { viewModel.restoreAutomaticBackup() }
    Text(
        stringResource(R.string.restore_backup_explanation),
        color = TextMuted,
        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
        lineHeight = 14.sp,
        modifier = Modifier.padding(horizontal = com.cinetrack.ui.theme.Spacing.xxl, vertical = com.cinetrack.ui.theme.Spacing.sm),
    )
}

@Composable
private fun AboutSettings(viewModel: CineTrackViewModel) {
    val context = LocalContext.current
    val updateState by viewModel.appUpdateState.collectAsStateWithLifecycle()
    val changelogState by viewModel.appChangelogState.collectAsStateWithLifecycle()
    var showChangelog by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (updateState is AppUpdateState.Idle) viewModel.checkForAppUpdate()
    }
    Column(Modifier.padding(horizontal = com.cinetrack.ui.theme.Spacing.xl, vertical = com.cinetrack.ui.theme.Spacing.xs).fillMaxWidth().glass().padding(com.cinetrack.ui.theme.Spacing.lg), horizontalAlignment = Alignment.CenterHorizontally) {
        BrandMark(76.dp)
        Spacer(Modifier.height(10.dp))
        Text("CineTrack", color = TextPrimary, fontWeight = FontWeight.ExtraBold, style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.version_label, BuildConfig.VERSION_NAME), color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ServiceLogo("TMDB", "https://www.themoviedb.org/")
            ServiceLogo("MDBList", "https://mdblist.com/")
            ServiceLogo("Simkl", "https://simkl.com/")
        }
        Spacer(Modifier.height(9.dp))
        Text("TMDB · MDBList · Simkl · Room", color = TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(18.dp))
        val statusText = when (val current = updateState) {
            AppUpdateState.Idle -> stringResource(R.string.updates_from_github)
            AppUpdateState.Checking -> stringResource(R.string.checking_for_updates)
            AppUpdateState.UpToDate -> stringResource(R.string.app_is_up_to_date)
            is AppUpdateState.Available -> stringResource(R.string.update_available, current.update.version)
            is AppUpdateState.Downloading -> stringResource(R.string.downloading_update, (current.progress * 100).toInt())
            is AppUpdateState.Error -> current.message
        }
        Text(statusText, color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(10.dp))
        val updateAvailable = updateState is AppUpdateState.Available
        val busy = updateState is AppUpdateState.Checking || updateState is AppUpdateState.Downloading
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PrimaryAction(
                text = if (updateAvailable) stringResource(R.string.download_and_install) else stringResource(R.string.check_for_updates),
                icon = if (updateAvailable) Icons.Filled.Download else Icons.Filled.Refresh,
                modifier = Modifier.weight(1f),
                enabled = !busy,
            ) {
                if (updateAvailable) viewModel.openAppUpdate(context) else viewModel.checkForAppUpdate()
            }
            Spacer(Modifier.width(8.dp))
            val changelogDescription = stringResource(R.string.changelog)
            IconButton(
                onClick = rememberUiAction {
                    showChangelog = true
                    viewModel.loadAppChangelog()
                },
                modifier = Modifier.size(48.dp).glassIcon(),
            ) {
                Icon(Icons.Filled.History, changelogDescription, tint = AccentLight, modifier = Modifier.size(20.dp))
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
        com.cinetrack.ui.components.SectionHeader(title)
        Spacer(Modifier.height(com.cinetrack.ui.theme.Spacing.md))
        Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Medium))) { content() }
    }
}

@Composable
private fun currentAppLanguageLabel(): String {
    val language = AppCompatDelegate.getApplicationLocales().toLanguageTags().substringBefore('-').substringBefore(',')
        .ifBlank { Locale.getDefault().language }
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
        Box(
            Modifier.offset(x = knobOffset, y = 3.dp).size(26.dp).clip(CircleShape)
                .background(if (checked) Color.White else TextMuted.copy(alpha = .55f)),
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
        Box(Modifier.size(9.dp).clip(CircleShape).background(if (configured) Success else TextMuted))
        Spacer(Modifier.width(6.dp))
        Icon(Icons.Filled.ChevronRight, null, tint = TextMuted, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun ServiceLogo(name: String, siteUrl: String? = null) {
    val uriHandler = LocalUriHandler.current
    val (logo, background, logoSize) = when (name.lowercase()) {
        "tmdb" -> Triple(R.drawable.ic_service_tmdb, com.cinetrack.ui.theme.SurfacePalette.OceanDeep, 29.dp)
        "mdblist" -> Triple(R.drawable.ic_service_mdblist, com.cinetrack.ui.theme.SurfacePalette.CoolText, 24.dp)
        else -> Triple(R.drawable.ic_service_simkl, com.cinetrack.ui.theme.SurfacePalette.WarmText, 24.dp)
    }
    Box(
        Modifier.size(38.dp).clip(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Small)).background(background)
            .border(.65.dp, com.cinetrack.ui.theme.GlassStrong, RoundedCornerShape(com.cinetrack.ui.theme.Radius.Small))
            .then(
                if (siteUrl != null) Modifier.clickable(onClick = rememberUiAction { runCatching { uriHandler.openUri(siteUrl) } })
                else Modifier,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(logo),
            contentDescription = name,
            modifier = Modifier.size(logoSize),
        )
    }
}

@Composable
internal fun ChoiceRow(title: String, selected: Boolean, description: String? = null, onClick: () -> Unit) {
    val clickAction = rememberUiAction(onClick)
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = clickAction).padding(horizontal = com.cinetrack.ui.theme.Spacing.lg, vertical = com.cinetrack.ui.theme.Spacing.md), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            if (!description.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(description, color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, lineHeight = 14.sp)
            }
        }
        Box(Modifier.size(20.dp).clip(CircleShape).background(if (selected) Accent else Color.Transparent).then(if (!selected) Modifier.border(1.dp, TextMuted, CircleShape) else Modifier), contentAlignment = Alignment.Center) {
            if (selected) Box(Modifier.size(7.dp).clip(CircleShape).background(Color.White))
        }
    }
}

