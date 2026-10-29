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

Primary source:
https://github.com/NatuneGroup/ArchiveTune/blob/b7e4dd444fea202a5c32e96f7b1199a74fe3ae70/app/src/main/kotlin/moe/rukamori/archivetune/playback/DownloadUtil.kt
