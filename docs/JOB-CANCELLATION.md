# Cancellation when a job-start response is lost

Source review of the current Gboard/companion candidate found an open end-to-end
cancellation gap. This is a source-level failure scenario, not an observed live
model request or a claim that an existing phone job is running.

`Service::start` in `companion/src/jobs.rs` generates the job ID on the server,
records an encrypted intent, starts its worker and returns the ID in the HTTP
response. `BridgeClient.execute` in `gboard/.../BridgeClient.java` learns that ID
only after the POST response is parsed; its `finally` block for DELETE is entered
after that point. If the POST has been accepted and the worker starts but the
response is lost or the connection is disconnected during cancellation, the
client may never learn the ID and cannot issue its job-scoped DELETE. The
companion's existing worker deadline still applies, but a Cancel press is not
proof that the job immediately stopped or that the response was never processed.

The server handles accepted connections serially. A separate cancel request can
therefore also wait behind a request that is still being read or accepted.
TCP write success is not evidence that the client received the returned ID.
The encrypted intent is useful recovery evidence; it does not by itself give
the client a handle or prove that an unknown job was cancelled and reaped.

Alpha.13 source marks a possible start before sending the POST. If the user
cancels while a start may have been sent, the keyboard warns that stopping was
requested and the job may still be running. A transport/error result after a
possible start similarly warns that the outcome may be unknown. A cancel that
wins before dispatch does not issue that warning; the worker rechecks the stop
flag before sending. Terminal job states clear the marker. These are
conservative, in-memory notices, not a job ID, durable recovery, confirmed
cancellation or a way to inspect an unknown job. Session loss can still hide
the notice. The [hosted bundle and cancellation-ordering controls](gboard-alpha13-build-verification.json)
passed; [static APK inspection](gboard-alpha13-apk-verification.json) found the
warning references and passed a missing-bridge negative control. Those checks
do not establish Android behavior or actual cancellation and recovery.

The success path has a related recovery boundary. At source `6926a11`, the
worker seals a failure/cancellation receipt when its engine outcome is an
error, but on success it puts the completed job and result in the in-memory
queue without a corresponding success-settlement write in `jobs.rs`.
`Service::new` starts that queue empty. After a companion restart, this service
therefore cannot itself recover the earlier volatile result or establish a
terminal state from that queue. The previously sealed intent remains evidence
to preserve; this observation does not claim that an independent owner cannot
provide a recovery interface. No user request or model was run for this review.

Before device activation, the client and existing companion need a versioned,
authenticated way to bind an operation identity before the uncertain POST,
handle cancellation that races acceptance, and reconcile an unknown response
without silently resubmitting the request. Any duplicate/cancel/restart policy
must use the actual owner's encrypted recovery and no-replay authority; a
serialized ID or an in-memory flag alone is not that authority. Preserve the
existing one-shot local capability and process ownership. Do not expose a
general command endpoint or substitute another model.

Acceptance needs synthetic controls for a lost start response after acceptance,
cancellation before and after acceptance, duplicate IDs with different bodies,
server/client interruption and restart, and actual owned-process termination
and reaping. A still-running or uncertain operation must be reported as such.
Those controls cannot replace later authorized Android and privacy-writer tests.
