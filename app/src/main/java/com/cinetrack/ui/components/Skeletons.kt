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
import com.cinetrack.R
import com.cinetrack.ui.theme.GlassStrong
import com.cinetrack.ui.theme.Motion
import com.cinetrack.ui.theme.Radius
import com.cinetrack.ui.theme.Spacing

@Composable
fun SkeletonBox(modifier: Modifier, shape: Shape = RoundedCornerShape(Radius.Medium)) {
    val motionEnabled = Build.VERSION.SDK_INT < 26 || ValueAnimator.areAnimatorsEnabled()
    val opacity = if (motionEnabled) {
        rememberInfiniteTransition(label = "skeleton").animateFloat(
            initialValue = .55f,
            targetValue = .90f,
            animationSpec = infiniteRepeatable(tween(Motion.Long * 3), RepeatMode.Reverse),
            label = "skeletonOpacity",
        )
    } else remember { mutableStateOf(.72f) }
    Box(
        modifier.clearAndSetSemantics { }
            .graphicsLayer { alpha = opacity.value }
            .clip(shape).background(GlassStrong),
    )
}

@Composable
fun SkeletonLines(modifier: Modifier = Modifier) {
    val loading = stringResource(R.string.loading)
    Column(modifier.semantics { contentDescription = loading }, verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        repeat(3) { index ->
            SkeletonBox(Modifier.fillMaxWidth(if (index == 2) .65f else 1f).height(14.dp))
        }
    }
}

@Composable
fun SkeletonPosterRow(modifier: Modifier = Modifier) {
    val loading = stringResource(R.string.loading)
    Row(modifier.semantics { contentDescription = loading }, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        repeat(3) { SkeletonBox(Modifier.weight(1f).aspectRatio(2f / 3f)) }
    }
}
