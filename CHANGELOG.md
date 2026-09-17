# CineTrack 0.99.22 beta

- Added capability-gated idempotent Floppy episode-event bootstrap with exact watched timestamps, durable retry identity, and safe rewatch preservation.
- Added Floppy fork capability discovery and database-level episode pagination while retaining the stock-server fallback path.

# CineTrack 0.99.19 beta

- Made Floppy bootstrap delivery durable at each confirmed same-season transport unit, so later failures cannot discard earlier acknowledgements.
- Prevented cross-season/gapped bulk ranges from creating implicit episode watches, added safe bulk-task conflict diagnostics, and aligned Sync Operations failure counts with managed bootstrap state.

# CineTrack 0.99.18 beta

- Restored cold-cache Progress population by deriving targeted season loads from durable watch history rather than DetailScreen-only season metadata.
- Made normal Progress eligibility require a known released episode, revalidating stale durable Up Next rows instead of allowing unknown/future metadata to surface a card.
- Kept Floppy bulk episode ranges within one season, report safe 409 bulk context, and derive bootstrap completion from durable delivery acknowledgements.

# CineTrack 0.99.17 beta

- Restored watch-history-first Progress membership with real partial-playback precedence and release-aware episode eligibility.
- Added Floppy bootstrap bulk episode transport, exact-generation batch acknowledgement, focused remote-index preparation, and unique run-scoped progress observation.

# CineTrack 0.99.16 beta

- Restored watch-history-first Progress selection so older seasons, gaps, active playback, specials, and missing latest metadata cannot be displaced by latest-air data.
- Added current-generation Floppy bootstrap queue queries and a bootstrap-only Room materialization path, reducing repeated global queue maintenance for large plans.
- Stabilized WorkManager bootstrap progress identity and observation so retries and historical jobs cannot move a running counter backward.

# CineTrack 0.99.15 beta

- Corrected Progress episode selection for out-of-order history, active playback precedence, sparse season caches, and distant future schedules.
- Bulk-materialized Floppy bootstrap deliveries in bounded Room chunks with real preparation progress and same-plan restart safety.
- Retired current-generation synced operations and removed stale provider-instance cancellation errors from current presentation.

# CineTrack 0.99.14 beta

- Fixed stalled Floppy initial synchronization by indexing remote episode history once per worker run and reusing a session-scoped transport cache.
- Added logical-unit progress, current-item diagnostics, no-progress stall detection, per-unit timeouts, and safe WorkManager retries that preserve the persisted bootstrap plan.
- Redesigned the managed Floppy synchronization card with localized stage-specific progress, compact retry actions, and a non-contradictory empty state.

# CineTrack 0.99.13 beta

- Prevented generic and forced synchronization from stealing managed Floppy bootstrap operations.
- Isolated Simkl and Floppy provider I/O, added provider-specific failure presentation, and restored current-instance bootstrap progress.
- Restored upcoming and recently aired Progress attention without overriding genuine playback activity.

# CineTrack 0.99.12 beta

- Moved Floppy SECONDARY initial synchronization into durable, instance-safe WorkManager batches with real syncing and verification progress.
- Added durable TMDB library artwork refresh with incremental Room updates, bounded requests, exact changed/unchanged/failed accounting, and a background progress popup.
- Preserved existing provider delivery generations, retry safety, and tracking-state isolation.

# CineTrack 0.99.11 beta

- Fixed real-device Floppy Connect taps with a direct Material3 action, synchronous staged feedback, lifecycle-safe serialization, and explicit failure diagnostics.
- Hardened Floppy validation and persistence, private-LAN HTTP policy propagation, v26.9.10 response compatibility, and malformed-response classification.
- Added handshake and Compose instrumentation coverage for public info, authenticated preferences, and the Connect/PrimaryAction tap paths.

# CineTrack 0.99.10 beta

- Added pull-to-refresh artwork and metadata updates for active TMDB library titles without changing tracking state.
- Ordered progress by real playback activity timestamps and stabilized release notifications with structured episode titles and localized formatting.
- Refined inline Floppy configuration, API guidance, retry states, and provider-key links for English and Italian.

# CineTrack 0.99.04 beta

- Added the runtime-configured Floppy tracking provider with secure API-key storage, reverse-proxy-safe URL handling, capability discovery, paginated library/history sync, provider identity reset, and Floppy settings controls.
- Hardened current-intent rebinding so terminal deliveries from any provider cannot be reopened accidentally.

# CineTrack 0.99.02 beta

- Hardened durable multi-provider routing with mutex-protected target snapshots and atomic KEEP_LOCAL conflict replacement.
- Added secondary-only configuration normalization, provider bootstrap readiness, safe MAIN promotion rules, and race coverage.

# CineTrack 0.99.01 beta

- Completed the final multi-provider synchronization hardening pass, including real Room migration validation in CI and packaging historical schemas for instrumentation tests.
- Moved Simkl full-sync orchestration into its dedicated engine and removed the application binding cycle.

# CineTrack 0.97 beta

- Hardened generation-aware delivery routing: missing rows are never inferred as pending, role switches preserve targets, and removed providers are cancelled terminally.
- Made all local library/history mutations snapshot provider deliveries atomically and moved immediate dispatch ownership into the local mutation layer.
- Added granular capability checks, provider-neutral tracking state, delivery outcome UI, orphan repair, migration-chain coverage, and current-schema CI validation.

# CineTrack 0.96 beta

- Re-evaluate all persisted provider deliveries after a full-sync retry so an operation completes when only a previously failed secondary delivery remains.

Verification is pending in the release environment.

# CineTrack 0.95 beta

- Route tracking settings and synchronization operations through dedicated screen entry points so future provider controls can grow without expanding the monolithic settings surface.

Verification is pending in the release environment.

# CineTrack 0.94 beta

- Keep full-sync acknowledgements scoped to the MAIN deliveries actually attempted, so a retry after a partial secondary failure never replays an already acknowledged MAIN write.

Verification is pending in the release environment.

# CineTrack 0.93 beta

- Preserve immutable provider target snapshots across role switches, add idempotent legacy delivery backfill, and surface partial provider delivery status.
- Mark unsupported MAIN operations explicitly failed while keeping delivery state transitions generation-safe.

Verification is pending in the release environment.

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

