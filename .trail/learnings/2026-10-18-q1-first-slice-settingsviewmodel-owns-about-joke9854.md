---
title: "Q1 first slice: SettingsViewModel owns About, Logs, Language"
author: Joke9854
date: 2026-10-18
branch: beta-testing
cost: { minutes: 23, tokens: 110586 }
touches:
  - app/src/main/java/com/cinetrack/data/repository/AppPreferences.kt
  - app/src/main/java/com/cinetrack/ui/CineTrackApp.kt
  - app/src/main/java/com/cinetrack/ui/CineTrackViewModel.kt
  - app/src/main/java/com/cinetrack/ui/screens/LibrarySettingsScreens.kt
  - app/src/main/java/com/cinetrack/ui/settings/SettingsViewModel.kt
  - app/src/test/java/com/cinetrack/ui/settings/SettingsViewModelTest.kt
  - app/build.gradle.kts
  - baselineprofile/build.gradle.kts
  - build.gradle.kts
  - docs/ROADMAP.md
---

## Decided
- ui/settings/SettingsViewModel.kt is the first screen ViewModel split from CineTrackViewModel: app update + changelog, Logs (read/clear/export), Language. It is created in CineTrackApp next to the main ViewModel (activity scope) and passed down, because the update pill on every screen shares appUpdateState with Settings > About. Creating it with viewModel() inside a NavHost destination would scope it to that back-stack entry.
- Its failures go out through SettingsViewModel.errors; CineTrackApp forwards them to CineTrackViewModel.showError so the existing error banner and error-log append still work.
- Logs stay live through AppPreferences.errorLogChanges (bumped on append/clear). The main ViewModel no longer keeps an in-memory log list; it only appends to the file.
- Fakes: SettingsPreferences and AppUpdater interfaces (GitHubUpdater wraps the GitHubAppUpdater object). Tests run under Robolectric only for a Context.

## Tried and ruled out
- Moving Notifications, Metadata, Ratings, Regions or Backup in this slice: they write AppUiState fields the main ViewModel reads (notifiesByDefault, detail caches, scheduleProgressCacheRefresh, readCachedState). Each needs the main state to observe preferences first. Sync/Floppy pages touch sync invariants and go last.

## Watch out
- androidx.test.core is not on the unit-test classpath; use RuntimeEnvironment.getApplication().
