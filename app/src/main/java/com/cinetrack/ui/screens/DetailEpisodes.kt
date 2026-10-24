@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.cinetrack.ui.screens

import com.cinetrack.ui.components.sharedBackdrop
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import com.cinetrack.ui.components.liquidGlassPill
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.RemoveDone
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import com.cinetrack.domain.WatchSummary
import com.cinetrack.domain.episodeWatchCounts
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.map
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.cinetrack.R
import com.cinetrack.domain.EpisodeCard
import com.cinetrack.domain.latestEpisodeWatchTimes
import com.cinetrack.domain.MediaCard
import com.cinetrack.domain.PersonCard
import com.cinetrack.domain.TimelineCard
import com.cinetrack.ui.CineTrackViewModel
import com.cinetrack.ui.components.PrimaryAction
import com.cinetrack.ui.components.SectionHeader
import com.cinetrack.ui.components.SharedGlassSheet
import com.cinetrack.ui.components.GlassMaterial
import com.cinetrack.ui.components.glass
import com.cinetrack.ui.components.rememberUiAction
import com.cinetrack.ui.theme.Info
import com.cinetrack.ui.theme.Success
import com.cinetrack.ui.theme.TextMuted
import com.cinetrack.ui.theme.Motion
import com.cinetrack.ui.theme.TextPrimary
import com.cinetrack.ui.theme.TextSecondary
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import com.cinetrack.ui.components.fillMaxWidthOf
import com.cinetrack.ui.theme.LocalDetailAccent
import com.cinetrack.ui.theme.Radius
import com.cinetrack.ui.theme.Spacing
import com.cinetrack.ui.theme.SurfacePalette

/** On detail pages the accent follows the poster (see [LocalDetailAccent]). */
private val Accent: Color
    @Composable get() = LocalDetailAccent.current?.value ?: com.cinetrack.ui.theme.Accent
private val AccentLight: Color
    @Composable get() = LocalDetailAccent.current?.value?.let { androidx.compose.ui.graphics.lerp(it, Color.White, .32f) }
        ?: com.cinetrack.ui.theme.AccentLight

