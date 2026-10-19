---
title: "Baseline profile on the S23: run journeys with adb, not Gradle"
author: Joke9854
date: 2026-10-19
branch: beta-testing
cost: { minutes: 29, tokens: 37973 }
touches:
  - baselineprofile/README.md
  - app/src/release/generated/baselineProfiles/baseline-prof.txt
  - app/src/release/generated/baselineProfiles/startup-prof.txt
  - docs/floppy-integration.md
  - README.md
  - ".github/workflows/android.yml"
  - app/src/main/java/com/cinetrack/ui/CineTrackApp.kt
  - app/src/main/java/com/cinetrack/ui/CineTrackViewModel.kt
  - app/src/main/java/com/cinetrack/ui/discover/DiscoverViewModel.kt
  - app/src/test/java/com/cinetrack/ui/discover/DiscoverViewModelTest.kt
---

## Decided
- The committed profile (app/src/release/generated/baselineProfiles/) came from the user's S23 (API 36) with a synced library: startup plus Progress -> Abbott Elementary (TMDB 125935) detail. baseline-prof.txt is the sorted union of both journeys; startup-prof.txt is the startup journey. It only affects the release build type (preview doesn't pick it up).
- Generation targets com.cinetrack, the user's real release app, so the user had to uninstall it and reinstall from GitHub afterwards. The user accepted this (2026-10-19).

## Tried and ruled out
- ./gradlew :app:generateReleaseBaselineProfile on the S23: Samsung Secure Folder (user 150) makes UTP print "Invalid userId -2". It runs 0 tests, reports BUILD SUCCESSFUL with "No baseline profile rules were generated", and still uninstalls com.cinetrack, deleting the setup the user had just done.

## Watch out
- Use adb install --user 0 plus adb shell "am instrument -w --user 0 ...", with the whole command as one quoted string. Passing -e mediaTitle through Git Bash either kept the quotes or broke '#'.
- Each instrumentation run empties /storage/emulated/0/Android/media/com.cinetrack.baselineprofile, so pull after each journey.
- adb pull to a directory path with a trailing slash fails on Windows ("Not a directory"); pull to an explicit file name.
