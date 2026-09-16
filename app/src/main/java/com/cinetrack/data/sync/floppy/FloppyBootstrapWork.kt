package com.cinetrack.data.sync.floppy

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
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
        val runId = java.util.UUID.randomUUID().toString()
        val request = OneTimeWorkRequestBuilder<FloppyBootstrapWorker>()
            .setInputData(
                workDataOf(
                    FloppyBootstrapWorker.EXPECTED_CONNECTION_ID to connectionId,
                    FloppyBootstrapWorker.BOOTSTRAP_RUN_ID to runId,
                    FloppyBootstrapWorker.BOOTSTRAP_SCHEDULED_AT to System.currentTimeMillis(),
                ),
            )
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

    /** Selects the unique WorkManager execution for the requested immutable
     * provider instance. Querying the unique name avoids historical tagged
     * retries/cancellations masking the active instance's progress. */
    fun progressFor(connectionId: String?): Flow<FloppyBootstrapProgress?> =
        if (connectionId.isNullOrBlank()) {
            flowOf(null)
        } else {
            workManager.getWorkInfosForUniqueWorkFlow(FloppyBootstrapWorkScheduler.uniqueWorkName(connectionId))
                .map { infos -> infos.selectFloppyWork()?.toFloppyProgress() }
        }
}

