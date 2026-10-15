package com.cinetrack.ui.components

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * A detail page that follows the back gesture itself: while the finger is down the page
 * shrinks a little towards the far side, with rounded corners, carrying its poster along.
 * Only when the gesture is released does the page close, so the poster flies home exactly
 * as it does after a tap on the back button. (Letting the navigation seek the closing
 * transition instead detached the poster mid-gesture and swapped in the list's copy.)
 */
@Composable
fun PredictiveBackPage(onBack: () -> Unit, content: @Composable () -> Unit) {
    val progress = remember { Animatable(0f) }
    var edge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }
    var leaving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    PredictiveBackHandler(enabled = !leaving) { events ->
        try {
            events.collect { event ->
                edge = event.swipeEdge
                progress.snapTo(event.progress)
            }
            leaving = true
            onBack()
        } catch (cancelled: CancellationException) {
            scope.launch { progress.animateTo(0f, spring(stiffness = 600f)) }
            throw cancelled
        }
    }
    Box(
        Modifier.fillMaxSize().graphicsLayer {
            val p = progress.value
            if (p > 0f) {
                val scale = 1f - .1f * p
                scaleX = scale
                scaleY = scale
                translationX = (if (edge == BackEventCompat.EDGE_LEFT) 1f else -1f) * 16.dp.toPx() * p
                shape = RoundedCornerShape(32.dp * p)
                clip = true
            }
        },
    ) { content() }
}
