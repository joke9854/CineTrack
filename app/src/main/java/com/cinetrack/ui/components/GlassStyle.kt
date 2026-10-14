package com.cinetrack.ui.components

import kotlinx.coroutines.launch
import androidx.compose.ui.layout.positionInParent
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import androidx.compose.ui.platform.LocalContext
import dev.chrisbanes.haze.HazeState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.dp
import com.cinetrack.ui.theme.Background0
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.styropyr0.prismal.PrismalGlassEffectProvider
import com.styropyr0.prismal.PrismalBackdrop
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.unit.Density
import com.styropyr0.prismal.drawPrismalGlass
import com.styropyr0.prismal.effects.applyPrismalGlassEffects
import com.styropyr0.prismal.shapes.PrismalCapsule
import com.styropyr0.prismal.specular.PrismalSpecular
import com.styropyr0.prismal.sources.PrismalGlassLayer
import com.styropyr0.prismal.sources.rememberPrismalGlassLayer

// One material family: lightweight content, compact controls, and live overlays.
internal object GlassMaterial {
    val Content = com.cinetrack.ui.theme.SurfacePalette.GlassSurface.copy(alpha = .27f)
    /** Cards: a faint cool lift so they read as raised on the near-black page. */
    val Card = Color(0xFFB4C3D2).copy(alpha = .075f).compositeOver(com.cinetrack.ui.theme.SurfacePalette.GlassSurface.copy(alpha = .30f))
    val Control = com.cinetrack.ui.theme.SurfacePalette.GlassSurface.copy(alpha = .58f)
    val OverlayFallback = Background0.copy(alpha = .90f)
    val DetailSurface = Background0.copy(alpha = .58f)
}

// Shared live material for navigation and floating sheets/dialogs.
internal val NavGlassStyle = HazeStyle(
    backgroundColor = Background0,
    tint = HazeTint(Background0.copy(alpha = .58f)),
    blurRadius = 16.dp,
    noiseFactor = .03f,
    fallbackTint = HazeTint(GlassMaterial.OverlayFallback),
)

// Detail foreground sections supply their own tint; blur the backdrop only once.
internal val DetailBackdropStyle = HazeStyle(
    backgroundColor = Background0,
    tint = HazeTint(Color.Transparent),
    blurRadius = 16.dp,
    noiseFactor = .03f,
    fallbackTint = HazeTint(Color.Transparent),
)

// Cards can't take Prismal's specular, so their border imitates it (0.9 dp, 20% peak, like
// LiquidRim): light catches the top edge, the sides stay faint but hold the card's shape on
// near-black, and the bottom picks up a little bounce light.
internal val GlassEdgeBrush = Brush.verticalGradient(
    0f to Color.White.copy(alpha = .20f),
    .30f to Color.White.copy(alpha = .085f),
    .60f to Color.White.copy(alpha = .055f),
    1f to Color.White.copy(alpha = .09f),
)

/** Softer rim for controls, the nav bar and popups: the card rim's shape, one step quieter. */
internal val ControlEdgeBrush = Brush.verticalGradient(
    0f to Color.White.copy(alpha = .13f),
    .30f to Color.White.copy(alpha = .055f),
    .60f to Color.White.copy(alpha = .036f),
    1f to Color.White.copy(alpha = .059f),
)

private class FloatingGlassCapture(val state: HazeState?, val page: PageGlassBackdrop?) {
    var users by mutableIntStateOf(0)
    var pageUsers by mutableIntStateOf(0)
}

private val LocalFloatingGlass = staticCompositionLocalOf<FloatingGlassCapture?> { null }

/**
 * Capture the page only while navigation or a floating surface needs it. With [page]
 * (liquid glass) the bottom bar and sheets refract it; Haze then serves only popups.
 */
@Composable
internal fun FloatingGlassHost(
    state: HazeState?,
    page: PageGlassBackdrop?,
    navigationVisible: Boolean,
    content: @Composable (Modifier) -> Unit,
) {
    val capture = remember(state, page) { FloatingGlassCapture(state, page) }
    CompositionLocalProvider(LocalFloatingGlass provides capture) {
        val hazeNeeded = state != null && (capture.users > 0 || (navigationVisible && page == null))
        val pageNeeded = page != null && (navigationVisible || capture.pageUsers > 0)
        content(
            (if (hazeNeeded && state != null) Modifier.hazeSource(state) else Modifier)
                .pageGlassSource(if (pageNeeded) page else null),
        )
    }
}

