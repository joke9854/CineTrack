@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.cinetrack.ui.components

import androidx.compose.ui.graphics.asImageBitmap
import android.animation.ValueAnimator
import android.os.Build
import android.util.LruCache
import android.graphics.Color as AndroidColor
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.graphics.toArgb
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import androidx.core.graphics.drawable.toBitmap
import com.cinetrack.R
import com.cinetrack.domain.LibraryStatus
import com.cinetrack.domain.MediaCard
import com.cinetrack.domain.MediaType
import com.cinetrack.ui.theme.Accent
import com.cinetrack.ui.theme.AccentLight
import com.cinetrack.ui.theme.Background0
import com.cinetrack.ui.theme.Background1
import com.cinetrack.ui.theme.DesignTokens
import com.cinetrack.ui.theme.GlassBorder
import com.cinetrack.ui.theme.Gold
import com.cinetrack.ui.theme.Success
import com.cinetrack.ui.theme.StatusDropped
import com.cinetrack.ui.theme.StatusPaused
import com.cinetrack.ui.theme.StatusPlanned
import com.cinetrack.ui.theme.StatusWatching
import com.cinetrack.ui.theme.TextMuted
import com.cinetrack.ui.theme.TextPrimary
import com.cinetrack.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.Locale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.hazeEffect
import com.styropyr0.prismal.sources.prismalGlassLayer
import kotlin.math.abs

@Composable
fun rememberUiAction(action: () -> Unit): () -> Unit {
    val currentAction by rememberUpdatedState(action)
    return remember {
        {
            currentAction()
        }
    }
}

fun Modifier.glass(
    shape: RoundedCornerShape = RoundedCornerShape(com.cinetrack.ui.theme.Radius.Large),
    fill: Color = GlassMaterial.Card,
): Modifier =
    drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        // drawOutline is not available in every Compose UI version supported by
        // this project. Cache a Path instead and render it with stable DrawScope
        // APIs so rounded and generic shapes retain the same external depth.
        val outlinePath = when (outline) {
            is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
            is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
            is Outline.Generic -> outline.path
        }
        // Two cached passes keep the exterior lift while limiting GPU overdraw
        // on poster-heavy lists during cold-start image decoding.
        val shadowSteps = listOf(
            2.dp.toPx() to .072f,
            7.dp.toPx() to .026f,
        )
        onDrawBehind {
            // Draw depth outside the material itself. Avoiding Android's hardware
            // shadow layer also prevents the pale rectangular tiles seen on some
            // Samsung/Android 16 renderers.
            shadowSteps.asReversed().forEach { (offset, alpha) ->
                withTransform({ translate(0f, offset) }) {
                    drawPath(outlinePath, Color.Black.copy(alpha = alpha))
                }
            }
        }
    }
        .clip(shape)
        // Keep chromatic gradients on the page background. Controls use a
        // neutral, low-opacity material so artwork colour can pass through.
        .background(fill)
        .border(
            .8.dp,
            GlassEdgeBrush,
            shape,
        )

/**
 * Small circular controls need room around the material for their shadow.
 * Applying the regular glass modifier directly to a fixed-size IconButton
 * compresses the shadow into the circle and creates the dark inner rings seen
 * in the supplied screenshots.
 */
fun Modifier.glassIcon(): Modifier =
    padding(6.dp)
        .shadow(8.dp, CircleShape, clip = false)
        .clip(CircleShape)
        .background(GlassMaterial.Control)
        .border(.55.dp, ControlEdgeBrush, CircleShape)

fun Modifier.blueEdgeClickable(
    shape: RoundedCornerShape,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val alpha by animateFloatAsState(if (pressed) .95f else 0f, tween(com.cinetrack.ui.theme.Motion.Short), label = "blueEdgePress")
    this
        .border(1.7.dp, Accent.copy(alpha = alpha), shape)
        .combinedClickable(
            interactionSource = interactionSource,
            indication = null,
            role = Role.Button,
            onClick = {
                onClick()
            },
            onLongClick = onLongClick?.let { action ->
                {
                    action()
                }
            },
        )
}

