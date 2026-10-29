package dev.purple.patches.gboard;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Public synthetic worker-start controls; no Android, network or model access. */
public final class WorkerStartCheck {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void refused(WorkerStart.Starter starter) {
        AtomicInteger work = new AtomicInteger();
        boolean started = WorkerStart.launch(starter, work::incrementAndGet, "test");
        check(!started, "failed start reported success");
        check(work.get() == 0, "work ran after a refused start");
    }

    private static void scheduledThenRefused() throws Exception {
        AtomicInteger work = new AtomicInteger();
        AtomicReference<Thread> child = new AtomicReference<>();
        CountDownLatch childEntered = new CountDownLatch(1);
        boolean started = WorkerStart.launch((guarded, name) -> {
            Thread thread = new Thread(() -> { childEntered.countDown(); guarded.run(); }, name);
            child.set(thread);
            thread.start();
            try {
                if (!childEntered.await(3, TimeUnit.SECONDS)) throw new AssertionError("child did not start");
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AssertionError(interrupted);
            }
            throw new OutOfMemoryError("synthetic failure after scheduling");
        }, work::incrementAndGet, "purple-worker-refusal-control");
        check(!started, "post-scheduling refusal reported success");
        Thread thread = child.get();
        check(thread != null, "refusal control did not create a child");
        thread.join(3000);
        check(!thread.isAlive() && work.get() == 0, "a refused worker dispatched its action");
    }

    public static void main(String[] args) throws Exception {
        refused((work, name) -> { throw new SecurityException("synthetic refusal"); });
        refused((work, name) -> { throw new IllegalThreadStateException("synthetic refusal"); });
        refused((work, name) -> { throw new OutOfMemoryError("synthetic thread exhaustion"); });
        scheduledThenRefused();

        try {
            WorkerStart.launch((work, name) -> { throw new AssertionError("unexpected"); },
                    () -> {}, "test");
            throw new AssertionError("unexpected VM error was suppressed");
        } catch (AssertionError expected) {
            check("unexpected".equals(expected.getMessage()), "unexpected error handling changed");
        }

        CountDownLatch finished = new CountDownLatch(1);
        AtomicInteger completed = new AtomicInteger();
        boolean started = WorkerStart.launch(() -> { completed.incrementAndGet(); finished.countDown(); },
                "purple-worker-control");
        check(started && finished.await(3, TimeUnit.SECONDS), "actual worker did not complete");
        check(completed.get() == 1, "successful worker did not run exactly once");
        System.out.println("Worker start, scheduled-then-refused and success controls passed");
    }
}
