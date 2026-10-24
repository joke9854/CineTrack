@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.cinetrack.ui

import com.cinetrack.ui.discover.discoverUiState
import com.cinetrack.ui.library.libraryUiState
import com.cinetrack.ui.progress.progressUiState
import com.cinetrack.ui.settings.settingsUiState
import com.cinetrack.ui.components.popupBackdropBlur
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.SlowMotionVideo
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.SlowMotionVideo
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Composable
import dev.chrisbanes.haze.HazeState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cinetrack.CineTrackApplication
import com.cinetrack.SimklAuthCallback
import com.cinetrack.R
import androidx.compose.material.icons.filled.Delete
import com.cinetrack.data.sync.ReleaseNotifier
import com.cinetrack.domain.MediaType
import com.cinetrack.ui.components.BottomNavItem
import com.cinetrack.ui.components.BrandMark
import com.cinetrack.ui.components.LiquidBottomNav
import com.cinetrack.ui.components.glass
import com.cinetrack.ui.theme.AccentLight
import com.cinetrack.ui.theme.applyUiAccent
import com.cinetrack.ui.screens.DetailScreen
import com.cinetrack.ui.screens.DiscoverFiltersScreen
import com.cinetrack.ui.screens.DiscoverListScreen
import com.cinetrack.ui.screens.DiscoverScreen
import com.cinetrack.ui.screens.EpisodeDetailScreen
import com.cinetrack.ui.screens.LibraryScreen
import com.cinetrack.ui.screens.IntroductionScreen
import com.cinetrack.ui.screens.ProgressScreen
import com.cinetrack.ui.screens.SearchScreen
import com.cinetrack.ui.screens.ActorSheet
import com.cinetrack.ui.screens.SettingsDetailScreen
import com.cinetrack.ui.screens.SettingsScreen
import com.cinetrack.data.sync.floppy.FloppyBootstrapWorkManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.cinetrack.ui.theme.LocalCardAppearance
import com.cinetrack.ui.theme.Motion
import com.cinetrack.ui.theme.Radius
import com.cinetrack.ui.theme.Spacing

private object Routes {
    const val Discover = "discover"
    const val Progress = "progress"
    const val Library = "library"
    const val Settings = "settings"
    const val Search = "search/{scope}"
    const val DiscoverList = "discover-list/{railId}"
    const val DiscoverFilters = "discover-filters"
    const val Detail = "detail/{type}/{id}"
    const val Episode = "episode/{showId}/{season}/{number}"
    const val SettingsDetail = "settings-detail/{page}"
}

/** A destination that exposes its enter/exit scope to shared elements inside it. */
private fun androidx.navigation.NavGraphBuilder.screen(
    route: String,
    arguments: List<androidx.navigation.NamedNavArgument> = emptyList(),
    onPredictiveBack: (() -> Unit)? = null,
    content: @Composable androidx.compose.animation.AnimatedContentScope.(androidx.navigation.NavBackStackEntry) -> Unit,
) = composable(route, arguments) { entry ->
    androidx.compose.runtime.CompositionLocalProvider(com.cinetrack.ui.components.LocalNavVisibility provides this) {
        if (onPredictiveBack == null) content(entry)
        else com.cinetrack.ui.components.PredictiveBackPage(onPredictiveBack) { content(entry) }
    }
}

private fun isDetailRoute(route: String?): Boolean = route == Routes.Detail || route == Routes.Episode

// Into and out of a detail page the pages swap in ~90 ms, so both are drawn only briefly;
// the no-op slide keeps the transition alive while the poster or cover finishes flying.
private val DetailEnter = fadeIn(tween(90)) + androidx.compose.animation.slideInVertically(tween(300)) { 0 }
// The leaving page is gone after its 90 ms fade, so it cannot swallow the next tap.
private val DetailExit = fadeOut(tween(90))
// Coming back, the poster waits for the rebuilt page before it flies, so the transition
// lasts that much longer.
private val DetailPopEnter = fadeIn(tween(90)) +
    androidx.compose.animation.slideInVertically(tween(300 + com.cinetrack.ui.components.POSTER_RETURN_HOLD_MS)) { 0 }
// The closing page fades out while its poster flies home, not at once.
private val DetailPopExit = fadeOut(tween(300, delayMillis = com.cinetrack.ui.components.POSTER_RETURN_HOLD_MS / 2))


private fun mainPageIndex(route: String?): Int? = when (route) {
    Routes.Discover -> 0
    Routes.Progress -> 1
    Routes.Library -> 2
    Routes.Settings -> 3
    else -> null
}

