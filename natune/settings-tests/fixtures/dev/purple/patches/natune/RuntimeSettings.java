package dev.purple.patches.natune;
import android.content.Context;

/** Host-only callback stand-in, never packaged in an APK. */
public final class RuntimeSettings {
    public static Context opened;
    public static boolean fail;
    private RuntimeSettings(){}
    public static void open(Context context){if(fail)throw new IllegalStateException("fixture");opened=context;}
}
