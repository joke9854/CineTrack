@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.cinetrack.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cinetrack.R
import com.cinetrack.domain.LibraryStatus
import com.cinetrack.domain.MediaCard
import com.cinetrack.domain.MediaType
import com.cinetrack.ui.theme.Accent
import com.cinetrack.ui.theme.Success
import com.cinetrack.ui.theme.TextMuted
import com.cinetrack.ui.theme.TextPrimary
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs
import com.cinetrack.ui.theme.LocalCardAppearance
import com.cinetrack.ui.theme.Motion
import com.cinetrack.ui.theme.Radius
import com.cinetrack.ui.theme.Spacing
import com.cinetrack.ui.theme.SurfacePalette
import com.cinetrack.ui.theme.WhiteBright

@Composable
fun MediaPoster(
    media: MediaCard,
    modifier: Modifier = Modifier,
    width: Dp = LocalCardAppearance.current.posterWidthDp.dp,
    showTitle: Boolean = true,
    showYear: Boolean = true,
    showAirDate: Boolean = false,
    progress: Float? = null,
    selectedBorder: Color? = null,
    selectionMode: Boolean = false,
    onStatus: ((LibraryStatus) -> Unit)? = null,
    onNotInterested: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    var statusPopup by remember(media.stableKey) { mutableStateOf(false) }
    val posterSlot = rememberPosterSlot()
    val edgeAlpha by animateFloatAsState(if (pressed || statusPopup) .95f else 0f, tween(Motion.Short), label = "posterEdge")
    RevealOnMount(media.stableKey, modifier.width(width)) {
    Box {
    Column(
        Modifier.semantics {
            role = if (selectionMode) Role.Checkbox else Role.Button
            if (selectionMode) selected = selectedBorder != null
        }.combinedClickable(
            interactionSource = interactionSource,
            indication = null,
            role = if (selectionMode) Role.Checkbox else Role.Button,
            onClick = {
                SharedPosterSource.select(posterSlot, media.stableKey)
                onClick()
            },
            onLongClick = {
                if (onStatus != null || onNotInterested != null) {
                    statusPopup = true
                }
            },
        ),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(LocalCardAppearance.current.posterAspectRatio)
                .sharedPoster(media.stableKey, posterSlot)
                .clip(RoundedCornerShape(Radius.Medium))
                .background(posterBrush(media.id))
                .then(
                    selectedBorder?.let { Modifier.border(2.dp, it, RoundedCornerShape(Radius.Medium)) }
                        ?: Modifier,
                )
                .border(1.8.dp, Accent.copy(alpha = edgeAlpha), RoundedCornerShape(Radius.Medium)),
        ) {
            if (!media.posterUrl.isNullOrBlank()) {
                SharedPosterImage(media.posterUrl, media.title, posterSlot, Modifier.fillMaxSize(), ContentScale.Crop)
            } else {
                Text(
                    media.title.take(1),
                    color = WhiteBright,
                    fontSize = 46.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            if (showAirDate && !selectionMode) {
                val dateLabel = remember(media.releaseDate) { formattedAirDate(media.releaseDate) }
                if (dateLabel.isNotBlank()) {
                    Text(
                        dateLabel,
                        color = Color.White,
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        lineHeight = 10.sp,
                        fontWeight = FontWeight.Black,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.align(Alignment.TopStart).padding(Spacing.sm).clip(RoundedCornerShape(Radius.Compact))
                            .background(Accent).padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                    )
                }
            }
            if (selectionMode) {
                StateBadge(if (selectedBorder != null) Accent else SurfacePalette.DeepOverlay,
                    if (selectedBorder != null) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                    stringResource(if (selectedBorder != null) R.string.poster_selected else R.string.poster_not_selected),
                    Modifier.align(Alignment.TopEnd))
            } else if (media.watched || media.status == LibraryStatus.COMPLETED) {
                StateBadge(
                    Success,
                    Icons.Filled.CheckCircle,
                    stringResource(if (media.type == MediaType.MOVIE) R.string.watched_movie else R.string.watched_tv),
                    Modifier.align(Alignment.TopEnd),
                )
            } else if (media.status != LibraryStatus.NONE) {
                StateBadge(libraryStatusColor(media.status), libraryStatusIcon(media.status), stringResource(libraryStatusLabel(media.status)), Modifier.align(Alignment.TopEnd))
            }
            if (!selectionMode && progress != null && progress > 0f && !media.watched && media.status != LibraryStatus.COMPLETED) {
                // A thin line along the bottom edge, as on the Progress card; the art stays clear.
                Box(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp)
                        .background(SurfacePalette.BlueOverlay),
                ) {
                    Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(Success))
                }
            }
        }
        if (showTitle) {
            Spacer(Modifier.height(7.dp))
            Text(media.title, color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (showYear && media.year.isNotBlank()) Text(media.year, color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
        }
    }
    MediaStatusPopup(
        expanded = statusPopup,
        currentStatus = media.status,
        media = media,
        onDismiss = { statusPopup = false },
        onStatus = { status -> onStatus?.invoke(status); statusPopup = false },
        onNotInterested = onNotInterested?.let { action -> { action(); statusPopup = false } },
    )
    }
    }
}

private fun formattedAirDate(raw: String?): String = runCatching {
    val date = LocalDate.parse(raw?.take(10))
    date.format(com.cinetrack.ui.UiDateFormatters.current.dayMonth).uppercase(Locale.getDefault())
}.getOrDefault("")

@Composable
private fun StateBadge(color: Color, icon: ImageVector, label: String, modifier: Modifier) {
    Box(modifier.padding(Spacing.sm).size(27.dp).clip(CircleShape).background(color).semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(16.dp))
    }
}

private fun posterBrush(seed: Int): Brush {
    val palettes = listOf(
        listOf(SurfacePalette.PosterOrange, SurfacePalette.PosterBlue, SurfacePalette.WarmPosterShadow),
        listOf(SurfacePalette.PosterMint, SurfacePalette.PosterCyan, SurfacePalette.CoolPosterShadow),
        listOf(SurfacePalette.LavenderText, SurfacePalette.PosterGray, SurfacePalette.WarmSurface),
        listOf(SurfacePalette.PosterLilac, SurfacePalette.PosterIndigo, SurfacePalette.VioletDeep),
        listOf(SurfacePalette.PosterRose, SurfacePalette.PosterPlum, SurfacePalette.VioletPosterShadow),
    )
    return Brush.linearGradient(palettes[abs(seed) % palettes.size])
}

@Composable
fun MediaRail(
    items: List<MediaCard>,
    onMedia: (MediaCard) -> Unit,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp),
    showAirDate: Boolean = false,
    showYear: Boolean = true,
    showTitle: Boolean = true,
    progressByKey: Map<String, Float> = emptyMap(),
    onStatus: ((MediaCard, LibraryStatus) -> Unit)? = null,
    onNotInterested: ((MediaCard) -> Unit)? = null,
) {
    LazyRow(contentPadding = contentPadding, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(items.distinctBy(MediaCard::stableKey), key = MediaCard::stableKey) {
            MediaPoster(
                it,
                showAirDate = showAirDate,
                showTitle = showTitle,
                showYear = showYear,
                progress = progressByKey[it.stableKey],
                onStatus = onStatus?.let { callback -> { status -> callback(it, status) } },
                onNotInterested = onNotInterested?.let { callback -> { callback(it) } },
                onClick = { onMedia(it) },
            )
        }
    }
}
