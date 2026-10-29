# Complete storage-first delivery plan

29 October 2026. This consolidates the storage release around its complete user
workflow. Component controls are supporting evidence, not a usable release.
The wider Natune and Gboard objectives, original ownership and device/model/
installation holds remain intact. Execute one project production job at a time.

## Acceptance target

The user selects existing downloaded songs and a local shared folder. One Move
job at a time streams the exact complete track, verifies the completed document,
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

The new queue must hold a single end-to-end admission until the current track's
chosen workflow is settled. The next track is not admitted merely because the
previous file was handed to an external uploader. Use bounded pending metadata,
not a batch of materialized pending files. Temporarily pausing competing NaTune
downloads must preserve their original user stop states and resume only jobs
that this operation actually paused, unless the user changed their state.
Do not stop unrelated apps/services. Other user writers can still consume free
space, so check actual headroom before each track and stop cleanly if it changes.

For the local streaming path, reserve the measured complete destination length
plus bounded metadata/IO overhead while the cached source still exists. Avoid
the full tagging temp file. A100MiB track therefore needs roughly another
100MiB, not zero extra bytes. This is an estimate until the exact local document
provider is verified. Cloud SAF providers may spool additional copies; do not
apply the local-folder allocation claim to them. Reading back a hash uses the
existing destination bytes, not another complete file.

The existing selected remote pull route should stream the shared file without
another phone-sized upload staging file. Verify the receiver's complete bytes,
then perform the separately authorized phone cleanup. Export→verify→local cache
release→transfer→remote verify→phone cleanup can run one track at a time. The
user can alternatively keep the shared phone file; that mode cannot claim its
space was reclaimed remotely.

## Ordered critical path and release gates

| Gate | Required completed behavior | Current evidence / remaining work |
| --- | --- | --- |
|1. Installation and existing data | A supported deployment/data-handoff route preserves the installed library. | Original target identity is pinned. No compatible same-signer patched update or verified old-cache handoff has been established. Resolve this before assuming a newly signed patch can use old app data. |
|2. User entry and destination | Distinct Move to folder action, copy-export preserved, user-selected local content URI and scoped grants; clear progress/cancel/failure. | Android SAF stream adapter is compiled. Actual UI entry and destination lifecycle remain unbound. |
|3. Whole-job admission | One track across the selected export/transfer/cleanup pipeline, actual size/headroom, owned pause/restore of competing download requests. | Full queue/owner admission and background scheduling remain to implement. |
|4. Exact complete source | Real original cache locks, original coroutine/job stops, exact rendition and complete spans; no network fallback or active-track removal. | Cache ABI/source reader implemented;46 controlled cache checks and49 copy checks passed. Actual app/coroutine/UI hook and phone behavior remain unverified. |
|5. Verified output | Bounded streaming, complete count/hash readback, real format/extension and usable URI; no full tagging temp. | Actual copy core and SAF adapter implemented/host-checked; real provider and low-space behavior remain untested. |
|6. Transaction and recovery | Original operation identity with required encrypted-before-persistence claim/result/recovery; restart/UNKNOWN reconciliation. | Original encrypted phone identity exists. The app-scoped writer/admission and actual move journal remain unbound; no new identity is authorized. |
|7. Exact cache release | Settle original request, preserve other source variants/readers/owners, release only eligible unchanged cached bytes and reconcile counters. | Original remover is asynchronous and its listener purges all recognized source variants from both caches. Scoped listener/removal and settlement binding remain to implement; no deletion path exists in the new reader. |
|8. Library and playback | Stable original song/rendition→content URI mapping, coherent download indicators/playlists/favorites and offline playback after cache release. | Existing MediaStore scanner is not an arbitrary SAF/original-ID mapping. New index and playback selection remain unbound. |
|9. Remote transfer and cleanup | Selected receiver gets complete verified bytes before authorized phone-file removal; every queued track settles before next admission. | Existing route/owners are retained. No media transfer/deletion is performed by this plan or the current source checks. |
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
