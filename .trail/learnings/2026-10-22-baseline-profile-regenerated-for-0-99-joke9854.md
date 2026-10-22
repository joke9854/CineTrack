---
title: Baseline profile regenerated for 0.99.74 (P1 splits + P2)
author: Joke9854
date: 2026-10-22
branch: beta-testing
cost: { minutes: 30, tokens: 50348 }
touches:
  - docs/ROADMAP.md
  - app/src/release/generated/baselineProfiles/baseline-prof.txt
  - app/src/release/generated/baselineProfiles/startup-prof.txt
  - app/src/main/java/com/cinetrack/ui/components/AdaptiveBackground.kt
  - app/src/main/java/com/cinetrack/ui/components/Components.kt
  - app/src/main/java/com/cinetrack/ui/components/DeferredModifiers.kt
  - app/src/main/java/com/cinetrack/ui/components/LiquidBottomNav.kt
  - app/src/main/java/com/cinetrack/ui/components/Popups.kt
  - app/src/main/java/com/cinetrack/ui/components/Skeletons.kt
  - app/src/main/java/com/cinetrack/ui/screens/CardAppearanceSettings.kt
---

## Decided
- Regenerated app/src/release/generated/baselineProfiles/ on 2026-10-22 with the README adb flow and the same journeys (startup; Progress -> Abbott Elementary, TMDB 125935). The user exported a backup and approved uninstalling com.cinetrack again.
## Watch out
- Merge the journey files with `awk 1 a b | tr -d '\r' | LC_ALL=C sort -u`, not `cat`: the pulled files end without a newline, so cat glued two rules into one line and compileReleaseArtProfile failed ("'-' is expected, but 'L' was read").
- Check every line matches ^[HSP]*L[^;]+;(->.+)?$ before committing; then run ./gradlew :app:compileReleaseArtProfile (alone, after --stop, or AAPT hits the Windows resource error).
