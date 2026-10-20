package com.cinetrack.ui.settings

import android.content.Context
import com.cinetrack.data.repository.CineTrackRepository
import com.cinetrack.data.sync.ConnectionResult
import com.cinetrack.data.sync.ManagedBootstrapSummary
import com.cinetrack.data.sync.TrackingSyncError
import com.cinetrack.domain.FloppyConnectionError
import com.cinetrack.domain.FloppyConnectionStage
import com.cinetrack.domain.FloppyUnplacedItem
import com.cinetrack.domain.SyncConflictChoice
import com.cinetrack.domain.SyncOperationCard
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SyncSettingsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private class FakeStore : SyncSettingsStore {
        var connectResults = ArrayDeque<ConnectionResult>()
        var connectGate: CompletableDeferred<Unit>? = null
        val connectCalls = mutableListOf<Pair<String, Boolean?>>()
        val discarded = mutableListOf<String>()
        var bootstrapRetries = 0
        var operationRetries = 0
        var resolveResult: Result<Unit> = Result.success(Unit)
        var operationLoads = 0
        var simklDisconnects = 0
        var backgroundSync: Boolean? = null
        val diagnostics = mutableListOf<String>()

        override suspend fun floppyServerVersion() = "1.2"
        override suspend fun connectFloppy(
            baseUrl: String,
            apiKey: String,
            allowInsecureLocalHttp: Boolean,
            onStage: (FloppyConnectionStage, String?) -> Unit,
            sameServerAtNewAddress: Boolean?,
        ): ConnectionResult {
            connectCalls += baseUrl to sameServerAtNewAddress
            connectGate?.await()
            onStage(FloppyConnectionStage.AUTHENTICATING, "1.2")
            onStage(FloppyConnectionStage.INITIAL_SYNC, null)
            return connectResults.removeFirstOrNull() ?: ConnectionResult.Connected
        }
        override suspend fun discardFloppyCredential(baseUrl: String, credential: String) { discarded += credential }
        override suspend fun floppySignInServer(baseUrl: String, allowInsecureLocalHttp: Boolean) = Result.success(baseUrl)
        override suspend fun beginFloppyLogin(context: Context, baseUrl: String, allowInsecureLocalHttp: Boolean) = Result.success(Unit)
        override suspend fun completeFloppySignIn(code: String?, state: String?, error: String?, issuer: String?) =
            Result.failure<CineTrackRepository.FloppySignIn>(IllegalStateException("refused"))
        override suspend fun retryFloppyBootstrap() { bootstrapRetries++ }
        override suspend fun disconnectFloppy() = Unit
        override suspend fun refreshFloppyUsername() = "giuseppe"
        override suspend fun floppyUnplacedItems() = listOf(FloppyUnplacedItem(1, "Show", 1, 2, null))
        override suspend fun dismissFloppyUnplaced(key: String) = Unit
        override suspend fun loadSyncOperations(): List<SyncOperationCard> { operationLoads++; return emptyList() }
        override suspend fun loadManagedBootstrapSummary(): ManagedBootstrapSummary? = null
        override suspend fun retrySyncOperation(operationId: String): Result<Unit> { operationRetries++; return Result.success(Unit) }
        override suspend fun resolveSyncConflict(operationId: String, choice: SyncConflictChoice) = resolveResult
        override suspend fun beginSimklLogin(context: Context) = Result.success(Unit)
        override suspend fun disconnectSimkl() { simklDisconnects++ }
        override suspend fun refreshSimklUsername() = "giuseppe"
        override suspend fun setBackgroundSync(enabled: Boolean) { backgroundSync = enabled }
        override suspend fun setWifiOnly(enabled: Boolean) = Unit
        override fun appendDiagnostic(line: String) { diagnostics += line }
    }

    private fun TestScope.subject(store: FakeStore): Pair<SyncSettingsViewModel, List<SyncSettingsChange>> {
        val viewModel = SyncSettingsViewModel(store, dispatcher)
        val changes = mutableListOf<SyncSettingsChange>()
        backgroundScope.launch(dispatcher) { viewModel.changes.toList(changes) }
        return viewModel to changes
    }

    @Test
    fun `connect publishes its stages, then the provider change, and never logs the key`() = runTest(dispatcher) {
        val store = FakeStore()
        val (viewModel, changes) = subject(store)

        viewModel.connectFloppy(" https://floppy.test ", " secret-key ", allowInsecureLocalHttp = false)

        assertEquals(FloppyConnectionStage.COMPLETE, viewModel.floppyConnectionUiState.value.stage)
        assertEquals("1.2", viewModel.floppyConnectionUiState.value.serverVersion)
        assertEquals(listOf<SyncSettingsChange>(SyncSettingsChange.ProviderChanged), changes)
        assertEquals("https://floppy.test", store.connectCalls.single().first)
        assertTrue(store.diagnostics.any { it.endsWith("Floppy connect: complete") })
        assertFalse(store.diagnostics.any { "secret-key" in it })
    }

    @Test
    fun `a second connect while one runs is ignored`() = runTest(dispatcher) {
        val store = FakeStore().apply { connectGate = CompletableDeferred() }
        val (viewModel, _) = subject(store)

        viewModel.connectFloppy("https://floppy.test", "key", allowInsecureLocalHttp = false)
        viewModel.connectFloppy("https://floppy.test", "key", allowInsecureLocalHttp = false)
        assertEquals(FloppyConnectionStage.CHECKING_SERVER, viewModel.floppyConnectionUiState.value.stage)
        store.connectGate!!.complete(Unit)

        assertEquals(1, store.connectCalls.size)
        assertEquals(FloppyConnectionStage.COMPLETE, viewModel.floppyConnectionUiState.value.stage)
    }

    @Test
    fun `a blank address fails without reaching the store`() = runTest(dispatcher) {
        val store = FakeStore()
        val (viewModel, changes) = subject(store)

        viewModel.connectFloppy("  ", "key", allowInsecureLocalHttp = false)

        assertEquals(FloppyConnectionError.INVALID_REQUEST, viewModel.floppyConnectionUiState.value.error)
        assertTrue(store.connectCalls.isEmpty())
        assertTrue(changes.isEmpty())
    }

    @Test
    fun `a failed connect still refreshes the cached state and maps the error`() = runTest(dispatcher) {
        val store = FakeStore().apply { connectResults += ConnectionResult.Failed(TrackingSyncError.Timeout(RuntimeException())) }
        val (viewModel, changes) = subject(store)

        viewModel.connectFloppy("https://floppy.test", "key", allowInsecureLocalHttp = false)

        assertEquals(FloppyConnectionStage.FAILED, viewModel.floppyConnectionUiState.value.stage)
        assertEquals(FloppyConnectionError.TIMEOUT, viewModel.floppyConnectionUiState.value.error)
        assertEquals(listOf<SyncSettingsChange>(SyncSettingsChange.ProviderChanged), changes)
    }

    @Test
    fun `the address-move answer reconnects with it`() = runTest(dispatcher) {
        val store = FakeStore().apply { connectResults += ConnectionResult.AddressMoveNeedsConfirmation }
        val (viewModel, changes) = subject(store)

        viewModel.connectFloppy("https://new.floppy.test", "key", allowInsecureLocalHttp = false)
        assertEquals("https://new.floppy.test", viewModel.floppyAddressMove.value?.baseUrl)
        assertEquals(FloppyConnectionStage.IDLE, viewModel.floppyConnectionUiState.value.stage)
        assertTrue(changes.isEmpty())

        viewModel.answerFloppyAddressMove(same = true)

        assertNull(viewModel.floppyAddressMove.value)
        assertEquals(listOf<Pair<String, Boolean?>>("https://new.floppy.test" to null, "https://new.floppy.test" to true), store.connectCalls)
        assertEquals(listOf<SyncSettingsChange>(SyncSettingsChange.ProviderChanged), changes)
    }

    @Test
    fun `cancelling the address move discards the fresh credential and changes nothing`() = runTest(dispatcher) {
        val store = FakeStore().apply { connectResults += ConnectionResult.AddressMoveNeedsConfirmation }
        val (viewModel, changes) = subject(store)

        viewModel.connectFloppy("https://new.floppy.test", "fresh", allowInsecureLocalHttp = false)
        viewModel.cancelFloppyAddressMove()

        assertNull(viewModel.floppyAddressMove.value)
        assertEquals(listOf("fresh"), store.discarded)
        assertEquals(1, store.connectCalls.size)
        assertTrue(changes.isEmpty())
    }

    @Test
    fun `retrying a bootstrap operation retries the initial sync`() = runTest(dispatcher) {
        val store = FakeStore()
        val (viewModel, changes) = subject(store)

        viewModel.retrySyncOperation("bootstrap:connection-1")

        assertEquals(1, store.bootstrapRetries)
        assertEquals(0, store.operationRetries)
        assertEquals(FloppyConnectionStage.COMPLETE, viewModel.floppyConnectionUiState.value.stage)
        assertEquals(listOf<SyncSettingsChange>(SyncSettingsChange.ProviderChanged), changes)
    }

    @Test
    fun `resolving a conflict reloads the operations and reports the outcome`() = runTest(dispatcher) {
        val store = FakeStore()
        val (viewModel, changes) = subject(store)

        viewModel.resolveSyncConflict("op-1", SyncConflictChoice.KEEP_LOCAL)
        store.resolveResult = Result.failure(IllegalStateException("Floppy refused"))
        viewModel.resolveSyncConflict("op-2", SyncConflictChoice.USE_REMOTE)

        assertEquals(2, store.operationLoads)
        assertEquals(listOf(SyncSettingsChange.ConflictResolved, SyncSettingsChange.Error("Floppy refused")), changes)
    }

    @Test
    fun `simkl and switch changes are reported after the write`() = runTest(dispatcher) {
        val store = FakeStore()
        val (viewModel, changes) = subject(store)

        viewModel.disconnectSimkl()
        viewModel.setBackgroundSync(false)
        viewModel.loadFloppyUsername()

        assertEquals(1, store.simklDisconnects)
        assertEquals(false, store.backgroundSync)
        assertEquals(
            listOf(SyncSettingsChange.SimklDisconnected, SyncSettingsChange.BackgroundSync(false), SyncSettingsChange.FloppyUsername("giuseppe")),
            changes,
        )
    }

    @Test
    fun `dismissed unplaced episodes leave the list`() = runTest(dispatcher) {
        val store = FakeStore()
        val (viewModel, _) = subject(store)

        viewModel.refreshFloppyUnplaced()
        val item = viewModel.floppyUnplaced.value.single()
        viewModel.dismissFloppyUnplaced(item)

        assertTrue(viewModel.floppyUnplaced.value.isEmpty())
    }
}
