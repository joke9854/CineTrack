# CineTrack 0.82

- Open TMDB, MDBList, and Simkl directly by tapping their logos in About.
- Match episode notifications to the requested compact format: show title plus `SxxExx is now available!`.
- Preserve Simkl release timestamps and timezone offsets, prevent TMDB date-only data from replacing them, and schedule availability by the actual release time.
- Align episode identity text with the rest of the detail page.
- Make the season sheet scroll and dismiss like the actor sheet, and return to the season sheet after closing an actor opened from it.
- Clear stale search results when leaving Search and add removable, persistent search history.
- Scroll Library to the top when its active type or status pill is tapped again, matching Progress.
- Make upcoming-item hiding discoverable with a visible action, confirmation popup, and per-item restore controls in Settings.
- Apply all non-empty English and Italian updates from the supplied translation workbook while preserving existing text for empty cells.

Debug and release APK builds, unit tests, lint, baseline-profile compilation, resource checks, database migration checks, and APK signature verification passed. Device-level visual verification remains necessary.
