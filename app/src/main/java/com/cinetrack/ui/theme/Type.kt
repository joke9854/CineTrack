package com.cinetrack.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.cinetrack.R

/** Manrope (SIL OFL 1.1, docs/licenses/Manrope-OFL.txt), one variable file for every weight. */
@OptIn(ExperimentalTextApi::class)
/** Letter spacing for uppercase eyebrow labels (section names, card overlines). */
val EyebrowLetterSpacing = 1.6.sp

val Manrope = FontFamily(
    listOf(400, 500, 600, 700, 800).map { weight ->
        Font(R.font.manrope, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))
    },
)

private val Base = Typography()

private fun TextStyle.manrope() = copy(fontFamily = Manrope)

// Seven sizes taken from the existing UI. Weight, line height and tracking
// remain the responsibility of the existing semantic components.
val CineTrackTypography = Typography(
    displayLarge = Base.displayLarge.manrope(),
    displayMedium = Base.displayMedium.manrope(),
    displaySmall = TextStyle(fontSize = 29.sp, letterSpacing = (-.6).sp).manrope(),
    headlineLarge = Base.headlineLarge.manrope(),
    headlineMedium = Base.headlineMedium.manrope(),
    headlineSmall = Base.headlineSmall.copy(letterSpacing = (-.4).sp).manrope(),
    titleLarge = TextStyle(fontSize = 22.sp, letterSpacing = (-.3).sp).manrope(),
    titleMedium = TextStyle(fontSize = 18.sp, letterSpacing = (-.2).sp).manrope(),
    titleSmall = TextStyle(fontSize = 16.sp).manrope(),
    bodyLarge = Base.bodyLarge.manrope(),
    bodyMedium = TextStyle(fontSize = 14.sp).manrope(),
    bodySmall = TextStyle(fontSize = 13.sp).manrope(),
    labelLarge = Base.labelLarge.manrope(),
    labelMedium = Base.labelMedium.manrope(),
    labelSmall = TextStyle(fontSize = 10.sp).manrope(),
)
