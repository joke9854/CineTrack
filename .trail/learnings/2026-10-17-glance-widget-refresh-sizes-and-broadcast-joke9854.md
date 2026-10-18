---
title: "Glance widget: refresh, sizes and broadcast limits (0.99.65)"
author: Joke9854
date: 2026-10-17
branch: beta-testing
cost: { minutes: 30, tokens: 73490 }
touches:
  - CHANGELOG.md
  - app/build.gradle.kts
  - app/src/main/java/com/cinetrack/UpNextWidgetProvider.kt
  - app/src/main/java/com/cinetrack/data/sync/ReleaseNotifier.kt
  - app/src/main/res/xml/up_next_widget_info.xml
  - app/src/main/res/drawable/widget_check_button.xml
  - app/src/test/java/com/cinetrack/UpNextWidgetRowsTest.kt
---

## Decided
- UpNextWidget uses SizeMode.Exact. Responsive with N sizes composed the content N times, and each refresh ran N list and poster loads (GC stalls, slow taps).
- Refreshing from outside the widget: UpNextWidgetProvider.refreshNow writes a new REFRESH_STAMP into the widget's Preferences state, then calls update(). provideContent reloads in LaunchedEffect(stamp). A plain update()/updateAll() only recomposes a live session with the data it loaded at start.
- MarkEpisodeWatchedReceiver (notification action and widget check) only marks and redraws. The next-episode fetch (refreshProgressCache) runs in WatchedProgressRefreshWorker, unique work with APPEND_OR_REPLACE.
- Widget diagnostics: logcat tag CineTrackWidget, with mark/refresh/load showing episode coordinates only.

## Tried and ruled out
- Doing the network progress refresh inside goAsync(): a widget tap is a foreground broadcast with a ~10s limit. ActivityManager logged the overrun and the process could be killed, so later taps showed nothing until the app opened.
- Wrapping the widget load in runCatching: it swallowed CancellationException and drew an empty list. Rethrow cancellation.

## Watch out
- Samsung One UI: a 4x2 widget is ~2 cells tall. Use height for the row count (1 cell = 1 row), and width only to hide posters.
- loadCachedState() takes ~3-5s on the user's library, so the widget redraws a few seconds after a tap.
