---
title: "Floppy characters endpoint speed: external_ids, not the full TMDB title"
author: Joke9854
date: 2026-10-19
branch: beta-testing
cost: { minutes: 28, tokens: 712680 }
touches:
  - CHANGELOG.md
  - app/build.gradle.kts
  - app/src/main/java/com/cinetrack/data/repository/CineTrackRepository.kt
  - app/src/main/java/com/cinetrack/domain/CharacterImages.kt
  - app/src/main/java/com/cinetrack/domain/Models.kt
  - app/src/main/java/com/cinetrack/ui/screens/DetailScreens.kt
  - app/src/test/java/com/cinetrack/domain/CharacterImagesTest.kt
  - app/src/main/java/com/cinetrack/CineTrackApplication.kt
  - app/src/main/java/com/cinetrack/data/media/MediaRepository.kt
  - app/src/main/java/com/cinetrack/data/repository/AppPreferences.kt
---

## Decided
- Floppy's characters endpoint resolves the TVDB id from TMDB /{tv|movie}/{id}/external_ids (cached 30 days), never the full TMDB title; movies map IMDb -> TVDB through search/remoteid, cached 30 days including misses. TV series characters come from the raw series/extended payload without the translation request. Floppy 03481e21 + 99b2b19d.
- Result on the user's server (2026-10-19): new titles 0.4-1.1 s, down from 5-64 s.

## Tried and ruled out
- Fetching tmdb.tv()/tmdb.movie() to read the external IDs: full title with every append, 5-64 s for titles Floppy had never fetched; CineTrack closed the request first (nginx 499) and showed no characters.

## Watch out
- Requests over 1 s log cinetrack_characters_timing (tvdb_id_ms, characters_ms); read those before guessing which provider is slow.
- The user's Floppy serves requests from one process, so one slow request delays the next ones.
