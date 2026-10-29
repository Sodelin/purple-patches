import dev.purple.patches.natune.VerifiedStreamCopy;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.Arrays;

/** Public synthetic byte streams. No Android ownership, URI grant or removal. */
public final class VerifiedStreamCopyControls {
    private static int checks;
    private static void need(boolean ok, String label) {
        checks++;
        if (!ok) throw new IllegalStateException(label);
    }
    private interface Task { void run() throws Exception; }
    private static void refuses(Task task, String label) throws Exception {
        try { task.run(); }
        catch (IOException expected) { need(true, label); return; }
        throw new IllegalStateException("Expected refusal: " + label);
    }
    private static final class Source implements VerifiedStreamCopy.SourceLease {
        final byte[] original;
        long declared;
        int opens, closes, validations, failValidation;
        boolean complete = true, nullInput, zeroRead, failRead, failClose;
        Source(byte[] bytes) { original = bytes.clone(); declared = bytes.length; }
        public long length() { return declared; }
        public boolean complete() { return complete; }
        public void assertUnchanged() throws IOException {
            validations++;
            if (validations == failValidation) throw new IOException("Changed source");
        }
        public InputStream open() {
            opens++;
            if (nullInput) return null;
            return new InputStream() {
                int offset;
                public int read() { throw new AssertionError("Bounded bulk reads only"); }
                public int read(byte[] out, int start, int count) throws IOException {
                    if (failRead) throw new IOException("Read failed");
                    if (zeroRead) return 0;
                    if (offset == original.length) return -1;
                    int n = Math.min(count, original.length - offset);
                    System.arraycopy(original, offset, out, start, n); offset += n; return n;
                }
                public void close() throws IOException {
                    closes++;
                    if (failClose) throw new IOException("Source close failed");
                }
            };
        }
    }
    private static final class Destination implements VerifiedStreamCopy.Destination {
        final ByteArrayOutputStream storage = new ByteArrayOutputStream();
        int writes, opens, closes, reopens, readCloses, maxWrite;
        int lengthDelta;
        boolean nullOutput, nullRead, failWrite, failFlush, failClose, failReadClose, corrupt;
        Runnable afterClose = () -> {};
        public OutputStream openNewOutput() {
            opens++;
            if (nullOutput) return null;
            return new OutputStream() {
                public void write(int b) { throw new AssertionError("Bounded bulk writes only"); }
                public void write(byte[] in, int start, int length) throws IOException {
                    if (failWrite) throw new IOException("Write failed");
                    writes++; maxWrite = Math.max(maxWrite, length); storage.write(in, start, length);
                }
                public void flush() throws IOException { if (failFlush) throw new IOException("Flush failed"); }
                public void close() throws IOException {
                    closes++; afterClose.run();
                    if (failClose) throw new IOException("Output close failed");
                }
            };
        }
        public InputStream reopen() {
            reopens++;
            if (nullRead) return null;
            byte[] bytes = Arrays.copyOf(storage.toByteArray(), storage.size() + lengthDelta);
            if (corrupt && bytes.length > 0) bytes[0] ^= 1;
            return new ByteArrayInputStream(bytes) {
                public void close() throws IOException {
                    readCloses++;
                    if (failReadClose) throw new IOException("Readback close failed");
                }
            };
        }
    }
    private static void copy(Source source, Destination dest) throws IOException {
        VerifiedStreamCopy.copy(source, dest, () -> {});
    }
    public static void main(String[] args) throws Exception {
        byte[] original = new byte[100003];
        for (int i = 0; i < original.length; i++) original[i] = (byte)(i * 37 + i / 13);
        Source good = new Source(original); Destination dest = new Destination();
        VerifiedStreamCopy.VerifiedCopy proof = VerifiedStreamCopy.copy(good, dest, () -> {});
        need(Arrays.equals(original, dest.storage.toByteArray()), "Complete exact bytes");
        need(Arrays.equals(original, good.original), "Source unchanged");
        need(proof.bytes == original.length && Arrays.equals(proof.sha256(),
                MessageDigest.getInstance("SHA-256").digest(original)), "Full digest and count");
        need(good.opens == 1 && good.closes == 1 && dest.opens == 1 && dest.closes == 1 &&
                dest.reopens == 1 && dest.readCloses == 1, "Fresh readback after close, all streams closed");
        need(dest.maxWrite <= VerifiedStreamCopy.BUFFER_BYTES && dest.writes > 1, "Bounded streaming");
        need(proof.issuedFor(good, dest) && !proof.issuedFor(new Source(original), dest) &&
                !proof.issuedFor(good, new Destination()), "Original objects bound");
        byte[] digest = proof.sha256(); digest[0] ^= 1;
        need(!Arrays.equals(digest, proof.sha256()), "Digest is immutable");
        for (String mode : new String[]{"short", "extra", "nullInput", "zeroRead", "failRead", "failClose"}) {
            Source source = new Source(original); Destination output = new Destination();
            switch (mode) {
                case "short" -> source.declared++;
                case "extra" -> source.declared--;
                case "nullInput" -> source.nullInput = true;
                case "zeroRead" -> source.zeroRead = true;
                case "failRead" -> source.failRead = true;
                case "failClose" -> source.failClose = true;
            }
            refuses(() -> copy(source, output), mode);
            need(output.reopens == 0 && Arrays.equals(source.original, original), "No proof/source change: " + mode);
        }
        for (String mode : new String[]{"nullOutput", "failWrite", "failFlush", "failClose",
                "nullRead", "shortReadback", "extraReadback", "corrupt", "failReadClose"}) {
            Source source = new Source(original); Destination output = new Destination();
            switch (mode) {
                case "nullOutput" -> output.nullOutput = true;
                case "failWrite" -> output.failWrite = true;
                case "failFlush" -> output.failFlush = true;
                case "failClose" -> output.failClose = true;
                case "nullRead" -> output.nullRead = true;
                case "shortReadback" -> output.lengthDelta = -1;
                case "extraReadback" -> output.lengthDelta = 1;
                case "corrupt" -> output.corrupt = true;
                case "failReadClose" -> output.failReadClose = true;
            }
            refuses(() -> copy(source, output), mode);
            need(source.closes == 1 && Arrays.equals(source.original, original), "Closed/retained source: " + mode);
        }
        for (int phase = 1; phase <= 3; phase++) {
            Source source = new Source(original); source.failValidation = phase;
            Destination output = new Destination();
            refuses(() -> copy(source, output), "Source revision changed at phase " + phase);
        }
        for (long length : new long[]{0, -1, Long.MAX_VALUE}) {
            Source source = new Source(original); source.declared = length;
            refuses(() -> copy(source, new Destination()), "Invalid/incomplete expected length " + length);
        }
        Source partial = new Source(original); partial.complete = false;
        Destination unopened = new Destination();
        refuses(() -> copy(partial, unopened), "Incomplete source");
        need(partial.opens == 0 && unopened.opens == 0, "Incomplete source touches no streams");
        Source stopped = new Source(original); Destination untouched = new Destination();
        refuses(() -> VerifiedStreamCopy.copy(stopped, untouched, () -> { throw new IOException("Stopped"); }), "Stop before opening");
        need(stopped.opens == 0 && untouched.opens == 0, "Stopped operation touches no streams");
        Source lateSource = new Source(original); Destination lateDest = new Destination();
        boolean[] stop = {false}; lateDest.afterClose = () -> stop[0] = true;
        refuses(() -> VerifiedStreamCopy.copy(lateSource, lateDest, () -> {
            if (stop[0]) throw new IOException("Stopped after write");
        }), "Stop after output close");
        need(lateDest.reopens == 0 && Arrays.equals(lateSource.original, original), "Late stop retains source");
        System.out.println("VERIFIED_STREAM_COPY_CONTROLS passed=" + checks +
                " android_owner_binding=false source_removal=false installed_ui=false");
    }
}