private fun List<WorkInfo>.selectFloppyWork(): WorkInfo? =
    asSequence()
        .sortedByDescending {
            maxOf(
                it.progress.getLong(FloppyBootstrapWorker.BOOTSTRAP_SCHEDULED_AT, 0L),
                it.outputData.getLong(FloppyBootstrapWorker.BOOTSTRAP_SCHEDULED_AT, 0L),
            )
        }
        .firstOrNull { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
        ?: asSequence()
            .sortedByDescending {
                maxOf(
                    it.progress.getLong(FloppyBootstrapWorker.BOOTSTRAP_SCHEDULED_AT, 0L),
                    it.outputData.getLong(FloppyBootstrapWorker.BOOTSTRAP_SCHEDULED_AT, 0L),
                )
            }
            .firstOrNull()

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
            setProgress(progressData(FloppyBootstrapProgress(FloppyBootstrapStage.BUILDING_PLAN, providerInstanceId = expected)))
            coordinator.start { stage, prepared, planningTotal ->
                setProgress(
                    progressData(
                        FloppyBootstrapProgress(
                            stage = stage,
                            providerInstanceId = expected,
                            planningProcessed = prepared,
                            planningTotal = planningTotal,
                        ),
                    ),
                )
            }
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
        Log.i(TAG, "Floppy bootstrap started: plan total=$total")
        if (total > 100) setForeground(createForegroundInfo(initialPlan.copy(total = total)))
        var processed = (total - application.container.syncCoordinator.pendingBootstrapCount(expected)).coerceIn(0, total)
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
        // Seed remote-state preparation with one focused SQL result instead
        // of loading the entire 9k-operation plan just to detect episodes.
        val preparationSeed = application.container.syncCoordinator
            .pendingBootstrapOperations(expected, limit = BOOTSTRAP_PREPARATION_SEED)
        if (preparationSeed.isNotEmpty()) {
            Log.i(TAG, "Floppy bootstrap preparing remote movie/episode state")
            setProgress(progressData(FloppyBootstrapProgress(FloppyBootstrapStage.CHECKING_REMOTE_STATE, processed, total, processed, failed, expected)))
            try {
                withTimeout(90_000) { transport.prepare(preparationSeed) }
            } catch (timeout: TimeoutCancellationException) {
                val error = TrackingSyncError.Timeout(timeout)
                setProgress(progressData(FloppyBootstrapProgress(FloppyBootstrapStage.WAITING_FOR_SERVER, processed, total, processed, failed, expected)))
                return terminalResult(error)
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
                        Log.w(TAG, "Floppy bootstrap no progress for 60s: operation=${current?.currentOperationType}")
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
                    val fetchedBatch = application.container.syncCoordinator
                        .pendingBootstrapOperations(expected, BOOTSTRAP_FETCH_BATCH)
                    if (fetchedBatch.isEmpty()) break
                    // Movie and episode history are each indexed once, on
                    // the first focused window that contains that media type.
                    if ((!transport.context.movieHistoryLoaded && fetchedBatch.any { it.mediaType == com.cinetrack.domain.MediaType.MOVIE }) ||
                        (!transport.context.episodeHistoryLoaded && fetchedBatch.any { it.type == SyncOperationType.EPISODE_WATCHED })) {
                        transport.prepare(fetchedBatch)
                    }
                    val movieWave = fetchedBatch.bootstrapMovieWave(MAX_CONCURRENT_MOVIE_UNITS)
                    if (movieWave.isNotEmpty()) {
                        val outcomes = application.container.syncCoordinator.withFloppyBootstrapLane(expected) {
                            supervisorScope {
                                movieWave.map { unit ->
                                    async {
                                        unit to runCatching { withTimeout(90_000) { transport.push(unit) } }
                                    }
                                }.awaitAll()
                            }
                        }
                        var waveFailure: Throwable? = null
                        outcomes.forEach { (unit, outcome) ->
                            val error = outcome.exceptionOrNull()
                            application.container.syncCoordinator.settleFloppyBootstrapUnit(expected, unit, error)
                            if (error != null && waveFailure == null) waveFailure = error
                        }
                        val remaining = application.container.syncCoordinator.pendingBootstrapCount(expected)
                        processed = (total - remaining).coerceIn(processed, total)
                        lastProgressAt = System.currentTimeMillis()
                        if (waveFailure != null) {
                            failed += movieWave.firstOrNull { unit -> outcomes.firstOrNull { it.first == unit }?.second?.isFailure == true }?.size ?: 0
                            loopError = waveFailure
                            break
                        }
                        continue
                    }
                    // A transport unit is the durable acknowledgement boundary.
                    // Earlier bulk-task successes are committed before an
                    // unrelated later unit can fail.
                    val batch = fetchedBatch.bootstrapTransportUnit()
                    val first = batch.first()
                    Log.i(TAG, "Floppy bootstrap batch started: size=\${batch.size} type=\${first.type} media=\${first.mediaType}:\${first.mediaId}")
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
                                batch.mapTo(linkedSetOf()) { it.id },
                                expected,
                                transport = transport::push,
                            )
                        }
                    } catch (timeout: TimeoutCancellationException) {
                        SyncResult.failure(TrackingSyncError.Timeout(timeout))
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
                        failed += batch.size
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
                    val remaining = application.container.syncCoordinator.pendingBootstrapCount(expected)
                    val nextProcessed = (total - remaining).coerceIn(processed, total)
                    processed = nextProcessed
                    lastProgressAt = System.currentTimeMillis()
                    stalledPublished = false
                    current = FloppyBootstrapProgress(FloppyBootstrapStage.SYNCING, processed, total, processed, failed, expected, first.type.name, first.title.takeIf(String::isNotBlank), lastProgressAt)
                    Log.i(TAG, "Floppy bootstrap batch complete: \${processed}/\${total}")
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
        Log.i(TAG, "Floppy bootstrap verifying")
        val ready = coordinator.markReadyIfComplete()
        if (!ready) {
            application.container.preferences.setProviderBootstrapState(TrackingProviderId.FLOPPY, ProviderBootstrapState.FAILED)
            setProgress(progressData(FloppyBootstrapProgress(FloppyBootstrapStage.NEEDS_ATTENTION, processed, total, processed, failed, expected)))
            return Result.failure(progressData(FloppyBootstrapProgress(FloppyBootstrapStage.NEEDS_ATTENTION, processed, total, processed, failed, expected)))
        }
        setProgress(progressData(FloppyBootstrapProgress(FloppyBootstrapStage.COMPLETE, total, total, total, failed, expected)))
        Log.i(TAG, "Floppy bootstrap READY: $total/$total")
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

    private fun progressData(progress: FloppyBootstrapProgress): Data {
        // Every WorkInfo update carries the immutable execution identity from
        // input. This keeps retries of one request correlated while ensuring
        // historical executions cannot be rendered as the current run.
        val current = if (progress.bootstrapRunId == null) {
            progress.copy(bootstrapRunId = inputData.getString(BOOTSTRAP_RUN_ID))
        } else progress
        return Data.Builder()
            .putString("stage", current.stage.name)
            .putInt("processed", current.processed)
            .putInt("total", current.total)
            .putInt("succeeded", current.succeeded)
            .putInt("failed", current.failed)
            .apply {
                current.providerInstanceId?.let { putString("providerInstanceId", it) }
                current.bootstrapRunId?.let { putString("bootstrapRunId", it) }
                inputData.getLong(BOOTSTRAP_SCHEDULED_AT, 0L).takeIf { it > 0L }?.let {
                    putLong(BOOTSTRAP_SCHEDULED_AT, it)
                }
                current.currentOperationType?.let { putString("currentOperationType", it) }
                current.currentTitle?.let { putString("currentTitle", it) }
                current.lastProgressAtMillis?.let { putLong("lastProgressAt", it) }
                putInt("planningProcessed", current.planningProcessed)
                putInt("planningTotal", current.planningTotal)
            }
            .build()
    }

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
        const val BOOTSTRAP_RUN_ID = "bootstrapRunId"
        const val BOOTSTRAP_SCHEDULED_AT = "bootstrapScheduledAt"
        private const val BOOTSTRAP_FETCH_BATCH = 200
        internal const val MAX_CONCURRENT_MOVIE_UNITS = 4
        private const val BOOTSTRAP_PREPARATION_SEED = 200
        private const val MAX_RETRIES = 5
        private const val WATCHDOG_POLL_MS = 1_000L
        private const val NO_PROGRESS_TIMEOUT_MS = 60_000L
        private const val CHANNEL_ID = "cinetrack_background_sync"
        private const val NOTIFICATION_ID = 6011
        private const val TAG = "FloppyBootstrap"
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
        bootstrapRunId = data.getString("bootstrapRunId"),
        currentOperationType = data.getString("currentOperationType"),
        currentTitle = data.getString("currentTitle"),
        lastProgressAtMillis = data.getLong("lastProgressAt", 0L).takeIf { it > 0L },
        planningProcessed = data.getInt("planningProcessed", 0),
        planningTotal = data.getInt("planningTotal", 0),
    )
}

