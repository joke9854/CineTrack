package com.cinetrack.ui.theme

import androidx.compose.ui.graphics.Color

// Distinct artwork palettes and layered sheet/control colors retain their exact
// values. They are not interchangeable with the opaque page background.
object SurfacePalette {
    val DeepOverlay = Color(0x99060D13)
    val BlueOverlay = Color(0xCC152129)
    val MenuSurface = Color(0xE6272B32)
    val PosterShadow = Color(0xFF07101C)
    val ArtworkBackdrop = Color(0xFF07131C)
    val BottomFade = Color(0xFF090A0D)
    val WatchedInk = Color(0xFF09271A)
    val OceanDeep = Color(0xFF0D253F)
    val WarmPosterShadow = Color(0xFF111522)
    val CoolPosterShadow = Color(0xFF121925)
    val VioletPosterShadow = Color(0xFF151824)
    val OceanMid = Color(0xFF152538)
    val VioletDeep = Color(0xFF171421)
    val WarmSurface = Color(0xFF171821)
    val GlassSurface = Color(0xFF171B21)
    val SeaSurface = Color(0xFF172331)
    val BlueSurface = Color(0xFF202637)
    val EpisodeSurface = Color(0xFF202831)
    val PosterBlue = Color(0xFF214C73)
    val PosterCyan = Color(0xFF275D7C)
    val EpisodeTeal = Color(0xFF287C77)
    val PosterMint = Color(0xFF38B99C)
    val PosterIndigo = Color(0xFF3A416E)
    val ProgressTrack = Color(0xFF4A4E57)
    val PosterPlum = Color(0xFF573B66)
    val NeutralControl = Color(0xFF59606B)
    val DisabledControl = Color(0xFF687083)
    val PosterGray = Color(0xFF6B7082)
    val PosterBrown = Color(0xFF705446)
    val WarningDark = Color(0xFF8A5B00)
    val PosterLilac = Color(0xFF996BCB)
    val LavenderText = Color(0xFFA8B1CF)
    val PosterRose = Color(0xFFD76870)
    val LightText = Color(0xFFE2E6EA)
    val BrightText = Color(0xFFE7EBEF)
    val NeutralText = Color(0xFFF2F2F4)
    val PosterOrange = Color(0xFFF49A47)
    val WarmText = Color(0xFFF4F4F5)
    val CoolText = Color(0xFFF4F7FB)
    val Negative = Color(0xFFFF5C5C)
    /** Filled destructive actions (remove, unmark, replace). */
    val DangerAction = Color(0xFFB3423F)
    /** Destructive text and icons on dark surfaces (Disconnect, error banner icon). */
    val DangerText = Color(0xFFF09595)
    /** Body text inside a danger notice. */
    val DangerSoftText = Color(0xFFF7C1C1)
    /** Tinted fill behind a danger notice. */
    val DangerContainer = Color(0x33E24B4A)
    /** The error banner's fill (used at partial alpha over glass). */
    val DangerBanner = Color(0xFF5A2028)
    /** Bright success for dots, checks and all-clear cards. */
    val SuccessBright = Color(0xFF5DCAA5)
    /** Problem tint on a status card. */
    val WarningSoft = Color(0xFFF0997B)
    /** Page scrim colour; fades use it at the alpha they need. */
    val Scrim = Color(0xFF080A0C)
    val WarningLight = Color(0xFFFFD37A)
}

val GlassBare = Color.White.copy(alpha = .045f)
val GlassFaint = Color.White.copy(alpha = .06f)
val GlassSubtle = Color.White.copy(alpha = .08f)
val GlassStrokeMedium = Color.White.copy(alpha = .24f)
val WhiteBright = Color.White.copy(alpha = .84f)
