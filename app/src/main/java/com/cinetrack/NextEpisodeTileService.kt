package com.cinetrack

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.cinetrack.data.sync.WatchedProgressRefreshWorker
import com.cinetrack.domain.EpisodeCard
import com.cinetrack.domain.PlaybackCard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Quick Settings tile: the first episode of Up next. A tap marks it watched, like the widget's
 * check. Until Up next moves on, the tile shows it as marked and a tap opens the app instead,
 * so a second tap can never log the same episode twice.
 */
class NextEpisodeTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var next: PlaybackCard? = null

    override fun onStartListening() {
        super.onStartListening()
        scope.launch {
            next = loadNext()
            render()
        }
    }

    override fun onClick() {
        super.onClick()
        val item = next
        if (item == null || item.key() == lastMarked) {
            openApp(item)
            return
        }
        if (isLocked) unlockAndRun { mark(item) } else mark(item)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun mark(item: PlaybackCard) {
        lastMarked = item.key()
        render()
        val context = applicationContext
        scope.launch {
            withContext(Dispatchers.IO) {
                val container = (context as CineTrackApplication).container
                container.syncHost.awaitStartup()
                container.libraryRepository.markEpisodeWatched(
                    EpisodeCard(-1, item.media.id, item.season ?: 0, item.episodeNumber ?: 0, item.episodeTitle.orEmpty(), "", LocalDate.now().toString()),
                )
                UpNextWidgetProvider.refreshNow(context)
                WatchedProgressRefreshWorker.enqueue(context)
            }
            next = loadNext()
            render()
        }
    }

    private suspend fun loadNext(): PlaybackCard? = withContext(Dispatchers.IO) {
        runCatching {
            val container = (applicationContext as CineTrackApplication).container
            container.syncHost.awaitStartup()
            container.appState.loadCachedState().playbackTv.firstOrNull { it.season != null && it.episodeNumber != null }
        }.getOrNull()
    }

    private fun render() {
        val tile = qsTile ?: return
        val item = next
        val marked = item != null && item.key() == lastMarked
        tile.icon = Icon.createWithResource(this, R.drawable.ic_notification_cinetrack)
        tile.label = item?.media?.title ?: getString(R.string.tile_next_episode)
        val detail = when {
            item == null -> getString(R.string.tile_nothing_next)
            marked -> getString(R.string.tile_marked, item.code())
            else -> getString(R.string.tile_tap_to_mark, item.code())
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.subtitle = detail
        tile.contentDescription = listOfNotNull(item?.media?.title, detail).joinToString(", ")
        tile.state = when {
            item == null -> Tile.STATE_UNAVAILABLE
            marked -> Tile.STATE_INACTIVE
            else -> Tile.STATE_ACTIVE
        }
        tile.updateTile()
    }

    private fun openApp(item: PlaybackCard?) {
        val uri = item?.let { "cinetrack://episode/${it.media.id}/${it.season}/${it.episodeNumber}" } ?: "cinetrack://app/up-next"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri), this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        } else {
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }

    private fun PlaybackCard.key() = "${media.id}:$season:$episodeNumber"
    private fun PlaybackCard.code() = "S${season}E$episodeNumber"

    private companion object {
        /** Survives the service being rebuilt while Up next refreshes in the background. */
        @Volatile var lastMarked: String? = null
    }
}
