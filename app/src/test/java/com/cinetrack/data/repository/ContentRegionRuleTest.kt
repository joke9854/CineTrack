package com.cinetrack.data.repository

import com.cinetrack.data.remote.TmdbMediaDto
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentRegionRuleTest {
    private val regions = setOf("IT", "US", "GB")

    @Test fun originCountryDecidesWhenPresent() {
        assertTrue(TmdbMediaDto(id = 1, originCountries = listOf("US")).inContentRegions(regions))
        assertFalse(TmdbMediaDto(id = 2, originCountries = listOf("JP"), originalLanguage = "en").inContentRegions(regions))
    }

    @Test fun titleWithoutCountryIsJudgedByLanguage() {
        assertFalse(TmdbMediaDto(id = 1, originalLanguage = "ja").inContentRegions(regions))
        assertTrue(TmdbMediaDto(id = 2, originalLanguage = "it").inContentRegions(regions))
        assertTrue(TmdbMediaDto(id = 3, originalLanguage = "en").inContentRegions(regions))
    }

    @Test fun lowercaseRegionsAndNoRegionsBehave() {
        assertTrue(TmdbMediaDto(id = 1, originCountries = listOf("it")).inContentRegions(setOf("it")))
        assertTrue(TmdbMediaDto(id = 2, originalLanguage = "ja").inContentRegions(emptySet()))
    }
}
