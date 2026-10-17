---
title: "Board drafts: tracking CineTrack and Floppy work without repo issues"
author: Joke9854
date: 2026-10-17
branch: beta-testing
cost: { minutes: 4, tokens: 10837 }
---

## Decided
- Work is tracked as draft items directly on project joke9854/2 ("CineTrack + Floppy") for both repos, not as repo issues. Drafts have no labels or milestones, so the board's own fields carry that: App (CineTrack / Floppy / Both), Area (Sync / API / UI / Data / Release / Tests / Crash), Kind (Feature / Bug / Test), Priority (P0-P3), Status, Target version (text, e.g. 0.99.64 or Backlog), Estimate.
- CineTrack#5 stays a real issue; a draft can be converted to a repo issue later if needed.

## Tried and ruled out
- Floppy repo issues: the user decided against enabling Issues on the Floppy fork.
- Field names "Repo" and "Type" are reserved by GitHub Projects; we use "App" and "Kind".

## Watch out
- `gh project item-edit --field/--value` only works with `--url` (real issues). For drafts, pass `--project-id`, `--id`, `--field-id` and `--single-select-option-id` (or `--text`).
- In Windows PowerShell 5.1, double quotes inside a `--body` argument get mangled and make `gh project item-create` fail; avoid them in draft bodies.
- Issue templates don't apply to drafts; follow the same sections (Goal, Sync/invariant impact, Acceptance criteria, Tests) by hand.
