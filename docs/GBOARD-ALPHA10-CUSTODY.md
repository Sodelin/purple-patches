# Gboard alpha.10 and companion dev.6 custody

The verified [alpha.10 bundle](gboard-alpha10-build-verification.json),
[patched unsigned APK](gboard-alpha10-apk-verification.json) and
[dev.6 companion](companion-022-dev6-verification.json) are hosted workflow
artifacts, not installation recommendations. All were read and hashed in
memory against their recorded checksums and source. On 29 October their hosted
expiration times were, respectively, 2026-11-05 19:18:44 UTC, 19:20:24 UTC,
and 19:09:13 UTC. These are provider metadata, not permanent custody.

The alpha.10 build ZIP was copied directly between the GitHub and Drive
connectors to the existing unshared private folder, with no large phone file.
Its full Drive readback and extracted main MPP matched the independently
verified hosted SHA-256 values in memory. The exact private file identity and
readback are held in an encrypted receipt using the existing identity. An actual
restored file on disk has not been verified; no original was deleted.

The separate dev.6 companion upload returned a tool error. Its outcome is
uncertain; a subsequent exact-name search found no match, but that does not by
itself prove no upload occurred. It was not retried. The unsigned patched APK
has not been archived. The phone was below the user's 20 GiB engineering
reserve; an attempted memory-file route was also denied across same-UID
processes. The bounded next step is to reconcile the companion upload outcome
through the existing Drive route, then verify any actual remote copy in full.
Retain the source and exact recorded hashes while that remains unresolved.

The public source commits and reproducible hosted workflow remain available.
An artifact, backup or static inspection does not establish the bearer-privacy,
caller authority, editor, model or actual phone acceptance gates. See
[ACCEPTANCE.md](ACCEPTANCE.md).