@Composable
fun GlassBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clickAction = rememberUiAction(onClick)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val buttonScale by animateFloatAsState(
        targetValue = if (pressed) .91f else 1f,
        animationSpec = spring(dampingRatio = .68f, stiffness = Spring.StiffnessMediumLow),
        label = "backButtonPress",
    )
    Box(modifier.size(52.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(42.dp)
                .scale(buttonScale)
                .liquidGlass(surface = GlassMaterial.Control, refraction = 6.dp) {
                    shadow(8.dp, CircleShape, clip = false)
                        .clip(CircleShape)
                        .background(GlassMaterial.Control)
                        .border(.55.dp, ControlEdgeBrush, CircleShape)
                }
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button,
                    onClick = clickAction,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                stringResource(R.string.accessibility_back),
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

fun libraryStatusColor(status: LibraryStatus): Color = when (status) {
    LibraryStatus.WATCHING -> StatusWatching
    LibraryStatus.PLAN_TO_WATCH -> StatusPlanned
    LibraryStatus.PAUSED -> StatusPaused
    LibraryStatus.COMPLETED -> Success
    LibraryStatus.DROPPED -> StatusDropped
    LibraryStatus.NONE -> Accent
}

fun libraryStatusIcon(status: LibraryStatus): ImageVector = when (status) {
    LibraryStatus.WATCHING -> Icons.Filled.Visibility
    LibraryStatus.PLAN_TO_WATCH -> Icons.Filled.Bookmark
    LibraryStatus.PAUSED -> Icons.Filled.Pause
    LibraryStatus.COMPLETED -> Icons.Filled.Check
    LibraryStatus.DROPPED -> Icons.Filled.VisibilityOff
    LibraryStatus.NONE -> Icons.Filled.Bookmark
}

/**
 * Gives content restored from the local cache a calm entrance instead of
 * snapping into place when a database flow or background enrichment finishes.
 */
@Composable
fun RevealOnMount(
    animationKey: Any?,
    modifier: Modifier = Modifier,
    delayMillis: Int = 0,
    content: @Composable () -> Unit,
) {
    // Keep list rows layer-free. The app-level launch/page transitions provide
    // continuity while LazyColumn/LazyRow items can now be recycled cheaply.
    Box(modifier) { content() }
}

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
    val primary by animateColorAsState(primaryTarget, tween(com.cinetrack.ui.theme.Motion.Extended), label = "adaptivePrimary")
    val secondary by animateColorAsState(secondaryTarget, tween(com.cinetrack.ui.theme.Motion.Extended), label = "adaptiveSecondary")
    val base by animateColorAsState(baseTarget, tween(com.cinetrack.ui.theme.Motion.Extended), label = "adaptiveBase")
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
        val bitmap = result.drawable.toBitmap(72, 72)
        val weights = DoubleArray(12)
        val sums = Array(12) { DoubleArray(3) }
        val hsv = FloatArray(3)
        var counted = 0
        for (y in 0 until bitmap.height step 2) {
            for (x in 0 until bitmap.width step 2) {
                AndroidColor.colorToHSV(bitmap.getPixel(x, y), hsv)
                counted++
                if (hsv[1] < .30f || hsv[2] < .22f) continue
                val band = ((hsv[0] / 30f).toInt()).coerceIn(0, 11)
                val weight = (hsv[1] * hsv[2]).toDouble()
                weights[band] += weight
                sums[band][0] += hsv[1] * weight
                sums[band][1] += hsv[2] * weight
                sums[band][2] += hsv[0] * weight
            }
        }
        val best = weights.indices.maxByOrNull { weights[it] } ?: return@runCatching null
        // Too little colour overall: leave the app accent.
        if (counted == 0 || weights[best] / counted < .035) return@runCatching null
        val hue = (sums[best][2] / weights[best]).toFloat()
        val saturation = (sums[best][0] / weights[best]).toFloat().coerceIn(.45f, .75f)
        Color(AndroidColor.HSVToColor(floatArrayOf(hue, saturation, .82f)))
    }.getOrNull()
}

