package com.cinetrack

import android.app.Application
import com.cinetrack.data.local.AppDatabase
import com.cinetrack.data.remote.NetworkFactory
import com.cinetrack.data.repository.AppPreferences
import com.cinetrack.data.repository.CineTrackRepository
import com.cinetrack.data.sync.SimklWorkScheduler
import com.cinetrack.data.sync.ReleaseNotifier
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicReference

class CineTrackApplication : Application(), ImageLoaderFactory {
    lateinit var container: AppContainer
        private set
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        ReleaseNotifier.createChannel(this)
        applicationScope.launch {
            SimklWorkScheduler.update(
                this@CineTrackApplication,
                enabled = container.preferences.backgroundSync.first(),
                wifiOnly = container.preferences.wifiOnly.first(),
            )
        }
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .memoryCache {
            MemoryCache.Builder(this)
                .maxSizePercent(.20)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("cinetrack_image_cache"))
                .maxSizePercent(.03)
                .build()
        }
        .crossfade(true)
        .build()
}

class AppContainer(application: Application) {
    val preferences = AppPreferences(application)
    private val startupPreferences = runBlocking {
        coroutineScope {
            val token = async(Dispatchers.IO) { preferences.tokenNow() }
            val tmdbApiKey = async(Dispatchers.IO) { preferences.tmdbApiKeyNow() }
            val mdbListApiKey = async(Dispatchers.IO) { preferences.mdbListApiKeyNow() }
            val metadataLanguage = async(Dispatchers.IO) { preferences.metadataLanguage.first() }
            val metadataRegion = async(Dispatchers.IO) { preferences.metadataRegion.first() }
            val metadataTimezone = async(Dispatchers.IO) { preferences.metadataTimezone.first() }
            StartupPreferences(
                token = token.await(),
                tmdbApiKey = tmdbApiKey.await(),
                mdbListApiKey = mdbListApiKey.await(),
                metadataLanguage = metadataLanguage.await(),
                metadataRegion = metadataRegion.await(),
                metadataTimezone = metadataTimezone.await(),
            )
        }
    }
    private val token = AtomicReference(startupPreferences.token)
    private val tmdbApiKey = AtomicReference(startupPreferences.tmdbApiKey)
    private val mdbListApiKey = AtomicReference(startupPreferences.mdbListApiKey)
    private val metadataLanguage = AtomicReference(startupPreferences.metadataLanguage)
    private val metadataRegion = AtomicReference(startupPreferences.metadataRegion)
    private val metadataTimezone = AtomicReference(startupPreferences.metadataTimezone)
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

private data class StartupPreferences(
    val token: String?,
    val tmdbApiKey: String,
    val mdbListApiKey: String,
    val metadataLanguage: String,
    val metadataRegion: String,
    val metadataTimezone: String,
)
