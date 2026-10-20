---
title: "Q1 Progress slice: ProgressViewModel for release bells and statistics"
author: Joke9854
date: 2026-10-20
branch: beta-testing
cost: { minutes: 17, tokens: 159982 }
touches:
  - app/src/main/java/com/cinetrack/ui/CineTrackApp.kt
  - app/src/main/java/com/cinetrack/ui/CineTrackViewModel.kt
  - app/src/main/java/com/cinetrack/ui/screens/DetailScreens.kt
  - app/src/main/java/com/cinetrack/data/repository/RepositoryProgressStore.kt
  - app/src/main/java/com/cinetrack/ui/progress/ProgressViewModel.kt
  - app/src/test/java/com/cinetrack/ui/progress/ProgressViewModelTest.kt
  - app/src/main/java/com/cinetrack/data/repository/RepositoryLibraryArtwork.kt
  - app/src/main/java/com/cinetrack/domain/Models.kt
  - app/src/main/java/com/cinetrack/ui/library/LibraryViewModel.kt
  - app/src/main/java/com/cinetrack/ui/screens/LibrarySettingsScreens.kt
---

## Decided
- ui/progress/ProgressViewModel (activity-scoped; store ProgressStore -> data/repository/RepositoryProgressStore) owns the release reminder bells (releaseReminders, isReleaseReminderOn, setReleaseReminder) and the Statistics people (viewingInsights, loadViewingInsights(history)). The Detail and Episode countdowns take a progressViewModel param for the bell.
- "Notifies by default" (calendar entry + episode/movie notification switch) needs AppUiState, so it is the pure function ui/progress/notifiesByDefault(state, ...) and callers pass byDefault in. Detail reads viewModel.state.value for it, like the old non-reactive read.
- Stay on CineTrackViewModel: markPlaybackWatched, hideUpcomingEpisode, sync/syncProgress (episode writes, pending edits and the Progress cache).

## Tried and ruled out
- A separate reminders ViewModel for Detail: the bell is one feature shared by Progress and Detail; one owner keeps a single write path to the reminder keys.

## Watch out
- Un-muting a default release must not schedule an explicit reminder on top of the default notification; ProgressViewModelTest pins that.
