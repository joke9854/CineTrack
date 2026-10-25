package com.cinetrack.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FanartLogoTest {
    private val logos = listOf(
        FanartImage("http://assets.fanart.tv/en-low.png", "en", "1"),
        FanartImage("https://assets.fanart.tv/en-top.png", "en", "9"),
        FanartImage("https://assets.fanart.tv/it.png", "it", "2"),
        FanartImage("https://assets.fanart.tv/none.png", "00", "20"),
    )

    @Test
    fun appLanguageWinsThenEnglishByLikes() {
        assertEquals("https://assets.fanart.tv/it.png", pickLogo(logos, "it"))
        assertEquals("https://assets.fanart.tv/en-top.png", pickLogo(logos, "de"))
    }

    @Test
    fun plainHttpIsUpgradedAndEmptyGivesNothing() {
        assertEquals("https://assets.fanart.tv/en-low.png", pickLogo(listOf(logos.first()), "en"))
        assertNull(pickLogo(emptyList(), "en"))
    }
}
