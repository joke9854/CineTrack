package com.cinetrack.data.sync

import org.junit.Assert.assertTrue
import org.junit.Test

class MutationGenerationTest {
    @Test
    fun persistedFutureGenerationWinsClockRollback() {
        val next = MutationGeneration.next(previous = 5_000L, now = 4_000L)
        assertTrue(next >= 5_001L)
    }
}