@Composable
internal fun EpisodesSection(
    media: MediaCard,
    episodes: List<EpisodeCard>,
    history: List<TimelineCard>,
    onEpisode: (EpisodeCard) -> Unit,
    initialSeason: Int?,
    initialEpisode: Int?,
    onEpisodeWatched: (EpisodeCard, Boolean) -> Unit,
    onSeasonWatched: (List<EpisodeCard>, Boolean) -> Unit,
    onSeasonInfo: (Int) -> Unit,
    posterPicker: Boolean,
    onPosterPicker: (Boolean) -> Unit,
) {
    val grouped = remember(episodes) { episodes.groupBy(EpisodeCard::season).toSortedMap() }
    val watchedAtByEpisode = remember(history) { latestEpisodeWatchTimes(history) }
    val watchCounts = remember(history, media.id) { episodeWatchCounts(history, media.id) }
    val nextEpisode = remember(episodes) {
        val ordered = episodes.filter { it.season > 0 }.sortedWith(compareBy(EpisodeCard::season, EpisodeCard::number))
        // Only a show you've started has a next episode to point at.
        val lastWatched = ordered.lastOrNull(EpisodeCard::watched) ?: return@remember null
        ordered.firstOrNull { candidate ->
            !candidate.watched && (
                candidate.season > lastWatched.season ||
                    (candidate.season == lastWatched.season && candidate.number > lastWatched.number)
                )
        }
    }
    val defaultSeason = initialSeason?.takeIf { it in grouped }
        ?: nextEpisode?.season?.takeIf { it in grouped }
        ?: grouped.keys.firstOrNull { it > 0 } ?: grouped.keys.firstOrNull()
    var selectedSeason by rememberSaveable(media.stableKey) { mutableStateOf(defaultSeason) }
    LaunchedEffect(initialSeason, grouped.keys) {
        if (initialSeason != null && grouped.containsKey(initialSeason)) selectedSeason = initialSeason
    }
    val season = selectedSeason?.takeIf { it in grouped } ?: defaultSeason ?: return
    val seasonEpisodes = grouped[season].orEmpty()
    val summary = media.seasons.firstOrNull { it.number == season }
    val total = (summary?.episodeCount ?: seasonEpisodes.size).coerceAtLeast(seasonEpisodes.size).coerceAtLeast(1)
    val watched = seasonEpisodes.count(EpisodeCard::watched)
    val allWatched = seasonEpisodes.isNotEmpty() && watched == seasonEpisodes.size
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val seasonKeys = remember(grouped) { grouped.keys.toList() }
    // Pills and covers each keep their own row state: one state shared by both
    // rows while they crossfade leaves the incoming row blank for a few frames.
    val pillState = rememberLazyListState()
    val coverState = rememberLazyListState()
    LaunchedEffect(season, posterPicker) {
        // Keep the chosen season in view, with its neighbour before it.
        val target = (seasonKeys.indexOf(season) - 1).coerceAtLeast(0)
        val (shown, hidden) = if (posterPicker) coverState to pillState else pillState to coverState
        hidden.scrollToItem(target)
        shown.animateScrollToItem(target)
    }
    val progress = androidx.compose.animation.core.animateFloatAsState(
        watched.toFloat() / total,
        Motion.gentle(),
        label = "seasonProgress",
    )
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = DetailLayout.Gutter), verticalAlignment = Alignment.CenterVertically) {
            SectionHeader(stringResource(R.string.episodes), Modifier.weight(1f))
            Text(
                stringResource(if (posterPicker) R.string.season_style_text else R.string.season_style_posters),
                color = TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                modifier = Modifier.glass(RoundedCornerShape(Radius.Pill))
                    .clickable(role = androidx.compose.ui.semantics.Role.Switch) { onPosterPicker(!posterPicker) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        // Switching style: the old row fades out and the new row fades and grows in. The
        // height snaps here; the section surface animates it once, so the list below moves once.
        androidx.compose.animation.AnimatedContent(
            posterPicker,
            transitionSpec = {
                val enter = androidx.compose.animation.fadeIn(Motion.enter(Motion.Medium, delay = 90)) +
                    androidx.compose.animation.scaleIn(Motion.enter(Motion.Long, delay = 90), initialScale = .94f, transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, .5f))
                val exit = androidx.compose.animation.fadeOut(Motion.exit(90))
                androidx.compose.animation.ContentTransform(
                    enter, exit,
                    sizeTransform = androidx.compose.animation.SizeTransform(clip = false) { _, _ -> androidx.compose.animation.core.snap() },
                )
            },
            label = "seasonPickerStyle",
        ) { covers ->
            LazyRow(state = if (covers) coverState else pillState, contentPadding = PaddingValues(horizontal = DetailLayout.Gutter), horizontalArrangement = Arrangement.spacedBy(if (covers) 10.dp else 8.dp)) {
                items(seasonKeys, key = { it }) { number ->
                    val chosen = number == season
                    // The accent fades between choices instead of jumping.
                    val fill by androidx.compose.animation.core.animateFloatAsState(
                        if (chosen) 1f else 0f, Motion.snappy(), label = "seasonChoice",
                    )
                    val label = if (number == 0) stringResource(R.string.specials_short)
                    else stringResource(if (covers) R.string.season_number else R.string.season_short, number)
                    if (covers) {
                        val cover = media.seasons.firstOrNull { it.number == number }?.posterUrl ?: media.posterUrl
                        val shape = RoundedCornerShape(Radius.Small)
                        Column(
                            Modifier.width(92.dp).clip(shape)
                                .clickable(role = androidx.compose.ui.semantics.Role.Tab) { selectedSeason = number },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(shape)
                                    .background(SurfacePalette.ArtworkBackdrop)
                                    .border(2.dp, Accent.copy(alpha = fill), shape),
                            ) {
                                if (!cover.isNullOrBlank()) AsyncImage(cover, label, Modifier.fillMaxSize().padding(2.dp).clip(shape), contentScale = ContentScale.Crop)
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                label, color = androidx.compose.ui.graphics.lerp(TextMuted, TextPrimary, fill), maxLines = 1,
                                style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                                fontWeight = if (chosen) FontWeight.Bold else FontWeight.Medium,
                            )
                        }
                    } else {
                        val pill = RoundedCornerShape(Radius.Pill)
                        val accent = Accent
                        val ink = if (accent.luminance() > .6f) Color.Black else Color.White
                        Text(
                            label,
                            color = androidx.compose.ui.graphics.lerp(TextSecondary, ink, fill),
                            style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                            fontWeight = if (chosen) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.glass(pill).drawBehind { drawRect(accent.copy(alpha = fill)) }
                                .clickable(role = androidx.compose.ui.semantics.Role.Tab) { selectedSeason = number }
                                .padding(horizontal = 16.dp, vertical = 9.dp),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().padding(start = DetailLayout.Gutter, end = DetailLayout.Gutter - 8.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.animation.Crossfade(stringResource(R.string.season_progress, watched, total), label = "seasonCount") { label ->
                Text(label, color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(SurfacePalette.ProgressTrack)) {
                Box(Modifier.fillMaxWidthOf { progress.value }.fillMaxHeight().clip(CircleShape).background(Accent))
            }
            Spacer(Modifier.width(6.dp))
            Box(Modifier.size(44.dp).clip(CircleShape).clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = rememberUiAction { onSeasonInfo(season) }), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Info, stringResource(R.string.show_season_info, season), tint = TextSecondary, modifier = Modifier.size(19.dp))
            }
            Box(Modifier.size(44.dp).clip(CircleShape).clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = rememberUiAction {
                haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.Confirm)
                onSeasonWatched(seasonEpisodes, !allWatched)
            }), contentAlignment = Alignment.Center) {
                WatchedCircle(allWatched, stringResource(if (allWatched) R.string.mark_unwatched else R.string.mark_watched))
            }
        }
        // The list slides in from the side of the season you picked; its rows follow one after another.
        androidx.compose.animation.AnimatedContent(
            season,
            transitionSpec = {
                val forward = targetState > initialState
                val enter = androidx.compose.animation.slideInHorizontally(Motion.enter(Motion.Long)) { width -> if (forward) width / 4 else -width / 4 } +
                    androidx.compose.animation.fadeIn(Motion.enter(Motion.Medium, delay = 60))
                val exit = androidx.compose.animation.slideOutHorizontally(Motion.exit(Motion.Medium)) { width -> if (forward) -width / 6 else width / 6 } +
                    androidx.compose.animation.fadeOut(Motion.exit(Motion.Short))
                // Height snaps here too; the section surface is the one size animation.
                androidx.compose.animation.ContentTransform(
                    enter, exit,
                    sizeTransform = androidx.compose.animation.SizeTransform(clip = false) { _, _ -> androidx.compose.animation.core.snap() },
                )
            },
            label = "seasonEpisodes",
        ) { shown ->
            val shownEpisodes = grouped[shown].orEmpty()
            var showAll by rememberSaveable(media.stableKey, shown) { mutableStateOf(false) }
            // A long season opens on the episode you came from, or your next one, with one before it.
            val anchor = shownEpisodes.indexOfFirst { shown == initialSeason && it.number == initialEpisode }.takeIf { it >= 0 }
                ?: shownEpisodes.indexOfFirst { it.season == nextEpisode?.season && it.number == nextEpisode.number }.takeIf { it >= 0 }
                ?: 0
            val start = (anchor - 1).coerceIn(0, (shownEpisodes.size - FoldedEpisodeCount).coerceAtLeast(0))
            val folded = !showAll && shownEpisodes.size > FoldEpisodesAbove
            val visible = if (folded) shownEpisodes.subList(start, (start + FoldedEpisodeCount).coerceAtMost(shownEpisodes.size)) else shownEpisodes
            Column(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = DetailLayout.Gutter)) {
                    visible.forEachIndexed { index, episode ->
                        val returnTarget = episode.season == initialSeason && episode.number == initialEpisode
                        val isNext = episode.season == nextEpisode?.season && episode.number == nextEpisode.number
                        val watchedAt = watchedAtByEpisode[episode.season to episode.number]
                        // Stagger capped at the sixth row, so long seasons don't trickle in.
                        val delay = 40 + index.coerceAtMost(StaggerCap) * 40
                        Column(
                            Modifier.animateEnterExit(
                                enter = androidx.compose.animation.fadeIn(Motion.enter(Motion.Medium, delay = delay)) +
                                    androidx.compose.animation.slideInVertically(Motion.enter(Motion.Long, delay = delay)) { it / 5 },
                                exit = androidx.compose.animation.ExitTransition.None,
                            ),
                        ) {
                            if (index > 0) EpisodeDivider()
                            EpisodeRow(
                                episode,
                                subtitle = when {
                                    isNext -> listOfNotNull(stringResource(R.string.next_short), formatDurationMinutes(episode.runtimeMinutes).takeIf(String::isNotBlank)).joinToString(" · ")
                                    episode.watched && !watchedAt.isNullOrBlank() && (watchCounts[episode.season to episode.number] ?: 0) > 1 -> {
                                        val times = watchCounts[episode.season to episode.number] ?: 0
                                        androidx.compose.ui.res.pluralStringResource(R.plurals.watched_times, times, times, formatFullDate(watchedAt))
                                    }
                                    episode.watched && !watchedAt.isNullOrBlank() -> stringResource(R.string.watched_on, formatFullDate(watchedAt))
                                    else -> formatFullDate(episode.airDate)
                                },
                                subtitleAccent = isNext,
                                highlight = if (returnTarget || isNext) Accent else null,
                                onClick = { onEpisode(episode) },
                                onWatched = { onEpisodeWatched(episode, !episode.watched) },
                            )
                        }
                    }
                }
                if (folded) {
                    Text(
                        stringResource(R.string.show_all_episodes, shownEpisodes.size), color = AccentLight,
                        style = androidx.compose.material3.MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = DetailLayout.Gutter - 8.dp).clip(RoundedCornerShape(Radius.Pill))
                            .clickable { showAll = true }.padding(horizontal = 8.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

private const val StaggerCap = 6

internal fun stepLabel(target: EpisodeCard, from: EpisodeCard): String =
    if (target.season == from.season) "E${target.number}" else "S${target.season} E${target.number}"

@Composable
internal fun EpisodeStepButton(label: String?, forward: Boolean, description: String, onClick: () -> Unit) {
    val enabled = label != null
    Row(
        Modifier.height(50.dp).liquidGlassPill(under = GlassMaterial.DetailSurface)
            .clip(RoundedCornerShape(Radius.Pill))
            .clickable(enabled = enabled, role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tint = if (enabled) TextPrimary else TextMuted
        if (!forward) Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = tint, modifier = Modifier.size(16.dp))
        if (label != null) {
            Text(label, color = tint, style = androidx.compose.material3.MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 5.dp))
        }
        if (forward) Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = tint, modifier = Modifier.size(16.dp))
    }
}

/** One dash per episode of the season: watched in accent, this one bright, the rest dim. */
@Composable
internal fun SeasonDashes(episodes: List<EpisodeCard>, current: Int, currentWatched: Boolean) {
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 56.dp), contentAlignment = Alignment.Center) {
        val gap = 3.dp
        val width = ((maxWidth - gap * (episodes.size - 1)) / episodes.size).coerceIn(3.dp, 16.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            episodes.forEach { episode ->
                val isCurrent = episode.number == current
                val seen = if (isCurrent) currentWatched else episode.watched
                val color by androidx.compose.animation.animateColorAsState(
                    when {
                        isCurrent && !seen -> TextPrimary
                        seen -> Accent
                        else -> Color.White.copy(alpha = .14f)
                    },
                    label = "seasonDash",
                )
                Box(Modifier.width(width).height(if (isCurrent) 4.dp else 3.dp).clip(CircleShape).drawBehind { drawRect(color) })
            }
        }
    }
}

/** Whole days from today until [raw]'s date; negative once it has passed, null without a date. */
internal fun daysUntil(raw: String?): Int? {
    val day = raw?.take(10)?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() } ?: return null
    return java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.now(), day).toInt()
}

