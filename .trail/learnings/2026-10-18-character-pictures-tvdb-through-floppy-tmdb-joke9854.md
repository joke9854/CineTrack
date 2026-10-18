---
title: "Character pictures: TVDB through Floppy, TMDB stays the cast"
author: Joke9854
date: 2026-10-18
branch: beta-testing
cost: { minutes: 50, tokens: 163641 }
touches:
  - app/src/main/java/com/cinetrack/CineTrackApplication.kt
  - app/src/main/java/com/cinetrack/data/repository/AppPreferences.kt
  - app/src/main/java/com/cinetrack/data/repository/CineTrackRepository.kt
  - app/src/main/java/com/cinetrack/data/sync/floppy/FloppyModels.kt
  - app/src/main/java/com/cinetrack/data/sync/floppy/FloppyTrackingProvider.kt
  - app/src/main/java/com/cinetrack/data/sync/floppy/network/FloppyApi.kt
  - app/src/main/java/com/cinetrack/data/sync/floppy/network/FloppyRemoteDataSource.kt
  - app/src/main/java/com/cinetrack/domain/Models.kt
  - app/src/main/java/com/cinetrack/ui/screens/DetailScreens.kt
  - app/src/main/res/values-it/strings.xml
---

## Decided
- The cast row stays TMDB (names, roles, actor photos). Floppy only adds TVDB character pictures on top; without Floppy, without the capability, or on any failure the row is TMDB-only.
- Floppy GET /api/v1/media/{tv|movie}/{tmdb|tvdb}/{id}/characters/ (api.cinetrack_character_views, capability api_extensions.cinetrack_characters_v1, scope watchlist:read in api/scopes.py VIEW_SCOPES). TV resolves the TVDB id with tmdb.resolve_tvdb_id_for_tmdb_show; movies via IMDb -> tvdb.search_remote_id -> "movie" entry.
- CineTrack matches TVDB characters to TMDB cast by normalized actor name (preferring the same character), then by a character name only one TVDB character has; generic roles (Himself, Narrator...) never match. domain/CharacterImages.kt.
- Cast / Characters headlines switch like the Film / Serie header, shown only when some cast member has a picture; a character without one keeps the actor photo.

## Tried and ruled out
- Simkl as the source for character pictures (the original board plan): the user pointed to TVDB.

## Watch out
- A new Floppy view returns 403 to every scoped token until it is listed in api/scopes.py VIEW_SCOPES.
- TVDB v4 sends the people type as a number in `type` (3 Actor, 4 Guest Star) and its name in `peopleType`. The older tvdb._normalize_characters compares `type` to strings, so it may sort actors under crew.
- Floppy test_public_endpoints.test_info_endpoint already failed before this change (api_extensions missing from its expected key set).
- Saved connections learn new capabilities only on refresh, so FloppyTrackingProvider.characterImages also checks the public /info once per process.
