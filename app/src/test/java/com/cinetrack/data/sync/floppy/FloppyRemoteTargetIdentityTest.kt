package com.cinetrack.data.sync.floppy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloppyRemoteTargetIdentityTest {
    private val base = FloppyConnectionSettings(
        baseUrl = "https://floppy.example/",
        serverIdentity = "https://floppy.example/",
        accountIdentity = "alice",
        connectionId = "instance-a",
    )

    @Test
    fun sameServerAccountAndKeyPreservesTarget() {
        assertTrue(sameFloppyRemoteTarget(base, base.copy(connectionId = "candidate"), "key", "key"))
    }

    @Test
    fun normalizedUrlVariantsPreserveTargetIdentity() {
        val candidate = base.copy(baseUrl = "https://FLOPPY.example", serverIdentity = base.serverIdentity)
        assertTrue(sameFloppyRemoteTarget(base, candidate, "key", "key"))
    }

    @Test
    fun transportOnlyHttpPolicyChangePreservesTarget() {
        assertTrue(sameFloppyRemoteTarget(base, base.copy(allowInsecureLocalHttp = true), "key", "key"))
    }

    @Test
    fun accountProofAllowsApiKeyRotation() {
        assertTrue(sameFloppyRemoteTarget(base, base.copy(connectionId = "candidate"), "old", "new"))
    }

    @Test
    fun unknownAccountWithRotatedKeyIsConservative() {
        val unknown = base.copy(accountIdentity = null)
        assertFalse(sameFloppyRemoteTarget(unknown, unknown.copy(connectionId = "candidate"), "old", "new"))
    }

    @Test
    fun differentAccountIsDifferentTarget() {
        assertFalse(sameFloppyRemoteTarget(base, base.copy(accountIdentity = "bob"), "key", "key"))
    }

    @Test
    fun differentServerIsDifferentTarget() {
        assertFalse(sameFloppyRemoteTarget(base, base.copy(serverIdentity = "https://other.example/"), "key", "key"))
    }
}

