@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.cinetrack.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cinetrack.R
import com.cinetrack.domain.EpisodeCard
import com.cinetrack.domain.LibraryStatus
import com.cinetrack.domain.MediaCard
import com.cinetrack.domain.MediaType
import com.cinetrack.domain.PersonCard
import com.cinetrack.domain.RatingScore
import com.cinetrack.domain.TimelineCard
import com.cinetrack.ui.CineTrackViewModel
import com.cinetrack.ui.components.AdaptiveBackground
import com.cinetrack.ui.components.GlassBackButton
import com.cinetrack.ui.components.GlassDivider
import com.cinetrack.ui.components.LoadingPane
import com.cinetrack.ui.components.LibraryStatusSheet
import com.cinetrack.ui.components.MediaPoster
import com.cinetrack.ui.components.MediaRail
import com.cinetrack.ui.components.PrimaryAction
import com.cinetrack.ui.components.SectionHeader
import com.cinetrack.ui.components.SharedGlassSheet
import com.cinetrack.ui.components.glass
import com.cinetrack.ui.components.blueEdgeClickable
import com.cinetrack.ui.components.libraryStatusColor
import com.cinetrack.ui.components.libraryStatusIcon
import com.cinetrack.ui.components.rememberLightHapticAction
import com.cinetrack.ui.theme.Accent
import com.cinetrack.ui.theme.AccentLight
import com.cinetrack.ui.theme.Gold
import com.cinetrack.ui.theme.Info
import com.cinetrack.ui.theme.Success
import com.cinetrack.ui.theme.TextMuted
import com.cinetrack.ui.theme.TextPrimary
import com.cinetrack.ui.theme.TextSecondary
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
fun DetailScreen(
    media: MediaCard?,
    people: List<PersonCard>,
    episodes: List<EpisodeCard>,
    history: List<TimelineCard>,
    recommended: List<MediaCard>,
    viewModel: CineTrackViewModel,
    onBack: () -> Unit,
    onStatus: (MediaCard, LibraryStatus) -> Unit,
    onMedia: (MediaCard) -> Unit,
    onEpisode: (EpisodeCard) -> Unit,
    initialSeason: Int? = null,
    initialEpisode: Int? = null,
) {
    if (media == null) {
        AdaptiveBackground { LoadingPane(); GlassBackButton(onClick = onBack, modifier = Modifier.statusBarsPadding().padding(12.dp)) }
        return
    }
    val cachedDetail = viewModel.cachedDetails(media)
    val cachedEpisodes = viewModel.cachedEpisodes(media.id)
    var detail by remember(media.stableKey) {
        mutableStateOf(
            (cachedDetail ?: media).copy(
                status = media.status,
                watched = media.watched,
                libraryUpdatedAt = media.libraryUpdatedAt,
            ),
        )
    }
    var ratings by remember(media.stableKey) { mutableStateOf(viewModel.cachedRatings(media)) }
    var detailPeople by remember(media.stableKey) { mutableStateOf(people) }
    var detailEpisodes by remember(media.stableKey) {
        mutableStateOf(mergeWatchedEpisodes(cachedEpisodes.ifEmpty { episodes }, episodes, history))
    }
    var collectionItems by remember(media.stableKey) { mutableStateOf<List<MediaCard>>(emptyList()) }
    var moreLikeThis by remember(media.stableKey) { mutableStateOf(recommended) }
    var librarySheet by remember { mutableStateOf(false) }
    var trailerSheet by remember(media.stableKey) { mutableStateOf(false) }
    var trailerKey by remember(media.stableKey) { mutableStateOf<String?>(null) }
    var trailerLoading by remember(media.stableKey) { mutableStateOf(false) }
    var trailerAttempt by remember(media.stableKey) { mutableStateOf(0) }
    var selectedPerson by remember { mutableStateOf<PersonCard?>(null) }
    var showFullCast by remember(media.stableKey) { mutableStateOf(false) }
    var pendingPreviousEpisodes by remember(media.stableKey) {
        mutableStateOf<Pair<EpisodeCard, List<EpisodeCard>>?>(null)
    }
    val openLibrarySheet = rememberLightHapticAction { librarySheet = true }
    LaunchedEffect(media.status, media.watched, media.libraryUpdatedAt) {
        // Keep the action label/pill tied to the shared Room state even when a
        // Simkl push or another screen changes this title while detail stays open.
        detail = detail.copy(
            status = media.status,
            watched = media.watched,
            libraryUpdatedAt = media.libraryUpdatedAt,
        )
    }
    LaunchedEffect(media.stableKey) {
        val loadedDetail = viewModel.loadDetails(media)
        detail = loadedDetail.copy(
            status = media.status,
            watched = media.watched,
            libraryUpdatedAt = media.libraryUpdatedAt,
        )
        ratings = viewModel.loadRatings(loadedDetail).ifEmpty { ratings }
        detailPeople = viewModel.loadCast(loadedDetail).ifEmpty { detailPeople }
        if (loadedDetail.type == MediaType.TV) {
            detailEpisodes = mergeWatchedEpisodes(viewModel.loadAllEpisodes(loadedDetail), episodes, history)
        }
        collectionItems = viewModel.loadCollection(loadedDetail)
        moreLikeThis = viewModel.loadRecommendations(loadedDetail).ifEmpty { recommended }
    }
    LaunchedEffect(episodes, history) {
        if (detail.type == MediaType.TV) {
            detailEpisodes = mergeWatchedEpisodes(
                viewModel.cachedEpisodes(detail.id).ifEmpty { detailEpisodes },
                episodes,
                history,
            )
        }
    }
    AdaptiveBackground(artworkUrl = detail.posterUrl ?: detail.backdropUrl) {
        val detailListState = rememberLazyListState()
        LazyColumn(state = detailListState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 0.dp)) {
            item { DetailHero(detail, onBack) }
            item {
                val sheetShape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xFF171A20).copy(alpha = .46f),
                                    Color(0xFF11141A).copy(alpha = .56f),
                                ),
                            ),
                            sheetShape,
                        )
                        .border(.6.dp, Color.White.copy(alpha = .10f), sheetShape)
                        .navigationBarsPadding()
                        .padding(top = 32.dp, bottom = 72.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    DetailIdentity(detail, detailEpisodes)
                    if (ratings.isNotEmpty()) RatingsSection(ratings)
                    if (detail.overview.isNotBlank()) GlassTextSection(stringResource(R.string.overview), detail.overview)
                    Row(
                        Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val completed = detail.status == LibraryStatus.COMPLETED || detail.watched
                        PrimaryAction(
                            text = stringResource(if (completed) R.string.watched else R.string.mark_watched),
                            icon = Icons.Filled.Check,
                            modifier = Modifier.weight(1f),
                            containerColor = if (completed) Success else Accent,
                        ) {
                            val target = if (completed) {
                                if (detail.type == MediaType.TV) LibraryStatus.WATCHING else LibraryStatus.PLAN_TO_WATCH
                            } else LibraryStatus.COMPLETED
                            detail = detail.copy(status = target, watched = target == LibraryStatus.COMPLETED)
                            onStatus(detail, target)
                        }
                        Box(
                            Modifier.size(48.dp).glass(RoundedCornerShape(999.dp))
                                .clickable(onClick = openLibrarySheet),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                if (detail.status == LibraryStatus.NONE) Icons.Outlined.BookmarkBorder else libraryStatusIcon(detail.status),
                                stringResource(R.string.choose_library_status),
                                tint = if (detail.status == LibraryStatus.NONE) AccentLight else libraryStatusColor(detail.status),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    TrailerActionButton { trailerSheet = true }
                    if (detail.type == MediaType.TV && detailEpisodes.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        EpisodesSection(
                            detail,
                            detailEpisodes,
                            onEpisode,
                            initialSeason = initialSeason,
                            initialEpisode = initialEpisode,
                            onEpisodeWatched = { episode, watched ->
                                val previousUnwatched = if (watched) detailEpisodes.filter {
                                    !it.watched &&
                                        (it.season < episode.season || (it.season == episode.season && it.number < episode.number))
                                } else emptyList()
                                if (previousUnwatched.isNotEmpty()) {
                                    pendingPreviousEpisodes = episode to previousUnwatched
                                } else {
                                    detailEpisodes = detailEpisodes.map {
                                        if (it.season == episode.season && it.number == episode.number) it.copy(watched = watched) else it
                                    }
                                    viewModel.setEpisodeWatched(episode, watched)
                                }
                            },
                            onSeasonWatched = { seasonEpisodes, watched ->
                                val numbers = seasonEpisodes.map { it.season to it.number }.toSet()
                                detailEpisodes = detailEpisodes.map { if ((it.season to it.number) in numbers) it.copy(watched = watched) else it }
                                viewModel.setSeasonWatched(seasonEpisodes, watched)
                            },
                        )
                    }
                    if (detailPeople.isNotEmpty()) {
                        CastSection(detailPeople, onViewAll = { showFullCast = true }) { selectedPerson = it }
                    }
                    if (collectionItems.isNotEmpty()) {
                        CollectionSection(detail, collectionItems, onMedia)
                    }
                    if (detail.providers.isNotEmpty()) ProviderSection(detail)
                    UsefulInfoSection(detail)
                    if (moreLikeThis.isNotEmpty()) {
                        SectionHeader(stringResource(R.string.more_like_this), Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp))
                        MediaRail(moreLikeThis.filterNot { it.stableKey == detail.stableKey }, onMedia)
                    }
                }
            }
        }
    }
    if (librarySheet) {
        LibraryStatusSheet(detail, onDismiss = { librarySheet = false }) { status ->
            detail = detail.copy(status = status, watched = status == LibraryStatus.COMPLETED)
            onStatus(detail, status)
            librarySheet = false
        }
    }
    if (trailerSheet) {
        LaunchedEffect(detail.stableKey, trailerAttempt) {
            trailerLoading = true
            trailerKey = viewModel.loadTrailerKey(detail)
            trailerLoading = false
        }
        TrailerPlayerSheet(
            title = detail.title,
            trailerKey = trailerKey,
            loading = trailerLoading,
            onDismiss = { trailerSheet = false },
            onRetry = { trailerAttempt += 1 },
        )
    }
    selectedPerson?.let { person ->
        ActorSheet(person, viewModel, onDismiss = { selectedPerson = null }, onMedia = onMedia)
    }
    if (showFullCast) {
        FullCastSheet(detailPeople, onDismiss = { showFullCast = false }) { person ->
            showFullCast = false
            selectedPerson = person
        }
    }
    pendingPreviousEpisodes?.let { (episode, previous) ->
        PreviousEpisodesPrompt(
            previousCount = previous.size,
            onDismiss = { pendingPreviousEpisodes = null },
            onOnlyThis = {
                detailEpisodes = detailEpisodes.map {
                    if (it.season == episode.season && it.number == episode.number) it.copy(watched = true) else it
                }
                viewModel.setEpisodeWatched(episode, true)
                pendingPreviousEpisodes = null
            },
            onIncludePrevious = {
                val affected = (previous + episode).distinctBy { it.season to it.number }
                val numbers = affected.map { it.season to it.number }.toSet()
                detailEpisodes = detailEpisodes.map { if ((it.season to it.number) in numbers) it.copy(watched = true) else it }
                viewModel.setEpisodesWatched(affected, true)
                pendingPreviousEpisodes = null
            },
        )
    }
}

