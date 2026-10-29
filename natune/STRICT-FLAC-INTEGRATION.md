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
