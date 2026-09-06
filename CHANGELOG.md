# CineTrack 0.73 testing

- Progress episode/movie cards give the progress bar an independent 14dp right margin, keeping it separated from the watched check button.
- The text column's remaining 4dp right padding is removed, giving titles and details more room while the progress bar keeps its own spacing. Card size, check-button position and title line limit are preserved.
- Discover's upcoming movies now always use TMDB Discover with dates starting tomorrow, popularity-descending selection and a minimum of 8 votes, with or without configured content regions.
- Upcoming TV shows use the same minimum of 8 votes and popularity-descending selection. Region filtering, hidden-title exclusions and three-page fetching are retained.
- The combined upcoming row remains ordered by release date, soonest first, and continues to exclude already-released or undated entries. The new selection takes effect when Discover refreshes.
- TMDB popularity is now mapped as an optional DTO field for future use; it does not change the final chronological display order.

No new dependencies or database migration are introduced. Progress tab scroll preservation and the blur surfaces from 0.72 remain in place. GitHub Actions checks Android compilation, unit tests and the signed APK. Physical-device layout checks have not been performed in this workspace.
