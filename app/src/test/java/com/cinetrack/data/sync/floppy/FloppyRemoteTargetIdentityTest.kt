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
    fun legacyUsernameToProbeAccountWithSameKeyPreservesTarget() {
        val migrated = base.copy(accountIdentity = FLOPPY_PROBE_ACCOUNT_PREFIX + "opaque", connectionId = "candidate")
        assertTrue(sameFloppyRemoteTarget(base, migrated, "key", "key"))
    }

    @Test
    fun legacyUsernameToProbeAccountWithRotatedKeyIsConservative() {
        val migrated = base.copy(accountIdentity = FLOPPY_PROBE_ACCOUNT_PREFIX + "opaque", connectionId = "candidate")
        assertFalse(sameFloppyRemoteTarget(base, migrated, "old", "new"))
    }

    @Test
    fun differentProbeAccountsAreDifferentTargets() {
        val a = base.copy(accountIdentity = FLOPPY_PROBE_ACCOUNT_PREFIX + "a")
        assertFalse(sameFloppyRemoteTarget(a, a.copy(accountIdentity = FLOPPY_PROBE_ACCOUNT_PREFIX + "b"), "key", "key"))
    }

    @Test
    fun sameProbeAccountAtNewAddressNeedsTheUsersConfirmation() {
        val a = base.copy(accountIdentity = FLOPPY_PROBE_ACCOUNT_PREFIX + "a")
        val moved = a.copy(baseUrl = "https://floppy.new.example/", serverIdentity = "https://floppy.new.example/", connectionId = "candidate")
        assertTrue(isFloppyAddressMove(a, moved))
        assertFalse(sameFloppyRemoteTarget(a, moved, "oauth:old", "oauth:new"))
        assertTrue(sameFloppyRemoteTarget(a, moved, "oauth:old", "oauth:new", acceptAddressMove = true))
    }

    @Test
    fun differentProbeAccountAtNewAddressIsDifferentTargetEvenWhenConfirmed() {
        val a = base.copy(accountIdentity = FLOPPY_PROBE_ACCOUNT_PREFIX + "a")
        val other = a.copy(serverIdentity = "https://floppy.new.example/", accountIdentity = FLOPPY_PROBE_ACCOUNT_PREFIX + "b")
        assertFalse(isFloppyAddressMove(a, other))
        assertFalse(sameFloppyRemoteTarget(a, other, "key", "key", acceptAddressMove = true))
    }

    @Test
    fun sameAddressIsNotAMove() {
        val a = base.copy(accountIdentity = FLOPPY_PROBE_ACCOUNT_PREFIX + "a")
        assertFalse(isFloppyAddressMove(a, a.copy(connectionId = "candidate")))
    }

    @Test
    fun sameUsernameAtNewAddressIsDifferentTarget() {
        // A username is not unique across servers.
        assertFalse(sameFloppyRemoteTarget(base, base.copy(serverIdentity = "https://other.example/"), "key", "key"))
    }

    @Test
    fun differentServerIsDifferentTarget() {
        assertFalse(sameFloppyRemoteTarget(base, base.copy(serverIdentity = "https://other.example/"), "key", "key"))
    }
}

