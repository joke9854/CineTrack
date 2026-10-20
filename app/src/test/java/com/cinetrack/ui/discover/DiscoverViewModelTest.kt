package com.cinetrack.ui.discover

import com.cinetrack.data.discovery.DiscoveryRepository
import com.cinetrack.data.watchprovider.WatchProviderRepository
import com.cinetrack.domain.AppUiState
import com.cinetrack.domain.DiscoverMovieFilters
import com.cinetrack.domain.LibraryStatus
import com.cinetrack.domain.RailIds
import com.cinetrack.domain.discoverBrowseKey
import com.cinetrack.domain.DiscoverPage
import com.cinetrack.domain.MediaCard
import com.cinetrack.domain.MediaType
import com.cinetrack.domain.StreamingProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DiscoverViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun movie(id: Int) = MediaCard(id, MediaType.MOVIE, "Title $id")

    private class FakeDiscovery(
        val batches: Map<Int, DiscoverPage> = emptyMap(),
        val filterResults: List<MediaCard> = emptyList(),
        val failure: Throwable? = null,
        val pages: Map<Int, DiscoverPage> = emptyMap(),
    ) : DiscoveryRepository {
        val batchCalls = mutableListOf<Int>()
        override suspend fun refresh() = Unit
        override suspend fun page(railId: String, page: Int) = pages[page] ?: DiscoverPage(emptyList(), false)
        override suspend fun discover(filters: DiscoverMovieFilters): List<MediaCard> {
            failure?.let { throw it }
            return filterResults
        }
        override suspend fun discoverBatch(filters: DiscoverMovieFilters, batch: Int): DiscoverPage {
            failure?.let { throw it }
            batchCalls += batch
            return batches[batch] ?: DiscoverPage(emptyList(), false)
        }
        override suspend fun search(query: String) = emptyList<MediaCard>()
    }

    private class FakeProviders(val providers: List<StreamingProvider>) : WatchProviderRepository {
        override suspend fun getWatchProviders(mediaType: MediaType, region: String?, failOnError: Boolean) = providers
        override suspend fun getAvailableProviders(region: String) = providers
    }

    private fun viewModel(
        discovery: DiscoveryRepository = FakeDiscovery(),
        providers: WatchProviderRepository = FakeProviders(emptyList()),
    ) = DiscoverViewModel(discovery, providers, dispatcher)

    private val filters = DiscoverMovieFilters()

    @Test fun mosaicLoadsFirstBatchThenAppendsWithoutDuplicates() = runTest(dispatcher) {
        val discovery = FakeDiscovery(
            batches = mapOf(
                1 to DiscoverPage(listOf(movie(1), movie(2)), hasMore = true),
                2 to DiscoverPage(listOf(movie(2), movie(3)), hasMore = false),
            ),
        )
        val vm = viewModel(discovery)
        vm.loadMosaic(filters)
        assertEquals(listOf(1, 2), vm.mosaicResults.value!!.second.map(MediaCard::id))
        assertTrue(vm.mosaicCanLoadMore.value)
        vm.loadMoreMosaic()
        assertEquals(listOf(1, 2, 3), vm.mosaicResults.value!!.second.map(MediaCard::id))
        assertFalse(vm.mosaicCanLoadMore.value)
        vm.loadMoreMosaic()
        assertEquals(listOf(1, 2), discovery.batchCalls)
    }

    @Test fun cachedMosaicIsShownWithoutRefetching() = runTest(dispatcher) {
        val discovery = FakeDiscovery(batches = mapOf(1 to DiscoverPage(listOf(movie(1)), hasMore = false)))
        val vm = viewModel(discovery)
        vm.loadMosaic(filters)
        vm.loadMosaic(filters.copy(mediaType = MediaType.TV))
        vm.loadMosaic(filters)
        assertEquals(filters, vm.mosaicResults.value!!.first)
        assertEquals(listOf(1, 1), discovery.batchCalls)
    }

    @Test fun hideResultRemovesTitleFromFilterPageMosaicAndCache() = runTest(dispatcher) {
        val discovery = FakeDiscovery(
            batches = mapOf(1 to DiscoverPage(listOf(movie(1), movie(2)), hasMore = false)),
            filterResults = listOf(movie(1), movie(3)),
        )
        val vm = viewModel(discovery)
        vm.applyDiscoverFilters(filters)
        vm.loadMosaic(filters)
        vm.hideResult(movie(1))
        assertEquals(listOf(3), vm.discoverFilterResults.value.map(MediaCard::id))
        assertEquals(listOf(2), vm.mosaicResults.value!!.second.map(MediaCard::id))
        vm.loadMosaic(filters)
        assertEquals(listOf(2), vm.mosaicResults.value!!.second.map(MediaCard::id))
    }

    @Test fun filterFailureGoesToErrorsAndStopsLoading() = runTest(dispatcher) {
        val vm = viewModel(FakeDiscovery(failure = IllegalStateException("TMDB is down")))
        val errors = mutableListOf<String>()
        val job = launch { vm.errors.collect(errors::add) }
        vm.applyDiscoverFilters(filters)
        vm.loadMosaic(filters)
        assertEquals(listOf("TMDB is down", "TMDB is down"), errors)
        assertFalse(vm.discoverFiltersLoading.value)
        job.cancel()
    }

    @Test fun providersAreNarrowedToPreferredServices() = runTest(dispatcher) {
        val all = listOf(StreamingProvider(8, "Netflix"), StreamingProvider(337, "Disney Plus"))
        val vm = viewModel(providers = FakeProviders(all))
        vm.loadStreamingProviders(MediaType.MOVIE, setOf("Netflix"))
        assertEquals(listOf("Netflix"), vm.streamingProviders.value.map(StreamingProvider::name))
        vm.loadStreamingProviders(MediaType.MOVIE, emptySet())
        assertEquals(all, vm.streamingProviders.value)
    }

    @Test fun seeAllPagesStartFromTheRailAndFollowStatusWrites() = runTest(dispatcher) {
        val discovery = FakeDiscovery(
            pages = mapOf(
                1 to DiscoverPage(listOf(movie(1), movie(2)), hasMore = true),
                2 to DiscoverPage(listOf(movie(3)), hasMore = false),
            ),
        )
        val vm = viewModel(discovery)
        val state = AppUiState(rails = mapOf(RailIds.POPULAR_MOVIES to listOf(movie(1))))
        val key = discoverBrowseKey(RailIds.POPULAR_MOVIES, state)

        vm.loadDiscoverMore(RailIds.POPULAR_MOVIES, state)
        val browse = vm.discoverBrowse.value.getValue(key)
        assertEquals(listOf(1, 2, 3), browse.items.map(MediaCard::id))
        assertFalse(browse.hasMore)
        assertFalse(browse.loading)

        vm.applyStatus(movie(2), LibraryStatus.COMPLETED)
        val updated = vm.discoverBrowse.value.getValue(key).items.single { it.id == 2 }
        assertEquals(LibraryStatus.COMPLETED, updated.status)
        assertTrue(updated.watched)

        vm.resetBrowse()
        assertTrue(vm.discoverBrowse.value.isEmpty())
    }
}
