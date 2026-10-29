package dev.purple.patches.gboard;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

/** One finite PCM capture; the owner supplies the actual recorder and clock. */
final class CaptureSession {
    private static final int BYTES_PER_SECOND = 32000;
    private static final int MAX_BYTES = SessionPolicy.AUDIO_SECONDS * BYTES_PER_SECOND;
    private static final long MAX_MILLIS = SessionPolicy.AUDIO_SECONDS * 1000L;
    private static final int READ_BYTES = 4096;
    private static final long IDLE_WAIT_MILLIS = 20;

    interface Source {
        void start() throws Exception;
        int read(byte[] destination, int offset, int length) throws Exception;
        void close() throws Exception;
    }
    interface Factory { Source open() throws Exception; }
    interface Clock {
        long nowMillis();
        void pause(long millis) throws InterruptedException;
    }

    private final AtomicBoolean stopped = new AtomicBoolean();
    private final AtomicBoolean claimed = new AtomicBoolean();
    void stop() { stopped.set(true); }

    private static long elapsed(Clock clock, long start) {
        long now = clock.nowMillis();
        if (start < 0 || now < start) throw new IllegalStateException("Recording clock unavailable");
        return now - start;
    }

    byte[] record(Factory factory, Clock clock, AtomicBoolean cancelled) throws Exception {
        if (factory == null || clock == null || cancelled == null) throw new IllegalArgumentException();
        if (!claimed.compareAndSet(false, true)) throw new IllegalStateException("Recording session already used");
        if (cancelled.get() || stopped.get()) throw new InterruptedException();
        Source source = null;
        byte[] pcm = null;
        byte[] wav = null;
        try {
            source = factory.open();
            if (source == null) throw new IllegalStateException("Microphone unavailable");
            if (cancelled.get() || stopped.get()) throw new InterruptedException();
            long start = clock.nowMillis();
            if (start < 0) throw new IllegalStateException("Recording clock unavailable");
            source.start();
            if (cancelled.get() || stopped.get()) throw new InterruptedException();
            pcm = new byte[MAX_BYTES];
            int used = 0;
            while (true) {
                if (cancelled.get()) throw new InterruptedException();
                if (stopped.get() || used == MAX_BYTES || elapsed(clock, start) >= MAX_MILLIS) break;
                int limit = Math.min(READ_BYTES, MAX_BYTES - used);
                int count = source.read(pcm, used, limit);
                if (cancelled.get()) throw new InterruptedException();
                if (count < 0) {
                    if (stopped.get()) break;
                    throw new IllegalStateException("Recording failed");
                }
                if (count > limit || (count & 1) != 0) throw new IllegalStateException("Invalid audio frame");
                long elapsed = elapsed(clock, start);
                // A delayed read cannot extend the accepted recording past its deadline.
                if (elapsed >= MAX_MILLIS) break;
                used += count;
                if (count == 0 && !stopped.get()) clock.pause(Math.min(IDLE_WAIT_MILLIS, MAX_MILLIS - elapsed));
            }
            if (cancelled.get()) throw new InterruptedException();
            if (used < 2) throw new IllegalStateException("No audio recorded");
            wav = new byte[44 + used];
            ByteBuffer out = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);
            out.put(new byte[]{'R','I','F','F'}).putInt(36 + used).put(new byte[]{'W','A','V','E','f','m','t',' '})
                    .putInt(16).putShort((short)1).putShort((short)1).putInt(16000).putInt(BYTES_PER_SECOND)
                    .putShort((short)2).putShort((short)16).put(new byte[]{'d','a','t','a'}).putInt(used).put(pcm, 0, used);
        } finally {
            if (pcm != null) Arrays.fill(pcm, (byte)0);
            try { if (source != null) source.close(); }
            catch (Exception failure) {
                if (wav != null) Arrays.fill(wav, (byte)0);
                throw failure;
            }
        }
        if (cancelled.get()) { Arrays.fill(wav, (byte)0); throw new InterruptedException(); }
        return wav;
    }
}
