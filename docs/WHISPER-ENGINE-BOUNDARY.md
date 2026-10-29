# Whisper engine identity and checked-byte boundary

The current companion source has different trust rules for the model and the
engine. It contains a fixed SHA-256 and size for the selected full Whisper
large-v3 model. The engine path, SHA-256 and source revision come from the
private `whisper/config.json`. The config reader checks file ownership,
permissions, size and no-follow opening. The preflight confines the engine to
the private Whisper directory, checks that it is executable and compares its
bytes with the hash in that same config. `engine_commit` is checked only for
the shape of a 40-character hexadecimal string. These checks detect changes
relative to the chosen config; they do not independently establish the engine's
origin or that its revision corresponds to its bytes.

The existing `preflight_checks_selected_model_engine_and_full_hash_separately`
control uses a synthetic model pin, a synthetic executable and an
`engine_commit` made of 40 `a` characters. It expects and obtains successful
preflight and full-hash validation for those fixture values. It passed in
[hosted companion run 40966246230](https://github.com/Sodelin/purple-patches/actions/runs/40966246230).
This is a controlled source demonstration, not execution of that fixture or a
finding that the current phone configuration has been altered.

The [retained engine observation](whisper-engine.json) records one previously
hashed v1.8.3 candidate and reviewed source. It is useful provenance for a
future installer, but the companion does not currently bind that observation to
the config or independently prove that the binary was built from that source.
The stored base model remains separately protected and is not full-v3.

There is also no demonstrated checked-byte continuity from the hash to use:
the worker hashes the configured engine and model paths and then starts the
engine by pathname, passing the model pathname to it. An actor able to alter
those paths between the check and use could make the used bytes differ. This
is a conditional source-level gap; ownership/mode checks limit access, and no
actual replacement or phone incident has been observed.

Before activation, select a real owner-authorized engine profile and establish
how its trusted provenance, model compatibility, and checked bytes are bound to
the actual execution and model load. The design must support legitimate engine
builds and device architectures without silently substituting the user's
selected model. A hash copied from the same mutable configuration is not that
independent binding. Test the selected mechanism with public synthetic inputs,
including changed paths and mismatched provenance, before separate authorized
model/device acceptance. The existing caller, privacy, recovery and installation
holds remain unchanged.
