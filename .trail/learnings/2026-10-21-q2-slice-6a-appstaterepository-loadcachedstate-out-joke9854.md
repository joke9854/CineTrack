---
title: "Q2 slice 6a: AppStateRepository (loadCachedState) out of the facade"
author: Joke9854
date: 2026-10-21
branch: beta-testing
cost: { minutes: 29, tokens: 52752 }
touches:
  - app/src/main/java/com/cinetrack/CineTrackApplication.kt
  - app/src/main/java/com/cinetrack/UpNextWidgetProvider.kt
  - app/src/main/java/com/cinetrack/data/repository/CineTrackRepository.kt
  - app/src/main/java/com/cinetrack/data/sync/SimklSyncWorker.kt
  - app/src/main/java/com/cinetrack/ui/CineTrackApp.kt
  - app/src/main/java/com/cinetrack/ui/CineTrackViewModel.kt
  - app/src/test/java/com/cinetrack/data/repository/BackupRoundTripTest.kt
  - app/src/main/java/com/cinetrack/data/repository/AppStateRepository.kt
  - app/src/main/java/com/cinetrack/data/repository/CatalogRepository.kt
  - app/src/main/java/com/cinetrack/data/repository/LegacyMediaDataSource.kt
---

## Decided
- Q2 final split agreed with the user (2026-10-21): 6a AppStateRepository, 6b AccountRepository (Floppy/Simkl sign-in, disconnect, usernames), 6c SyncConflictResolver (optional; keep-local/use-remote), 6d rename the rest to TrackingSyncHost and delete the facade. Risk rises in that order; 6c/6d wiring is decided in the main session only.
- data/repository/AppStateRepository owns loadCachedState, buildTrackingProviderStates, toFloppyUiState, scheduleTime, playbackTimestamp and simklConnectedNow, moved verbatim. Deps: database, preferences, currentToken, trackingProviderRegistry, tmdbApiKey, mdbListApiKey (loadCachedState reads both keys).
- AppContainer.appState is built before the facade; ProgressCacheRepository's loadCachedState and BackupRepository's loadState use it, so neither depends on `repository` any more. CineTrackViewModel takes appState as a ctor param (factory in CineTrackApp.kt). The facade has appStateProvider with a lazy fallback, which still uses its mdbListApiKey param.
- The facade's observeLocalChanges was dead (the ViewModel already uses LibraryRepository.observeLocalChanges) and was deleted.

## Watch out
- Every install printed "adb server version (32) doesn't match this client (41)" and the S23 dropped off adb twice between builds: two adb binaries are on the machine. Check `adb devices` before installPreview.
