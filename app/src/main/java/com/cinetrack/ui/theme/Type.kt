package com.cinetrack.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

// Seven sizes taken from the existing UI. Weight, line height and tracking
// remain the responsibility of the existing semantic components.
val CineTrackTypography = Typography(
    labelSmall = TextStyle(fontSize = 10.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    titleSmall = TextStyle(fontSize = 16.sp),
    titleMedium = TextStyle(fontSize = 18.sp),
    titleLarge = TextStyle(fontSize = 22.sp),
    displaySmall = TextStyle(fontSize = 29.sp),
)
