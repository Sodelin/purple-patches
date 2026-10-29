# Normal1DM handoff and shared-folder download finding

Read-only29October2026. Exact supported target is normal1DM package
`idm.internet.download.manager`, not the Plus package. Installed metadata was
normal1DM20.0/code30257 and NatuneN202610191717/code6900. The installed Natune
base APK's complete SHA256 still matches target.json's pinned public original.
No application preferences/tokens/private music were inspected or changed.

## What the existing Natune control does

Pinned Natune sourceb7e4dd444fea202a5c32e96f7b1199a74fe3ae70:
`ui/menu/YouTubeSongMenu.kt:694–736` and `ui/menu/SongMenu.kt:1104–1145` add
**Open with downloader** when ExternalDownloaderEnabledKey is true. The action
creates ACTION_VIEW with the configured package and a music.youtube.com/watch
page built from the song ID. It does not pass a resolved FLAC/file URL, required
source headers, destination, filename or Natune's already-downloaded bytes.
The ordinary Download action is a separate internal download/cache path.

Correct value for the Downloader App Package field is:

    idm.internet.download.manager

This is sufficient only for selecting the installed receiving app. It is NOT
a working solution for the desired direct FLAC/shared-folder workflow. The
normal1DM developer explicitly documents that YouTube music/video downloading
is unsupported; choosing Plus does not establish compatibility.

Official1DM integration supports ACTION_VIEW to its Downloader component,
`idm.internet.download.manager.Downloader`, with a direct supported download URL
and optional filename/HTTP-header extras. Android read-only resolution confirmed
the installed component exists. Generic URL resolution was not a unique direct
Downloader selection. No Activity was opened or URL/network request sent.
Natune's current action does not use that explicit component/file contract.

## Storage and permission implications

Natune's SongMenu.exportDownloadedSongToUri reads existing downloadCache spans
and writes them to the selected destination. The source cache is retained; this
export is a copy, so it requires additional space. Repeating export does not
change its source into a direct-to-shared-folder downloader.

For supported direct-file downloads in1DM, its documented Download Location
setting chooses the destination. A suggested user-owned folder is Music/Natune
(or Music/Natune-Test for a test). If Android's folder picker requests a grant,
select the intended folder and approve **Use this folder**. Exact current grant
state/destination was not inspected. No rooting, app-private storage access,
new connection or broad permission change is needed for the documented intent
handoff; do not grant unrelated access as a workaround.

The actual no-duplicate fix needs a supported source/Natune writer that streams
permitted audio directly to the selected shared document/media destination and
retains coherent completion/codec/matching/index evidence. It must not first
materialize the same whole file in Natune's download cache. This source/receiver
is not supplied by the current external-downloader setting. A lossy source is
not made FLAC by either1DM or a .flac filename. No YouTube URL-extraction bypass
is proposed by this finding.

## Proposed test only, not executed

After choosing a supported direct-file source and the destination, one small
permitted non-YouTube FLAC (for example <=10MiB), one job/part, could be saved to
Music/Natune-Test. Verify the completed file's bytes/hash/actual format and its
shared-folder placement. Compare Natune's own trustworthy storage/download
counters before/after to establish no second full cached copy; a global free-
space delta alone is insufficient with concurrent writers. If such counters
are unavailable, report the no-duplicate check unproven rather than inspect
private app data. This would test1DM's supported destination path separately;
it would not prove the existing Natune watch-page handoff works. No bulk transfer,
cleanup, install, permission grant, app launch or test download was performed.

Primary sources:

- https://github.com/NatuneGroup/ArchiveTune/blob/b7e4dd444fea202a5c32e96f7b1199a74fe3ae70/app/src/main/kotlin/moe/rukamori/archivetune/ui/menu/YouTubeSongMenu.kt
- https://github.com/NatuneGroup/ArchiveTune/blob/b7e4dd444fea202a5c32e96f7b1199a74fe3ae70/app/src/main/kotlin/moe/rukamori/archivetune/ui/menu/SongMenu.kt
- https://www.apps2sd.info/idmp/faq
- https://play.google.com/store/apps/details?id=idm.internet.download.manager
