package dev.purple.patches.natune;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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
    private final Map<String, Reservation> recoveredReservations = new HashMap<>();

    /** Use only when the actual owner has proven the durable journal empty. */
    public TemporarySpaceBudget(long limit) {
        if (limit <= 0) throw new IllegalArgumentException("Positive budget required");
        this.limit = limit;
    }

    public synchronized long reservedBytes() { return reserved; }
    public long limitBytes() { return limit; }

    /**
     * An in-memory projection of one original durable journal attempt. This is
     * not a serializable permit or proof that the journal was read correctly.
     * The actual owner must supply ALL attempts from one locked, authenticated
     * journal snapshot before admitting any work, with no concurrent writer.
     */
    public static final class RecoveredAttempt {
        public final String requestId;
        public final long sourceBytes, peakBytes;
        public final boolean unsettled;

        public RecoveredAttempt(String requestId, long sourceBytes, long peakBytes,
                boolean unsettled) {
            this.requestId = requestId;
            this.sourceBytes = sourceBytes;
            this.peakBytes = peakBytes;
            this.unsettled = unsettled;
        }
    }

    /**
     * Rebuilds the Java mirror from a complete journal snapshot. The expected
     * count and held-byte total detect accidental partial or inconsistent
     * transfer; they do not authenticate the sender or prove source custody.
     * Settled IDs remain consumed. An unsettled original is charged even if a
     * later limit is smaller, and cannot copy again without a future explicit
     * original-operation reconciliation. Overflow or disagreement fails closed.
     */
    public static TemporarySpaceBudget fromRecovery(long limit,
            Iterable<RecoveredAttempt> attempts, int expectedCount, long expectedHeldBytes)
            throws IOException {
        if (limit <= 0 || attempts == null || expectedCount < 0 || expectedHeldBytes < 0)
            throw new IOException("Complete recovery snapshot required");
        TemporarySpaceBudget result = new TemporarySpaceBudget(limit);
        int count = 0;
        for (RecoveredAttempt attempt : attempts) {
            if (count >= expectedCount) throw new IOException("Too many recovered attempts");
            if (attempt == null || !validId(attempt.requestId) || attempt.sourceBytes <= 0 ||
                    attempt.peakBytes <= attempt.sourceBytes ||
                    !result.attemptedIds.add(attempt.requestId)) {
                throw new IOException("Invalid or duplicate recovered attempt");
            }
            count++;
            if (attempt.unsettled) {
                if (attempt.peakBytes > Long.MAX_VALUE - result.reserved)
                    throw new IOException("Recovered holds overflow");
                result.reserved += attempt.peakBytes;
                result.recoveredReservations.put(attempt.requestId,
                        result.new Reservation(attempt.requestId, attempt.sourceBytes,
                                attempt.peakBytes, true));
            }
        }
        if (count != expectedCount || result.reserved != expectedHeldBytes)
            throw new IOException("Incomplete or inconsistent recovery snapshot");
        return result;
    }

    /** Only an unresolved restored attempt has a reconciliation handle. */
    public synchronized Reservation recoveredReservation(String requestId) throws IOException {
        Reservation reservation = recoveredReservations.get(requestId);
        if (reservation == null) throw new IOException("Recovered hold unavailable or settled");
        return reservation;
    }

    private static boolean validId(String requestId) {
        if (requestId == null || requestId.isEmpty() || requestId.length() > 128) return false;
        for (int i = 0; i < requestId.length(); i++)
            if (Character.isISOControl(requestId.charAt(i))) return false;
        return true;
    }

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
        if (!validId(requestId) || sourceBytes <= 0 ||
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
        if (reserved > limit || peak > limit - reserved) throw new IOException("Temporary budget busy");
        Reservation result = new Reservation(requestId, sourceBytes, peak, false);
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
         * A retained document qualifies only after exact cache release/indexing.
         * For remote staging, the owner must verify the corresponding receiver
         * copy, confirm this operation's disposable local staging is gone while
         * preserving the source, and durably record/reconcile StageCleaned
         * BEFORE returning. Failed/abandoned output also requires confirmed
         * cleanup. A callback or an uncertain journal append is not enough.
         */
        void verifySettled() throws IOException;
    }

    public final class Reservation {
        private final String requestId;
        public final long sourceBytes;
        public final long peakBytes;
        private boolean attempted, copying, settling, released;

        private Reservation(String requestId, long sourceBytes, long peakBytes, boolean attempted) {
            this.requestId = requestId;
            this.sourceBytes = sourceBytes;
            this.peakBytes = peakBytes;
            this.attempted = attempted;
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
                        recoveredReservations.remove(requestId);
                    }
                    settling = false;
                }
            }
        }
    }
}
