---
title: Baseline profile regenerated after the Q2 facade split
author: Joke9854
date: 2026-10-22
branch: beta-testing
cost: { minutes: 27, tokens: 26879 }
touches:
  - app/src/release/generated/baselineProfiles/baseline-prof.txt
  - app/src/release/generated/baselineProfiles/startup-prof.txt
  - app/src/main/java/com/cinetrack/CineTrackApplication.kt
  - app/src/main/java/com/cinetrack/UpNextWidgetProvider.kt
  - app/src/main/java/com/cinetrack/data/repository/BackupRepository.kt
  - app/src/main/java/com/cinetrack/data/repository/CatalogRepository.kt
  - app/src/main/java/com/cinetrack/data/repository/MediaMetadataRepository.kt
  - app/src/main/java/com/cinetrack/data/repository/MetadataSettingsRepository.kt
  - app/src/main/java/com/cinetrack/data/repository/ProgressCacheRepository.kt
  - app/src/main/java/com/cinetrack/data/sync/ReleaseNotifier.kt
---

## Decided
- Regenerated app/src/release/generated/baselineProfiles/ on 2026-10-22 after Q2 renamed/split CineTrackRepository (the old rules named its methods and no longer matched). Same journeys as 2026-10-19: startup, then Progress -> Abbott Elementary (TMDB 125935) detail, on the S23 via adb (README flow). baseline-prof.txt = LC_ALL=C sort -u of both journey files; startup-prof.txt = the startup journey, also sorted (the committed one was sorted). Both LF.
- The user exported a backup from the release app and approved uninstalling com.cinetrack for this run; afterwards the release is reinstalled from GitHub and the backup restored.

## Watch out
- The startup journey writes only BaselineProfileGenerator_startup-startup-prof.txt (no -baseline-prof file); that's expected. Journeys took ~1.5 and ~3.5 min.
- After any large class move or rename, grep the committed profiles for the old class names: stale rules are silently ignored and the moved code loses AOT compilation.
- ./gradlew :app:compileReleaseArtProfile checks that the rules compile into baseline.prof without a full release build or signing.
