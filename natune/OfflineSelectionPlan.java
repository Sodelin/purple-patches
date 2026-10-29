import java.util.*;

/**
 * Pure selection/reconciliation for the future app adapter. No I/O, account,
 * download, deletion or permission is performed. Inputs must come from the
 * actual app's captured queue/library/cache owner, not provider labels alone.
 */
public final class OfflineSelectionPlan {
    private OfflineSelectionPlan() {}
    public record Key(String mediaId,String rendition) {
        public Key { text(mediaId);text(rendition); }
    }
    /** Reservation for staged bytes; actual transfer must enforce its bound. */
    public record Candidate(Key key,long reservationBytes) {
        public Candidate { Objects.requireNonNull(key);if(reservationBytes<0)throw new IllegalArgumentException("Negative reservation"); }
    }
    /** Caller-owned snapshot, not a certificate or a writer permit. */
    public record Held(Key key,long bytes,Set<String> owners,boolean complete,boolean protectedUse) {
        public Held { Objects.requireNonNull(key);if(bytes<0)throw new IllegalArgumentException("Negative allocation");owners=Set.copyOf(owners);owners.forEach(OfflineSelectionPlan::text); }
    }
    public record Selection(String owner,String revision,List<Key> selected,
                            Map<Key,Long> reservations,List<Key> needDownload,List<Key> reused,
                            List<Key> unknownSize,List<Key> overBudget,long selectedBytes,long byteBudget) {
        public Selection { text(owner);text(revision);selected=List.copyOf(selected);reservations=Map.copyOf(reservations);
            needDownload=List.copyOf(needDownload);reused=List.copyOf(reused);unknownSize=List.copyOf(unknownSize);overBudget=List.copyOf(overBudget);
            var keys=new HashSet<>(selected);
            if(keys.size()!=selected.size() || !keys.equals(reservations.keySet()) || byteBudget<0 || selectedBytes<0 || selectedBytes>byteBudget)
                throw new IllegalArgumentException("Invalid captured selection");
            long sum=0;
            for(long size:reservations.values()) {
                if(size<=0 || size>byteBudget-sum)throw new IllegalArgumentException("Invalid reservation total");
                sum+=size;
            }
            if(sum!=selectedBytes)throw new IllegalArgumentException("Changed selection total");
            var work=new HashSet<>(needDownload);var ready=new HashSet<>(reused);
            if(work.size()!=needDownload.size() || ready.size()!=reused.size() || !Collections.disjoint(work,ready))
                throw new IllegalArgumentException("Ambiguous selection work");
            work.addAll(ready);if(!work.equals(keys))throw new IllegalArgumentException("Incomplete selection work");
        }
    }
    /** These are proposed ownership changes only. The real owner must journal,
     * fence generations, recheck uses and apply them under its original lock. */
    public record ReadyDelta(List<Key> claim,List<Key> release,List<Key> retireTemporaryAfterCommit) {
        public ReadyDelta { claim=List.copyOf(claim);release=List.copyOf(release);retireTemporaryAfterCommit=List.copyOf(retireTemporaryAfterCommit); }
    }
    static void text(String s) { if(s==null || s.isBlank())throw new IllegalArgumentException("Missing scoped identity"); }
    static Map<Key,Held> snapshot(Collection<Held> input) {
        var out=new LinkedHashMap<Key,Held>();
        for(Held h:input)if(out.put(h.key,h)!=null)throw new IllegalArgumentException("Ambiguous held generation");
        return Collections.unmodifiableMap(out);
    }
    /** Complete authoritative source result required. Never turn a failed,
     * partial or signed-out remote page into a desired empty playlist. Rank
     * is supplied by the selected recommendation/playlist source unchanged. */
    public static Selection select(String owner,String sourceRevision,boolean completeSource,
                                   List<Candidate> ranked,Collection<Held> existing,long budgetBytes) {
        text(owner);text(sourceRevision);
        if(!completeSource)throw new IllegalStateException("Incomplete source: retain current selection");
        if(budgetBytes<0)throw new IllegalArgumentException("Negative user budget");
        var held=snapshot(existing);var seen=new HashSet<Key>();
        var selected=new ArrayList<Key>();var downloads=new ArrayList<Key>();var reused=new ArrayList<Key>();
        var unknown=new ArrayList<Key>();var over=new ArrayList<Key>();var reservations=new LinkedHashMap<Key,Long>();
        long used=0;
        for(Candidate c:ranked) {
            if(!seen.add(c.key))continue;
            Held h=held.get(c.key);
            long size=h!=null && h.complete?h.bytes:c.reservationBytes;
            if(size==0) {unknown.add(c.key);continue;}
            // Subtraction prevents overflow even with caller-supplied huge sizes.
            if(size>budgetBytes-used) {over.add(c.key);continue;}
            used+=size;selected.add(c.key);reservations.put(c.key,size);
            if(h!=null && h.complete)reused.add(c.key);else downloads.add(c.key);
        }
        return new Selection(owner,sourceRevision,selected,reservations,downloads,reused,unknown,over,used,budgetBytes);
    }
    /** Compute releases only AFTER every selected generation is actually ready
     * in a fresh owner snapshot. Stage failures/cancellation cannot authorize
     * removal of the former collection. Changed source revisions refuse. */
    public static ReadyDelta readyDelta(Selection plan,String currentSourceRevision,Collection<Held> fresh) {
        if(!plan.revision.equals(currentSourceRevision))throw new IllegalStateException("Source selection changed");
        if(!plan.unknownSize.isEmpty())throw new IllegalStateException("Unresolved candidate sizes: retain old collection");
        var held=snapshot(fresh);long actual=0;
        for(Key key:plan.selected) {
            Held h=held.get(key);
            if(h==null || !h.complete || h.bytes<=0)throw new IllegalStateException("Replacement not ready: retain old collection");
            if(h.bytes>plan.reservations.get(key) || h.bytes>plan.byteBudget-actual)
                throw new IllegalStateException("Actual bytes exceed selected reservation/user budget");
            actual+=h.bytes;
        }
        var wanted=new HashSet<>(plan.selected);var claim=new ArrayList<Key>();var release=new ArrayList<Key>();var retire=new ArrayList<Key>();
        for(Key key:plan.selected)if(!held.get(key).owners.contains(plan.owner))claim.add(key);
        for(Held h:held.values())if(h.owners.contains(plan.owner) && !wanted.contains(h.key)) {
            // Keep the collection reference as well as the bytes while in use.
            // Otherwise a later snapshot cannot identify who should retire the
            // now-unreferenced previous-track resource when its hold ends.
            if(h.protectedUse)continue;
            release.add(h.key);
            // Another playlist, permanent manual save, playback, recovery or
            // unknown in-flight use keeps the actual bytes. No broad cache purge.
            if(h.owners.size()==1 && !h.protectedUse)retire.add(h.key);
        }
        return new ReadyDelta(claim,release,retire);
    }
}
