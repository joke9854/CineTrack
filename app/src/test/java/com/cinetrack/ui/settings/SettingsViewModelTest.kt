package com.cinetrack.ui.settings

import android.content.Context
import org.robolectric.RuntimeEnvironment
import com.cinetrack.data.update.AppChangelogState
import com.cinetrack.data.update.AppUpdateInfo
import com.cinetrack.data.update.AppUpdateState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class SettingsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private class FakePreferences(var lines: List<String> = emptyList()) : SettingsPreferences {
        private val changes = MutableStateFlow(0L)
        override val errorLogChanges: StateFlow<Long> = changes
        var language: String? = null
        override fun readErrorLogs() = lines
        override fun clearErrorLog() { lines = emptyList(); changes.value++ }
        override suspend fun setLanguage(value: String) { language = value }
        val notifications = MutableStateFlow(NotificationSettings())
        override val notificationSettings: StateFlow<NotificationSettings> = notifications
        override suspend fun setNotification(kind: String, enabled: Boolean) {
            notifications.value = when (kind) {
                "episodes" -> notifications.value.copy(episodes = enabled)
                "movies" -> notifications.value.copy(movies = enabled)
                else -> notifications.value.copy(sync = enabled)
            }
        }
        override suspend fun setQuietHours(enabled: Boolean, startHour: Int?, endHour: Int?) {
            notifications.value = notifications.value.copy(
                quietHoursEnabled = enabled,
                quietHoursStart = startHour ?: notifications.value.quietHoursStart,
                quietHoursEnd = endHour ?: notifications.value.quietHoursEnd,
            )
        }
        fun append(line: String) { lines = lines + line; changes.value++ }
    }

    private class FakeUpdater(
        val checkResult: Result<AppUpdateInfo?> = Result.success(null),
        val releaseResult: Result<AppUpdateInfo?> = Result.success(null),
        val downloadResult: Result<File> = Result.failure(IllegalStateException("no download")),
    ) : AppUpdater {
        var checks = 0
        var downloads = 0
        val installed = mutableListOf<File>()
        var checkGate: CompletableDeferred<Unit>? = null
        override suspend fun check(): Result<AppUpdateInfo?> { checks++; checkGate?.await(); return checkResult }
        override suspend fun latestRelease() = releaseResult
        override suspend fun download(context: Context, update: AppUpdateInfo, onProgress: (Float) -> Unit): Result<File> {
            downloads++
            onProgress(.5f)
            return downloadResult
        }
        override fun launchInstaller(context: Context, apk: File): Boolean { installed += apk; return true }
    }

    private val update = AppUpdateInfo("0.99.99", "CineTrack 0.99.99", "notes", "https://example.invalid/r", "https://example.invalid/a.apk", "https://example.invalid/a.sha256", 10L)

    private fun viewModel(preferences: SettingsPreferences = FakePreferences(), updater: AppUpdater = FakeUpdater()) =
        SettingsViewModel(preferences, updater, dispatcher)

    @Test fun notificationSwitchesWriteAndFollowPreferences() {
        val preferences = FakePreferences()
        val model = viewModel(preferences)
        model.setNotification("movies", false)
        model.setNotification("sync", false)
        assertEquals(NotificationSettings(movies = false, sync = false), model.notificationSettings.value)
        // A restored backup writes preferences directly; the page follows.
        preferences.notifications.value = NotificationSettings(episodes = false)
        assertEquals(NotificationSettings(episodes = false), model.notificationSettings.value)
    }

    @Test fun quietHoursKeepTheWindowWhenOnlyToggled() {
        val preferences = FakePreferences()
        val model = viewModel(preferences)
        model.setQuietHours(true, 22, 7)
        model.setQuietHours(false)
        assertEquals(NotificationSettings(quietHoursEnabled = false, quietHoursStart = 22, quietHoursEnd = 7), model.notificationSettings.value)
    }

    @Test fun logsFollowTheFileAndClear() {
        val preferences = FakePreferences(listOf("a"))
        val model = viewModel(preferences)
        assertEquals(listOf("a"), model.errorLogs.value)
        preferences.append("b")
        assertEquals(listOf("a", "b"), model.errorLogs.value)
        model.clearErrorLogs()
        assertEquals(emptyList<String>(), model.errorLogs.value)
        assertEquals(emptyList<String>(), preferences.lines)
    }

    @Test fun languageIsSaved() {
        val preferences = FakePreferences()
        viewModel(preferences).setLanguage("it")
        assertEquals("it", preferences.language)
    }

    @Test fun updateCheckReportsAvailableOrUpToDate() {
        val found = viewModel(updater = FakeUpdater(checkResult = Result.success(update)))
        found.checkForAppUpdate()
        assertEquals(AppUpdateState.Available(update), found.appUpdateState.value)

        val none = viewModel(updater = FakeUpdater(checkResult = Result.success(null)))
        none.checkForAppUpdate()
        assertEquals(AppUpdateState.UpToDate, none.appUpdateState.value)

        val failing = viewModel(updater = FakeUpdater(checkResult = Result.failure(RuntimeException("offline"))))
        failing.checkForAppUpdate()
        assertEquals(AppUpdateState.Error("offline"), failing.appUpdateState.value)
    }

    @Test fun aSecondCheckWhileCheckingIsIgnored() {
        val updater = FakeUpdater(checkResult = Result.success(null)).apply { checkGate = CompletableDeferred() }
        val model = viewModel(updater = updater)
        model.checkForAppUpdate()
        model.checkForAppUpdate()
        assertEquals(AppUpdateState.Checking, model.appUpdateState.value)
        updater.checkGate!!.complete(Unit)
        assertEquals(1, updater.checks)
        assertEquals(AppUpdateState.UpToDate, model.appUpdateState.value)
    }

    @Test fun changelogStates() {
        val model = viewModel(updater = FakeUpdater(releaseResult = Result.success(update)))
        model.loadAppChangelog()
        assertEquals(AppChangelogState.Available(update), model.appChangelogState.value)

        val empty = viewModel(updater = FakeUpdater(releaseResult = Result.success(null)))
        empty.loadAppChangelog()
        assertEquals(AppChangelogState.Empty, empty.appChangelogState.value)
    }

    @Test fun downloadedUpdateIsReusedOnTheNextInstall() {
        val apk = File.createTempFile("update", ".apk").apply { deleteOnExit() }
        val updater = FakeUpdater(checkResult = Result.success(update), downloadResult = Result.success(apk))
        val model = viewModel(updater = updater)
        model.checkForAppUpdate()
        model.openAppUpdate(context)
        model.openAppUpdate(context)
        assertEquals(1, updater.downloads)
        assertEquals(listOf(apk, apk), updater.installed)
        assertEquals(AppUpdateState.Available(update), model.appUpdateState.value)
    }

    @Test fun failedDownloadReportsAnError() {
        val model = viewModel(updater = FakeUpdater(checkResult = Result.success(update)))
        model.checkForAppUpdate()
        model.openAppUpdate(context)
        val state = model.appUpdateState.value
        assertTrue(state is AppUpdateState.Error)
        assertEquals("no download", (state as AppUpdateState.Error).message)
    }
}
