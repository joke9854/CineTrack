package com.cinetrack.data.sync

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.cinetrack.CineTrackApplication
import com.cinetrack.MainActivity
import com.cinetrack.R
import com.cinetrack.data.repository.AppPreferences
import com.cinetrack.data.repository.CineTrackRepository
import com.cinetrack.domain.AppUiState
import com.cinetrack.domain.EpisodeCard
import com.cinetrack.domain.LibraryStatus
import com.cinetrack.domain.MediaType
import com.cinetrack.domain.RailIds
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

object ReleaseNotifier {
    const val CHANNEL_ID = "cinetrack_releases"
    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, context.getString(R.string.release_notifications), NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
    }

    private suspend fun canNotify(context: Context, preferences: AppPreferences): Boolean {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        if (!preferences.quietHoursEnabled.first()) return true
        val configuredTimezone = preferences.metadataTimezone.first()
        val zone = if (configuredTimezone == "system") ZoneId.systemDefault()
        else runCatching { ZoneId.of(configuredTimezone) }.getOrDefault(ZoneId.systemDefault())
        val hour = LocalTime.now(zone).hour
        val start = preferences.quietHoursStart.first()
        val end = preferences.quietHoursEnd.first()
        val quiet = isQuietHour(hour, start, end)
        return !quiet
    }

    suspend fun scheduleUpcoming(context: Context, state: AppUiState, preferences: AppPreferences) {
        val configuredTimezone = preferences.metadataTimezone.first()
        val zone = if (configuredTimezone == "system") ZoneId.systemDefault()
        else runCatching { ZoneId.of(configuredTimezone) }.getOrDefault(ZoneId.systemDefault())
        val now = ZonedDateTime.now(zone)
        val notified = preferences.notifiedReleaseKeys()
        state.calendar.asSequence()
            .mapNotNull { event ->
                val day = runCatching { LocalDate.parse(event.timestamp.take(10)) }.getOrNull() ?: return@mapNotNull null
                if (day.isBefore(now.toLocalDate()) || day.isAfter(now.toLocalDate().plusDays(90))) return@mapNotNull null
                val key = "${event.media.stableKey}:${event.season ?: 0}:${event.episodeNumber ?: 0}:$day"
                if (key in notified) return@mapNotNull null
                Triple(event, day, key)
            }
            .distinctBy { it.third }
            .take(100)
            .forEach { (event, day, key) ->
                val releaseAt = day.atTime(9, 0).atZone(zone)
                val delay = Duration.between(now, releaseAt).toMillis().coerceAtLeast(0L)
                val deepLink = if (event.media.type == MediaType.TV && event.season != null && event.episodeNumber != null) {
                    "cinetrack://episode/${event.media.id}/${event.season}/${event.episodeNumber}"
                } else "cinetrack://detail/${event.media.type.name}/${event.media.id}"
                val request = OneTimeWorkRequestBuilder<ReleaseNotificationWorker>()
                    .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                    .setInputData(
                        workDataOf(
                            "key" to key,
                            "title" to event.media.title,
                            "text" to (event.episodeLabel ?: context.getString(R.string.available_today)),
                            "deepLink" to deepLink,
                            "mediaType" to event.media.type.name,
                            "mediaId" to event.media.id,
                            "season" to (event.season ?: -1),
                            "episode" to (event.episodeNumber ?: -1),
                        ),
                    )
                    .build()
                WorkManager.getInstance(context).enqueueUniqueWork(
                    "cinetrack-release-${key.hashCode()}",
                    ExistingWorkPolicy.REPLACE,
                    request,
                )
            }
    }

    @SuppressLint("MissingPermission")
    suspend fun notifyScheduledRelease(context: Context, preferences: AppPreferences, data: androidx.work.Data) {
        val type = data.getString("mediaType")?.let { runCatching { MediaType.valueOf(it) }.getOrNull() } ?: return
        if (type == MediaType.TV && !preferences.notificationEpisodes.first()) return
        if (type == MediaType.MOVIE && !preferences.notificationMovies.first()) return
        if (!canNotify(context, preferences)) return
        val key = data.getString("key") ?: return
        if (!preferences.markReleaseNotified(key)) return
        val deepLink = data.getString("deepLink") ?: return
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_cinetrack)
            .setContentTitle(data.getString("title"))
            .setContentText(data.getString("text"))
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    key.hashCode(),
                    Intent(Intent.ACTION_VIEW, Uri.parse(deepLink), context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        val season = data.getInt("season", -1)
        val episode = data.getInt("episode", -1)
        if (type == MediaType.TV && season >= 0 && episode > 0) {
            val actionIntent = Intent(context, MarkEpisodeWatchedReceiver::class.java).apply {
                putExtra("showId", data.getInt("mediaId", 0))
                putExtra("season", season)
                putExtra("episode", episode)
                putExtra("title", data.getString("text").orEmpty())
            }
            builder.addAction(
                0,
                context.getString(R.string.mark_watched),
                PendingIntent.getBroadcast(context, key.hashCode(), actionIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE),
            )
        }
        NotificationManagerCompat.from(context).notify(key.hashCode(), builder.build())
    }

    @SuppressLint("MissingPermission")
    suspend fun notifySyncFailure(context: Context, preferences: AppPreferences) {
        if (!preferences.notificationSync.first() || !canNotify(context, preferences)) return
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("cinetrack://app/sync-operations"),
            context,
            MainActivity::class.java,
        )
        NotificationManagerCompat.from(context).notify(
            "sync-failure".hashCode(),
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_cinetrack)
                .setContentTitle(context.getString(R.string.sync_failed_notification_title))
                .setContentText(context.getString(R.string.sync_failed_notification_text))
                .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.sync_failed_notification_text)))
                .setAutoCancel(true)
                .setContentIntent(
                    PendingIntent.getActivity(
                        context,
                        "sync-failure".hashCode(),
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
                .build(),
        )
    }

    @SuppressLint("MissingPermission")
    suspend fun notifyUpcoming(
        context: Context,
        state: AppUiState,
        preferences: AppPreferences,
        repository: CineTrackRepository? = null,
    ) {
        if (!canNotify(context, preferences)) return
        val configuredTimezone = preferences.metadataTimezone.first()
        val releaseZone = if (configuredTimezone == "system") ZoneId.systemDefault()
        else runCatching { ZoneId.of(configuredTimezone) }.getOrDefault(ZoneId.systemDefault())
        val today = LocalDate.now(releaseZone).toString()
        val notified = preferences.notifiedReleaseKeys().toMutableSet()
        val manager = NotificationManagerCompat.from(context)
        state.calendar.filter { it.timestamp.take(10) == today }.forEach { event ->
            if (event.media.type == MediaType.TV && !preferences.notificationEpisodes.first()) return@forEach
            if (event.media.type == MediaType.MOVIE && !preferences.notificationMovies.first()) return@forEach
            val key = "${event.media.stableKey}:${event.season ?: 0}:${event.episodeNumber ?: 0}:$today"
            if (!notified.add(key)) return@forEach
            val deepLink = if (event.media.type == MediaType.TV && event.season != null && event.episodeNumber != null) {
                "cinetrack://episode/${event.media.id}/${event.season}/${event.episodeNumber}"
            } else "cinetrack://detail/${event.media.type.name}/${event.media.id}"
            val contentIntent = PendingIntent.getActivity(
                context, key.hashCode(), Intent(Intent.ACTION_VIEW, Uri.parse(deepLink), context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_cinetrack)
                .setContentTitle(event.media.title)
                .setContentText(event.episodeLabel ?: context.getString(R.string.available_today))
                .setAutoCancel(true)
                .setContentIntent(contentIntent)
            if (event.media.type == MediaType.TV && event.season != null && event.episodeNumber != null) {
                val actionIntent = Intent(context, MarkEpisodeWatchedReceiver::class.java).apply {
                    putExtra("showId", event.media.id); putExtra("season", event.season); putExtra("episode", event.episodeNumber)
                    putExtra("title", event.episodeLabel.orEmpty())
                }
                builder.addAction(
                    0,
                    context.getString(R.string.mark_watched),
                    PendingIntent.getBroadcast(context, key.hashCode(), actionIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE),
                )
            }
            manager.notify(key.hashCode(), builder.build())
        }
        val preferredProviders = preferences.preferredProviders.first()
        if (repository != null && preferredProviders.isNotEmpty()) {
            val candidates = state.rails[RailIds.LIBRARY].orEmpty()
                .filter { it.status in setOf(LibraryStatus.PLAN_TO_WATCH, LibraryStatus.WATCHING, LibraryStatus.PAUSED) }
                .distinctBy { it.stableKey }
                .take(40)
            val availability = coroutineScope {
                val requests = Semaphore(4)
                candidates.map { media -> async {
                    requests.withPermit {
                        val detailed = runCatching { repository.loadDetails(media) }.getOrNull()
                            ?: return@withPermit emptyList<Pair<com.cinetrack.domain.MediaCard, String>>()
                        detailed.subscriptionProviders.filter(preferredProviders::contains).map { provider -> detailed to provider }
                    }
                } }.flatMap { it.await() }
            }
            availability.forEach { (media, provider) ->
                val key = "provider:${media.stableKey}:$provider"
                if (!notified.add(key)) return@forEach
                val contentIntent = PendingIntent.getActivity(
                    context,
                    key.hashCode(),
                    Intent(Intent.ACTION_VIEW, Uri.parse("cinetrack://detail/${media.type.name}/${media.id}"), context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                manager.notify(
                    key.hashCode(),
                    NotificationCompat.Builder(context, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_notification_cinetrack)
                        .setContentTitle(media.title)
                        .setContentText(context.getString(R.string.now_available_on, provider))
                        .setAutoCancel(true)
                        .setContentIntent(contentIntent)
                        .build(),
                )
            }
        }
        preferences.setNotifiedReleaseKeys(notified)
    }
}

internal fun isQuietHour(hour: Int, start: Int, end: Int): Boolean =
    if (start == end) true else if (start < end) hour in start until end else hour >= start || hour < end

class ReleaseNotificationWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val application = applicationContext as CineTrackApplication
        application.container.repository.awaitStartup()
        ReleaseNotifier.notifyScheduledRelease(applicationContext, application.container.preferences, inputData)
        return Result.success()
    }
}

class MarkEpisodeWatchedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val showId = intent.getIntExtra("showId", 0)
                val season = intent.getIntExtra("season", 0)
                val number = intent.getIntExtra("episode", 0)
                if (showId > 0 && season >= 0 && number > 0) {
                    val repository = (context.applicationContext as CineTrackApplication).container.repository
                    repository.awaitStartup()
                    repository.markEpisodeWatched(EpisodeCard(-1, showId, season, number, intent.getStringExtra("title").orEmpty(), "", LocalDate.now().toString()))
                }
            } finally { result.finish() }
        }
    }
}
