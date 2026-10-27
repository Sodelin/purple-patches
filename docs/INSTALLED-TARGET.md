# Installed input review — 27 October 2026

Read-only observation of the existing authenticated same-phone route identified
stock Gboard `18.5.4.993608717-beta-arm64-v8a`, version code176049710. It is
installed as four APK parts totaling140429366bytes. The base APK SHA256 is
`a80c381bcd0faaf835419355d453f9118a159f0dd7ed232533e8f60631e8ac4f`.
Morphe Manager `2.2.0` is present. No matching Gboard installer was found in
the top-level shared Downloads directory.

All four Gboard parts passed the installed `apksigner verify --print-certs`.
Actual APK signers match the two Google certificate fingerprints pinned by the
upstream target. The separately reported source-stamp certificate is not an APK
signing identity. Do not treat every certificate printed by apksigner as an
additional accepted APK signer.

These facts do not establish patch compatibility. The verified candidate
currently admits `18.5.4.993608717-release-arm64-v8a`; it must continue rejecting
this installed beta until a beta-specific input/binding/patching check passes.
Do not relabel beta as release, force compatibility, discard native-library
splits or alter the user's current keyboard to fit a convenient input.

Private encrypted evidence, retained in the existing shared store:

- `record-8073-1793114695853091050.age`: same-phone boot/UID verification and package metadata.
- `record-12904-1793114833347922039.age`: package shapes and allocation without APK copies.
- `record-14202-1793114878767252803.age`: complete APK hashes and signature verifier output.
- `record-14724-1793114903952113263.age`: bounded installer-input lookup.
- `record-25337-1793115290361714001.age`: successful reproducible read-only audit.

The source helper is `companion/deployment/inspect-installed-gboard.py`. It
reuses the reviewed existing device helper and single record identity, refuses
an absent ADB server or changed helper, verifies only the fixed packages,
streams hashes without copies, and seals observations before persistence.
It does not install, select an IME, grant permissions, pair or create keys.

Next: validate the pinned Morphe Desktop CLI and complete the original split
input route on an off-phone runner. Original installed APKs remain in place.
Phone free space remains below the20GiB reserve; no local APK merge/build or
full-v3 model download is admitted. New-model testing remains held separately.
