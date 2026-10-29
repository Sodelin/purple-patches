package dev.purple.patches.gboard;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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

    public static void main(String[] args) throws Exception {
        refused((work, name) -> { throw new SecurityException("synthetic refusal"); });
        refused((work, name) -> { throw new IllegalThreadStateException("synthetic refusal"); });
        refused((work, name) -> { throw new OutOfMemoryError("synthetic thread exhaustion"); });

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
        System.out.println("Worker start refusal and success controls passed");
    }
}
