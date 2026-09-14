# Floppy integration

CineTrack integrates with the current Floppy API described by the running
instance at `/api/docs/` and `/api/schema/`. The checked-in DTOs follow the
current media, Consumption, history, and episode routes; they do not assume a
historical API version.

## Role and bootstrap

Floppy is currently a SECONDARY, outbound-only provider. Floppy MAIN is not
enabled yet: its read endpoints are useful for connection checks, bootstrap
verification, and retry reconciliation, but CineTrack does not advertise a
bidirectional MAIN capability and rejects attempts to persist Floppy as MAIN.

When Floppy is configured as SECONDARY, bootstrap seeds the current canonical
CineTrack state through durable Floppy-only deliveries. Bootstrap operation IDs
are tied to the provider connection generation, so process restart does not
create a second completed Consumption. The milestone uses merge/seed semantics;
it does not claim that remote history absent from a partial read was deleted.

## Authentication and transport

The API key is stored in Android Keystore-backed secure storage and is sent only
as `X-API-Key` to the configured Floppy origin. Public `/api/v1/info/` requests
do not include the key. Redirects are disabled, TLS uses Android's normal
certificate verification, and logs redact authentication headers.

HTTPS is the default and recommended transport. Plain HTTP is rejected unless a
caller explicitly opts in and the host is a private/local destination
(RFC1918, loopback, link-local/private IPv6, or `.local`). Public HTTP is never
accepted and CineTrack never downgrades HTTPS automatically.

Changing the URL, account, or credential creates a new CineTrack connection
generation. The active credential is referenced by an alias, so a crash during
replacement cannot combine old metadata with a new token. Old HTTP clients are
invalidated when a credential or connection changes.

## Consumption semantics

Floppy permits multiple Consumptions for one media item. CineTrack keeps each
Consumption's ID, status, dates, progress, score, and creation metadata. A
resolver selects the active library Consumption separately from completed play
history and chooses the latest completed play deterministically.

Library status updates read the media detail first. Existing active rows are
patched through the exact history-entry route; a new Consumption is created only
when no suitable active row exists. `NONE` removes only the active row and
preserves completed plays. Movie watched operations read history before the
append and use the operation timestamp, making retries after an ambiguous
timeout idempotent. Movie unwatch and legacy history removal delete only an
identifiable exact Consumption; ambiguous legacy rows are reported unsupported
instead of deleting a whole title.

Episode watched uses `POST .../{season}/episodes/{episode}/watch/` after a
read/verify check. Episode unwatched uses the documented `POST .../drop/`
route, never the old DELETE shortcut.

## Provider boundary

Floppy DTOs remain inside `data.sync.floppy`. The provider receives only the
immutable operations selected by `SyncCoordinator`, captures one immutable
connection session for each pass, and never creates a second provider queue.
Provider errors are mapped to structured `TrackingSyncError` values.
