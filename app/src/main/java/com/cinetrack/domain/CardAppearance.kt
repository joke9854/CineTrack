package com.cinetrack.domain

/** Stored keys remain stable across backups and future releases. */
data class CardAppearance(
    val heroLayout: String = "standard",
    val posterFormat: String = "classic",
    val posterSize: String = "standard",
    /** Cast and character photos: "circle" or "poster" (2:3). */
    val castPhotoShape: String = "circle",
    /** Titles under the posters in classic Discover and the Library grid. */
    val posterTitles: Boolean = true,
    /** Blurs stills and hides plots of episodes not watched yet. */
    val hideSpoilers: Boolean = false,
) {
    val heroAspectRatio: Float? get() = when (heroLayout) {
        "landscape" -> 16f / 9f
        "balanced" -> 4f / 3f
        else -> null
    }
    val posterAspectRatio: Float get() = when (posterFormat) {
        "compact" -> 3f / 4f
        "tall" -> 9f / 16f
        else -> 2f / 3f
    }
    val posterWidthDp: Int get() = when (posterSize) {
        "small" -> 100
        "large" -> 140
        else -> 118
    }
    companion object {
        fun normalizeHero(value: String) = value.takeIf { it in setOf("standard", "landscape", "balanced") } ?: "standard"
        fun normalizeFormat(value: String) = value.takeIf { it in setOf("classic", "compact", "tall") } ?: "classic"
        fun normalizeCastShape(value: String) = value.takeIf { it in setOf("circle", "poster") } ?: "circle"
        fun normalizeSize(value: String) = value.takeIf { it in setOf("small", "standard", "large") } ?: "standard"
        fun gridColumns(density: String) = when (density) { "compact" -> 4; "large" -> 2; else -> 3 }

        /** Phones keep [gridColumns]; tablets and unfolded foldables add columns instead of stretching posters. */
        fun gridColumns(density: String, widthDp: Int): Int {
            val base = gridColumns(density)
            return if (widthDp < WIDE_WIDTH_DP) base else (base * widthDp / PHONE_WIDTH_DP).coerceAtLeast(base)
        }

        const val WIDE_WIDTH_DP = 600
        private const val PHONE_WIDTH_DP = 400
    }
}
