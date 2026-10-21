---
title: "Q2 slice 6c: SyncConflictResolver out of the facade"
author: Joke9854
date: 2026-10-21
branch: beta-testing
cost: { minutes: 15, tokens: 293415 }
touches:
  - app/src/main/java/com/cinetrack/CineTrackApplication.kt
  - app/src/main/java/com/cinetrack/data/repository/CineTrackRepository.kt
  - app/src/main/java/com/cinetrack/data/repository/RepositorySyncSettings.kt
  - app/src/main/java/com/cinetrack/ui/CineTrackApp.kt
  - app/src/main/java/com/cinetrack/data/repository/SyncConflictResolver.kt
  - app/src/test/java/com/cinetrack/data/repository/SyncConflictResolverTest.kt
  - app/src/main/java/com/cinetrack/data/repository/AccountRepository.kt
  - app/src/main/java/com/cinetrack/ui/CineTrackViewModel.kt
  - app/src/main/java/com/cinetrack/ui/settings/SyncSettingsViewModel.kt
  - app/src/test/java/com/cinetrack/ui/settings/SyncSettingsViewModelTest.kt
---

## Decided
- data/repository/SyncConflictResolver owns resolveSyncConflict and the keep-local/use-remote code (buildKeepLocal*, resolveUseRemoteConflict, validateRemoteConflict, validateConflictGeneration, removeExactConflictIntent, RemoteConflictPlan and the operation/write mappers), moved byte-for-byte (checked with an ordered diff, not only a line-set diff).
- Wiring decided in the main session: ctor takes database, durableOperationWriter, syncCoordinator, trackingRoutingMutex (the same instances the facade gets) plus functions stateOperationId, rebuildLibraryRailInTransaction and enqueueSecondaryMirror (a lambda so MainSyncHost's default mainProvider still applies). AppContainer.syncConflicts is built right after the facade from repository:: references. RepositorySyncSettings(repository, accounts, conflicts).
- One generation floor: top-level internal persistedGenerationFloor(database) in SyncConflictResolver.kt; the facade and the resolver both compute MutationGeneration.next(previous = it). Both resolver call sites hold trackingRoutingMutex (use-remote takes it; keep-local runs inside durableOperationWriter.enqueueFromCurrentState).
- SyncConflictResolverTest is the first unit coverage of conflict resolution (real DurableSyncOperationWriter/SyncCoordinator, Robolectric Room as in BackupRoundTripTest): use-remote writes history at exactly Instant.ofEpochMilli(updatedAt), removes the exact local write and the conflict, and mirrors with a fresh generation; a stale conflict fails with "Conflict changed while resolving" and changes nothing.

## Watch out
- RoomLibraryRepository.nextMutationGenerationLocked still has its own copy of the generation-floor query; unify only in a deliberate change, not during a move.
- "Risorse di sistema insufficienti" (insufficient system resources) on a source-file read is the machine out of memory (7.5 GB RAM, a Gradle daemon alive for hours held ~3.8 GB): ./gradlew --stop and rerun.
