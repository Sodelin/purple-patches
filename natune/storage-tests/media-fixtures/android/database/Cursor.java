package android.database;

/** Host-only stand-in, never packaged in an APK. */
public interface Cursor extends AutoCloseable {
    boolean moveToFirst();
    boolean moveToNext();
    String getString(int index);
    int getInt(int index);
    void close();
}
