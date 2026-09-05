# CineTrack optimization and design notes

Work starts from 0.69. `main` retains that baseline; development continues on
`beta-testing`. Intermediate commits use `[skip release]` so a partially
completed design pass is not distributed to installed apps.

## Build and runtime

- Release resource shrinking is enabled alongside R8. No dynamic
  `Resources.getIdentifier` references were found requiring keep rules.
- Personal TMDB/MDBList keys are entered in Settings. Their BuildConfig defaults
  are always empty, even when environment or local Gradle properties contain keys.
  Only the SIMKL application client ID is embedded, as authorized by the owner.
- Baseline Profile configuration and startup/Progress/detail journeys are included.
  Generation is an explicit connected-device task; see `baselineprofile/README.md`.
  CI compiles the generator, but no app-specific profile or measured cold-start
  improvement is claimed before a real device run.
- Search debounces for 300 ms, cancels superseded work, propagates cancellation
  through repository fallbacks, and rejects results for a newer pending query.
- Six date formatters cover the eight original call sites. A locale-keyed bundle
  preserves runtime language changes without repeatedly parsing patterns.
- Episode titles use a synchronized 750-entry LRU. Per-import results survive
  eviction until the returned history is assembled. Episode fetches remain
  individual requests, not season-level requests.
- Coil's existing singleton uses 15% memory and a 150 MiB disk limit.
- Foreground resume reuses the eight-hour due check and sync mutex; initial
  startup does not start a second concurrent foreground sync.
- Shared HTTP clients retry 429 once. Seconds and HTTP-date Retry-After values
  are supported. Delays over 30 seconds propagate the response rather than
  retrying earlier than the server requested; cancellation is checked while waiting.

## Visual boundaries and accessibility

- Typography uses seven roles: 10, 12, 14, 16, 18, 22 and 29sp. The 38sp hero
  title and 46sp statistic remain explicit exceptions. Existing weights, shadows,
  explicit line heights and letter spacing are retained.
- Corners consolidate to 8/12/16/22dp and pill shapes within a 3dp tolerance.
  Tiny heatmap cells, proportional artwork corners and 28/32dp sheet tops remain.
- Distinct artwork and layered surface colors retain exact values under shared
  palette names. Close neutral overlays use shared glass tokens.
- Padding uses 4/8/12/16/20/24/32dp tokens only within a 2dp tolerance.
  Large bottom/hero clearances, dynamic inset padding and other exceptions remain.
- Action labels cover episode info, watched/unwatched controls and key visibility.
  Decorative icons next to text stay silent. English/Italian season counts,
  episode counts, season progress and runtime templates use resources.
- Live Haze blur is scoped to the bottom navigation on Android 12+ devices not
  classified as low-RAM. Other devices retain the translucent fallback. Neutral
  glass borders use a directional highlight; semantic selection/status colors
  are preserved.

## Device validation still required

No emulator or physical Android device is connected to this workspace. Verify
scrolling with the navigation blur on the target device before extending it to
additional surfaces. Check text scaling, TalkBack, touch comfort and artwork
layout on-device; compilation does not establish visual equivalence or frame time.

The initial broad benchmark assemble task compiled successfully but then tried
to run instrumentation without a device. CI now targets generator compilation
explicitly, and automatic profile generation during ordinary builds is disabled.