private suspend fun extractArtworkColors(context: android.content.Context, url: String): Pair<Color, Color>? = withContext(Dispatchers.IO) {
    runCatching {
        val request = ImageRequest.Builder(context).data(url).size(72).allowHardware(false).build()
        val result = context.imageLoader.execute(request) as? SuccessResult ?: return@runCatching null
        val bitmap = result.drawable.toBitmap(72, 72)
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

@Composable
fun PageTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        modifier = modifier,
        color = TextPrimary,

        lineHeight = 34.sp,
        fontWeight = FontWeight.ExtraBold,
        style = MaterialTheme.typography.displaySmall,
    )
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            color = TextPrimary,

            lineHeight = 21.sp,
            fontWeight = FontWeight.ExtraBold,
            style = MaterialTheme.typography.titleMedium.copy(
                shadow = Shadow(Color.Black.copy(alpha = .62f), Offset(0f, 2.5f), 6f),
            ),
            modifier = Modifier.weight(1f),
        )
        if (actionLabel != null && onAction != null) {
            val primaryAction = rememberUiAction(onAction)
            Row(
                Modifier.clip(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Pill)).clickable(onClick = primaryAction).padding(start = com.cinetrack.ui.theme.Spacing.sm, top = com.cinetrack.ui.theme.Spacing.sm, bottom = com.cinetrack.ui.theme.Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(actionLabel, color = TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(2.dp))
                Icon(Icons.Filled.ChevronRight, null, tint = TextSecondary, modifier = Modifier.size(15.dp))
            }
        }
    }
}

