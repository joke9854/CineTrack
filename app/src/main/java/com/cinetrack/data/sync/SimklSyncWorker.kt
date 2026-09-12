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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

object TrackingWorkScheduler {
    private const val WORK_NAME = "simkl-periodic-sync"

    fun update(context: Context, enabled: Boolean, wifiOnly: Boolean) {
        val workManager = WorkManager.getInstance(context)
        if (!enabled) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        val networkType = if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
        val request = PeriodicWorkRequestBuilder<TrackingSyncWorker>(9, TimeUnit.HOURS)
            .setInitialDelay(9, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(networkType).build())
            .build()
        workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}

/** Keeps old callers/source compatibility while scheduling the generic worker path. */
@Deprecated("Use TrackingWorkScheduler")
object SimklWorkScheduler {
    fun update(context: Context, enabled: Boolean, wifiOnly: Boolean) =
        TrackingWorkScheduler.update(context, enabled, wifiOnly)
}

open class TrackingSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val application = applicationContext as CineTrackApplication
        application.container.repository.awaitStartup()
        if (!application.container.syncCoordinator.isMainProviderConnected()) return Result.success()
        if (!application.container.repository.isMainTrackingSyncDue(TimeUnit.HOURS.toMillis(8))) return Result.success()
        return application.container.syncCoordinator.sync { }.fold(
            onSuccess = {
                application.container.preferences.mainTrackingProvider.first()?.let { provider ->
                    application.container.preferences.markTrackingChecked(provider)
                }
                try {
                    val state = application.container.repository.loadCachedState()
                    ReleaseNotifier.notifyUpcoming(
                        applicationContext,
                        state,
                        application.container.preferences,
                        application.container.repository,
                    )
                    ReleaseNotifier.scheduleUpcoming(applicationContext, state, application.container.preferences)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Throwable) {
                    // Release reminders are secondary to a completed data sync.
                }
                Result.success()
            },
            onFailure = {
                ReleaseNotifier.notifySyncFailure(applicationContext, application.container.preferences)
                if (runAttemptCount < 3) Result.retry() else Result.failure()
            },
        )
    }
}

/** Keeps the pre-refactor worker name available to existing manifests and callers. */
@Deprecated("Use TrackingSyncWorker")
class SimklSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : TrackingSyncWorker(appContext, params)

