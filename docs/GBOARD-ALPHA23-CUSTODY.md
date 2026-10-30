# Gboard alpha.23 build artifact custody

The build ZIP from run `40974389461`, source
`42de89706d8c326ab1074670dc87de689b876f60`, was copied to the existing
owner-only private Drive folder. A complete remote readback matched the ZIP
size and SHA-256 in the [build verification](gboard-alpha23-build-verification.json).
The independent readback used an in-memory SHA-256 implementation checked
against the standard empty and `abc` vectors. The main MPP was later extracted
from the Drive readback in memory and matched the independently pinned bundle
digest. The ZIP's complete member checksums, source provenance and committed
overlay/build inputs also matched.
The Drive file and folder identities are recorded in the existing encrypted
private record system. No physical restore or installation was performed.

This is a development candidate, not a feed promotion or recommendation to
install. The [APK inspection](gboard-alpha23-apk-verification.json) and
[acceptance record](ACCEPTANCE.md) identify the remaining gates.

`tools/recover_gboard_bundle.py` rechecks a caller-supplied archive stream
against a selected build-verification record and its exact committed source.
Its default output is a JSON verification report; `--extract-main` emits the
verified MPP to non-terminal stdout only after all checks pass. The tool does
not fetch from Drive, write an output file, update a feed or install a patch.
Any caller that saves stdout must choose a new destination and verify the saved
bytes; shell redirection can overwrite an existing file before the tool runs.
The selected proof and source commit remain part of the trust boundary.
The tool recovered the exact MPP from the Drive stream in memory. Six synthetic
recovery controls passed locally in normal and optimized Python and in
[hosted run 40978096587](https://github.com/Sodelin/purple-patches/actions/runs/40978096587);
the existing three comparison controls also passed. These controls do not
establish a physical restore or a working keyboard.
