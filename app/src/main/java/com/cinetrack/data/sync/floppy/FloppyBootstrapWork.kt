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
import com.cinetrack.data.sync.ConnectionResult
import com.cinetrack.data.sync.TrackingProviderId
import com.cinetrack.data.sync.TrackingSyncError
import com.cinetrack.data.sync.SyncOperation
import com.cinetrack.data.sync.SyncOperationType
import com.cinetrack.domain.FloppyBootstrapProgress
import com.cinetrack.domain.FloppyBootstrapStage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.IOException
import kotlin.Result as SyncResult
import java.util.concurrent.TimeUnit

internal fun isFloppyBootstrapRetryable(error: Throwable): Boolean = when (error) {
    is TrackingSyncError.NetworkUnavailable,
    is TrackingSyncError.DnsFailure,
    is TrackingSyncError.Timeout,
    is TrackingSyncError.RateLimited,
    is IOException,
    -> true
    else -> false
}

object FloppyBootstrapWorkScheduler {
    private const val PREFIX = "floppy-bootstrap:"

    fun uniqueWorkName(connectionId: String) = "$PREFIX$connectionId"

    fun enqueue(context: Context, connectionId: String, wifiOnly: Boolean = false) {
        ensureScheduled(context, connectionId, wifiOnly)
    }

