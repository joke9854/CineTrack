@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.cinetrack.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp
import com.cinetrack.R
import com.cinetrack.ui.theme.Accent
import com.cinetrack.ui.theme.Motion
import com.cinetrack.ui.theme.Radius

fun Modifier.glass(
    shape: RoundedCornerShape = RoundedCornerShape(Radius.Large),
    fill: Color = GlassMaterial.Card,
): Modifier =
    drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        // drawOutline is not available in every Compose UI version supported by
        // this project. Cache a Path instead and render it with stable DrawScope
        // APIs so rounded and generic shapes retain the same external depth.
        val outlinePath = when (outline) {
            is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
            is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
            is Outline.Generic -> outline.path
        }
        // Two cached passes keep the exterior lift while limiting GPU overdraw
        // on poster-heavy lists during cold-start image decoding.
        val shadowSteps = listOf(
            2.dp.toPx() to .072f,
            7.dp.toPx() to .026f,
        )
        onDrawBehind {
            // Draw depth outside the material itself. Avoiding Android's hardware
            // shadow layer also prevents the pale rectangular tiles seen on some
            // Samsung/Android 16 renderers.
            shadowSteps.asReversed().forEach { (offset, alpha) ->
                withTransform({ translate(0f, offset) }) {
                    drawPath(outlinePath, Color.Black.copy(alpha = alpha))
                }
            }
        }
    }
        .clip(shape)
        // Keep chromatic gradients on the page background. Controls use a
        // neutral, low-opacity material so artwork colour can pass through.
        .background(fill)
        .border(
            .9.dp,
            GlassEdgeBrush,
            shape,
        )

/**
 * Small circular controls need room around the material for their shadow.
 * Applying the regular glass modifier directly to a fixed-size IconButton
 * compresses the shadow into the circle and creates the dark inner rings seen
 * in the supplied screenshots.
 */
fun Modifier.glassIcon(): Modifier =
    padding(6.dp)
        .shadow(8.dp, CircleShape, clip = false)
        .clip(CircleShape)
        .background(GlassMaterial.Control)
        .border(.55.dp, ControlEdgeBrush, CircleShape)

fun Modifier.blueEdgeClickable(
    shape: RoundedCornerShape,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val alpha by animateFloatAsState(if (pressed) .95f else 0f, tween(Motion.Short), label = "blueEdgePress")
    this
        .border(1.7.dp, Accent.copy(alpha = alpha), shape)
        .combinedClickable(
            interactionSource = interactionSource,
            indication = null,
            role = Role.Button,
            onClick = {
                onClick()
            },
            onLongClick = onLongClick?.let { action ->
                {
                    action()
                }
            },
        )
}

@Composable
fun GlassBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) = GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.accessibility_back), onClick, modifier)

/** The round glass control of the detail headers (back, more). */
@Composable
fun GlassIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
) {
    val clickAction = rememberUiAction(onClick)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val buttonScale by animateFloatAsState(
        targetValue = if (pressed) .91f else 1f,
        animationSpec = com.cinetrack.ui.theme.Motion.press(),
        label = "backButtonPress",
    )
    Box(modifier.size(52.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(42.dp)
                .scale(buttonScale)
                .liquidGlass(GlassSurface.BackButton, surface = ControlGlassTint) {
                    shadow(8.dp, CircleShape, clip = false)
                        .clip(CircleShape)
                        .background(GlassMaterial.Control)
                        .border(.55.dp, ControlEdgeBrush, CircleShape)
                }
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button,
                    onClick = clickAction,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (busy) androidx.compose.material3.CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            else Icon(icon, description, tint = Color.White, modifier = Modifier.size(22.dp))
        }
    }
}
