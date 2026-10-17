---
title: "Roadmap audit prep: what is already resolved or tracked"
author: Joke9854
date: 2026-10-17
branch: beta-testing
cost: { minutes: 1, tokens: 1209 }
touches:
  - CLAUDE.md
---

## Decided
- Before a roadmap/audit, check the board first: `gh project item-list 2 --owner joke9854 --format json`. As of 2026-10-17 it holds 4 drafts (FloppySecondaryRoomIntegrationTest in CI, OpenAPI for web-mirror ops, N+1 in bootstrap ensure paths, record CI per deployed Floppy SHA) plus CineTrack#5.

## Tried and ruled out
- Treating the CLAUDE.md "Open issue" (preflight calls /user/preferences/) as open: CineTrack already calls GET api/v1/cinetrack/connection/ (FloppyApi.kt:63). The preferences endpoint is still declared in FloppyApi.kt but has no caller.

## Watch out
- The gh CLI IS installed now (C:\Program Files\GitHub CLI\gh); the older "no gh CLI" note in the releases learning is out of date.
- `trail map` fails with "no graph" until `trail build` has run in this checkout.
