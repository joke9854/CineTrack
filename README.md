# CineTrack

CineTrack is a Kotlin/Jetpack Compose movie and TV tracker for Android. It uses TMDB
for the catalogue, MDBList for ratings and an offline-first Room cache, and it can sync
with [Simkl](https://simkl.com) and with a self-hosted [Floppy](https://github.com/joke9854/Floppy)
server (`CineTrack-API` branch).

The app is in public beta (0.99.x). Releases are published as GitHub prereleases, and
the in-app updater installs them. See [CHANGELOG.md](CHANGELOG.md) for what changed in
each release and [docs/ROADMAP.md](docs/ROADMAP.md) for what's next.

## What it does

- **Discover**: trending and upcoming rails, an endless Mosaic layout, filters (genres
  to include or exclude, year, rating, runtime, language, streaming providers), saved
  presets and "Not interested".
- **Progress**: what to watch next per show, swipe left to mark watched, upcoming
  episodes, a calendar, history and viewing statistics.
- **Library**: plan to watch, watching, paused, completed and dropped, with bulk editing.
- **Details**: show, movie and episode pages with providers, ratings, cast or characters,
  collections, recommendations and trailers.
- **Sync**: Simkl or Floppy as the main tracker, with Floppy optionally as a secondary
  mirror. Watched times are kept to the exact instant, and rewatches count as separate plays.
- **Extras**: release and provider notifications with quiet hours, an Up Next home-screen
  widget, calendar (ICS) export, automatic backups with restore, crash reports sent to
  your Floppy server, and English and Italian translations.

## Data sources

| Source | Used for |
|---|---|
| TMDB | Catalogue, search, metadata, images, providers, episodes, people |
| MDBList | Extra rating sources |
| Trakt | Air times only (optional client ID); not a sync provider |
| Simkl | Library, watched history, playback, air dates (OAuth AUTH V2 with PKCE) |
| Floppy | Library and watched history (API key or "Sign in with Floppy"); see [docs/floppy-integration.md](docs/floppy-integration.md) |
| Room / DataStore | Offline state, pending writes and preferences |

You enter your own TMDB, MDBList and Trakt keys in Settings. They are stored encrypted
on the device and are never built into the APK. Only the Simkl client ID is embedded at
build time. Without a TMDB key, Discover stays empty and says so. The app never inserts
demo titles.

## Build

1. Open the project in Android Studio with JDK 17. The build uses AGP 9, compileSdk 37,
   targetSdk 36 and minSdk 25.
2. Copy `local.properties.example` to `local.properties` and set `sdk.dir` and
   `SIMKL_CLIENT_ID`.
3. For Simkl, register an AUTH V2 app of type "Mobile, desktop & browser apps" with the
   redirect URI `cinetrack://simkl`. It must match exactly, with no trailing slash.
   No client secret is needed.
4. Run the `app` configuration.

Dependencies live in [gradle/libs.versions.toml](gradle/libs.versions.toml), and Renovate
keeps them current. The Compose BOM is pinned to the one
[PrismalAGSL](https://github.com/styropyr0/PrismalAGSL) (the glass effects) is built against.

Build types: `debug`; `preview` (release with R8 and the baseline profile, signed with the
debug key, id `com.cinetrack.debug`, tagged `<version>-preview.<commit count>+<sha>`); and
`release`.

## Tests

```sh
./gradlew :app:testDebugUnitTest --tests '<Class>'   # one class
./gradlew :app:testDebugUnitTest                      # all JVM tests
```

Screenshot tests (Robolectric + Roborazzi) cover the Progress artwork card, a status pill
and an empty state in light and dark. Their reference images are in
`app/src/test/screenshots/`. CI compares them on every push. After an intended UI
change, re-record them, review the PNG diff and commit them:

```sh
./gradlew :app:recordRoborazziDebug --tests 'com.cinetrack.screenshot.*'
./gradlew :app:verifyRoborazziDebug --tests 'com.cinetrack.screenshot.*'
```

Room migration tests run on an emulator in CI. The baseline profile is generated on a
device; see [baselineprofile/README.md](baselineprofile/README.md).

## Releases

CI runs on every push to `beta-testing`. A commit whose message contains `[release]`
(after the version bump in `app/build.gradle.kts` and the `CHANGELOG.md` entry) is
validated first. Only then does `release.yml` build, sign and publish the
`v<version>-testing` prerelease with its APK and SHA-256 file. `[skip release]` validates
without publishing. `[skip migration]` skips the Room migration job when no schema
changed.

Signing uses these repository secrets: `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEY_ALIAS`,
`ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_PASSWORD` and `SIMKL_CLIENT_ID`. Keystores,
APKs and `local.properties` are git-ignored and must never be committed.

## Simkl troubleshooting

After you accept the Simkl authorization, Android should return straight to CineTrack,
and Settings > Sync should show Connected before the first sync starts. If authorization
or a request fails, the page shows the provider and HTTP error. Check the redirect URI
first: `cinetrack://simkl` and `cinetrack://simkl/` are different values.

## Attribution and license

The previous app was based on Showly 2.0. This rebuild preserves and adapts relevant API endpoint/data-model ideas from the user-supplied GPL project, so the included project remains distributed under the GNU GPL v3 in `LICENSE`.
