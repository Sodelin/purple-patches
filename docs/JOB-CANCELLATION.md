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
