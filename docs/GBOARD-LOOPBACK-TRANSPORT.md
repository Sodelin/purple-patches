# Gboard loopback transport and proxy selection

The keyboard validates its companion endpoint as IPv4 loopback and sends the
capability and selected text or audio through its `BridgeClient` request method.
Through alpha.20 that method used `URL.openConnection()` without explicitly
selecting a proxy. Android documents that
[`openConnection(Proxy)`](https://developer.android.com/reference/java/net/URL#openConnection(java.net.Proxy))
preempts the default proxy selector, and that `Proxy.NO_PROXY` requests a direct
connection. The earlier code therefore did not itself establish that a configured
Java proxy would be bypassed. No forwarding or leak on a particular phone has
been observed.

Alpha.21 source requests `Proxy.NO_PROXY` for every bridge request. The hosted
fixture invokes the actual `BridgeClient.request` for synthetic GET and POST
requests using a fake URL handler and synthetic token; it opens no socket or
listener. The previous source failed the direct-proxy assertion in
[run 40971360001](https://github.com/Sodelin/purple-patches/actions/runs/40971360001).
Verification of the corrected bundle and APK is recorded separately when complete.

This bypasses the Java proxy selector for these loopback requests. It does not
prove Android runtime routing, protect against all local processes or network
interception, establish private Codex execution, or resolve the Termux stdout
bearer, owner identity or interrupted-job recovery. Device, model and installation
holds remain in force.