@Composable
fun CineTrackApp(
    authCallback: MutableStateFlow<SimklAuthCallback?>,
    navigationRequest: MutableStateFlow<String?>,
) {
    val context = LocalContext.current
    val application = context.applicationContext as CineTrackApplication
    val navHazeState = remember { HazeState() }
    val navBlurEnabled = remember(context) {
        android.os.Build.VERSION.SDK_INT >= 31 &&
            !(context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager).isLowRamDevice
    }
    // Liquid glass bottom bar and sheets refract the page capture (Android 13+); older devices blur it.
    val pageGlass = remember(navBlurEnabled) { navBlurEnabled && com.cinetrack.ui.components.liquidGlassEligible(context) }
    val viewModel: CineTrackViewModel = viewModel(
        factory = CineTrackViewModel.Factory(
            application.container.syncHost,
            application.container.libraryRepository,
            application.container.mediaRepository,
            application.container.discoveryRepository,
            application.container.peopleRepository,
            application.container.watchProviderRepository,
            application.container.syncCoordinator,
            application.container.floppyBootstrapWorkManager,
            application.container.detailEpisodeCache,
            application.container.backupRepository,
            application.container.appState,
            application.container.accounts,
            application.container.progressCache,
            application.container.preferences,
        ),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    // What the settings pages still run on CineTrackViewModel (sync mutex, AppUiState and Progress).
    val syncControls = remember(viewModel) {
        com.cinetrack.ui.settings.TrackingSyncControls(viewModel.syncProgress, viewModel::sync, viewModel.mainTrackerSwap, viewModel::swapMainTracker, viewModel.floppyBootstrapProgress)
    }
    val hiddenContentControls = remember(viewModel) {
        com.cinetrack.ui.settings.HiddenContentControls(
            onExcludeSpecials = viewModel::setExcludeSpecials,
            onRestoreHiddenUpcomingEpisode = viewModel::restoreHiddenUpcomingEpisode,
            onRestoreHiddenUpcoming = viewModel::restoreHiddenUpcoming,
            onRestoreHiddenDiscovery = viewModel::restoreHiddenDiscovery,
            onExportCalendar = viewModel::exportCalendar,
        )
    }
    val searchViewModel: com.cinetrack.ui.search.SearchViewModel = viewModel(
        factory = com.cinetrack.ui.search.SearchViewModel.Factory(
            application.container.discoveryRepository,
            application.container.peopleRepository,
            application.container.preferences,
        ),
    )
    val searchResults by searchViewModel.searchResults.collectAsStateWithLifecycle()
    val searchLoading by searchViewModel.searchLoading.collectAsStateWithLifecycle()
    val personSearchResults by searchViewModel.personSearchResults.collectAsStateWithLifecycle()
    val personSearchLoading by searchViewModel.personSearchLoading.collectAsStateWithLifecycle()
    val searchHistory by searchViewModel.searchHistory.collectAsStateWithLifecycle()
    val discoverViewModel: com.cinetrack.ui.discover.DiscoverViewModel = viewModel(
        factory = com.cinetrack.ui.discover.DiscoverViewModel.Factory(
            application.container.discoveryRepository,
            application.container.watchProviderRepository,
        ),
    )
    LaunchedEffect(discoverViewModel) { discoverViewModel.errors.collect(viewModel::showError) }
    LaunchedEffect(discoverViewModel) { viewModel.libraryStatusChanges.collect { (media, status) -> discoverViewModel.applyStatus(media, status) } }
    val hideDiscoveryItem: (com.cinetrack.domain.MediaCard) -> Unit = remember(viewModel, searchViewModel, discoverViewModel) {
        { media ->
            viewModel.hideDiscoveryItem(media)
            searchViewModel.hideResult(media)
            discoverViewModel.hideResult(media)
        }
    }
    val discoverFilterResults by discoverViewModel.discoverFilterResults.collectAsStateWithLifecycle()
    val discoverFiltersLoading by discoverViewModel.discoverFiltersLoading.collectAsStateWithLifecycle()
    val mosaicResults by discoverViewModel.mosaicResults.collectAsStateWithLifecycle()
    val mosaicCanLoadMore by discoverViewModel.mosaicCanLoadMore.collectAsStateWithLifecycle()
    val settingsViewModel: com.cinetrack.ui.settings.SettingsViewModel = viewModel(
        factory = com.cinetrack.ui.settings.SettingsViewModel.Factory(application.container.preferences),
    )
    val appUpdateState by settingsViewModel.appUpdateState.collectAsStateWithLifecycle()
    val appearance = settingsViewModel.appearance.collectAsStateWithLifecycle().value
    LaunchedEffect(settingsViewModel) { settingsViewModel.errors.collect(viewModel::showError) }
    val detailViewModel: com.cinetrack.ui.detail.DetailViewModel = viewModel(
        factory = com.cinetrack.ui.detail.DetailViewModel.Factory(
            com.cinetrack.data.repository.RepositoryDetailStore(
                application.container.mediaRepository,
                application.container.peopleRepository,
                application.container.syncHost.renumberedShows,
            ),
            application.container.detailEpisodeCache,
        ),
    )
    val contentSettingsViewModel: com.cinetrack.ui.settings.ContentSettingsViewModel = viewModel(
        factory = com.cinetrack.ui.settings.ContentSettingsViewModel.Factory(
            com.cinetrack.data.repository.RepositoryContentSettings(application.container.preferences, application.container.metadataSettings, application.container.watchProviderRepository),
        ),
    )
    LaunchedEffect(contentSettingsViewModel) {
        contentSettingsViewModel.changes.collect { change ->
            detailViewModel.onContentChanged(change)
            viewModel.onContentChanged(change)
        }
    }
    val progressViewModel: com.cinetrack.ui.progress.ProgressViewModel = viewModel(
        factory = com.cinetrack.ui.progress.ProgressViewModel.Factory(
            com.cinetrack.data.repository.RepositoryProgressStore(context, application.container.preferences, application.container.peopleRepository),
        ),
    )
    val releaseReminders by progressViewModel.releaseReminders.collectAsStateWithLifecycle()
    val viewingInsights by progressViewModel.viewingInsights.collectAsStateWithLifecycle()
    val libraryViewModel: com.cinetrack.ui.library.LibraryViewModel = viewModel(
        factory = com.cinetrack.ui.library.LibraryViewModel.Factory(
            com.cinetrack.data.repository.RepositoryLibraryArtwork(application.container.libraryArtworkRefreshManager, application.container.preferences),
        ),
    )
    val libraryArtwork by libraryViewModel.artwork.collectAsStateWithLifecycle()
    LaunchedEffect(libraryViewModel) { libraryViewModel.errors.collect(viewModel::showError) }
    val backupViewModel: com.cinetrack.ui.settings.BackupViewModel = viewModel(
        factory = com.cinetrack.ui.settings.BackupViewModel.Factory(com.cinetrack.data.repository.RepositoryBackupStore(application.container.backupRepository, application.container.progressCache)),
    )
    LaunchedEffect(backupViewModel) { backupViewModel.errors.collect(viewModel::showError) }
    LaunchedEffect(backupViewModel) { backupViewModel.restored.collect { viewModel.onBackupRestored() } }
    val backupProgress by backupViewModel.backupProgress.collectAsStateWithLifecycle()
    val syncSettingsViewModel: com.cinetrack.ui.settings.SyncSettingsViewModel = viewModel(
        factory = com.cinetrack.ui.settings.SyncSettingsViewModel.Factory(com.cinetrack.data.repository.RepositorySyncSettings(application.container.preferences, application.container.accounts, application.container.syncConflicts, application.container.syncOperationRepository, application.container.syncCoordinator)),
    )
    LaunchedEffect(syncSettingsViewModel) { syncSettingsViewModel.changes.collect(viewModel::onSyncSettingsChanged) }
    val notificationPermission = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }
    val streamingProviders by discoverViewModel.streamingProviders.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route.orEmpty()
    var compactNav by remember { mutableStateOf(false) }
    var minimumLaunchElapsed by remember { mutableStateOf(false) }
    val callback by authCallback.collectAsStateWithLifecycle()
    val requestedRoute by navigationRequest.collectAsStateWithLifecycle()

    SideEffect { applyUiAccent(state.uiAccent) }

    LaunchedEffect(state.calendar, state.notificationEpisodes, state.notificationMovies, state.metadataTimezone) {
        if (state.calendar.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                ReleaseNotifier.scheduleUpcoming(context, state, application.container.preferences)
            }
        }
    }

    LaunchedEffect(route) { compactNav = false }
    LaunchedEffect(Unit) {
        // Keep the launch layer long enough to read as an intentional transition,
        // while Room and DataStore publish the first usable snapshot underneath.
        delay(620)
        minimumLaunchElapsed = true
    }

    LaunchedEffect(callback) {
        callback?.let {
            if (it.floppy) syncSettingsViewModel.completeFloppySignIn(it.code, it.state, it.error, it.issuer)
            else viewModel.completeSimklLogin(it.code, it.state, it.error, it.issuer)
            authCallback.value = null
        }
    }
    if (state.simklConnected && state.simklFirstSyncPending) {
        com.cinetrack.ui.screens.SimklFirstSyncDialog(
            onMerge = { viewModel.chooseSimklFirstSync(merge = true) },
            onReplace = { viewModel.chooseSimklFirstSync(merge = false) },
            onDisconnect = syncSettingsViewModel::disconnectSimkl,
        )
    }
    LaunchedEffect(requestedRoute) {
        requestedRoute?.let { requested ->
            val destination = if (requested == "sync") Routes.Progress else requested
            // A link to a main tab switches tabs exactly like the nav bar. Pushing it on top of
            // the current tab saved it into that tab's history, so tapping that tab later
            // reopened the linked screen (Discover showed Library).
            val tab = destination in setOf(Routes.Discover, Routes.Progress, Routes.Library, Routes.Settings)
            runCatching {
                navController.navigate(destination) {
                    if (tab) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        restoreState = true
                    }
                    launchSingleTop = true
                }
            }
            if (requested == "sync") viewModel.sync()
            navigationRequest.value = null
        }
    }
    LaunchedEffect(state.error) {
        if (!state.error.isNullOrBlank()) {
            delay(6_500)
            viewModel.dismissError()
        }
    }

    val navItems = listOf(
        BottomNavItem(Routes.Discover, stringResource(R.string.discover), Icons.Outlined.Explore, Icons.Filled.Explore),
        BottomNavItem(Routes.Progress, stringResource(R.string.progress), Icons.Outlined.SlowMotionVideo, Icons.Filled.SlowMotionVideo),
        BottomNavItem(Routes.Library, stringResource(R.string.library), Icons.Outlined.BookmarkBorder, Icons.Filled.Bookmark),
        BottomNavItem(Routes.Settings, stringResource(R.string.settings), Icons.Outlined.Settings, Icons.Filled.Settings),
    )
    val selectedIndex = navItems.indexOfFirst { it.route == route }.coerceAtLeast(0)
    val showBottomNav = route in navItems.map(BottomNavItem::route)
    // Coming back from a detail page, the bar (and the page recording its glass needs)
    // returns once the transition has played, instead of during it.
    val navShown by androidx.compose.runtime.produceState(showBottomNav, showBottomNav) {
        if (showBottomNav) kotlinx.coroutines.delay(230)
        value = showBottomNav
    }
    // Key actions answer with a haptic: a status change confirms, a tab switch ticks.
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val applyStatus: (List<com.cinetrack.domain.MediaCard>, com.cinetrack.domain.LibraryStatus) -> Unit = { items, status ->
        // One confirm per change, however many titles it covers.
        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.Confirm)
        items.forEach { viewModel.setStatus(it, status) }
    }
    // Removing a title also deletes its watch history here and on Simkl/Floppy, so every
    // removal path (context menu, bulk edit, sheets) asks first.
    var pendingRemoval by remember { mutableStateOf<List<com.cinetrack.domain.MediaCard>>(emptyList()) }
    val setStatuses: (List<com.cinetrack.domain.MediaCard>, com.cinetrack.domain.LibraryStatus) -> Unit = { items, status ->
        if (status == com.cinetrack.domain.LibraryStatus.NONE && items.any { it.status != com.cinetrack.domain.LibraryStatus.NONE || it.watched }) {
            pendingRemoval = items
        } else applyStatus(items, status)
    }
    val setStatus: (com.cinetrack.domain.MediaCard, com.cinetrack.domain.LibraryStatus) -> Unit = { media, status -> setStatuses(listOf(media), status) }
    val setStatusConfirmed: (com.cinetrack.domain.MediaCard, com.cinetrack.domain.LibraryStatus) -> Unit = { media, status -> applyStatus(listOf(media), status) }
    if (pendingRemoval.isNotEmpty()) {
        val items = pendingRemoval
        com.cinetrack.ui.components.ConfirmPrompt(
            title = stringResource(R.string.remove_titles_title),
            message = if (items.size == 1) stringResource(R.string.remove_title_message, items.first().title)
                else androidx.compose.ui.res.pluralStringResource(R.plurals.remove_titles_message, items.size, items.size),
            confirmLabel = stringResource(R.string.remove_titles_confirm),
            icon = androidx.compose.material.icons.Icons.Filled.Delete,
            onDismiss = { pendingRemoval = emptyList() },
        ) {
            applyStatus(items, com.cinetrack.domain.LibraryStatus.NONE)
            pendingRemoval = emptyList()
        }
    }
    val openMedia: (com.cinetrack.domain.MediaCard) -> Unit = { media ->
        detailViewModel.rememberOpened(media)
        navController.navigate("detail/${media.type.name}/${media.id}")
    }

    val popupBlur = remember { com.cinetrack.ui.components.PopupBlurController() }
    Box(Modifier.fillMaxSize().imePadding().popupBackdropBlur(popupBlur)) {
        com.cinetrack.ui.components.FloatingGlassHost(if (navBlurEnabled) navHazeState else null, pageGlass, navShown) { captureModifier ->
        androidx.compose.runtime.CompositionLocalProvider(
            LocalCardAppearance provides com.cinetrack.domain.CardAppearance(state.heroLayout, state.posterFormat, state.posterSize, appearance.castPhotoShape, appearance.posterTitles),
            com.cinetrack.ui.components.LocalPopupBlur provides popupBlur,
        ) {
        // Shared elements (the tapped poster flying into the detail page) need one scope
        // around every destination.
        androidx.compose.animation.SharedTransitionLayout {
        androidx.compose.runtime.CompositionLocalProvider(com.cinetrack.ui.components.LocalSharedTransition provides this) {
        NavHost(
            modifier = captureModifier,
            navController = navController,
            startDestination = Routes.Discover,
            enterTransition = {
                val from = mainPageIndex(initialState.destination.route)
                val to = mainPageIndex(targetState.destination.route)
                when {
                    from != null && to != null -> Motion.tabEnter(forward = to > from)
                    isDetailRoute(targetState.destination.route) -> DetailEnter
                    else -> Motion.PushEnter
                }
            },
            exitTransition = {
                val from = mainPageIndex(initialState.destination.route)
                val to = mainPageIndex(targetState.destination.route)
                when {
                    from != null && to != null -> Motion.tabExit(forward = to > from)
                    isDetailRoute(targetState.destination.route) -> DetailExit
                    else -> Motion.PushExit
                }
            },
            popEnterTransition = {
                val from = mainPageIndex(initialState.destination.route)
                val to = mainPageIndex(targetState.destination.route)
                when {
                    from != null && to != null -> Motion.tabEnter(forward = to > from)
                    isDetailRoute(initialState.destination.route) -> DetailPopEnter
                    else -> Motion.PopEnter
                }
            },
            popExitTransition = {
                val from = mainPageIndex(initialState.destination.route)
                val to = mainPageIndex(targetState.destination.route)
                when {
                    from != null && to != null -> Motion.tabExit(forward = to > from)
                    isDetailRoute(initialState.destination.route) -> DetailPopExit
                    else -> Motion.PopExit
                }
            },
        ) {
            screen(Routes.Discover) {
                DiscoverScreen(
                    state = state.discoverUiState(),
                    onRefresh = { if (viewModel.refresh()) discoverViewModel.resetBrowse() },
                    loadTagline = detailViewModel::loadTagline,
                    onSearch = { navController.navigate("search/discover") },
                    onFilters = { navController.navigate(Routes.DiscoverFilters) },
                    onSeeAll = { navController.navigate("discover-list/$it") },
                    onMedia = openMedia,
                    onStatus = setStatus,
                    onNotInterested = hideDiscoveryItem,
                    onOpenTmdbSettings = { navController.navigate("settings-detail/${com.cinetrack.ui.screens.SettingsPages.ServiceTmdb}") },
                    onCompactNav = { compactNav = it },
                    mosaicResults = mosaicResults,
                    onMosaicQuery = discoverViewModel::loadMosaic,
                    mosaicCanLoadMore = mosaicCanLoadMore,
                    onMosaicMore = discoverViewModel::loadMoreMosaic,
                )
            }
            screen(Routes.Progress) {
                ProgressScreen(
                    state = state.progressUiState(),
                    syncProgress = viewModel.syncProgress,
                    syncRunning = viewModel.syncRunning,
                    onSearch = { navController.navigate("search/progress") },
                    onSync = viewModel::sync,
                    onSyncOperations = { navController.navigate("settings-detail/${com.cinetrack.ui.screens.SettingsPages.SyncOperations}") },
                    onSyncSettings = { navController.navigate("settings-detail/${com.cinetrack.ui.screens.SettingsPages.ServiceSimkl}") },
                    onMedia = openMedia,
                    onWatched = viewModel::markPlaybackWatched,
                    onEpisode = { episode -> navController.navigate("episode/${episode.showId}/${episode.season}/${episode.number}") },
                    onHideUpcoming = viewModel::hideUpcomingEpisode,
                    viewingInsights = viewingInsights,
                    onLoadViewingInsights = { progressViewModel.loadViewingInsights(state.history) },
                    onCompactNav = { compactNav = it },
                    releaseReminders = releaseReminders,
                    isReminderOn = { item -> progressViewModel.isReleaseReminderOn(releaseReminders, item.media, item.timestamp, item.season, item.episodeNumber, com.cinetrack.ui.progress.notifiesByDefault(state, item.media, item.season, item.episodeNumber)) },
                    onReminder = { item, enable ->
                        if (enable && android.os.Build.VERSION.SDK_INT >= 33 &&
                            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
                        ) notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        progressViewModel.setReleaseReminder(item.media, item.timestamp, item.season, item.episodeNumber, item.episodeTitle, enable, com.cinetrack.ui.progress.notifiesByDefault(state, item.media, item.season, item.episodeNumber))
                    },
                )
            }
            screen(Routes.Library) {
                LibraryScreen(state = state.libraryUiState(), artwork = libraryArtwork, onSearch = { navController.navigate("search/library") }, onMedia = openMedia, onStatus = setStatus, onBulkStatus = setStatuses, onRefreshArtwork = { libraryViewModel.refreshArtwork(state.tmdbApiConfigured) }, onCompactNav = { compactNav = it })
            }
            screen(Routes.Settings) {
                SettingsScreen(
                    state = state.settingsUiState(),
                    onPage = { navController.navigate("settings-detail/$it") },
                    onCompactNav = { compactNav = it },
                )
            }
            screen(
                Routes.Search,
                arguments = listOf(navArgument("scope") { type = NavType.StringType }),
            ) { entry ->
                val scope = entry.arguments?.getString("scope").orEmpty()
                var selectedSearchPerson by remember { mutableStateOf<com.cinetrack.domain.PersonCard?>(null) }
                val scopedItems = when (scope) {
                    "progress" -> state.playbackTv.map { it.media } + state.playbackMovies.map { it.media } + state.history.map { it.media } + state.calendar.map { it.media }
                    "library" -> state.rails[com.cinetrack.domain.RailIds.LIBRARY].orEmpty()
                    else -> emptyList()
                }.distinctBy(com.cinetrack.domain.MediaCard::stableKey)
                SearchScreen(
                    results = searchResults,
                    peopleResults = personSearchResults,
                    mediaSearchLoading = searchLoading,
                    peopleSearchLoading = personSearchLoading,
                    sourceItems = scopedItems,
                    remoteSearch = scope == "discover",
                    history = searchHistory,
                    onQuery = searchViewModel::search,
                    onPeopleQuery = searchViewModel::searchPeople,
                    onSubmitQuery = searchViewModel::rememberSearchQuery,
                    onRemoveHistory = searchViewModel::removeSearchHistory,
                    onClearHistory = searchViewModel::clearSearchHistory,
                    onLeave = searchViewModel::clearSearch,
                    onBack = { navController.popBackStack() },
                    onMedia = openMedia,
                    onPerson = { selectedSearchPerson = it },
                )
                selectedSearchPerson?.let { person ->
                    ActorSheet(
                        person = person,
                        detailViewModel = detailViewModel,
                        onDismiss = { selectedSearchPerson = null },
                        onMedia = { media ->
                            selectedSearchPerson = null
                            openMedia(media)
                        },
                    )
                }
            }
            screen(
                Routes.DiscoverList,
                arguments = listOf(navArgument("railId") { type = NavType.StringType }),
            ) { entry ->
                DiscoverListScreen(
                    railId = entry.arguments?.getString("railId").orEmpty(),
                    viewModel = discoverViewModel,
                    state = state.discoverUiState(),
                    onBack = { navController.popBackStack() },
                    onMedia = openMedia,
                    onStatus = setStatus,
                    onNotInterested = hideDiscoveryItem,
                )
            }
            screen(Routes.DiscoverFilters) {
                DiscoverFiltersScreen(
                    results = discoverFilterResults,
                    loading = discoverFiltersLoading,
                    providers = streamingProviders,
                    regionKey = "${state.providerRegion}:${state.metadataRegion}:${state.contentRegions.sorted().joinToString(",")}:${state.preferredProviders.sorted().joinToString(",")}",
                    onApply = discoverViewModel::applyDiscoverFilters,
                    onLoadProviders = { discoverViewModel.loadStreamingProviders(it, state.preferredProviders) },
                    onBack = { navController.popBackStack() },
                    onMedia = openMedia,
                    onStatus = setStatus,
                    onNotInterested = hideDiscoveryItem,
                )
            }
            screen(
                Routes.Detail,
                onPredictiveBack = { com.cinetrack.ui.components.SharedPosterSource.goingBack(); navController.popBackStack() },
                arguments = listOf(
                    navArgument("type") { type = NavType.StringType },
                    navArgument("id") { type = NavType.IntType },
                ),
            ) { entry ->
                val type = runCatching { MediaType.valueOf(entry.arguments?.getString("type").orEmpty()) }.getOrDefault(MediaType.MOVIE)
                val id = entry.arguments?.getInt("id") ?: 0
                val cachedMedia = state.findMedia(type, id)
                    ?: searchResults.firstOrNull { it.type == type && it.id == id }
                    ?: discoverFilterResults.firstOrNull { it.type == type && it.id == id }
                    ?: mosaicResults?.second?.firstOrNull { it.type == type && it.id == id }
                    ?: detailViewModel.openedMedia(type, id)
                var resolvedMedia by remember(type, id) { mutableStateOf(cachedMedia) }
                val selectedSeason by entry.savedStateHandle
                    .getStateFlow("selectedSeason", -1)
                    .collectAsStateWithLifecycle()
                val selectedEpisode by entry.savedStateHandle
                    .getStateFlow("selectedEpisode", -1)
                    .collectAsStateWithLifecycle()
                LaunchedEffect(type, id, cachedMedia?.status, cachedMedia?.watched, cachedMedia?.libraryUpdatedAt) {
                    resolvedMedia = cachedMedia ?: detailViewModel.loadMedia(type, id) ?: resolvedMedia
                }
                DetailScreen(
                    media = resolvedMedia,
                    people = state.people,
                    // Placeholder Simkl schedule rows are useful for Progress,
                    // but the full season catalogue shown here is TMDB-backed.
                    episodes = state.episodes.filter { it.showId == id && it.id > 0 },
                    history = state.history.filter { it.media.type == type && it.media.id == id },
                    recommended = (
                        state.rails[com.cinetrack.domain.RailIds.RECOMMENDED].orEmpty() +
                            state.rails[if (type == MediaType.TV) com.cinetrack.domain.RailIds.TRENDING_TV else com.cinetrack.domain.RailIds.TRENDING_MOVIES].orEmpty()
                        ).distinctBy(com.cinetrack.domain.MediaCard::stableKey),
                    viewModel = viewModel,
                    detailViewModel = detailViewModel,
                    progressViewModel = progressViewModel,
                    onBack = { com.cinetrack.ui.components.SharedPosterSource.goingBack(); navController.popBackStack() },
                    onStatus = setStatusConfirmed,
                    onMedia = openMedia,
                    onEpisode = { episode ->
                        entry.savedStateHandle["selectedSeason"] = episode.season
                        entry.savedStateHandle["selectedEpisode"] = episode.number
                        navController.navigate("episode/${episode.showId}/${episode.season}/${episode.number}")
                    },
                    initialSeason = selectedSeason.takeIf { it > 0 },
                    initialEpisode = selectedEpisode.takeIf { it > 0 },
                )
            }
            screen(
                Routes.Episode,
                onPredictiveBack = { com.cinetrack.ui.components.SharedPosterSource.goingBack(); navController.popBackStack() },
                arguments = listOf(
                    navArgument("showId") { type = NavType.IntType },
                    navArgument("season") { type = NavType.IntType },
                    navArgument("number") { type = NavType.IntType },
                ),
            ) { entry ->
                val showId = entry.arguments?.getInt("showId") ?: 0
                val season = entry.arguments?.getInt("season") ?: 1
                val number = entry.arguments?.getInt("number") ?: 1
                val cachedShow = state.findMedia(MediaType.TV, showId)
                var resolvedShow by remember(showId) { mutableStateOf(cachedShow) }
                LaunchedEffect(showId, cachedShow?.stableKey) {
                    resolvedShow = cachedShow ?: detailViewModel.loadMedia(MediaType.TV, showId) ?: resolvedShow
                }
                EpisodeDetailScreen(
                    show = resolvedShow ?: com.cinetrack.domain.MediaCard(id = showId, type = MediaType.TV, title = ""),
                    episode = state.episodes.firstOrNull { it.showId == showId && it.season == season && it.number == number },
                    requestedSeason = season,
                    requestedNumber = number,
                    allEpisodes = state.episodes.filter { it.showId == showId },
                    people = state.people,
                    viewModel = viewModel,
                    detailViewModel = detailViewModel,
                    progressViewModel = progressViewModel,
                    onBack = { com.cinetrack.ui.components.SharedPosterSource.goingBack(); navController.popBackStack() },
                    onSeries = { navController.navigate("detail/${MediaType.TV.name}/$showId") },
                    onMedia = openMedia,
                    onWatched = viewModel::setEpisodeWatched,
                )
            }
            screen(
                Routes.SettingsDetail,
                arguments = listOf(navArgument("page") { type = NavType.StringType }),
            ) { entry ->
                SettingsDetailScreen(
                    page = entry.arguments?.getString("page").orEmpty(),
                    state = state,
                    syncControls = syncControls,
                    contentControls = hiddenContentControls,
                    settingsViewModel = settingsViewModel,
                    contentViewModel = contentSettingsViewModel,
                    backupViewModel = backupViewModel,
                    syncViewModel = syncSettingsViewModel,
                    onBack = { navController.popBackStack() },
                    onPage = { navController.navigate("settings-detail/$it") },
                )
            }
        }
        }
        }
        }

        }
        AnimatedVisibility(
            visible = navShown,
            enter = fadeIn(Motion.enter(Motion.Medium)) + androidx.compose.animation.slideInVertically(Motion.enter(Motion.Long)) { it / 2 },
            exit = fadeOut(Motion.exit(100)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            LiquidBottomNav(
                hazeState = if (navBlurEnabled) navHazeState else null,
                glassBackdrop = if (pageGlass) navHazeState else null,
                items = navItems,
                selectedIndex = selectedIndex,
                compact = true,
                onSelected = { index ->
                    if (index != selectedIndex) haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.SegmentTick)
                    val destination = navItems[index].route
                    navController.navigate(destination) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                modifier = Modifier.navigationBarsPadding().padding(bottom = Spacing.lg),
            )
        }
        // Debug and preview builds: live Haze settings for every glass surface.
        com.cinetrack.ui.components.GlassLabHost()

        // Backup creation and restore report their stage in a pill at the top.
        var shownBackup by remember { mutableStateOf<com.cinetrack.domain.BackupProgress?>(null) }
        if (backupProgress != null) shownBackup = backupProgress
        AnimatedVisibility(
            visible = backupProgress != null,
            enter = Motion.PillEnter,
            exit = Motion.PillExit,
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = Spacing.sm),
        ) {
            shownBackup?.let { com.cinetrack.ui.components.BackupProgressPill(it) }
        }
        // Library artwork refresh and an available update report in the same pill spot.
        com.cinetrack.ui.screens.FloppyAddressMoveDialog(syncSettingsViewModel)
        LibraryArtworkPill(libraryArtwork, onDismiss = libraryViewModel::dismissProgress, modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = Spacing.sm))
        // Once per launch, after the start-up animation: offer a newer release.
        var updateDismissed by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
        LaunchedEffect(minimumLaunchElapsed) {
            if (minimumLaunchElapsed && appUpdateState is com.cinetrack.data.update.AppUpdateState.Idle) settingsViewModel.checkForAppUpdate()
        }
        val offeredUpdate = when (val current = appUpdateState) {
            is com.cinetrack.data.update.AppUpdateState.Available -> current.update
            is com.cinetrack.data.update.AppUpdateState.Downloading -> current.update
            else -> null
        }
        var shownUpdate by remember { mutableStateOf<com.cinetrack.data.update.AppUpdateInfo?>(null) }
        if (offeredUpdate != null) shownUpdate = offeredUpdate
        AnimatedVisibility(
            visible = offeredUpdate != null && offeredUpdate.version != updateDismissed && backupProgress == null && !libraryArtwork.progressVisible,
            enter = Motion.PillEnter,
            exit = Motion.PillExit,
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = Spacing.sm),
        ) {
            shownUpdate?.let { update ->
                val downloading = appUpdateState as? com.cinetrack.data.update.AppUpdateState.Downloading
                com.cinetrack.ui.components.StatusPill(
                    title = stringResource(com.cinetrack.R.string.update_available, update.version),
                    detail = if (downloading != null) stringResource(com.cinetrack.R.string.downloading_update, (downloading.progress * 100).toInt()) else stringResource(com.cinetrack.R.string.update_pill_detail),
                    tone = if (downloading != null) com.cinetrack.ui.components.PillTone.RUNNING else com.cinetrack.ui.components.PillTone.INFO,
                    fraction = downloading?.progress,
                    actionLabel = if (downloading == null) stringResource(com.cinetrack.R.string.update_install) else null,
                    onAction = { settingsViewModel.openAppUpdate(context) },
                    onDismiss = { updateDismissed = update.version },
                )
            }
        }
        AnimatedVisibility(
            visible = minimumLaunchElapsed && !state.error.isNullOrBlank(),
            enter = Motion.PillEnter,
            exit = Motion.PillExit,
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        ) {
            Row(
                Modifier.fillMaxWidth().glass(RoundedCornerShape(Radius.Medium)).background(Color(0xFF5A2028).copy(alpha = .42f)).padding(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Info, null, tint = Color(0xFFFF8C96), modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(10.dp))
                Text(state.error.orEmpty(), color = Color.White, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            }
        }

        AnimatedVisibility(
            visible = !minimumLaunchElapsed || state.loading,
            enter = fadeIn(tween(Motion.Short)),
            exit = fadeOut(tween(Motion.Long, easing = FastOutSlowInEasing)),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color(0xFF11151D), Color(0xFF080A0F))),
                ),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    BrandMark(76.dp)
                    Spacer(Modifier.height(18.dp))
                    Text("CineTrack", color = Color.White, style = androidx.compose.material3.MaterialTheme.typography.displaySmall, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.loading_library), color = AccentLight.copy(alpha = .78f), style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                }
            }
        }

        if (minimumLaunchElapsed && !state.loading && !state.introductionCompleted) {
            IntroductionScreen(
                onOpenSettings = {
                    viewModel.completeIntroduction()
                    navController.navigate("settings-detail/${com.cinetrack.ui.screens.SettingsPages.Integrations}") {
                        launchSingleTop = true
                    }
                },
                onFinish = viewModel::completeIntroduction,
            )
        }
    }
}

