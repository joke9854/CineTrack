---
title: "Q1 Discover slice, Q8 screenshot verify in CI, Q9 docs"
author: Joke9854
date: 2026-10-19
branch: beta-testing
cost: { minutes: 22, tokens: 77890 }
touches:
  - ".github/workflows/android.yml"
  - README.md
  - app/src/main/java/com/cinetrack/ui/CineTrackApp.kt
  - app/src/main/java/com/cinetrack/ui/CineTrackViewModel.kt
  - app/src/main/java/com/cinetrack/ui/discover/DiscoverViewModel.kt
  - app/src/test/java/com/cinetrack/ui/discover/DiscoverViewModelTest.kt
  - app/src/main/java/com/cinetrack/data/repository/AppPreferences.kt
  - app/src/main/java/com/cinetrack/ui/search/SearchViewModel.kt
  - app/src/test/java/com/cinetrack/ui/search/SearchViewModelTest.kt
  - docs/floppy-integration.md
---

## Decided
- ui/discover/DiscoverViewModel.kt owns the filter page results, the Mosaic results with its cache/paging, and the filter page's streaming providers. Rails, discoverBrowse ("see all") and restoreHiddenDiscovery stay in CineTrackViewModel because they read/write AppUiState (rails, refresh, hiddenDiscovery). The settings provider picker (loadSettingsStreamingProviders) also stays there.
- Failures go out through DiscoverViewModel.errors, which CineTrackApp forwards to showError (same as SettingsViewModel). loadStreamingProviders takes the preferred set as an argument; CineTrackApp passes state.preferredProviders.
- "Not interested" is one hideDiscoveryItem lambda in CineTrackApp that calls the main, Search and Discover ViewModels.
- Q8: CI's Compile-and-test step passes -Proborazzi.test.verify=true to testDebugUnitTest (no second test run) and uploads app/build/outputs/roborazzi on failure. The baseline profile stays with the user, per the roadmap.
- Q9: README rewritten for 0.99.x; Floppy docs/integrations/cinetrack.md lists the CineTrack endpoints, /info flags and invariants.

## Watch out
- Screenshot references were recorded on Windows; the first Linux CI verify run is the real check. If it fails on render-only differences, re-record from the CI artifact rather than loosening the comparison blindly.
- CineTrack docs/floppy-integration.md still says Floppy MAIN is not enabled; that's stale since 0.99.47.
