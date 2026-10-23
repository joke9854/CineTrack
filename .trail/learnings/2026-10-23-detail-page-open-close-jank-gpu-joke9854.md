---
title: "Detail page open/close jank: GPU-bound live effects and first-use shader compiles"
author: Joke9854
date: 2026-10-23
branch: beta-testing
cost: { minutes: 29, tokens: 47089 }
touches:
  - app/src/main/java/com/cinetrack/ui/CineTrackApp.kt
  - app/src/main/java/com/cinetrack/ui/components/AdaptiveBackground.kt
  - app/src/main/java/com/cinetrack/ui/components/GlassStyle.kt
  - app/src/main/java/com/cinetrack/ui/components/LiquidBottomNav.kt
  - app/src/main/java/com/cinetrack/ui/components/Popups.kt
  - app/src/main/java/com/cinetrack/ui/screens/CardAppearanceSettings.kt
  - app/src/main/java/com/cinetrack/ui/screens/DetailEpisodes.kt
  - app/src/main/java/com/cinetrack/ui/screens/DetailScreens.kt
  - app/src/main/java/com/cinetrack/ui/screens/DetailSections.kt
  - app/src/main/java/com/cinetrack/ui/screens/DiscoverScreens.kt
---

## Decided
- The detail/episode header art is blurred and faded once at decode (BlurredArtTransformation in AdaptiveBackground.kt: 160 px wide, 3 box-blur passes radius 3, 0.72 opacity, fade from 55% to the bottom) and drawn as a plain image. The live Modifier.blur(18.dp), the fadeOutBottom DstIn mask layer and AdaptiveBackground's blurBackdrop haze (DetailBackdropStyle) are gone.
- Tabs and pushed pages slide as whole pages with no fade (Motion.tabEnter/tabExit/Push*/Pop*): fading two full 1440p pages forced offscreen layers under the nav glass.
- Result on the S23 (preview): warm detail open/close janky frames ~30% -> 9.5%; first open after launch no longer has 130-280 ms frames.
## Tried and ruled out
- The UI thread was never the problem during detail transitions (1-3 ms); the GPU was (20-37 ms per frame).
- Turning off the liquid-glass controls or the DetailTopBar header haze made no measurable difference to the first-open stall.
- debug.hwui.skia_atrace_enabled adds no shader-compile names; a stall shows only as one long FillRectOp under "flush layers".
## Watch out
- A 100 ms+ FillRectOp inside "flush layers" on the first use in a fresh process = a new GPU program (new blur radius or blend/shader combo in an offscreen layer). Avoid new DstIn masks or blur radii in transition paths; bake them into bitmaps instead.
- Header art arrives with loadDetails (list cards may lack backdropUrl), so anything drawn only with the art lands mid-flight.
- Measure with framestats (GpuCompleted - IssueDrawCommandsStart), not totals: the 1440p page sits near the 8.3 ms budget even when settled.
- installPreview on this machine intermittently fails in lintVitalAnalyzePreview with a FileSystemException in .gradle/caches: ./gradlew --stop and rerun.
