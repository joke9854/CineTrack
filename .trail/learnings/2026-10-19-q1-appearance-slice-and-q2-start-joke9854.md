---
title: "Q1 Appearance slice and Q2 start: settings writes skip the façade"
author: Joke9854
date: 2026-10-19
branch: beta-testing
cost: { minutes: 6, tokens: 84027 }
touches:
  - app/src/main/java/com/cinetrack/data/repository/AppPreferences.kt
  - app/src/main/java/com/cinetrack/data/repository/CineTrackRepository.kt
  - app/src/main/java/com/cinetrack/ui/CineTrackApp.kt
  - app/src/main/java/com/cinetrack/ui/CineTrackViewModel.kt
  - app/src/main/java/com/cinetrack/ui/screens/CardAppearanceSettings.kt
  - app/src/main/java/com/cinetrack/ui/screens/LibrarySettingsScreens.kt
  - app/src/main/java/com/cinetrack/ui/settings/SettingsViewModel.kt
  - app/src/test/java/com/cinetrack/ui/settings/SettingsViewModelTest.kt
---

## Decided
- Q1 fifth slice: the Appearance page (accent, poster accent, Discover layout, cover, poster format/size, grid columns, cast photo shape) is owned by SettingsViewModel: `appearance` StateFlow from AppPreferences.appearanceSettings (one Flow<AppearanceSettings>, normalized like the per-field flows) plus eight setters that call the existing AppPreferences setters, now SettingsPreferences overrides.
- CineTrackViewModel no longer writes those AppUiState fields; its init mirrors appearanceSettings into AppUiState because every other screen reads state.heroLayout/posterFormat/cardDensity/uiAccent. castPhotoShape left the main ViewModel entirely; CineTrackApp reads it from settingsViewModel.appearance.
- Season picker stays on the main ViewModel: it's set from the show page, not from Appearance.
- Q2 start: removed CineTrackRepository's setUiAccent and setIntroductionCompleted passthroughs; callers use preferences directly (restore uses preferences.setUiAccent).

## Tried and ruled out
- One "save the whole AppearanceSettings" write: two quick taps before the flow round-trips would let the second overwrite the first. Per-field setters keep each DataStore edit atomic.

## Watch out
- Appearance changes now reach AppUiState through a DataStore round trip instead of a synchronous _state copy; the page itself reads SettingsViewModel.appearance, so its choices update directly.
