@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.cinetrack.ui.screens

import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.cinetrack.R
import com.cinetrack.domain.EpisodeCard
import com.cinetrack.domain.MediaCard
import com.cinetrack.domain.MediaType
import com.cinetrack.domain.PlaybackCard
import com.cinetrack.domain.releaseDateTime
import com.cinetrack.ui.components.PrimaryAction
import com.cinetrack.ui.components.SectionHeader
import com.cinetrack.ui.components.SharedGlassDialog
import com.cinetrack.ui.components.glass
import com.cinetrack.ui.components.rememberUiAction
import com.cinetrack.ui.theme.Accent
import com.cinetrack.ui.theme.Info
import com.cinetrack.ui.theme.TextMuted
import com.cinetrack.ui.theme.TextPrimary
import com.cinetrack.ui.theme.TextSecondary
import java.time.Instant
import java.time.ZoneId
import com.cinetrack.ui.theme.Radius
import com.cinetrack.ui.theme.Spacing
import com.cinetrack.ui.theme.Motion

@Composable
internal fun UpcomingEpisodesRail(
    title: String,
    episodes: List<EpisodeCard>,
    shows: List<MediaCard>,
    onEpisode: (EpisodeCard) -> Unit,
    onHide: (EpisodeCard) -> Unit,
) {
    var pendingHide by remember { mutableStateOf<EpisodeCard?>(null) }
    Column(Modifier.padding(top = Spacing.sm, bottom = Spacing.lg)) {
        SectionHeader(title, Modifier.padding(horizontal = Spacing.xl))
        Spacer(Modifier.height(13.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            items(episodes.distinctBy(EpisodeCard::scheduleKey), key = EpisodeCard::scheduleKey) { episode ->
                val showTitle = shows.firstOrNull { it.id == episode.showId && it.type == com.cinetrack.domain.MediaType.TV }?.title.orEmpty()
                val interaction = remember(episode.showId, episode.season, episode.number) { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                val openEpisodeAction = rememberUiAction { onEpisode(episode) }
                Column(
                    Modifier.animateItem(fadeInSpec = Motion.enter(Motion.Medium), placementSpec = Motion.gentle(androidx.compose.ui.unit.IntOffset.VisibilityThreshold), fadeOutSpec = Motion.exit()).width(236.dp).combinedClickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = openEpisodeAction,
                        onLongClick = { pendingHide = episode },
                    ),
                ) {
                    Box(Modifier.fillMaxWidth().height(132.dp).clip(RoundedCornerShape(Radius.Medium)).background(Brush.linearGradient(listOf(Accent.copy(alpha = .65f), Info.copy(alpha = .28f))))
                        .border(if (pressed) 1.7.dp else 0.dp, Accent.copy(alpha = if (pressed) .9f else 0f), RoundedCornerShape(Radius.Medium))) {
                        if (!episode.stillUrl.isNullOrBlank()) AsyncImage(episode.stillUrl, episode.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .74f)))))
                        Text(shortAirDate(episode.airDate), color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, lineHeight = 10.sp, fontWeight = FontWeight.ExtraBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(Spacing.sm).glass(RoundedCornerShape(Radius.Compact)).padding(horizontal = Spacing.sm, vertical = Spacing.xs))
                        IconButton(
                            onClick = { pendingHide = episode },
                            modifier = Modifier.align(Alignment.TopEnd).padding(2.dp).size(40.dp),
                        ) {
                            Box(
                                Modifier.size(26.dp).glass(RoundedCornerShape(Radius.Compact)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Filled.VisibilityOff,
                                    stringResource(R.string.hide_upcoming_episode),
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                        Text(showTitle, color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.BottomStart).padding(Spacing.md))
                    }
                    Text("${episode.code} · ${episode.title}", color = TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = Spacing.sm))
                }
            }
        }
    }
    pendingHide?.let { episode ->
        SharedGlassDialog(onDismiss = { pendingHide = null }) {
            Column(Modifier.padding(Spacing.lg)) {
                Text(stringResource(R.string.hide_upcoming_title), color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(7.dp))
                Text(stringResource(R.string.hide_upcoming_message, episode.code, episode.title), color = TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(Spacing.lg))
                PrimaryAction(
                    stringResource(R.string.hide_upcoming_episode),
                    Icons.Filled.VisibilityOff,
                    Modifier.fillMaxWidth(),
                ) {
                    onHide(episode)
                    pendingHide = null
                }
                androidx.compose.material3.TextButton(onClick = { pendingHide = null }, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.cancel), color = TextPrimary)
                }
            }
        }
    }
}

@Composable
internal fun UpcomingDiagnostics(trackedShows: Int) {
    Column(
        Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.sm).fillMaxWidth()
            .glass(RoundedCornerShape(Radius.Medium)).padding(Spacing.md),
    ) {
        Text(stringResource(R.string.why_not_shown), color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(5.dp))
        Text(
            if (trackedShows == 0) stringResource(R.string.no_tracked_shows_for_upcoming)
            else stringResource(R.string.no_announced_episodes, trackedShows),
            color = TextMuted,
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            lineHeight = 15.sp,
        )
    }
}

internal data class UpcomingInputs(
    val episodes: List<com.cinetrack.domain.EpisodeCard>,
    val rails: Map<String, List<MediaCard>>,
    val playbackTv: List<PlaybackCard>,
    val excludeSpecials: Boolean,
    val hiddenUpcoming: Set<String>,
    val zone: ZoneId,
)

/** Last upcoming list, so re-entering Progress does not wait for the background scan. */
internal object UpcomingCache {
    @Volatile var inputs: UpcomingInputs? = null
    @Volatile var result: List<com.cinetrack.domain.EpisodeCard>? = null
}

internal fun computeUpcoming(inputs: UpcomingInputs, now: java.time.Instant): List<com.cinetrack.domain.EpisodeCard> {
    val trackedShowIds = (
        inputs.rails[com.cinetrack.domain.RailIds.LIBRARY].orEmpty()
            .filter {
                it.type == com.cinetrack.domain.MediaType.TV &&
                    it.status != com.cinetrack.domain.LibraryStatus.NONE &&
                    it.status != com.cinetrack.domain.LibraryStatus.DROPPED
            }
            .map(MediaCard::id) +
            inputs.playbackTv.map { it.media.id }
        ).toSet()
    return inputs.episodes.asSequence()
        .filter { it.showId in trackedShowIds }
        .filter { !inputs.excludeSpecials || it.season > 0 }
        .filter { it.scheduleKey !in inputs.hiddenUpcoming }
        .mapNotNull { episode -> releaseDateTime(episode.airDate, inputs.zone)?.toInstant()?.takeIf { it.isAfter(now) }?.let { episode to it } }
        .distinctBy { (episode, _) -> "${episode.showId}:${episode.season}:${episode.number}" }
        .sortedWith(
            compareBy<Pair<com.cinetrack.domain.EpisodeCard, java.time.Instant>> { it.second }
                .thenBy { it.first.showId }
                .thenBy { it.first.season }
                .thenBy { it.first.number },
        )
        .map { it.first }
        .toList()
}
