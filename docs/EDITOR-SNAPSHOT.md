# Gboard editor snapshot safety

The published `0.1.0-alpha.1` candidate converts a null result from the selected,
before-cursor or after-cursor editor query into an empty string. Android's
[InputConnection documentation](https://developer.android.com/reference/android/view/inputmethod/InputConnection)
also permits null when a connection fails or times out. Treating that as known
empty context can make two incomplete snapshots appear equal.

The `0.1.0-alpha.2` source uses a finite `EditorReader` to capture immutable
selection and surrounding strings, rejects missing context, negative or
overflowed positions, inconsistent selection length, oversized data and an
observed selection/offset change during the reads. A null selected-text value
is accepted only when the separately observed selection is collapsed; Android's
default [BaseInputConnection](https://developer.android.com/reference/android/view/inputmethod/BaseInputConnection)
returns null when nothing is selected. This cannot distinguish every timeout
from an empty selection; the surrounding reads and second position observation
must also succeed.

In `0.1.0-alpha.3`, after capturing the second snapshot, the implementation
also checks that both that snapshot and the current input connection still
refer to the original connection, and rechecks the current editor's sensitive
field flags. These checks can refuse an observed editor switch during capture;
they do not make editor reads and insertion atomic.

Through alpha.11, the session/editor/content checks run before preview and before
explicit insertion. Alpha.12's changed-anchor preview is copy-only, while the
checks still run before insertion. Multiple editor IPC calls and the later `commitText` are
not an atomic compare-and-replace; an editor can change between the last check
and insertion. This source change is not an installed keyboard or device
acceptance. The alpha.3 bundle and supported APK patch passed the hosted checks
recorded in [gboard-alpha3-build-verification.json](gboard-alpha3-build-verification.json)
and [gboard-alpha3-apk-verification.json](gboard-alpha3-apk-verification.json).
Actual editor behavior and rollback remain to be verified before release or
activation.

Alpha.11 source also catches `RuntimeException` around the complete anchor
comparison and refuses it, so an unavailable or throwing editor accessor does
not escape that check. Its [hosted bundle build](gboard-alpha11-build-verification.json)
passed, and [static APK inspection](gboard-alpha11-apk-verification.json) found
the handler and passed a missing-type negative control. Android behavior remains
unverified. The
catch does not make the multi-call observation atomic or identify an active
composing region.

## Active composition and insertion result: open release gate

Android documents that [`InputConnection.commitText`](https://developer.android.com/reference/android/view/inputmethod/InputConnection#commitText(java.lang.CharSequence,%20int))
replaces the editor's current composing text if a composing region exists; only
when there is no composing region does it describe inserting at the cursor and
removing any selection. The current snapshot captures selection positions and
surrounding text but does not establish the composing region. Its unchanged
selection check therefore does not prove that `commitText` will replace the
intended selection rather than another active composition. This is a source and
platform-contract finding, not a demonstrated device incident.

Alpha.8's positive button also dismisses the preview while ignoring the
`commitText` boolean. If the connection is no longer valid and returns false,
the user can lose the visible preview even though the input field was not
updated. Android documents false for an invalid connection. A thrown or delayed
call may leave a different outcome uncertain; automatic insertion retry is not
safe without an actual outcome and anchor check.

Alpha.9 source keeps the selectable preview visible after an insertion attempt,
including when the editor rejects the request or an exception makes the outcome
uncertain. It disables that preview's Insert button before attempting the call,
and a one-shot policy refuses duplicate requests. A changed or unavailable anchor
prevents the request. A `true` return is presented as a request sent, not proof
that the editor ultimately changed; a false return or exception remains unknown.
The user can select and copy the displayed result while the preview stays open.
The [hosted source and bundle build](gboard-alpha9-build-verification.json)
passed, including synthetic one-shot/refusal controls. The separately patched
[APK inspection](gboard-alpha9-apk-verification.json) found the insertion and
preview references; it did not exercise their control flow. This has not been
tested on Android. It does not resolve the
active-composition, editor-session-dismissal, earlier-result-loss or cross-process
atomicity gaps described here.

Through alpha.11, when an operation completes, its queued UI callback returns
without showing the result if the captured editor anchor no longer matches.
Alpha.12 source instead displays a selectable, copy-only preview when the
original request is still current in the same non-sensitive session but its
anchor no longer matches or cannot be verified. Insert is disabled for that
preview. It still checks the anchor before an insertion offered in the unchanged
case. The [hosted bundle and pure policy controls](gboard-alpha12-build-verification.json)
passed, and [static APK inspection](gboard-alpha12-apk-verification.json) found
the preview references and passed a missing-class negative control. Static
references do not prove Android control flow; device behavior remains untested.

Ending the input session still cancels the operation and dismisses its dialog.
The current implementation does not provide a way to recover a generated
result after that session ends, the process exits, or the preview cannot be
shown. The safety checks are multiple non-atomic calls, and a field may change
after them. These are source limitations, not observed device incidents.

Before activation, select and verify an insertion protocol that handles active
composition deliberately, keeps a recoverable preview when insertion fails or
is uncertain or the editor changes, and does not claim cross-process atomic
compare-and-replace.
Calling `finishComposingText` itself changes editor state and needs its own
explicit UX and regression analysis; it is not assumed to be a free fix.
Authorized tests must cover active composition, selection changes, input
connection loss, refusal, exception, delayed response, and duplicate gestures in
real representative editors. Preserve original text and all privacy holds.

The documented `InputConnection.setSelection` does not clear a composing region,
so reselecting the same range alone does not resolve that risk. Android also
documents that IMEs currently receive null from `InputConnection.takeSnapshot`,
despite the API describing an atomic text snapshot for system use. A composing
span helper operates on a supplied `Spannable`; the current connection contract
does not establish that every editor returns one with complete composing state.
See the [InputConnection reference](https://developer.android.com/reference/android/view/inputmethod/InputConnection)
and [BaseInputConnection reference](https://developer.android.com/reference/android/view/inputmethod/BaseInputConnection).
These are platform/source observations, not a claim that every editor behaves
identically or that a safe user-directed alternative is impossible.
