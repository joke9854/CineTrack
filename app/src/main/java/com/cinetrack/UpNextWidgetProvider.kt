package com.cinetrack

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.currentState
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionSendBroadcast
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.cinetrack.data.sync.MarkEpisodeWatchedReceiver
import com.cinetrack.domain.PlaybackCard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Home screen widget: the next episodes from Progress, in the same order, each opening its episode; the check marks it watched. */
class UpNextWidgetProvider : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = UpNextWidget()

    companion object {
        /** Redraws any placed widgets, e.g. after the next episodes changed. */
        fun refresh(context: Context) {
            val appContext = context.applicationContext
            CoroutineScope(Dispatchers.IO).launch { refreshNow(appContext) }
        }

        /**
         * Reloads the list in every placed widget. A plain update() only recomposes a session
         * that is still alive with the list it loaded at start, so each widget gets a new
         * refresh stamp in its state first and reloads when the stamp changes.
         */
        suspend fun refreshNow(context: Context) {
            val appContext = context.applicationContext
            runCatching {
                val widget = UpNextWidget()
                val ids = GlanceAppWidgetManager(appContext).getGlanceIds(UpNextWidget::class.java)
                Log.i(WIDGET_LOG, "refresh: ${ids.size} widget(s)")
                ids.forEach { id ->
                    updateAppWidgetState(appContext, id) { it[REFRESH_STAMP] = System.currentTimeMillis() }
                    widget.update(appContext, id)
                }
            }.onFailure { Log.w(WIDGET_LOG, "refresh failed: ${it.javaClass.simpleName}") }
        }
    }
}

private const val MAX_ROWS = 5
private const val WIDGET_LOG = "CineTrackWidget"
private val REFRESH_STAMP = longPreferencesKey("up_next_refresh_stamp")

/** Height picks the rows (1 cell tall: one, 2-3 cells: three, 4+ cells: five); a narrow widget drops the posters. */
private val ROW_HEIGHT_THREE = 110.dp
private val ROW_HEIGHT_FIVE = 280.dp
private val WIDE = 250.dp

internal fun upNextWidgetRows(size: DpSize): Int = when {
    size.height >= ROW_HEIGHT_FIVE -> 5
    size.height >= ROW_HEIGHT_THREE -> 3
    else -> 1
}

internal fun upNextWidgetShowsPosters(size: DpSize): Boolean = size.width >= WIDE
private val MutedText = Color(0xFFA2AEB8)
private val BodyText = Color(0xFFD7DEE4)
private val TitleText = Color(0xFFF8F9FB)