private fun mergeWatchedEpisodes(
    loaded: List<EpisodeCard>,
    localEpisodes: List<EpisodeCard>,
    history: List<TimelineCard>,
): List<EpisodeCard> {
    val watchedIds = localEpisodes.asSequence().filter(EpisodeCard::watched).map(EpisodeCard::id).toSet()
    val watchedNumbers = buildSet {
        localEpisodes.asSequence().filter(EpisodeCard::watched).forEach { add(it.season to it.number) }
        history.asSequence().filter { it.media.type == MediaType.TV }.forEach eventLoop@{ event ->
            val season = event.season ?: return@eventLoop
            val number = event.episodeNumber ?: return@eventLoop
            add(season to number)
        }
    }
    return (loaded + localEpisodes).distinctBy { it.season to it.number }.map { episode ->
        if (episode.watched || episode.id in watchedIds || (episode.season to episode.number) in watchedNumbers) {
            episode.copy(watched = true)
        } else episode
    }
}

@Composable
private fun TrailerActionButton(onClick: () -> Unit) {
    val hapticClick = rememberLightHapticAction(onClick)
    Row(
        Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(48.dp)
            .glass(RoundedCornerShape(999.dp))
            .background(Color(0xFFFF0000).copy(alpha = .84f), RoundedCornerShape(999.dp))
            .border(.7.dp, Color(0xFFFF7777).copy(alpha = .55f), RoundedCornerShape(999.dp))
            .clickable(onClick = hapticClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.trailer), color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun TrailerPlayerSheet(
    title: String,
    trailerKey: String?,
    loading: Boolean,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
) {
    SharedGlassSheet(onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
            Text(
                stringResource(R.string.watch_trailer),
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
            )
            Text(title, color = TextMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF090A0D)),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    loading -> CircularProgressIndicator(color = Color(0xFFFF3434))
                    trailerKey.isNullOrBlank() -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.trailer_unavailable), color = TextSecondary, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))
                        PrimaryAction(stringResource(R.string.retry), Icons.Filled.PlayArrow, Modifier.width(150.dp), onClick = onRetry)
                    }
                    else -> YouTubeTrailerPlayer(trailerKey)
                }
            }
        }
    }
}

