# Gboard candidate acceptance

This is the verification contract for the first two Purple actions. Existing
stock typing, languages, swipe typing, clipboard, translation and voice entry
must continue working. A successful hosted build is not device acceptance.

| Gate | Evidence or remaining work |
| --- | --- |
| Native deterministic controls | Historical0.2.0 run40527691599 passed27 controls/cross-build but actual Android OS probe later exposed anonymous-path denial. Source0.2.1 uses pipe audio plus fixed authored schema; run40540825059 passed28 public controls and Android ARM64 cross-build. Development source0.2.2-dev.4 passed 44 library controls and an ARM64 cross-build, including synthetic hash cancellation; see [companion-022-dev4-verification.json](companion-022-dev4-verification.json). No model invoked and no phone acceptance inferred. |
| Gboard bundle | Alpha.7 hosted build and policy controls passed; the MPP, artifact hashes and exact overlay provenance were independently checked in RAM. See [gboard-alpha7-build-verification.json](gboard-alpha7-build-verification.json). Its bootstrap logging mitigation does not close the privacy gate. |
| Java editor policy | The published alpha.1 candidate has hosted privacy and anchor controls. Alpha.2 rejects unavailable/inconsistent editor reads and observed selection movement; its hosted bundle evidence is [here](gboard-alpha2-build-verification.json). Alpha.3 adds an input-connection and sensitive-field recheck after capture; its hosted bundle evidence is [here](gboard-alpha3-build-verification.json). Device evidence remains open. See [EDITOR-SNAPSHOT.md](EDITOR-SNAPSHOT.md). |
| Version admission | The new action participates in upstream generated editing-access-point admission and published catalog. Unsupported versions must remain rejected. |
| Target APK | Pinned upstream targets18.5.4.993608717-release-arm64-v8a. Installed beta18.5.4.993608717, four original parts and Google signatures now verified read-only; User selected the supported release APKMirror input; beta compatibility is unnecessary. See[INSTALLED-TARGET.md](INSTALLED-TARGET.md). |
| Morphe patching | The exact APKMirror release hash and Google signature were verified and three selected patches applied for alpha.7 ([gboard-alpha7-apk-verification.json](gboard-alpha7-apk-verification.json)). Original classes, native libraries and assets were retained; translation, permission-scope and logging-extra references were present. This is static evidence only. |
| Termux connection | A source review found that bootstrap sends the bearer over stdout, which reviewed Termux source can log. Alpha.7 requests custom background log level off as defense in depth; the bundle built but this has not been verified on the installed version and is not a safe-handoff proof. Activation is blocked until the handoff and actual logging paths are resolved. See [BOOTSTRAP-PRIVACY.md](BOOTSTRAP-PRIVACY.md). Actual callback, receiver, permission, expiry and app-process restart acceptance also remain pending. |
| Permission flow | Alpha.7 source requests Termux's command permission for local-tools connection and microphone permission separately for dictation; translation does not request the microphone. The selected Termux permission permits this app to run commands as Termux. Device permission/denial/revocation and rationale behavior remain open; see [PERMISSION-FLOW.md](PERMISSION-FLOW.md). |
| Job cancellation | A lost job-start response can leave the keyboard without the server-generated ID needed for cancellation. A versioned client/companion reconciliation and recovery path remains open; see [JOB-CANCELLATION.md](JOB-CANCELLATION.md). Dev.4 separately checks cancellation during the full model hash; [hosted controls passed and limitations remain](WHISPER-HASH-CANCELLATION.md). |
| Privacy | New companion keeps prompt/audio/events/output in anonymous memory/pipes, seals its capability and recovery intents using the existing identity. Actual phone sink integration and internal Codex writer coverage remain separate open checks. Dated offline catalog still reports native writer integration uninstalled; see[privacy-status-readback.json](privacy-status-readback.json), not a fresh deployment acceptance. |
| Android memory transport | Actual public cat fixture: inherited anonymous /proc/self/fd path denied, inherited stdin and pipe stdin succeed. Source0.2.1 corrects both audio and schema routes; see[android-transport-control.json](android-transport-control.json). Rust/app/decoder acceptance remains separate. |
| Whisper | Existing base multilingual assets do not qualify. The `0.2.2-dev.3` source checks selected full-v3 declaration/size/header and the executable engine hash at health preflight; Gboard alpha.5 requires its explicit capability marker. The complete model hash is still checked once later, before transcription. Preflight alone is not a full-model integrity check. The [hosted companion controls](companion-022-dev3-verification.json) and [alpha.5 bundle build](gboard-alpha5-build-verification.json) passed. User-selected full large-v3 weights, matching architecture, pinned bytes and engine are required. No weights downloaded or live speech tested. |
| Editor integrity | Device tests must cover selection replacement, cursor movement, editor switch, app switch, cancellation, delayed result and starting another operation before an earlier preview is inserted. Original text must survive every refusal. |
| Active composition and result retention | Current `commitText` can target an active composing region, and its return is ignored while the preview is dismissed. Safe handling and representative editor acceptance remain open; see [EDITOR-SNAPSHOT.md](EDITOR-SNAPSHOT.md). |
| Microphone | Alpha.4 source adds an elapsed-time limit, nonblocking reads, worker-owned release and PCM staging cleanup; synthetic hosted checks passed in [gboard-alpha4-build-verification.json](gboard-alpha4-build-verification.json). Alpha.5's [hosted APK inspection](gboard-alpha5-apk-verification.json) also found the voice-readiness and capture references. This is static linkage evidence, not a control-flow or device proof. See [MICROPHONE-CAPTURE.md](MICROPHONE-CAPTURE.md). Device tests must verify stop, cancel, the 60-second ceiling, permissions, interruptions and release. Health/family validation must precede microphone opening. |
| Long-form dictation | Current source silently caps capture at 60 seconds and has separate transport, engine deadline and result limits. Longer capture and honest partial-result behavior remain open; see [LONG-DICTATION.md](LONG-DICTATION.md). |
| Translation | Alpha.6 adds an explicit Medium/Max choice for each selected-text Sol request; no context inheritance or effort/model fallback. See [TRANSLATION-EFFORT.md](TRANSLATION-EFFORT.md). Its hosted bundle and companion controls passed. Preview and insertion are explicit; actual UI/translation acceptance remains open and live model testing is held. |
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

