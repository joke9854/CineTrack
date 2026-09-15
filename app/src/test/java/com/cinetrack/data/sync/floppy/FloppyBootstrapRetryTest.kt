package com.cinetrack.data.sync.floppy

import com.cinetrack.data.sync.TrackingSyncError
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloppyBootstrapRetryTest {
    @Test
    fun dnsFailureIsRetryable() {
        assertTrue(isFloppyBootstrapRetryable(TrackingSyncError.DnsFailure(IllegalStateException("dns"))))
    }

    @Test
    fun authenticationFailureIsTerminal() {
        assertFalse(isFloppyBootstrapRetryable(TrackingSyncError.AuthenticationRequired(com.cinetrack.data.sync.TrackingProviderId.FLOPPY)))
    }
}

