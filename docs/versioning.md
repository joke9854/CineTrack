# CineTrack beta versioning

Beta releases use a two-stage version sequence:

- `0.xx` for `0.00` through `0.99` (for example, `0.97`, `0.98`, `0.99`).
- After `0.99`, use `0.99.xx` with a two-digit patch number (for example, `0.99.01`, `0.99.02`).

Each beta release must increase both `versionName` and Android `versionCode`. The release workflow validates the format and progression before building or publishing an APK. Non-release maintenance commits should include `[skip release]` in the commit message.

