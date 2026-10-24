---
title: "CineTrack 1.0 audit (0.99.77): leftovers, design, cohesion, security"
author: Joke9854
date: 2026-10-24
branch: beta-testing
cost: { minutes: 28, tokens: 203481 }
touches:
  - docs/ROADMAP.md
---

## Decided
- 2026-10-24 read-only audit at 2445492, report published as an Artifact (claude.ai/artifact/NMRxKoYkgTqcSzBbMR6i6D) with IDs L1-L8, D1-D20, C1-C21, S1-S18 and batches B1-B8. Awaiting the user's pick; nothing implemented.
- Glass lab gate verified: GlassTuning.available = APPLICATION_ID.endsWith(".debug"); release has no suffix; preview shares .debug on purpose. params() returns defaults and load() skips glass_lab prefs when unavailable.
- Progress card fallback blur (ProgressPlayback.kt:459, poster as backdrop when none) is part of the approved card: keep, don't re-flag.
## Tried and ruled out
- A network_security_config can't express "user's LAN Floppy IP", so S1 (app-wide cleartext) is better fixed with an OkHttp interceptor on the shared client than with NSC base-config alone.
## Watch out
- Security is solid already (Keystore AES-GCM no plaintext fallback, Floppy redirects off + same-origin auth, Simkl PKCE+state+iss, ACRA allowlist + sanitizer, no secrets in BuildConfig/git). Main gaps: cleartext app-wide, .local accepted by name (FloppyApiClientFactory.kt:51), updater has no signer check, crash reports opt-out, unvalidated deep-link segments can crash navigate.
- localizedSyncValue (SyncSettingsScreens.kt:433) has no WATCHING branch: conflicts show raw "WATCHING". Status labels are mapped in 5 places.
- GlassSheets.kt:161 tapping the selected status sets NONE, which for TV wipes episode history with no prompt; restores run without confirmation.
- Episode code has 6 formats; before unifying, confirm the labels are display-only (never dedupe by S/E).
