package com.cinetrack

import android.app.Application
import com.cinetrack.data.local.AppDatabase
import com.cinetrack.data.remote.NetworkFactory
import com.cinetrack.data.repository.AppPreferences
import com.cinetrack.data.repository.CineTrackRepository
import com.cinetrack.data.sync.SimklWorkScheduler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicReference

class CineTrackApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        runBlocking {
            SimklWorkScheduler.update(
                this@CineTrackApplication,
                enabled = container.preferences.backgroundSync.first(),
                wifiOnly = container.preferences.wifiOnly.first(),
            )
        }
    }
}

class AppContainer(application: Application) {
    val preferences = AppPreferences(application)
    private val token = AtomicReference<String?>(runBlocking { preferences.tokenNow() })
    private val tmdbApiKey = AtomicReference(runBlocking { preferences.tmdbApiKeyNow() })
    private val mdbListApiKey = AtomicReference(runBlocking { preferences.mdbListApiKeyNow() })
    private val metadataLanguage = AtomicReference(runBlocking { preferences.metadataLanguage.first() })
    private val metadataRegion = AtomicReference(runBlocking { preferences.metadataRegion.first() })
    private val metadataTimezone = AtomicReference(runBlocking { preferences.metadataTimezone.first() })
    private val database = AppDatabase.create(application)
    private val services = NetworkFactory.create(
        token = token::get,
        tmdbApiKey = tmdbApiKey::get,
        metadataLanguage = metadataLanguage::get,
        metadataRegion = metadataRegion::get,
        metadataTimezone = metadataTimezone::get,
    )
    val repository = CineTrackRepository(
        database = database,
        services = services,
        preferences = preferences,
        onTokenChanged = token::set,
        currentToken = token::get,
        onTmdbApiKeyChanged = tmdbApiKey::set,
        tmdbApiKey = tmdbApiKey::get,
        onMdbListApiKeyChanged = mdbListApiKey::set,
        mdbListApiKey = mdbListApiKey::get,
        onMetadataLanguageChanged = metadataLanguage::set,
        onMetadataRegionChanged = metadataRegion::set,
        onMetadataTimezoneChanged = metadataTimezone::set,
    )
}
