package com.cinetrack

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.cinetrack.domain.PlaybackCard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Home screen widget: the next episodes from Progress, in the same order, each opening its episode. */
class UpNextWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = (context.applicationContext as CineTrackApplication).container.repository
                repository.awaitStartup()
                val items = runCatching { repository.loadCachedState().playbackTv.take(ROWS.size) }.getOrDefault(emptyList())
                val posters = items.map { item -> item.media.posterUrl?.let { loadPoster(context, it) } }
                appWidgetIds.forEach { id -> manager.updateAppWidget(id, views(context, items, posters)) }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun views(context: Context, items: List<PlaybackCard>, posters: List<Bitmap?>): RemoteViews =
        RemoteViews(context.packageName, R.layout.up_next_widget).apply {
            setViewVisibility(R.id.widget_empty, if (items.isEmpty()) View.VISIBLE else View.GONE)
            setOnClickPendingIntent(R.id.widget_root, openApp(context, "cinetrack://app/progress"))
            ROWS.forEachIndexed { index, row ->
                removeAllViews(row)
                val item = items.getOrNull(index)
                setViewVisibility(row, if (item == null) View.GONE else View.VISIBLE)
                if (item == null) return@forEachIndexed
                val route = if (item.season != null && item.episodeNumber != null) {
                    "cinetrack://episode/${item.media.id}/${item.season}/${item.episodeNumber}"
                } else "cinetrack://detail/${item.media.type.name}/${item.media.id}"
                addView(row, RemoteViews(context.packageName, R.layout.up_next_widget_row).apply {
                    setTextViewText(R.id.row_title, item.media.title)
                    setTextViewText(R.id.row_episode, listOfNotNull(item.episodeLabel, item.episodeTitle?.takeIf(String::isNotBlank)).joinToString(" · "))
                    val poster = posters.getOrNull(index)
                    if (poster != null) setImageViewBitmap(R.id.row_poster, poster) else setImageViewResource(R.id.row_poster, R.drawable.widget_poster_placeholder)
                    setOnClickPendingIntent(R.id.row_root, openApp(context, route))
                })
            }
        }

    private fun openApp(context: Context, route: String): PendingIntent =
        PendingIntent.getActivity(
            context, route.hashCode(),
            Intent(Intent.ACTION_VIEW, Uri.parse(route), context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** A small poster with rounded corners; widgets cannot clip images themselves. */
    private suspend fun loadPoster(context: Context, url: String): Bitmap? = runCatching {
        val request = ImageRequest.Builder(context).data(url).size(102, 150).allowHardware(false).build()
        val result = context.imageLoader.execute(request) as? SuccessResult ?: return@runCatching null
        val source = result.drawable.toBitmap(102, 150)
        val rounded = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
        Canvas(rounded).drawRoundRect(RectF(0f, 0f, source.width.toFloat(), source.height.toFloat()), 14f, 14f, paint)
        rounded
    }.getOrNull()

    companion object {
        private val ROWS = intArrayOf(R.id.widget_row_1, R.id.widget_row_2, R.id.widget_row_3)

        /** Redraws any placed widgets, e.g. after the next episodes changed. */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, UpNextWidgetProvider::class.java))
            if (ids.isEmpty()) return
            context.sendBroadcast(
                Intent(context, UpNextWidgetProvider::class.java)
                    .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids),
            )
        }
    }
}
