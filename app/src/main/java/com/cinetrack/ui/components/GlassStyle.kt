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
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.hazeSource
import androidx.compose.ui.platform.LocalContext
import dev.chrisbanes.haze.HazeState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.dp
import com.cinetrack.ui.theme.Background0
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.layout.onGloballyPositioned
import com.cinetrack.ui.theme.Accent
import com.cinetrack.ui.theme.Radius
import com.cinetrack.ui.theme.Success
import com.cinetrack.ui.theme.SurfacePalette

// One material family: lightweight content, compact controls, and live overlays.
internal object GlassMaterial {
    val Content = SurfacePalette.GlassSurface.copy(alpha = .27f)
    /** Cards: a faint cool lift so they read as raised on the near-black page. */
    val Card = Color(0xFFB4C3D2).copy(alpha = .075f).compositeOver(SurfacePalette.GlassSurface.copy(alpha = .30f))
    val Control = SurfacePalette.GlassSurface.copy(alpha = .58f)
    val OverlayFallback = Background0.copy(alpha = .90f)
    val DetailSurface = Background0.copy(alpha = .58f)
}

// Shared live material for navigation and floating sheets/dialogs.
internal val NavGlassStyle = blurStyle(tint = Background0.copy(alpha = .58f), fallback = GlassMaterial.OverlayFallback)

/** Haze blur over an opaque [background], with [fallback] where blur is off. */
internal fun blurStyle(tint: Color, fallback: Color, radius: Dp = 16.dp, background: Color = Background0): HazeBlurStyle = HazeBlurStyle {
    backgroundColor(background)
    colorEffects(listOf(HazeColorEffect.tint(tint)))
    blurRadius(radius)
    noiseFactor(.03f)
    fallbackColorEffect(HazeColorEffect.tint(fallback))
}

// Cards don't take the glass specular, so their border imitates the controls' rim (0.9 dp,
// 20% peak): light catches the top edge, the sides stay faint but hold the card's shape on
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

private class FloatingGlassCapture(val state: HazeState?, val glass: Boolean) {
    var users by mutableIntStateOf(0)
}

private val LocalFloatingGlass = staticCompositionLocalOf<FloatingGlassCapture?> { null }

/**
 * Capture the page into [state] only while navigation or a floating surface needs it. With
 * [glass] the bottom bar and sheets refract it as liquid glass; otherwise they blur it.
 */
@Composable
internal fun FloatingGlassHost(
    state: HazeState?,
    glass: Boolean,
    navigationVisible: Boolean,
    content: @Composable (Modifier) -> Unit,
) {
    val capture = remember(state, glass) { FloatingGlassCapture(state, glass) }
    CompositionLocalProvider(LocalFloatingGlass provides capture) {
        val needed = state != null && (navigationVisible || capture.users > 0)
        content(if (needed && state != null) Modifier.hazeSource(state) else Modifier)
    }
}

/** The page for a floating sheet or dialog to refract as liquid glass; null keeps the blur. */
@Composable
internal fun rememberFloatingPageBackdrop(): HazeState? {
    val capture = LocalFloatingGlass.current
    val page = capture?.state?.takeIf { capture.glass }
    DisposableEffect(page) {
        if (page != null) capture.users++
        onDispose { if (page != null) capture.users-- }
    }
    return page
}

