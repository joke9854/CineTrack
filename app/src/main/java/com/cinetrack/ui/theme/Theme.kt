package com.cinetrack.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val Background0 = Color(0xFF1C1B22)
val Background1 = Color(0xFF2A2932)
val Glass = Color(0x1FFFFFFF)
val GlassStrong = Color(0x2BFFFFFF)
val GlassBorder = Color(0x34FFFFFF)
val StatusWatching = Color(0xFF1572B1)
val StatusPlanned = Color(0xFFFFC75D)
val StatusPaused = Color(0xFFD46F0F)
val Success = Color(0xFF06804B)
val StatusDropped = Color(0xFFBA1600)
var Accent by mutableStateOf(StatusWatching)
    private set
var AccentLight by mutableStateOf(lerp(StatusWatching, Color.White, .32f))
    private set
val Info = Color(0xFF4FC7E8)
val Violet = Color(0xFF9B7CF0)
val Gold = Color(0xFFF6B23E)
val TextPrimary = Color(0xFFF8F9FB)
val TextSecondary = Color(0xFFD7DEE4)
val TextMuted = Color(0xFFA2AEB8)

const val PosterAspectRatio = 2f / 3f

object DesignTokens {
    const val NavTravelMs = Motion.Long
    const val SheetTravelMs = Motion.Long
    const val WatchedConfirmationMs = 720L
}

fun applyUiAccent(key: String) {
    val color = when (key) {
        "planned" -> StatusPlanned
        "paused" -> StatusPaused
        "completed" -> Success
        "dropped" -> StatusDropped
        else -> StatusWatching
    }
    Accent = color
    AccentLight = lerp(color, Color.White, .32f)
}

@Composable
fun CineTrackTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = darkColorScheme(
        primary = AccentLight,
        onPrimary = Color.White,
        primaryContainer = Accent.copy(alpha = .24f),
        secondary = Info,
        tertiary = Violet,
        background = Background0,
        onBackground = TextPrimary,
        surface = Background1,
        onSurface = TextPrimary,
        surfaceVariant = GlassStrong,
        onSurfaceVariant = TextSecondary,
        outline = GlassBorder,
        error = Color(0xFFFF7C83),
    )
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
        }
    }
    MaterialTheme(colorScheme = colors, typography = CineTrackTypography, content = content)
}
