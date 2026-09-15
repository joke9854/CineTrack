package com.cinetrack.data.sync.floppy

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.cinetrack.CineTrackApplication
import com.cinetrack.R
import com.cinetrack.data.sync.ProviderBootstrapState
import com.cinetrack.data.sync.TrackingProviderId
import com.cinetrack.data.sync.TrackingSyncError
import com.cinetrack.domain.FloppyBootstrapProgress
import com.cinetrack.domain.FloppyBootstrapStage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.util.concurrent.TimeUnit

object FloppyBootstrapWorkScheduler {
    private const val PREFIX = "floppy-bootstrap:"

    fun uniqueWorkName(connectionId: String) = "$PREFIX$connectionId"

    fun enqueue(context: Context, connectionId: String, wifiOnly: Boolean = false) {
        if (connectionId.isBlank()) return
        val request = OneTimeWorkRequestBuilder<FloppyBootstrapWorker>()
            .setInputData(workDataOf(FloppyBootstrapWorker.EXPECTED_CONNECTION_ID to connectionId))
            .addTag("floppy-bootstrap")
            .addTag("floppy-bootstrap:$connectionId")
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(uniqueWorkName(connectionId), ExistingWorkPolicy.KEEP, request)
    }

    fun cancel(context: Context, connectionId: String?) {
        connectionId?.takeIf(String::isNotBlank)?.let { WorkManager.getInstance(context).cancelUniqueWork(uniqueWorkName(it)) }
    }
}

/** WorkManager observation boundary for Floppy settings and startup restoration. */
class FloppyBootstrapWorkManager(context: Context) {
    private val workManager = WorkManager.getInstance(context.applicationContext)
    private val allProgress: Flow<List<WorkInfo>> = workManager
        .getWorkInfosByTagFlow("floppy-bootstrap")

    /** Compatibility view for callers that do not yet know the active instance. */
    val progress: Flow<FloppyBootstrapProgress?> = allProgress.map { infos -> infos.selectFloppyWork()?.toFloppyProgress() }

    /** Selects only WorkInfo belonging to the requested immutable provider instance. */
    fun progressFor(connectionId: String?): Flow<FloppyBootstrapProgress?> = allProgress.map { infos ->
        if (connectionId.isNullOrBlank()) null
        else infos.filter { info ->
            "floppy-bootstrap:$connectionId" in info.tags ||
                info.progress.getString("providerInstanceId") == connectionId ||
                info.outputData.getString("providerInstanceId") == connectionId
        }.selectFloppyWork()?.toFloppyProgress()
    }
}

