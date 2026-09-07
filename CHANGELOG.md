# CineTrack 0.77 testing

- Unify floating glass: navigation, library status/bulk-edit sheets, Progress/Library ordering sheets, trailer and previous-episode prompts, actor/full-cast sheets, and the changelog dialog share the same 16dp blur material.
- Capture page content once for floating surfaces and release the capture when it is no longer needed. Keep scrolling cards lightweight and remove nested blur from actor-credit rows.
- Standardize circular toolbar controls to 48dp targets with 40dp surfaces, matching tint, border and shadow. Movie/TV and episode detail panels share the same background treatment on all supported devices.
- Replace SIMKL notification icons with a monochrome CineTrack progress ring and play mark across reminders and sync alerts.
- Restore leading icons for Seasons and episodes and Where to watch, using matching icon size, gap and alignment. Preserve the 24dp section spacing and 12dp ratings-to-description gap.
- Always show Where to watch, with an English/Italian message when no streaming, rental or purchase services are listed in the selected region.
- Move Trailer into a compact 48dp play button to the left of Mark watched, with the library action on the right. Keep the trailer player behavior and add an accessible label. Allow the primary action to wrap on narrow screens.

No new dependencies. Real blur remains limited to Android 12+ devices that are not classified as low-RAM; other devices use the shared translucent fallback. Compilation, unit tests, lint, migration and signed APK checks run in CI. On-device visual and scrolling verification remains pending.
