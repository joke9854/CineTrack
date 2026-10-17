---
title: "Roadmap audit 2026-10-17: state of CineTrack and Floppy"
author: Joke9854
date: 2026-10-17
branch: beta-testing
cost: { minutes: 4, tokens: 33955 }
touches:
  - docs/ROADMAP.md
  - CLAUDE.md
---

## Decided
- The roadmap lives in docs/ROADMAP.md with board-ready rows (App, Area, Kind, Priority, Target, Estimate). Proposed order: 0.99.64 hardening (H1-H9), then 1.0 quality bar (Q*), then features (F*, S*). Personal ratings (F1) is the first feature candidate. Open decisions are listed at the bottom of the file.
- Most CineTrack feature gaps already have Floppy server support (sync/ratings, lists, statistics/overview, sync/conflicts, playback/now-playing, ~30 importers). Prefer consuming those over building client-only versions.

## Tried and ruled out
- docs/CineTrack_Floppy_Project_State.md, the reference doc named in CLAUDE.md, is not in either repo.
- A bug from the capability flags drifting: cinetrack/connection omits cinetrack_episode_translate_v1, but CineTrack reads that flag from /info (FloppyRemoteDataSource.kt:158). Only hygiene (H4).
- Hilt/Koin now: the manual AppContainer works. Revisit only during the ViewModel split.

## Watch out
- Floppy CI on CineTrack-API runs only cinetrack-targeted.yml (7 modules, no lint). test_fork_tracking, sync_writes, sync_items, translate, crash_reports, oauth_app_signin and fork_scrobble are not run there.
- Floppy docker-image.yml hardcodes dannyvfilms/floppy, so no published image contains the CineTrack endpoints.
- The CineTrack emulator CI job runs only AppDatabaseMigrationTest.
- The Floppy clone currently has no `upstream` remote; re-add dannyvfilms/Floppy before an upstream sync.
- Removing SimklSyncWorker needs a cancel of old enqueued work first, or WorkManager fails to instantiate the class.
