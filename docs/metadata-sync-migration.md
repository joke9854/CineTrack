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

`TrackingSnapshot`, `MediaIds`, `LocalTrackingSnapshot`, `SyncReconciler`, `ReconciliationResult`, `LocalMutation`, and `SyncConflict` are provider-neutral. The reconciler performs no I/O and applies deterministic timestamp rules. Pending/dirty local state is protected from stale MAIN snapshots, and remote-origin mutations are explicitly marked `REMOTE_SYNC` so they cannot create feedback-loop writes.

Only the configured MAIN provider may pull and reconcile. SECONDARY is dispatched through `push` only. Simkl DTOs and request mapping are contained in `SimklTrackingProvider`; the legacy full-import path remains behind the façade until its proven Room commit logic is fully migrated.

