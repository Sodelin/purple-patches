import android.net.Uri;
import dev.purple.patches.natune.FlacIntegrity;
import dev.purple.patches.natune.NatuneFolderCopy;
import dev.purple.patches.natune.TemporarySpaceBudget;
import dev.purple.patches.natune.VerifiedStreamCopy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.TreeSet;
import r8.c7b;
import r8.d7b;
import r8.dd6;

/** Integrated public fixtures only; no actual app, encrypted owner or phone. */
public final class NatuneFlacFolderCopyControls {
    static final String ID = "b".repeat(64), KEY = "qobuz:public-fixture";
    static final NatuneFolderCopy.Target TARGET = new NatuneFolderCopy.Target(
        "external_primary", "Music/Selected", "fixture.flac", "audio/flac");
    static int checks, serial;
    static Path root;
    interface Task { void run() throws Exception; }
    static void need(boolean value, String why) { checks++; if (!value) throw new IllegalStateException(why); }
    static void refuses(Task task, String why) throws Exception {
        try { task.run(); } catch (IOException expected) { need(true, why); return; }
        throw new IllegalStateException("Expected refusal: " + why);
    }
    static final class Fixture implements NatuneFolderCopy.JournalOwner {
        final MediaStoreAudioDestinationControls.App app = new MediaStoreAudioDestinationControls.App();
        final TemporarySpaceBudget budget = new TemporarySpaceBudget(1_000_000_000L);
        final NatuneFolderCopy.Attempt attempt = new NatuneFolderCopy.Attempt();
        final dd6 cache;
        final TreeSet<d7b> selected;
        final Path sourceFile;
        int begins, destinations, verified;
        FlacIntegrity.Result received;
        Fixture(byte[] bytes) throws IOException {
            Path dir = Files.createDirectory(root.resolve("case-" + serial++));
            sourceFile = Files.write(dir.resolve("public-source.flac"), bytes);
            c7b delegate = new c7b(bytes.length);
            delegate.spans.add(new d7b(KEY, 0, bytes.length, 1, sourceFile.toFile()));
            cache = new dd6(delegate);
            selected = cache.p(KEY);
        }
        public void begin(String id, String key, VerifiedStreamCopy.SourceLease source,
                TemporarySpaceBudget.Reservation reservation, NatuneFolderCopy.Target target, boolean requireFullFlac) throws IOException {
            begins++;
            need(id.equals(ID) && key.equals(KEY) && target == TARGET && requireFullFlac &&
                Thread.holdsLock(cache.b) && Thread.holdsLock(cache.c), "Original fixture selection reaches owner");
            source.assertUnchanged();
        }
        public void destination(String id, Uri uri) { destinations++; }
        public void verified(String id, Uri uri, String name, VerifiedStreamCopy.VerifiedCopy copy,
                FlacIntegrity.Result flac) {
            verified++; received = flac;
            need(id.equals(ID) && uri.equals(attempt.returnedUri()) && name.equals(app.store.name) &&
                flac != null && flac.state == FlacIntegrity.State.VERIFIED && app.store.pending == 0,
                "Actual checked FLAC observation reaches owner after publication");
        }
        void run(VerifiedStreamCopy.StopCheck stop) throws IOException {
            attempt.runFlac(app, cache, selected, ID, TARGET, budget, 0, 128, stop, this);
        }
    }
    static void test(byte[] valid) throws Exception {
        Fixture success = new Fixture(valid);
        success.run(() -> {});
        need(success.attempt.phase() == NatuneFolderCopy.Phase.LOCAL_VERIFIED &&
            success.received == success.attempt.flacResult() && success.received.bitsPerSample == 24 &&
            success.received.samples == 8000 && success.begins == 1 && success.destinations == 1 &&
            success.verified == 1 && success.app.store.updates == 1, "One complete FLAC copy is published");
        need(Files.isRegularFile(success.sourceFile) && Arrays.equals(success.app.store.body, valid) &&
            success.budget.reservedBytes() > 0, "Source remains and duplicate stays charged");
        refuses(() -> success.run(() -> {}), "Original successful attempt cannot replay");

        byte[] noChecksum = valid.clone(); Arrays.fill(noChecksum, 26, 42, (byte) 0);
        Fixture unknown = new Fixture(noChecksum);
        refuses(() -> unknown.run(() -> {}), "Missing checksum cannot publish as verified FLAC");
        need(unknown.attempt.phase() == NatuneFolderCopy.Phase.FLAC_VERIFY_ENTERED &&
            unknown.attempt.flacResult() == null && unknown.app.store.pending == 1 &&
            unknown.app.store.updates == 0 && unknown.verified == 0 && unknown.budget.reservedBytes() > 0 &&
            Files.isRegularFile(unknown.sourceFile), "Unknown integrity retains source and pending destination");
        refuses(() -> unknown.run(() -> {}), "Uncertain original is not replayed");

        byte[] damaged = valid.clone(); damaged[damaged.length - 6] ^= 1;
        Fixture corrupt = new Fixture(damaged);
        refuses(() -> corrupt.run(() -> {}), "Damaged FLAC cannot publish");
        need(corrupt.app.store.updates == 0 && corrupt.verified == 0 && Files.isRegularFile(corrupt.sourceFile),
            "Damaged audio retains source");

        Fixture cancelled = new Fixture(valid);
        refuses(() -> cancelled.run(() -> {
            if (cancelled.attempt.phase() == NatuneFolderCopy.Phase.FLAC_VERIFY_ENTERED)
                throw new IOException("Synthetic original stop");
        }), "Cancellation before FLAC decode stops publication");
        need(cancelled.app.store.updates == 0 && cancelled.verified == 0 && Files.isRegularFile(cancelled.sourceFile),
            "Cancelled validation retains source and pending destination");

        Fixture mismatch = new Fixture(valid);
        NatuneFolderCopy.Target wrong = new NatuneFolderCopy.Target("external_primary", "Music/Selected", "fixture.mp3", "audio/mpeg");
        refuses(() -> mismatch.attempt.runFlac(mismatch.app, mismatch.cache, mismatch.selected, ID,
            wrong, mismatch.budget, 0, 128, () -> {}, mismatch), "FLAC mode requires selected FLAC MIME");
        need(mismatch.attempt.phase() == NatuneFolderCopy.Phase.NEW && mismatch.budget.reservedBytes() == 0 &&
            mismatch.app.store.inserts == 0 && mismatch.begins == 0, "Wrong target refuses before admission");
    }
    public static void main(String[] args) throws Exception {
        byte[] input = Files.readAllBytes(Path.of(args[0]).resolve("fixture-24.flac"));
        root = Files.createTempDirectory("purple-public-flac-folder-");
        try {
            if (args.length > 1 && args[1].equals("missing")) {
                Fixture unavailable = new Fixture(input);
                refuses(() -> unavailable.run(() -> {}), "Missing selected native verifier");
                need(unavailable.attempt.phase() == NatuneFolderCopy.Phase.ENTERED &&
                    unavailable.budget.reservedBytes() == 0 && unavailable.app.store.inserts == 0 &&
                    unavailable.begins == 0 && Files.isRegularFile(unavailable.sourceFile),
                    "Missing verifier refuses before admission or output");
            } else test(input);
        } finally {
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
        System.out.println("NATUNE_FLAC_FOLDER_CONTROLS passed=" + checks +
            " synthetic_owner_provider=true actual_app=false media_removed=false");
    }
}
