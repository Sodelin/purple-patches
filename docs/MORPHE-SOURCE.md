# Maintained candidate delivery

The exact verified MPP, native ARM64 companion, wrapper, catalog/provenance,
corresponding authored sources and pinned upstream GPL source are retained in
private draft release425693294 (`v0.1.0-alpha.1`). All ten assets were downloaded
back and compared in full after upload. This is durable candidate custody;
it does not establish a published update source or phone acceptance.
See[private-candidate-release.json](private-candidate-release.json).

`tools/stage_candidate_release.py --stage-private-draft` reuses the authenticated
GitHub identity and operates in RAM. It requires a clean source repository,
refuses a public repository or published release, verifies pinned build bytes,
checks all existing asset bytes before adding anything missing, never replaces
assets, and verifies all uploaded bytes. An uncertain mutation is inspected
before retry. No APK, model or signing key is distributed. The first upload
attempt was rejected because gh stdin transport lacked fixed Content-Length;
fixed-length memory upload then passed.

## Manager 2.2.0 source contract

Reviewed official tagv2.2.0, commit
`2c2bdc6a8368fa790ca2e3a57b050070a2c36eea`:

- [MorpheAsset.kt](https://github.com/MorpheApp/morphe-manager/blob/2c2bdc6a8368fa790ca2e3a57b050070a2c36eea/app/src/main/java/app/morphe/manager/network/dto/MorpheAsset.kt) declares `created_at` as `kotlinx.datetime.LocalDateTime`, with version, description, download URL and optional signature/page URLs.
- [RemotePatchBundle.kt](https://github.com/MorpheApp/morphe-manager/blob/2c2bdc6a8368fa790ca2e3a57b050070a2c36eea/app/src/main/java/app/morphe/manager/domain/bundles/RemotePatchBundle.kt) parses JSON-source manifests into that DTO.
- [MorpheAPI.kt](https://github.com/MorpheApp/morphe-manager/blob/2c2bdc6a8368fa790ca2e3a57b050070a2c36eea/app/src/main/java/app/morphe/manager/network/api/MorpheAPI.kt) retries a missing GitHub raw manifest with the user's configured PAT and resolves versioned release downloads through `releases/tags/{tag}`.
- [AssetDownloader.kt](https://github.com/MorpheApp/morphe-manager/blob/2c2bdc6a8368fa790ca2e3a57b050070a2c36eea/app/src/main/java/app/morphe/manager/network/service/AssetDownloader.kt) supports private GitHub asset downloads through the existing PAT/API route.

These are observed source behaviors, not a check that this phone's Manager has
an appropriately scoped PAT or that draft-tag resolution succeeds. No token
was read from Manager, copied into it or embedded in a source URL. Private
source access is a viable route to verify before proposing public publication.
An authenticated lookup of `releases/tags/v0.1.0-alpha.1` returned HTTP404 while direct draft ID425693294 remained accessible. This confirms the current draft cannot resolve through Manager's observed tag route. A draft and an expiring signed URL are not usable permanent feed addresses.

The previous placeholder copied Git's timezone-bearing timestamp directly into
`created_at`. That mismatched the JSON-source LocalDateTime contract.
`tools/bundle_feed.py` now converts the original offset to UTC, then serializes
without an offset. Controls cover non-UTC source commits, date rollover,
ambiguous timestamps, wrong source/version, auxiliary MPP and credential-bearing
URLs. The unapproved feed keeps its download URL empty. No working feed is
claimed from a schema check.

Next activation boundary: verify the actual held privacy/control criteria,
then authorize the finite phone acceptance window and provision full large-v3
under the storage gate. After acceptance, publish a versioned private release
and matching manifest, verify authenticated Manager import/update and retain
rollback. Friends would require their own approved access/companion setup;
this draft does not automatically make the patch available to them.
