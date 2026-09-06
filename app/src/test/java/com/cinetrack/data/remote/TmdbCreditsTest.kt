package com.cinetrack.data.remote

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class TmdbCreditsTest {
    @Test fun aggregateCreditsKeepActorsBeyondThePreviewAndMultipleRoles() {
        val cast = (1..40).joinToString(",") { id ->
            """{"id":$id,"name":"Actor $id","roles":[{"character":"Season one","episode_count":2},{"character":"Season two","episode_count":4}]}"""
        }
        val dto = Json { ignoreUnknownKeys = true }.decodeFromString<TmdbMediaDto>(
            """{"id":42,"aggregate_credits":{"cast":[$cast],"crew":[{"id":100,"name":"Crew","jobs":[{"job":"Director"}]}]}}""",
        )
        assertEquals(40, dto.aggregateCredits!!.cast.size)
        assertEquals("Season two", dto.aggregateCredits.cast.last().roles.last().character)
        assertEquals("Director", dto.aggregateCredits.crew.single().jobs.single().job)
    }
}
