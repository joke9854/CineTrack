package com.cinetrack.domain

import java.time.Instant

enum class LogSeverity { ERROR, WARNING, INFO }

/** One line of the app log, split for display. The exported file keeps the raw lines. */
data class LogEntry(
    val at: Instant?,
    val severity: LogSeverity,
    /** Short headline: the message up to its first colon, without run/connection ids. */
    val summary: String,
    /** The whole message, for the expanded view. */
    val detail: String,
)

private val ERROR_PATTERN = Regex(
    "failed|error|stopped|timed out|could not|not marked ready|needs attention|refused|unavailable",
    RegexOption.IGNORE_CASE,
)
private val WARNING_PATTERN = Regex(
    "skipped|not accepted|no floppy counterpart|no progress|will retry|retry|slow|paused|stalled",
    RegexOption.IGNORE_CASE,
)
private val NOISE_PATTERN = Regex("\\s*\\b(run|connection)=[0-9a-f-]{8,}")

/** Parses "2026-10-10T14:09:00.071Z  message" lines written by appendErrorLog. */
fun parseLogLine(line: String): LogEntry {
    val trimmed = line.trim()
    val separator = trimmed.indexOf(' ')
    val at = if (separator > 0) runCatching { Instant.parse(trimmed.substring(0, separator)) }.getOrNull() else null
    val message = if (at != null) trimmed.substring(separator).trim() else trimmed
    val severity = when {
        ERROR_PATTERN.containsMatchIn(message) -> LogSeverity.ERROR
        WARNING_PATTERN.containsMatchIn(message) -> LogSeverity.WARNING
        else -> LogSeverity.INFO
    }
    val cleaned = message.replace(NOISE_PATTERN, "").trim()
    val colon = cleaned.indexOf(':')
    val summary = (if (colon in 1..90) cleaned.substring(0, colon) else cleaned.take(90)).trim()
    return LogEntry(at, severity, summary, cleaned)
}
