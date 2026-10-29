# Strict FLAC integration boundaries

Reviewed source: NatuneGroup/ArchiveTune commit
`b7e4dd444fea202a5c32e96f7b1199a74fe3ae70`. These are public source observations,
not a live provider test, user-setting inspection or completed patch.

The mobile-data patch removes five audio-specific low-data results. It preserves
the global connectivity helper and does not guarantee a FLAC response.

## Required next hooks

In `playback/DownloadUtil.kt`, lines 322–339 can return already-cached bytes
before fresh resolution. Strict FLAC must validate those bytes and bind codec
evidence to that exact rendition/cache key; changing only network resolution
would leave a lossy-cache bypass. Lines 342–375 fall back to YouTube after the
preferred source chain. Strict mode must return an explicit unavailable result
instead of entering this lossy fallback.

Lines 793–815 construct eligible cache keys and selected-source DataSpecs.
Lines 823–882 dispatch Qobuz, Tidal, Qobuz backup, Deezer and JioSaavn. The
Deezer branch can produce encrypted FLAC or MP3; validation belongs after the
existing decryption layer. A provider enum or quality preference alone is not
media evidence. Lines 991–1023 preserve evidence conditionally when format
metadata is redeclared; retain that rendition binding rather than stamping
every response as FLAC.

Streaming separately enters `MusicService.resolveMultiSourceDataSpec` at 9414,
its deadline variant at 9431 and `resolvePlaybackDataSpec` at 11031. Inspect and
fingerprint these actual binary paths before introducing the shared validator.
Do not infer that download-only enforcement also covers streaming or prefetch.

The validator must inspect actual decoded upstream/container bytes without
losing the initial bytes, handle seeks/ranges and distinguish incomplete input
from a wrong codec. It must reject lossy content, previews, HTML/error responses
and wrong recordings. Duration/version matching, bounded retries and an explicit
failure result remain part of the requirement. No transcoding provenance claim
follows merely from finding FLAC headers.

Additional providers require reviewed public interfaces and authorized access;
no provider requests or credential acquisition were performed by this review.
Existing source support is not evidence of current provider health.

## Produced source and exact interface evidence

`stream-inspection-verification.json` binds hosted run40866800058 to source
273db99 and complete artifact hashes. Independent dexlib and the stdlib DEX
reader agree on26data-source types and17factory methods. The actual DataSource
interface is `Lr8/dm2;`, open is `g(Lr8/km2;)J`, and read is inherited from its
DataReader ancestor. The initial same-declaring-class heuristic missed that
interface; the corrected hierarchy traversal is retained in `inspect_streams.py`.
`stream-hooks.json` contains only public APK type/method references, code sizes
and fingerprints; no arbitrary embedded strings or app state.

Candidate factory methods include `Lr8/kd;->v()Lr8/dm2;`,
`Lr8/wm5;->v()Lr8/dm2;`, `Lr8/xk1;->v()Lr8/dm2;` and
`Lr8/py7;->v()Lr8/dm2;`. These are actual release signatures, not yet proof
of complete playback/download/cache/prefetch coverage. In particular a wrapper
below CacheDataSource can miss existing cached bytes. DataSpec field names and
types are pinned, but position/length semantics need constructor/use binding;
do not infer those semantics from field letters alone.

The source's remaining low-data call in `MusicService.kt:8673–8683` controls
`createPlayerCacheDataSourceFactory(cacheWriteEnabled=...)`, rather than the
selected audio quality. Keep that distinction when reviewing `Lr8/py7;`;
removing every remaining low-data result globally is not strict codec validation.
The cache factory also has concrete return methods
`Lr8/ae1;->a()Lr8/be1;` and `b()Lr8/be1;`. A dynamic interface proxy cannot
replace those final concrete return types. The next adapter must preserve real
class/field contracts and validate above any reused-cache bypass, including the
offline downloader's own cache path. Factory names alone are not an admission
boundary.

`FlacPrefixProbe.java` is implemented pure Java for eventual Android extension
integration, using actual byte arrays only. It has no I/O, caller authentication
or persistence. `flac-prefix-verification.json` records hosted run40867651333
with129controls and a standard RFC9639 example. It checks native STREAMINFO,
metadata traversal and first-frame header properties/CRC8. Nonzero ranges
require canonical-prefix evidence. Non-native containers require their actual
container decoder; they are never relabelled FLAC by MIME or filename.

