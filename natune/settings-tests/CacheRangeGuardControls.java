import dev.purple.patches.natune.CacheRangeGuard;
import r8.ud1;

/** Models disjoint ranges without writing or reading user cache data. */
public final class CacheRangeGuardControls {
    static final class Cache implements ud1 {
        boolean throwsOnCheck;
        public boolean h(long start, long length, String key) {
            if (throwsOnCheck) throw new IllegalStateException("test");
            if (!key.equals("track") || start != 0) return false;
            // Cached spans are [0,50) and [100,150): 100 bytes but a gap.
            return length <= 50;
        }
    }
    static void need(boolean value) { if (!value) throw new AssertionError(); }
    public static void main(String[] args) {
        Cache cache = new Cache();
        need(CacheRangeGuard.complete(cache, "track", 50));
        need(!CacheRangeGuard.complete(cache, "track", 100));
        need(!CacheRangeGuard.complete(cache, "other", 50));
        need(!CacheRangeGuard.complete(cache, "track", 0));
        need(!CacheRangeGuard.complete(new Object(), "track", 50));
        cache.throwsOnCheck = true;
        need(!CacheRangeGuard.complete(cache, "track", 50));
        System.out.println("Six cache-range controls passed; no real app cache exercised.");
    }
}
