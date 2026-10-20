package com.cinetrack.ui.library

import com.cinetrack.domain.LibraryArtworkRefreshProgress
import com.cinetrack.domain.LibraryArtworkRefreshStage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private class FakeStore(var wifi: Boolean = false, var failure: Exception? = null) : LibraryArtworkStore {
        override val progress = MutableStateFlow<LibraryArtworkRefreshProgress?>(null)
        val enqueued = mutableListOf<Boolean>()
        override suspend fun wifiOnly() = wifi
        override fun enqueue(wifiOnly: Boolean) {
            failure?.let { throw it }
            enqueued += wifiOnly
        }
    }

    @Test
    fun refreshEnqueuesWithTheWifiPreferenceAndShowsThePill() {
        val store = FakeStore(wifi = true)
        val viewModel = LibraryViewModel(store, dispatcher)

        viewModel.refreshArtwork(tmdbConfigured = true)

        assertEquals(listOf(true), store.enqueued)
        val state = viewModel.artwork.value
        assertTrue(state.progressVisible)
        assertFalse(state.refreshing)
        assertEquals(LibraryArtworkRefreshStage.PREPARING, state.progress?.stage)
    }

    @Test
    fun refreshWithoutTmdbDoesNothing() {
        val store = FakeStore()
        val viewModel = LibraryViewModel(store, dispatcher)

        viewModel.refreshArtwork(tmdbConfigured = false)

        assertTrue(store.enqueued.isEmpty())
        assertEquals(LibraryArtworkState(), viewModel.artwork.value)
    }

    @Test
    fun workerProgressReplacesTheStateAndDismissKeepsIt() {
        val store = FakeStore()
        val viewModel = LibraryViewModel(store, dispatcher)
        viewModel.refreshArtwork(tmdbConfigured = true)

        val running = LibraryArtworkRefreshProgress(LibraryArtworkRefreshStage.COMPLETE)
        store.progress.value = running
        assertEquals(running, viewModel.artwork.value.progress)

        viewModel.dismissProgress()
        assertFalse(viewModel.artwork.value.progressVisible)
        assertEquals(running, viewModel.artwork.value.progress)
    }

    @Test
    fun enqueueFailureEmitsTheErrorAndStopsRefreshing() {
        val store = FakeStore(failure = IllegalStateException("WorkManager unavailable"))
        val viewModel = LibraryViewModel(store, dispatcher)
        val errors = mutableListOf<String>()
        val job = CoroutineScope(dispatcher).launch { viewModel.errors.collect { errors += it } }

        viewModel.refreshArtwork(tmdbConfigured = true)

        assertEquals(listOf("WorkManager unavailable"), errors)
        assertFalse(viewModel.artwork.value.refreshing)
        job.cancel()
    }
}