HEADER_VERIFIED explicitly does not verify complete frame payloads, CRC16/PCM
MD5, recording identity, previews or a whole download. A control deliberately
shows corrupted later payload retaining header evidence, so no consumer may
treat that state as full media admission. No strict patch has adopted this probe
yet. The next runtime join must bind actual DataSource/cached-byte provenance,
preserve prefix bytes and ranges, and pair complete decoder evidence with the
existing rendition/source-retry policy. The existing PCM spectral authenticity
analyzer is separate from encoded-codec enforcement and can be unavailable in
bit-perfect output; it cannot be the only strict-FLAC gate.

## Implemented decoder-input and request-copy adapters

`FlacProbeStream.java` now preserves bytes consumed by prefix inspection and
replays them unchanged to an owned decoder input. Nonzero ranges inspect the
canonical prefix, close that reader, then check the requested reader's original
rendition key/start/known total. Original stops are checked before/after I/O;
changed source, invalid blocking reads, bad response lengths and exhausted
caller-selected prefix budgets refuse and close the selected reader. Scratch
storage is reused per stream and cleared on close; no file-size quota is added.

The bounded overload retains the original requested length. Range completion
is distinct from actual source EOF and neither means complete codec admission.
A7byte original range remains7bytes even when a longer prefix was inspected;
known out-of-bounds seeks refuse before opening the second reader.
`flac-stream-verification.json` pins hosted run40871952290 with53stream controls
and23request-copy controls, in addition to the129prefix controls. Source/stop
stand-ins and Android ABI fixtures are explicitly not actual app authority.

`NatuneDataSpecBinding.java` implements the exact request-copy ABI. Actual DEX
constructor, buildUpon and subrange observations in `data-spec-verification.json`
confirm `b=uriPositionOffset`, `f=position`, `g=length` against the app's pinned
Media3 1.10.1 dependency. URI, headers, body snapshot, key and flags are preserved;
no replacement URL/credentials or network operation is exposed. Canonical
resource requests refuse fragmented URIs, non-GET/body replay and explicit Range
headers instead of silently rewriting their meaning. Ordinary range copies
preserve resource offsets; the real cache/decryption owner must select the
canonical resource above a fragment.

Remaining actual host bindings: select the release factory/lifecycle callsite,
derive a real immutable resource revision/cache-generation or validated entity
identity, retain original cancellation and per-request bounds, and pair the
adapter with actual complete container/codec/recording validation. A mediaId or
URL string alone is not that revision. The Source.responseLength contract means
an actually known response length, not blindly the requested DataSpec.length
or an open() return that only repeats that request's ceiling. No adapter test
certifies a live CDN, a persistent cache entry or an Android decoder.

The classes under `test-fixtures/` are hosted controls ONLY. Do not copy them
into an extension or APK. The selected published mobile-data MPP and feed remain
unchanged; these adapters are not yet installed or presented as a strict patch.

Primary API references:

- https://github.com/androidx/media/blob/1.10.1/libraries/datasource/src/main/java/androidx/media3/datasource/DataSpec.java
- https://developer.android.com/reference/androidx/media3/datasource/DataSource
- https://developer.android.com/reference/androidx/media3/common/DataReader

Primary source:
https://github.com/NatuneGroup/ArchiveTune/blob/b7e4dd444fea202a5c32e96f7b1199a74fe3ae70/app/src/main/kotlin/moe/rukamori/archivetune/playback/DownloadUtil.kt

## Local decoder policy prototype 0.2.0-dev.1

`NatuneLocalFlacPatch.kt` adds an optional default-off local decoder policy to
this exact original build/version code/signer. `codec-entry-verification.json`
pins the actual release's optimized signatures and original bodies. The guard
compares extractor-parsed `Format.sampleMimeType` (`Lr8/yf4;->o`) against
`audio/flac`, rather than relying on a filename, resolver label or HTTP MIME.
It preserves the complete original method on a match and refuses other codecs:

- `Lr8/ge7;->z0(Lr8/hv3;Lr8/yf4;)I`: unsupported subtype.
- `Lr8/ge7;->y0(Lr8/yf4;)Z`: disables non-FLAC hardware bypass/offload.
- `Lr8/ge7;->Q(Lr8/hv3;Lr8/yf4;Z)Ljava/util/ArrayList;`: returns an actual empty
  ArrayList for non-FLAC decoder selection. R8 narrowed upstream's List return;
  Collections.emptyList would violate this release's type.

These hooks leave decoded PCM output and the video renderer untouched. Input
from an existing cache still reaches the parsed decoder format. Upstream
MediaCodecInfo.canReuseCodec refuses reuse across changed sample MIME types;
actual release reuse behavior, lifecycle/error presentation and Android decoder
acceptance remain separate checks, not inferred from the source reference.
The guard cannot detect audio transcoded to FLAC earlier in its provenance.

