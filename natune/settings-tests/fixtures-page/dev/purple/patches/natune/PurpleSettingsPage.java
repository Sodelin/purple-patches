package dev.purple.patches.natune;
import android.content.Context;
/** Host-only dialog stand-in, never packaged in an APK. */
public final class PurpleSettingsPage {
    public static Context opened;
    private PurpleSettingsPage(){}
    public static void show(Context context){opened=context;}
}
