package com.cinetrack

import com.cinetrack.domain.AppUiState
import com.cinetrack.domain.MediaCard
import com.cinetrack.domain.MediaType
import com.cinetrack.domain.PersonCard
import com.cinetrack.domain.RailIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DomainModelsTest {
    @Test
    fun actorAgeUsesFullBirthday() {
        val person = PersonCard(id = 1, name = "Test", role = "Actor", birthday = "2000-09-15")
        assertEquals(25, person.age(LocalDate.of(2026, 8, 24)))
        assertEquals(26, person.age(LocalDate.of(2026, 9, 15)))
    }

    @Test
    fun discoverKeepsTvAndMoviesInSeparateRails() {
        val state = AppUiState(
            rails = mapOf(
                RailIds.TRENDING_TV to listOf(MediaCard(1, MediaType.TV, "TV")),
                RailIds.TRENDING_MOVIES to listOf(MediaCard(2, MediaType.MOVIE, "Movie")),
            ),
        )
        assertTrue(state.rails.getValue(RailIds.TRENDING_TV).all { it.type == MediaType.TV })
        assertTrue(state.rails.getValue(RailIds.TRENDING_MOVIES).all { it.type == MediaType.MOVIE })
    }

    @Test
    fun emptyStateContainsNoMockupTitles() {
        assertTrue(AppUiState().allMedia.isEmpty())
    }
}
