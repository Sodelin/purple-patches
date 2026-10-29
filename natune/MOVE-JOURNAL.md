# Durable Move mapping and reservations

The Rust companion's public `natune_journal` module supplies source for a
Termux-owned encrypted journal. It has no HTTP/CLI dispatch or Android caller,
starts no listener and inherits no keyboard bearer or shutdown capability.
Morphe UI, authenticated app delegation and actual native writer observations
remain unbound. It is not an installed Move feature or a deletion permit.

`EncryptedFiles` reuses the existing vault::retain/open implementation and the
installed single-identity featurectl seal/readback path. It creates no key or
identity. Only ciphertext reaches the exclusive immutable event files; file and
parent directory sync plus full decrypt/readback precede successful commit.
The global encrypted originals are retained. A partial/uncertain event is not
replaced or automatically deleted. One private OS lock covers the store lifetime.
The eventual owner supplies a dedicated private state parent; journal paths are
not taken from an Android URI or arbitrary app message.

## Native sequence

| Durable stage | Meaning supplied by the real owner | Reservation / mapping |
| --- | --- | --- |
| Begin | Original operation ID, song/rendition, source fingerprint/length, peak allocation and selected destination tree/name | Full peak charged; no document mapping |
| Destination | Actual new document identity after creation | Still fully charged |
| Verified | Complete document length/hash readback matches that exact destination and selected source | Still charged; no published playback mapping |
| Indexed | Verified document is durably selected for the original song/rendition | Mapping reference available; still charged |
| RemovalPending | Owner durably marks its one original scoped removal attempt before acting | Mapping remains; still charged through uncertainty |
| Moved | Owner has checked exact source/index removal and all staging disposition | Mapping remains; duplicate reservation released |
| Abandoned | Before indexing only: owner has verified cleanup and retained the original source | Reservation released; operation ID remains consumed |

**Current source gate:** New `RemovalPending` events are refused before any
journal write. The v1 journal has no actual binding to an independently
verified computer copy or the user's reconciled selection/KEEP decisions.
A serialized event or a successful local
copy cannot supply those missing checks. Old v1 `RemovalPending` histories can
still be read, and the existing owner's later `Moved` observation can reconcile
them; replay does not perform removal. The host must verify that observation.
This is a journal guard, not a system-wide block on other code removing files.
No production app is wired to this API.

