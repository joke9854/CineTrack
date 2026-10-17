package com.cinetrack.crash

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.cinetrack.CineTrackApplication
import java.util.concurrent.TimeUnit

class CrashReportUploadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val application = applicationContext as CineTrackApplication
        return when (application.container.crashReportUploader.upload()) {
            CrashUploadResult.SUCCESS -> Result.success()
            CrashUploadResult.RETRY -> Result.retry()
        }
    }
}

/** Entry points the app uses; the pieces below are tested on their own. */
object CrashReporting {
    const val UNIQUE_WORK_NAME = "cinetrack-crash-report-upload"

    /** Blocking; call off the main thread, main process only. */
    fun startup(context: Context, hasFloppyConnection: Boolean) {
        AnrCollector.collect(context)
        if (hasFloppyConnection) scheduleUpload(context)
    }

    fun scheduleUpload(context: Context) {
        if (!CrashReportSettings.isEnabled(context) || CrashOutbox.forContext(context).isEmpty()) return
        val request = OneTimeWorkRequestBuilder<CrashReportUploadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    /** Off clears everything waiting and cancels the worker; on lets the next start queue an upload. */
    fun setEnabled(context: Context, enabled: Boolean) {
        CrashReportSettings.setEnabled(context, enabled)
        if (!enabled) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
            CrashOutbox.forContext(context).clear()
        }
    }
}
