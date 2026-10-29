# Cancellation while verifying the Whisper model

Source review found that the selected full model's complete SHA-256 check can
read about 3.1 GB before the engine starts. Previously the worker did not check
its cancellation flag during that read, so stopping the bridge or cancelling a
known job could wait for the entire hash even though inference had not started.
This was a source-level finding; no real model or phone cancellation was run.

The `0.2.2-dev.4` source adds a cancellable streaming hash for the worker's
model and final engine integrity checks. It checks the existing owned-job stop
flag before and after each read and refuses to start inference when cancelled.
The ordinary non-cancellable file hash remains available for existing callers.
Synthetic controls cover cancellation before the first read and during a read,
plus a known SHA-256 digest. The tests do not substitute a different model.

This is cooperative cancellation between reads: a blocked filesystem call or
an operating-system scheduling pause is not a hard real-time guarantee. The
earlier synchronous health/request preflight still has no job cancellation ID.
The existing 120-second engine limit begins at the subprocess stage and is not
an end-to-end hash-and-inference deadline. Device behavior remains untested.

This change does **not** resolve the distinct lost-job-start-response race in
[JOB-CANCELLATION.md](JOB-CANCELLATION.md). The client may still lack the ID of
an accepted job. No new cancellation protocol, model activation or held-state
release is claimed.
