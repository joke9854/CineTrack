package com.cinetrack.ui.components

import android.animation.ValueAnimator
import android.os.Build
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.cinetrack.R
import com.cinetrack.ui.theme.GlassStrong
import com.cinetrack.ui.theme.Motion
import com.cinetrack.ui.theme.Radius
import com.cinetrack.ui.theme.Spacing
import com.cinetrack.ui.theme.PosterAspectRatio

/** A 0..1 pulse that reverses every [durationMillis]; skeletons map it onto their own range. */
@Stable
internal class SkeletonPulse(val durationMillis: Int, val reducedMotion: Boolean, val fraction: State<Float>)

/** The pulse shared by every skeleton inside a [SkeletonPulseGroup]; null outside one. */
private val LocalSkeletonPulse = compositionLocalOf<SkeletonPulse?> { null }

internal const val SkeletonBoxPulseMillis = Motion.Long * 3

@Composable
private fun rememberSkeletonPulse(durationMillis: Int, reducedMotion: Boolean): SkeletonPulse {
    val fraction = if (reducedMotion) remember { mutableStateOf(0f) } else rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis), RepeatMode.Reverse),
        label = "skeletonPulse",
    )
    return remember(durationMillis, reducedMotion, fraction) { SkeletonPulse(durationMillis, reducedMotion, fraction) }
}

/**
 * The shared pulse for [durationMillis], or a pulse of this skeleton's own when there is
 * no matching [SkeletonPulseGroup] above it.
 */
@Composable
internal fun skeletonPulse(durationMillis: Int, reducedMotion: Boolean): SkeletonPulse {
    val shared = LocalSkeletonPulse.current
    return if (shared != null && shared.durationMillis == durationMillis && shared.reducedMotion == reducedMotion) shared
    else rememberSkeletonPulse(durationMillis, reducedMotion)
}

/**
 * Runs one pulse animation for all the skeletons in [content] instead of one per skeleton.
 * With [enabled] false (nothing in [content] is loading) no animation runs at all.
 */
@Composable
internal fun SkeletonPulseGroup(
    durationMillis: Int = SkeletonBoxPulseMillis,
    reducedMotion: Boolean = !skeletonMotionEnabled(),
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val pulse = if (enabled) rememberSkeletonPulse(durationMillis, reducedMotion) else null
    CompositionLocalProvider(LocalSkeletonPulse provides pulse, content = content)
}

internal fun skeletonMotionEnabled(): Boolean = Build.VERSION.SDK_INT < 26 || ValueAnimator.areAnimatorsEnabled()

@Composable
fun SkeletonBox(
    modifier: Modifier,
    shape: Shape = RoundedCornerShape(Radius.Medium),
    description: String? = null,
) {
    val pulse = skeletonPulse(SkeletonBoxPulseMillis, reducedMotion = !skeletonMotionEnabled())
    Box(
        modifier.clearAndSetSemantics { if (description != null) contentDescription = description }
            .graphicsLayer { alpha = if (pulse.reducedMotion) .72f else lerp(.55f, .90f, pulse.fraction.value) }
            .clip(shape).background(GlassStrong),
    )
}

@Composable
fun SkeletonLines(modifier: Modifier = Modifier) {
    val loading = stringResource(R.string.loading)
    Column(modifier.semantics { contentDescription = loading }, verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        SkeletonPulseGroup {
            repeat(3) { index ->
                SkeletonBox(Modifier.fillMaxWidth(if (index == 2) .65f else 1f).height(14.dp))
            }
        }
    }
}

@Composable
fun SkeletonPosterRow(modifier: Modifier = Modifier) {
    val loading = stringResource(R.string.loading)
    Row(modifier.semantics { contentDescription = loading }, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        SkeletonPulseGroup { repeat(3) { SkeletonBox(Modifier.weight(1f).aspectRatio(PosterAspectRatio)) } }
    }
}