@Composable
fun MediaPoster(
    media: MediaCard,
    modifier: Modifier = Modifier,
    width: Dp = com.cinetrack.ui.theme.LocalCardAppearance.current.posterWidthDp.dp,
    showTitle: Boolean = true,
    showYear: Boolean = true,
    showAirDate: Boolean = false,
    progress: Float? = null,
    selectedBorder: Color? = null,
    selectionMode: Boolean = false,
    onStatus: ((LibraryStatus) -> Unit)? = null,
    onNotInterested: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    var statusPopup by remember(media.stableKey) { mutableStateOf(false) }
    val posterSlot = rememberPosterSlot()
    val edgeAlpha by animateFloatAsState(if (pressed || statusPopup) .95f else 0f, tween(com.cinetrack.ui.theme.Motion.Short), label = "posterEdge")
    RevealOnMount(media.stableKey, modifier.width(width)) {
    Box {
    Column(
        Modifier.semantics {
            role = if (selectionMode) Role.Checkbox else Role.Button
            if (selectionMode) selected = selectedBorder != null
        }.combinedClickable(
            interactionSource = interactionSource,
            indication = null,
            role = if (selectionMode) Role.Checkbox else Role.Button,
            onClick = {
                SharedPosterSource.active = posterSlot
                onClick()
            },
            onLongClick = {
                if (onStatus != null || onNotInterested != null) {
                    statusPopup = true
                }
            },
        ),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(com.cinetrack.ui.theme.LocalCardAppearance.current.posterAspectRatio)
                .sharedPoster(media.stableKey, posterSlot)
                .clip(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Medium))
                .background(posterBrush(media.id))
                .then(
                    selectedBorder?.let { Modifier.border(2.dp, it, RoundedCornerShape(com.cinetrack.ui.theme.Radius.Medium)) }
                        ?: Modifier,
                )
                .border(1.8.dp, Accent.copy(alpha = edgeAlpha), RoundedCornerShape(com.cinetrack.ui.theme.Radius.Medium)),
        ) {
            if (!media.posterUrl.isNullOrBlank()) {
                SharedPosterImage(media.posterUrl, media.title, posterSlot, Modifier.fillMaxSize(), ContentScale.Crop)
            } else {
                Text(
                    media.title.take(1),
                    color = com.cinetrack.ui.theme.WhiteBright,
                    fontSize = 46.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            if (showAirDate && !selectionMode) {
                val dateLabel = remember(media.releaseDate) { formattedAirDate(media.releaseDate) }
                if (dateLabel.isNotBlank()) {
                    Text(
                        dateLabel,
                        color = Color.White,
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        lineHeight = 10.sp,
                        fontWeight = FontWeight.Black,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.align(Alignment.TopStart).padding(com.cinetrack.ui.theme.Spacing.sm).clip(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Compact))
                            .background(Accent).padding(horizontal = com.cinetrack.ui.theme.Spacing.sm, vertical = com.cinetrack.ui.theme.Spacing.xs),
                    )
                }
            }
            if (selectionMode) {
                StateBadge(if (selectedBorder != null) Accent else com.cinetrack.ui.theme.SurfacePalette.DeepOverlay,
                    if (selectedBorder != null) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                    stringResource(if (selectedBorder != null) R.string.poster_selected else R.string.poster_not_selected),
                    Modifier.align(Alignment.TopEnd))
            } else if (media.watched || media.status == LibraryStatus.COMPLETED) {
                StateBadge(
                    Success,
                    Icons.Filled.CheckCircle,
                    stringResource(if (media.type == MediaType.MOVIE) R.string.watched_movie else R.string.watched_tv),
                    Modifier.align(Alignment.TopEnd),
                )
            } else if (media.status != LibraryStatus.NONE) {
                val stateIcon = when (media.status) {
                    LibraryStatus.WATCHING -> Icons.Filled.Visibility
                    LibraryStatus.PLAN_TO_WATCH -> Icons.Filled.Bookmark
                    LibraryStatus.PAUSED -> Icons.Filled.Pause
                    LibraryStatus.DROPPED -> Icons.Filled.VisibilityOff
                    else -> Icons.Filled.Bookmark
                }
                StateBadge(libraryStatusColor(media.status), stateIcon, stringResource(R.string.in_library), Modifier.align(Alignment.TopEnd))
            }
            if (!selectionMode && progress != null && progress > 0f && !media.watched && media.status != LibraryStatus.COMPLETED) {
                CircularProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(com.cinetrack.ui.theme.Spacing.sm).size(27.dp),
                    color = Success,
                    trackColor = com.cinetrack.ui.theme.SurfacePalette.BlueOverlay,
                    strokeWidth = 2.5.dp,
                )
            }
        }
        if (showTitle) {
            Spacer(Modifier.height(7.dp))
            Text(media.title, color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (showYear && media.year.isNotBlank()) Text(media.year, color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
        }
    }
    MediaStatusPopup(
        expanded = statusPopup,
        currentStatus = media.status,
        media = media,
        onDismiss = { statusPopup = false },
        onStatus = { status -> onStatus?.invoke(status); statusPopup = false },
        onNotInterested = onNotInterested?.let { action -> { action(); statusPopup = false } },
    )
    }
    }
}

private fun formattedAirDate(raw: String?): String = runCatching {
    val date = LocalDate.parse(raw?.take(10))
    date.format(com.cinetrack.ui.UiDateFormatters.current.dayMonth).uppercase(Locale.getDefault())
}.getOrDefault("")

@Composable
private fun StateBadge(color: Color, icon: ImageVector, label: String, modifier: Modifier) {
    Box(modifier.padding(com.cinetrack.ui.theme.Spacing.sm).size(27.dp).clip(CircleShape).background(color).semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(16.dp))
    }
}

