# CineTrack 0.79

- Unify Discover and Library poster cards with normal-case, two-line titles, consistent badge placement, proportional artwork, and explicit bulk-selection checkmarks.
- Refine Discover with a larger search field, readable two-line hero titles, actual TMDB taglines, accurate library-status icons, a steadier background, and matching three-column See all grids. Pause automatic carousel advancement during interaction or an open status menu.
- Simplify Library controls, unify pill selectors, show the active sort and title count, move bulk editing beside the sort summary, and distinguish empty filtered results. Remove placeholder watching progress and the redundant status-distribution strip.
- Align thicker, shorter Progress bars with watched controls, balance their edge spacing, reduce Calendar date insets, and replace the rectangular sync-status press effect with a rounded glass highlight.
- Give TMDB language, country, and time-zone preferences searchable selection sheets matching the streaming-country picker.
- Add season information sheets with TMDB description, rating, release date, episode count, average episode runtime, and season-specific cast. Load on demand with caching, bounded requests, retry and empty states.
- Use neutral detail-page watched, trailer, and library controls while retaining green watched confirmation. Improve provider-label spacing and useful-information alignment.
- Publish this as a regular GitHub release. Keep the testing channel tag so existing installations can discover the update.

No database schema changes or new dependencies. Debug compilation, unit tests, lint, baseline-profile compilation and database checks passed. Device-level visual checks remain necessary.
