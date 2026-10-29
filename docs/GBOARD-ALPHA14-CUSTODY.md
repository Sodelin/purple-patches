# Gboard reproducibility artifacts and alpha.14 custody

The alpha.13 reproduction ZIP from hosted run `40947061110`, source
`bc81100e7851c7cc0ca345e8189d16916a6bb3b0`, and both alpha.14 build ZIPs from
runs `40948413452` and `40948916651`, source
`81f042630155be28730f6c0f08c9912d6b8c6b33`, were copied into the existing
unshared private Drive folder. Full Drive readback of each ZIP and its
extracted main MPP matched the separately checked hashes in memory. The private
file identities and proofs are held in encrypted receipts using the existing
identity. No large phone file was staged, no original was removed, and physical
disk restoration has not been verified.

The alpha.14 build artifacts were reported to expire on 2026-11-05 at 20:25:02
and 20:28:28 UTC; the separately patched unsigned APK was reported to expire at
20:30:05 UTC. The latter has not been archived. The earlier dev.6 companion upload
outcome remains uncertain and was not retried. These are build artifacts, not a
public feed promotion or a keyboard installation recommendation. See the
[alpha.13 comparison](gboard-alpha13-reproducibility.json),
[alpha.14 repeatability result](gboard-alpha14-reproducibility.json),
[APK inspection](gboard-alpha14-apk-verification.json), and
[remaining acceptance gates](ACCEPTANCE.md).
