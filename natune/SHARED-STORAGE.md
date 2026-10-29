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
use adaptive admissions under the1GB temporary-space contract in
STORAGE-FIRST-PLAN.md, and stop cleanly without replaying uncertain work.
The current reader still serializes reads under original cache monitors;
admission concurrency alone is not parallel cache-copy implementation.

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
4. For a document intentionally retained on the phone, preserve its URI and
   original song/rendition relationship in the app's offline index. For a
   disposable computer-transfer staging document, do not advertise it as a
   permanent playback URI; independently verify the corresponding saved Fuji
   copy and confirm temporary cleanup before releasing its budget. Preserve
   album, playlist, favorite and play-history identity in either lane.
5. Before removing an original phone song, verify complete matching source and
   computer-copy evidence and the user's applicable selection. Preserve the
   three explicit KEEP albums. Nolan's later clarification does not require
   Screening Room Omni acceptance first for additional music outside the earlier
   priority list. Stop/cancel the exact download request and wait for owner
   settlement, then remove only the eligible unchanged cached source after fresh
   reader/writer checks. Confirm removal and download-state consistency.
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
remain for the owning job's explicit recovery/cleanup; it does not delete a
source. Android specifies that document creation allocates a new ID, but the
client cannot enforce that a particular third-party provider obeys the rule.
Some reviewed providers can overwrite or return before remote durability. An
actual provider's creation, write and settlement behavior must be established
before this adapter is selected; a successful create call alone does not prove
an existing file was protected.

`MediaStoreAudioDestination.java` is a separate source candidate for a local
Android 10+ MediaStore audio folder. It creates a pending audio row on a selected
real volume, checks its owner, volume, folder and pending state, and implements
the existing verified-copy destination. It publishes only the original copy
result for the same live source and destination after another full readback,
and rechecks the published bytes before reporting success;
an uncertain publication is not automatically retried. It never removes a
source or claims a computer copy. The actual app still must select a folder,
admit peak space, bind a durable encrypted operation and reconcile uncertain
rows before this can serve as a Move or future-download route. It does not make
an arbitrary SAF folder or Fuji receiver equivalent to MediaStore. Hosted
successor run 40923679222 passed 48 synthetic MediaStore/copy controls
and compiled the adapter against the real Android SDK;
`media-store-recovery-verification.json` pins the exact source and artifact.
The successor returns even an unexpected provider URI to the owner before
validation so the owner can retain it for encrypted recovery. A null/throwing
insertion remains an uncertain original operation. This is not a device/provider
or Move acceptance test.

`NatuneFolderCopy.java` now joins the selected-span lease, the existing shared
temporary-budget mirror and the local MediaStore destination into one attempt.
It requires the real owner to retain the original encrypted Begin, returned
destination and verified-copy observations in order. The reservation stays
charged on success and failure. It preserves returned provider URIs and the
last entered phase for original-operation reconciliation; it never retries,
deletes a source, transfers to a computer or releases a reservation. Its owner
callbacks are not an authentication mechanism or an implementation of the
Termux-owned journal. App selection, original cancellation/headroom, journal
IPC and recovery, actual codec/recording identity, UI and device acceptance
remain unbound. Hosted run 40925574995 passed 68 integrated synthetic-owner
controls and compiled the actual Android source against the SDK;
`folder-copy-verification.json` records its exact source and full artifact
readback. Hosted successor run 40926425767 passed 75 controls, including the original-stop
check after the cache lease closes; `folder-copy-stop-verification.json` pins its
source and artifact. Newer run 40928837373 passed 106 synthetic folder-copy and
74 synthetic MediaStore controls, including a provider-selected collision name
and a name changing during publication, and compiled against the Android SDK.
`media-store-name-verification.json` pins its source, artifact and the separate
Rust journal controls. This is not an app, device or real journal test.

The copy result is in-memory byte evidence, not a deletion permit or completed
move. The source lease/coverage reader is now implemented against the exact
original public ABI and controlled stand-ins; its actual UI/caller hook,
durable encrypted transaction custody, song-ID/content-URI index, UI entry and
settled DownloadManager/cache removal are still unbound. Real Android provider/grant,
low-space interruption/restart and playback/playlist retention acceptance are
not established by host controls or SDK compilation. The current public feed
and installed app do not include a Move to folder button.

The separate hosted verify-natune-shared-storage workflow exercises synthetic
byte-stream failure cases against the actual copy core and compiles the SAF
adapter against a real Android SDK. It performs no APK build or phone action.
Its actual run/result must be recorded before claiming those checks passed.