/** "Esce tra 5 giorni" for an upcoming film or episode, with a bell that reminds you on the day. */
@Composable
internal fun ReleaseCountdown(media: MediaCard, release: String, episode: EpisodeCard?, viewModel: CineTrackViewModel, progressViewModel: com.cinetrack.ui.progress.ProgressViewModel) {
    val days = daysUntil(release) ?: return
    val context = LocalContext.current
    val reminders by progressViewModel.releaseReminders.collectAsStateWithLifecycle()
    val byDefault by remember(viewModel, media.stableKey, episode?.season, episode?.number) {
        viewModel.state.map { com.cinetrack.ui.progress.notifiesByDefault(it, media, episode?.season, episode?.number) }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = false)
    val reminderOn = progressViewModel.isReleaseReminderOn(reminders, media, release, episode?.season, episode?.number, byDefault)
    val permission = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }
    val whenText = when (days) {
        0 -> stringResource(R.string.release_today)
        1 -> stringResource(R.string.release_tomorrow)
        else -> androidx.compose.ui.res.pluralStringResource(R.plurals.release_in_days, days, days)
    }
    val label = if (episode != null) "S${episode.season} E${episode.number} · $whenText" else "$whenText · ${formatFullDate(release)}"
    Row(
        Modifier.padding(horizontal = DetailLayout.Gutter).fillMaxWidth().glass(RoundedCornerShape(16.dp))
            .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.CalendarMonth, null, tint = AccentLight, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Box(
            Modifier.size(44.dp).clip(CircleShape).clickable(role = androidx.compose.ui.semantics.Role.Switch) {
                val enable = !reminderOn
                if (enable && android.os.Build.VERSION.SDK_INT >= 33 &&
                    androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
                ) permission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                progressViewModel.setReleaseReminder(media, release, episode?.season, episode?.number, episode?.title, enable, byDefault)
            },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (reminderOn) Icons.Filled.NotificationsActive else Icons.Outlined.NotificationsNone,
                stringResource(if (reminderOn) R.string.reminder_on else R.string.reminder_off),
                tint = if (reminderOn) AccentLight else TextSecondary, modifier = Modifier.size(21.dp),
            )
        }
    }
}

