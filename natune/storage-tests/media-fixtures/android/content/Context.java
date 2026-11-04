package android.content;

/** Host-only stand-in, never packaged in an APK. */
public abstract class Context {
    public static final int MODE_PRIVATE=0;
    public abstract Context getApplicationContext();
    public abstract String getPackageName();
    public abstract ContentResolver getContentResolver();
    public ClassLoader getClassLoader(){return getClass().getClassLoader();}
    public SharedPreferences getSharedPreferences(String name,int mode){throw new UnsupportedOperationException();}
}
