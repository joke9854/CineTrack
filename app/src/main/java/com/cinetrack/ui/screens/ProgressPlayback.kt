@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.cinetrack.ui.screens

import com.cinetrack.ui.components.sharedBackdrop
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SortByAlpha
import kotlin.math.roundToInt
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.foundation.gestures.draggable
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.blur
import com.cinetrack.R
import com.cinetrack.domain.EpisodeCard
import com.cinetrack.domain.MediaCard
import com.cinetrack.domain.MediaType
import com.cinetrack.domain.PlaybackCard
import com.cinetrack.ui.components.PrimaryAction
import com.cinetrack.ui.components.fillMaxWidthOf
import com.cinetrack.ui.components.RevealOnMount
import com.cinetrack.ui.components.SharedGlassSheet
import com.cinetrack.ui.components.glass
import com.cinetrack.ui.components.liquidGlassPill
import com.cinetrack.ui.components.rememberUiAction
import com.cinetrack.ui.theme.Accent
import com.cinetrack.ui.theme.AccentLight
import com.cinetrack.ui.theme.DesignTokens
import com.cinetrack.ui.theme.Success
import com.cinetrack.ui.theme.TextMuted
import com.cinetrack.ui.theme.TextPrimary
import com.cinetrack.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import com.cinetrack.ui.theme.Background0
import com.cinetrack.ui.theme.Motion
import com.cinetrack.ui.theme.Radius
import com.cinetrack.ui.theme.Spacing
import com.cinetrack.ui.theme.SurfacePalette

