---
title: "Q2 small slices: BackupRepository and facade dead code"
author: Joke9854
date: 2026-10-20
branch: beta-testing
cost: { minutes: 12, tokens: 58219 }
touches:
  - app/src/main/java/com/cinetrack/CineTrackApplication.kt
  - app/src/main/java/com/cinetrack/data/repository/CineTrackRepository.kt
  - app/src/main/java/com/cinetrack/data/repository/RepositoryBackupStore.kt
  - app/src/main/java/com/cinetrack/data/sync/ReleaseNotifier.kt
  - app/src/main/java/com/cinetrack/ui/CineTrackApp.kt
  - app/src/main/java/com/cinetrack/ui/CineTrackViewModel.kt
  - app/src/test/java/com/cinetrack/data/repository/BackupRoundTripTest.kt
  - app/src/main/java/com/cinetrack/data/repository/BackupRepository.kt
  - app/src/main/java/com/cinetrack/data/repository/MetadataSettingsRepository.kt
  - app/src/main/java/com/cinetrack/data/repository/RepositoryContentSettings.kt
---

## Decided
- data/repository/BackupRepository owns exportBackupFiles, restoreBackupFiles, create/restoreAutomaticBackup and scheduleAutomaticBackup (moved verbatim). Dependencies are functions: metadataSettings (restore applies language/region/timezone through MetadataSettingsRepository so the network clients update), loadState = CineTrackRepository.loadCachedState, rebuildLibraryRail = rebuildLibraryRailInTransaction (runs inside the restore transaction). It has its own SupervisorJob+IO scope, like the old progressEnrichmentScope launch. AppContainer.backupRepository (lazy); RoomLibraryRepository's backup hook, RepositoryBackupStore and CineTrackViewModel (after-sync backup) use it.
- With restore gone, the facade no longer needs metadataSettingsProvider; it keeps clearLanguageCaches().
- Removed uncalled facade members: exportJson, loadWidgetUpNext, isSimklSyncDue, loadInitial, pushPendingLibraryChanges, pushLibraryChange, and the markWatched/markEpisodeWatched/setEpisodeWatched/setLibraryStatus passthroughs. The release notification's mark-watched action calls container.libraryRepository.markEpisodeWatched (the same RoomLibraryRepository instance the facade delegated to).

## Watch out
- BackupRoundTripTest builds BackupRepository next to the facade (Store.backup); restore still inserts history by exact restoreKey (type, id, season, episode, watchedAt), never merging rewatches.
- Next slices (bigger): metadata loads (LegacyMediaDataSource delegates them to the facade, with castCache/episodeTitleCache/recommendationCandidatesCache), then progress cache + loadCachedState, then accounts and SimklSyncHost.