private class UpNextWidget : GlanceAppWidget() {
    // One composition for the widget's real size; Responsive composed (and loaded) once per declared size.
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val initial = load(context)
        provideContent {
            val stamp = currentState<Preferences>()[REFRESH_STAMP] ?: 0L
            var data by remember { mutableStateOf(initial) }
            // A new stamp from refreshNow() means the next episodes changed while this session was alive.
            LaunchedEffect(stamp) {
                Log.i(WIDGET_LOG, "session stamp=$stamp")
                if (stamp != 0L) data = load(context)
            }
            Content(context, data.first, data.second)
        }
    }

    private suspend fun load(context: Context): Pair<List<PlaybackCard>, List<Bitmap?>> = withContext(Dispatchers.IO) {
        val repository = (context.applicationContext as CineTrackApplication).container.repository
        repository.awaitStartup()
        val cards = try {
            repository.loadCachedState().playbackTv.take(MAX_ROWS)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            // A newer refresh replaced this load; never draw an empty list for it.
            throw cancelled
        } catch (error: Exception) {
            Log.w(WIDGET_LOG, "load failed: ${error.javaClass.simpleName}")
            emptyList()
        }
        // Episode coordinates only, no titles.
        Log.i(WIDGET_LOG, "load: " + cards.joinToString { "${it.media.id}:S${it.season}E${it.episodeNumber}" })
        cards to cards.map { card -> card.media.posterUrl?.let { loadPoster(context, it) } }
    }

    @Composable
    private fun Content(context: Context, items: List<PlaybackCard>, posters: List<Bitmap?>) {
        val size = LocalSize.current
        val rows = upNextWidgetRows(size)
        val showPosters = upNextWidgetShowsPosters(size)
        Column(
            modifier = GlanceModifier.fillMaxSize()
                .background(ImageProvider(R.drawable.widget_background))
                .padding(start = 16.dp, top = if (rows == 1) 6.dp else 14.dp, end = 12.dp, bottom = if (rows == 1) 6.dp else 12.dp)
                .clickable(actionStartActivity(openApp(context, "cinetrack://app/progress"))),
        ) {
            // One cell tall has room for the row only.
            if (rows > 1) {
                Text(
                    text = context.getString(R.string.widget_up_next_title).uppercase(),
                    modifier = GlanceModifier.padding(bottom = 4.dp),
                    style = TextStyle(color = ColorProvider(MutedText), fontSize = 11.sp, fontWeight = FontWeight.Bold),
                )
            }
            if (items.isEmpty()) {
                Box(modifier = GlanceModifier.fillMaxWidth().defaultWeight(), contentAlignment = Alignment.Center) {
                    Text(
                        text = context.getString(R.string.widget_up_next_empty),
                        style = TextStyle(color = ColorProvider(BodyText), fontSize = 13.sp, textAlign = TextAlign.Center),
                    )
                }
            }
            items.take(rows).forEachIndexed { index, item ->
                val route = if (item.season != null && item.episodeNumber != null) {
                    "cinetrack://episode/${item.media.id}/${item.season}/${item.episodeNumber}"
                } else "cinetrack://detail/${item.media.type.name}/${item.media.id}"
                Row(
                    modifier = GlanceModifier.fillMaxWidth().defaultWeight()
                        .padding(vertical = if (rows == 1) 0.dp else 4.dp)
                        .clickable(actionStartActivity(openApp(context, route))),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (showPosters) {
                        val poster = posters.getOrNull(index)
                        Image(
                            provider = if (poster != null) ImageProvider(poster) else ImageProvider(R.drawable.widget_poster_placeholder),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = GlanceModifier.width(34.dp).height(50.dp),
                        )
                    }
                    Column(modifier = GlanceModifier.defaultWeight().padding(start = if (showPosters) 12.dp else 0.dp, end = 8.dp)) {
                        Text(
                            text = item.media.title,
                            maxLines = 1,
                            style = TextStyle(color = ColorProvider(TitleText), fontSize = 14.sp, fontWeight = FontWeight.Bold),
                        )
                        Text(
                            text = listOfNotNull(item.episodeLabel, item.episodeTitle?.takeIf(String::isNotBlank)).joinToString(" · "),
                            maxLines = 1,
                            modifier = GlanceModifier.padding(top = 2.dp),
                            style = TextStyle(color = ColorProvider(MutedText), fontSize = 12.sp),
                        )
                    }
                    if (item.season != null && item.episodeNumber != null) {
                        Image(
                            provider = ImageProvider(R.drawable.widget_check_button),
                            contentDescription = context.getString(R.string.mark_watched),
                            modifier = GlanceModifier.size(32.dp).clickable(actionSendBroadcast(markWatched(context, item))),
                        )
                    }
                }
            }
        }
    }

    /** Same receiver as the release notification's action: records the play at the tap time and refreshes this widget. */
    private fun markWatched(context: Context, item: PlaybackCard): Intent =
        Intent(context, MarkEpisodeWatchedReceiver::class.java)
            // A distinct data URI per episode keeps each row's pending intent separate.
            .setData(Uri.parse("cinetrack-widget://mark/${item.media.id}/${item.season}/${item.episodeNumber}"))
            .putExtra("showId", item.media.id)
            .putExtra("season", item.season ?: 0)
            .putExtra("episode", item.episodeNumber ?: 0)
            .putExtra("title", item.episodeTitle.orEmpty())

    private fun openApp(context: Context, route: String): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(route), context, MainActivity::class.java)

    /** A small poster with rounded corners; widgets cannot clip images themselves. */
    private suspend fun loadPoster(context: Context, url: String): Bitmap? = runCatching {
        val request = ImageRequest.Builder(context).data(url).size(102, 150).allowHardware(false).build()
        val result = context.imageLoader.execute(request) as? SuccessResult ?: return@runCatching null
        val source = result.image.toBitmap(102, 150)
        val rounded = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
        Canvas(rounded).drawRoundRect(RectF(0f, 0f, source.width.toFloat(), source.height.toFloat()), 14f, 14f, paint)
        rounded
    }.getOrNull()
}
