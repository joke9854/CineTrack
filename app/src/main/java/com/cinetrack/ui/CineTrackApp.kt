package com.cinetrack.ui

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
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Composable
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
import com.cinetrack.ui.screens.SettingsDetailScreen
import com.cinetrack.ui.screens.SettingsScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.delay

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
    val viewModel: CineTrackViewModel = viewModel(factory = CineTrackViewModel.Factory(application.container.repository))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val discoverFilterResults by viewModel.discoverFilterResults.collectAsStateWithLifecycle()
    val discoverFiltersLoading by viewModel.discoverFiltersLoading.collectAsStateWithLifecycle()
    val streamingProviders by viewModel.streamingProviders.collectAsStateWithLifecycle()
    val viewingInsights by viewModel.viewingInsights.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route.orEmpty()
    var compactNav by remember { mutableStateOf(false) }
    var minimumLaunchElapsed by remember { mutableStateOf(false) }
    val callback by authCallback.collectAsStateWithLifecycle()
    val requestedRoute by navigationRequest.collectAsStateWithLifecycle()

    SideEffect { applyUiAccent(state.uiAccent) }

    LaunchedEffect(route) { compactNav = false }
    LaunchedEffect(Unit) {
        // Keep the launch layer long enough to read as an intentional transition,
        // while Room and DataStore publish the first usable snapshot underneath.
        delay(620)
        minimumLaunchElapsed = true
    }

    LaunchedEffect(callback) {
        callback?.let {
            viewModel.completeSimklLogin(it.code, it.state, it.error)
            authCallback.value = null
        }
    }
    LaunchedEffect(requestedRoute) {
        requestedRoute?.let { destination ->
            if (destination == "sync") {
                navController.navigate(Routes.Progress) { launchSingleTop = true }
                viewModel.sync()
            } else {
                navController.navigate(destination) { launchSingleTop = true }
            }
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
        BottomNavItem(Routes.Progress, stringResource(R.string.progress), Icons.Outlined.QueryStats, Icons.Filled.QueryStats),
        BottomNavItem(Routes.Library, stringResource(R.string.library), Icons.Outlined.BookmarkBorder, Icons.Filled.Bookmark),
        BottomNavItem(Routes.Settings, stringResource(R.string.settings), Icons.Outlined.Settings, Icons.Filled.Settings),
    )
    val selectedIndex = navItems.indexOfFirst { it.route == route }.coerceAtLeast(0)
    val showBottomNav = route in navItems.map(BottomNavItem::route)
    val openMedia: (com.cinetrack.domain.MediaCard) -> Unit = { media -> navController.navigate("detail/${media.type.name}/${media.id}") }

    Box(Modifier.fillMaxSize().imePadding()) {
        NavHost(
            navController = navController,
            startDestination = Routes.Discover,
            enterTransition = {
                val from = mainPageIndex(initialState.destination.route)
                val to = mainPageIndex(targetState.destination.route)
                when {
                    from != null && to != null && to > from ->
                        slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { it / 3 } + fadeIn(tween(220))
                    from != null && to != null && to < from ->
                        slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { -it / 3 } + fadeIn(tween(220))
                    else -> slideInHorizontally(tween(280, easing = FastOutSlowInEasing)) { it / 5 } + fadeIn(tween(220))
                }
            },
            exitTransition = {
                val from = mainPageIndex(initialState.destination.route)
                val to = mainPageIndex(targetState.destination.route)
                when {
                    from != null && to != null && to > from ->
                        slideOutHorizontally(tween(260, easing = FastOutSlowInEasing)) { -it / 4 } + fadeOut(tween(180))
                    from != null && to != null && to < from ->
                        slideOutHorizontally(tween(260, easing = FastOutSlowInEasing)) { it / 4 } + fadeOut(tween(180))
                    else -> slideOutHorizontally(tween(220, easing = FastOutSlowInEasing)) { -it / 10 } + fadeOut(tween(150))
                }
            },
            popEnterTransition = {
                val from = mainPageIndex(initialState.destination.route)
                val to = mainPageIndex(targetState.destination.route)
                when {
                    from != null && to != null && to > from ->
                        slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { it / 3 } + fadeIn(tween(220))
                    from != null && to != null && to < from ->
                        slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { -it / 3 } + fadeIn(tween(220))
                    else -> slideInHorizontally(tween(280, easing = FastOutSlowInEasing)) { -it / 5 } + fadeIn(tween(220))
                }
            },
            popExitTransition = {
                val from = mainPageIndex(initialState.destination.route)
                val to = mainPageIndex(targetState.destination.route)
                when {
                    from != null && to != null && to > from ->
                        slideOutHorizontally(tween(260, easing = FastOutSlowInEasing)) { -it / 4 } + fadeOut(tween(180))
                    from != null && to != null && to < from ->
                        slideOutHorizontally(tween(260, easing = FastOutSlowInEasing)) { it / 4 } + fadeOut(tween(180))
                    else -> slideOutHorizontally(tween(230, easing = FastOutSlowInEasing)) { it / 4 } + fadeOut(tween(170))
                }
            },
        ) {
            composable(Routes.Discover) {
                DiscoverScreen(
                    state = state,
                    onRefresh = viewModel::refresh,
                    onSearch = { navController.navigate("search/discover") },
                    onFilters = { navController.navigate(Routes.DiscoverFilters) },
                    onSeeAll = { navController.navigate("discover-list/$it") },
                    onMedia = openMedia,
                    onStatus = viewModel::setStatus,
                    onNotInterested = viewModel::hideDiscoveryItem,
                    onOpenTmdbSettings = { navController.navigate("settings-detail/${com.cinetrack.ui.screens.SettingsPages.ServiceTmdb}") },
                    onCompactNav = { compactNav = it },
                )
            }
            composable(Routes.Progress) {
                ProgressScreen(
                    state = state,
                    syncProgress = viewModel.syncProgress,
                    syncRunning = viewModel.syncRunning,
                    onSearch = { navController.navigate("search/progress") },
                    onSync = viewModel::sync,
                    onMedia = openMedia,
                    onWatched = viewModel::markPlaybackWatched,
                    onEpisode = { episode -> navController.navigate("episode/${episode.showId}/${episode.season}/${episode.number}") },
                    onHideUpcoming = viewModel::hideUpcomingEpisode,
                    viewingInsights = viewingInsights,
                    onLoadViewingInsights = viewModel::loadViewingInsights,
                    onCompactNav = { compactNav = it },
                )
            }
            composable(Routes.Library) {
                LibraryScreen(state = state, onSearch = { navController.navigate("search/library") }, onMedia = openMedia, onStatus = viewModel::setStatus, onCompactNav = { compactNav = it })
            }
            composable(Routes.Settings) {
                SettingsScreen(
                    state = state,
                    onPage = { navController.navigate("settings-detail/$it") },
                    onCompactNav = { compactNav = it },
                )
            }
            composable(
                Routes.Search,
                arguments = listOf(navArgument("scope") { type = NavType.StringType }),
            ) { entry ->
                val scope = entry.arguments?.getString("scope").orEmpty()
                val scopedItems = when (scope) {
                    "progress" -> state.playbackTv.map { it.media } + state.playbackMovies.map { it.media } + state.history.map { it.media } + state.calendar.map { it.media }
                    "library" -> state.rails[com.cinetrack.domain.RailIds.LIBRARY].orEmpty()
                    else -> emptyList()
                }.distinctBy(com.cinetrack.domain.MediaCard::stableKey)
                SearchScreen(
                    results = searchResults,
                    sourceItems = scopedItems,
                    remoteSearch = scope == "discover",
                    onQuery = viewModel::search,
                    onBack = { navController.popBackStack() },
                    onMedia = openMedia,
                )
            }
            composable(
                Routes.DiscoverList,
                arguments = listOf(navArgument("railId") { type = NavType.StringType }),
            ) { entry ->
                DiscoverListScreen(
                    railId = entry.arguments?.getString("railId").orEmpty(),
                    state = state,
                    onBack = { navController.popBackStack() },
                    onMedia = openMedia,
                    onStatus = viewModel::setStatus,
                    onNotInterested = viewModel::hideDiscoveryItem,
                )
            }
            composable(Routes.DiscoverFilters) {
                DiscoverFiltersScreen(
                    results = discoverFilterResults,
                    loading = discoverFiltersLoading,
                    providers = streamingProviders,
                    regionKey = "${state.metadataRegion}:${state.contentRegions.sorted().joinToString(",")}:${state.preferredProviders.sorted().joinToString(",")}",
                    onApply = viewModel::applyDiscoverFilters,
                    onLoadProviders = viewModel::loadStreamingProviders,
                    onBack = { navController.popBackStack() },
                    onMedia = openMedia,
                    onStatus = viewModel::setStatus,
                    onNotInterested = viewModel::hideDiscoveryItem,
                )
            }
            composable(
                Routes.Detail,
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
                var resolvedMedia by remember(type, id) { mutableStateOf(cachedMedia) }
                val selectedSeason by entry.savedStateHandle
                    .getStateFlow("selectedSeason", -1)
                    .collectAsStateWithLifecycle()
                val selectedEpisode by entry.savedStateHandle
                    .getStateFlow("selectedEpisode", -1)
                    .collectAsStateWithLifecycle()
                LaunchedEffect(type, id, cachedMedia?.status, cachedMedia?.watched, cachedMedia?.libraryUpdatedAt) {
                    resolvedMedia = cachedMedia ?: viewModel.loadMedia(type, id) ?: resolvedMedia
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
                    onBack = { navController.popBackStack() },
                    onStatus = viewModel::setStatus,
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
            composable(
                Routes.Episode,
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
                    resolvedShow = cachedShow ?: viewModel.loadMedia(MediaType.TV, showId) ?: resolvedShow
                }
                EpisodeDetailScreen(
                    show = resolvedShow ?: com.cinetrack.domain.MediaCard(id = showId, type = MediaType.TV, title = ""),
                    episode = state.episodes.firstOrNull { it.showId == showId && it.season == season && it.number == number },
                    requestedSeason = season,
                    requestedNumber = number,
                    allEpisodes = state.episodes.filter { it.showId == showId },
                    people = state.people,
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onSeries = { navController.navigate("detail/${MediaType.TV.name}/$showId") },
                    onMedia = openMedia,
                    onWatched = viewModel::setEpisodeWatched,
                )
            }
            composable(
                Routes.SettingsDetail,
                arguments = listOf(navArgument("page") { type = NavType.StringType }),
            ) { entry ->
                SettingsDetailScreen(
                    page = entry.arguments?.getString("page").orEmpty(),
                    state = state,
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onPage = { navController.navigate("settings-detail/$it") },
                )
            }
        }

        if (showBottomNav) {
            LiquidBottomNav(
                items = navItems,
                selectedIndex = selectedIndex,
                compact = compactNav,
                onSelected = { index ->
                    val destination = navItems[index].route
                    navController.navigate(destination) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 18.dp),
            )
        }

        AnimatedVisibility(
            visible = minimumLaunchElapsed && !state.error.isNullOrBlank(),
            enter = fadeIn(tween(180)) + slideInHorizontally(tween(260)) { it / 5 },
            exit = fadeOut(tween(180)),
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(horizontal = 18.dp, vertical = 10.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().glass(RoundedCornerShape(18.dp)).background(Color(0xFF5A2028).copy(alpha = .42f)).padding(13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Info, null, tint = Color(0xFFFF8C96), modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(10.dp))
                Text(state.error.orEmpty(), color = Color.White, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            }
        }

        AnimatedVisibility(
            visible = !minimumLaunchElapsed || state.loading,
            enter = fadeIn(tween(120)),
            exit = fadeOut(tween(360, easing = FastOutSlowInEasing)),
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
