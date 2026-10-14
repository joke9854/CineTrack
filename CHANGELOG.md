# CineTrack 0.99.52 beta

- Removing a show now stays removed: after about half an hour it used to come back as Dropped (and on Floppy as In progress).
- Removing a show from its page also clears its watched episodes there right away.
- Choosing Completed for a show marks all its aired episodes as watched, like Simkl does.
- The Floppy check no longer reports episodes whose time only differs by milliseconds.
- Settings > Logs has Clear logs (tap twice).

# CineTrack 0.99.51 beta

**Sync**
- A status change or removal no longer bounces back when Simkl hasn't caught up with it yet. Settings > Logs notes when this happens.
- A change stuck in Sync operations no longer stops every later sync.
- Watching an episode of a show in Plan to watch (or not in your library) moves it to Watching right away, so it shows in Progress without waiting for a sync.
- The Floppy check after a sync no longer re-sends the same episodes every hour. Episodes Floppy has at another time are listed in Logs with both times.

**Fixes**
- Removing and unmarking a show from its page now really unmarks its episodes.
- Choosing Completed for a show offers to mark the aired episodes you haven't watched; they're marked only if you accept.
- Notifications due during quiet hours arrive when quiet hours end, without opening the app.
- The finished initial sync card no longer stays in Sync operations.
- Log text is readable in the dark theme.

# CineTrack 0.99.50 beta

**Floppy**
- After each sync (at most once an hour), CineTrack checks that Floppy has everything this device has: every movie and show in your library, their status, and every watched movie and episode at its exact time. Anything missing is sent again on its own. The result is in Settings > Logs. This runs when Floppy is the second tracker; when Floppy is the main tracker, Floppy is the reference and nothing is re-sent.
- Marking a show as Completed now reaches Floppy without Floppy marking every episode as watched. Needs a Floppy server updated to the latest CineTrack API.
- Removing a show from your library now removes it from Floppy too, as it already did on Simkl and on this device.
- Floppy re-sends are much faster for anime and other shows Floppy numbers differently, with a Floppy server updated to the latest CineTrack API.

# CineTrack 0.99.49 beta

- Changing a show's status (Watching, Paused, Dropped, Plan to watch) after watching one of its episodes now reaches Floppy. Floppy used to refuse it as a conflict, and CineTrack kept retrying.
- Signing in to Floppy again, or using a new API key, for the same account keeps your connection instead of resending your whole history.

# CineTrack 0.99.48 beta

**Sign in with Floppy**
- Connecting Floppy works like Simkl now: enter the server address, tap "Sign in with Floppy", log in on Floppy's own page and allow CineTrack. No API key to create or paste. Needs a Floppy server updated to the latest CineTrack API.
- The sign-in renews itself and only allows what tracking needs. Floppy lists CineTrack under your connected apps, where you can end it at any time; disconnecting in CineTrack ends it too.
- Already connected with an API key? It keeps working. To switch, tap "Sign in with Floppy" in Settings > Floppy: the same account keeps your connection, sync history and main-tracker choice.
- Connection errors now say what to do when CineTrack knows it (for example, "Floppy is your main tracker…") instead of a generic message.

# CineTrack 0.99.47 beta

**Floppy as the main tracker**
- Floppy can now be the main tracker, with Simkl as the second one, and you can switch back at any time: Settings > Floppy > Main tracker. Needs a Floppy server updated to the latest CineTrack API, with its initial sync from this device finished.
- Switching first finishes a sync with the current main tracker. Floppy's first sync as main tracker then adds what it has to this device and removes nothing.
- With Floppy as main tracker, every play keeps its exact time, rewatches stay separate plays, and changes made on Floppy (from its website or other apps) come back to CineTrack. Simkl receives every change.
- A Floppy server or account that can't be the main tracker is never used as one: CineTrack hands the main role back to Simkl.

# CineTrack 0.99.46 beta

- Film details show the digital release date for your region, or the US date (marked "US") when your region has none.
- Softer glass edges: cards and controls catch the light along the top edge and fade along the sides.
- Library lists Films before TV series, the same order as Discover.
- New Progress icon in the navigation bar: a play button inside a ring.
- Fixed: after opening a section from the widget or a link, tapping another tab could reopen that section (e.g. Discover showed Library).

# CineTrack 0.99.45 beta

**Simkl sign-in (AUTH V2)**
- CineTrack now uses Simkl's new sign-in. After updating, Settings > Simkl shows "Reconnect needed": sign in once. Your library, history and unsent changes stay on this device.
- Sign-in stays valid on its own (tokens refresh automatically), asks only for the access CineTrack needs, and "Don't allow" now reads as cancelled.
- Disconnecting tells Simkl to end the connection.

