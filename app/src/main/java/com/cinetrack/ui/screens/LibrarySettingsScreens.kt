@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.cinetrack.ui.screens

import com.cinetrack.ui.components.tonalPill
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Place
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.Brush
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
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
import com.cinetrack.domain.MediaType
import com.cinetrack.ui.CineTrackViewModel
import com.cinetrack.ui.settings.SettingsViewModel
import com.cinetrack.ui.settings.BackupViewModel
import com.cinetrack.ui.settings.SyncSettingsViewModel
import com.cinetrack.ui.settings.TrackingSyncControls
import com.cinetrack.ui.settings.ContentSettingsViewModel
import com.cinetrack.ui.components.AdaptiveBackground
import com.cinetrack.ui.components.GlassBackButton
import com.cinetrack.ui.components.BrandMark
import com.cinetrack.ui.components.PageTitle
import com.cinetrack.ui.components.PrimaryAction
import com.cinetrack.ui.components.glass
import com.cinetrack.ui.components.rememberUiAction
import com.cinetrack.ui.theme.Accent
import com.cinetrack.ui.theme.AccentLight
import com.cinetrack.ui.theme.StatusDropped
import com.cinetrack.ui.theme.StatusPaused
import com.cinetrack.ui.theme.TextMuted
import com.cinetrack.ui.theme.TextPrimary
import com.cinetrack.ui.theme.TextSecondary
import java.util.Locale
import com.cinetrack.ui.theme.AccentPalette
import com.cinetrack.ui.theme.Spacing

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

internal const val FLOPPY_API_GUIDE_URL = "https://github.com/dannyvfilms/Floppy/wiki/7.-API-and-MCP-Server"
internal const val TMDB_API_KEY_URL = "https://www.themoviedb.org/settings/api"
internal const val MDBLIST_API_KEY_URL = "https://mdblist.com/preferences"
internal const val TRAKT_API_APPS_URL = "https://trakt.tv/oauth/applications"

private data class SettingsItem(val page: String, val title: String, val subtitle: String, val icon: ImageVector)

