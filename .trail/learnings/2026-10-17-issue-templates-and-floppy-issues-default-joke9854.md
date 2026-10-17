---
title: "Issue templates and Floppy issues: default-branch and fork limits"
author: Joke9854
date: 2026-10-17
branch: beta-testing
cost: { minutes: 4, tokens: 42919 }
touches:
  - ".github/ISSUE_TEMPLATE/bug.yml"
  - ".github/ISSUE_TEMPLATE/feature.yml"
  - ".github/ISSUE_TEMPLATE/test_task.yml"
---

## Decided
- Issue forms (Feature, Bug, Test task) live in CineTrack `.github/ISSUE_TEMPLATE/` and auto-add new issues to project joke9854/2. Milestones 0.99.64, 1.0 and Backlog exist in both repos; a `test` label exists in both.

## Tried and ruled out
- Issue templates on Floppy: GitHub only reads templates from the default branch, and Floppy's default is `latest`, which we never commit to. Templates on `CineTrack-API` would never show.

## Watch out
- CineTrack templates only show once `beta-testing` is merged to `main` (the default branch).
- joke9854/Floppy is a fork of dannyvfilms/Floppy, so Issues is off by default; `gh issue create` fails until it is turned on in repo settings.
- In PowerShell, `gh ... --jq` filters with `\(...)` string interpolation fail to parse; use ConvertFrom-Json. `gh project item-edit` by `--url` + `--field` + `--value` is simpler than node IDs.
