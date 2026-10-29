package r8;
import java.util.TreeSet;
/** Controlled monitor/collection substitute; original device code is inspected separately. */
public final class c7b {
    public final TreeSet<d7b> spans = new TreeSet<>();
    public final vu2 metadata;
    public c7b(long length) { metadata = new vu2(length); }
    public synchronized TreeSet<d7b> p(String key) {
        TreeSet<d7b> result = new TreeSet<>();
        for (d7b span : spans) if (span.f.equals(key)) result.add(span);
        return result;
    }
    public synchronized vu2 d(String key) { return metadata; }
}
