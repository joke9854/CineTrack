package com.cinetrack

import android.app.Application
import com.cinetrack.data.local.AppDatabase
import com.cinetrack.data.library.LibraryRepository
import com.cinetrack.data.library.RoomLibraryRepository
import com.cinetrack.data.discovery.DefaultDiscoveryRepository
import com.cinetrack.data.discovery.DiscoveryRepository
import com.cinetrack.data.media.DefaultMediaRepository
import com.cinetrack.data.media.MediaRepository
import com.cinetrack.data.people.DefaultPeopleRepository
import com.cinetrack.data.people.PeopleRepository
import com.cinetrack.data.remote.NetworkFactory
import com.cinetrack.data.repository.AppPreferences
import com.cinetrack.data.repository.CineTrackRepository
import com.cinetrack.data.repository.LegacyMediaDataSource
import com.cinetrack.data.repository.SettingsRepository
import com.cinetrack.data.schedule.DefaultReleaseScheduleRepository
import com.cinetrack.data.sync.DefaultTrackingProviderRegistry
import com.cinetrack.data.sync.DurableSyncOperationWriter
import com.cinetrack.data.sync.DurableTrackingQueue
import com.cinetrack.data.sync.RoomSyncOperationRepository
import com.cinetrack.data.sync.SyncCoordinator
import com.cinetrack.data.sync.TrackingProviderRegistry
import com.cinetrack.data.sync.TrackingConfigurationService
import com.cinetrack.data.sync.TrackingRoutingMutex
import com.cinetrack.data.sync.TrackingProviderId
import com.cinetrack.data.sync.ProviderBootstrapState
import com.cinetrack.data.sync.TrackingWorkScheduler
import com.cinetrack.data.sync.SyncReconciler
import com.cinetrack.data.sync.SecondaryProviderDeliveryObserver
import com.cinetrack.data.sync.floppy.FloppyTrackingProvider
import com.cinetrack.data.sync.floppy.FloppyBootstrapCoordinator
import com.cinetrack.data.sync.floppy.FloppyBootstrapVerifier
import com.cinetrack.data.sync.floppy.FloppySecondaryService
import com.cinetrack.data.sync.floppy.FloppyBootstrapWorkScheduler
import com.cinetrack.data.sync.floppy.FloppyBootstrapWorkManager
import com.cinetrack.data.work.LibraryArtworkRefreshManager
import com.cinetrack.data.sync.simkl.SimklTrackingProvider
import com.cinetrack.data.sync.simkl.SimklSyncEngine
import com.cinetrack.data.watchprovider.DefaultWatchProviderRepository
import com.cinetrack.data.watchprovider.WatchProviderRepository
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
            TrackingWorkScheduler.update(
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
    lateinit var settingsRepository: SettingsRepository
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
    private val syncOperationRepository = RoomSyncOperationRepository(database, preferences)
    val trackingRoutingMutex = TrackingRoutingMutex()
    val trackingConfigurationService = TrackingConfigurationService(preferences, syncOperationRepository, trackingRoutingMutex)
    val trackingProviderRegistry: TrackingProviderRegistry
    val syncCoordinator: SyncCoordinator
    lateinit var floppyBootstrapCoordinator: FloppyBootstrapCoordinator
    lateinit var floppySecondaryService: FloppySecondaryService
    lateinit var libraryArtworkRefreshManager: LibraryArtworkRefreshManager
    lateinit var floppyBootstrapWorkManager: FloppyBootstrapWorkManager
    val repository: CineTrackRepository
    val libraryRepository: LibraryRepository
    val mediaRepository: MediaRepository
    val discoveryRepository: DiscoveryRepository
    val peopleRepository: PeopleRepository
    val watchProviderRepository: WatchProviderRepository

    init {
        settingsRepository = SettingsRepository(preferences, trackingConfigurationService)
        val simklSyncEngine by lazy {
            SimklSyncEngine { repository }
        }
        val simkl = SimklTrackingProvider(
            services = services,
            preferences = preferences,
            syncEngine = { simklSyncEngine },
        )
        val floppy = FloppyTrackingProvider(
            preferences = preferences,
            onDisconnect = { trackingConfigurationService.removeProvider(com.cinetrack.data.sync.TrackingProviderId.FLOPPY) },
        )
        trackingProviderRegistry = DefaultTrackingProviderRegistry(
            providers = listOf(simkl, floppy),
            settingsRepository = settingsRepository,
        )
        val syncReconciler = SyncReconciler()
        syncCoordinator = SyncCoordinator(
            trackingProviderRegistry,
            syncOperationRepository,
            syncReconciler,
            trackingRoutingMutex,
            SecondaryProviderDeliveryObserver { providerId ->
                if (providerId == com.cinetrack.data.sync.TrackingProviderId.FLOPPY && ::floppyBootstrapCoordinator.isInitialized) {
                    floppyBootstrapCoordinator.markReadyIfComplete()
                }
            },
        )
        floppySecondaryService = FloppySecondaryService(
            provider = floppy,
            preferences = preferences,
            configuration = trackingConfigurationService,
            coordinator = syncCoordinator,
            bootstrap = { floppyBootstrapCoordinator },
            context = application,
            wifiOnly = { preferences.wifiOnly.first() },
        )
        val durableOperationWriter = DurableSyncOperationWriter(
            operationRepository = syncOperationRepository,
            durableQueue = DurableTrackingQueue(trackingProviderRegistry, trackingRoutingMutex),
            routingMutex = trackingRoutingMutex,
        )
        val localLibrary = RoomLibraryRepository(
            database,
            preferences,
            syncCoordinator,
            { repository.scheduleAutomaticBackup() },
            trackingProviderRegistry,
            trackingRoutingMutex,
        )
        repository = CineTrackRepository(
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
            syncOperationRepository = syncOperationRepository,
            durableOperationWriter = durableOperationWriter,
            syncCoordinator = syncCoordinator,
            releaseScheduleRepository = DefaultReleaseScheduleRepository(database, services),
            libraryRepository = localLibrary,
            syncReconciler = syncReconciler,
            trackingProviderRegistry = trackingProviderRegistry,
            trackingRoutingMutex = trackingRoutingMutex,
            floppySecondaryService = floppySecondaryService,
            floppyRetryBootstrap = {
                preferences.floppySettingsNow()?.connectionId?.let { instance ->
                    FloppyBootstrapWorkScheduler.enqueue(application, instance, preferences.wifiOnly.first())
                }
            },
        )
        libraryRepository = localLibrary
        mediaRepository = DefaultMediaRepository(LegacyMediaDataSource(repository))
        discoveryRepository = DefaultDiscoveryRepository(mediaRepository)
        peopleRepository = DefaultPeopleRepository(mediaRepository)
        watchProviderRepository = DefaultWatchProviderRepository(mediaRepository)

        libraryArtworkRefreshManager = LibraryArtworkRefreshManager(application)
        floppyBootstrapWorkManager = FloppyBootstrapWorkManager(application)

        floppyBootstrapCoordinator = FloppyBootstrapCoordinator(
            preferences = preferences,
            operationRepository = syncOperationRepository,
            operationWriter = durableOperationWriter,
            canonicalSnapshot = { repository.canonicalTrackingSnapshot() },
            verifyRemote = { expected ->
                runCatching {
                    FloppyBootstrapVerifier().verify(
                        expected,
                        floppy.verificationProjection(),
                    )
                }.getOrDefault(false)
            },
        )

        applicationScope.launch(Dispatchers.IO) {
            runCatching {
                trackingConfigurationService.repair()
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
        applicationScope.launch(Dispatchers.IO) {
            runCatching {
                startupReady.await()
                if (settingsRepository.trackingConfigurationNow().secondaryProvider == TrackingProviderId.FLOPPY) {
                    when (preferences.providerBootstrapStateNow(TrackingProviderId.FLOPPY)) {
                        ProviderBootstrapState.NOT_STARTED,
                        ProviderBootstrapState.RUNNING,
                        ProviderBootstrapState.FAILED,
                        -> preferences.floppySettingsNow()?.connectionId?.let { instance ->
                            FloppyBootstrapWorkScheduler.enqueue(application, instance, preferences.wifiOnly.first())
                        }
                        ProviderBootstrapState.READY -> Unit
                    }
                }
            }
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

