# CineTrack 0.74 testing

- Fixed watched episodes briefly returning to Progress: local history, next-episode rows and retry writes commit together; background rebuilds rebase on current history, and UI snapshots preserve newer taps.
- Preserve concurrent local episode edits when committing a Simkl import.
- Restore Discover’s In arrivo rail by removing the vote-count floor for unreleased titles, and refresh an empty cached upcoming rail at startup. Region filters and chronological ordering remain active.
- Add consistent 24dp spacing between detail sections, with the watch/trailer actions grouped together.
- Compose major detail sections lazily, move detail processing to IO, load independent sections concurrently, reuse cast data, and use short fade transitions.
- Share a single viewport-sized live blur behind detail sections instead of stacking full-height and per-card blur effects. Actor/full-cast sheets retain their own live backdrop blur; existing device fallback remains.
- Enlarge cast cards and portraits. View all receives uncapped cast/crew, including aggregate TV credits across seasons and complete episode credits.

Validation: automated compilation, unit tests, release build and signature verification run in GitHub Actions. Device scrolling/frame-time verification remains pending; no measured frame-rate claim is made.
