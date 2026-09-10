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
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.atomic.AtomicReference

class CineTrackApplication : Application(), ImageLoaderFactory {
    lateinit var container: AppContainer
        private set
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this, applicationScope)
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
                .maxSizePercent(.15)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("cinetrack_image_cache"))
                .maxSizeBytes(150L * 1024 * 1024)
                .build()
        }
        .crossfade(true)
        .build()
}

class AppContainer(application: Application, applicationScope: CoroutineScope) {
    val preferences = AppPreferences(application)
    private val startupReady = CompletableDeferred<Unit>()
    private val token = AtomicReference<String?>(null)
    private val tmdbApiKey = AtomicReference(BuildConfig.TMDB_API_TOKEN)
    private val mdbListApiKey = AtomicReference(BuildConfig.MDBLIST_API_KEY)
    private val metadataLanguage = AtomicReference("system")
    private val metadataRegion = AtomicReference("system")
    private val metadataTimezone = AtomicReference("system")
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
        awaitStartupReady = startupReady::await,
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

    init {
        applicationScope.launch(Dispatchers.IO) {
            runCatching {
                coroutineScope {
                    val token = async { preferences.tokenNow() }
                    val tmdbApiKey = async { preferences.tmdbApiKeyNow() }
                    val mdbListApiKey = async { preferences.mdbListApiKeyNow() }
                    val metadataLanguage = async { preferences.metadataLanguage.first() }
                    val metadataRegion = async { preferences.metadataRegion.first() }
                    val metadataTimezone = async { preferences.metadataTimezone.first() }
                    StartupPreferences(
                        token = token.await(),
                        tmdbApiKey = tmdbApiKey.await(),
                        mdbListApiKey = mdbListApiKey.await(),
                        metadataLanguage = metadataLanguage.await(),
                        metadataRegion = metadataRegion.await(),
                        metadataTimezone = metadataTimezone.await(),
                    )
                }
            }.onSuccess { startup ->
                token.set(startup.token)
                tmdbApiKey.set(startup.tmdbApiKey)
                mdbListApiKey.set(startup.mdbListApiKey)
                metadataLanguage.set(startup.metadataLanguage)
                metadataRegion.set(startup.metadataRegion)
                metadataTimezone.set(startup.metadataTimezone)
                startupReady.complete(Unit)
            }.onFailure { startupReady.completeExceptionally(it) }
        }
    }
}

private data class StartupPreferences(
    val token: String?,
    val tmdbApiKey: String,
    val mdbListApiKey: String,
    val metadataLanguage: String,
    val metadataRegion: String,
    val metadataTimezone: String,
)
