# CineTrack 0.71 testing

- Tapping any Progress tab (In progress, Calendar, History or Statistics) returns its list to the top, including a second tap on the selected tab.
- Remaining time / Up next now sits directly above the progress bar in episode and movie cards.
- Progress titles gain 40dp of text space by reducing redundant end padding from 52dp to 12dp; card size, title line limit and check-button position are preserved.
- Season headers no longer show an expand/collapse icon. Tapping the header still opens/closes the season; the current-season default behavior and watched button are preserved.
- Shared bodySmall text increases from 12sp to 13sp, including plot summaries and secondary labels.
- Library filter keys use the stable status value and do not change with translated labels.
- Room upgrades from version 4 to 5 in one data-preserving migration, adding indexes on watch_history(watchedAt), playback(updatedAt), and user_media_state(mediaType, status). The existing episode-history index is retained. Local substring search remains unchanged; no FTS or additional dependencies were introduced.
- First glass expansion stage: the main movie/TV detail sheet uses the navigation blur material on Android 12+ devices that are not low-RAM. Its source captures only the page background so foreground text stays sharp. Other devices retain the previous translucent sheet.

## Validation and staged follow-up

The index migration is checked against SQLite with 6,000 representative rows for data preservation, unchanged table definitions, index definitions and ordered-query plans. Both CI workflows run this check alongside their Android compilation/unit-test gates.

The requested physical-device scroll gate remains open for the new detail-sheet blur. Actor popup/card, Seasons and episodes, Where to watch, and Useful information blur will be extended one surface at a time after that check, as requested. Visual checks for short/long Progress titles and the shared 13sp text also need a device. App-specific Baseline Profile generation remains a separate pending device task.
