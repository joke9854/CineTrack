package com.cinetrack.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TvdbCharactersTest {
    @Test
    fun onlyOnScreenCharactersAreKeptInTvdbOrderWithAbsolutePictures() {
        val result = tvdbCharacterImages(
            listOf(
                TvdbCharacterDto(name = "Director", personName = "Jane Roe", type = 1, sort = 0),
                TvdbCharacterDto(name = "Walter White", personName = "Bryan Cranston", image = "/banners/person/1.jpg", type = 3, sort = 2),
                TvdbCharacterDto(name = "Jesse Pinkman", personName = "Aaron Paul", image = "http://artworks.thetvdb.com/p/2.jpg", type = 3, sort = 1),
                TvdbCharacterDto(name = "Narrator", personName = "Guest", peopleType = "Guest Star"),
            ),
        )

        assertEquals(listOf("Aaron Paul", "Bryan Cranston", "Guest"), result.map { it.actor })
        assertEquals("https://artworks.thetvdb.com/p/2.jpg", result[0].imageUrl)
        assertEquals("https://artworks.thetvdb.com/banners/person/1.jpg", result[1].imageUrl)
        assertNull(result[2].imageUrl)
    }

    @Test
    fun tvmazePremiumKeyGoesInABasicAuthHeader() {
        // TVmaze's user API: the username as user, the API key as password.
        assertEquals("Basic " + java.util.Base64.getEncoder().encodeToString("joe:secret".toByteArray()), tvmazeBasicAuth(" joe ", "secret "))
    }
}
