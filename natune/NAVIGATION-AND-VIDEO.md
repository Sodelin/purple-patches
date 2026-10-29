# Persistent main navigation and FLAC music-video playback

Nolan29October2026 requested all three main destinations remain reachable from
deep album/artist browsing while preserving search/browse history. He also
requests music videos paired with matching FLAC audio and correct SponsorBlock
alignment/editing. Both belong to the full Natune patch scope. Source pins are
in navigation-video-source-review.json; no app video/UI playback test was run.

## Existing navigation source and owned change

MainActivity.kt:1516–1521 makes shouldShowNavigationBar true only at null/main
routes while search is inactive. Nested artist/album pages therefore hide the
bar. Existing navigateToTab:2040–2046 already uses saveState, launchSingleTop and
restoreState; reuse this to keep independent history while exposing top-level
navigation from deep pages. Preserve player/search/modal/TV/rail and keyboard
insets, selected-tab semantics, accessibility and fast repeated-tap coalescing.
Do not replace the entire back stack with a crude unconditional home restart.
Current long-press back conveniences are not an always-reachable three-tab UI.
Actual release visibility/callsite fingerprint and runtime toggle remain needed.

## Existing video/audio source and actual mismatch

VideoArtworkPlayer.kt already disables video audio and sets its player volume
zero; the main MusicService audio player is the source of sound. Preserve that
architecture. The local FLAC guard can refuse other parsed audio formats, but
matching/source selection and full audio acceptance remain independent.

The video drift loop directly computes videoPos-mainPos and seeks the video to
mainPos. SponsorBlockPlaybackController.tick directly seeks its audio player to
segment.endMs. These direct comparisons assume identical video/audio timelines.
Different intros, inserted nonmusic, edits and recording versions invalidate
that assumption. A constant global offset cannot cover multiple inserted cuts.

Select the same recording first, validate rendition identity/duration, and bind
an explicit piecewise mapping between the original video and audio time axes.
Treat SponsorBlock video intervals on their original axis, then map each skip
through the selected alignment. Only verified nonmusic/video edits should be
cut automatically; missing/ambiguous alignment must be explicit, not proof of
synchrony or permission to remove musical content. Never silently switch to a
lossy main audio rendition to make the timing appear to match. Preserve user
seek/back/speed, video readiness/cancellation, decoder/drift recovery and source
changes through their original owner. No new model or active audio test is
selected by this source work.
