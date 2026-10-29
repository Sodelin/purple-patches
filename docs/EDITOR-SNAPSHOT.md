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
acceptance. The exact new bundle, supported APK patch, selection behavior and
rollback must still be verified separately before release or activation.
