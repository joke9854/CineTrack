package com.cinetrack.data.sync.floppy

import com.cinetrack.data.sync.floppy.network.FloppyUrlNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FloppyUrlNormalizerTest {
    @Test fun trailingSlashIsCanonical() {
        assertEquals("https://floppy.example.com/", FloppyUrlNormalizer.normalize("  https://floppy.example.com/ ").baseUrl)
    }

    @Test fun reverseProxyPrefixIsPreserved() {
        assertEquals("https://example.com/floppy/", FloppyUrlNormalizer.normalize("https://example.com/floppy").baseUrl)
    }

    @Test fun explicitSchemeIsRequired() {
        assertThrows(IllegalArgumentException::class.java) { FloppyUrlNormalizer.normalize("floppy.example.com") }
    }

    @Test fun queryAndCredentialsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { FloppyUrlNormalizer.normalize("https://u:p@example.com/floppy") }
        assertThrows(IllegalArgumentException::class.java) { FloppyUrlNormalizer.normalize("https://example.com/floppy?x=1") }
    }

    @Test fun publicHttpIsRejectedButExplicitPrivateHttpIsAllowed() {
        assertThrows(IllegalArgumentException::class.java) {
            FloppyUrlNormalizer.normalize("http://floppy.example.com")
        }
        assertEquals(
            "http://192.168.1.20/floppy/",
            FloppyUrlNormalizer.normalize("http://192.168.1.20/floppy", allowInsecureLocalHttp = true).baseUrl,
        )
    }

    @Test fun hostnamePrefixesDoNotImplyPrivateIpv6() {
        assertThrows(IllegalArgumentException::class.java) {
            FloppyUrlNormalizer.normalize("http://fc-example.com", allowInsecureLocalHttp = true)
        }
        assertThrows(IllegalArgumentException::class.java) {
            FloppyUrlNormalizer.normalize("http://fd-service.example", allowInsecureLocalHttp = true)
        }
    }

    @Test fun publicAndPrivateIpv4FollowTheExplicitOptIn() {
        assertThrows(IllegalArgumentException::class.java) {
            FloppyUrlNormalizer.normalize("http://8.8.8.8", allowInsecureLocalHttp = true)
        }
        assertThrows(IllegalArgumentException::class.java) {
            FloppyUrlNormalizer.normalize("http://192.168.1.20")
        }
        assertEquals(
            "http://192.168.1.20/",
            FloppyUrlNormalizer.normalize("http://192.168.1.20", allowInsecureLocalHttp = true).baseUrl,
        )
    }

    @Test fun ulaIpv6IsAllowedOnlyWithExplicitOptIn() {
        assertThrows(IllegalArgumentException::class.java) {
            FloppyUrlNormalizer.normalize("http://[fd12::20]")
        }
        assertEquals(
            "http://[fd12::20]/",
            FloppyUrlNormalizer.normalize("http://[fd12::20]", allowInsecureLocalHttp = true).baseUrl,
        )
    }

    @Test fun publicHttpsHostnameRemainsAllowed() {
        assertEquals("https://fc-example.com/", FloppyUrlNormalizer.normalize("https://fc-example.com").baseUrl)
    }
}