@Composable
internal fun ExpandablePlaybackSection(
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
    // One clock drives the peeking card, the rows below it and the button, so the
    // button only ever travels in one direction while the list opens or closes.
    val expandSpec = tween<Float>(Motion.Extended, easing = FastOutSlowInEasing)
    val reveal by animateFloatAsState(if (expanded) 1f else 0f, expandSpec, label = "showAllReveal")
    // Only whether the extra rows exist is a composition question; the fade itself is drawn.
    val showRest by remember { androidx.compose.runtime.derivedStateOf { reveal > 0f } }
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.xl), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f).padding(bottom = Spacing.md))
            if (showEpisodeControl) {
                Row(
                    Modifier.padding(bottom = Spacing.sm).heightIn(min = 48.dp)
                        .liquidGlassPill()
                        .clickable(onClick = rememberUiAction { showOrderSheet = true })
                        .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
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
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            if (expanded) {
                IconButton(onClick = { expanded = false }, modifier = Modifier.padding(bottom = Spacing.sm).size(48.dp)) {
                    Icon(Icons.Filled.ExpandLess, stringResource(R.string.collapse), tint = TextSecondary, modifier = Modifier.size(18.dp))
                }
            }
        }
        if (items.isEmpty()) {
            Text(
                stringResource(R.string.no_playback),
                color = TextMuted,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = Spacing.xl, end = Spacing.xl, bottom = Spacing.md).fillMaxWidth().glass(RoundedCornerShape(Radius.Medium)).padding(Spacing.md),
            )
        }
        orderedItems.take(3).forEachIndexed { index, item ->
            AnimatedPlaybackSlot(index, item, onMedia, onEpisode, onWatched)
        }
        if (orderedItems.size > 3) {
            // The fourth card peeks under a fade while collapsed and grows to full height.
            val peek = with(androidx.compose.ui.platform.LocalDensity.current) { 132.dp.roundToPx() }
            Box(
                Modifier.fillMaxWidth()
                    .clipToBounds()
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val height = androidx.compose.ui.util.lerp(minOf(peek, placeable.height).toFloat(), placeable.height.toFloat(), reveal).roundToInt()
                        layout(placeable.width, height) { placeable.place(0, 0) }
                    }
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        val fade = 1f - reveal
                        if (fade > 0f) drawRect(
                            brush = Brush.verticalGradient(
                                0f to Color.White,
                                .44f to Color.White.copy(alpha = 1f - .62f * fade),
                                .78f to Color.White.copy(alpha = 1f - .86f * fade),
                                1f to Color.White.copy(alpha = 1f - fade),
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                    },
            ) {
                PlaybackRow(orderedItems[3], onMedia, onEpisode, onWatched)
            }
        }
        if (orderedItems.size > 4) {
            Column(
                Modifier.fillMaxWidth().clipToBounds()
                    .layout { measurable, constraints ->
                        if (reveal <= 0f) return@layout layout(constraints.maxWidth, 0) {}
                        val placeable = measurable.measure(constraints)
                        layout(placeable.width, (placeable.height * reveal).roundToInt()) { placeable.place(0, 0) }
                    }
                    .graphicsLayer { alpha = reveal },
            ) {
                if (showRest) orderedItems.drop(4).forEach { PlaybackRow(it, onMedia, onEpisode, onWatched) }
            }
        }
        if (orderedItems.size > 3) {
            Row(
                Modifier.padding(start = Spacing.xl, end = Spacing.xl, bottom = Spacing.xl).offset { androidx.compose.ui.unit.IntOffset(0, ((-42).dp.toPx() * (1f - reveal)).roundToInt()) }.fillMaxWidth().height(48.dp)
                    .liquidGlassPill()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(if (expanded) stringResource(R.string.collapse) else showAllLabel, color = TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
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
            (slideInHorizontally(Motion.enter(Motion.Long)) { it / 7 } + fadeIn(Motion.enter(Motion.Medium, delay = 60))) togetherWith
                (slideOutHorizontally(Motion.exit(Motion.Medium)) { -it / 8 } + fadeOut(Motion.exit(90)))
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
        Column(Modifier.padding(horizontal = 12.dp)) {
            com.cinetrack.ui.components.SheetTitle(stringResource(R.string.sort_by))
            com.cinetrack.ui.components.GlassMenuGroup {
                PlaybackOrder.entries.forEachIndexed { index, option ->
                    com.cinetrack.ui.components.GlassMenuRow(
                        label = when (option) {
                            PlaybackOrder.RECENT -> stringResource(R.string.order_recent)
                            PlaybackOrder.TITLE -> stringResource(R.string.order_title)
                            PlaybackOrder.TOP_RATED -> stringResource(R.string.order_top_rated)
                            PlaybackOrder.REMAINING -> stringResource(R.string.order_remaining)
                            PlaybackOrder.RANDOM -> stringResource(R.string.order_random)
                        },
                        icon = when (option) {
                            PlaybackOrder.RECENT -> Icons.Filled.History
                            PlaybackOrder.TITLE -> Icons.Filled.SortByAlpha
                            PlaybackOrder.TOP_RATED -> Icons.Filled.Star
                            PlaybackOrder.REMAINING -> Icons.Filled.Schedule
                            PlaybackOrder.RANDOM -> Icons.Filled.Shuffle
                        },
                        tint = AccentLight,
                        selected = option == selected,
                        divider = index < PlaybackOrder.entries.lastIndex,
                    ) { selected = option }
                }
            }
            Spacer(Modifier.height(10.dp))
            com.cinetrack.ui.components.GlassMenuGroup {
                com.cinetrack.ui.components.GlassMenuRow(
                    label = if (ascending) stringResource(R.string.ascending) else stringResource(R.string.descending),
                    icon = if (ascending) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                    tint = AccentLight,
                    divider = false,
                ) { ascending = !ascending }
            }
            Spacer(Modifier.height(18.dp))
            PrimaryAction(stringResource(R.string.apply), Icons.Filled.Sort, Modifier.fillMaxWidth()) { onApply(selected, ascending) }
        }
    }
}

@Composable
internal fun PlaybackRow(
    item: PlaybackCard,
    onMedia: (MediaCard) -> Unit,
    onEpisode: (EpisodeCard) -> Unit,
    onWatched: (PlaybackCard) -> Unit,
) {
    val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale.coerceAtLeast(1f)
    var confirming by remember(item.media.stableKey) { mutableStateOf(false) }
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    LaunchedEffect(confirming) {
        if (confirming) {
            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.Confirm)
            delay(DesignTokens.WatchedConfirmationMs)
            onWatched(item)
            confirming = false
        }
    }
    val checkScale by animateFloatAsState(
        if (confirming) 1.25f else 1f,
        Motion.bouncy(),
        label = "watchedScale",
    )
    val timelineProgress = when {
        item.progress > 0f -> item.progress
        item.remainingMinutes != null && item.durationMinutes != null && item.durationMinutes > 0 ->
            (1f - item.remainingMinutes.toFloat() / item.durationMinutes.toFloat()).coerceIn(0f, 1f)
        else -> 0f
    }
    val remainingMinutes = item.remainingMinutes ?: item.durationMinutes
        ?.takeIf { it > 0 && item.progress > 0f }
        ?.let { duration -> (duration * (1f - item.progress.coerceIn(0f, 1f))).toInt().coerceAtLeast(0) }
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
    // The card grows into the episode's still (or a film's header) when opened.
    val cardSlot = com.cinetrack.ui.components.rememberPosterSlot()
    val sharedKey = if (item.media.type == com.cinetrack.domain.MediaType.TV && item.season != null && item.episodeNumber != null) {
        "ep:${item.media.id}:${item.season}:${item.episodeNumber}"
    } else item.media.stableKey
    val openItemAction = rememberUiAction {
        com.cinetrack.ui.components.SharedPosterSource.select(cardSlot, sharedKey)
        openItem()
    }
    RevealOnMount("${item.media.stableKey}:${item.season}:${item.episodeNumber}") {
    // Artwork card: the show's art fills the card behind a dark fade, the poster sits on
    // it, the remaining time sits beside the title and progress runs along the bottom edge.
    val cardShape = RoundedCornerShape(20.dp)
    val backdrop = item.media.backdropUrl ?: item.media.posterUrl
    val posterArt = item.media.posterUrl ?: item.media.backdropUrl
    val animatedProgress by animateFloatAsState(timelineProgress.coerceIn(0f, 1f), tween(Motion.Extended), label = "progressValue")
    // Swipe left to tick: the card slides over a green check and springs back once released.
    // The drag writes the offset directly (no coroutine per frame); the release hands the
    // finger's velocity to the spring. swipe is negative while the card is pulled left.
    var swipe by remember(item.media.stableKey, item.season, item.episodeNumber) { androidx.compose.runtime.mutableFloatStateOf(0f) }
    var cardWidth by remember { mutableStateOf(1f) }
    val swipeThreshold = cardWidth * .34f
    var swipeArmed by remember { mutableStateOf(false) }
    val checkPainter = androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Filled.Check)
    val swipeLabel = androidx.compose.ui.res.stringResource(com.cinetrack.R.string.mark_watched)
    Box(
        Modifier.padding(start = Spacing.xl, end = Spacing.xl, bottom = Spacing.md).fillMaxWidth().height((150 * fontScale).dp)
            .onSizeChanged { cardWidth = it.width.toFloat().coerceAtLeast(1f) }
            .drawBehind {
                // Revealed behind the card: green, with the check growing as the threshold nears.
                val pulled = -swipe
                val reveal = (pulled / swipeThreshold).coerceIn(0f, 1f)
                if (pulled > 0f) {
                    drawRoundRect(Success.copy(alpha = .25f + .6f * reveal), cornerRadius = androidx.compose.ui.geometry.CornerRadius(20.dp.toPx()))
                    val icon = (22.dp.toPx() * (.7f + .45f * reveal))
                    translate(left = size.width - 26.dp.toPx() - icon, top = (size.height - icon) / 2f) {
                        with(checkPainter) { draw(androidx.compose.ui.geometry.Size(icon, icon), alpha = .5f + .5f * reveal, colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color.White)) }
                    }
                }
            }
            .draggable(
                orientation = androidx.compose.foundation.gestures.Orientation.Horizontal,
                state = androidx.compose.foundation.gestures.rememberDraggableState { delta ->
                    // Past the threshold the card resists, so it never runs off the screen.
                    val next = swipe + if (-swipe > swipeThreshold) delta * .35f else delta
                    swipe = next.coerceIn(-cardWidth * .6f, 0f)
                    val armed = -swipe >= swipeThreshold
                    if (armed != swipeArmed) haptics.performHapticFeedback(
                        if (armed) androidx.compose.ui.hapticfeedback.HapticFeedbackType.GestureThresholdActivate
                        else androidx.compose.ui.hapticfeedback.HapticFeedbackType.SegmentTick,
                    )
                    swipeArmed = armed
                },
                onDragStopped = { velocity ->
                    if (-swipe >= swipeThreshold) confirming = true
                    swipeArmed = false
                    androidx.compose.animation.core.animate(swipe, 0f, velocity, Motion.snappy()) { value, _ -> swipe = value }
                },
            )
            .semantics {
                customActions = listOf(androidx.compose.ui.semantics.CustomAccessibilityAction(swipeLabel) { confirming = true; true })
            }
            .offset { androidx.compose.ui.unit.IntOffset(swipe.roundToInt(), 0) }
            .shadow(14.dp, cardShape, clip = false, ambientColor = Color.Black, spotColor = Color.Black)
            .sharedBackdrop(sharedKey, cardSlot)
            .clip(cardShape)
            .background(Brush.linearGradient(listOf(SurfacePalette.PosterBrown, Background0)))
            .border(if (cardPressed) 1.5.dp else .5.dp, if (cardPressed) Accent.copy(alpha = .9f) else Color.White.copy(alpha = .10f), cardShape)
            .clickable(interactionSource = cardInteraction, indication = null, onClick = openItemAction),
    ) {
        if (!backdrop.isNullOrBlank()) {
            com.cinetrack.ui.components.SharedPosterImage(
                backdrop, null, cardSlot, Modifier.fillMaxSize()
                    .then(if (item.media.backdropUrl == null) Modifier.blur(12.dp) else Modifier),
                ContentScale.Crop,
            )
        }
        Box(Modifier.matchParentSize().background(Brush.horizontalGradient(
            0f to Color(0x40080A0C), .38f to Color(0xD1080A0C), 1f to Color(0xF0080A0C),
        )))
        Box(
            Modifier.padding(14.dp).width(82.dp).fillMaxHeight()
                .shadow(10.dp, RoundedCornerShape(10.dp), clip = false)
                .clip(RoundedCornerShape(10.dp))
                .background(Brush.linearGradient(listOf(SurfacePalette.PosterBrown, SurfacePalette.OceanMid)))
                .border(.5.dp, Color.White.copy(alpha = .14f), RoundedCornerShape(10.dp)),
        ) {
            if (!posterArt.isNullOrBlank()) AsyncImage(posterArt, item.media.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Column(Modifier.fillMaxSize().padding(start = 112.dp, end = 12.dp, top = 16.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(item.media.title, color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                remainingMinutes?.let {
                    Spacer(Modifier.width(8.dp))
                    Text(formatDurationMinutes(it), color = AccentLight, style = androidx.compose.material3.MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
            val detail = if (item.media.type == com.cinetrack.domain.MediaType.TV) {
                val numbered = item.episodeLabel ?: item.season?.let { season -> item.episodeNumber?.let { com.cinetrack.domain.episodeCode(season, it) } } ?: listOfNotNull(item.season?.let { "S$it" }, item.episodeNumber?.let { "E$it" }).joinToString(" ")
                listOfNotNull(numbered.takeIf(String::isNotBlank), item.episodeTitle?.takeIf(String::isNotBlank)).joinToString(" · ")
            } else stringResource(R.string.movies)
            Text(detail.ifBlank { stringResource(R.string.in_progress) }, color = TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    (if (timelineProgress > 0f) stringResource(R.string.resume)
                    else if (item.media.type == com.cinetrack.domain.MediaType.TV) stringResource(R.string.up_next)
                    else stringResource(R.string.in_progress)).uppercase(com.cinetrack.ui.currentLocale()),
                    color = TextMuted,
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.4.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = rememberUiAction { confirming = true },
                    enabled = !confirming,
                    modifier = Modifier.size(48.dp),
                ) {
                    // Flat glass: the button sits on artwork, which the page backdrop cannot show.
                    Box(
                        Modifier.size(40.dp).clip(CircleShape)
                            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = .20f), Color.White.copy(alpha = .08f))))
                            .border(.6.dp, Color.White.copy(alpha = .30f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Check, stringResource(R.string.mark_watched), tint = TextPrimary, modifier = Modifier.size(17.dp).graphicsLayer { scaleX = checkScale; scaleY = checkScale })
                    }
                }
            }
        }
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = .10f))) {
            Box(Modifier.fillMaxWidthOf { animatedProgress }.fillMaxHeight().background(Accent))
        }
        AnimatedVisibility(
            visible = confirming,
            modifier = Modifier.matchParentSize(),
            enter = slideInHorizontally(Motion.enter(Motion.Long)) { it } + fadeIn(Motion.enter(Motion.Short)),
            exit = slideOutHorizontally(Motion.exit(Motion.Medium)) { -it / 5 } + fadeOut(Motion.exit(Motion.Short)),
        ) {
            Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)).background(Success.copy(alpha = .96f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(34.dp))
                    Text(
                        stringResource(if (item.media.type == MediaType.MOVIE) R.string.watched_movie else R.string.watched_tv),
                        color = Color.White,
                        style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
        }
    }
    }
}
