---
title: CineTrack client for Floppy bootstrap jobs
author: Joke9854
date: 2026-11-02
branch: beta-testing
cost: { minutes: 7, tokens: 139561 }
touches:
  - app/src/main/java/com/cinetrack/data/repository/AppPreferences.kt
  - app/src/main/java/com/cinetrack/data/sync/floppy/FloppyBootstrapWork.kt
  - app/src/main/java/com/cinetrack/data/sync/floppy/FloppyModels.kt
  - app/src/main/java/com/cinetrack/data/sync/floppy/FloppyTrackingProvider.kt
  - app/src/main/java/com/cinetrack/data/sync/floppy/network/FloppyApi.kt
  - app/src/main/java/com/cinetrack/data/sync/floppy/network/FloppyRemoteDataSource.kt
  - app/src/test/java/com/cinetrack/data/sync/floppy/FloppyRemoteDataSourceTest.kt
  - docs/ROADMAP.md
---

## Decided
- Bootstrap movies/shows go through POST cinetrack/bootstrap/jobs/ + GET .../jobs/{id}/ only when /info advertises cinetrack_bootstrap_jobs_v1 (FloppyCapabilities.canBootstrapJobs, persisted as "bootstrap_jobs_v1"; learned on connect only, so an existing connection needs a reconnect to pick it up).
- All logic lives in FloppyRemoteDataSource.pushBootstrapV2 -> bootstrapStatuses/awaitBootstrapJob. Results map by index (order sent). created/already_satisfied are ACKed; error or unknown statuses go to rejectedOperationIds, so SyncCoordinator throws PartiallyRejected and the worker skips them for this run (resent on the next run; no loop).
- Submit 503/404 -> fall back to synchronous 50-item ensure (nothing was written). Poll 404 -> resubmit once, then fail. Poll budget 5 min with 1s..10s backoff -> TrackingSyncError.Timeout (retryable).
- Job ids are not persisted: after an app restart the unACKed operations are resubmitted; Floppy ensures are idempotent.
- Worker: movie wave 200 with jobs (BOOTSTRAP_JOB_MOVIES_MAX), push timeout bootstrapPushTimeoutMs(); shows still come in 50-show units.

## Tried and ruled out
- One request DTO with nullable movies/shows: the server returns 400 if both keys are present, so there are two Retrofit methods on the same path.

## Watch out
- The movie V2 branch of FloppyBootstrapWorker did not handle PartiallyRejected before (only the show/episode branch did). It does now.
- The watchdog can show STALLED during a long poll (no progress callback from the transport).
