---
title: "Profile images, Trakt removal, TVmaze premium key, direct TVDB (0.99.78)"
author: Joke9854
date: 2026-10-25
branch: beta-testing
cost: { minutes: 30, tokens: 45737 }
touches:
  - app/build.gradle.kts
  - app/src/androidTest/java/com/cinetrack/data/local/AppDatabaseMigrationTest.kt
  - app/src/main/AndroidManifest.xml
  - app/src/main/java/com/cinetrack/CineTrackApplication.kt
  - app/src/main/java/com/cinetrack/crash/CrashReportSettings.kt
  - app/src/main/java/com/cinetrack/data/local/AppDatabase.kt
  - app/src/main/java/com/cinetrack/data/local/Daos.kt
  - app/src/main/java/com/cinetrack/data/remote/Apis.kt
  - app/src/main/java/com/cinetrack/data/remote/SharedHttp.kt
  - app/src/main/java/com/cinetrack/data/repository/AppPreferences.kt
---

## Decided
- Trakt removed: AirDateSource.DEFAULT = TVMAZE; AirDateSource.normalize() maps a saved "trakt" (or unknown) choice to TVmaze; resolveAirDate ignores rows whose source isn't in AirDateSource.all. MetadataSettingsRepository.retireTrakt() runs once at startup (deletes trakt_client_id, migrates the pref, deletes "trakt" episode_air_dates rows, reapplies dates).
- TVmaze premium key = username + API key as HTTP basic, only on /v1/user and /v1/auth/validate (used to verify). Public endpoints never get it; it changes no air times (TVmaze docs at static.tvmaze.com/apidoc/v1.yaml).
- Direct TVDB: TvdbCharacterSource (POST v4/login {apikey,pin} -> bearer in memory; series via TMDB tvdb_id, movies via TMDB imdb_id + search/remoteid). Floppy stays first; failure when neither source exists so nothing is cached.
- Profile images: filesDir/profile/<kind>-<time>.jpg (new name per save busts Coil), prefs keep the file name; backup puts base64 JPEG in settings.jsonl (profileAvatarJpeg/profileBannerJpeg, "" = none).
## Tried and ruled out
- java.util.Base64 (minSdk 25): use kotlin.io.encoding.Base64.
- fanart.tv site is behind a Cloudflare check; its mark was taken from the Jellyfin plugin image, TVmaze's from its apple-touch-icon, TVDB's from thetvdb.com/images/logo.svg.
## Watch out
- painterResource can't load <bitmap> XML, so PNG logos (drawable-nodpi) carry their attribution as a code comment in ServiceLogo.
