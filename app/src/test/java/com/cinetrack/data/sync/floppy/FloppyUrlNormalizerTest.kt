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
}
