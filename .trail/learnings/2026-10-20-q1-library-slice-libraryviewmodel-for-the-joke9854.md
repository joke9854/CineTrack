---
title: "Q1 Library slice: LibraryViewModel for the artwork refresh"
author: Joke9854
date: 2026-10-20
branch: beta-testing
cost: { minutes: 20, tokens: 116510 }
touches:
  - app/src/main/java/com/cinetrack/domain/Models.kt
  - app/src/main/java/com/cinetrack/ui/CineTrackApp.kt
  - app/src/main/java/com/cinetrack/ui/CineTrackViewModel.kt
  - app/src/main/java/com/cinetrack/ui/screens/LibrarySettingsScreens.kt
  - app/src/main/java/com/cinetrack/data/repository/RepositoryLibraryArtwork.kt
  - app/src/main/java/com/cinetrack/ui/library/LibraryViewModel.kt
  - app/src/test/java/com/cinetrack/ui/library/LibraryViewModelTest.kt
---

## Decided
- Q1 after Settings: Library first (smallest screen). The user chose to keep going and release Settings + Library together later, instead of shipping Settings as 0.99.70 first.
- ui/library/LibraryViewModel (activity-scoped, the artwork pill shows on every screen) owns the library artwork refresh: refreshArtwork(tmdbConfigured), dismissProgress, LibraryArtworkState(refreshing, progress, progressVisible), errors -> CineTrackViewModel.showError. Store LibraryArtworkStore -> data/repository/RepositoryLibraryArtwork (LibraryArtworkRefreshManager + AppPreferences.wifiOnly).
- AppUiState lost libraryArtworkRefreshing/Progress/ProgressVisible; CineTrackViewModel no longer takes LibraryArtworkRefreshManager. Library rows (rails, history, playback) still come from AppUiState; setStatus stays on the main VM (shared by many screens).

## Tried and ruled out
- Re-reading artwork progress after awaitStartup in the new VM: only needed before because readCachedState() overwrote the fields in AppUiState; a separate StateFlow just collects manager.progress.

## Watch out
- adb "server is out of date" means two adb binaries are fighting (Gradle/SDK vs PATH); it drops the S23 and closes emulators. installPreview then fails with "No connected devices" after a successful R8 build.
