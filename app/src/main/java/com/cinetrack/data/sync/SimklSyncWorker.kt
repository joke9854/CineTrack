package com.cinetrack.data.sync

import com.cinetrack.data.repository.ProgressRefreshRequest
import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
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

    private const val RATE_LIMIT_RETRY = "simkl-rate-limit-retry"

    /**
     * After Simkl's 429, send the queued changes once its allowance is back: after Retry-After
     * when Simkl gives one, otherwise in an hour. A newer 429 replaces the pending retry.
     */
    fun scheduleRateLimitRetry(context: Context, retryAfterSeconds: Long?) {
        val delay = (retryAfterSeconds ?: TimeUnit.HOURS.toSeconds(1)).coerceIn(60, TimeUnit.DAYS.toSeconds(1))
        val request = OneTimeWorkRequestBuilder<PendingDeliveryWorker>()
            .setInitialDelay(delay, TimeUnit.SECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(RATE_LIMIT_RETRY, ExistingWorkPolicy.REPLACE, request)
    }
}

/** Sends the durable queue without a full sync; used to resume after Simkl's rate limit. */
class PendingDeliveryWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as CineTrackApplication).container
        container.syncHost.awaitStartup()
        if (!container.syncCoordinator.isMainProviderConnected()) return Result.success()
        return container.syncCoordinator.pushPending().fold(
            onSuccess = { Result.success() },
            // A new 429 has already scheduled the next attempt.
            onFailure = { error -> if (error is TrackingSyncError.RateLimited || runAttemptCount >= 3) Result.success() else Result.retry() },
        )
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
        application.container.syncHost.awaitStartup()
        // Even without a sync, keep Progress current: the release schedule and
        // next episodes refresh on their own cadence (cheap when nothing is due).
        if (!application.container.syncCoordinator.isMainProviderConnected() ||
            !application.container.syncHost.isMainTrackingSyncDue(TimeUnit.HOURS.toMillis(8))
        ) {
            runCatching { application.container.progressCache.refreshProgressCache(ProgressRefreshRequest(), null) }
            return Result.success()
        }
        return application.container.syncCoordinator.sync { }.fold(
            onSuccess = {
                application.container.preferences.mainTrackingProvider.first()?.let { provider ->
                    application.container.preferences.markTrackingChecked(provider)
                }
                try {
                    val state = application.container.appState.loadCachedState()
                    ReleaseNotifier.notifyUpcoming(
                        applicationContext,
                        state,
                        application.container.preferences,
                        application.container.mediaMetadata,
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

