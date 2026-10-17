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
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
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
import com.cinetrack.domain.PlaybackCard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Home screen widget: the next episodes from Progress, in the same order, each opening its episode. */
class UpNextWidgetProvider : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = UpNextWidget()

    companion object {
        /** Redraws any placed widgets, e.g. after the next episodes changed. */
        fun refresh(context: Context) {
            val appContext = context.applicationContext
            CoroutineScope(Dispatchers.IO).launch { runCatching { UpNextWidget().updateAll(appContext) } }
        }
    }
}

private const val MAX_ROWS = 3
private val MutedText = Color(0xFFA2AEB8)
private val BodyText = Color(0xFFD7DEE4)
private val TitleText = Color(0xFFF8F9FB)

private class UpNextWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val (items, posters) = withContext(Dispatchers.IO) {
            val repository = (context.applicationContext as CineTrackApplication).container.repository
            repository.awaitStartup()
            val cards = runCatching { repository.loadCachedState().playbackTv.take(MAX_ROWS) }.getOrDefault(emptyList())
            cards to cards.map { card -> card.media.posterUrl?.let { loadPoster(context, it) } }
        }
        provideContent { Content(context, items, posters) }
    }

    @Composable
    private fun Content(context: Context, items: List<PlaybackCard>, posters: List<Bitmap?>) {
        Column(
            modifier = GlanceModifier.fillMaxSize()
                .background(ImageProvider(R.drawable.widget_background))
                .padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 12.dp)
                .clickable(actionStartActivity(openApp(context, "cinetrack://app/progress"))),
        ) {
            Text(
                text = context.getString(R.string.widget_up_next_title).uppercase(),
                modifier = GlanceModifier.padding(bottom = 4.dp),
                style = TextStyle(color = ColorProvider(MutedText), fontSize = 11.sp, fontWeight = FontWeight.Bold),
            )
            if (items.isEmpty()) {
                Box(modifier = GlanceModifier.fillMaxWidth().defaultWeight(), contentAlignment = Alignment.Center) {
                    Text(
                        text = context.getString(R.string.widget_up_next_empty),
                        style = TextStyle(color = ColorProvider(BodyText), fontSize = 13.sp, textAlign = TextAlign.Center),
                    )
                }
            }
            items.forEachIndexed { index, item ->
                val route = if (item.season != null && item.episodeNumber != null) {
                    "cinetrack://episode/${item.media.id}/${item.season}/${item.episodeNumber}"
                } else "cinetrack://detail/${item.media.type.name}/${item.media.id}"
                Row(
                    modifier = GlanceModifier.fillMaxWidth().defaultWeight()
                        .padding(vertical = 4.dp)
                        .clickable(actionStartActivity(openApp(context, route))),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val poster = posters.getOrNull(index)
                    Image(
                        provider = if (poster != null) ImageProvider(poster) else ImageProvider(R.drawable.widget_poster_placeholder),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = GlanceModifier.width(34.dp).height(50.dp),
                    )
                    Column(modifier = GlanceModifier.defaultWeight().padding(start = 12.dp)) {
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
                }
            }
        }
    }

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
