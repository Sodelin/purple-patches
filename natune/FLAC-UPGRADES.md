# Find FLAC upgrades for existing downloads

Requested by Nolan29October2026. This feature joins the full FLAC/provider work;
it is not implemented merely by the existing playback guard. Public source
pins are in `upgrade-source-review.json`. No user downloads were inspected.

The Downloads page should offer **Find FLAC upgrades** for the whole downloaded
library, with optional album/song selection. The scan reports verified FLAC,
FLAC upgrade available, no current match, needs inspection and errors separately.
Search can be repeated when a source or its available recordings changes. An
existing completed download remains playable while a candidate is sought and
verified. FLAC files with no trustworthy codec evidence need inspection rather
than filename-based classification. The current source's configured quality or
FormatEntity label is not sufficient proof of stored bytes.

## Actual app entry points and necessary changes

| Layer | Existing pinned source | Required integration |
|---|---|---|
| Downloads toolbar | DownloadLibraryScreen.kt:277–284 actions has search only | Add an explicit upgrade action and results/selection UI; keep ordinary search. |
| UI actions | DownloadLibraryViewModel.kt::runAction | Add a distinct scan/stage operation with cancellation and visible results; its existing per-key jobs currently cancel prior actions. |
| Library source | DownloadRepository.kt::observeDownloads | Capture completed DownloadRequests, actual resource/cache generations and original Song/album/playlist relationships. Use the full library, not only current search-filter results. |
| Existing retry | Media3DownloadRepository.resume | Failed requests are re-added; a completed request just has stop reason cleared. Neither selects/validates a new rendition. |
| Existing deletion | Media3DownloadRepository.remove | Calls DownloadManager.removeDownload. This is destructive removal, not an upgrade commit. |
| Candidate resolution | DownloadUtil.kt:710–790 resolveDownloadStream | Split search/matching from its immediate metadata writes; preserve original metadata throughout preview/staging. |
| Rendition cache identity | DownloadUtil.kt:805–808 | Current key is source:mediaId. Add an owned staged generation and a verified selection/swap path; reusing this key can retrieve or overwrite existing bytes. |
| Existing cache prefetch | DownloadUtil.kt:615–690 | Writes directly into playerCache; its error handlers removeResource(cacheKey). Use only a new staged identity, never the original key. |

`resolveDownloadStream` calls enrichSongMetadataFromDeezer before a match and
persistSourceFormatEntity before returning a candidate. Calling it as a
supposed read-only scan would change the current library. The upgrade lane must
first expose a noncommitting candidate result through a selected app adapter,
then commit verified candidate metadata with the final cache selection.

Existing per-song source pins bypass TitleMatch for that selected source
(DownloadUtil.kt:756–760). A pool candidate or explicit source pin is still not
proof of recording identity, whole file or FLAC. Preserve the chosen recording
and original pin semantics; a source choice must not silently approve a different
live/studio/remastered/clean/explicit rendition. Reuse the original TitleMatch
implementation as one check, alongside exact identifiers and actual duration/
whole-file verification, rather than inventing a second incompatible matcher.

## Staging and replacement acceptance

1. Capture the completed original and its exact resource generation; retain
   song/library identity, current metadata, artwork links, playlist membership,
   album/track/disc relationships, source pin and playable original bytes.
2. Inspect original stored media through its actual cache/decryption/container
   path. Skip trustworthy complete FLAC; search for lossy or unverified media as
   selected, clearly marking unknown evidence. Do not transcode old lossy audio
   into FLAC and call it an upgrade.
3. Search the configured compatible source catalog for the same recording. Keep
   source/rendition/matching evidence; distinguish unavailable, expired access,
   unsupported source, no match and network failure. Re-searching later must be
   useful rather than caching permanent no-match decisions.
4. Stage selected replacements under separate operation/resource generations.
   Check actual storage needs before transfer and retain the old original.
   Downloads respect the user's cellular preference; a metered connection alone
   must not force a lossy replacement or refuse an otherwise allowed download.
5. Verify actual complete FLAC/container/decoded length and recording match,
   not just a prefix/provider claim. Handle decryption, expiry, valid ranges,
   partial files and cancellation. Header verification is not this acceptance.
6. Recheck the original generation and serialize the owning app/cache writer.
   Retain required encrypted commit/recovery state before changing any pointer.
   Select the verified staged rendition and update its metadata/download index
   coherently. A DB transaction alone cannot make cache writes atomic: crash
   recovery must reconcile both sides using the original commit state.
7. Retire the old rendition only after the new selection is durably verified
   and all original usage/preservation gates allow it. No remove-then-redownload,
   silent cache eviction, generic cleanup or raw failure log. Unknown/partial
   commit cannot start a second replacement with a new identity blindly.

App acceptance must cover single tracks and albums, duplicate tracks/playlists,
existing cache hits, no match then a later match, inaccurate provider FLAC labels,
wrong versions/previews, low storage, cellular data, URL expiry, interruption,
concurrent playback/download/removal, changed metadata/cache generation and
recovery after each commit boundary. The button is not complete until its actual
Morphe insertion, receiving app adapter and this whole flow work together.
