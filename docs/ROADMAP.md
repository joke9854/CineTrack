# CineTrack + Floppy roadmap

Audit date: 2026-10-17. CineTrack `beta-testing` @ c897ead (0.99.63, versionCode 162). Floppy `CineTrack-API` @ c2bfb856.
Board: https://github.com/users/joke9854/projects/2. The columns below match the board fields (App, Area, Kind, Priority, Target, Estimate XS/S/M/L/XL).
Items marked **[board]** are already on the board.

## State in one screen

**CineTrack.** 37k lines of Kotlin in `:app`, plus `:baselineprofile`. Room v14 with 11 migrations. 416 unit tests and 39 instrumented tests. No TODO markers.
- Complete:
  - Discover/search, detail, Progress/Up Next
  - Simkl two-way sync
  - Floppy as main or secondary tracker (V2 bootstrap, snapshot/changes feed, OAuth, crash upload)
  - Backup/restore (JSONL+CSV), ICS export, release notifications with quiet hours
  - Shortcuts and deep links, EN/IT strings, in-app updater
- Partial:
  - Stats: a section inside Progress, no screen of its own
  - Up Next widget: opens the app only
  - Trakt: used for calendar air times only
- Stub: personal ratings. `SET_RATING` op exists, but there is no UI and both providers report `supportsRatings = false`.
- Missing: custom lists, imports from other trackers, tablet layouts, onboarding without bring-your-own TMDB key.
- Structural debt:
  - `CineTrackViewModel` (2.1k lines, passed to 29 screens), `AppUiState` (86 fields), `CineTrackRepository` façade (3.8k lines)
  - Manual DI, no version catalog
  - CI emulator job runs only `AppDatabaseMigrationTest`
  - No ViewModel or backup tests
  - ~77 icons without content descriptions

**Floppy fork.** Django 5.2 LTS, DRF, DOT 3.4.1, Celery/Redis, SQLite (WAL) or Postgres. 70 commits ahead of `latest`.
- The full CineTrack/sync API is complete and has drf-spectacular annotations. The server also has importers for about 30 services, Apprise notifications, lists/smart lists, a large stats system, iCal and an MCP server.
- Risks:
  - A push to `CineTrack-API` runs only 7 test modules and no lint.
  - `docker-image.yml` builds `dannyvfilms/floppy` only, so no published image contains the CineTrack endpoints.
  - Bootstrap ensure writes one row at a time and sleeps for SQLite locks inside the request thread.
  - CineTrack capability flags are listed twice and the lists already differ.
  - `REGISTRATION` defaults to on.
  - The iCal URL uses the API token.
  - CRLF files and fork migration numbers (app 0186-0187, users 0134-0135, integrations 0048-0049) will conflict with upstream.

**Already resolved (verify on device):** the bootstrap preflight now uses `GET /api/v1/cinetrack/connection/` (FloppyApi.kt:63). `GET /user/preferences/` is still declared and has no caller.

## Milestone 0.99.64: hardening (no new UI)

