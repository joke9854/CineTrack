package com.cinetrack.ui.settings

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cinetrack.data.update.AppChangelogState
import com.cinetrack.data.update.AppUpdateInfo
import com.cinetrack.data.update.AppUpdateState
import com.cinetrack.data.update.GitHubAppUpdater
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** The preferences the Settings pages owned by [SettingsViewModel] read and write. */
interface SettingsPreferences {
    /** Changes whenever the error log file is appended to or cleared. */
    val errorLogChanges: StateFlow<Long>
    fun readErrorLogs(): List<String>
    fun clearErrorLog()
    suspend fun setLanguage(value: String)
}

/** The in-app updater, behind an interface so tests don't reach GitHub. */
interface AppUpdater {
    suspend fun check(): Result<AppUpdateInfo?>
    suspend fun latestRelease(): Result<AppUpdateInfo?>
    suspend fun download(context: Context, update: AppUpdateInfo, onProgress: (Float) -> Unit): Result<File>
    fun launchInstaller(context: Context, apk: File): Boolean
}

object GitHubUpdater : AppUpdater {
    override suspend fun check() = GitHubAppUpdater.check()
    override suspend fun latestRelease() = GitHubAppUpdater.latestRelease()
    override suspend fun download(context: Context, update: AppUpdateInfo, onProgress: (Float) -> Unit) =
        GitHubAppUpdater.download(context, update, onProgress)
    override fun launchInstaller(context: Context, apk: File) = GitHubAppUpdater.launchInstaller(context, apk)
}

/**
 * Settings pages that don't touch the library or sync state: About (app update and
 * changelog), Logs and Language. Activity-scoped, because the update pill on every
 * screen shares [appUpdateState] with the About page.
 */
class SettingsViewModel(
    private val preferences: SettingsPreferences,
    private val updater: AppUpdater = GitHubUpdater,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val _appUpdateState = MutableStateFlow<AppUpdateState>(AppUpdateState.Idle)
    val appUpdateState: StateFlow<AppUpdateState> = _appUpdateState.asStateFlow()
    private val _appChangelogState = MutableStateFlow<AppChangelogState>(AppChangelogState.Idle)
    val appChangelogState: StateFlow<AppChangelogState> = _appChangelogState.asStateFlow()
    private var downloadedUpdateFile: File? = null

    private val _errorLogs = MutableStateFlow<List<String>>(emptyList())
    val errorLogs: StateFlow<List<String>> = _errorLogs.asStateFlow()

    /** Failures to show in the app's error banner. */
    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    init {
        // Workers and the main ViewModel append to the log file; follow it.
        viewModelScope.launch {
            preferences.errorLogChanges.collect { refreshErrorLogsNow() }
        }
    }

    private suspend fun refreshErrorLogsNow() {
        _errorLogs.value = withContext(io) { preferences.readErrorLogs() }
    }

    /** Background workers in another process may have written; re-read on open. */
    fun refreshErrorLogs() {
        viewModelScope.launch { refreshErrorLogsNow() }
    }

    fun clearErrorLogs() {
        viewModelScope.launch {
            withContext(io) { preferences.clearErrorLog() }
            _errorLogs.value = emptyList()
        }
    }

    fun exportLogs(context: Context) {
        val lines = _errorLogs.value
        viewModelScope.launch {
            runCatching {
                val file = withContext(io) {
                    val directory = File(context.cacheDir, "exports").apply { mkdirs() }
                    File(directory, "cinetrack-logs.txt").apply {
                        writeText(lines.joinToString(separator = "\n", postfix = "\n"))
                    }
                }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(share, context.getString(com.cinetrack.R.string.export_logs)))
            }.onFailure { failure -> failure.message?.let(_errors::tryEmit) }
        }
    }

    fun setLanguage(value: String) {
        viewModelScope.launch { preferences.setLanguage(value) }
    }

    fun checkForAppUpdate() {
        if (_appUpdateState.value is AppUpdateState.Checking || _appUpdateState.value is AppUpdateState.Downloading) return
        viewModelScope.launch {
            _appUpdateState.value = AppUpdateState.Checking
            updater.check()
                .onSuccess { update ->
                    _appUpdateState.value = update?.let(AppUpdateState::Available) ?: AppUpdateState.UpToDate
                }
                .onFailure { failure ->
                    _appUpdateState.value = AppUpdateState.Error(failure.message ?: "Update check failed")
                }
        }
    }

    fun loadAppChangelog() {
        if (_appChangelogState.value is AppChangelogState.Loading) return
        viewModelScope.launch {
            _appChangelogState.value = AppChangelogState.Loading
            updater.latestRelease()
                .onSuccess { release ->
                    _appChangelogState.value = release?.let(AppChangelogState::Available)
                        ?: AppChangelogState.Empty
                }
                .onFailure { failure ->
                    _appChangelogState.value = AppChangelogState.Error(
                        failure.message ?: "Could not load the changelog",
                    )
                }
        }
    }

    fun openAppUpdate(context: Context) {
        val update = (_appUpdateState.value as? AppUpdateState.Available)?.update ?: return
        viewModelScope.launch {
            val cached = downloadedUpdateFile?.takeIf(File::exists)
            if (cached != null) {
                runCatching { updater.launchInstaller(context, cached) }
                    .onFailure { _appUpdateState.value = AppUpdateState.Error(it.message ?: "Could not open Android installer") }
                return@launch
            }
            _appUpdateState.value = AppUpdateState.Downloading(update, 0f)
            updater.download(context, update) { progress ->
                _appUpdateState.value = AppUpdateState.Downloading(update, progress)
            }.onSuccess { apk ->
                downloadedUpdateFile = apk
                _appUpdateState.value = AppUpdateState.Available(update)
                runCatching { updater.launchInstaller(context, apk) }
                    .onFailure { _appUpdateState.value = AppUpdateState.Error(it.message ?: "Could not open Android installer") }
            }.onFailure { failure ->
                _appUpdateState.value = AppUpdateState.Error(failure.message ?: "Update download failed")
            }
        }
    }

    class Factory(private val preferences: SettingsPreferences) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SettingsViewModel(preferences) as T
    }
}
