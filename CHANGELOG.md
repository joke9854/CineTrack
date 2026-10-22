# CineTrack 0.99.71 beta

**Changes**
- Behind the scenes: the app's old all-in-one data layer is gone. Metadata settings, backups, title and episode details, Progress, Discover and search, the app's saved state, Floppy and Simkl sign-in, and sync conflicts each have their own part now, and what remains only handles syncing with Simkl and Floppy. Everything works as before; this makes later fixes safer.
- Sync conflicts: Keep local and Use remote work as before and now have automated tests, including that Use remote keeps the remote watched time exactly.
- The startup profile was recorded again on a real phone, so the reorganised code is prepared at install time as well.

# CineTrack 0.99.70 beta

**Changes**
- Behind the scenes: Settings, Library, Progress, the title and episode pages and Discover's See all lists each run on their own, separate from the rest of the app. Everything works as before; the split makes later fixes on one screen safer for the others.
- Settings: every page (Notifications, Appearance, TMDB, Ratings, Streaming, the TMDB/MDBList/Trakt keys, Export and restore, Sync, Sync operations, Simkl and Floppy) now has its own logic. Rating sources now save R.Tomatoes correctly.
- Library: the artwork refresh pill no longer disappears when the library reloads in the middle of a refresh.
- Progress: release reminder bells (Calendar, title and episode pages) and the Statistics people moved with the Progress screen.
- Title and episode pages: details, cast, ratings, episodes, collection, recommendations, trailers and character pictures load and cache as before; watched ticks still show the moment you tap.
- Discover: See all lists page, retry and follow status changes as before; pull to refresh starts them over.

# CineTrack 0.99.69 beta

**Changes**
- The app now includes a startup profile recorded on a real phone, so Android can prepare the app's code at install time instead of on first use. Opening the app and the Progress and detail pages should feel quicker on first launch after installing an update.
- Behind the scenes: Search and Discover's filter page and Mosaic now run on their own, separate from the rest of the app. Searching titles and people, recent searches, filters, providers, Mosaic paging and Not interested work as before.

# CineTrack 0.99.68 beta

**Fixes**
- Episode pages now show the Characters / Cast switch with character pictures.
- Characters shows only character names. Crew jobs such as Executive Producer appear only under Cast.
- Character pictures for titles opened from Collection or More like this load much faster with Floppy 03481e21 or newer.

# CineTrack 0.99.67 beta

**New**
- Characters: with Floppy connected and a TVDB key set on Floppy, the cast row on show, movie and episode pages has two headlines, Characters and Cast. Characters shows each character's picture with the character name first and the actor below; a character without a picture keeps the actor photo. Directors, producers and other crew appear only under Cast.
- Settings > Appearance > Cast photos: choose round photos or poster-shaped ones.

**Changes**
- Cast photos are a bit bigger, and names and roles stay on one line using the full card width.
- The cast shows at once; character pictures fill in when Floppy answers. Without Floppy the cast row works as before, from TMDB.

Requires Floppy 5b8e6a9a or newer for character pictures.

# CineTrack 0.99.66 beta

**Changes**
- Behind the scenes: the About, Logs and Language settings pages now run on their own, separate from the rest of the app. Checking for updates, the changelog, and viewing, exporting or clearing logs work as before.
- Library versions are kept in one place, so dependency updates can arrive as small, separately tested changes.

# CineTrack 0.99.65 beta

**New**
- Up Next widget: tap the green check on a row to mark that episode watched without opening the app. The widget then moves on to the next episode.
- The widget resizes from 1 cell tall (one row) to 4 or more cells tall (five rows). When it is narrow, posters are hidden so titles stay readable.

**Fixes**
- Marking an episode from a release notification no longer keeps the app busy while the next episode loads; that now happens in the background.

# CineTrack 0.99.64 beta

**Changes**
- Progress: swipe a card to the left (instead of the right) to mark the episode watched.
- Pull-to-refresh uses the Material 3 indicator. The pull length and where the spinner rests are unchanged.

**Fixes**
- Crash reports are saved correctly on Android 7.1. Saving one there could fail.

# CineTrack 0.99.63 beta

**New**
- Settings > Metadata > Hide adult titles (on by default) keeps 18+ titles out of search, Discover and filmographies, including Japanese adult videos that TMDB does not flag as adult.
- Settings shows which account you are signed in with: "Connected as <name>" on the Simkl and Floppy cards. The Floppy name needs the latest Floppy update.
- The Floppy server address field adds https:// for you, or http:// when "Allow HTTP on local network" is on.
- Library posters show watch progress as a thin line along the bottom edge instead of a circle.

**Fixes**
- Release notifications held back by quiet hours now arrive when quiet hours end, even with the app closed and the phone idle.
- Content regions: Trending in Discover's browse grid, and other lists, no longer show titles from regions you did not choose. A title TMDB gives no country is kept only when its original language is spoken in one of your regions.

# CineTrack 0.99.62 beta