private fun posterBrush(seed: Int): Brush {
    val palettes = listOf(
        listOf(com.cinetrack.ui.theme.SurfacePalette.PosterOrange, com.cinetrack.ui.theme.SurfacePalette.PosterBlue, com.cinetrack.ui.theme.SurfacePalette.WarmPosterShadow),
        listOf(com.cinetrack.ui.theme.SurfacePalette.PosterMint, com.cinetrack.ui.theme.SurfacePalette.PosterCyan, com.cinetrack.ui.theme.SurfacePalette.CoolPosterShadow),
        listOf(com.cinetrack.ui.theme.SurfacePalette.LavenderText, com.cinetrack.ui.theme.SurfacePalette.PosterGray, com.cinetrack.ui.theme.SurfacePalette.WarmSurface),
        listOf(com.cinetrack.ui.theme.SurfacePalette.PosterLilac, com.cinetrack.ui.theme.SurfacePalette.PosterIndigo, com.cinetrack.ui.theme.SurfacePalette.VioletDeep),
        listOf(com.cinetrack.ui.theme.SurfacePalette.PosterRose, com.cinetrack.ui.theme.SurfacePalette.PosterPlum, com.cinetrack.ui.theme.SurfacePalette.VioletPosterShadow),
    )
    return Brush.linearGradient(palettes[abs(seed) % palettes.size])
}

@Composable
fun MediaRail(
    items: List<MediaCard>,
    onMedia: (MediaCard) -> Unit,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp),
    showAirDate: Boolean = false,
    showYear: Boolean = true,
    progressByKey: Map<String, Float> = emptyMap(),
    onStatus: ((MediaCard, LibraryStatus) -> Unit)? = null,
    onNotInterested: ((MediaCard) -> Unit)? = null,
) {
    LazyRow(contentPadding = contentPadding, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(items.distinctBy(MediaCard::stableKey), key = MediaCard::stableKey) {
            MediaPoster(
                it,
                showAirDate = showAirDate,
                showYear = showYear,
                progress = progressByKey[it.stableKey],
                onStatus = onStatus?.let { callback -> { status -> callback(it, status) } },
                onNotInterested = onNotInterested?.let { callback -> { callback(it) } },
                onClick = { onMedia(it) },
            )
        }
    }
}

@Composable
fun MediaStatusPopup(
    expanded: Boolean,
    currentStatus: LibraryStatus,
    onDismiss: () -> Unit,
    onStatus: (LibraryStatus) -> Unit,
    onNotInterested: (() -> Unit)? = null,
    media: MediaCard? = null,
) {
    MediaContextMenu(expanded, media, currentStatus, onDismiss, onStatus, onNotInterested)
}

