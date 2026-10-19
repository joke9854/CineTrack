package com.cinetrack.domain

import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * The Upcoming list as an iCalendar file. A release with an explicit time becomes a UTC
 * DTSTART; a date-only release becomes an all-day event.
 */
fun upcomingCalendarIcs(items: List<TimelineCard>, releaseZone: ZoneId): String = buildString {
    appendLine("BEGIN:VCALENDAR")
    appendLine("VERSION:2.0")
    appendLine("PRODID:-//CineTrack//Upcoming//EN")
    items.forEach { item ->
        val day = item.timestamp.take(10).replace("-", "")
        val uid = "${item.media.stableKey}-${item.season ?: 0}-${item.episodeNumber ?: 0}-$day@cinetrack"
        val summary = (item.media.title + item.episodeLabel?.let { " · $it" }.orEmpty())
            .replace("\\", "\\\\").replace(",", "\\,").replace(";", "\\;")
        appendLine("BEGIN:VEVENT")
        appendLine("UID:$uid")
        val releaseAt = releaseDateTime(item.timestamp, releaseZone)
        if (releaseAt != null && hasExplicitReleaseTime(item.timestamp)) {
            val utcTimestamp = DateTimeFormatter
                .ofPattern("yyyyMMdd'T'HHmmss'Z'")
                .withZone(ZoneOffset.UTC)
                .format(releaseAt.toInstant())
            appendLine("DTSTART:$utcTimestamp")
        } else {
            appendLine("DTSTART;VALUE=DATE:$day")
        }
        appendLine("SUMMARY:$summary")
        appendLine("END:VEVENT")
    }
    appendLine("END:VCALENDAR")
}
