---
title: "Q2 first slice: MetadataSettingsRepository out of the CineTrackRepository facade"
author: Joke9854
date: 2026-10-20
branch: beta-testing
cost: { minutes: 28, tokens: 99804 }
touches:
  - app/src/main/java/com/cinetrack/CineTrackApplication.kt
  - app/src/main/java/com/cinetrack/data/repository/CineTrackRepository.kt
  - app/src/main/java/com/cinetrack/data/repository/RepositoryContentSettings.kt
  - app/src/main/java/com/cinetrack/ui/CineTrackApp.kt
  - app/src/main/java/com/cinetrack/ui/CineTrackViewModel.kt
  - app/src/main/java/com/cinetrack/data/repository/MetadataSettingsRepository.kt
  - app/src/test/java/com/cinetrack/data/repository/MetadataSettingsRepositoryTest.kt
  - CHANGELOG.md
  - app/build.gradle.kts
  - app/src/main/java/com/cinetrack/ui/screens/DetailScreens.kt
---

## Decided
- Q2 slice order (facade is 3852 lines): 1 metadata settings + API keys, 2 backup, 3 metadata loads (~1000 lines), 4 progress cache / loadCachedState, 5 accounts/sign-in and the SimklSyncHost overrides last (sync invariants).
- data/repository/MetadataSettingsRepository owns verifyAndSet TMDB/MDBList/Trakt, setMetadataLanguage/Region/Timezone, setAirDateSource and the five live-value callbacks (AtomicReferences in AppContainer that the network clients read). AppContainer.metadataSettings (lazy); RepositoryContentSettings takes AppPreferences + MetadataSettingsRepository instead of the facade.
- The facade keeps clearLanguageCaches() (castCache, episodeTitleCache, recommendationCandidatesCache belong to the metadata loads, slice 3) and gets metadataSettingsProvider so backup restore still updates the live values; without it (tests) it builds a local MetadataSettingsRepository with no-op callbacks, as the old defaults.
- Removed uncalled facade setTmdbApiKey/setMdbListApiKey and CineTrackViewModel.setTmdbApiKey.

## Watch out
- AppContainer.metadataSettings is a lazy that references repository; it must only be read after the init block assigns repository (restore and the settings pages run long after).
- Run lintDebug on every slice: unit tests do not catch StateFlowValueCalledInComposition (0.99.70 found one only at release).
