package com.cinetrack.data.sync.floppy

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FloppyConsumptionResolverTest {
    private val resolver = FloppyConsumptionResolver()
    private val first = Instant.parse("2025-01-01T00:00:00Z")
    private val second = Instant.parse("2025-02-01T00:00:00Z")

    @Test fun planningOnlyResolvesActiveLibraryConsumption() {
        val value = FloppyConsumption(1, status = 0, created = first.toString())
        val result = resolver.resolve(listOf(value))
        assertSame(value, result.active)
        assertTrue(result.completed.isEmpty())
    }

    @Test fun watchingPausedAndDroppedRemainActiveLibraryStates() {
        listOf(1, 2, 4).forEach { status ->
            val value = FloppyConsumption(status, status = status, created = first.toString())
            assertEquals(value, resolver.resolve(listOf(value)).active)
        }
    }

    @Test fun completedOnlyIsHistoryAndLatestCompletedWins() {
        val old = FloppyConsumption(1, status = 3, endDate = first.toString())
        val latest = FloppyConsumption(2, status = 3, endDate = second.toString())
        val result = resolver.resolve(listOf(old, latest))
        assertNull(result.active)
        assertEquals(listOf(latest, old), result.completed)
        assertEquals(latest, result.latestCompleted)
        assertEquals(latest, resolver.findExactWatch(listOf(old, latest), second))
    }

    @Test fun activeAndHistoricalCompletedStayIndependent() {
        val active = FloppyConsumption(1, status = 1, progressedAt = second.toString())
        val completed = FloppyConsumption(2, status = 3, endDate = first.toString())
        val result = resolver.resolve(listOf(active, completed))
        assertEquals(active, result.active)
        assertEquals(listOf(completed), result.completed)
    }
}
