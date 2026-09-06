package com.cinetrack.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import dev.chrisbanes.haze.HazeState
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cinetrack.ui.theme.Background0
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint

// Shared live material for the navigation pill and the first detail-sheet stage.
// Other surfaces await the requested physical-device scroll check.
internal val NavGlassStyle = HazeStyle(
    backgroundColor = Background0,
    tint = HazeTint(Background0.copy(alpha = .58f)),
    blurRadius = 16.dp,
    noiseFactor = .03f,
    fallbackTint = HazeTint(Background0.copy(alpha = .78f)),
)

internal val GlassEdgeBrush = Brush.linearGradient(
    listOf(Color.White.copy(alpha = .20f), Color.White.copy(alpha = .04f)),
)

/** Match the navigation blur device policy; null retains the existing surface. */
@Composable
internal fun rememberDetailGlassState(): HazeState? {
    val context = LocalContext.current
    return remember(context) {
        val manager = context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        if (android.os.Build.VERSION.SDK_INT >= 31 && !manager.isLowRamDevice) HazeState() else null
    }
}
