---
title: "Impeccable document: DESIGN.md for CineTrack"
author: Joke9854
date: 2026-10-26
branch: beta-testing
cost: { minutes: 7, tokens: 64284 }
touches:
  - docs/ROADMAP.md
  - ".impeccable/design.json"
  - DESIGN.md
  - PRODUCT.md
---

## Decided
- DESIGN.md (root) + .impeccable/design.json now exist; supersedes the "no DESIGN.md yet" note from the init learning.
- North star "The Private Screening Room"; depth = soft painted lift + top-lit rim; components = quiet, tactile glass (user-chosen 2026-10-26).
- Tokens come from ui/theme (Palette, Theme, Type, Shapes, Spacing, Motion) and ui/components/GlassStyle.kt + GlassModifiers.kt; px in DESIGN.md means dp/sp.

## Tried and ruled out
- Light theme: the app is dark-only by design (darkColorScheme always, light system bars off).

## Watch out
- Sidecar tonal ramps are synthesized, not from code. Regenerate DESIGN.md and the sidecar together after theme token changes.
