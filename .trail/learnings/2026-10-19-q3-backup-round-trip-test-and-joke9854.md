---
title: Q3 backup round-trip test and history-only restore fix
author: Joke9854
date: 2026-10-19
branch: beta-testing
cost: { minutes: 16, tokens: 79761 }
touches:
  - app/src/main/java/com/cinetrack/data/repository/AppPreferences.kt
  - app/src/main/java/com/cinetrack/data/repository/CineTrackRepository.kt
  - app/src/main/java/com/cinetrack/ui/CineTrackViewModel.kt
  - app/src/main/java/com/cinetrack/ui/screens/LibrarySettingsScreens.kt
  - app/src/main/java/com/cinetrack/ui/settings/SettingsViewModel.kt
  - app/src/test/java/com/cinetrack/ui/settings/SettingsViewModelTest.kt
  - app/src/main/java/com/cinetrack/domain/CalendarExport.kt
  - app/src/test/java/com/cinetrack/data/repository/BackupRoundTripTest.kt
  - app/src/test/java/com/cinetrack/domain/CalendarExportTest.kt
---

## Decided
- app/src/test/.../data/repository/BackupRoundTripTest.kt is the first test that builds a real CineTrackRepository: Robolectric, in-memory Room, real AppPreferences, ApiServices made of throwing java.lang.reflect.Proxy instances, a TrackingProviderRegistry with no providers. It exports from one database and restores into a fresh one (a reinstall).
- It checks: every history event comes back with the exact watchedAt string (rewatches, bulk-import identical instants, a movie watched twice), statuses, playback, settings, a completed show with no history gets none, nothing is queued in sync operations, and restoring the same backup twice adds no history.
- Fixed a real bug it found: restoreBackupFiles only recreated media rows for library and progress items. History of a title removed from the library (status NONE) was written to Room but hidden (loadCachedState drops history without a media row), and the automatic backup scheduled 1.5 s after restore then dropped it for good. Restore now inserts a bare media row from the history title, only when the target database has none, so cached metadata is never overwritten.

## Watch out
- Robolectric has no Android Keystore: AppPreferences' SecureCredentialStore needs a fake "AndroidKeyStore" provider (the test registers an empty KeyStoreSpi; Provider's String-version constructor isn't on the Android stub, use the Double one).
- Restoring settings calls TrackingWorkScheduler, so call WorkManagerTestInitHelper.initializeTestWorkManager first.
- Don't close the in-memory databases in @After: the repository's delayed automatic backup reads them afterwards and logs "re-open an already-closed object".
- AppPreferences/DataStore is a process-wide singleton across Robolectric tests; set every preference a test asserts on.
- CineTrackRepository.kt is CRLF; script edits must match \r\n.
