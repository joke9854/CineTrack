---
title: "Q2 slice 4: progress cache out of the CineTrackRepository facade"
author: Joke9854
date: 2026-10-21
branch: beta-testing
cost: { minutes: 2, tokens: 75881 }
---

## Decided
- Slice 4 moves the progress cache into the existing internal ProgressCacheRepository: refreshProgressCache, refreshLocalUpNext, scheduleProgressEnrichment, enrichHistoryLabels (+ episodeTitleCache/episodeTitleMisses), loadUpcomingEpisodes, refreshSimklSchedule, refreshMissingArtwork, refreshLibraryArtwork (+ helpers), cachedEpisodeTitle.
- loadCachedState and buildTrackingProviderStates stay in the facade: they build provider states, so they go with the accounts/SimklSyncHost slice. ProgressCacheRepository gets loadCachedState as a function (container lambda `{ repository.loadCachedState() }`), like BackupRepository's loadState.
- The facade keeps `override refreshProgressCache` as a one-line delegate because MainSyncHost/SimklSyncHost (FloppySyncEngine, SimklSyncEngine) call it; the ViewModel, ReleaseNotifier, SimklSyncWorker and RepositoryBackupStore callers stay on the facade until the host slice. Only LibraryArtworkRefreshWork and ReleaseNotifier.cachedEpisodeTitle are rewired to container.progressCache.
- The facade takes `progressCacheProvider: ProgressCacheRepository? = null` with a lazy local fallback so tests that build the facade still work (same pattern as metadataSettingsProvider).

## Tried and ruled out
- Reusing RoomLibraryRepository's private refreshLocalUpNext: it is a near-duplicate, but it runs inside the library's mutation transaction and maps entities inline. Unifying the two is a separate change, not part of this move.

## Watch out
- clearLanguageCaches must still clear the episode-title cache (now through progressCache.clearEpisodeTitleCache()).
- Run lintDebug on the slice; unit tests miss Compose StateFlow lint issues.
