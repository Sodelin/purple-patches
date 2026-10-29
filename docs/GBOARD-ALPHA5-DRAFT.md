# Gboard alpha.5 and companion dev.3 — draft build custody

This draft preserves selected hosted build outputs. It is not a release for
installation, a Morphe feed update, or proof that the keyboard works on a phone.
The existing public feed still selects alpha.1 and warns about its known editor
snapshot issue.

The Gboard bundle is built from source
`58e1d65be69409adff0d3f3e695463920f09ef30` in hosted run `40921539615`.
Its exact APK patch and static inspection are recorded in
[`gboard-alpha5-apk-verification.json`](gboard-alpha5-apk-verification.json).
The matching `0.2.2-dev.3` companion is built from source
`8f354708df96ab07b0a6d3ef5c90b3314dff55c1` in hosted run `40921999924`;
its source and artifact hashes are in
[`companion-022-dev3-verification.json`](companion-022-dev3-verification.json).
Both source snapshots have the same companion code and manifest for this build.

Important remaining gates include the lost-start-response cancellation design,
actual privacy-writer coverage, the selected full large-v3 model and engine,
Morphe Manager import/update, signing and side-by-side device regression and
rollback. No model, keyboard or app was installed or tested on the phone for
this draft. See [`ACCEPTANCE.md`](ACCEPTANCE.md) for the complete open list.

All assets must match their recorded SHA-256 values before any future authorized
use. A draft asset, hosted synthetic test, or static APK inspection is not device
acceptance and does not release existing privacy or installation holds.
