package com.cinetrack.ui.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.unit.IntOffset

object Motion {
    const val Instant = 0
    /** Press-in and the detail routes' cross-fade: quick enough to feel immediate. */
    const val Quick = 90
    const val Short = 180
    const val Medium = 300
    const val Long = 420
    // Preserve deliberately slower artwork/progress interpolation (>50ms away).
    const val Extended = 480

    // Material 3 easings.
    val Emphasized: Easing = CubicBezierEasing(.2f, 0f, 0f, 1f)
    val EmphasizedDecelerate: Easing = CubicBezierEasing(.05f, .7f, .1f, 1f)
    val EmphasizedAccelerate: Easing = CubicBezierEasing(.3f, 0f, .8f, .15f)
    val Standard: Easing = CubicBezierEasing(.2f, 0f, 0f, 1f)
    val StandardDecelerate: Easing = CubicBezierEasing(0f, 0f, 0f, 1f)
    val StandardAccelerate: Easing = CubicBezierEasing(.3f, 0f, 1f, 1f)

    /** Selections, toggles, thumbs: settles quickly with no visible overshoot. */
    fun <T> snappy(visibilityThreshold: T? = null): SpringSpec<T> =
        spring(dampingRatio = .9f, stiffness = 320f, visibilityThreshold = visibilityThreshold)

    /** Confirmations (the watched check): a small, playful overshoot. */
    fun <T> bouncy(visibilityThreshold: T? = null): SpringSpec<T> =
        spring(dampingRatio = .6f, stiffness = 380f, visibilityThreshold = visibilityThreshold)

    /** Pressed controls shrinking and springing back. */
    fun <T> press(visibilityThreshold: T? = null): SpringSpec<T> =
        spring(dampingRatio = .7f, stiffness = Spring.StiffnessMediumLow, visibilityThreshold = visibilityThreshold)

    /** Sizes and placements that should glide: list items, expanding sections. */
    fun <T> gentle(visibilityThreshold: T? = null): SpringSpec<T> =
        spring(dampingRatio = 1f, stiffness = Spring.StiffnessLow, visibilityThreshold = visibilityThreshold)

    // Enter on the smooth emphasized curve: the decelerate variant moves most of the way in
    // the first frames, which reads as a jump when one of them is late.
    fun <T> enter(duration: Int = Medium, delay: Int = 0): FiniteAnimationSpec<T> =
        tween(duration, delay, Emphasized)

    fun <T> exit(duration: Int = Short, delay: Int = 0): FiniteAnimationSpec<T> =
        tween(duration, delay, StandardAccelerate)

    /** Whole pages moving side by side: both use the same curve so they stay joined. */
    private fun <T> page(): FiniteAnimationSpec<T> = tween(Long, easing = Emphasized)

    // Tabs and pushed pages slide as whole pages, like a pager, with no fade: fading two
    // full-screen pages forces offscreen layers under the nav glass and drops frames.
    fun tabEnter(forward: Boolean): EnterTransition =
        slideInHorizontally(page<IntOffset>()) { if (forward) it else -it }

    fun tabExit(forward: Boolean): ExitTransition =
        slideOutHorizontally(page<IntOffset>()) { if (forward) -it else it }

    val PushEnter: EnterTransition = tabEnter(forward = true)
    val PushExit: ExitTransition = tabExit(forward = true)
    val PopEnter: EnterTransition = tabEnter(forward = false)
    val PopExit: ExitTransition = tabExit(forward = false)

    // Floating status pills drop in from the top and leave the same way.
    val PillEnter: EnterTransition = slideInVertically(enter<IntOffset>(Long)) { -it } + fadeIn(enter(Medium))
    val PillExit: ExitTransition = slideOutVertically(exit<IntOffset>(Medium)) { -it } + fadeOut(exit(Medium))
}
