package dev.purple.patches.natune;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * Full native-FLAC decode of an already verified local copy. The native library
 * is a separately packaged, pinned libFLAC consumer; no app/device binding is
 * implied by this source. A result is audio-integrity evidence, not proof of
 * recording identity, a computer receipt, or permission to remove a source.
 */
public final class FlacIntegrity {
    private FlacIntegrity() {}

    public enum State {
        VERIFIED, CHECKSUM_UNAVAILABLE, SAMPLE_COUNT_UNAVAILABLE,
        CHECKSUM_AND_SAMPLE_COUNT_UNAVAILABLE
    }
    public static final class Result {
        public final State state;
        public final long samples;
        public final int sampleRate, channels, bitsPerSample;
        private Result(State state, long samples, int sampleRate, int channels, int bitsPerSample) {
            this.state = state; this.samples = samples; this.sampleRate = sampleRate;
            this.channels = channels; this.bitsPerSample = bitsPerSample;
        }
    }

    private static boolean loaded;
    static synchronized void loadDecoder() throws IOException {
        if (loaded) return;
        try { System.loadLibrary("purple_natune_flac"); }
        catch (UnsatisfiedLinkError unavailable) { throw new IOException("Selected FLAC verifier unavailable", unavailable); }
        loaded = true;
    }
    private static native long[] decode(Reader reader) throws IOException;

    /**
     * Decode the exact destination used by proof, while the original source
     * lease is current. Recompute the full encoded SHA-256 during decoding;
     * changed output, unknown extent, cancellation or close failure refuses.
     * The destination must still be reopenable, e.g. a pending MediaStore row.
     */
    public static Result verify(VerifiedStreamCopy.VerifiedCopy proof,
            VerifiedStreamCopy.SourceLease source, VerifiedStreamCopy.Destination destination,
            VerifiedStreamCopy.StopCheck stop) throws IOException {
        Objects.requireNonNull(proof, "proof");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(stop, "stop");
        stop.check();
        if (!proof.issuedFor(source, destination) || proof.bytes <= 0 ||
                !source.complete() || source.length() != proof.bytes)
            throw new IOException("Original complete copy required");
        source.assertUnchanged();
        loadDecoder();
        stop.check();
        Reader reader;
        long[] info;
        try (InputStream input = Objects.requireNonNull(destination.reopen(), "destination input")) {
            reader = new Reader(input, stop, proof.bytes);
            info = decode(reader);
            if (info == null || info.length != 6 || !reader.eof || reader.count != proof.bytes ||
                    !reader.nativeFlacPrefix() || !MessageDigest.isEqual(reader.digest.digest(), proof.sha256()))
                throw new IOException("FLAC copy identity or extent changed");
        }
        stop.check();
        source.assertUnchanged();
        if (!source.complete() || source.length() != proof.bytes)
            throw new IOException("Original source changed during FLAC validation");
        boolean checksum = info[0] == 1, total = info[1] == 1;
        if ((info[0] != 0 && !checksum) || (info[1] != 0 && !total) || info[2] <= 0 ||
                info[3] <= 0 || info[3] > Integer.MAX_VALUE || info[4] < 1 || info[4] > 8 ||
                info[5] < 4 || info[5] > 32)
            throw new IOException("Invalid FLAC decoder result");
        State state = checksum ? (total ? State.VERIFIED : State.SAMPLE_COUNT_UNAVAILABLE)
            : (total ? State.CHECKSUM_UNAVAILABLE : State.CHECKSUM_AND_SAMPLE_COUNT_UNAVAILABLE);
        return new Result(state, info[2], (int) info[3], (int) info[4], (int) info[5]);
    }

    /** The JNI decoder calls these methods only on the original calling thread. */
    private static final class Reader {
        final InputStream input;
        final VerifiedStreamCopy.StopCheck stop;
        final long expected;
        final MessageDigest digest;
        final byte[] prefix = new byte[4];
        long count;
        boolean eof;
        Reader(InputStream input, VerifiedStreamCopy.StopCheck stop, long expected) {
            this.input = input; this.stop = stop; this.expected = expected;
            try { digest = MessageDigest.getInstance("SHA-256"); }
            catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
        }
        private void check() throws IOException { stop.check(); }
        private int read(byte[] buffer) throws IOException {
            check();
            if (eof) return -1;
            int n = input.read(buffer, 0, buffer.length);
            check();
            if (n == -1) {
                eof = true;
                if (count != expected) throw new IOException("Encoded FLAC length changed");
                return -1;
            }
            if (n <= 0 || n > buffer.length || n > expected - count)
                throw new IOException("Encoded FLAC read changed");
            int prefixBytes = (int) Math.min((long) n, Math.max(0L, 4L - count));
            if (prefixBytes > 0) System.arraycopy(buffer, 0, prefix, (int) count, prefixBytes);
            digest.update(buffer, 0, n);
            count += n;
            return n;
        }
        boolean nativeFlacPrefix() {
            return prefix[0] == 'f' && prefix[1] == 'L' && prefix[2] == 'a' && prefix[3] == 'C';
        }
    }
}
