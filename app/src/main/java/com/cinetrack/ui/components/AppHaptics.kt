package com.cinetrack.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * The app's haptic vocabulary in one place:
 * - [confirm]: a status change or anything marked watched (once per action, however many titles).
 * - [toggle]: a two-state control the user flips (episode/season circle, reminder bell).
 * - [tick]: moving between tabs or segments.
 * - [arm]/[longPress]: a gesture crossing its threshold, a long press opening a menu.
 */
@Immutable
class AppHaptics internal constructor(private val feedback: HapticFeedback) {
    fun confirm() = feedback.performHapticFeedback(HapticFeedbackType.Confirm)
    fun toggle(on: Boolean) = feedback.performHapticFeedback(if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
    fun tick() = feedback.performHapticFeedback(HapticFeedbackType.SegmentTick)
    fun arm() = feedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
    fun longPress() = feedback.performHapticFeedback(HapticFeedbackType.LongPress)
}

@Composable
fun rememberAppHaptics(): AppHaptics {
    val feedback = LocalHapticFeedback.current
    return remember(feedback) { AppHaptics(feedback) }
}
