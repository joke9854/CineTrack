---
title: "Cast row: cast cache prefill and loadCachedState size limit"
author: Joke9854
date: 2026-10-18
branch: beta-testing
cost: { minutes: 23, tokens: 73239 }
touches:
  - CHANGELOG.md
  - app/build.gradle.kts
  - app/src/main/java/com/cinetrack/CineTrackApplication.kt
  - app/src/main/java/com/cinetrack/data/media/MediaRepository.kt
  - app/src/main/java/com/cinetrack/data/repository/AppPreferences.kt
  - app/src/main/java/com/cinetrack/data/repository/CineTrackRepository.kt
  - app/src/main/java/com/cinetrack/data/repository/LegacyMediaDataSource.kt
  - app/src/main/java/com/cinetrack/data/sync/floppy/FloppyModels.kt
  - app/src/main/java/com/cinetrack/data/sync/floppy/FloppyTrackingProvider.kt
  - app/src/main/java/com/cinetrack/data/sync/floppy/network/FloppyApi.kt
---

## Decided
- The TMDB cast shows first; character pictures are loaded separately (CineTrackViewModel.withCharacterPictures -> MediaRepository.loadCharacterPictures) and merged when Floppy answers, on show, movie and episode pages. Episode pages use the show's pictures and don't overwrite the show's cast cache (remember = false).
- Cast photo shape (circle/poster) lives in CardAppearance.castPhotoShape, read from its own StateFlow (CineTrackViewModel.castPhotoShape), not from AppUiState.

## Tried and ruled out
- Fetching pictures inside CineTrackRepository.loadCast: loadDetails already prefills castCache with the TMDB cast, so loadCast returned the cache and never called Floppy; making loadCast wait for Floppy made the cast row take seconds.
- Adding the setting to AppUiState: CineTrackRepository.loadCachedState fails to compile with "Method too large" (JVM 64 KB method limit) once one more preference is added.

## Watch out
- Any new setting: don't add it to loadCachedState until that method is split.
- Windows preview builds fail at random (resource merge "insufficient system resources", AAPT link, lint worker crash); rerun the single task to tell a flake from a real error, and never install the APK without checking its mtime.
