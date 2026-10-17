---
title: Releases run on beta-testing; main is a manual PR target
author: Joke9854
date: 2026-10-17
branch: beta-testing
cost: { minutes: 2, tokens: 45815 }
---

## Decided
- CineTrack releases are cut only on `beta-testing` (or `testing/*`): android.yml and release.yml trigger on those branches and a `[release]` commit. `main` has no CI or release workflow.
- Bringing `main` up to date = PR beta-testing -> main (merge commit). No new release build; the published beta stays the release.

## Tried and ruled out
- "Release on main" via a `[release]` commit on main: nothing would run.

## Watch out
- main was last merged at 0.84 (PR #3), ~350 commits behind as of 0.99.63.
- No `gh` CLI on this machine; open PRs in the browser pane (GitHub sign-in needed there).
