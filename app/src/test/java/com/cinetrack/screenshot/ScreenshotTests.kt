package com.cinetrack.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.cinetrack.domain.MediaCard
import com.cinetrack.domain.MediaType
import com.cinetrack.domain.PlaybackCard
import com.cinetrack.ui.components.PillTone
import com.cinetrack.ui.components.StatusPill
import com.cinetrack.ui.screens.PlaybackRow
import com.cinetrack.ui.screens.ProgressEmpty
import com.cinetrack.ui.theme.CineTrackTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * JVM screenshot tests with static data and no network: media cards carry no poster/backdrop URL,
 * so the card shows its gradient art and Coil is never asked to load anything.
 * AGSL/RuntimeShader glass is not available under Robolectric and falls back to its plain
 * fill, so goldens only guard layout and colors, not the real glass look.
 * CineTrackTheme is always dark; the night/notnight qualifier variants are kept so a future
 * light theme is covered by the same goldens.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = android.app.Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTests {
    @get:Rule val compose = createComposeRule()

    private val show = MediaCard(id = 1, type = MediaType.TV, title = "Static Show")
    private val card = PlaybackCard(
        media = show,
        season = 2, episodeNumber = 5, episodeTitle = "The Long Night",
        progress = .4f, remainingMinutes = 27, durationMinutes = 45,
    )

    private fun shoot(name: String, content: @Composable () -> Unit) {
        compose.setContent {
            CineTrackTheme { Box(Modifier.padding(16.dp)) { content() } }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test fun progressCard_light() = lightOnly("progress_card_light") {
        PlaybackRow(card, onMedia = {}, onEpisode = {}, onWatched = {})
    }

    @Test @Config(qualifiers = "+night")
    fun progressCard_dark() = shoot("progress_card_dark") {
        PlaybackRow(card, onMedia = {}, onEpisode = {}, onWatched = {})
    }

    @Test fun statusPill_light() = lightOnly("status_pill_light") {
        StatusPill("Library updated", "12 titles refreshed", PillTone.DONE)
    }

    @Test @Config(qualifiers = "+night")
    fun statusPill_dark() = shoot("status_pill_dark") {
        StatusPill("Library updated", "12 titles refreshed", PillTone.DONE)
    }

    @Test fun emptyState_light() = lightOnly("empty_state_light") {
        ProgressEmpty("Nothing in progress yet")
    }

    @Test @Config(qualifiers = "+night")
    fun emptyState_dark() = shoot("empty_state_dark") {
        ProgressEmpty("Nothing in progress yet")
    }

    private fun lightOnly(name: String, content: @Composable () -> Unit) = shoot(name, content)
}
