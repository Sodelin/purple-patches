# Review of public chess and Discord patch candidates

Nolan described a local Stockfish chess patch and a Discord Bluetooth-audio patch. Their installed patch sources and versions have not been established. The following public candidates match those descriptions; this review does not identify either one as his installed patch.

## Stockfish candidate

At [Noam commit `33c81c3`](https://github.com/ImNoammm/Noams-Morphe-Patches/blob/33c81c3a0acdf55bd0240dc62c7e30085642d0e3/patches/src/main/kotlin/app/noam/patches/chesscom/stockfish/StockfishPatch.kt), the patch downloads the official Stockfish 19 Android ARM64 archive during patching, verifies pinned SHA-256 values for both archive and executable, and puts the executable in the patched APK. The source describes an approximately 80 MB download; this review downloaded neither the engine nor an APK.

In the same revision, [`Engine.waitFor`](https://github.com/ImNoammm/Noams-Morphe-Patches/blob/33c81c3a0acdf55bd0240dc62c7e30085642d0e3/extensions/chesscom/src/main/java/app/noam/extension/chesscom/stockfish/Engine.java) checks its 30-second deadline before calling `BufferedReader.readLine()`. If the stream stays open but supplies no line, that blocking read can outlast the deadline. `readSearch` has the same structural issue around its search and stop deadlines. A [Prathxm source revision](https://github.com/PrathxmOp/Prathxm-Patches/blob/011e2aecfed3ec899d009cd1270c39e6a6bb1676/extensions/extension/src/main/java/app/prathxm/chess/extension/stockfish/StockfishProcess.java) also places blocking reads inside deadline loops; the fixture here exercises only Noam's method. Another caller or watchdog could affect an actual app. No phone incident or installed-version behavior has been established.

The `stockfish-timeout.yml` workflow fetches the exact Noam source above, verifies its SHA-256 (`3fcc5011c739a694f4f9e5df7e1dc59de3832801220ec7ee4c1b8245c33c6f1a`), compiles it with minimal synthetic Android stubs, and invokes the real private `waitFor` method with an open, silent synthetic reader. A successful reproduction reports that the call remains blocked for more than 30 seconds, then releases the reader and confirms the method returns on EOF. This is a source-level reproduction, not a fix, a Stockfish execution, or Android acceptance. Hosted result: pending.

## Discord candidate

The public [Venus source at commit `ade5ae6`](https://github.com/VenusIsJaded/Venus-Patches/blob/ade5ae67b394af7f7e836e671eb3a66e6a6fa9ae/extensions/audio/src/main/kotlin/app/venus/audio/Handsfree.kt) describes a switch that, when enabled and an A2DP output is present, suppresses Bluetooth hands-free routing and intends to use the phone microphone. Its source passes other choices through and rereads the setting at call start. This is an explanation of that source, not a claim that Nolan has this version or that his phone, microphone, reconnect behavior, or call quality has been tested.

Before choosing or changing a patch, identify the actual configured source and installed version. Do not inspect credentials or change the phone merely to complete this review.