/** The recorded page for a floating sheet or dialog; null keeps the Haze blur. */
@Composable
internal fun rememberFloatingPageBackdrop(): PageGlassBackdrop? {
    val capture = LocalFloatingGlass.current
    DisposableEffect(capture) {
        if (capture?.page != null) capture.pageUsers++
        onDispose { if (capture?.page != null) capture.pageUsers-- }
    }
    return capture?.page
}

@Composable
internal fun rememberFloatingGlassState(): HazeState? {
    val capture = LocalFloatingGlass.current
    DisposableEffect(capture) {
        if (capture?.state != null) capture.users++
        onDispose { if (capture?.state != null) capture.users-- }
    }
    return capture?.state
}

/** Match the navigation blur device policy; null retains the existing surface. */
@Composable
internal fun rememberDetailGlassState(): HazeState? {
    val context = LocalContext.current
    return remember(context) {
        val manager = context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        if (android.os.Build.VERSION.SDK_INT >= 31 && !manager.isLowRamDevice) HazeState() else null
    }
}

/** The backdrop that liquid-glass controls refract; null keeps the flat material. */
internal val LocalGlassBackdrop = staticCompositionLocalOf<PrismalBackdrop?> { null }

/** Same device policy as the live blur: Android 12+ and not low-RAM, otherwise null. */
@Composable
internal fun rememberGlassBackdrop(): PrismalGlassLayer? {
    val context = LocalContext.current
    val layer = rememberPrismalGlassLayer()
    val eligible = remember(context) { liquidGlassEligible(context) }
    return layer.takeIf { eligible }
}

internal fun liquidGlassEligible(context: android.content.Context): Boolean {
    val manager = context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
    return android.os.Build.VERSION.SDK_INT >= 31 && !manager.isLowRamDevice
}

/**
 * The page under the bottom bar and floating sheets, recorded once per frame and drawn
 * from that recording (a PrismalGlassLayer would draw the whole page twice). Offsets use
 * screen positions so sheets and dialogs in their own windows sample the right spot.
 */
@Stable
internal class PageGlassBackdrop : PrismalBackdrop {
    internal var layer: GraphicsLayer? = null
    internal var layerCoordinates: LayoutCoordinates? by mutableStateOf(null)
    private var contentVersion by mutableIntStateOf(0)

    override val isCoordinatesDependent: Boolean = true

    override fun readSamplingState() {
        layerCoordinates
    }

    /** Android 12 glass has no live render-node link to the page; redraw it after each recording. */
    internal fun notifyRecorded() {
        if (android.os.Build.VERSION.SDK_INT < 33) Snapshot.withoutReadObservation { contentVersion++ }
    }

    override fun DrawScope.drawPrismalGlass(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
    ) {
        if (android.os.Build.VERSION.SDK_INT < 33) contentVersion
        val layer = layer ?: return
        val target = coordinates?.takeIf { it.isAttached } ?: return
        val source = layerCoordinates?.takeIf { it.isAttached } ?: return
        val offset = target.positionOnScreen() - source.positionOnScreen()
        translate(-offset.x, -offset.y) { drawLayer(layer) }
    }
}

/** Records the page for [backdrop] (null: no recording). */
@Composable
internal fun Modifier.pageGlassSource(backdrop: PageGlassBackdrop?): Modifier {
    if (backdrop == null) return this
    val layer = rememberGraphicsLayer()
    DisposableEffect(backdrop, layer) {
        backdrop.layer = layer
        onDispose {
            if (backdrop.layer === layer) backdrop.layer = null
            backdrop.layerCoordinates = null
        }
    }
    return onGloballyPositioned { backdrop.layerCoordinates = it }
        .drawWithContent {
            layer.record { this@drawWithContent.drawContent() }
            drawLayer(layer)
            backdrop.notifyRecorded()
        }
}

