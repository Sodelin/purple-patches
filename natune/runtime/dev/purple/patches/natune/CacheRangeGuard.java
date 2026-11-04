package dev.purple.patches.natune;

import java.lang.reflect.Method;

/** Checks only the selected cache and range; never opens or mutates a cache. */
public final class CacheRangeGuard {
    private CacheRangeGuard() {}

    public static boolean complete(Object cache, String key, long length) {
        if (cache == null || key == null || length <= 0) return false;
        try {
            // The installed release's public Cache interface is R8-renamed.
            // Resolve on that interface, not a potentially non-public implementation.
            Class<?> api = Class.forName("r8.ud1");
            if (!api.isInstance(cache)) return false;
            Method isCached = api.getMethod("h", long.class, long.class, String.class);
            return Boolean.TRUE.equals(isCached.invoke(cache, 0L, length, key));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            return false;
        }
    }
}
