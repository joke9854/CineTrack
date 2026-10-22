@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.cinetrack.ui.components

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cinetrack.R
import com.cinetrack.domain.LibraryStatus
import com.cinetrack.domain.MediaCard
import com.cinetrack.ui.theme.Accent
import com.cinetrack.ui.theme.Success
import com.cinetrack.ui.theme.StatusDropped
import com.cinetrack.ui.theme.StatusPaused
import com.cinetrack.ui.theme.StatusPlanned
import com.cinetrack.ui.theme.StatusWatching
import com.cinetrack.ui.theme.TextMuted
import com.cinetrack.ui.theme.TextPrimary
import com.cinetrack.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import dev.chrisbanes.haze.HazeState
import com.cinetrack.ui.theme.Glass
import com.cinetrack.ui.theme.GlassSubtle
import com.cinetrack.ui.theme.LocalDetailAccent
import com.cinetrack.ui.theme.Radius
import com.cinetrack.ui.theme.Spacing
import com.cinetrack.ui.theme.SurfacePalette

@Composable
fun rememberUiAction(action: () -> Unit): () -> Unit {
    val currentAction by rememberUpdatedState(action)
    return remember {
        {
            currentAction()
        }
    }
}

fun libraryStatusColor(status: LibraryStatus): Color = when (status) {
    LibraryStatus.WATCHING -> StatusWatching
    LibraryStatus.PLAN_TO_WATCH -> StatusPlanned
    LibraryStatus.PAUSED -> StatusPaused
    LibraryStatus.COMPLETED -> Success
    LibraryStatus.DROPPED -> StatusDropped
    LibraryStatus.NONE -> Accent
}

fun libraryStatusIcon(status: LibraryStatus): ImageVector = when (status) {
    LibraryStatus.WATCHING -> Icons.Filled.Visibility
    LibraryStatus.PLAN_TO_WATCH -> Icons.Filled.Bookmark
    LibraryStatus.PAUSED -> Icons.Filled.Pause
    LibraryStatus.COMPLETED -> Icons.Filled.Check
    LibraryStatus.DROPPED -> Icons.Filled.VisibilityOff
    LibraryStatus.NONE -> Icons.Filled.Bookmark
}

/**
 * Gives content restored from the local cache a calm entrance instead of
 * snapping into place when a database flow or background enrichment finishes.
 */
@Composable
fun RevealOnMount(
    animationKey: Any?,
    modifier: Modifier = Modifier,
    delayMillis: Int = 0,
    content: @Composable () -> Unit,
) {
    // Keep list rows layer-free. The app-level launch/page transitions provide
    // continuity while LazyColumn/LazyRow items can now be recycled cheaply.
    Box(modifier) { content() }
}

@Composable
fun PageTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        modifier = modifier,
        color = TextPrimary,

        lineHeight = 34.sp,
        fontWeight = FontWeight.ExtraBold,
        style = MaterialTheme.typography.displaySmall,
    )
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            color = TextPrimary,

            lineHeight = 21.sp,
            fontWeight = FontWeight.ExtraBold,
            style = MaterialTheme.typography.titleMedium.copy(
                shadow = Shadow(Color.Black.copy(alpha = .62f), Offset(0f, 2.5f), 6f),
            ),
            modifier = Modifier.weight(1f),
        )
        if (actionLabel != null && onAction != null) {
            val primaryAction = rememberUiAction(onAction)
            Row(
                Modifier.clip(RoundedCornerShape(Radius.Pill)).clickable(onClick = primaryAction).padding(start = Spacing.sm, top = Spacing.sm, bottom = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(actionLabel, color = TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(2.dp))
                Icon(Icons.Filled.ChevronRight, null, tint = TextSecondary, modifier = Modifier.size(15.dp))
            }
        }
    }
}

@Composable
fun MediaStatusPopup(
    expanded: Boolean,
    currentStatus: LibraryStatus,
    onDismiss: () -> Unit,
    onStatus: (LibraryStatus) -> Unit,
    onNotInterested: (() -> Unit)? = null,
    media: MediaCard? = null,
) {
    MediaContextMenu(expanded, media, currentStatus, onDismiss, onStatus, onNotInterested)
}

@Composable
fun PrimaryAction(
    text: String,
    icon: ImageVector = Icons.Filled.PlayArrow,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = Accent,
    compact: Boolean = false,
    liveGlass: Boolean = false,
    hazeState: HazeState? = null,
    onClick: () -> Unit,
) {
    val clickAction = rememberUiAction(onClick)
    val liveFallback = if (liveGlass) Modifier.liveActionGlass(hazeState, containerColor == Success) else Modifier
    Row(
        modifier
            .then(if (compact) Modifier.heightIn(min = 52.dp) else Modifier.height(52.dp))
            .then(if (liveGlass) Modifier.liquidGlass(
                surface = if (containerColor == Success) Success.copy(alpha = .50f) else (LocalDetailAccent.current ?: Accent).copy(alpha = .42f),
                refraction = 10.dp,
                under = GlassMaterial.DetailSurface,
            ) { then(liveFallback) } else Modifier
            .glass(RoundedCornerShape(Radius.Pill))
            .background(
                if (enabled) SolidColor(containerColor.copy(alpha = .82f))
                else SolidColor(GlassSubtle),
            )
            .border(.7.dp, if (enabled) containerColor.copy(alpha = .72f) else SurfacePalette.DisabledControl.copy(alpha = .22f), RoundedCornerShape(Radius.Pill)))
            .clickable(enabled = enabled, onClick = clickAction)
            .padding(horizontal = if (compact) 8.dp else Spacing.lg, vertical = if (compact) 8.dp else 0.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (enabled) Color.White else TextMuted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(7.dp))
        Text(text, modifier = if (compact) Modifier.weight(1f, fill = false) else Modifier,
            color = if (enabled) Color.White else TextMuted, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.ExtraBold, maxLines = if (compact) 2 else 1,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun BrandMark(size: Dp = 84.dp, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Image(
        painter = androidx.compose.ui.res.painterResource(com.cinetrack.R.drawable.ic_brand_mark),
        contentDescription = "CineTrack",
        modifier = modifier.size(size).clip(RoundedCornerShape(size * .23f)),
    )
}

@Composable
fun GlassDivider() {
    HorizontalDivider(color = Glass, thickness = 1.dp)
}

@Composable
fun LoadingPane(modifier: Modifier = Modifier) {
    val loading = stringResource(R.string.loading)
    Column(
        modifier.fillMaxSize().padding(Spacing.xl).semantics { contentDescription = loading },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.CenterVertically),
    ) {
        SkeletonBox(Modifier.width(160.dp).height(240.dp))
        SkeletonBox(Modifier.fillMaxWidth(.72f).height(24.dp))
        SkeletonBox(Modifier.fillMaxWidth(.9f).height(14.dp))
        SkeletonBox(Modifier.fillMaxWidth(.8f).height(14.dp))
        SkeletonBox(Modifier.fillMaxWidth().height(80.dp))
    }
}
