# CineTrack 0.78 testing

- Refine Settings and Progress around the shared glass style: white section headings, aligned settings rows, clearer summaries, consistent media-card corners and titles, proportional poster cropping, and a full-width watched-time statistic.
- Make the idle sync card compact and expand it during syncing or errors. Tap its status to open Sync operations, or connect Simkl when disconnected.
- Fix slow Discover pull-to-refresh: remove the unrelated forced Progress/episode/schedule rebuild, reject overlapping pulls, and cancel stalled Discover requests after 45 seconds while retaining saved rails. Existing bounded parallel fetching and atomic rail updates are preserved.
- Add Streaming services to Settings with a favorites summary, searchable two-column logo picker, selected count, country selection, and retained favorites across countries. Keep the Settings catalog independent of Discover filters and clear cached provider details after preference changes.
- Add subscription, rent, buy, free and ad-supported visibility filters for Where to watch. Keep subscription availability intact for notifications, distinguish filtered-out offers from unavailable titles, and preserve streaming preferences in backups.
- Use the shared floating blur, neutral edge and depth for the anchored long-press library-status popup.
- Reduce circular header surfaces from 40dp to 36dp while retaining existing icon sizes and 48dp header layout slots.
- Redraw the launcher and in-app brand mark with a dark glass surface, a pale-blue progress ring and a white play symbol. Include the Android themed-icon variant.

No new dependencies. Tests cover Discover timeout/cancellation and provider region/offer semantics. On-device visual verification and a timed refresh with a personal TMDB credential remain to be checked.
