---
title: "P3 state-path perf (0.99.75): what was changed and how"
author: Joke9854
date: 2026-10-23
branch: beta-testing
cost: { minutes: 30, tokens: 212801 }
touches:
  - docs/ROADMAP.md
  - app/src/androidTest/java/com/cinetrack/ui/screens/ProgressLiquidGlassUiTest.kt
  - app/src/main/java/com/cinetrack/domain/DiscoverBrowse.kt
  - app/src/main/java/com/cinetrack/domain/Models.kt
  - app/src/main/java/com/cinetrack/ui/CineTrackApp.kt
  - app/src/main/java/com/cinetrack/ui/discover/DiscoverUiState.kt
  - app/src/main/java/com/cinetrack/ui/discover/DiscoverViewModel.kt
  - app/src/main/java/com/cinetrack/ui/library/LibraryUiState.kt
  - app/src/main/java/com/cinetrack/ui/progress/ProgressUiState.kt
  - app/src/main/java/com/cinetrack/ui/screens/DiscoverScreens.kt
---

## Decided
- AppStateRepository.loadCachedState reads DataStore once via AppPreferences.appStatePreferences (AppStatePreferences: notification/content/appearance settings + the rest); notificationSettings/contentSettings/appearanceSettings, floppySettingsNow and syncReportNow share private Preferences.x() readers, so defaults can't drift. The VM's four extra reads (Simkl reconnect/first sync, Floppy username, hide adult) moved into it.
- AppSnapshotDao.appStateSnapshot(previous, changedTables): media is only rows referenced by media_rails/playback/watch_history (UNION join; row-value IN needs SQLite 3.15, minSdk 25 has 3.9). With a changed-table set only those tables are re-read; media is re-read when media, media_rails, playback or watch_history changed; episodes (not observed) always. snapshot() is unchanged for TrackingSyncHost/ProgressCache.
- Only the CineTrackViewModel invalidation observer passes changed tables. It unions every emission into a set and removes a set only after its load returns, so collectLatest cancels or the sync/refresh skip never lose a table. AppStateRepository serializes loads with a Mutex and keeps lastSnapshot; every other caller does a full read.
- SimklStatusMapping (data/sync/simkl) is the one Simkl list <-> LibraryStatus mapping (plus String.fromSimklStatus/toSimklStatus/fromCineTrackStatus). TrackingSyncHost's Simkl item mappers were dead copies of SimklSyncEngine's and were deleted.
- ApiServices is a class with lazy services (public constructor kept for tests; NetworkFactory uses ApiServices.lazily); clients, logger and TMDB cache are built on first use. FloppyOAuthClient takes Lazy<OkHttpClient>.
- The 510-min VM sync loop runs in ProcessLifecycleOwner repeatOnLifecycle(STARTED).
- R8: only @Serializable classes in data.remote are kept; Retrofit/kotlinx bundled rules cover services (Floppy already relied on them).
- Q1 remainder: DiscoverUiState (Discover + see-all), ProgressUiState (+ Statistics), LibraryUiState, SettingsUiState are built in CineTrackApp; SettingsDetailScreen takes TrackingSyncControls + HiddenContentControls instead of CineTrackViewModel.
## Tried and ruled out
- Tracking dirty tables with a repository-level InvalidationTracker observer: Room notifies asynchronously, so a read right after a write could reuse a stale table. Only Room's own emission set is trusted.
- Moving SimklSyncHost.mergeSimklItems/resolveSimklItems: interface methods the engine calls back into; left in TrackingSyncHost.
## Watch out
- Screens that still take AppUiState: Detail, EpisodeDetail and the settings detail pages.
- A slice field added to a screen must also be added to its slice; there is no fallback to AppUiState.