    /** Idempotent scheduling for the current immutable Floppy instance. */
    fun ensureScheduled(context: Context, connectionId: String, wifiOnly: Boolean = false) {
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

    /** Explicit user retry for a stalled/failed job. The persisted plan and
     * operation generations are untouched; only the WorkManager execution is
     * replaced. */
    fun retryNow(context: Context, connectionId: String, wifiOnly: Boolean = false) {
        if (connectionId.isBlank()) return
        WorkManager.getInstance(context).cancelUniqueWork(uniqueWorkName(connectionId))
        ensureScheduled(context, connectionId, wifiOnly)
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
        val planIds = plan.mapTo(linkedSetOf()) { it.id }
        var processed = (total - application.container.syncCoordinator.pendingOperationCount(planIds)).coerceIn(0, total)
        var failed = 0
        // A cheap authenticated reachability check prevents a transient
        // private-DNS/VPN outage from poisoning an entire batch of durable
        // delivery rows. It runs once per WorkManager attempt, not per item.
        val provider = application.container.trackingProviderRegistry.getProvider(TrackingProviderId.FLOPPY)
        val preflight = provider?.let {
            application.container.syncCoordinator.withProviderIoQuiesced { it.testConnection() }
        }
        when (val connection = preflight) {
            ConnectionResult.Connected -> Unit
            ConnectionResult.AuthenticationRequired -> {
                val error = TrackingSyncError.AuthenticationRequired(TrackingProviderId.FLOPPY)
                application.container.preferences.setProviderBootstrapState(TrackingProviderId.FLOPPY, ProviderBootstrapState.FAILED)
                setProgress(progressData(FloppyBootstrapProgress(FloppyBootstrapStage.NEEDS_ATTENTION, processed, total, processed, failed, expected)))
                return Result.failure(progressData(FloppyBootstrapProgress(FloppyBootstrapStage.NEEDS_ATTENTION, processed, total, processed, failed, expected)))
            }
            is ConnectionResult.Failed -> {
                if (!isCurrent(application, expected)) return Result.success()
                val error = connection.error
                val waiting = FloppyBootstrapProgress(FloppyBootstrapStage.WAITING_FOR_SERVER, processed, total, processed, failed, expected)
                setProgress(progressData(waiting))
                return terminalResult(error, progressData(waiting))
            }
            null -> {
                val error = TrackingSyncError.ProviderUnavailable(TrackingProviderId.FLOPPY)
                setProgress(progressData(FloppyBootstrapProgress(FloppyBootstrapStage.WAITING_FOR_SERVER, processed, total, processed, failed, expected)))
                return terminalResult(error)
            }
        }
        val floppy = application.container.trackingProviderRegistry
            .getProvider(TrackingProviderId.FLOPPY) as? FloppyTrackingProvider
            ?: return terminalResult(TrackingSyncError.ProviderUnavailable(TrackingProviderId.FLOPPY))
        val transport = try {
            floppy.openBootstrapSession(expected)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            setProgress(progressData(FloppyBootstrapProgress(FloppyBootstrapStage.WAITING_FOR_SERVER, processed, total, processed, failed, expected)))
            return terminalResult(error)
        }

        // Expensive remote preparation belongs to its own stage.  In
        // particular, episode history is indexed once for this worker run,
        // never once per 15-operation batch.
        val remainingBeforeIndex = application.container.syncCoordinator.pendingOperations(planIds)
        if (remainingBeforeIndex.any { it.type == SyncOperationType.EPISODE_WATCHED }) {
            setProgress(progressData(FloppyBootstrapProgress(FloppyBootstrapStage.CHECKING_REMOTE_STATE, processed, total, processed, failed, expected)))
            try {
                withTimeout(90_000) { transport.prepare(remainingBeforeIndex) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                val stage = if (isRetryable(error)) FloppyBootstrapStage.WAITING_FOR_SERVER else FloppyBootstrapStage.NEEDS_ATTENTION
                setProgress(progressData(FloppyBootstrapProgress(stage, processed, total, processed, failed, expected)))
                return terminalResult(error)
            }
        }

        var lastProgressAt = System.currentTimeMillis()
        var stalledPublished = false
        var current: FloppyBootstrapProgress? = null
        var stoppedForInstanceChange = false
        var loopError: Throwable? = null
        coroutineScope {
            val watchdog = launch {
                while (isActive) {
                    delay(WATCHDOG_POLL_MS)
                    if (!stalledPublished && current?.stage == FloppyBootstrapStage.SYNCING &&
                        System.currentTimeMillis() - lastProgressAt >= NO_PROGRESS_TIMEOUT_MS
                    ) {
                        stalledPublished = true
                        val stalled = current!!.copy(stage = FloppyBootstrapStage.STALLED, lastProgressAtMillis = lastProgressAt)
                        setProgress(progressData(stalled))
                    }
                }
            }
            try {
                while (true) {
                    if (!isCurrent(application, expected)) {
                        stoppedForInstanceChange = true
                        break
                    }
                    val pendingOps = application.container.syncCoordinator.pendingOperations(planIds)
                    if (pendingOps.isEmpty()) break
                    val unit = pendingOps.bootstrapLogicalUnit()
                    val first = unit.first()
                    current = FloppyBootstrapProgress(
                        FloppyBootstrapStage.SYNCING,
                        processed,
                        total,
                        processed,
                        failed,
                        expected,
                        currentOperationType = first.type.name,
                        currentTitle = first.title.takeIf(String::isNotBlank),
                        lastProgressAtMillis = lastProgressAt,
                    )
                    setProgress(progressData(current!!))
                    val result: SyncResult<Unit> = try {
                        withTimeout(90_000) {
                            application.container.syncCoordinator.pushPendingForProvider(
                                TrackingProviderId.FLOPPY,
                                unit.mapTo(linkedSetOf()) { it.id },
                                expected,
                                transport = transport::push,
                            )
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Throwable) {
                        SyncResult.failure(error)
                    }
                    if (result.isFailure) {
                        val error = result.exceptionOrNull() ?: IllegalStateException("Floppy bootstrap delivery failed")
                        if (!isCurrent(application, expected)) {
                            stoppedForInstanceChange = true
                            break
                        }
                        failed += unit.size
                        val retryable = isRetryable(error)
                        val terminalStage = if (retryable) FloppyBootstrapStage.WAITING_FOR_SERVER else FloppyBootstrapStage.NEEDS_ATTENTION
                        if (!retryable) application.container.preferences.setProviderBootstrapState(TrackingProviderId.FLOPPY, ProviderBootstrapState.FAILED)
                        val failure = FloppyBootstrapProgress(terminalStage, processed, total, processed, failed, expected, first.type.name, first.title.takeIf(String::isNotBlank), lastProgressAt)
                        setProgress(progressData(failure))
                        loopError = error
                        break
                    }
                    if (!isCurrent(application, expected)) {
                        stoppedForInstanceChange = true
                        break
                    }
                    val remaining = application.container.syncCoordinator.pendingOperationCount(planIds)
                    processed = (total - remaining).coerceIn(0, total)
                    lastProgressAt = System.currentTimeMillis()
                    stalledPublished = false
                    current = FloppyBootstrapProgress(FloppyBootstrapStage.SYNCING, processed, total, processed, failed, expected, first.type.name, first.title.takeIf(String::isNotBlank), lastProgressAt)
                    setProgress(progressData(current!!))
                    if (total > 100) setForeground(createForegroundInfo(current!!))
                }
            } finally {
                watchdog.cancel()
            }
        }
        if (stoppedForInstanceChange) return Result.success()
        loopError?.let { return terminalResult(it) }
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

    private suspend fun terminalResult(error: Throwable, failureData: Data? = null): Result {
        val retryable = isFloppyBootstrapRetryable(error)
        if (retryable && runAttemptCount < MAX_RETRIES) return Result.retry()
        val data = failureData ?: Data.EMPTY
        if (retryable) {
            (applicationContext as? CineTrackApplication)?.container?.preferences?.setProviderBootstrapState(
                TrackingProviderId.FLOPPY,
                ProviderBootstrapState.FAILED,
            )
            val attention = workDataOf(
                "stage" to FloppyBootstrapStage.NEEDS_ATTENTION.name,
                "processed" to data.getInt("processed", 0),
                "total" to data.getInt("total", 0),
                "succeeded" to data.getInt("succeeded", data.getInt("processed", 0)),
                "failed" to data.getInt("failed", 0),
                "providerInstanceId" to data.getString("providerInstanceId"),
            )
            setProgress(attention)
            return Result.failure(attention)
        }
        return Result.failure(data)
    }

    private fun isRetryable(error: Throwable): Boolean = isFloppyBootstrapRetryable(error)

    private fun progressData(progress: FloppyBootstrapProgress): Data = Data.Builder()
        .putString("stage", progress.stage.name)
        .putInt("processed", progress.processed)
        .putInt("total", progress.total)
        .putInt("succeeded", progress.succeeded)
        .putInt("failed", progress.failed)
        .apply {
            progress.providerInstanceId?.let { putString("providerInstanceId", it) }
            progress.currentOperationType?.let { putString("currentOperationType", it) }
            progress.currentTitle?.let { putString("currentTitle", it) }
            progress.lastProgressAtMillis?.let { putLong("lastProgressAt", it) }
        }
        .build()

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
        private const val WATCHDOG_POLL_MS = 1_000L
        private const val NO_PROGRESS_TIMEOUT_MS = 60_000L
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
        currentOperationType = data.getString("currentOperationType"),
        currentTitle = data.getString("currentTitle"),
        lastProgressAtMillis = data.getLong("lastProgressAt", 0L).takeIf { it > 0L },
    )
}

/** Keeps a completed-movie pair together while allowing every other
 * bootstrap operation to advance progress independently. */
internal fun List<SyncOperation>.bootstrapLogicalUnit(): List<SyncOperation> {
    val first = first()
    if (first.mediaType != com.cinetrack.domain.MediaType.MOVIE) return listOf(first)
    val pair = filter {
        it.mediaId == first.mediaId && it.sourceVersion == first.sourceVersion &&
            it.type in setOf(SyncOperationType.LIBRARY_STATUS, SyncOperationType.MOVIE_WATCHED)
    }
    return if (pair.any { it.type == SyncOperationType.LIBRARY_STATUS && it.value == com.cinetrack.domain.LibraryStatus.COMPLETED.name } &&
        pair.any { it.type == SyncOperationType.MOVIE_WATCHED }
    ) pair else listOf(first)
}

