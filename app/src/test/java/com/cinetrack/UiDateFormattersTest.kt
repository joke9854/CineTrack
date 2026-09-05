package com.cinetrack

import com.cinetrack.ui.UiDateFormatters
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class UiDateFormattersTest {
    @Test fun reusesFormattersAndFollowsLanguageChanges() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            val english = UiDateFormatters.current
            assertSame(english, UiDateFormatters.current)
            val date = LocalDate.of(2026, 1, 5)
            assertEquals("05/01/2026", date.format(english.date))
            assertEquals("05\nJan", date.format(english.dayMonth))
            Locale.setDefault(Locale.ITALY)
            val italian = UiDateFormatters.current
            assertNotSame(english, italian)
            assertEquals("gen", date.format(italian.month))
            assertEquals("05/01/2026", date.format(italian.date))
        } finally {
            Locale.setDefault(previous)
        }
    }
}
