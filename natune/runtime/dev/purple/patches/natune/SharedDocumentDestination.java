package dev.purple.patches.natune;

import android.content.ContentResolver;
import android.net.Uri;
import android.provider.DocumentsContract;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;

/**
 * Real Android SAF stream adapter for a newly created document. The owning UI
 * supplies its already-authorized tree URI. This class obtains no permission,
 * stores no URI/preferences/journal, and never deletes a document or cache.
 * Compilation is not evidence of a device grant or provider acceptance.
 */
public final class SharedDocumentDestination implements VerifiedStreamCopy.Destination {
    private final ContentResolver resolver;
    private final Uri document;
    private boolean opened;

    private SharedDocumentDestination(ContentResolver resolver, Uri document) {
        this.resolver = resolver;
        this.document = document;
    }

    public static SharedDocumentDestination create(ContentResolver resolver, Uri tree,
            String mimeType, String displayName) throws IOException {
        Objects.requireNonNull(resolver, "resolver");
        Objects.requireNonNull(tree, "tree");
        if (!"content".equals(tree.getScheme()) || !DocumentsContract.isTreeUri(tree)) {
            throw new IOException("Selected document tree required");
        }
        if (mimeType == null || !mimeType.startsWith("audio/") ||
                displayName == null || displayName.trim().isEmpty() ||
                displayName.indexOf('/') >= 0 || displayName.indexOf('\\') >= 0) {
            throw new IOException("Audio type and document name required");
        }
        // The provider creates a new document, choosing a unique name when
        // necessary. Never look up and truncate a user file by display name.
        Uri parent = DocumentsContract.buildDocumentUriUsingTree(tree,
                DocumentsContract.getTreeDocumentId(tree));
        Uri created = DocumentsContract.createDocument(resolver, parent, mimeType, displayName);
        if (created == null || !"content".equals(created.getScheme())) {
            throw new IOException("Document creation unavailable");
        }
        return new SharedDocumentDestination(resolver, created);
    }

    /** In-memory selected document identity for the real host's indexing step. */
    public Uri documentUri() { return document; }

    @Override public synchronized OutputStream openNewOutput() throws IOException {
        if (opened) throw new IOException("Destination already attempted");
        // Reserve before opening: uncertain provider failure is not a retry.
        opened = true;
        OutputStream stream = resolver.openOutputStream(document, "wt");
        if (stream == null) throw new IOException("Destination output unavailable");
        return stream;
    }

    @Override public synchronized InputStream reopen() throws IOException {
        if (!opened) throw new IOException("Destination not written");
        InputStream stream = resolver.openInputStream(document);
        if (stream == null) throw new IOException("Destination readback unavailable");
        return stream;
    }
}
