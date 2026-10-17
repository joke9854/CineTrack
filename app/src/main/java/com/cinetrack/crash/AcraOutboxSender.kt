package com.cinetrack.crash

import android.content.Context
import android.os.Build
import com.cinetrack.BuildConfig
import org.acra.ReportField
import org.acra.config.CoreConfiguration
import org.acra.data.CrashReportData
import org.acra.sender.ReportSender
import org.acra.sender.ReportSenderFactory

/** Registered through META-INF/services; ACRA creates it in its `:acra` process. */
class AcraOutboxSenderFactory : ReportSenderFactory {
    override fun create(context: Context, config: CoreConfiguration): ReportSender = AcraOutboxSender()
}

/**
 * Turns an ACRA report into the Floppy contract and parks it in the outbox.
 * It never touches DataStore or the network: the main process uploads later.
 */
class AcraOutboxSender : ReportSender {
    override fun send(context: Context, errorContent: CrashReportData) {
        runCatching {
            CrashReportIntake(
                outbox = CrashOutbox.forContext(context),
                enabled = { CrashReportSettings.isEnabled(context) },
                build = ::currentBuildInfo,
            ).accept(fields(errorContent))
        }
    }

    private fun fields(data: CrashReportData) = AcraFields(
        reportId = data.getString(ReportField.REPORT_ID),
        appVersionName = data.getString(ReportField.APP_VERSION_NAME),
        appVersionCode = data.getString(ReportField.APP_VERSION_CODE),
        phoneModel = data.getString(ReportField.PHONE_MODEL),
        brand = data.getString(ReportField.BRAND),
        stackTrace = data.getString(ReportField.STACK_TRACE),
        crashDate = data.getString(ReportField.USER_CRASH_DATE),
        threadDetails = data.getString(ReportField.THREAD_DETAILS),
    )
}

internal fun currentBuildInfo() = CrashBuildInfo(
    versionName = BuildConfig.VERSION_NAME,
    versionCode = BuildConfig.VERSION_CODE,
    buildType = BuildConfig.BUILD_TYPE,
    sdk = Build.VERSION.SDK_INT,
    deviceModel = "${Build.BRAND} ${Build.MODEL}".trim(),
)
