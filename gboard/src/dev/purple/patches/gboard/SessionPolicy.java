package dev.purple.patches.gboard;

/** Pure policy shared by real IME actions and off-device regression checks. */
public final class SessionPolicy {
    public static final int TEXT_BYTES = 8192;
    public static final int AUDIO_SECONDS = 60;
    public static final String WHISPER_PREFLIGHT = "full-v3-pin-engine-sha-v1";
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
    public static boolean whisperPreflightSupported(String advertised) {
        return WHISPER_PREFLIGHT.equals(advertised);
    }
    public static boolean translationEffortAllowed(String effort) {
        return "medium".equals(effort) || "max".equals(effort);
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
