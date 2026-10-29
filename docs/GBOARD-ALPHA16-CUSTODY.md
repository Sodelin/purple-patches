# Gboard alpha.16 build artifact custody

Both alpha.16 build ZIPs from runs `40951874920` and `40952582514`, source
`907a886adb8b2ff7bc8c3d8df86aef92c7134baa`, were copied to the existing
unshared private Drive folder. Each complete ZIP and its extracted main MPP
passed independent hash readback in memory. Private file identities and proofs
are retained in encrypted receipts using the existing identity. No large phone
file was staged, no original was removed, and physical disk restoration has not
been verified.

GitHub reported that the first and second build ZIPs expire on 2026-11-05 at
20:48:19 and 20:52:56 UTC respectively, and the separately patched unsigned APK
expires at 20:54:30 UTC. The APK
was not archived. The earlier dev.6 companion upload outcome remains uncertain
and was not retried. These artifacts are not a public feed promotion or a
keyboard installation recommendation. See the [comparison](gboard-alpha16-reproducibility.json),
[APK inspection](gboard-alpha16-apk-verification.json), and
[remaining acceptance gates](ACCEPTANCE.md).