The dedicated `build-natune-local-flac.yml` builds a separate development bundle
and applies both this guard and the five mobile-data overrides to the pinned
original on a hosted runner. It does not publish or replace the selected0.1.0
feed. `VerifyNatuneLocalFlacPatch.java` independently checks the exact emitted
prefixes and branch-to-original targets, runs decisions/negative controls over
those actual instructions, then removes only verified prefixes for the existing
complete program/metadata/resource comparison. It rejects guard try handlers or
changed register layout; unsupported extra changes still fail the full verifier.
Its small interpreter proves dispatch/refusal of the prefix, not actual decoding.

This remains an increment toward the requested strict FLAC/download/provider
feature. Whole-download integrity, correct recording/full-length matching,
source retry/quality-tier selection, remote Cast and additional provider adapters
remain unfinished. No phone install/playback or provider/model request is part
of this source/build step. Existing selected public bundles are unchanged.

Primary pinned implementation references:

- https://github.com/androidx/media/blob/1.10.1/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/audio/MediaCodecAudioRenderer.java
- https://github.com/androidx/media/blob/1.10.1/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/mediacodec/MediaCodecInfo.java
- https://github.com/MorpheApp/morphe-patcher/blob/v2.0.0/src/main/kotlin/app/morphe/patcher/patch/Compatibility.kt

Actual hosted run40875761792 at sourceb7abfd2 built the prototype MPP and
successfully patched/validated the pinned APK with both selected patches.
`local-flac-build-verification.json` retains full artifact/member hashes,
generated signer/version-code compatibility,39controls on the ACTUAL emitted
prefixes and the full27129class/114583method preservation result, including
24native libraries,12assets and1884non-DEX/signature entries. All current patch
source hashes matched the build provenance. These are static APK/build results;
no Android decoder, lifecycle, next-track transition or phone UI was exercised.

Transition follow-up `codec-transition-verification.json` pins hosted inspection
40876305620. The actual optimized `Lr8/je7;->b(Format,Format)` reads both parsed
sample MIME fields, compares them with Objects.equals and sets discard bit8 on
mismatch before the audio/video branch. This supports the upstream reuse mapping;
the bounded report stops before the entire368code-unit method ends, so it is not
a complete release control-flow or next-track playback proof.

The actual audio input-change entry is now identified too:
`Lr8/ge7;->e0(Lr8/ie3;)Lr8/bt2;`, flags17/registers6/29code units/no try blocks.
It takes `FormatHolder.z:Object`, casts/checks it, writes the input Format to
`ge7.l1` and calls `ne7.e0` before posting its original event. A next source
increment can enforce parsed FLAC at this lifecycle boundary with the original
renderer error mechanism, independently of initial format support/decoder reuse.
It must preserve FLAC's original event/lifecycle path and refuse unsupported
input explicitly. No such fourth guard or device-transition acceptance is claimed
by the three-guard0.2.0-dev.1 build.

## Whole-file verifier selection boundary

Android's documented `MediaCodec` raw audio output is signed 16-bit PCM by
default, with float output only when requested and confirmed in the returned
format. A generic successful decode therefore does not by itself prove that
24- or 32-bit source samples were preserved. Do not silently reduce sample
precision or classify a FLAC solely from that default output.

The reference libFLAC streaming decoder can check decoded PCM against the
STREAMINFO MD5, but that check is disabled by default and is turned off after
a seek or when the signature is absent. A successful `finish()` alone can
therefore mean no comparison happened. The caller must bind uninterrupted
complete input, actual nonzero expected checksum, decoder error/EOF and the
original source identity; unknown checksums need a distinct truthful result.
Even an actual matching PCM checksum does not prove which cache rendition the
user selected or whether it is the same recording. The FLAC format supports
4–32 bits per sample; retain the full range in a selected implementation.

An independently installed Termux decoder does not establish access from the
separately sandboxed NaTune app or a stable deployment dependency. The new
`FlacIntegrity.java` and `flac_integrity_jni.c` source candidate binds a complete
decode to the existing copy proof, checks original-source/stop/byte identity,
and uses pinned reference libFLAC 1.5.0 for decoded PCM/CRC validation. Missing
STREAMINFO checksum or total samples produces an explicitly indeterminate state.
Hosted run `40931287508` built the selected decoder, passed 21 public synthetic
controls including 8-, 16-, 24- and 32-bit inputs and damaged/unknown-metadata
cases, and cross-built its Android ARM64 library. Exact source and artifact
readback is recorded in `flac-integrity-verification.json`. Encoding and
decoding in that check used the same pinned reference project; it is not an
independent conformance suite. Native library packaging, R8/JNI
keep rules, the actual app callsite, recording/source selection, full-file
format alternatives and phone acceptance remain unbound; the current patch
materializer does not include this verifier. Nothing was installed or executed
on the phone.

