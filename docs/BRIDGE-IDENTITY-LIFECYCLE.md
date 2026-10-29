# Gboard companion: owner identity and lifecycle

**Activation blocker, open.** At source `dcfc2089736f3d09014bfbeea7fe3438b96214f5`,
`companion/src/lib.rs` uses one boolean, `Identity::current`, for both observing
the recorded service and comparing the executable of the command making the
observation. A live same-UID service can therefore fail the check when an
updated command is executed from a different path, even when the observed PID,
birth, boot, UID, executable path, device and inode match its original record.
Errors reading those fields also produce `false`; they do not prove exit.

In that situation `bootstrap` can remove the old encrypted `bridge.age` and
attempt to start a service. `stop` can remove the record and report a stale
record removed without stopping the old service. The shutdown wait likewise
interprets an observation failure as absence. `serve` separately holds a
`server.lock` for its lifetime: if the original process continues to hold the
same effective lock, that lock should refuse a second server. The source does
not prove a second server starts, and no real service or incident was observed.
Record removal can nevertheless lose the control information for a live owner.

An explicitly ignored public diagnostic in `companion/src/lib.rs` copies the
test executable on a hosted Linux runner. The copy observes its still-live
same-UID parent through the real `Identity::read` and checks that the current
predicate returns false while the caller executable differs. The existing
`companion.yml` workflow's optional `identity_diagnostic` mode runs only that fixture; it does not
open an actual vault, run `bootstrap` or `stop`, start a bridge, or simulate
Android. [Hosted run 40959549174](https://github.com/Sodelin/purple-patches/actions/runs/40959549174)
executed exactly that diagnostic at source `aeb34e8e2a60df6e12feb20b33cf2c62cd289617`:
the live fixture owner was classified as not current. The test passes because it
reproduces the defect, not because the defect is fixed. The run skipped the
normal suite and Android cross-build. See [the bounded evidence](bridge-identity-diagnostic-verification.json).

Review must distinguish a matching live owner, a conclusively different or
exited owner, and unknown observation; preserve records on uncertainty; and
address process/lock races, changed executable paths, scope of stop, version
compatibility, crash recovery, and the actual encrypted record owner. A simple
removal of the caller-path comparison would not settle the errors-as-absence
problem. Synthetic controls and later separately authorized device acceptance
are required. The existing Dot review is prepared but the route to it is not
verified; no peer approval or fix is claimed.
