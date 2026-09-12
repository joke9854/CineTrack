# CineTrack 0.92 beta

- Preserve the existing local-library constructor contract while retaining provider-target snapshots for new mutations.

Verification is pending in the release environment.

# CineTrack 0.91 beta

- Clear legacy Simkl sync state when disconnecting or switching accounts so a new account syncs immediately without affecting local library data.

Verification is pending in the release environment.

# CineTrack 0.90 beta

- Make provider deliveries generation-aware so a completed acknowledgement cannot suppress a later edit.
- Delete delivery rows transactionally with completed or superseded operations and guard terminal state transitions.
- Freeze provider targets at mutation time, batch episode writes into one dispatch, and invalidate Simkl sync state on account changes.
- Use the configured MAIN provider for queue repair and cadence decisions, including Floppy’s first-sync behavior.

Verification is pending in the release environment.

# CineTrack 0.89 beta

- Add provider-specific delivery state so Simkl and future Floppy deliveries acknowledge and retry independently.
- Add non-destructive Room 8→9 migration with delivery indexes and deterministic legacy queue backfill hooks.
- Make foreground/background cadence provider-neutral and redact both API-key query forms plus credential headers.
- Isolate the Simkl full-sync implementation behind a dedicated sync engine boundary.

Verification is pending in the release environment.

# CineTrack 0.88 beta

- Preserve the coupled movie-watched change when a local mark-watched action is represented by the legacy completed library operation.
- Keep ordinary library-status edits field-specific so they do not suppress unrelated MAIN watch-history changes.

Verification is pending in the release environment.

# CineTrack 0.87 beta

- Repair remote-only MAIN changes so Simkl updates are adopted locally without restoring stale CineTrack values.
- Validate current field-level local intent before protecting library, movie history, or episode changes.
- Remove stale and duplicate reconciliation queue rows, preserve retryable conflicts, and require exact provider acknowledgements.
- Scope synchronization baselines by MAIN provider and advance them only from confirmed agreement.
- Repair pending, failed, and conflict titles from stored media and episode metadata.
- Add regression coverage for stale queues, field-specific protection, remote-only changes, and synchronization decisions.

Verification is pending in the release environment.

# CineTrack 0.86 beta

- Persist MAIN synchronization baselines for deterministic three-way reconciliation.
- Resolve remote-only changes without false conflicts and make KEEP LOCAL / USE REMOTE durable and provider-neutral.
- Add localized, provider-aware conflict cards with typed episode context and retryable resolution operations.
- Add schema migrations and regression coverage for library, movie, and episode reconciliation.

Verification is pending in the release environment.

# CineTrack 0.85 beta

- Introduce provider-neutral tracking snapshots and deterministic reconciliation for library, movie, and episode history.
- Protect pending local changes while synchronizing and surface explicit conflicts for review.
- Add focused discovery, people, watch-provider, and media data-source boundaries while keeping the legacy facade as a compatibility bridge.
- Expand Simkl synchronization to pull complete history and reconcile watched movie state alongside library and episode state.

Verification is pending in the release environment; local verification is currently blocked by Android SDK permissions.

# CineTrack 0.84

- Give the Coming Soon hide action a compact 26 dp rounded-square glass background while preserving its 16 dp icon and a comfortable touch target.
- Keep notification titles and message bodies in the same hierarchy across scheduled, immediate, provider, and synchronization notifications.
- Fully localize the synchronization card's relative status in Italian, including never-synced, just-now, minutes, hours, and days states.

Debug and release APK builds, unit tests, lint, baseline-profile compilation, resource checks, database migration checks, and APK signature verification passed. Device-level visual verification remains necessary.

# CineTrack 0.83

- Show the exact watched date and time beneath an episode description when its info panel is expanded.
- Reduce the Coming Soon hide-control container from 36 dp to 30 dp while keeping its icon unchanged.
- Add cohesive Movies & TV and People pills to Discover search.
- Search TMDB people with profile cards and open results in the existing actor detail popup, including their credits.
- Clear stale results while switching queries and show a proper empty state after searches complete.

Debug and release APK builds, unit tests, lint, baseline-profile compilation, resource checks, database migration checks, and APK signature verification passed. Device-level visual verification remains necessary.

