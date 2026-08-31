package com.cinetrack

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class UpNextWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = (context.applicationContext as CineTrackApplication).container.repository
                val next = repository.loadCachedState().playbackTv.firstOrNull()
                appWidgetIds.forEach { id ->
                    val views = RemoteViews(context.packageName, R.layout.up_next_widget).apply {
                        setTextViewText(R.id.widget_title, next?.media?.title ?: context.getString(R.string.up_next))
                        setTextViewText(
                            R.id.widget_episode,
                            listOfNotNull(next?.episodeLabel, next?.episodeTitle).joinToString(" · ")
                                .ifBlank { context.getString(R.string.no_announced_episodes_short) },
                        )
                        val route = next?.let { "cinetrack://episode/${it.media.id}/${it.season ?: 1}/${it.episodeNumber ?: 1}" }
                            ?: "cinetrack://app/progress"
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(route), context, MainActivity::class.java)
                        setOnClickPendingIntent(
                            R.id.widget_root,
                            PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE),
                        )
                    }
                    manager.updateAppWidget(id, views)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
