# Full patch objective and component ownership

2026-10-29 continuation. Work directly in this source repository, without
sub-agents. Each app needs its own source/binary compatibility proof, optional
features, maintained bundle/feed and actual user acceptance. Do not shrink the
whole objective to a single scaffold or successful static check.

| Component | User-requested behavior | Current evidence / remaining work |
| --- | --- | --- |
| Gboard | Full Whisper large-v3 dictation; explicit gpt-6.1-sol translation with preview/insertion; retain stock keyboard | Existing built/private delivered candidate and source-feed proof. `completion-audit.json` still records unproven live model, privacy/control, installation, rollback and actual Manager update acceptance. Original holds remain. |
| Natune / renamed ArchiveTune fork | Strict FLAC streaming/downloads, full quality on mobile data, reliable additional compatible providers alongside Qobuz | Exact installed public APK bytes/signer pinned; independent hosted DEX inspection succeeded. Five-site mobile-data patch compiled and applied off phone. Independent preservation gate is under investigation; no accepted installation, strict-FLAC enforcement or additional provider integration yet. Existing connected GitHub app resolved workflow registration without broadening credentials. |
| MPV | Automatic subtitle lookup based on media filename / suitable subtitle provider | Earlier requested idea retained. Need exact installed app/version, existing script/plugin support, provider terms/interface and acceptance before implementing. No media access or provider account operation inferred. |
| Existing Discord modifications | Review already-applied changes, including separate microphone/output audio behavior | User said patches were already applied. Need actual patch source/version before reviewing; do not repeat installation or invent a new patch need. |
| Existing chess / Stockfish modification | Review existing local-engine patch and retain useful integration ideas | Already-applied modification reported by user; source/version and actual review remain unverified. No replacement engine or model operation selected. |

Shared support: existing standalone storage migration retains its own root,
goal, owners and holds. Its queued historical messages are not cleanup grants.
The Natune work changes no source-owner scope outside this repository. Current
Morphe authentication and RuStore source reviews are in the parent coordination
records; neither credential configuration nor assumed RuStore compatibility is
a patch deliverable.

Next concrete implementation gate: complete independent Natune preservation
verification, then implement strict media validation and resolver/cache policy
as separate selectable functionality. Do not substitute a mobile-data override
for the complete FLAC/provider request. While runtime/model/device holds
persist, source/build/interface work stays eligible within its own boundaries.
