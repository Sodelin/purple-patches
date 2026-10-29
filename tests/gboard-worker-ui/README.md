# Gboard worker failure cleanup controls

This fixture compiles the actual Gboard runtime source against the Android SDK
and invokes its private worker-start failure cleanup on a host JVM. Its tiny
`android.*` stand-ins implement only the Looper, Handler and dialog behavior
needed for these calls. It does not launch Android, the app, a microphone,
network access, the companion or a model.

The `current` control covers a matching request and a synthetic dialog-dismiss
failure. The `stale` probe supplies a deliberately stale request alongside a
simulated newer owner and checks that the newer busy state is retained. This is
a source-level state probe: it does not prove that Android's actual event order
can reach that state. A known alpha.23 failure is captured as an expected
diagnostic in the specifically pinned hosted workflow, not counted as a fix.
