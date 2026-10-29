# Local companion source adaptation

This is a separately versioned adaptation of the retained native Rust keyboard
bridge, with exact original working-file hashes in UPSTREAM.json. Original
source/runtime/installations are untouched. The existing finite request API,
model policy, WAV validation, cancellation and process birth/UID/inode custody
are retained. The adaptation adds memory/pipe prompt and audio inputs, bounded
nonblocking process pipes, encryption of the daemon capability record through
the ONE existing featurectl identity, and no persistent prompt/audio/event/
transcript files in the new request path. Old job directories are never pruned
by this startup. Fixed diagnostics are discarded, not written to bridge.log.
Required failure/cancellation receipts go to the shared encrypted store.

This does not assert that the existing Codex provider/session/telemetry stores
are universally encrypted or that cloud inference is offline. The original
all-new-model-testing hold persists until its applicable enforcement criterion
and owner acceptance are satisfied. No inference or microphone test is run by
CI. Lifecycle/controller adoption and full writer enforcement are distinct
acceptance gates; candidate compilation does not clear them.

Deployment uses the fixed wrapper at ~/.local/bin/purple-gboard-service,
selected native binary under ~/.local/lib/purple-gboard-companion/current,
and new scoped state ~/.local/state/purple-gboard-companion-v1. Shared featurectl
must already exist with its single identity; no new keys are generated.
The old bridge is not stopped or replaced by source creation or CI.

The new state/whisper/config.json follows the retained schema plus
model_family="whisper-large-v3". The selected model header must have128mel bins,
1280audio width,32encoder layers and32(full) decoder layers; the4-layer turbo variant is refused; full
binary/model hashes still verify before transcription. Base and v2 are refused.
Retain actual variant, source revision, bytes and official digest in deployment
receipts; no filename-based relabeling. The configuration currently confines engine/model files to the private Whisper
directory. A small reviewed engine copy can be reused there when activation is
authorized; full weights must be acquired once at their selected final location.
No shared external model path or second full weights copy is admitted by this
source.

The `0.2.2-dev.2` source changes the health preflight: it requires a valid
private config, the selected official full-large-v3 SHA declaration and size,
the full-v3 header, and a present nonempty executable engine with matching SHA
inside the owned Whisper directory. This preflight deliberately does not hash
the entire model on every health request. The owned worker checks the complete
model hash once and rechecks the engine before inference, after audio capture. Removing the
duplicate full-model hash from request admission avoids reading the same 3.1 GB
model twice for each transcription. Metadata preflight alone cannot detect a
same-size corrupt model or establish phone readiness. The development companion
is not installed or accepted. The earlier0.2.1 candidate remains the published
historical artifact and is not silently replaced.


Companion0.2.1 corrects an actual Android transport failure: `/proc/self/fd/N`
reopening is denied for the tested inherited anonymous file. Audio is now sent
to the pinned engine's `-f -` stdin reader. The Codex `--output-schema` path
names only `response-schema.json` beside the actual companion executable. That
file is fixed authored source, not a prompt, result or runtime observation. Its
bytes must equal the compiled template; it must be owner-private mode0600 and
not a symlink. Missing/changed templates refuse before model invocation.
Prompt/audio/events/results remain memory/pipe only. Include this schema in
future deployments; no companion has been installed or activated by this work.
The earlier0.2.0 companion remains retained evidence, not an Android-accepted
runtime. See[actual OS control](../docs/android-transport-control.json) and
[engine provenance](../docs/whisper-engine.json).
