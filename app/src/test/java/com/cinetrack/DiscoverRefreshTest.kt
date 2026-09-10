package com.cinetrack

import com.cinetrack.ui.runDiscoverRefresh
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class DiscoverRefreshTest {
    @Test fun completedRailsReturnWithoutWaitingForUnrelatedLibraryWork() = runTest {
        var committed = false
        runDiscoverRefresh { delay(100); committed = true }
        assertTrue(committed)
        assertEquals(100L, testScheduler.currentTime)
    }

    @Test fun stalledNetworkIsCancelledAt45Seconds() = runTest {
        var released = false
        try {
            runDiscoverRefresh {
                try { CompletableDeferred<Unit>().await() } finally { released = true }
            }
            fail("Expected timeout")
        } catch (_: TimeoutCancellationException) {
            assertEquals(45_000L, testScheduler.currentTime)
            assertTrue(released)
        }
    }

    @Test fun cancellationPropagatesToTheNetworkOperation() = runTest {
        val entered = CompletableDeferred<Unit>()
        var cancelled = false
        val refresh = async {
            runDiscoverRefresh {
                entered.complete(Unit)
                try { CompletableDeferred<Unit>().await() } finally { cancelled = true }
            }
        }
        entered.await()
        refresh.cancel()
        try { refresh.await(); fail("Expected cancellation") } catch (_: CancellationException) { }
        assertTrue(cancelled)
    }
}
