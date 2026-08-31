@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.cinetrack.ui.screens

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.cinetrack.BuildConfig
import com.cinetrack.R
import com.cinetrack.domain.AppUiState
import com.cinetrack.domain.LibraryStatus
import com.cinetrack.domain.MediaCard
import com.cinetrack.domain.MediaType
import com.cinetrack.domain.RailIds
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
import com.cinetrack.ui.components.rememberLightHapticAction
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
    const val Sync = "sync"
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
    AdaptiveBackground(artworkUrl = items.firstOrNull()?.posterUrl) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                PageTitle(stringResource(R.string.library), Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    IconButton(onClick = rememberLightHapticAction(onSearch), modifier = Modifier.size(40.dp).glassIcon()) {
                        Icon(Icons.Filled.Search, stringResource(R.string.accessibility_search), tint = TextSecondary, modifier = Modifier.size(21.dp))
                    }
                    IconButton(onClick = rememberLightHapticAction { showOrderSheet = true }, modifier = Modifier.size(40.dp).glassIcon()) {
                        Icon(Icons.Filled.FilterList, stringResource(R.string.filters), tint = TextSecondary, modifier = Modifier.size(21.dp))
                    }
                }
            }
            Row(
                Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(MediaType.TV, MediaType.MOVIE).forEach { item ->
                    val selected = item == type
                    Row(
                        Modifier.weight(1f).height(40.dp).glass(RoundedCornerShape(11.dp))
                            .background(if (selected) Accent.copy(alpha = .25f) else Color.Transparent)
                            .clickable { type = item },
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(if (item == MediaType.TV) Icons.Filled.Tv else Icons.Filled.Movie, null, tint = if (selected) AccentLight else TextMuted, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        val count = state.rails[RailIds.LIBRARY].orEmpty().count { it.type == item }
                        Text("${if (item == MediaType.TV) stringResource(R.string.tv_shows) else stringResource(R.string.movies)} · $count", color = if (selected) TextPrimary else TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filterChoices) { (value, label, color) ->
                    val selected = status == value
                    val count = if (value == null) typeItems.size else typeItems.count { it.status == value }
                    Row(
                        modifier = Modifier.glass(RoundedCornerShape(999.dp))
                            .background(if (selected) Accent.copy(alpha = .25f) else Color.Transparent)
                            .clickable { status = value }.padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        if (value != null) Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                        Text("$label · $count", color = if (selected) TextPrimary else TextSecondary, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            val counts = listOf(
                typeItems.count { it.status == LibraryStatus.WATCHING },
                typeItems.count { it.status == LibraryStatus.PLAN_TO_WATCH },
                typeItems.count { it.status == LibraryStatus.PAUSED },
                typeItems.count { it.status == LibraryStatus.COMPLETED },
                typeItems.count { it.status == LibraryStatus.DROPPED },
            )
            if (typeItems.isNotEmpty()) Row(Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(8.dp).clip(CircleShape)) {
                val colors = listOf(
                    libraryStatusColor(LibraryStatus.WATCHING),
                    libraryStatusColor(LibraryStatus.PLAN_TO_WATCH),
                    libraryStatusColor(LibraryStatus.PAUSED),
                    libraryStatusColor(LibraryStatus.COMPLETED),
                    libraryStatusColor(LibraryStatus.DROPPED),
                )
                counts.forEachIndexed { index, count -> if (count > 0) Box(Modifier.weight(count.toFloat()).fillMaxSize().background(colors[index])) }
            }
            if (items.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.AutoAwesome, null, tint = AccentLight, modifier = Modifier.size(40.dp))
                        Text(stringResource(R.string.empty_library), color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(stringResource(R.string.empty_library_description), color = TextMuted, fontSize = 12.sp)
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    state = gridState,
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 112.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(items, key = MediaCard::stableKey) { media ->
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            val visibleProgress = progressByKey[media.stableKey]
                                ?: if (media.type == MediaType.TV && media.status == LibraryStatus.WATCHING) .03f else null
                            MediaPoster(media, width = maxWidth, progress = visibleProgress, onStatus = { onStatus(media, it) }, onClick = { onMedia(media) })
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
}

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
        Column(Modifier.padding(horizontal = 18.dp)) {
            Text(stringResource(R.string.sort_by), color = AccentLight, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = .8.sp)
            Spacer(Modifier.height(12.dp))
            LibraryOrder.entries.forEach { option ->
                val active = selected == option
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp).glass(RoundedCornerShape(16.dp))
                        .background(if (active) Accent.copy(alpha = .18f) else Color.Transparent)
                        .clickable {
                            selected = option
                            if (option == LibraryOrder.RECENTLY_WATCHED || option == LibraryOrder.RECENTLY_ADDED) {
                                ascending = false
                            }
                        }.padding(horizontal = 15.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        when (option) {
                            LibraryOrder.TITLE -> stringResource(R.string.order_title)
                            LibraryOrder.RATING -> stringResource(R.string.order_rating)
                            LibraryOrder.YEAR -> stringResource(R.string.order_year)
                            LibraryOrder.RECENTLY_WATCHED -> stringResource(R.string.order_recently_watched)
                            LibraryOrder.RECENTLY_ADDED -> stringResource(R.string.order_recently_added)
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
            PrimaryAction(stringResource(R.string.apply), Icons.Filled.FilterList, Modifier.fillMaxWidth()) { onApply(selected, ascending) }
        }
    }
}

private data class SettingsItem(val page: String, val title: String, val subtitle: String, val icon: ImageVector)

@Composable
fun SettingsScreen(state: AppUiState, onPage: (String) -> Unit, onCompactNav: (Boolean) -> Unit) {
    val listState = rememberLazyListState()
    NavCollapseEffect(listState, onCompactNav)
    val items = listOf(
        SettingsItem(SettingsPages.Integrations, stringResource(R.string.integrations), stringResource(R.string.integrations_summary), Icons.Filled.Link),
        SettingsItem(SettingsPages.Appearance, stringResource(R.string.appearance), stringResource(R.string.appearance_summary), Icons.Filled.Palette),
        SettingsItem(SettingsPages.Notifications, stringResource(R.string.notifications), stringResource(R.string.notifications_summary), Icons.Filled.Notifications),
        SettingsItem(SettingsPages.Language, stringResource(R.string.language), stringResource(R.string.italian), Icons.Filled.Language),
        SettingsItem(SettingsPages.Export, stringResource(R.string.export_data), stringResource(R.string.export_data_summary), Icons.Filled.Download),
        SettingsItem(SettingsPages.Logs, stringResource(R.string.logs), stringResource(R.string.logs_summary), Icons.Filled.BugReport),
        SettingsItem(SettingsPages.About, stringResource(R.string.about_app), stringResource(R.string.version_label, BuildConfig.VERSION_NAME), Icons.Filled.Info),
    )
    AdaptiveBackground {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(bottom = 112.dp),
        ) {
            item { PageTitle(stringResource(R.string.settings), Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 16.dp)) }
            item { SettingsGroup("SERVICES", listOf(items[0]), onPage) }
            item { SettingsGroup("PREFERENCES", items.slice(1..3), onPage) }
            item { SettingsGroup("DATA & DIAGNOSTICS", items.slice(4..5), onPage) }
            item { SettingsGroup("MORE", listOf(items[6]), onPage) }
        }
    }
}

@Composable
private fun SettingsGroup(label: String, items: List<SettingsItem>, onPage: (String) -> Unit) {
    Column(Modifier.padding(top = 14.dp)) {
        Text(
            label,
            color = TextMuted,
            fontSize = 11.5.sp,
            letterSpacing = .45.sp,
            fontWeight = FontWeight.ExtraBold,
            style = androidx.compose.material3.MaterialTheme.typography.labelLarge.copy(
                shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = .72f), androidx.compose.ui.geometry.Offset(0f, 2f), 5f),
            ),
            modifier = Modifier.padding(start = 20.dp, bottom = 8.dp),
        )
        Column(Modifier.padding(horizontal = 20.dp).fillMaxWidth().glass(RoundedCornerShape(16.dp))) {
            items.forEachIndexed { index, item ->
                SettingsRow(item, onPage)
                if (index != items.lastIndex) GlassDivider()
            }
        }
    }
}

@Composable
private fun SettingsRow(item: SettingsItem, onPage: (String) -> Unit) {
    val hapticClick = rememberLightHapticAction { onPage(item.page) }
    Row(Modifier.fillMaxWidth().clickable(onClick = hapticClick).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(Accent.copy(alpha = .18f)), contentAlignment = Alignment.Center) {
            Icon(item.icon, null, tint = AccentLight, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(item.subtitle, color = TextMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    val context = LocalContext.current
    val title = when (page) {
        SettingsPages.Sync -> stringResource(R.string.synchronization)
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
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    GlassBackButton(onClick = onBack)
                    Spacer(Modifier.size(8.dp))
                    PageTitle(title, Modifier.weight(1f))
                }
            }
            item {
                if (page != SettingsPages.About) SettingsDetailHero(page, title)
                when (page) {
                    SettingsPages.Sync -> SyncSettings(state, viewModel, { viewModel.beginSimklLogin(context) })
                    SettingsPages.Integrations -> IntegrationsSettings(state, onPage)
                    SettingsPages.ServiceSimkl -> SyncSettings(state, viewModel, { viewModel.beginSimklLogin(context) })
                    SettingsPages.ServiceTmdb -> Column {
                        ApiCredentialSettings("TMDB", state.tmdbApiConfigured, viewModel::setTmdbApiKey)
                        MetadataSettings(state, viewModel)
                        ContentRegionSettings(state, viewModel)
                    }
                    SettingsPages.ServiceMdblist -> Column {
                        ApiCredentialSettings("MDBList", state.mdbListApiConfigured, viewModel::setMdbListApiKey)
                        RatingSettings(state, viewModel)
                    }
                    SettingsPages.Appearance -> AppearanceSettings(state, viewModel)
                    SettingsPages.Notifications -> NotificationSettings(viewModel)
                    SettingsPages.Language -> LanguageSettings(viewModel)
                    SettingsPages.Ratings -> RatingSettings(state, viewModel)
                    SettingsPages.Export -> ExportSettings(viewModel)
                    SettingsPages.Logs -> LogsSettings(viewModel)
                    else -> AboutSettings()
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
    Row(Modifier.padding(horizontal = 20.dp, vertical = 4.dp).fillMaxWidth().glass(RoundedCornerShape(16.dp)).padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
        if (serviceName != null) ServiceLogo(serviceName)
        else Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(Accent.copy(alpha = .20f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = AccentLight, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            Text(description, color = TextMuted, fontSize = 10.5.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SyncSettings(state: AppUiState, viewModel: CineTrackViewModel, onConnect: () -> Unit) {
    var background by rememberSaveable(state.backgroundSync) { mutableStateOf(state.backgroundSync) }
    var wifiOnly by rememberSaveable(state.wifiOnly) { mutableStateOf(state.wifiOnly) }
    SettingsSection(stringResource(R.string.status)) {
        ValueRow(stringResource(R.string.synchronization), if (state.simklConnected) stringResource(R.string.connected) else stringResource(R.string.not_connected), state.simklConnected)
    }
    SettingsSection(stringResource(R.string.synchronization)) {
        ToggleRow(stringResource(R.string.background_sync), background) { background = it; viewModel.setBackgroundSync(it) }
        GlassDivider()
        ToggleRow(stringResource(R.string.wifi_only), wifiOnly) { wifiOnly = it; viewModel.setWifiOnly(it) }
    }
    state.error?.takeIf(String::isNotBlank)?.let { error ->
        Text(
            error,
            color = androidx.compose.material3.MaterialTheme.colorScheme.error,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp).fillMaxWidth().glass(RoundedCornerShape(14.dp)).padding(12.dp),
        )
    }
    state.sync.message?.takeIf { state.sync.stage == com.cinetrack.domain.SyncStage.ERROR && it.isNotBlank() }?.let { error ->
        Text(
            error,
            color = androidx.compose.material3.MaterialTheme.colorScheme.error,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp).fillMaxWidth().glass(RoundedCornerShape(14.dp)).padding(12.dp),
        )
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        PrimaryAction(
            text = if (state.simklConnected) stringResource(R.string.sync_now) else stringResource(R.string.connect_simkl),
            icon = if (state.simklConnected) Icons.Filled.Refresh else Icons.Filled.Link,
            modifier = Modifier.weight(1f),
            onClick = if (state.simklConnected) viewModel::sync else onConnect,
        )
        if (state.simklConnected) Button(onClick = viewModel::disconnectSimkl, modifier = Modifier.height(46.dp), shape = RoundedCornerShape(999.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = .08f))) { Text(stringResource(R.string.disconnect), fontSize = 11.sp) }
    }
}

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
    val choices = listOf(
        Triple("watching", stringResource(R.string.watching), StatusWatching),
        Triple("planned", stringResource(R.string.plan_to_watch), StatusPlanned),
        Triple("paused", stringResource(R.string.paused), StatusPaused),
        Triple("completed", stringResource(R.string.completed), Success),
        Triple("dropped", stringResource(R.string.dropped), StatusDropped),
    )
    SettingsSection(stringResource(R.string.main_ui_color)) {
        choices.forEachIndexed { index, (key, label, color) ->
            AccentChoiceRow(label, color, state.uiAccent == key) { viewModel.setUiAccent(key) }
            if (index != choices.lastIndex) GlassDivider()
        }
    }
}

@Composable
private fun AccentChoiceRow(title: String, color: Color, selected: Boolean, onClick: () -> Unit) {
    val hapticClick = rememberLightHapticAction(onClick)
    Row(Modifier.fillMaxWidth().clickable(onClick = hapticClick).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(22.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
            if (selected) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
        }
        Spacer(Modifier.width(11.dp))
        Text(title, color = TextPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (selected) Text(stringResource(R.string.active), color = color, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun ApiCredentialSettings(service: String, configured: Boolean, onSave: (String?) -> Unit) {
    var value by remember(service) { mutableStateOf("") }
    SettingsSection(stringResource(R.string.api_credential)) {
        Column(Modifier.padding(12.dp)) {
            Text(
                if (configured) stringResource(R.string.api_credential_configured, service) else stringResource(R.string.api_credential_missing, service),
                color = if (configured) Success else TextMuted,
                fontSize = 11.5.sp,
            )
            Spacer(Modifier.height(9.dp))
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                placeholder = { Text(stringResource(R.string.api_credential_hint, service), fontSize = 12.sp) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Accent,
                    unfocusedBorderColor = Color.White.copy(alpha = .16f),
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                ),
            )
            Spacer(Modifier.height(9.dp))
            PrimaryAction(
                stringResource(if (value.isBlank()) R.string.use_build_credential else R.string.save),
                Icons.Filled.Save,
                Modifier.fillMaxWidth(),
            ) {
                onSave(value.takeIf(String::isNotBlank))
                value = ""
            }
        }
    }
}

@Composable
private fun MetadataSettings(state: AppUiState, viewModel: CineTrackViewModel) {
    val languages = listOf(
        "system" to stringResource(R.string.system_default),
        "it-IT" to "Italiano", "en-US" to "English", "fr-FR" to "Français",
        "de-DE" to "Deutsch", "es-ES" to "Español", "ja-JP" to "日本語", "ko-KR" to "한국어",
    )
    val regions = listOf(
        "system" to stringResource(R.string.system_default),
        "IT" to "Italia", "US" to "United States", "GB" to "United Kingdom",
        "FR" to "France", "DE" to "Deutschland", "ES" to "España", "JP" to "Japan", "KR" to "South Korea",
    )
    val timezones = listOf(
        "system" to stringResource(R.string.system_default),
        "Europe/Rome" to "Europe/Rome", "UTC" to "UTC", "America/New_York" to "America/New_York", "Asia/Tokyo" to "Asia/Tokyo",
    )
    SettingsSection(stringResource(R.string.metadata_language)) {
        languages.forEachIndexed { index, (value, label) ->
            ChoiceRow(label, state.metadataLanguage == value) { viewModel.setMetadataLanguage(value) }
            if (index != languages.lastIndex) GlassDivider()
        }
    }
    SettingsSection(stringResource(R.string.metadata_region)) {
        regions.forEachIndexed { index, (value, label) ->
            ChoiceRow(label, state.metadataRegion == value) { viewModel.setMetadataRegion(value) }
            if (index != regions.lastIndex) GlassDivider()
        }
    }
    SettingsSection(stringResource(R.string.metadata_timezone)) {
        timezones.forEachIndexed { index, (value, label) ->
            ChoiceRow(label, state.metadataTimezone == value) { viewModel.setMetadataTimezone(value) }
            if (index != timezones.lastIndex) GlassDivider()
        }
    }
}

@Composable
private fun NotificationSettings(viewModel: CineTrackViewModel) {
    var episodes by rememberSaveable { mutableStateOf(true) }
    var movies by rememberSaveable { mutableStateOf(true) }
    var sync by rememberSaveable { mutableStateOf(true) }
    SettingsSection(stringResource(R.string.notifications)) {
        ToggleRow(stringResource(R.string.new_episodes), episodes) { episodes = it; viewModel.setNotification("episodes", it) }
        GlassDivider(); ToggleRow(stringResource(R.string.movie_releases), movies) { movies = it; viewModel.setNotification("movies", it) }
        GlassDivider(); ToggleRow(stringResource(R.string.sync_problems), sync) { sync = it; viewModel.setNotification("sync", it) }
        GlassDivider(); ValueRow(stringResource(R.string.quiet_hours), "23:00–08:00")
    }
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
    val labels = listOf("IMDb", "TMDB", "Metacritic", "Rotten Tomatoes", "Letterboxd")
    fun sourceKey(label: String) = when (label) {
        "Rotten Tomatoes" -> "tomatoes"
        else -> label.lowercase()
    }
    var enabled by remember(state.ratingSources) { mutableStateOf(labels.associateWith { sourceKey(it) in state.ratingSources }) }
    SettingsSection(stringResource(R.string.rating_sources)) {
        labels.forEachIndexed { index, label ->
            ToggleRow(label, enabled[label] == true) { value -> enabled = enabled + (label to value); viewModel.setRatingSource(label, value) }
            if (index != labels.lastIndex) GlassDivider()
        }
    }
}

@Composable
private fun ContentRegionSettings(state: AppUiState, viewModel: CineTrackViewModel) {
    val choices = listOf(
        "IT" to "Italia", "US" to "United States", "GB" to "United Kingdom",
        "CA" to "Canada", "AU" to "Australia", "FR" to "France",
        "DE" to "Deutschland", "ES" to "España", "JP" to "Japan", "KR" to "South Korea",
    )
    var selected by remember(state.contentRegions) { mutableStateOf(state.contentRegions) }
    SettingsSection(stringResource(R.string.content_regions)) {
        ChoiceRow(stringResource(R.string.all_regions), selected.isEmpty()) {
            selected = emptySet()
            viewModel.setContentRegions(selected)
        }
        choices.forEach { (code, label) ->
            GlassDivider()
            ToggleRow("$label · $code", code in selected) { checked ->
                selected = if (checked) selected + code else selected - code
                viewModel.setContentRegions(selected)
            }
        }
    }
    Text(
        stringResource(R.string.content_regions_scope),
        color = TextMuted,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        modifier = Modifier.padding(horizontal = 26.dp, vertical = 4.dp),
    )
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
                Text(entry, color = TextSecondary, fontSize = 10.5.sp, lineHeight = 14.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
                if (index != logs.takeLast(20).lastIndex) GlassDivider()
            }
        }
    }
    PrimaryAction(
        text = stringResource(R.string.export_logs),
        icon = Icons.Filled.Download,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        enabled = logs.isNotEmpty(),
    ) { viewModel.exportLogs(context) }
}

@Composable
private fun ExportSettings(viewModel: CineTrackViewModel) {
    val context = LocalContext.current
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
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
    ) { viewModel.exportData(context, selected.filterValues { it }.keys) }
}

@Composable
private fun AboutSettings() {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp).fillMaxWidth().glass().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        BrandMark(76.dp)
        Spacer(Modifier.height(10.dp))
        Text("CineTrack", color = TextPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
        Text(stringResource(R.string.version_label, BuildConfig.VERSION_NAME), color = TextMuted, fontSize = 12.sp)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ServiceLogo("TMDB")
            ServiceLogo("MDBList")
            ServiceLogo("Simkl")
        }
        Spacer(Modifier.height(9.dp))
        Text("TMDB · MDBList · Simkl · Room", color = TextSecondary, fontSize = 12.sp)
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 7.dp)) {
        Text(
            title.uppercase(),
            color = AccentLight,
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            style = androidx.compose.material3.MaterialTheme.typography.labelMedium.copy(
                shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = .78f), androidx.compose.ui.geometry.Offset(0f, 2f), 5f),
            ),
            modifier = Modifier.padding(start = 6.dp, bottom = 7.dp),
        )
        Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp)), content = { content() })
    }
}

@Composable
private fun ToggleRow(title: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    val hapticToggle = rememberLightHapticAction { onChecked(!checked) }
    Row(Modifier.fillMaxWidth().clickable(onClick = hapticToggle).padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = TextPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        CompactSwitch(checked) { onChecked(!checked) }
    }
}

@Composable
private fun CompactSwitch(checked: Boolean, onClick: () -> Unit) {
    val knobOffset by animateDpAsState(if (checked) 22.dp else 3.dp, label = "switchKnob")
    val hapticClick = rememberLightHapticAction(onClick)
    Box(
        Modifier.width(52.dp).height(32.dp).clip(CircleShape)
            .background(if (checked) Accent else Color.White.copy(alpha = .12f))
            .border(.7.dp, AccentLight.copy(alpha = .24f), CircleShape)
            .clickable(onClick = hapticClick),
    ) {
        Box(
            Modifier.offset(x = knobOffset, y = 3.dp).size(26.dp).clip(CircleShape)
                .background(if (checked) Color.White else TextMuted.copy(alpha = .55f)),
        )
    }
}

@Composable
private fun ValueRow(title: String, value: String, success: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = TextPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text(value, color = if (success) Success else TextMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ProviderRow(name: String, subtitle: String, configured: Boolean, onClick: () -> Unit) {
    val hapticClick = rememberLightHapticAction(onClick)
    Row(Modifier.fillMaxWidth().clickable(onClick = hapticClick).padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        ServiceLogo(name)
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) { Text(name, color = TextPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.Bold); Text(subtitle, color = TextMuted, fontSize = 10.5.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        Box(Modifier.size(9.dp).clip(CircleShape).background(if (configured) Success else TextMuted))
        Spacer(Modifier.width(6.dp))
        Icon(Icons.Filled.ChevronRight, null, tint = TextMuted, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun ServiceLogo(name: String) {
    val (logo, background, logoSize) = when (name.lowercase()) {
        "tmdb" -> Triple(R.drawable.ic_service_tmdb, Color(0xFF0D253F), 29.dp)
        "mdblist" -> Triple(R.drawable.ic_service_mdblist, Color(0xFFF4F7FB), 24.dp)
        else -> Triple(R.drawable.ic_service_simkl, Color(0xFFF4F4F5), 24.dp)
    }
    Box(
        Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(background)
            .border(.65.dp, Color.White.copy(alpha = .18f), RoundedCornerShape(12.dp)),
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
private fun ChoiceRow(title: String, selected: Boolean, onClick: () -> Unit) {
    val hapticClick = rememberLightHapticAction(onClick)
    Row(Modifier.fillMaxWidth().clickable(onClick = hapticClick).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = TextPrimary, fontSize = 13.5.sp, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
        Box(Modifier.size(20.dp).clip(CircleShape).background(if (selected) Accent else Color.Transparent).then(if (!selected) Modifier.border(1.dp, TextMuted, CircleShape) else Modifier), contentAlignment = Alignment.Center) {
            if (selected) Box(Modifier.size(7.dp).clip(CircleShape).background(Color.White))
        }
    }
}
