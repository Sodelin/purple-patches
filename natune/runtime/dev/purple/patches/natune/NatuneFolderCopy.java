package dev.purple.patches.natune;

import android.content.Context;
import android.net.Uri;
import java.io.IOException;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One selected-cache-to-local-folder copy, not a Move or removal operation.
 * The actual app owner must supply the original intent, stop/headroom checks,
 * a recovered shared budget and the existing encrypted journal binding. This
 * class does not authenticate an owner or make a receiver/device claim.
 */
public final class NatuneFolderCopy {
    private NatuneFolderCopy() {}

    /**
     * Real owner callbacks must return only after the corresponding existing
     * encrypted journal event is durable and read back. They also validate the
     * frozen song/rendition, current source generation, selected destination,
     * actual peak/headroom, and the SAME shared journal/budget. A fixture or
     * callback return alone is not proof of those obligations.
     */
    public interface JournalOwner {
        void begin(String operationId, String actualCacheKey,
                VerifiedStreamCopy.SourceLease source,
                TemporarySpaceBudget.Reservation reservation, Target target) throws IOException;
        void destination(String operationId, Uri actualReturnedUri) throws IOException;
        void verified(String operationId, Uri actualReturnedUri,
                VerifiedStreamCopy.VerifiedCopy copy) throws IOException;
    }

    /** Frozen user-selected local MediaStore destination; no grant is created. */
    public static final class Target {
        public final String volume, folder, displayName, audioMime;
        public Target(String volume, String folder, String displayName, String audioMime) {
            this.volume = Objects.requireNonNull(volume, "volume");
            this.folder = Objects.requireNonNull(folder, "folder");
            this.displayName = Objects.requireNonNull(displayName, "displayName");
            this.audioMime = Objects.requireNonNull(audioMime, "audioMime");
        }
    }

    /** Ordered original stages, including calls whose outcome may be unknown. */
    public enum Phase {
        NEW, ENTERED, RESERVED, BEGIN_ENTERED, BEGIN_RETAINED,
        CREATE_ENTERED, DESTINATION_RETURNED, DESTINATION_ENTERED,
        DESTINATION_RETAINED, WRITE_ENTERED, COPY_VERIFIED, PUBLISH_ENTERED,
        PUBLISHED, VERIFIED_ENTERED, VERIFIED_RETAINED, LOCAL_VERIFIED
    }

    /**
     * One in-memory original invocation. On any failure retain this object and
     * its durable original claim for reconciliation; never create a replacement
     * attempt automatically. Getters expose neither a source-removal permit nor
     * a verified computer copy. No raw state is persisted by this class.
     */
    public static final class Attempt {
        private final AtomicBoolean started = new AtomicBoolean();
        private volatile Phase phase = Phase.NEW;
        private volatile boolean failed;
        private volatile MediaStoreAudioDestination destination;
        private volatile TemporarySpaceBudget.Reservation reservation;

        public Phase phase() { return phase; }
        public boolean failed() { return failed; }
        /** May be an unexpected provider response; owner must reconcile it. */
        public Uri returnedUri() {
            MediaStoreAudioDestination value = destination;
            return value == null ? null : value.documentUri();
        }
        public TemporarySpaceBudget.Reservation reservation() { return reservation; }

        /**
         * The actual caller owns operationId and selected spans. stagingBytes
         * and overheadBytes must reflect the selected provider and measured
         * allocation, including required safety/cleanup headroom. The same
         * stop checks the actual owner, free space and original cancellation.
         * Both successful and uncertain outcomes keep the reservation charged.
         */
        public VerifiedStreamCopy.VerifiedCopy run(Context context, Object cache,
                NavigableSet<?> selected, String operationId, Target target,
                TemporarySpaceBudget budget, long stagingBytes, long overheadBytes,
                VerifiedStreamCopy.StopCheck stop, JournalOwner owner) throws IOException {
            Objects.requireNonNull(context, "context");
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(budget, "budget");
            Objects.requireNonNull(stop, "stop");
            Objects.requireNonNull(owner, "owner");
            if (!started.compareAndSet(false, true)) throw new IOException("Original copy already attempted");
            phase = Phase.ENTERED;
            try {
                VerifiedStreamCopy.VerifiedCopy result = NatuneCachedSource.withSelectedSpans(
                    cache, selected, stop, (key, source) -> {
                        source.assertUnchanged();
                        TemporarySpaceBudget.Reservation admitted = budget.reserve(operationId,
                            source.length(), stagingBytes, overheadBytes);
                        reservation = admitted;
                        phase = Phase.RESERVED;
                        stop.check();
                        phase = Phase.BEGIN_ENTERED;
                        owner.begin(operationId, key, source, admitted, target);
                        phase = Phase.BEGIN_RETAINED;
                        stop.check();
                        source.assertUnchanged();
                        VerifiedStreamCopy.VerifiedCopy copy = admitted.copy(source, () -> {
                            stop.check();
                            phase = Phase.CREATE_ENTERED;
                            MediaStoreAudioDestination created = MediaStoreAudioDestination.create(
                                context, target.volume, target.folder, target.displayName, target.audioMime);
                            destination = created;
                            phase = Phase.DESTINATION_RETURNED;
                            stop.check();
                            phase = Phase.DESTINATION_ENTERED;
                            owner.destination(operationId, created.documentUri());
                            phase = Phase.DESTINATION_RETAINED;
                            stop.check();
                            phase = Phase.WRITE_ENTERED;
                            return created;
                        }, stop);
                        phase = Phase.COPY_VERIFIED;
                        stop.check();
                        source.assertUnchanged();
                        phase = Phase.PUBLISH_ENTERED;
                        destination.publish(copy, source, stop);
                        phase = Phase.PUBLISHED;
                        stop.check();
                        phase = Phase.VERIFIED_ENTERED;
                        owner.verified(operationId, destination.documentUri(), copy);
                        phase = Phase.VERIFIED_RETAINED;
                        stop.check();
                        source.assertUnchanged();
                        return copy;
                    });
                phase = Phase.LOCAL_VERIFIED;
                return result;
            } catch (IOException | RuntimeException | Error failure) {
                failed = true;
                throw failure;
            }
        }
    }
}
