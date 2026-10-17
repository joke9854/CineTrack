package com.cinetrack.data.work

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
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.cinetrack.CineTrackApplication
import com.cinetrack.R
import com.cinetrack.domain.LibraryArtworkRefreshProgress
import com.cinetrack.domain.LibraryArtworkRefreshStage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.io.IOException
import java.util.concurrent.TimeUnit

object LibraryArtworkRefreshScheduler {
    const val UNIQUE_WORK_NAME = "library-artwork-refresh"

    fun enqueue(context: Context, wifiOnly: Boolean = false) {
        val request = OneTimeWorkRequestBuilder<LibraryArtworkRefreshWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }
}

class LibraryArtworkRefreshManager(context: Context) {
    private val appContext = context.applicationContext
    private val workManager = WorkManager.getInstance(appContext)
    val progress: Flow<LibraryArtworkRefreshProgress?> = workManager
        .getWorkInfosForUniqueWorkFlow(LibraryArtworkRefreshScheduler.UNIQUE_WORK_NAME)
        .map { infos ->
            infos.firstOrNull { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
                ?.toArtworkProgress()
                ?: infos.firstOrNull()?.toArtworkProgress()
        }

    fun enqueue(wifiOnly: Boolean = false) = LibraryArtworkRefreshScheduler.enqueue(appContext, wifiOnly)
}

class LibraryArtworkRefreshWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val application = applicationContext as CineTrackApplication
        application.container.repository.awaitStartup()
        return try {
            val result = kotlinx.coroutines.withTimeoutOrNull(MAX_JOB_MILLIS) {
                application.container.repository.refreshLibraryArtwork { progress ->
                    setProgress(progress.toData())
                    if (progress.total > 100 && progress.stage == LibraryArtworkRefreshStage.REFRESHING) {
                        setForeground(createForegroundInfo(progress))
                    }
                }
            } ?: return if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure(workDataOf("error" to "Artwork refresh exceeded its safety window"))
            val final = result.copy(stage = if (result.failed == 0) LibraryArtworkRefreshStage.COMPLETE else LibraryArtworkRefreshStage.COMPLETE_WITH_ERRORS)
            setProgress(final.toData())
            Result.success(final.toData())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: IllegalStateException) {
            // Missing credentials are permanent and need an actionable settings error.
            setProgress(LibraryArtworkRefreshProgress(LibraryArtworkRefreshStage.FAILED).toData())
            Result.failure(workDataOf("error" to (error.message ?: "TMDB refresh failed")))
        } catch (error: IOException) {
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure(workDataOf("error" to "Network unavailable"))
        } catch (error: Throwable) {
            setProgress(LibraryArtworkRefreshProgress(LibraryArtworkRefreshStage.FAILED).toData())
            Result.failure(workDataOf("error" to (error.message ?: "TMDB refresh failed")))
        }
    }

    private fun createForegroundInfo(progress: LibraryArtworkRefreshProgress): androidx.work.ForegroundInfo {
        ensureChannel()
        val text = if (progress.total > 0) "${progress.processed} of ${progress.total}" else "Preparing…"
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(applicationContext.getString(R.string.app_name))
            .setContentText("Refreshing library artwork · $text")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(progress.total, progress.processed, progress.total == 0)
            .build()
        return if (Build.VERSION.SDK_INT >= 29) {
            androidx.work.ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else androidx.work.ForegroundInfo(NOTIFICATION_ID, notification)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            applicationContext.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "CineTrack background sync", NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    private companion object {
        const val CHANNEL_ID = "cinetrack_background_sync"
        const val NOTIFICATION_ID = 6012
        const val MAX_RETRIES = 5
        const val MAX_JOB_MILLIS = 25L * 60L * 1000L
    }
}

private fun LibraryArtworkRefreshProgress.toData(): Data = workDataOf(
    "stage" to stage.name,
    "processed" to processed,
    "total" to total,
    "changed" to changed,
    "unchanged" to unchanged,
    "failed" to failed,
    "currentTitle" to currentTitle,
)

private fun WorkInfo.toArtworkProgress(): LibraryArtworkRefreshProgress {
    val data = if (state == WorkInfo.State.SUCCEEDED || state == WorkInfo.State.FAILED) outputData else progress
    val stage = runCatching { LibraryArtworkRefreshStage.valueOf(data.getString("stage").orEmpty()) }.getOrElse {
        when (state) {
            WorkInfo.State.RUNNING -> LibraryArtworkRefreshStage.REFRESHING
            WorkInfo.State.FAILED -> LibraryArtworkRefreshStage.FAILED
            WorkInfo.State.SUCCEEDED -> LibraryArtworkRefreshStage.COMPLETE
            else -> LibraryArtworkRefreshStage.PREPARING
        }
    }
    return LibraryArtworkRefreshProgress(
        stage = stage,
        processed = data.getInt("processed", 0),
        total = data.getInt("total", 0),
        changed = data.getInt("changed", 0),
        unchanged = data.getInt("unchanged", 0),
        failed = data.getInt("failed", 0),
        currentTitle = data.getString("currentTitle"),
    )
}

