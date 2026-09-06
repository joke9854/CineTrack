package com.cinetrack.ui.theme

import androidx.compose.ui.unit.dp

object Radius {
    val Compact = 8.dp
    val Small = 12.dp
    val Medium = 16.dp
    val Large = 22.dp
    val Pill = 999.dp
    // Preserve deliberate sheet silhouettes; collapsing to Large would exceed 3dp.
    val Sheet = 28.dp
    val TallSheet = 32.dp
}
