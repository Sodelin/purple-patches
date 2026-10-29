package dev.purple.patches.natune;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Objects;

/**
 * A new pending audio row on a user-selected Android MediaStore volume/folder.
 * The actual owner must admit space and durably retain the operation/returned
 * URI before continuing. Creation, a provider error and publication can be
 * uncertain; this class does not retry, delete or reconcile any row or source.
 * A MediaStore folder is not an arbitrary SAF provider or a remote receipt.
 */
public final class MediaStoreAudioDestination implements VerifiedStreamCopy.Destination {
    private final ContentResolver resolver;
    private final String ownerPackage;
    private final String selectedFolder;
    private final Uri row;
    private boolean outputAttempted;
    private boolean publishAttempted;

    private MediaStoreAudioDestination(ContentResolver resolver, String ownerPackage, String selectedFolder, Uri row) {
        this.resolver = resolver;
        this.ownerPackage = ownerPackage;
        this.selectedFolder = selectedFolder;
        this.row = row;
    }

    /** Call only after the real operation and complete peak allocation are admitted. */
    public static MediaStoreAudioDestination create(Context context, String volume,
            String relativeFolder, String displayName, String actualMime) throws IOException {
        Objects.requireNonNull(context, "context");
        if (Build.VERSION.SDK_INT < 29) throw new IOException("Pending audio requires Android 10 or later");
        Context app = context.getApplicationContext();
        if (app == null || app.getPackageName() == null || app.getPackageName().isEmpty())
            throw new IOException("Application owner unavailable");
        if (volume == null || !MediaStore.getExternalVolumeNames(app).contains(volume))
            throw new IOException("Selected writable volume unavailable");
        if (!relativeFolder(relativeFolder) || !fileName(displayName) || actualMime == null ||
                !actualMime.startsWith("audio/") || actualMime.length() <= "audio/".length())
            throw new IOException("Selected folder, audio filename and actual MIME required");
        ContentResolver resolver = app.getContentResolver();
        if (resolver == null) throw new IOException("Media provider unavailable");
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, displayName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, actualMime);
        String folder = relativeFolder.endsWith("/") ? relativeFolder : relativeFolder + "/";
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, folder);
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);
        Uri row = resolver.insert(MediaStore.Audio.Media.getContentUri(volume), values);
        if (!selectedRow(row, volume))
            throw new IOException("Pending media creation uncertain");
        MediaStoreAudioDestination result = new MediaStoreAudioDestination(resolver, app.getPackageName(), folder, row);
        result.requireOwnedState(true);
        return result;
    }

    private static boolean fileName(String name) {
        if (name == null || name.trim().isEmpty() || name.equals(".") || name.equals("..")) return false;
        return name.indexOf('/') < 0 && name.indexOf('\\') < 0 && name.indexOf('\0') < 0;
    }
    private static boolean relativeFolder(String path) {
        if (path == null || path.isEmpty() || path.startsWith("/") || path.indexOf('\\') >= 0 ||
                path.indexOf('\0') >= 0) return false;
        String normalized = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        if (normalized.isEmpty()) return false;
        for (String part : normalized.split("/", -1)) if (!fileName(part)) return false;
        return true;
    }
    private static boolean selectedRow(Uri row, String volume) {
        if (row == null || !"content".equals(row.getScheme()) || !"media".equals(row.getAuthority())) return false;
        List<String> parts = row.getPathSegments();
        if (parts.size() != 4 || !volume.equals(parts.get(0)) || !"audio".equals(parts.get(1)) ||
                !"media".equals(parts.get(2))) return false;
        try { return Long.parseLong(parts.get(3)) > 0; }
        catch (NumberFormatException invalid) { return false; }
    }
    public Uri documentUri() { return row; }

    private void requireOwnedState(boolean pending) throws IOException {
        String[] columns = {MediaStore.MediaColumns.OWNER_PACKAGE_NAME, MediaStore.MediaColumns.IS_PENDING,
            MediaStore.MediaColumns.RELATIVE_PATH};
        try (Cursor cursor = resolver.query(row, columns, null, null, null)) {
            if (cursor == null || !cursor.moveToFirst() ||
                    !ownerPackage.equals(cursor.getString(0)) || cursor.getInt(1) != (pending ? 1 : 0) ||
                    !selectedFolder.equals(cursor.getString(2)) ||
                    cursor.moveToNext()) throw new IOException("Media row owner/state unavailable or changed");
        }
    }

    @Override public synchronized OutputStream openNewOutput() throws IOException {
        if (outputAttempted || publishAttempted) throw new IOException("Output already attempted");
        outputAttempted = true;
        requireOwnedState(true);
        OutputStream output = resolver.openOutputStream(row, "w");
        if (output == null) throw new IOException("Pending output unavailable");
        return output;
    }
    @Override public synchronized InputStream reopen() throws IOException {
        if (!outputAttempted || publishAttempted) throw new IOException("Pending output unavailable");
        requireOwnedState(true);
        InputStream input = resolver.openInputStream(row);
        if (input == null) throw new IOException("Pending readback unavailable");
        return input;
    }

    /**
     * Publish only this copy result, while its exact source lease is still held.
     * Recheck the complete pending bytes immediately before the one update.
     * Returned publication is not a computer receipt or source-removal permit.
     */
    public synchronized void publish(VerifiedStreamCopy.VerifiedCopy proof,
            VerifiedStreamCopy.SourceLease source, VerifiedStreamCopy.StopCheck stop) throws IOException {
        Objects.requireNonNull(proof, "proof");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(stop, "stop");
        if (!outputAttempted || publishAttempted || !proof.issuedFor(source, this))
            throw new IOException("Original verified copy required");
        stop.check();
        source.assertUnchanged();
        if (!source.complete() || source.length() != proof.bytes) throw new IOException("Source changed");
        MessageDigest hash;
        try { hash = MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
        long count = 0;
        byte[] buffer = new byte[VerifiedStreamCopy.BUFFER_BYTES];
        try (InputStream input = reopen()) {
            while (true) {
                stop.check();
                int n = input.read(buffer);
                stop.check();
                if (n == -1) break;
                if (n <= 0 || n > buffer.length || n > proof.bytes - count)
                    throw new IOException("Pending readback length changed");
                hash.update(buffer, 0, n);
                count += n;
            }
        }
        if (count != proof.bytes || !MessageDigest.isEqual(hash.digest(), proof.sha256()))
            throw new IOException("Pending audio changed after verification");
        stop.check();
        source.assertUnchanged();
        if (!source.complete() || source.length() != proof.bytes) throw new IOException("Source changed");
        requireOwnedState(true);
        stop.check();
        // Latch BEFORE the provider write. A throw/zero result may still have
        // changed the row; only the actual owner can reconcile that uncertainty.
        publishAttempted = true;
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.IS_PENDING, 0);
        if (resolver.update(row, values, null, null) != 1) throw new IOException("Publication uncertain");
        requireOwnedState(false);
        stop.check();
    }
}
