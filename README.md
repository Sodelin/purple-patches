# Purple Patches

**Public community project**, made public on 29 October 2026 at Nolan's request.
Published components are experimental prereleases, not phone-accepted stable
releases. The Natune mobile-data patch has a verified off-phone build alongside the
Gboard candidate; strict FLAC/provider integration remains in development. This project is independent of the Morphe maintainers.

The published Gboard alpha.1 candidate can mistake an unavailable editor response
for empty context. Alpha.3 corrects this in source; alpha.4 also adds bounded
microphone capture. Both passed hosted bundle and APK checks. Alpha.5 also passed
its [hosted bundle](docs/gboard-alpha5-build-verification.json) and
[exact-APK static checks](docs/gboard-alpha5-apk-verification.json), and requires
the newer companion's explicit voice-preflight capability. These
candidates have not passed device acceptance or replaced the public feed.
See [editor snapshot safety](docs/EDITOR-SNAPSHOT.md) before using the candidate.
The alpha.6 source adds a per-translation Medium/Max reasoning choice. Its
[hosted bundle](docs/gboard-alpha6-build-verification.json) passed; phone
acceptance remains open.
Alpha.7 adds a source-level mitigation for a Termux bootstrap logging risk and
separates the microphone request from the local-tools request. The mitigation
and a replacement capability handoff remain unverified. See [bootstrap privacy](docs/BOOTSTRAP-PRIVACY.md)
and [permission flow](docs/PERMISSION-FLOW.md). Do not activate the candidate.
Alpha.8 makes the existing 60-second recording limit visible; its
[off-phone build](docs/gboard-alpha8-build-verification.json) and
[exact-APK static checks](docs/gboard-alpha8-apk-verification.json) passed.
Longer dictation and Android validation remain open. See [long dictation](docs/LONG-DICTATION.md).
Alpha.9 retains the result preview after an insertion request. Alpha.10 binds a
pending local-tools connection to the offered keyboard session; its [hosted
bundle and synthetic controls](docs/gboard-alpha10-build-verification.json)
passed, as did [exact-APK static checks](docs/gboard-alpha10-apk-verification.json).
These source/build checks do not establish Android behavior or resolve
the bearer-output privacy blocker. The public Gboard feed still points to the
older alpha.1 and warns against installing it.
Alpha.11 uses the local Sol catalog to show only advertised Medium/Max options;
its [hosted bundle and synthetic catalog controls](docs/gboard-alpha11-build-verification.json)
and [exact-APK static checks](docs/gboard-alpha11-apk-verification.json) passed.
No live account or Android behavior has been verified.
Alpha.12 offers a copy-only preview when the original editor anchor changed or
cannot be verified within the same non-sensitive session. Its [hosted bundle
and policy controls](docs/gboard-alpha12-build-verification.json) and
[exact-APK static inspection](docs/gboard-alpha12-apk-verification.json)
passed. Session-end retention, active composition and Android behavior remain
unverified; it has not replaced the public feed.
Alpha.13 warns if a local job may have started when cancellation or an error
occurs. Its [hosted bundle and cancellation-ordering controls](docs/gboard-alpha13-build-verification.json)
and [exact-APK static inspection](docs/gboard-alpha13-apk-verification.json)
passed. This warning does not provide durable job recovery or prove that a
cancelled job stopped; the candidate remains uninstalled.
Alpha.14 replaces the build-clock manifest timestamp with the source commit
time and refuses uncommitted or extra overlay inputs. Two [hosted builds](docs/gboard-alpha14-reproducibility.json)
produced identical patch bytes on the observed runner image; a separate
[static APK inspection](docs/gboard-alpha14-apk-verification.json) passed.
The wider privacy, recovery, phone and release gates remain open.
Alpha.16 restricts the hosted build's SDK view to Android 36 and pins its JDK
and action references. Two [hosted builds](docs/gboard-alpha16-reproducibility.json)
matched all artifact member bytes, and an [exact-APK static inspection](docs/gboard-alpha16-apk-verification.json)
passed. SDK provenance and the same privacy, recovery, device and release gates
remain open; this is not an installation recommendation.
Alpha.17 separates the companion's result-size limit from the smaller input
limit, so a valid larger completed result can reach preview. The [hosted
controls](docs/gboard-alpha17-build-verification.json) and [exact-APK static
inspection](docs/gboard-alpha17-apk-verification.json) passed; Android behavior,
session recovery and the existing release gates remain open.
Alpha.18 preserves an installed locale's script and region in the translation
picker, so choices such as Simplified and Traditional Chinese need not collapse
to the same language code. [Hosted controls](docs/gboard-alpha18-build-verification.json)
and [static APK checks](docs/gboard-alpha18-apk-verification.json) passed;
actual Android selection and translation quality remain unverified.
Alpha.19 improves preservation of existing network-security resources. Two
[hosted builds](docs/gboard-alpha19-build-verification.json) matched, and the
[selected public APK](docs/gboard-alpha19-apk-verification.json) passed static
patch and compiled loopback-policy checks. General resource-alias handling,
private bootstrap, companion recovery and real device acceptance remain open;
alpha.19 has not been published to the candidate feed.
Alpha.20 adds an access-point fallback for a missing or incompatible optional
dependency. Its [hosted controls and two-build comparison](docs/gboard-alpha20-build-verification.json)
and [exact-APK static checks](docs/gboard-alpha20-apk-verification.json) passed;
it has not passed Android acceptance or replaced the public feed.
Alpha.21 source explicitly bypasses the Java proxy selector for local bridge
requests; its hosted verification is in progress. See [loopback transport](docs/GBOARD-LOOPBACK-TRANSPORT.md).

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
stable Termux path ~/.local/bin/purple-gboard-service. The keyboard does not
persist the bearer in its preferences, but the current bootstrap sends it
through Termux stdout and a one-shot explicit nonexported callback.
Termux may log that stdout. The bearer handoff is a release blocker; see
[bootstrap privacy](docs/BOOTSTRAP-PRIVACY.md).
The keyboard refuses text/audio submission unless the companion advertises
keyboard_data_path=memory-only-v1, and refuses dictation unless the declared
model family is whisper-large-v3. The older retained bridge does not satisfy
those gates merely because it can start. No live model test is selected here.

