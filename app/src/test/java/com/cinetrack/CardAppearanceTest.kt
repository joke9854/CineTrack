package com.cinetrack

import com.cinetrack.domain.CardAppearance
import org.junit.Assert.*
import org.junit.Test

class CardAppearanceTest {
    @Test fun unknownBackupValuesFallBackToOriginalAppearance() {
        val appearance = CardAppearance(CardAppearance.normalizeHero("future"), CardAppearance.normalizeFormat(""), CardAppearance.normalizeSize("huge"))
        assertEquals(CardAppearance(), appearance)
        assertNull(appearance.heroAspectRatio)
        assertEquals(118, appearance.posterWidthDp)
    }

    @Test fun supportedChoicesSurviveNormalization() {
        assertEquals("landscape", CardAppearance.normalizeHero("landscape"))
        assertEquals("balanced", CardAppearance.normalizeHero("balanced"))
        assertEquals("tall", CardAppearance.normalizeFormat("tall"))
        assertEquals("compact", CardAppearance.normalizeFormat("compact"))
        assertEquals("small", CardAppearance.normalizeSize("small"))
        assertEquals("large", CardAppearance.normalizeSize("large"))
        assertEquals(16f / 9f, CardAppearance(heroLayout = "landscape").heroAspectRatio!!, .0001f)
    }
}