**Fixes**
- Anime numbered straight through on Simkl (Naruto episode 183) are now stored with TMDB's numbering (season 4, episode 183), the same as Floppy and the show page. Switching Floppy to main tracker no longer splits a show's history into two numberings. Episodes are still sent to Simkl with Simkl's own numbers.
- After updating, plays saved with the old numbering are renumbered once, keeping their exact watch times. This needs a Floppy connection with the latest Floppy update; shows switch over only once their renumbering has finished.

# CineTrack 0.99.61 beta

**Fixes**
- Much less background traffic to TMDB: missing episode titles in History are read a whole season at a time, and episodes TMDB does not have (long anime numbered straight through) are not asked for again and again. With Floppy as main tracker this made the first sync after the switch crawl.
- Simkl: a show or movie removed on the Simkl website now leaves the app even when another item in your Simkl library cannot be matched to TMDB.
- When Simkl, TMDB or Floppy is briefly busy or unavailable, reads are retried a couple of times with a short, slightly randomized wait instead of failing.

# CineTrack 0.99.60 beta

**Fixes**
- Simkl: giving a movie a library status (Plan to watch, Watching, Paused, Dropped) now reaches Simkl. The status was sent, but the "not watched" update sent with it removed the movie from your Simkl lists again. Set the status once more on movies you changed recently.

# CineTrack 0.99.59 beta

**Fixes**
- A show removed from the library no longer comes back as In progress hours later. Simkl accepted the removal, but the app's saved copy of your Simkl library kept the old status, and later syncs read it back as if Simkl still had the show. Remove the show once more after updating.

# CineTrack 0.99.58 beta

**Diagnostics**
- Settings > Logs now records how a show you removed comes back: whether Simkl accepted the removal, and whether the returning status came from Simkl or from the app's saved copy of your Simkl library.

# CineTrack 0.99.57 beta

**New**
- Floppy: when you change the server address and the app sees the same Floppy account there, it asks whether it is the same server at a new address or a different server. "Same server" keeps the connection and its finished initial sync; "Different server" starts a new initial sync.

**Fixes**
- Floppy initial sync is much faster: after each batch the app no longer re-checked the whole sync plan.
- A show TMDB no longer has (deleted or merged) no longer stops the Floppy initial sync from finishing; its episodes are listed as not on Floppy. Needs Floppy CineTrack-API a39dd59 or later.

# CineTrack 0.99.56 beta

**Fixes**
- A show removed from the library no longer comes back as In progress (on the phone and on Floppy) after a sync. A removal Simkl accepted while a sync was running was overwritten by that sync's older copy of your Simkl library.
- The update pill shows the download percentage instead of placeholder text.

# CineTrack 0.99.55 beta

**New**
- Calendar: episodes and films out today stay in the list, marked "Aired" / "Released" instead of the time. A show's episodes on the same day fold into one stacked card (tap to see them); History's multi-episode cards get the same stacked look.
- Release bells start on for releases that notify you anyway; turning one off silences just that release.
- Floppy settings: a "Sync now" button while connected, and the status reads Connected / Not connected.

**Design**
- Going back from a show or movie page: the page follows your back gesture, and when you let go the poster flies back to its place while the page fades out. From the Library and Progress it now lands on the right tile without a jump.

**Fixes**
- Calendar: which day a release falls on now uses your time zone. With Simkl (or TMDB) air dates, episodes airing today could be missing.
- Statistics: "vs last year" compares the same stretch of last year, and the genre bars count this year.

# CineTrack 0.99.54 beta

**New**
- Choose where episode air dates come from: Settings > Metadata > Air dates from (TMDB, Trakt or Simkl). When the chosen one has no date for an episode, the next one fills in. Switching applies right away.
- Calendar: a week strip at the top, days as "Today", "Tomorrow", "Friday 17", a large card for the next release and a reminder bell on every release.
- History: each day shows its plays and time watched; episodes of one show watched in a row fold into one card (tap to expand), with "Season finished" and "Rewatch" marks.
- Statistics: the year in one number with a month-by-month curve, four key facts and genre bars. Tap the year card for the full year in review with the activity heatmap.
- Shows you've started have "Mark all watched" under the series progress.
- Show and movie pages: a ⋮ menu with "Refresh info" for that title.
- Library artwork refresh and app updates now show as a pill at the top; the app checks for updates when it starts.
- Search history has "Clear all".

**Design**
- Bigger poster on show and movie pages, like Trakt.
- Headers in Progress, Library and Discover stay pinned and take the page's colour.
- Episode pages: swiping between episodes no longer flashes; the show chip has a cleaner shape.
- The Covers / Text switch for seasons animates smoothly.
- Going back from a detail page, the page shrinks with its poster instead of the poster floating alone.
- The Discover mosaic shuffles its tile shapes.
- Floppy settings: no sign-in button or API key field once connected; the main tracker is a Simkl | Floppy switch.
- Search results no longer show the year under the posters.

**Fixes**
- The Floppy check no longer reports plays that Simkl rounded to the next second.

# CineTrack 0.99.53 beta

- Removing a show no longer brings it back on Floppy as In progress: unwatching an episode now removes its plays on Floppy instead of marking it dropped.

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
