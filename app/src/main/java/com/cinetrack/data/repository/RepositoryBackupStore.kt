package com.cinetrack.data.repository

import com.cinetrack.ui.settings.BackupStore

class RepositoryBackupStore(
    private val backup: BackupRepository,
    private val progressCache: ProgressCacheRepository,
) : BackupStore {
    override suspend fun exportBackupFiles(sections: Set<String>) = backup.exportBackupFiles(sections)
    override suspend fun restoreBackupFiles(files: Map<String, String>, onStage: (String) -> Unit) = backup.restoreBackupFiles(files, onStage)
    override suspend fun restoreAutomaticBackup() = backup.restoreAutomaticBackup()
    override val backupFolder get() = backup.autoBackupFolder
    override suspend fun setBackupFolder(uri: String?) = backup.setAutoBackupFolder(uri)
    override val backupFolderFrequency get() = backup.autoBackupFolderFrequency
    override suspend fun setBackupFolderFrequency(frequency: BackupFolderFrequency) = backup.setAutoBackupFolderFrequency(frequency)
    override suspend fun backupFolderName(uri: String) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { backup.autoBackupFolderName(uri) }
    override suspend fun refreshProgressCache() {
        progressCache.refreshProgressCache(ProgressRefreshRequest(force = true), null)
    }
}