@Composable
fun PrimaryAction(
    text: String,
    icon: ImageVector = Icons.Filled.PlayArrow,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = Accent,
    compact: Boolean = false,
    liveGlass: Boolean = false,
    hazeState: HazeState? = null,
    onClick: () -> Unit,
) {
    val clickAction = rememberUiAction(onClick)
    val liveFallback = if (liveGlass) Modifier.liveActionGlass(hazeState, containerColor == Success) else Modifier
    Row(
        modifier
            .then(if (compact) Modifier.heightIn(min = 52.dp) else Modifier.height(52.dp))
            .then(if (liveGlass) Modifier.liquidGlass(
                surface = if (containerColor == Success) Success.copy(alpha = .50f) else (com.cinetrack.ui.theme.LocalDetailAccent.current ?: Accent).copy(alpha = .42f),
                refraction = 10.dp,
                under = GlassMaterial.DetailSurface,
            ) { then(liveFallback) } else Modifier
            .glass(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Pill))
            .background(
                if (enabled) SolidColor(containerColor.copy(alpha = .82f))
                else SolidColor(com.cinetrack.ui.theme.GlassSubtle),
            )
            .border(.7.dp, if (enabled) containerColor.copy(alpha = .72f) else com.cinetrack.ui.theme.SurfacePalette.DisabledControl.copy(alpha = .22f), RoundedCornerShape(com.cinetrack.ui.theme.Radius.Pill)))
            .clickable(enabled = enabled, onClick = clickAction)
            .padding(horizontal = if (compact) 8.dp else com.cinetrack.ui.theme.Spacing.lg, vertical = if (compact) 8.dp else 0.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (enabled) Color.White else TextMuted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(7.dp))
        Text(text, modifier = if (compact) Modifier.weight(1f, fill = false) else Modifier,
            color = if (enabled) Color.White else TextMuted, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.ExtraBold, maxLines = if (compact) 2 else 1,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun BrandMark(size: Dp = 84.dp, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Image(
        painter = androidx.compose.ui.res.painterResource(com.cinetrack.R.drawable.ic_brand_mark),
        contentDescription = "CineTrack",
        modifier = modifier.size(size).clip(RoundedCornerShape(size * .23f)),
    )
}

data class BottomNavItem(
    val route: String,
    val label: String,
    val outlineIcon: ImageVector,
    val filledIcon: ImageVector,
)

@Composable
fun LiquidBottomNav(
    items: List<BottomNavItem>,
    selectedIndex: Int,
    compact: Boolean,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    /** The recorded page: liquid glass bar and a glass droplet on the selected tab. */
    glassBackdrop: com.styropyr0.prismal.PrismalBackdrop? = null,
) {
    val motionEnabled = remember { Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled() }
    val labelFraction by animateFloatAsState(
        targetValue = if (compact) 0f else 1f,
        animationSpec = if (motionEnabled) tween(com.cinetrack.ui.theme.Motion.Medium, easing = FastOutSlowInEasing) else tween(com.cinetrack.ui.theme.Motion.Instant),
        label = "navLabels",
    )
    val height by animateDpAsState(
        if (compact) 54.dp else 64.dp,
        if (motionEnabled) tween(com.cinetrack.ui.theme.Motion.Medium, easing = FastOutSlowInEasing) else tween(com.cinetrack.ui.theme.Motion.Instant),
        label = "navHeight",
    )
    val sidePadding by animateDpAsState(
        if (compact) 58.dp else 28.dp,
        if (motionEnabled) tween(com.cinetrack.ui.theme.Motion.Medium, easing = FastOutSlowInEasing) else tween(com.cinetrack.ui.theme.Motion.Instant),
        label = "navSidePadding",
    )
    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = sidePadding)
            .height(height)
            .shadow(15.dp, RoundedCornerShape(com.cinetrack.ui.theme.Radius.Pill), clip = false)
            .liquidGlass(
                surface = Background0.copy(alpha = .50f),
                refraction = 16.dp,
                blur = 14.dp,
                shadow = false,
                backdrop = glassBackdrop,
            ) {
                clip(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Pill))
                    .then(if (hazeState != null) Modifier.hazeEffect(hazeState, style = NavGlassStyle) else Modifier.background(GlassMaterial.OverlayFallback))
                    .border(.6.dp, ControlEdgeBrush, RoundedCornerShape(com.cinetrack.ui.theme.Radius.Pill))
            }
            .padding(horizontal = com.cinetrack.ui.theme.Spacing.sm, vertical = com.cinetrack.ui.theme.Spacing.xs),
    ) {
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
            // One selection droplet slides between tabs instead of each tab fading its own fill.
            val itemWidth = maxWidth / items.size.coerceAtLeast(1)
            val dropletX by animateDpAsState(
                itemWidth * selectedIndex,
                if (motionEnabled) spring(dampingRatio = .72f, stiffness = Spring.StiffnessMediumLow) else tween(com.cinetrack.ui.theme.Motion.Instant),
                label = "navDroplet",
            )
            Box(
                Modifier
                    .offset { androidx.compose.ui.unit.IntOffset(dropletX.roundToPx(), 0) }
                    .width(itemWidth)
                    .fillMaxHeight()
                    .liquidGlass(
                        surface = Accent.copy(alpha = .24f),
                        refraction = 10.dp,
                        under = Background0.copy(alpha = .38f),
                        blur = 2.dp,
                        shadow = false,
                        backdrop = glassBackdrop,
                    ) { clip(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Pill)).background(Accent.copy(alpha = .18f)) },
            )
        Row(Modifier.fillMaxSize()) {
            items.forEachIndexed { index, item ->
                val selected = index == selectedIndex
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Pill))
                        .clickable {
                            onSelected(index)
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        if (selected) item.filledIcon else item.outlineIcon,
                        contentDescription = item.label,
                        tint = if (selected) AccentLight else com.cinetrack.ui.theme.SurfacePalette.BrightText,
                        modifier = Modifier.size(if (compact) 25.dp else 22.dp),
                    )
                    if (labelFraction > .01f) {
                        Column(
                            Modifier.graphicsLayer { alpha = labelFraction },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Spacer(Modifier.height(6.dp * labelFraction))
                            Text(
                                item.label,
                                color = if (selected) Color.White else com.cinetrack.ui.theme.SurfacePalette.LightText,
                                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                                lineHeight = 11.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                modifier = Modifier.height(12.dp * labelFraction),
                            )
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
fun SharedGlassDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    // With the app already blurred behind it, a tinted panel reads as glass and needs no
    // recording of the page; that recording made popups stutter on heavy pages.
    val appBlur = rememberAppBlurActive()
    val page = if (appBlur) null else rememberFloatingPageBackdrop()
    val state = if (page == null && !appBlur) rememberFloatingGlassState() else null
    val shape = PopupMaterial.FloatingShape
    Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        DialogWindowDim()
        PopupBackdropBlur()
        Column(Modifier.padding(horizontal = 14.dp).widthIn(max = 520.dp).fillMaxWidth()
            .then(if (appBlur) Modifier.clip(shape).background(PopupMaterial.Panel).border(.5.dp, ControlEdgeBrush, shape)
            else if (page != null) Modifier.liquidGlassPanel(page, shape)
                else Modifier.clip(shape)
                    .then(if (state != null) Modifier.hazeEffect(state, style = NavGlassStyle)
                        else Modifier.background(GlassMaterial.OverlayFallback))
                    .border(.5.dp, ControlEdgeBrush, shape))) {
            // Controls inside sit on the panel, not on the page the panel refracts.
            androidx.compose.runtime.CompositionLocalProvider(LocalGlassBackdrop provides null) { content() }
        }
    }
}

