---
title: Floppy bootstrap jobs stuck queued (0.99.79)
author: Joke9854
date: 2026-11-03
branch: beta-testing
cost: { minutes: 30, tokens: 30480 }
touches:
  - app/src/main/java/com/cinetrack/data/sync/floppy/network/FloppyRemoteDataSource.kt
  - app/src/test/java/com/cinetrack/data/sync/floppy/FloppyRemoteDataSourceTest.kt
  - docs/ROADMAP.md
  - CHANGELOG.md
  - app/build.gradle.kts
  - app/src/main/java/com/cinetrack/data/repository/AppPreferences.kt
  - app/src/main/java/com/cinetrack/data/sync/floppy/FloppyBootstrapWork.kt
  - app/src/main/java/com/cinetrack/data/sync/floppy/FloppyModels.kt
  - app/src/main/java/com/cinetrack/data/sync/floppy/FloppyTrackingProvider.kt
  - app/src/main/java/com/cinetrack/data/sync/floppy/network/FloppyApi.kt
---

## Decided
- Root cause on Floppy: Celery processes use a reduced INSTALLED_APPS without "api", so autodiscovery never imported api.tasks; workers logged "Received unregistered task of type 'Run CineTrack bootstrap job'" and every job stayed queued. Fixed by adding "api.tasks" to the background CELERY_IMPORTS (Floppy 5358b04b).
- Client (0.99.80): jobs only for batches > 50 entries; a job still queued after 60 s falls back to the synchronous ensure (safe, ensures are idempotent); FloppyRemoteDataSource.push rethrows CancellationException instead of mapping it to TrackingSyncError.Unknown.

## Tried and ruled out
- Suspecting the cache backend: Floppy's cache is shared Redis, so job state is visible to web and worker.

## Watch out
- FloppyApiErrorMapper.map turns any unknown Throwable into Unknown, including CancellationException; that recorded "Job was cancelled" as a delivery lastError and swallowed worker stops. Rethrow cancellations before mapping.
- Floppy tests run Celery tasks eagerly, so a task missing from a worker's registry passes CI. Check registration as FLOPPY_PROCESS_ROLE=background (sys.argv with "test" avoids Redis).
