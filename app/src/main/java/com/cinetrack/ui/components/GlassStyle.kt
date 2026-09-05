package com.cinetrack.ui.components

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cinetrack.ui.theme.Background0
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint

// Only the navigation pill opts into live backdrop blur. Older/low-RAM devices
// keep the original material; broaden use only after physical-device frame tests.
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
