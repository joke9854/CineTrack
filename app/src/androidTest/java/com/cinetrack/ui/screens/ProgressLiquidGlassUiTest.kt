package com.cinetrack.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cinetrack.R
import com.cinetrack.domain.AppUiState
import com.cinetrack.domain.MediaCard
import com.cinetrack.domain.MediaType
import com.cinetrack.domain.PlaybackCard
import com.cinetrack.domain.SyncProgress
import com.cinetrack.domain.ViewingPeopleInsights
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProgressLiquidGlassUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun playbackCardsWithLiquidGlassControlsRenderAndMarkWatched() {
        val watched = mutableListOf<PlaybackCard>()
        val cards = (1..4).map { index ->
            PlaybackCard(
                media = MediaCard(id = index, type = MediaType.TV, title = "Show $index"),
                season = 1,
                episodeNumber = index,
                episodeTitle = "Episode $index",
                progress = index / 5f,
                durationMinutes = 45,
            )
        }

        composeRule.setContent {
            MaterialTheme {
                ProgressScreen(
                    state = AppUiState(loading = false, playbackTv = cards),
                    syncProgress = MutableStateFlow(SyncProgress()),
                    syncRunning = MutableStateFlow(false),
                    onSearch = {},
                    onSync = {},
                    onSyncOperations = {},
                    onSyncSettings = {},
                    onMedia = {},
                    onWatched = { watched += it },
                    onEpisode = {},
                    onHideUpcoming = {},
                    viewingInsights = ViewingPeopleInsights(),
                    onLoadViewingInsights = {},
                    onCompactNav = {},
                )
            }
        }

        val markWatched = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.mark_watched)
        // Three cards plus the faded fourth one; every glass button stays a working control.
        composeRule.onAllNodesWithContentDescription(markWatched).assertCountEquals(4)
        composeRule.onAllNodesWithContentDescription(markWatched).onFirst().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { watched.isNotEmpty() }
        assertEquals("Show 1", watched.single().media.title)
    }
}