/** "Visto il 12/10/2026", or "Visto 2 volte · ultimo …" once rewatched. */
@Composable
internal fun WatchSummaryLine(summary: WatchSummary) {
    val date = formatFullDate(summary.last)
    Row(Modifier.fillMaxWidth().padding(horizontal = DetailLayout.Gutter), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Check, null, tint = Success, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            if (summary.count > 1) androidx.compose.ui.res.pluralStringResource(R.plurals.watched_times, summary.count, summary.count, date)
            else stringResource(R.string.watched_on, date),
            color = TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
        )
    }
}

/** The first aired episode after the last one you watched, or null for a show you haven't started. */
/** Regular episodes that have aired (today included) and are not watched yet. */
internal fun unwatchedAiredEpisodes(episodes: List<EpisodeCard>): List<EpisodeCard> {
    val today = java.time.LocalDate.now()
    return episodes
        .filter { episode ->
            episode.season > 0 && !episode.watched &&
                episode.airDate?.take(10)?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }?.let { !it.isAfter(today) } == true
        }
        .sortedWith(compareBy(EpisodeCard::season, EpisodeCard::number))
}

internal fun nextAiredEpisode(episodes: List<EpisodeCard>): EpisodeCard? {
    val ordered = episodes.filter { it.season > 0 }.sortedWith(compareBy(EpisodeCard::season, EpisodeCard::number))
    val last = ordered.lastOrNull(EpisodeCard::watched) ?: return null
    val today = java.time.LocalDate.now()
    return ordered.firstOrNull { candidate ->
        !candidate.watched && (candidate.season > last.season || (candidate.season == last.season && candidate.number > last.number))
    }?.takeIf { candidate ->
        candidate.airDate?.take(10)?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }?.let { !it.isAfter(today) } == true
    }
}

