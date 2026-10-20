---
title: "Q1 Detail slice: DetailViewModel and the shared episode cache"
author: Joke9854
date: 2026-10-20
branch: beta-testing
cost: { minutes: 10, tokens: 212164 }
touches:
  - app/src/main/java/com/cinetrack/CineTrackApplication.kt
  - app/src/main/java/com/cinetrack/ui/CineTrackApp.kt
  - app/src/main/java/com/cinetrack/ui/CineTrackViewModel.kt
  - app/src/main/java/com/cinetrack/ui/screens/DetailScreens.kt
  - app/src/main/java/com/cinetrack/data/repository/RepositoryDetailStore.kt
  - app/src/main/java/com/cinetrack/ui/detail/DetailEpisodeCache.kt
  - app/src/main/java/com/cinetrack/ui/detail/DetailViewModel.kt
  - app/src/test/java/com/cinetrack/ui/detail/DetailViewModelTest.kt
  - app/src/main/java/com/cinetrack/data/repository/RepositoryProgressStore.kt
  - app/src/main/java/com/cinetrack/ui/progress/ProgressViewModel.kt
---

## Decided
- ui/detail/DetailViewModel (activity-scoped; store DetailStore -> data/repository/RepositoryDetailStore over MediaRepository, PeopleRepository and repository.renumberedShows) owns every Detail/Episode/person metadata load and the detail caches (media, cast, ratings, collection, recommendations, opened titles), rememberOpened/openedMedia/loadMedia for the nav routes, loadTagline for Discover heroes, and forgetWatchedEpisodes. ActorSheet and SeasonInfoSheet take DetailViewModel.
- The per-show episode list cache is DetailEpisodeCache, one instance in AppContainer shared by both ViewModels: CineTrackViewModel's watched writes (setEpisodeWatched, setSeasonWatched, setEpisodesWatched, markPlaybackWatched via updateCachedEpisode) patch it synchronously as before; DetailViewModel fills and reads it.
- refreshTitle returns the fresh title; for a TV show the page then calls CineTrackViewModel.onTitleRefreshed() (suspend, refreshCachedState(refreshProgress = true)), keeping the old await order before the pull-refresh spinner stops.
- Content changes go to both: CineTrackApp forwards ContentSettingsViewModel.changes to DetailViewModel.onContentChanged (cache clears) and then CineTrackViewModel.onContentChanged (Progress refreshes, flags).
- Stay on CineTrackViewModel: all watched writes, state, setSeasonPicker.

## Tried and ruled out
- Sending watched changes to DetailViewModel as events: delivery is asynchronous, so a page recomposed right after the tap could read the stale watched mark. A shared cache object keeps the synchronous patch.

## Watch out
- In CineTrackApp, detailViewModel must be declared before the contentSettingsViewModel LaunchedEffect that uses it.
- CineTrackViewModel.setMdbListApiKey was dead (no callers) and cleared the moved ratings cache; it was removed. setTmdbApiKey is also uncalled but left alone.
