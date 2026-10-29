# Complete storage-first delivery plan

29 October 2026. This consolidates the storage release around its complete user
workflow. Component controls are supporting evidence, not a usable release.
The wider Natune and Gboard objectives, original ownership and device/model/
installation holds remain intact. Execute one project production job at a time.

## Acceptance target

The user selects existing downloaded songs and a local shared folder. One Move
batch uses size-budgeted track admissions to stream each exact complete track, verifies the completed document,
indexes its location, and removes only its verified cached source through the
settled original app lifecycle. Failure, cancellation and interruption retain
the original or an explicitly recoverable verified copy; uncertain destructive
work is reconciled instead of replayed. Other music/renditions and library
identity remain intact.

Moving between two locations on the same phone is not lasting storage recovery.
It prevents retained duplicates after a successful move. Lasting recovery needs
a verified copy on the selected computer/drive and removal of the appropriate
phone copy. The transfer stage must be included when claiming reclaimed space.

## Whole-pipeline allocation and queue contract

There is temporary source/destination overlap while verification is pending.
The current bulk export adds a complete tagging temp file as well. A loop that
serializes export but starts many other download/export/upload jobs does not
bound the complete pipeline's allocation.

The queue uses a **1 GB (1,000,000,000 byte) temporary-space pool** by default,
per Nolan's adaptive concurrency request. This media-operation budget is separate
from the20GiB engineering build reserve and migration admission rules. It limits
reserved additional allocation, not library size, existing music or song count.
Admit as many tracks as fit their complete peak reservations. Do not wait until
a running copy reaches the limit to stop accepting work.

For every track, reserve the full verified source length needed by the new
local destination, any tagging/provider/upload staging, and bounded metadata/IO
allocation overhead BEFORE creating output. Existing cached originals already
consume free space; do not charge their old allocation twice. Their destination
copies are charged while both exist. Round conservatively for actual allocation;
logical length alone does not prove allocated blocks. Avoid the complete tagging
temp file. Readback hashes reuse destination bytes and bounded buffers.
Unknown lengths or unknown provider spooling cannot be treated as zero. A single
track whose required peak exceeds the pool waits for an explicit budget change
or a separately supported streaming route; never truncate it or secretly raise
the pool. Use local, measured providers for the initial release, not cloud SAF
providers with unknown staging.

Keep each reservation until the corresponding temporary duplicate/staging is
confirmed gone. Completed copy, uploader handoff, timeout, cancellation or a
failure toast does not release it. An uncertain cleanup stays charged and stops
new admissions if necessary. Required operation identities and reconciliation
must survive restart through the existing encrypted recovery contract; restart
does not reset uncertain reservations to zero. No new identity or plaintext
catalog journal is authorized.

After a verified local document is indexed AND the exact cached original is
confirmed removed, that document is the retained music copy rather than temporary
duplication. Its duplicate reservation can settle. A remote receiver streaming
that existing document without extra phone staging can overlap the next move;
remote receipt alone cannot release a separate uploader's staging reservation.
Remote copy verification and separately authorized phone cleanup remain necessary
to reclaim the music's actual phone space. Keeping the shared file is valid but
does not claim remote space recovery.

Check fresh headroom before each admission and during output, with a nonzero
failure/cleanup allowance. Account for outstanding promises without double-counting
bytes already materialized: a free-space snapshot includes their physical writes,
so subtract still-unwritten reserved peaks rather than all reservations again.
Retained output, failed partial files and new music consume actual headroom even
when no longer charged to the duplicate pool. If other writers consume space,
stop new admissions and preserve recoverable originals; this queue cannot promise
that unrelated apps will never fill the phone.

Bound pending work to track metadata, not pre-created pending audio files.
Coordinate competing NaTune writers through their actual owners, preserving
manual pause/stop changes; resume only requests this job paused. Do not stop
unrelated services. One project job may contain multiple admitted tracks; that
is not permission for another chat or production job.

Current implementation limitation: NatuneCachedSource holds the original wrapper
and delegate monitors throughout copy/readback. Several workers therefore cannot
read the same cache concurrently. The queue may overlap independent settled
stages, but true parallel reads require a separately verified descriptor/span
lifecycle and fresh unchanged-source guards. Do not claim adaptive admission
alone makes the current reader parallel, or remove cache locks for throughput.

Release controls must cover mixed sizes fitting exactly at the boundary, one
byte over, changing headroom, unknown/oversized tracks, failed partial cleanup,
restart with unsettled reservations, concurrent admission and actual source-lock
behavior. Provider/device proof must measure peak allocation and verify exact
cache release; a synthetic accounting pass alone cannot prove the1GB limit on
all Android writers.

## Ordered critical path and release gates

