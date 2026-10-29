# Gboard alpha.23 build artifact custody

The build ZIP from run `40974389461`, source
`42de89706d8c326ab1074670dc87de689b876f60`, was copied to the existing
owner-only private Drive folder. A complete remote readback matched the ZIP
size and SHA-256 in the [build verification](gboard-alpha23-build-verification.json).
The independent readback used an in-memory SHA-256 implementation checked
against the standard empty and `abc` vectors. The main MPP was checked inside
the matching hosted ZIP, not independently extracted from the Drive readback.
The Drive file and folder identities are recorded in the existing encrypted
private record system. No physical restore or installation was performed.

This is a development candidate, not a feed promotion or recommendation to
install. The [APK inspection](gboard-alpha23-apk-verification.json) and
[acceptance record](ACCEPTANCE.md) identify the remaining gates.
