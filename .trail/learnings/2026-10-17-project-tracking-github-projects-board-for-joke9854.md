---
title: "Project tracking: GitHub Projects board for CineTrack + Floppy"
author: Joke9854
date: 2026-10-17
branch: beta-testing
cost: { minutes: 19, tokens: 32045 }
---

## Decided
- Issue tracking uses GitHub Issues + one shared GitHub Project: "CineTrack + Floppy" (https://github.com/users/joke9854/projects/2), linked to joke9854/CineTrack and joke9854/Floppy.
- Project fields: Status (Todo / In Progress / Done), Priority (P0-P3), Target version (text), Estimate (number), plus the built-in Repository field.
- Both repos have the same extra labels: area: sync, area: api, area: ui, area: data, area: release, cross-repo, crash. Use cross-repo for API-contract work that touches both repos.

## Tried and ruled out
- A custom "Repo" field: GitHub rejects "Repo" as a reserved name. Use the built-in Repository field instead.

## Watch out
- gh needs the `project` scope (`gh auth refresh -s project`); repo scope alone can't see or edit projects.
- gh can't create Board/Roadmap views; add them in the web UI.
