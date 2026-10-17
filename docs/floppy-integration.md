# Floppy integration

CineTrack integrates with the current Floppy API described by the running
instance at `/api/docs/` and `/api/schema/`. The checked-in DTOs follow the
current media, Consumption, history, and episode routes; they do not assume a
historical API version.

## Role and bootstrap

Floppy is currently a SECONDARY, outbound-only provider. Floppy MAIN is not
enabled: its read endpoints are useful for connection checks, bootstrap
verification, and retry reconciliation, but CineTrack does not advertise a
bidirectional MAIN capability and rejects attempts to persist Floppy as MAIN.

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

## Authentication and transport

The API key is stored in Android Keystore-backed secure storage and is sent only
as `X-API-Key` to the configured Floppy origin. Public `/api/v1/info/` requests
do not include the key. Redirects are disabled, TLS uses Android's normal
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
read/verify check. Episode unwatched uses the documented `POST .../drop/`
route, never the old DELETE shortcut.

## Provider boundary

Floppy DTOs remain inside `data.sync.floppy`. The provider receives only the
immutable operations selected by `SyncCoordinator`, captures one immutable
connection session for each pass, and never creates a second provider queue.
Every modern Floppy delivery stores the opaque `providerInstanceId` captured at
enqueue time; `SyncCoordinator` only delivers rows whose ID matches the current
instance. Connection activation is serialized with provider I/O before the
instance switch, preventing an A-targeted operation from being sent through B.
Provider errors are mapped to structured `TrackingSyncError` values.

