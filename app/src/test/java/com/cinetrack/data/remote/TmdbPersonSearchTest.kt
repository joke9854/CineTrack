package com.cinetrack.data.remote

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class TmdbPersonSearchTest {
    @Test
    fun multiSearchRetainsPersonProfileAndDepartment() {
        val page = Json.decodeFromString<TmdbPage>(
            """{"page":1,"total_pages":1,"results":[{"id":42,"media_type":"person","name":"Example Actor","profile_path":"/profile.jpg","known_for_department":"Acting"}]}""",
        )

        val person = page.results.single()
        assertEquals("person", person.mediaType)
        assertEquals("Example Actor", person.name)
        assertEquals("/profile.jpg", person.profilePath)
        assertEquals("Acting", person.knownForDepartment)
    }
}
