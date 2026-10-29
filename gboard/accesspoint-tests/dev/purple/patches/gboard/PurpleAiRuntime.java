package dev.purple.patches.gboard;

import android.content.Context;

public final class PurpleAiRuntime {
    public static boolean enabled;
    public static int calls, failAt;
    public static Boolean clicked;

    public static boolean enabled(Context context) {
        calls++;
        if (failAt < 0 || calls == failAt) throw new NoClassDefFoundError("synthetic optional dependency");
        return enabled;
    }

    public static void click(boolean whisper) { clicked = whisper; }
}
