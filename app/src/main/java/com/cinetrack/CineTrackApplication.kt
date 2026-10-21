package com.cinetrack

import android.app.Application
import android.content.Context
import com.cinetrack.crash.CrashOutbox
import com.cinetrack.crash.CrashReportSettings
import com.cinetrack.crash.CrashReportUploader
import com.cinetrack.crash.CrashReporting
import com.cinetrack.data.sync.floppy.FloppySession
import org.acra.ACRA
import org.acra.ReportField
import org.acra.data.StringFormat
import org.acra.ktx.initAcra
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
import com.cinetrack.data.repository.BackupRepository
import com.cinetrack.data.repository.CineTrackRepository
import com.cinetrack.data.repository.MetadataSettingsRepository
import com.cinetrack.data.repository.LegacyMediaDataSource
import com.cinetrack.data.repository.MediaMetadataRepository
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
import com.cinetrack.data.sync.simkl.SimklTokenManager
import com.cinetrack.data.sync.simkl.SimklTokenStore
import com.cinetrack.data.sync.simkl.SimklSyncEngine
import com.cinetrack.data.watchprovider.DefaultWatchProviderRepository
import com.cinetrack.data.watchprovider.WatchProviderRepository
import com.cinetrack.data.sync.ReleaseNotifier
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.crossfade
import okio.Path.Companion.toOkioPath
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.atomic.AtomicReference

