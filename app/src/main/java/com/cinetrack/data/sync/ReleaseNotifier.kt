package com.cinetrack.data.sync

import android.Manifest
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
import java.time.ZoneId

object ReleaseNotifier {
    const val CHANNEL_ID = "cinetrack_releases"
    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, context.getString(R.string.release_notifications), NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
    }

    suspend fun notifyUpcoming(
        context: Context,
        state: AppUiState,
        preferences: AppPreferences,
        repository: CineTrackRepository? = null,
    ) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
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
                .setSmallIcon(R.drawable.ic_simkl_s)
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
                        .setSmallIcon(R.drawable.ic_simkl_s)
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
                    repository.markEpisodeWatched(EpisodeCard(-1, showId, season, number, intent.getStringExtra("title").orEmpty(), "", LocalDate.now().toString()))
                }
            } finally { result.finish() }
        }
    }
}
