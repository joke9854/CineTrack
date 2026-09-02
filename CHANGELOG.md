# CineTrack 0.69 testing

- Compose domain models are explicitly immutable and high-frequency synchronization progress is isolated from the main app state, reducing unnecessary whole-screen recompositions.
- Startup credential and metadata preference reads now run in parallel, while background-sync scheduling starts asynchronously after the first app setup.
- Progress-cache episode selection moved behind a dedicated repository component and indexes watched history once per show instead of rescanning the full history for every tracked series.
- TMDB, MDBList and Simkl clients share one OkHttp connection pool and dispatcher; the TMDB credential pattern is compiled once.
- Progress-derived lists and statistics are memoized, lazy lists use stable keys, and Coil now uses explicit bounded memory and disk caches.
