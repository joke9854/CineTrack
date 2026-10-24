@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.cinetrack.ui.screens

import com.cinetrack.ui.components.sharedBackdrop
import dev.chrisbanes.haze.hazeSource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import com.cinetrack.domain.watchSummary
import androidx.compose.ui.text.withStyle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.map
import androidx.compose.ui.draw.alpha
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.cinetrack.R
import com.cinetrack.domain.EpisodeCard
import com.cinetrack.domain.MediaCard
import com.cinetrack.domain.PersonCard
import com.cinetrack.ui.CineTrackViewModel
import com.cinetrack.ui.components.AdaptiveBackground
import com.cinetrack.ui.components.GlassBackButton
import com.cinetrack.ui.components.LoadingPane
import com.cinetrack.ui.components.PrimaryAction
import com.cinetrack.ui.components.SectionHeader
import com.cinetrack.ui.components.rememberDetailGlassState
import com.cinetrack.ui.theme.Success
import com.cinetrack.ui.theme.TextMuted
import com.cinetrack.ui.theme.TextPrimary
import com.cinetrack.ui.theme.TextSecondary
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import com.cinetrack.ui.theme.LocalDetailAccent
import com.cinetrack.ui.theme.Radius
import com.cinetrack.ui.theme.Spacing
import com.cinetrack.ui.theme.SurfacePalette

private val AccentLight: Color
    @Composable get() = LocalDetailAccent.current?.value?.let { androidx.compose.ui.graphics.lerp(it, Color.White, .32f) }
        ?: com.cinetrack.ui.theme.AccentLight

