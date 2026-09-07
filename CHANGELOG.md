# CineTrack 0.76 testing

- Add a Sync operations screen in Settings with pending writes, failed actions, individual retry controls and explicit local/Simkl conflict resolution.
- Persist the synchronization queue in Room so offline writes and failures survive restarts, with a data-preserving database 5→6 migration.
- Add notification permission guidance, configurable quiet hours, scheduled upcoming-release reminders and sync-failure notifications that open the affected operations.
- Harden credentials, backups and updates: Android Keystore-backed API keys, sensitive-log redaction, bounded backup imports, rotating automatic backups and mandatory APK checksum verification.
- Make startup non-blocking, load detail seasons concurrently, preserve cached upcoming items after partial network failures and avoid unnecessary full-state rebuilds.
- Bound detail and episode-title caches, invalidate language-dependent entries correctly, and load the Up Next widget through a focused database query.
- Run compilation, unit tests, lint, Baseline Profile generator compilation and Room migration checks in CI before publishing.

Saved TMDB and MDBList keys remain on the device during this update. App-specific Baseline Profile generation and final notification timing checks still require an Android device run.
