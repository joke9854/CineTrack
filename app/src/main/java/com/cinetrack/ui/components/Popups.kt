@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.cinetrack.ui.components

import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.widthIn
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import coil3.compose.AsyncImage
import com.cinetrack.R
import com.cinetrack.domain.LibraryStatus
import com.cinetrack.domain.MediaCard
import com.cinetrack.domain.MediaType
import com.cinetrack.ui.theme.TextMuted
import com.cinetrack.ui.theme.TextPrimary
import com.cinetrack.ui.theme.TextSecondary
import com.cinetrack.ui.theme.Accent
import com.cinetrack.ui.theme.AccentLight
import com.cinetrack.ui.theme.Gold
import com.cinetrack.ui.theme.Radius
import com.cinetrack.ui.theme.StatusDropped
import com.cinetrack.ui.theme.Success
import com.cinetrack.ui.theme.SurfacePalette

/** Popup material shared by menus, dialogs and sheets. */
internal object PopupMaterial {
    val GroupShape = RoundedCornerShape(18.dp)
    val GroupFill = Color.White.copy(alpha = .05f)
    val GroupEdge = Color.White.copy(alpha = .06f)
    val Hairline = Color.White.copy(alpha = .08f)
    val Scrim = Color(0xFF050608)
    /** Sheets float above the page with a margin and round on all corners. */
    val FloatingShape = RoundedCornerShape(30.dp)
    val FloatingMargin = 6.dp
    const val Dim = .38f
    /** Popup surface over the blurred app. */
    val Panel = Color(0xB8101318)
    /** Light: the page stays readable behind the popup, just out of focus. */
    val BlurRadius = 7.dp
}

/** Counts open popups so the app behind them can blur; system window blur is often off. */
class PopupBlurController {
    internal var open by androidx.compose.runtime.mutableIntStateOf(0)
}

val LocalPopupBlur = androidx.compose.runtime.staticCompositionLocalOf<PopupBlurController?> { null }

/** Blurs this layer lightly while any popup is open (API 31+). */
@Composable
fun Modifier.popupBackdropBlur(controller: PopupBlurController): Modifier {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) return this
    // Switched on in one step: animating the radius redrew the whole page on every frame
    // of the sheet animation. The sheet's slide and scrim fade carry the transition.
    val open = controller.open > 0
    return graphicsLayer {
        val radius = if (open) PopupMaterial.BlurRadius.toPx() else 0f
        renderEffect = if (radius > .5f) androidx.compose.ui.graphics.BlurEffect(radius, radius, androidx.compose.ui.graphics.TileMode.Clamp) else null
    }
}

/** True when popups blur the app behind them (API 31+ inside the app root). */
@Composable
internal fun rememberAppBlurActive(): Boolean =
    android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S && LocalPopupBlur.current != null

/** Registers an open popup with the app blur while [visible]. */
@Composable
internal fun PopupBackdropBlur(visible: Boolean = true) {
    val controller = LocalPopupBlur.current ?: return
    androidx.compose.runtime.DisposableEffect(controller, visible) {
        if (visible) controller.open++
        onDispose { if (visible) controller.open-- }
    }
}

/** Dims the app behind the dialog window this is composed in. */
@Composable
internal fun DialogWindowDim(amount: Float = PopupMaterial.Dim) {
    val view = LocalView.current
    val window = remember(view) {
        generateSequence(view as View?) { it.parent as? View }.filterIsInstance<DialogWindowProvider>().firstOrNull()?.window
    } ?: return
    LaunchedEffect(window, amount) {
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.setDimAmount(amount)
    }
}

/** Large left-aligned sheet title. */
@Composable
internal fun SheetTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.fillMaxWidth().padding(start = 4.dp, top = 2.dp, bottom = 14.dp),
        color = TextPrimary,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.ExtraBold,
    )
}