@Composable
fun EpisodeDetailScreen(
    show: MediaCard?,
    episode: EpisodeCard?,
    requestedSeason: Int,
    requestedNumber: Int,
    allEpisodes: List<EpisodeCard>,
    people: List<PersonCard>,
    viewModel: CineTrackViewModel,
    detailViewModel: com.cinetrack.ui.detail.DetailViewModel,
    progressViewModel: com.cinetrack.ui.progress.ProgressViewModel,
    onBack: () -> Unit,
    onSeries: () -> Unit,
    onMedia: (MediaCard) -> Unit,
    onWatched: (EpisodeCard, Boolean) -> Unit,
) {
    var loadedEpisode by remember(show?.id) { mutableStateOf(episode) }
    var loadedEpisodes by remember(show?.id) { mutableStateOf(allEpisodes) }
    // Each episode's cast, kept while the page is open; the episodes beside the
    // current one are fetched ahead, so swiping shows their cast right away.
    val castByEpisode = remember(show?.id) { androidx.compose.runtime.mutableStateMapOf<Pair<Int, Int>, List<PersonCard>>() }
    // Which episode's plot was asked for; others show a skeleton until they load.
    var overviewFor by remember(show?.id) { mutableStateOf<Pair<Int, Int>?>(null) }
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
            loadedEpisode = (detailViewModel.loadEpisode(it, requestedSeason, requestedNumber)
                ?: loadedEpisode)
                .also { overviewFor = requestedSeason to requestedNumber }
                ?: EpisodeCard(
                    id = -1,
                    showId = it.id,
                    season = requestedSeason,
                    number = requestedNumber,
                    title = com.cinetrack.domain.episodeCode(requestedSeason, requestedNumber),
                    overview = "",
                    airDate = null,
                )
            val seasonEpisodes = detailViewModel.loadEpisodes(it, requestedSeason)
            loadedEpisodes = (loadedEpisodes + seasonEpisodes + listOfNotNull(loadedEpisode))
                .distinctBy { candidate -> candidate.season to candidate.number }
                .sortedWith(compareBy(EpisodeCard::season, EpisodeCard::number))
        }
    }
    val currentEpisode = loadedEpisode
    if (currentEpisode == null) {
        AdaptiveBackground { LoadingPane(); GlassBackButton(onClick = onBack, modifier = Modifier.statusBarsPadding().padding(Spacing.md)) }
        return
    }
    val orderedEpisodes = remember(loadedEpisodes) {
        loadedEpisodes.distinctBy { it.season to it.number }
            .sortedWith(compareBy(EpisodeCard::season, EpisodeCard::number))
    }
    // One page per episode: the page swiped to is the page that stays, so
    // nothing is swapped or redrawn when a swipe settles.
    val pages = remember(orderedEpisodes, currentEpisode.season, currentEpisode.number) {
        if (orderedEpisodes.any { it.season == currentEpisode.season && it.number == currentEpisode.number }) orderedEpisodes
        else (orderedEpisodes + currentEpisode).sortedWith(compareBy(EpisodeCard::season, EpisodeCard::number))
    }
    fun pageOf(target: EpisodeCard) = pages.indexOfFirst { it.season == target.season && it.number == target.number }
    val pagerState = rememberPagerState(initialPage = pageOf(currentEpisode).coerceAtLeast(0), pageCount = { pages.size })
    val pagerScope = rememberCoroutineScope()
    val showCast by androidx.compose.runtime.rememberUpdatedState(people)
    LaunchedEffect(show?.stableKey, currentEpisode.season, currentEpisode.number, pages.size) {
        val series = show ?: return@LaunchedEffect
        val index = pageOf(currentEpisode)
        listOfNotNull(currentEpisode, pages.getOrNull(index + 1), pages.getOrNull(index - 1)).forEach { target ->
            val key = target.season to target.number
            if (key !in castByEpisode) {
                castByEpisode[key] = runCatching { detailViewModel.loadEpisodeCast(series, target.season, target.number) }.getOrDefault(emptyList()).ifEmpty { showCast }
            }
        }
        // The show's TVDB character pictures fill in after the TMDB cast is on screen.
        listOfNotNull(currentEpisode, pages.getOrNull(index + 1), pages.getOrNull(index - 1)).forEach { target ->
            val key = target.season to target.number
            castByEpisode[key]?.let { cast -> castByEpisode[key] = detailViewModel.withCharacterPictures(series, cast, remember = false) }
        }
    }
    // Episodes arriving (the season loading) move the current one to another index.
    LaunchedEffect(pages) {
        val index = pageOf(currentEpisode)
        if (index >= 0 && index != pagerState.currentPage && !pagerState.isScrollInProgress) pagerState.scrollToPage(index)
    }
    LaunchedEffect(pagerState, pages) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .drop(1) // the page at (re)start, before any swipe
            .collect { page ->
                val target = pages.getOrNull(page) ?: return@collect
                val active = loadedEpisode
                if (active == null || active.season != target.season || active.number != target.number) loadedEpisode = target
            }
    }
    val loadedPeople = castByEpisode[currentEpisode.season to currentEpisode.number] ?: people
    var selectedPerson by remember { mutableStateOf<PersonCard?>(null) }
    var showFullCast by remember(show?.stableKey) { mutableStateOf(false) }
    var showCharacters by remember(show?.stableKey) { mutableStateOf(true) }
    val history by remember(viewModel) { viewModel.state.map { it.history }.distinctUntilChanged() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val posterAccent = com.cinetrack.ui.components.rememberPosterAccent(show?.posterUrl ?: show?.backdropUrl, rememberPosterAccentEnabled(viewModel))
    androidx.compose.runtime.CompositionLocalProvider(LocalDetailAccent provides posterAccent) {
    val detailGlassState = rememberDetailGlassState()
    var activeListState by remember { mutableStateOf<androidx.compose.foundation.lazy.LazyListState?>(null) }
    AdaptiveBackground(
        artworkUrl = currentEpisode.stillUrl ?: show?.backdropUrl ?: show?.posterUrl,
        hazeState = detailGlassState,
    ) {
        val headerHaze = remember { dev.chrisbanes.haze.HazeState() }
        Box(Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize().hazeSource(headerHaze),
            beyondViewportPageCount = 0,
            key = { index -> pages.getOrNull(index)?.let { "${it.season}:${it.number}" } ?: index },
        ) { page ->
            val displayedEpisode = pages.getOrNull(page) ?: currentEpisode
            val previous = pages.getOrNull(page - 1)
            val next = pages.getOrNull(page + 1)
            var watched by remember(displayedEpisode.season, displayedEpisode.number, displayedEpisode.watched) {
                mutableStateOf(displayedEpisode.watched)
            }
            val pageListState = remember(displayedEpisode.season, displayedEpisode.number) { androidx.compose.foundation.lazy.LazyListState() }
            if (page == pagerState.currentPage) androidx.compose.runtime.SideEffect { activeListState = pageListState }
            LazyColumn(
                Modifier.fillMaxSize(),
                state = pageListState,
                contentPadding = PaddingValues(bottom = 0.dp),
            ) {
                item {
                    Box(Modifier.fillMaxWidth()) {
                        (displayedEpisode.stillUrl ?: show?.backdropUrl)?.takeIf(String::isNotBlank)?.let { art ->
                            AsyncImage(com.cinetrack.ui.components.rememberBlurredArtRequest(art), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                            Box(Modifier.matchParentSize().background(Brush.verticalGradient(0f to Color(0x33080A0C), .55f to Color(0x4D080A0C), 1f to Color.Transparent)))
                        }
                    Box(Modifier.fillMaxWidth().statusBarsPadding().height(330.dp)) {
                        // The tapped Progress card grows into this still; its art stands in until the still loads.
                        Box(Modifier.fillMaxWidth().padding(start = Spacing.xl, end = Spacing.xl, top = 74.dp, bottom = 46.dp).aspectRatio(16f / 9f)
                            .sharedBackdrop("ep:${displayedEpisode.showId}:${displayedEpisode.season}:${displayedEpisode.number}", slot = null)
                            .shadow(22.dp, RoundedCornerShape(Radius.Large), clip = false).clip(RoundedCornerShape(Radius.Large)).background(Brush.linearGradient(listOf(SurfacePalette.EpisodeTeal, SurfacePalette.EpisodeSurface)))) {
                            if (!displayedEpisode.stillUrl.isNullOrBlank()) com.cinetrack.ui.components.SharedPosterTargetImage(displayedEpisode.stillUrl, displayedEpisode.title, "ep:${displayedEpisode.showId}:${displayedEpisode.season}:${displayedEpisode.number}", Modifier.fillMaxSize(), ContentScale.Crop)
                            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .54f)))))
                        }
                    }
                    }
                }
                item {
                    Column(
                        Modifier.fillMaxWidth()
                            .shelfFeather()
                            .navigationBarsPadding()
                            .padding(top = Spacing.xxxl, bottom = 72.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = DetailLayout.Gutter), horizontalAlignment = Alignment.CenterHorizontally) {
                            // The series as a small chip with its poster; it opens the series page.
                            Row(
                                Modifier.clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = .08f))
                                    .clickable(onClick = onSeries).padding(start = 4.dp, end = 14.dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // A rounded chip, not a pill: its corners stay concentric with the poster's (12dp = 8dp + the 4dp inset).
                                show?.posterUrl?.takeIf(String::isNotBlank)?.let {
                                    AsyncImage(it, null, Modifier.width(24.dp).height(34.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text(show?.title.orEmpty(), color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = TextMuted, modifier = Modifier.padding(start = 6.dp).size(13.dp))
                            }
                            Text(displayedEpisode.title, color = Color.White, style = androidx.compose.material3.MaterialTheme.typography.displaySmall, lineHeight = 31.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp))
                            val episodeFacts = listOfNotNull(
                                formatFullDate(displayedEpisode.airDate).takeIf { it.isNotBlank() && it != "—" },
                                formatDurationMinutes(displayedEpisode.runtimeMinutes).takeIf(String::isNotBlank),
                                displayedEpisode.score?.let { "★ " + String.format(com.cinetrack.ui.currentLocale(), "%.1f", it) },
                            )
                            Text(
                                androidx.compose.ui.text.buildAnnotatedString {
                                    withStyle(androidx.compose.ui.text.SpanStyle(color = AccentLight, fontWeight = FontWeight.ExtraBold)) {
                                        append(displayedEpisode.code)
                                    }
                                    episodeFacts.forEach { append(" · "); append(it) }
                                },
                                color = TextSecondary,
                                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                        // Previous episode, watched, next episode; the arrows say where they lead.
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = DetailLayout.Gutter), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            EpisodeStepButton(previous?.let { stepLabel(it, displayedEpisode) }, forward = false, description = stringResource(R.string.previous_episode)) {
                                pagerScope.launch { pagerState.animateScrollToPage(page - 1) }
                            }
                            PrimaryAction(
                                if (watched) stringResource(R.string.watched_tv) else stringResource(R.string.mark_watched),
                                Icons.Filled.Check,
                                Modifier.weight(1f),
                                containerColor = if (watched) Success else SurfacePalette.NeutralControl,
                                compact = true,
                                liveGlass = true,
                                hazeState = detailGlassState,
                            ) {
                                val newWatched = !watched
                                watched = newWatched
                                onWatched(displayedEpisode.copy(watched = newWatched), newWatched)
                            }
                            EpisodeStepButton(next?.let { stepLabel(it, displayedEpisode) }, forward = true, description = stringResource(R.string.next_episode)) {
                                pagerScope.launch { pagerState.animateScrollToPage(page + 1) }
                            }
                        }
                        val seasonLine = orderedEpisodes.filter { it.season == displayedEpisode.season }
                        if (seasonLine.size in 2..60) SeasonDashes(seasonLine, displayedEpisode.number, watched)
                        if (watched && show != null) {
                            remember(history, show.stableKey, displayedEpisode.season, displayedEpisode.number) {
                                watchSummary(history, show.stableKey, displayedEpisode.season, displayedEpisode.number)
                            }?.let { WatchSummaryLine(it) }
                        }
                        val airDate = displayedEpisode.airDate
                        if (show != null && !watched && airDate != null && daysUntil(airDate)?.let { it >= 0 } == true) {
                            ReleaseCountdown(show, airDate, displayedEpisode, viewModel, progressViewModel)
                        }
                        }
                        val episodeKey = displayedEpisode.season to displayedEpisode.number
                        val pageCast = castByEpisode[episodeKey]
                        LoadedSection(displayedEpisode.overview.isNotBlank() || overviewFor == episodeKey || pageCast != null, { OverviewSkeleton() }) {
                            if (displayedEpisode.overview.isNotBlank()) PlotSection(displayedEpisode.overview)
                        }
                        LoadedSection(pageCast != null, { CastSkeleton() }) {
                            if (!pageCast.isNullOrEmpty()) CastSection(pageCast, showCharacters, { showCharacters = it }, onViewAll = { showFullCast = true }) { selectedPerson = it }
                        }
                        if (pageCast != null) {
                            // Episode credits list crew by their job; show who directed and wrote it.
                            val directors = pageCast.filter { it.role.equals("Director", ignoreCase = true) }.map(PersonCard::name).distinct()
                            val writers = pageCast.filter { person -> listOf("Writer", "Teleplay", "Screenplay", "Story").any { person.role.equals(it, ignoreCase = true) } }
                                .map(PersonCard::name).distinct()
                            val rows = buildList {
                                if (directors.isNotEmpty()) add(stringResource(R.string.director_label) to directors.joinToString(", "))
                                if (writers.isNotEmpty()) add(stringResource(R.string.writer_label) to writers.take(3).joinToString(", "))
                            }
                            if (rows.isNotEmpty()) DetailsList(stringResource(R.string.details), rows)
                        }
                        val seasonRows = orderedEpisodes.filter { it.season == displayedEpisode.season }
                        if (seasonRows.size > 1) {
                            val index = seasonRows.indexOfFirst { it.number == displayedEpisode.number }.coerceAtLeast(0)
                            val start = (index - 1).coerceIn(0, (seasonRows.size - 3).coerceAtLeast(0))
                            Column(Modifier.fillMaxWidth().padding(horizontal = DetailLayout.Gutter)) {
                                SectionHeader(stringResource(R.string.season_number, displayedEpisode.season))
                                seasonRows.drop(start).take(3).forEachIndexed { rowIndex, episode ->
                                    if (rowIndex > 0) EpisodeDivider()
                                    val isThis = episode.number == displayedEpisode.number
                                    EpisodeRow(
                                        episode.copy(watched = if (isThis) watched else episode.watched),
                                        subtitle = if (isThis) stringResource(R.string.this_episode) else formatFullDate(episode.airDate),
                                        subtitleAccent = false,
                                        highlight = TextPrimary.takeIf { isThis },
                                        onClick = {
                                            val target = pageOf(episode)
                                            if (!isThis && target >= 0) pagerScope.launch { pagerState.animateScrollToPage(target) }
                                        },
                                        onWatched = {
                                            if (isThis) {
                                                val newWatched = !watched
                                                watched = newWatched
                                                onWatched(displayedEpisode.copy(watched = newWatched), newWatched)
                                            } else {
                                                val newWatched = !episode.watched
                                                loadedEpisodes = loadedEpisodes.map {
                                                    if (it.season == episode.season && it.number == episode.number) it.copy(watched = newWatched) else it
                                                }
                                                onWatched(episode.copy(watched = newWatched), newWatched)
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        val headerProgress = rememberHeaderProgress(activeListState, revealStart = 230.dp)
        DetailTopBar(
            title = currentEpisode.title,
            subtitle = listOfNotNull(show?.title, currentEpisode.code).joinToString(" · "),
            progress = headerProgress,
            haze = headerHaze,
            onBack = onBack,
        )
        }
    }
    selectedPerson?.let { person ->
        ActorSheet(person, detailViewModel, onDismiss = { selectedPerson = null }, onMedia = onMedia)
    }
    if (showFullCast) {
        FullCastSheet(loadedPeople, showCharacters, onDismiss = { showFullCast = false }) { person ->
            showFullCast = false
            selectedPerson = person
        }
    }
}
}