@Composable
fun SharedGlassSheet(
    onDismiss: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    content: @Composable () -> Unit,
) {
    val appBlur = rememberAppBlurActive()
    val page = if (appBlur) null else rememberFloatingPageBackdrop()
    val hazeState = if (page == null && !appBlur) rememberFloatingGlassState() else null
    // Floating: inset from the screen edges and rounded on every corner, the glass
    // refracting the page behind it, over a light scrim.
    val shape = PopupMaterial.FloatingShape
    val rim = Modifier.border(.6.dp, Brush.verticalGradient(0f to Color.White.copy(alpha = .12f), .18f to Color.White.copy(alpha = .05f), 1f to Color.White.copy(alpha = .03f)), shape)
    val handle: @Composable () -> Unit = {
        Box(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 12.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.width(36.dp).height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = .30f)))
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(),
        sheetState = sheetState,
        containerColor = Color.Transparent,
        contentColor = TextPrimary,
        scrimColor = PopupMaterial.Scrim.copy(alpha = .34f),
        shape = shape,
        tonalElevation = 0.dp,
        dragHandle = null,
    ) {
        PopupBackdropBlur(visible = sheetState.targetValue != androidx.compose.material3.SheetValue.Hidden)
        val material = when {
            appBlur -> Modifier.clip(shape).background(PopupMaterial.Panel)
            page != null -> Modifier.liquidGlassPanel(page, shape)
            hazeState != null -> Modifier.clip(shape).hazeEffect(hazeState, style = NavGlassStyle)
            else -> Modifier.clip(shape).background(GlassMaterial.OverlayFallback)
        }
        Column(
            Modifier.fillMaxWidth()
                .padding(start = PopupMaterial.FloatingMargin, end = PopupMaterial.FloatingMargin, bottom = PopupMaterial.FloatingMargin)
                .then(material)
                .then(rim)
                .padding(bottom = 18.dp),
        ) {
            handle()
            // Controls inside sit on the panel, not on the page the panel refracts.
            androidx.compose.runtime.CompositionLocalProvider(LocalGlassBackdrop provides null) { content() }
        }
    }
}