Hosted [run 40910866618](https://github.com/Sodelin/purple-patches/actions/runs/40910866618)
at `49703d7b4527cd8fd8834f4d1a7496b55c727de0` passed 38 Rust library
controls, including refusal of a new removal and synthetic legacy-pending
replay, and cross-built the AArch64 checker. Artifact and source readback are
recorded in `removal-gate-verification.json`. The checker was not run on the
phone; these controls do not verify the real receiver, original app, encryption
backend, selection or any music copy.

V2 source adds a separate computer-transfer branch after `Verified`:

| Durable stage | Required actual owner observation | Reservation / mapping |
| --- | --- | --- |
| RemoteVerified | The selected receiver's independently checked byte count and SHA-256 equal this operation's verified source/local copy; receiver and transfer identities are bound to the actual receipt | Still charged; source and staging remain |
| StagedRemotely | The owner has confirmed this operation's disposable shared document and related temporary staging are gone; original cached song remains | Temporary charge released; remote receipt retained; no local playback URI advertised |

The actual receiver/authentication, original source, cleanup and Android owner
are still **unbound**. These events are observations supplied by the trusted
owner, not proof of their own truth, permission to remove source music, or a
working scheduler. Unknown remote completion or cleanup keeps the reservation
charged until the owner reconciles it. Reusing a receiver/transfer identity for
another song, mismatched bytes or digest, out-of-order cleanup, and a schema
downgrade refuse. Concurrent operations may not claim the same live local
document; after confirmed cleanup, an owner may select a newly created document
with the same provider URI and recovery reconciles the final states independent
of operation sort order. After confirmed staging cleanup, the document is not returned
as a live local playback mapping. The original song remains claimed and its
operation ID consumed; moving it later still requires an actual bound removal
and selection path.

Older v1 records remain readable. The first `RemoteVerified` record upgrades
that operation's chain to schema 2, with no downgrade; an older reader that
does not know those events must refuse rather than silently discard them. No
history is rewritten. No serializer or receipt descriptor authenticates a
caller or rechecks computer bytes on restart.

Hosted [run 40911881847](https://github.com/Sodelin/purple-patches/actions/runs/40911881847)
at `ef58831a2e18bb966f173ac6ca394c4b2537fba4` passed 42 Rust library
controls and cross-built the checker. The fixtures cover matching remote
receipts, staged cleanup and restart, uncertainty, receipt reuse, schema
downgrade, and live destination conflicts including reordered recovery. Full
artifact and source hashes are recorded in `remote-stage-verification.json`.
All receiver and cleanup observations were synthetic; the checker was not
executed on the phone and no original media or app data was accessed.

The journal records observations; none of these enums authenticates a caller,
recreates a native permit or executes a copy/removal. Android may not submit
serialized `Moved` as a cleanup grant. The future captured owner must actually
check the byte proof, current URI grant/index, source fingerprint, readers,
manager/index/cache lifecycle and source/staging release before recording it.
No timeout/cancel/removal callback automatically advances this state machine.

A Begin's selected tree/name is recovery context, not permission to delete by
name. A crash between provider creation and recording its returned URI leaves
an unresolved reservation requiring actual provider/owner reconciliation; it
must not create another file or guess which matching title belongs to the job.

Every event binds its original operation, monotonic generation and previous
plaintext record hash. Replay is read-only and checks filename/body identity,
closed schemas, lifecycle order, document identity/extent/hash and uniqueness
of active song/rendition claims. Corruption or a missing middle event refuses;
this does not provide an external anti-rollback anchor against deletion of a
whole history or its final suffix, nor sandbox malicious same-UID writers.
Age encryption is not a sender-authentication/delegation protocol.

Accepted IDs remain consumed. A separately authorized new attempt for a fully
abandoned/cleaned resource can use a new ID; UNKNOWN is not abandonment. Existing
mapped renditions need an explicit replacement protocol before another claim
can supersede them; the FLAC replacement lane remains separate unfinished work.

## Pool, restart and current limits

The durable journal counts complete outstanding peaks against the owner-selected
pool (the NaTune default request is1GB). Mixed-size admissions are atomic under
the owner lock. Reopening restores all holds. A smaller later pool does not drop
old claims or prevent reconciliation; it prevents further overcommit. An
uncertain append poisons the current handle, so it cannot report zero reserved
bytes or allow a fresh ID retry. Reopen and inspect the retained state instead.

Use this durable admission as the real host authority, with the Java byte budget
as an in-process guard, never as a replacement for durable recovery. They are
not two independent pools granting twice the capacity. `admission_recovery`
provides an in-memory snapshot of every original operation ID, source/peak
sizes, pending state and total charge, without song names or document URIs.
`TemporarySpaceBudget.fromRecovery` can rebuild the Java mirror, retain
terminal IDs, reject inconsistent totals and retain holds larger than a new
limit. A restored hold cannot copy again automatically and only settles after
the original owner verifies cleanup. The host must freeze admissions and
transfer the complete snapshot through its actual authenticated app-scoped
binding; the objects and matching totals do not themselves provide that
binding or prove completeness. Values outside Java's positive long range must
refuse rather than truncate. This API does not yet connect the two processes.

Hosted Rust run40907405554 and Java run40907229999 both used source commit
2b73f584d76499cab85d22669cfc2294b38278b4. The Rust controls exercise pending
and terminal IDs after reopening a RAM fixture; the Java controls exercise
restoration above a lowered limit, consumed IDs and inconsistent snapshots.
Complete artifact/source readback is recorded in journal-recovery-verification.json
and budget-recovery-verification.json. These are separate controls, not an
end-to-end process-bound or device-restart test.

The paired Android admission and app-scoped receipt remain to integrate.
The journal does not measure free blocks, provider allocation, current grants,
current document contents or external writers. Actual headroom/cleanup checks
are still required and the general20GiB engineering build reserve is separate.

`document(song,rendition)` returns an indexed reference with URI/extent/digest;
it is not a fresh media verification or an authorization token. The native
request conversion and pre-router hook must retain original song identity and
reject stale/revoked document access without silently fetching lossy/network
fallback. No phone music, model, key, permission or installed app is touched by
this source module.

Controls use a RAM-only Store with public synthetic events. They exercise the
actual Rust state machine, recovery, ordering, pool and uncertainty behavior;
they do not claim actual vault encryption or Android/provider acceptance.
The production backend calls the retained real vault, not fixture encryption.

## Retained hosted verification

Run40903101938 at957a94037e01c965fd01f5e56289fc4073d98a0f passed36 Rust library
tests, including all8 new journal tests, and the hosted aarch64-linux-android
cross-build. move-journal-verification.json retains full artifact/member/source
hash readback and the checked AArch64 ELF metadata. The binary was not executed
or installed on the phone. Generic library/source compilation does not establish
an externally callable journal command; no new command/endpoint was exposed.
The tests use the RAM Store; production vault/file backend acceptance remains
open and must not be inferred from those tests or the successful cross-build.

## Finite backend-check candidate

`companion/src/bin/natune-journal-check.rs` is a separate, finite candidate
for checking the existing encrypted backend with synthetic public fixtures.
It does not access app data, music or a provider, and does not authorize or
perform an Android Move. It uses the existing Termux record
identity and leaves encrypted proof records under a fresh caller-provided case
ID. An existing or uncertain case must not be replayed under a new ID. A
cross-build by itself does not establish that this check ran successfully on
the phone; actual backend, app-owner and device acceptance remain open.
The current v3 checker source exercises the remote-verified and cleaned-staging
states using only synthetic receiver and cleanup observations. It creates no
audio or staging document and must not be reported as an actual Fuji transfer.
Hosted [run 40913495559](https://github.com/Sodelin/purple-patches/actions/runs/40913495559)
at `e1d10051d5de553696df059f33220c2919d8e0da` passed 42 library controls and
cross-built that checker; `remote-journal-checker-verification.json` records
the artifact/source hashes. The checker itself was not executed on the phone.

Hosted run40905442006 at2dd1a59201b4d90d39514365c4f0efe8bdccdc97
passed36 Rust library controls and cross-built the checker for AArch64 Android.
`journal-check-verification.json` records complete artifact/member and run-source
hash readback. The checker was not executed or installed on the phone.
