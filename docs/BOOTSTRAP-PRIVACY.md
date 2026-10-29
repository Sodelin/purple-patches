# Gboard bootstrap: bearer disclosure through command output

**Activation blocker, open.** The current bootstrap sends an authentication
bearer in the Termux command's stdout. On the reviewed Termux implementation,
that output can be logged when verbose logging is enabled. The candidate has
not been installed, and no log, bearer, installed Termux logging level or actual
leak was examined. This is a conditional source finding, not evidence that a
credential was disclosed on a device.

## Source evidence

Purple Patches commit `275d12e05151c90a0df097d49aa4e3255a7ac5f2`
returns a bearer from `Record::bootstrap()` and serializes the result to stdout
in `companion/src/lib.rs` (SHA-256
`01c94b9939ec65148683a221ea0fa0bd55b7f9d4a03483a0a4fafb3ce9bad068`).
At that commit `PurpleAiRuntime.java` (SHA-256
`176e07cde6fc458693e164f254223fc07a64a0d3aca46869067f8d9cacc0ab7f`)
launches the command with a one-shot `PendingIntent`, supplies no custom
background log level and reads `stdout` from the result. Alpha.6 retains this
same bearer-over-stdout protocol. A nonexported receiver and in-memory keyboard
state do not prevent logging before delivery.

The following independently checked Termux source is pinned to commit
`8629e632fcb95da272221be327db653fb24befe9`:

* [RunCommandService.java](https://github.com/termux/termux-app/blob/8629e632fcb95da272221be327db653fb24befe9/app/src/main/java/com/termux/app/RunCommandService.java)
  reads the custom background level as a string-converted integer and also logs
  the incoming intent at verbose level. The present intent does not put the
  bearer in its arguments or extras; future designs must retain that boundary.
* [Logger.java](https://github.com/termux/termux-app/blob/8629e632fcb95da272221be327db653fb24befe9/termux-shared/src/main/java/com/termux/shared/logger/Logger.java)
  uses `0` for off and enables custom-level output logging for an absent value
  when the current level is verbose.
* [AppShell.java](https://github.com/termux/termux-app/blob/8629e632fcb95da272221be327db653fb24befe9/termux-shared/src/main/java/com/termux/shared/shell/command/runner/app/AppShell.java)
  passes that level to stdout's [StreamGobbler.java](https://github.com/termux/termux-app/blob/8629e632fcb95da272221be327db653fb24befe9/termux-shared/src/main/java/com/termux/shared/shell/StreamGobbler.java),
  which can log each output line. This is an additional path beyond the final
  result callback.
* [TermuxPluginUtils.java](https://github.com/termux/termux-app/blob/8629e632fcb95da272221be327db653fb24befe9/termux-shared/src/main/java/com/termux/shared/termux/plugins/TermuxPluginUtils.java)
  passes the computed output-logging flag to [ResultSender.java](https://github.com/termux/termux-app/blob/8629e632fcb95da272221be327db653fb24befe9/termux-shared/src/main/java/com/termux/shared/shell/command/result/ResultSender.java).
  The result sender constructs its log using [ResultData.java](https://github.com/termux/termux-app/blob/8629e632fcb95da272221be327db653fb24befe9/termux-shared/src/main/java/com/termux/shared/shell/command/result/ResultData.java),
  which includes stdout when the flag is true.

The four cited peer-review source bodies independently matched the supplied
SHA-256 values: `ff234b4f9ee2d6818bd7e93ebeb05e4cfd9c86280c7698d1bccf0d7ed2918d14`
(plugin utils), `fb303c74b496aac293cdd0751639cc4c7719f4f42b348f41053f3d6fe837b50f`
(logger), `02f3cccf02e1be27740b213212d58f6348886da86ac48d496e918eeb1a5e2c38`
(result sender), and `71314eff78b2295040d5cb6263b9bbdbc5494eca9aaa6af872d5b077aa0bd52f`
(result data).

## Mitigation and remaining work

Alpha.7 source requests `com.termux.RUN_COMMAND_BACKGROUND_CUSTOM_LOG_LEVEL`
as the **string** `"0"`, the type expected by the reviewed parser. For that
pinned source, static analysis indicates this disables both identified
custom-level output paths. It does not change the phone's global log setting.
The [alpha.7 bundle built off phone](gboard-alpha7-build-verification.json), but
this change has not been exercised on Android; the installed Termux version and
all other success/error/logging paths remain unverified.
Older or different implementations may ignore the extra. It does not remove
the bearer from stdout or prove the callback and its surrounding system safe.

Before activation, select and review a handoff that avoids exposing a plaintext
bearer in stdout, stderr, command arguments or loggable input extras. Establish
how the actual app and companion authenticate each other, bind the response to
the right request and process, handle replay, expiry, failure and restart, and
keep sensitive intermediate values out of persistent or logging sinks. Compare
candidate approaches against the selected Android/Termux versions and the
existing privacy contract. A one-shot callback, setting a log flag, or a
synthetic encryption example alone is not acceptance. Verify the actual chosen
implementation with suitable public fixtures and authorized device evidence
before any installation, keyboard activation or model use. Preserve all
existing device, model and privacy holds in the meantime.

## Caller authority is a separate boundary

The pinned upstream [Termux manifest](https://github.com/termux/termux-app/blob/8629e632fcb95da272221be327db653fb24befe9/app/src/main/AndroidManifest.xml)
declares `RUN_COMMAND` a dangerous permission and protects its exported command
service with that permission. The reviewed service also checks its existing
allow-external-apps policy. The current fixed wrapper accepts `bootstrap` and
the companion returns the existing service bearer. Consequently, another app
already granted that broad permission, with external commands enabled, may be
able to invoke the wrapper and request its own result. This is a source-level
consequence requiring actual selected-version and caller-authority review; it
is not a claim about any installed app's permissions or an observed exploit.
Encrypting a response to a caller-supplied public key could protect it from
stdout logging but would not, by itself, establish that the caller is the
intended keyboard. The scope and implications of the broad Termux command
permission must be explicit in the design and user-facing consent.

For prospective compatibility analysis only, Android documents `ECDH` key
agreement from API 11 and `XDH` from API 33 in its [KeyAgreement reference](https://developer.android.com/reference/javax/crypto/KeyAgreement).
That API list does not establish the supported Gboard target range, provider
behavior, protocol security or a chosen implementation. No key was generated
and no encrypted transport was implemented by this source review.
