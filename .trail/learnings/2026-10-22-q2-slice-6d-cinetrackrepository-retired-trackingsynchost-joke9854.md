---
title: "Q2 slice 6d: CineTrackRepository retired, TrackingSyncHost remains"
author: Joke9854
date: 2026-10-22
branch: beta-testing
cost: { minutes: 25, tokens: 626807 }
touches:
  - app/src/main/java/com/cinetrack/CineTrackApplication.kt
  - app/src/main/java/com/cinetrack/UpNextWidgetProvider.kt
  - app/src/main/java/com/cinetrack/data/repository/BackupRepository.kt
  - app/src/main/java/com/cinetrack/data/repository/CatalogRepository.kt
  - app/src/main/java/com/cinetrack/data/repository/MediaMetadataRepository.kt
  - app/src/main/java/com/cinetrack/data/repository/MetadataSettingsRepository.kt
  - app/src/main/java/com/cinetrack/data/repository/ProgressCacheRepository.kt
  - app/src/main/java/com/cinetrack/data/sync/ReleaseNotifier.kt
  - app/src/main/java/com/cinetrack/data/sync/SimklSyncWorker.kt
  - app/src/main/java/com/cinetrack/data/sync/TrackingSyncHost.kt
---

## Decided
- Q2 is done (2026-10-22). The facade's work now lives in data/repository: MetadataSettings, Backup, MediaMetadata, ProgressCache, Catalog, AppState, Account repositories and SyncConflictResolver. What remains is data/sync/TrackingSyncHost: the SimklSyncHost/MainSyncHost the sync engines call back into (push*, enqueueSecondaryMirror, advanceSyncBaseline, mergePulledSnapshot, repairSyncQueue, generations, withMainAuthority, Simkl item mapping), plus awaitStartup, swapMainTracker, isMainTrackingSyncDue, canonicalTrackingSnapshot and renumberedShows. AppContainer.syncHost replaces container.repository.
- Part A rewired the last passthroughs: callers outside the sync engines use container.progressCache (same instance and lock), RepositorySyncSettings calls SyncOperationRepository and SyncCoordinator.retry directly (Explicit Retry unchanged), CineTrackViewModel takes preferences and progressCache. ProgressRefreshRequest and the progress-attention helpers live in ProgressCacheRepository.kt (same package).
- Part B was a git mv + rename; the class body was verified identical with an ordered diff. TrackingSyncHost takes progressCache as a required param; currentToken, mdbListApiKey, syncCoordinator, releaseScheduleRepository, libraryRepository, mediaMetadata, characterImages and the provider fallbacks were unused and removed.

## Watch out
- The committed baseline profiles (app/src/release/generated/baselineProfiles/) still name CineTrackRepository methods; those rules no longer match, so the moved startup code is not precompiled until the profile is regenerated on the S23 (see the baseline-profile learning). Do it before the 0.99.71 release.
- BackupRoundTripTest now builds AppState/MediaMetadata/ProgressCache repositories like AppContainer; its restore path does not clear the episode-title cache (the app does, via the container's clearLanguageCaches callback).
