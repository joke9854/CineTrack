---
title: "Q2 slice 5: CatalogRepository (discover, search, providers) out of the facade"
author: Joke9854
date: 2026-10-21
branch: beta-testing
cost: { minutes: 30, tokens: 154567 }
touches:
  - app/src/main/java/com/cinetrack/CineTrackApplication.kt
  - app/src/main/java/com/cinetrack/data/repository/CineTrackRepository.kt
  - app/src/main/java/com/cinetrack/data/repository/LegacyMediaDataSource.kt
  - app/src/main/java/com/cinetrack/data/repository/CatalogRepository.kt
  - app/src/main/java/com/cinetrack/data/repository/ProgressCacheRepository.kt
  - app/src/main/java/com/cinetrack/data/sync/ReleaseNotifier.kt
  - app/src/main/java/com/cinetrack/data/work/LibraryArtworkRefreshWork.kt
---

## Decided
- data/repository/CatalogRepository owns refreshDiscover, loadDiscoverPage, discoverMovies, discoverBatch, search, searchPeople, loadStreamingProviders and loadSettingsStreamingProviders, moved verbatim. Deps: database, services, preferences, tmdbApiKey. It reuses the internal helpers loadTrendingCandidates, inContentRegions, cancellableResult and effectiveProviderRegion; it has its own private localZone/localToday (the facade's localToday is a SimklSyncHost override and stays).
- LegacyMediaDataSource now takes (CatalogRepository, MediaMetadataRepository) and no longer references the facade; AppContainer builds the catalog next to it. Facade: 2193 -> 1875 lines.

## Watch out
- What is left in the facade: loadCachedState/buildTrackingProviderStates, accounts and sign-in (Simkl/Floppy), sync conflict resolution, refreshLibraryArtwork-free progress delegate, and the SimklSyncHost/MainSyncHost overrides. That last slice touches sync invariants: decide it in the main session.
- `gh project item-list` can lag and miss freshly created drafts (returned 72 items without the 3 new ones). Read new drafts by ID with `gh api graphql` and a `nodes(ids: [...])` query.
- lintDebug once failed with a lint internal error and passed on rerun; treat a single internal-error failure as a flake only after a clean rerun.