| Gate | Required completed behavior | Current evidence / remaining work |
| --- | --- | --- |
|1. Installation and existing data | A supported deployment/data-handoff route preserves the installed library. | Original target identity is pinned. No compatible same-signer patched update or verified old-cache handoff has been established. Resolve this before assuming a newly signed patch can use old app data. |
|2. User entry and destination | Distinct Move to folder action, copy-export preserved, user-selected local content URI and scoped grants; clear progress/cancel/failure. | Android SAF stream adapter is compiled. Actual UI entry and destination lifecycle remain unbound. |
|3. Whole-job admission | Adaptive track count under a1GB reserved temporary pool; actual size/headroom, durable unsettled reservations, owned pause/restore of competing download requests. | The Java mirror and Rust journal have source interfaces to reconstruct pending charges and consumed IDs. Actual authenticated snapshot transfer, headroom, cleanup ownership and scheduling remain unbound; hosted controls do not establish on-device recovery. |
|4. Exact complete source | Real original cache locks, original coroutine/job stops, exact rendition and complete spans; no network fallback or active-track removal. | Cache ABI/source reader implemented;46 controlled cache checks and49 copy checks passed. Original-coroutine cancellation bridge source is implemented; actual app/UI hook and phone behavior remain unverified. |
|5. Verified output | Bounded streaming, complete count/hash readback, real format/extension and usable URI; no full tagging temp. | Actual copy core and SAF adapter implemented/host-checked; real provider and low-space behavior remain untested. |
|6. Transaction and recovery | Original operation identity with required encrypted-before-persistence claim/result/recovery; restart/UNKNOWN reconciliation. | Rust journal and production encrypted-file backend reuse the existing vault;8 RAM lifecycle/recovery tests and Android cross-build pass. Actual app-scoped writer/delegation and real journal acceptance remain unbound; no new identity is authorized. |
|7. Exact cache release | Settle original request, preserve other source variants/readers/owners, release only eligible unchanged cached bytes and reconcile counters. | Original request-key remover and all-source listener both affect deletion scope; even a removal callback may follow task/index/purge failure. Scoped listener/removal and settlement binding remain to implement; no deletion path exists in the new reader. |
|8. Library and playback | Stable original song/rendition→content URI mapping, coherent download indicators/playlists/favorites and offline playback after cache release. | Native content-URI bypass is reusable; request conversion preserves original key/range. Rust original-ID/rendition index is implemented; Android lookup/delegation, pre-router bytecode hook and prefetch/recovery guards remain unbound (shared-document-playback-source-review.json). |
|9. Remote transfer and cleanup | Selected receiver gets complete verified bytes before authorized phone-file removal; each temporary reservation settles before reuse; no unaccounted upload staging. | Existing route/owners are retained. No media transfer/deletion is performed by this plan or the current source checks. |
|10. Packaging and phone proof | Reproducible off-phone MPP/APK patching, declared compatibility, full unrelated-code/resource preservation, finite install/regression/rollback acceptance and verified update source. | Previous unrelated quality candidates are not a Move release. A storage MPP, complete patched APK verification, installation route and device acceptance remain open. |

## First gate: signer/data continuity

Android app updates require a compatible signing certificate or valid signing
rotation lineage. Re-signing a modified APK does not preserve the original
signature. A separate package installation is not automatically allowed to read
the original package's private downloads. Uninstalling the original is not a
data-preserving deployment plan.

The public target build source configures release signing conditionally and does
not track its expected release keystore. No key material was inspected or
copied, and no same-signer route has been proven. Resolve supported original
signer cooperation or an existing app-provided data handoff. If no such update
route is available, evacuate the existing library through the installed app's
supported export workflow before adopting a differently signed patched app.
Prepare that sequential recovery workflow with its existing media owner; do
not bypass private data access, uninstall or infer permission for device work.

The reviewed Android backup rules explicitly exclude download/player-cache
files and the ExoPlayer index in both cloud and device-transfer lanes. The app's
own backup ZIP includes selected settings/account/library databases and fonts,
not downloaded audio. Do not treat a library/database backup as a verified media
handoff. Exact public source pins/hashes and selected rule lines are retained in
existing-data-handoff-source-review.json; no backup operation was performed.

An original-app single-song export to a remote document provider is a possible
data-handoff route without replacing the original app. It retains the cache and
does not prove source completeness or remote durability. Reviewed provider
sources include write-error, overwrite and local-cache hazards; none is yet an
accepted automatic-deletion route. See REMOTE-HANDOFF-REVIEW.md and
remote-handoff-source-review.json before selecting a provider or receiver.

Primary Android rule: https://developer.android.com/studio/publish/app-signing
Pinned build source:
https://github.com/NatuneGroup/ArchiveTune/blob/b7e4dd444fea202a5c32e96f7b1199a74fe3ae70/app/build.gradle.kts

## Release proof and rollback

Before an authorized phone trial, prepare the exact artifact, signer/package/
version plan, complete data-preservation/restore path and bounded test steps.
One small existing track must prove complete destination bytes, readable audio,
correct app state and exact source release. Test cancellation, short/null write,
provider/grant failure, low space, process interruption/restart, duplicate action
and changed source. Then verify a bounded multi-track queue cannot accumulate
pending files and does not change unrelated media. Keep required encrypted
uncertainty/recovery records; an error must not trigger another deletion.

Rollback must preserve original music and app state, retain source/build pins,
and stop only this job. Old installation/model/privacy holds are not released
by a green hosted test. Do not issue a shipping ETA until deployment/data access
and the first end-to-end phone proof are established.

## Subsequent capabilities

The future-download destination is a full direct content-URI writer plus native
download/resume/completion and playback indexing, avoiding a complete private
cache copy first. It is a separate deliverable from moving existing downloads.
Continue runtime patch settings, mobile quality/actual FLAC verification,
reviewed source adapters, FLAC replacements, Smart Downloads/playlist ownership,
road-trip retention and navigation/video work through their existing plans.
Keep the full Gboard dictation/translation objective and its existing holds.

Report progress at integrated milestones: deployment/data route established,
end-to-end storage build verified, one-track phone proof, then bounded batch
release/update source. Do not present another control count as storage recovery.
