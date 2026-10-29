package r8;
import java.io.File;
/** Public structural stand-in only; never package test fixtures in an APK. */
public final class d7b implements Comparable<d7b> {
    public final String f;
    public final long s, z, C;
    public final boolean A;
    public final File B;
    public d7b(String key, long position, long length, long touched, File file) {
        f = key; s = position; z = length; C = touched; B = file; A = file != null;
    }
    public int compareTo(d7b other) {
        int keyOrder = f.compareTo(other.f);
        return keyOrder != 0 ? keyOrder : Long.compare(s, other.s);
    }
}
