---
title: "Q1 second slice: SearchViewModel owns title and people search"
author: Joke9854
date: 2026-10-19
branch: beta-testing
cost: { minutes: 17, tokens: 92956 }
touches:
  - app/src/main/java/com/cinetrack/data/repository/AppPreferences.kt
  - app/src/main/java/com/cinetrack/ui/CineTrackApp.kt
  - app/src/main/java/com/cinetrack/ui/CineTrackViewModel.kt
  - app/src/main/java/com/cinetrack/ui/search/SearchViewModel.kt
  - app/src/test/java/com/cinetrack/ui/search/SearchViewModelTest.kt
---

## Decided
- ui/search/SearchViewModel.kt owns title search, people search (300 ms debounce, stale-result guard), the recent-search list and clearSearch. Created in CineTrackApp next to the main and Settings ViewModels (activity scope), because the details route looks up a title opened from searchResults.
- SearchHistoryStore interface (searchHistory flow + add/clear/remove); AppPreferences implements it, like SettingsPreferences.
- "Not interested" still hides a title from search results: CineTrackApp passes one hideDiscoveryItem lambda that calls CineTrackViewModel.hideDiscoveryItem and SearchViewModel.hideResult.
- SearchViewModel takes DiscoveryRepository/PeopleRepository directly; both interfaces are small enough to fake. Tests are plain JVM (no Robolectric): runTest(UnconfinedTestDispatcher) + advanceTimeBy(301), with the same dispatcher injected as io.

## Tried and ruled out
- Chosen over Settings part 2 and Discover for this slice: Search shares no AppUiState fields, so it needed no preference observation in the main state.

## Watch out
- AppPreferences.kt and CineTrackViewModel.kt are LF in the working copy (git warns about CRLF on checkout); edit them line-ending-preserving.
- The S23 must be connected over adb before installPreview, or the build runs ~8 min and then fails with "No connected devices".
