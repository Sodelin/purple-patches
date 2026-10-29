import dev.purple.patches.natune.NatuneCachedSource;
import dev.purple.patches.natune.VerifiedStreamCopy;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Comparator;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import r8.c7b;
import r8.d7b;
import r8.dd6;

/** Synthetic public files/ABI stand-ins; no installed cache, Android grant or deletion. */
public final class NatuneCachedSourceControls {
    private static int checks;
    private static final String KEY = "public-fixture-rendition";
    private static final byte[] BYTES = new byte[12345];
    private static Path root;
    private static int serial;
    private interface Task { void run() throws Exception; }
    private static void need(boolean ok, String name) {
        checks++;
        if (!ok) throw new IllegalStateException(name);
    }
    private static void refuses(Task task, String name) throws Exception {
        try { task.run(); }
        catch (IOException expected) { need(true, name); return; }
        throw new IllegalStateException("Expected refusal: " + name);
    }
    private static dd6 fixture() throws IOException {
        c7b cache = new c7b(BYTES.length);
        Path dir = Files.createDirectory(root.resolve("fixture-" + serial++));
        int split = 5500;
        Path a = Files.write(dir.resolve("a.public"), Arrays.copyOfRange(BYTES, 0, split));
        Path b = Files.write(dir.resolve("b.public"), Arrays.copyOfRange(BYTES, split, BYTES.length));
        cache.spans.add(new d7b(KEY, 0, split, 1, a.toFile()));
        cache.spans.add(new d7b(KEY, split, BYTES.length - split, 1, b.toFile()));
        return new dd6(cache);
    }
    private static final class Destination implements VerifiedStreamCopy.Destination {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        boolean opened;
        public OutputStream openNewOutput() { opened = true; return bytes; }
        public InputStream reopen() { return new ByteArrayInputStream(bytes.toByteArray()); }
    }
    private static <T> T withSource(Object cache, NatuneCachedSource.ReadAction<T> action) throws IOException {
        // Explicit controlled stop stand-in; no Android job/control authority.
        return NatuneCachedSource.withSource(cache, KEY, () -> {}, action);
    }
    private static VerifiedStreamCopy.VerifiedCopy copy(dd6 cache, Destination destination) throws IOException {
        return withSource(cache,
                source -> VerifiedStreamCopy.copy(source, destination, () -> {}));
    }
    private static void check() throws Exception {
        dd6 cache = fixture(); Destination dest = new Destination();
        VerifiedStreamCopy.SourceLease[] escaped = new VerifiedStreamCopy.SourceLease[1];
        VerifiedStreamCopy.VerifiedCopy proof = withSource(cache, source -> {
            need(Thread.holdsLock(cache.b) && Thread.holdsLock(cache.c), "Actual two owner monitors held");
            escaped[0] = source;
            return VerifiedStreamCopy.copy(source, dest, () -> {});
        });
        need(Arrays.equals(BYTES, dest.bytes.toByteArray()) && proof.bytes == BYTES.length,
                "Two real public fixture files stream in byte order");
        need(cache.c.spans.size() == 2 && cache.c.spans.stream().allMatch(s -> s.B.isFile()),
                "No source cache/file removal");
        need(!escaped[0].complete(), "Escaped lease invalidated");
        refuses(() -> escaped[0].open(), "Escaped source cannot reopen");
        refuses(() -> escaped[0].assertUnchanged(), "Escaped source cannot assert owner state");
        InputStream[] escapedInput = new InputStream[1];
        withSource(fixture(), source -> {
            escapedInput[0] = source.open();
            need(escapedInput[0].read() == (BYTES[0] & 255), "Actual source stream opened");
            return null;
        });
        refuses(() -> escapedInput[0].read(), "Escaped stream cannot read outside lock lifetime");
        dd6 singleAttempt = fixture();
        withSource(singleAttempt, source -> {
            try (InputStream ignored = source.open()) { ignored.read(); }
            try { source.open(); throw new IllegalStateException("Unexpected source retry"); }
            catch (IOException expected) { need(true, "Source opening reserved once"); }
            return null;
        });
        refuses(() -> withSource(new Object(), source -> null), "Wrong owner rejected");
        refuses(() -> withSource(new dd6(null), source -> null), "No implicit cache creation");
        for (String mode : new String[]{"unknownLength", "noLength", "shortTail", "gap", "overlap",
                "wrongKey", "uncachedHole", "empty", "fileLength"}) {
            dd6 bad = fixture(); d7b first = bad.c.spans.first(), last = bad.c.spans.last();
            switch (mode) {
                case "unknownLength" -> bad.c.metadata.length(-1);
                case "noLength" -> bad.c.metadata.values.clear();
                case "shortTail" -> bad.c.metadata.length(BYTES.length + 1);
                case "gap" -> { bad.c.spans.remove(last); bad.c.spans.add(new d7b(KEY, last.s + 1, last.z, 1, last.B)); }
                case "overlap" -> { bad.c.spans.remove(last); bad.c.spans.add(new d7b(KEY, last.s - 1, last.z, 1, last.B)); }
                case "wrongKey" -> { bad.c.spans.remove(first); bad.c.spans.add(new d7b("other", first.s, first.z, 1, first.B)); }
                case "uncachedHole" -> { bad.c.spans.remove(first); bad.c.spans.add(new d7b(KEY, first.s, first.z, 1, null)); }
                case "empty" -> bad.c.spans.clear();
                case "fileLength" -> Files.write(first.B.toPath(), new byte[]{1}, StandardOpenOption.APPEND);
            }
            Destination untouched = new Destination();
            refuses(() -> copy(bad, untouched), mode);
            need(!untouched.opened, "Incomplete source opens no destination: " + mode);
        }
        for (String mode : new String[]{"delegate", "metadata", "spans", "file"}) {
            dd6 changed = fixture(); Destination untouched = new Destination();
            refuses(() -> withSource(changed, source -> {
                switch (mode) {
                    case "delegate" -> changed.c = new c7b(BYTES.length);
                    case "metadata" -> changed.c.metadata.length(BYTES.length + 1);
                    case "spans" -> changed.c.spans.pollLast();
                    case "file" -> Files.write(changed.c.spans.first().B.toPath(), new byte[]{1}, StandardOpenOption.APPEND);
                }
                return VerifiedStreamCopy.copy(source, untouched, () -> {});
            }), "Change under callback: " + mode);
            need(!untouched.opened, "Changed lease refuses before writing: " + mode);
        }
        AtomicBoolean refusedOtherThread = new AtomicBoolean();
        withSource(fixture(), source -> {
            Thread other = new Thread(() -> {
                try { source.open(); }
                catch (IOException expected) { refusedOtherThread.set(true); }
            });
            other.start();
            try { other.join(2000); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException("Interrupted", e); }
            need(refusedOtherThread.get() && !other.isAlive(), "Lease cannot transfer to another thread");
            return null;
        });
        dd6 contended = fixture(); CountDownLatch starting = new CountDownLatch(1);
        AtomicBoolean entered = new AtomicBoolean();
        Thread writer = new Thread(() -> { starting.countDown(); contended.p(KEY); entered.set(true); });
        withSource(contended, source -> {
            writer.start();
            try {
                if (!starting.await(2, TimeUnit.SECONDS)) throw new IOException("Worker did not start");
                long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
                while (writer.getState() != Thread.State.BLOCKED && System.nanoTime() < end) Thread.yield();
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException("Interrupted", e); }
            need(!entered.get() && writer.getState() == Thread.State.BLOCKED,
                    "Original wrapper operation waits on the held original lock");
            return null;
        });
        writer.join(2000);
        need(entered.get() && !writer.isAlive(), "Original operation resumes after lease closes");

        AtomicBoolean callbackEntered = new AtomicBoolean();
        refuses(() -> NatuneCachedSource.withSource(fixture(), KEY,
                () -> { throw new IOException("Original stopped"); }, source -> {
                    callbackEntered.set(true); return null;
                }), "Stopped job refuses before locks");
        need(!callbackEntered.get(), "Stopped job enters no copy callback");

        dd6 waiting = fixture(); AtomicBoolean stopped = new AtomicBoolean();
        AtomicBoolean stoppedRefused = new AtomicBoolean(); CountDownLatch requested = new CountDownLatch(1);
        Thread waitingJob = new Thread(() -> {
            try {
                NatuneCachedSource.withSource(waiting, KEY, () -> {
                    requested.countDown();
                    if (stopped.get()) throw new IOException("Original stopped while waiting");
                }, source -> { callbackEntered.set(true); return null; });
            } catch (IOException expected) { stoppedRefused.set(true); }
        });
        synchronized (waiting.b) {
            waitingJob.start();
            need(requested.await(2, TimeUnit.SECONDS), "Waiting job checked original stop");
            long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (waitingJob.getState() != Thread.State.BLOCKED && System.nanoTime() < end) Thread.yield();
            need(waitingJob.getState() == Thread.State.BLOCKED, "Job waits on actual wrapper lock");
            stopped.set(true);
        }
        waitingJob.join(2000);
        need(stoppedRefused.get() && !callbackEntered.get() && !waitingJob.isAlive(),
                "Cancelled lock waiter cannot later enter callback");

        // The pinned original resolver returns an actual TreeSet of span
        // objects. The selected key is extracted from that set, not inferred
        // from the song ID or from another available rendition.
        dd6 selectedCache = fixture();
        String alternateKey = "qobuz:public-fixture-song";
        d7b firstSelected = selectedCache.c.spans.first();
        d7b lastSelected = selectedCache.c.spans.last();
        selectedCache.c.spans.add(new d7b(alternateKey, 0, firstSelected.z, 1, firstSelected.B));
        selectedCache.c.spans.add(new d7b(alternateKey, firstSelected.z, lastSelected.z, 1, lastSelected.B));
        TreeSet<d7b> actualSelection = selectedCache.p(alternateKey);
        Destination selectedDestination = new Destination();
        NatuneCachedSource.withSelectedSpans(selectedCache, actualSelection, () -> {}, source -> {
            need(Thread.holdsLock(selectedCache.b) && Thread.holdsLock(selectedCache.c),
                    "Resolver selection is checked under the original locks");
            return VerifiedStreamCopy.copy(source, selectedDestination, () -> {});
        });
        need(Arrays.equals(BYTES, selectedDestination.bytes.toByteArray()),
                "Actual resolver-selected rendition reaches verified copy");

        dd6 stale = fixture(); TreeSet<d7b> staleSelection = stale.p(KEY);
        d7b replaced = stale.c.spans.pollLast();
        stale.c.spans.add(new d7b(replaced.f, replaced.s, replaced.z, replaced.C, replaced.B));
        Destination staleOutput = new Destination();
        refuses(() -> NatuneCachedSource.withSelectedSpans(stale, staleSelection, () -> {},
                source -> VerifiedStreamCopy.copy(source, staleOutput, () -> {})),
                "Same-key same-file replacement makes the old selection stale");
        need(!staleOutput.opened, "Stale selected spans open no output");

        dd6 partial = fixture(); TreeSet<d7b> partialSelection = partial.p(KEY);
        partialSelection.pollLast(); Destination partialOutput = new Destination();
        refuses(() -> NatuneCachedSource.withSelectedSpans(partial, partialSelection, () -> {},
                source -> VerifiedStreamCopy.copy(source, partialOutput, () -> {})),
                "Partial resolver selection refuses even if the cache is complete");
        need(!partialOutput.opened, "Partial selection opens no output");

        TreeSet<d7b> mixed = selectedCache.p(alternateKey);
        mixed.add(selectedCache.p(KEY).first());
        refuses(() -> NatuneCachedSource.withSelectedSpans(selectedCache, mixed, () -> {}, source -> null),
                "Mixed rendition keys refuse");
        refuses(() -> NatuneCachedSource.withSelectedSpans(selectedCache, new TreeSet<d7b>(),
                () -> {}, source -> null), "Empty resolver selection refuses");
        refuses(() -> NatuneCachedSource.withSelectedSpans(fixture(), actualSelection,
                () -> {}, source -> null), "Selection from another cache refuses");
        refuses(() -> NatuneCachedSource.withSelectedSpans(selectedCache, actualSelection,
                () -> { throw new IOException("Original stopped"); }, source -> null),
                "Stopped original cannot enter selected source");

        stopped.set(false);
        refuses(() -> NatuneCachedSource.withSource(fixture(), KEY, () -> {
            if (stopped.get()) throw new IOException("Original stopped during read");
        }, source -> {
            try (InputStream in = source.open()) {
                in.read(); stopped.set(true); in.read();
            }
            return null;
        }), "Original stop closes off further source reads");
    }
    public static void main(String[] args) throws Exception {
        for (int i = 0; i < BYTES.length; i++) BYTES[i] = (byte)(i * 29 + i / 7);
        root = Files.createTempDirectory("purple-public-cache-controls-");
        try { check(); }
        finally {
            try (var paths = Files.walk(root)) {
                for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(p);
            }
        }
        System.out.println("NATUNE_CACHE_SOURCE_CONTROLS passed=" + checks +
                " owners=public_fixture_stand_ins apk_hook=false device_cache_test=false source_removal=false");
    }
}
