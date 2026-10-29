package dev.purple.patches.natune;

import java.io.IOException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * In-memory admission for one owner's Move batch. Counts promised PEAK extra
 * file allocation, not existing music, bytes copied so far, or available RAM.
 * No executor, persistence, URI access or removal authority is created here.
 * The host must restore unsettled encrypted claims BEFORE admitting work after
 * restart, serialize its actual writers, and supply actual headroom/stop checks.
 * This counter alone does not enforce a limit on Android/provider allocations.
 */
public final class TemporarySpaceBudget {
    public static final long DEFAULT_BYTES = 1_000_000_000L;
    private final long limit;
    private long reserved;
    private final Set<String> attemptedIds = new HashSet<>();

    public TemporarySpaceBudget(long limit) {
        if (limit <= 0) throw new IllegalArgumentException("Positive budget required");
        this.limit = limit;
    }

    public synchronized long reservedBytes() { return reserved; }
    public long limitBytes() { return limit; }

    /**
     * sourceBytes is the exact complete byte length. Staging and overhead must
     * conservatively bound EXTRA ALLOCATION, including rounding, as supplied by
     * the real owner. Staging includes tagging/provider/upload copies, if any.
     * Metadata/rounding overhead must be positive, never silently assumed zero.
     * A refused admission creates no destination and may stay queued under the
     * SAME ID; an accepted ID cannot be admitted again, even after settlement.
     */
    public synchronized Reservation reserve(String requestId, long sourceBytes,
            long stagingBytes, long overheadBytes) throws IOException {
        if (requestId == null || requestId.isEmpty() || sourceBytes <= 0 ||
                stagingBytes < 0 || overheadBytes <= 0) {
            throw new IOException("Known positive length and overhead required");
        }
        if (attemptedIds.contains(requestId)) throw new IOException("Already admitted ID");
        // Check by subtraction before addition, including hostile long inputs.
        if (sourceBytes > limit || stagingBytes > limit - sourceBytes ||
                overheadBytes > limit - sourceBytes - stagingBytes) {
            throw new IOException("Track peak exceeds budget");
        }
        long peak = sourceBytes + stagingBytes + overheadBytes;
        if (peak > limit - reserved) throw new IOException("Temporary budget busy");
        Reservation result = new Reservation(sourceBytes, peak);
        attemptedIds.add(requestId);
        reserved += peak;
        return result;
    }

    public interface DestinationFactory {
        /** Create the NEW owned document only after admission and owner checks. */
        VerifiedStreamCopy.Destination create() throws IOException;
    }

    public interface CleanupCheck {
        /**
         * Actual owner verifies no charged temporary duplicates/staging remain.
         * This is a required host implementation, NOT a boolean cleanup permit.
         * It must not perform/remake a destructive operation as a side effect.
         * A retained document qualifies only after exact cache release/indexing,
         * or after verified removal of a failed/abandoned destination instead.
         */
        void verifySettled() throws IOException;
    }

    public final class Reservation {
        public final long sourceBytes;
        public final long peakBytes;
        private boolean attempted, copying, settling, released;

        private Reservation(long sourceBytes, long peakBytes) {
            this.sourceBytes = sourceBytes;
            this.peakBytes = peakBytes;
        }

        /**
         * Calls the actual bounded copy implementation exactly once. Original
         * StopCheck must ALSO enforce real owner headroom during stream IO.
         * Neither success nor ANY failure releases this reservation. Even a
         * destination factory failure can leave provider-created partial data.
         */
        public VerifiedStreamCopy.VerifiedCopy copy(VerifiedStreamCopy.SourceLease source,
                DestinationFactory factory, VerifiedStreamCopy.StopCheck stop) throws IOException {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(factory, "factory");
            Objects.requireNonNull(stop, "stop");
            synchronized (TemporarySpaceBudget.this) {
                if (released || settling || attempted) throw new IOException("Copy already attempted or closed");
                attempted = true;
                copying = true;
            }
            try {
                stop.check();
                source.assertUnchanged();
                if (!source.complete() || source.length() != sourceBytes) {
                    throw new IOException("Source differs from admission");
                }
                VerifiedStreamCopy.Destination destination = Objects.requireNonNull(factory.create(), "destination");
                return VerifiedStreamCopy.copy(source, destination, stop);
            } finally {
                synchronized (TemporarySpaceBudget.this) { copying = false; }
            }
        }

        /** No auto-close, timeout release, or release-on-export-success. */
        public void releaseAfterCleanup(CleanupCheck owner) throws IOException {
            Objects.requireNonNull(owner, "owner");
            synchronized (TemporarySpaceBudget.this) {
                if (released || copying || settling) throw new IOException("Reservation busy or closed");
                settling = true;
            }
            boolean verified = false;
            try {
                owner.verifySettled();
                verified = true;
            } finally {
                synchronized (TemporarySpaceBudget.this) {
                    if (verified) {
                        reserved -= peakBytes;
                        released = true;
                    }
                    settling = false;
                }
            }
        }
    }
}
