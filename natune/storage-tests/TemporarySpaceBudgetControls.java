import dev.purple.patches.natune.TemporarySpaceBudget;
import dev.purple.patches.natune.VerifiedStreamCopy;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Synthetic byte streams and owner callbacks, NOT Android cleanup authority. */
public final class TemporarySpaceBudgetControls {
    private static int checks;
    private interface Task { void run() throws Exception; }
    private static void need(boolean ok, String label) {
        checks++;
        if (!ok) throw new IllegalStateException(label);
    }
    private static void refuses(Task task, String label) throws Exception {
        try { task.run(); }
        catch (IOException expected) { need(true, label); return; }
        throw new IllegalStateException("Expected refusal: " + label);
    }
    private static final class Source implements VerifiedStreamCopy.SourceLease {
        final byte[] bytes = new byte[]{3, 1, 4, 1, 5, 9, 2, 6, 5};
        long length = bytes.length;
        public long length() { return length; }
        public boolean complete() { return true; }
        public void assertUnchanged() {}
        public InputStream open() { return new ByteArrayInputStream(bytes); }
    }
    private static final class Destination implements VerifiedStreamCopy.Destination {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        public OutputStream openNewOutput() { return bytes; }
        public InputStream reopen() { return new ByteArrayInputStream(bytes.toByteArray()); }
    }
    public static void main(String[] args) throws Exception {
        TemporarySpaceBudget budget = new TemporarySpaceBudget(TemporarySpaceBudget.DEFAULT_BYTES);
        TemporarySpaceBudget.Reservation a = budget.reserve("a", 399_999_999, 0, 1);
        TemporarySpaceBudget.Reservation b = budget.reserve("b", 499_999_999, 0, 1);
        TemporarySpaceBudget.Reservation c = budget.reserve("c", 99_999_999, 0, 1);
        need(budget.reservedBytes() == 1_000_000_000L, "Mixed track sizes exactly fill pool");
        refuses(() -> budget.reserve("queued", 1, 0, 1), "No admission above peak pool");
        refuses(() -> a.releaseAfterCleanup(() -> { throw new IOException("Unsettled staging"); }), "Unknown cleanup");
        need(budget.reservedBytes() == budget.limitBytes(), "Unsettled work stays charged");
        a.releaseAfterCleanup(() -> {});
        need(budget.reservedBytes() == 600_000_000L, "Only exact settled reservation released");
        TemporarySpaceBudget.Reservation d = budget.reserve("queued", 399_999_999, 0, 1);
        need(budget.reservedBytes() == budget.limitBytes(), "Refused queued ID admitted later without new identity");
        refuses(() -> budget.reserve("a", 1, 0, 1), "Settled ID never readmitted");
        refuses(() -> a.releaseAfterCleanup(() -> {}), "Double release");
        b.releaseAfterCleanup(() -> {}); c.releaseAfterCleanup(() -> {}); d.releaseAfterCleanup(() -> {});
        need(budget.reservedBytes() == 0, "No counter leak after controlled settlement");
        for (long[] sizes : new long[][]{{0,0,1}, {-1,0,1}, {1,-1,1}, {1,0,0},
                {Long.MAX_VALUE,0,1}, {1,Long.MAX_VALUE,1}, {1,0,Long.MAX_VALUE},
                {999_999_999,1,1}}) {
            refuses(() -> budget.reserve("invalid", sizes[0], sizes[1], sizes[2]), "Invalid/overflow/oversized peak");
        }
        need(budget.reservedBytes() == 0, "Invalid admissions allocate nothing");

        TemporarySpaceBudget small = new TemporarySpaceBudget(30);
        Source source = new Source(); Destination destination = new Destination();
        TemporarySpaceBudget.Reservation copy = small.reserve("copy", 9, 10, 1);
        VerifiedStreamCopy.VerifiedCopy proof = copy.copy(source, () -> destination, () -> {});
        need(proof.issuedFor(source, destination) && Arrays.equals(source.bytes, destination.bytes.toByteArray()),
                "Actual full copy/readback with bound original objects");
        need(small.reservedBytes() == 20, "Copy success does not release duplicate/staging budget");
        refuses(() -> copy.copy(source, () -> destination, () -> {}), "No copy replay");
        copy.releaseAfterCleanup(() -> {});
        need(small.reservedBytes() == 0, "Controlled owner cleanup frees capacity");

        TemporarySpaceBudget.Reservation partial = small.reserve("partial", 9, 0, 1);
        refuses(() -> partial.copy(source, () -> { throw new IOException("Provider failed after create"); }, () -> {}), "Factory failure");
        need(small.reservedBytes() == 10, "Factory uncertainty remains charged");
        refuses(() -> partial.copy(source, () -> new Destination(), () -> {}), "No retry after factory uncertainty");
        partial.releaseAfterCleanup(() -> {});
        TemporarySpaceBudget.Reservation stopped = small.reserve("stopped", 9, 0, 1);
        AtomicInteger factories = new AtomicInteger();
        refuses(() -> stopped.copy(source, () -> { factories.incrementAndGet(); return new Destination(); },
                () -> { throw new IOException("Original stop/headroom failed"); }), "Original stop before destination");
        need(factories.get() == 0 && small.reservedBytes() == 10, "No destination before owner checks; claim retained");
        stopped.releaseAfterCleanup(() -> {});
        TemporarySpaceBudget.Reservation changed = small.reserve("changed", 9, 0, 1);
        source.length = 10;
        refuses(() -> changed.copy(source, () -> { factories.incrementAndGet(); return new Destination(); }, () -> {}),
                "Source length changed after admission");
        need(factories.get() == 0, "Changed source refuses before document creation");
        changed.releaseAfterCleanup(() -> {}); source.length = 9;

        // Concurrent admission is an accounting race control, not parallel production.
        TemporarySpaceBudget concurrent = new TemporarySpaceBudget(10);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger admitted = new AtomicInteger(); AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread[] threads = new Thread[8];
        for (int i = 0; i < threads.length; i++) {
            final String id = "race-" + i;
            threads[i] = new Thread(() -> {
                try { start.await(); concurrent.reserve(id, 8, 0, 1); admitted.incrementAndGet(); }
                catch (IOException full) { /* Expected competitors stay pending. */ }
                catch (Throwable problem) { failure.compareAndSet(null, problem); }
            });
            threads[i].start();
        }
        start.countDown();
        for (Thread thread : threads) thread.join(5000);
        for (Thread thread : threads) need(!thread.isAlive(), "Bounded admission control completes");
        need(failure.get() == null && admitted.get() == 1 && concurrent.reservedBytes() == 9,
                "Atomic peak admission cannot overcommit");

        TemporarySpaceBudget reentrant = new TemporarySpaceBudget(10);
        TemporarySpaceBudget.Reservation held = reentrant.reserve("held", 9, 0, 1);
        held.copy(source, () -> {
            try { held.releaseAfterCleanup(() -> {}); throw new AssertionError("Released while copying"); }
            catch (IOException expected) { return new Destination(); }
        }, () -> {});
        need(reentrant.reservedBytes() == 10, "No release during active copy");
        refuses(() -> held.releaseAfterCleanup(() -> held.releaseAfterCleanup(() -> {})), "Reentrant settlement refused");
        need(reentrant.reservedBytes() == 10, "Failed settlement leaves entire reservation");
        held.releaseAfterCleanup(() -> {});
        System.out.println("TEMPORARY_SPACE_BUDGET_CONTROLS passed=" + checks +
                " android_headroom=false cleanup_owner_binding=false durable_recovery=false apk_hook=false");
    }
}
