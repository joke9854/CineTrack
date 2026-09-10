package com.cinetrack

import com.cinetrack.domain.EpisodeCard
import com.cinetrack.domain.averageEpisodeRuntime
import org.junit.Assert.*
import org.junit.Test

class EpisodeRuntimeTest {
    private fun episode(number: Int, runtime: Int?) = EpisodeCard(number, 42, 1, number, "", "", null, runtimeMinutes = runtime)

    @Test fun averagesKnownRegularEpisodesWithoutDoubleCounting() {
        val first = episode(1, 40)
        val episodes = listOf(first, first.copy(id = 99), episode(2, 51), episode(3, null), episode(4, 0),
            episode(5, 120).copy(season = 0), episode(6, 200).copy(showId = 7))
        assertEquals(46, averageEpisodeRuntime(42, episodes, 60))
    }

    @Test fun missingEpisodesUsePositiveFallbackAndNeverReportZero() {
        assertEquals(45, averageEpisodeRuntime(42, emptyList(), 45))
        assertNull(averageEpisodeRuntime(42, listOf(episode(1, null)), null))
        assertNull(averageEpisodeRuntime(42, listOf(episode(1, -1)), 0))
    }

    @Test fun duplicateMissingRuntimeDoesNotHideKnownRuntime() {
        assertEquals(50, averageEpisodeRuntime(42, listOf(episode(1, null), episode(1, 50)), 60))
    }
}