Hosted run40886068466 at972a1a48e9b0c6b75d0a8cf989f4e8089ed24be6 passed49
actual core controls and real Android SDK compilation; complete artifact and
source SHA verification is retained in shared-storage-verification.json.
Hosted inspection40886323108 located both exact export coroutine bodies and
their captured original Context, download utility, destination/tree and song
fields. Its complete artifact hashes and selected public code metadata are
retained in storage-hook-inspection.json. The optimized cache receiver is
td1.p(String)TreeSet; an assumed NavigableSet-returning Media3 interface was not
found. This is evidence to inspect and bind the actual receiver before making
an extension call, not a reason to loosen its type/lock checks.

Follow-up inspection40886659336 confirms td1 is the retained abstract Cache
interface (flags1537), with sixteen abstract methods, rather than a concrete
cache owner. The new proof supersedes the first inspection in
storage-hook-inspection.json, retaining predecessor run40886323108 in history.
DownloadUtil's actual optimized type is playback/g, capturing two td1 fields
and its original manager. These descriptors identify hook inputs; they do not
establish a held read lease, a synchronized concrete implementation or durable
move custody. The next binding step must resolve the actual implementors and
their writer/reader lifecycle before any source removal path is admitted.

Later inspections40887293242/40888097008 resolve concrete wrappers dd6/c7b and
the original span/metadata fields. NatuneCachedSource.java now implements that
read-only source boundary with the original monitor order and mandatory stop
checks, validated by successor run40889712120 (46 cache-source controls plus49
copy controls). CACHE-SOURCE-BINDING.md records the exact ABI and scopes.
The original removal listener also purges all recognized source variants from
both caches, so the next removal binding must scope that callback, not attach
the old Delete action to a successful copy. No deletion path has been enabled.

## Reproducible source evidence

`shared-storage-source-review.json` pins the inspected public file bodies.
All links below refer to the exact installed-target source revision:

- [Storage roots](https://github.com/NatuneGroup/ArchiveTune/blob/b7e4dd444fea202a5c32e96f7b1199a74fe3ae70/app/src/main/kotlin/moe/rukamori/archivetune/storage/StorageLocationRepository.kt)
- [Download factories](https://github.com/NatuneGroup/ArchiveTune/blob/b7e4dd444fea202a5c32e96f7b1199a74fe3ae70/app/src/main/kotlin/moe/rukamori/archivetune/playback/DownloadUtil.kt)
- [Bulk export and separate deletion](https://github.com/NatuneGroup/ArchiveTune/blob/b7e4dd444fea202a5c32e96f7b1199a74fe3ae70/app/src/main/kotlin/moe/rukamori/archivetune/ui/screens/settings/ExportDownloadedSongsScreen.kt)
- [Single-song export](https://github.com/NatuneGroup/ArchiveTune/blob/b7e4dd444fea202a5c32e96f7b1199a74fe3ae70/app/src/main/kotlin/moe/rukamori/archivetune/ui/menu/SongMenu.kt)
- [Local indexing](https://github.com/NatuneGroup/ArchiveTune/blob/b7e4dd444fea202a5c32e96f7b1199a74fe3ae70/app/src/main/kotlin/moe/rukamori/archivetune/localmedia/LocalSongScanner.kt)

## Reuse the native local-document playback route

The exact public target source already routes content/file/android.resource
URIs through directFactory before the two cache layers. The native resolver
also returns content URIs before YouTube resolution. This can support an
original song ID with a different physical document URI: those are separate
DataSpec fields. MediaStore scanning alone instead uses the content URI as the
song ID; setting SongEntity.isLocal alone supplies no file location.

NatuneDataSpecBinding.forSharedDocument now implements the native constructor
conversion while preserving original key, byte range and flags. It requires a
complete positive resource extent, canonical GET, a native content URI with an
authority and an in-bounds range. Fragment offsets, POST/HEAD, request bodies,
conflicting Range headers and file/network redirects refuse. HTTP headers/body
are removed rather than sent to a document provider. The original spec is not
mutated. Supplied URI/extent are inputs, not verified-map or grant authority.

The actual hook must run BEFORE SchemeRoutingDataSource chooses its factory.
Doing this only in the upstream resolver below CacheDataSource could serve an
old cached rendition instead of the verified document. The persisted original
song/rendition map, URI access/recovery, exact bytecode hook and prefetch/error
recovery guards remain unbound. In particular, existing error/prefetch code
still tests whether the original media ID itself is local; it must not silently
re-resolve a moved file through a network source on a local-access failure.

shared-document-playback-source-review.json retains the exact source bodies'
hashes and selected lines. This is source and request-conversion evidence,
not phone playback acceptance or an installed Move action.

Hosted run40900141658 at7e10a53f1433737cb4b333e159da732609ab81e3 passed43 native
request controls, including the20 new shared-document cases, plus49 copy,
46 cache,41 budget and10 coroutine controls and real Android SAF compilation.
shared-document-request-verification.json retains full artifact/source hash
readback. The native request/URI classes in those controls are ABI stand-ins;
no actual grant, document, installed native router or phone playback was tested.
