package com.cinetrack.data.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuietHoursTest {
    @Test fun overnightWindowWrapsAcrossMidnight() {
        assertTrue(isQuietHour(23, 23, 8))
        assertTrue(isQuietHour(4, 23, 8))
        assertFalse(isQuietHour(8, 23, 8))
        assertFalse(isQuietHour(18, 23, 8))
    }

    @Test fun daytimeWindowUsesHalfOpenRange() {
        assertTrue(isQuietHour(10, 9, 17))
        assertFalse(isQuietHour(17, 9, 17))
    }
}
