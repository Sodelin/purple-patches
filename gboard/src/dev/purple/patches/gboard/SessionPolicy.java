package dev.purple.patches.gboard;

/** Pure policy shared by real IME actions and off-device regression checks. */
public final class SessionPolicy {
    public static final int TEXT_BYTES = 8192;
    public static final int AUDIO_SECONDS = 60;
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
    public static boolean sameAnchor(long expected, long actual, String before, String after,
            String selected, String nowBefore, String nowAfter, String nowSelected) {
        return expected == actual && java.util.Objects.equals(before, nowBefore)
                && java.util.Objects.equals(after, nowAfter)
                && java.util.Objects.equals(selected, nowSelected);
    }
}
