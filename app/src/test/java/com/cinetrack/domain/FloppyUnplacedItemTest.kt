package com.cinetrack.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class FloppyUnplacedItemTest {
    @Test
    fun titledSpecialSearchesFloppyMovies() {
        val special = FloppyUnplacedItem(8592, "Parks and Recreation", 0, 12, "A Parks and Recreation Special")
        assertEquals(
            "https://floppy.example/search?media_type=movie&q=A+Parks+and+Recreation+Special",
            special.floppySearchUrl("https://floppy.example/"),
        )
        assertEquals("S00E12", special.label)
    }

    @Test
    fun regularEpisodeSearchesTheShow() {
        val episode = FloppyUnplacedItem(37854, "One Piece", 1, 1100, null)
        assertEquals("https://floppy.example/search?media_type=tv&q=One+Piece", episode.floppySearchUrl("https://floppy.example"))
    }
}
