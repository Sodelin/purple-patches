package dev.purple.patches.natune;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Objects;
import java.util.TreeSet;

/**
 * Read-only exact N202610191717 cache binding. Uses only observed public members
 * inside the app; never private-field access, a new cache, network or removal.
 * The selecting Morphe patch must enforce the original target/signature pins.
 *
 * Locks are the actual LazyCache lock then its actual SimpleCache monitor, in
 * original order. Copy callbacks run synchronously on this owning thread.
 * This excludes known cache mutations, not every possible filesystem writer.
 * Phone latency/playback acceptance and the move transaction remain separate.
 */
public final class NatuneCachedSource {
    private NatuneCachedSource() {}
    public interface ReadAction<T> {
        T run(VerifiedStreamCopy.SourceLease source) throws IOException;
    }

    public static <T> T withSource(Object cache, String exactCacheKey,
            VerifiedStreamCopy.StopCheck stop, ReadAction<T> action)
            throws IOException {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(stop, "stop");
        stop.check();
        if (cache == null || exactCacheKey == null || exactCacheKey.isEmpty())
            throw new IOException("Selected cache and key required");
        Binding binding = new Binding(cache.getClass().getClassLoader());
        if (cache.getClass() != binding.wrapper) throw new IOException("Unsupported cache owner");
        Object lock = get(binding.lock, cache);
        if (lock == null) throw new IOException("Cache lock unavailable");
        synchronized (lock) {
            stop.check();
            Object delegate = get(binding.delegate, cache);
            // Do not call q(): it creates a new cache when none is initialized.
            if (delegate == null || delegate.getClass() != binding.concrete)
                throw new IOException("Initialized original cache required");
            synchronized (delegate) {
                stop.check();
                try (Snapshot source = new Snapshot(binding, cache, lock, delegate, exactCacheKey, stop)) {
                    return action.run(source);
                }
            }
        }
    }

    private static final class Binding {
        final Class<?> wrapper, concrete, span, metadata;
        final Field lock, delegate, key, position, length, cached, file;
        final Method spans, contentMetadata, contentLength;
        Binding(ClassLoader loader) throws IOException {
            try {
                wrapper = Class.forName("r8.dd6", false, loader);
                concrete = Class.forName("r8.c7b", false, loader);
                span = Class.forName("r8.d7b", false, loader);
                metadata = Class.forName("r8.vu2", false, loader);
                lock = field(wrapper, "b", Object.class);
                delegate = field(wrapper, "c", concrete);
                key = field(span, "f", String.class);
                position = field(span, "s", long.class);
                length = field(span, "z", long.class);
                cached = field(span, "A", boolean.class);
                file = field(span, "B", File.class);
                spans = wrapper.getMethod("p", String.class);
                contentMetadata = wrapper.getMethod("d", String.class);
                contentLength = metadata.getMethod("b", long.class, String.class);
                if (spans.getReturnType() != TreeSet.class || contentMetadata.getReturnType() != metadata ||
                    contentLength.getReturnType() != long.class || Modifier.isStatic(spans.getModifiers()) ||
                    Modifier.isStatic(contentMetadata.getModifiers()) || Modifier.isStatic(contentLength.getModifiers()))
                    throw new IOException("Cache method layout changed");
            } catch (ReflectiveOperationException error) {
                throw new IOException("Exact cache binding unavailable", error);
            }
        }
        private static Field field(Class<?> owner, String name, Class<?> type)
                throws ReflectiveOperationException, IOException {
            Field f = owner.getField(name);
            if (f.getType() != type || Modifier.isStatic(f.getModifiers()))
                throw new IOException("Cache field layout changed");
            return f;
        }
        long expected(Object cache, String key) throws IOException {
            Object m = call(contentMetadata, cache, key);
            if (m == null || m.getClass() != metadata) throw new IOException("Cache metadata unavailable");
            // Media3 1.10.1 ContentMetadata.KEY_CONTENT_LENGTH; no URI/header read.
            return (Long) call(contentLength, m, -1L, "exo_len");
        }
        Object[] currentSpans(Object cache, String key) throws IOException {
            Object result = call(spans, cache, key);
            if (!(result instanceof TreeSet<?>)) throw new IOException("Cache spans unavailable");
            return ((TreeSet<?>) result).toArray();
        }
    }

    private static final class Segment {
        final Object original;
        final File file;
        final long length, modified;
        Segment(Object original, File file, long length) {
            this.original = original; this.file = file; this.length = length;
            this.modified = file.lastModified();
        }
        void checkFile() throws IOException {
            if (!file.isFile() || file.length() != length || file.lastModified() != modified)
                throw new IOException("Cached file changed");
        }
    }

