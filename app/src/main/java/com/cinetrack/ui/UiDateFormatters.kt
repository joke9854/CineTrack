package com.cinetrack.ui

import java.time.format.DateTimeFormatter
import java.util.Locale

/** Immutable formatters; replace the small bundle when the app language changes. */
internal object UiDateFormatters {
    data class Formats(val locale: Locale) {
        val dayMonth = DateTimeFormatter.ofPattern("dd\nMMM", locale)
        val month = DateTimeFormatter.ofPattern("MMM", locale)
        val narrowWeekday = DateTimeFormatter.ofPattern("EEEEE", locale)
        val time = DateTimeFormatter.ofPattern("HH:mm", locale)
        val date = DateTimeFormatter.ofPattern("dd/MM/yyyy", locale)
        val weekdayDate = DateTimeFormatter.ofPattern("EEE\ndd MMM", locale)
    }
    @Volatile private var cached = Formats(Locale.getDefault())
    val current: Formats
        get() {
            val locale = Locale.getDefault()
            return cached.takeIf { it.locale == locale } ?: Formats(locale).also { cached = it }
        }
}
