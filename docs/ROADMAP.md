# CineTrack + Floppy roadmap

Audit: 2026-10-17. CineTrack `beta-testing` @ c897ead (0.99.63, versionCode 162); Floppy `CineTrack-API` @ c2bfb856.
Plan agreed with the user: 2026-10-17. Board: https://github.com/users/joke9854/projects/2.
Board Estimate is in points: XS=1, S=2, M=3, L=5, XL=8.

## Rules for this plan
- Ship one theme per release so each release is easy to test. Build and install once per release.
- Show design changes on the phone before committing them.
- The Floppy track deploys to the server and doesn't need a CineTrack release, except for API contract changes, which ship in both repos together.

## CineTrack releases

| Release | Item | Area | Kind | Pri | Est | How to test |
|---|---|---|---|---|---|---|
| 0.99.64 | Progress cards: swipe **left** to mark watched (today it's swipe right, ProgressScreen.kt:818). The card layout stays the same | UI | Feature | P1 | S | Swipe on the phone |
| 0.99.64 | Q6 Material 3 `PullToRefreshBox`, drop `androidx.compose.material` (Material 2 `pullrefresh`) | UI | Feature | P3 | S | Pull-to-refresh on every list |
| 0.99.64 | H6 Confirm the preflight fix on the S23 (`cinetrack/connection`), remove the unused `user/preferences` declaration, update CLAUDE.md's open issue and reference-doc path | Sync | Bug | P2 | XS | Floppy connect and first sync |
| 0.99.65 | F2 Widget: mark-watched action (reuse the notification receiver), more sizes, remove the legacy `up_next_widget.xml` | UI | Feature | P1 | M | Home-screen widget |
| 0.99.66 | Q5 Version catalog (`gradle/libs.versions.toml`) plus Renovate, with the Compose BOM grouped and pinned to PrismalAGSL | Release | Feature | P3 | M | App starts and syncs as before |
| 0.99.67+ | Q1 Split `CineTrackViewModel` / `AppUiState` **one screen per release**, starting with Settings | UI | Feature | P2 | L | Only that screen |
| 0.99.67+ | Q2 Retire the `CineTrackRepository` façade alongside Q1 (docs/metadata-sync-migration.md) | Data | Feature | P2 | L | Only that screen |
| 0.99.67+ | Q3 Tests for each split screen's ViewModel, plus a backup→restore round trip and ICS export | Tests | Test | P2 | M | CI |
| Later | F7 Sync conflicts screen using Floppy `sync/conflicts` (both repos) | Sync | Feature | P3 | M | Make a conflict on purpose |
| Anytime | Q8 Commit a baseline profile (the user generates it) and add a Roborazzi verify step in CI | Tests | Test | P3 | S | CI |
| Anytime | Q9 Refresh the README (it still says 0.70) and add a Floppy `docs/integrations/cinetrack.md` | Release | Feature | P3 | S | none |

## Floppy batches (server deploys, in this order)

| Batch | Item | Area | Kind | Pri | Est | Notes |
|---|---|---|---|---|---|---|
| A | H1 On `CineTrack-API` pushes, run every CineTrack test module (test_fork_tracking, sync_writes, sync_items, episode_translate, crash_reports, oauth_app_signin, fork_scrobble) plus lint | Tests | Test | P1 | S | Goes first so later batches are tested |
| A | Normalize line endings: `.gitattributes`, convert `serializers.py`, `fork_urls.py` and `media_list_filters.py` once | Release | Feature | P2 | XS | User approved this one-time whole-file change |
| A | Q10 Run the `mcp_server` tests (51) in CI | Tests | Test | P3 | XS | |
| A | H4 One source for the CineTrack capability flags (`/info` and `cinetrack/connection` differ), plus a test | API | Bug | P2 | XS | The client reads `/info`, so no live bug |
| B | H3 Build and publish our own image from `CineTrack-API` (ghcr.io/joke9854/floppy), point compose at it, and record the image/CI SHA per deploy (existing board item) | Release | Feature | P1 | M | `docker-image.yml` builds `dannyvfilms/floppy` only |
| C | H7 Separate calendar token for iCal, hide `debug` in public `/info`, `REGISTRATION` off by default | API | Feature | P2 | S | The iCal URL changes once |
| C | H8 Commit the CineTrack contract (cinetrack, sync, ensure, translate, scrobble, search/id, health) into `api/contracts/openapi.yaml`, plus a diff test | API | Test | P2 | S | |
| D | H9 Upstream fixes: unaired/undated episodes count toward completion (#1/#51/#67), falsely reopened shows (#11) | Data | Bug | P2 | M | Check CineTrack progress afterwards |
| E | H5 Batch the bootstrap ensure writes and move SQLite lock backoff out of the gunicorn thread (existing N+1 board item) | API | Feature | P2 | M | No contract change |
| E | S5 Background job for large ensure batches | API | Feature | P3 | L | API contract change: ships with a CineTrack release |
| F | S1 Crash report viewer: list/export API plus a small page | Crash | Feature | P2 | S | |
| F | S4 Manage signed-in devices (list/revoke OAuth tokens) | API | Feature | P3 | M | |
| G | S3 Error and uptime monitoring: GlitchTip (Sentry-compatible) and/or django-prometheus, plus an Uptime Kuma check on `/health` | Release | Feature | P3 | M | Pairs with the Funnel watchdog |

## Decided 2026-10-17
- Play Store: later. 1.0 stays a GitHub release, so the in-app updater and the bring-your-own TMDB key stay as they are.
- Trakt as a full sync provider: not planned. Trakt stays calendar-only.
- First feature after the quick wins: F2 Widget.
- Not picked, kept here for reference:
  - H2 (all instrumented tests in CI). The existing board item for `FloppySecondaryRoomIntegrationTest` stays as is.
  - Q4 accessibility, Q7 removing the old Simkl worker.
  - F1 ratings, F4 lists, F5 TMDB proxy, F6 imports, F8 now playing, F9 push, F10 tablets.
  - S2 Litestream backup, S6 upstream sync round 2, S7 Jellyfin/Emby importers.

## Decided 2026-10-18
- F3 Stats screen: dropped. The user likes the statistics where and how they are in Progress.
- Character pictures: dropped. Simkl's API (OpenAPI spec at api.simkl.org) has no cast or character data, and TMDB has actor photos only.

## State in one screen (audit summary)
**CineTrack:**
- 37k lines of Kotlin in `:app`, plus `:baselineprofile`. Room v14 with 11 migrations. 416 unit tests and 39 instrumented tests.
- Complete: Discover, Progress, Simkl two-way sync, Floppy main/secondary, backup/restore, ICS export, notifications.
- Partial: stats inside Progress; the widget only opens the app.
- Stub: personal ratings.
- Debt: `CineTrackViewModel` (2.1k lines, passed to 29 screens), `CineTrackRepository` (3.8k lines), no version catalog, the CI emulator job runs only the migration test.

**Floppy fork:**
- Django 5.2 LTS, DRF, DOT 3.4.1, Celery/Redis, SQLite or Postgres. 70 commits ahead of `latest`.
- The CineTrack/sync API is complete and has drf-spectacular annotations.
- Risks: see batches A-E. Fork migrations app 0186-0187, users 0134-0135 and integrations 0048-0049 will collide with upstream numbering.
