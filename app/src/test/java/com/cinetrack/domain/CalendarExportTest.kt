package com.cinetrack.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class CalendarExportTest {
    private val rome = ZoneId.of("Europe/Rome")
    private val dark = MediaCard(2, MediaType.TV, "Dark")
    private val dune = MediaCard(1, MediaType.MOVIE, "Dune")

    private fun events(ics: String) = ics.lines().filter(String::isNotEmpty)

    @Test fun timedEpisodeBecomesUtcStartAndDateOnlyMovieAllDay() {
        val ics = upcomingCalendarIcs(
            listOf(
                TimelineCard(dark, "Upcoming", "2026-10-20T21:00:00+02:00", episodeLabel = "S1E2", season = 1, episodeNumber = 2),
                TimelineCard(dune, "Upcoming", "2026-11-05"),
            ),
            rome,
        )
        assertEquals(
            listOf(
                "BEGIN:VCALENDAR",
                "VERSION:2.0",
                "PRODID:-//CineTrack//Upcoming//EN",
                "BEGIN:VEVENT",
                "UID:TV:2-1-2-20261020@cinetrack",
                "DTSTART:20261020T190000Z",
                "SUMMARY:Dark · S1E2",
                "END:VEVENT",
                "BEGIN:VEVENT",
                "UID:MOVIE:1-0-0-20261105@cinetrack",
                "DTSTART;VALUE=DATE:20261105",
                "SUMMARY:Dune",
                "END:VEVENT",
                "END:VCALENDAR",
            ),
            events(ics),
        )
    }

    @Test fun localTimeWithoutOffsetUsesTheReleaseZone() {
        val ics = upcomingCalendarIcs(listOf(TimelineCard(dark, "Upcoming", "2026-01-10 09:30", season = 2, episodeNumber = 1)), rome)
        assertTrue(ics.contains("DTSTART:20260110T083000Z\n"))
    }

    @Test fun summaryEscapesIcsSpecialCharacters() {
        val ics = upcomingCalendarIcs(listOf(TimelineCard(MediaCard(3, MediaType.MOVIE, "Love, Death; Robots\\"), "Upcoming", "2026-03-01")), rome)
        assertTrue(ics.contains("SUMMARY:Love\\, Death\\; Robots\\\\\n"))
    }

    @Test fun emptyListIsAValidEmptyCalendar() {
        assertEquals(listOf("BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//CineTrack//Upcoming//EN", "END:VCALENDAR"), events(upcomingCalendarIcs(emptyList(), rome)))
    }
}
