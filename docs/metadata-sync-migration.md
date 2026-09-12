# Metadata and reconciliation migration map

The focused repositories now define the dependency direction used by the UI:

| Responsibility | Public owner | Current implementation boundary | Legacy callers |
| --- | --- | --- | --- |
| Discover rails, pagination, filters, search | `DiscoveryRepository` | `DefaultDiscoveryRepository` delegates to the existing TMDB-backed metadata implementation while behavior is preserved | notification/legacy paths through `CineTrackRepository` |
| Media details, seasons, episodes, cast, ratings, recommendations, trailers, collections | `MediaRepository` | `DefaultMediaRepository` compatibility adapter; transport mapping remains below the repository boundary | `ReleaseNotifier`, legacy façade |
| People search, person details, viewing insights | `PeopleRepository` | `DefaultPeopleRepository` adapter over `MediaRepository` | older widget/notification paths |
| Region-specific watch providers | `WatchProviderRepository` | `DefaultWatchProviderRepository` adapter over `MediaRepository` | settings compatibility methods |
| Library writes and derived progress | `LibraryRepository` | `RoomLibraryRepository` | façade delegates |
| Air dates and release timestamps | `ReleaseScheduleRepository` | `DefaultReleaseScheduleRepository` | façade delegates |

`CineTrackViewModel` uses the focused Discovery, People, Watch Provider, Media, Library, and SyncCoordinator dependencies directly. `CineTrackRepository` remains a compatibility façade for existing notifications, widgets, exports, authentication, and the still-incremental metadata/full Simkl migration.

## Reconciliation boundary

`TrackingSnapshot`, `MediaIds`, `LocalTrackingSnapshot`, `SyncReconciler`, `ReconciliationResult`, `LocalMutation`, and `SyncConflict` are provider-neutral. The reconciler performs no I/O and evaluates a three-way baseline (previous remote, current local, current MAIN snapshot). A local value is authoritative only when a field-specific durable operation matches its current value and source version; arbitrary dirty or stale rows do not protect a field. MAIN-only remote changes are applied locally without outbound writes, while true concurrent edits become conflicts.

Only the configured MAIN provider may pull and reconcile. SECONDARY is dispatched through `push` only. Every logical `SyncOperation` can have independent provider delivery rows, so acknowledgements and failures are retried per provider. Existing delivery targets retain the role captured at enqueue; role changes affect new operations only. Simkl DTOs and request mapping are contained in `SimklTrackingProvider`, with the proven full-import path isolated behind `SimklSyncEngine` until its Room commit logic is fully migrated.

```text
Local mutation
      |
Logical SyncOperation
      |
      +-- Simkl delivery (MAIN or SECONDARY)
      |
      +-- Floppy delivery (when configured and supported)
```