    private static final class Snapshot implements VerifiedStreamCopy.SourceLease, AutoCloseable {
        final Binding binding;
        final Object cache, lock, delegate;
        final String key;
        final VerifiedStreamCopy.StopCheck stop;
        final Thread thread = Thread.currentThread();
        final long expected;
        final Segment[] segments;
        boolean alive = true, opened;
        SpanInput input;
        Snapshot(Binding binding, Object cache, Object lock, Object delegate, String key,
                VerifiedStreamCopy.StopCheck stop) throws IOException {
            this.binding = binding; this.cache = cache; this.lock = lock;
            this.delegate = delegate; this.key = key;
            this.stop = stop;
            expected = binding.expected(cache, key);
            if (expected <= 0) throw new IOException("Complete content length required");
            Object[] spans = binding.currentSpans(cache, key);
            if (spans.length == 0) throw new IOException("Completed cached bytes required");
            segments = new Segment[spans.length];
            long end = 0;
            for (int i = 0; i < spans.length; i++) {
                Object span = spans[i];
                if (span == null || span.getClass() != binding.span ||
                    !key.equals(get(binding.key, span)) || !Boolean.TRUE.equals(get(binding.cached, span)))
                    throw new IOException("Different cached rendition");
                long start = (Long) get(binding.position, span), length = (Long) get(binding.length, span);
                Object path = get(binding.file, span);
                if (start != end || length <= 0 || length > expected - end || !(path instanceof File))
                    throw new IOException("Incomplete or overlapping cached coverage");
                segments[i] = new Segment(span, (File) path, length);
                segments[i].checkFile();
                end += length;
            }
            if (end != expected) throw new IOException("Incomplete cached coverage");
            assertUnchanged();
        }
        void guard() throws IOException {
            if (!alive || Thread.currentThread() != thread || !Thread.holdsLock(lock) ||
                !Thread.holdsLock(delegate) || get(binding.delegate, cache) != delegate)
                throw new IOException("Original cache lease no longer held");
            stop.check();
        }
        public long length() { return expected; }
        public boolean complete() { return alive; }
        public void assertUnchanged() throws IOException {
            guard();
            if (binding.expected(cache, key) != expected) throw new IOException("Content length changed");
            Object[] now = binding.currentSpans(cache, key);
            if (now.length != segments.length) throw new IOException("Cached spans changed");
            for (int i = 0; i < now.length; i++) {
                if (now[i] != segments[i].original) throw new IOException("Cached span identity changed");
                segments[i].checkFile();
            }
        }
        public InputStream open() throws IOException {
            assertUnchanged();
            if (opened) throw new IOException("Source already attempted");
            opened = true;
            input = new SpanInput(this);
            return input;
        }
        public void close() throws IOException {
            try { if (input != null) input.close(); }
            finally { alive = false; }
        }
    }

    private static final class SpanInput extends InputStream {
        final Snapshot source;
        final byte[] single = new byte[1];
        int index;
        long remaining;
        FileInputStream current;
        boolean closed;
        SpanInput(Snapshot source) { this.source = source; }
        public int read() throws IOException {
            int n = read(single, 0, 1);
            return n == -1 ? -1 : single[0] & 255;
        }
        public int read(byte[] out, int offset, int length) throws IOException {
            source.guard();
            if (closed) throw new IOException("Source stream closed");
            if (offset < 0 || length < 0 || offset > out.length - length) throw new IndexOutOfBoundsException();
            if (length == 0) return 0;
            while (true) {
                if (current != null && remaining == 0) {
                    if (current.read() != -1) throw new IOException("Cached file grew");
                    current.close(); current = null; index++;
                }
                if (index == source.segments.length) return -1;
                if (current == null) {
                    Segment segment = source.segments[index]; segment.checkFile();
                    current = new FileInputStream(segment.file); remaining = segment.length;
                }
                int n = current.read(out, offset, (int) Math.min(length, remaining));
                if (n <= 0) throw new IOException("Cached file ended early");
                remaining -= n;
                return n;
            }
        }
        public void close() throws IOException {
            closed = true;
            if (current != null) {
                try { current.close(); }
                finally { current = null; }
            }
        }
    }

    private static Object get(Field field, Object target) throws IOException {
        try { return field.get(target); }
        catch (ReflectiveOperationException error) { throw new IOException("Cache field unavailable", error); }
    }
    private static Object call(Method method, Object target, Object... args) throws IOException {
        try { return method.invoke(target, args); }
        catch (ReflectiveOperationException error) { throw new IOException("Cache method unavailable", error); }
    }
}