/** The page for a floating surface to blur, when liquid glass is off. */
@Composable
internal fun rememberFloatingGlassState(): HazeState? {
    val capture = LocalFloatingGlass.current
    val state = capture?.state?.takeIf { !capture.glass }
    DisposableEffect(state) {
        if (state != null) capture.users++
        onDispose { if (state != null) capture.users-- }
    }
    return state
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

/** The background that liquid-glass controls refract; null keeps the flat material. */
internal val LocalGlassBackdrop = staticCompositionLocalOf<HazeState?> { null }

/** A background capture for liquid glass; null where Haze glass can't refract. */
@Composable
internal fun rememberGlassBackdrop(): HazeState? {
    val context = LocalContext.current
    return remember(context) { if (liquidGlassEligible(context)) HazeState() else null }
}

/** Haze glass refracts and blurs only with RuntimeShader (Android 13+); older or low-RAM devices keep the flat material. */
internal fun liquidGlassEligible(context: android.content.Context): Boolean {
    val manager = context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
    return android.os.Build.VERSION.SDK_INT >= 33 && !manager.isLowRamDevice
}

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

/**
 * Liquid glass for compact controls (pills, progress tracks, round buttons) and floating
 * bars: refracts [backdrop] through a capsule with a specular rim, with the [kind]'s
 * settings ([GlassTuning]). [surface] is the tint laid on the glass so text keeps its
 * contrast; [under] is the material the control sits on (a card), since the backdrop is what
 * lies behind that card. Without a backdrop (older or low-RAM devices) [fallback] keeps the
 * flat material.
 */
@OptIn(ExperimentalHazeApi::class)
@Composable
internal fun Modifier.liquidGlass(
    kind: GlassSurface,
    surface: Color,
    under: Color = Color.Unspecified,
    shadow: Boolean = true,
    backdrop: HazeState? = LocalGlassBackdrop.current,
    fallback: Modifier.() -> Modifier,
): Modifier {
    if (backdrop == null) return fallback()
    val params = GlassTuning.params(kind)
    val style = remember(params, surface, under) {
        params.style(if (under.isSpecified) surface.compositeOver(under) else surface)
    }
    return (if (shadow) capsuleDepth() else this)
        .then(if (params.lift > 0f) Modifier.shadow(params.lift.dp, CircleShape, clip = false) else Modifier)
        .clip(CircleShape)
        .hazeGlass(input = HazeInput.Sources(backdrop), style = style, performanceMode = params.performanceMode)
}

/**
 * Page controls share the bottom bar's glass: the same dark tint and frost, with the
 * refraction band scaled to the control (the bar bends 16 dp of its 54 dp height). The
 * lighter control fill made the rim read as a flat grey stroke on small buttons.
 */
internal val ControlGlassTint = Background0.copy(alpha = .50f)

/** A page-level pill ([glass] with a pill shape) as liquid glass. */
@Composable
internal fun Modifier.liquidGlassPill(under: Color = Color.Unspecified): Modifier =
    liquidGlass(GlassSurface.Pill, surface = ControlGlassTint, under = under) {
        glass(RoundedCornerShape(Radius.Pill), fill = GlassMaterial.Content)
    }

/** [glassIcon] as liquid glass: same 6dp inset, the bar's material and a soft lift inside the IconButton's bounds. */
@Composable
internal fun Modifier.liquidGlassIcon(under: Color = Color.Unspecified): Modifier =
    padding(4.dp).liquidGlass(GlassSurface.IconButton, surface = ControlGlassTint, under = under, shadow = false) {
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
internal fun Modifier.tonalPill(selected: Boolean, tint: Color = Accent): Modifier {
    val shape = RoundedCornerShape(Radius.Pill)
    return clip(shape)
        .background(if (selected) tint.copy(alpha = .28f) else Color.White.copy(alpha = .06f))
        .border(.6.dp, if (selected) tint.copy(alpha = .42f) else Color.White.copy(alpha = .08f), shape)
}

/** A large floating surface (sheet, dialog) as liquid glass over the page, with its own [shape]. */
@OptIn(ExperimentalHazeApi::class)
@Composable
internal fun Modifier.liquidGlassPanel(backdrop: HazeState, shape: RoundedCornerShape): Modifier {
    val params = GlassTuning.params(GlassSurface.Panel)
    val style = remember(params, shape) { params.style(Background0.copy(alpha = .58f), shape) }
    return clip(shape).hazeGlass(input = HazeInput.Sources(backdrop), style = style, performanceMode = params.performanceMode)
}

/** The popup material, clipped to an action. Capture only the separate artwork/backdrop. */
@Composable
internal fun Modifier.liveActionGlass(state: HazeState?, completed: Boolean = false): Modifier {
    val shape = RoundedCornerShape(Radius.Pill)
    val style = if (!completed) NavGlassStyle else CompletedActionGlass
    return clip(shape)
        .then(if (state != null) Modifier.hazeBlur(input = HazeInput.Sources(state), style = style)
            else Modifier.background(if (completed) Success.copy(alpha = .82f) else GlassMaterial.OverlayFallback))
        .border(.7.dp, ControlEdgeBrush, shape)
}

private val CompletedActionGlass = blurStyle(tint = Success.copy(alpha = .45f), fallback = Success.copy(alpha = .82f))

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
            Accent,
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
            animationSpec = com.cinetrack.ui.theme.Motion.snappy(),
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