/** A grouped glass list: rows share one rounded surface and are split by hairlines. */
@Composable
internal fun GlassMenuGroup(
    modifier: Modifier = Modifier,
    /** Taller content scrolls inside the group; a fade marks the rows below the edge. */
    maxHeight: androidx.compose.ui.unit.Dp = androidx.compose.ui.unit.Dp.Unspecified,
    content: @Composable () -> Unit,
) {
    val scroll = if (maxHeight != androidx.compose.ui.unit.Dp.Unspecified) androidx.compose.foundation.rememberScrollState() else null
    Box(
        modifier.fillMaxWidth().clip(PopupMaterial.GroupShape)
            .background(PopupMaterial.GroupFill)
            .border(.6.dp, PopupMaterial.GroupEdge, PopupMaterial.GroupShape),
    ) {
        Column(
            if (scroll == null) Modifier.fillMaxWidth()
            else Modifier.fillMaxWidth().heightIn(max = maxHeight).verticalScroll(scroll)
                .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    val fade = 36.dp.toPx()
                    val more = scroll.value < scroll.maxValue
                    if (more) drawRect(
                        Brush.verticalGradient(0f to Color.White, 1f to Color.Transparent, startY = size.height - fade, endY = size.height),
                        topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - fade),
                        blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
                    )
                },
        ) { content() }
    }
}

/**
 * One row of a [GlassMenuGroup]. Without an [icon] it is a plain text option; the selected
 * option is written in [tint], the way a single-choice list reads at a glance.
 */
@Composable
internal fun GlassMenuRow(
    label: String,
    icon: ImageVector?,
    tint: Color,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    selected: Boolean = false,
    divider: Boolean = true,
    onClick: () -> Unit,
) {
    val clickAction = rememberUiAction(onClick)
    Box(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 54.dp)
                .clickable(onClick = clickAction)
                .padding(horizontal = 16.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Box(Modifier.size(32.dp).clip(CircleShape).background(tint.copy(alpha = .16f)), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = tint, modifier = Modifier.size(17.dp))
                }
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    label,
                    color = if (selected) tint else TextPrimary,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!supporting.isNullOrBlank()) {
                    Text(supporting, color = TextMuted, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (selected) {
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Filled.Check, null, tint = tint, modifier = Modifier.size(18.dp))
            }
        }
        if (divider) {
            Box(Modifier.align(Alignment.BottomStart).padding(start = if (icon != null) 60.dp else 16.dp).fillMaxWidth().height(.6.dp).background(PopupMaterial.Hairline))
        }
    }
}

/**
 * Long-press actions as a floating card: poster, title and plot on top, the library
 * states as round buttons, then "watched" and "not interested" as the two large actions.
 */