@Composable
fun SettingsScreen(state: AppUiState, onPage: (String) -> Unit, onCompactNav: (Boolean) -> Unit) {
    val listState = rememberLazyListState()
    NavCollapseEffect(listState, onCompactNav)
    val accentName = stringResource(
        when (AccentPalette.normalize(state.uiAccent)) {
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
            item { PageTitle(stringResource(R.string.settings), Modifier.padding(start = Spacing.xl, end = Spacing.xl, top = Spacing.lg, bottom = Spacing.md)) }
            // Services first: each one's state at a glance, one tap to set it up.
            item {
                Column(Modifier.padding(horizontal = Spacing.xl), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
internal fun StatusDot(active: Boolean) {
    Box(
        Modifier.size(9.dp).clip(CircleShape)
            .background(if (active) Color(0xFF5DCAA5) else TextMuted.copy(alpha = .45f)),
    )
}

@Composable
internal fun SettingsGroupLabel(label: String) {
    Text(
        label.uppercase(com.cinetrack.ui.currentLocale()),
        color = TextMuted,
        style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.6.sp,
        modifier = Modifier.padding(start = 4.dp, top = Spacing.sm),
    )
}

@Composable
private fun SettingsGroup(label: String, items: List<SettingsItem>, onPage: (String) -> Unit) {
    Column(Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.md)) {
        SettingsGroupLabel(label)
        Spacer(Modifier.height(Spacing.sm))
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
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = clickAction).padding(horizontal = Spacing.lg, vertical = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
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
    settingsViewModel: SettingsViewModel,
    contentViewModel: ContentSettingsViewModel,
    backupViewModel: BackupViewModel,
    syncViewModel: SyncSettingsViewModel,
    onBack: () -> Unit,
    onPage: (String) -> Unit,
) {
    if (page == SettingsPages.Providers) {
        StreamingProvidersScreen(state, contentViewModel, onBack)
        return
    }
    val context = LocalContext.current
    val syncControls = remember(viewModel) {
        TrackingSyncControls(viewModel.syncProgress, viewModel::sync, viewModel.mainTrackerSwap, viewModel::swapMainTracker, viewModel.floppyBootstrapProgress)
    }
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
        AdaptiveBackground { AppearancePage(title, state, settingsViewModel, onBack) }
        return
    }
    AdaptiveBackground {
        LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(bottom = 40.dp)) {
            item {
                if (brand != null) {
                    // Service pages open on a header in the service's own colours.
                    ServiceHero(brand, state, onBack)
                } else {
                    Row(Modifier.fillMaxWidth().padding(start = Spacing.xl, end = Spacing.xl, top = Spacing.lg, bottom = Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
                        GlassBackButton(onClick = onBack)
                        Spacer(Modifier.size(8.dp))
                        PageTitle(title, Modifier.weight(1f))
                    }
                }
            }
            item {
                when (page) {
                    SettingsPages.Streaming -> StreamingSettings(contentViewModel, onPage)
                    SettingsPages.Sync -> TrackingSettingsScreen(state, syncViewModel, syncControls, { syncViewModel.beginSimklLogin(context) })
                    SettingsPages.SyncOperations -> SyncOperationsScreen(syncViewModel, syncControls)
                    SettingsPages.Integrations -> IntegrationsSettings(state, onPage)
                    SettingsPages.ServiceSimkl -> SyncSettingsHost(state, syncViewModel, syncControls, { syncViewModel.beginSimklLogin(context) })
                    SettingsPages.ServiceFloppy -> FloppySettingsHost(state, syncViewModel, syncControls)
                    SettingsPages.ServiceTmdb -> Column {
                        CredentialBlock("TMDB", state.tmdbApiConfigured, contentViewModel::verifyAndSetTmdbApiKey)
                        MetadataSettings(contentViewModel)
                        ContentRegionSettings(state, viewModel, contentViewModel, onPage)
                    }
                    SettingsPages.ServiceMdblist -> Column {
                        CredentialBlock("MDBList", state.mdbListApiConfigured, contentViewModel::verifyAndSetMdbListApiKey)
                        RatingSettings(contentViewModel)
                    }
                    SettingsPages.ServiceTrakt -> Column {
                        CredentialBlock("Trakt", state.traktConfigured, contentViewModel::verifyAndSetTraktClientId)
                        SettingsSection(stringResource(R.string.section_what_it_adds)) {
                            KitRow(Icons.Filled.Schedule, stringResource(R.string.trakt_adds_times), stringResource(R.string.trakt_adds_times_sub))
                            KitRow(Icons.Filled.CalendarMonth, stringResource(R.string.trakt_adds_calendar), stringResource(R.string.trakt_adds_calendar_sub))
                        }
                    }
                    SettingsPages.Notifications -> NotificationSettings(state, viewModel, settingsViewModel)
                    SettingsPages.Language -> LanguageSettings(settingsViewModel)
                    SettingsPages.Ratings -> RatingSettings(contentViewModel)
                    SettingsPages.Export -> ExportSettings(backupViewModel)
                    SettingsPages.Logs -> LogsSettings(settingsViewModel)
                    else -> AboutSettings(settingsViewModel)
                }
            }
        }
    }
}

private const val SavedCredentialMask = "••••••••••••••••••••••••••••••••"

@Composable
internal fun ApiCredentialSettings(
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
        Column(Modifier.padding(Spacing.md)) {
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
                modifier = Modifier.padding(top = Spacing.xs),
            )
            Text(help, color = TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, lineHeight = 16.sp, modifier = Modifier.padding(top = Spacing.sm))
            Text(credentialLinkHelp, color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = Spacing.xs))
            TextButton(onClick = { runCatching { uriHandler.openUri(credentialUrl) } }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Link, contentDescription = credentialLink, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(Spacing.xs))
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
                    modifier = Modifier.padding(top = Spacing.sm),
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
            if (validating) Text(stringResource(R.string.api_credential_verifying, service), color = AccentLight, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = Spacing.sm))
        }
    }
}

