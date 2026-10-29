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
import java.nio.charset.StandardCharsets;
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
    private final String selectedVolume;
    private final String selectedMime;
    private final Uri row;
    private String observedName;
    private boolean outputAttempted;
    private boolean publishAttempted;

    private MediaStoreAudioDestination(ContentResolver resolver, String ownerPackage, String selectedFolder,
            String selectedVolume, String selectedMime, Uri row) {
        this.resolver = resolver;
        this.ownerPackage = ownerPackage;
        this.selectedFolder = selectedFolder;
        this.selectedVolume = selectedVolume;
        this.selectedMime = selectedMime;
        this.row = row;
    }

    /** Read-only preflight; actual volume/provider state can still change. */
    public static void validateTarget(Context context, String volume, String relativeFolder,
            String displayName, String actualMime) throws IOException {
        Objects.requireNonNull(context, "context");
        if (Build.VERSION.SDK_INT < 29) throw new IOException("Pending audio requires Android 10 or later");
        Context app = context.getApplicationContext();
        if (app == null || app.getPackageName() == null || app.getPackageName().isEmpty())
            throw new IOException("Application owner unavailable");
        if (!validVolume(volume) || !MediaStore.getExternalVolumeNames(app).contains(volume))
            throw new IOException("Selected writable volume unavailable");
        if (normalizedFolder(relativeFolder) == null || !journalName(displayName) || !audioMime(actualMime))
            throw new IOException("Selected folder, audio filename and actual MIME required");
        if (app.getContentResolver() == null) throw new IOException("Media provider unavailable");
    }

    /** Call only after the real operation and complete peak allocation are admitted. */
    public static MediaStoreAudioDestination create(Context context, String volume,
            String relativeFolder, String displayName, String actualMime) throws IOException {
        validateTarget(context, volume, relativeFolder, displayName, actualMime);
        Context app = context.getApplicationContext();
        if (app == null || app.getPackageName() == null || app.getPackageName().isEmpty())
            throw new IOException("Application owner changed");
        ContentResolver resolver = app.getContentResolver();
        if (resolver == null) throw new IOException("Media provider unavailable");
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, displayName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, actualMime);
        String folder = normalizedFolder(relativeFolder);
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, folder);
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);
        Uri row = resolver.insert(MediaStore.Audio.Media.getContentUri(volume), values);
        if (row == null) throw new IOException("Pending media creation uncertain");
        // Return the exact provider result to the owner before a validation
        // query can fail. The owner must durably retain it; a wrong URI is
        // evidence to reconcile, not permission to open or delete a row.
        return new MediaStoreAudioDestination(resolver, app.getPackageName(), folder, volume, actualMime, row);
    }

    static boolean fileName(String name) {
        if (name == null || name.trim().isEmpty() || name.equals(".") || name.equals("..")) return false;
        if (name.indexOf('/') >= 0 || name.indexOf('\\') >= 0) return false;
        for (int i = 0; i < name.length(); i++) if (Character.isISOControl(name.charAt(i))) return false;
        return true;
    }
    // The existing journal accepts at most 512 UTF-8 bytes and rejects blank,
    // control-containing or non-filename values. Reject malformed UTF-16 too;
    // Java's replacement encoding must not change the retained identity.
    static boolean journalName(String name) {
        if (!fileName(name) || !StandardCharsets.UTF_8.newEncoder().canEncode(name) ||
                name.getBytes(StandardCharsets.UTF_8).length > 512) return false;
        return name.codePoints().anyMatch(c -> !Character.isWhitespace(c) && !Character.isSpaceChar(c));
    }
    static boolean validVolume(String volume) {
        if (volume == null || volume.isEmpty()) return false;
        for (int i = 0; i < volume.length(); i++) {
            char c = volume.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') ||
                (c >= '0' && c <= '9') || c == '_' || c == '-')) return false;
        }
        return true;
    }
    static boolean audioMime(String mime) {
        if (mime == null || !mime.startsWith("audio/") || mime.length() <= 6 || mime.equals("audio/*")) return false;
        for (int i = 6; i < mime.length(); i++) {
            char c = mime.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') ||
                (c >= '0' && c <= '9') || "!#$%&'*+-.^_`|~".indexOf(c) >= 0)) return false;
        }
        return true;
    }
    static String normalizedFolder(String path) {
        if (path == null || path.isEmpty() || path.startsWith("/") || path.indexOf('\\') >= 0 ||
                path.indexOf('\0') >= 0 || !StandardCharsets.UTF_8.newEncoder().canEncode(path)) return null;
        String normalized = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        if (normalized.isEmpty()) return null;
        // The durable journal bounds each UTF-8 path component at 4096 bytes.
        for (String part : normalized.split("/", -1))
            if (!fileName(part) || part.getBytes(StandardCharsets.UTF_8).length > 4096) return null;
        return normalized + "/";
    }
    private static boolean selectedRow(Uri row, String volume) {
        if (row == null || !"content".equals(row.getScheme()) || !"media".equals(row.getAuthority())) return false;
        List<String> parts = row.getPathSegments();
        if (parts.size() != 4 || !volume.equals(parts.get(0)) || !"audio".equals(parts.get(1)) ||
                !"media".equals(parts.get(2))) return false;
        String id = parts.get(3);
        if (id.isEmpty() || !row.toString().equals("content://media/" + volume + "/audio/media/" + id))
            return false;
        for (int i = 0; i < id.length(); i++) if (id.charAt(i) < '0' || id.charAt(i) > '9') return false;
        try { return Long.parseLong(id) > 0; }
        catch (NumberFormatException invalid) { return false; }
    }
    public Uri documentUri() { return row; }
    /** The provider may choose a collision-safe name different from the request. */
    public synchronized String observedDisplayName() throws IOException {
        if (observedName == null) throw new IOException("Media row name not yet observed");
        return observedName;
    }

    private void requireOwnedState(boolean pending) throws IOException {
        if (!selectedRow(row, selectedVolume)) throw new IOException("Unexpected media row identity");
        String[] columns = {MediaStore.MediaColumns.OWNER_PACKAGE_NAME, MediaStore.MediaColumns.IS_PENDING,
            MediaStore.MediaColumns.RELATIVE_PATH, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.MIME_TYPE};
        try (Cursor cursor = resolver.query(row, columns, null, null, null)) {
            if (cursor == null || !cursor.moveToFirst())
                throw new IOException("Media row owner/state unavailable or changed");
            String name = cursor.getString(3);
            if (!ownerPackage.equals(cursor.getString(0)) || cursor.getInt(1) != (pending ? 1 : 0) ||
                    !selectedFolder.equals(cursor.getString(2)) || !journalName(name) ||
                    (observedName != null && !observedName.equals(name)) ||
                    !selectedMime.equals(cursor.getString(4)) || cursor.moveToNext())
                throw new IOException("Media row owner/state unavailable or changed");
            if (observedName == null) observedName = name;
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
     * Recheck the complete bytes before and after the one publication update.
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
        verifyBytes(reopen(), proof, stop);
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
        InputStream published = resolver.openInputStream(row);
        if (published == null) throw new IOException("Published audio readback unavailable");
        verifyBytes(published, proof, stop);
        source.assertUnchanged();
        if (!source.complete() || source.length() != proof.bytes) throw new IOException("Source changed");
        requireOwnedState(false);
        stop.check();
    }

    private static void verifyBytes(InputStream stream, VerifiedStreamCopy.VerifiedCopy proof,
            VerifiedStreamCopy.StopCheck stop) throws IOException {
        MessageDigest hash;
        try { hash = MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
        long count = 0;
        byte[] buffer = new byte[VerifiedStreamCopy.BUFFER_BYTES];
        try (InputStream input = stream) {
            while (true) {
                stop.check();
                int n = input.read(buffer);
                stop.check();
                if (n == -1) break;
                if (n <= 0 || n > buffer.length || n > proof.bytes - count)
                    throw new IOException("Media readback length changed");
                hash.update(buffer, 0, n);
                count += n;
            }
        }
        if (count != proof.bytes || !MessageDigest.isEqual(hash.digest(), proof.sha256()))
            throw new IOException("Media bytes changed after verification");
    }
}