The current public feed and historical local deliverable point to alpha.1, which
has the editor-snapshot issue documented in [EDITOR-SNAPSHOT.md](EDITOR-SNAPSHOT.md).
The feed description now warns against installing it; anonymous readback is
recorded in [feed-warning-readback-20261029.json](feed-warning-readback-20261029.json).
Do not treat it as the current activation candidate. The hosted archives also
contain auxiliary `-sources.mpp` and `-javadoc.mpp` outputs; those are not the
Manager patch input. Actual Manager import/update remains unperformed.

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

Gboard alpha.5 requires the voice-readiness capability introduced in
`0.2.2-dev.3`; the `0.2.2-dev.4` source retains that capability and adds
cooperative model-hash cancellation. Its hosted artifact and source are
verified in [companion-022-dev4-verification.json](companion-022-dev4-verification.json).
The exact main Gboard bundle and dev.3/dev.4 companion archives have private
Drive copies with full readback and actual restoration; see
[candidate custody](GBOARD-ALPHA5-DRAFT.md).
Public deployment and update custody still need to be established. The previously published0.2.1
companion remains historical evidence for earlier candidates; it does not
satisfy alpha.5's voice-preflight contract. Its earlier readback is retained in
[private-companion-0.2.1.json](private-companion-0.2.1.json). The previous0.2.0
artifact/draft is retained history, with a read-back supersession notice.

The retained Whisper1.8.3 engine hash was reverified without executing it.
Pinned source supports large-v3 and `-f -` stdin audio; rebuilding the engine is
not currently required by source evidence. This does not turn the protected
base weights into v3 or prove actual full-model decoder performance. See
[whisper-engine.json](whisper-engine.json).
