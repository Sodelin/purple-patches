# Preserving a completed result larger than the input

Source review of alpha.16 found that the companion accepts up to 8 KiB of
selected translation input, but can return up to 64 KiB of Whisper output and
slightly less Codex output. `BridgeClient.execute` used the smaller input bound
for the returned text. A valid result larger than 8 KiB could therefore be
rejected before a preview appeared, even though the job had completed. This is
a source-level finding; no phone request or lost user result was observed.

The alpha.17 source separates the input and result limits. It retains the
existing 8 KiB input limit, accepts a nonempty result up to the companion's
64 KiB output ceiling, and applies that result limit to an explicit insertion
request. It leaves the possible-start warning armed if result parsing or
validation fails. The job response reader has a separate cap derived from the
maximum completed-result bytes, worst-case six-byte JSON escaping of one byte,
and the fixed companion job envelope. Other response types retain their earlier
bound. The source is a candidate until the hosted checks and APK inspection are
recorded; it has not been executed on Android.

`SessionPolicyCheck.java` contains public synthetic controls for multibyte
UTF-8 limits, a result larger than the input limit, a maximum-size escaped job
response and over-limit refusals. These call the actual result validation and
response-reading helpers with controlled JSON stubs and memory streams; they do
not open a socket, test the Android JSON parser or exercise an actual editor.
The APK verifier checks references, not control flow or phone behavior.

This change does not provide unrestricted long dictation, durable result
recovery or safe cross-process atomic insertion. The companion still has a
64 KiB result ceiling, capture has a 60-second ceiling, session changes can
lose a preview, and active composing text needs an accepted insertion design.
The bearer, cancellation/recovery, model and device-acceptance gates remain
open. A failed or uncertain insertion retains the preview only while that
session and dialog remain available.