/** Where you are in a started series: the current season on the left, the whole show on the right. */
@Composable
internal fun SeriesProgressCard(media: MediaCard, episodes: List<EpisodeCard>, nextUp: EpisodeCard?, onMarkAll: (() -> Unit)? = null) {
    val regular = remember(episodes) { episodes.filter { it.season > 0 } }
    val focusSeason = nextUp?.season ?: regular.filter(EpisodeCard::watched).maxOfOrNull(EpisodeCard::season) ?: return
    val inSeason = regular.filter { it.season == focusSeason }
    val seasonTotal = (media.seasons.firstOrNull { it.number == focusSeason }?.episodeCount ?: 0).coerceAtLeast(inSeason.size)
    val total = media.seasons.filter { it.number > 0 }.sumOf { it.episodeCount }.coerceAtLeast(regular.size).coerceAtLeast(1)
    val watched = regular.count(EpisodeCard::watched)
    val progress = androidx.compose.animation.core.animateFloatAsState(watched.toFloat() / total, label = "seriesProgress")
    Column(Modifier.padding(horizontal = DetailLayout.Gutter).fillMaxWidth().glass(RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.series_progress_season, focusSeason, inSeason.count(EpisodeCard::watched), seasonTotal),
                color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.series_progress_total, watched, total), color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.height(9.dp))
        Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(SurfacePalette.ProgressTrack)) {
            Box(Modifier.fillMaxWidthOf { progress.value }.fillMaxHeight().clip(CircleShape).background(Accent))
        }
        if (onMarkAll != null) {
            Row(
                Modifier.padding(top = 10.dp).align(Alignment.End).clip(RoundedCornerShape(Radius.Pill))
                    .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onMarkAll)
                    .glass(RoundedCornerShape(Radius.Pill))
                    .heightIn(min = 36.dp).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.DoneAll, null, tint = AccentLight, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.mark_all_aired_watched), color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Seasons longer than this open on a window around your episode, with Mostra tutti. */
