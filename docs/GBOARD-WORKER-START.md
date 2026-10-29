# Optional action worker startup

Before alpha.22, the translation-catalog lookup caught runtime exceptions from
starting its worker but not native-thread exhaustion. The translation and
dictation action started a worker without either guard. If thread creation or
start failed at that point, the optional action could crash the keyboard or
leave its progress/busy state reserved. This is a source-level failure mode;
there is no observed incident or device test.

Alpha.23 handles a runtime exception or `OutOfMemoryError` raised while starting
either worker. Its failure path marks the action stopped, clears the matching
capture and progress dialog when possible, releases the busy flag, and reports
the failure when the UI is available. The worker waits for the start call to
return successfully before dispatching its action; if startup throws after the
thread was scheduled, it exits without running the action. An unexpected VM
error is not suppressed. Severe memory exhaustion or a failure before this
narrow start guard may still affect the
keyboard. Cancellation or shutdown after a worker starts is a separate contract.

The [reviewed AOSP ART source](https://android.googlesource.com/platform/art/+/refs/heads/main/runtime/thread.cc)
returns after a successful `pthread_create` and reports an out-of-memory
failure when its JNI environment or thread creation fails. That source explains
the particular guarded path; it is not a verification of the installed Android
version or every possible runtime failure.

Synthetic controls inject start refusals and thread exhaustion both before and
after a worker is scheduled, and also start one ordinary test thread. They
exercise the worker-start helper, not Android's actual UI cleanup, the installed
keyboard, microphone, local companion or model. Two hosted builds produced
identical artifact members; the exact supported public APK was patched and
statically inspected. See the [build](gboard-alpha23-build-verification.json)
and [APK](gboard-alpha23-apk-verification.json) records. Device regression is
still pending.