@Composable
fun LibraryStatusSheet(
    media: MediaCard,
    onDismiss: () -> Unit,
    onStatus: (LibraryStatus) -> Unit,
) {
    val dismissAction = rememberUiAction(onDismiss)
    SharedGlassSheet(onDismiss) {
        Column(Modifier.padding(horizontal = 12.dp)) {
            SheetTitle(stringResource(R.string.add_to_library))
            val choices = listOf(
                Triple(LibraryStatus.PLAN_TO_WATCH, stringResource(R.string.plan_to_watch), Icons.Filled.Bookmark),
                Triple(LibraryStatus.WATCHING, stringResource(R.string.in_progress), Icons.Filled.Visibility),
                Triple(LibraryStatus.PAUSED, stringResource(R.string.paused), Icons.Filled.Pause),
                Triple(LibraryStatus.COMPLETED, stringResource(R.string.completed), Icons.Filled.Check),
                Triple(LibraryStatus.DROPPED, stringResource(R.string.dropped), Icons.Filled.VisibilityOff),
            )
            GlassMenuGroup {
                choices.forEachIndexed { index, (status, label, icon) ->
                    val selected = media.status == status
                    GlassMenuRow(
                        label = label,
                        icon = icon,
                        tint = libraryStatusColor(status),
                        supporting = when (status) {
                            LibraryStatus.PLAN_TO_WATCH -> stringResource(R.string.status_plan_description)
                            LibraryStatus.WATCHING -> stringResource(R.string.status_watching_description)
                            LibraryStatus.PAUSED -> stringResource(R.string.paused_description)
                            LibraryStatus.COMPLETED -> stringResource(if (media.type == MediaType.MOVIE) R.string.watched_movie else R.string.watched_tv)
                            LibraryStatus.DROPPED -> stringResource(R.string.status_dropped_description)
                            else -> null
                        },
                        selected = selected,
                        divider = index < choices.lastIndex,
                    ) { onStatus(if (selected) LibraryStatus.NONE else status) }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().height(42.dp).glass(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Pill)).clickable(onClick = dismissAction),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) { Text(stringResource(android.R.string.cancel), color = TextSecondary, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
fun GlassDivider() {
    HorizontalDivider(color = com.cinetrack.ui.theme.Glass, thickness = 1.dp)
}

@Composable
fun LoadingPane(modifier: Modifier = Modifier) {
    val loading = stringResource(R.string.loading)
    Column(
        modifier.fillMaxSize().padding(com.cinetrack.ui.theme.Spacing.xl).semantics { contentDescription = loading },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(com.cinetrack.ui.theme.Spacing.md, Alignment.CenterVertically),
    ) {
        SkeletonBox(Modifier.width(160.dp).height(240.dp))
        SkeletonBox(Modifier.fillMaxWidth(.72f).height(24.dp))
        SkeletonBox(Modifier.fillMaxWidth(.9f).height(14.dp))
        SkeletonBox(Modifier.fillMaxWidth(.8f).height(14.dp))
        SkeletonBox(Modifier.fillMaxWidth().height(80.dp))
    }
}
