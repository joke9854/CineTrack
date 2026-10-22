package com.cinetrack.crash

import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID
import com.cinetrack.data.remote.AppJson

/** The ACRA report fields CineTrack collects, already read out of ACRA's JSON container. */
data class AcraFields(
    val reportId: String?,
    val appVersionName: String?,
    val appVersionCode: String?,
    val phoneModel: String?,
    val brand: String?,
    val stackTrace: String?,
    val crashDate: String?,
    val threadDetails: String?,
)

/** What identifies this build and device; the fallback for fields a report lacks. */
data class CrashBuildInfo(
    val versionName: String,
    val versionCode: Int,
    val buildType: String,
    val sdk: Int,
    val deviceModel: String,
)

object CrashReportMapper {
    private val acraDate = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
    private val json = AppJson.tolerant
    private val className = Regex("""^[A-Za-z_$][\w$.]*$""")

    /** Null when the report carries no stack trace (the contract requires one). */
    fun fromAcra(fields: AcraFields, build: CrashBuildInfo, now: Instant = Instant.now()): CrashReportDto? {
        val trace = fields.stackTrace?.takeIf(String::isNotBlank) ?: return null
        val model = deviceModel(fields.brand, fields.phoneModel) ?: build.deviceModel
        return CrashReportDto(
            reportId = fields.reportId?.let(::validUuid) ?: UUID.nameUUIDFromBytes(trace.toByteArray()).toString(),
            kind = CrashReportDto.KIND_CRASH,
            occurredAt = parseDate(fields.crashDate) ?: format(now),
            appVersion = clip(fields.appVersionName?.takeIf(String::isNotBlank) ?: build.versionName, 64),
            versionCode = fields.appVersionCode?.toIntOrNull() ?: build.versionCode,
            buildType = clip(build.buildType, 32),
            androidSdk = build.sdk,
            deviceModel = clip(model, 128),
            exceptionClass = exceptionClass(trace)?.let { clip(CrashSanitizer.sanitize(it), 256) },
            thread = threadName(fields.threadDetails)?.let { clip(CrashSanitizer.sanitize(it), 128) },
            stackTrace = CrashSanitizer.sanitizeStackTrace(trace),
        )
    }

    fun fromAnr(pid: Int, timestamp: Long, trace: String, build: CrashBuildInfo): CrashReportDto = CrashReportDto(
        reportId = UUID.nameUUIDFromBytes("anr:$pid:$timestamp".toByteArray()).toString(),
        kind = CrashReportDto.KIND_ANR,
        occurredAt = format(Instant.ofEpochMilli(timestamp)),
        appVersion = clip(build.versionName, 64),
        versionCode = build.versionCode,
        buildType = clip(build.buildType, 32),
        androidSdk = build.sdk,
        deviceModel = clip(build.deviceModel, 128),
        exceptionClass = null,
        thread = null,
        stackTrace = CrashSanitizer.sanitizeStackTrace(trace.ifBlank { "ANR: no trace available" }),
    )

    private fun deviceModel(brand: String?, model: String?): String? {
        val m = model?.trim().orEmpty()
        val b = brand?.trim().orEmpty()
        return when {
            m.isEmpty() && b.isEmpty() -> null
            b.isEmpty() || m.startsWith(b, ignoreCase = true) -> m
            m.isEmpty() -> b
            else -> "$b $m"
        }
    }

    private fun validUuid(value: String): String? = runCatching { UUID.fromString(value.trim()).toString() }.getOrNull()

    private fun parseDate(value: String?): String? {
        val text = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val parsed = runCatching { OffsetDateTime.parse(text, DateTimeFormatter.ISO_OFFSET_DATE_TIME) }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(text, acraDate) }.getOrNull()
            ?: return null
        return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(parsed)
    }

    private fun format(instant: Instant): String =
        DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(OffsetDateTime.ofInstant(instant, ZoneOffset.UTC))

    private fun exceptionClass(trace: String): String? =
        trace.lineSequence().firstOrNull()?.substringBefore(':')?.trim()?.takeIf { it.matches(className) }

    private fun threadName(details: String?): String? = runCatching {
        json.parseToJsonElement(details ?: return null).jsonObject["name"]?.jsonPrimitive?.contentOrNull
    }.getOrNull()?.takeIf(String::isNotBlank)

    private fun clip(value: String, max: Int) = if (value.length <= max) value else value.take(max)
}
