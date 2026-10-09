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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.io.IOException
import retrofit2.HttpException
import kotlin.Result as SyncResult
import java.util.concurrent.TimeUnit

internal fun isFloppyBootstrapRetryable(error: Throwable): Boolean = when (error) {
    is TrackingSyncError.NetworkUnavailable,
    is TrackingSyncError.DnsFailure,
    is TrackingSyncError.Timeout,
    is TrackingSyncError.RateLimited,
    is IOException,
    -> true
    is TrackingSyncError.ProviderUnavailable ->
        (error.cause as? HttpException)?.code() in 500..599
    else -> false
}

/** One-line, bounded error description for logs; never a response body. */
internal fun Throwable.safeSummary(): String {
    // Release builds shorten class names, so app errors are described by their
    // message (plus the HTTP status when there is one) rather than their type.
    val text = message?.replace(Regex("\\s+"), " ")?.trim()?.take(200)
    val http = (cause as? retrofit2.HttpException)?.code()?.let { " (HTTP $it)" }.orEmpty()
    return if (this is TrackingSyncError) {
        (text ?: "Sync error") + http
    } else {
        this::class.java.simpleName + text?.let { ": $it" }.orEmpty() + http
    }
}

/**
 * A V2 request rejected for its own content (unknown media, missing season
 * metadata, a conflicting event) cannot succeed on retry. Its operations stay
 * unresolved while the run continues with the rest of the plan, unless the
 * failures repeat back to back, which points at the server rather than items.
 */
internal fun shouldSkipFloppyBootstrapUnit(
    error: Throwable,
    canBootstrapV2: Boolean,
    consecutiveSkipped: Int,
): Boolean = canBootstrapV2 &&
    consecutiveSkipped < MAX_CONSECUTIVE_SKIPPED_UNITS &&
    when (error) {
        is TrackingSyncError.WrongApi,
        is TrackingSyncError.Validation,
        is TrackingSyncError.Conflict,
        is TrackingSyncError.InvalidRemoteData,
        -> true
        else -> false
    }

