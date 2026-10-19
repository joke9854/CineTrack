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

On a Samsung phone with Secure Folder (a second Android user), the Gradle task
runs no tests ("Invalid userId -2") and then uninstalls `com.cinetrack`, which
deletes its data. Run the journeys with adb instead:

```sh
./gradlew :app:assembleNonMinifiedRelease :baselineprofile:assembleNonMinifiedRelease
adb install -r --user 0 app/build/outputs/apk/nonMinifiedRelease/app-nonMinifiedRelease.apk
adb install -r -t --user 0 baselineprofile/build/outputs/apk/nonMinifiedRelease/baselineprofile-nonMinifiedRelease.apk
# set the app up on the phone, then run each journey on its own:
adb shell "am instrument -w --user 0 -e class com.cinetrack.baselineprofile.BaselineProfileGenerator#progressAndDetail -e androidx.benchmark.enabledRules BaselineProfile -e mediaId 123 -e mediaType TV -e mediaTitle 'Your cached show' com.cinetrack.baselineprofile/androidx.test.runner.AndroidJUnitRunner"
adb pull /storage/emulated/0/Android/media/com.cinetrack.baselineprofile/BaselineProfileGenerator_progressAndDetail-baseline-prof.txt progress.txt
```

Each run empties the output folder, so pull a journey's file before running the
next one (`#startup` writes `BaselineProfileGenerator_startup-startup-prof.txt`).
`baseline-prof.txt` is the sorted union of both files; `startup-prof.txt` is the
startup file. Pass the whole `am instrument` command as one quoted string, so
the device shell keeps the title's spaces.
Then assemble release and inspect the APK for `assets/dexopt/baseline.prof`
(compiled binary, not the text source). Library-provided profiles alone do not
prove that the app-specific journeys were recorded. Measure cold startup and
scroll frame timing on a physical device before claiming a speed improvement.
Normal CI builds must not require a connected device or generate profiles.

## Macrobenchmarks

`StartupBenchmark` (cold with no compilation vs partial/profile, plus warm) and
`ScrollBenchmark` (Progress list frame timing) live in this module. Run on a
physical device with a configured library:

```sh
./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest   -Pandroid.testInstrumentationRunnerArguments.mediaTitle='Your cached show'
```

Warning: this installs a benchmark build of the app (`com.cinetrack`, release
config, signed with the debug key) on the connected device, replacing the
installed app and possibly clearing its data on uninstall. Use a spare device or
back up first. `ScrollBenchmark` is skipped (assume) when `mediaTitle` is missing
or not visible in Progress. Profile modes use `UseIfAvailable`, so a missing
profile does not fail the run. No credentials are read or embedded.