@Composable
private fun YouTubeTrailerPlayer(videoKey: String) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var playerView by remember(videoKey) { mutableStateOf<YouTubePlayerView?>(null) }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = {
            YouTubePlayerView(context).apply {
                lifecycleOwner.lifecycle.addObserver(this)
                addYouTubePlayerListener(object : AbstractYouTubePlayerListener() {
                    override fun onReady(youTubePlayer: YouTubePlayer) {
                        youTubePlayer.loadVideo(videoKey, 0f)
                    }
                })
                playerView = this
            }
        },
        update = { },
    )
    DisposableEffect(videoKey, lifecycleOwner) {
        onDispose {
            playerView?.let { view ->
                lifecycleOwner.lifecycle.removeObserver(view)
                view.release()
            }
            playerView = null
        }
    }
}

@Composable
private fun DetailHero(media: MediaCard, onBack: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(438.dp)) {
        // AdaptiveBackground is the single artwork-derived base layer. Keeping
        // the hero transparent avoids a second tinted seam above the sheet.
        GlassBackButton(
            onClick = onBack,
            modifier = Modifier.statusBarsPadding().padding(start = 16.dp, top = 8.dp),
        )
        Box(
            Modifier.align(Alignment.Center).padding(top = 60.dp).width(214.dp).aspectRatio(.70f)
                .shadow(18.dp, RoundedCornerShape(22.dp), clip = false)
                .clip(RoundedCornerShape(22.dp)).background(Brush.linearGradient(listOf(Accent, Color(0xFF202637))))
        ) {
                if (!media.posterUrl.isNullOrBlank()) AsyncImage(media.posterUrl, media.title, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        }
    }
}

