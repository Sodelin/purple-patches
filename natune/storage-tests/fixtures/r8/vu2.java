package r8;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
/** Controlled public ContentMetadata representation, not an Android instance. */
public final class vu2 {
    public final Map<String, byte[]> values = new HashMap<>();
    public vu2(long length) { length(length); }
    public void length(long length) { values.put("exo_len", ByteBuffer.allocate(8).putLong(length).array()); }
    public long b(long fallback, String key) {
        byte[] bytes = values.get(key);
        return bytes == null ? fallback : ByteBuffer.wrap(bytes).getLong();
    }
}
