package android.content;
import android.database.Cursor;
import android.net.Uri;
import java.io.*;

/** Host-only stand-in, never packaged in an APK. */
public abstract class ContentResolver {
    public abstract Uri insert(Uri uri,ContentValues values);
    public abstract Cursor query(Uri uri,String[] columns,String selection,String[] args,String order);
    public abstract OutputStream openOutputStream(Uri uri,String mode) throws IOException;
    public abstract InputStream openInputStream(Uri uri) throws IOException;
    public abstract int update(Uri uri,ContentValues values,String selection,String[] args);
}
