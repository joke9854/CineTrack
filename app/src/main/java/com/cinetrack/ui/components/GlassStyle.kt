package com.cinetrack.ui.components

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
import androidx.compose.ui.unit.dp
import com.cinetrack.ui.theme.Background0
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.styropyr0.prismal.PrismalGlassEffectProvider
import com.styropyr0.prismal.depth.PrismalDepthShadow
import com.styropyr0.prismal.drawPrismalGlass
import com.styropyr0.prismal.effects.applyPrismalGlassEffects
import com.styropyr0.prismal.shapes.PrismalCapsule
import com.styropyr0.prismal.specular.PrismalSpecular
import com.styropyr0.prismal.sources.PrismalGlassLayer
import com.styropyr0.prismal.sources.rememberPrismalGlassLayer

// One material family: lightweight content, compact controls, and live overlays.
internal object GlassMaterial {
    val Content = com.cinetrack.ui.theme.SurfacePalette.GlassSurface.copy(alpha = .27f)
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

internal val GlassEdgeBrush = Brush.linearGradient(
    listOf(Color.White.copy(alpha = .20f), Color.White.copy(alpha = .04f)),
)

private class FloatingGlassCapture(val state: HazeState?) {
    var users by mutableIntStateOf(0)
}

private val LocalFloatingGlass = staticCompositionLocalOf<FloatingGlassCapture?> { null }

/** Capture the page once, and only while navigation or a floating surface needs it. */
@Composable
internal fun FloatingGlassHost(state: HazeState?, navigationVisible: Boolean, content: @Composable (Modifier) -> Unit) {
    val capture = remember(state) { FloatingGlassCapture(state) }
    CompositionLocalProvider(LocalFloatingGlass provides capture) {
        content(if (state != null && (navigationVisible || capture.users > 0)) Modifier.hazeSource(state) else Modifier)
    }
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

/** The page background that liquid-glass controls refract; null keeps the flat material. */
internal val LocalGlassBackdrop = staticCompositionLocalOf<PrismalGlassLayer?> { null }

/** Same device policy as the live blur: Android 12+ and not low-RAM, otherwise null. */
@Composable
internal fun rememberGlassBackdrop(): PrismalGlassLayer? {
    val context = LocalContext.current
    val layer = rememberPrismalGlassLayer()
    val eligible = remember(context) {
        val manager = context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        android.os.Build.VERSION.SDK_INT >= 31 && !manager.isLowRamDevice
    }
    return layer.takeIf { eligible }
}

private val LiquidCapsule = PrismalCapsule()
private val LiquidShape = { LiquidCapsule }
private val LiquidRim = PrismalSpecular(width = .8.dp)
private val LiquidRimProvider = { LiquidRim }
private val LiquidShadow = PrismalDepthShadow(radius = 8.dp, color = Color.Black.copy(alpha = .14f))
private val LiquidShadowProvider = { LiquidShadow }

/**
 * Liquid glass for compact controls only (pills, progress tracks, round buttons):
 * refracts the page backdrop through a capsule with a specular rim. [surface] is the
 * material drawn on the glass so text keeps its contrast; [onCard] also redraws the
 * card material the control covers, since the backdrop is the page behind the card.
 * Without a backdrop (older or low-RAM devices) [fallback] keeps the flat material.
 */
@Composable
internal fun Modifier.liquidGlass(
    onCard: Boolean,
    surface: Color,
    refraction: Dp,
    shadow: Boolean = true,
    fallback: Modifier.() -> Modifier,
): Modifier {
    val backdrop = LocalGlassBackdrop.current ?: return fallback()
    val density = LocalDensity.current
    val effects: PrismalGlassEffectProvider.() -> Unit = remember(density, refraction) {
        {
            applyPrismalGlassEffects(
                density = density,
                adaptiveLuminance = false,
                luminance = .5f,
                blurRadiusPx = with(density) { 4.dp.toPx() },
                refractionHeightPx = with(density) { refraction.toPx() },
                refractionAmountPx = with(density) { (refraction * 2).toPx() },
                saturation = 1.4f,
            )
        }
    }
    val drawSurface: DrawScope.() -> Unit = remember(onCard, surface) {
        {
            if (onCard) drawRect(GlassMaterial.Content)
            drawRect(surface)
        }
    }
    return drawPrismalGlass(
        backdrop = backdrop,
        shape = LiquidShape,
        effects = effects,
        specular = LiquidRimProvider,
        depthShadow = if (shadow) LiquidShadowProvider else null,
        onDrawSurface = drawSurface,
    )
}

/** [glassIcon] as liquid glass: same 6dp inset and circular control material. */
@Composable
internal fun Modifier.liquidGlassIcon(): Modifier =
    padding(6.dp).liquidGlass(onCard = true, surface = GlassMaterial.Control, refraction = 6.dp) {
        shadow(8.dp, CircleShape, clip = false)
            .clip(CircleShape)
            .background(GlassMaterial.Control)
            .border(.55.dp, GlassEdgeBrush, CircleShape)
    }

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
        .border(.7.dp, GlassEdgeBrush, shape)
}