// Skipped units are only remembered within one run, so a low limit let a few
// rejected units at the head of the queue stop every Retry before any progress.
internal const val MAX_CONSECUTIVE_SKIPPED_UNITS = 25

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

    /** Continues the bootstrap in a fresh execution after the current one ends.
     * Used when a run made progress before a transient failure, so a long
     * sync does not exhaust WorkManager's per-request retry budget. */
    fun continueLater(context: Context, connectionId: String, wifiOnly: Boolean, delaySeconds: Long) {
        if (connectionId.isBlank()) return
        val request = OneTimeWorkRequestBuilder<FloppyBootstrapWorker>()
            .setInputData(workDataOf(
                FloppyBootstrapWorker.EXPECTED_CONNECTION_ID to connectionId,
                FloppyBootstrapWorker.BOOTSTRAP_RUN_ID to java.util.UUID.randomUUID().toString(),
                FloppyBootstrapWorker.BOOTSTRAP_SCHEDULED_AT to System.currentTimeMillis(),
            ))
            .addTag("floppy-bootstrap")
            .addTag("floppy-bootstrap:$connectionId")
            .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        // Appended: starts only after the current execution has finished.
        WorkManager.getInstance(context).enqueueUniqueWork(uniqueWorkName(connectionId), ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    /** Explicit user retry for a stalled/failed job. The persisted plan and
     * operation generations are untouched; only the WorkManager execution is
     * replaced. */
    fun retryNow(context: Context, connectionId: String, wifiOnly: Boolean = false) {
        if (connectionId.isBlank()) return
        val runId = java.util.UUID.randomUUID().toString()
        val request = OneTimeWorkRequestBuilder<FloppyBootstrapWorker>()
            .setInputData(workDataOf(
                FloppyBootstrapWorker.EXPECTED_CONNECTION_ID to connectionId,
                FloppyBootstrapWorker.BOOTSTRAP_RUN_ID to runId,
                FloppyBootstrapWorker.BOOTSTRAP_SCHEDULED_AT to System.currentTimeMillis(),
            ))
            .addTag("floppy-bootstrap")
            .addTag("floppy-bootstrap:$connectionId")
            .setConstraints(Constraints.Builder().setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        // REPLACE is atomic at the unique-work boundary. cancel + KEEP races
        // with WorkManager's asynchronous cancellation and can reuse stale work.
        WorkManager.getInstance(context).enqueueUniqueWork(uniqueWorkName(connectionId), ExistingWorkPolicy.REPLACE, request)
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

    val progress: Flow<FloppyBootstrapProgress?> = allProgress.map { infos -> infos.selectFloppyWork()?.toFloppyProgress() }

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

        // Probe before any fresh canonical snapshot/plan materialization. For an
        // interrupted run, coordinator.progress reads the existing immutable plan
        // and preserves its durable processed count without regenerating it.
        val beforePlan = coordinator.progress(expected)
        val runId = inputData.getString(BOOTSTRAP_RUN_ID).orEmpty()
        report("Floppy bootstrap preflight: run=$runId connection=$expected stage=AUTH_PROBE processed=${beforePlan.processed} total=${beforePlan.total}")
        setProgress(progressData(beforePlan.copy(stage = FloppyBootstrapStage.PREPARING)))
        val provider = application.container.trackingProviderRegistry.getProvider(TrackingProviderId.FLOPPY)
        val preflight = provider?.let {
            application.container.syncCoordinator.withProviderIoQuiesced { it.testConnection() }
        }
        when (val connection = preflight) {
            ConnectionResult.Connected -> {
                // A successful probe may discover that the token now belongs to
                // another account. The provider then rotates connectionId; never
                // deliver this old plan to that new provider instance.
                if (!isCurrent(application, expected)) {
                    report(warn = true, message = "Floppy bootstrap stopped after probe: provider instance changed")
                    return Result.success()
                }
            }
            ConnectionResult.AuthenticationRequired -> {
                application.container.preferences.setProviderBootstrapState(TrackingProviderId.FLOPPY, ProviderBootstrapState.FAILED)
                val failure = beforePlan.copy(stage = FloppyBootstrapStage.NEEDS_ATTENTION)
                setProgress(progressData(failure))
                report(warn = true, message = "Floppy bootstrap preflight failed: run=$runId connection=$expected stage=AUTH_PROBE reason=AUTH_REQUIRED")
                return Result.failure(progressData(failure))
            }
            is ConnectionResult.Failed -> {
                if (!isCurrent(application, expected)) return Result.success()
                val waitingStage = if (isFloppyBootstrapRetryable(connection.error)) {
                    FloppyBootstrapStage.WAITING_FOR_SERVER
                } else {
                    FloppyBootstrapStage.NEEDS_ATTENTION
                }
                val failure = beforePlan.copy(stage = waitingStage)
                setProgress(progressData(failure))
                report(warn = true, message = "Floppy bootstrap preflight failed: run=$runId connection=$expected stage=AUTH_PROBE error=${connection.error.safeSummary()}")
                return terminalResult(connection.error, progressData(failure))
            }
            null -> {
                val error = TrackingSyncError.ProviderUnavailable(TrackingProviderId.FLOPPY)
                val waiting = beforePlan.copy(stage = FloppyBootstrapStage.WAITING_FOR_SERVER)
                setProgress(progressData(waiting))
                return terminalResult(error, progressData(waiting))
            }
        }

        val initialPlan = try {
            setProgress(progressData(beforePlan.copy(stage = FloppyBootstrapStage.BUILDING_PLAN)))
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
        report("Floppy bootstrap started: run=$runId connection=$expected planTotal=$total pending=${application.container.syncCoordinator.pendingBootstrapCount(expected)}")
        if (total > 100) setForeground(createForegroundInfo(initialPlan.copy(total = total)))
        var processed = (total - application.container.syncCoordinator.pendingBootstrapCount(expected)).coerceIn(0, total)
        var failed = 0

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

        var lastProgressAt = System.currentTimeMillis()
        var stalledPublished = false
        var current: FloppyBootstrapProgress? = null
        var stoppedForInstanceChange = false
        var loopError: Throwable? = null
        // Unresolved operations from item-level V2 failures; excluded for the
        // rest of this run only. A later run retries them first.
        val skipped = linkedSetOf<String>()
        var consecutiveSkipped = 0
        var completedBatches = 0
        coroutineScope {
            val watchdog = launch {
                while (isActive) {
                    delay(WATCHDOG_POLL_MS)
                    if (!stalledPublished && current?.stage in setOf(
                            FloppyBootstrapStage.CHECKING_REMOTE_STATE,
                            FloppyBootstrapStage.SYNCING,
                        ) &&
                        System.currentTimeMillis() - lastProgressAt >= NO_PROGRESS_TIMEOUT_MS
                    ) {
                        stalledPublished = true
                        val stalled = current!!.copy(stage = FloppyBootstrapStage.STALLED, lastProgressAtMillis = lastProgressAt)
                        report(warn = true, message = "Floppy bootstrap no progress for 60s: run=$runId connection=$expected operation=${current?.currentOperationType}")
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
                        .pendingBootstrapOperations(expected, BOOTSTRAP_FETCH_BATCH + skipped.size)
                        .filterNot { it.id in skipped }
                    if (fetchedBatch.isEmpty()) break
                    val movieWave = fetchedBatch.bootstrapMovieWave(
                        if (transport.context.canBootstrapV2) BOOTSTRAP_V2_MAX else MAX_CONCURRENT_MOVIE_UNITS,
                    )
                    if (movieWave.isNotEmpty()) {
                        if (!transport.context.canBootstrapV2 && !transport.context.movieHistoryLoaded && !transport.context.moviePreparationUnavailable) {
                            current = FloppyBootstrapProgress(
                                FloppyBootstrapStage.CHECKING_REMOTE_STATE,
                                processed, total, processed, failed, expected,
                                currentOperationType = "MOVIE_STATE",
                                lastProgressAtMillis = lastProgressAt,
                            )
                            setProgress(progressData(current!!))
                            try {
                                transport.prepare(movieWave.flatten()) { loaded, remoteTotal ->
                                    lastProgressAt = System.currentTimeMillis()
                                    stalledPublished = false
                                    current = current?.copy(
                                        planningProcessed = loaded,
                                        planningTotal = remoteTotal,
                                        lastProgressAtMillis = lastProgressAt,
                                    )
                                    current?.let { setProgress(progressData(it)) }
                                }
                                lastProgressAt = System.currentTimeMillis()
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (error: Throwable) {
                                val retryable = isRetryable(error)
                                current = current?.copy(
                                    stage = if (retryable) FloppyBootstrapStage.WAITING_FOR_SERVER else FloppyBootstrapStage.NEEDS_ATTENTION,
                                    lastProgressAtMillis = lastProgressAt,
                                )
                                current?.let { setProgress(progressData(it)) }
                                loopError = error
                                break
                            }
                        }
                        current = FloppyBootstrapProgress(
                            FloppyBootstrapStage.SYNCING, processed, total, processed, failed, expected,
                            currentOperationType = movieWave.first().firstOrNull()?.type?.name,
                            lastProgressAtMillis = lastProgressAt,
                        )
                        setProgress(progressData(current!!))

                        if (transport.context.canBootstrapV2) {
                            val modernBatch = movieWave.flatten()
                            val result: SyncResult<Unit> = try {
                                withTimeout(90_000) {
                                    application.container.syncCoordinator.pushPendingForProvider(
                                        TrackingProviderId.FLOPPY,
                                        modernBatch.mapTo(linkedSetOf()) { it.id },
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
                                val error = result.exceptionOrNull() ?: IllegalStateException("Floppy V2 movie batch failed")
                                failed += modernBatch.size
                                if (shouldSkipFloppyBootstrapUnit(error, true, consecutiveSkipped)) {
                                    skipped += modernBatch.map(SyncOperation::id)
                                    consecutiveSkipped++
                                    report(warn = true, message = "Floppy bootstrap unit skipped: run=$runId connection=$expected route=MOVIE_BOOTSTRAP_V2 size=${modernBatch.size} error=${error.safeSummary()}")
                                    continue
                                }
                                loopError = error
                                break
                            }
                            consecutiveSkipped = 0
                        } else {
                            val outcomes = application.container.syncCoordinator.withFloppyBootstrapLane(expected) {
                                supervisorScope {
                                    movieWave.map { unit ->
                                        async {
                                            unit to try {
                                                SyncResult.success(withTimeout(90_000) { transport.push(unit) })
                                            } catch (cancelled: CancellationException) {
                                                throw cancelled
                                            } catch (error: Throwable) {
                                                SyncResult.failure(error)
                                            }
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
                            if (waveFailure != null) {
                                failed += movieWave.firstOrNull { unit -> outcomes.firstOrNull { it.first == unit }?.second?.isFailure == true }?.size ?: 0
                                loopError = waveFailure
                                break
                            }
                        }

                        val remaining = application.container.syncCoordinator.pendingBootstrapCount(expected)
                        processed = (total - remaining).coerceIn(processed, total)
                        lastProgressAt = System.currentTimeMillis()
                        stalledPublished = false
                        current = current?.copy(processed = processed, succeeded = processed, lastProgressAtMillis = lastProgressAt)
                        current?.let { setProgress(progressData(it)) }
                        continue
                    }

                    val windowUnit = fetchedBatch.bootstrapTransportUnit(
                        canEnsureEpisodeEvents = transport.canEnsureEpisodeEvents,
                        canBootstrapV2 = transport.context.canBootstrapV2,
                    )
                    // Plan order follows watch history, so one fetch window holds
                    // only a few episodes per show. Explicit-event requests may
                    // carry any of the show's episodes, so fill the request from
                    // all of that show's unresolved episodes instead.
                    val batch = if (transport.canEnsureEpisodeEvents && windowUnit.first().type == SyncOperationType.EPISODE_WATCHED) {
                        application.container.syncCoordinator
                            .pendingBootstrapShowEpisodes(expected, windowUnit.first().mediaId)
                            .filterNot { it.id in skipped }
                            .bootstrapTransportUnit(canEnsureEpisodeEvents = true, canBootstrapV2 = transport.context.canBootstrapV2)
                            .ifEmpty { windowUnit }
                    } else windowUnit
                    val first = batch.first()
                    if (first.type == SyncOperationType.EPISODE_WATCHED &&
                        !transport.canEnsureEpisodeEvents && !transport.context.episodeHistoryLoaded) {
                        current = FloppyBootstrapProgress(
                            FloppyBootstrapStage.CHECKING_REMOTE_STATE,
                            processed, total, processed, failed, expected,
                            currentOperationType = "EPISODE_STATE",
                            lastProgressAtMillis = lastProgressAt,
                        )
                        setProgress(progressData(current!!))
                        try {
                            transport.prepare(batch) { loaded, remoteTotal ->
                                lastProgressAt = System.currentTimeMillis()
                                stalledPublished = false
                                current = current?.copy(
                                    planningProcessed = loaded,
                                    planningTotal = remoteTotal,
                                    lastProgressAtMillis = lastProgressAt,
                                )
                                current?.let { setProgress(progressData(it)) }
                            }
                            lastProgressAt = System.currentTimeMillis()
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Throwable) {
                            val retryable = isRetryable(error)
                            current = current?.copy(
                                stage = if (retryable) FloppyBootstrapStage.WAITING_FOR_SERVER else FloppyBootstrapStage.NEEDS_ATTENTION,
                                lastProgressAtMillis = lastProgressAt,
                            )
                            current?.let { setProgress(progressData(it)) }
                            loopError = error
                            break
                        }
                    }
                    Log.i(TAG, "Floppy bootstrap batch started: run=$runId connection=$expected size=${batch.size} type=${first.type} media=${first.mediaType}:${first.mediaId}")
                    val batchStartedAt = System.nanoTime()
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
                        if (error is TrackingSyncError.PartiallyRejected) {
                            // The rest of the request was applied and acknowledged;
                            // only the refused items stay unresolved for this run.
                            failed += error.rejected.size
                            skipped += error.rejected.keys
                            if (error.rejected.size < batch.size) consecutiveSkipped = 0 else consecutiveSkipped++
                            error.rejected.values.forEach { reason ->
                                report(warn = true, message = "Floppy bootstrap item not accepted: run=$runId $reason")
                            }
                            continue
                        }
                        failed += batch.size
                        if (shouldSkipFloppyBootstrapUnit(error, transport.context.canBootstrapV2, consecutiveSkipped)) {
                            skipped += batch.map(SyncOperation::id)
                            consecutiveSkipped++
                            val route = if (first.type == SyncOperationType.EPISODE_WATCHED) "EPISODE_ENSURE" else "SHOW_BOOTSTRAP_V2"
                            report(warn = true, message = "Floppy bootstrap unit skipped: run=$runId connection=$expected route=$route size=${batch.size} media=${first.mediaType}:${first.mediaId} error=${error.safeSummary()}")
                            continue
                        }
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
                    processed = (total - remaining).coerceIn(processed, total)
                    lastProgressAt = System.currentTimeMillis()
                    stalledPublished = false
                    current = FloppyBootstrapProgress(FloppyBootstrapStage.SYNCING, processed, total, processed, failed, expected, first.type.name, first.title.takeIf(String::isNotBlank), lastProgressAt)
                    consecutiveSkipped = 0
                    val batchMs = (System.nanoTime() - batchStartedAt) / 1_000_000
                    completedBatches++
                    val batchLine = "Floppy bootstrap batch complete: run=$runId size=${batch.size} type=${first.type} media=${first.mediaType}:${first.mediaId} durationMs=$batchMs processed=$processed total=$total"
                    // The in-app log is capped, so only slow batches and a periodic
                    // summary go there; every batch still reaches logcat.
                    if (batchMs >= SLOW_BATCH_MS || completedBatches % BATCH_LOG_EVERY == 0) report(batchLine) else Log.i(TAG, batchLine)
                    setProgress(progressData(current!!))
                    if (total > 100) setForeground(createForegroundInfo(current!!))
                }
            } finally {
                watchdog.cancel()
            }
        }
        if (stoppedForInstanceChange) {
            report(warn = true, message = "Floppy bootstrap stopped: run=$runId provider instance changed")
            return Result.success()
        }
        loopError?.let {
            report(warn = true, message = "Floppy bootstrap stopped: run=$runId processed=$processed total=$total error=${it.safeSummary()}")
            // WorkManager's retry count spans the whole multi-hour sync. A run
            // that made progress hands off to a fresh execution instead, so
            // only failures without any progress use up automatic retries.
            if (isRetryable(it) && processed > beforePlan.processed) {
                FloppyBootstrapWorkScheduler.continueLater(
                    applicationContext,
                    expected,
                    application.container.preferences.wifiOnly.first(),
                    CONTINUE_AFTER_PROGRESS_SECONDS,
                )
                report("Floppy bootstrap continues in ${CONTINUE_AFTER_PROGRESS_SECONDS}s after progress: run=$runId processed ${beforePlan.processed} -> $processed of $total")
                val waiting = FloppyBootstrapProgress(FloppyBootstrapStage.WAITING_FOR_SERVER, processed, total, processed, failed, expected)
                setProgress(progressData(waiting))
                return Result.success(progressData(waiting))
            }
            return terminalResult(it)
        }
        if (!isCurrent(application, expected)) return Result.success()
        if (skipped.isNotEmpty()) {
            // Everything deliverable was delivered; the skipped operations stay
            // unresolved (never ACKed) and are retried first by the next run.
            application.container.preferences.setProviderBootstrapState(TrackingProviderId.FLOPPY, ProviderBootstrapState.FAILED)
            val attention = FloppyBootstrapProgress(FloppyBootstrapStage.NEEDS_ATTENTION, processed, total, processed, skipped.size, expected)
            setProgress(progressData(attention))
            report(warn = true, message = "Floppy bootstrap finished with skipped units: run=$runId connection=$expected skipped=${skipped.size} processed=$processed total=$total")
            return Result.failure(progressData(attention))
        }
        setProgress(progressData(FloppyBootstrapProgress(FloppyBootstrapStage.VERIFYING, processed, total, processed, failed, expected)))
        Log.i(TAG, "Floppy bootstrap verifying: run=$runId connection=$expected")
        val ready = coordinator.markReadyIfComplete()
        if (!ready) {
            application.container.preferences.setProviderBootstrapState(TrackingProviderId.FLOPPY, ProviderBootstrapState.FAILED)
            setProgress(progressData(FloppyBootstrapProgress(FloppyBootstrapStage.NEEDS_ATTENTION, processed, total, processed, failed, expected)))
            return Result.failure(progressData(FloppyBootstrapProgress(FloppyBootstrapStage.NEEDS_ATTENTION, processed, total, processed, failed, expected)))
        }
        setProgress(progressData(FloppyBootstrapProgress(FloppyBootstrapStage.COMPLETE, total, total, total, failed, expected)))
        report("Floppy bootstrap READY: run=$runId connection=$expected $total/$total")
        return Result.success(progressData(FloppyBootstrapProgress(FloppyBootstrapStage.COMPLETE, total, total, total, failed, expected)))
    }

    private suspend fun isCurrent(application: CineTrackApplication, expected: String): Boolean =
        application.container.preferences.floppySettingsNow()?.connectionId == expected

    private suspend fun terminalResult(error: Throwable, failureData: Data? = null): Result {
        val retryable = isFloppyBootstrapRetryable(error)
        report(
            warn = true,
            message = when {
                retryable && runAttemptCount < MAX_RETRIES ->
                    "Floppy bootstrap will retry automatically: attempt ${runAttemptCount + 1}/$MAX_RETRIES error=${error.safeSummary()}"
                retryable -> "Floppy bootstrap gave up after $MAX_RETRIES automatic retries: error=${error.safeSummary()}"
                else -> "Floppy bootstrap needs attention (not retried automatically): error=${error.safeSummary()}"
            },
        )
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

    /** Logcat plus the in-app error log (Settings), so sync problems can be
     * read on the device. Messages carry ids, counts and error summaries only. */
    private fun report(message: String, warn: Boolean = false) {
        if (warn) Log.w(TAG, message) else Log.i(TAG, message)
        (applicationContext as? CineTrackApplication)?.container?.preferences
            ?.appendErrorLog("${java.time.Instant.now()}  $message")
    }

    private fun progressData(progress: FloppyBootstrapProgress): Data {
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
        internal const val BOOTSTRAP_V2_MAX = 50
        private const val MAX_RETRIES = 5
        private const val WATCHDOG_POLL_MS = 1_000L
        private const val NO_PROGRESS_TIMEOUT_MS = 60_000L
        private const val SLOW_BATCH_MS = 20_000L
        private const val CONTINUE_AFTER_PROGRESS_SECONDS = 30L
        private const val BATCH_LOG_EVERY = 10
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

/** Plans at most one ordered logical unit for each distinct movie. */
internal fun List<SyncOperation>.bootstrapMovieWave(maxUnits: Int): List<List<SyncOperation>> =
    filter { it.mediaType == com.cinetrack.domain.MediaType.MOVIE }
        .groupBy(SyncOperation::mediaId)
        .toSortedMap()
        .values
        .map { it.bootstrapLogicalUnit() }
        .take(maxUnits)

/**
 * One independently-confirmable Floppy request. Modern explicit-event
 * endpoints do not imply omitted coordinates, so their batches may span gaps
 * and seasons. Legacy range transport remains same-show/same-season/contiguous.
 */
internal fun List<SyncOperation>.bootstrapTransportUnit(
    canEnsureEpisodeEvents: Boolean = false,
    canBootstrapV2: Boolean = false,
): List<SyncOperation> {
    if (isEmpty()) return emptyList()
    val episode = filter { it.type == SyncOperationType.EPISODE_WATCHED }
        .sortedWith(compareBy<SyncOperation> { it.mediaId }
            .thenBy { it.episodePartsForBootstrap().first }
            .thenBy { it.episodePartsForBootstrap().second })
        .firstOrNull()
    if (episode != null) {
        if (canEnsureEpisodeEvents) {
            val showEpisodes = filter {
                it.type == SyncOperationType.EPISODE_WATCHED && it.mediaId == episode.mediaId
            }
            // Specials travel in their own request: servers that predate
            // season-0 support reject the whole request, which must not take
            // the show's regular episodes down with it.
            return showEpisodes.filter { it.episodePartsForBootstrap().first != 0 }
                .ifEmpty { showEpisodes }
                .sortedWith(compareBy<SyncOperation> { it.episodePartsForBootstrap().first }
                    .thenBy { it.episodePartsForBootstrap().second })
                .take(50)
        }
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
    if (canBootstrapV2) {
        val show = firstOrNull {
            it.mediaType == com.cinetrack.domain.MediaType.TV && it.type == SyncOperationType.LIBRARY_STATUS
        }
        if (show != null) {
            return filter {
                it.mediaType == com.cinetrack.domain.MediaType.TV && it.type == SyncOperationType.LIBRARY_STATUS
            }
                .distinctBy(SyncOperation::mediaId)
                .take(50)
        }
    }
    return bootstrapLogicalUnit()
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
