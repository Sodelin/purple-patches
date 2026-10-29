package dev.purple.patches.natune;

import android.content.Context;
import android.content.SharedPreferences;

/** Private app configuration only. No account, model, media, log or service. */
public final class RuntimeSettings {
    private RuntimeSettings() {}
    private static final String FILE = "purple_patches_settings_v1";
    private static final String MOBILE = "full_quality_mobile";
    private static final String FLAC = "require_flac";
    private static volatile Context application;

    /** Exact methods are patch-time feature markers, not persisted user flags.
     * A selected Morphe hook must mark only its own implementation present. */
    public static boolean mobilePatchPresent() { return false; }
    public static boolean flacPatchPresent() { return false; }

    public static void initialize(Context context) {
        if (context == null) throw new IllegalArgumentException("Missing application context");
        Context app = context.getApplicationContext();
        if (app == null) throw new IllegalArgumentException("Application context unavailable");
        application = app;
    }
    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }
    private static boolean read(Context context,String key,boolean fallback) {
        if (context == null) return fallback;
        try { return preferences(context).getBoolean(key,fallback); }
        catch (ClassCastException invalidStoredType) { return fallback; }
    }
    public static boolean fullQualityMobile(Context context) {
        return mobilePatchPresent() && read(context,MOBILE,true);
    }
    /** Called with the ACTUAL original audio-only policy result. A disabled
     * switch returns that exact value, not a reconstructed network guess. */
    /** Morphe binds the exact original helper in front of this refusal body. */
    public static boolean lowData(Context context) {
        throw new IllegalStateException("Original low-data helper is not bound");
    }
    public static boolean mobilePolicy(Context context,boolean originalLowData) {
        return fullQualityMobile(context) ? false : originalLowData;
    }
    public static boolean requireFlac() {
        // If the selected FLAC patch runs before bootstrap, keep its codec gate
        // closed. Correct bootstrap binding remains a mandatory acceptance gate.
        return flacPatchPresent() && read(application,FLAC,true);
    }
    public static boolean requireFlac(Context context) {
        return flacPatchPresent() && read(context,FLAC,true);
    }
    public static boolean setFullQualityMobile(Context context,boolean enabled) {
        if (!mobilePatchPresent()) return false;
        return preferences(context).edit().putBoolean(MOBILE,enabled).commit();
    }
    public static boolean setRequireFlac(Context context,boolean enabled) {
        if (!flacPatchPresent()) return false;
        return preferences(context).edit().putBoolean(FLAC,enabled).commit();
    }
    public static boolean resetPatchSettings(Context context) {
        // Clear only our own preference file. No user downloads, original app
        // preferences/account state, queue, source pins or history are touched.
        return preferences(context).edit().clear().commit();
    }
    public static void open(Context context) {
        PurpleSettingsPage.show(context);
    }
}
