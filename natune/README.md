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
The independent hosted dexlib result remains pending.
The 2026-10-29 push was refused because the existing GitHub development OAuth
credential lacks permission to create/update workflow files. No workflow was
created remotely and no hosted inspection job started. This is separate from
Morphe's read-only download PAT. Neither credential was inspected or broadened.
The prior verified hosted Gboard/native results remain at their original epochs.
Subsequently the already-connected GitHub application successfully created the
reviewed inspection workflow at remote commit 82286ed. No credential scope was
changed. That resolves workflow registration through an existing authorized
interface; actual hosted inspection must still be dispatched and verified.
