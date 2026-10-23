package com.cinetrack.data.sync.simkl

import com.cinetrack.domain.LibraryStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The values the three former copies (host, engine, provider) produced. */
class SimklStatusMappingTest {
    private val lists = mapOf(
        "watching" to LibraryStatus.WATCHING,
        "hold" to LibraryStatus.PAUSED,
        "plantowatch" to LibraryStatus.PLAN_TO_WATCH,
        "completed" to LibraryStatus.COMPLETED,
        "dropped" to LibraryStatus.DROPPED,
    )

    @Test
    fun simklListsMapBothWays() {
        lists.forEach { (simkl, status) ->
            assertEquals(status, simkl.fromSimklStatus())
            assertEquals(simkl, status.name.toSimklStatus())
            assertEquals(simkl, SimklStatusMapping.toSimkl(status))
        }
    }

    @Test
    fun unknownValuesStayNoneOrNull() {
        assertEquals(LibraryStatus.NONE, "".fromSimklStatus())
        assertEquals(LibraryStatus.NONE, "Watching".fromSimklStatus())
        assertNull(LibraryStatus.NONE.name.toSimklStatus())
        assertNull("watching".toSimklStatus())
        assertNull(SimklStatusMapping.toSimkl(LibraryStatus.NONE))
        assertEquals(LibraryStatus.NONE, "BOGUS".fromCineTrackStatus())
        assertEquals(LibraryStatus.PAUSED, "PAUSED".fromCineTrackStatus())
    }
}
