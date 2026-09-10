package com.cinetrack.data.remote

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class TmdbSeasonDetailsTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun seasonResponseKeepsItsOwnMetadataAndAggregateCast() {
        val dto = json.decodeFromString<TmdbSeasonDto>("""
            {"id":501,"name":"Season 2","season_number":2,"overview":"A new chapter.",
             "air_date":"2024-04-12","poster_path":"/season.jpg","vote_average":8.3,
             "episodes":[{"id":701,"name":"Return","season_number":2,"episode_number":1,"runtime":52}],
             "aggregate_credits":{"cast":[{"id":80,"name":"Season actor","roles":[{"character":"Detective","episode_count":6}]}]}}
        """.trimIndent())
        assertEquals("A new chapter.", dto.overview)
        assertEquals("2024-04-12", dto.airDate)
        assertEquals(8.3, dto.voteAverage!!, 0.001)
        assertEquals(52, dto.episodes.single().runtime)
        assertEquals("Detective", dto.aggregateCredits!!.cast.single().roles.single().character)
    }

    @Test fun existingEpisodeOnlyResponsesStillDecodeWithoutDetails() {
        val dto = json.decodeFromString<TmdbSeasonDto>("""{"id":501,"name":"Season 2","episodes":[]}""")
        assertEquals("", dto.overview)
        assertNull(dto.voteAverage)
        assertNull(dto.aggregateCredits)
        assertTrue(dto.episodes.isEmpty())
    }

    @Test fun heroTaglineIsSeparateFromOverviewAndOptionalInListResponses() {
        val details = json.decodeFromString<TmdbMediaDto>("""{"id":42,"tagline":"Every story starts somewhere.","overview":"A much longer plot."}""")
        assertEquals("Every story starts somewhere.", details.tagline)
        assertNotEquals(details.overview, details.tagline)
        assertNull(json.decodeFromString<TmdbMediaDto>("""{"id":42}""").tagline)
    }
}
