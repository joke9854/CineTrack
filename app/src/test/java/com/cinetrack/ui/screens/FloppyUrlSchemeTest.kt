package com.cinetrack.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class FloppyUrlSchemeTest {
    @Test fun bareAddressDefaultsToHttps() {
        assertEquals("https://floppy.example.net", withDefaultFloppyScheme(" floppy.example.net ", allowLocalHttp = false))
    }

    @Test fun bareAddressUsesHttpWhenLocalHttpIsOn() {
        assertEquals("http://192.168.1.10:8000", withDefaultFloppyScheme("192.168.1.10:8000", allowLocalHttp = true))
    }

    @Test fun typedSchemeIsKept() {
        assertEquals("http://floppy.lan", withDefaultFloppyScheme("http://floppy.lan", allowLocalHttp = false))
        assertEquals("https://floppy.lan", withDefaultFloppyScheme("https://floppy.lan", allowLocalHttp = true))
        assertEquals("", withDefaultFloppyScheme("  ", allowLocalHttp = false))
    }
}