private const val FoldEpisodesAbove = 30
private const val FoldedEpisodeCount = 10

/** One episode as a flat row: its still, number and title, a status line and a check circle. */
@Composable
internal fun EpisodeRow(
    episode: EpisodeCard,
    subtitle: String,
    subtitleAccent: Boolean,
    highlight: Color?,
    onClick: () -> Unit,
    onWatched: () -> Unit,
) {
    val stillShape = RoundedCornerShape(Radius.Compact)
    var infoVisible by rememberSaveable(episode.showId, episode.season, episode.number) { mutableStateOf(false) }
    // The still grows into the episode page's large still when the row is opened.
    val stillSlot = com.cinetrack.ui.components.rememberPosterSlot()
    val sharedKey = "ep:${episode.showId}:${episode.season}:${episode.number}"
    Column(Modifier.fillMaxWidth()) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.Small))
            .clickable {
                com.cinetrack.ui.components.SharedPosterSource.select(stillSlot, sharedKey)
                onClick()
            }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.width(96.dp).aspectRatio(16f / 9f)
                .sharedBackdrop(sharedKey, stillSlot)
                .clip(stillShape).background(SurfacePalette.ArtworkBackdrop)
                .then(if (highlight != null) Modifier.border(1.6.dp, highlight, stillShape) else Modifier),
        ) {
            episode.stillUrl?.let { com.cinetrack.ui.components.SharedPosterImage(it, episode.title, stillSlot, Modifier.fillMaxSize(), ContentScale.Crop) }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("${episode.number} · ${episode.title}", color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank() && subtitle != "—") {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, color = if (subtitleAccent) AccentLight else TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    fontWeight = if (subtitleAccent) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
            }
        }
        if (episode.overview.isNotBlank()) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).clickable(role = androidx.compose.ui.semantics.Role.Button) { infoVisible = !infoVisible },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Info,
                    stringResource(if (infoVisible) R.string.hide_episode_info else R.string.show_episode_info, episode.title),
                    tint = if (infoVisible) AccentLight else TextMuted, modifier = Modifier.size(19.dp),
                )
            }
        }
        val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
        Box(
            Modifier.size(48.dp).clip(CircleShape).clickable(role = androidx.compose.ui.semantics.Role.Button) {
                haptics.performHapticFeedback(
                    if (episode.watched) androidx.compose.ui.hapticfeedback.HapticFeedbackType.ToggleOff
                    else androidx.compose.ui.hapticfeedback.HapticFeedbackType.ToggleOn,
                )
                onWatched()
            },
            contentAlignment = Alignment.Center,
        ) {
            WatchedCircle(episode.watched, stringResource(if (episode.watched) R.string.mark_unwatched else R.string.mark_watched))
        }
    }
    AnimatedVisibility(infoVisible) {
        Text(
            episode.overview, color = TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, lineHeight = 18.sp,
            modifier = Modifier.padding(start = 108.dp, end = 8.dp, bottom = 10.dp),
        )
    }
    }
}

