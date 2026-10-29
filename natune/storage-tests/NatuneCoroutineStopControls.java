import dev.purple.patches.natune.NatuneCoroutineStop;
import dev.purple.patches.natune.VerifiedStreamCopy;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.CancellationException;

/** Public original-ABI stand-ins; no installed job, private data or Android IO. */
public final class NatuneCoroutineStopControls {
    private static int checks;
    private interface Task { void run() throws Exception; }
    private static void need(boolean ok, String label) {
        checks++;
        if (!ok) throw new IllegalStateException(label);
    }
    private static void refuses(Task task) throws Exception {
        try { task.run(); }
        catch (IOException expected) { need(true, "Refused"); return; }
        throw new IllegalStateException("Expected IO refusal");
    }
    public static void main(String[] args) throws Exception {
        CancellationException original = new CancellationException("Synthetic original job stop");
        boolean[] active = {true}; int[] calls = {0};
        r8.cf2 continuation = new r8.cf2(() -> {
            calls[0]++;
            if (!active[0]) throw original;
        });
        NatuneCoroutineStop stop = NatuneCoroutineStop.bind(continuation, () -> {});
        need(calls[0] == 1, "Bind checks real supplied context through native ABI");
        stop.check(); need(calls[0] == 2, "Original context checked again");
        active[0] = false;
        try { stop.check(); throw new AssertionError("Cancelled context entered"); }
        catch (CancellationException cancelled) { need(cancelled == original, "Original cancellation identity preserved"); }
        continuation.context = null;
        refuses(stop::check);
        refuses(() -> NatuneCoroutineStop.bind(new Object(), () -> {}));
        boolean[] ownerStopped = {false};
        continuation.context = () -> ownerStopped[0] = true;
        refuses(() -> NatuneCoroutineStop.bind(continuation, () -> {
            if (ownerStopped[0]) throw new IOException("Owner/headroom stopped");
        }));
        int[] before = {calls[0]};
        continuation.context = () -> calls[0]++;
        refuses(() -> NatuneCoroutineStop.bind(continuation, () -> { throw new IOException("Owner initially stopped"); }));
        need(calls[0] == before[0], "Owner refusal does not invoke original context");

        active[0] = true;
        continuation.context = () -> { if (!active[0]) throw original; };
        NatuneCoroutineStop copyStop = NatuneCoroutineStop.bind(continuation, () -> {});
        VerifiedStreamCopy.SourceLease source = new VerifiedStreamCopy.SourceLease() {
            public long length() { return 3; }
            public boolean complete() { return true; }
            public void assertUnchanged() {}
            public InputStream open() { return new ByteArrayInputStream(new byte[]{1, 2, 3}); }
        };
        int[] reopens = {0};
        VerifiedStreamCopy.Destination destination = new VerifiedStreamCopy.Destination() {
            public OutputStream openNewOutput() {
                return new ByteArrayOutputStream() {
                    public void close() { active[0] = false; }
                };
            }
            public InputStream reopen() { reopens[0]++; return new ByteArrayInputStream(new byte[]{1, 2, 3}); }
        };
        try { VerifiedStreamCopy.copy(source, destination, copyStop); throw new AssertionError("Late cancellation ignored"); }
        catch (CancellationException cancelled) { need(cancelled == original, "Copy propagates original job cancellation"); }
        need(reopens[0] == 0, "Cancelled copy issues no readback/proof");
        System.out.println("NATUNE_COROUTINE_STOP_CONTROLS passed=" + checks +
                " original_android_job=false apk_hook=false deletion=false");
    }
}
