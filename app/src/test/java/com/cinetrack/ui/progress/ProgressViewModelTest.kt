package com.cinetrack.ui.progress

import com.cinetrack.data.sync.ReleaseNotifier
import com.cinetrack.domain.AppUiState
import com.cinetrack.domain.MediaCard
import com.cinetrack.domain.MediaType
import com.cinetrack.domain.PersonCard
import com.cinetrack.domain.TimelineCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private val show = MediaCard(id = 7, type = MediaType.TV, title = "Show")
    private val release = "2026-11-02"

    private class FakeStore : ProgressStore {
        override val releaseReminders = MutableStateFlow<Set<String>>(emptySet())
        val reminderCalls = mutableListOf<Boolean>()
        var peopleCalls = 0
        var peopleFailure: Exception? = null
        override suspend fun setReminderKey(key: String, enabled: Boolean) {
            releaseReminders.value = if (enabled) releaseReminders.value + key else releaseReminders.value - key
        }
        override suspend fun setReminder(media: MediaCard, release: String, season: Int?, episode: Int?, episodeTitle: String?, enabled: Boolean) {
            reminderCalls += enabled
            setReminderKey(ReleaseNotifier.reminderKey(media.stableKey, season, episode, release), enabled)
        }
        override suspend fun viewingPeople(history: List<TimelineCard>): Pair<List<PersonCard>, List<PersonCard>> {
            peopleCalls++
            peopleFailure?.let { throw it }
            return listOf(PersonCard(id = 1, name = "Actor", role = "")) to emptyList()
        }
    }

    @Test
    fun explicitReminderTurnsTheBellOnAndOff() {
        val store = FakeStore()
        val viewModel = ProgressViewModel(store, dispatcher)

        viewModel.setReleaseReminder(show, release, 1, 2, "Pilot", enabled = true, byDefault = false)
        assertTrue(viewModel.isReleaseReminderOn(viewModel.releaseReminders.value, show, release, 1, 2, byDefault = false))

        viewModel.setReleaseReminder(show, release, 1, 2, "Pilot", enabled = false, byDefault = false)
        assertFalse(viewModel.isReleaseReminderOn(viewModel.releaseReminders.value, show, release, 1, 2, byDefault = false))
        assertEquals(listOf(true, false), store.reminderCalls)
    }

    @Test
    fun defaultReleaseIsMutedInsteadOfGettingASecondReminder() {
        val store = FakeStore()
        val viewModel = ProgressViewModel(store, dispatcher)
        assertTrue(viewModel.isReleaseReminderOn(emptySet(), show, release, 1, 2, byDefault = true))

        viewModel.setReleaseReminder(show, release, 1, 2, "Pilot", enabled = false, byDefault = true)
        val muted = viewModel.releaseReminders.value
        assertTrue(ReleaseNotifier.muteKey(show.stableKey, 1, 2, release) in muted)
        assertFalse(viewModel.isReleaseReminderOn(muted, show, release, 1, 2, byDefault = true))

        viewModel.setReleaseReminder(show, release, 1, 2, "Pilot", enabled = true, byDefault = true)
        assertTrue(viewModel.isReleaseReminderOn(viewModel.releaseReminders.value, show, release, 1, 2, byDefault = true))
        // Un-muting never schedules an explicit reminder on top of the default notification.
        assertEquals(listOf(false), store.reminderCalls)
    }

    @Test
    fun notifiesByDefaultNeedsTheCalendarEntryAndItsKindSwitch() {
        val entry = TimelineCard(media = show, label = "", timestamp = release, season = 1, episodeNumber = 2)
        val state = AppUiState(calendar = listOf(entry), notificationEpisodes = true)
        assertTrue(notifiesByDefault(state, show, 1, 2))
        assertFalse(notifiesByDefault(state, show, 1, 3))
        assertFalse(notifiesByDefault(state.copy(notificationEpisodes = false), show, 1, 2))
    }

    @Test
    fun viewingInsightsLoadOnceAndAllowARetryAfterFailure() {
        val store = FakeStore().apply { peopleFailure = IllegalStateException("offline") }
        val viewModel = ProgressViewModel(store, dispatcher)

        viewModel.loadViewingInsights(emptyList())
        assertTrue(viewModel.viewingInsights.value.actors.isEmpty())
        assertFalse(viewModel.viewingInsights.value.loading)

        store.peopleFailure = null
        viewModel.loadViewingInsights(emptyList())
        viewModel.loadViewingInsights(emptyList())
        assertEquals(listOf("Actor"), viewModel.viewingInsights.value.actors.map { it.name })
        assertEquals(2, store.peopleCalls)
    }
}