class CineTrackApplication : Application(), SingletonImageLoader.Factory {
    lateinit var container: AppContainer
        private set
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        initAcra {
            buildConfigClass = BuildConfig::class.java
            reportFormat = StringFormat.JSON
            // Whitelist only: no logcat, preferences, device ids, settings, environment or custom data.
            reportContent = listOf(
                ReportField.REPORT_ID,
                ReportField.APP_VERSION_CODE,
                ReportField.APP_VERSION_NAME,
                ReportField.ANDROID_VERSION,
                ReportField.PHONE_MODEL,
                ReportField.BRAND,
                ReportField.STACK_TRACE,
                ReportField.USER_CRASH_DATE,
                ReportField.THREAD_DETAILS,
            )
        }
    }

    override fun onCreate() {
        super.onCreate()
        // ACRA's sender runs in its own process, which only writes the outbox: skip the app's heavy init there.
        if (ACRA.isACRASenderServiceProcess()) return
        container = AppContainer(this, applicationScope)
        // Keep the TMDB adult filter in step with Settings > Metadata, also for background work.
        applicationScope.launch {
            container.preferences.hideAdultTitles.collect { com.cinetrack.data.remote.TmdbContentFilter.hideAdult = it }
        }
        ReleaseNotifier.createChannel(this)
        applicationScope.launch(Dispatchers.IO) {
            val connected = runCatching { container.preferences.floppySettingsNow() != null }.getOrDefault(false)
            runCatching { CrashReporting.startup(this@CineTrackApplication, connected) }
        }
        applicationScope.launch {
            TrackingWorkScheduler.update(
                this@CineTrackApplication,
                enabled = container.preferences.backgroundSync.first(),
                wifiOnly = container.preferences.wifiOnly.first(),
            )
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(context)
        .memoryCache {
            MemoryCache.Builder()
                .maxSizePercent(context, .15)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("cinetrack_image_cache").toOkioPath())
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
    private val simklTokens: SimklTokenManager = SimklTokenManager(
        store = object : SimklTokenStore {
            override suspend fun accessToken() = preferences.tokenNow()
            override fun refreshToken() = preferences.simklRefreshTokenNow()
            override suspend fun expiresAt() = preferences.simklTokenExpiresAtNow()
            override suspend fun storeRefreshed(accessToken: String, refreshToken: String?, expiresAt: Long?) =
                preferences.storeRefreshedSimklTokens(accessToken, refreshToken, expiresAt)
            override suspend fun dropGrant() = preferences.dropSimklGrant()
        },
        auth = { services.simklAuth },
        current = token,
    )
    private val services: com.cinetrack.data.remote.ApiServices = NetworkFactory.create(
        token = { simklTokens.tokenForRequest() },
        refreshSimklToken = { rejected -> simklTokens.refreshAfterRejection(rejected) },
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
    val crashReportUploader: CrashReportUploader
    /** Metadata settings and API keys; updates the live values the network clients read. */
    val metadataSettings by lazy {
        MetadataSettingsRepository(
            database = database,
            services = services,
            preferences = preferences,
            onTmdbApiKeyChanged = tmdbApiKey::set,
            onMdbListApiKeyChanged = mdbListApiKey::set,
            onMetadataLanguageChanged = metadataLanguage::set,
            onMetadataRegionChanged = metadataRegion::set,
            onMetadataTimezoneChanged = metadataTimezone::set,
            clearLanguageCaches = {
                repository.clearLanguageCaches()
                mediaMetadata.clearLanguageCaches()
            },
        )
    }
    /** Export, restore and the automatic backup after library changes. */
    val backupRepository by lazy {
        BackupRepository(
            database = database,
            preferences = preferences,
            metadataSettings = { metadataSettings },
            loadState = repository::loadCachedState,
            rebuildLibraryRail = repository::rebuildLibraryRailInTransaction,
        )
    }
    /** Shared by CineTrackViewModel (watched marks) and DetailViewModel (loads). */
    val detailEpisodeCache = com.cinetrack.ui.detail.DetailEpisodeCache()
    val repository: CineTrackRepository
    /** Detail-page metadata loads (details, episodes, cast, ratings...). */
    val mediaMetadata: MediaMetadataRepository
    val progressCache: com.cinetrack.data.repository.ProgressCacheRepository
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
        // Simkl's own anime numbering is converted to TMDB numbering through Floppy's translate
        // endpoint; the table is filled lazily and the one-time local repair settles each show.
        val numberingLog: (String) -> Unit = { message -> preferences.appendErrorLog("${java.time.Instant.now()}  $message") }
        val floppyHolder = AtomicReference<FloppyTrackingProvider?>(null)
        var animeRepair: com.cinetrack.data.sync.numbering.AnimeNumberingRepair? = null
        val episodeNumbering = com.cinetrack.data.sync.numbering.EpisodeNumbering(
            dao = database.episodeNumberingDao(),
            transport = object : com.cinetrack.data.sync.numbering.EpisodeTranslateTransport {
                override suspend fun isAvailable() =
                    preferences.floppySettingsNow()?.capabilities?.canTranslateEpisodes == true

                override suspend fun translate(tmdbId: Int, from: String, coords: List<Pair<Int, Int>>) =
                    floppyHolder.get()?.translateEpisodes(tmdbId, from, coords)
            },
            log = numberingLog,
            settled = { showId -> animeRepair?.isSettled(showId) ?: false },
        )
        animeRepair = com.cinetrack.data.sync.numbering.AnimeNumberingRepair(
            episodeNumbering,
            com.cinetrack.data.sync.numbering.RoomAnimeRepairStore(
                database = database,
                preferences = preferences,
                fetchAnime = { services.simklSync.allItems("anime").anime },
                refreshProgress = { request -> repository.refreshProgressCache(request) },
                onInvalidate = { showId -> repository.renumberedShows.tryEmit(showId) },
                logLine = numberingLog,
            ),
        )
        val simkl = SimklTrackingProvider(
            services = services,
            preferences = preferences,
            syncEngine = { simklSyncEngine },
            onRateLimited = { retryAfter -> TrackingWorkScheduler.scheduleRateLimitRetry(application, retryAfter) },
            episodeNumbering = episodeNumbering,
        )
        val floppySyncEngine by lazy {
            com.cinetrack.data.sync.floppy.FloppySyncEngine { repository }
        }
        val floppyOAuth = com.cinetrack.data.sync.floppy.network.FloppyOAuthClient(preferences.floppyOAuthTokenStore)
        val floppyRemote = com.cinetrack.data.sync.floppy.network.FloppyRemoteDataSource(
            com.cinetrack.data.sync.floppy.network.FloppyApiClientFactory(floppyOAuth),
        )
        crashReportUploader = CrashReportUploader(
            outbox = CrashOutbox.forContext(application),
            enabled = { CrashReportSettings.isEnabled(application) },
            session = {
                preferences.floppySettingsNow()?.let { settings ->
                    preferences.floppyApiKeyNow(settings.credentialAlias)?.let { apiKey ->
                        FloppySession(
                            instanceId = settings.connectionId,
                            baseUrl = settings.baseUrl,
                            accountIdentity = settings.accountIdentity,
                            credentialAlias = settings.credentialAlias,
                            apiKey = apiKey,
                            capabilities = settings.capabilities,
                            serverVersion = settings.serverVersion,
                            allowInsecureLocalHttp = settings.allowInsecureLocalHttp,
                        )
                    }
                }
            },
            // Connections saved before the capability existed learn it from the public /info.
            hasCapability = { session ->
                session.capabilities.canCrashReports ||
                    floppyRemote.inspect(session.baseUrl, session.allowInsecureLocalHttp).apiExtensions?.cinetrackCrashReportsV1 == true
            },
            remote = floppyRemote,
        )
        // Where Floppy stored a locally submitted episode under another number is a translation too.
        floppyRemote.storedCoordinateSink = { showId, season, episode, storedSeason, storedEpisode ->
            episodeNumbering.recordStored(showId, season, episode, storedSeason, storedEpisode)
        }
        val floppy = FloppyTrackingProvider(
            preferences = preferences,
            remote = floppyRemote,
            syncPass = { provider, operations, onProgress -> floppySyncEngine.sync(provider, operations, onProgress) },
            onDisconnect = { trackingConfigurationService.removeProvider(com.cinetrack.data.sync.TrackingProviderId.FLOPPY) },
        )
        floppyHolder.set(floppy)
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
            deliveryLog = { message -> preferences.appendErrorLog("${java.time.Instant.now()}  $message") },
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
            { backupRepository.scheduleAutomaticBackup() },
            trackingProviderRegistry,
            trackingRoutingMutex,
        )
        val releaseSchedule = DefaultReleaseScheduleRepository(database, services, traktClientId = { preferences.traktClientIdNow() }, airDateSource = { preferences.airDateSource.first() })
        mediaMetadata = MediaMetadataRepository(
            database = database,
            services = services,
            preferences = preferences,
            releaseSchedule = releaseSchedule,
            tmdbApiKey = tmdbApiKey::get,
            mdbListApiKey = mdbListApiKey::get,
            characterImages = { type, tmdbId ->
                floppy.characterImages(if (type == com.cinetrack.domain.MediaType.MOVIE) "movie" else "tv", tmdbId)
                    ?.map { com.cinetrack.domain.CharacterImage(it.name, it.personName, it.characterImage) }
            },
        )
        progressCache = com.cinetrack.data.repository.ProgressCacheRepository(
            database = database,
            preferences = preferences,
            tmdbApiKey = tmdbApiKey::get,
            loadEpisodes = mediaMetadata::loadEpisodes,
            services = services,
            releaseScheduleRepository = releaseSchedule,
            mediaMetadata = mediaMetadata,
            // Runs only long after `repository` is assigned below (on a refresh pass), never during construction.
            loadCachedState = { repository.loadCachedState() },
        )
        repository = CineTrackRepository(
            database = database,
            services = services,
            preferences = preferences,
            awaitStartupReady = startupReady::await,
            onTokenChanged = { value ->
                if (value == null) simklTokens.onSignedOut()
                else applicationScope.launch(Dispatchers.IO) { simklTokens.onSignedIn() }
                token.set(value)
            },
            currentToken = token::get,
            tmdbApiKey = tmdbApiKey::get,
            mdbListApiKey = mdbListApiKey::get,
            syncOperationRepository = syncOperationRepository,
            durableOperationWriter = durableOperationWriter,
            syncCoordinator = syncCoordinator,
            releaseScheduleRepository = releaseSchedule,
            libraryRepository = localLibrary,
            progressCacheProvider = progressCache,
            syncReconciler = syncReconciler,
            trackingProviderRegistry = trackingProviderRegistry,
            trackingRoutingMutex = trackingRoutingMutex,
            floppySecondaryService = floppySecondaryService,
            trackingConfigurationService = trackingConfigurationService,
            floppyOAuth = floppyOAuth,
            floppyRetryBootstrap = {
                preferences.floppySettingsNow()?.connectionId?.let { instance ->
                    FloppyBootstrapWorkScheduler.retryNow(application, instance, preferences.wifiOnly.first())
                }
            },
            floppyUnplacedEpisodes = {
                if (::floppyBootstrapCoordinator.isInitialized) floppyBootstrapCoordinator.unplacedEpisodes() else emptyList()
            },
            episodeNumbering = episodeNumbering,
            animeNumberingRepair = animeRepair,
            mediaMetadata = mediaMetadata,
        )
        libraryRepository = localLibrary
        mediaRepository = DefaultMediaRepository(LegacyMediaDataSource(repository, mediaMetadata))
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
                fun log(message: String) = preferences.appendErrorLog("${java.time.Instant.now()}  $message")
                val capabilities = preferences.floppySettingsNow()?.capabilities
                if (capabilities?.canBootstrapV2 == true && capabilities.canEnsureEpisodeEvents) {
                    // Modern Floppy answers every movie, show and episode with
                    // its exact outcome, and only those outcomes acknowledge an
                    // operation, so they are the verification. Re-reading the
                    // whole remote history would be the global scan the modern
                    // path must never do (it timed out on large libraries).
                    log("Floppy bootstrap verified by per-item outcomes (no history scan)")
                    true
                } else {
                    runCatching {
                        FloppyBootstrapVerifier().failures(expected, floppy.verificationProjection())
                    }.fold(
                        onSuccess = { failures ->
                            if (failures.isEmpty()) {
                                log("Floppy bootstrap verification passed")
                            } else {
                                log("Floppy bootstrap verification: ${failures.size} item(s) missing on Floppy, e.g. ${failures.take(5).joinToString()}")
                            }
                            failures.isEmpty()
                        },
                        onFailure = { error ->
                            log("Floppy bootstrap verification could not read Floppy: ${error.message?.take(160) ?: error::class.simpleName}")
                            false
                        },
                    )
                }
            },
        )

        val floppyLibraryCheck = com.cinetrack.data.sync.floppy.FloppyLibraryCheck(
            isSecondary = { settingsRepository.trackingConfigurationNow().secondaryProvider == TrackingProviderId.FLOPPY },
            provider = floppy,
            coordinator = floppyBootstrapCoordinator,
            lastCheckedAt = { preferences.floppyLibraryCheckedAtNow() },
            setLastCheckedAt = { preferences.setFloppyLibraryCheckedAt(it) },
            lastResent = { preferences.floppyLibraryResentNow() },
            setLastResent = { preferences.setFloppyLibraryResent(it) },
            quiesced = { read -> syncCoordinator.withProviderIoQuiesced { read() } },
            startWorker = { instance -> FloppyBootstrapWorkScheduler.enqueue(application, instance, preferences.wifiOnly.first()) },
            log = { message -> preferences.appendErrorLog("${java.time.Instant.now()}  $message") },
        )
        val libraryCheckRunning = java.util.concurrent.atomic.AtomicBoolean(false)
        // Off the sync's own path: reading Floppy's library takes a while.
        syncCoordinator.afterSync = {
            if (libraryCheckRunning.compareAndSet(false, true)) {
                applicationScope.launch(Dispatchers.IO) {
                    try {
                        floppyLibraryCheck.runIfDue()
                    } catch (error: Throwable) {
                        if (error is kotlinx.coroutines.CancellationException) throw error
                        preferences.appendErrorLog("${java.time.Instant.now()}  Floppy check failed: ${error.message?.take(160) ?: error::class.simpleName}")
                    } finally {
                        libraryCheckRunning.set(false)
                    }
                }
            }
        }

        applicationScope.launch(Dispatchers.IO) {
            runCatching {
                trackingConfigurationService.repair()
                coroutineScope {
                    val token = async { simklTokens.load() }
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
                        -> preferences.floppySettingsNow()?.connectionId?.let { instance ->
                            FloppyBootstrapWorkScheduler.enqueue(application, instance, preferences.wifiOnly.first())
                        }
                        // Leave a permanent failure visible as NEEDS_ATTENTION;
                        // the settings Retry action explicitly enqueues it.
                        ProviderBootstrapState.FAILED -> Unit
                        // Unmatched episodes get another attempt once Floppy's
                        // episode matching improves (a new retry generation).
                        ProviderBootstrapState.READY ->
                            if (::floppyBootstrapCoordinator.isInitialized && floppyBootstrapCoordinator.hasPendingCounterpartRetry()) {
                                preferences.floppySettingsNow()?.connectionId?.let { instance ->
                                    FloppyBootstrapWorkScheduler.enqueue(application, instance, preferences.wifiOnly.first())
                                }
                            }
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

