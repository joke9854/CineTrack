package com.cinetrack.data.repository

import com.cinetrack.ui.settings.BackupStore

class RepositoryBackupStore(
    private val backup: BackupRepository,
    private val repository: CineTrackRepository,
) : BackupStore {
    override suspend fun exportBackupFiles(sections: Set<String>) = backup.exportBackupFiles(sections)
    override suspend fun restoreBackupFiles(files: Map<String, String>, onStage: (String) -> Unit) = backup.restoreBackupFiles(files, onStage)
    override suspend fun restoreAutomaticBackup() = backup.restoreAutomaticBackup()
    override suspend fun refreshProgressCache() {
        repository.refreshProgressCache()
    }
}