Hosted attempt `40931041024` stopped during reference-decoder CMake
configuration because the runner had neither Pandoc nor prebuilt manpages.
No new decoder controls or Android cross-build ran in that attempt. The
successor explicitly disables installation of those manuals; it does not alter
the selected audio source or replace the outstanding verification.

Primary references: [Android MediaCodec raw audio](https://developer.android.com/reference/android/media/MediaCodec#raw-audio-buffers),
[libFLAC streaming decoder](https://xiph.org/flac/api/group__flac__stream__decoder.html),
and [RFC 9639 STREAMINFO](https://www.rfc-editor.org/rfc/rfc9639.html#section-8.2).

## Added requirement: upgrade existing non-FLAC downloads

Nolan29October2026 requests a **Find FLAC upgrades** action on the Downloads page,
with a repeatable later search when expanded sources gain a matching rendition.
This is part of the full Natune patch objective, not satisfied by decoder refusal.
Search the existing completed downloads, including albums; derive actual codec
from bytes/validated media evidence rather than filename or a resolver label.
Already verified FLAC should be retained. Unknown codec needs inspection, not an
assumption that the existing file is lossy or safe to remove.

Find the same recording/album edition, including artist, title, duration,
explicit/clean/live/remaster/version distinctions and track/disc relationships.
Present available replacements, unchanged verified FLAC, no current match and
inspection/errors distinctly. Re-running the action can discover a newer match;
a failed or cancelled attempt must leave the playable original intact.

Stage a candidate separately from the current download/cache identity. Verify
complete file/codec/length/integrity and matching before an owner-controlled
atomic replacement; retain original library identity, playlists, album grouping,
metadata and appropriate source provenance. Do not remove the old download first
or use remove-then-download as an upgrade. Space admission must include old plus
staged bytes; no action may silently evict another original. Persist any required
operation/recovery state through the existing selected privacy contract. Crash,
error, cancellation, changed cache generation and uncertain commit need explicit
recovery; no blind retry/deletion. Actual app/cache writer and UI joins remain
required. No user library was inspected or modified to prepare this requirement.

Initial public source surfaces at the pinned Natune commit:

- `ui/screens/downloads/DownloadLibraryScreen.kt` has the Compose downloads
  toolbar and per-collection controls, rather than a generic XML layout hook.
- `downloads/ManageDownloadsUseCase.kt::observe/mapSnapshot` selects completed
  downloads; its public actions currently expose pause/resume/remove only.
- A second tap on an existing completed download must not accidentally invoke
  removal or reuse the same old cached bytes as its supposed replacement.

The expanded community-provider catalog, reviewed compatible adapters and actual
matching/FLAC verification are shared requirements for this action. An available
provider name, public pool listing or configured premium tier is not evidence
that a matching complete FLAC file exists. Provider availability/access and any
required account/session bindings remain explicit per-source contracts.

## Input-change successor under verification: 0.2.0-dev.2

Source now also guards `ge7.e0(FormatHolder)` before it changes input state or
calls the base codec renderer. FLAC enters the exact original first instruction.
A non-FLAC input uses the original inherited renderer error factory with the
same renderer and actual Format, IOException cause, isRecoverable=false and
Media3 ERROR_CODE_DECODING_FORMAT_UNSUPPORTED4005. No arbitrary runtime exception
or new error transport is substituted. The independent verifier checks the
actual emitted error/argument/branch instructions, transitions across successive
fixture formats and rejects changed error code, recovery flag, source field,
Format argument and return-instead-of-throw mutations. These fixture checks are
not an Android runtime or UI/next-track acceptance claim.

Primary error/lifecycle references:

- https://github.com/androidx/media/blob/1.10.1/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/BaseRenderer.java
- https://github.com/androidx/media/blob/1.10.1/libraries/common/src/main/java/androidx/media3/common/PlaybackException.java

Hosted lifecycle successor40877133662 at source606cab9 passed actual APK patching
with all four guards and54emitted-instruction controls. Full original remaining
program/resources passed the same27129class/114583method comparison. Exact
artifact/member/source hashes and report are in
local-flac-lifecycle-build-verification.json. This proves the source/build/static
increment; actual Android next-track/error UI and decoder acceptance remain open.
Nolan's additional Find FLAC upgrades, Road Trip buffering and unified patch
settings requirements are tracked in FLAC-UPGRADES.md and
ROAD-TRIP-AND-SETTINGS.md; they are not claimed by this bundle.
