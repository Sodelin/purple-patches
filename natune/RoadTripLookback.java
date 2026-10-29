import java.util.*;

/** Actual playback transition history, not queue-index subtraction. Pure policy;
 * the app owner supplies transitions and holds its cache lock during apply. */
public record RoadTripLookback(OfflineSelectionPlan.Key current,OfflineSelectionPlan.Key previous) {
    public static RoadTripLookback empty() {return new RoadTripLookback(null,null);}
    public RoadTripLookback advance(OfflineSelectionPlan.Key next) {
        Objects.requireNonNull(next);
        // Repeated callbacks/repeat of the same rendition don't discard the
        // distinct prior song that the back button can still return to.
        return next.equals(current)?this:new RoadTripLookback(next,current);
    }
    public boolean protects(OfflineSelectionPlan.Key key) {
        return key.equals(current) || key.equals(previous);
    }
    public List<OfflineSelectionPlan.Held> protect(Collection<OfflineSelectionPlan.Held> snapshot) {
        return snapshot.stream().map(h->new OfflineSelectionPlan.Held(h.key(),h.bytes(),h.owners(),
            h.complete(),h.protectedUse() || protects(h.key()))).toList();
    }
}
