package com.cinetrack.data.sync.floppy

import com.cinetrack.data.remote.SharedHttp
import com.cinetrack.data.sync.floppy.network.FloppyUrlNormalizer
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class FloppyPrivateNetworkTest {
    private fun address(literal: String) = InetAddress.getByName(literal)

    @Test fun `resolved addresses must be private for plain HTTP`() {
        listOf("10.0.0.5", "172.20.1.1", "192.168.1.10", "169.254.3.4", "127.0.0.1", "100.101.102.103", "::1", "fd7a:115c:a1e0::1", "fe80::1")
            .forEach { assertTrue(it, FloppyUrlNormalizer.isPrivateAddress(address(it))) }
        listOf("8.8.8.8", "100.128.0.1", "172.32.0.1", "2001:4860:4860::8888", "::ffff:8.8.8.8")
            .forEach { assertFalse(it, FloppyUrlNormalizer.isPrivateAddress(address(it))) }
    }

    @Test fun `tailscale addresses count as a private host`() {
        assertTrue(FloppyUrlNormalizer.isPrivateHost("100.64.0.1"))
        assertFalse(FloppyUrlNormalizer.isPrivateHost("100.128.0.1"))
    }

    @Test fun `public plain http is upgraded and private http is left alone`() {
        assertEquals("https://image.tmdb.org/t/p/w500/a.jpg", SharedHttp.secureUrl("http://image.tmdb.org/t/p/w500/a.jpg".toHttpUrl()).toString())
        assertEquals("https://example.com:8080/x", SharedHttp.secureUrl("http://example.com:8080/x".toHttpUrl()).toString())
        assertEquals("http://192.168.1.10:8000/media/a.jpg", SharedHttp.secureUrl("http://192.168.1.10:8000/media/a.jpg".toHttpUrl()).toString())
        assertEquals("https://simkl.in/a.jpg", SharedHttp.secureUrl("https://simkl.in/a.jpg".toHttpUrl()).toString())
    }
}