@Composable
internal fun MediaContextMenu(
    expanded: Boolean,
    media: MediaCard?,
    currentStatus: LibraryStatus,
    onDismiss: () -> Unit,
    onStatus: (LibraryStatus) -> Unit,
    onNotInterested: (() -> Unit)? = null,
) {
    if (!expanded) return
    SharedGlassSheet(onDismiss) {
        Column(Modifier.padding(horizontal = 12.dp)) {
            if (media != null) {
                ContextMenuHeader(media)
                Box(Modifier.padding(vertical = 16.dp).fillMaxWidth().height(.6.dp).background(PopupMaterial.Hairline))
            }
            val round = listOf(
                LibraryStatus.PLAN_TO_WATCH to stringResource(R.string.plan_to_watch),
                LibraryStatus.WATCHING to stringResource(R.string.in_progress),
                LibraryStatus.PAUSED to stringResource(R.string.paused),
                LibraryStatus.DROPPED to stringResource(R.string.dropped),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                round.forEach { (status, label) ->
                    val selected = currentStatus == status
                    RoundAction(label, libraryStatusIcon(status), libraryStatusColor(status), selected) {
                        onStatus(if (selected) LibraryStatus.NONE else status)
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val completed = currentStatus == LibraryStatus.COMPLETED
                TonalButton(
                    text = stringResource(R.string.watched),
                    icon = Icons.Filled.Check,
                    filled = completed,
                    tint = Success,
                    modifier = Modifier.weight(1f),
                ) { onStatus(if (completed) LibraryStatus.NONE else LibraryStatus.COMPLETED) }
                if (onNotInterested != null) {
                    TonalButton(
                        text = stringResource(R.string.show_less_like_this).substringBefore(" · "),
                        icon = Icons.Filled.ThumbDown,
                        filled = false,
                        tint = TextPrimary,
                        modifier = Modifier.weight(1f),
                        onClick = onNotInterested,
                    )
                }
            }
        }
    }
}

@Composable
private fun ContextMenuHeader(media: MediaCard) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Box(
            Modifier.width(72.dp).height(108.dp).clip(RoundedCornerShape(12.dp))
                .background(Brush.linearGradient(listOf(SurfacePalette.PosterShadow, PopupMaterial.Scrim))),
        ) {
            val art = media.posterUrl ?: media.backdropUrl
            if (!art.isNullOrBlank()) AsyncImage(art, media.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(media.title, color = TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 3.dp)) {
                media.score?.takeIf { it > 0 }?.let { score ->
                    Icon(Icons.Filled.Star, null, tint = Gold, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(3.dp))
                    Text(String.format(com.cinetrack.ui.currentLocale(), "%.1f", score) + "  •  ", color = TextSecondary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
                Text(
                    listOf(media.year, stringResource(if (media.type == MediaType.TV) R.string.tv_shows else R.string.movies))
                        .filter(String::isNotBlank).joinToString("  •  "),
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                )
            }
            if (media.overview.isNotBlank()) {
                Text(
                    media.overview,
                    color = TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

/** Round glass action with a label; filled with [tint] while [selected]. */
@Composable
private fun RoundAction(label: String, icon: ImageVector, tint: Color, selected: Boolean, onClick: () -> Unit) {
    val clickAction = rememberUiAction(onClick)
    Column(
        Modifier.width(78.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = clickAction).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(56.dp).clip(CircleShape)
                .background(if (selected) tint.copy(alpha = .85f) else Color.White.copy(alpha = .06f))
                .border(.8.dp, if (selected) tint.copy(alpha = .7f) else Color.White.copy(alpha = .16f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = if (selected) Color.White else TextPrimary, modifier = Modifier.size(22.dp))
        }
        Text(
            label,
            color = if (selected) TextPrimary else TextSecondary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/**
 * Glass + Material You button: [filled] is a tonal container in [tint] (Material's
 * filled-tonal role), otherwise an outlined glass button. 52dp tall, full pill.
 */
@Composable
internal fun TonalButton(
    text: String,
    icon: ImageVector?,
    filled: Boolean,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val clickAction = rememberUiAction(onClick)
    val shape = RoundedCornerShape(Radius.Pill)
    Row(
        modifier.height(52.dp).clip(shape)
            .background(if (filled) tint.copy(alpha = .78f) else Color.White.copy(alpha = .05f))
            .border(.8.dp, if (filled) Color.White.copy(alpha = .12f) else Color.White.copy(alpha = .16f), shape)
            .clickable(onClick = clickAction)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = Color.White, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Floating pill with the current backup or restore stage and a thin progress line. */
@Composable
fun BackupProgressPill(progress: com.cinetrack.domain.BackupProgress) {
    val shape = RoundedCornerShape(Radius.Pill)
    val tint = when {
        progress.failed -> StatusDropped
        progress.finished -> Success
        else -> AccentLight
    }
    val fraction by androidx.compose.animation.core.animateFloatAsState(
        progress.fraction ?: 0f,
        androidx.compose.animation.core.tween(300),
        label = "backupFraction",
    )
    Column(
        Modifier.widthIn(min = 240.dp, max = 340.dp)
            .shadow(16.dp, shape, clip = false, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .background(Color(0xF0121419))
            .border(.6.dp, ControlEdgeBrush, shape)
            .semantics(mergeDescendants = true) { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite },
    ) {
        Row(Modifier.padding(start = 14.dp, end = 18.dp, top = 10.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                when {
                    progress.failed -> Icon(Icons.Filled.Close, null, tint = tint, modifier = Modifier.size(20.dp))
                    progress.finished -> Icon(Icons.Filled.Check, null, tint = tint, modifier = Modifier.size(20.dp))
                    else -> androidx.compose.material3.CircularProgressIndicator(Modifier.size(20.dp), color = tint, strokeWidth = 2.dp, trackColor = Color.White.copy(alpha = .10f))
                }
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    stringResource(if (progress.restore) R.string.backup_restoring else R.string.backup_creating),
                    color = TextPrimary, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.ExtraBold, maxLines = 1,
                )
                Text(
                    stringResource(progress.stage) + (progress.fraction?.takeIf { !progress.finished }?.let { " · ${(it * 100).toInt()}%" } ?: ""),
                    color = TextSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1,
                )
            }
        }
        // Progress line along the bottom edge, like the Progress cards.
        Box(Modifier.fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = .06f))) {
            if (progress.fraction != null) Box(Modifier.fillMaxWidthOf { fraction }.height(3.dp).background(tint))
        }
    }
}

/** What a [StatusPill] reports: work going on, done, or failed. */
enum class PillTone { RUNNING, INFO, DONE, FAILED }

/**
 * Floating pill for background work and short offers (library refresh, app
 * update): same look as the backup pill, with an optional action and close.
 */
@Composable
fun StatusPill(
    title: String,
    detail: String?,
    tone: PillTone,
    fraction: Float? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(Radius.Pill)
    val tint = when (tone) {
        PillTone.FAILED -> StatusDropped
        PillTone.DONE -> Success
        else -> AccentLight
    }
    val shownFraction by androidx.compose.animation.core.animateFloatAsState(fraction ?: 0f, androidx.compose.animation.core.tween(300), label = "pillFraction")
    Column(
        Modifier.widthIn(min = 260.dp, max = 360.dp)
            .shadow(16.dp, shape, clip = false, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .background(Color(0xF0121419))
            .border(.6.dp, ControlEdgeBrush, shape)
            .semantics(mergeDescendants = false) { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite },
    ) {
        Row(Modifier.padding(start = 14.dp, end = if (onDismiss != null) 4.dp else 18.dp, top = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                when (tone) {
                    PillTone.FAILED -> Icon(Icons.Filled.Close, null, tint = tint, modifier = Modifier.size(20.dp))
                    PillTone.DONE -> Icon(Icons.Filled.Check, null, tint = tint, modifier = Modifier.size(20.dp))
                    PillTone.INFO -> Icon(Icons.Filled.SystemUpdate, null, tint = tint, modifier = Modifier.size(20.dp))
                    PillTone.RUNNING -> androidx.compose.material3.CircularProgressIndicator(Modifier.size(20.dp), color = tint, strokeWidth = 2.dp, trackColor = Color.White.copy(alpha = .10f))
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f, fill = false)) {
                Text(title, color = TextPrimary, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                if (!detail.isNullOrBlank()) Text(detail, color = TextSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier.clip(shape).background(Accent).clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onAction)
                        .heightIn(min = 34.dp).padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(actionLabel, color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold) }
            }
            if (onDismiss != null) {
                Box(
                    Modifier.size(40.dp).clip(androidx.compose.foundation.shape.CircleShape).clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Close, stringResource(R.string.close), tint = TextSecondary, modifier = Modifier.size(18.dp)) }
            }
        }
        Box(Modifier.fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = .06f))) {
            if (fraction != null) Box(Modifier.fillMaxWidthOf { shownFraction }.height(3.dp).background(tint))
        }
    }
}
