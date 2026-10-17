---
title: "Improvement audit: what CineTrack already has"
author: Joke9854
date: 2026-10-17
branch: beta-testing
cost: { minutes: 3, tokens: 31086 }
---

## Decided
- Crash/ANR reporting: ACRA core only (no logcat/prefs/device id), writes sanitized JSON to filesDir/crash-outbox from ACRA's process; a WorkManager worker uploads to Floppy POST /api/v1/cinetrack/crash-reports/ when capability `cinetrack_crash_reports_v1` is advertised. 201/200 already_received/400 delete the file; 401/403 stop; 429/5xx/network retry.
- Baseline profile: add Macrobenchmark tests only; the user generates the profile on a device themselves (generation would install a non-minified release over the real app).

## Tried and ruled out
- Replacing security-crypto: already done. SecureCredentialStore (Keystore AES-GCM) is the store; EncryptedSharedPreferences remains only as a lazy one-way migration read. Keep it.
- MDBList ratings and ICS calendar export: already implemented (MdbListService in Apis.kt, exportCalendar in CineTrackViewModel).

## Watch out
- Check the code before proposing features: dependency lists miss features built without a library (MDBList, ICS, baseline profile module :baselineprofile).
- No committed baseline-prof.txt exists yet; README in baselineprofile/ explains generation.
