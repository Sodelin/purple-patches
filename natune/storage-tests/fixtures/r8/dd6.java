package r8;
import java.util.TreeSet;
/** Controlled wrapper lock in the original source's wrapper-then-delegate order. */
public final class dd6 {
    public final Object b = new Object();
    public volatile c7b c;
    public dd6(c7b delegate) { c = delegate; }
    public TreeSet<d7b> p(String key) { synchronized (b) { return c.p(key); } }
    public vu2 d(String key) { synchronized (b) { return c.d(key); } }
}
