package com.cinetrack.ui.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cinetrack.data.sync.ReleaseNotifier
import com.cinetrack.domain.AppUiState
import com.cinetrack.domain.MediaCard
import com.cinetrack.domain.MediaType
import com.cinetrack.domain.PersonCard
import com.cinetrack.domain.TimelineCard
import com.cinetrack.domain.ViewingPeopleInsights
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.cinetrack.ui.viewModelFactoryOf

/** Release reminder keys and writes, plus the people behind the viewing statistics. */
interface ProgressStore {
    val releaseReminders: Flow<Set<String>>
    /** Stores a reminder or mute key on its own, without scheduling anything. */
    suspend fun setReminderKey(key: String, enabled: Boolean)
    /** Stores the explicit reminder key and schedules (or cancels) its notification. */
    suspend fun setReminder(media: MediaCard, release: String, season: Int?, episode: Int?, episodeTitle: String?, enabled: Boolean)
    suspend fun viewingPeople(history: List<TimelineCard>): Pair<List<PersonCard>, List<PersonCard>>
}

/** Whether this release already notifies on its own: it is in the Calendar and its kind of notification is on. */
internal fun notifiesByDefault(state: AppUiState, media: MediaCard, season: Int?, episode: Int?): Boolean {
    val kindOn = if (media.type == MediaType.TV) state.notificationEpisodes else state.notificationMovies
    return kindOn && state.calendar.any { it.media.stableKey == media.stableKey && it.season == season && it.episodeNumber == episode }
}

/**
 * Progress-page state that isn't the Room projection: the Statistics people and the release
 * bells (also used by the Detail and Episode countdowns). Activity-scoped.
 */
class ProgressViewModel(
    private val store: ProgressStore,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    val releaseReminders: StateFlow<Set<String>> = store.releaseReminders
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    private val _viewingInsights = MutableStateFlow(ViewingPeopleInsights())
    val viewingInsights: StateFlow<ViewingPeopleInsights> = _viewingInsights.asStateFlow()

    /** The bell's state: a reminder the user set, or a default release notification they haven't silenced. */
    fun isReleaseReminderOn(reminders: Set<String>, media: MediaCard, release: String, season: Int?, episode: Int?, byDefault: Boolean): Boolean {
        if (ReleaseNotifier.reminderKey(media.stableKey, season, episode, release) in reminders) return true
        return byDefault && ReleaseNotifier.muteKey(media.stableKey, season, episode, release) !in reminders
    }

    /**
     * Turns the bell on or off. A release that notifies by default is silenced (or un-silenced)
     * instead of getting a second, explicit reminder.
     */
    fun setReleaseReminder(media: MediaCard, release: String, season: Int?, episode: Int?, episodeTitle: String?, enabled: Boolean, byDefault: Boolean) {
        viewModelScope.launch {
            if (byDefault) {
                store.setReminderKey(ReleaseNotifier.muteKey(media.stableKey, season, episode, release), !enabled)
                if (!enabled) store.setReminder(media, release, season, episode, episodeTitle, false)
            } else {
                store.setReminder(media, release, season, episode, episodeTitle, enabled)
            }
        }
    }

    fun loadViewingInsights(history: List<TimelineCard>) {
        val current = _viewingInsights.value
        if (current.loading || current.actors.isNotEmpty() || current.directors.isNotEmpty()) return
        viewModelScope.launch {
            _viewingInsights.value = ViewingPeopleInsights(loading = true)
            val result = withContext(io) { runCatching { store.viewingPeople(history) } }
            _viewingInsights.value = result.fold(
                onSuccess = { (actors, directors) -> ViewingPeopleInsights(actors, directors) },
                onFailure = { ViewingPeopleInsights() },
            )
        }
    }

    class Factory(store: ProgressStore) : ViewModelProvider.Factory by viewModelFactoryOf({
        ProgressViewModel(store)
    })
}