Workflow output contains a Morphe .mpp candidate, source metadata, provenance
and hashes. The selected public candidate feed is published after static artifact verification;
actual Manager import/update and phone acceptance remain pending. Stable
releases require device acceptance. A friend still needs the supported local
Whisper/Codex companion, not just the keyboard patch bundle.

The current acceptance record is [docs/ACCEPTANCE.md](docs/ACCEPTANCE.md).
Native controls and the Android cross-build are verified separately from the
Gboard bundle and actual phone behavior. The candidate has its own version;
its selected update feed points only at the exact own versioned MPP, never an upstream binary.

The published candidate prerelease and the verified local copy in
`Download/Purple-Patches/` are the older alpha.1, not alpha.20. The public feed
warns against installing alpha.1. See [candidate delivery](docs/MORPHE-SOURCE.md)
and the [local readback record](docs/local-patch-delivery.json).

The candidate source URL is
`https://raw.githubusercontent.com/Sodelin/purple-patches/main/channels/candidate.json`.
It is now publicly accessible; a repository-access token is not needed for
public downloads. Its earlier private authenticated feed/tag/full-asset proof
is retained as dated history in [network proof](docs/candidate-source-verification.json).
No Manager credential or device setting was changed. Older documents describing
private custody refer to the state before the repository became public.

## Natune candidate

The separate [Natune prerelease](https://github.com/Sodelin/purple-patches/releases/tag/natune-patches-v0.1.0-alpha.1)
contains the optional, default-off **Allow full-quality audio on mobile data**
patch for `moe.rukamori.archivetune` version `N202610191717`. It removes five
audio-specific low-data results while retaining unrelated low-data behavior.
Exact APK patching and independent DEX/manifest/resource preservation passed
off phone. It does **not yet enforce FLAC or add providers**, and phone
behavior/Manager import have not been verified.

Use the component's own JSON source so it selects the Natune bundle rather than
the Gboard bundle or an auxiliary source/documentation MPP:
`https://raw.githubusercontent.com/Sodelin/purple-patches/main/channels/natune-candidate.json`.
This feed is public; no GitHub token is required. See
[Natune source and verification](natune/README.md) and
[remaining FLAC integration](natune/STRICT-FLAC-INTEGRATION.md).
