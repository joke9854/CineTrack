package com.cinetrack.ui.components

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cinetrack.BuildConfig
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.glass.ChromaticAberrationMode
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.RefractionProfile
import dev.chrisbanes.haze.glass.SurfaceProfile
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Every liquid-glass surface in the app; each is tuned on its own. */
internal enum class GlassSurface(val label: String) {
    NavBar("Nav bar"),
    NavDroplet("Nav selection"),
    IconButton("Round buttons"),
    Pill("Pills"),
    BackButton("Back button"),
    ActionButton("Action button"),
    Panel("Sheets & dialogs"),
    Toggle("Switch knob"),
}

/** Haze blur surfaces (no refraction). */
internal enum class BlurSurface(val label: String) {
    DetailHeader("Detail header blur"),
}

/**
 * One glass surface's Haze settings. Sizes are dp. [tint] scales the alpha of the colour the
 * surface lays on its glass; [displacement] is a multiple of [refraction]; [light] is the
 * light's vertical position (-1 top, 0 centre, 1 bottom); [quality] is Haze's fixed quality
 * fraction (0 Performance, .5 Balanced, 1 Quality).
 */
@Serializable
internal data class GlassParams(
    val tint: Float = 1f,
    val refraction: Float,
    val displacement: Float = 2f,
    val strength: Float = .7f,
    val blur: Float,
    val depth: Float = 1f,
    val specular: Float = .32f,
    val specularExponent: Float = 24f,
    val edgeShadow: Float = .14f,
    val ambient: Float = .10f,
    val fresnel: Float = 3f,
    val edgeSoftness: Float = 1f,
    val light: Float = 0f,
    val whitePoint: Float = 0f,
    val contrast: Float = 0f,
    val chroma: Float = 1.4f,
    val dispersion: Float = 0f,
    val lift: Float = 0f,
    val quality: Float = .5f,
)

/** One blur surface's Haze settings: radius in dp, tint alpha, grain and quality fraction. */
@Serializable
internal data class BlurParams(
    val radius: Float,
    val tint: Float,
    val noise: Float = .03f,
    val quality: Float = .5f,
)

/**
 * The glass settings in use. Release builds always get the defaults; debug and preview builds
 * can change them live from the Glass lab, which keeps them across restarts.
 */
internal object GlassTuning {
    /** Debug and preview builds (application id `.debug`) only. */
    val available: Boolean = BuildConfig.APPLICATION_ID.endsWith(".debug")

    private val glass = mutableStateMapOf<GlassSurface, GlassParams>()
    private val blurs = mutableStateMapOf<BlurSurface, BlurParams>()
    private const val Prefs = "glass_lab"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // Tuned by the user on the S23 in the Glass lab (2026-10-24). The specular rim is off or
    // faint everywhere: on small round controls it read as a flat grey stroke.
    fun defaults(surface: GlassSurface): GlassParams = when (surface) {
        GlassSurface.NavBar -> GlassParams(
            tint = .80f, refraction = 16f, displacement = 2.5f, blur = 14f, specular = 0f,
            edgeShadow = .20f, ambient = .10f, edgeSoftness = 2f, light = -.34f,
        )
        GlassSurface.NavDroplet -> GlassParams(tint = .80f, refraction = 12.5f, blur = 2f, specular = 0f)
        GlassSurface.IconButton -> GlassParams(
            refraction = 11f, blur = 14f, specular = .06f, specularExponent = 30f, ambient = 0f, fresnel = .5f,
            edgeSoftness = 1.75f, light = -.15f, contrast = .09f, lift = 4f,
        )
        GlassSurface.Pill -> GlassParams(
            tint = .50f, refraction = 12f, blur = 14f, specular = .10f, specularExponent = 30f, edgeShadow = .10f,
            ambient = .04f, edgeSoftness = 2.5f, whitePoint = .05f, lift = 3f,
        )
        GlassSurface.BackButton -> GlassParams(
            tint = .90f, refraction = 10.9f, blur = 14f, specular = .10f, ambient = 0f, fresnel = .5f,
            edgeSoftness = 2.55f, whitePoint = .05f, lift = 4f,
        )
        GlassSurface.ActionButton -> GlassParams(
            tint = 1.15f, refraction = 10f, blur = 5.1f, specular = .10f, edgeShadow = .10f, edgeSoftness = 2.5f,
        )
        GlassSurface.Panel -> GlassParams(refraction = 18f, blur = 16f)
        GlassSurface.Toggle -> GlassParams(refraction = 6f, blur = 4f)
    }

    fun defaults(surface: BlurSurface): BlurParams = when (surface) {
        BlurSurface.DetailHeader -> BlurParams(radius = 22f, tint = .20f)
    }

    fun params(surface: GlassSurface): GlassParams = if (available) glass[surface] ?: defaults(surface) else defaults(surface)
    fun params(surface: BlurSurface): BlurParams = if (available) blurs[surface] ?: defaults(surface) else defaults(surface)

