package com.cinetrack.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import dev.chrisbanes.haze.hazeSource
import androidx.compose.ui.platform.LocalContext
import dev.chrisbanes.haze.HazeState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cinetrack.ui.theme.Background0
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint

// One material family: lightweight content, compact controls, and live overlays.
internal object GlassMaterial {
    val Content = com.cinetrack.ui.theme.SurfacePalette.GlassSurface.copy(alpha = .27f)
    val Control = com.cinetrack.ui.theme.SurfacePalette.GlassSurface.copy(alpha = .58f)
    val OverlayFallback = Background0.copy(alpha = .90f)
    val DetailSurface = Background0.copy(alpha = .58f)
}

// Shared live material for navigation and floating sheets/dialogs.
internal val NavGlassStyle = HazeStyle(
    backgroundColor = Background0,
    tint = HazeTint(Background0.copy(alpha = .58f)),
    blurRadius = 16.dp,
    noiseFactor = .03f,
    fallbackTint = HazeTint(GlassMaterial.OverlayFallback),
)

// Detail foreground sections supply their own tint; blur the backdrop only once.
internal val DetailBackdropStyle = HazeStyle(
    backgroundColor = Background0,
    tint = HazeTint(Color.Transparent),
    blurRadius = 16.dp,
    noiseFactor = .03f,
    fallbackTint = HazeTint(Color.Transparent),
)

internal val GlassEdgeBrush = Brush.linearGradient(
    listOf(Color.White.copy(alpha = .20f), Color.White.copy(alpha = .04f)),
)

private class FloatingGlassCapture(val state: HazeState?) {
    var users by mutableIntStateOf(0)
}

private val LocalFloatingGlass = staticCompositionLocalOf<FloatingGlassCapture?> { null }

/** Capture the page once, and only while navigation or a floating surface needs it. */
@Composable
internal fun FloatingGlassHost(state: HazeState?, navigationVisible: Boolean, content: @Composable (Modifier) -> Unit) {
    val capture = remember(state) { FloatingGlassCapture(state) }
    CompositionLocalProvider(LocalFloatingGlass provides capture) {
        content(if (state != null && (navigationVisible || capture.users > 0)) Modifier.hazeSource(state) else Modifier)
    }
}

@Composable
internal fun rememberFloatingGlassState(): HazeState? {
    val capture = LocalFloatingGlass.current
    DisposableEffect(capture) {
        if (capture?.state != null) capture.users++
        onDispose { if (capture?.state != null) capture.users-- }
    }
    return capture?.state
}

/** Match the navigation blur device policy; null retains the existing surface. */
@Composable
internal fun rememberDetailGlassState(): HazeState? {
    val context = LocalContext.current
    return remember(context) {
        val manager = context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        if (android.os.Build.VERSION.SDK_INT >= 31 && !manager.isLowRamDevice) HazeState() else null
    }
}
