package com.cinetrack.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.cinetrack.CineTrackApplication
import java.util.concurrent.TimeUnit

object SimklWorkScheduler {
    private const val WORK_NAME = "simkl-periodic-sync"

    fun update(context: Context, enabled: Boolean, wifiOnly: Boolean) {
        val workManager = WorkManager.getInstance(context)
        if (!enabled) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        val networkType = if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
        val request = PeriodicWorkRequestBuilder<SimklSyncWorker>(9, TimeUnit.HOURS)
            .setInitialDelay(9, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(networkType).build())
            .build()
        workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}

class SimklSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val application = applicationContext as CineTrackApplication
        if (!application.container.preferences.simklConnectedValue()) return Result.success()
        if (!application.container.repository.isSimklSyncDue(TimeUnit.HOURS.toMillis(8))) return Result.success()
        return application.container.repository.syncSimkl { }.fold(
            onSuccess = {
                runCatching {
                    ReleaseNotifier.notifyUpcoming(
                        applicationContext,
                        application.container.repository.loadCachedState(),
                        application.container.preferences,
                        application.container.repository,
                    )
                }
                Result.success()
            },
            onFailure = { if (runAttemptCount < 3) Result.retry() else Result.failure() },
        )
    }
}

private suspend fun com.cinetrack.data.repository.AppPreferences.simklConnectedValue(): Boolean =
    !tokenNow().isNullOrBlank()
