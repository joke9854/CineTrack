# Baseline profile generation

Configuration and journeys are provided; no fabricated profile is checked in.
Use a connected API 33+ device (or a rooted API 28+ emulator). On the
non-minified release app, complete onboarding, configure your own credentials,
and sync a representative library with an in-progress show. Keep credentials
and personal database backups outside Git.

Generate with a cached show's TMDB id and title:

```sh
./gradlew :app:generateReleaseBaselineProfile \
  -Pandroid.testInstrumentationRunnerArguments.mediaId=123 \
  -Pandroid.testInstrumentationRunnerArguments.mediaType=TV \
  -Pandroid.testInstrumentationRunnerArguments.mediaTitle='Your cached show'
```

Review and commit `app/src/release/generated/baselineProfiles/` after generation.
Then assemble release and inspect the APK for `assets/dexopt/baseline.prof`
(compiled binary, not the text source). Library-provided profiles alone do not
prove that the app-specific journeys were recorded. Measure cold startup and
scroll frame timing on a physical device before claiming a speed improvement.
Normal CI builds must not require a connected device or generate profiles.
