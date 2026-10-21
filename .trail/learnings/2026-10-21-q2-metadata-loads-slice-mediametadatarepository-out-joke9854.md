---
title: "Q2 metadata loads slice: MediaMetadataRepository out of the facade"
author: Joke9854
date: 2026-10-21
branch: beta-testing
cost: { minutes: 18, tokens: 121542 }
touches:
  - app/src/main/java/com/cinetrack/CineTrackApplication.kt
  - app/src/main/java/com/cinetrack/data/repository/CineTrackRepository.kt
  - app/src/main/java/com/cinetrack/data/repository/LegacyMediaDataSource.kt
  - app/src/main/java/com/cinetrack/data/sync/ReleaseNotifier.kt
  - app/src/main/java/com/cinetrack/data/sync/SimklSyncWorker.kt
  - app/src/test/java/com/cinetrack/data/repository/BackupRoundTripTest.kt
  - app/src/main/java/com/cinetrack/data/repository/MediaMetadataRepository.kt
---

## Decided
- data/repository/MediaMetadataRepository owns the detail-page loads moved verbatim from CineTrackRepository: loadTagline, loadSeasonDetails, loadDetails, loadMedia, loadAllEpisodes, loadPerson, loadCast, loadCharacterPictures, loadEpisodes, loadCachedEpisodes, loadEpisode, loadTrailerKey, loadEpisodeCast, loadCollection, loadRecommendations, loadViewingPeople, loadRatings, plus the tagline/season/cast/character-picture/recommendation caches. Built in AppContainer before the facade (it shares the one DefaultReleaseScheduleRepository; loadAllEpisodes refreshes the schedule overlay through it).
- The facade takes it as `mediaMetadata` (ProgressCacheRepository gets mediaMetadata::loadEpisodes, refreshLocalUpNext uses its internal watchedEpisodeNumbers). LegacyMediaDataSource(facade, metadata) sends the detail loads to it; ReleaseNotifier.notifyUpcoming takes MediaMetadataRepository instead of the facade.
- Shared helpers became internal top-level in MediaMetadataRepository.kt: TmdbMediaDto.toEntity, tmdbGenreName, EpisodeCard.toEntity, EpisodeEntity.toDomain, AppPreferences.effectiveProviderRegion, cancellableResult.
- clearLanguageCaches is split: the container's MetadataSettingsRepository callback clears both the facade (episodeTitleCache) and mediaMetadata.

## Watch out
- episodeTitleCache/episodeTitleMisses, enrichHistoryLabels, cachedEpisodeTitle and loadUpNextEpisodes stayed in the facade: they belong with the progress cache + loadCachedState slice.
- Discover, search, searchPeople and streaming providers still go through the facade via LegacyMediaDataSource: their own slice.