@Composable
private fun DetailIdentity(media: MediaCard, episodes: List<EpisodeCard>) {
    val seasonCount = remember(media.stableKey, episodes) {
        (media.seasons.map { it.number } + episodes.map { it.season }).filter { it > 0 }.distinct().size
    }
    val episodeCount = remember(media.stableKey, episodes) {
        media.seasons.sumOf { it.episodeCount }.takeIf { it > 0 } ?: episodes.size
    }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            media.title,
            color = TextPrimary,
            fontSize = 30.sp,
            lineHeight = 33.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        if (media.status != LibraryStatus.NONE || !media.tmdbStatus.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (media.status != LibraryStatus.NONE) {
                    val statusColor = libraryStatusColor(media.status)
                    Row(
                        Modifier.clip(RoundedCornerShape(999.dp)).background(statusColor.copy(alpha = .15f)).padding(horizontal = 11.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(statusColor))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            detailLibraryActionLabel(media.status).uppercase(),
                            color = Color.White,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                }
                media.tmdbStatus?.takeIf(String::isNotBlank)?.let { status ->
                    Row(
                        Modifier.clip(RoundedCornerShape(999.dp)).background(Accent.copy(alpha = .15f)).padding(horizontal = 11.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(AccentLight))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "TMDB · ${tmdbStatusLabel(status)}".uppercase(),
                            color = Color.White,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(
            if (media.type == MediaType.TV) {
                listOfNotNull(
                    media.year.takeIf(String::isNotBlank),
                    seasonCount.takeIf { it > 0 }?.let { "$it seasons" },
                    episodeCount.takeIf { it > 0 }?.let { "$it episodes" },
                ).joinToString(" · ")
            } else {
                listOfNotNull(
                    media.year.takeIf(String::isNotBlank),
                    formatDurationMinutes(media.runtimeMinutes).takeIf(String::isNotBlank),
                ).joinToString(" · ")
            },
            color = TextSecondary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        )
        if (media.genres.isNotEmpty()) {
            Spacer(Modifier.height(5.dp))
            Text(
                media.genres.joinToString(" · "),
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun GlassTextSection(title: String, body: String) {
    Column(Modifier.padding(horizontal = 20.dp).fillMaxWidth().glass().padding(14.dp)) {
        SectionHeader(title)
        Spacer(Modifier.height(8.dp))
        Text(body, color = TextSecondary, fontSize = 13.sp, lineHeight = 19.sp)
    }
}

@Composable
private fun RatingsSection(ratings: List<RatingScore>) {
    Row(
        Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        ratings.take(4).forEach { rating ->
            Column(
                Modifier.weight(1f).glass(RoundedCornerShape(12.dp)).padding(horizontal = 5.dp, vertical = 9.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(rating.source, color = TextMuted, fontSize = 8.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(rating.score, color = TextPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun ProviderSection(media: MediaCard) {
    val uriHandler = LocalUriHandler.current
    Column(Modifier.padding(horizontal = 20.dp).fillMaxWidth().glass().padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.PlayArrow, null, tint = AccentLight)
            Spacer(Modifier.width(8.dp))
            SectionHeader(stringResource(R.string.where_to_watch), Modifier.weight(1f))
        }
        ProviderCategory(stringResource(R.string.subscription), media.subscriptionProviders.ifEmpty { media.providers }, media.providerLogos)
        ProviderCategory(stringResource(R.string.rent), media.rentProviders, media.providerLogos)
        ProviderCategory(stringResource(R.string.buy), media.buyProviders, media.providerLogos)
        media.providerLink?.takeIf(String::isNotBlank)?.let { link ->
            Spacer(Modifier.height(9.dp))
            Text(
                stringResource(R.string.open_provider_options),
                color = AccentLight,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { runCatching { uriHandler.openUri(link) } },
            )
        }
    }
}

@Composable
private fun ProviderCategory(label: String, providers: List<String>, logos: Map<String, String>) {
    if (providers.isEmpty()) return
    Text(label, color = TextMuted, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(providers) { provider ->
                Row(
                    Modifier.glass(RoundedCornerShape(999.dp)).padding(horizontal = 9.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val logo = logos[provider]
                    Box(Modifier.size(24.dp).clip(RoundedCornerShape(7.dp)).background(Accent.copy(alpha = .26f)), contentAlignment = Alignment.Center) {
                        if (!logo.isNullOrBlank()) AsyncImage(logo, provider, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                        else Text(provider.take(1), color = TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Black)
                    }
                    Spacer(Modifier.width(7.dp))
                    Text(provider, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                }
        }
    }
}

@Composable
private fun EpisodesSection(
    media: MediaCard,
    episodes: List<EpisodeCard>,
    onEpisode: (EpisodeCard) -> Unit,
    initialSeason: Int?,
    initialEpisode: Int?,
    onEpisodeWatched: (EpisodeCard, Boolean) -> Unit,
    onSeasonWatched: (List<EpisodeCard>, Boolean) -> Unit,
) {
    val grouped = episodes.groupBy(EpisodeCard::season).toSortedMap()
    val watchingSeason = remember(media.status, episodes) {
        if (media.status != LibraryStatus.WATCHING) null
        else {
            val ordered = episodes.filter { it.season > 0 }
                .sortedWith(compareBy(EpisodeCard::season, EpisodeCard::number))
            val lastWatched = ordered.lastOrNull(EpisodeCard::watched)
            ordered.firstOrNull { candidate ->
                !candidate.watched && (
                    lastWatched == null || candidate.season > lastWatched.season ||
                        (candidate.season == lastWatched.season && candidate.number > lastWatched.number)
                    )
            }?.season ?: lastWatched?.season ?: ordered.firstOrNull()?.season
        }
    }
    var expandedSeason by rememberSaveable(media.stableKey, media.status) {
        mutableStateOf<Int?>(
            initialSeason?.takeIf { it in grouped }
                ?: watchingSeason?.takeIf { it in grouped },
        )
    }
    var expandedInfo by remember(media.stableKey) { mutableStateOf<Pair<Int, Int>?>(null) }
    LaunchedEffect(initialSeason, grouped.keys) {
        if (initialSeason != null && grouped.containsKey(initialSeason)) expandedSeason = initialSeason
    }
    Column(Modifier.padding(horizontal = 20.dp).fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Tv, null, tint = AccentLight)
            Spacer(Modifier.width(8.dp))
            SectionHeader(stringResource(R.string.seasons_episodes), Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            grouped.forEach { (seasonNumber, seasonEpisodes) ->
                val expanded = expandedSeason == seasonNumber
                val seasonInteraction = remember(media.stableKey, seasonNumber) { MutableInteractionSource() }
                val seasonPressed by seasonInteraction.collectIsPressedAsState()
                val watched = seasonEpisodes.count(EpisodeCard::watched)
                val allWatched = watched == seasonEpisodes.size && seasonEpisodes.isNotEmpty()
                val summary = media.seasons.firstOrNull { it.number == seasonNumber }
                val hapticExpand = rememberLightHapticAction { expandedSeason = if (expanded) null else seasonNumber }
                val hapticSeasonWatched = rememberLightHapticAction { onSeasonWatched(seasonEpisodes, !allWatched) }
                Column(
                    Modifier.fillMaxWidth().glass(RoundedCornerShape(18.dp))
                        .border(if (seasonPressed) 1.6.dp else 0.dp, Accent.copy(alpha = if (seasonPressed) .9f else 0f), RoundedCornerShape(18.dp)),
                ) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 114.dp)
                            .clickable(
                                interactionSource = seasonInteraction,
                                indication = null,
                            ) { hapticExpand() }
                            .padding(11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.width(62.dp).height(92.dp).clip(RoundedCornerShape(11.dp)).background(Brush.linearGradient(listOf(Info.copy(alpha = .36f), Color(0xFF07131C))))) {
                            summary?.posterUrl?.let { AsyncImage(it, summary.title, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
                        }
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(summary?.title ?: "Season $seasonNumber", color = TextPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                                Spacer(Modifier.width(7.dp))
                                Box(Modifier.size(27.dp).clip(CircleShape).background(Color(0xFF59606B).copy(alpha = .52f)), contentAlignment = Alignment.Center) {
                                    Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = TextSecondary, modifier = Modifier.size(17.dp))
                                }
                            }
                            Text("$watched of ${summary?.episodeCount ?: seasonEpisodes.size} episodes", color = TextMuted, fontSize = 10.5.sp)
                        }
                        Box(Modifier.size(32.dp).clip(CircleShape).background(if (allWatched) Success.copy(alpha = .28f) else Color(0xFF59606B).copy(alpha = .60f)).clickable(onClick = hapticSeasonWatched), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Check, stringResource(R.string.mark_watched), tint = if (allWatched) Success else TextSecondary, modifier = Modifier.size(18.dp))
                        }
                    }
                    val total = (summary?.episodeCount ?: seasonEpisodes.size).coerceAtLeast(1)
                    Box(Modifier.fillMaxWidth().height(4.dp).background(Color(0xFF4A4E57))) {
                        Box(Modifier.fillMaxWidth(watched.toFloat() / total).fillMaxSize().background(Brush.horizontalGradient(listOf(AccentLight, Accent))))
                    }
                    AnimatedVisibility(expanded) {
                        Column(Modifier.padding(9.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            seasonEpisodes.forEach { episode ->
                                val infoVisible = expandedInfo == (episode.season to episode.number)
                                val returnTarget = episode.season == initialSeason && episode.number == initialEpisode
                                val hapticInfo = rememberLightHapticAction {
                                    expandedInfo = if (infoVisible) null else episode.season to episode.number
                                }
                                val hapticWatched = rememberLightHapticAction { onEpisodeWatched(episode, !episode.watched) }
                                Column(
                                    Modifier.fillMaxWidth().glass(RoundedCornerShape(13.dp))
                                        .then(
                                            if (returnTarget) Modifier.border(.8.dp, Accent.copy(alpha = .72f), RoundedCornerShape(13.dp))
                                            else Modifier,
                                        )
                                        .blueEdgeClickable(RoundedCornerShape(13.dp)) { onEpisode(episode) },
                                ) {
                                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.width(66.dp).height(44.dp).clip(RoundedCornerShape(9.dp)).background(Color(0xFF07131C))) {
                                        episode.stillUrl?.let { AsyncImage(it, episode.title, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
                                    }
                                    Spacer(Modifier.width(9.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text("S$seasonNumber E${episode.number}", color = TextMuted, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                                        Text(episode.title, color = TextPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(formatFullDate(episode.airDate), color = TextMuted, fontSize = 9.5.sp)
                                    }
                                    Box(Modifier.size(28.dp).clip(CircleShape).border(.8.dp, Info.copy(alpha = .75f), CircleShape).clickable(onClick = hapticInfo), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Filled.Info, null, tint = TextSecondary, modifier = Modifier.size(14.dp))
                                    }
                                    Spacer(Modifier.width(7.dp))
                                    Box(Modifier.size(28.dp).clip(CircleShape).background(if (episode.watched) Success else Color(0xFF555963)).clickable(onClick = hapticWatched), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Filled.Check, null, tint = if (episode.watched) Color(0xFF09271A) else TextSecondary, modifier = Modifier.size(15.dp))
                                    }
                                }
                                AnimatedVisibility(infoVisible) {
                                    Text(episode.overview.ifBlank { stringResource(R.string.overview) }, color = TextSecondary, fontSize = 11.5.sp, lineHeight = 16.sp, modifier = Modifier.padding(start = 75.dp, end = 12.dp, bottom = 10.dp))
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

@Composable
private fun CastSection(people: List<PersonCard>, onViewAll: () -> Unit, onPerson: (PersonCard) -> Unit) {
    Column {
        SectionHeader(stringResource(R.string.cast_and_crew), Modifier.padding(horizontal = 20.dp), stringResource(R.string.see_all), onViewAll)
        Spacer(Modifier.height(10.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(people, key = PersonCard::id) { person ->
                Column(
                    Modifier.width(92.dp).blueEdgeClickable(RoundedCornerShape(18.dp)) { onPerson(person) }.padding(vertical = 3.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.size(76.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Accent, Info)))) {
                        if (!person.profileUrl.isNullOrBlank()) AsyncImage(person.profileUrl, person.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        else Icon(Icons.Filled.Person, null, tint = Color.White, modifier = Modifier.align(Alignment.Center).size(34.dp))
                    }
                    Spacer(Modifier.height(7.dp))
                    Text(person.name, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(person.role, color = TextMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun FullCastSheet(people: List<PersonCard>, onDismiss: () -> Unit, onPerson: (PersonCard) -> Unit) {
    SharedGlassSheet(onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
            SectionHeader(stringResource(R.string.full_cast))
            Spacer(Modifier.height(10.dp))
            LazyColumn(
                Modifier.fillMaxWidth().heightIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 18.dp),
            ) {
                items(people, key = PersonCard::id) { person ->
                    Row(
                        Modifier.fillMaxWidth().glass(RoundedCornerShape(15.dp))
                            .blueEdgeClickable(RoundedCornerShape(15.dp)) { onPerson(person) }
                            .padding(9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(48.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Accent, Info)))) {
                            if (!person.profileUrl.isNullOrBlank()) {
                                AsyncImage(person.profileUrl, person.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            } else {
                                Icon(Icons.Filled.Person, null, tint = Color.White, modifier = Modifier.align(Alignment.Center).size(24.dp))
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(person.name, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(person.role, color = TextMuted, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviousEpisodesPrompt(
    previousCount: Int,
    onDismiss: () -> Unit,
    onOnlyThis: () -> Unit,
    onIncludePrevious: () -> Unit,
) {
    SharedGlassSheet(onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
            Text(stringResource(R.string.previous_episodes_title), color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(7.dp))
            Text(
                stringResource(R.string.previous_episodes_message, previousCount),
                color = TextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(16.dp))
            PrimaryAction(stringResource(R.string.mark_previous_too), Icons.Filled.Check, Modifier.fillMaxWidth(), onClick = onIncludePrevious)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onOnlyThis, modifier = Modifier.fillMaxWidth().height(46.dp), shape = RoundedCornerShape(999.dp)) {
                Text(stringResource(R.string.only_this_episode), color = TextPrimary, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun CollectionSection(current: MediaCard, related: List<MediaCard>, onMedia: (MediaCard) -> Unit) {
    val collection = (listOf(current) + related.filter { it.type == current.type })
        .distinctBy(MediaCard::stableKey)
        .sortedWith(
            compareBy<MediaCard> { it.releaseDate.isNullOrBlank() }
                .thenBy { it.releaseDate.orEmpty() }
                .thenBy(MediaCard::title),
        )
    val selectedIndex = collection.indexOfFirst { it.stableKey == current.stableKey }.coerceAtLeast(0)
    val collectionState = rememberLazyListState(
        initialFirstVisibleItemIndex = (selectedIndex - 1).coerceAtLeast(0),
    )
    LaunchedEffect(current.stableKey, collection.size) {
        // Show the selected film immediately with one chronological neighbour
        // before it whenever the rail has enough items.
        collectionState.scrollToItem((selectedIndex - 1).coerceAtLeast(0))
    }
    Column(Modifier.padding(horizontal = 20.dp).fillMaxWidth().glass().padding(vertical = 16.dp)) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(4.dp).height(34.dp).clip(CircleShape).background(Gold))
            Spacer(Modifier.width(10.dp))
            SectionHeader(stringResource(R.string.collections_related), Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        LazyRow(
            state = collectionState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(collection, key = MediaCard::stableKey) { item ->
                Column {
                    MediaPoster(
                        item,
                        width = 104.dp,
                        selectedBorder = Gold.takeIf { item.stableKey == current.stableKey },
                        onClick = { onMedia(item) },
                    )
                    Text(
                        stringResource(R.string.movie_n_of_n, collection.indexOf(item) + 1, collection.size),
                        color = if (item.stableKey == current.stableKey) Gold else TextMuted,
                        fontSize = 10.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun detailLibraryActionLabel(status: LibraryStatus): String = when (status) {
    LibraryStatus.NONE -> stringResource(R.string.add_to_library)
    LibraryStatus.WATCHING -> stringResource(R.string.in_progress)
    LibraryStatus.PLAN_TO_WATCH -> stringResource(R.string.plan_to_watch)
    LibraryStatus.PAUSED -> stringResource(R.string.paused)
    LibraryStatus.COMPLETED -> stringResource(R.string.completed)
    LibraryStatus.DROPPED -> stringResource(R.string.dropped)
}

@Composable
private fun tmdbStatusLabel(status: String): String = when (status.lowercase()) {
    "returning series" -> stringResource(R.string.tmdb_returning_series)
    "in production" -> stringResource(R.string.tmdb_in_production)
    "post production" -> stringResource(R.string.tmdb_post_production)
    "released" -> stringResource(R.string.tmdb_released)
    "ended" -> stringResource(R.string.tmdb_ended)
    "planned" -> stringResource(R.string.tmdb_planned)
    "canceled", "cancelled" -> stringResource(R.string.tmdb_canceled)
    "pilot" -> stringResource(R.string.tmdb_pilot)
    "rumored", "rumoured" -> stringResource(R.string.tmdb_rumored)
    else -> status
}

@Composable
private fun UsefulInfoSection(media: MediaCard) {
    Column(Modifier.padding(horizontal = 20.dp).fillMaxWidth().glass().padding(14.dp)) {
        SectionHeader(stringResource(R.string.useful_information))
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth()) {
            InfoCell(
                if (media.type == MediaType.TV) Icons.Filled.Tv else Icons.Filled.Movie,
                stringResource(R.string.status),
                media.tmdbStatus?.takeIf(String::isNotBlank)?.let { tmdbStatusLabel(it) }.orEmpty(),
                Modifier.weight(1f),
            )
            InfoCell(Icons.Filled.CalendarMonth, stringResource(R.string.release_date), formatFullDate(media.releaseDate), Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth()) {
            InfoCell(Icons.Filled.Schedule, stringResource(R.string.runtime), formatDurationMinutes(media.runtimeMinutes), Modifier.weight(1f))
            InfoCell(Icons.Filled.Star, stringResource(R.string.rating), media.score?.let { "%.1f / 10".format(it) }.orEmpty(), Modifier.weight(1f))
        }
        if (media.type == MediaType.TV && media.seasons.isNotEmpty()) {
            val regularSeasons = media.seasons.filter { it.number > 0 }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth()) {
                InfoCell(Icons.Filled.Tv, stringResource(R.string.seasons), regularSeasons.size.toString(), Modifier.weight(1f))
                InfoCell(
                    Icons.Filled.Info,
                    stringResource(R.string.episodes),
                    regularSeasons.sumOf { it.episodeCount }.toString(),
                    Modifier.weight(1f),
                )
            }
        }
        if (media.networks.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            InfoCell(
                Icons.Filled.Tv,
                stringResource(R.string.networks),
                media.networks.joinToString(" · "),
                Modifier.fillMaxWidth(),
                maxLines = 2,
            )
        }
        if (media.type == MediaType.MOVIE && (media.budget != null || media.boxOffice != null)) {
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth()) {
                InfoCell(Icons.Filled.AttachMoney, stringResource(R.string.budget), formatUsd(media.budget), Modifier.weight(1f))
                InfoCell(Icons.Filled.AttachMoney, stringResource(R.string.box_office), formatUsd(media.boxOffice), Modifier.weight(1f))
            }
        }
        if (media.productionCountries.isNotEmpty() || !media.originalLanguage.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth()) {
                InfoCell(
                    Icons.Filled.Public,
                    stringResource(R.string.production_countries),
                    media.productionCountries.joinToString(" · "),
                    Modifier.weight(1f),
                    maxLines = 2,
                )
                InfoCell(
                    Icons.Filled.Language,
                    stringResource(R.string.original_language),
                    media.originalLanguage.orEmpty(),
                    Modifier.weight(1f),
                )
            }
        }
        if (media.productionCompanies.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            InfoCell(
                Icons.Filled.Business,
                stringResource(R.string.production_companies),
                media.productionCompanies.joinToString(" · "),
                Modifier.fillMaxWidth(),
                maxLines = 2,
            )
        }
        if (media.genres.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            InfoCell(
                Icons.Filled.Movie,
                stringResource(R.string.genres),
                media.genres.joinToString(" · "),
                Modifier.fillMaxWidth(),
                maxLines = 2,
            )
        }
    }
}

private fun formatUsd(amount: Long?): String {
    val value = amount?.takeIf { it > 0L } ?: return ""
    fun compact(divisor: Double, suffix: String): String {
        val number = value / divisor
        val formatted = if (number >= 100 || number % 1.0 == 0.0) "%.0f".format(java.util.Locale.US, number)
        else "%.1f".format(java.util.Locale.US, number)
        return "\$$formatted$suffix"
    }
    return when {
        value >= 1_000_000_000L -> compact(1_000_000_000.0, "B")
        value >= 1_000_000L -> compact(1_000_000.0, "M")
        else -> java.text.NumberFormat.getCurrencyInstance(java.util.Locale.US).format(value)
    }
}

@Composable
private fun InfoCell(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(Accent.copy(alpha = .18f)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = AccentLight, modifier = Modifier.size(18.dp)) }
        Spacer(Modifier.width(9.dp))
        Column { Text(label, color = TextMuted, fontSize = 10.sp); Text(value.ifBlank { "—" }, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = maxLines, overflow = TextOverflow.Ellipsis) }
    }
}

@Composable
private fun ActorSheet(person: PersonCard, viewModel: CineTrackViewModel, onDismiss: () -> Unit, onMedia: (MediaCard) -> Unit) {
    var details by remember(person.id) { mutableStateOf(person) }
    var biographyExpanded by remember(person.id) { mutableStateOf(false) }
    var biographyOverflowing by remember(person.id) { mutableStateOf(false) }
    LaunchedEffect(person.id) { details = viewModel.loadPerson(person) }
    SharedGlassSheet(onDismiss) {
        Column(
            Modifier.fillMaxWidth().fillMaxHeight(.72f).padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(66.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Accent, Info)))) {
                    if (!details.profileUrl.isNullOrBlank()) AsyncImage(details.profileUrl, details.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else Icon(Icons.Filled.Person, null, tint = Color.White, modifier = Modifier.align(Alignment.Center).size(32.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(details.name, color = TextPrimary, fontSize = 20.sp, lineHeight = 22.sp, fontWeight = FontWeight.ExtraBold)
                    Text(details.role, color = AccentLight, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    val age = details.age()
                    val born = listOfNotNull(formatFullDate(details.birthday).takeIf(String::isNotBlank), age?.let { stringResource(R.string.years_old, it) }).joinToString(" · ")
                    if (born.isNotBlank()) Text(born, color = TextSecondary, fontSize = 11.sp)
                    details.placeOfBirth?.let { Text(it, color = TextMuted, fontSize = 10.sp, maxLines = 1) }
                }
            }
            if (details.biography.isNotBlank()) {
                Spacer(Modifier.height(13.dp))
                Text(
                    details.biography,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    maxLines = if (biographyExpanded) Int.MAX_VALUE else 5,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { result -> if (!biographyExpanded) biographyOverflowing = result.hasVisualOverflow },
                )
                if (biographyOverflowing || biographyExpanded) {
                    Text(
                        if (biographyExpanded) stringResource(R.string.collapse) else stringResource(R.string.see_all),
                        color = AccentLight,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 5.dp).clickable { biographyExpanded = !biographyExpanded },
                    )
                }
            }
            if (details.movieCredits.isNotEmpty()) {
                Spacer(Modifier.height(16.dp)); SectionHeader(stringResource(R.string.recent_credits))
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    details.movieCredits.forEach { movie ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = .045f))
                            .blueEdgeClickable(RoundedCornerShape(12.dp)) { onDismiss(); onMedia(movie) }.padding(7.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.width(42.dp).height(60.dp).clip(RoundedCornerShape(9.dp)).background(Accent.copy(alpha = .18f))) {
                                if (!movie.posterUrl.isNullOrBlank()) AsyncImage(movie.posterUrl, movie.title, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(movie.title, color = TextPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text(movie.year, color = AccentLight, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EpisodeDetailScreen(
    show: MediaCard?,
    episode: EpisodeCard?,
    requestedSeason: Int,
    requestedNumber: Int,
    allEpisodes: List<EpisodeCard>,
    people: List<PersonCard>,
    viewModel: CineTrackViewModel,
    onBack: () -> Unit,
    onSeries: () -> Unit,
    onMedia: (MediaCard) -> Unit,
    onWatched: (EpisodeCard, Boolean) -> Unit,
) {
    var loadedEpisode by remember(show?.id) { mutableStateOf(episode) }
    var loadedEpisodes by remember(show?.id) { mutableStateOf(allEpisodes) }
    var loadedPeople by remember(show?.id) { mutableStateOf(people) }
    LaunchedEffect(episode?.season, episode?.number, episode?.watched) {
        val latest = episode ?: return@LaunchedEffect
        if (loadedEpisode?.season == latest.season && loadedEpisode?.number == latest.number) {
            loadedEpisode = loadedEpisode?.copy(watched = latest.watched) ?: latest
        }
        loadedEpisodes = loadedEpisodes.map { cached ->
            if (cached.season == latest.season && cached.number == latest.number) latest else cached
        }
    }
    LaunchedEffect(show?.stableKey, requestedSeason, requestedNumber) {
        show?.let {
            loadedEpisode = viewModel.loadEpisode(it, requestedSeason, requestedNumber)
                ?: loadedEpisode
                ?: EpisodeCard(
                    id = -1,
                    showId = it.id,
                    season = requestedSeason,
                    number = requestedNumber,
                    title = "S${requestedSeason.toString().padStart(2, '0')} E${requestedNumber.toString().padStart(2, '0')}",
                    overview = "",
                    airDate = null,
                )
            val seasonEpisodes = viewModel.loadEpisodes(it, requestedSeason)
            loadedEpisodes = (loadedEpisodes + seasonEpisodes + listOfNotNull(loadedEpisode))
                .distinctBy { candidate -> candidate.season to candidate.number }
                .sortedWith(compareBy(EpisodeCard::season, EpisodeCard::number))
        }
    }
    val currentEpisode = loadedEpisode
    if (currentEpisode == null) {
        AdaptiveBackground { LoadingPane(); GlassBackButton(onClick = onBack, modifier = Modifier.statusBarsPadding().padding(12.dp)) }
        return
    }
    val orderedEpisodes = remember(loadedEpisodes) {
        loadedEpisodes.distinctBy { it.season to it.number }
            .sortedWith(compareBy(EpisodeCard::season, EpisodeCard::number))
    }
    val currentIndex = orderedEpisodes.indexOfFirst {
        it.season == currentEpisode.season && it.number == currentEpisode.number
    }
    val previous = orderedEpisodes.getOrNull(currentIndex - 1)
    val next = orderedEpisodes.getOrNull(currentIndex + 1)
    val pagerState = rememberPagerState(initialPage = 1, pageCount = { 3 })
    val pagerScope = rememberCoroutineScope()
    LaunchedEffect(show?.stableKey, currentEpisode.season, currentEpisode.number) {
        show?.let {
            viewModel.loadEpisodeCast(it, currentEpisode.season, currentEpisode.number)
                .takeIf { cast -> cast.isNotEmpty() }
                ?.let { cast -> loadedPeople = cast }
        }
    }
    LaunchedEffect(pagerState, orderedEpisodes) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                if (page == 1) return@collect
                val active = loadedEpisode ?: return@collect
                val activeIndex = orderedEpisodes.indexOfFirst {
                    it.season == active.season && it.number == active.number
                }
                val target = if (page < 1) {
                    orderedEpisodes.getOrNull(activeIndex - 1)
                } else {
                    orderedEpisodes.getOrNull(activeIndex + 1)
                }
                if (target == null) {
                    pagerState.animateScrollToPage(1)
                    return@collect
                }
                // The new current page and the pager reset are applied before
                // the next frame, so page 0/2 becomes page 1 without a visual jump.
                loadedEpisode = target
                pagerState.scrollToPage(1)
            }
    }
    var selectedPerson by remember { mutableStateOf<PersonCard?>(null) }
    var showFullCast by remember(show?.stableKey) { mutableStateOf(false) }
    AdaptiveBackground(artworkUrl = currentEpisode.stillUrl ?: show?.backdropUrl ?: show?.posterUrl) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
            key = { it },
        ) { page ->
            val displayedEpisode = when (page) {
                0 -> previous ?: currentEpisode
                2 -> next ?: currentEpisode
                else -> currentEpisode
            }
            var watched by remember(displayedEpisode.season, displayedEpisode.number, displayedEpisode.watched) {
                mutableStateOf(displayedEpisode.watched)
            }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 0.dp),
            ) {
                item {
                    Box(Modifier.fillMaxWidth().statusBarsPadding().height(330.dp)) {
                        Box(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 74.dp, bottom = 46.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(24.dp)).background(Brush.linearGradient(listOf(Color(0xFF287C77), Color(0xFF202831))))) {
                            if (!displayedEpisode.stillUrl.isNullOrBlank()) AsyncImage(displayedEpisode.stillUrl, displayedEpisode.title, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .54f)))))
                        }
                        GlassBackButton(onClick = onBack, modifier = Modifier.padding(start = 16.dp, top = 2.dp))
                    }
                }
                item {
                    val sheetShape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
                    Column(
                        Modifier.fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color(0xFF171A20).copy(alpha = .46f),
                                        Color(0xFF11141A).copy(alpha = .56f),
                                    ),
                                ),
                                sheetShape,
                            )
                            .border(.6.dp, Color.White.copy(alpha = .10f), sheetShape)
                            .navigationBarsPadding()
                            .padding(top = 32.dp, bottom = 72.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(Modifier.padding(horizontal = 20.dp)) {
                            Text(show?.title.orEmpty().uppercase(), color = AccentLight, fontSize = 11.5.sp, letterSpacing = .7.sp, fontWeight = FontWeight.ExtraBold)
                            Text(displayedEpisode.title, color = Color.White, fontSize = 28.sp, lineHeight = 31.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text("${displayedEpisode.label} · ${formatDurationMinutes(displayedEpisode.runtimeMinutes)}", color = TextSecondary, fontSize = 12.sp)
                        }
                        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                            OutlinedButton(onClick = onSeries, modifier = Modifier.height(40.dp), shape = RoundedCornerShape(14.dp), border = BorderStroke(.7.dp, Color.White.copy(alpha = .28f))) {
                                Icon(Icons.Filled.Info, null, tint = TextSecondary, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(7.dp))
                                Text(stringResource(R.string.series_details), color = TextPrimary, fontSize = 12.sp, maxLines = 1)
                            }
                        }
                        GlassTextSection(stringResource(R.string.overview), displayedEpisode.overview)
                        PrimaryAction(
                            if (watched) stringResource(R.string.watched) else stringResource(R.string.mark_watched),
                            Icons.Filled.Check,
                            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                            containerColor = if (watched) Success else Accent,
                        ) {
                            val newWatched = !watched
                            watched = newWatched
                            onWatched(displayedEpisode.copy(watched = newWatched), newWatched)
                        }
                        if (loadedPeople.isNotEmpty()) CastSection(loadedPeople, onViewAll = { showFullCast = true }) { selectedPerson = it }
                        Column(Modifier.padding(horizontal = 20.dp).fillMaxWidth().glass().padding(14.dp)) {
                            SectionHeader(stringResource(R.string.useful_information))
                            Spacer(Modifier.height(12.dp))
                            Row {
                                InfoCell(Icons.Filled.CalendarMonth, stringResource(R.string.release_date), formatFullDate(displayedEpisode.airDate), Modifier.weight(1f))
                                InfoCell(Icons.Filled.Visibility, stringResource(R.string.status), if (watched) stringResource(R.string.watched) else stringResource(R.string.not_watched), Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(12.dp))
                            Row {
                                InfoCell(Icons.Filled.Schedule, stringResource(R.string.runtime), formatDurationMinutes(displayedEpisode.runtimeMinutes), Modifier.weight(1f))
                                InfoCell(Icons.Filled.Star, stringResource(R.string.rating), show?.score?.let { "%.1f / 10".format(it) } ?: "—", Modifier.weight(1f))
                            }
                        }
                        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = { pagerScope.launch { pagerState.animateScrollToPage(0) } }, enabled = previous != null, modifier = Modifier.weight(1f).height(44.dp), shape = RoundedCornerShape(999.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(5.dp)); Text(stringResource(R.string.previous_episode), fontSize = 11.sp, maxLines = 1) }
                            OutlinedButton(onClick = { pagerScope.launch { pagerState.animateScrollToPage(2) } }, enabled = next != null, modifier = Modifier.weight(1f).height(44.dp), shape = RoundedCornerShape(999.dp)) { Text(stringResource(R.string.next_episode), fontSize = 11.sp, maxLines = 1); Spacer(Modifier.width(5.dp)); Icon(Icons.AutoMirrored.Filled.ArrowForward, null, modifier = Modifier.size(17.dp)) }
                        }
                    }
                }
            }
        }
    }
    selectedPerson?.let { person ->
        ActorSheet(person, viewModel, onDismiss = { selectedPerson = null }, onMedia = onMedia)
    }
    if (showFullCast) {
        FullCastSheet(loadedPeople, onDismiss = { showFullCast = false }) { person ->
            showFullCast = false
            selectedPerson = person
        }
    }
}
