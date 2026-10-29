# Additional provider interface review — 29 October 2026

Nolan requested SpotiFLAC-style additional providers alongside Natune's existing
Qobuz route. This review selected public source metadata only; no music-service
request, login, session grant, registry installation or token access occurred.
The named ecosystem has several projects/forks; a matching name alone does not
identify Nolan's installed app or establish current upstream authority.

Public comparison source: `mornyeapp/Mornye-Extension` commit
`abc88a27fcd5c8f6591d4efc6521de26e3de317d`. Its README describes source-backed
packages, reproducible `.sflx` builds and offline mocked regression tests.
Its source license is LGPL-3.0-only with GPL terms/notices retained. This is a
provider-port comparison input, not adoption of that extension host.

| Manifest | Observed compatibility requirement |
| --- | --- |
| qobuz-web1.2.23 | Lossless fallback tier, network/storage/file capabilities, declared15second network timeout. |
| tidal-web1.2.9 | Lossless tier plus native container conversion; at most4transfer attempts, bounded retry delays, validated resume, persistent checkpoints and refresh on401/403. |
| amazon2.3.14 | Hi-res tier plus native container conversion, network/storage/file capabilities and a host-selected session path. |

Those are declared capabilities, not proof of actual codec, provider health or
authorized access. The README says signed-session providers omit `callbackUrl`
so the host supplies its own session-grant callback. Natune's existing app is
not that host. Morphe MPPs and `.sflx` provider packages have different loaders
and execution contracts; importing a registry does not bind its JavaScript,
storage, download, session or conversion functions into Natune.

Preferred integration direction: reuse Natune's existing source chain, matching,
decryption/container and expiry/retry machinery; port reviewed selected adapter
logic into that owned app path instead of silently adding another app's runtime.
Preserve complete provenance and applicable license notices. Bind any required
session callback to an actual authorized owner; metadata, a public endpoint or a
shared token does not create that authority. Required checkpoint persistence must
have its actual privacy/retention contract before use. No provider adapter is
implemented by this review. Full native/container FLAC validation remains an
independent requirement even for a manifest labelled lossless or hi-res.

Primary pinned sources:

- [Registry source and development contract](https://github.com/mornyeapp/Mornye-Extension/blob/abc88a27fcd5c8f6591d4efc6521de26e3de317d/README.md)
- [Qobuz manifest](https://github.com/mornyeapp/Mornye-Extension/blob/abc88a27fcd5c8f6591d4efc6521de26e3de317d/sources/qobuz-web/manifest.json)
- [Tidal manifest](https://github.com/mornyeapp/Mornye-Extension/blob/abc88a27fcd5c8f6591d4efc6521de26e3de317d/sources/tidal-web/manifest.json)
- [Amazon manifest](https://github.com/mornyeapp/Mornye-Extension/blob/abc88a27fcd5c8f6591d4efc6521de26e3de317d/sources/amazon/manifest.json)
