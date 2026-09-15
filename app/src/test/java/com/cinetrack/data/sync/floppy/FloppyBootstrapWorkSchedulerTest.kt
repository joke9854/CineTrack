package com.cinetrack.data.sync.floppy

import org.junit.Assert.assertEquals
import org.junit.Test

class FloppyBootstrapWorkSchedulerTest {
    @Test
    fun uniqueWorkIsScopedToProviderInstance() {
        assertEquals("floppy-bootstrap:instance-a", FloppyBootstrapWorkScheduler.uniqueWorkName("instance-a"))
        assertEquals("floppy-bootstrap:instance-b", FloppyBootstrapWorkScheduler.uniqueWorkName("instance-b"))
    }
}

