package com.cinetrack.data.remote

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class TmdbProviderOffersTest {
    @Test fun freeAndAdSupportedOffersKeepTheirCategory() {
        val country = Json.decodeFromString<TmdbProviderCountryDto>("""{
            "free":[{"provider_id":1,"provider_name":"Free service"}],
            "ads":[{"provider_id":2,"provider_name":"Ad service"}],
            "rent":[{"provider_id":3,"provider_name":"Rental service"}]
        }""")
        assertEquals("Free service", country.free.single().name)
        assertEquals("Ad service", country.ads.single().name)
        assertEquals("Rental service", country.rent.single().name)
        assertTrue(country.flatrate.isEmpty())
        assertTrue(country.buy.isEmpty())
    }
}
