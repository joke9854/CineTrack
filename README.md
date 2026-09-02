# CineTrack 0.69

CineTrack 0.69 is a ground-up Kotlin/Jetpack Compose movie and TV tracker. It combines a TMDB-first catalogue, MDBList ratings and two-way Simkl synchronization with an offline-first Room cache and a native Compose interface.

## Open in Android Studio

1. Extract the ZIP and open the `CineTrack` folder in Android Studio.
2. Use JDK 17 and let Android Studio install Android SDK 36 if it is missing.
3. Copy `local.properties.example` to `local.properties`.
4. Set `sdk.dir` and add the API values you want to use:

   ```properties
   sdk.dir=C\:\\Users\\YOUR_NAME\\AppData\\Local\\Android\\Sdk
   TMDB_API_TOKEN=your_tmdb_v4_read_token
   MDBLIST_API_KEY=your_mdblist_key
   SIMKL_CLIENT_ID=your_simkl_client_id
   ```

5. Register `cinetrack://simkl` as the Simkl redirect URI in the Simkl developer app. It must match exactly, including casing and the absence of a trailing slash. CineTrack uses the Android-safe PKCE authorization flow and does not require a client secret.
6. Sync Gradle and run the `app` configuration.

The app never inserts mockup/demo titles. Without a TMDB token, discovery stays empty and reports the missing configuration; Simkl and Room continue to show only real account and locally saved data.

## Included 0.69 behavior

- Immutable Compose domain models and a dedicated synchronization-progress stream prevent per-item sync progress from invalidating the full app state.
- Startup preferences load concurrently; background scheduling starts asynchronously after setup.
- Progress-cache calculation is split behind a dedicated repository component and indexes watched episodes by show for linear-time history preparation.
- API services share one OkHttp connection pool/dispatcher, derived Progress values are memoized, lazy-list items use stable keys and image cache limits are explicit.

- Progress persists the latest watched-show interaction so a newly aired episode cannot jump back above the show watched afterward.
- The Changelog control opens a native in-app popup populated from the latest GitHub release notes.
- TMDB Useful information includes status, networks, budget, box office, production companies/countries and original language alongside its existing details.

- Marking a Progress episode watched advances to the next locally cached episode immediately, persists that show’s new up-next row before the Simkl request, and keeps the card at the top.
- Viewing time is decomposed into localized years, months, days, hours and minutes; actor/director statistics include portraits.
- About includes a circular changelog shortcut; TMDB status, season/episode totals and genres enrich Useful information.
- Season cards start collapsed except for the active season of a currently watched show, and every density choice now explains its layout effect.

- First-run introduction with an optional direct route to service setup, plus a dedicated no-TMDB warning and setup action in Discover.
- TMDB and MDBList credentials stay encrypted at rest and masked in Settings, are verified with their service before saving, and cannot be replaced with an empty value.
- Discover provider filters use the configured content/metadata region and show only preferred providers when favorites are selected.
- Marking an episode watched promotes that TV show immediately and persists latest-watched ordering; Simkl corrects only episode air dates while TMDB remains the complete season/episode source.
- Refined viewing heatmap spacing and long watched-time formatting in days, months, or years; larger Settings typography and literal UI color names.

- Detailed synchronization activity with downloaded/uploaded/add/remove/unchanged counts, pending writes, failures, conflicts and full/incremental timestamps. Unchanged activity exits before Room item writes.
- Previous-episode watched prompt, white detail status text, hour-based duration formatting and a complete scrollable cast sheet.
- Statistics (including on-demand actor/director insights), saved discovery presets, include/exclude genres, anime/provider/runtime/language/decade controls, persisted “show less like this,” recommendation explanations, and bulk library status editing.
- Tracked-only upcoming diagnostics, specials filtering, hide/restore upcoming entries, and one coming-soon row based primarily on Simkl air dates while preserving the last valid schedule.
- Region-aware subscription/rent/buy provider groups, preferred-provider availability notifications, release notifications, calendar export, deep links, shortcuts and an Up Next widget.
- Encrypted Simkl/API overrides, automatic logical backups, one-tap recovery and checksum-validated restore before any database transaction.

- Correct Simkl list payloads for immediate movie/show status changes and durable removal retries.
- Mark-as-watched actions stay consistent between show, movie and episode detail pages.
- English metadata fallback when the selected TMDB language has missing titles, descriptions, actor biographies or episode text.
- Movie and TV discovery filters with genre, year, minimum rating and sorting controls.
- Light haptic feedback across navigation, cards, status controls and primary actions.
- Refined status-menu dismissal and compact Progress/Library action controls.

- Item removals are sent to Simkl immediately with their preserved Simkl/TMDB identifiers and cannot be blocked by an older pending write.
- Simkl watchlist writes use the documented top-level status payload, while failed requests remain dirty for safe retry.
- Local-save commits batch history replacement, removals, pending-write cleanup and sync markers to reduce transaction time on large accounts.
- Detail actions follow live library state, TMDB release status appears beside the library status, and collection titles are chronologically indexed.

- TV/movie status changes are pushed to Simkl immediately; failed requests remain dirty and retry safely during the next synchronization.
- The determinate Simkl progress bar includes the correctness-critical Room schedule and up-next rebuild, with per-show progress; cosmetic label and artwork repair continues asynchronously after completion.
- Watched-history imports use batched Room inserts; the public Simkl TV/anime calendar is cached for five hours and TMDB is retained as a schedule fallback.
- Up-next selections are persisted in Room, rebuilt only when their TV-library/history/playback/schedule dependencies change, and restored without rescanning the full history on every UI snapshot.

