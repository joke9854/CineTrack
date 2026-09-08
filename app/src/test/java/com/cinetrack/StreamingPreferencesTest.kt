package com.cinetrack

import com.cinetrack.domain.*
import org.junit.Assert.*
import org.junit.Test

class StreamingPreferencesTest {
    @Test fun explicitStreamingCountryOverridesContentOrigins() {
        assertEquals("US", resolveProviderRegion("US", setOf("IT"), "GB", "DE"))
        assertEquals("IT", resolveProviderRegion("system", setOf("IT"), "GB", "DE"))
        assertEquals("GB", resolveProviderRegion("system", emptySet(), "GB", "DE"))
        assertEquals("US", resolveProviderRegion("system", emptySet(), "system", ""))
    }

    @Test fun hidingSubscriptionsDoesNotSuppressNotificationAvailability() {
        val media = MediaCard(1, MediaType.MOVIE, "Film", subscriptionProviders = listOf("Stream"),
            rentProviders = listOf("Rent"), visibleProviderTypes = setOf("rent"))
        assertEquals(mapOf("rent" to listOf("Rent")), visibleProviderOffers(media))
        assertEquals(listOf("Stream"), media.subscriptionProviders)
    }

    @Test fun allTypesCanBeHiddenAndRentalIsNeverLabeledSubscription() {
        val rental = MediaCard(1, MediaType.MOVIE, "Film", rentProviders = listOf("Rent"))
        assertTrue(visibleProviderOffers(rental)["flatrate"].orEmpty().isEmpty())
        assertTrue(visibleProviderOffers(rental.copy(visibleProviderTypes = emptySet())).isEmpty())
    }
}