**Connecting an account**
- After signing in you choose how to combine this device with the account: **Merge** adds what only this device has to Simkl (with the original watch times) and Simkl's library here, removing nothing; **Use Simkl's library** makes this device match the account.
- Disconnecting Simkl or Floppy now asks for confirmation.

**Series**
- "Mark as watched" on a series asks first and marks only the aired episodes you haven't watched. On a watched series it offers to remove it from every list and unmark its episodes.
- Moving a series to another list no longer unmarks its episodes.
- Marking or unmarking many episodes at once sends them to Simkl in one request. If Simkl's daily limit is reached, changes stay saved and are sent automatically later.

**Fixes**
- Progress keeps the order of what you watched most recently after a sync; before, a sync could push the show you just watched down and sort the rest alphabetically.

# CineTrack 0.99.44 beta

- Discover mosaic keeps loading as you scroll, for every view (trending, popular, upcoming, box office, top rated and genres). Before, it stopped after about 60 titles, or 20 in the default view.
- Episodes open from a series page by growing their thumbnail into the episode page, and shrink back into the row when you go back. The episode image no longer briefly shows another picture first.
- Titles opened from "More like this" or a saga show their page at once instead of an empty loading page. Going back keeps the saga and recommendation rows where they were, and the poster flies back with its image.
- Poster colours also pick up muted posters (e.g. Lanterns turns green); black-and-white posters keep your accent.
- Detail and episode pages label the plot with an "Overview" heading.

# CineTrack 0.99.43 beta

A redesign of the whole app, plus a few new features.

**Detail pages**
- New top: one line of facts (year, runtime, genres), the ratings in a single bar that never cuts off, and series status as a small chip. Started series show your progress and a "Continue · S2 E18" button that opens the next episode.
- Below the buttons: the plot as plain text (folds after four lines), where to watch as one card with an Open button, round cast photos, the saga as a numbered row with how many you've seen, "More like this", and a short details list with only what the header doesn't already say.
- Episodes: season pills or season covers (switch in the Episodes header), flat episode rows with an info button for the description, all episodes shown unless a season has more than 30, a smooth slide between seasons, and "Next" only once you've started the show.
- Episode pages: the series as a small chip, the episode's own rating, arrows that say where they lead ("E17", "E19"), a dash for each episode of the season, director and writer, and the season's neighbouring episodes at the bottom.
- Pages take their colour from the poster (switch in Appearance). Plot, cast and ratings show placeholders while loading. Opening a cover grows it into the page.

**New**
- Rewatches: "Watched 2 times · last …" on films and episodes.
- Release countdowns: "Out in 5 days" on upcoming films and episodes, with a bell that reminds you on the day, for any title.
- Swipe a Progress card to the right to mark the episode watched.
- Home screen widget with your next three episodes, each with its poster.
- "Your 2026" in Statistics: time watched, most-watched series, longest streak, busiest month and top genres.

**Everywhere else**
- Lighter cards, the Manrope typeface, softer tab feedback with a sliding underline, and smoother transitions between Films / Series and the Library tabs.
- Settings redesigned: TMDB, Simkl and Floppy as brand cards (official Floppy logo), Trakt and MDBList as compact cards, and every service page with its own header. Simkl shows your username and last sync. Appearance has a live preview of your choices.
- Discover mosaic with Cinopsys-style filters (type, period, genres) and long-press menus; bigger 16:9 carousel.
- Wider, cleaner pop-ups, a progress pill while backups are created or restored, a collapsing header in Library with remembered scroll per tab, and equal-size Statistics cards.

**Fixes**
- A show you just advanced in Progress stays on top instead of dropping back after a few seconds.
- Taps right after going back from a detail page are no longer ignored.

# CineTrack 0.99.42 beta

- Fixed the dark disc around the watched button on Progress cards: the glass is now clipped to its own shape, so the page colour no longer shows around controls that sit on a card. The pills keep a soft shadow.

# CineTrack 0.99.41 beta

- Liquid glass on the Progress page: the tab pills, the order and "Show all" buttons, the watched button and the progress bars on each card now refract the page colours, with a light rim highlight. Cards, posters and layout are unchanged.
- Glass needs Android 12 or later and is skipped on low-memory devices, which keep the previous look.

# CineTrack 0.99.40 beta

- Build toolchain upgraded (Android Gradle Plugin 9.4, Gradle 9.6, compileSdk 37, Compose BOM 2026.06.01) to prepare the new glass effects. No feature changes.
- CineTrack now requires Android 7.1 or later (previously Android 6.0).
- Dates, month labels and region names now follow a change of app language immediately.

