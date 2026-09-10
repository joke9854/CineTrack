package com.cinetrack.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Parses Simkl timestamps without losing their release time or UTC offset. */
fun releaseDateTime(raw: String?, fallbackZone: ZoneId): ZonedDateTime? {
    val value = raw?.trim()?.takeIf(String::isNotBlank) ?: return null
    val normalized = value.replaceFirst(' ', 'T')
    return runCatching { Instant.parse(normalized).atZone(fallbackZone) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(normalized).atZoneSameInstant(fallbackZone) }.getOrNull()
        ?: runCatching { ZonedDateTime.parse(normalized).withZoneSameInstant(fallbackZone) }.getOrNull()
        ?: runCatching { LocalDateTime.parse(normalized, DateTimeFormatter.ISO_LOCAL_DATE_TIME).atZone(fallbackZone) }.getOrNull()
        ?: runCatching { LocalDate.parse(value.take(10)).atStartOfDay(fallbackZone) }.getOrNull()
}

fun releaseInstant(raw: String?, fallbackZone: ZoneId): Instant? =
    releaseDateTime(raw, fallbackZone)?.toInstant()

fun hasExplicitReleaseTime(raw: String?): Boolean =
    raw?.trim()?.let { value ->
        value.length > 10 && (value.getOrNull(10) == 'T' || value.getOrNull(10) == ' ') &&
            value.drop(11).take(5).matches(Regex("\\d{2}:\\d{2}"))
    } == true
