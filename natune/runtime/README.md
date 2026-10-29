# Runtime settings extension source increment

`RuntimeSettings.java` and `PurpleSettingsActivity.java` implement native Android
configuration/accessor/page source for the existing two Natune policy features.
They read/write only the owned patch preference file; reset touches no music or
original app settings. Disabling the mobile policy is designed to return the
actual supplied stock result, rather than reconstructing network status.

Patch-presence marker methods intentionally return false until actual selected
Morphe hooks bind them. These are source hook sites, not claims that features
are installed. Application bootstrap, exact original-result wrapper, FLAC guard
switches, marker rewriting, manifest/native settings navigation and independent
APK/resource comparisons remain required. The original application entry now
has actual release inspection: App.onCreate22registers/813code units/4try blocks.
Any added prefix must preserve those original handler boundaries, not loosen
zero-try guard checks. No app bootstrap or UI has been injected by this source.

`materialize.py --runtime-settings` selects a new development profile and copies
only these Android Java sources into the official extension module. It excludes
an otherwise unnecessary second Kotlin standard library. This profile has not
yet been built or adopted; current selected feeds and existing patch behavior
are unchanged. No phone install/UI/permission/model action accompanies it.

The separately implemented VerifiedStreamCopy and SharedDocumentDestination
provide bounded streaming, full destination readback and the real Android SAF
stream boundary for future Move to folder integration. Hosted run40886068466
passed49 core controls and real SDK compilation. They are not an installed UI,
a Media3 source lease, a verified provider grant or a cache-deletion authority.
See ../SHARED-STORAGE.md and ../shared-storage-verification.json for the exact
remaining binding and acceptance gates. These classes add no model/network
client, listener, grant, runtime record or source deletion path.

NatuneCachedSource now supplies exact-release cache-source code using original
wrapper/delegate monitors, full span coverage and original stop checks.
Run40889712120 passes46 public-fixture cache-source controls plus49 copy
controls. It remains unbound to an actual APK UI hook and has no removal path.
See ../CACHE-SOURCE-BINDING.md for the original broad-purge callback hazard and
remaining transaction, indexing and phone acceptance requirements.

TemporarySpaceBudget implements synchronized peak-byte admissions for one batch,
with a1GB default and adaptive file count. Its Reservation.copy calls the actual
VerifiedStreamCopy only after admission and original owner/length checks, using
a destination factory so output need not exist before admission. Full known
staging and rounding overhead belong in the reservation. Neither successful
readback nor a failed factory releases space. Accepted IDs remain consumed;
release requires the owner's explicit cleanup-verification callback and is
refused during copy or another settlement. No automatic timeout/close release.

This is an in-memory counter and copy binding, not an Android scheduler,
allocation quota, encrypted journal or cache-removal implementation. The host
must restore unsettled claims before accepting another batch after restart,
provide real ongoing headroom/cancellation checks, verify/index the retained
URI and settle exact cache removal. Cleanup callbacks in controls are stand-ins.
No UI calls this yet. Original monitor serialization still limits cache read
parallelism. See ../STORAGE-FIRST-PLAN.md for the full deployment/release gates.

Hosted run40896283116 at8ba4582e9d9d6f780871bc41d7e675da13def1f9 passed41
budget/copy integration controls,49 existing copy controls,46 cache-source
controls and real Android SDK compilation of the SAF adapter. Full artifact
member hashes and every recorded source hash were compared against the run's
immutable Git commit; ../temporary-budget-verification.json retains the proof.
The41 controls cover mixed-size boundary admission, unknown/overflow sizes,
concurrent admission, retained charges after copy/provider/stop failure,
consumed IDs, reentrant cleanup and a complete actual copy/readback. They do
not prove real Android allocation or restart recovery.

NatuneCoroutineStop binds the retained original public continuation/context/job
check ABI. It preserves the original cancellation exception and combines it
with a required caller-supplied owner/headroom check. No original Job/context is
reconstructed, and no private reflection or default non-stopped owner is used.
Run40897610592 at96f368cb53dd4e7034513adb42ad76f9ed24c901 passed10 new
coroutine bridge controls,49 copy,46 cache and41 budget controls plus Android
SAF SDK compilation. ../coroutine-stop-verification.json records complete artifact
and immutable-source hash readback. The cancellation tests use public ABI
stand-ins, including a late cancellation in the actual copy implementation;
no original Android coroutine or installed APK callsite has executed this code.
The source-level manager/transport settlement audit is separately retained in
../removal-lifecycle-source-review.json and ../CACHE-SOURCE-BINDING.md.
