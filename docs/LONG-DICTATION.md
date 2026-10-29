# Long dictation: current limits and integration requirements

Through alpha.7, `CaptureSession` stops collecting after 60 seconds or the
equivalent PCM byte allocation, then proceeds with the captured audio. Its
recording dialog says to tap Stop when finished and does not indicate that
automatic limit. A user speaking beyond that point could reasonably expect audio
that the candidate did not collect. This is source behavior, not a claim about
an installed keyboard.

Alpha.8 keeps the existing 60-second bound but states it while recording and,
when the capture controller reaches the time or byte limit, identifies the
result as covering only the captured audio. Its off-phone build and synthetic
checks passed, including manual stop, a delayed read and the byte limit; see
[the build evidence](gboard-alpha8-build-verification.json). This is a
transparency improvement, not long-form support. The [hosted APK inspection](gboard-alpha8-apk-verification.json)
also found the limit state and notice references in DEX; this is static linkage,
not proof of Android behavior. Actual Android validation remains open.

Simply increasing one number does not provide complete long-form support. The
companion currently validates at most 90 seconds per WAV; its HTTP request has a
10 MiB ceiling; engine supervision has a 120-second timeout; the engine result
has a 64 KiB limit; and the keyboard currently accepts at most 8 KiB of result
text through alpha.16. Alpha.17 source separately accepts a completed result up
to the companion's 64 KiB ceiling; see [result preview](RESULT-PREVIEW-BOUNDARY.md).
Audio, result and IPC overhead also affect memory. The pinned upstream
[whisper.cpp CLI source](https://github.com/ggml-org/whisper.cpp/blob/2eeeba56e9edd762b4b38467bab96c2517163158/examples/cli/cli.cpp)
passes a decoded sample vector to its full inference call, but that source
fact does not establish acceptable memory, latency or performance on the phone.

The current keyboard clears its in-memory WAV in a `finally` block even if the
engine request fails or times out. It therefore cannot offer a retry of that
recording. A lost start response can also leave the original job's outcome
unknown; automatically submitting the same or another request is not a safe
recovery protocol. No actual failure or lost recording has been observed here.

For the desired user experience, make recording boundaries and any partial
result explicit, retain the complete usable transcript for review, and support
longer speech without silent truncation or automatic duplicate execution.
Choose the input, work and result limits from the actual device/engine and
memory/privacy requirements, with user control where appropriate. A chunked or
streamed design also needs ordering, missed/overlapping boundary handling,
cancellation, no-replay identity and failure recovery. It must not persist
private audio or text outside the existing approved encrypted or memory-only
contract. The separate [uncertain job-start](JOB-CANCELLATION.md) issue must be
resolved for any multi-part work.

Until a real long-form design and authorized runtime tests exist, do not
advertise unrestricted dictation or silently substitute another Whisper model.
The user's full large-v3 selection, model/device and privacy holds remain.
