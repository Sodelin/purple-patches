# Gboard candidate acceptance

This is the verification contract for the first two Purple actions. Existing
stock typing, languages, swipe typing, clipboard, translation and voice entry
must continue working. A successful hosted build is not device acceptance.

| Gate | Evidence or remaining work |
| --- | --- |
| Native deterministic controls | Run40527691599, sourcefa32125:27 passed; Android ARM64 cross-build passed. No model invoked. |
| Gboard bundle | Run40528625015, source6719a21: complete build/test/metadata generation passed. Main MPP, all artifact hashes,44-patch catalog and overlay provenance independently verified in RAM; see[gboard-artifact.json](gboard-artifact.json). |
| Java editor policy | Password, visible/web/numeric password, no-personalized-learning, localhost, UTF-8, stale anchors and Termux result-code controls run in hosted Gboard workflow. |
| Version admission | The new action participates in upstream generated editing-access-point admission and published catalog. Unsupported versions must remain rejected. |
| Target APK | Pinned upstream targets18.5.4.993608717-release-arm64-v8a. Actual installed version, original APK hash and signer remain unverified. |
| Morphe patching | Produce distinctly versioned MPP, inspect catalog/provenance/hashes, then patch an exact compatible original APK. Not yet performed. |
| Termux connection | Actual one-shot callback, nonexported receiver, denied permission, expired callback and app-process restart acceptance pending. No persisted capability preferences. |
| Privacy | New companion keeps prompt/audio/events/output in anonymous memory/pipes, seals its capability and recovery intents using the existing identity. Actual phone sink integration and internal Codex writer coverage remain separate open checks. |
| Whisper | Existing base multilingual assets do not qualify. User-selected full large-v3 weights, matching architecture, pinned bytes and engine are required. No weights downloaded or live speech tested. |
| Editor integrity | Device tests must cover selection replacement, cursor movement, editor switch, app switch, cancellation, delayed result and starting another operation before an earlier preview is inserted. Original text must survive every refusal. |
| Microphone | Device tests must verify stop, cancel,60-second ceiling, permissions, interruptions and release. Health/family validation must precede microphone opening. |
| Translation | One explicit selected-text request, exact gpt-6.1-sol, no context inheritance/fallback, preview and explicit insertion. Live testing remains held. |
| Regression/rollback | Alongside-stock package option and independent keyboard recovery must work before changing the user's default. No installation/default switch has occurred. |
| Update feed | Candidate feed deliberately has no download URL. Do not point it at an upstream release or present it as a working install source. Publish the verified bundle and corresponding feed together later. |

## Reproduce without phone toolchains

The private repository is `Sodelin/purple-patches`. Its Gboard workflow checks
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
those are not the Manager patch input. No update feed is published yet.
