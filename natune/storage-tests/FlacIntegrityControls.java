import dev.purple.patches.natune.FlacIntegrity;
import dev.purple.patches.natune.VerifiedStreamCopy;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/** Public hosted fixtures only; no app, provider, phone or personal audio. */
public final class FlacIntegrityControls {
    private static int checks;
    interface Task { void run() throws Exception; }
    static void need(boolean value, String why) { checks++; if (!value) throw new IllegalStateException(why); }
    static void refuses(Task task, String why) throws Exception {
        try { task.run(); } catch (IOException expected) { need(true, why); return; }
        throw new IllegalStateException("Expected refusal: " + why);
    }
    static final class Source implements VerifiedStreamCopy.SourceLease {
        final byte[] bytes;
        boolean changed;
        Source(byte[] bytes) { this.bytes = bytes.clone(); }
        public long length() { return bytes.length; }
        public boolean complete() { return true; }
        public void assertUnchanged() throws IOException { if (changed) throw new IOException("Changed fixture source"); }
        public InputStream open() { return new ByteArrayInputStream(bytes); }
    }
    static final class Destination implements VerifiedStreamCopy.Destination {
        byte[] body;
        boolean closeFailure;
        public OutputStream openNewOutput() {
            return new ByteArrayOutputStream() {
                public void close() throws IOException { super.close(); body = toByteArray(); }
            };
        }
        public InputStream reopen() {
            return new ByteArrayInputStream(body.clone()) {
                public void close() throws IOException {
                    super.close();
                    if (closeFailure) throw new IOException("Synthetic close failure");
                }
            };
        }
    }
    static final class Copy {
        final Source source;
        final Destination destination = new Destination();
        final VerifiedStreamCopy.VerifiedCopy proof;
        Copy(byte[] bytes) throws IOException {
            source = new Source(bytes);
            proof = VerifiedStreamCopy.copy(source, destination, () -> {});
        }
        FlacIntegrity.Result inspect() throws IOException {
            return FlacIntegrity.verify(proof, source, destination, () -> {});
        }
    }
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]);
        for (int bits : new int[] {8, 16, 24, 32}) {
            byte[] input = Files.readAllBytes(root.resolve("fixture-" + bits + ".flac"));
            Copy copy = new Copy(input);
            FlacIntegrity.Result result = copy.inspect();
            need(result.state == FlacIntegrity.State.VERIFIED && result.bitsPerSample == bits &&
                result.channels == 2 && result.sampleRate == 8000 && result.samples == 8000,
                "Complete original-bit-depth FLAC and PCM checksum");
            need(Arrays.equals(copy.source.bytes, input) && Arrays.equals(copy.destination.body, input),
                "Validation retains exact public fixture source and copy");
        }
        byte[] valid = Files.readAllBytes(root.resolve("fixture-24.flac"));
        byte[] noChecksum = valid.clone(); Arrays.fill(noChecksum, 26, 42, (byte) 0);
        need(new Copy(noChecksum).inspect().state == FlacIntegrity.State.CHECKSUM_UNAVAILABLE,
            "Missing checksum is not a verified checksum");
        byte[] noTotal = valid.clone(); noTotal[21] &= (byte) 0xf0; Arrays.fill(noTotal, 22, 26, (byte) 0);
        need(new Copy(noTotal).inspect().state == FlacIntegrity.State.SAMPLE_COUNT_UNAVAILABLE,
            "Unknown total is not a known-complete duration");
        byte[] neither = noTotal.clone(); Arrays.fill(neither, 26, 42, (byte) 0);
        need(new Copy(neither).inspect().state == FlacIntegrity.State.CHECKSUM_AND_SAMPLE_COUNT_UNAVAILABLE,
            "Both absent fields remain indeterminate");

        byte[] badDigest = valid.clone(); badDigest[26] ^= 1;
        refuses(() -> new Copy(badDigest).inspect(), "STREAMINFO PCM digest mismatch");
        byte[] badTotal = valid.clone(); badTotal[25]++;
        refuses(() -> new Copy(badTotal).inspect(), "Decoded sample count differs");
        byte[] badFrame = valid.clone(); badFrame[badFrame.length - 6] ^= 1;
        refuses(() -> new Copy(badFrame).inspect(), "Damaged frame CRC or decoded samples");
        refuses(() -> new Copy(Arrays.copyOf(valid, valid.length - 5)).inspect(), "Truncated FLAC");
        refuses(() -> new Copy(new byte[] {1, 2, 3, 4, 5, 6, 7}).inspect(), "Not a FLAC stream");

        Copy changed = new Copy(valid); changed.destination.body[changed.destination.body.length - 1] ^= 1;
        refuses(changed::inspect, "Verified destination changed");
        Copy wrong = new Copy(valid); Copy other = new Copy(valid);
        refuses(() -> FlacIntegrity.verify(wrong.proof, other.source, other.destination, () -> {}),
            "Proof belongs to the original source and destination");
        Copy sourceChanged = new Copy(valid); sourceChanged.source.changed = true;
        refuses(sourceChanged::inspect, "Original source changed");
        Copy cancelled = new Copy(valid); final int[] calls = {0};
        refuses(() -> FlacIntegrity.verify(cancelled.proof, cancelled.source, cancelled.destination,
            () -> { if (++calls[0] >= 5) throw new IOException("Synthetic stop"); }),
            "Original cancellation stops validation");
        Copy close = new Copy(valid); close.destination.closeFailure = true;
        refuses(close::inspect, "Readback close failure prevents a result");
        System.out.println("FLAC_INTEGRITY_CONTROLS passed=" + checks +
            " public_pcm_fixtures=true actual_app=false device_decoder=false media_removed=false");
    }
}
