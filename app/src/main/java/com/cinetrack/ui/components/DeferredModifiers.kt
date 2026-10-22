package com.cinetrack.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.offset
import kotlin.math.roundToInt

/**
 * Same as `fillMaxWidth(fraction)`, but [fraction] is read during layout, so an animated
 * value only re-measures this element instead of recomposing its caller every frame.
 */
fun Modifier.fillMaxWidthOf(fraction: () -> Float): Modifier = layout { measurable, constraints ->
    val childConstraints = if (constraints.hasBoundedWidth) {
        val width = (constraints.maxWidth * fraction()).roundToInt().coerceIn(constraints.minWidth, constraints.maxWidth)
        Constraints(width, width, constraints.minHeight, constraints.maxHeight)
    } else constraints
    val placeable = measurable.measure(childConstraints)
    layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
}

/** Same as `height(height)`, but [height] is read during layout. */
fun Modifier.heightOf(height: () -> Dp): Modifier = layout { measurable, constraints ->
    val target = constraints.constrainHeight(height().roundToPx().coerceAtLeast(0))
    val placeable = measurable.measure(constraints.copy(minHeight = target, maxHeight = target))
    layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
}

/** Same as `padding(horizontal = padding)`, but [padding] is read during layout. */
fun Modifier.horizontalPaddingOf(padding: () -> Dp): Modifier = layout { measurable, constraints ->
    val side = padding().roundToPx().coerceAtLeast(0)
    val placeable = measurable.measure(constraints.offset(horizontal = -2 * side))
    layout(constraints.constrainWidth(placeable.width + 2 * side), constraints.constrainHeight(placeable.height)) {
        placeable.placeRelative(side, 0)
    }
}
