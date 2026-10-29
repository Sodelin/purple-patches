# Full patch objective and component ownership

2026-10-29 continuation. Work directly in this source repository, without
sub-agents. Each app needs its own source/binary compatibility proof, optional
features, maintained bundle/feed and actual user acceptance. Do not shrink the
whole objective to a single scaffold or successful static check.

| Component | User-requested behavior | Current evidence / remaining work |
| --- | --- | --- |
| Gboard | Full Whisper large-v3 dictation; explicit gpt-6.1-sol translation with preview/insertion; retain stock keyboard | Alpha.17 retains the copy-only preview and uncertain-start warning and accepts companion-sized completed results; hosted controls and pinned-APK static checks passed. Two builds had identical member bytes and both bundles have private full-readback archives. [Current completion audit](completion-audit-20261029.json) records the open privacy, job recovery, model, installation, rollback and Manager-update gates. |
| Natune / renamed ArchiveTune fork | Strict FLAC streaming/downloads, full quality on mobile data, reliable additional compatible providers alongside Qobuz | The separate existing owner has a [combined development build](https://github.com/Sodelin/purple-patches/blob/8dd56b1877d34a49ab20f1db59c510e4a44190ab/natune/combined-dev-verification.json) with audio settings and a folder/FLAC carrier. It is uninstalled, needs separate native finalization, and does not yet implement the Move action or complete FLAC/provider selection. Original data continuity and real app/receiver acceptance remain open. |
| MPV | Automatic subtitle lookup based on media filename / suitable subtitle provider | Earlier requested idea retained. Need exact installed app/version, existing script/plugin support, provider terms/interface and acceptance before implementing. No media access or provider account operation inferred. |
| Existing Discord modifications | Review already-applied changes, including separate microphone/output audio behavior | User said patches were already applied. Need actual patch source/version before reviewing; do not repeat installation or invent a new patch need. |
| Existing chess / Stockfish modification | Review existing local-engine patch and retain useful integration ideas | Already-applied modification reported by user; source/version and actual review remain unverified. No replacement engine or model operation selected. |

Shared support: existing standalone storage migration retains its own root,
goal, owners and holds. Its queued historical messages are not cleanup grants.
The separate NaTune owner retains its worktree, source/index and media scope. Current
Morphe authentication and RuStore source reviews are in the parent coordination
records; neither credential configuration nor assumed RuStore compatibility is
a patch deliverable.

Next concrete Gboard gates are the authenticated private bootstrap, durable
job recovery and safe editor/device acceptance. The NaTune owner continues its
own app/data and transaction integration. Do not substitute the mobile-data
override or source carrier for the complete FLAC/provider request. While
runtime/model/device holds persist, source/build/interface work stays eligible
within its own boundaries.
