# Smart Downloads and signed-in playlist synchronization

Nolan29October2026 clarified Smart Downloads is the primary offline feature,
ahead of Road Trip buffering. Use a user-selected GB storage threshold, default
off until configured, with a rotating mix of familiar and new recommendations
and a preference for discovery. Do not hardcode500tracks or estimate FLAC storage
using YouTube's compressed-file sizes. Road Trip remains the optional queue/local
lookback behavior; it does not replace this recommendation collection.

The requested ideal is YouTube's actual Smart Downloads selection for the signed
in account. Installed public resource labels establish the feature exists, not
that its personal selection is an exposed feed. Preserve that as an explicit
receiving-source gap. If a supported source only supplies recommendations/mixes,
identify it accurately; do not call it a mirror of the private Smart Downloads
selection. No model-generated mix or new inference is selected for this task.

## Actual existing YouTube playlist path

At pinned upstreamb7e4dd444fea202a5c32e96f7b1199a74fe3ae70:

- `SyncUtils.isLoggedIn` checks the app's original login-cookie presence.
  `isYtmSyncEnabled` independently checks YtmSyncKey (defaulttrue).
- `syncSavedPlaylists` fetches YouTube.library("FEmusic_liked_playlists").completed(),
  filters PlaylistItem entries and excludes special LM/SE collections. It upserts
  returned playlist rows, then syncs selected playlists' contents. Selected IDs
  restrict song-content synchronization, not the initial playlist-row upsert.
- `LibraryPlaylistsViewModel.sync` invokes saved-playlist and auto-sync paths.
  `LibraryPlaylistsScreen` chooses a separate Spotify screen when that library
  source is selected. The actual account/sync/library-source state matters.

This proves existing source support, not why Nolan's/friend's playlists were
missing. No account/cookies/preferences or live playlist contents were inspected.
A YouTube Music saved-playlist response is also not proof of every YouTube
video-playlist type. Check actual identity, supported playlist categories,
complete pagination, sync errors and display/filter path through the app's
original authorized account receiver before claiming all playlists visible.
Source hashes are retained in smart-source-review.json.

## Downloaded playlist follows later edits

The user corrected the earlier automatic-all-playlists idea: do not subscribe to
or download every signed-in playlist. A user explicitly downloads a playlist;
then that selected playlist's later additions/removals should reconcile its
managed offline membership. Other playlists remain untouched. Preserve the
original account and playlist identity and complete remote revision before
applying changes. Partial pagination, failed sync, sign-out, changed account or
an unavailable playlist must not become a desired empty collection/deletion.

Use per-collection references: a song can be needed by a manually saved album,
multiple downloaded playlists, Smart Downloads and Road Trip simultaneously.
Removing it from one list releases that list's reference only. Retire bytes only
when no other reference/protected current/recent playback/recovery need remains.
Keep the original file until additions/replacements required for the new selected
collection are verified complete. No automatic change to user-created playlist
contents or unrelated library rows is requested.

## Implemented pure policy increment

`OfflineSelectionPlan.java` captures ordered source candidates, immutable rendition
identities, original held resources and a chosen byte budget. It deduplicates
resources, accounts shared complete bytes once within a selection, distinguishes
unknown-size/over-budget candidates and stages work without issuing I/O.
A second fresh-snapshot phase refuses incomplete replacements, larger-than-reserved
actual bytes or a changed source revision before proposing ownership releases.
Sole-owner unprotected temporary files are eligible only after the actual owner
commits the new selection. These lists are data, not delete commands, native
write permits, authenticated account evidence or a durable commit protocol.

`RoadTripLookback.java` protects current and previous actual playback renditions.
A->B keepsA/B; B->C can releaseA but protectsB/C. Going back changes that history,
and duplicate callbacks do not prematurely release the previous distinct song.
Its protection merges with existing playing/recovery/other-use holds. It does not
subtract a queue index, so shuffle/reorders do not accidentally identify history.

`OfflineSelectionPlanControls` exercises these policies with controlled snapshots.
Actual app source/receiver, complete codec/recording verification, scheduler,
privacy-preserving durable ownership, cache locks/commit/recovery, new settings
UI and Morphe insertion remain required. No user media was read, staged or deleted
by implementing these pure policy classes.

## Screenshot transcription and confirmed target

Nolan confirmed the target is Natune (formerly ArchiveTune), resolving the
relay's Notion/Natune ambiguity. Only a textual transcription was supplied; no
image bytes were accessed. The exact private reference/transcription is retained
encrypted. Its displayed capacity is historical screenshot text, not a current
phone free-space measurement. No toggle state is established by the relay.

The reference Downloads & storage controls include Show device files, Download
over Wi-Fi only, Audio quality, Video quality, Smart downloads with a song-count/
approximate-byte display, and a separate Recently played songs collection.
Its example Smart Downloads count is500; recently played describes up to200.
Those are product-reference values, not hardcoded new limits or a trustworthy
FLAC bytes-per-song conversion. Keep the user's byte ceiling and actual original
quality/source policies authoritative. The new settings should show meaningful
estimates from actual candidate/complete-file sizes and distinguish GB/GiB.

Preserve separate choices for recommendations and recent playback. They may
share actual files, which must not be downloaded or charged twice in the common
managed storage total. Candidate reservations and final unique physical bytes
need a shared admission check across collections; per-collection policy calls
alone do not prove that shared global bound. Wi-Fi/battery conditions belong to
the automatic scheduling policy, with explicit permitted-network choices rather
than assuming a carrier plan from a5G label. Defaults/configuration still require
user setup; screenshot text does not authorize toggling or downloading anything.

Pinned Natune DownloadsSettings.kt currently exposes auto-download-on-like,
source order, external downloader and export/clear UI; that file has no Smart
Downloads or Recently Played scheduling/control implementation. Reuse its real
settings navigation and original preference ownership while adding the actual
patch runtime receiver. Do not relabel auto-download-on-like or one-song URL
prefetch as the requested Smart Downloads feature.

## Current policy verification

Hosted run40879354779 at source4e0fef4 passed37controlled selection/retention
checks. offline-selection-verification.json retains full artifact/member hashes.
Cases include byte overflow/budgets, missing candidate sizes, incomplete sources,
not-ready replacements, changed source revision, shared/manual ownership,
protected current/previous tracks, repeated transitions and going back. Protected
resources retain their collection reference until the protection ends, so later
retirement can still be attributed to the correct collection. Actual Android
owner/source authentication, common scheduler admission, persistence/cache commit,
UI and deletion authority remain unbound; these tests confer none of them.
