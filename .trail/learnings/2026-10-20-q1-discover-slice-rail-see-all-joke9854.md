---
title: "Q1 Discover slice: rail see-all paging moves to DiscoverViewModel"
author: Joke9854
date: 2026-10-20
branch: beta-testing
cost: { minutes: 11, tokens: 234598 }
touches:
  - app/src/main/java/com/cinetrack/ui/CineTrackApp.kt
  - app/src/main/java/com/cinetrack/ui/CineTrackViewModel.kt
  - app/src/main/java/com/cinetrack/ui/discover/DiscoverViewModel.kt
  - app/src/main/java/com/cinetrack/ui/screens/DiscoverScreens.kt
  - app/src/test/java/com/cinetrack/ui/discover/DiscoverViewModelTest.kt
  - app/src/main/java/com/cinetrack/CineTrackApplication.kt
  - app/src/main/java/com/cinetrack/data/repository/RepositoryDetailStore.kt
  - app/src/main/java/com/cinetrack/ui/detail/DetailEpisodeCache.kt
  - app/src/main/java/com/cinetrack/ui/detail/DetailViewModel.kt
  - app/src/main/java/com/cinetrack/ui/screens/DetailScreens.kt
---

## Decided
- The Discover rails' "see all" paging (discoverBrowse, loadDiscoverMore, the browse generation) moved from CineTrackViewModel to the existing DiscoverViewModel. This reverses the earlier note that said it stays because it is built from library state: it only needs the rail key and first items, so loadDiscoverMore(railId, state) takes AppUiState as an argument.
- CineTrackViewModel.refresh() now returns whether a refresh started; CineTrackApp calls discoverViewModel.resetBrowse() only then (same as the old in-place reset, which was skipped when TMDB was missing or a refresh was running).
- Status writes: CineTrackViewModel.libraryStatusChanges (SharedFlow, emitted after the write and state republish, where the old browse patch ran) -> DiscoverViewModel.applyStatus.
- The rails themselves, refresh(), hideDiscoveryItem and setStatus stay on CineTrackViewModel (Room projection and library writes).

## Watch out
- DiscoverListScreen now takes DiscoverViewModel; the browse status patch is one event later than before, which was already asynchronous (after the DB write).
