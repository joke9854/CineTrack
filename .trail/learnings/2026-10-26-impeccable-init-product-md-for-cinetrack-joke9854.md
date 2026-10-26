---
title: "Impeccable init: PRODUCT.md for CineTrack"
author: Joke9854
date: 2026-10-26
branch: beta-testing
cost: { minutes: 6, tokens: 57642 }
touches:
  - docs/ROADMAP.md
  - PRODUCT.md
  - CLAUDE.md
---

## Decided
- PRODUCT.md at repo root (impeccable product-schema 1), platform android. Primary users: self-hosters running Floppy; Simkl users secondary.
- Positioning pillars (user-confirmed 2026-10-26): self-hosted ownership, exact sync fidelity, next-episode flow, premium native polish.
- Accessibility: Android baseline (TalkBack labels, 48dp targets, font scaling, AA contrast), no formal WCAG gate.

## Tried and ruled out
- Live mode and comp-first buildPath: not applicable (native app, no image generation tool).

## Watch out
- No DESIGN.md yet; visual rules (Progress card, Haze glass on controls only) live in CLAUDE.md until `/impeccable document` runs.
- README still lists Trakt as a data source; it was removed in 0.99.78 (roadmap Q9).
