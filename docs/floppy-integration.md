# Floppy integration

CineTrack integrates with the current Floppy API described by the running
instance at `/api/docs/` and `/api/schema/`. The checked-in DTOs follow the
current media, Consumption, history, and episode routes; they do not assume a
historical API version.

## Role and bootstrap

Floppy can be the SECONDARY provider (outbound only: it receives every change
made in CineTrack) or the MAIN provider (two-way sync). It becomes MAIN only when
the user chooses it in Settings > Floppy > Main tracker; see "Floppy as main
tracker" below. The rest of this section describes the SECONDARY role.

Connecting Floppy runs one explicit activation flow. The connection is first
validated, then its immutable connection/session metadata is persisted. If a
MAIN provider exists, CineTrack immediately sets `SECONDARY = FLOPPY`, starts
or resumes bootstrap, pushes the Floppy-only deliveries, and performs semantic
verification. With no MAIN provider, Floppy remains connected but is shown as
"Connected / inactive" until a MAIN is configured; authentication alone is not
reported as an active role.

Bootstrap states are `NOT_STARTED`, `RUNNING`, `FAILED`, and `READY`. A running
plan persists its Floppy connection ID, deterministic operation IDs, and exact
source generations, so a restart resumes the same intents instead of generating
new watched timestamps. Bootstrap uses merge/seed semantics: unrelated remote
history is preserved, while every seeded library, movie-watch, and episode-watch
field is checked by `FloppyBootstrapVerifier` before `READY` is recorded. An
HTTP response alone never makes bootstrap ready.

Changing server, account, or credential creates a new opaque provider instance.
Outstanding deliveries for the old instance are terminally cancelled, never
retried against the new server, and the new instance receives fresh canonical
state through bootstrap. Disconnect removes Floppy from SECONDARY and cancels
its outstanding deliveries without changing local data or the MAIN provider.

## Floppy as main tracker

Floppy can be MAIN only on a server whose `/api/v1/info/` reports
`sync_items_v1`, which is persisted as the connection's `canSyncItems`
capability. `TrackingConfigurationService` refuses to make Floppy MAIN without
it. At startup, `repair()` hands MAIN back to a READY non-Floppy SECONDARY (or to
no provider) if the capability is gone, and cancels Floppy's deliveries.

`FloppySyncEngine` runs the MAIN pass:

1. The operations `SyncCoordinator` selects go out through the normal Floppy
   transport, with the same idempotent client event IDs and exact timestamps.
2. The first pass reads `GET /api/v1/sync/items/snapshot/`. Later passes follow
   `GET /api/v1/sync/items/changes/?cursor=`. The cursor is stored per
   connection in `SyncStateEntity` under `floppy_items:<connectionId>`, in the
   same Room transaction as the items it covers.
3. Plays are matched to local plays by exact instant, so rewatches stay
   distinct. Local plays are deleted only when the change feed reports an item
   with `in_library: false`. A play missing on Floppy is never treated as a
   deletion, because the bootstrap sends one watch per movie. Partial play
   removals on Floppy are not propagated.
4. Items with an unsent or newer local intent are skipped, and the cursor never
   moves past a skipped item, so its remote state is read again once the intent
   is delivered.

Switching the main tracker (Settings > Floppy > Main tracker) first runs a
forced sync with the current MAIN. It then resets the Floppy cursor when Floppy
is becoming MAIN, swaps the roles (the old MAIN becomes SECONDARY) and syncs
again. When MAIN is removed, a READY SECONDARY takes over.

## Authentication and transport

CineTrack connects with an API key or with **Sign in with Floppy**. The sign-in
uses OAuth authorization code with PKCE at `/o/`, the built-in public app
`cinetrack` and the redirect URI `cinetrack://floppy`. CineTrack renews the
OAuth tokens itself and sends them as `Authorization: Bearer`.

Credentials are stored in Android Keystore-backed secure storage. An API key is
sent only as `X-API-Key` to the configured Floppy origin. Public
`/api/v1/info/` requests carry no credential. Redirects are disabled, TLS uses Android's normal
certificate verification, and logs redact authentication headers.

HTTPS is the default and recommended transport. Explicit cleartext HTTP is
accepted only for loopback, RFC1918/link-local IPv4, IPv6 ULA/link-local, or
deliberately supported `.local` hosts, and only when the user enables **Allow
insecure local HTTP**. Public HTTP, including lookalike hostnames such as
`fc-example.com`, is rejected; CineTrack never downgrades HTTPS automatically.

## Consumption semantics

Floppy permits multiple Consumptions for one media item. CineTrack keeps each
Consumption's ID, status, dates, progress, score, and creation metadata. A
resolver selects the active library Consumption separately from completed play
history and chooses the latest completed play deterministically.

Library status updates read the media detail first. Existing active rows are
patched through the exact history-entry route; a new Consumption is created only
when no suitable active row exists. `NONE` removes only the active row and
preserves completed plays. Movie watched operations read history before append
and use the operation timestamp. A coupled CineTrack movie completion
(`LIBRARY_STATUS=COMPLETED` plus `MOVIE_WATCHED` in one source generation) is
coalesced into exactly one completed Consumption; standalone completed TV writes
are also read-before-create and idempotent.

Movie unwatch operations use the versioned provider-neutral `v2|...` context
containing the desired library status, whether a previous watch existed, and
its exact timestamp. A known never-watched mutation is a no-op. Legacy rows
without a trustworthy timestamp never delete an arbitrary Consumption. Exact
matching removal preserves other plays, and the desired library status remains
available to Simkl.

Episode watched uses `POST .../{season}/episodes/{episode}/watch/` after a
read/verify check. Episode unwatched sends
`DELETE .../{season}/episodes/{episode}/watch/` (which removes the latest play)
until Floppy answers 404. The old `POST .../drop/` route is no longer used: it
re-created the show and season with a dropped episode, so removed shows came
back as In progress.

## Provider boundary

Floppy DTOs remain inside `data.sync.floppy`. The provider receives only the
immutable operations selected by `SyncCoordinator`, captures one immutable
connection session for each pass, and never creates a second provider queue.
Every modern Floppy delivery stores the opaque `providerInstanceId` captured at
enqueue time; `SyncCoordinator` only delivers rows whose ID matches the current
instance. Connection activation is serialized with provider I/O before the
instance switch, preventing an A-targeted operation from being sent through B.
Provider errors are mapped to structured `TrackingSyncError` values.

