package android.content;

/** Host-only stand-in, never packaged in an APK. */
public abstract class Context {
    public abstract Context getApplicationContext();
    public abstract String getPackageName();
    public abstract ContentResolver getContentResolver();
    public ClassLoader getClassLoader(){return getClass().getClassLoader();}
}
