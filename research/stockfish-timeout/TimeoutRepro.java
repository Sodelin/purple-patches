import app.noam.extension.chesscom.stockfish.Engine;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Executes the exact public Engine.waitFor method with a silent synthetic stream. */
public final class TimeoutRepro {
    private static final class HeldReader extends Reader {
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        @Override public int read(char[] chars, int offset, int length) throws IOException {
            entered.countDown();
            try {
                release.await();
                return -1;
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IOException("Synthetic reader interrupted", interrupted);
            }
        }
        @Override public void close() { release.countDown(); }
    }

    public static void main(String[] args) throws Exception {
        Engine engine = new Engine();
        HeldReader held = new HeldReader();
        Field stdout = Engine.class.getDeclaredField("stdout");
        stdout.setAccessible(true);
        stdout.set(engine, new BufferedReader(held));
        Method waitFor = Engine.class.getDeclaredMethod("waitFor", String.class);
        waitFor.setAccessible(true);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<Boolean> result = executor.submit(() -> (Boolean) waitFor.invoke(engine, "uciok"));
        long started = System.nanoTime();
        try {
            if (!held.entered.await(5, TimeUnit.SECONDS)) throw new AssertionError("Reader was never entered");
            try {
                result.get(31_500, TimeUnit.MILLISECONDS);
                throw new AssertionError("Pinned source returned while the silent stream stayed open");
            } catch (TimeoutException expected) {
                long observedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
                if (observedMs <= 30_000) throw new AssertionError("Fixture did not exceed source deadline");
                System.out.println("SOURCE_TIMEOUT_EXCEEDED_WHILE_READ_BLOCKED observed_ms=" + observedMs);
            }
            held.release.countDown();
            if (result.get(5, TimeUnit.SECONDS)) throw new AssertionError("EOF unexpectedly matched uciok");
            System.out.println("SYNTHETIC_STREAM_RELEASED_AND_METHOD_RETURNED_FALSE");
        } catch (ExecutionException failed) {
            throw new AssertionError("Pinned method failed during the fixture", failed.getCause());
        } finally {
            held.release.countDown();
            executor.shutdownNow();
        }
    }
}
