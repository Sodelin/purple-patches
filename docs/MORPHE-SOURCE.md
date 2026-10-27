# Private candidate update source

The selected Morphe JSON source is:

`https://raw.githubusercontent.com/Sodelin/purple-patches/main/channels/candidate.json`

The repository and both selected releases remain private. The source requires
an existing GitHub credential with repository access in Manager's PAT setting.
No Manager credential was inspected, copied or configured by this work. This
channel does not automatically grant friends access.

The actual remote route is verified: anonymous feed access returned404,
authenticated feed bytes matched versioned source, the published component tag
resolved through GitHub's release API, and its complete main MPP downloaded
with the pinned hash. See[candidate-source-verification.json](candidate-source-verification.json).
This used the existing gh identity, not Manager on the phone. Actual Manager
source import/update acceptance, keyboard installation and live testing remain
unverified and held separately.

## Selected components

| Component | Version and custody |
| --- | --- |
| Morphe main MPP | `0.1.0-alpha.1`, release425723616, tag`patches-v0.1.0-alpha.1`, seven full-byte-verified assets including corresponding source. Exact main MPP SHA256`af2077907783247f667940e02659fe8b574ec36fd26df8af67e22469de3f11fe`. This is the existing full-APK-tested bundle. |
| Companion | `0.2.1`, release425713896, tag`companion-v0.2.1-candidate`, six full-byte-verified assets including source and the fixed authored schema.28 public controls and Android ARM64 cross-build passed. |

Both are published **private prereleases**, not phone-accepted stable releases.
Source-controlled pins for every selected asset are in
[selected-release-assets.json](selected-release-assets.json). No APK, model
weights or signing keys were distributed. No asset was replaced beneath an
existing hash. Historical draft425693294 retains the original mixed candidate
and superseded0.2.0 companion with an explicit supersession notice.

`tools/publish_private_candidates.py --publish` verifies all selected component
asset bytes against source pins before any publication mutation, refuses a
public repository or stable-release promotion, publishes only private
prereleases, and checks the actual tag routes. Repeating it on the same
published versions verifies custody without replacing assets.
`tools/verify_candidate_source.py` verifies the authenticated raw-feed and actual
versioned asset-download route without device/model calls. Credentials remain
in RAM and are never embedded in the feed URL.

For future changes, build and verify a new component version, retain its exact
source and asset pins, publish that version, update the stable candidate-feed
path and re-run the remote verification. Never overwrite an existing version.
The stage helper's candidate modes refuse published releases. Gboard and native
companion versions are independent; a native-only correction need not repatch
or relabel an unchanged MPP. Stable promotion still requires actual phone
acceptance and rollback proof.

## Manager 2.2.0 contract

Reviewed official tagv2.2.0, commit`2c2bdc6a8368fa790ca2e3a57b050070a2c36eea`:

- [MorpheAsset.kt](https://github.com/MorpheApp/morphe-manager/blob/2c2bdc6a8368fa790ca2e3a57b050070a2c36eea/app/src/main/java/app/morphe/manager/network/dto/MorpheAsset.kt) declares JSON-source `created_at` as `kotlinx.datetime.LocalDateTime`, with version, description, download URL and optional signature/page URLs.
- [RemotePatchBundle.kt](https://github.com/MorpheApp/morphe-manager/blob/2c2bdc6a8368fa790ca2e3a57b050070a2c36eea/app/src/main/java/app/morphe/manager/domain/bundles/RemotePatchBundle.kt) deserializes custom JSON manifests into that DTO.
- [MorpheAPI.kt](https://github.com/MorpheApp/morphe-manager/blob/2c2bdc6a8368fa790ca2e3a57b050070a2c36eea/app/src/main/java/app/morphe/manager/network/api/MorpheAPI.kt) retries a missing GitHub raw manifest with the configured PAT and resolves downloads through`releases/tags/{tag}`.
- [AssetDownloader.kt](https://github.com/MorpheApp/morphe-manager/blob/2c2bdc6a8368fa790ca2e3a57b050070a2c36eea/app/src/main/java/app/morphe/manager/network/service/AssetDownloader.kt) supports private GitHub assets through the PAT/API route.

The feed generator converts the source timestamp to UTC and then removes its
offset for LocalDateTime serialization. Controls cover offset/date rollover,
ambiguous time, wrong repository/version/component, auxiliary MPP and
credential-bearing URLs. The generated **unpublished build placeholder** keeps
its download URL blank; the selected external candidate channel now points at
the actual published private release.

## Retained experiments and activation gaps

The original draft tag returned404 through the Manager release route. This
was a distribution-state issue, resolved by separate clean published private
component releases without lifting installation/model holds. Earlier gh stdin
uploads failed with Bad Content-Length; fixed-length RAM uploads and complete
remote readback passed. The corrected timestamp pipeline passed full build
run40538677135; its normalized MPP comparison changed only MANIFEST.MF and did
not replace the selected APK-tested bundle. Exact experiments remain in
[feed-rebuild-artifact.json](feed-rebuild-artifact.json) and
[private-candidate-release.json](private-candidate-release.json).

Actual Android public controls exposed anonymous /proc/self/fd reopening denial
in companion0.2.0. Companion0.2.1 sends audio through stdin and uses a fixed,
owner-private authored schema beside its executable. It has build/control
acceptance, not live decoder or keyboard acceptance.

Full-large-v3 weights and the retained small engine/companion assets require
known minimum allocation beyond the20GiB reserve. See
[runtime-asset-headroom.json](runtime-asset-headroom.json); it is a dated
read-only lower bound, not full APK-signing/install admission or a quota.
Existing privacy/model and installation/default-keyboard holds remain.
