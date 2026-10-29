# Separate local-tools and microphone permission requests

Earlier source requests both `com.termux.permission.RUN_COMMAND` and
`android.permission.RECORD_AUDIO` whenever either is needed. That can ask for
microphone access when the user only wants translation, or ask for the Termux
permission again while requesting the microphone.

Alpha.7 source has two allowlisted request scopes. The explicit Connect action
can request only Termux's command permission; an explicit Whisper action can
request only microphone permission after the local bridge is connected. The
permission activity accepts only those two scopes, rechecks whether the selected
permission is already granted, requests exactly one permission, and closes if
the scope is invalid or the platform rejects the request. The user can invoke
the original action again after deciding the permission prompt. These changes
do not automatically grant permissions or begin a model request.

The Connect explanation now identifies the important scope of Termux's
permission: it lets this keyboard run commands as Termux. This is broader than
access to one translation function and is separate from the open
[bootstrap bearer and caller-authorization](BOOTSTRAP-PRIVACY.md) issue.

The pure-policy checks include both one-permission mappings and unknown scopes.
A JVM fixture compiles the actual permission activity against small fake Android
classes and exercises the two requests, refusal, invalid input, already-granted
and request-error cases. Those checks passed in the [alpha.7 hosted build](gboard-alpha7-build-verification.json).
The [exact-APK check](gboard-alpha7-apk-verification.json) also found the relevant
permission-scope references in the patched DEX. Neither check demonstrates
Android's actual permission dialog, lifecycle,
rationale, denial/revocation or behavior of stock Gboard. Those remain device
acceptance gates. [Android's runtime permission guidance](https://developer.android.com/training/permissions/requesting)
recommends requesting permissions in the context of the action needing them.
