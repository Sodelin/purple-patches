# Gboard alpha.17 build artifact custody

Both alpha.17 build ZIPs from runs `40954855554` and `40955602858`, hosted
source `17f325c481f7f84550013924496ff088d99f2ad2`, were copied to the existing
unshared private Drive folder. Each complete ZIP and its extracted main MPP
passed independent hash readback in memory. Private file identities and proofs
are retained in encrypted receipts using the existing identity. No large phone
file was staged, no original was removed, and physical disk restoration has
not been verified.

GitHub reported that the first build ZIP expires on 2026-11-05 at 21:09:03 UTC,
the repeat ZIP at 21:14:04 UTC, and the separately patched unsigned APK at
21:10:30 UTC. The APK has not been archived. The earlier dev.6 companion upload outcome remains uncertain
and was not retried. These artifacts are not a public feed promotion or a
keyboard installation recommendation. See the [build](gboard-alpha17-build-verification.json),
[repeatability comparison](gboard-alpha17-reproducibility.json),
[APK inspection](gboard-alpha17-apk-verification.json), and
[remaining acceptance gates](ACCEPTANCE.md).
