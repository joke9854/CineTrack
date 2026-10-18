---
title: Version catalog + Renovate; character pictures and stats dropped (2026-10-18)
author: Joke9854
date: 2026-10-18
branch: beta-testing
cost: { minutes: 17, tokens: 79740 }
touches:
  - app/build.gradle.kts
  - baselineprofile/build.gradle.kts
  - build.gradle.kts
  - docs/ROADMAP.md
  - gradle/libs.versions.toml
  - renovate.json
---

## Decided
- F3 Stats screen dropped: the user likes the statistics where and how they are in Progress.
- Character pictures dropped: Simkl's OpenAPI spec (api.simkl.org/openapi.json) has no cast, people or character data; TMDB has actor photos only. The user picked the version catalog as the next item instead (now 0.99.66).
- Dependencies live in gradle/libs.versions.toml. versionName/versionCode stay in app/build.gradle.kts because release.yml seds them from there.
- renovate.json: base branch beta-testing, commit suffix [skip release], Compose BOM updates disabled (bump by hand to the BOM in PrismalAGSL's POM), PrismalAGSL PRs carry a note about the BOM, Kotlin+KSP and Room grouped, majors need dashboard approval.

## Tried and ruled out
- A Simkl character-image source: no such endpoint exists.

## Watch out
- Verify catalog migrations by diffing `:app:dependencies` for release/unit-test/androidTest/ksp classpaths plus `buildEnvironment` before and after; they must be identical.
- `:baselineprofile` (com.android.test) has no releaseRuntimeClasspath configuration.
- Renovate only runs once the user installs the Renovate GitHub app on joke9854/CineTrack.
