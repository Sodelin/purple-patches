package dev.purple.patches.gboard;

/** Pure policy shared by real IME actions and off-device regression checks. */
public final class SessionPolicy {
    public static final int TEXT_BYTES = 8192;
    public static final int AUDIO_SECONDS = 60;
    public static final String WHISPER_PREFLIGHT = "full-v3-pin-engine-sha-v1";
    public static final String PERMISSION_LOCAL_TOOLS = "local-tools";
    public static final String PERMISSION_MICROPHONE = "microphone";
    private SessionPolicy() {}
    public static boolean sensitive(int inputType, int imeOptions) {
        int cls = inputType & 15, variation = inputType & 4080;
        return (imeOptions & 0x1000000) != 0
                || (cls == 1 && (variation == 128 || variation == 144 || variation == 224))
                || (cls == 2 && variation == 16) || cls == 0;
    }
    /** Termux err is Android RESULT_OK (-1), independently of shell exitCode (0). */
    public static boolean bootstrapSuccess(Object exitCode, Object err) {
        return exitCode instanceof Integer && ((Integer)exitCode) == 0
                && err instanceof Integer && ((Integer)err) == -1;
    }
    public static boolean bootstrapOfferCurrent(long offered, long current, Object owner, Object active) {
        return offered == current && owner != null && owner == active;
    }
    /** The owner synchronizes this request with the actual IME session and delivery. */
    public static final class BootstrapAttempt {
        private long session, deadline;
        private String route;
        public void begin(long session, String route, long deadline) {
            if (route == null || route.isEmpty() || deadline <= 0)
                throw new IllegalArgumentException("Invalid bootstrap attempt");
            this.session = session; this.route = route; this.deadline = deadline;
        }
        public void invalidate() { route = null; deadline = 0; }
        public boolean pending() { return route != null; }
        public boolean consume(long currentSession, boolean ownerPresent, String received, long now) {
            if (route == null) return false;
            if (!ownerPresent || session != currentSession || now > deadline) {
                invalidate();
                return false;
            }
            if (!route.equals(received)) return false;
            invalidate();
            return true;
        }
    }
    public interface InsertionAction { boolean run(); }
    public enum InsertionOutcome { REQUEST_SENT, EDITOR_CHANGED, UNKNOWN, ALREADY_ATTEMPTED }
    public enum PreviewDisposition { INSERTABLE, COPY_ONLY, REFUSE }
    /** A changed anchor may still be shown for copying in the same safe session. */
    public static PreviewDisposition previewDisposition(boolean currentRequest, boolean cancelled,
            boolean safeSession, boolean sameAnchor) {
        if (!currentRequest || cancelled || !safeSession) return PreviewDisposition.REFUSE;
        return sameAnchor ? PreviewDisposition.INSERTABLE : PreviewDisposition.COPY_ONLY;
    }
    /** One preview may send at most one insertion request, even on a duplicate gesture. */
    public static final class InsertionAttempt {
        private boolean attempted;
        public synchronized InsertionOutcome attempt(InsertionAction eligible, InsertionAction send) {
            if (attempted) return InsertionOutcome.ALREADY_ATTEMPTED;
            attempted = true;
            try {
                if (eligible == null || !eligible.run()) return InsertionOutcome.EDITOR_CHANGED;
            } catch (RuntimeException unavailable) { return InsertionOutcome.EDITOR_CHANGED; }
            try {
                return send != null && send.run() ? InsertionOutcome.REQUEST_SENT : InsertionOutcome.UNKNOWN;
            } catch (RuntimeException uncertain) { return InsertionOutcome.UNKNOWN; }
        }
    }
    public static boolean whisperPreflightSupported(String advertised) {
        return WHISPER_PREFLIGHT.equals(advertised);
    }
    /** A health marker is not meaningful unless the expected protocol succeeded. */
    public static boolean bridgeHealth(Object ok, Object protocol, String dataPath) {
        return Boolean.TRUE.equals(ok) && protocol instanceof Integer && ((Integer)protocol) == 1
                && "memory-only-v1".equals(dataPath);
    }
    public static boolean translationEffortAllowed(String effort) {
        return "medium".equals(effort) || "max".equals(effort);
    }
    /** Only show explicitly permitted levels that the selected CLI advertises. */
    public static String[] translationEffortChoices(String[] advertised) {
        boolean medium = false, max = false;
        if (advertised != null) for (String effort : advertised) {
            if ("medium".equals(effort)) medium = true;
            if ("max".equals(effort)) max = true;
        }
        if (medium && max) return new String[]{"medium", "max"};
        if (medium) return new String[]{"medium"};
        if (max) return new String[]{"max"};
        return new String[0];
    }
    public interface TranslationCatalogReader {
        int size();
        String modelId(int index) throws Exception;
        String[] efforts(int index) throws Exception;
    }
    /** Require one exact Sol entry and never substitute an unadvertised level. */
    public static String[] translationCatalog(TranslationCatalogReader catalog) throws Exception {
        if (catalog == null) throw new IllegalStateException("Invalid local model catalogue");
        int count = catalog.size();
        if (count < 0) throw new IllegalStateException("Invalid local model catalogue");
        boolean found = false;
        String[] selected = null;
        for (int i = 0; i < count; i++) {
            if (!"gpt-6.1-sol".equals(catalog.modelId(i))) continue;
            if (found) throw new IllegalStateException("The local Sol catalogue is ambiguous");
            found = true;
            selected = translationEffortChoices(catalog.efforts(i));
        }
        if (!found) throw new IllegalStateException("Sol 6.1 is unavailable in the local catalogue");
        if (selected.length == 0) throw new IllegalStateException("No permitted Sol 6.1 reasoning level is advertised");
        return selected;
    }
    /** Each explicit action requests only its own Android permission. */
    public static String[] permissionsFor(String scope) {
        if (PERMISSION_LOCAL_TOOLS.equals(scope))
            return new String[]{"com.termux.permission.RUN_COMMAND"};
        if (PERMISSION_MICROPHONE.equals(scope))
            return new String[]{"android.permission.RECORD_AUDIO"};
        return new String[0];
    }
    public static boolean bounded(String value) {
        return value != null && !value.trim().isEmpty()
                && value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= TEXT_BYTES;
    }
    public static boolean localEndpoint(String value) {
        if (value == null) return false;
        try {
            java.net.URI uri = new java.net.URI(value);
            return "http".equals(uri.getScheme()) && "127.0.0.1".equals(uri.getHost())
                    && uri.getPort() > 0 && uri.getPort() <= 65535
                    && uri.getRawUserInfo() == null && uri.getRawQuery() == null
                    && uri.getRawFragment() == null
                    && (uri.getRawPath() == null || uri.getRawPath().isEmpty());
        } catch (Exception ignored) { return false; }
    }
    /** Fail closed on unavailable context or an unreadable/inconsistent selection.
     * Android's default connection may return null for a collapsed selection. */
    public static boolean validEditorRead(int start, int end, int offset,
            CharSequence before, CharSequence after, CharSequence selected) {
        if (start < 0 || end < 0 || offset < 0 || before == null || after == null) return false;
        if ((long)offset + Math.max(start,end) > Integer.MAX_VALUE) return false;
        long selectedLength = Math.abs((long)end - start);
        return selected == null ? selectedLength == 0 : selected.length() == selectedLength;
    }
    /** Finite reads of the actual editor; no substitute editor or saved text. */
    public interface EditorReader {
        EditorPosition position();
        CharSequence selected();
        CharSequence before();
        CharSequence after();
    }
    public static final class EditorPosition {
        public final int start, end, offset;
        public EditorPosition(int start, int end, int offset) {
            this.start=start; this.end=end; this.offset=offset;
        }
    }
    public static final class EditorSnapshot {
        public final int start, end, offset;
        public final String before, after, selected;
        private EditorSnapshot(EditorPosition position, String before, String after, String selected) {
            this.start=position.start; this.end=position.end; this.offset=position.offset;
            this.before=before; this.after=after; this.selected=selected;
        }
    }
    /** Re-read selection after context IPC; not an atomic later commitText. */
    public static EditorSnapshot captureEditor(EditorReader reader) {
        if (reader == null) return null;
        try {
            EditorPosition first=reader.position();
            if (first==null) return null;
            CharSequence raw=reader.selected();
            if (raw!=null && raw.length()>TEXT_BYTES) return null;
            String selected=raw==null ? null : raw.toString();
            raw=reader.before(); if (raw==null || raw.length()>64) return null; String before=raw.toString();
            raw=reader.after(); if (raw==null || raw.length()>64) return null; String after=raw.toString();
            if (!validEditorRead(first.start,first.end,first.offset,before,after,selected)) return null;
            EditorPosition last=reader.position();
            if (last==null || first.start!=last.start || first.end!=last.end || first.offset!=last.offset) return null;
            if (selected==null) selected="";
            if (selected.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > TEXT_BYTES) return null;
            return new EditorSnapshot(first,before,after,selected);
        } catch (RuntimeException ignored) { return null; }
    }
    public static boolean sameAnchor(long expected, long actual, String before, String after,
            String selected, String nowBefore, String nowAfter, String nowSelected) {
        return before != null && after != null && selected != null && nowBefore != null
                && nowAfter != null && nowSelected != null
                && expected == actual && java.util.Objects.equals(before, nowBefore)
                && java.util.Objects.equals(after, nowAfter)
                && java.util.Objects.equals(selected, nowSelected);
    }
}
