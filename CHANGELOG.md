# CineTrack 0.75 testing

- Add Popular movies and Popular TV rows with full catalogue grids, English/Italian titles and the configured origin-country filter. Popular has no date restriction.
- Preserve real Trending order under origin-country filters: fetch two global pages, keep matching origins, then append Popular results only if fewer than ten remain. Deduplicate and respect hidden titles. Global Trending keeps its existing three pages.
- Load Discover pages concurrently with at most six HTTP requests at once. Reuse Popular results for fallback and finish networking before opening the database transaction.
- Restore the original slide-and-fade detail-page enter/exit animations, retaining lazy sections and shared viewport blur.
- Restore the 12dp ratings-to-description gap, retaining 24dp spacing between major sections.
- Align section headings and body text to common gutters, including top-aligned Useful information labels when values wrap.
- Use one larger cast/crew card in the row and the complete View all grid: 132dp width, a full-width 160dp rectangular photo, and equal name/role line slots. Photos fill their panels without circular clipping or stretching.

Validation: regression tests cover regional/global request counts, ranking, fallback, deduplication, hidden titles and concurrent page loading. Android compilation, unit tests, signed release and signature checks run in CI. Live network timing and device rendering remain unmeasured.