/** The library artwork refresh as a pill; it closes itself a few seconds after finishing. */
@Composable
private fun LibraryArtworkPill(artwork: com.cinetrack.ui.library.LibraryArtworkState, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val progress = artwork.progress
    val visible = artwork.progressVisible && progress != null
    var shown by remember { mutableStateOf(progress) }
    if (progress != null) shown = progress
    val finished = progress?.stage in setOf(
        com.cinetrack.domain.LibraryArtworkRefreshStage.COMPLETE,
        com.cinetrack.domain.LibraryArtworkRefreshStage.COMPLETE_WITH_ERRORS,
        com.cinetrack.domain.LibraryArtworkRefreshStage.FAILED,
    )
    LaunchedEffect(visible, finished) {
        if (visible && finished) {
            kotlinx.coroutines.delay(4_000)
            onDismiss()
        }
    }
    AnimatedVisibility(
        visible = visible,
        enter = Motion.PillEnter,
        exit = Motion.PillExit,
        modifier = modifier,
    ) {
        val current = shown ?: return@AnimatedVisibility
        val stage = current.stage
        val title = when (stage) {
            com.cinetrack.domain.LibraryArtworkRefreshStage.COMPLETE ->
                stringResource(if (current.changed > 0) com.cinetrack.R.string.artwork_refresh_done else com.cinetrack.R.string.artwork_refresh_up_to_date)
            com.cinetrack.domain.LibraryArtworkRefreshStage.COMPLETE_WITH_ERRORS -> stringResource(com.cinetrack.R.string.artwork_refresh_errors, current.failed)
            com.cinetrack.domain.LibraryArtworkRefreshStage.FAILED -> stringResource(com.cinetrack.R.string.artwork_refresh_failed)
            else -> stringResource(com.cinetrack.R.string.artwork_refresh_running)
        }
        val count = if (current.total > 0) stringResource(com.cinetrack.R.string.artwork_refresh_count, current.processed, current.total) else null
        com.cinetrack.ui.components.StatusPill(
            title = title,
            detail = listOfNotNull(count, current.currentTitle?.takeIf { !finished && it.isNotBlank() }).joinToString(" · ").ifBlank { null },
            tone = when (stage) {
                com.cinetrack.domain.LibraryArtworkRefreshStage.COMPLETE -> com.cinetrack.ui.components.PillTone.DONE
                com.cinetrack.domain.LibraryArtworkRefreshStage.COMPLETE_WITH_ERRORS, com.cinetrack.domain.LibraryArtworkRefreshStage.FAILED -> com.cinetrack.ui.components.PillTone.FAILED
                else -> com.cinetrack.ui.components.PillTone.RUNNING
            },
            fraction = if (current.total > 0) (current.processed.toFloat() / current.total).coerceIn(0f, 1f) else null,
            onDismiss = onDismiss,
        )
    }
}
