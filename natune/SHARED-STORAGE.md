# Shared-folder destination and verified move: pinned source review

Reviewed 29 October 2026 against Natune N202610191717/code6900 source
`b7e4dd444fea202a5c32e96f7b1199a74fe3ae70`. This is a source finding and
implementation contract, not an installed feature or a completed media move.
The source-only inspection performed no download, cache removal, permission
request, setting change, application launch or build. Existing patch source
and all media remain separate and preserved.

## Existing controls

No supported direct shared-folder/SAF destination was found in this version's
native download, storage-location, download-settings and export paths.

| Source | Actual behavior |
| --- | --- |
| `di/AppModule.kt:198–210` | Download cache is a Media3 SimpleCache with NoOpCacheEvictor, rooted through StorageLocationRepository. It is not a user-visible complete audio-file destination. |
| `playback/DownloadUtil.kt:287–307,383–417` | Download factories write through CacheDataSink into that cache. DownloadManager receives the same cache. Selecting an external downloader does not replace these sinks. |
| `storage/StorageLocationRepository.kt:628–706` | The allowed roots are internal app files or ArchiveTune below getExternalFilesDirs. The picker excludes primary emulated external storage. The removable-volume option still selects app-specific cache storage, not an arbitrary shared Music folder or SAF document tree. |
| `storage/StorageLocationRepository.kt:221–258` | Relocation moves cache directories, saves the chosen root and schedules an app restart. It does not turn cache fragments into ordinary named music files. No relocation was invoked. |
| `ui/menu/SongMenu.kt:1462–1478,1519–1531` | Single-song export streams cached spans into a content URI, keeping the cache. This avoids a separate tagging temp file but still creates an additional complete destination copy. |
| `ui/screens/settings/DownloadsSettings.kt:205–215` | Export entry opens settings/storage/export_songs. It is an export page, not the future-download destination setting. |
| `ui/screens/settings/ExportDownloadedSongsScreen.kt:172–279` | Folder picker selects SAF export destination. Bulk export assembles a complete file in cacheDir/export_tmp, tags it, copies it to a new document, then deletes the temp file. The original download remains. Peak additional allocation can approach two track copies, even though tracks are processed serially. |
| `ui/screens/settings/ExportDownloadedSongsScreen.kt:315–343` | Separate delete action removes selected source-key cache entries and asks DownloadManager to remove requests. It is not coupled to verified export. |

The bulk export's output stream is nullable at lines267–273: a null stream can
reach onSuccess without writing a document body. A success toast/count is not
sufficient evidence to delete a source. Existing span concatenation also does
not by itself prove that spans cover the complete original byte range.
The existing YouTube export branch changes the filename to .mp3 while its own
comment describes underlying MP4/AAC bytes. Filename changes are not transcoding.

Normal1DM's supported direct-file destination is a separate receiver contract.
Natune currently sends it a YouTube watch page; this supplies neither a supported
file stream nor Natune's private cached bytes. See EXTERNAL-DOWNLOADER.md.

## Priority one: Move to folder

Add a distinct **Move to folder** action alongside the existing copy export;
do not silently change the meaning of Export. The selected UI action should
process one track at a time and stop cleanly without replaying uncertain work.

1. Select the exact completed download/rendition and its cache keys. Check full
   expected length, contiguous spans, current playback/readers and active
   DownloadManager writers under actual owner serialization. Keep manual music,
   other renditions, shared owners and unrelated player-cache entries intact.
2. Create a new destination document in the chosen folder; never overwrite an
   existing user file by matching its title. Require real writable and readable
   streams. Stream source bytes directly to that document with bounded memory,
   counting bytes and hashing. No complete export_tmp staging file. Preserve the
   real container/extension. Metadata rewriting that requires a full temp file
   must be a separate explicit option, not a hidden allocation in this path.
3. Close the output, reopen the same document, and verify its complete byte count
   and SHA-256 against the selected complete source. Confirm the URI is usable
   by the app. Any failure, cancellation, revoked grant or changed source retains
   the cached original. A flush, file existence, count/toast or free-space delta
   alone is not verification.
4. Retain the verified content URI and its original song/rendition relationship
   in the app's offline index before removing the source. Preserve album,
   playlist, favorite and play-history identity. Stop/cancel the exact download
   request and wait for the owner to settle it so it cannot recreate cache spans.
5. Remove only the exact unchanged cached source after verification, indexing
   and fresh reader/writer checks. Confirm removal and download-state consistency.
   An interrupted removal is an uncertain transaction to reconcile, not a fresh
   request to copy/delete again. Required claim/result/recovery records must use
   the existing encrypted-before-persistence contract; do not add plaintext
   URI/catalog journals. No source deletion authority exists in this document.