- Library additions, removals and status moves are pushed to Simkl immediately; failed requests remain dirty for automatic retry.
- Simkl `removed_from_list` activity is tracked separately and triggers a complete membership reconciliation, following the official synchronization guidance.
- The versioned 0.51 repair snapshot removes stale local entries that were previously deleted on Simkl while preserving pending local changes and rejecting partial empty responses.

- Simkl activity is checked before playback, library, history or metadata requests.
- An unchanged Simkl activity generation skips Room item writes and rebuilds Progress only when its schedule or durable up-next cache is stale.
- The 8-hour scheduler stores its last successful check separately in DataStore, so a no-change check does not invalidate Room.

- One transactionally consistent Room snapshot for media, rails, library state, episodes, playback, history and synchronization metadata.
- Simkl downloads and pending local writes are staged before one atomic database commit, including the ordered Library rail and sync generation.
- Progress metadata is cached in Room before publication; Compose never receives direct or partial network results.
- Episode-cache invalidations automatically refresh visible pages after foreground or background synchronization.
- Removed destructive database fallback so an unsupported future migration cannot silently erase the user cache.

- Atomic post-sync state publication so unchanged Simkl data cannot clear or reorder Progress rows.
- Cold-start database and enrichment work moved away from the UI thread.
- YouTube trailer embed supplied with a valid player origin/referrer context.
- Full-width directional episode page transitions matching the reference recording.

- Smooth launch layer while the local account snapshot is restored.
- Lighter list rendering and Nuvio-inspired floating navigation motion.
- Cache-first tracked-show schedule for upcoming episodes and Calendar.
- In-app YouTube trailer sheet with loading and retry states.
- Direction-aware episode transitions and a simplified episode hero.

- Four Compose destinations: Discover, Progress, Library and Settings.
- Deliberate long pull-down refresh on Discover and Progress, with a threshold that avoids accidental refreshes during normal scrolling.
- Progress episode cards open the exact episode; upcoming episodes use landscape artwork, and Calendar is built only from active library titles without an eight-show enrichment cap.
- Cached Progress and Calendar rows are filtered before their first frame, preventing aired episodes and non-library catalogue titles from flashing during startup or synchronization.
- A single shared liquid-glass selection pill that travels between bottom-navigation tabs and adapts to the compact scroll state.
- Separate trending TV, trending movie and upcoming rails; watched/library poster badges; three-column See All pages.
- Cached Room/DataStore state is published before network refresh, and active pages observe Room invalidations so a completed Simkl import is applied immediately without restarting the app.
- Real Simkl TV/movie playback groups, Simkl-style latest-unwatched ordering that promotes newly aired episodes for caught-up shows, episode-level watched repair (including anime), mixed history and calendar timelines, and a staged determinate sync bar. No mockup entries are seeded into Room.
- Movie, TV and episode detail designs with one continuous artwork tint behind one translucent rounded sheet, unified exterior-depth back controls, shared primary actions, providers, ratings, two-column information, episodes, cast, collection titles and a dedicated More like this recommendation rail.
- Fully expanded shared Material 3 sheets for Add to Library, sorting and actor details. Actor details remain bounded and scrollable; movie credits show their covers and are sorted newest-first when loaded from TMDB.
- Episode swipe navigation with previous/next fallbacks; the nested cast rail consumes its own horizontal gesture.
- Dedicated Settings pages for synchronization, integrations, notifications, language, rating sources, export and About.
- Room cache and user state, DataStore preferences/token storage, Retrofit/Kotlin serialization clients, and optimistic pending local state writes. WorkManager scheduling follows both the Background sync and Wi-Fi only switches.
- English and Italian string resources, reduced-motion-aware navigation, semantic labels and 48dp-class primary controls.

## Data ownership

- TMDB: discovery, search, metadata, images, providers, episodes and people.
- MDBList: normalized rating sources.
- Simkl: OAuth/PKCE connection, list state, watched history, playback progress, airing dates and synchronization activity.
- Room: merged offline state and pending writes.

The project uses Android Gradle Plugin 8.13.2/JDK 17, Compose BOM 2025.08.00 (Compose 1.9, compatible with compileSdk 36), Room 2.8.4 and Navigation 2.9.8. See the official [Compose BOM](https://developer.android.com/develop/ui/compose/bom), [Room release notes](https://developer.android.com/jetpack/androidx/releases/room) and [Navigation release notes](https://developer.android.com/jetpack/androidx/releases/navigation).

## Simkl troubleshooting

After accepting the Simkl authorization, Android should return directly to CineTrack and the Synchronization page should change to Connected before the first sync starts. CineTrack exchanges the authorization code with Simkl's public-client PKCE flow using form-encoded `code`, `client_id`, `code_verifier`, `redirect_uri`, and `grant_type=authorization_code` fields; no client secret is stored in the APK. If authorization or an API request fails, the Synchronization page shows the provider/HTTP error instead of silently remaining disconnected. Check the redirect URI first: `cinetrack://simkl` and `cinetrack://simkl/` are different values.

This revision increments the Room schema and intentionally drops the earlier development database once so any previously seeded mockup rows are removed. Real TMDB and Simkl content is then repopulated normally.

## Publishing testing updates

Configure these GitHub Actions repository secrets with the same permanent keystore used to sign the APK already installed on the device:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_PASSWORD`
- `SIMKL_CLIENT_ID`

Pushing a version-bumped commit to `testing/**` runs the signed release workflow. It publishes a testing prerelease containing the universal APK and its SHA-256 file. CineTrack checks that channel, downloads and verifies the APK, then opens Android's installer. `local.properties`, keystores, APKs and environment files are ignored by Git and must never be committed.

## Attribution and license

The previous app was based on Showly 2.0. This rebuild preserves and adapts relevant API endpoint/data-model ideas from the user-supplied GPL project, so the included project remains distributed under the GNU GPL v3 in `LICENSE`.
