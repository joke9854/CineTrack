---
title: "0.99.64: preferences fallback is live, crash outbox needs API 25-safe rename"
author: Joke9854
date: 2026-10-17
branch: beta-testing
cost: { minutes: 20, tokens: 24114 }
touches:
  - CHANGELOG.md
  - app/build.gradle.kts
  - app/src/main/java/com/cinetrack/crash/CrashOutbox.kt
  - app/src/main/java/com/cinetrack/ui/screens/ProgressScreen.kt
  - app/src/main/java/com/cinetrack/ui/screens/ScreenUtilities.kt
---

## Decided
- GET /user/preferences/ in FloppyApi.legacyPreferences is NOT dead: FloppyRemoteDataSource.authenticatedAccount falls back to it only when cinetrack/connection returns 404 (older Floppy). Keep it. This corrects the earlier audit-prep learning that said it had no caller.
- CrashOutbox writes tmp then File.renameTo; java.nio.file.Files.move needs API 26 and failed lint (minSdk 25).
- Material 3 pull-to-refresh: Modifier.pullToRefresh(threshold = 132.dp) sets the trigger distance; PullToRefreshDefaults.Indicator(maxDistance = 62.dp) sets where the spinner rests (the param is maxDistance, not threshold, in material3 1.4).

## Tried and ruled out
- Plain File.renameTo onto an existing file: it fails on Windows (JVM unit tests) while Android replaces atomically. Delete the target and retry only when the first rename fails.

## Watch out
- lintAnalyzeDebugUnitTest can crash its worker in a long combined run; rerunning lint alone passed. Treat that as an infra failure, not code.
- Commit 006496e (crash reports) went in with a red lint; check CI on a commit before building on it.
