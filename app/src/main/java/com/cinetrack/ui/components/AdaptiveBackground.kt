@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.cinetrack.ui.components

import androidx.compose.ui.graphics.asImageBitmap
import android.util.LruCache
import android.graphics.Color as AndroidColor
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import androidx.core.graphics.drawable.toBitmap
import com.cinetrack.ui.theme.Accent
import com.cinetrack.ui.theme.Background0
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.hazeEffect
import com.styropyr0.prismal.sources.prismalGlassLayer
import com.cinetrack.ui.theme.Motion

private val artworkColorCache = LruCache<String, Pair<Color, Color>>(24)

/** A small tile of fixed noise, repeated across the page for a film-grain texture. */
private val GrainBrush: androidx.compose.ui.graphics.ShaderBrush by lazy {
    val size = 96
    val random = java.util.Random(7)
    val pixels = IntArray(size * size) {
        val value = random.nextInt(256)
        android.graphics.Color.argb(255, value, value, value)
    }
    val bitmap = android.graphics.Bitmap.createBitmap(pixels, size, size, android.graphics.Bitmap.Config.ARGB_8888)
    androidx.compose.ui.graphics.ShaderBrush(
        androidx.compose.ui.graphics.ImageShader(
            bitmap.asImageBitmap(),
            androidx.compose.ui.graphics.TileMode.Repeated,
            androidx.compose.ui.graphics.TileMode.Repeated,
        ),
    )
}

/** The colour at the top of the current page's [AdaptiveBackground]; pinned headers draw with it. */
val LocalPageTint = androidx.compose.runtime.staticCompositionLocalOf<() -> Color> { { Color(0xFF0A0C10) } }

@Composable
fun AdaptiveBackground(
    artworkUrl: String? = null,
    glow: Color? = null,
    secondaryGlow: Color? = null,
    hazeState: HazeState? = null,
    blurBackdrop: Boolean = false,
    /** Record the background so liquidGlass controls in [content] can refract it. */
    liquidGlass: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val context = LocalContext.current
    val artworkColors by produceState<Pair<Color, Color>?>(artworkUrl?.let { artworkColorCache.get(it) }, artworkUrl) {
        value = artworkUrl?.let { url -> artworkColorCache.get(url) ?: extractArtworkColors(context, url)?.also { artworkColorCache.put(url, it) } }
    }
    // Near-black ground; artwork colour glows in from the top so glass and posters stand out.
    val primaryTarget = glow ?: artworkColors?.first ?: Color(0xFF3B4352)
    val secondaryTarget = secondaryGlow ?: artworkColors?.second ?: Color(0xFF2A3040)
    val baseTarget = artworkColors?.first?.let {
        Color(
            (it.red * .16f + .035f).coerceIn(0f, 1f),
            (it.green * .16f + .04f).coerceIn(0f, 1f),
            (it.blue * .16f + .05f).coerceIn(0f, 1f),
        )
    } ?: Color(0xFF0B0D11)
    val primary by animateColorAsState(primaryTarget, tween(Motion.Extended), label = "adaptivePrimary")
    val secondary by animateColorAsState(secondaryTarget, tween(Motion.Extended), label = "adaptiveSecondary")
    val base by animateColorAsState(baseTarget, tween(Motion.Extended), label = "adaptiveBase")
    // What the top of the page looks like: the ground with the artwork wash and bloom over it.
    val pageTint: () -> Color = remember { { androidx.compose.ui.graphics.lerp(base, primary, .30f) } }
    val backgroundModifier = Modifier
            .fillMaxSize()
            .background(base)
            .drawBehind {
                // Artwork light falls evenly across the top: a full-width wash, a soft bloom
                // centred above the screen, and the second colour low at the bottom.
                drawRect(
                    Brush.verticalGradient(
                        0f to primary.copy(alpha = .34f),
                        .18f to primary.copy(alpha = .16f),
                        .46f to Color.Transparent,
                    ),
                )
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(primary.copy(alpha = .30f), primary.copy(alpha = .10f), Color.Transparent),
                        center = Offset(size.width * .5f, -size.height * .04f),
                        radius = size.maxDimension * .5f,
                    ),
                )
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(secondary.copy(alpha = .22f), secondary.copy(alpha = .06f), Color.Transparent),
                        center = Offset(size.width * .5f, size.height * 1.02f),
                        radius = size.maxDimension * .45f,
                    ),
                )
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, Background0.copy(alpha = .16f))))
                // Faint film grain.
                drawRect(GrainBrush, alpha = .07f, blendMode = androidx.compose.ui.graphics.BlendMode.Overlay)
            }
    val glassBackdrop = if (liquidGlass) rememberGlassBackdrop() else null
    androidx.compose.runtime.CompositionLocalProvider(LocalPageTint provides pageTint) {
    if (glassBackdrop != null && hazeState == null) {
        Box(modifier.fillMaxSize()) {
            // Sibling layer: the glass controls sample only the background, never themselves.
            Box(Modifier.matchParentSize().prismalGlassLayer(glassBackdrop).then(CachedBackground).then(backgroundModifier))
            androidx.compose.runtime.CompositionLocalProvider(LocalGlassBackdrop provides glassBackdrop) { content() }
        }
    } else if (hazeState == null) {
        Box(modifier.fillMaxSize()) {
            Box(Modifier.matchParentSize().then(CachedBackground).then(backgroundModifier))
            content()
        }
    } else {
        Box(modifier.fillMaxSize()) {
            // Capture only the backdrop, never the foreground sheet or its text.
            // Sibling source/effect layers avoid recursive blur and stay viewport-sized.
            Box(
                Modifier.matchParentSize().hazeSource(hazeState)
                    .then(if (glassBackdrop != null) Modifier.prismalGlassLayer(glassBackdrop) else Modifier)
                    .then(CachedBackground)
                    .then(backgroundModifier),
            )
            if (blurBackdrop) {
                Box(Modifier.matchParentSize().hazeEffect(hazeState,
                    style = DetailBackdropStyle))
            }
            androidx.compose.runtime.CompositionLocalProvider(LocalGlassBackdrop provides glassBackdrop) { content() }
        }
    }
    }
}

