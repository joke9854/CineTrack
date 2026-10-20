---
title: "Q1 rest of Settings: ContentSettingsViewModel and BackupViewModel"
author: Joke9854
date: 2026-10-20
branch: beta-testing
cost: { minutes: 29, tokens: 224578 }
touches:
  - app/src/main/java/com/cinetrack/ui/CineTrackApp.kt
  - app/src/main/java/com/cinetrack/ui/CineTrackViewModel.kt
  - app/src/main/java/com/cinetrack/ui/screens/LibrarySettingsScreens.kt
  - app/src/main/java/com/cinetrack/data/repository/RepositoryBackupStore.kt
  - app/src/main/java/com/cinetrack/ui/settings/BackupViewModel.kt
  - app/src/test/java/com/cinetrack/ui/settings/BackupViewModelTest.kt
  - app/src/main/java/com/cinetrack/data/repository/RepositoryContentSettings.kt
  - app/src/main/java/com/cinetrack/ui/settings/ContentSettingsViewModel.kt
  - app/src/test/java/com/cinetrack/ui/settings/ContentSettingsViewModelTest.kt
  - app/src/main/java/com/cinetrack/data/repository/AppPreferences.kt
---

## Decided
- Q1 slices 6-8 (user chose "rest of Settings"; Sync/Simkl/Floppy pages are still on CineTrackViewModel and go last, they touch sync invariants).
- ui/settings/ContentSettingsViewModel: TMDB metadata (language, region, timezone, air-date source), adult filter, content regions, rating sources, streaming providers/country/offer types and the provider list, plus TMDB/MDBList/Trakt credential checks. Store interface ContentSettingsStore, implemented by data/repository/RepositoryContentSettings.
- ui/settings/BackupViewModel: export, restore from file, automatic restore and the backup pill (activity-scoped, CineTrackApp reads backupProgress from it). Zip guards moved unchanged. BackupStore -> RepositoryBackupStore.
- The main ViewModel keeps its caches and refreshes, triggered by events emitted after each write finishes: ContentSettingsViewModel.changes -> CineTrackViewModel.onContentChanged, BackupViewModel.restored -> onBackupRestored. AppUiState mirrors AppPreferences.contentSettings for display elsewhere.

## Tried and ruled out
- Letting the main ViewModel watch the preference flows for side effects: setAirDateSource writes the preference before reapplyAirDates rewrites the DB, so a refresh would read the old air dates. Events after completion keep the order.

## Watch out
- Provider and offer-type toggles read the stored set under the provider mutex (store.contentSettings.first()); computing from the StateFlow loses a quick second tap.
- Rating sources now pass the normalized key to AppPreferences.setRatingSource; the old code passed the label, so "R.Tomatoes" was never saved.
