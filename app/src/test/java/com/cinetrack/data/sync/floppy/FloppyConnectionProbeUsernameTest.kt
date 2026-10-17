package com.cinetrack.data.sync.floppy

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FloppyConnectionProbeUsernameTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun probeCarriesDisplayNameNextToOpaqueAccountId() {
        val probe = json.decodeFromString<FloppyConnectionProbeDto>(
            """{"authenticated":true,"account_id":"abc123","user":"abc123","username":"giuseppe","server_version":"1","api_extensions":{}}""",
        )
        assertEquals("abc123", probe.accountId)
        assertEquals("giuseppe", probe.username)
    }

    @Test fun olderServersWithoutUsernameStillDecode() {
        val probe = json.decodeFromString<FloppyConnectionProbeDto>(
            """{"authenticated":true,"account_id":"abc123","server_version":"1","api_extensions":{}}""",
        )
        assertNull(probe.username)
    }
}
