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

Primary source:
https://github.com/NatuneGroup/ArchiveTune/blob/b7e4dd444fea202a5c32e96f7b1199a74fe3ae70/app/src/main/kotlin/moe/rukamori/archivetune/playback/DownloadUtil.kt
