package com.cinetrack.data.repository

import android.content.Context
import com.cinetrack.data.sync.SyncCoordinator
import com.cinetrack.data.sync.SyncOperationRepository
import com.cinetrack.domain.FloppyConnectionStage
import com.cinetrack.domain.SyncConflictChoice
import com.cinetrack.ui.settings.SyncSettingsStore

class RepositorySyncSettings(
    private val preferences: AppPreferences,
    private val accounts: AccountRepository,
    private val conflicts: SyncConflictResolver,
    private val syncOperationRepository: SyncOperationRepository,
    private val syncCoordinator: SyncCoordinator,
) : SyncSettingsStore {
    override suspend fun floppyServerVersion() = preferences.floppySettingsNow()?.serverVersion
    override suspend fun connectFloppy(
        baseUrl: String,
        apiKey: String,
        allowInsecureLocalHttp: Boolean,
        onStage: (FloppyConnectionStage, String?) -> Unit,
        sameServerAtNewAddress: Boolean?,
    ) = accounts.connectFloppy(baseUrl, apiKey, allowInsecureLocalHttp, onStage, sameServerAtNewAddress)
    override suspend fun discardFloppyCredential(baseUrl: String, credential: String) = accounts.discardFloppyCredential(baseUrl, credential)
    override suspend fun floppySignInServer(baseUrl: String, allowInsecureLocalHttp: Boolean) = accounts.floppySignInServer(baseUrl, allowInsecureLocalHttp)
    override suspend fun beginFloppyLogin(context: Context, baseUrl: String, allowInsecureLocalHttp: Boolean) =
        preferences.beginFloppyLogin(context, baseUrl, allowInsecureLocalHttp)
    override suspend fun completeFloppySignIn(code: String?, state: String?, error: String?, issuer: String?) =
        accounts.completeFloppySignIn(code, state, error, issuer)
    override suspend fun retryFloppyBootstrap() = accounts.retryFloppyBootstrap()
    override suspend fun disconnectFloppy() = accounts.disconnectFloppy()
    override suspend fun refreshFloppyUsername() = accounts.refreshFloppyUsername()
    override suspend fun floppyUnplacedItems(): List<com.cinetrack.domain.FloppyUnplacedItem> {
        val dismissed = preferences.floppyUnplacedDismissedNow()
        return accounts.floppyUnplacedItems().filterNot { it.dismissKey in dismissed }
    }
    override suspend fun dismissFloppyUnplaced(key: String) = preferences.dismissFloppyUnplaced(key)
    override suspend fun loadSyncOperations() = syncOperationRepository.cards()
    override suspend fun loadManagedBootstrapSummary() = syncOperationRepository.managedBootstrapSummary()
    override suspend fun retrySyncOperation(operationId: String) = syncCoordinator.retry(operationId)
    override suspend fun resolveSyncConflict(operationId: String, choice: SyncConflictChoice) = conflicts.resolveSyncConflict(operationId, choice)
    override suspend fun beginSimklLogin(context: Context) = preferences.beginSimklLogin(context)
    override suspend fun disconnectSimkl() = accounts.disconnectSimkl()
    override suspend fun refreshSimklUsername() = accounts.refreshSimklUsername()
    override suspend fun setBackgroundSync(enabled: Boolean) = preferences.setBackgroundSync(enabled)
    override suspend fun setWifiOnly(enabled: Boolean) = preferences.setWifiOnly(enabled)
    override fun appendDiagnostic(line: String) = preferences.appendErrorLog(line)
}
