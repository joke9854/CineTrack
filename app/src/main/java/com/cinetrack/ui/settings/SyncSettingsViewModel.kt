package com.cinetrack.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cinetrack.data.repository.CineTrackRepository
import com.cinetrack.data.sync.ConnectionResult
import com.cinetrack.data.sync.ManagedBootstrapSummary
import com.cinetrack.data.sync.TrackingSyncError
import com.cinetrack.domain.FloppyBootstrapProgress
import com.cinetrack.domain.FloppyConnectionError
import com.cinetrack.domain.FloppyConnectionStage
import com.cinetrack.domain.FloppyConnectionUiState
import com.cinetrack.domain.FloppyUnplacedItem
import com.cinetrack.domain.MainTrackerSwapUiState
import com.cinetrack.domain.SyncConflictChoice
import com.cinetrack.domain.SyncOperationCard
import com.cinetrack.domain.SyncProgress
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

/** The repository side of the Sync, Sync operations, Simkl and Floppy pages. */
interface SyncSettingsStore {
    suspend fun floppyServerVersion(): String?
    suspend fun connectFloppy(
        baseUrl: String,
        apiKey: String,
        allowInsecureLocalHttp: Boolean,
        onStage: (FloppyConnectionStage, String?) -> Unit,
        sameServerAtNewAddress: Boolean?,
    ): ConnectionResult
    suspend fun discardFloppyCredential(baseUrl: String, credential: String)
    suspend fun floppySignInServer(baseUrl: String, allowInsecureLocalHttp: Boolean): Result<String>
    suspend fun beginFloppyLogin(context: Context, baseUrl: String, allowInsecureLocalHttp: Boolean): Result<Unit>
    suspend fun completeFloppySignIn(code: String?, state: String?, error: String?, issuer: String?): Result<CineTrackRepository.FloppySignIn>
    suspend fun retryFloppyBootstrap()
    suspend fun disconnectFloppy()
    suspend fun refreshFloppyUsername(): String?
    /** Unplaced episodes the user has not dismissed. */
    suspend fun floppyUnplacedItems(): List<FloppyUnplacedItem>
    suspend fun dismissFloppyUnplaced(key: String)
    suspend fun loadSyncOperations(): List<SyncOperationCard>
    suspend fun loadManagedBootstrapSummary(): ManagedBootstrapSummary?
    suspend fun retrySyncOperation(operationId: String): Result<Unit>
    suspend fun resolveSyncConflict(operationId: String, choice: SyncConflictChoice): Result<Unit>
    suspend fun beginSimklLogin(context: Context): Result<Unit>
    suspend fun disconnectSimkl()
    suspend fun refreshSimklUsername(): String?
    suspend fun setBackgroundSync(enabled: Boolean)
    suspend fun setWifiOnly(enabled: Boolean)
    /** Appends one line to the error log; callers never pass credentials. */
    fun appendDiagnostic(line: String)
}

/**
 * What the sync pages still take from CineTrackViewModel: running a sync, swapping MAIN and the
 * WorkManager bootstrap progress all go through its sync mutex and provider-instance check.
 */
class TrackingSyncControls(
    val syncProgress: StateFlow<SyncProgress>,
    val onSync: () -> Unit,
    val mainTrackerSwap: StateFlow<MainTrackerSwapUiState>,
    val onSwapMainTracker: () -> Unit,
    val floppyBootstrapProgress: StateFlow<FloppyBootstrapProgress?>,
)

/** What CineTrackViewModel must reflect once a write on these pages has finished. */
sealed interface SyncSettingsChange {
    /** A Floppy connect, disconnect or initial-sync retry finished: re-read the cached state. */
    data object ProviderChanged : SyncSettingsChange
    /** A conflict was resolved: re-read the cached state, keeping the visible sync. */
    data object ConflictResolved : SyncSettingsChange
    /** The error banner text; null clears it. */
    data class Error(val message: String?) : SyncSettingsChange
    data object SimklDisconnected : SyncSettingsChange
    data class SimklUsername(val name: String) : SyncSettingsChange
    data class FloppyUsername(val name: String) : SyncSettingsChange
    data class BackgroundSync(val enabled: Boolean) : SyncSettingsChange
    data class WifiOnly(val enabled: Boolean) : SyncSettingsChange
}