# CineTrack 0.99.39 beta

- Episodes stored one episode off by older versions (after two-part episodes TMDB merges, e.g. The Office season 4, and anime numbered by air date) are moved to the right episode on Floppy, keeping their exact watch time. For this the initial Floppy sync runs once more after the update, in repair mode; items already correct are untouched and nothing is duplicated.
- A successful initial sync now shows "Complete" instead of "Ready".
- "Not on Floppy": each entry is a clearer card (show, episode, title) with "Open in Floppy", and can be dismissed ("Done, hide" or ×); dismissed entries stay hidden.
- Trakt now shows its own logo in Integrations, on its settings page and in About (tap to open trakt.tv).
- Requires Floppy CineTrack-API 48f7707c or later for the repair.

# CineTrack 0.99.38 beta

- Exact air times from Trakt: add a Trakt Client ID in Settings > Integrations > Trakt and new episodes use Trakt's exact release time (including streaming release times) for yesterday and the coming week. Simkl's calendar remains the fallback and covers the following month. Episodes are matched by their TMDB id, so different numbering (e.g. anime) does not matter.
- Logs show only problems by default (errors and warnings), grouped by day with a short headline; tap an entry for details. "Show all activity" reveals everything, and the exported file always contains the complete log.

# CineTrack 0.99.37 beta

- Fixed: episodes and movies marked watched shortly after the app started were silently not sent to Floppy (they were treated as unsupported until a Floppy connection check had run). Floppy's saved connection is now always used to decide.
- Fixed: the episodes Floppy could not place at the end of a sync were sometimes forgotten, so "Not on Floppy" stayed empty and they were never retried.
- To recover anything missed by these two bugs, the initial Floppy sync runs once more after this update. Items already on Floppy are recognised and not duplicated.
- Settings > Logs now shows every Floppy write outside the initial sync: delivered, skipped or failed.

# CineTrack 0.99.36 beta

- Settings > Floppy now lists the watched episodes Floppy could not store ("Not on Floppy"), with their title and an "Open in Floppy" button that searches Floppy for them so they can be marked by hand. A special that TMDB lists as a movie (for example "A Parks and Recreation Special") opens the movie search.
- The episodes still unplaced are retried once so Floppy can return their titles.
- Requires Floppy CineTrack-API d765504f or later for the titles; older servers show the show and episode number only.

# CineTrack 0.99.35 beta

- Anime that TMDB numbers absolutely inside its seasons (One Piece, Naruto Shippuden and similar) is now matched on Floppy by episode number, not by air date, which TMDB and TVDB often disagree on. This also applies to every future episode.
- Specials whose TMDB title carries the show name (for example "Game of Thrones: The Last Watch") are now matched.
- Episodes closed as "no Floppy counterpart" are retried once with these rules.
- Requires Floppy CineTrack-API 180aae4c or later. Episodes already stored one off by the old air-date match can be moved with the server command repair_cinetrack_absolute_episode_plays.

# CineTrack 0.99.34 beta

- The initial Floppy sync no longer gets stuck at the very end: on Floppy servers with the batch API, completion is confirmed by Floppy's answer for every item instead of re-downloading the whole Floppy history, which timed out on large libraries.
- Settings > Logs now says whether the final verification passed and, if not, which items are missing on Floppy or why Floppy could not be read.
- Anime double-episode days (two episodes aired the same day) are now matched on Floppy; the episodes closed for this reason are retried once.
- Requires Floppy CineTrack-API 96a76622 or later.

# CineTrack 0.99.33 beta

- Progress now always shows the episode after the last one you watched. A skipped earlier episode no longer replaces it, and a show you are caught up on no longer shows an old skipped episode.
- Marking an episode watched from a show's page moves its Progress card to the next episode immediately instead of after the network refresh.
- A show you just advanced stays above shows whose episode merely aired in the last few days.
- Progress updates when you return to the app, in background refreshes, after a release notification or its "mark watched" action, and after changing the metadata timezone or language, so newly released episodes appear without opening the show.
- Progress fetches missing earlier episodes when only a later one was cached, and starts shows without history at their first regular season.

# CineTrack 0.99.32 beta

- Episodes that were closed as "no Floppy counterpart (tvdb_id_unknown)" are retried: Floppy now uses your personal TVDB key and the TVDB id TMDB already provides, so anime and renumbered episodes can be matched.
- A finished initial sync now remembers the episodes it could not place and retries them automatically when Floppy's matching improves, instead of forgetting them.
- If your initial sync finished before this version, it runs once more after the update; items already on Floppy are recognised and not duplicated.
- Requires Floppy CineTrack-API 3e424def or later.

# CineTrack 0.99.31 beta