    fun set(context: android.content.Context, surface: GlassSurface, params: GlassParams) {
        if (params == defaults(surface)) glass.remove(surface) else glass[surface] = params
        save(context)
    }

    fun set(context: android.content.Context, surface: BlurSurface, params: BlurParams) {
        if (params == defaults(surface)) blurs.remove(surface) else blurs[surface] = params
        save(context)
    }

    fun resetAll(context: android.content.Context) {
        glass.clear()
        blurs.clear()
        save(context)
    }

    @Serializable
    private data class Saved(val glass: Map<GlassSurface, GlassParams> = emptyMap(), val blurs: Map<BlurSurface, BlurParams> = emptyMap())

    fun load(context: android.content.Context) {
        if (!available) return
        val text = context.getSharedPreferences(Prefs, android.content.Context.MODE_PRIVATE).getString("tuning", null) ?: return
        val saved = runCatching { json.decodeFromString<Saved>(text) }.getOrNull() ?: return
        glass.clear(); glass.putAll(saved.glass)
        blurs.clear(); blurs.putAll(saved.blurs)
    }

    private fun save(context: android.content.Context) {
        context.getSharedPreferences(Prefs, android.content.Context.MODE_PRIVATE).edit()
            .putString("tuning", json.encodeToString(Saved.serializer(), Saved(glass.toMap(), blurs.toMap())))
            .apply()
    }

    /** The changed surfaces, as text to paste back into a chat. */
    fun export(): String = buildString {
        appendLine("CineTrack glass lab (${BuildConfig.VERSION_NAME})")
        if (glass.isEmpty() && blurs.isEmpty()) appendLine("No changes from the defaults.")
        glass.toSortedMap().forEach { (surface, params) -> appendLine("${surface.name}: $params") }
        blurs.toSortedMap().forEach { (surface, params) -> appendLine("${surface.name}: $params") }
    }
}

/** The surface's glass with [tint] laid on it, its alpha scaled by [GlassParams.tint]. */
@OptIn(ExperimentalHazeApi::class)
internal fun GlassParams.style(tint: Color, shape: RoundedCornerShape = CircleShape): GlassStyle {
    val p = this
    return GlassStyle {
        // Every channel is written, so the glass never follows the system light/dark
        // setting the way Haze's built-in styles do; CineTrack is always dark.
        shape(shape)
        tint(tint.copy(alpha = (tint.alpha * p.tint).coerceIn(0f, 1f)))
        optics(
            refractionStrength = p.strength,
            refractionHeightFraction = .25f,
            refractionDisplacement = (p.refraction * p.displacement).dp,
            depth = p.depth,
            blurRadius = p.blur.dp,
            refractionDetailIntensity = 0f,
            refractionProfile = RefractionProfile.Edge(p.refraction.dp),
        )
        specularIntensity(p.specular)
        specularExponent(p.specularExponent)
        edgeShadow(Color.Black.copy(alpha = p.edgeShadow))
        ambientResponse(p.ambient)
        fresnelExponent(p.fresnel)
        edgeSoftness(p.edgeSoftness.dp)
        lightPosition(BiasAlignment(0f, p.light))
        surfaceProfile(SurfaceProfile.Circle)
        chromaticAberrationStrength(p.dispersion)
        chromaticAberrationMode(ChromaticAberrationMode.Simple)
        contrast(p.contrast)
        whitePoint(p.whitePoint)
        chromaMultiplier(p.chroma)
        contentNormalBlend(.15f)
    }
}

internal val GlassParams.performanceMode: HazePerformanceMode get() = HazePerformanceMode.Fixed(quality)

/** The blur over an opaque [background], with [fallback] where blur is off. */
internal fun BlurParams.style(tintColor: Color, fallback: Color, background: Color): HazeBlurStyle {
    val p = this
    return HazeBlurStyle {
        backgroundColor(background)
        colorEffects(listOf(HazeColorEffect.tint(tintColor.copy(alpha = p.tint))))
        blurRadius(p.radius.dp)
        noiseFactor(p.noise)
        fallbackColorEffect(HazeColorEffect.tint(fallback))
    }
}

internal val BlurParams.performanceMode: HazePerformanceMode get() = HazePerformanceMode.Fixed(quality)

/** Haze blur of [state] with the [surface]'s settings ([GlassTuning]). */
@androidx.compose.runtime.Composable
internal fun androidx.compose.ui.Modifier.tunedBlur(
    surface: BlurSurface,
    state: dev.chrisbanes.haze.HazeState,
    tint: Color,
    fallback: Color,
    background: Color,
): androidx.compose.ui.Modifier {
    val params = GlassTuning.params(surface)
    val style = androidx.compose.runtime.remember(params, tint, fallback, background) { params.style(tint, fallback, background) }
    return hazeBlur(input = dev.chrisbanes.haze.HazeInput.Sources(state), style = style, performanceMode = params.performanceMode)
}
