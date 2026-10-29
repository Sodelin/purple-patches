package dev.purple.patches.natune;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * One byte-preserving copy with bounded memory and complete destination readback.
 * No temporary file, source deletion, URI logging, indexing or recovery writes.
 *
 * The Android owner must hold a real stable source lease and an exclusive newly
 * created destination for the whole operation. Interface implementations in
 * controls are substitutes, not evidence of Android lock or grant ownership.
 */
public final class VerifiedStreamCopy {
    private VerifiedStreamCopy() {}
    public static final int BUFFER_BYTES = 32768;

    public interface SourceLease {
        /** Actual complete rendition length, including validated cache coverage. */
        long length();
        boolean complete();
        /** Must refuse changed rendition/span/owner state; not a boolean permit. */
        void assertUnchanged() throws IOException;
        /** Full byte-zero stream for this exact lease; not a cache/network fallback. */
        InputStream open() throws IOException;
    }

    public interface Destination {
        /** New owned document, never an existing user file. Called at most once. */
        OutputStream openNewOutput() throws IOException;
        /** Reopen the same destination after the output has closed. */
        InputStream reopen() throws IOException;
    }

    public interface StopCheck {
        /** Original owner's cancellation/deadline; must throw to stop. */
        void check() throws IOException;
    }

    /**
     * In-memory byte evidence only. Not serializable, not permission to delete.
     * The host must still bind indexing, durable claims, fresh use checks and
     * exact original removal. A record reconstructed from these fields is not
     * this result and cannot recreate source/destination ownership.
     */
    public static final class VerifiedCopy {
        private final SourceLease source;
        private final Destination destination;
        public final long bytes;
        private final byte[] digest;
        private VerifiedCopy(SourceLease source, Destination destination, long bytes, byte[] digest) {
            this.source = source;
            this.destination = destination;
            this.bytes = bytes;
            this.digest = digest.clone();
        }
        public byte[] sha256() { return digest.clone(); }
        public boolean issuedFor(SourceLease source, Destination destination) {
            return this.source == source && this.destination == destination;
        }
    }

    public static VerifiedCopy copy(SourceLease source, Destination destination, StopCheck stop)
            throws IOException {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(stop, "stop");
        stop.check();
        final long expected = source.length();
        if (expected <= 0 || !source.complete()) throw new IOException("Complete source required");
        source.assertUnchanged();
        byte[] scratch = new byte[BUFFER_BYTES];
        MessageDigest written = sha256();
        try (InputStream input = require(source.open());
             OutputStream output = require(destination.openNewOutput())) {
            transfer(input, output, expected, written, scratch, stop);
            output.flush();
        }
        // Both source and destination close errors refuse before issuing proof.
        stop.check();
        source.assertUnchanged();
        if (source.length() != expected || !source.complete()) throw new IOException("Source changed");
        MessageDigest readback = sha256();
        try (InputStream input = require(destination.reopen())) {
            transfer(input, null, expected, readback, scratch, stop);
        }
        byte[] digest = written.digest();
        if (!MessageDigest.isEqual(digest, readback.digest())) throw new IOException("Readback mismatch");
        stop.check();
        source.assertUnchanged();
        if (source.length() != expected || !source.complete()) throw new IOException("Source changed");
        return new VerifiedCopy(source, destination, expected, digest);
    }

    private static void transfer(InputStream input, OutputStream output, long expected,
            MessageDigest digest, byte[] scratch, StopCheck stop) throws IOException {
        long count = 0;
        while (true) {
            stop.check();
            int n = input.read(scratch, 0, scratch.length);
            stop.check();
            if (n == -1) break;
            if (n <= 0 || n > scratch.length) throw new IOException("Invalid stream read");
            // Subtraction avoids overflow even for a hostile length declaration.
            if (n > expected - count) throw new IOException("Unexpected extra bytes");
            if (output != null) output.write(scratch, 0, n);
            digest.update(scratch, 0, n);
            count += n;
        }
        if (count != expected) throw new IOException("Incomplete bytes");
    }

    private static InputStream require(InputStream input) throws IOException {
        if (input == null) throw new IOException("Input stream unavailable");
        return input;
    }
    private static OutputStream require(OutputStream output) throws IOException {
        if (output == null) throw new IOException("Output stream unavailable");
        return output;
    }
    private static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
}
