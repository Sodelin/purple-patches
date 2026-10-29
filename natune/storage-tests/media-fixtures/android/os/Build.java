package android.os;

/** Host-only stand-in, never packaged in an APK. */
public final class Build {
    private Build(){}
    public static final class VERSION { public static int SDK_INT=35; private VERSION(){} }
}
