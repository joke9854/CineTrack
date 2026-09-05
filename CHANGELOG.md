# CineTrack 0.70 testing

- Search waits briefly while you type and cancels superseded requests, preventing older results from replacing a newer query.
- Returning to CineTrack checks whether synchronization is due, preserving the existing eight-hour interval and avoiding overlapping foreground syncs.
- API requests handle temporary rate limits with one bounded retry, respecting Retry-After.
- Episode-title and image caches have explicit limits; date formatting reuses locale-aware formatters. Episode history titles still load individually.
- Shared typography, corner, color, spacing and animation tokens make the interface more consistent. Poster layouts use a common 2:3 ratio.
- Episode and season controls have larger touch areas. Settings switches announce their on/off state, and additional action labels and Italian translations improve accessibility.
- Layout-shaped loading placeholders replace several spinners and respect disabled system animations. Explicit app-triggered tap vibrations are removed consistently; visual feedback remains.
- Bottom navigation gains live background blur on supported Android 12+ devices, with a translucent fallback elsewhere and refined neutral glass borders.
- Personal TMDB and MDBList keys are configured only in Settings and are no longer embedded by local or GitHub builds. Existing saved keys are retained. The SIMKL application client ID remains included.
- Release builds now shrink unused resources and verify the signed APK before publishing it with its SHA-256 checksum.

Baseline Profile tooling and app journeys are configured. Generating the app-specific profile and measuring startup/scrolling performance still require a connected device. Navigation blur also needs on-device visual and scrolling checks before extending it to other surfaces.