private val LiquidCapsule = PrismalCapsule()
private val LiquidShape = { LiquidCapsule }
private val LiquidRim = PrismalSpecular(width = .9.dp, alpha = .20f)
private val LiquidRimProvider = { LiquidRim }

/** The same two-step lift as [glass], drawn outside the clipped glass. */
private fun Modifier.capsuleDepth(): Modifier = drawWithCache {
    val corner = androidx.compose.ui.geometry.CornerRadius(size.height / 2f)
    val near = androidx.compose.ui.geometry.Offset(0f, 2.dp.toPx())
    val far = androidx.compose.ui.geometry.Offset(0f, 7.dp.toPx())
    onDrawBehind {
        drawRoundRect(Color.Black.copy(alpha = .026f), topLeft = far, size = size, cornerRadius = corner)
        drawRoundRect(Color.Black.copy(alpha = .072f), topLeft = near, size = size, cornerRadius = corner)
    }
}

@Composable
private fun rememberLiquidEffects(blur: Dp, refraction: Dp): PrismalGlassEffectProvider.() -> Unit {
    val density = LocalDensity.current
    return remember(density, blur, refraction) {
        {
            applyPrismalGlassEffects(
                density = density,
                adaptiveLuminance = false,
                luminance = .5f,
                blurRadiusPx = with(density) { blur.toPx() },
                refractionHeightPx = with(density) { refraction.toPx() },
                refractionAmountPx = with(density) { (refraction * 2).toPx() },
                saturation = 1.4f,
            )
        }
    }
}

/**
 * Liquid glass for compact controls (pills, progress tracks, round buttons) and floating
 * bars: refracts [backdrop] through a capsule with a specular rim. [surface] is the
 * material drawn on the glass so text keeps its contrast; [under] redraws the material
 * the control sits on (a card), since the backdrop is what lies behind that card.
 * Without a backdrop (older or low-RAM devices) [fallback] keeps the flat material.
 */
@Composable
internal fun Modifier.liquidGlass(
    surface: Color,
    refraction: Dp,
    under: Color = Color.Unspecified,
    blur: Dp = 4.dp,
    shadow: Boolean = true,
    backdrop: PrismalBackdrop? = LocalGlassBackdrop.current,
    fallback: Modifier.() -> Modifier,
): Modifier {
    if (backdrop == null) return fallback()
    val effects = rememberLiquidEffects(blur, refraction)
    val drawSurface: DrawScope.() -> Unit = remember(under, surface) {
        {
            if (under.isSpecified) drawRect(under)
            drawRect(surface)
        }
    }
    // Prismal draws the blurred backdrop a few dp past the shape; clip it, or on a card
    // that margin shows the page colour as a halo (a hard disc inside an IconButton).
    return (if (shadow) capsuleDepth() else this)
        .clip(CircleShape)
        .drawPrismalGlass(
            backdrop = backdrop,
            shape = LiquidShape,
            effects = effects,
            specular = LiquidRimProvider,
            depthShadow = null,
            onDrawSurface = drawSurface,
        )
}

/** A page-level pill ([glass] with a pill shape) as liquid glass. */
@Composable
internal fun Modifier.liquidGlassPill(under: Color = Color.Unspecified): Modifier =
    liquidGlass(surface = GlassMaterial.Content, refraction = 8.dp, under = under) {
        glass(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Pill), fill = GlassMaterial.Content)
    }

/** [glassIcon] as liquid glass: same 6dp inset and circular control material; no lift, the IconButton clips it. */
@Composable
internal fun Modifier.liquidGlassIcon(under: Color = Color.Unspecified): Modifier =
    padding(4.dp).liquidGlass(surface = GlassMaterial.Control, refraction = 6.dp, under = under, shadow = false) {
        shadow(8.dp, CircleShape, clip = false)
            .clip(CircleShape)
            .background(GlassMaterial.Control)
            .border(.55.dp, ControlEdgeBrush, CircleShape)
    }

/**
 * Glass + Material You for chips: a flat frosted pill that becomes a tonal [tint]
 * container when selected. Flat on purpose, so long chip rows scroll without
 * re-blurring the page for every chip.
 */
