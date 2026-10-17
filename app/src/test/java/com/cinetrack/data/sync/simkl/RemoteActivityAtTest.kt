package com.cinetrack.data.sync.simkl

import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteActivityAtTest {
    private val commit = 9_000L

    @Test
    fun `a watch time from Simkl wins`() = assertEquals(100L, remoteActivityAt(remoteUpdatedAt = 200L, watchedAt = 100L, previousUpdatedAt = 300L, committedAt = commit))

    @Test
    fun `otherwise the title's Simkl activity is kept`() = assertEquals(200L, remoteActivityAt(200L, null, 300L, commit))

    @Test
    fun `without Simkl times the device's time is kept`() = assertEquals(300L, remoteActivityAt(null, null, 300L, commit))

    @Test
    fun `the sync time is used only for a title never seen`() = assertEquals(commit, remoteActivityAt(null, null, null, commit))

    @Test
    fun `changes applied in one sync no longer tie at the commit time`() {
        val applied = listOf(1_000L, 2_000L, 3_000L).map { remoteActivityAt(it, null, null, commit) }
        assertEquals(3, applied.distinct().size)
    }
}
