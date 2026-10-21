---
title: "Q2 slice 6b: AccountRepository (Floppy/Simkl sign-in) out of the facade"
author: Joke9854
date: 2026-10-21
branch: beta-testing
cost: { minutes: 30, tokens: 18349 }
touches:
  - app/src/main/java/com/cinetrack/CineTrackApplication.kt
  - app/src/main/java/com/cinetrack/data/repository/CineTrackRepository.kt
  - app/src/main/java/com/cinetrack/data/repository/RepositorySyncSettings.kt
  - app/src/main/java/com/cinetrack/ui/CineTrackApp.kt
  - app/src/main/java/com/cinetrack/ui/CineTrackViewModel.kt
  - app/src/main/java/com/cinetrack/ui/settings/SyncSettingsViewModel.kt
  - app/src/test/java/com/cinetrack/ui/settings/SyncSettingsViewModelTest.kt
  - app/src/main/java/com/cinetrack/data/repository/AccountRepository.kt
  - app/src/main/java/com/cinetrack/UpNextWidgetProvider.kt
  - app/src/main/java/com/cinetrack/data/repository/AppStateRepository.kt
---

## Decided
- data/repository/AccountRepository owns connectFloppy, discardFloppyCredential, floppySignInServer, FloppySignIn (now AccountRepository.FloppySignIn), completeFloppySignIn, disconnectFloppy, retryFloppyBootstrap, floppyUnplacedItems, completeLogin, refreshFloppyUsername, refreshSimklUsername and disconnectSimkl, moved verbatim (revoke order, setFloppyUsername(null), syncDao().delete("all") and onTokenChanged unchanged).
- AppContainer.accounts is built before the facade and gets the onTokenChanged, floppyRetryBootstrap and floppyUnplacedEpisodes lambdas that used to go to the facade. The facade lost onTokenChanged, floppySecondaryService, floppyOAuth, floppyRetryBootstrap and floppyUnplacedEpisodes; it keeps currentToken (appState fallback) and trackingProviderRegistry.
- RepositorySyncSettings(repository, accounts): account calls go to accounts, sync-operation calls stay on the facade. CineTrackViewModel takes accounts (after appState) for Simkl completeLogin and username.

## Tried and ruled out
- Running FloppySecondaryRoomIntegrationTest for this slice: it is instrumented and would run on the attached S23 (uninstalling the preview), and FloppySecondaryService did not change.

## Watch out
- installPreview can fail with BUILD FAILED and no code error when adb restarts mid-install (adb server 32 vs client 41); rerun before investigating, and don't filter the output down to the last few lines.
