# Gboard candidate acceptance

This is the verification contract for the first two Purple actions. Existing
stock typing, languages, swipe typing, clipboard, translation and voice entry
must continue working. A successful hosted build is not device acceptance.

| Gate | Evidence or remaining work |
| --- | --- |
| Native deterministic controls | Historical0.2.0 run40527691599 passed27 controls/cross-build but actual Android OS probe later exposed anonymous-path denial. Source0.2.1 uses pipe audio plus fixed authored schema; run40540825059 passed28 public controls and Android ARM64 cross-build. ZIP/member/SHA/ELF verified independently; see[native-0.2.1-artifact.json](native-0.2.1-artifact.json). No model invoked. |
| Gboard bundle | Run40528625015, source6719a21: complete build/test/metadata generation passed. Main MPP, all artifact hashes,44-patch catalog and overlay provenance independently verified in RAM; see[gboard-artifact.json](gboard-artifact.json). |
| Java editor policy | The published alpha.1 candidate has hosted privacy and anchor controls. Alpha.2 rejects unavailable/inconsistent editor reads and observed selection movement; its hosted bundle evidence is [here](gboard-alpha2-build-verification.json). Alpha.3 adds an input-connection and sensitive-field recheck after capture; its hosted bundle evidence is [here](gboard-alpha3-build-verification.json). Device evidence remains open. See [EDITOR-SNAPSHOT.md](EDITOR-SNAPSHOT.md). |
| Version admission | The new action participates in upstream generated editing-access-point admission and published catalog. Unsupported versions must remain rejected. |
| Target APK | Pinned upstream targets18.5.4.993608717-release-arm64-v8a. Installed beta18.5.4.993608717, four original parts and Google signatures now verified read-only; User selected the supported release APKMirror input; beta compatibility is unnecessary. See[INSTALLED-TARGET.md](INSTALLED-TARGET.md). |
| Morphe patching | PASS: exact APKMirror release hash and Google signature verified; three selected patches applied with full bytecode and validation in run40536927173. Separate output package, original22860 classes,15 native libraries and140 assets checked. See[release-apk-artifact.json](release-apk-artifact.json). This is static acceptance only. |
| Termux connection | Actual one-shot callback, nonexported receiver, denied permission, expired callback and app-process restart acceptance pending. No persisted capability preferences. |
| Privacy | New companion keeps prompt/audio/events/output in anonymous memory/pipes, seals its capability and recovery intents using the existing identity. Actual phone sink integration and internal Codex writer coverage remain separate open checks. Dated offline catalog still reports native writer integration uninstalled; see[privacy-status-readback.json](privacy-status-readback.json), not a fresh deployment acceptance. |
| Android memory transport | Actual public cat fixture: inherited anonymous /proc/self/fd path denied, inherited stdin and pipe stdin succeed. Source0.2.1 corrects both audio and schema routes; see[android-transport-control.json](android-transport-control.json). Rust/app/decoder acceptance remains separate. |
| Whisper | Existing base multilingual assets do not qualify. User-selected full large-v3 weights, matching architecture, pinned bytes and engine are required. No weights downloaded or live speech tested. |
| Editor integrity | Device tests must cover selection replacement, cursor movement, editor switch, app switch, cancellation, delayed result and starting another operation before an earlier preview is inserted. Original text must survive every refusal. |
| Microphone | Device tests must verify stop, cancel,60-second ceiling, permissions, interruptions and release. Health/family validation must precede microphone opening. |
| Translation | One explicit selected-text request, exact gpt-6.1-sol, no context inheritance/fallback, preview and explicit insertion. Live testing remains held. |
| Regression/rollback | Alongside-stock package option and independent keyboard recovery must work before changing the user's default. No installation/default switch has occurred. |
| Update feed | The public candidate feed, published Gboard prerelease and complete pinned MPP were rechecked anonymously on 29 October 2026; the separate companion0.2.1 prerelease also resolved anonymously. See [public-feed-check-20261029.json](public-feed-check-20261029.json). Actual phone Manager import/update acceptance remains pending. |

## Reproduce without phone toolchains

The public repository is `Sodelin/purple-patches`. Its Gboard workflow checks
out the exact upstream commit recorded in `tools/materialize.py`, applies this
small overlay, runs policy checks plus the upstream test suite, and builds the
Morphe bundle/catalog. `tools/version.txt` names the distinct candidate version.
The companion workflow is manual and performs native controls before Android
cross-compilation. Both workflows retain hashed selected artifacts for seven
days; expired artifacts must be rebuilt from recorded source, not treated as
permanent rollback custody.

No build, SDK installation or model download is admitted on this phone below
its20GiB working reserve. No personal downloads are cleanup candidates.

## Runtime ownership

The candidate wrapper addresses only
`~/.local/state/purple-gboard-companion-v1` and
`~/.local/lib/purple-gboard-companion/current/purple-keyboard-bridge`.
It does not replace the older bridge, model directory or controllers. The
wrapper's `stop` action must pass the companion's existing PID/birth/UID/inode
custody checks. Installation and activation have not occurred.

New-model testing remains held under the shared privacy policy. The scoped
memory transport label does not certify all internal Codex/session/provider
writers, Android app data or cloud-input invisibility. Resolve those actual
writer gates before a live translation test. Keep supporting source, public
fixture checks and packaging work moving while that action is held.

For Morphe input, select exactly `patches-0.1.0-alpha.1.mpp`. The hosted
archive also contains auxiliary `-sources.mpp` and `-javadoc.mpp` outputs;
those are not the Manager patch input. The public candidate update feed and selected MPP have been remotely verified; actual Manager import/update remains unperformed.

## Selected local deliverable

The exact main bundle is also available at
`/storage/emulated/0/Download/Purple-Patches/patches-0.1.0-alpha.1.mpp`,
with SHA256SUMS alongside it. Full local bytes/hash readback passed; see
[local-patch-delivery.json](local-patch-delivery.json). This is an8MB selected
user deliverable, not a local compiler/SDK cache, APK installation, Manager
import or keyboard activation. `tools/export_patch_bundle.py` recreates it from
the retained release asset, verifies the pin before writing, uses exclusive
creation and refuses a differing existing file.

## Companion revision selection

Use the verified0.2.1 companion with the unchanged0.1.0-alpha.1 main MPP for
future authorized deployment. Its executable, wrapper, fixed schema and exact
corresponding source are retained in public prerelease425713896; all six assets
passed full remote byte readback. See[private-companion-0.2.1.json](private-companion-0.2.1.json).
The previous0.2.0 artifact/draft is retained history, with a read-back
supersession notice; it is not a supported future runtime selection.

The retained Whisper1.8.3 engine hash was reverified without executing it.
Pinned source supports large-v3 and `-f -` stdin audio; rebuilding the engine is
not currently required by source evidence. This does not turn the protected
base weights into v3 or prove actual full-model decoder performance. See
[whisper-engine.json](whisper-engine.json).
