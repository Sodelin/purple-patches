import dev.purple.patches.natune.NatuneFolderCopy;
import dev.purple.patches.natune.FlacIntegrity;
import dev.purple.patches.natune.TemporarySpaceBudget;
import dev.purple.patches.natune.VerifiedStreamCopy;
import android.net.Uri;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;
import r8.c7b;
import r8.d7b;
import r8.dd6;

/** Integrated public-fixture controls; no actual journal, app or media. */
public final class NatuneFolderCopyControls {
    private static final String KEY = "qobuz:fixture-song";
    private static final String OPERATION = "a".repeat(64);
    private static final byte[] BYTES = new byte[4099];
    private static final NatuneFolderCopy.Target TARGET = new NatuneFolderCopy.Target(
        "external_primary", "Music/Selected", "fixture.flac", "audio/flac");
    private static int checks, serial;
    private static Path root;
    private interface Task { void run() throws Exception; }
    private static void need(boolean condition, String why) {
        checks++;
        if (!condition) throw new IllegalStateException(why);
    }
    private static void refuses(Task task, String why) throws Exception {
        try { task.run(); } catch (IOException expected) { need(true, why); return; }
        throw new IllegalStateException("Expected refusal: " + why);
    }
    private static final class Fixture {
        final MediaStoreAudioDestinationControls.App app = new MediaStoreAudioDestinationControls.App();
        final dd6 cache;
        final TreeSet<d7b> selected;
        final TemporarySpaceBudget budget = new TemporarySpaceBudget(1_000_000_000L);
        final NatuneFolderCopy.Attempt attempt = new NatuneFolderCopy.Attempt();
        final Owner owner;
        Fixture() throws IOException {
            Path dir = Files.createDirectory(root.resolve("case-" + serial++));
            Path a = Files.write(dir.resolve("a.public"), Arrays.copyOfRange(BYTES, 0, 2000));
            Path b = Files.write(dir.resolve("b.public"), Arrays.copyOfRange(BYTES, 2000, BYTES.length));
            c7b delegate = new c7b(BYTES.length);
            delegate.spans.add(new d7b(KEY, 0, 2000, 1, a.toFile()));
            delegate.spans.add(new d7b(KEY, 2000, BYTES.length - 2000, 1, b.toFile()));
            cache = new dd6(delegate);
            selected = cache.p(KEY);
            owner = new Owner(this);
        }
        VerifiedStreamCopy.VerifiedCopy run(VerifiedStreamCopy.StopCheck stop) throws IOException {
            return attempt.run(app, cache, selected, OPERATION, TARGET, budget,
                0, 128, stop, owner);
        }
        void originalRetained() {
            need(cache.c.spans.size() == 2 && cache.c.spans.stream().allMatch(s -> s.B.isFile()),
                "Original fixture spans remain");
        }
    }
    private static final class Owner implements NatuneFolderCopy.JournalOwner {
        final Fixture fixture;
        final List<String> calls = new ArrayList<>();
        String failAt;
        String actualName;
        boolean stopped;
        Owner(Fixture fixture) { this.fixture = fixture; }
        public void begin(String id, String key, VerifiedStreamCopy.SourceLease source,
                TemporarySpaceBudget.Reservation reservation, NatuneFolderCopy.Target target, boolean requireFullFlac)
                throws IOException {
            calls.add("begin");
            need(id.equals(OPERATION) && key.equals(KEY) && target == TARGET && !requireFullFlac,
                "Original intent and selected key reach owner");
            need(target.collectionUri().toString().equals("content://media/external_primary/audio/media") &&
                target.volume.equals("external_primary") && target.folder.equals("Music/Selected/") &&
                target.displayName.equals("fixture.flac") && target.audioMime.equals("audio/flac"),
                "Owner receives the actual collection and normalized selected destination");
            need(Thread.holdsLock(fixture.cache.b) && Thread.holdsLock(fixture.cache.c),
                "Original cache locks held during owner binding");
            need(fixture.app.store.inserts == 0 && reservation.sourceBytes == BYTES.length &&
                reservation.peakBytes == BYTES.length + 128, "Reservation precedes creation");
            source.assertUnchanged();
            if ("begin".equals(failAt)) throw new IOException("Synthetic uncertain begin");
        }
        public void destination(String id, Uri uri) throws IOException {
            calls.add("destination");
            need(id.equals(OPERATION) && uri.equals(fixture.attempt.returnedUri()) &&
                fixture.app.store.opens == 0, "Actual returned URI reaches owner before output");
            if ("destination".equals(failAt)) throw new IOException("Synthetic uncertain destination");
        }
        public void verified(String id, Uri uri, String name, VerifiedStreamCopy.VerifiedCopy proof,
                FlacIntegrity.Result flac) throws IOException {
            calls.add("verified");
            need(id.equals(OPERATION) && uri.equals(fixture.attempt.returnedUri()) &&
                name.equals(fixture.app.store.name) && flac == null && proof.bytes == BYTES.length &&
                fixture.app.store.pending == 0 && fixture.app.store.updates == 1,
                "Owner receives matching proof after publication and readback");
            actualName = name;
            if ("verified".equals(failAt)) throw new IOException("Synthetic uncertain verification");
            if ("stop-after-verified".equals(failAt)) stopped = true;
        }
    }
    private static void check() throws Exception {
        Fixture badId = new Fixture();
        for (String id : new String[]{null, "a".repeat(63), "a".repeat(65),
                "A".repeat(64), "g".repeat(64), "a".repeat(63) + "\n"}) {
            refuses(() -> badId.attempt.run(badId.app, badId.cache, badId.selected, id,
                TARGET, badId.budget, 0, 128, () -> {}, badId.owner),
                "Original operation ID must match the journal format");
        }
        need(badId.attempt.phase() == NatuneFolderCopy.Phase.NEW && badId.budget.reservedBytes() == 0 &&
            badId.owner.calls.isEmpty() && badId.app.store.inserts == 0,
            "Invalid ID never reaches reservation, journal or provider");
        for (String[] fields : new String[][] {
                {"bad/volume", "Music", "song.flac", "audio/flac"},
                {"external_primary", "Music/../Other", "song.flac", "audio/flac"},
                {"external_primary", "Music", "../song.flac", "audio/flac"},
                {"external_primary", "Music", "song.flac", "audio/*"},
                {"external_primary", "Music", "é".repeat(257), "audio/flac"},
                {"external_primary", "Music", "\ud800", "audio/flac"}}) {
            try {
                new NatuneFolderCopy.Target(fields[0], fields[1], fields[2], fields[3]);
                throw new IllegalStateException("Invalid target accepted");
            } catch (IllegalArgumentException expected) { need(true, "Invalid target refused before admission"); }
        }

        Fixture ok = new Fixture();
        var proof = ok.run(() -> {});
        need(ok.attempt.phase() == NatuneFolderCopy.Phase.LOCAL_VERIFIED && !ok.attempt.failed(),
            "One local copy finishes only after all selected stages");
        need(ok.owner.calls.equals(List.of("begin", "destination", "verified")) &&
            proof.bytes == BYTES.length && Arrays.equals(ok.app.store.body, BYTES),
            "One ordered source-to-folder copy and readback");
        need(ok.budget.reservedBytes() == BYTES.length + 128, "A local copy does not release staging budget");
        ok.originalRetained();
        refuses(() -> ok.run(() -> {}), "A successful original invocation cannot repeat");
        need(ok.app.store.inserts == 1 && ok.app.store.updates == 1, "No duplicate output or publication");

        Fixture collision = new Fixture(); collision.app.store.renameOnInsert = true;
        collision.run(() -> {});
        need("fixture (1).flac".equals(collision.owner.actualName),
            "Owner receives actual collision-safe name, not only requested name");
        collision.originalRetained();

        Fixture stale = new Fixture();
        d7b old = stale.cache.c.spans.pollLast();
        stale.cache.c.spans.add(new d7b(old.f, old.s, old.z, old.C, old.B));
        refuses(() -> stale.run(() -> {}), "Stale original selection refuses");
        need(stale.attempt.phase() == NatuneFolderCopy.Phase.ENTERED && stale.attempt.failed() &&
            stale.budget.reservedBytes() == 0 && stale.app.store.inserts == 0 && stale.owner.calls.isEmpty(),
            "Stale selection causes no admission or output");

        Fixture tooLarge = new Fixture();
        TemporarySpaceBudget small = new TemporarySpaceBudget(BYTES.length);
        refuses(() -> tooLarge.attempt.run(tooLarge.app, tooLarge.cache, tooLarge.selected,
            OPERATION, TARGET, small, 0, 128, () -> {}, tooLarge.owner),
            "An entire peak that exceeds the selected pool refuses");
        need(tooLarge.app.store.inserts == 0 && tooLarge.owner.calls.isEmpty() && small.reservedBytes() == 0,
            "Budget refusal creates no destination or journal claim");

        Fixture begin = new Fixture(); begin.owner.failAt = "begin";
        refuses(() -> begin.run(() -> {}), "Uncertain original claim stops creation");
        need(begin.attempt.phase() == NatuneFolderCopy.Phase.BEGIN_ENTERED && begin.attempt.failed() &&
            begin.app.store.inserts == 0 && begin.budget.reservedBytes() > 0,
            "Uncertain begin retains original admission");
        refuses(() -> begin.run(() -> {}), "Uncertain begin is not replayed");

        Fixture destination = new Fixture(); destination.owner.failAt = "destination";
        refuses(() -> destination.run(() -> {}), "Uncertain destination retention stops output");
        need(destination.attempt.phase() == NatuneFolderCopy.Phase.DESTINATION_ENTERED &&
            destination.attempt.returnedUri() != null && destination.app.store.opens == 0 &&
            destination.budget.reservedBytes() > 0, "Returned destination and reservation remain available");
        destination.originalRetained();

        Fixture wrongUri = new Fixture();
        wrongUri.app.store.returned = "content://other/external_primary/audio/media/42";
        refuses(() -> wrongUri.run(() -> {}), "An unexpected returned provider URI refuses output");
        need(wrongUri.attempt.phase() == NatuneFolderCopy.Phase.WRITE_ENTERED &&
            wrongUri.attempt.returnedUri() != null && wrongUri.app.store.opens == 0 &&
            wrongUri.budget.reservedBytes() > 0, "Unexpected URI remains available without opening it");

        Fixture afterCreate = new Fixture();
        refuses(() -> afterCreate.run(() -> {
            if (afterCreate.app.store.inserts > 0 && afterCreate.owner.calls.size() == 1)
                throw new IOException("Original cancelled after provider returned");
        }), "Original cancellation after creation stops output");
        need(afterCreate.attempt.phase() == NatuneFolderCopy.Phase.DESTINATION_RETURNED &&
            afterCreate.attempt.returnedUri() != null && afterCreate.app.store.opens == 0 &&
            afterCreate.budget.reservedBytes() > 0, "Cancellation retains the returned URI for reconciliation");

        Fixture corrupt = new Fixture(); corrupt.app.store.corruptRead = true;
        refuses(() -> corrupt.run(() -> {}), "Corrupt pending readback refuses publication");
        need(corrupt.attempt.phase() == NatuneFolderCopy.Phase.WRITE_ENTERED &&
            corrupt.app.store.updates == 0 && corrupt.owner.calls.equals(List.of("begin", "destination")) &&
            corrupt.budget.reservedBytes() > 0, "Failed copy retains the pending duplicate and original");
        corrupt.originalRetained();

        Fixture publication = new Fixture(); publication.app.store.updateZeroAfterChange = true;
        refuses(() -> publication.run(() -> {}), "Uncertain publication refuses success");
        need(publication.attempt.phase() == NatuneFolderCopy.Phase.PUBLISH_ENTERED &&
            publication.app.store.updates == 1 && publication.owner.calls.size() == 2 &&
            publication.budget.reservedBytes() > 0, "Publication uncertainty remains charged");
        refuses(() -> publication.run(() -> {}), "Unknown publication is not replayed");

        Fixture finalWrite = new Fixture(); finalWrite.owner.failAt = "verified";
        refuses(() -> finalWrite.run(() -> {}), "Uncertain verified event refuses success");
        need(finalWrite.attempt.phase() == NatuneFolderCopy.Phase.VERIFIED_ENTERED &&
            finalWrite.app.store.updates == 1 && finalWrite.budget.reservedBytes() > 0,
            "Verified event uncertainty retains publication and admission");
        finalWrite.originalRetained();

        Fixture lateStop = new Fixture(); lateStop.owner.failAt = "stop-after-verified";
        refuses(() -> lateStop.run(() -> { if (lateStop.owner.stopped) throw new IOException("Original stopped"); }),
            "Stop after verified retention cannot report success");
        need(lateStop.attempt.phase() == NatuneFolderCopy.Phase.VERIFIED_RETAINED && lateStop.attempt.failed() &&
            lateStop.budget.reservedBytes() > 0, "Late stop preserves verified event for reconciliation");

        Fixture afterClose = new Fixture();
        refuses(() -> afterClose.run(() -> {
            if (afterClose.owner.calls.contains("verified") && !Thread.holdsLock(afterClose.cache.b))
                throw new IOException("Original stopped while the lease closed");
        }), "Stop after lease close cannot report success");
        need(afterClose.attempt.phase() == NatuneFolderCopy.Phase.VERIFIED_RETAINED && afterClose.attempt.failed() &&
            afterClose.budget.reservedBytes() > 0, "Post-close stop retains original verified state");
    }
    public static void main(String[] args) throws Exception {
        for (int i = 0; i < BYTES.length; i++) BYTES[i] = (byte)(i * 29 + i / 7);
        root = Files.createTempDirectory("purple-public-folder-copy-");
        try { check(); }
        finally {
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
        System.out.println("NATUNE_FOLDER_COPY_CONTROLS passed=" + checks +
            " synthetic_owner_provider=true app_hook=false encrypted_owner_bound=false media_removed=false");
    }
}