This still needs room for one complete destination track while its original is
retained. It avoids whole-library duplication and the extra full tagging temp
file; it cannot guarantee success with less than one track's available space.

## Priority two: future downloads directly to a folder

Add a destination setting with a user-selected MediaStore audio collection or
SAF tree and the appropriate Android picker grant. The writer must consume the
existing permitted resolved/decrypted audio stream directly into a new owned
pending destination, without first writing the complete download or player
cache. Preserve genuine codec checks; do not route YouTube through1DM to bypass
its unsupported-source behavior.

An ordinary path preference cannot accomplish this: Media3 SimpleCache's spans,
locking and index assume filesystem cache storage. Implement a document-aware
download job/sink and completion record, or an equivalently complete supported
downloader replacement. Handle content-provider seek/resume limitations and
partial-file cleanup only for documents created by that job. Never mark a
partial or unverified document as a complete offline track.

Bind original song/rendition IDs to completed content URIs and route offline
playback to those URIs before cache/network resolution. Existing
localmedia/LocalSongScanner.kt uses MediaStore records and creates local-song
identities; an arbitrary SAF document is not guaranteed to be enumerated by
that scanner. A rescan alone therefore does not preserve the remote track's
playlist/history identity or downloaded badge. Keep the original source/cache
only if it has an independently owned runtime role; do not silently retain a
second full download copy after the new destination has been adopted.

## Implemented streaming increment

`runtime/dev/purple/patches/natune/VerifiedStreamCopy.java` implements actual
fixed32KiB-buffer copy, complete expected-length checks, full destination
readback and SHA-256 comparison. Missing/short/extra/corrupted streams, source
revision changes, original cancellation and stream close failures refuse a
verification result. It creates no temp file, logs or persistent record and
has no source-removal method. Source ownership must be an actual held native
lease, not just a caller claiming a length or complete flag.

`SharedDocumentDestination.java` supplies the real ContentResolver and
DocumentsContract stream boundary for a newly created document under an
already-authorized SAF tree. It obtains no grant. It reserves output opening
before attempting it, so an uncertain open does not automatically retry, and
reopens that exact content URI for verification. Incomplete created documents
remain for the owning job's explicit recovery/cleanup; no old user file or
source can be deleted by this adapter.

The copy result is in-memory byte evidence, not a deletion permit or completed
move. Exact Media3 source lease/coverage, original caller serialization, durable
encrypted transaction custody, song-ID/content-URI index, UI entry and settled
DownloadManager/cache removal are still unbound. Real Android provider/grant,
low-space interruption/restart and playback/playlist retention acceptance are
not established by host controls or SDK compilation. The current public feed
and installed app do not include a Move to folder button.

The separate hosted verify-natune-shared-storage workflow exercises synthetic
byte-stream failure cases against the actual copy core and compiles the SAF
adapter against a real Android SDK. It performs no APK build or phone action.
Its actual run/result must be recorded before claiming those checks passed.

## Reproducible source evidence

`shared-storage-source-review.json` pins the inspected public file bodies.
All links below refer to the exact installed-target source revision:

- [Storage roots](https://github.com/NatuneGroup/ArchiveTune/blob/b7e4dd444fea202a5c32e96f7b1199a74fe3ae70/app/src/main/kotlin/moe/rukamori/archivetune/storage/StorageLocationRepository.kt)
- [Download factories](https://github.com/NatuneGroup/ArchiveTune/blob/b7e4dd444fea202a5c32e96f7b1199a74fe3ae70/app/src/main/kotlin/moe/rukamori/archivetune/playback/DownloadUtil.kt)
- [Bulk export and separate deletion](https://github.com/NatuneGroup/ArchiveTune/blob/b7e4dd444fea202a5c32e96f7b1199a74fe3ae70/app/src/main/kotlin/moe/rukamori/archivetune/ui/screens/settings/ExportDownloadedSongsScreen.kt)
- [Single-song export](https://github.com/NatuneGroup/ArchiveTune/blob/b7e4dd444fea202a5c32e96f7b1199a74fe3ae70/app/src/main/kotlin/moe/rukamori/archivetune/ui/menu/SongMenu.kt)
- [Local indexing](https://github.com/NatuneGroup/ArchiveTune/blob/b7e4dd444fea202a5c32e96f7b1199a74fe3ae70/app/src/main/kotlin/moe/rukamori/archivetune/localmedia/LocalSongScanner.kt)
