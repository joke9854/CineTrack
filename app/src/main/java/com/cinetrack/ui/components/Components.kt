@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.cinetrack.ui.components

import android.animation.ValueAnimator
import android.os.Build
import android.util.LruCache
import android.view.HapticFeedbackConstants
import android.graphics.Color as AndroidColor
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import androidx.core.graphics.drawable.toBitmap
import com.cinetrack.R
import com.cinetrack.domain.LibraryStatus
import com.cinetrack.domain.MediaCard
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
import java.time.format.DateTimeFormatter
import java.util.Locale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import kotlin.math.abs

@Composable
fun rememberLightHapticAction(action: () -> Unit): () -> Unit {
    val view = LocalView.current
    val currentAction by rememberUpdatedState(action)
    return remember(view) {
        {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            currentAction()
        }
    }
}

fun Modifier.glass(shape: RoundedCornerShape = RoundedCornerShape(com.cinetrack.ui.theme.Radius.Large)): Modifier =
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
        .background(com.cinetrack.ui.theme.SurfacePalette.GlassSurface.copy(alpha = .27f))
        .border(
            .5.dp,
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
    padding(4.dp)
        .shadow(8.dp, CircleShape, clip = false)
        .clip(CircleShape)
        .background(com.cinetrack.ui.theme.SurfacePalette.IconSurface.copy(alpha = .50f))
        .border(.55.dp, GlassEdgeBrush, CircleShape)

fun Modifier.blueEdgeClickable(
    shape: RoundedCornerShape,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val view = LocalView.current
    val pressed by interactionSource.collectIsPressedAsState()
    val alpha by animateFloatAsState(if (pressed) .95f else 0f, tween(120), label = "blueEdgePress")
    this
        .border(1.7.dp, Accent.copy(alpha = alpha), shape)
        .combinedClickable(
            interactionSource = interactionSource,
            indication = null,
            role = Role.Button,
            onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                onClick()
            },
            onLongClick = onLongClick?.let { action ->
                {
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
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
    val hapticClick = rememberLightHapticAction(onClick)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val buttonScale by animateFloatAsState(
        targetValue = if (pressed) .91f else 1f,
        animationSpec = spring(dampingRatio = .68f, stiffness = Spring.StiffnessMediumLow),
        label = "backButtonPress",
    )
    Box(modifier.size(48.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(40.dp)
                .scale(buttonScale)
                .shadow(9.dp, CircleShape, clip = false)
                .clip(CircleShape)
                .background(com.cinetrack.ui.theme.SurfacePalette.BackControl)
                .border(.55.dp, GlassEdgeBrush, CircleShape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button,
                    onClick = hapticClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                stringResource(R.string.accessibility_back),
                tint = Color.White,
                modifier = Modifier.size(19.dp),
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

@Composable
fun AdaptiveBackground(
    artworkUrl: String? = null,
    glow: Color? = null,
    secondaryGlow: Color? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val context = LocalContext.current
    val artworkColors by produceState<Pair<Color, Color>?>(artworkUrl?.let { artworkColorCache.get(it) }, artworkUrl) {
        value = artworkUrl?.let { url -> artworkColorCache.get(url) ?: extractArtworkColors(context, url)?.also { artworkColorCache.put(url, it) } }
    }
    val primaryTarget = glow ?: artworkColors?.first ?: com.cinetrack.ui.theme.SurfacePalette.SoftText
    val secondaryTarget = secondaryGlow ?: artworkColors?.second ?: com.cinetrack.ui.theme.SurfacePalette.LightMuted
    val baseTarget = artworkColors?.first?.let {
        Color(
            (it.red * .45f + .17f).coerceIn(0f, 1f),
            (it.green * .45f + .18f).coerceIn(0f, 1f),
            (it.blue * .45f + .20f).coerceIn(0f, 1f),
        )
    } ?: if (artworkUrl.isNullOrBlank() && glow == null) com.cinetrack.ui.theme.SurfacePalette.NeutralMuted else Background1
    val primary by animateColorAsState(primaryTarget, tween(420), label = "adaptivePrimary")
    val secondary by animateColorAsState(secondaryTarget, tween(420), label = "adaptiveSecondary")
    val base by animateColorAsState(baseTarget, tween(420), label = "adaptiveBase")
    Box(
        Modifier
            .fillMaxSize()
            .background(base)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(primary.copy(alpha = .72f), primary.copy(alpha = .26f), Color.Transparent),
                        center = Offset(size.width * .08f, size.height * .08f),
                        radius = size.minDimension * .72f,
                    ),
                )
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(secondary.copy(alpha = .48f), secondary.copy(alpha = .15f), Color.Transparent),
                        center = Offset(size.width * .92f, size.height * .9f),
                        radius = size.minDimension * .82f,
                    ),
                )
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, Background0.copy(alpha = .16f))))
            },
        content = content,
    )
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
        color = Accent,

        lineHeight = 32.sp,
        fontWeight = FontWeight.Black,
        style = MaterialTheme.typography.displaySmall.copy(
            shadow = Shadow(Color.Black.copy(alpha = .82f), Offset(0f, 4f), 9f),
        ),
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
            val hapticAction = rememberLightHapticAction(onAction)
            Row(
                Modifier.clip(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Pill)).clickable(onClick = hapticAction).padding(start = 10.dp, top = 7.dp, bottom = 7.dp),
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
    width: Dp = 118.dp,
    showTitle: Boolean = true,
    showAirDate: Boolean = false,
    progress: Float? = null,
    selectedBorder: Color? = null,
    onStatus: ((LibraryStatus) -> Unit)? = null,
    onNotInterested: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    var statusPopup by remember(media.stableKey) { mutableStateOf(false) }
    val edgeAlpha by animateFloatAsState(if (pressed || statusPopup) .95f else 0f, tween(120), label = "posterEdge")
    RevealOnMount(media.stableKey, modifier.width(width)) {
    Box {
    Column(
        Modifier.semantics { role = Role.Button }.combinedClickable(
            interactionSource = interactionSource,
            indication = null,
            role = Role.Button,
            onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                onClick()
            },
            onLongClick = {
                if (onStatus != null || onNotInterested != null) {
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    statusPopup = true
                }
            },
        ),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Medium))
                .background(posterBrush(media.id))
                .then(
                    selectedBorder?.let { Modifier.border(2.dp, it, RoundedCornerShape(com.cinetrack.ui.theme.Radius.Medium)) }
                        ?: Modifier,
                )
                .border(1.8.dp, Accent.copy(alpha = edgeAlpha), RoundedCornerShape(com.cinetrack.ui.theme.Radius.Medium)),
        ) {
            if (!media.posterUrl.isNullOrBlank()) {
                AsyncImage(
                    model = media.posterUrl,
                    contentDescription = media.title,
                    // TMDB posters already use a 2:3 frame. FillBounds preserves the
                    // entire artwork while also eliminating the empty bars produced by Fit.
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    media.title.take(1),
                    color = com.cinetrack.ui.theme.WhiteBright,
                    fontSize = 46.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            if (showAirDate) {
                val dateLabel = remember(media.releaseDate) { formattedAirDate(media.releaseDate) }
                if (dateLabel.isNotBlank()) {
                    Text(
                        dateLabel,
                        color = Color.White,
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        lineHeight = 10.sp,
                        fontWeight = FontWeight.Black,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.align(Alignment.TopStart).padding(8.dp).clip(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Compact))
                            .background(Accent).padding(horizontal = 8.dp, vertical = 5.dp),
                    )
                }
            }
            if (media.watched || media.status == LibraryStatus.COMPLETED) {
                StateBadge(Success, Icons.Filled.CheckCircle, stringResource(R.string.watched), Modifier.align(Alignment.TopEnd))
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
            if (progress != null && progress > 0f && !media.watched && media.status != LibraryStatus.COMPLETED) {
                CircularProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(7.dp).size(27.dp),
                    color = Success,
                    trackColor = com.cinetrack.ui.theme.SurfacePalette.BlueOverlay,
                    strokeWidth = 2.5.dp,
                )
            }
        }
        if (showTitle) {
            Spacer(Modifier.height(7.dp))
            Text(media.title.uppercase(), color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (media.year.isNotBlank()) Text(media.year, color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
        }
    }
    MediaStatusPopup(
        expanded = statusPopup,
        currentStatus = media.status,
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
    Box(modifier.padding(7.dp).size(27.dp).clip(CircleShape).background(color).semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
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
    progressByKey: Map<String, Float> = emptyMap(),
    onStatus: ((MediaCard, LibraryStatus) -> Unit)? = null,
    onNotInterested: ((MediaCard) -> Unit)? = null,
) {
    LazyRow(contentPadding = contentPadding, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(items.distinctBy(MediaCard::stableKey), key = MediaCard::stableKey) {
            MediaPoster(
                it,
                showAirDate = showAirDate,
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
) {
    var rendered by remember { mutableStateOf(expanded) }
    val view = LocalView.current
    val popupAlpha by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(if (expanded) 175 else 215, easing = FastOutSlowInEasing),
        label = "statusPopupAlpha",
    )
    val popupScale by animateFloatAsState(
        targetValue = if (expanded) 1f else .93f,
        animationSpec = tween(if (expanded) 205 else 225, easing = FastOutSlowInEasing),
        label = "statusPopupScale",
    )
    LaunchedEffect(expanded) {
        if (expanded) rendered = true
        else if (rendered) {
            delay(235)
            rendered = false
        }
    }
    val popupShape = RoundedCornerShape(com.cinetrack.ui.theme.Radius.Large)
    DropdownMenu(
        expanded = rendered,
        onDismissRequest = onDismiss,
        modifier = Modifier.width(226.dp).graphicsLayer {
            alpha = popupAlpha
            scaleX = popupScale
            scaleY = popupScale
            translationY = -6.dp.toPx() * (1f - popupAlpha)
            transformOrigin = TransformOrigin(.5f, 0f)
        },
        shape = popupShape,
        containerColor = com.cinetrack.ui.theme.SurfacePalette.ModalSurface,
        tonalElevation = 0.dp,
        shadowElevation = 18.dp,
        border = BorderStroke(.8.dp, AccentLight.copy(alpha = .24f)),
    ) {
        val choices = listOf(
            LibraryStatus.PLAN_TO_WATCH to stringResource(R.string.plan_to_watch),
            LibraryStatus.WATCHING to stringResource(R.string.in_progress),
            LibraryStatus.PAUSED to stringResource(R.string.paused),
            LibraryStatus.COMPLETED to stringResource(R.string.completed),
            LibraryStatus.DROPPED to stringResource(R.string.dropped),
        )
        choices.forEach { (status, label) ->
            val selected = currentStatus == status
            DropdownMenuItem(
                text = { Text(label, color = if (selected) libraryStatusColor(status) else TextPrimary, fontWeight = FontWeight.Bold) },
                leadingIcon = { Icon(libraryStatusIcon(status), null, tint = libraryStatusColor(status), modifier = Modifier.size(18.dp)) },
                trailingIcon = { if (selected) Icon(Icons.Filled.Check, null, tint = libraryStatusColor(status), modifier = Modifier.size(17.dp)) },
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    onStatus(if (selected) LibraryStatus.NONE else status)
                },
            )
        }
        if (onNotInterested != null) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.show_less_like_this), color = TextPrimary, fontWeight = FontWeight.Bold) },
                leadingIcon = { Icon(Icons.Filled.ThumbDown, null, tint = TextMuted, modifier = Modifier.size(18.dp)) },
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    onNotInterested()
                },
            )
        }
    }
}

