# CineTrack 0.72 testing

- Progress tabs preserve their individual scroll positions when switching between In progress, Calendar, History and Statistics. Only tapping the currently selected tab returns that list to the top.
- Episode/movie cards gain another 12dp of text width through smaller side padding. The title keeps its existing size and one-line limit, and the check button stays in place.
- Live glass blur is now enabled throughout all scheduled detail surfaces, without waiting for the earlier device-scroll gate.

## Blur locations

- Movie and TV detail panels (retained from 0.71).
- Episode detail panels.
- Actor/crew cards in the cast rail on movie, TV and episode detail pages.
- Actor detail popup, including its handle and filmography cards.
- Full cast popup and its person cards.
- Seasons and episodes: season cards and expanded episode cards.
- Where to watch section.
- Useful information on movie, TV and episode detail pages.
- Bottom navigation (existing blur retained).

All these surfaces use the existing 16dp blur material on Android 12+ devices that are not classified as low-RAM. Unsupported devices retain their previous translucent layouts. Detail sections sample the page backdrop; actor popups use a separate capture of the underlying page, so text and images inside the popup remain sharp. No new dependencies or database migration are introduced in this release.

Compilation and release/unit-test checks run in GitHub Actions. Physical-device appearance and frame pacing have not been measured in this workspace; rollout no longer waits for that check, as requested.
