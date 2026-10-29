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

The original session/editor/content checks still run before preview and before
explicit insertion. Multiple editor IPC calls and the later `commitText` are
not an atomic compare-and-replace; an editor can change between the last check
and insertion. This source change is not an installed keyboard or device
acceptance. The alpha.3 bundle and supported APK patch passed the hosted checks
recorded in [gboard-alpha3-build-verification.json](gboard-alpha3-build-verification.json)
and [gboard-alpha3-apk-verification.json](gboard-alpha3-apk-verification.json).
Actual editor behavior and rollback remain to be verified before release or
activation.

## Active composition and insertion result: open release gate

Android documents that [`InputConnection.commitText`](https://developer.android.com/reference/android/view/inputmethod/InputConnection#commitText(java.lang.CharSequence,%20int))
replaces the editor's current composing text if a composing region exists; only
when there is no composing region does it describe inserting at the cursor and
removing any selection. The current snapshot captures selection positions and
surrounding text but does not establish the composing region. Its unchanged
selection check therefore does not prove that `commitText` will replace the
intended selection rather than another active composition. This is a source and
platform-contract finding, not a demonstrated device incident.

The current positive button also dismisses the preview while ignoring the
`commitText` boolean. If the connection is no longer valid and returns false,
the user can lose the visible preview even though the input field was not
updated. Android documents false for an invalid connection. A thrown or delayed
call may leave a different outcome uncertain; automatic insertion retry is not
safe without an actual outcome and anchor check.

There is an earlier result-loss path as well: when an operation completes, its
queued UI callback returns without showing the result if the captured editor
anchor no longer matches. Ending the input session also cancels the operation
and dismisses its dialog. These checks avoid inserting into a changed editor,
but the current implementation does not provide a way to recover a generated
result in those cases. This is source behavior, not an observed device incident.

Before activation, select and verify an insertion protocol that handles active
composition deliberately, keeps a recoverable preview when insertion fails or
is uncertain or the editor changes, and does not claim cross-process atomic
compare-and-replace.
Calling `finishComposingText` itself changes editor state and needs its own
explicit UX and regression analysis; it is not assumed to be a free fix.
Authorized tests must cover active composition, selection changes, input
connection loss, refusal, exception, delayed response, and duplicate gestures in
real representative editors. Preserve original text and all privacy holds.
