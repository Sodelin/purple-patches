package android.content;
/** Host-only stand-in, never packaged in an APK. */
public interface SharedPreferences {
    boolean getBoolean(String key,boolean fallback);
    Editor edit();
    interface Editor {
        Editor putBoolean(String key,boolean value);
        Editor clear();
        boolean commit();
    }
}
