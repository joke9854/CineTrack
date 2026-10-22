package com.cinetrack.data.sync

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
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
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.OutOfQuotaPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.cinetrack.CineTrackApplication
import com.cinetrack.MainActivity
import com.cinetrack.R
import com.cinetrack.data.repository.AppPreferences
import com.cinetrack.data.repository.MediaMetadataRepository
import com.cinetrack.data.repository.ProgressRefreshRequest
import com.cinetrack.domain.AppUiState
import com.cinetrack.domain.EpisodeCard
import com.cinetrack.domain.LibraryStatus
import com.cinetrack.domain.MediaCard
import com.cinetrack.domain.MediaType
import com.cinetrack.domain.RailIds
import com.cinetrack.domain.releaseDateTime
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

    /** Milliseconds until quiet hours end, or null when a notification may go out now. */
    suspend fun quietHoursRemainingMillis(preferences: AppPreferences): Long? {
        if (!preferences.quietHoursEnabled.first()) return null
        val configuredTimezone = preferences.metadataTimezone.first()
        val zone = if (configuredTimezone == "system") ZoneId.systemDefault()
        else runCatching { ZoneId.of(configuredTimezone) }.getOrDefault(ZoneId.systemDefault())
        val now = ZonedDateTime.now(zone)
        val start = preferences.quietHoursStart.first()
        val end = preferences.quietHoursEnd.first()
        if (start == end || !isQuietHour(now.hour, start, end)) return null
        var endsAt = now.withHour(end).withMinute(0).withSecond(0).withNano(0)
        if (!endsAt.isAfter(now)) endsAt = endsAt.plusDays(1)
        return Duration.between(now, endsAt).toMillis()
    }

    suspend fun scheduleUpcoming(context: Context, state: AppUiState, preferences: AppPreferences) {
        val configuredTimezone = preferences.metadataTimezone.first()
        val zone = if (configuredTimezone == "system") ZoneId.systemDefault()
        else runCatching { ZoneId.of(configuredTimezone) }.getOrDefault(ZoneId.systemDefault())
        val now = ZonedDateTime.now(zone)
        val notified = preferences.notifiedReleaseKeys()
        state.calendar.asSequence()
            .mapNotNull { event ->
                val releaseAt = releaseDateTime(event.timestamp, zone) ?: return@mapNotNull null
                val day = releaseAt.toLocalDate()
                if (day.isBefore(now.toLocalDate()) || day.isAfter(now.toLocalDate().plusDays(90))) return@mapNotNull null
                val key = "${event.media.stableKey}:${event.season ?: 0}:${event.episodeNumber ?: 0}:$day"
                if (key in notified) return@mapNotNull null
                Triple(event, releaseAt, key)
            }
            .distinctBy { it.third }
            .take(100)
            .forEach { (event, releaseAt, key) ->
                val delay = Duration.between(now, releaseAt).toMillis().coerceAtLeast(0L)
                val deepLink = if (event.media.type == MediaType.TV && event.season != null && event.episodeNumber != null) {
                    "cinetrack://episode/${event.media.id}/${event.season}/${event.episodeNumber}"
                } else "cinetrack://detail/${event.media.type.name}/${event.media.id}"
                val request = OneTimeWorkRequestBuilder<ReleaseNotificationWorker>()
                    .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                    .setInputData(
                        workDataOf(
                            "key" to key,
                            "muteKey" to muteKey(event.media.stableKey, event.season, event.episodeNumber, event.timestamp),
                            "title" to event.media.title,
                            "text" to releaseNotificationText(context, event.season, event.episodeNumber, event.episodeTitle, event.episodeLabel),
                            "episodeTitle" to (event.episodeTitle ?: extractEpisodeTitle(event.episodeLabel).orEmpty()),
                            "episodeLabel" to event.episodeLabel.orEmpty(),
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

    fun reminderKey(mediaKey: String, season: Int?, episode: Int?, day: String): String =
        "reminder:$mediaKey:${season ?: 0}:${episode ?: 0}:${day.take(10)}"

    /** Marks a release the user silenced although its kind of release notifies by default. */
    fun muteKey(mediaKey: String, season: Int?, episode: Int?, day: String): String =
        "mute:$mediaKey:${season ?: 0}:${episode ?: 0}:${day.take(10)}"

    /**
     * Schedules (or cancels) a notification on a title's release day, for any title,
     * in the library or not. A bare date reminds at 9 in the morning rather than at midnight.
     */
    suspend fun setReminder(
        context: Context,
        preferences: AppPreferences,
        media: MediaCard,
        release: String,
        season: Int?,
        episode: Int?,
        episodeTitle: String?,
        enabled: Boolean,
    ) {
        val key = reminderKey(media.stableKey, season, episode, release)
        val work = "cinetrack-reminder-${key.hashCode()}"
        preferences.setReleaseReminder(key, enabled)
        if (!enabled) {
            WorkManager.getInstance(context).cancelUniqueWork(work)
            return
        }
        val configuredTimezone = preferences.metadataTimezone.first()
        val zone = if (configuredTimezone == "system") ZoneId.systemDefault()
        else runCatching { ZoneId.of(configuredTimezone) }.getOrDefault(ZoneId.systemDefault())
        val releaseAt = releaseDateTime(release, zone) ?: return
        val fireAt = if (release.trim().length <= 10) releaseAt.toLocalDate().atTime(9, 0).atZone(zone) else releaseAt
        val delay = Duration.between(ZonedDateTime.now(zone), fireAt).toMillis().coerceAtLeast(0L)
        val deepLink = if (media.type == MediaType.TV && season != null && episode != null) {
            "cinetrack://episode/${media.id}/$season/$episode"
        } else "cinetrack://detail/${media.type.name}/${media.id}"
        val request = OneTimeWorkRequestBuilder<ReleaseNotificationWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(
                workDataOf(
                    "key" to key,
                    "explicit" to true,
                    "title" to media.title,
                    "text" to releaseNotificationText(context, season, episode, episodeTitle, null),
                    "episodeTitle" to episodeTitle.orEmpty(),
                    "episodeLabel" to "",
                    "deepLink" to deepLink,
                    "mediaType" to media.type.name,
                    "mediaId" to media.id,
                    "season" to (season ?: -1),
                    "episode" to (episode ?: -1),
                ),
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(work, ExistingWorkPolicy.REPLACE, request)
    }

    @SuppressLint("MissingPermission")
    suspend fun notifyScheduledRelease(
        context: Context,
        preferences: AppPreferences,
        data: androidx.work.Data,
        progressCache: com.cinetrack.data.repository.ProgressCacheRepository? = null,
    ) {
        val type = data.getString("mediaType")?.let { runCatching { MediaType.valueOf(it) }.getOrNull() } ?: return
        // A reminder the user set on a title is sent even when the general release toggles are off.
        val explicit = data.getBoolean("explicit", false)
        if (explicit) data.getString("key")?.let { preferences.setReleaseReminder(it, false) }
        if (!explicit && type == MediaType.TV && !preferences.notificationEpisodes.first()) return
        if (!explicit && type == MediaType.MOVIE && !preferences.notificationMovies.first()) return
        if (!explicit && data.getString("muteKey") in preferences.releaseReminders.first()) return
        if (!canNotify(context, preferences)) return
        val key = data.getString("key") ?: return
        if (!preferences.markReleaseNotified(key)) return
        val deepLink = data.getString("deepLink") ?: return
        val season = data.getInt("season", -1)
        val episode = data.getInt("episode", -1)
        val rawTitle = data.getString("episodeTitle")?.takeIf(String::isNotBlank)
            ?: extractEpisodeTitle(data.getString("episodeLabel"))
            ?: progressCache?.let {
                if (type == MediaType.TV && season >= 0 && episode > 0) it.cachedEpisodeTitle(data.getInt("mediaId", 0), season, episode) else null
            }
        val text = releaseNotificationText(
            context,
            season.takeIf { it >= 0 },
            episode.takeIf { it > 0 },
            rawTitle,
            data.getString("text"),
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_cinetrack)
            .setStableTextLayout(data.getString("title").orEmpty(), text)
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    key.hashCode(),
                    Intent(Intent.ACTION_VIEW, Uri.parse(deepLink), context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        if (type == MediaType.TV && season >= 0 && episode > 0) {
            val actionIntent = Intent(context, MarkEpisodeWatchedReceiver::class.java).apply {
                putExtra("showId", data.getInt("mediaId", 0))
                putExtra("season", season)
                putExtra("episode", episode)
                putExtra("title", rawTitle.orEmpty())
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
                .setStableTextLayout(
                    context.getString(R.string.sync_failed_notification_title),
                    context.getString(R.string.sync_failed_notification_text),
                )
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
        mediaMetadata: MediaMetadataRepository? = null,
    ) {
        if (!canNotify(context, preferences)) return
        val configuredTimezone = preferences.metadataTimezone.first()
        val releaseZone = if (configuredTimezone == "system") ZoneId.systemDefault()
        else runCatching { ZoneId.of(configuredTimezone) }.getOrDefault(ZoneId.systemDefault())
        val now = ZonedDateTime.now(releaseZone)
        val today = now.toLocalDate().toString()
        val notified = preferences.notifiedReleaseKeys().toMutableSet()
        val manager = NotificationManagerCompat.from(context)
        val muted = preferences.releaseReminders.first()
        state.calendar.filter { event ->
            releaseDateTime(event.timestamp, releaseZone)?.let { releaseAt ->
                releaseAt.toLocalDate() == now.toLocalDate() && !releaseAt.isAfter(now)
            } == true
        }.forEach { event ->
            if (event.media.type == MediaType.TV && !preferences.notificationEpisodes.first()) return@forEach
            if (event.media.type == MediaType.MOVIE && !preferences.notificationMovies.first()) return@forEach
            if (muteKey(event.media.stableKey, event.season, event.episodeNumber, event.timestamp) in muted) return@forEach
            val key ="${event.media.stableKey}:${event.season ?: 0}:${event.episodeNumber ?: 0}:$today"
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
                .setStableTextLayout(
                    event.media.title,
                    releaseNotificationText(context, event.season, event.episodeNumber, event.episodeTitle, event.episodeLabel),
                )
                .setAutoCancel(true)
                .setContentIntent(contentIntent)
            if (event.media.type == MediaType.TV && event.season != null && event.episodeNumber != null) {
                val actionIntent = Intent(context, MarkEpisodeWatchedReceiver::class.java).apply {
                    putExtra("showId", event.media.id); putExtra("season", event.season); putExtra("episode", event.episodeNumber)
                    putExtra("title", event.episodeTitle ?: extractEpisodeTitle(event.episodeLabel).orEmpty())
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
        if (mediaMetadata != null && preferredProviders.isNotEmpty()) {
            val candidates = state.rails[RailIds.LIBRARY].orEmpty()
                .filter { it.status in setOf(LibraryStatus.PLAN_TO_WATCH, LibraryStatus.WATCHING, LibraryStatus.PAUSED) }
                .distinctBy { it.stableKey }
                .take(40)
            val availability = coroutineScope {
                val requests = Semaphore(4)
                candidates.map { media -> async {
                    requests.withPermit {
                        val detailed = runCatching { mediaMetadata.loadDetails(media) }.getOrNull()
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
                        .setStableTextLayout(media.title, context.getString(R.string.now_available_on, provider))
                        .setAutoCancel(true)
                        .setContentIntent(contentIntent)
                        .build(),
                )
            }
        }
        preferences.setNotifiedReleaseKeys(notified)
    }
}

private fun NotificationCompat.Builder.setStableTextLayout(title: String, text: String): NotificationCompat.Builder =
    setContentTitle(title)
        .setContentText(text)
        .setStyle(
            NotificationCompat.BigTextStyle()
                .setBigContentTitle(title)
                .bigText(text),
        )

internal fun extractEpisodeTitle(episodeLabel: String?): String? {
    val raw = episodeLabel?.trim().orEmpty()
    if (raw.isBlank()) return null
    val title = raw.substringAfter(" · ", "").trim()
    return title.takeIf { it.isNotBlank() && it != raw }
}

internal fun releaseNotificationText(
    context: Context,
    season: Int?,
    episode: Int?,
    episodeTitle: String? = null,
    legacyLabel: String? = null,
): String {
    if (season != null && season >= 0 && episode != null && episode > 0) {
        val title = episodeTitle?.takeIf(String::isNotBlank) ?: extractEpisodeTitle(legacyLabel)
        return if (title != null) context.getString(R.string.episode_available_named_notification, season, episode, title)
        else context.getString(R.string.episode_available_unnamed_notification, season, episode)
    }
    return legacyLabel?.takeIf(String::isNotBlank) ?: context.getString(R.string.available_today)
}

internal fun isQuietHour(hour: Int, start: Int, end: Int): Boolean =
    if (start == end) true else if (start < end) hour in start until end else hour >= start || hour < end

class ReleaseNotificationWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val application = applicationContext as CineTrackApplication
        application.container.syncHost.awaitStartup()
        // Inside quiet hours: come back when they end instead of dropping the
        // notification until the app is next opened.
        ReleaseNotifier.quietHoursRemainingMillis(application.container.preferences)?.let { wait ->
            val key = inputData.getString("key").orEmpty()
            WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                "cinetrack-release-quiet-${key.hashCode()}",
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<ReleaseNotificationWorker>()
                    .setInitialDelay(wait, TimeUnit.MILLISECONDS)
                    .setInputData(inputData)
                    .build(),
            )
            // WorkManager delays are held back while the phone is idle, so an
            // idle-safe alarm also wakes the app when quiet hours end. Whichever
            // runs second finds the release already notified and posts nothing.
            QuietHoursEndReceiver.schedule(applicationContext, key, inputData, wait)
            return Result.success()
        }
        ReleaseNotifier.notifyScheduledRelease(applicationContext, application.container.preferences, inputData, application.container.progressCache)
        // The released episode should be in Progress without opening the show.
        runCatching { application.container.progressCache.refreshProgressCache(ProgressRefreshRequest(), null) }
        com.cinetrack.UpNextWidgetProvider.refresh(applicationContext)
        return Result.success()
    }

    // Expedited work needs this before Android 12; on later versions it is unused.
    override suspend fun getForegroundInfo(): ForegroundInfo {
        ReleaseNotifier.createChannel(applicationContext)
        val notification = NotificationCompat.Builder(applicationContext, ReleaseNotifier.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_cinetrack)
            .setContentTitle(applicationContext.getString(R.string.release_notifications))
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        return ForegroundInfo("cinetrack-release-fg".hashCode(), notification)
    }
}

/** After a play marked from a notification or the widget: fetches the following episode, then redraws the widget. */
class WatchedProgressRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as CineTrackApplication).container
        val syncHost = container.syncHost
        syncHost.awaitStartup()
        runCatching { container.progressCache.refreshProgressCache(ProgressRefreshRequest(episodeHistoryChanged = true), null) }
        com.cinetrack.UpNextWidgetProvider.refreshNow(applicationContext)
        return Result.success()
    }

    companion object {
        /** Appends, so a second tap during a running refresh still gets its own pass. */
        fun enqueue(context: Context) {
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                "cinetrack-watched-progress-refresh",
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                OneTimeWorkRequestBuilder<WatchedProgressRefreshWorker>().build(),
            )
        }
    }
}

/** Fires when quiet hours end and runs the held release notification, even while the phone is idle. */
class QuietHoursEndReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getStringExtra(EXTRA_KEY) ?: return
        val bytes = intent.getByteArrayExtra(EXTRA_DATA) ?: return
        val data = runCatching { Data.fromByteArray(bytes) }.getOrNull() ?: return
        WorkManager.getInstance(context).enqueueUniqueWork(
            "cinetrack-release-quiet-${key.hashCode()}",
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<ReleaseNotificationWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setInputData(data)
                .build(),
        )
    }

    companion object {
        private const val EXTRA_KEY = "key"
        private const val EXTRA_DATA = "data"

        fun schedule(context: Context, key: String, data: Data, delayMillis: Long) {
            val alarms = context.getSystemService(AlarmManager::class.java) ?: return
            val intent = Intent(context, QuietHoursEndReceiver::class.java)
                .putExtra(EXTRA_KEY, key)
                .putExtra(EXTRA_DATA, data.toByteArray())
            val pending = PendingIntent.getBroadcast(
                context,
                "quiet:$key".hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            // A minute past the end so the hour check no longer reads as quiet.
            val at = System.currentTimeMillis() + delayMillis + 60_000L
            runCatching { alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending) }
        }
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
                android.util.Log.i("CineTrackWidget", "mark: $showId:S${season}E$number")
                if (showId > 0 && season >= 0 && number > 0) {
                    val container = (context.applicationContext as CineTrackApplication).container
                    container.syncHost.awaitStartup()
                    container.libraryRepository.markEpisodeWatched(EpisodeCard(-1, showId, season, number, intent.getStringExtra("title").orEmpty(), "", LocalDate.now().toString()))
                    // Redraw at once from the local up-next. Fetching the following episode can take
                    // longer than a broadcast may run (about 10s for a widget tap), so it goes to work.
                    com.cinetrack.UpNextWidgetProvider.refreshNow(context)
                    WatchedProgressRefreshWorker.enqueue(context)
                }
            } finally { result.finish() }
        }
    }
}