@Composable
private fun MetadataSettings(viewModel: ContentSettingsViewModel) {
    val state by viewModel.content.collectAsStateWithLifecycle()
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
private fun NotificationSettings(state: AppUiState, viewModel: CineTrackViewModel, settingsViewModel: SettingsViewModel) {
    val notifications by settingsViewModel.notificationSettings.collectAsStateWithLifecycle()
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
        KitToggleRow(Icons.Filled.Tv, stringResource(R.string.new_episodes), stringResource(R.string.notif_episodes_sub), notifications.episodes) { settingsViewModel.setNotification("episodes", it) }
        KitToggleRow(Icons.Filled.Movie, stringResource(R.string.movie_releases), stringResource(R.string.notif_movies_sub), notifications.movies) { settingsViewModel.setNotification("movies", it) }
        KitToggleRow(Icons.Filled.SyncProblem, stringResource(R.string.sync_problems), stringResource(R.string.notif_sync_sub), notifications.sync) { settingsViewModel.setNotification("sync", it) }
        KitToggleRow(Icons.Filled.Bedtime, stringResource(R.string.quiet_hours), stringResource(R.string.quiet_hours_sub), notifications.quietHoursEnabled) { settingsViewModel.setQuietHours(it) }
    }
    if (notifications.quietHoursEnabled) {
        val quietHourOptions = listOf(22 to 7, 23 to 8, 0 to 8)
        SettingsSection(stringResource(R.string.quiet_hours_schedule)) {
            quietHourOptions.forEach { (start, end) ->
                ChoiceRow(
                    "%02d:00–%02d:00".format(start, end),
                    notifications.quietHoursStart == start && notifications.quietHoursEnd == end,
                ) { settingsViewModel.setQuietHours(true, start, end) }
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
                val showTitle = state.findMedia(MediaType.TV, episode.showId)?.title.orEmpty()
                Row(
                    Modifier.fillMaxWidth().padding(start = Spacing.lg, end = Spacing.sm, top = Spacing.sm, bottom = Spacing.sm),
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
private fun LanguageSettings(viewModel: SettingsViewModel) {
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
private fun RatingSettings(viewModel: ContentSettingsViewModel) {
    val state by viewModel.content.collectAsStateWithLifecycle()
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
private fun ContentRegionSettings(appState: AppUiState, viewModel: CineTrackViewModel, contentViewModel: ContentSettingsViewModel, onPage: (String) -> Unit = {}) {
    val state by contentViewModel.content.collectAsStateWithLifecycle()
    var showRegions by remember { mutableStateOf(false) }
    val locale = com.cinetrack.ui.currentLocale()
    val summary = if (state.contentRegions.isEmpty()) stringResource(R.string.all_regions) else
        state.contentRegions.sorted().joinToString(", ") { java.util.Locale("", it).getDisplayCountry(locale) }
    SettingsSection(stringResource(R.string.section_discover_streaming)) {
        KitRow(Icons.Filled.Public, stringResource(R.string.content_regions), summary, onClick = { showRegions = true })
        KitRow(Icons.Filled.Tv, stringResource(R.string.preferred_providers), stringResource(R.string.providers_selected_count, state.preferredProviders.size), onClick = { onPage(SettingsPages.Streaming) })
        if (appState.hiddenDiscovery.isNotEmpty()) {
            KitRow(
                Icons.Filled.Visibility,
                pluralStringResource(R.plurals.restore_hidden_recommendations, appState.hiddenDiscovery.size, appState.hiddenDiscovery.size),
                onClick = viewModel::restoreHiddenDiscovery,
            )
        }
    }
    KitFootnote(stringResource(R.string.content_regions_scope))
    if (showRegions) ContentRegionsSheet(state.contentRegions, { showRegions = false }) {
        contentViewModel.setContentRegions(it)
        showRegions = false
    }
}

@Composable
private fun LogsSettings(viewModel: SettingsViewModel) {
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
        com.cinetrack.domain.LogSeverity.ERROR -> stringResource(R.string.logs_error) to StatusDropped
        com.cinetrack.domain.LogSeverity.WARNING -> stringResource(R.string.logs_warning) to StatusPaused
        com.cinetrack.domain.LogSeverity.INFO -> stringResource(R.string.logs_info) to com.cinetrack.ui.theme.Info
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
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
            Spacer(Modifier.width(Spacing.sm))
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
private fun ExportSettings(viewModel: BackupViewModel) {
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
private fun AboutSettings(viewModel: SettingsViewModel) {
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
                .padding(Spacing.lg),
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
                    com.cinetrack.ui.components.SkeletonLines(Modifier.fillMaxWidth().padding(Spacing.lg))
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
