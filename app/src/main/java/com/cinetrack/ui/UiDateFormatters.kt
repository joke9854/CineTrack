package com.cinetrack.ui

import java.text.DateFormat
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

/** Immutable formatters; replace the small bundle when the app language changes. */
internal object UiDateFormatters {
    data class Formats(val locale: Locale) {
        val dayMonth = DateTimeFormatter.ofPattern("dd\nMMM", locale)
        val month = DateTimeFormatter.ofPattern("MMM", locale)
        val narrowWeekday = DateTimeFormatter.ofPattern("EEEEE", locale)
        val shortWeekday = DateTimeFormatter.ofPattern("EEE", locale)
        val weekdayDay = DateTimeFormatter.ofPattern("EEEE d", locale)
        val time = DateTimeFormatter.ofPattern("HH:mm", locale)
        // The device-language short date, the same style as shortDate() (not a fixed dd/MM/yyyy).
        val date: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.SHORT).withLocale(locale)
        val weekdayDate = DateTimeFormatter.ofPattern("EEE\ndd MMM", locale)
    }
    @Volatile private var cached = Formats(Locale.getDefault())
    val current: Formats
        get() = of(Locale.getDefault())

    fun of(locale: Locale): Formats = cached.takeIf { it.locale == locale } ?: Formats(locale).also { cached = it }

    /** Short date in the device style, for epoch millis. */
    fun shortDate(millis: Long): String = DateFormat.getDateInstance(DateFormat.SHORT).format(Date(millis))

    /** Short date and time in the device style, for epoch millis. */
    fun shortDateTime(millis: Long): String =
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(millis))
}