@Composable
fun PrimaryAction(
    text: String,
    icon: ImageVector = Icons.Filled.PlayArrow,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = Accent,
    onClick: () -> Unit,
) {
    val hapticClick = rememberLightHapticAction(onClick)
    Row(
        modifier
            .height(46.dp)
            .glass(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Pill))
            .background(
                if (enabled) SolidColor(containerColor.copy(alpha = .82f))
                else SolidColor(com.cinetrack.ui.theme.GlassSubtle),
            )
            .border(.7.dp, if (enabled) containerColor.copy(alpha = .72f) else com.cinetrack.ui.theme.SurfacePalette.DisabledControl.copy(alpha = .22f), RoundedCornerShape(com.cinetrack.ui.theme.Radius.Pill))
            .clickable(enabled = enabled, onClick = hapticClick)
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (enabled) Color.White else TextMuted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(7.dp))
        Text(text, color = if (enabled) Color.White else TextMuted, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.ExtraBold, maxLines = 1)
    }
}

@Composable
fun BrandMark(size: Dp = 84.dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * .23f))
            .background(com.cinetrack.ui.theme.SurfacePalette.PlaybackSurface.copy(alpha = .82f))
            .border(.7.dp, AccentLight.copy(alpha = .28f), RoundedCornerShape(size * .23f)),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            progress = { .76f },
            modifier = Modifier.size(size * .68f),
            color = AccentLight,
            trackColor = com.cinetrack.ui.theme.Glass,
            strokeWidth = size * .055f,
        )
        Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(size * .34f))
    }
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
) {
    val view = LocalView.current
    val motionEnabled = remember { Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled() }
    val labelFraction by animateFloatAsState(
        targetValue = if (compact) 0f else 1f,
        animationSpec = if (motionEnabled) tween(260, easing = FastOutSlowInEasing) else tween(0),
        label = "navLabels",
    )
    val height by animateDpAsState(
        if (compact) 54.dp else 64.dp,
        if (motionEnabled) tween(260, easing = FastOutSlowInEasing) else tween(0),
        label = "navHeight",
    )
    val sidePadding by animateDpAsState(
        if (compact) 58.dp else 28.dp,
        if (motionEnabled) tween(280, easing = FastOutSlowInEasing) else tween(0),
        label = "navSidePadding",
    )
    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = sidePadding)
            .height(height)
            .shadow(15.dp, RoundedCornerShape(com.cinetrack.ui.theme.Radius.Pill), clip = false)
            .clip(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Pill))
            .then(if (hazeState != null) Modifier.hazeEffect(hazeState, style = NavGlassStyle) else Modifier.background(com.cinetrack.ui.theme.SurfacePalette.NavSurface.copy(alpha = .78f)))
            .border(.65.dp, GlassEdgeBrush, RoundedCornerShape(com.cinetrack.ui.theme.Radius.Pill))
            .padding(horizontal = 7.dp, vertical = 5.dp),
    ) {
        Row(Modifier.fillMaxSize()) {
            items.forEachIndexed { index, item ->
                val selected = index == selectedIndex
                val selectedAlpha by animateFloatAsState(
                    if (selected) 1f else 0f,
                    if (motionEnabled) tween(220, easing = FastOutSlowInEasing) else tween(0),
                    label = "navSelection",
                )
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Pill))
                        .background(Accent.copy(alpha = .18f * selectedAlpha))
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
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

@Composable
fun SharedGlassSheet(
    onDismiss: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    content: @Composable () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        // Padding the ModalBottomSheet itself also pads/clips its popup window on
        // some One UI builds. Keep the window full-width; sheet content supplies
        // its own safe horizontal inset.
        modifier = Modifier.fillMaxWidth(),
        sheetState = sheetState,
        containerColor = com.cinetrack.ui.theme.SurfacePalette.InkSurface.copy(alpha = .90f),
        contentColor = TextPrimary,
        scrimColor = Color.Black.copy(alpha = .62f),
        shape = RoundedCornerShape(topStart = com.cinetrack.ui.theme.Radius.Sheet, topEnd = com.cinetrack.ui.theme.Radius.Sheet),
        dragHandle = {
            Box(Modifier.fillMaxWidth().padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.width(42.dp).height(5.dp).clip(CircleShape).background(com.cinetrack.ui.theme.GlassDisabled))
            }
        },
    ) {
        content()
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
fun LibraryStatusSheet(
    media: MediaCard,
    onDismiss: () -> Unit,
    onStatus: (LibraryStatus) -> Unit,
) {
    val view = LocalView.current
    val hapticDismiss = rememberLightHapticAction(onDismiss)
    SharedGlassSheet(onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(stringResource(R.string.add_to_library), modifier = Modifier.fillMaxWidth(), color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Text(stringResource(R.string.choose_library_status), modifier = Modifier.fillMaxWidth(), color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.height(13.dp))
            val choices = listOf(
                Triple(LibraryStatus.PLAN_TO_WATCH, stringResource(R.string.plan_to_watch), Icons.Filled.Bookmark),
                Triple(LibraryStatus.WATCHING, stringResource(R.string.in_progress), Icons.Filled.Visibility),
                Triple(LibraryStatus.PAUSED, stringResource(R.string.paused), Icons.Filled.Pause),
                Triple(LibraryStatus.COMPLETED, stringResource(R.string.completed), Icons.Filled.Check),
                Triple(LibraryStatus.DROPPED, stringResource(R.string.dropped), Icons.Filled.VisibilityOff),
            )
            choices.forEach { (status, label, icon) ->
                val selected = media.status == status
                val statusColor = libraryStatusColor(status)
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp).glass(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Medium))
                        .background(if (selected) statusColor.copy(alpha = .15f) else Color.Transparent)
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            onStatus(if (selected) LibraryStatus.NONE else status)
                        }
                        .padding(horizontal = 12.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(36.dp).clip(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Small)).background(statusColor.copy(alpha = .20f)), contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = statusColor, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text(label, color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text(
                            when (status) {
                                LibraryStatus.PLAN_TO_WATCH -> "Save for later"
                                LibraryStatus.WATCHING -> "Keep it in your progress"
                                LibraryStatus.PAUSED -> stringResource(R.string.paused_description)
                                LibraryStatus.COMPLETED -> stringResource(R.string.watched)
                                LibraryStatus.DROPPED -> "Hide from upcoming items"
                                else -> ""
                            },
                            color = TextMuted,
                            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        )
                    }
                    Box(Modifier.size(22.dp).clip(CircleShape).background(if (selected) statusColor else com.cinetrack.ui.theme.SurfacePalette.NeutralFill), contentAlignment = Alignment.Center) {
                        if (selected) Icon(Icons.Filled.Check, null, tint = com.cinetrack.ui.theme.SurfacePalette.SuccessInk, modifier = Modifier.size(15.dp))
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().height(42.dp).glass(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Pill)).clickable(onClick = hapticDismiss),
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
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = AccentLight)
    }
}
