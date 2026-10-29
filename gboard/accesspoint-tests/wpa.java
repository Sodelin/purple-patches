import java.util.Collection;
import java.util.LinkedHashSet;

/** Synthetic set-shaped catalog: actual Gboard code is not loaded. */
public final class wpa extends LinkedHashSet<Object> {
    public wpa(Collection<?> values) { super(values); }
    public static wpa n(Collection<?> values) { return new wpa(values); }
}
