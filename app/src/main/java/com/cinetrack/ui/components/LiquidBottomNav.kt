@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.cinetrack.ui.components

import android.animation.ValueAnimator
import android.os.Build
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cinetrack.ui.theme.Accent
import com.cinetrack.ui.theme.AccentLight
import com.cinetrack.ui.theme.Background0
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import com.cinetrack.ui.theme.Motion
import com.cinetrack.ui.theme.Radius
import com.cinetrack.ui.theme.Spacing
import com.cinetrack.ui.theme.SurfacePalette

data class BottomNavItem(
    val route: String,
    val label: String,
    val outlineIcon: ImageVector,
    val filledIcon: ImageVector,
)

@Composable
fun LiquidBottomNav(
    items: List<BottomNavItem>,
    selectedIndex: Int,
    compact: Boolean,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    /** The recorded page: liquid glass bar and a glass droplet on the selected tab. */
    glassBackdrop: com.styropyr0.prismal.PrismalBackdrop? = null,
) {
    val motionEnabled = remember { Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled() }
    val labelFraction by animateFloatAsState(
        targetValue = if (compact) 0f else 1f,
        animationSpec = if (motionEnabled) Motion.enter(Motion.Medium) else tween(Motion.Instant),
        label = "navLabels",
    )
    val height by animateDpAsState(
        if (compact) 54.dp else 64.dp,
        if (motionEnabled) Motion.enter(Motion.Medium) else tween(Motion.Instant),
        label = "navHeight",
    )
    // The bar's size and labels follow these during layout and drawing; only whether the
    // labels exist at all is decided in composition.
    val labelsShown by remember { androidx.compose.runtime.derivedStateOf { labelFraction > .01f } }
    val sidePadding by animateDpAsState(
        if (compact) 58.dp else 28.dp,
        if (motionEnabled) Motion.enter(Motion.Medium) else tween(Motion.Instant),
        label = "navSidePadding",
    )
    Box(
        modifier
            .fillMaxWidth()
            .horizontalPaddingOf { sidePadding }
            .heightOf { height }
            .shadow(15.dp, RoundedCornerShape(Radius.Pill), clip = false)
            .liquidGlass(
                surface = Background0.copy(alpha = .50f),
                refraction = 16.dp,
                blur = 14.dp,
                shadow = false,
                backdrop = glassBackdrop,
            ) {
                clip(RoundedCornerShape(Radius.Pill))
                    .then(if (hazeState != null) Modifier.hazeEffect(hazeState, style = NavGlassStyle) else Modifier.background(GlassMaterial.OverlayFallback))
                    .border(.6.dp, ControlEdgeBrush, RoundedCornerShape(Radius.Pill))
            }
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
    ) {
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
            // One selection droplet slides between tabs instead of each tab fading its own fill.
            val itemWidth = maxWidth / items.size.coerceAtLeast(1)
            val dropletX by animateDpAsState(
                itemWidth * selectedIndex,
                if (motionEnabled) Motion.snappy() else tween(Motion.Instant),
                label = "navDroplet",
            )
            Box(
                Modifier
                    .offset { androidx.compose.ui.unit.IntOffset(dropletX.roundToPx(), 0) }
                    .width(itemWidth)
                    .fillMaxHeight()
                    .liquidGlass(
                        surface = Accent.copy(alpha = .24f),
                        refraction = 10.dp,
                        under = Background0.copy(alpha = .38f),
                        blur = 2.dp,
                        shadow = false,
                        backdrop = glassBackdrop,
                    ) { clip(RoundedCornerShape(Radius.Pill)).background(Accent.copy(alpha = .18f)) },
            )
        Row(Modifier.fillMaxSize()) {
            items.forEachIndexed { index, item ->
                val selected = index == selectedIndex
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(Radius.Pill))
                        .clickable {
                            onSelected(index)
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        if (selected) item.filledIcon else item.outlineIcon,
                        contentDescription = item.label,
                        tint = if (selected) AccentLight else SurfacePalette.BrightText,
                        modifier = Modifier.size(if (compact) 25.dp else 22.dp),
                    )
                    if (labelsShown) {
                        Column(
                            Modifier.graphicsLayer { alpha = labelFraction },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Spacer(Modifier.heightOf { 6.dp * labelFraction })
                            Text(
                                item.label,
                                color = if (selected) Color.White else SurfacePalette.LightText,
                                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                                lineHeight = 11.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                modifier = Modifier.heightOf { 12.dp * labelFraction },
                            )
                        }
                    }
                }
            }
        }
        }
    }
}
