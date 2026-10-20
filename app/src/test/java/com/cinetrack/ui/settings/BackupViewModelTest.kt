package com.cinetrack.ui.settings

import android.content.Context
import android.net.Uri
import com.cinetrack.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
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
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class BackupViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private class FakeStore(val failRestore: Boolean = false) : BackupStore {
        val restoredFiles = mutableListOf<Map<String, String>>()
        var automaticRestores = 0
        var progressRefreshes = 0
        override suspend fun exportBackupFiles(sections: Set<String>) = mapOf("library.json" to "[]")
        override suspend fun restoreBackupFiles(files: Map<String, String>, onStage: (String) -> Unit): Int {
            if (failRestore) error("Backup is from a newer version")
            onStage("verify")
            onStage("write")
            restoredFiles += files
            return files.size
        }
        override suspend fun restoreAutomaticBackup(): Int { automaticRestores++; return 1 }
        override suspend fun refreshProgressCache() { progressRefreshes++ }
    }

    private fun zip(vararg entries: Pair<String, String>): Uri {
        val file = File.createTempFile("backup", ".zip", context.cacheDir)
        ZipOutputStream(file.outputStream()).use { archive ->
            entries.forEach { (name, text) ->
                archive.putNextEntry(ZipEntry(name))
                archive.write(text.toByteArray())
                archive.closeEntry()
            }
        }
        return Uri.fromFile(file)
    }

    @Test fun restoreHandsTheFilesOverUnchangedAndAnnouncesIt() {
        val store = FakeStore()
        val model = BackupViewModel(store, dispatcher)
        var restored = 0
        val job = CoroutineScope(dispatcher).launch { model.restored.collect { restored++ } }
        val history = """[{"watched_at":"2024-03-01T20:15:30.123Z"},{"watched_at":"2024-03-01T20:15:30.123Z"}]"""
        model.restoreData(context, zip("history.json" to history, "settings.json" to "{}"))
        assertEquals(listOf(mapOf("history.json" to history, "settings.json" to "{}")), store.restoredFiles)
        assertEquals(1, store.progressRefreshes)
        assertEquals(1, restored)
        val progress = model.backupProgress.value!!
        assertTrue(progress.finished && !progress.failed)
        assertEquals(R.string.restore_done, progress.stage)
        job.cancel()
    }

    @Test fun aFailedRestoreReportsTheErrorAndReloadsNothing() {
        val model = BackupViewModel(FakeStore(failRestore = true), dispatcher)
        val errors = mutableListOf<String>()
        var restored = 0
        val scope = CoroutineScope(dispatcher)
        val jobs = listOf(
            scope.launch { model.errors.collect { errors += it } },
            scope.launch { model.restored.collect { restored++ } },
        )
        model.restoreData(context, zip("history.json" to "[]"))
        assertEquals(listOf("Backup is from a newer version"), errors)
        assertEquals(0, restored)
        assertTrue(model.backupProgress.value!!.failed)
        jobs.forEach { it.cancel() }
    }

    @Test fun tooManyEntriesAreRefusedBeforeRestoring() {
        val store = FakeStore()
        val model = BackupViewModel(store, dispatcher)
        val errors = mutableListOf<String>()
        val job = CoroutineScope(dispatcher).launch { model.errors.collect { errors += it } }
        model.restoreData(context, zip(*Array(33) { "file$it.json" to "{}" }))
        assertTrue(store.restoredFiles.isEmpty())
        assertEquals(listOf("Backup contains too many entries"), errors)
        job.cancel()
    }

    @Test fun automaticRestoreRefreshesProgressAndAnnounces() {
        val store = FakeStore()
        val model = BackupViewModel(store, dispatcher)
        var restored = 0
        val job = CoroutineScope(dispatcher).launch { model.restored.collect { restored++ } }
        model.restoreAutomaticBackup()
        assertEquals(1, store.automaticRestores)
        assertEquals(1, store.progressRefreshes)
        assertEquals(1, restored)
        job.cancel()
    }
}
