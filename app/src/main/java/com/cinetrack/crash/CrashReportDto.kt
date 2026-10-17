package com.cinetrack.crash

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Body of `POST /api/v1/cinetrack/crash-reports/`; field limits are Floppy's. */
@Serializable
data class CrashReportDto(
    @SerialName("report_id") val reportId: String,
    val kind: String,
    @SerialName("occurred_at") val occurredAt: String,
    @SerialName("app_version") val appVersion: String,
    @SerialName("version_code") val versionCode: Int,
    @SerialName("build_type") val buildType: String,
    @SerialName("android_sdk") val androidSdk: Int,
    @SerialName("device_model") val deviceModel: String,
    @SerialName("exception_class") val exceptionClass: String? = null,
    val thread: String? = null,
    @SerialName("stack_trace") val stackTrace: String,
) {
    companion object {
        const val KIND_CRASH = "crash"
        const val KIND_ANR = "anr"
    }
}

@Serializable
data class CrashReportResponse(
    @SerialName("report_id") val reportId: String = "",
    val status: String = "",
)