@Composable
private fun WatchedCircle(watched: Boolean, description: String) {
    // The fill fades in and the check pops on a bouncy spring instead of swapping at once.
    val fill by androidx.compose.animation.core.animateFloatAsState(if (watched) 1f else 0f, Motion.snappy(), label = "watchedFill")
    val check by androidx.compose.animation.core.animateFloatAsState(if (watched) 1f else 0f, Motion.bouncy(), label = "watchedCheck")
    // Both values are read while drawing only, so the row doesn't recompose per frame.
    Box(
        Modifier.size(28.dp).semantics { contentDescription = description }.clip(CircleShape)
            .drawBehind {
                val shown = fill.coerceIn(0f, 1f)
                drawCircle(Success.copy(alpha = shown))
                val stroke = 1.3.dp.toPx()
                drawCircle(TextMuted.copy(alpha = .7f * (1f - shown)), radius = (size.minDimension - stroke) / 2f, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.Check, null, tint = SurfacePalette.WatchedInk,
            modifier = Modifier.size(16.dp).graphicsLayer { scaleX = check; scaleY = check; alpha = check.coerceIn(0f, 1f) },
        )
    }
}

@Composable
internal fun EpisodeDivider() {
    Box(Modifier.fillMaxWidth().padding(start = 108.dp).height(.5.dp).background(Color.White.copy(alpha = .08f)))
}

@Composable
internal fun SeasonInfoSheet(
    show: MediaCard,
    number: Int,
    detailViewModel: com.cinetrack.ui.detail.DetailViewModel,
    onDismiss: () -> Unit,
    onPerson: (PersonCard) -> Unit,
) {
    var details by remember(show.stableKey, number) { mutableStateOf<com.cinetrack.domain.SeasonDetails?>(null) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableStateOf(0) }
    LaunchedEffect(show.stableKey, number, attempt) {
        loading = true
        failed = false
        detailViewModel.loadSeasonDetails(show, number).fold(
            onSuccess = { details = it },
            onFailure = { failed = true },
        )
        loading = false
    }
    SharedGlassSheet(onDismiss) {
        Column(
            Modifier.fillMaxWidth().fillMaxHeight(.72f)
                .padding(horizontal = DetailLayout.Gutter)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column {
                SectionHeader(details?.title?.takeIf(String::isNotBlank) ?: stringResource(R.string.season_number, number))
                Text(show.title, color = TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
            }
            if (loading) {
                Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.CircularProgressIndicator(color = AccentLight)
                }
            }
            if (failed) {
                Column {
                    Text(stringResource(R.string.season_info_error), color = TextSecondary)
                    androidx.compose.material3.TextButton(onClick = { attempt++ }) { Text(stringResource(R.string.retry), color = TextPrimary) }
                }
            }
            details?.let { season ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    season.posterUrl?.let { url ->
                        AsyncImage(url, season.title, Modifier.width(80.dp).height(120.dp).clip(RoundedCornerShape(Radius.Small)), contentScale = ContentScale.Crop)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("TMDB " + (season.score?.let { "%.1f".format(it) } ?: "—"), color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text(formatFullDate(season.airDate), color = TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, maxLines = 2)
                    }
                }
                Column {
                    SectionHeader(stringResource(R.string.overview))
                    Spacer(Modifier.height(8.dp))
                    Text(season.overview.ifBlank { stringResource(R.string.season_no_description) }, color = TextSecondary,
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                }
                DetailsList(stringResource(R.string.details), listOfNotNull(
                    stringResource(R.string.episodes) to season.episodeCount.toString(),
                    formatDurationMinutes(season.runtimeMinutes).takeIf(String::isNotBlank)?.let { stringResource(R.string.average_runtime) to it },
                ), inset = 0.dp)
                SectionHeader(stringResource(R.string.season_cast))
                if (season.cast.isEmpty()) Text(stringResource(R.string.season_no_cast), color = TextSecondary)
                else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(season.cast, key = PersonCard::id) { CastAvatar(it, onPerson) }
                    }
                }
            }
        }
    }
}

