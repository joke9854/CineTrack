package com.cinetrack.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cinetrack.R
import com.cinetrack.domain.BackupProgress
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
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** The repository side of a backup; the history it restores keeps its exact timestamps. */
interface BackupStore {
    suspend fun exportBackupFiles(sections: Set<String>): Map<String, String>
    suspend fun restoreBackupFiles(files: Map<String, String>, onStage: (String) -> Unit): Int
    suspend fun restoreAutomaticBackup(): Int
    suspend fun refreshProgressCache()
}

/**
 * The Export page: backup export, restore from a file or from the automatic backup, and the
 * progress pill. Activity-scoped, because the pill shows on every screen.
 */
class BackupViewModel(
    private val store: BackupStore,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val errors: SharedFlow<String> = _errors.asSharedFlow()
    private val _restored = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** A restore finished; CineTrackViewModel reloads its state from the database. */
    val restored: SharedFlow<Unit> = _restored.asSharedFlow()

    private val _backupProgress = MutableStateFlow<BackupProgress?>(null)
    val backupProgress: StateFlow<BackupProgress?> = _backupProgress.asStateFlow()
    private var backupProgressClear: kotlinx.coroutines.Job? = null

    private fun backupStep(restore: Boolean, stage: Int, fraction: Float?) {
        backupProgressClear?.cancel()
        _backupProgress.value = BackupProgress(restore, stage, fraction)
    }

    private fun backupFinished(restore: Boolean, failed: Boolean) {
        _backupProgress.value = BackupProgress(
            restore = restore,
            stage = when {
                failed -> R.string.backup_failed
                restore -> R.string.restore_done
                else -> R.string.backup_done
            },
            fraction = 1f,
            finished = true,
            failed = failed,
        )
        backupProgressClear = viewModelScope.launch {
            kotlinx.coroutines.delay(2600)
            _backupProgress.value = null
        }
    }

    fun exportData(context: Context, sections: Set<String> = emptySet()) {
        viewModelScope.launch {
            backupStep(restore = false, stage = R.string.backup_stage_collect, fraction = null)
            runCatching {
                val file = withContext(io) {
                    val files = store.exportBackupFiles(sections)
                    val directory = File(context.cacheDir, "exports").apply { mkdirs() }
                    val target = File(directory, "cinetrack-backup-${LocalDate.now()}.zip")
                    val total = files.values.sumOf { it.length.toLong() }.coerceAtLeast(1L)
                    var written = 0L
                    ZipOutputStream(FileOutputStream(target)).use { archive ->
                        files.forEach { (path, contents) ->
                            archive.putNextEntry(ZipEntry(path))
                            archive.write(contents.toByteArray())
                            archive.closeEntry()
                            written += contents.length
                            backupStep(restore = false, stage = R.string.backup_stage_compress, fraction = .3f + .7f * written / total)
                        }
                    }
                    target
                }
                backupFinished(restore = false, failed = false)
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(share, context.getString(R.string.export_data)))
            }.onFailure {
                backupFinished(restore = false, failed = true)
                it.message?.let(_errors::tryEmit)
            }
        }
    }

    fun restoreData(context: Context, uri: Uri) {
        viewModelScope.launch {
            backupStep(restore = true, stage = R.string.backup_stage_read, fraction = 0f)
            runCatching {
                val restored = withContext(io) {
                    val entries = linkedMapOf<String, String>()
                    val archiveSize = runCatching { context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } }.getOrNull() ?: -1L
                    var lastReported = 0f
                    context.contentResolver.openInputStream(uri)?.use { raw ->
                        // Reading is a third of the restore; report it by compressed bytes read.
                        val input = object : java.io.FilterInputStream(raw) {
                            var count = 0L
                            private fun advance(read: Long) {
                                if (read <= 0 || archiveSize <= 0) return
                                count += read
                                val fraction = .33f * (count.toFloat() / archiveSize).coerceIn(0f, 1f)
                                if (fraction - lastReported >= .01f) {
                                    lastReported = fraction
                                    backupStep(restore = true, stage = R.string.backup_stage_read, fraction = fraction)
                                }
                            }
                            override fun read(): Int = super.read().also { if (it >= 0) advance(1) }
                            override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { advance(it.toLong()) }
                        }
                        ZipInputStream(input).use { archive ->
                            var totalBytes = 0L
                            var entry = archive.nextEntry
                            while (entry != null) {
                                if (!entry.isDirectory) {
                                    check(entries.size < 32) { "Backup contains too many entries" }
                                    check(entry.size <= 32L * 1024 * 1024 || entry.size < 0L) { "Backup entry is too large" }
                                    val output = ByteArrayOutputStream()
                                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                    var entryBytes = 0L
                                    while (true) {
                                        val read = archive.read(buffer)
                                        if (read <= 0) break
                                        entryBytes += read
                                        totalBytes += read
                                        check(entryBytes <= 32L * 1024 * 1024) { "Backup entry is too large" }
                                        check(totalBytes <= 64L * 1024 * 1024) { "Backup is too large" }
                                        output.write(buffer, 0, read)
                                    }
                                    entries[entry.name] = output.toByteArray().decodeToString()
                                }
                                archive.closeEntry()
                                entry = archive.nextEntry
                            }
                        }
                    } ?: error("Could not open backup")
                    store.restoreBackupFiles(entries) { stage ->
                        if (stage == "verify") backupStep(restore = true, stage = R.string.backup_stage_verify, fraction = .38f)
                        else backupStep(restore = true, stage = R.string.backup_stage_write, fraction = .55f)
                    }.also {
                        backupStep(restore = true, stage = R.string.backup_stage_refresh, fraction = .8f)
                        store.refreshProgressCache()
                    }
                }
                _restored.emit(Unit)
                backupFinished(restore = true, failed = false)
                restored
            }.onFailure {
                backupFinished(restore = true, failed = true)
                it.message?.let(_errors::tryEmit)
            }
        }
    }

    fun restoreAutomaticBackup() {
        viewModelScope.launch {
            backupStep(restore = true, stage = R.string.backup_stage_verify, fraction = .2f)
            runCatching {
                withContext(io) {
                    store.restoreAutomaticBackup()
                    backupStep(restore = true, stage = R.string.backup_stage_refresh, fraction = .8f)
                    store.refreshProgressCache()
                }
                _restored.emit(Unit)
                backupFinished(restore = true, failed = false)
            }.onFailure {
                backupFinished(restore = true, failed = true)
                it.message?.let(_errors::tryEmit)
            }
        }
    }

    class Factory(private val store: BackupStore) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = BackupViewModel(store) as T
    }
}
