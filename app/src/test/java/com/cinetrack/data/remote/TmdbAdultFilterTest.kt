package com.cinetrack.data.remote

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class TmdbAdultFilterTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun pageResultsDropAdultTitles() {
        val page = json.decodeFromString<TmdbPage>(
            """{"page":1,"results":[{"id":1,"title":"Film","adult":false},{"id":2,"title":"Adult","adult":true},{"id":3,"name":"Show"}],"total_pages":1}""",
        )
        assertEquals(listOf(1, 3), page.results.map { it.id })
    }

    @Test fun personCreditsDropAdultTitles() {
        val credits = json.decodeFromString<TmdbCombinedCreditsDto>(
            """{"cast":[{"id":1,"title":"Film"},{"id":2,"title":"Adult","adult":true}]}""",
        )
        assertEquals(listOf(1), credits.cast.map { it.id })
    }

    @Test fun japaneseAdultVideosWithoutTheFlagAreDropped() {
        val page = json.decodeFromString<TmdbPage>(
            """{"results":[
                {"id":1469906,"title":"START-205 A man films his girlfriend","original_title":"START-205 これは","original_language":"ja","adult":false,"genre_ids":[18]},
                {"id":2,"title":"FC2-PPV-1234567 something","original_language":"ja"},
                {"id":3,"title":"Spirited Away","original_title":"千と千尋の神隠し","original_language":"ja"},
                {"id":4,"title":"THX-1138","original_language":"en"},
                {"id":5,"name":"Attack on Titan","original_name":"進撃の巨人","original_language":"ja"}
            ]}""",
        )
        assertEquals(listOf(3, 4, 5), page.results.map { it.id })
    }

    @Test fun turningTheSettingOffKeepsEveryTitle() {
        TmdbContentFilter.hideAdult = false
        try {
            val page = json.decodeFromString<TmdbPage>(
                """{"results":[{"id":1,"title":"START-205","original_language":"ja"},{"id":2,"title":"Adult","adult":true}]}""",
            )
            assertEquals(listOf(1, 2), page.results.map { it.id })
        } finally {
            TmdbContentFilter.hideAdult = true
        }
    }
}