/** Keeps a completed-movie pair together while allowing every other
 * bootstrap operation to advance progress independently. */
/** One independently-confirmable Floppy bootstrap request. Episode units are
 * same-show, same-season, gap-free ranges capped at the server payload limit.
 * Other operations preserve the existing completed-movie pair invariant. */
/** Plans at most one ordered logical unit for each distinct movie. */
internal fun List<SyncOperation>.bootstrapMovieWave(maxUnits: Int): List<List<SyncOperation>> =
    filter { it.mediaType == com.cinetrack.domain.MediaType.MOVIE }
        .groupBy(SyncOperation::mediaId)
        .toSortedMap()
        .values
        .map { it.bootstrapLogicalUnit() }
        .take(maxUnits)

internal fun List<SyncOperation>.bootstrapTransportUnit(): List<SyncOperation> {
    if (isEmpty()) return emptyList()
    val episode = filter { it.type == SyncOperationType.EPISODE_WATCHED }
        .sortedWith(compareBy<SyncOperation> { it.mediaId }
            .thenBy { it.episodePartsForBootstrap().first }
            .thenBy { it.episodePartsForBootstrap().second })
        .firstOrNull()
    if (episode == null) return bootstrapLogicalUnit()
    val (season, firstEpisode) = episode.episodePartsForBootstrap()
    return filter { operation ->
        val parts = operation.episodePartsForBootstrap()
        operation.type == SyncOperationType.EPISODE_WATCHED &&
            operation.mediaId == episode.mediaId &&
            parts.first == season
    }
        .sortedBy { it.episodePartsForBootstrap().second }
        .takeWhileIndexed { index, operation ->
            index < 50 && operation.episodePartsForBootstrap().second == firstEpisode + index
        }
}

private inline fun <T> List<T>.takeWhileIndexed(predicate: (Int, T) -> Boolean): List<T> {
    val result = ArrayList<T>()
    forEachIndexed { index, value ->
        if (!predicate(index, value)) return result
        result += value
    }
    return result
}

private fun SyncOperation.episodePartsForBootstrap(): Pair<Int, Int> {
    val parts = payload?.split(':', limit = 3).orEmpty()
    return (parts.getOrNull(0)?.toIntOrNull() ?: -1) to (parts.getOrNull(1)?.toIntOrNull() ?: -1)
}

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

