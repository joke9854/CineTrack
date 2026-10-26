# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users

Self-hosters who run (or want to run) a Floppy tracker server and want a native Android client for it. They track films and series daily, care where their watch history lives, and notice when a tracker gets their data wrong. Simkl users are a secondary audience: Simkl can be the main tracker, with Floppy as an optional mirror.

The app is in public beta (0.99.x), distributed through GitHub releases and an in-app updater, in English and Italian.

## Product Purpose

CineTrack answers "what do I watch next?" and records what was watched, exactly, on a server the user controls. It discovers titles (TMDB catalogue), keeps a library by status, shows per-show progress and upcoming episodes, and syncs history to Floppy or Simkl. Success means the user trusts the history on their server to match what they actually watched, and reaching the next episode takes one glance and one gesture.

## Positioning

- **Self-hosted ownership:** Floppy can be the main tracker, not just a backup target. The user's data lives on their own server; third-party keys (TMDB, MDBList, TVmaze, Fanart.tv, TVDB) are the user's own, stored encrypted on the device, never built into the APK.
- **Exact sync fidelity:** watched times are kept to the exact instant, rewatches are separate plays, writes are offline-first and retried safely. The app never shows demo or fabricated titles.
- **Next-episode flow:** Progress / Up Next is the heart of the app: what's next per show, swipe left to mark watched, upcoming episodes and calendar, plus a home-screen widget and Quick Settings tile.
- **Premium native polish:** a fast, crafted Jetpack Compose app, not a wrapped web tracker.

## Operating Context

- Used on phones daily (primary test device: Samsung S23 Ultra), also adapts to tablets, foldables and landscape.
- Main tabs: Discover, Progress (In progress, Calendar, History), Library (Plan to watch, Watching, Paused, Completed, Dropped), Settings (with Profile).
- Settings groups services into Tracking (Simkl, Floppy) and Metadata (TMDB, MDBList, TVmaze, Fanart.tv, TVDB), each with its own page.
- Notifications (releases, providers, quiet hours), backups with restore, ICS export, Letterboxd/IMDb import, crash reports to the user's Floppy server (asked before sending).

## Capabilities and Constraints

- Kotlin, Jetpack Compose, Material 3; Haze for blur and liquid glass. minSdk 25, targetSdk 36. Liquid glass requires Android 13+; Android 12 and below keep flat buttons and blur on bars and sheets.
- Offline-first Room cache; without a TMDB key Discover stays empty and says so.
- Status vocabulary is fixed: one name per status. Setting a show to Completed marks every aired unwatched episode; marking an episode of a Plan-to-watch show moves it to Watching.
- Sync failures must be classified (auth, not found, rate limit, server, network) rather than shown as a generic "unavailable" message.
- Trakt is no longer a data source (removed in 0.99.78; TVmaze provides air times).
- Design changes are shown on the phone and approved before they are committed.

## Brand Commitments

- Names: **CineTrack** (the app) and **Floppy** (the self-hosted server). Floppy is credited under About's Services used.
- Voice: plain, specific, honest about state; no jargon, no fake content.
- Binding visual decision already approved by the user (2026-10-11): the Progress card layout (artwork behind a dark fade, 82dp poster, remaining time beside the title, ✓ button, progress line along the bottom edge). Glass goes on overlays and controls, never as a full-card layer; poster art stays crisp. Detailed visual rules belong in DESIGN.md.

## Evidence on Hand

- Release history: `CHANGELOG.md`; plan and release batches: `docs/ROADMAP.md`; sync contract: `docs/floppy-integration.md`.
- No testimonials, user counts, reviews or press exist; future work must not invent them.

## Product Principles

1. **The user's history is the truth.** Never trade accuracy of what was watched (exact instants, rewatches) for convenience or a cleaner UI.
2. **Their server, their keys.** Self-hosting is a first-class path, not an advanced setting.
3. **Next episode first.** Every surface should shorten the path from opening the app to watching or marking the next thing.
4. **Say what's really happening.** Empty, offline, syncing and failed states are named precisely; nothing is faked to look full.
5. **Native and crafted.** Feel like a premium Android app: fast, smooth, consistent, with polish in the details.

## Accessibility & Inclusion

Android baseline on every screen: TalkBack content descriptions, 48dp touch targets, support for system font scaling, WCAG AA text contrast. No formal standard beyond that. English and Italian, locale-aware text.
