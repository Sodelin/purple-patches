package dev.purple.patches.gboard;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

/** Public synthetic inputs exercise the actual capture controller; no microphone is opened. */
public final class CaptureSessionCheck {
    private static int checks;
    interface Checked { void run() throws Exception; }
    interface Reader { int read(Source source, byte[] out, int offset, int length) throws Exception; }

    static void check(boolean value) {
        checks++;
        if (!value) throw new AssertionError("capture control " + checks);
    }
    static void fails(Class<? extends Exception> expected, Checked work) throws Exception {
        try { work.run(); throw new AssertionError("Expected " + expected.getSimpleName()); }
        catch (Exception failure) { check(expected.isInstance(failure)); }
    }
    static final class Clock implements CaptureSession.Clock {
        long now;
        boolean interrupted;
        int pauses;
        public long nowMillis() { return now; }
        public void pause(long millis) throws InterruptedException {
            if (interrupted) throw new InterruptedException();
            check(millis > 0 && millis <= 20);
            pauses++;
            now += millis;
        }
    }
    static final class Source implements CaptureSession.Source {
        int starts, reads, closes;
        boolean failStart, failClose;
        byte[] lastBuffer;
        Runnable onStart = () -> {};
        Runnable onClose = () -> {};
        Reader reader;
        Source(Reader reader) { this.reader = reader; }
        public void start() {
            starts++;
            onStart.run();
            if (failStart) throw new IllegalStateException("start");
        }
        public int read(byte[] out, int offset, int length) throws Exception {
            reads++;
            lastBuffer = out;
            return reader.read(this, out, offset, length);
        }
        public void close() {
            closes++;
            onClose.run();
            if (failClose) throw new IllegalStateException("close");
        }
        boolean wiped() {
            if (lastBuffer == null) return true;
            for (byte value : lastBuffer) if (value != 0) return false;
            return true;
        }
    }
    static int sample(Source source, byte[] out, int offset, int length) {
        out[offset] = 1; out[offset + 1] = 2;
        return 2;
    }
    static void wav(byte[] value, int pcmLength) {
        ByteBuffer b = ByteBuffer.wrap(value).order(ByteOrder.LITTLE_ENDIAN);
        check(value.length == 44 + pcmLength && b.getInt(4) == 36 + pcmLength && b.getInt(40) == pcmLength);
        check(b.getInt(24) == 16000 && b.getInt(28) == 32000 && b.getShort(34) == 16);
        check(new String(value, 0, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("RIFF"));
    }
    public static void main(String[] args) throws Exception {
        CaptureSession normal = new CaptureSession(); Clock clock = new Clock();
        Source one = new Source((s, out, offset, length) -> { int n = sample(s,out,offset,length); normal.stop(); return n; });
        byte[] result = normal.record(() -> one, clock, new AtomicBoolean());
        wav(result, 2); check(result[44] == 1 && result[45] == 2);
        check(!normal.limitReached());
        check(one.starts == 1 && one.reads == 1 && one.closes == 1 && one.wiped());
        fails(IllegalStateException.class, () -> normal.record(() -> one, clock, new AtomicBoolean()));

        CaptureSession preStop = new CaptureSession(); preStop.stop();
        int[] opened = {0};
        fails(InterruptedException.class, () -> preStop.record(() -> { opened[0]++; return one; }, new Clock(), new AtomicBoolean()));
        check(opened[0] == 0);
        CaptureSession preCancel = new CaptureSession();
        fails(InterruptedException.class, () -> preCancel.record(() -> { opened[0]++; return one; }, new Clock(), new AtomicBoolean(true)));
        check(opened[0] == 0);

        CaptureSession duringOpen = new CaptureSession(); Source unopened = new Source(CaptureSessionCheck::sample);
        fails(InterruptedException.class, () -> duringOpen.record(() -> { duringOpen.stop(); return unopened; }, new Clock(), new AtomicBoolean()));
        check(unopened.starts == 0 && unopened.reads == 0 && unopened.closes == 1);

        CaptureSession duringStart = new CaptureSession(); Source starting = new Source(CaptureSessionCheck::sample);
        starting.onStart = duringStart::stop;
        fails(InterruptedException.class, () -> duringStart.record(() -> starting, new Clock(), new AtomicBoolean()));
        check(starting.starts == 1 && starting.reads == 0 && starting.closes == 1);

        CaptureSession cancel = new CaptureSession(); AtomicBoolean cancelled = new AtomicBoolean();
        Source mid = new Source((s, out, offset, length) -> { int n = sample(s,out,offset,length); cancelled.set(true); return n; });
        fails(InterruptedException.class, () -> cancel.record(() -> mid, new Clock(), cancelled));
        check(mid.closes == 1 && mid.wiped());

        CaptureSession cancelOnClose = new CaptureSession(); AtomicBoolean closingCancellation = new AtomicBoolean();
        Source closing = new Source((s, out, offset, length) -> { int n = sample(s,out,offset,length); cancelOnClose.stop(); return n; });
        closing.onClose = () -> closingCancellation.set(true);
        fails(InterruptedException.class, () -> cancelOnClose.record(() -> closing, new Clock(), closingCancellation));
        check(closing.closes == 1 && closing.wiped());

        CaptureSession silent = new CaptureSession(); Clock noData = new Clock(); Source zero = new Source((s, out, offset, length) -> 0);
        fails(IllegalStateException.class, () -> silent.record(() -> zero, noData, new AtomicBoolean()));
        check(noData.now == 60000 && noData.pauses == 3000 && zero.closes == 1 && zero.wiped());

        CaptureSession late = new CaptureSession(); Clock cutoff = new Clock();
        Source slow = new Source((s, out, offset, length) -> {
            int n = sample(s,out,offset,length);
            if (s.reads == 2) cutoff.now = 60000;
            return n;
        });
        byte[] bounded = late.record(() -> slow, cutoff, new AtomicBoolean());
        wav(bounded, 2); check(slow.reads == 2 && slow.closes == 1 && slow.wiped() && late.limitReached());

        CaptureSession full = new CaptureSession(); Source fast = new Source((s, out, offset, length) -> {
            Arrays.fill(out, offset, offset + length, (byte)7); return length;
        });
        byte[] maximum = full.record(() -> fast, new Clock(), new AtomicBoolean());
        wav(maximum, 60 * 32000); check(fast.closes == 1 && fast.wiped() && full.limitReached());

        for (int bad : new int[]{-1, 1, 4097}) {
            CaptureSession invalid = new CaptureSession(); Source source = new Source((s, out, offset, length) -> bad);
            fails(IllegalStateException.class, () -> invalid.record(() -> source, new Clock(), new AtomicBoolean()));
            check(source.closes == 1 && source.wiped());
        }
        CaptureSession startFailure = new CaptureSession(); Source failed = new Source(CaptureSessionCheck::sample); failed.failStart = true;
        fails(IllegalStateException.class, () -> startFailure.record(() -> failed, new Clock(), new AtomicBoolean()));
        check(failed.closes == 1);

        CaptureSession interrupted = new CaptureSession(); Clock sleeping = new Clock(); sleeping.interrupted = true;
        Source waiting = new Source((s, out, offset, length) -> 0);
        fails(InterruptedException.class, () -> interrupted.record(() -> waiting, sleeping, new AtomicBoolean()));
        check(waiting.closes == 1 && waiting.wiped());

        CaptureSession closeFailure = new CaptureSession();
        Source failedClose = new Source((s, out, offset, length) -> { int n = sample(s,out,offset,length); closeFailure.stop(); return n; });
        failedClose.failClose = true;
        fails(IllegalStateException.class, () -> closeFailure.record(() -> failedClose, new Clock(), new AtomicBoolean()));
        check(failedClose.closes == 1 && failedClose.wiped());

        CaptureSession clockFailure = new CaptureSession(); Clock backwards = new Clock(); backwards.now = 10;
        Source badClock = new Source((s, out, offset, length) -> { backwards.now = 9; return sample(s,out,offset,length); });
        fails(IllegalStateException.class, () -> clockFailure.record(() -> badClock, backwards, new AtomicBoolean()));
        check(badClock.closes == 1 && badClock.wiped());
        System.out.println("Finite capture, stop/cancel, deadline, WAV and cleanup checks passed: " + checks);
    }
}