private fun List<WorkInfo>.selectFloppyWork(): WorkInfo? =
    firstOrNull { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
        ?: firstOrNull()

class FloppyBootstrapWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val application = applicationContext as CineTrackApplication
        application.container.repository.awaitStartup()
        val expected = inputData.getString(EXPECTED_CONNECTION_ID).orEmpty()
        if (expected.isBlank() || !isCurrent(application, expected)) return Result.success()
        val coordinator = application.container.floppyBootstrapCoordinator
        val initialPlan = try {
            setProgress(progressData(FloppyBootstrapProgress(FloppyBootstrapStage.PREPARING, providerInstanceId = expected)))
            coordinator.start()
            coordinator.progress(expected).copy(stage = FloppyBootstrapStage.QUEUED)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            application.container.preferences.setProviderBootstrapState(TrackingProviderId.FLOPPY, ProviderBootstrapState.FAILED)
            setProgress(progressData(FloppyBootstrapProgress(FloppyBootstrapStage.NEEDS_ATTENTION, providerInstanceId = expected)))
            return terminalResult(error)
        }
        if (application.container.preferences.providerBootstrapStateNow(TrackingProviderId.FLOPPY) == ProviderBootstrapState.READY) {
            val complete = initialPlan.copy(stage = FloppyBootstrapStage.COMPLETE, processed = initialPlan.total, succeeded = initialPlan.total)
            setProgress(progressData(complete))
            return Result.success(progressData(complete))
        }
        setProgress(progressData(initialPlan))
        val plan = coordinator.ensurePlan()
        val total = plan.size
        if (total > 100) setForeground(createForegroundInfo(initialPlan.copy(total = total)))
        val chunkSize = 40
        var processed = (total - application.container.syncCoordinator.pendingOperationCount(plan.map { it.id }.toSet())).coerceIn(0, total)
        var failed = 0
        while (true) {
            if (!isCurrent(application, expected)) return Result.success()
            val pending = application.container.syncCoordinator.pendingOperationIds(plan.map { it.id }.toSet())
            if (pending.isEmpty()) break
            val batch = pending.take(chunkSize).toSet()
            val result = application.container.syncCoordinator.pushPendingForProvider(
                TrackingProviderId.FLOPPY,
                batch,
                expected,
            )
            if (result.isFailure) {
                val error = result.exceptionOrNull() ?: IllegalStateException("Floppy bootstrap delivery failed")
                // A reconnect can win the race after the pre-batch check. The
                // old instance must stop quietly rather than poisoning the new
                // instance's bootstrap state.
                if (!isCurrent(application, expected)) return Result.success()
                failed += batch.size
                val retryable = isRetryable(error)
                if (!retryable) {
                    application.container.preferences.setProviderBootstrapState(TrackingProviderId.FLOPPY, ProviderBootstrapState.FAILED)
                    setProgress(progressData(FloppyBootstrapProgress(FloppyBootstrapStage.NEEDS_ATTENTION, processed, total, processed, failed, expected)))
                } else {
                    setProgress(progressData(FloppyBootstrapProgress(FloppyBootstrapStage.SYNCING, processed, total, processed, failed, expected)))
                }
                return terminalResult(
                    error,
                    progressData(FloppyBootstrapProgress(FloppyBootstrapStage.NEEDS_ATTENTION, processed, total, processed, failed, expected)),
                )
            }
            if (!isCurrent(application, expected)) return Result.success()
            val remaining = application.container.syncCoordinator.pendingOperationCount(plan.map { it.id }.toSet())
            processed = (total - remaining).coerceIn(0, total)
            val progress = FloppyBootstrapProgress(FloppyBootstrapStage.SYNCING, processed, total, processed, failed, expected)
            setProgress(progressData(progress))
            if (total > 100) setForeground(createForegroundInfo(progress))
        }
        if (!isCurrent(application, expected)) return Result.success()
        setProgress(progressData(FloppyBootstrapProgress(FloppyBootstrapStage.VERIFYING, processed, total, processed, failed, expected)))
        val ready = coordinator.markReadyIfComplete()
        if (!ready) {
            application.container.preferences.setProviderBootstrapState(TrackingProviderId.FLOPPY, ProviderBootstrapState.FAILED)
            setProgress(progressData(FloppyBootstrapProgress(FloppyBootstrapStage.NEEDS_ATTENTION, processed, total, processed, failed, expected)))
            return Result.failure(progressData(FloppyBootstrapProgress(FloppyBootstrapStage.NEEDS_ATTENTION, processed, total, processed, failed, expected)))
        }
        setProgress(progressData(FloppyBootstrapProgress(FloppyBootstrapStage.COMPLETE, total, total, total, failed, expected)))
        return Result.success(progressData(FloppyBootstrapProgress(FloppyBootstrapStage.COMPLETE, total, total, total, failed, expected)))
    }

    private suspend fun isCurrent(application: CineTrackApplication, expected: String): Boolean =
        application.container.preferences.floppySettingsNow()?.connectionId == expected

    private fun terminalResult(error: Throwable, failureData: Data? = null): Result = when (error) {
        is TrackingSyncError.NetworkUnavailable,
        is TrackingSyncError.Timeout,
        is TrackingSyncError.RateLimited,
        is IOException,
        -> if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure(failureData ?: Data.EMPTY)
        else -> Result.failure(failureData ?: Data.EMPTY)
    }

    private fun isRetryable(error: Throwable): Boolean = when (error) {
        is TrackingSyncError.NetworkUnavailable,
        is TrackingSyncError.Timeout,
        is TrackingSyncError.RateLimited,
        is IOException,
        -> true
        else -> false
    }

    private fun progressData(progress: FloppyBootstrapProgress): Data = workDataOf(
        "stage" to progress.stage.name,
        "processed" to progress.processed,
        "total" to progress.total,
        "succeeded" to progress.succeeded,
        "failed" to progress.failed,
        "providerInstanceId" to progress.providerInstanceId,
    )

    private fun createForegroundInfo(progress: FloppyBootstrapProgress): androidx.work.ForegroundInfo {
        if (Build.VERSION.SDK_INT >= 26) {
            applicationContext.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "CineTrack background sync", NotificationManager.IMPORTANCE_LOW),
            )
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle(applicationContext.getString(R.string.app_name))
            .setContentText("Syncing with Floppy · ${progress.processed} of ${progress.total}")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(progress.total, progress.processed, progress.total == 0)
            .build()
        return if (Build.VERSION.SDK_INT >= 29) {
            androidx.work.ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else androidx.work.ForegroundInfo(NOTIFICATION_ID, notification)
    }

    companion object {
        const val EXPECTED_CONNECTION_ID = "expectedConnectionId"
        private const val MAX_RETRIES = 5
        private const val CHANNEL_ID = "cinetrack_background_sync"
        private const val NOTIFICATION_ID = 6011
    }
}

private fun WorkInfo.toFloppyProgress(): FloppyBootstrapProgress {
    val data = if (state == WorkInfo.State.SUCCEEDED || state == WorkInfo.State.FAILED) outputData else progress
    val stage = runCatching { FloppyBootstrapStage.valueOf(data.getString("stage").orEmpty()) }.getOrElse {
        when (state) {
            WorkInfo.State.RUNNING -> FloppyBootstrapStage.SYNCING
            WorkInfo.State.FAILED -> FloppyBootstrapStage.NEEDS_ATTENTION
            WorkInfo.State.SUCCEEDED -> FloppyBootstrapStage.COMPLETE
            else -> FloppyBootstrapStage.QUEUED
        }
    }
    return FloppyBootstrapProgress(
        stage = stage,
        processed = data.getInt("processed", 0),
        total = data.getInt("total", 0),
        succeeded = data.getInt("succeeded", 0),
        failed = data.getInt("failed", 0),
        providerInstanceId = data.getString("providerInstanceId"),
    )
}

