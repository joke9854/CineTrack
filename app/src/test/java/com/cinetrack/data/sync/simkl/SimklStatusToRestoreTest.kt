package com.cinetrack.data.sync.simkl

import com.cinetrack.data.sync.MovieHistoryMutationContext
import com.cinetrack.domain.LibraryStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SimklStatusToRestoreTest {
    @Test
    fun `the status in a v2 movie unwatch payload is put back`() {
        val payload = MovieHistoryMutationContext(LibraryStatus.PLAN_TO_WATCH, previousWatched = false, previousWatchedAt = null).encode()

        assertEquals(LibraryStatus.PLAN_TO_WATCH, simklStatusToRestore(payload))
    }

    @Test
    fun `a legacy plain status payload is still put back`() {
        assertEquals(LibraryStatus.PAUSED, simklStatusToRestore("PAUSED"))
    }

    @Test
    fun `a removal or a missing status restores nothing`() {
        assertNull(simklStatusToRestore(MovieHistoryMutationContext(LibraryStatus.NONE, null, null).encode()))
        assertNull(simklStatusToRestore(MovieHistoryMutationContext(null, true, null).encode()))
        assertNull(simklStatusToRestore(null))
    }
}