/**
 * Rasterise the page background once into its own layer and reuse it while scrolling.
 * Otherwise its five full-screen gradient passes are redrawn every frame (measured
 * ~2 ms of GPU time per frame at 1440p on a Galaxy S23 Ultra).
 */
private val CachedBackground = Modifier.graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }

/**
 * The poster's most vivid colour as an accent for its detail page, fading in once known.
 * Null when turned off; posters without a clear colour (black and white, grey) keep the app accent.
 */
@Composable
fun rememberPosterAccent(url: String?, enabled: Boolean): Color? {
    val context = androidx.compose.ui.platform.LocalContext.current
    val target by produceState(url?.let { posterAccentCache.get(it) }, url, enabled) {
        value = if (!enabled || url == null) null else posterAccentCache.get(url) ?: extractPosterAccent(context, url)?.also { posterAccentCache.put(url, it) }
    }
    val animated by animateColorAsState(target ?: Accent, androidx.compose.animation.core.tween(500), label = "posterAccent")
    return if (enabled) animated else null
}

private val posterAccentCache = LruCache<String, Color>(24)

/**
 * The poster's dominant vivid hue: saturated pixels are grouped into twelve hue bands and the
 * heaviest band wins, so a red poster with black and white text still reads as red.
 */
private suspend fun extractPosterAccent(context: android.content.Context, url: String): Color? = withContext(Dispatchers.IO) {
    runCatching {
        val request = ImageRequest.Builder(context).data(url).size(72).allowHardware(false).build()
        val result = context.imageLoader.execute(request) as? SuccessResult ?: return@runCatching null
        val bitmap = result.image.toBitmap(72, 72)
        val weights = DoubleArray(12)
        val sums = Array(12) { DoubleArray(3) }
        val hsv = FloatArray(3)
        var counted = 0
        for (y in 0 until bitmap.height step 2) {
            for (x in 0 until bitmap.width step 2) {
                AndroidColor.colorToHSV(bitmap.getPixel(x, y), hsv)
                counted++
                // Muted tones count too (a green-tinted sky), weighted by saturation squared
                // so vivid areas still win; near-black and near-white pixels are ignored.
                if (hsv[1] < .10f || hsv[2] < .15f || hsv[2] > .97f) continue
                val band = ((hsv[0] / 30f).toInt()).coerceIn(0, 11)
                val weight = (hsv[1] * hsv[1] * hsv[2]).toDouble()
                weights[band] += weight
                sums[band][0] += hsv[1] * weight
                sums[band][1] += hsv[2] * weight
                sums[band][2] += hsv[0] * weight
            }
        }
        val best = weights.indices.maxByOrNull { weights[it] } ?: return@runCatching null
        // Too little colour overall: leave the app accent.
        if (counted == 0 || weights[best] / counted < .004) return@runCatching null
        val hue = (sums[best][2] / weights[best]).toFloat()
        val saturation = (sums[best][0] / weights[best]).toFloat().coerceIn(.45f, .75f)
        Color(AndroidColor.HSVToColor(floatArrayOf(hue, saturation, .82f)))
    }.getOrNull()
}

private suspend fun extractArtworkColors(context: android.content.Context, url: String): Pair<Color, Color>? = withContext(Dispatchers.IO) {
    runCatching {
        val request = ImageRequest.Builder(context).data(url).size(72).allowHardware(false).build()
        val result = context.imageLoader.execute(request) as? SuccessResult ?: return@runCatching null
        val bitmap = result.image.toBitmap(72, 72)
        var red = 0.0
        var green = 0.0
        var blue = 0.0
        var weightTotal = 0.0
        for (y in 0 until bitmap.height step 3) {
            for (x in 0 until bitmap.width step 3) {
                val pixel = bitmap.getPixel(x, y)
                val r = AndroidColor.red(pixel)
                val g = AndroidColor.green(pixel)
                val b = AndroidColor.blue(pixel)
                val max = maxOf(r, g, b)
                val min = minOf(r, g, b)
                val saturation = (max - min) / 255.0
                val luminance = (r + g + b) / 765.0
                if (luminance < .035 || luminance > .96) continue
                val weight = .35 + saturation * 1.4
                red += r * weight
                green += g * weight
                blue += b * weight
                weightTotal += weight
            }
        }
        if (weightTotal == 0.0) return@runCatching null
        val base = Color(
            (red / weightTotal / 255.0).toFloat(),
            (green / weightTotal / 255.0).toFloat(),
            (blue / weightTotal / 255.0).toFloat(),
        )
        val secondary = Color(
            (base.red * .62f + base.blue * .22f).coerceIn(0f, 1f),
            (base.green * .72f + .08f).coerceIn(0f, 1f),
            (base.blue * .82f + .09f).coerceIn(0f, 1f),
        )
        base to secondary
    }.getOrNull()
}