- Anime numbered continuously by Simkl (Naruto, Naruto Shippuden, One Piece and similar) is now placed on its TMDB season on servers whose TMDB language is not English, and through TVDB's absolute order when TMDB's seasons do not line up.
- Episodes of a season TMDB does not have at all (for example some specials) no longer stop the initial sync with "metadata_unavailable"; they are matched through TVDB or closed as having no Floppy counterpart.
- Episodes closed as "no Floppy counterpart" by 0.99.30 are retried once more with the improved matching.
- Settings > Logs now shows why Floppy could not place an episode (for example "not_in_tvdb" or "no_unique_tmdb_match").
- Requires Floppy CineTrack-API 395d7266 or later.

# CineTrack 0.99.30 beta

- Episodes numbered differently on TMDB (two-part episodes TMDB merges, specials, split seasons) are now matched by air date and title through TVDB and saved on Floppy under the right TMDB episode, instead of being closed as having no counterpart. Episodes later in the same season are no longer stored one episode off.
- Episodes the initial sync had already closed as "no Floppy counterpart" are retried once with the new matching; any still without a match are closed again.
- Requires Floppy CineTrack-API dd6231a0 or later with a TVDB API key configured.

# CineTrack 0.99.29 beta

- Anime numbered continuously in one season (for example One Piece S01E62 and later) is now saved on Floppy under the matching TMDB season and episode.
- Episodes with no TMDB counterpart on Floppy (two-part episodes TMDB merges, specials it does not list) no longer keep the initial sync from finishing: they are closed and listed in Settings > Logs.
- A long initial sync no longer gives up after a few scattered temporary errors: a run that made progress continues in a fresh attempt 30 seconds later.
- Requires Floppy CineTrack-API 216787ad or later for the anime translation and busy-database handling.

# CineTrack 0.99.28 beta

- When Floppy refuses individual episodes (for example episode numbers its metadata provider lists differently), the rest of the request is now saved and acknowledged; only the refused episodes stay unsynced, each listed in Settings > Logs with the reason.
- Floppy 404 errors now show the server's reason instead of "API endpoint was not found", and log lines use readable error messages.
- Requires Floppy CineTrack-API fb56e6b1 or later for per-episode results; older servers keep working with whole-request failures.

# CineTrack 0.99.27 beta

- The initial Floppy sync now resumes where it stopped after Retry, an error or a timeout, instead of re-sending everything already synced from 0 (which caused minutes of re-checking movies and a loop on TV episodes). An existing in-progress sync does one last full check, then resumes normally.
- Floppy request errors now include the server's short reason (for example which episode was rejected).
- The initial sync writes its key events to Settings > Logs: start, stalls, skipped items with the reason, why it stopped and whether it retries, slow batches and periodic progress.

# CineTrack 0.99.26 beta

- Episodes you watch are now sent to Floppy as exact, retry-safe events: no more scan of the whole Floppy history on every watch, and genuine rewatches are no longer dropped.
- A show's specials (season 0) are sent in their own request, so a server that rejects them can no longer fail the show's regular episodes.
- A few rejected items at the front of the initial sync no longer make every Retry stop again before any progress.
- The sync log now records each batch's size and duration to help measure server speed.

# CineTrack 0.99.25 beta

- Much faster TV episode upload during the initial Floppy sync: each request now carries up to 50 of one show's unsynced episodes instead of only the few that happened to be next in watch-history order. The sync plan, exact timestamps and retry safety are unchanged, so an in-progress sync continues where it left off.

# CineTrack 0.99.24 beta

- Floppy connection checks now use the dedicated CineTrack probe instead of `/user/preferences/`, so tracking-scoped tokens without `user:read` can sync; older servers without the probe still fall back to preferences.
- Each connection check and Retry refreshes the server's Bootstrap V2 capabilities, and an existing connection keeps its identity (and its bootstrap plan) when moving to the probe's account id with the same API key.
- Floppy errors are now classified (sign-in, token scope, API redirect, wrong route, rate limit, server, invalid response) instead of a generic "Floppy is unavailable".
- One Floppy item the server permanently rejects no longer blocks the whole initial sync: it is left unresolved, the rest continues, and the sync ends as needing attention with the skipped count.

# CineTrack 0.99.23 beta

- Added Floppy Bootstrap V2 support for batched movie/show imports on compatible servers, removing unnecessary full movie-history preparation before export.
- Fixed Floppy bootstrap progress so movie writes report as syncing, and made explicit retries replace stale WorkManager executions instead of racing cancel + KEEP.
- Stabilized Room migration CI by requiring usable KVM acceleration and running only the dedicated AppDatabaseMigrationTest instrumentation class.

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
