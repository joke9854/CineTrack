package com.cinetrack.data.repository

import androidx.room.Room
import com.cinetrack.data.local.AppDatabase
import com.cinetrack.data.remote.ApiServices
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy
import java.security.Provider
import java.security.Security

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class MetadataSettingsRepositoryTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val preferences by lazy { AppPreferences(context) }
    private lateinit var database: AppDatabase
    private val calls = mutableListOf<String>()

    @Before fun setUp() {
        if (Security.getProvider("AndroidKeyStore") == null) {
            Security.addProvider(object : Provider("AndroidKeyStore", 1.0, "test") {
                init { put("KeyStore.AndroidKeyStore", BackupRoundTripTest.EmptyKeyStore::class.java.name) }
            })
        }
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    }

    @After fun tearDown() = database.close()

    private inline fun <reified T> offline(): T = Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
        when (method.name) {
            "toString" -> "offline ${T::class.java.simpleName}"
            "hashCode" -> 0
            "equals" -> false
            else -> throw UnsupportedOperationException("No network in this test: ${method.name}")
        }
    } as T

    private fun repository() = MetadataSettingsRepository(
        database = database,
        services = ApiServices(offline(), offline(), offline(), offline(), offline()),
        preferences = preferences,
        onTmdbApiKeyChanged = { calls += "tmdb:$it" },
        onMdbListApiKeyChanged = { calls += "mdblist:$it" },
        onMetadataLanguageChanged = { calls += "language:$it" },
        onMetadataRegionChanged = { calls += "region:$it" },
        onMetadataTimezoneChanged = { calls += "timezone:$it" },
        clearLanguageCaches = { calls += "clear" },
    )

    @Test fun metadataWritesSaveThenUpdateTheLiveValues() = runTest {
        val repository = repository()

        repository.setMetadataLanguage("it-IT")
        repository.setMetadataRegion("IT")
        repository.setMetadataTimezone("Europe/Rome")

        assertEquals("it-IT", preferences.metadataLanguage.first())
        assertEquals("IT", preferences.metadataRegion.first())
        assertEquals("Europe/Rome", preferences.metadataTimezone.first())
        // The language change also drops metadata cached in the previous language.
        assertEquals(listOf("language:it-IT", "clear", "region:IT", "timezone:Europe/Rome"), calls)
    }

    @Test fun aCredentialIsSavedOnlyAfterItsServiceAcceptsIt() = runTest {
        val repository = repository()
        val before = preferences.mdbListApiKeyNow()

        assertTrue(repository.verifyAndSetMdbListApiKey("   ").isFailure)
        // The check request fails (no network here): nothing is saved or applied.
        assertTrue(repository.verifyAndSetMdbListApiKey("new-key").isFailure)
        assertTrue(repository.verifyAndSetFanartApiKey("").isFailure)

        assertEquals(before, preferences.mdbListApiKeyNow())
        assertTrue(calls.isEmpty())
    }
}
