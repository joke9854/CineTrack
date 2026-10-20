package com.cinetrack.data.repository

import com.cinetrack.ui.settings.BackupStore

class RepositoryBackupStore(private val repository: CineTrackRepository) : BackupStore {
    override suspend fun exportBackupFiles(sections: Set<String>) = repository.exportBackupFiles(sections)
    override suspend fun restoreBackupFiles(files: Map<String, String>, onStage: (String) -> Unit) = repository.restoreBackupFiles(files, onStage)
    override suspend fun restoreAutomaticBackup() = repository.restoreAutomaticBackup()
    override suspend fun refreshProgressCache() {
        repository.refreshProgressCache()
    }
}
