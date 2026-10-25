package com.cinetrack.data.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamingArrivalsTest {
    @Test
    fun onlyNewServicesCount() {
        val previous = setOf("MOVIE:1:Netflix", "TV:2:Disney Plus")
        val current = mapOf(
            "MOVIE:1" to setOf("Netflix", "Prime Video"),
            "TV:2" to setOf("Disney Plus"),
            "MOVIE:3" to setOf("Netflix"),
            "MOVIE:4" to emptySet(),
        )
        assertEquals(
            mapOf("MOVIE:1" to setOf("Prime Video"), "MOVIE:3" to setOf("Netflix")),
            streamingArrivals(previous, current),
        )
    }

    @Test
    fun aServiceThatLeftAndCameBackIsNewAgain() {
        // The stored set only keeps what is there now, so leaving drops the entry.
        assertEquals(mapOf("MOVIE:1" to setOf("Netflix")), streamingArrivals(emptySet(), mapOf("MOVIE:1" to setOf("Netflix"))))
    }
}
