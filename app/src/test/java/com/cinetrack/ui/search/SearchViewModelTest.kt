package com.cinetrack.ui.search

import com.cinetrack.data.discovery.DiscoveryRepository
import com.cinetrack.data.people.PeopleRepository
import com.cinetrack.domain.DiscoverMovieFilters
import com.cinetrack.domain.DiscoverPage
import com.cinetrack.domain.MediaCard
import com.cinetrack.domain.MediaType
import com.cinetrack.domain.PersonCard
import com.cinetrack.domain.TimelineCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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
class SearchViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private class FakeDiscovery(val results: Map<String, List<MediaCard>>) : DiscoveryRepository {
        val queries = mutableListOf<String>()
        override suspend fun refresh() = Unit
        override suspend fun page(railId: String, page: Int) = DiscoverPage(emptyList(), false)
        override suspend fun discover(filters: DiscoverMovieFilters) = emptyList<MediaCard>()
        override suspend fun discoverBatch(filters: DiscoverMovieFilters, batch: Int) = DiscoverPage(emptyList(), false)
        override suspend fun search(query: String): List<MediaCard> {
            queries += query
            return results[query].orEmpty()
        }
    }

    private class FakePeople(val results: Map<String, List<PersonCard>>) : PeopleRepository {
        override suspend fun search(query: String) = results[query].orEmpty()
        override suspend fun getPerson(person: PersonCard) = person
        override suspend fun viewingPeople(history: List<TimelineCard>) = emptyList<PersonCard>() to emptyList<PersonCard>()
    }

    private class FakeHistory : SearchHistoryStore {
        override val searchHistory = MutableStateFlow<List<String>>(emptyList())
        override suspend fun addSearchHistory(query: String) { searchHistory.value = listOf(query) + searchHistory.value }
        override suspend fun clearSearchHistory() { searchHistory.value = emptyList() }
        override suspend fun removeSearchHistory(query: String) { searchHistory.value = searchHistory.value - query }
    }

    private val dune = MediaCard(1, MediaType.MOVIE, "Dune")
    private val dark = MediaCard(2, MediaType.TV, "Dark")

    private fun viewModel(
        discovery: DiscoveryRepository = FakeDiscovery(mapOf("dune" to listOf(dune, dark))),
        people: PeopleRepository = FakePeople(mapOf("villeneuve" to listOf(PersonCard(7, "Denis Villeneuve", "Director")))),
        history: SearchHistoryStore = FakeHistory(),
    ) = SearchViewModel(discovery, people, history, dispatcher)

    @Test fun searchWaitsForTypingPauseThenPublishes() = runTest(dispatcher) {
        val vm = viewModel()
        vm.search("  dune ")
        assertTrue(vm.searchLoading.value)
        assertEquals(emptyList<MediaCard>(), vm.searchResults.value)
        advanceTimeBy(301)
        assertEquals(listOf(dune, dark), vm.searchResults.value)
        assertFalse(vm.searchLoading.value)
    }

    @Test fun onlyTheLastQueryInsideTheDebounceIsSent() = runTest(dispatcher) {
        val discovery = FakeDiscovery(mapOf("dune" to listOf(dune)))
        val vm = viewModel(discovery = discovery)
        vm.search("du")
        advanceTimeBy(100)
        vm.search("dune")
        advanceTimeBy(301)
        assertEquals(listOf("dune"), discovery.queries)
        assertEquals(listOf(dune), vm.searchResults.value)
    }

    @Test fun blankQueryClearsAtOnce() = runTest(dispatcher) {
        val vm = viewModel()
        vm.search("dune")
        advanceTimeBy(301)
        vm.search("   ")
        assertEquals(emptyList<MediaCard>(), vm.searchResults.value)
        assertFalse(vm.searchLoading.value)
    }

    @Test fun peopleSearchIsSeparateFromTitles() = runTest(dispatcher) {
        val vm = viewModel()
        vm.searchPeople("villeneuve")
        assertTrue(vm.personSearchLoading.value)
        advanceTimeBy(301)
        assertEquals("Denis Villeneuve", vm.personSearchResults.value.single().name)
        assertEquals(emptyList<MediaCard>(), vm.searchResults.value)
    }

    @Test fun hideResultDropsTheTitle() = runTest(dispatcher) {
        val vm = viewModel()
        vm.search("dune")
        advanceTimeBy(301)
        vm.hideResult(dune)
        assertEquals(listOf(dark), vm.searchResults.value)
    }

    @Test fun clearSearchResetsBothSearches() = runTest(dispatcher) {
        val vm = viewModel()
        vm.search("dune")
        vm.searchPeople("villeneuve")
        advanceTimeBy(301)
        vm.clearSearch()
        assertEquals(emptyList<MediaCard>(), vm.searchResults.value)
        assertEquals(emptyList<PersonCard>(), vm.personSearchResults.value)
        assertFalse(vm.searchLoading.value || vm.personSearchLoading.value)
    }

    @Test fun historyIsTrimmedAndBlankQueriesAreIgnored() = runTest(dispatcher) {
        val history = FakeHistory()
        val vm = viewModel(history = history)
        vm.rememberSearchQuery("  dune  ")
        vm.rememberSearchQuery("   ")
        vm.rememberSearchQuery("dark")
        assertEquals(listOf("dark", "dune"), vm.searchHistory.value)
        vm.removeSearchHistory("dark")
        assertEquals(listOf("dune"), vm.searchHistory.value)
        vm.clearSearchHistory()
        assertEquals(emptyList<String>(), vm.searchHistory.value)
    }
}
