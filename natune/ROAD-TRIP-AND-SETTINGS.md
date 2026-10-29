# Road Trip mode and Purple Patches settings

Nolan29October2026 requested these as part of the maintained Natune patch. This
is the current implementation contract; a settings/button mock or URL-only
prefetch does not satisfy it. The FLAC upgrade feature is in FLAC-UPGRADES.md.

## Road Trip mode

- Provide an explicit manual toggle and up to15 upcoming queue songs ready
  offline, adjustable downward. Treat the user's15 as a count of upcoming songs,
  not a new fixed storage quota. Show how many complete playable tracks are ready.
- Replenish after track transitions, queue edits/reorders, skips and radio/playlist
  pagination. Use actual playback order, including repeat/shuffle and duplicate
  occurrences. Buffer resolved future radio/playlist items; don't invent future
  radio results while the network is unavailable.
- Prioritize current/next playback and finish near-term whole tracks before
  distant ones. URL resolution or a small partial span is not offline readiness.
  Account for actual complete cached coverage, container/codec/matching evidence,
  original cancellation and authenticated/expiring source behavior.
- Keep the queue's original chosen recording/source semantics and selected FLAC
  policy. A YouTube-origin playlist/radio is a discovery/queue source; it does not
  prove the audio rendition is FLAC. Report an unavailable FLAC match explicitly;
  do not transcode or quietly choose lossy audio while strict FLAC is enabled.
- Fill the buffer while the connection is good so it exists before rough patches.
  Cellular permission is a user setting, independent of a5G/UWB label. Support
  user-allowed ordinary cellular and carrier-reported unrestricted mobile data.
  Neither a cellular transport flag nor an assumed plan should force low quality.
- Preserve permanent/user downloads and currently playing files. Own temporary
  road-trip entries separately; only evict eligible owned temporary generations,
  never a permanent original or a file held by another operation. Track count,
  bytes/space and queue generations independently. Don't apply the engineering
  build reserve as a new quota on the user's personal music downloads.
- Manual mode works without GPS/speedometer permissions. Optional Android Auto
  triggering requires the actual app/service connection contract and explicit
  setting; a radio label or Bluetooth connection alone is not that signal. No
  continuous location/speed acquisition is needed for the manual feature.
- Stop replenishment on disable/cancellation, preserve playing/explicitly saved
  media and reconcile in-flight state without a second uncontrolled downloader.
  Integrate with the existing owned downloader/cache/player rather than starting
  another background service or persistent controller.

## Proven existing app surfaces

Pinned Natune sourceb7e4dd444fea202a5c32e96f7b1199a74fe3ae70:
MusicService.onTimelineChanged currently caches queue metadata only.
onMediaItemTransition invokes prefetchNextMediaItemStream. That implementation
selects ONE next item, caches source/YouTube stream URLs, and cancels/reuses one
resolution coroutine. It explicitly returns after lossless URL resolution.
It does not download15whole songs. Replacing its low-data result alone therefore
cannot implement Road Trip mode. Reuse actual queue events and resolver/cache
ownership while adding complete-file staging/readiness and bounded queue work.
Exact source bytes are pinned in upgrade-source-review.json.

## YouTube Music investigation, not assumed account access

The installed YouTube Music9.43.51 base APK contains public Smart Downloads
resource labels. Scope was selected package version and bundled resources only;
no account/preferences/download cache/clipboard/screenshot/UI focus was accessed.
Encrypted local receipt: record-16384-1793278732707168777.age. This observation
establishes the installed controls' presence, not their active setting or a route
to Nolan's personal list.

Google's current Help describes Smart Downloads based on listening history and
its Downloads & storage controls, plus a choice of Wi-Fi/unrestricted mobile
data on supported carriers/devices. Its documented public Data API resource
catalog does not list a Smart Downloads resource. That establishes a documented
API gap, not universal proof that no version-specific supported app interface
exists. Signing into Natune with the same account is not by itself a proven
Smart Downloads list binding. Investigate exact current app/authorized account
interfaces before selecting a sync hook; don't copy another app's credentials or
private offline bytes, and don't label recommendations as its actual download list.

References, reviewed29October2026:

- https://support.google.com/youtubemusic/answer/6313535?co=GENIE.Platform%3DAndroid&hl=en
- https://developers.google.com/youtube/data/reference/rest
- https://github.com/NatuneGroup/ArchiveTune/blob/b7e4dd444fea202a5c32e96f7b1199a74fe3ae70/app/src/main/kotlin/moe/rukamori/archivetune/playback/MusicService.kt

## Purple Patches settings page

Provide a real Natune settings entry with patch-specific runtime controls:

| Section | Controls/status |
|---|---|
| Playback/download quality | Mobile full-quality policy; strict FLAC; explicit no-FLAC result. |
| Sources | Reviewed provider enable/order/availability and applicable original account binding. Never expose credentials in status/exports. |
| Existing downloads | Find FLAC upgrades; selection/results; stage/verify/apply/cancel status. |
| Road Trip | Enable; upcoming-track count up to15; permitted networks; optional Android Auto trigger; ready/downloading/unavailable counts and owned buffer usage. |
| Troubleshooting | Patch/component/target versions, which settings are active, safe compact errors and reset of patch settings without deleting user downloads. |

Persist only the selected configuration through the app's appropriate owner;
new operation/recovery records still need the existing privacy contracts. Respect
in-flight policy snapshots and cancellation when changing settings, rather than
silently changing the interpretation of a running upgrade or download.

Current0.1.0 patch selection is default-off AT PATCH TIME; its five result
replacements are not runtime toggles. Current0.2.0 decoder guards likewise have
no selected settings receiver yet. A real extension/runtime settings accessor,
Morphe injection and visible settings entry must replace those unconditional
patched decisions before the runtime-toggle requirement is complete. Disabling
a feature should restore that path's stock behavior; it must not delete media,
clear account state or turn off unrelated features. Package this as maintained
Morphe source with versioned settings/migrations and actual regression checks.

Gboard's separate action/settings/control scope remains unchanged; don't reuse
its keyboard capability as a Natune settings or account delegation.