| # | Item | App | Area | Kind | Pri | Est |
|---|---|---|---|---|---|---|
| H1 | Floppy CI: on `CineTrack-API` pushes, run every CineTrack-related test module (test_fork_tracking, sync_writes, sync_items, episode_translate, crash_reports, oauth_app_signin, fork_scrobble) plus lint | Floppy | Tests | Test | P1 | S |
| H2 | Run all instrumented tests in the CineTrack emulator job, not only the migration test (extends the existing board item) **[board]** | CineTrack | Tests | Test | P1 | S |
| H3 | Build and publish our own Floppy image from `CineTrack-API` (e.g. ghcr.io/joke9854/floppy) and point compose at it; record the image SHA per deploy **[board: CI per SHA]** | Floppy | Release | Feature | P1 | M |
| H4 | One source of truth for capability flags (`/info` and `cinetrack/connection` lists differ) plus a contract test | Floppy | API | Bug | P2 | XS |
| H5 | Batch the bootstrap ensure writes and move SQLite lock backoff out of the gunicorn thread **[board: N+1]** | Floppy | API | Feature | P2 | M |
| H6 | Remove the dead `user/preferences` declaration, confirm the preflight on the S23, update the CLAUDE.md open issue and reference-doc path | CineTrack | Sync | Bug | P2 | XS |
| H7 | Security defaults: separate calendar token for iCal, hide `debug` in public `/info`, `REGISTRATION` off by default for this deployment | Floppy | API | Feature | P2 | S |
| H8 | Commit the CineTrack contract (cinetrack, sync, ensure, translate, scrobble, search/id, health) into `api/contracts/openapi.yaml` and add a diff test **[board: web-mirror OpenAPI is the rest]** | Floppy | API | Test | P2 | S |
| H9 | Upstream ports that affect CineTrack progress: unreleased/undated episodes counting toward completion (#1/#51/#67), falsely reopened shows (#11) | Floppy | Data | Bug | P2 | M |

## Milestone 1.0: quality bar

| # | Item | App | Area | Kind | Pri | Est |
|---|---|---|---|---|---|---|
| Q1 | Split `CineTrackViewModel` / `AppUiState` per screen, one screen per release (start with Settings, the 3k-line file) | CineTrack | UI | Feature | P2 | L |
| Q2 | Finish retiring the `CineTrackRepository` façade (docs/metadata-sync-migration.md) | CineTrack | Data | Feature | P2 | L |
| Q3 | Tests: ViewModel tests for the split screens, backup→restore round-trip, ICS export | CineTrack | Tests | Test | P2 | M |
| Q4 | Accessibility: content descriptions, TalkBack and font-scale pass, move the 3 hard-coded notification strings to strings.xml | CineTrack | UI | Feature | P2 | M |
| Q5 | Version catalog (`gradle/libs.versions.toml`) plus Renovate, with PrismalAGSL/Compose BOM grouped and pinned | CineTrack | Release | Feature | P3 | M |
| Q6 | Material 2 `pullrefresh` → Material 3 `PullToRefreshBox`; drop `androidx.compose.material` | CineTrack | UI | Feature | P3 | S |
| Q7 | Remove `SimklSyncWorker`/`SimklWorkScheduler` (keep a cancel-by-tag for old enqueued work) and the `runBlocking` in `SimklTokenManager` | CineTrack | Sync | Feature | P3 | S |
| Q8 | Commit a baseline profile (user generates it on device) and add a Roborazzi verify step in CI | CineTrack | Tests | Test | P3 | S |
| Q9 | README (still says 0.70) and a Floppy `docs/integrations/cinetrack.md` like the Nuvio guide | Both | Release | Feature | P3 | S |
| Q10 | Run `mcp_server` tests (51) in Floppy CI | Floppy | Tests | Test | P3 | XS |

## Features: CineTrack (server support already exists in most rows)

| # | Item | App | Area | Kind | Pri | Est | Notes |
|---|---|---|---|---|---|---|---|
| F1 | Personal ratings | Both | Sync | Feature | P1 | L | Op/queue exists; Floppy has `sync/ratings` (`sync_ratings_v1`); Simkl has ratings. Needs UI, a stored user rating, provider push |
| F2 | Widget: ✓ mark-watched action, more sizes, drop legacy XML layout | CineTrack | UI | Feature | P2 | M | Reuse the notification mark-watched receiver |
| F3 | Stats screen (time watched, genres, year review) | CineTrack | UI | Feature | P2 | M | Optionally fed by Floppy `statistics/overview` |
| F4 | Custom lists | Both | Data | Feature | P2 | L | Floppy lists/smart lists API exists |
| F5 | Onboarding without a personal TMDB key: TMDB read proxy through Floppy | Both | API | Feature | P2 | M | API contract change: both repos |
| F6 | Import from other trackers by triggering Floppy's importers (Trakt, IMDb, TV Time, Letterboxd via CSV) from CineTrack | Both | Data | Feature | P3 | M | Floppy already has ~30 importers |
| F7 | Sync conflicts screen using `sync/conflicts` | Both | Sync | Feature | P3 | M | Server detection exists |
| F8 | "Now playing" card from Floppy `playback/now-playing` (Plex/Jellyfin live) | CineTrack | UI | Feature | P3 | M | |
| F9 | Push without FCM: ntfy / UnifiedPush target via Floppy Apprise | Both | API | Feature | P3 | M | |
| F10 | Adaptive layouts (tablet/foldable list-detail) | CineTrack | UI | Feature | P3 | L | |
| F11 | Trakt as a full sync provider | CineTrack | Sync | Feature | P3 | XL | Decision needed |

## Features and services: Floppy

| # | Item | Area | Kind | Pri | Est | Notes |
|---|---|---|---|---|---|---|
| S1 | Crash report viewer: API list/export plus a small page (admin is off by default) | API | Feature | P2 | S | |
| S2 | Continuous SQLite backup with **Litestream** (or a verified off-box copy) and a restore drill; `pg_dump` job for Postgres | Data | Feature | P2 | M | Today only a nightly local snapshot |
| S3 | Error and metrics visibility: self-hosted **GlitchTip** (Sentry-compatible) and/or `django-prometheus`, with an Uptime Kuma check on `/health` | Release | Feature | P3 | M | Ties into the Funnel watchdog |
| S4 | Per-device OAuth token management (list/revoke CineTrack sign-ins) | API | Feature | P3 | M | |
| S5 | Background job for large ensure batches instead of 50-per-request retries | API | Feature | P3 | L | Only if H5 is not enough |
| S6 | Upstream sync round 2 (status-rule chain needs `entry_source` migration) | Data | Feature | P3 | L | Re-add the `upstream` remote first |
| S7 | Jellyfin/Emby importers (TODO in `_media_imports.py:186`) | Data | Feature | P3 | M | |

## Libraries and tools worth adding

| Tool | Where | Why | When |
|---|---|---|---|
| Renovate + version catalog | CineTrack | Inline versions today; grouping keeps Compose pinned to PrismalAGSL | Q5 |
| detekt (or ktlint) | CineTrack | No static analysis beyond Android lint | With Q1 |
| Kover | CineTrack | No coverage numbers to target the gaps | With Q3 |
| Litestream | Floppy | SQLite on a home server with no off-box backup | S2 |
| GlitchTip / django-prometheus / Uptime Kuma | Floppy | No error or metrics visibility | S3 |
| ntfy / UnifiedPush | Both | Push without Google services | F9 |
| Hilt or Koin | CineTrack | Not now: the manual `AppContainer` works; revisit only if Q1 needs it | Later |

## Decisions needed

1. Is a Play Store release a 1.0 goal? It changes the scope: the in-app updater uses `REQUEST_INSTALL_PACKAGES`, the bring-your-own TMDB key affects onboarding, and targetSdk would move to 36+.
2. Trakt as a full sync provider (F11): yes or not planned?
3. Normalize CRLF in Floppy (`serializers.py`, `fork_urls.py`, `media_list_filters.py`) with a `.gitattributes`? This cuts the upstream diff noise, but it is a one-time whole-file change.
4. Should 0.99.64 be hardening only (H1-H9), with F1 personal ratings as the first feature after it?
