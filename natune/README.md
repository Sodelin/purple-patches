# Natune component: exact target, source and binary admission

Target: Natune nightly `N202610191717`, Android package
`moe.rukamori.archivetune`, code 6900. `target.json` pins the public official
universal GMS APK. Its full SHA256/size match the installed base APK; the
installed APK signer was independently verified without app-private data.
The installed APK application label is Natune. Canonical repository:
NatuneGroup/ArchiveTune; old vossgraves URLs redirect there.

Source requirements and traced low-data gates are versioned in the parent
workspace `coordination/natune-flac-patch-requirements.md`. Requested scope is
strict FLAC playback/downloads, intentional full-quality cellular allowance,
and compatible resilient provider integrations alongside Qobuz.

Before implementing bytecode fingerprints, the off-phone workflow
`inspect-natune.yml` verifies the exact original digest, signer, package and
version, then uses the already hash-pinned Morphe DEX reader. It scans actual
release methods for reviewed public low-data anchors and network-policy calls.
It does not execute Natune or print arbitrary embedded strings. Only compact
public provenance/hook evidence is retained; no APK/DEX artifact is uploaded.

`fetch_target.py` refuses outside a hosted Linux GitHub Actions runner. No APK,
SDK, dependency installation or compiler target is admitted on the phone.
The inspector is analysis tooling, not a patch. A successful scan does not
prove an applicable patch, FLAC media verification, provider health or phone
acceptance. Those gates remain open. No live music/provider/model test or app
installation/setting change is part of inspection.

Morphe template `e721536ab19e30d16ce9d31ae1830ca299aad383` uses patcher 2.0.0;
installed Manager 2.2.0 source carries patcher 2.0.1. Use a matching tested
template/toolchain epoch for the separate Natune component; do not mutate the
already verified Gboard overlay's upstream pin merely to add another app.

Current target source commit: `b7e4dd444fea202a5c32e96f7b1199a74fe3ae70`.
Current state: the dependency-free `inspect_dex.py` completed read-only inspection
of all three DEX files in the verified installed public APK, covering 110490
methods with code. Seven malformed-input/instruction-boundary controls pass.
`policy-hooks.json` records six policy candidates and their callers; it exports
only method references, fixed public anchors and code hashes, not arbitrary
strings or app data. Five direct calls in the playback package use the low-data
helper, allowing a scoped audio policy instead of changing global low-data mode.
The independent hosted dexlib inspection passed in run40862971246, matching all
six candidates. `inspection-verification.json` pins its exact artifact and source.

## Implemented mobile-data increment

`NatuneMobileDataPatch.kt` implements the optional, default-off **Allow
full-quality audio on mobile data** patch. Five exact playback/download callsites
return false for their local low-data result; the global helper and unrelated
UI/artwork callers remain unchanged. Every site is checked before mutation.
Compatibility is experimental and limited to `N202610191717`.

Hosted run40864550443 built the real MPP, verified the original public APK's
full digest/package/version/signer, applied the patch and passed independent
DEX preservation comparison across27129classes/114583methods. All24native
libraries and12assets matched. `build-verification.json` retains all member
hashes, the full compact preservation report and failed earlier attempts.

The verifier compares instruction/reference/register/literal/control-flow and
handler semantics, class/field/method/parameter annotations and field flags.
DEX writers may omit explicit default static initializers. Only observed,
type-correct false/null/positive float zero values are normalized to absent;
14controls preserve true, nonzero, wrong types, instance values, negative zero,
NaN and changed access flags. Primary rule:
https://source.android.com/docs/core/runtime/dex-format#class-def-item

Subsequent run40864921834 PASSED full manifest/resource/non-DEX-entry preservation;
its exact artifact is now selected in `build-verification.json`. APK signing
and debug information are outside semantic DEX comparison. No phone behavior,
provider reliability or installation has been accepted.

This increment is NOT strict FLAC or an additional provider implementation.
`STRICT-FLAC-INTEGRATION.md` records the actual cache, fresh resolver, decryption
and streaming hooks still needed. Existing caches must be validated too. New
provider interfaces and authorized access require their own reviewed bindings.

Workflow registration originally failed using the existing development OAuth
credential. The already-connected GitHub application created the reviewed
workflow without any credential scope change. Inspection/build jobs subsequently
ran through the existing hosted route. No phone SDK/compiler was installed.

## Versioned distribution

Experimental prerelease `natune-patches-v0.1.0-alpha.1` preserves all12published
assets, corresponding overlay and pinned upstream template source, generated
compatibility list, build proof and hashes. The publisher checked complete
remote bytes before publishing; it never overwrites a published asset.
`published-candidate.json` pins those exact bytes. No APK is distributed.

The separate public Manager JSON feed is `channels/natune-candidate.json`.
`python tools/verify_candidate_source.py --component natune --visibility public`
verifies the anonymous feed, release tag and complete main MPP against source
pins. A successful HTTP check does not prove an actual Manager import/update.
The remaining FLAC/provider and phone-acceptance gates remain open.

## Strict-FLAC source work in progress

Memory-only prefix evidence, byte-preserving/range-bounded decoder input and
the exact native request-copy adapter are implemented and hosted-control-tested.
See `flac-prefix-verification.json`, `flac-stream-verification.json` and
`data-spec-verification.json`. They are not wired into an installed app or
included in the selected mobile-data MPP. The Android classes in
`test-fixtures/` are explicit test stand-ins and must never enter an APK.
Real cache identity/lifecycle and complete decoder admission remain the next
integration gates, with provider work separately tracked in
`PROVIDER-INTERFACES.md`.

The separate local-decoder prototype `0.2.0-dev.1` now builds and patches the
exact original off phone. [Hosted run40875761792](https://github.com/Sodelin/purple-patches/actions/runs/40875761792)
passed three actual emitted FLAC guards,39guard controls and complete remaining
program/resource preservation. The prototype is optional/default-off, pins the
original signer and version code6900, and does not change the selected0.1.0 feed.
See [full artifact evidence](local-flac-build-verification.json) and
[scope and remaining integration](STRICT-FLAC-INTEGRATION.md).
This is local decoder admission, not complete FLAC downloads, new providers or
phone playback acceptance. No patched third-party APK is distributed.

The successor `0.2.0-dev.2` also guards actual input-format changes through
Natune's original renderer-error path. Hosted run40877133662 passed all54actual
prefix/negative controls and complete remaining-program/resource preservation;
[exact evidence](local-flac-lifecycle-build-verification.json) is retained.
No phone acceptance or runtime settings toggle is implied. Current additional
scope includes [upgrading existing non-FLAC downloads](FLAC-UPGRADES.md),
[Road Trip buffering and patch settings](ROAD-TRIP-AND-SETTINGS.md).
