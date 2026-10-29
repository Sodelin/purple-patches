# Runtime settings extension source increment

`RuntimeSettings.java`, `PurpleSettingsPage.java` and `NatuneSettingsNavigation.java` implement native Android
configuration/accessor/page source for the existing two Natune policy features.
They read/write only the owned patch preference file; reset touches no music or
original app settings. Disabling the mobile policy is designed to return the
actual supplied stock result, rather than reconstructing network status.

Patch-presence marker methods intentionally return false until actual selected
Morphe hooks bind them. The runtime-settings source checkpoint now contains an
experimental application bootstrap, original-result wrapper, marker rewriting
and switchable local decoder guards. Hosted run 40920698630 built the profile,
applied it to the exact pinned APK, and independently compared the original
program and resources after checking those additions. It did not execute the
Android settings or player. See `../runtime-settings-verification.json`. The original application
entry was inspected at 22 registers, 813 code units and four try blocks; the
new verifier checks handler boundaries as well as the added prefix. That earlier
run had no settings navigation. The successor adds a settings section at the
original list's remembered-construction boundary and opens a dialog using the
original UI context, so no extra Activity is needed. Hosted run 40923338143
passed the updated original/patched APK comparison, 11 synthetic model/callback
controls and 13 synthetic preference controls. See
`../runtime-settings-navigation-verification.json`. No settings UI has been
installed or accepted on a phone.

`materialize.py --local-flac --runtime-settings` selects the development profile
and copies exactly these three Android Java sources into the extension module.
The incomplete folder-copy components have a separate, uninstalled development
carrier profile in `materialize.py --folder-carrier`; its first hosted package
verification remains pending. It embeds the already-tested ARM64 FLAC decoder
and the folder-copy classes without adding an app action or a journal connection.
Their actual app host still needs to be selected. The settings profile excludes an otherwise unnecessary second
Kotlin standard library. Current selected feeds and installed behavior are
unchanged. No phone install/UI/permission/model action accompanies this source.

Hosted packaging run 40933871582 stopped at input verification: the recorded
library SHA-256 had accidentally acquired two extra characters and was not a
valid checksum. A fresh memory-only download of the original public artifact
matched all 15 member checksums and all 37 source hashes; the library fingerprint
and packaging pins have been corrected in the successor source. That failure did
not reach compilation or app patching and did not affect the earlier decoder tests.
Successor run 40934083058 built the bundle and applied it to the pinned public
APK, but its comparison refused the generated `FlacIntegrity-IA` DEX class.
The next verifier compares every added class and method to the actual compiled
extension DEX, including compiler-generated classes, and still requires all
original program and non-DEX resources to match. That run did not establish a
complete static-preservation result or any Android execution.
Run 40934581669 matched the compiled extension but stopped when an original
non-DEX APK entry differed after resource patching. The next run records all
changed public APK entry names/hashes and compares the Android manifest's
compiled XML tree; the byte-preservation check has not been weakened.

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
allocation quota, encrypted journal or cache-removal implementation. Its
`fromRecovery` source can restore all pending charges and consumed IDs from a
complete owner-supplied snapshot, even above a reduced limit. The host must
actually read and bind the durable snapshot before accepting another batch,
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

Successor run40907229999 at2b73f584d76499cab85d22669cfc2294b38278b4
passed57 budget controls, including synthetic recovery of pending and terminal
IDs, and the other copy/cache/stop controls. Full artifact and source hashes
are in ../budget-recovery-verification.json. The host still must bind a genuine
complete journal snapshot before admitting work; no device restart was tested.

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
