# Gboard alpha.18 build artifact custody

Both alpha.18 build ZIPs from runs `40957051732` and `40958024273`, hosted
source `a5c0cb15edf7d1d18592c079c2c16e4d2d66daeb`, were copied to the existing
unshared private Drive folder. Each complete ZIP and its extracted main MPP
passed independent hash readback in memory. Private file identities and proofs
are retained in encrypted receipts using the existing identity. No large phone
file was staged, no original was removed, and physical disk restoration has
not been verified.

GitHub reported that the first and repeat ZIPs expire on 2026-11-05 at 21:23:30
and 21:30:43 UTC respectively. The separately patched unsigned APK from run
`40957800280` has not been archived. The earlier dev.6 companion upload outcome remains uncertain and
was not retried. These artifacts are not a public feed promotion or a keyboard
installation recommendation. See the [build](gboard-alpha18-build-verification.json),
[repeatability comparison](gboard-alpha18-reproducibility.json),
[APK inspection](gboard-alpha18-apk-verification.json), and
[remaining acceptance gates](ACCEPTANCE.md).
