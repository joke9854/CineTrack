@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.cinetrack.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import java.util.UUID

/** The app's shared-transition scope; null where none is set up (previews, tests). */
val LocalSharedTransition = staticCompositionLocalOf<SharedTransitionScope?> { null }

/** The navigation destination's enter/exit scope that shared elements animate with. */
val LocalNavVisibility = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * The poster the user tapped. The same title can appear several times on a screen
 * (rails, grid, hero), so only the tapped copy takes part in the transition.
 */
object SharedPosterSource {
    var active by mutableStateOf<String?>(null)
    /** What the tapped source opened (a title's key, or "ep:show:season:episode"). */
    var activeTarget: String? = null

    /** Records the tapped source and the item it opens. */
    fun select(slot: String, target: String) {
        active = slot
        activeTarget = target
    }

    /** The tapped source's image, but only for the page it actually opened. */
    fun imageKeyFor(target: String): coil.memory.MemoryCache.Key? = if (activeTarget == target) activeImageKey else null
    /** Memory-cache key of each poster's loaded image, so the detail page can show it at once. */
    internal val loadedKeys = java.util.concurrent.ConcurrentHashMap<String, coil.memory.MemoryCache.Key>()
    val activeImageKey: coil.memory.MemoryCache.Key? get() = active?.let(loadedKeys::get)
}

/** A stable id for one poster on screen; survives scrolling it away and back. */
@Composable
fun rememberPosterSlot(): String = rememberSaveable { UUID.randomUUID().toString() }

// A fixed-length curve: a spring kept the page transition (and the invisible outgoing
// page that still catches touches) alive for about half a second after it looked done.
private val PosterBounds = BoundsTransform { _: Rect, _: Rect ->
    androidx.compose.animation.core.tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing)
}

/**
 * Poster that flies between a list and the detail page. Sources pass their [slot] and
 * only join while they are the tapped one; the detail page passes null and always joins.
 */
@Composable
fun Modifier.sharedPoster(mediaKey: String, slot: String?): Modifier {
    val transition = LocalSharedTransition.current ?: return this
    val visibility = LocalNavVisibility.current ?: return this
    if (slot != null && SharedPosterSource.active != slot) return this
    return with(transition) {
        this@sharedPoster.sharedElement(
            rememberSharedContentState("poster:$mediaKey"),
            animatedVisibilityScope = visibility,
            boundsTransform = PosterBounds,
        )
    }
}

/** A list poster that records its loaded image for [sharedPoster]'s target. */
@Composable
fun SharedPosterImage(url: String, contentDescription: String?, slot: String, modifier: Modifier, contentScale: androidx.compose.ui.layout.ContentScale) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // A page rebuilt on going back shows its poster from memory at once, so the poster
    // flying back into it is never an empty box while the image reloads.
    val request = androidx.compose.runtime.remember(url, slot) {
        coil.request.ImageRequest.Builder(context).data(url)
            .placeholderMemoryCacheKey(SharedPosterSource.loadedKeys[slot])
            .build()
    }
    coil.compose.AsyncImage(
        model = request,
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier,
        onSuccess = { state -> state.result.memoryCacheKey?.let { SharedPosterSource.loadedKeys[slot] = it } },
    )
}

/**
 * The detail page's poster: while its own (larger) copy loads it shows the tapped list
 * poster from memory, so the flying poster never shows an empty placeholder.
 */
@Composable
fun SharedPosterTargetImage(url: String, contentDescription: String?, sharedKey: String, modifier: Modifier, contentScale: androidx.compose.ui.layout.ContentScale) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val placeholder = androidx.compose.runtime.remember(url) { SharedPosterSource.imageKeyFor(sharedKey) }
    val request = androidx.compose.runtime.remember(url, placeholder) {
        coil.request.ImageRequest.Builder(context).data(url).placeholderMemoryCacheKey(placeholder).crossfade(220).build()
    }
    coil.compose.AsyncImage(model = request, contentDescription = contentDescription, contentScale = contentScale, modifier = modifier)
}

/**
 * A wide cover (carousel card, wide Mosaic tile) that grows into the detail page's header
 * and crossfades into it; the detail header passes null as [slot].
 */
@Composable
fun Modifier.sharedBackdrop(mediaKey: String, slot: String?): Modifier {
    val transition = LocalSharedTransition.current ?: return this
    val visibility = LocalNavVisibility.current ?: return this
    if (slot != null && SharedPosterSource.active != slot) return this
    return with(transition) {
        this@sharedBackdrop.sharedBounds(
            rememberSharedContentState("backdrop:$mediaKey"),
            animatedVisibilityScope = visibility,
            boundsTransform = PosterBounds,
        )
    }
}
