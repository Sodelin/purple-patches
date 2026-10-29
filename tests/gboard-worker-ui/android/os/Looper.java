package android.os;

/** Minimal host-only stand-in for loading the real runtime class. */
public final class Looper {
    private static final Looper MAIN = new Looper();
    public static Looper getMainLooper() { return MAIN; }
}
