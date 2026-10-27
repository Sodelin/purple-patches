# Local companion source adaptation

This is a separately versioned adaptation of the retained native Rust keyboard
bridge, with exact original working-file hashes in UPSTREAM.json. Original
source/runtime/installations are untouched. The existing finite request API,
model policy, WAV validation, cancellation and process birth/UID/inode custody
are retained. The adaptation adds anonymous memory schema/audio inputs, bounded
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
1280audio width,32encoder layers and32(full) or4(turbo) decoder layers; full
binary/model hashes still verify before transcription. Base and v2 are refused.
Retain actual variant, source revision, bytes and official digest in deployment
receipts; no filename-based relabeling. Engine/model may be reused through a
reviewed shared asset location; no second weights download is assumed.
