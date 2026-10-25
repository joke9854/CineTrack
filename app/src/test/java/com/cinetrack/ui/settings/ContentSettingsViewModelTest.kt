package com.cinetrack.ui.settings

import com.cinetrack.data.remote.TmdbContentFilter
import com.cinetrack.domain.StreamingProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ContentSettingsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() {
        Dispatchers.resetMain()
        TmdbContentFilter.hideAdult = true
    }

    private class FakeStore : ContentSettingsStore {
        val settings = MutableStateFlow(ContentSettings())
        override val contentSettings = settings
        var providerGate: CompletableDeferred<Unit>? = null
        var airDateGate: CompletableDeferred<Unit>? = null
        var providers: List<StreamingProvider> = emptyList()
        override suspend fun setRatingSource(key: String, enabled: Boolean) {
            settings.value = settings.value.copy(ratingSources = if (enabled) settings.value.ratingSources + key else settings.value.ratingSources - key)
        }
        override suspend fun setPreferredProviders(values: Set<String>) {
            providerGate?.await()
            settings.value = settings.value.copy(preferredProviders = values)
        }
        override suspend fun setProviderRegion(value: String) { settings.value = settings.value.copy(providerRegion = value) }
        override suspend fun setVisibleProviderTypes(values: Set<String>) { settings.value = settings.value.copy(visibleProviderTypes = values) }
        override suspend fun setHideAdultTitles(enabled: Boolean) { settings.value = settings.value.copy(hideAdultTitles = enabled) }
        override suspend fun setContentRegions(regions: Set<String>) { settings.value = settings.value.copy(contentRegions = regions) }
        override suspend fun setMetadataLanguage(value: String) { settings.value = settings.value.copy(metadataLanguage = value) }
        override suspend fun setMetadataRegion(value: String) { settings.value = settings.value.copy(metadataRegion = value) }
        override suspend fun setMetadataTimezone(value: String) { settings.value = settings.value.copy(metadataTimezone = value) }
        override suspend fun setAirDateSource(value: String) {
            airDateGate?.await()
            settings.value = settings.value.copy(airDateSource = value)
        }
        override suspend fun availableProviders(region: String) = providers
        var tmdbResult: Result<Unit> = Result.success(Unit)
        override suspend fun verifyAndSetTmdbApiKey(value: String) = tmdbResult
        override suspend fun verifyAndSetMdbListApiKey(value: String) = Result.failure<Unit>(IllegalStateException("bad key"))
        override suspend fun verifyAndSetFanartApiKey(value: String) = Result.success(Unit)
    }

    private fun collectChanges(model: ContentSettingsViewModel, into: MutableList<ContentChange>) =
        kotlinx.coroutines.CoroutineScope(dispatcher).launch { model.changes.collect { into += it } }

    @Test fun quickProviderTogglesKeepEveryTap() {
        val store = FakeStore()
        val model = ContentSettingsViewModel(store, dispatcher)
        val changes = mutableListOf<ContentChange>()
        val job = collectChanges(model, changes)
        val gate = CompletableDeferred<Unit>()
        store.providerGate = gate
        model.togglePreferredProvider("Netflix")
        // The second tap comes before the first write has landed.
        model.togglePreferredProvider("Prime Video")
        gate.complete(Unit)
        assertEquals(setOf("Netflix", "Prime Video"), model.content.value.preferredProviders)
        model.togglePreferredProvider("Netflix")
        assertEquals(setOf("Prime Video"), model.content.value.preferredProviders)
        assertEquals(List(3) { ContentChange.Providers }, changes)
        job.cancel()
    }

    @Test fun visibleOfferTypesToggle() {
        val store = FakeStore()
        val model = ContentSettingsViewModel(store, dispatcher)
        model.toggleVisibleProviderType("rent")
        model.toggleVisibleProviderType("ads")
        assertEquals(setOf("flatrate", "buy", "free"), model.content.value.visibleProviderTypes)
        model.toggleVisibleProviderType("rent")
        assertEquals(setOf("flatrate", "buy", "free", "rent"), model.content.value.visibleProviderTypes)
    }

    @Test fun ratingSourceUsesTheStoredKey() {
        val store = FakeStore()
        val model = ContentSettingsViewModel(store, dispatcher)
        val changes = mutableListOf<ContentChange>()
        val job = collectChanges(model, changes)
        model.setRatingSource("Rotten Tomatoes", false)
        model.setRatingSource("IMDb", false)
        assertEquals(setOf("tmdb", "metacritic"), model.content.value.ratingSources)
        assertEquals(listOf(ContentChange.Ratings, ContentChange.Ratings), changes)
        job.cancel()
    }

    @Test fun airDateChangeIsAnnouncedOnlyAfterTheWrite() = runTest(dispatcher) {
        val store = FakeStore()
        val model = ContentSettingsViewModel(store, dispatcher)
        val changes = mutableListOf<ContentChange>()
        val job = collectChanges(model, changes)
        val gate = CompletableDeferred<Unit>()
        store.airDateGate = gate
        model.setAirDateSource("trakt")
        yield()
        // The air dates are still being rewritten: a progress refresh now would read the old ones.
        assertTrue(changes.isEmpty())
        gate.complete(Unit)
        assertEquals("trakt", model.content.value.airDateSource)
        assertEquals(listOf(ContentChange.AirDates), changes)
        job.cancel()
    }

    @Test fun metadataChangesAreAnnounced() {
        val store = FakeStore()
        val model = ContentSettingsViewModel(store, dispatcher)
        val changes = mutableListOf<ContentChange>()
        val job = collectChanges(model, changes)
        model.setMetadataLanguage("it")
        model.setMetadataRegion("IT")
        model.setMetadataTimezone("Europe/Rome")
        model.setContentRegions(setOf("IT"))
        assertEquals(
            ContentSettings(metadataLanguage = "it", metadataRegion = "IT", metadataTimezone = "Europe/Rome", contentRegions = setOf("IT")),
            model.content.value,
        )
        assertEquals(listOf(ContentChange.Language, ContentChange.Region, ContentChange.Timezone, ContentChange.ContentRegions), changes)
        job.cancel()
    }

    @Test fun hidingAdultTitlesAppliesAtOnceWithoutARefresh() {
        val store = FakeStore()
        val model = ContentSettingsViewModel(store, dispatcher)
        val changes = mutableListOf<ContentChange>()
        val job = collectChanges(model, changes)
        model.setHideAdultTitles(false)
        assertFalse(TmdbContentFilter.hideAdult)
        assertFalse(model.content.value.hideAdultTitles)
        assertTrue(changes.isEmpty())
        job.cancel()
    }

    @Test fun onlyAWorkingCredentialIsAnnounced() {
        val store = FakeStore()
        val model = ContentSettingsViewModel(store, dispatcher)
        val changes = mutableListOf<ContentChange>()
        val job = collectChanges(model, changes)
        val results = mutableListOf<Result<Unit>>()
        model.verifyAndSetTmdbApiKey("good") { results += it }
        model.verifyAndSetMdbListApiKey("bad") { results += it }
        model.verifyAndSetFanartApiKey("id") { results += it }
        assertEquals(listOf(true, false, true), results.map { it.isSuccess })
        assertEquals("bad key", results[1].exceptionOrNull()?.message)
        assertEquals(listOf(ContentChange.TmdbKey, ContentChange.FanartKey), changes)
        job.cancel()
    }

    @Test fun providerListFailureIsReported() {
        val store = object : ContentSettingsStore by FakeStore() {
            override suspend fun availableProviders(region: String): List<StreamingProvider> = error("offline")
        }
        val model = ContentSettingsViewModel(store, dispatcher)
        model.loadStreamingProviders("IT")
        assertTrue(model.providersError.value)
        assertFalse(model.providersLoading.value)
        assertTrue(model.providers.value.isEmpty())
    }
}
