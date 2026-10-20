package com.cinetrack.data.repository

import android.content.Context
import com.cinetrack.data.people.PeopleRepository
import com.cinetrack.data.sync.ReleaseNotifier
import com.cinetrack.domain.MediaCard
import com.cinetrack.domain.TimelineCard
import com.cinetrack.ui.progress.ProgressStore

class RepositoryProgressStore(
    context: Context,
    private val preferences: AppPreferences,
    private val peopleRepository: PeopleRepository,
) : ProgressStore {
    private val appContext = context.applicationContext
    override val releaseReminders = preferences.releaseReminders
    override suspend fun setReminderKey(key: String, enabled: Boolean) = preferences.setReleaseReminder(key, enabled)
    override suspend fun setReminder(media: MediaCard, release: String, season: Int?, episode: Int?, episodeTitle: String?, enabled: Boolean) =
        ReleaseNotifier.setReminder(appContext, preferences, media, release, season, episode, episodeTitle, enabled)
    override suspend fun viewingPeople(history: List<TimelineCard>) = peopleRepository.viewingPeople(history)
}
