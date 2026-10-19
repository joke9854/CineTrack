---
title: Q1 Notifications slice and Q3 ICS export test
author: Joke9854
date: 2026-10-19
branch: beta-testing
cost: { minutes: 6, tokens: 111952 }
touches:
  - app/src/main/java/com/cinetrack/data/repository/AppPreferences.kt
  - app/src/main/java/com/cinetrack/ui/CineTrackViewModel.kt
  - app/src/main/java/com/cinetrack/ui/screens/LibrarySettingsScreens.kt
  - app/src/main/java/com/cinetrack/ui/settings/SettingsViewModel.kt
  - app/src/test/java/com/cinetrack/ui/settings/SettingsViewModelTest.kt
  - app/src/main/java/com/cinetrack/domain/CalendarExport.kt
  - app/src/test/java/com/cinetrack/domain/CalendarExportTest.kt
---

## Decided
- Q1 fourth slice: the Notifications switches and quiet hours moved to SettingsViewModel (setNotification, setQuietHours, notificationSettings StateFlow). AppPreferences.notificationSettings is one Flow<NotificationSettings> (data class in ui/settings/SettingsViewModel.kt) read from DataStore.
- CineTrackViewModel no longer writes those AppUiState fields; its init collects the same preference flow into AppUiState, because notifiesByDefault, the reminder LaunchedEffect in CineTrackApp and the backup export still read state.notification*/quietHours*. This is the "main state observes preferences" step the first-slice learning asked for.
- Exclude specials and hidden upcoming stay on the main ViewModel (they refresh the progress cache); the Notifications page takes both ViewModels.
- Q3 ICS: the iCalendar text is now domain/CalendarExport.kt upcomingCalendarIcs(items, zone), byte-for-byte the old output; CalendarExportTest covers it.

## Tried and ruled out
- Doing the backup→restore round trip in the same session: no test builds CineTrackRepository yet, it needs most of CineTrackApplication's wiring, and restore writes watch history (exact-timestamp/rewatch invariants). Proposed as its own session.

## Watch out
- An override can't declare default arguments: AppPreferences.setQuietHours lost its "= null" defaults when it became an interface override; every caller already passed all three.
- The ICS output uses "\n" line endings and has no DTSTAMP, which RFC 5545 requires; kept as-is (behavior-preserving extraction), worth a separate fix.
