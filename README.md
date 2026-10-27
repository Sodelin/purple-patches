# Purple Patches

A versioned patch source for Morphe, starting with Gboard. The first candidate
adds separate **Whisper 3 dictation** and **AI Translate** actions while retaining
stock Gboard. No chat window, automatic per-keystroke inference or replacement
clipboard is introduced.

This repository is a small source overlay over Jason Wu's GPLv3 Gboard patches
at commit2eb71854accc7e906d3e4f5495b2a54ed1e2af47. It preserves that mature
project's version admission, lifecycle hooks, existing features and notices.
The bundle is built on a hosted runner; Android SDK/Gradle dependencies are not
installed on the phone. Generated APKs and model weights are not distributed.

The candidate is not a verified keyboard release. Build, actual APK patching,
Gboard device regression, companion privacy path and Whisper3 acceptance are
separate gates. Current phone speech assets are base multilingual, NOT v3.
The user selected full large-v3 for accuracy. Turbo, base and other generations
are refused; no model substitution is permitted.

The two actions require the authenticated local companion bootstrap at the
stable Termux path ~/.local/bin/purple-gboard-service. Credentials remain only
in keyboard-process memory, through a one-shot explicit nonexported callback.
The keyboard refuses text/audio submission unless the companion advertises
keyboard_data_path=memory-only-v1, and refuses dictation unless the declared
model family is whisper-large-v3. The older retained bridge does not satisfy
those gates merely because it can start. No live model test is selected here.

Workflow output contains a Morphe .mpp candidate, source metadata, provenance
and hashes. The selected private candidate feed is now published after static artifact verification;
actual Manager import/update and phone acceptance remain pending. Stable
releases require device acceptance. A friend still needs the supported local
Whisper/Codex companion, not just the keyboard patch bundle.

The current acceptance record is [docs/ACCEPTANCE.md](docs/ACCEPTANCE.md).
Native controls and the Android cross-build are verified separately from the
Gboard bundle and actual phone behavior. The candidate has its own version;
its selected update feed points only at the exact own versioned MPP, never an upstream binary.

The verified main MPP is retained in a private candidate prerelease alongside its
corresponding source and hashes. A verified local copy is in the phone's
`Download/Purple-Patches/` folder. This does not install the keyboard or turn
on the companion. See [candidate delivery](docs/MORPHE-SOURCE.md) and the
[local readback record](docs/local-patch-delivery.json).

The private candidate source URL is
`https://raw.githubusercontent.com/Sodelin/purple-patches/main/channels/candidate.json`.
Its authenticated feed/tag/full-asset route is verified. Manager needs its own
existing repository-access credential; no Manager credential or device setting
was changed. See[network proof](docs/candidate-source-verification.json).
