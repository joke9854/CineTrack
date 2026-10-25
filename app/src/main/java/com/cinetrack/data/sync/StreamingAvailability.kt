package com.cinetrack.data.sync

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.cinetrack.CineTrackApplication
import com.cinetrack.MainActivity
import com.cinetrack.R
import com.cinetrack.data.local.AppDatabase
import com.cinetrack.data.remote.TmdbService
import com.cinetrack.data.repository.AppPreferences
import com.cinetrack.data.repository.effectiveProviderRegion
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.TimeUnit

/** A Plan-to-watch title that just arrived on one or more of your streaming services. */
data class StreamingArrival(val mediaType: String, val mediaId: Int, val title: String, val providers: List<String>)

/**
 * Checks Plan-to-watch titles against your streaming services (Settings > Streaming services).
 * The first run only records what is already there; later runs report what is new.
 */
class StreamingAvailabilityChecker(
    private val tmdb: () -> TmdbService,
    private val tmdbConfigured: () -> Boolean,
    private val database: AppDatabase,
    private val preferences: AppPreferences,
) {
    suspend fun newArrivals(): List<StreamingArrival> {
        val preferred = preferences.preferredProviders.first()
        if (preferred.isEmpty() || !tmdbConfigured()) return emptyList()
        val region = preferences.effectiveProviderRegion()
        val planned = database.personalDao().plannedMedia(MAX_TITLES)
        val api = tmdb()
        val gate = Semaphore(4)
        val current: Map<String, Set<String>> = coroutineScope {
            planned.map { media ->
                async {
                    gate.withPermit {
                        val dto = runCatching {
                            if (media.mediaType == "TV") api.show(media.tmdbId, append = "watch/providers") else api.movie(media.tmdbId, append = "watch/providers")
                        }.getOrNull() ?: return@withPermit null
                        val offers = dto.watchProviders?.results?.get(region)
                        val names = offers?.let { (it.flatrate + it.free + it.ads).map { p -> p.name } }.orEmpty().filter { it in preferred }.toSet()
                        "${media.mediaType}:${media.tmdbId}" to names
                    }
                }
            }.mapNotNull { it.await() }.toMap()
        }
        val previous = preferences.streamingSeen()
        val baseline = preferences.streamingBaselineDone()
        val arrivals = streamingArrivals(previous, current)
        // Titles that failed to load keep what was known about them; titles no longer planned are dropped.
        val plannedKeys = planned.mapTo(mutableSetOf()) { "${it.mediaType}:${it.tmdbId}" }
        val kept = previous.filter { entry -> entry.substringBeforeLast(':').let { it in plannedKeys && it !in current.keys } }
        preferences.setStreamingSeen(kept.toSet() + current.flatMap { (key, names) -> names.map { "$key:$it" } })
        if (!baseline) {
            preferences.setStreamingBaselineDone()
            return emptyList()
        }
        val titles = planned.associate { "${it.mediaType}:${it.tmdbId}" to it.title }
        return arrivals.mapNotNull { (key, names) ->
            val type = key.substringBefore(':')
            val id = key.substringAfter(':').toIntOrNull() ?: return@mapNotNull null
            StreamingArrival(type, id, titles[key].orEmpty(), names.sorted())
        }
    }

    private companion object {
        const val MAX_TITLES = 120
    }
}

/** [previous] holds "TYPE:id:Provider"; returns each title's providers that weren't there before. */
internal fun streamingArrivals(previous: Set<String>, current: Map<String, Set<String>>): Map<String, Set<String>> =
    current.mapValues { (key, names) -> names.filterTo(mutableSetOf()) { "$key:$it" !in previous } }.filterValues { it.isNotEmpty() }

object StreamingAlerts {
    const val CHANNEL_ID = "cinetrack_streaming"
    private const val WORK_NAME = "streaming-availability"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, context.getString(R.string.streaming_notifications), NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
    }

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<StreamingAvailabilityWorker>(12, TimeUnit.HOURS)
            .setInitialDelay(1, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun notify(context: Context, arrival: StreamingArrival) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val deepLink = "cinetrack://detail/${arrival.mediaType}/${arrival.mediaId}"
        val text = context.getString(R.string.streaming_now_on, arrival.providers.joinToString(", "))
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_cinetrack)
            .setContentTitle(arrival.title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    "streaming:${arrival.mediaType}:${arrival.mediaId}".hashCode(),
                    Intent(Intent.ACTION_VIEW, Uri.parse(deepLink), context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        try {
            NotificationManagerCompat.from(context).notify("streaming:${arrival.mediaType}:${arrival.mediaId}".hashCode(), builder.build())
        } catch (_: SecurityException) {
            // Notifications were turned off between the check and the post.
        }
    }
}

class StreamingAvailabilityWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as CineTrackApplication).container
        val preferences = container.preferences
        if (!preferences.notificationSettings.first().streaming) return Result.success()
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return Result.success()
        }
        // During quiet hours nothing is checked, so nothing is lost: the next run reports it.
        if (ReleaseNotifier.quietHoursRemainingMillis(preferences) != null) return Result.success()
        val arrivals = runCatching { container.streamingAvailability.newArrivals() }.getOrElse { return Result.retry() }
        arrivals.take(MAX_NOTIFICATIONS).forEach { StreamingAlerts.notify(applicationContext, it) }
        return Result.success()
    }

    private companion object {
        const val MAX_NOTIFICATIONS = 5
    }
}