internal fun Modifier.tonalPill(selected: Boolean, tint: Color = com.cinetrack.ui.theme.Accent): Modifier {
    val shape = RoundedCornerShape(com.cinetrack.ui.theme.Radius.Pill)
    return clip(shape)
        .background(if (selected) tint.copy(alpha = .28f) else Color.White.copy(alpha = .06f))
        .border(.6.dp, if (selected) tint.copy(alpha = .42f) else Color.White.copy(alpha = .08f), shape)
}

/** A large floating surface (sheet, dialog) as liquid glass over the page, with its own [shape]. */
@Composable
internal fun Modifier.liquidGlassPanel(backdrop: PrismalBackdrop, shape: androidx.compose.ui.graphics.Shape): Modifier {
    val effects = rememberLiquidEffects(blur = 16.dp, refraction = 18.dp)
    val shapeProvider = remember(shape) { { shape } }
    return clip(shape).drawPrismalGlass(
        backdrop = backdrop,
        shape = shapeProvider,
        effects = effects,
        specular = LiquidRimProvider,
        depthShadow = null,
        onDrawSurface = PanelSurface,
    )
}

private val PanelSurface: DrawScope.() -> Unit = { drawRect(Background0.copy(alpha = .58f)) }

/** The popup material, clipped to an action. Capture only the separate artwork/backdrop. */
@Composable
internal fun Modifier.liveActionGlass(state: HazeState?, completed: Boolean = false): Modifier {
    val shape = RoundedCornerShape(com.cinetrack.ui.theme.Radius.Pill)
    val style = if (!completed) NavGlassStyle else HazeStyle(
        backgroundColor = Background0,
        tint = HazeTint(com.cinetrack.ui.theme.Success.copy(alpha = .45f)),
        blurRadius = 16.dp,
        noiseFactor = .03f,
        fallbackTint = HazeTint(com.cinetrack.ui.theme.Success.copy(alpha = .82f)),
    )
    return clip(shape)
        .then(if (state != null) Modifier.hazeEffect(state, style = style)
            else Modifier.background(if (completed) com.cinetrack.ui.theme.Success.copy(alpha = .82f) else GlassMaterial.OverlayFallback))
        .border(.7.dp, ControlEdgeBrush, shape)
}

/**
 * Header whose title row ([titlePx] tall) scrolls away with the list while the rest stays
 * pinned; once content passes beneath, a dark, slightly see-through backing fades in.
 * [scrolled] is the list's distance from its top, Int.MAX_VALUE past the first item.
 */
internal fun Modifier.pinnedHeader(titlePx: () -> Int, scrolled: () -> Int, tint: () -> Color = { Color(0xFF0A0C10) }): Modifier =
    graphicsLayer { translationY = -minOf(scrolled(), titlePx()).toFloat() }
        .drawBehind {
            val title = titlePx()
            val backing = if (title == 0) 0f else ((scrolled() - title * .5f) / (title * .8f)).coerceIn(0f, 1f)
            if (backing > 0f) {
                val top = title.toFloat() - 60.dp.toPx()
                val bottom = size.height + 20.dp.toPx()
                // The page's own colour, so the header reads as part of the page.
                val ink = tint()
                drawRect(
                    Brush.verticalGradient(
                        0f to ink.copy(alpha = .96f * backing),
                        (size.height - top) / (bottom - top) to ink.copy(alpha = .90f * backing),
                        1f to Color.Transparent,
                        startY = top,
                        endY = bottom,
                    ),
                    topLeft = androidx.compose.ui.geometry.Offset(0f, top),
                    size = androidx.compose.ui.geometry.Size(size.width, bottom - top),
                )
            }
        }

/** The title row inside [pinnedHeader]: fades out as it scrolls away. */
internal fun Modifier.collapsingTitle(titlePx: () -> Int, scrolled: () -> Int): Modifier =
    graphicsLayer {
        val title = titlePx()
        alpha = if (title == 0) 1f else 1f - minOf(scrolled(), title).toFloat() / title
    }

/**
 * Tab press feedback: a soft light pill behind the label and a slight dip instead of the
 * default ripple, which resolved to a dark block on these screens. Keyboard focus shows
 * the same pill, fainter.
 */
