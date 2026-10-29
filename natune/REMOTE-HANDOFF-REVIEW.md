# Remote handoff of existing downloads

The installed NaTune target cannot be replaced by a differently signed patched
APK while assuming its private download cache will survive. A possible handoff
is the existing app's single-song **Export** into an Android document provider
backed by a computer or drive. This route is separate from the planned Move
patch and must preserve the original until the destination is verified.

At the pinned NaTune revision, single-song export writes cached span files to
the selected document stream without bulk export's complete tagging temporary
file. It leaves the original cached copy. The source does not prove all spans
cover the complete track, prove a remote provider has completed its write, or
automatically remove the source. Bulk export creates its own temporary tagged
file and also leaves the original. See the pinned [single-song export](https://github.com/NatuneGroup/ArchiveTune/blob/b7e4dd444fea202a5c32e96f7b1199a74fe3ae70/app/src/main/kotlin/moe/rukamori/archivetune/ui/menu/SongMenu.kt#L1462)
and [bulk export](https://github.com/NatuneGroup/ArchiveTune/blob/b7e4dd444fea202a5c32e96f7b1199a74fe3ae70/app/src/main/kotlin/moe/rukamori/archivetune/ui/screens/settings/ExportDownloadedSongsScreen.kt).

## Candidates reviewed, not approved for automatic removal

| Provider source | Finding for this use |
| --- | --- |
| [SFTP-SAF proxy](https://github.com/AlainKnaff/Android-SFTP-Documents-Provider/blob/2aafdad03e0bf377dc28f9c4a83c079f886b719e/app/src/main/java/com/island/androidsftpdocumentsprovider/provider/Proxy.kt#L135) | A write exception is caught, yet the method advances its offset and returns the requested byte count. Flush and close exceptions are also caught. An app-side success cannot establish a complete remote copy. |
| [Artmux document provider](https://github.com/guidov/artmux/blob/22957cdf8f209a5796230770526fead9594295d6/app/src/main/java/app/artmux/android/data/fileprovider/ArtmuxDocumentsProvider.kt#L131) | Writes are piped to a background SFTP worker. Creation uses the requested path without a collision check and sends an empty file to it. Its pinned [JSch 0.2.23](https://github.com/mwiede/jsch/blob/161569897ce40b035a7e6242da90a187ef9b6dda/src/main/java/com/jcraft/jsch/ChannelSftp.java#L463) defaults this `put` overload to overwrite, so an existing matching destination is at risk. |
| [RSAF](https://github.com/chenxiaolong/RSAF/blob/c5a0a3461b0206bb97da7c14df6d331065f3be7a/README.md#limitations) | The provider defaults to write caching, may buffer files on disk, and documents asynchronous completion after the client closes. This does not establish either low peak phone usage or a completed remote upload. |

These are static reviews of specific upstream revisions, not claims about any
installed version. None is selected as a removal authority. The full source
pins and hashes are in `remote-handoff-source-review.json`.

## Requirements before using a remote handoff

The destination must create an exclusive new object or a uniquely owned staging
object, report write failure, and provide a separately verifiable terminal
result. Verify full remote length and content against the selected complete
original, not just a toast, a client stream close, a filename or a file's
existence. A crash, lost connection, unclear source coverage or pending upload
keeps the original and its recovery state. Bound all phone-side staging and
concurrent work; do not assume a network provider streams without a local copy.

The existing app's source/export, selection identity, provider behavior,
remote receiver and original-cache deletion must be integrated and tested as
one workflow. The reviewed providers do not by themselves supply all of those
properties. No provider was installed, configured, exercised or used for media
in this review.
