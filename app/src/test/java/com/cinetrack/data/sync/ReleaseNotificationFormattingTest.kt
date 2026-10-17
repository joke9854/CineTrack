package com.cinetrack.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReleaseNotificationFormattingTest {
    @Test
    fun extractsOnlyTheEpisodeTitleFromLegacyLabel() {
        assertEquals("The War on Alcohol", extractEpisodeTitle("S18 E06 · The War on Alcohol"))
        assertNull(extractEpisodeTitle("S18 E06"))
        assertEquals("Part 1: The Return!", extractEpisodeTitle("S01 E02 · Part 1: The Return!"))
    }
}