@Composable
internal fun Modifier.softTab(selected: Boolean, onClick: () -> Unit): Modifier {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val press by androidx.compose.animation.core.animateFloatAsState(
        if (pressed) 1f else if (focused) .6f else 0f,
        androidx.compose.animation.core.tween(if (pressed) 90 else 280),
        label = "tabPress",
    )
    return selectable(
        selected = selected,
        interactionSource = interaction,
        indication = null,
        role = androidx.compose.ui.semantics.Role.Tab,
        onClick = onClick,
    ).drawBehind {
        if (press > 0f) {
            val inset = 6.dp.toPx()
            drawRoundRect(
                Color.White.copy(alpha = .09f * press),
                topLeft = androidx.compose.ui.geometry.Offset(-10.dp.toPx(), inset),
                size = androidx.compose.ui.geometry.Size(size.width + 20.dp.toPx(), size.height - inset * 2),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx()),
            )
        }
    }.graphicsLayer {
        val scale = 1f - .035f * press
        scaleX = scale
        scaleY = scale
    }
}

/** Each tab's horizontal span, so one underline can slide from tab to tab. */
internal class TabIndicatorState {
    val spans = androidx.compose.runtime.mutableStateMapOf<Int, Pair<Float, Float>>()
}

@Composable
internal fun rememberTabIndicator(): TabIndicatorState = remember { TabIndicatorState() }

/** Put on each tab: records where it sits inside the tab row. */
internal fun Modifier.tabSpan(state: TabIndicatorState, index: Int): Modifier =
    onGloballyPositioned { coordinates ->
        val left = coordinates.positionInParent().x
        val span = left to coordinates.size.width.toFloat()
        if (state.spans[index] != span) state.spans[index] = span
    }

/** Put on the tab row (after its padding): one accent underline that glides to [selected]. */
@Composable
internal fun Modifier.slidingTabIndicator(state: TabIndicatorState, selected: Int): Modifier {
    val left = remember { androidx.compose.animation.core.Animatable(0f) }
    val width = remember { androidx.compose.animation.core.Animatable(0f) }
    val target = state.spans[selected]
    androidx.compose.runtime.LaunchedEffect(target) {
        val (x, w) = target ?: return@LaunchedEffect
        if (width.value == 0f) {
            left.snapTo(x)
            width.snapTo(w)
        } else {
            val spec = androidx.compose.animation.core.spring<Float>(dampingRatio = .82f, stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow)
            kotlinx.coroutines.coroutineScope {
                launch { left.animateTo(x, spec) }
                launch { width.animateTo(w, spec) }
            }
        }
    }
    return drawBehind {
        val line = 2.dp.toPx()
        if (width.value > 0f) drawRoundRect(
            com.cinetrack.ui.theme.Accent,
            topLeft = androidx.compose.ui.geometry.Offset(left.value, size.height - line),
            size = androidx.compose.ui.geometry.Size(width.value, line),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(line / 2),
        )
    }
}

/**
 * Content that changes with a tab or a Film/Serie switch slides in from the side of the
 * new choice, like turning a page. Returns the offset as a fraction of the width.
 */
@Composable
internal fun rememberDirectionalSlide(index: Int): androidx.compose.runtime.State<Float> {
    val offset = remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    var last by remember { androidx.compose.runtime.mutableIntStateOf(index) }
    // The start offset is set in the same frame as the change, so the new content
    // never flashes in place before it slides.
    if (index != last) {
        offset.floatValue = (index - last).coerceIn(-1, 1) * .12f
        last = index
    }
    androidx.compose.runtime.LaunchedEffect(index) {
        androidx.compose.animation.core.animate(
            offset.floatValue,
            0f,
            animationSpec = androidx.compose.animation.core.tween(260, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        ) { value, _ -> offset.floatValue = value }
    }
    return offset
}

/** Applies a [rememberDirectionalSlide] offset with a matching fade. */
internal fun Modifier.directionalSlide(vararg slides: androidx.compose.runtime.State<Float>): Modifier =
    graphicsLayer {
        val offset = slides.fold(0f) { sum, slide -> sum + slide.value }
        translationX = offset * size.width
        alpha = 1f - (kotlin.math.abs(offset) * 2.2f).coerceAtMost(.3f)
    }
