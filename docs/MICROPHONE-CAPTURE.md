# Bounded microphone capture

The earlier candidate limited the number of PCM bytes to 60 seconds at the
expected sample rate, but used a blocking read and had no elapsed-time limit.
An unresponsive recorder or repeated empty reads could keep the capture action
open even though the byte limit had not been reached. The old stop path also
called the Android recorder from the UI thread.

The alpha.4 source uses a monotonic elapsed-time limit as well as the original
byte ceiling. Its Android adapter requests nonblocking reads; empty reads pause
briefly instead of spinning. A stop request signals the recording worker, which
owns recorder release. Observed cancellation rejects the captured output, and
the caller rechecks cancellation before submitting it. PCM staging is
cleared in a finally block, and a session cannot be started a second time.
Stopping before the recorder opens or before its start call returns prevents a
read from being admitted. A read that returns after the deadline is discarded.

The elapsed-time check runs on the capture worker. Android scheduling and a
misbehaving or stalled platform call can still delay actual microphone release;
this design does not claim hard real-time enforcement or prove hardware behavior.
The returned WAV still exists in memory until the caller finishes and clears it;
this is not a claim about Android's internal audio buffers or all process writers.
The stop/start timing, release, interruptions, permission flow and audio quality
still require an authorized device acceptance test.

The reusable controller is `CaptureSession.java`; the actual Android adapter is
`WhisperCapture.java`. `CaptureSessionCheck.java` uses synthetic input and a
fake monotonic clock, without opening a microphone or running a model. The
hosted build and checks passed; their exact source and artifact hashes are in
[gboard-alpha4-build-verification.json](gboard-alpha4-build-verification.json).
This is not a phone microphone test.

Android references:

- [AudioRecord nonblocking reads](https://developer.android.com/reference/android/media/AudioRecord#read(byte%5B%5D,%20int,%20int,%20int))
- [SystemClock elapsedRealtime](https://developer.android.com/reference/android/os/SystemClock#elapsedRealtime())
