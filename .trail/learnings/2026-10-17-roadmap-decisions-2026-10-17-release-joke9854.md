---
title: "Roadmap decisions 2026-10-17: release batches agreed with the user"
author: Joke9854
date: 2026-10-17
branch: beta-testing
cost: { minutes: 13, tokens: 59906 }
touches:
  - docs/ROADMAP.md
---

## Decided
- docs/ROADMAP.md is the agreed plan: one theme per CineTrack release (0.99.64 swipe-left on Progress + Material 3 pull-to-refresh + preflight check, 0.99.65 widget, 0.99.66 stats, 0.99.67 character pictures, 0.99.68 version catalog, 0.99.69+ ViewModel split one screen per release) and Floppy batches A-G (CI and line endings first, own Docker image next).
- The user wants work split into small, separately testable releases, not one big drop.
- Play Store later (keep the GitHub updater and bring-your-own TMDB key). Trakt stays calendar-only, not a sync provider.
- Normalizing Floppy CRLF files once with a .gitattributes is approved (batch A), despite the whole-file diff.
- Progress card swipe-to-watch moves from right to left. Only the gesture changes; the approved card layout stays.

## Tried and ruled out
- Simkl for character names: TMDB credits already give names, shown under each cast member (DetailScreens.kt:1718). The request is character pictures; check that Simkl's API returns them before building.
- H2 (all instrumented tests in CI) was not picked. The existing FloppySecondaryRoomIntegrationTest board item stays untouched.

## Watch out
- Board Target version holds CineTrack versions (0.99.64) or Floppy batch names (Floppy A). Estimate is points: XS=1, S=2, M=3, L=5, XL=8.
