---
title: "Q1 last Settings slice: SyncSettingsViewModel for Sync, Sync operations, Simkl, Floppy"
author: Joke9854
date: 2026-10-20
branch: beta-testing
cost: { minutes: 29, tokens: 123132 }
touches:
  - app/src/main/java/com/cinetrack/ui/CineTrackApp.kt
  - app/src/main/java/com/cinetrack/ui/CineTrackViewModel.kt
  - app/src/main/java/com/cinetrack/ui/screens/LibrarySettingsScreens.kt
  - app/src/main/java/com/cinetrack/ui/screens/SyncOperationsScreen.kt
  - app/src/main/java/com/cinetrack/ui/screens/TrackingSettingsScreen.kt
  - app/src/main/java/com/cinetrack/data/repository/RepositorySyncSettings.kt
  - app/src/main/java/com/cinetrack/ui/settings/SyncSettingsViewModel.kt
  - app/src/test/java/com/cinetrack/ui/settings/SyncSettingsViewModelTest.kt
---

## Decided
- ui/settings/SyncSettingsViewModel (store SyncSettingsStore -> data/repository/RepositorySyncSettings) owns the Floppy connect/sign-in flow and its mutex, the address-move question, completeFloppySignIn (CineTrackApp forwards the Floppy auth callback), sync operations (list, retry, conflicts), unplaced episodes, Simkl begin-login/disconnect/username, Floppy username and the background-sync/Wi-Fi switches.
- Stay on CineTrackViewModel: sync(), performTrackingSync and syncMutex, syncProgress, swapMainTracker/mainTrackerSwap (they run performTrackingSync), floppyBootstrapProgress (WorkManager observer keyed on floppyConnectionId with the provider-instance match), completeSimklLogin and chooseSimklFirstSync. Pages get these through a TrackingSyncControls holder built in SettingsDetailScreen, so no sync page takes CineTrackViewModel any more.
- Events: SyncSettingsChange via a buffered Channel (not a SharedFlow) so a connect finishing during activity recreation is not dropped; CineTrackViewModel.onSyncSettingsChanged keeps the old state updates (ProviderChanged -> readCachedState().copy(error = null); ConflictResolved keeps the live sync progress).
- The Sync operations page refreshes on open and whenever syncProgress.running or the bootstrap stage changes, replacing the main ViewModel's refresh after every sync.

## Tried and ruled out
- Moving sync() and the MAIN swap: they share syncMutex with cold-start, resume and first-sync runs; moving them would change sync orchestration.

## Watch out
- Never run connectedDebugAndroidTest while the S23 is attached (it uninstalls com.cinetrack.debug). Run emulator tests with adb -s emulator-5554 install -t of app-debug and app-debug-androidTest, then am instrument -w -e class ... com.cinetrack.debug.test/androidx.test.runner.AndroidJUnitRunner.
- mergeProjectDexDebug failing with (Accesso negato) on dexBuilderDebug/out is a stale intermediate: delete app/build/intermediates/project_dex_archive/debug.
- Restarting the adb server (version mismatch) closed the running emulator.