/**
 * The Sync, Sync operations, Simkl and Floppy pages: Floppy connection and sign-in, the
 * address-move question, sync operations, unplaced episodes and the background-sync switches.
 * Activity-scoped, because the address-move dialog and the sign-in callback live in CineTrackApp.
 * Running a sync and swapping MAIN stay on CineTrackViewModel, which owns the sync mutex.
 */
class SyncSettingsViewModel(
    private val store: SyncSettingsStore,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    // Buffered until CineTrackApp collects, so a connect finishing during recreation is not lost.
    private val _changes = Channel<SyncSettingsChange>(Channel.BUFFERED)
    val changes: Flow<SyncSettingsChange> = _changes.receiveAsFlow()

    private val floppyConnectionMutex = Mutex()
    /** Read when a connect starts, which must publish its first stage synchronously. */
    private var knownServerVersion: String? = null

    private val _floppyConnectionUiState = MutableStateFlow(FloppyConnectionUiState())
    val floppyConnectionUiState: StateFlow<FloppyConnectionUiState> = _floppyConnectionUiState.asStateFlow()

    /** A connect waiting for "is this the same Floppy at a new address?"; never shown in logs. */
    data class FloppyAddressMove(val baseUrl: String, val credential: String, val allowInsecureLocalHttp: Boolean)
    private val _floppyAddressMove = MutableStateFlow<FloppyAddressMove?>(null)
    val floppyAddressMove: StateFlow<FloppyAddressMove?> = _floppyAddressMove.asStateFlow()

    private val _floppyUnplaced = MutableStateFlow<List<FloppyUnplacedItem>>(emptyList())
    /** Episodes Floppy could not place; the settings screen links each to Floppy. */
    val floppyUnplaced: StateFlow<List<FloppyUnplacedItem>> = _floppyUnplaced.asStateFlow()

    private val _syncOperations = MutableStateFlow<List<SyncOperationCard>>(emptyList())
    val syncOperations: StateFlow<List<SyncOperationCard>> = _syncOperations.asStateFlow()
    private val _managedBootstrapSummary = MutableStateFlow<ManagedBootstrapSummary?>(null)
    val managedBootstrapSummary: StateFlow<ManagedBootstrapSummary?> = _managedBootstrapSummary.asStateFlow()

    init {
        viewModelScope.launch { knownServerVersion = withContext(io) { store.floppyServerVersion() } }
    }

    private suspend fun publish(change: SyncSettingsChange) = _changes.send(change)

    /** The user's answer: [same] keeps the connection and its finished initial sync. */
    fun answerFloppyAddressMove(same: Boolean) {
        val pending = _floppyAddressMove.value ?: return
        _floppyAddressMove.value = null
        appendFloppyDiagnostic("Floppy connect: new address confirmed as ${if (same) "the same server" else "a new server"}")
        connectFloppy(pending.baseUrl, pending.credential, pending.allowInsecureLocalHttp, sameServerAtNewAddress = same)
    }

    /** Dismissed: nothing changes and the fresh sign-in is ended. */
    fun cancelFloppyAddressMove() {
        val pending = _floppyAddressMove.value ?: return
        _floppyAddressMove.value = null
        _floppyConnectionUiState.value = FloppyConnectionUiState()
        viewModelScope.launch(io) { store.discardFloppyCredential(pending.baseUrl, pending.credential) }
    }

    fun connectFloppy(
        baseUrl: String,
        apiKey: String,
        allowInsecureLocalHttp: Boolean,
        sameServerAtNewAddress: Boolean? = null,
    ) {
        val normalizedUrl = baseUrl.trim()
        val normalizedApiKey = apiKey.trim()
        if (normalizedUrl.isBlank()) {
            publishFloppyFailure(FloppyConnectionError.INVALID_REQUEST)
            return
        }
        if (!floppyConnectionMutex.tryLock()) return
        // This acknowledgement is intentionally synchronous. It must be
        // published before launching work or reading DataStore so the next
        // Compose frame proves that the tap reached the ViewModel.
        _floppyConnectionUiState.value = FloppyConnectionUiState(
            stage = FloppyConnectionStage.CHECKING_SERVER,
            serverVersion = knownServerVersion,
        )
        appendFloppyDiagnostic("Floppy connect: UI event received")
        appendFloppyDiagnostic("Floppy connect: checking server")
        viewModelScope.launch(io) {
            var cancelled = false
            try {
                val result = store.connectFloppy(
                    normalizedUrl,
                    normalizedApiKey,
                    allowInsecureLocalHttp,
                    onStage = { stage, version -> publishFloppyStage(stage, version) },
                    sameServerAtNewAddress = sameServerAtNewAddress,
                )
                when (result) {
                    ConnectionResult.AddressMoveNeedsConfirmation -> {
                        appendFloppyDiagnostic("Floppy connect: same account at a new address, asking")
                        withContext(Dispatchers.Main) {
                            _floppyConnectionUiState.value = FloppyConnectionUiState()
                            _floppyAddressMove.value = FloppyAddressMove(normalizedUrl, normalizedApiKey, allowInsecureLocalHttp)
                        }
                    }
                    ConnectionResult.Connected -> {
                        val version = store.floppyServerVersion()
                        withContext(Dispatchers.Main) {
                            knownServerVersion = version
                            publish(SyncSettingsChange.ProviderChanged)
                            publishFloppyStage(FloppyConnectionStage.COMPLETE, version)
                        }
                    }
                    ConnectionResult.AuthenticationRequired -> {
                        withContext(Dispatchers.Main) {
                            publish(SyncSettingsChange.ProviderChanged)
                            publishFloppyFailure(FloppyConnectionError.AUTHENTICATION)
                        }
                    }
                    is ConnectionResult.Failed -> {
                        withContext(Dispatchers.Main) {
                            // Validation can commit the provider before a
                            // bootstrap/delivery failure. Always refresh the
                            // persistent projection, but never overwrite the
                            // dedicated action flow with that publication.
                            publish(SyncSettingsChange.ProviderChanged)
                            publishFloppyFailure(result.error.toFloppyConnectionError(), result.error.userMessage())
                        }
                    }
                }
            } catch (error: kotlinx.coroutines.CancellationException) {
                cancelled = true
                throw error
            } catch (error: Throwable) {
                withContext(Dispatchers.Main) {
                    publish(SyncSettingsChange.ProviderChanged)
                    publishFloppyFailure(error.toFloppyConnectionError())
                }
            } finally {
                if (!cancelled && _floppyConnectionUiState.value.running) {
                    publishFloppyFailure(FloppyConnectionError.UNKNOWN)
                }
                floppyConnectionMutex.unlock()
            }
        }
    }

    fun retryFloppyInitialSync() {
        if (!floppyConnectionMutex.tryLock()) return
        _floppyConnectionUiState.value = FloppyConnectionUiState(
            stage = FloppyConnectionStage.INITIAL_SYNC,
            serverVersion = knownServerVersion,
        )
        appendFloppyDiagnostic("Floppy connect: retrying initial sync")
        viewModelScope.launch(io) {
            try {
                store.retryFloppyBootstrap()
                val version = store.floppyServerVersion()
                withContext(Dispatchers.Main) {
                    knownServerVersion = version
                    publish(SyncSettingsChange.ProviderChanged)
                    publishFloppyStage(FloppyConnectionStage.COMPLETE, version)
                }
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Throwable) {
                withContext(Dispatchers.Main) {
                    publish(SyncSettingsChange.ProviderChanged)
                    publishFloppyFailure(FloppyConnectionError.BOOTSTRAP)
                }
            } finally {
                floppyConnectionMutex.unlock()
            }
        }
    }

    fun disconnectFloppy() {
        _floppyConnectionUiState.value = FloppyConnectionUiState()
        viewModelScope.launch(io) {
            store.disconnectFloppy()
            withContext(Dispatchers.Main) { publish(SyncSettingsChange.ProviderChanged) }
        }
    }

    /** Opens Floppy's sign-in page once the server confirms it offers sign-in. */
    fun beginFloppySignIn(context: Context, baseUrl: String, allowInsecureLocalHttp: Boolean) {
        if (baseUrl.isBlank()) {
            publishFloppyFailure(FloppyConnectionError.INVALID_REQUEST)
            return
        }
        if (floppyConnectionMutex.isLocked) return
        _floppyConnectionUiState.value = FloppyConnectionUiState(
            stage = FloppyConnectionStage.CHECKING_SERVER,
            serverVersion = knownServerVersion,
        )
        appendFloppyDiagnostic("Floppy sign-in: checking server")
        viewModelScope.launch {
            val server = withContext(io) { store.floppySignInServer(baseUrl.trim(), allowInsecureLocalHttp) }
            server.onSuccess { url ->
                store.beginFloppyLogin(context, url, allowInsecureLocalHttp)
                    .onSuccess {
                        appendFloppyDiagnostic("Floppy sign-in: browser opened")
                        _floppyConnectionUiState.value = FloppyConnectionUiState(serverVersion = knownServerVersion)
                    }
                    .onFailure { publishFloppyFailure(FloppyConnectionError.UNKNOWN, it.message) }
            }.onFailure { error ->
                publishFloppyFailure(error.toFloppyConnectionError(), error.userMessage())
            }
        }
    }

    /** Finishes a sign-in returned by the browser, then connects exactly like an API key. */
    fun completeFloppySignIn(code: String?, state: String?, callbackError: String?, issuer: String?) {
        if (floppyConnectionMutex.isLocked) return
        _floppyConnectionUiState.value = FloppyConnectionUiState(
            stage = FloppyConnectionStage.AUTHENTICATING,
            serverVersion = knownServerVersion,
        )
        viewModelScope.launch {
            val signIn = withContext(io) { store.completeFloppySignIn(code, state, callbackError, issuer) }
            signIn.onSuccess {
                appendFloppyDiagnostic("Floppy sign-in: tokens received")
                connectFloppy(it.baseUrl, it.credential, it.allowInsecureLocalHttp)
            }.onFailure { error ->
                appendFloppyDiagnostic("Floppy sign-in: refused")
                publishFloppyFailure(FloppyConnectionError.AUTHENTICATION, error.message)
            }
        }
    }

    /** Fills in the Floppy account name shown in Settings, once per connection. */
    fun loadFloppyUsername() {
        viewModelScope.launch {
            withContext(io) { store.refreshFloppyUsername() }?.let { publish(SyncSettingsChange.FloppyUsername(it)) }
        }
    }

    fun refreshFloppyUnplaced() {
        viewModelScope.launch {
            _floppyUnplaced.value = withContext(io) {
                runCatching { store.floppyUnplacedItems() }.getOrDefault(emptyList())
            }
        }
    }

    /** Hides an entry the user handled (or chose to ignore); it stays hidden. */
    fun dismissFloppyUnplaced(item: FloppyUnplacedItem) {
        _floppyUnplaced.update { items -> items.filterNot { it.dismissKey == item.dismissKey } }
        viewModelScope.launch(io) { store.dismissFloppyUnplaced(item.dismissKey) }
    }

    fun refreshSyncOperations() {
        viewModelScope.launch {
            _syncOperations.value = withContext(io) { store.loadSyncOperations() }
            _managedBootstrapSummary.value = withContext(io) { store.loadManagedBootstrapSummary() }
        }
    }

    fun retrySyncOperation(operationId: String) {
        if (operationId.startsWith("bootstrap:")) {
            retryFloppyInitialSync()
            return
        }
        viewModelScope.launch {
            val result = withContext(io) { store.retrySyncOperation(operationId) }
            _syncOperations.value = withContext(io) { store.loadSyncOperations() }
            result.exceptionOrNull()?.let { error -> publish(SyncSettingsChange.Error(error.message)) }
        }
    }

    fun resolveSyncConflict(operationId: String, choice: SyncConflictChoice) {
        viewModelScope.launch {
            val result = withContext(io) { store.resolveSyncConflict(operationId, choice) }
            _syncOperations.value = withContext(io) { store.loadSyncOperations() }
            if (result.isSuccess) {
                publish(SyncSettingsChange.ConflictResolved)
            } else {
                publish(SyncSettingsChange.Error(result.exceptionOrNull()?.message))
            }
        }
    }

    fun beginSimklLogin(context: Context) {
        viewModelScope.launch {
            publish(SyncSettingsChange.Error(null))
            store.beginSimklLogin(context).onFailure { publish(SyncSettingsChange.Error(it.message)) }
        }
    }

    fun disconnectSimkl() {
        viewModelScope.launch {
            store.disconnectSimkl()
            publish(SyncSettingsChange.SimklDisconnected)
        }
    }

    /** Fills in the Simkl account name shown in Settings, once per connection. */
    fun loadSimklUsername() {
        viewModelScope.launch {
            withContext(io) { store.refreshSimklUsername() }?.let { publish(SyncSettingsChange.SimklUsername(it)) }
        }
    }

    fun setBackgroundSync(enabled: Boolean) {
        viewModelScope.launch {
            store.setBackgroundSync(enabled)
            publish(SyncSettingsChange.BackgroundSync(enabled))
        }
    }

    fun setWifiOnly(enabled: Boolean) {
        viewModelScope.launch {
            store.setWifiOnly(enabled)
            publish(SyncSettingsChange.WifiOnly(enabled))
        }
    }

    private fun publishFloppyStage(stage: FloppyConnectionStage, serverVersion: String?) {
        val current = _floppyConnectionUiState.value
        _floppyConnectionUiState.value = current.copy(
            stage = stage,
            error = null,
            message = null,
            serverVersion = serverVersion ?: current.serverVersion,
        )
        val message = when (stage) {
            FloppyConnectionStage.CHECKING_SERVER -> "Floppy connect: checking server"
            FloppyConnectionStage.AUTHENTICATING -> "Floppy connect: authenticating"
            FloppyConnectionStage.ACTIVATING -> "Floppy connect: activating provider"
            FloppyConnectionStage.INITIAL_SYNC -> "Floppy connect: starting bootstrap"
            FloppyConnectionStage.COMPLETE -> "Floppy connect: complete"
            else -> null
        }
        message?.let(::appendFloppyDiagnostic)
        if (stage == FloppyConnectionStage.AUTHENTICATING) {
            appendFloppyDiagnostic("Floppy connect: server info OK, version=${serverVersion ?: current.serverVersion ?: "unknown"}")
        }
    }

    private fun publishFloppyFailure(error: FloppyConnectionError, message: String? = null) {
        _floppyConnectionUiState.value = _floppyConnectionUiState.value.copy(
            stage = FloppyConnectionStage.FAILED,
            error = error,
            message = message,
        )
        appendFloppyDiagnostic("Floppy connect: ${error.name.lowercase()}")
    }

    private fun appendFloppyDiagnostic(message: String) {
        val line = "${java.time.Instant.now()}  $message"
        viewModelScope.launch(io) { store.appendDiagnostic(line) }
    }

    /** Errors whose own text tells the user what to do; others use the generic text. */
    private fun Throwable.userMessage(): String? = when (this) {
        is TrackingSyncError.Validation, is TrackingSyncError.TokenScope -> message
        is TrackingSyncError.BootstrapFailure -> (cause as? TrackingSyncError.Validation)?.message
        else -> null
    }

    private fun Throwable.toFloppyConnectionError(): FloppyConnectionError = when (this) {
        is TrackingSyncError.AuthenticationRequired -> FloppyConnectionError.AUTHENTICATION
        is TrackingSyncError.DnsFailure -> FloppyConnectionError.DNS
        is TrackingSyncError.Timeout -> FloppyConnectionError.TIMEOUT
        is TrackingSyncError.TlsFailure -> FloppyConnectionError.TLS
        is TrackingSyncError.NetworkUnavailable -> FloppyConnectionError.NETWORK
        is TrackingSyncError.WrongApi -> FloppyConnectionError.WRONG_API
        is TrackingSyncError.InvalidRemoteData -> FloppyConnectionError.INVALID_RESPONSE
        is TrackingSyncError.InvalidUrl -> FloppyConnectionError.INVALID_REQUEST
        is TrackingSyncError.BootstrapFailure -> FloppyConnectionError.BOOTSTRAP
        else -> FloppyConnectionError.UNKNOWN
    }

    class Factory(private val store: SyncSettingsStore) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SyncSettingsViewModel(store) as T
    }
}