@Composable
internal fun MarkShowWatchedPrompt(
    title: String,
    episodeCount: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    SharedGlassSheet(onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg)) {
            Text(stringResource(R.string.mark_show_watched_title), color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(7.dp))
            Text(
                androidx.compose.ui.res.pluralStringResource(R.plurals.mark_show_watched_message, episodeCount, episodeCount, title),
                color = TextSecondary,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(16.dp))
            PrimaryAction(
                androidx.compose.ui.res.pluralStringResource(R.plurals.mark_show_watched_confirm, episodeCount, episodeCount),
                Icons.Filled.Check,
                Modifier.fillMaxWidth(),
                onClick = onConfirm,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(46.dp), shape = RoundedCornerShape(Radius.Pill)) {
                Text(stringResource(R.string.cancel), color = TextPrimary, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
internal fun UnmarkShowPrompt(
    title: String,
    watchedCount: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    com.cinetrack.ui.components.ConfirmPrompt(
        title = stringResource(R.string.unmark_show_title),
        message = androidx.compose.ui.res.pluralStringResource(R.plurals.unmark_show_message, watchedCount, watchedCount, title),
        confirmLabel = stringResource(R.string.unmark_show_confirm),
        icon = Icons.Outlined.RemoveDone,
        onDismiss = onDismiss,
        onConfirm = onConfirm,
    )
}

@Composable
internal fun PreviousEpisodesPrompt(
    previousCount: Int,
    onDismiss: () -> Unit,
    onOnlyThis: () -> Unit,
    onIncludePrevious: () -> Unit,
) {
    SharedGlassSheet(onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg)) {
            Text(stringResource(R.string.previous_episodes_title), color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(7.dp))
            Text(
                stringResource(R.string.previous_episodes_message, previousCount),
                color = TextSecondary,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(16.dp))
            PrimaryAction(stringResource(R.string.mark_previous_too), Icons.Filled.Check, Modifier.fillMaxWidth(), onClick = onIncludePrevious)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onOnlyThis, modifier = Modifier.fillMaxWidth().height(46.dp), shape = RoundedCornerShape(Radius.Pill)) {
                Text(stringResource(R.string.only_this_episode), color = TextPrimary, fontWeight = FontWeight.Bold)
            }
        }
    }
}
